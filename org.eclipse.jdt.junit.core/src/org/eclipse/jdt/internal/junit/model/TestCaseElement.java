/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Xavier Coulon <xcoulon@redhat.com> 
 *         - [JUnit] test method name cut off before '(' - https://bugs.eclipse.org/bugs/show_bug.cgi?id=102512
 *         - [JUnit] Add "Link with Editor" to JUnit view - https://bugs.eclipse.org/bugs/show_bug.cgi?id=372588 
 *******************************************************************************/

package org.eclipse.jdt.internal.junit.model;

import org.eclipse.jdt.junit.model.ITestCaseElement;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.junit.JUnitCorePlugin;

/**
 * 
 * @since 3.7 implements {@link IAdaptable}
 */
public class TestCaseElement extends TestElement implements ITestCaseElement, IAdaptable {

	private IMethod fJavaMethod= null;

	private boolean fJavaMethodResolved= false;

	private boolean fIgnored;

	public TestCaseElement(TestSuiteElement parent, String id, String testName) {
		super(parent, id, testName);
		Assert.isNotNull(parent);
	}

	/**
	 * @return the name of the Java Method associated with this {@link TestCaseElement}, ie, it
	 *         returns the valid java identifier part of the name (in particular, it removes the
	 *         brackets suffix for Parameterized JUnit tests).
	 * 
	 * 
	 */
	private String getJavaTestMethodName() {
		String testMethodName= getTestMethodName();
		for (int i= 0; i < testMethodName.length(); i++) {
			if (!Character.isJavaIdentifierPart(testMethodName.charAt(i))) {
				return testMethodName.substring(0, i);
			}
		}
		return testMethodName;
	}

	/**
	 * {@inheritDoc}
	 * @see org.eclipse.jdt.junit.model.ITestCaseElement#getTestMethodName()
	 * @see org.eclipse.jdt.internal.junit.runner.MessageIds#TEST_IDENTIFIER_MESSAGE_FORMAT
	 * @see org.eclipse.jdt.internal.junit.runner.MessageIds#IGNORED_TEST_PREFIX
	 */
	public String getTestMethodName() {
		String testName= getTestName();
		int index= testName.lastIndexOf('(');
		if (index > 0)
			return testName.substring(0, index);
		index= testName.indexOf('@');
		if (index > 0)
			return testName.substring(0, index);
		return testName;
	}

	/**
	 * Finds and returns the {@link IMethod} associated with this {@link TestCaseElement}.
	 * 
	 * @return the corresponding Java method element or null if not found.
	 * @since 3.7
	 */
	public IMethod getJavaMethod() {
		if (!fJavaMethodResolved) {
			try {
				final IType type= getJavaType();
				if (type != null) {
					final IMethod[] methods= type.getMethods();
					String testMethodName= getJavaTestMethodName();
					for (int i= 0; i < methods.length; i++) {
						if (methods[i].getElementName().equals(testMethodName)) {
							fJavaMethod= methods[i];
							return methods[i];
						}
					}
				}
				return null;
			} catch (JavaModelException e) {
				JUnitCorePlugin.log(e);
			} finally {
				fJavaMethodResolved= true;
			}
		}
		return fJavaMethod;
	}

	/**
	 * {@inheritDoc}
	 * @see org.eclipse.jdt.junit.model.ITestCaseElement#getTestClassName()
	 */
	public String getTestClassName() {
		return getClassName();
	}

	/*
	 * @see org.eclipse.jdt.internal.junit.model.TestElement#getTestResult(boolean)
	 * @since 3.6
	 */
	public Result getTestResult(boolean includeChildren) {
		if (fIgnored)
			return Result.IGNORED;
		else
			return super.getTestResult(includeChildren);
	}
	
	public void setIgnored(boolean ignored) {
		fIgnored= ignored;
	}

	public boolean isIgnored() {
		return fIgnored;
	}

	public String toString() {
		return "TestCase: " + getTestClassName() + "." + getTestMethodName() + " : " + super.toString(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	/**
	 * Provides support for converting this {@link TestCaseElement} into an {@link IMethod} when the
	 * given adapter class is {@link IJavaElement}.
	 * 
	 * @param adapter the class in which this {@link TestCaseElement} should be adapted.
	 * @return an object in the request type, or null if it could not be adapted.
	 * @since 3.7
	 */
	public Object getAdapter(Class adapter) {
		if (adapter != null && adapter.equals(IJavaElement.class)) {
			return getJavaMethod();
		}
		return null;
	}
}
