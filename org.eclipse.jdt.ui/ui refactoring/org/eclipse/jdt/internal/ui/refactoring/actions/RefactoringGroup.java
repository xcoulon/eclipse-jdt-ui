/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.refactoring.actions;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.viewers.ISelectionProvider;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.base.Refactoring;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameParametersRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.structure.PullUpMethodRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.structure.ReorderParametersRefactoring;
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.actions.ContextMenuGroup;
import org.eclipse.jdt.internal.ui.actions.GroupContext;
import org.eclipse.jdt.internal.ui.actions.StructuredSelectionProvider;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.refactoring.PullUpMethodsWizard;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringWizard;
import org.eclipse.jdt.internal.ui.refactoring.RenameParametersWizard;
import org.eclipse.jdt.internal.ui.refactoring.ReorderParametersWizard;

/**
 * Refactoring menu group
 */
public class RefactoringGroup extends ContextMenuGroup {
	
	private RefactoringAction[] fRefactoringActions;
	private boolean fIntitialized= false;
	
	public void fill(IMenuManager manager, GroupContext context) {
		createActions(context.getSelectionProvider());
		
		for (int i= 0; i < fRefactoringActions.length; i++) {
			RefactoringAction action= fRefactoringActions[i];
			action.update();
			if (action.isEnabled())
				manager.add(action);
		}	
	}
	
	private void createActions(ISelectionProvider p) {
		if (fIntitialized)
			return;
		
		StructuredSelectionProvider provider= StructuredSelectionProvider.createFrom(p);	
		
		fRefactoringActions= new RefactoringAction[]{
			createRenameParametersAction(provider),
			createReorderParametersAction(provider),
			createPullUpMethodsAction(provider),
			new SelfEncapsulateFieldAction(provider)
		};
		
		fIntitialized= true;
	}
		
	// -------------------- method refactorings ----------------------
	
	static OpenRefactoringWizardAction createRenameParametersAction(StructuredSelectionProvider selectionProvider) {
		String label= RefactoringMessages.getString("RefactoringGroup.rename_parameters"); //$NON-NLS-1$
		return new OpenRefactoringWizardAction(label, selectionProvider, IMethod.class) {
			protected Refactoring createNewRefactoringInstance(Object obj){
				return new RenameParametersRefactoring((IMethod)obj);
			}
			boolean canActivateRefactoring(Refactoring refactoring)  throws JavaModelException{
				return ((RenameParametersRefactoring)refactoring).checkPreactivation().isOK();
			}
			protected RefactoringWizard createWizard(Refactoring ref){
				String title= "Rename Method Parameters";
				String msg= "Enter the new names for the parameters. You can rename one or more parameters. All references to the renamed parameters will be updated.";
				String helpId= IJavaHelpContextIds.RENAME_PARAMS_ERROR_WIZARD_PAGE;
				return new RenameParametersWizard((RenameParametersRefactoring)ref, helpId, title, msg);
			}
		};
	}
	
	static OpenRefactoringWizardAction createReorderParametersAction(StructuredSelectionProvider selectionProvider) {
		String label= "Reorder Parameters...";
		return new OpenRefactoringWizardAction(label, selectionProvider, IMethod.class) {
			protected Refactoring createNewRefactoringInstance(Object obj){
				return new ReorderParametersRefactoring((IMethod)obj);
			}
			boolean canActivateRefactoring(Refactoring refactoring)  throws JavaModelException{
				return ((ReorderParametersRefactoring)refactoring).checkPreactivation().isOK();
			}
			protected RefactoringWizard createWizard(Refactoring ref){
				String title= "Reorder Method Parameters";
				//FIX ME: wrong
				String helpId= IJavaHelpContextIds.RENAME_PARAMS_ERROR_WIZARD_PAGE;
				return new ReorderParametersWizard((ReorderParametersRefactoring)ref, title, helpId);
			}
		};
	}
	
	static OpenRefactoringWizardAction createPullUpMethodsAction(StructuredSelectionProvider selectionProvider) {
		String label= "Pull Up ...";
		return new OpenRefactoringWizardAction(label, selectionProvider, IMethod.class) {
			protected Refactoring createNewRefactoringInstance(Object obj){
				Set methodSet= new HashSet();
				methodSet.addAll(Arrays.asList((Object[])obj));
				IMethod[] methods= (IMethod[]) methodSet.toArray(new IMethod[methodSet.size()]);
				return new PullUpMethodRefactoring(methods, JavaPreferencesSettings.getCodeGenerationSettings());
			}
			boolean canActivateRefactoring(Refactoring refactoring)  throws JavaModelException{
				return ((PullUpMethodRefactoring)refactoring).checkActivation(new NullProgressMonitor()).isOK();
			}
			protected boolean canOperateOnMultiSelection(){
				return true;
			}	
			protected RefactoringWizard createWizard(Refactoring ref){
				String title= "Pull Up Methods";
				//FIX ME: wrong
				String helpId= "HELPID";
				return new PullUpMethodsWizard((PullUpMethodRefactoring)ref, title, helpId);
			}
		};
	}	
}