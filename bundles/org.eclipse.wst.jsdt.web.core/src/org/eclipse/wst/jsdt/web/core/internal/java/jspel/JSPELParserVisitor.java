/*******************************************************************************
 * Copyright (c) 2005 BEA Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     BEA Systems - initial implementation
 *     
 *******************************************************************************/
/* Generated By:JJTree: Do not edit this line. JSPELParserVisitor.java */

package org.eclipse.wst.jsdt.web.core.internal.java.jspel;

public interface JSPELParserVisitor {
	public Object visit(SimpleNode node, Object data);

	public Object visit(ASTExpression node, Object data);

	public Object visit(ASTOrExpression node, Object data);

	public Object visit(ASTAndExpression node, Object data);

	public Object visit(ASTEqualityExpression node, Object data);

	public Object visit(ASTRelationalExpression node, Object data);

	public Object visit(ASTAddExpression node, Object data);

	public Object visit(ASTMultiplyExpression node, Object data);

	public Object visit(ASTChoiceExpression node, Object data);

	public Object visit(ASTUnaryExpression node, Object data);

	public Object visit(ASTValue node, Object data);

	public Object visit(ASTValuePrefix node, Object data);

	public Object visit(ASTValueSuffix node, Object data);

	public Object visit(ASTFunctionInvocation node, Object data);

	public Object visit(ASTLiteral node, Object data);
}