/*******************************************************************************
 * Copyright (c) 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.ui.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.IPackageFragmentRoot;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;

/**
 * Action to remove package fragment roots from the classpath of its parent
 * project. Currently, the action is applicable to selections containing
 * not-external archives (JAR or zip).
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 * @since 2.1
 */
public class RemoveFromClasspathAction extends SelectionDispatchAction {

	/**
	 * Creates a new <code>RemoveFromClasspathAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type
	 * <code> org.eclipse.jface.viewers.IStructuredSelection</code>.
	 * 
	 * @param site the site providing context information for this action
	 */
	public RemoveFromClasspathAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.getString("RemoveFromClasspathAction.Remove")); //$NON-NLS-1$
		setToolTipText(ActionMessages.getString("RemoveFromClasspathAction.tooltip")); //$NON-NLS-1$
		WorkbenchHelp.setHelp(this, IJavaHelpContextIds.REMOVE_FROM_CLASSPATH_ACTION);
	}
	
	/* (non-Javadoc)
	 * Method declared in SelectionDispatchAction
	 */
	protected void selectionChanged(IStructuredSelection selection) {
		setEnabled(checkEnabled(selection));
	}
	
	private static boolean checkEnabled(IStructuredSelection selection) {
		if (selection.isEmpty())
			return false;
		for (Iterator iter= selection.iterator(); iter.hasNext();) {
			if (! canRemove(iter.next()))
				return false;
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * Method declared in SelectionDispatchAction
	 */
	protected void run(final IStructuredSelection selection) {
		try {
			PlatformUI.getWorkbench().getActiveWorkbenchWindow().run(true, true, new WorkbenchRunnableAdapter(new IWorkspaceRunnable() {
				public void run(IProgressMonitor pm) throws CoreException {
					try{
						IPackageFragmentRoot[] roots= getRootsToRemove(selection);
						pm.beginTask(ActionMessages.getString("RemoveFromClasspathAction.Removing"), roots.length); //$NON-NLS-1$
						for (int i= 0; i < roots.length; i++) {
							int jCoreFlags= IPackageFragmentRoot.NO_RESOURCE_MODIFICATION | IPackageFragmentRoot.ORIGINATING_PROJECT_CLASSPATH;
							roots[i].delete(IResource.NONE, jCoreFlags, new SubProgressMonitor(pm, 1));
						}
					} finally {
						pm.done();
					}
				}
		}));
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, getShell(), 
					ActionMessages.getString("RemoveFromClasspathAction.exception_dialog_title"), //$NON-NLS-1$
					ActionMessages.getString("RemoveFromClasspathAction.Problems_occurred")); //$NON-NLS-1$
		} catch (InterruptedException e) {
			// canceled
		}
	}
	
	private static IPackageFragmentRoot[] getRootsToRemove(IStructuredSelection selection){
		List result= new ArrayList(selection.size()); 
		for (Iterator iter= selection.iterator(); iter.hasNext();) {
			Object element= (Object) iter.next();
			if (canRemove(element))
				result.add(element);
		}
		return (IPackageFragmentRoot[]) result.toArray(new IPackageFragmentRoot[result.size()]);
	}	

	private static boolean canRemove(Object element){
		if (! (element instanceof IPackageFragmentRoot))
			return false;
		IPackageFragmentRoot root= (IPackageFragmentRoot)element;
		if (! root.isArchive())
			return false;
		if (root.isExternal())
			return false;
		return true;	
	}	
}

