/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Xavier Coulon <xcoulon@redhat.com> -  [JUnit] Add "Link with Editor" to JUnit view - https://bugs.eclipse.org/bugs/show_bug.cgi?id=372588
 *******************************************************************************/

package org.eclipse.jdt.internal.junit.model;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.junit.model.ITestElement;
import org.eclipse.jdt.junit.model.ITestSuiteElement;

import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;


public class TestSuiteElement extends TestElement implements ITestSuiteElement, IAdaptable {

	private IJavaElement fJavaElement= null;

	private boolean fJavaElementResolved= false;

	private List/*<TestElement>*/ fChildren;
	private Status fChildrenStatus;

	public TestSuiteElement(TestSuiteElement parent, String id, String testName, int childrenCount) {
		super(parent, id, testName);
		fChildren= new ArrayList(childrenCount);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.junit.ITestElement#getTestResult()
	 */
	public Result getTestResult(boolean includeChildren) {
		if (includeChildren) {
			return getStatus().convertToResult();
		} else {
			return super.getStatus().convertToResult();
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.junit.ITestSuiteElement#getSuiteTypeName()
	 */
	public String getSuiteTypeName() {
		return getClassName();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.junit.model.ITestSuiteElement#getChildren()
	 */
	public ITestElement[] getChildren() {
		return (ITestElement[]) fChildren.toArray(new ITestElement[fChildren.size()]);
	}

	public void addChild(TestElement child) {
		fChildren.add(child);
	}

	public Status getStatus() {
		Status suiteStatus= getSuiteStatus();
		if (fChildrenStatus != null) {
			// must combine children and suite status here, since failures can occur e.g. in @AfterClass
			return Status.combineStatus(fChildrenStatus, suiteStatus);
		} else {
			return suiteStatus;
		}
	}

	private Status getCumulatedStatus() {
		TestElement[] children= (TestElement[]) fChildren.toArray(new TestElement[fChildren.size()]); // copy list to avoid concurreny problems
		if (children.length == 0)
			return getSuiteStatus();

		Status cumulated= children[0].getStatus();

		for (int i= 1; i < children.length; i++) {
			Status childStatus= children[i].getStatus();
			cumulated= Status.combineStatus(cumulated, childStatus);
		}
		// not necessary, see special code in Status.combineProgress()
//		if (suiteStatus.isErrorOrFailure() && cumulated.isNotRun())
//			return suiteStatus; //progress is Done if error in Suite and no children run
		return cumulated;
	}

	public Status getSuiteStatus() {
		return super.getStatus();
	}

	public void childChangedStatus(TestElement child, Status childStatus) {
		int childCount= fChildren.size();
		if (child == fChildren.get(0) && childStatus.isRunning()) {
			// is first child, and is running -> copy status
			internalSetChildrenStatus(childStatus);
			return;
		}
		TestElement lastChild= (TestElement) fChildren.get(childCount - 1);
		if (child == lastChild) {
			if (childStatus.isDone()) {
				// all children done, collect cumulative status
				internalSetChildrenStatus(getCumulatedStatus());
				return;
			}
			// go on (child could e.g. be a TestSuiteElement with RUNNING_FAILURE)

		} else 	if (! lastChild.getStatus().isNotRun()) {
			// child is not last, but last child has been run -> child has been rerun or is rerunning
			internalSetChildrenStatus(getCumulatedStatus());
			return;
		}

		// finally, set RUNNING_FAILURE/ERROR if child has failed but suite has not failed:
		if (childStatus.isFailure()) {
			if (fChildrenStatus == null || ! fChildrenStatus.isErrorOrFailure()) {
				internalSetChildrenStatus(Status.RUNNING_FAILURE);
				return;
			}
		} else if (childStatus.isError()) {
			if (fChildrenStatus == null || ! fChildrenStatus.isError()) {
				internalSetChildrenStatus(Status.RUNNING_ERROR);
				return;
			}
		}
	}

	private void internalSetChildrenStatus(Status status) {
		if (fChildrenStatus == status)
			return;

		if (status == Status.RUNNING) {
			if (fTime >= 0.0d) {
				// re-running child: ignore change
			} else {
				fTime= - System.currentTimeMillis() / 1000d;
			}
		} else if (status.convertToProgressState() == ProgressState.COMPLETED) {
			if (fTime < 0) { // assert ! Double.isNaN(fTime)
				double endTime= System.currentTimeMillis() / 1000d;
				fTime= endTime + fTime;
			}
		}

		fChildrenStatus= status;
		TestSuiteElement parent= getParent();
		if (parent != null)
			parent.childChangedStatus(this, getStatus());
	}

	public String toString() {
		return "TestSuite: " + getSuiteTypeName() + " : " + super.toString() + " (" + fChildren.size() + ")";   //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	/**
	 * Provides support for converting this {@link TestSuiteElement} into an {@link IType} (or an
	 * {@link IMethod} when this {@link TestSuiteElement} matches a JUnit Parameterized Test) when
	 * the given adapter class is {@link IJavaElement}.
	 * 
	 * @param adapter the class in which this {@link TestSuiteElement} should be adapted.
	 * @return an object in the request type, or null if it could not be adapted.
	 * @since 3.7
	 */
	public Object getAdapter(Class adapter) {
		if (adapter != null && adapter.equals(IJavaElement.class)) {
			return getJavaElement();
		}
		return null;
	}

	/**
	 * Returns the closest {@link IJavaElement} for the given {@link TestSuiteElement}, including
	 * with a work-around for Parameterized tests by looking at the child elements: if there's only
	 * one, return its Java {@link IMethod}. Otherwise, return the {@link IType}
	 * 
	 * @return the {@link IJavaElement} found for this {@link TestSuiteElement}.
	 * 
	 * @since 3.7
	 * @see TestElement#getJavaType()
	 */
	public IJavaElement getJavaElement() {
		if (!fJavaElementResolved) {
			// whatever happens, let's consider that the Java Type has been resolved, to make sure we don't come back here again for this TestSuitElement.
			fJavaElementResolved= true;
			fJavaElement= super.getJavaType();
			if (fJavaElement == null) {
				if (getChildren().length == 1 && getChildren()[0] instanceof TestCaseElement) {
					fJavaElement= ((TestCaseElement)getChildren()[0]).getJavaMethod();
				}
			}
		}
		return fJavaElement;
	}

	/**
	 * Returns the {@link IType} associated with the given {@link TestSuiteElement}, or uses the
	 * work-around in {@link TestSuiteElement#getJavaElement()} to retrieve the {@link IType}
	 * associated with the single child {@link TestCaseElement} if this {@link TestSuiteElement}
	 * matches a Parameterized JUnit Test.
	 * 
	 * @since 3.7
	 * @see TestElement#getJavaType()
	 */
	public IType getJavaType() {
		final IType javaType= super.getJavaType();
		if (javaType != null) {
			return javaType;
		} else if (getJavaElement() != null) {
			return (IType)fJavaElement.getAncestor(IJavaElement.TYPE);
		}
		return null;
	}
}
