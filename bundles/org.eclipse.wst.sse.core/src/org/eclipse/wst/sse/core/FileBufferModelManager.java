/*******************************************************************************
 * Copyright (c) 2001, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Jens Lukowski/Innoopract - initial renaming/restructuring
 *     
 *******************************************************************************/
package org.eclipse.wst.sse.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.IFileBuffer;
import org.eclipse.core.filebuffers.IFileBufferListener;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.content.IContentTypeManager;
import org.eclipse.jface.text.IDocument;
import org.eclipse.wst.sse.core.exceptions.ResourceInUse;
import org.eclipse.wst.sse.core.internal.Logger;
import org.eclipse.wst.sse.core.internal.modelhandler.ModelHandlerRegistry;
import org.eclipse.wst.sse.core.modelhandler.IModelHandler;
import org.eclipse.wst.sse.core.text.IStructuredDocument;
import org.eclipse.wst.sse.core.util.Assert;
import org.eclipse.wst.sse.core.util.ProjectResolver;
import org.eclipse.wst.sse.core.util.URIResolver;


class FileBufferModelManager {

	static class DocumentInfo {
		ITextFileBuffer buffer = null;
		String contentTypeID = null;
		IStructuredModel model = null;
		boolean selfConnected = false;
		int bufferReferenceCount = 0;
		int modelReferenceCount = 0;
	}

	/**
	 * A URIResolver instance of models built on java.io.Files
	 */
	class ExternalURIResolver implements URIResolver {
		String location = null;

		public String getFileBaseLocation() {
			return location;
		}

		public String getLocationByURI(String uri) {
			return null;
		}

		public String getLocationByURI(String uri, boolean resolveCrossProjectLinks) {
			return null;
		}

		public String getLocationByURI(String uri, String baseReference) {
			return null;
		}

		public String getLocationByURI(String uri, String baseReference, boolean resolveCrossProjectLinks) {
			return null;
		}

		public IProject getProject() {
			return null;
		}

		public IContainer getRootLocation() {
			return null;
		}

		public InputStream getURIStream(String uri) {
			return null;
		}

		public void setFileBaseLocation(String newLocation) {
			location = newLocation;
		}

		public void setProject(IProject newProject) {
		}
	}

	/**
	 * Maps interesting documents in file buffers to those file buffers.
	 * Required to allows us to go from documents to complete models.
	 */
	class FileBufferMapper implements IFileBufferListener {
		public void bufferContentAboutToBeReplaced(IFileBuffer buffer) {
		}

		public void bufferContentReplaced(IFileBuffer buffer) {
		}

		public void bufferCreated(IFileBuffer buffer) {
			if (buffer instanceof ITextFileBuffer) {
				ITextFileBuffer textBuffer = (ITextFileBuffer) buffer;
				if (!(textBuffer.getDocument() instanceof IStructuredDocument))
					return;
				if (debugTextBufferLifeCycle) {
					System.out.println("Learned new buffer: " + buffer.getLocation().toString() + " " + buffer + " " + ((ITextFileBuffer)buffer).getDocument());
				}
				DocumentInfo info = new DocumentInfo();
				info.buffer = textBuffer;
				info.contentTypeID = detectContentType(buffer.getLocation()).getId();
				info.bufferReferenceCount++;
				fDocumentMap.put(textBuffer.getDocument(), info);
			}
		}

		public void bufferDisposed(IFileBuffer buffer) {
			if (buffer instanceof ITextFileBuffer) {
				ITextFileBuffer textBuffer = (ITextFileBuffer) buffer;
				if (!(textBuffer.getDocument() instanceof IStructuredDocument))
					return;
				if (debugTextBufferLifeCycle) {
					System.out.println("Discarded buffer: " + buffer.getLocation().toString() + " " + buffer + " " + ((ITextFileBuffer)buffer).getDocument());
				}
				DocumentInfo info = (DocumentInfo) fDocumentMap.get(textBuffer.getDocument());
				if (info != null) {
					info.bufferReferenceCount--;
					if (info.bufferReferenceCount == 0 && info.modelReferenceCount == 0)
						fDocumentMap.remove(textBuffer.getDocument());
				}
			}
		}

		public void dirtyStateChanged(IFileBuffer buffer, boolean isDirty) {
			if (buffer instanceof ITextFileBuffer) {
				if (debugTextBufferLifeCycle) {
					System.out.println("Buffer dirty state changed: (" + isDirty + ") " + buffer.getLocation().toString() + " " + buffer + " " + ((ITextFileBuffer)buffer).getDocument());
				}
				ITextFileBuffer textBuffer = (ITextFileBuffer) buffer;
				if (!(textBuffer.getDocument() instanceof IStructuredDocument))
					return;
				DocumentInfo info = (DocumentInfo) fDocumentMap.get(textBuffer.getDocument());
				if (info != null && info.model != null) {
					String msg = "Updating model dirty state for" + info.buffer.getLocation();
					if (debugFileBufferModelManagement || debugTextBufferLifeCycle) {
						System.out.println(msg);
					}
					info.model.setDirtyState(isDirty);

					IFile workspaceFile = FileBuffers.getWorkspaceFileAtLocation(info.buffer.getLocation());
					if (!isDirty && workspaceFile != null) {
						info.model.resetSynchronizationStamp(workspaceFile);
					}
				}
			}
		}

		public void stateChangeFailed(IFileBuffer buffer) {
		}

		public void stateChanging(IFileBuffer buffer) {
		}

		public void stateValidationChanged(IFileBuffer buffer, boolean isStateValidated) {
		}

		public void underlyingFileDeleted(IFileBuffer buffer) {
			if (buffer instanceof ITextFileBuffer) {
				if (debugTextBufferLifeCycle) {
					System.out.println("Deleted buffer: " + buffer.getLocation().toOSString() + " " + buffer);
				}
			}
		}

		public void underlyingFileMoved(IFileBuffer buffer, IPath path) {
			if (buffer instanceof ITextFileBuffer) {
				if (debugTextBufferLifeCycle) {
					System.out.println("Moved buffer from: " + buffer.getLocation().toOSString() + " " + buffer);
					System.out.println("Moved buffer to: " + path.toOSString() + " " + buffer);
				}
//				ITextFileBuffer textBuffer = (ITextFileBuffer) buffer;
//				DocumentInfo info = (DocumentInfo) fDocumentMap.get(textBuffer.getDocument());
//				if (info != null) {
//					info.contentTypeID = detectContentType(path).getId();
//					if (info.model != null) {
//						info.model.setBaseLocation(path.toString());
//					}
//				}
			}
		}
	}

	static final boolean debugFileBufferModelManagement = "true".equalsIgnoreCase(Platform.getDebugOption("org.eclipse.wst.sse.core/filebuffers/modelmanagement"));

	static final boolean debugTextBufferLifeCycle = "true".equalsIgnoreCase(Platform.getDebugOption("org.eclipse.wst.sse.core/filebuffers/lifecycle"));

	private static FileBufferModelManager instance;

	static FileBufferModelManager getInstance() {
		if (instance == null) {
			instance = new FileBufferModelManager();
		}
		return instance;
	}

	static final void shutdown() {
		if (instance != null) {
			if (debugFileBufferModelManagement) {
				IDocument[] danglingDocuments = (IDocument[]) instance.fDocumentMap.keySet().toArray(new IDocument[0]);
				for (int i = 0; i < danglingDocuments.length; i++) {
					DocumentInfo info = (DocumentInfo) instance.fDocumentMap.get(danglingDocuments[i]);
					if (info.modelReferenceCount > 0)
						System.err.println("LEAKED MODEL: " + info.buffer.getLocation() + " " + info.buffer.getDocument());
					if (info.bufferReferenceCount > 0)
						System.err.println("LEAKED BUFFER: " + info.buffer.getLocation() + " " + info.buffer.getDocument());
				}
			}
			FileBuffers.getTextFileBufferManager().removeFileBufferListener(instance.fFileBufferListener);
			instance = null;
		}
	}

	static final void startup() {
		getInstance();
	}

	// a map of IStructuredDocuments to DocumentInfo objects
	Map fDocumentMap = null;

	IFileBufferListener fFileBufferListener = null;

	FileBufferModelManager() {
		super();
		fDocumentMap = new Hashtable(4);
		FileBuffers.getTextFileBufferManager().addFileBufferListener(fFileBufferListener = new FileBufferMapper());
	}

	private void addFactories(IStructuredModel model, IModelHandler handler) {
		Assert.isNotNull(model, "model can not be null"); //$NON-NLS-1$
		Assert.isNotNull(handler, "model handler can not be null"); //$NON-NLS-1$
		IFactoryRegistry registry = model.getFactoryRegistry();
		Assert.isNotNull(registry, "Factory Registry can not be null"); //$NON-NLS-1$
		List factoryList = handler.getAdapterFactories();
		addFactories(model, factoryList);
	}

	private void addFactories(IStructuredModel model, List factoryList) {
		Assert.isNotNull(model, "model can not be null"); //$NON-NLS-1$
		IFactoryRegistry registry = model.getFactoryRegistry();
		Assert.isNotNull(registry, "Factory Registry can not be null"); //$NON-NLS-1$
		// Note: we add all of them from handler, even if
		// already exists. May need to reconsider this.
		if (factoryList != null) {
			for (int i = 0; i < factoryList.size(); i++) {
				AdapterFactory factory = (AdapterFactory) factoryList.get(i);
				registry.addFactory(factory);
			}
		}
	}

	String calculateId(IFile file) {
		String id = null;
		IPath path = file.getLocation();
		if (path != null) {
			/*
			 * The ID of models must be the same as the normalized paths
			 * stored in the underlying FileBuffers to retrieve them by common
			 * ID later on. We chose the FileBuffer normalized path over the
			 * previously used absolute IFile path because the buffers should
			 * already exist before we build a model and we can't retrieve a
			 * FileBuffer using the ID of a model that doesn't yet exist.
			 */
			id = FileBuffers.normalizeLocation(path).toString();
		}
		return id;

	}

	String calculateId(IStructuredDocument document) {
		String id = null;
		ITextFileBuffer buffer = getBuffer(document);
		if (buffer != null) {
			id = buffer.getLocation().toString();
		}
		return id;
	}

	URIResolver createURIResolver(ITextFileBuffer buffer) {
		IPath location = buffer.getLocation();
		IFile workspaceFile = FileBuffers.getWorkspaceFileAtLocation(location);
		URIResolver resolver = null;
		if (workspaceFile != null) {
			IProject project = workspaceFile.getProject();
			resolver = (URIResolver) project.getAdapter(URIResolver.class);
			if (resolver == null) {
				resolver = new ProjectResolver(project);
			}
		} else {
			resolver = new ExternalURIResolver();
		}
		resolver.setFileBaseLocation(location.toString());
		return resolver;
	}


	IContentType detectContentType(IPath location) {
		IContentType type = null;

		IResource resource = FileBuffers.getWorkspaceFileAtLocation(location);
		if (resource != null) {
			if (resource.getType() == IResource.FILE && resource.isAccessible()) {
				IContentDescription d = null;
				try {
					// Optimized description lookup, might not succeed
					d = ((IFile) resource).getContentDescription();
					if (d != null) {
						type = d.getContentType();
					}
				} catch (CoreException e) {
					// Should not be possible given the accessible and file
					// type check above
				}
				if (type == null) {
					type = Platform.getContentTypeManager().findContentTypeFor(resource.getName());
				}
			}
		} else {
			File file = FileBuffers.getSystemFileAtLocation(location);
			if (file != null) {
				InputStream input = null;
				try {
					input = new FileInputStream(file);
					type = Platform.getContentTypeManager().findContentTypeFor(input, location.toOSString());
				} catch (FileNotFoundException e) {
				} catch (IOException e) {
				} finally {
					if (input != null) {
						try {
							input.close();
						} catch (IOException e1) {
						}
					}
				}
				if (type == null) {
					type = Platform.getContentTypeManager().findContentTypeFor(file.getName());
				}
			}
		}
		if (type == null) {
			type = Platform.getContentTypeManager().getContentType(IContentTypeManager.CT_TEXT);
		}
		return type;
	}

	ITextFileBuffer getBuffer(IDocument document) {
		DocumentInfo info = (DocumentInfo) fDocumentMap.get(document);
		if (info != null)
			return info.buffer;
		return null;
	}

	String getContentTypeID(IDocument document) {
		DocumentInfo info = (DocumentInfo) fDocumentMap.get(document);
		if (info != null)
			return info.contentTypeID;
		return null;
	}

	IStructuredModel getModel(File file) {
		IStructuredModel model = null;
		ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
		try {
			IPath location = new Path(file.getAbsolutePath());
			if (debugFileBufferModelManagement) {
				System.out.println("FileBufferModelManager connecting to File " + location);
			}
			bufferManager.connect(location, getProgressMonitor());
			ITextFileBuffer buffer = bufferManager.getTextFileBuffer(location);
			if (buffer != null) {
				DocumentInfo info = (DocumentInfo) fDocumentMap.get(buffer.getDocument());
				info.selfConnected = true;
				model = getModel((IStructuredDocument) buffer.getDocument());
			}
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return model;
	}

	IStructuredModel getModel(IFile file) {
		IStructuredModel model = null;
		ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
		try {
			if (debugFileBufferModelManagement) {
				System.out.println("FileBufferModelManager connecting to IFile " + file.getLocation());
			}
			bufferManager.connect(file.getLocation(), getProgressMonitor());
			ITextFileBuffer buffer = bufferManager.getTextFileBuffer(file.getLocation());
			if (buffer != null) {
				DocumentInfo info = (DocumentInfo) fDocumentMap.get(buffer.getDocument());
				info.selfConnected = true;
				model = getModel((IStructuredDocument) buffer.getDocument());
			}
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return model;
	}

	IStructuredModel getModel(IStructuredDocument document) {
		if (document == null)
			return null;

		DocumentInfo info = (DocumentInfo) fDocumentMap.get(document);
		if (info != null && info.model == null) {
			if (debugFileBufferModelManagement) {
				System.out.println("FileBufferModelManager creating model for " + info.buffer.getLocation() + " " + info.buffer.getDocument());
			}
			info.modelReferenceCount++;

			IStructuredModel model = null;
			IModelHandler handler = ModelHandlerRegistry.getInstance().getHandlerForContentTypeId(info.contentTypeID);
			ModelLoader loader = handler.getModelLoader();
			boolean mustSetDocument = true;
			if (loader instanceof IModelLoaderExtension) {
				mustSetDocument = false;
				model = ((IModelLoaderExtension) loader).createModel(document);
			} else {
				model = loader.createModel();
			}
			try {
				info.model = model;
				model.setBaseLocation(info.buffer.getLocation().toOSString());
				model.setId(info.buffer.getLocation().toString());
				model.setModelHandler(handler);
				model.setResolver(createURIResolver(getBuffer(document)));
				if (mustSetDocument) {
					model.setStructuredDocument(document);
				}
				addFactories(model, handler);
				if(info.buffer.isDirty()) {
					model.setDirtyState(true);
				}
			} catch (ResourceInUse e) {
				Logger.log(Logger.ERROR, "attempted to create new model with existing ID", e);
				model = null;
			}
		}
		if (info != null) {
			return info.model;
		}
		return null;
	}

	/**
	 * @return
	 */
	private IProgressMonitor getProgressMonitor() {
		return new NullProgressMonitor();
	}

	boolean isExistingBuffer(IDocument document) {
		if (document == null)
			return false;

		DocumentInfo info = (DocumentInfo) fDocumentMap.get(document);
		return info != null;
	}

	void releaseModel(IStructuredDocument document) {
		DocumentInfo info = (DocumentInfo) fDocumentMap.get(document);
		if (info != null) {
			if (debugFileBufferModelManagement) {
				System.out.println("FileBufferModelManager noticed full release of model for " + info.buffer.getLocation()  + " " + info.buffer.getDocument());
			}
			info.model = null;
			info.modelReferenceCount--;
			if (info.selfConnected) {
				if (debugFileBufferModelManagement) {
					System.out.println("FileBufferModelManager disconnecting from " + info.buffer.getLocation() + " " + info.buffer.getDocument());
				}
				try {
					FileBuffers.getTextFileBufferManager().disconnect(info.buffer.getLocation(), getProgressMonitor());
				} catch (CoreException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
}
