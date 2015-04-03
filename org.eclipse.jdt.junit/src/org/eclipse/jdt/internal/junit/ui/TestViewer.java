/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Brock Janiczak (brockj@tpg.com.au)
 *         - https://bugs.eclipse.org/bugs/show_bug.cgi?id=102236: [JUnit] display execution time next to each test
 *     Xavier Coulon <xcoulon@redhat.com>
 *         - https://bugs.eclipse.org/bugs/show_bug.cgi?id=102512 - [JUnit] test method name cut off before (
 *         - https://bugs.eclipse.org/bugs/show_bug.cgi?id=372588 - [JUnit] Add "Link with Editor" to JUnit view
 *******************************************************************************/

package org.eclipse.jdt.internal.junit.ui;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import org.eclipse.jdt.junit.model.ITestCaseElement;
import org.eclipse.jdt.junit.model.ITestElement;
import org.eclipse.jdt.junit.model.ITestElement.ProgressState;
import org.eclipse.jdt.junit.model.ITestElementContainer;
import org.eclipse.jdt.junit.model.ITestSuiteElement;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.TableItem;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.PageBook;

import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.junit.launcher.ITestFinder;
import org.eclipse.jdt.internal.junit.model.TestCaseElement;
import org.eclipse.jdt.internal.junit.model.TestElement;
import org.eclipse.jdt.internal.junit.model.TestElement.Status;
import org.eclipse.jdt.internal.junit.model.TestRoot;
import org.eclipse.jdt.internal.junit.model.TestRunSession;
import org.eclipse.jdt.internal.junit.model.TestSuiteElement;

import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.viewsupport.ColoringLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.SelectionProviderMediator;


public class TestViewer implements ISelectionListener {
	private final class TestSelectionListener implements ISelectionChangedListener {
		public void selectionChanged(SelectionChangedEvent event) {
			handleSelected();
		}
	}

	private final class TestOpenListener extends SelectionAdapter {
		@Override
		public void widgetDefaultSelected(SelectionEvent e) {
			handleDefaultSelected();
		}
	}

	private final class FailuresOnlyFilter extends ViewerFilter {
		@Override
		public boolean select(Viewer viewer, Object parentElement, Object element) {
			return select(((TestElement) element));
		}

		public boolean select(TestElement testElement) {
			Status status= testElement.getStatus();
			if (status.isErrorOrFailure())
				return true;
			else
				return ! fTestRunSession.isRunning() && status == Status.RUNNING;  // rerunning
		}
	}

	private static class ReverseList<E> extends AbstractList<E> {
		private final List<E> fList;
		public ReverseList(List<E> list) {
			fList= list;
		}
		@Override
		public E get(int index) {
			return fList.get(fList.size() - index - 1);
		}
		@Override
		public int size() {
			return fList.size();
		}
	}

	private class ExpandAllAction extends Action {
		public ExpandAllAction() {
			setText(JUnitMessages.ExpandAllAction_text);
			setToolTipText(JUnitMessages.ExpandAllAction_tooltip);
		}

		@Override
		public void run(){
			fTreeViewer.expandAll();
		}
	}

	private final FailuresOnlyFilter fFailuresOnlyFilter= new FailuresOnlyFilter();

	private final TestRunnerViewPart fTestRunnerPart;
	private final Clipboard fClipboard;

	private PageBook fViewerbook;
	private TreeViewer fTreeViewer;
	private TestSessionTreeContentProvider fTreeContentProvider;
	private TestSessionLabelProvider fTreeLabelProvider;
	private TableViewer fTableViewer;
	private TestSessionTableContentProvider fTableContentProvider;
	private TestSessionLabelProvider fTableLabelProvider;
	private SelectionProviderMediator fSelectionProvider;

	private int fLayoutMode;
	private boolean fTreeHasFilter;
	private boolean fTableHasFilter;

	private TestRunSession fTestRunSession;

	private boolean fTreeNeedsRefresh;
	private boolean fTableNeedsRefresh;
	private HashSet<TestElement> fNeedUpdate;
	private TestCaseElement fAutoScrollTarget;

	private LinkedList<TestSuiteElement> fAutoClose;
	private HashSet<TestSuiteElement> fAutoExpand;


	public TestViewer(Composite parent, Clipboard clipboard, TestRunnerViewPart runner) {
		fTestRunnerPart= runner;
		fClipboard= clipboard;

		fLayoutMode= TestRunnerViewPart.LAYOUT_HIERARCHICAL;

		createTestViewers(parent);

		registerViewersRefresh();

		initContextMenu();
	}

	private void createTestViewers(Composite parent) {
		fViewerbook= new PageBook(parent, SWT.NULL);

		fTreeViewer= new TreeViewer(fViewerbook, SWT.V_SCROLL | SWT.SINGLE);
		fTreeViewer.setUseHashlookup(true);
		fTreeContentProvider= new TestSessionTreeContentProvider();
		fTreeViewer.setContentProvider(fTreeContentProvider);
		fTreeLabelProvider= new TestSessionLabelProvider(fTestRunnerPart, TestRunnerViewPart.LAYOUT_HIERARCHICAL);
		fTreeViewer.setLabelProvider(new ColoringLabelProvider(fTreeLabelProvider));

		fTableViewer= new TableViewer(fViewerbook, SWT.V_SCROLL | SWT.H_SCROLL | SWT.SINGLE);
		fTableViewer.setUseHashlookup(true);
		fTableContentProvider= new TestSessionTableContentProvider();
		fTableViewer.setContentProvider(fTableContentProvider);
		fTableLabelProvider= new TestSessionLabelProvider(fTestRunnerPart, TestRunnerViewPart.LAYOUT_FLAT);
		fTableViewer.setLabelProvider(new ColoringLabelProvider(fTableLabelProvider));

		fSelectionProvider= new SelectionProviderMediator(new StructuredViewer[] { fTreeViewer, fTableViewer }, fTreeViewer);
		fSelectionProvider.addSelectionChangedListener(new TestSelectionListener());
		TestOpenListener testOpenListener= new TestOpenListener();
		fTreeViewer.getTree().addSelectionListener(testOpenListener);
		fTableViewer.getTable().addSelectionListener(testOpenListener);

		fTestRunnerPart.getSite().setSelectionProvider(fSelectionProvider);

		fViewerbook.showPage(fTreeViewer.getTree());
	}

	private void initContextMenu() {
		MenuManager menuMgr= new MenuManager("#PopupMenu"); //$NON-NLS-1$
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				handleMenuAboutToShow(manager);
			}
		});
		fTestRunnerPart.getSite().registerContextMenu(menuMgr, fSelectionProvider);
		Menu menu= menuMgr.createContextMenu(fViewerbook);
		fTreeViewer.getTree().setMenu(menu);
		fTableViewer.getTable().setMenu(menu);
	}


	void handleMenuAboutToShow(IMenuManager manager) {
		IStructuredSelection selection= (IStructuredSelection) fSelectionProvider.getSelection();
		if (! selection.isEmpty()) {
			TestElement testElement= (TestElement) selection.getFirstElement();

			String testLabel= testElement.getTestName();
			String className= testElement.getClassName();
			if (testElement instanceof TestSuiteElement) {
				manager.add(new OpenTestAction(fTestRunnerPart, testLabel));
				manager.add(new Separator());
				if (!fTestRunnerPart.lastLaunchIsKeptAlive()) {
					IType testType= findTestClass(testElement);
					if (testType != null) {
						String qualifiedName= testType.getFullyQualifiedName();
						String testName= qualifiedName.equals(className) ? null : testElement.getTestName();
						manager.add(new RerunAction(JUnitMessages.RerunAction_label_run, fTestRunnerPart, testElement.getId(), qualifiedName, testName, ILaunchManager.RUN_MODE));
						manager.add(new RerunAction(JUnitMessages.RerunAction_label_debug, fTestRunnerPart, testElement.getId(), qualifiedName, testName, ILaunchManager.DEBUG_MODE));
					}
				}
			} else {
				TestCaseElement testCaseElement= (TestCaseElement) testElement;
				String testMethodName= testCaseElement.getTestMethodName();
				manager.add(new OpenTestAction(fTestRunnerPart, testCaseElement));
				manager.add(new Separator());
				if (fTestRunnerPart.lastLaunchIsKeptAlive()) {
					manager.add(new RerunAction(JUnitMessages.RerunAction_label_rerun, fTestRunnerPart, testElement.getId(), className, testMethodName, ILaunchManager.RUN_MODE));

				} else {
					manager.add(new RerunAction(JUnitMessages.RerunAction_label_run, fTestRunnerPart, testElement.getId(), className, testMethodName, ILaunchManager.RUN_MODE));
					manager.add(new RerunAction(JUnitMessages.RerunAction_label_debug, fTestRunnerPart, testElement.getId(), className, testMethodName, ILaunchManager.DEBUG_MODE));
				}
			}
			if (fLayoutMode == TestRunnerViewPart.LAYOUT_HIERARCHICAL) {
				manager.add(new Separator());
				manager.add(new ExpandAllAction());
			}

		}
		if (fTestRunSession != null && fTestRunSession.getFailureCount() + fTestRunSession.getErrorCount() > 0) {
			if (fLayoutMode != TestRunnerViewPart.LAYOUT_HIERARCHICAL)
				manager.add(new Separator());
			manager.add(new CopyFailureListAction(fTestRunnerPart, fClipboard));
		}
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS + "-end")); //$NON-NLS-1$
	}

	/*
	 * Returns the element's test class or the next container's test class, which exists, and for which ITestFinder.isTest() is true.
	 */
	private IType findTestClass(ITestElement element) {
		ITestFinder finder= ((TestRunSession)element.getTestRunSession()).getTestRunnerKind().getFinder();
		IJavaProject project= fTestRunnerPart.getLaunchedProject();
		if (project == null)
			return null;
		ITestElement current= element;
		while (current != null) {
			try {
				String className= null;
				if (current instanceof TestRoot) {
					ILaunchConfiguration configuration= ((TestRunSession)element.getTestRunSession()).getLaunch().getLaunchConfiguration();
					className= configuration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, (String)null);
				} else if (current instanceof TestElement) {
					className= ((TestElement)current).getClassName();
				}
				if (className != null) {
					IType type= project.findType(className);
					if (type != null && finder.isTest(type))
						return type;
				}
			} catch (JavaModelException e) {
				// fall through
			} catch (CoreException e) {
				// fall through
			}
			current= current instanceof TestElement ? ((TestElement)current).getParent() : null;
		}
		return null;
	}

	public Control getTestViewerControl() {
		return fViewerbook;
	}

	public synchronized void registerActiveSession(TestRunSession testRunSession) {
		fTestRunSession= testRunSession;
		registerAutoScrollTarget(null);
		registerViewersRefresh();
	}

	void handleDefaultSelected() {
		IStructuredSelection selection= (IStructuredSelection) fSelectionProvider.getSelection();
		if (selection.size() != 1)
			return;

		TestElement testElement= (TestElement) selection.getFirstElement();

		OpenTestAction action;
		if (testElement instanceof TestSuiteElement) {
			String testName= testElement.getTestName();
			ITestElement[] children= ((TestSuiteElement) testElement).getChildren();
			if (testName.startsWith("[") && testName.endsWith("]") //$NON-NLS-1$ //$NON-NLS-2$
					&& children.length > 0 && children[0] instanceof TestCaseElement) {
				// a group of parameterized tests
				action= new OpenTestAction(fTestRunnerPart, (TestCaseElement) children[0]);
			} else {
				action= new OpenTestAction(fTestRunnerPart, testName);
			}
		} else if (testElement instanceof TestCaseElement) {
			TestCaseElement testCase= (TestCaseElement)testElement;
			action= new OpenTestAction(fTestRunnerPart, testCase);
		} else {
			throw new IllegalStateException(String.valueOf(testElement));
		}

		if (action.isEnabled())
			action.run();
	}

	private void handleSelected() {
		IStructuredSelection selection= (IStructuredSelection) fSelectionProvider.getSelection();
		TestElement testElement= null;
		if (selection.size() == 1) {
			testElement= (TestElement) selection.getFirstElement();
		}
		fTestRunnerPart.handleTestSelected(testElement);
		// if LinkWithEditor is active, reveal the JavaEditor and select the java type or method
		// matching the selected test element, even if the JavaEditor is opened by not active.
		if (fTestRunnerPart.isLinkWithEditorActive()) {
			handleTestElementSelected(testElement);
		}
	}

	public synchronized void setShowTime(boolean showTime) {
		try {
			fViewerbook.setRedraw(false);
			fTreeLabelProvider.setShowTime(showTime);
			fTableLabelProvider.setShowTime(showTime);
		} finally {
			fViewerbook.setRedraw(true);
		}
	}

	public synchronized void setShowFailuresOnly(boolean failuresOnly, int layoutMode) {
		/*
		 * Management of fTreeViewer and fTableViewer
		 * ******************************************
		 * - invisible viewer is updated on registerViewerUpdate unless its f*NeedsRefresh is true
		 * - invisible viewer is not refreshed upfront
		 * - on layout change, new viewer is refreshed if necessary
		 * - filter only applies to "current" layout mode / viewer
		 */
		try {
			fViewerbook.setRedraw(false);

			IStructuredSelection selection= null;
			boolean switchLayout= layoutMode != fLayoutMode;
			if (switchLayout) {
				selection= (IStructuredSelection) fSelectionProvider.getSelection();
				if (layoutMode == TestRunnerViewPart.LAYOUT_HIERARCHICAL) {
					if (fTreeNeedsRefresh) {
						clearUpdateAndExpansion();
					}
				} else {
					if (fTableNeedsRefresh) {
						clearUpdateAndExpansion();
					}
				}
				fLayoutMode= layoutMode;
				fViewerbook.showPage(getActiveViewer().getControl());
			}

			//avoid realizing all TableItems, especially in flat mode!
			StructuredViewer viewer= getActiveViewer();
			if (failuresOnly) {
				if (! getActiveViewerHasFilter()) {
					setActiveViewerNeedsRefresh(true);
					setActiveViewerHasFilter(true);
					viewer.setInput(null);
					viewer.addFilter(fFailuresOnlyFilter);
				}

			} else {
				if (getActiveViewerHasFilter()) {
					setActiveViewerNeedsRefresh(true);
					setActiveViewerHasFilter(false);
					viewer.setInput(null);
					viewer.removeFilter(fFailuresOnlyFilter);
				}
			}
			processChangesInUI();

			if (selection != null) {
				// workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=125708
				// (ITreeSelection not adapted if TreePaths changed):
				StructuredSelection flatSelection= new StructuredSelection(selection.toList());
				fSelectionProvider.setSelection(flatSelection, true);
			}

		} finally {
			fViewerbook.setRedraw(true);
		}
	}

	private boolean getActiveViewerHasFilter() {
		if (fLayoutMode == TestRunnerViewPart.LAYOUT_HIERARCHICAL)
			return fTreeHasFilter;
		else
			return fTableHasFilter;
	}

	private void setActiveViewerHasFilter(boolean filter) {
		if (fLayoutMode == TestRunnerViewPart.LAYOUT_HIERARCHICAL)
			fTreeHasFilter= filter;
		else
			fTableHasFilter= filter;
	}

	private StructuredViewer getActiveViewer() {
		if (fLayoutMode == TestRunnerViewPart.LAYOUT_HIERARCHICAL)
			return fTreeViewer;
		else
			return fTableViewer;
	}

	private boolean getActiveViewerNeedsRefresh() {
		if (fLayoutMode == TestRunnerViewPart.LAYOUT_HIERARCHICAL)
			return fTreeNeedsRefresh;
		else
			return fTableNeedsRefresh;
	}

	private void setActiveViewerNeedsRefresh(boolean needsRefresh) {
		if (fLayoutMode == TestRunnerViewPart.LAYOUT_HIERARCHICAL)
			fTreeNeedsRefresh= needsRefresh;
		else
			fTableNeedsRefresh= needsRefresh;
	}

	/**
	 * To be called periodically by the TestRunnerViewPart (in the UI thread).
	 */
	public void processChangesInUI() {
		TestRoot testRoot;
		if (fTestRunSession == null) {
			registerViewersRefresh();
			fTreeNeedsRefresh= false;
			fTableNeedsRefresh= false;
			fTreeViewer.setInput(null);
			fTableViewer.setInput(null);
			return;
		}

		testRoot= fTestRunSession.getTestRoot();

		StructuredViewer viewer= getActiveViewer();
		if (getActiveViewerNeedsRefresh()) {
			clearUpdateAndExpansion();
			setActiveViewerNeedsRefresh(false);
			viewer.setInput(testRoot);

		} else {
			Object[] toUpdate;
			synchronized (this) {
				toUpdate= fNeedUpdate.toArray();
				fNeedUpdate.clear();
			}
			if (! fTreeNeedsRefresh && toUpdate.length > 0) {
				if (fTreeHasFilter)
					for (Object element : toUpdate)
						updateElementInTree((TestElement) element);
				else {
					HashSet<Object> toUpdateWithParents= new HashSet<Object>();
					toUpdateWithParents.addAll(Arrays.asList(toUpdate));
					for (Object element : toUpdate) {
						TestElement parent= ((TestElement) element).getParent();
						while (parent != null) {
							toUpdateWithParents.add(parent);
							parent= parent.getParent();
						}
					}
					fTreeViewer.update(toUpdateWithParents.toArray(), null);
				}
			}
			if (! fTableNeedsRefresh && toUpdate.length > 0) {
				if (fTableHasFilter)
					for (Object element : toUpdate)
						updateElementInTable((TestElement) element);
				else
					fTableViewer.update(toUpdate, null);
			}
		}
		autoScrollInUI();
	}

	private void updateElementInTree(final TestElement testElement) {
		if (isShown(testElement)) {
			updateShownElementInTree(testElement);
		} else {
			TestElement current= testElement;
			do {
				if (fTreeViewer.testFindItem(current) != null)
					fTreeViewer.remove(current);
				current= current.getParent();
			} while (! (current instanceof TestRoot) && ! isShown(current));

			while (current != null && ! (current instanceof TestRoot)) {
				fTreeViewer.update(current, null);
				current= current.getParent();
			}
		}
	}

	private void updateShownElementInTree(TestElement testElement) {
		if (testElement == null || testElement instanceof TestRoot) // paranoia null check
			return;

		TestSuiteElement parent= testElement.getParent();
		updateShownElementInTree(parent); // make sure parent is shown and up-to-date

		if (fTreeViewer.testFindItem(testElement) == null) {
			fTreeViewer.add(parent, testElement); // if not yet in tree: add
		} else {
			fTreeViewer.update(testElement, null); // if in tree: update
		}
	}

	private void updateElementInTable(TestElement element) {
		if (isShown(element)) {
			if (fTableViewer.testFindItem(element) == null) {
				TestElement previous= getNextFailure(element, false);
				int insertionIndex= -1;
				if (previous != null) {
					TableItem item= (TableItem) fTableViewer.testFindItem(previous);
					if (item != null)
						insertionIndex= fTableViewer.getTable().indexOf(item);
				}
				fTableViewer.insert(element, insertionIndex);
			} else  {
				fTableViewer.update(element, null);
			}
		} else {
			fTableViewer.remove(element);
		}
	}

	private boolean isShown(TestElement current) {
		return fFailuresOnlyFilter.select(current);
	}

	private void autoScrollInUI() {
		if (! fTestRunnerPart.isAutoScroll()) {
			clearAutoExpand();
			fAutoClose.clear();
			return;
		}

		if (fLayoutMode == TestRunnerViewPart.LAYOUT_FLAT) {
			if (fAutoScrollTarget != null)
				fTableViewer.reveal(fAutoScrollTarget);
			return;
		}

		synchronized (this) {
			for (TestSuiteElement suite : fAutoExpand) {
				fTreeViewer.setExpandedState(suite, true);
			}
			clearAutoExpand();
		}

		TestCaseElement current= fAutoScrollTarget;
		fAutoScrollTarget= null;

		TestSuiteElement parent= current == null ? null : (TestSuiteElement) fTreeContentProvider.getParent(current);
		if (fAutoClose.isEmpty() || ! fAutoClose.getLast().equals(parent)) {
			// we're in a new branch, so let's close old OK branches:
			for (ListIterator<TestSuiteElement> iter= fAutoClose.listIterator(fAutoClose.size()); iter.hasPrevious();) {
				TestSuiteElement previousAutoOpened= iter.previous();
				if (previousAutoOpened.equals(parent))
					break;

				if (previousAutoOpened.getStatus() == TestElement.Status.OK) {
					// auto-opened the element, and all children are OK -> auto close
					iter.remove();
					fTreeViewer.collapseToLevel(previousAutoOpened, AbstractTreeViewer.ALL_LEVELS);
				}
			}

			while (parent != null && ! fTestRunSession.getTestRoot().equals(parent) && fTreeViewer.getExpandedState(parent) == false) {
				fAutoClose.add(parent); // add to auto-opened elements -> close later if STATUS_OK
				parent= (TestSuiteElement) fTreeContentProvider.getParent(parent);
			}
		}
		if (current != null)
			fTreeViewer.reveal(current);
	}

	/**
	 * Sets the current selection from the given {@link IEditorPart} (if it is a
	 * {@link CompilationUnitEditor}) and its selection.
	 * 
	 * @param editor the selected Java Element in the active Compilation Unit Editor
	 * 
	 * @since 3.8
	 */
	public void setSelection(final IEditorPart editor) {
		if (editor != null) {
			final IJavaElement selectedJavaElement= getSelectedJavaElementInEditor(editor);
			setSelection(selectedJavaElement);
		}
	}
	
	/**
	 * Sets the current selection from the given {@link IJavaElement} if it matches an
	 * {@link ITestCaseElement} and updates the LinkWithEditorAction image in the associated
	 * {@link TestRunnerViewPart}.
	 * 
	 * @param activeJavaElement the selected Java Element (or null) in the active
	 *            {@link IWorkbenchPart}
	 * 
	 */
	private void setSelection(final IJavaElement activeJavaElement) {
		final ITestElement activeTestCaseElement= findClosestTestElement(activeJavaElement, getCurrentViewerSelection());
		if (activeTestCaseElement != null) {
			// update the current selection in the viewer if it does not match with the java element selected in the given part (if it's not the parent TestViewerPart)
			final Object currentSelection= getCurrentViewerSelection();
			if (!activeTestCaseElement.equals(currentSelection)) {
				final IStructuredSelection selection= new StructuredSelection(activeTestCaseElement);
				fSelectionProvider.setSelection(selection, true);
			}
			// ensure link with editor is in 'synced' mode
			fTestRunnerPart.setLinkingWithEditorInSync(true);
		}
		else {
			// selection is out-of-sync: show a different icon on the button.
			fTestRunnerPart.setLinkingWithEditorInSync(false);
		}
	}

	/**
	 * @return the current {@link ITestElement} in the JUnit Viewer (provided by the underlying
	 *         selection provider), or {@code null} if the kind of selection is not an
	 *         {@link ITreeSelection} nor an {@link IStructuredSelection}.
	 */
	private ITestElement getCurrentViewerSelection() {
		final ISelection currentSelection= fSelectionProvider.getSelection();
		if (currentSelection instanceof ITreeSelection) {
			return (ITestElement) ((ITreeSelection) currentSelection).getFirstElement();
		} else if (currentSelection instanceof IStructuredSelection) {
			return (ITestElement) ((IStructuredSelection) currentSelection).getFirstElement();
		}
		return null;
	}

	/**
	 * Finds the closest {@link ITestElement} for the given {@link IJavaElement}. The search for the
	 * closest element starts from the {@link ITestElement} currently selected, to avoid an annoying
	 * selection shift if the {@link ITestSuiteElement} was ran multiple time in the test session.
	 * 
	 * @param javaElement the {@link IJavaElement} associated with the {@link ITestElement} to find.
	 * @param currentTestElement the current {@link ITestElement} selected in the TestViewer
	 * @return the {@link ITestElement} or null if it could not be found.
	 */
	private ITestElement findClosestTestElement(final IJavaElement javaElement, final ITestElement currentTestElement) {
		// skip if JUnit is still running or if no Java element was selected
		if (fTestRunnerPart.getCurrentProgressState() != ProgressState.COMPLETED || javaElement == null) {
			return null;
		}
		// if the current selection already matches the given java element
		if (matches(currentTestElement, javaElement)) {
			return currentTestElement;
		}
		// if current selection is a TestCaseElement / Java method, move to the parent
		ITestElementContainer currentElementContainer= getTestElementContainer(currentTestElement);
		// now, look in the current test container, or move to parent container until root
		search:
		while (currentElementContainer != null) {
			switch (javaElement.getElementType()) {
				case IJavaElement.METHOD:
					final ITestCaseElement resultTestCaseElement= findTestCaseElement(currentElementContainer, (IMethod) javaElement);
					if (resultTestCaseElement != null) {
						return resultTestCaseElement;
					}
					break;
				case IJavaElement.TYPE:
				case IJavaElement.COMPILATION_UNIT:
					final ITestSuiteElement resultTestSuiteElement= findTestSuiteElement(currentElementContainer, javaElement);
					if (resultTestSuiteElement != null) {
						return resultTestSuiteElement;
					}
					break;
				default:
					// no result will be provided if the user selects anything else, including package declaration, imports and fields.
					break search;
			}
			currentElementContainer= currentElementContainer.getParentContainer();
		}
		return null;
	}

	/**
	 * Compares the given {@link IJavaElement} with the given {@link ITestElement} and checks if
	 * they match:
	 * <ul>
	 * <li>If the {@link ITestElement} is an {@link ITestSuiteElement}, the valid
	 * {@link IJavaElement} are an {@link IType} or an {@link ICompilationUnit}.</li>
	 * <li>If the {@link ITestElement} is an {@link TestCaseElement}, the valid {@link IJavaElement}
	 * is an {@link IMethod}.</li>
	 * </ul>
	 * @param currentTestElement the current {@link ITestElement} to analyze
	 * @param expectedJavaElement the expected {@link IJavaElement} to match
	 * 
	 * @return <code>true</code> if the given arguments match, <code>false</code> otherwise.
	 */
	private boolean matches(final ITestElement currentTestElement, final IJavaElement expectedJavaElement) {
		if (currentTestElement instanceof TestCaseElement) {
			final TestCaseElement currentTestCaseElement= (TestCaseElement) currentTestElement;
			return currentTestCaseElement.getJavaMethod() != null
					&& currentTestCaseElement.getJavaMethod().equals(expectedJavaElement);
		} else if (currentTestElement instanceof TestSuiteElement) {
			final TestSuiteElement currentTestSuiteElement= (TestSuiteElement) currentTestElement;
			return currentTestSuiteElement.getJavaType() != null
					&& (currentTestSuiteElement.getJavaType().equals(expectedJavaElement) || currentTestSuiteElement.getJavaType().getCompilationUnit().equals(expectedJavaElement));
		}
		return false;
	}

	/**
	 * Finds and returns the closest {@link ITestElementContainer} for the given
	 * {@link ITestElement}.
	 * 
	 * @param testElement the {@link ITestElement} to analyze
	 * @return the given {@link ITestElement} is already an {@link ITestElementContainer}, or the
	 *         {@link ITestElement#getParentContainer()} if the given {@link ITestElement} is not
	 *         <code>null</code>, or the current {@link TestRunSession}.
	 */
	private ITestElementContainer getTestElementContainer(final ITestElement testElement) {
		if (testElement instanceof ITestElementContainer) {
			return (ITestElementContainer) testElement;
		} else if (testElement != null) {
			return testElement.getParentContainer();
		}
		return fTestRunSession;
	}

	/**
	 * Finds the {@link ITestCaseElement} with the given test class name and test method name in the
	 * given {@link ITestSuiteElement}
	 * 
	 * @param parentElement the parent Test Suite
	 * @param javaMethod the java method corresponding to the {@link ITestCaseElement} to find
	 * 
	 * @return the {@link ITestCaseElement} or null if it could not be found.
	 */
	private ITestCaseElement findTestCaseElement(final ITestElementContainer parentElement, final IMethod javaMethod) {
		final IType javaType= (IType) javaMethod.getAncestor(IJavaElement.TYPE);
		final String testClassName= javaType.getFullyQualifiedName();
		final String testMethodName= javaMethod.getElementName();
		for (ITestElement childElement : parentElement.getChildren()) {
			if (childElement instanceof ITestCaseElement) {
				final TestCaseElement testCaseElement= (TestCaseElement)childElement;
				if (testCaseElement.getJavaType() != null && testCaseElement.getJavaType().getFullyQualifiedName().equals(testClassName) && testCaseElement.getJavaMethod() != null
						&& testCaseElement.getJavaMethod() != null && testCaseElement.getJavaMethod().getElementName().equals(testMethodName)) {
					return testCaseElement;
				}
			} else if (childElement instanceof ITestSuiteElement) {
				final ITestCaseElement localResult= findTestCaseElement((ITestSuiteElement) childElement, javaMethod);
				if (localResult != null) {
					return localResult;
				}
			}
		}
		return null;
	}

	/**
	 * Finds the {@link ITestSuiteElement} with the given test class name matching the given {@link IJavaElement} name in the given
	 * {@link ITestElementContainer}
	 * 
	 * @param parentElement the parent Test Suite
	 * @param javaElement the {@link IType} or {@link ICompilationUnit} corresponding to the {@link ITestSuiteElement} to find
	 * 
	 * @return the {@link ITestSuiteElement} or null if it could not be found.
	 */
	private ITestSuiteElement findTestSuiteElement(final ITestElementContainer parentElement, final IJavaElement javaElement) {
		final String testClassName= getFullyQualifiedName(javaElement);
		if (parentElement instanceof ITestSuiteElement && ((ITestSuiteElement) parentElement).getSuiteTypeName().equals(testClassName)) {
			return (ITestSuiteElement) parentElement;
		}
		for (ITestElement childElement : parentElement.getChildren()) {
			if (childElement instanceof ITestSuiteElement) {
				final ITestSuiteElement childTestSuite= (ITestSuiteElement)childElement;
				if (childTestSuite.getSuiteTypeName().equals(testClassName)) {
					return childTestSuite;
				}
				final ITestSuiteElement matchingNestedTestSuiteElement= findTestSuiteElement(childTestSuite, javaElement);
				if (matchingNestedTestSuiteElement != null) {
					return matchingNestedTestSuiteElement;
				}
			}
		}
		return null;
	}

	/**
	 * @return the fully qualified name of the given {@link IJavaElement} if it is an {@link IType} or an {@link ICompilationUnit}, <code>null</code> otherwise.
	 * @param javaElement the {@link IJavaElement} to analyze.
	 */
	private String getFullyQualifiedName(final IJavaElement javaElement) {
		if (javaElement == null) {
			return null;
		} else if (javaElement.getElementType() == IJavaElement.TYPE) {
			return ((IType) javaElement).getFullyQualifiedName();
		} else if (javaElement.getElementType() == IJavaElement.COMPILATION_UNIT) {
			return ((ICompilationUnit) javaElement).findPrimaryType().getFullyQualifiedName();
		}
		return null;
	}

	public void selectFirstFailure() {
		TestCaseElement firstFailure= getNextChildFailure(fTestRunSession.getTestRoot(), true);
		if (firstFailure != null)
			getActiveViewer().setSelection(new StructuredSelection(firstFailure), true);
	}

	public void selectFailure(boolean showNext) {
		IStructuredSelection selection= (IStructuredSelection) getActiveViewer().getSelection();
		TestElement selected= (TestElement) selection.getFirstElement();
		TestElement next;

		if (selected == null) {
			next= getNextChildFailure(fTestRunSession.getTestRoot(), showNext);
		} else {
			next= getNextFailure(selected, showNext);
		}

		if (next != null)
			getActiveViewer().setSelection(new StructuredSelection(next), true);
	}

	private TestElement getNextFailure(TestElement selected, boolean showNext) {
		if (selected instanceof TestSuiteElement) {
			TestElement nextChild= getNextChildFailure((TestSuiteElement) selected, showNext);
			if (nextChild != null)
				return nextChild;
		}
		return getNextFailureSibling(selected, showNext);
	}

	private TestCaseElement getNextFailureSibling(TestElement current, boolean showNext) {
		TestSuiteElement parent= current.getParent();
		if (parent == null)
			return null;

		List<ITestElement> siblings= Arrays.asList(parent.getChildren());
		if (! showNext)
			siblings= new ReverseList<ITestElement>(siblings);

		int nextIndex= siblings.indexOf(current) + 1;
		for (int i= nextIndex; i < siblings.size(); i++) {
			TestElement sibling= (TestElement) siblings.get(i);
			if (sibling.getStatus().isErrorOrFailure()) {
				if (sibling instanceof TestCaseElement) {
					return (TestCaseElement) sibling;
				} else {
					return getNextChildFailure((TestSuiteElement) sibling, showNext);
				}
			}
		}
		return getNextFailureSibling(parent, showNext);
	}

	private TestCaseElement getNextChildFailure(TestSuiteElement root, boolean showNext) {
		List<ITestElement> children= Arrays.asList(root.getChildren());
		if (! showNext)
			children= new ReverseList<ITestElement>(children);
		for (int i= 0; i < children.size(); i++) {
			TestElement child= (TestElement) children.get(i);
			if (child.getStatus().isErrorOrFailure()) {
				if (child instanceof TestCaseElement) {
					return (TestCaseElement) child;
				} else {
					return getNextChildFailure((TestSuiteElement) child, showNext);
				}
			}
		}
		return null;
	}

	public synchronized void registerViewersRefresh() {
		fTreeNeedsRefresh= true;
		fTableNeedsRefresh= true;
		clearUpdateAndExpansion();
	}

	private void clearUpdateAndExpansion() {
		fNeedUpdate= new LinkedHashSet<TestElement>();
		fAutoClose= new LinkedList<TestSuiteElement>();
		fAutoExpand= new HashSet<TestSuiteElement>();
	}

	/**
	 * @param testElement the added test
	 */
	public synchronized void registerTestAdded(TestElement testElement) {
		//TODO: performance: would only need to refresh parent of added element
		fTreeNeedsRefresh= true;
		fTableNeedsRefresh= true;
	}

	public synchronized void registerViewerUpdate(final TestElement testElement) {
		fNeedUpdate.add(testElement);
	}

	private synchronized void clearAutoExpand() {
		fAutoExpand.clear();
	}

	public void registerAutoScrollTarget(TestCaseElement testCaseElement) {
		fAutoScrollTarget= testCaseElement;
	}

	public synchronized void registerFailedForAutoScroll(TestElement testElement) {
		TestSuiteElement parent= (TestSuiteElement) fTreeContentProvider.getParent(testElement);
		if (parent != null)
			fAutoExpand.add(parent);
	}

	public void expandFirstLevel() {
		fTreeViewer.expandToLevel(2);
	}

	/**
	 * Reacts to a selection change in the active {@link IWorkbenchPart} (or when another part
	 * received the focus).
	 * 
	 * @param part the {@link IWorkbenchPart} in which the selection change occurred
	 * @param selection the selection in the given part
	 * @since 3.8
	 */
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		if (part instanceof IEditorPart) {
			setSelection((IEditorPart)part);
		}
		// any ITreeSelection (eg: Project Explorer, Package Explorer, Content Outline) should be considered, too.
		else if (selection instanceof ITreeSelection) {
			final ITreeSelection treeSelection= (ITreeSelection) selection;
			if (treeSelection.size() == 1 && treeSelection.getFirstElement() instanceof IJavaElement) {
				setSelection((IJavaElement) treeSelection.getFirstElement());
			}
		}
	}

	/**
	 * @return the selected {@link IJavaElement} in the current editor if it is a {@link CompilationUnitEditor}, null otherwise.
	 * @param editor the editor
	 */
	private IJavaElement getSelectedJavaElementInEditor(final IEditorPart editor) {
		if (editor instanceof JavaEditor) {
			final JavaEditor javaEditor= (JavaEditor) editor;
			try {
				final IJavaElement inputJavaElement= JavaUI.getEditorInputJavaElement(editor.getEditorInput());
				// when the editor is opened on a .class file, not a .java source file
				if (inputJavaElement.getElementType() == IJavaElement.CLASS_FILE) {
					final IClassFile classFile= (IClassFile) inputJavaElement;
					return classFile.getType();
				}
				final ITextSelection selection= (ITextSelection) javaEditor.getSelectionProvider().getSelection();
				final ICompilationUnit compilationUnit= (ICompilationUnit)inputJavaElement.getAncestor(IJavaElement.COMPILATION_UNIT);
				final IJavaElement selectedElement= compilationUnit.getElementAt(selection.getOffset());
				return selectedElement;
			} catch (JavaModelException e) {
				JUnitPlugin.log(e);
			}
		}
		return null;
	}

	/**
	 * Handles the case when a {@link TestCaseElement} has been selected, unless there's a
	 * {@link TestRunSession} in progress.
	 * 
	 * @param testElement the new selected {@link TestCaseElement}
	 */
	private void handleTestElementSelected(final ITestElement testElement) {
		if (fTestRunnerPart.getCurrentProgressState().equals(ProgressState.RUNNING)) {
			return;
		}
		if (testElement instanceof TestCaseElement) {
			final IMethod selectedMethod= ((TestCaseElement)testElement).getJavaMethod();
			handleJavaElementSelected(selectedMethod);
		} else if (testElement instanceof TestSuiteElement) {
			final IJavaElement selectedElement= ((TestSuiteElement)testElement).getJavaElement();
			handleJavaElementSelected(selectedElement);
		}
	}

	/**
	 * Reveals the given {@link IJavaElement} in its associated Editor if this later is already
	 * open, and sets the "Link with Editor" button state accordingly.
	 * 
	 * @param selectedJavaElement the selected {@link IJavaElement} in the {@link TestViewer} that
	 *            should be revealed in its Java Editor.
	 */
	private void handleJavaElementSelected(final IJavaElement selectedJavaElement) {
		// skip if there's no editor open (yet)
		if (fTestRunnerPart.getSite().getPage().getActiveEditor() == null) {
			return;
		}
		try {
			final IEditorPart editor= EditorUtility.isOpenInEditor(selectedJavaElement);
			if (selectedJavaElement != null && editor != null && editor instanceof JavaEditor) {
				final JavaEditor javaEditor= (JavaEditor)editor;
				final ITextSelection javaEditorSelection= (ITextSelection)javaEditor.getSelectionProvider().getSelection();
				final IEditorPart selectedMethodEditor= EditorUtility.isOpenInEditor(selectedJavaElement);
				// checks if the editor is already open or not
				if (selectedMethodEditor != null) {
					// Retrieve the current active editor
					final IEditorPart activeEditor= fTestRunnerPart.getSite().getPage().getActiveEditor();
					// open the required editor if it is not the active one
					if (!selectedMethodEditor.equals(activeEditor)) {
						EditorUtility.openInEditor(selectedJavaElement, false);
					}
					// retrieve the current java element (unless the associated compilation unit cannot be retrieved)
					final ICompilationUnit compilationUnit= (ICompilationUnit)selectedJavaElement.getAncestor(IJavaElement.COMPILATION_UNIT);
					fTestRunnerPart.setLinkingWithEditorInSync(true);
					if (compilationUnit != null) {
						final IJavaElement javaEditorSelectedElement= compilationUnit.getElementAt(javaEditorSelection.getOffset());
						// force to reveal the selected element in case where the editor was not active
						if (!selectedMethodEditor.equals(activeEditor) || !selectedJavaElement.equals(javaEditorSelectedElement)) {
							EditorUtility.revealInEditor(selectedMethodEditor, selectedJavaElement);
						}
					}
					return;
				}
			}
		} catch (JavaModelException e) {
			JUnitPlugin.log(e);
		} catch (PartInitException e) {
			// occurs if the editor could not be opened or the input element is not valid Status code
			JUnitPlugin.log(e);
		}
		fTestRunnerPart.setLinkingWithEditorInSync(false);
	}

}
