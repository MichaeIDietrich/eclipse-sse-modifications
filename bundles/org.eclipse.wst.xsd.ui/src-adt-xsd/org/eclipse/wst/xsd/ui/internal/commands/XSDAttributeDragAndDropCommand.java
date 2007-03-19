/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.wst.xsd.ui.internal.commands;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.draw2d.geometry.Point;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.EditPartViewer;
import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.requests.ChangeBoundsRequest;
import org.eclipse.wst.xsd.ui.internal.actions.MoveXSDAttributeAction;
import org.eclipse.wst.xsd.ui.internal.adapters.XSDAttributeDeclarationAdapter;
import org.eclipse.wst.xsd.ui.internal.adt.design.editparts.BaseFieldEditPart;
import org.eclipse.wst.xsd.ui.internal.adt.design.editparts.CompartmentEditPart;
import org.eclipse.wst.xsd.ui.internal.adt.design.editparts.ComplexTypeEditPart;
import org.eclipse.wst.xsd.ui.internal.design.editparts.AttributeGroupDefinitionEditPart;
import org.eclipse.wst.xsd.ui.internal.design.editparts.TargetConnectionSpacingFigureEditPart;
import org.eclipse.wst.xsd.ui.internal.design.editparts.XSDAttributesForAnnotationEditPart;
import org.eclipse.wst.xsd.ui.internal.design.editparts.XSDBaseFieldEditPart;
import org.eclipse.wst.xsd.ui.internal.design.figures.GenericGroupFigure;
import org.eclipse.xsd.XSDAttributeDeclaration;
import org.eclipse.xsd.XSDConcreteComponent;

public class XSDAttributeDragAndDropCommand extends BaseDragAndDropCommand
{
  public XSDAttributeDragAndDropCommand(EditPartViewer viewer, ChangeBoundsRequest request, GraphicalEditPart target, XSDBaseFieldEditPart itemToDrag, Point location)
  {
    super(viewer, request);
    this.target = target;
    this.itemToDrag = itemToDrag;
    this.location = location;
    setup();
  }

  protected void setup()
  {
    canExecute = false;

    // Drop target is attribute group ref
    if (target instanceof AttributeGroupDefinitionEditPart)
    {
      parentEditPart = (AttributeGroupDefinitionEditPart) target;
      if (((GenericGroupFigure) parentEditPart.getFigure()).getIconFigure().getBounds().contains(location))
      {
        xsdComponentToDrag = (XSDConcreteComponent) ((XSDAttributeDeclarationAdapter) itemToDrag.getModel()).getTarget();
        action = new MoveXSDAttributeAction(((AttributeGroupDefinitionEditPart) parentEditPart).getXSDAttributeGroupDefinition(), xsdComponentToDrag, null, null);
        canExecute = action.canMove();
      }
    }
    else if (target instanceof BaseFieldEditPart)
    {
      targetSpacesList = new ArrayList();
      // Calculate the list of all sibling field edit parts;
      List targetEditPartSiblings = calculateFieldEditParts();
      calculateAttributeGroupList();

      // Get 'left' and 'right' siblings
      doDrop(targetEditPartSiblings, itemToDrag);
    }
  }

  protected void doDrop(List siblings, GraphicalEditPart movingEditPart)
  {
    commonSetup(siblings, movingEditPart);

    if (previousRefComponent instanceof XSDAttributeDeclaration && nextRefComponent instanceof XSDAttributeDeclaration)
    {
      XSDConcreteComponent parent = ((XSDAttributeDeclaration) previousRefComponent).getContainer().getContainer();
      if (closerSibling == BELOW_IS_CLOSER)
      {
        parent = ((XSDAttributeDeclaration) nextRefComponent).getContainer().getContainer();
      }
      action = new MoveXSDAttributeAction(parent, xsdComponentToDrag, previousRefComponent, nextRefComponent);
    }
    else if (previousRefComponent == null && nextRefComponent instanceof XSDAttributeDeclaration)
    {
      XSDConcreteComponent parent = ((XSDAttributeDeclaration) nextRefComponent).getContainer().getContainer();
      if (closerSibling == ABOVE_IS_CLOSER)
      {
        if (leftSiblingEditPart == null)
        {
          action = new MoveXSDAttributeAction(parent, xsdComponentToDrag, previousRefComponent, nextRefComponent);
        }
        else if (parentEditPart != null)
        {
          action = new MoveXSDAttributeAction(parentEditPart.getXSDConcreteComponent(), xsdComponentToDrag, previousRefComponent, nextRefComponent);
        }
      }
      else
      {
        action = new MoveXSDAttributeAction(parent, xsdComponentToDrag, previousRefComponent, nextRefComponent);
      }
    }
    else if (previousRefComponent instanceof XSDAttributeDeclaration && nextRefComponent == null)
    {
      XSDConcreteComponent parent = ((XSDAttributeDeclaration) previousRefComponent).getContainer().getContainer();
      if (closerSibling == ABOVE_IS_CLOSER)
      {
        action = new MoveXSDAttributeAction(parent, xsdComponentToDrag, previousRefComponent, nextRefComponent);
      }
      else
      {
        if (rightSiblingEditPart == null)
        {
          action = new MoveXSDAttributeAction(parent, xsdComponentToDrag, previousRefComponent, nextRefComponent);
        }
        else
        {
          action = new MoveXSDAttributeAction(parent, xsdComponentToDrag, previousRefComponent, nextRefComponent);
        }
      }
    }

    if (action != null)
      canExecute = action.canMove();
  }

  
  // Attribute Group related helper method 
  
  protected void calculateAttributeGroupList()
  {
    EditPart editPart = target;
    while (editPart != null)
    {
      if (editPart instanceof ComplexTypeEditPart)
      {
        List list = editPart.getChildren();
        for (Iterator i = list.iterator(); i.hasNext();)
        {
          Object child = i.next();
          if (child instanceof CompartmentEditPart)
          {
            List compartmentList = ((CompartmentEditPart) child).getChildren();
            for (Iterator it = compartmentList.iterator(); it.hasNext();)
            {
              Object obj = it.next();
              if (obj instanceof XSDAttributesForAnnotationEditPart)
              {
                XSDAttributesForAnnotationEditPart groups = (XSDAttributesForAnnotationEditPart) obj;
                List groupList = groups.getChildren();
                for (Iterator iter = groupList.iterator(); iter.hasNext();)
                {
                  Object groupChild = iter.next();
                  if (groupChild instanceof TargetConnectionSpacingFigureEditPart)
                  {
                    targetSpacesList.add(groupChild);
                  }
                  else if (groupChild instanceof AttributeGroupDefinitionEditPart)
                  {
                    getAttributeGroupEditParts((AttributeGroupDefinitionEditPart) groupChild);
                  }
                }
              }
            }
          }
        }
      }
      editPart = editPart.getParent();
    }

  }

  // Attribute Group related helper method
  
  protected List getAttributeGroupEditParts(AttributeGroupDefinitionEditPart attributeGroupEditPart)
  {
    List groupList = new ArrayList();
    List list = attributeGroupEditPart.getChildren();
    for (Iterator i = list.iterator(); i.hasNext();)
    {
      Object object = i.next();
      if (object instanceof TargetConnectionSpacingFigureEditPart)
      {
        targetSpacesList.add(object);
      }
      else if (object instanceof AttributeGroupDefinitionEditPart)
      {
        AttributeGroupDefinitionEditPart groupRef = (AttributeGroupDefinitionEditPart) object;
        List groupRefChildren = groupRef.getChildren();
        for (Iterator it = groupRefChildren.iterator(); it.hasNext();)
        {
          Object o = it.next();
          if (o instanceof TargetConnectionSpacingFigureEditPart)
          {
            targetSpacesList.add(o);
          }
          else if (o instanceof AttributeGroupDefinitionEditPart)
          {
            AttributeGroupDefinitionEditPart aGroup = (AttributeGroupDefinitionEditPart) o;
            groupList.add(aGroup);
            groupList.addAll(getAttributeGroupEditParts(aGroup));
          }
        }
      }
    }
    return groupList;
  }
}