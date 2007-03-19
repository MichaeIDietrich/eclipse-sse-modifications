/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.wst.jsdt.web.ui.views.contentoutline;



import org.eclipse.wst.html.ui.internal.contentoutline.JFaceNodeAdapterForHTML;
import org.eclipse.wst.sse.core.internal.provisional.INodeAdapter;
import org.eclipse.wst.sse.core.internal.provisional.INodeAdapterFactory;
import org.eclipse.wst.sse.core.internal.provisional.INodeNotifier;
import org.eclipse.wst.sse.ui.internal.contentoutline.IJFaceNodeAdapter;
import org.eclipse.wst.xml.ui.internal.contentoutline.JFaceNodeAdapterFactory;

/**
 * An adapter factory to create JFaceNodeAdapters.  Use this
 * adapter factory with a JFaceAdapterContentProvider to display
 * DOM nodes in a tree.
 */
public class JFaceNodeAdapterFactoryForJSDT extends JFaceNodeAdapterFactory {


	public JFaceNodeAdapterFactoryForJSDT() {
		this(IJFaceNodeAdapter.class, true);
	}

	public JFaceNodeAdapterFactoryForJSDT(Object adapterKey, boolean registerAdapters) {
		super(adapterKey, registerAdapters);
	}

	protected INodeAdapter createAdapter(INodeNotifier node) {
		if (singletonAdapter == null) {
			// create the JFaceNodeAdapter
			//singletonAdapter = new JFaceNodeAdapterForJSDT(this);
            singletonAdapter = new JFaceNodeAdapterForJs(this);
			initAdapter(singletonAdapter, node);
		}
		return singletonAdapter;
	}

	public INodeAdapterFactory copy() {
		return new JFaceNodeAdapterFactoryForJSDT(getAdapterKey(), isShouldRegisterAdapter());
	}

}