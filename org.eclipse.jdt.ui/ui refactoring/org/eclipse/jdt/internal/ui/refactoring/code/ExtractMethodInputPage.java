/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.refactoring.code;

import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;

import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.dom.Modifier;

import org.eclipse.jdt.internal.corext.refactoring.ParameterInfo;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.code.ExtractMethodRefactoring;
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.refactoring.ChangeParametersControl;
import org.eclipse.jdt.internal.ui.refactoring.IParameterListChangeListener;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.UserInputWizardPage;
import org.eclipse.jdt.internal.ui.util.RowLayouter;

public class ExtractMethodInputPage extends UserInputWizardPage {

	public static final String PAGE_NAME= "ExtractMethodInputPage";//$NON-NLS-1$

	private ExtractMethodRefactoring fRefactoring;
	private Text fTextField;
	private boolean fFirstTime;
	private Label fPreview;
	private IDialogSettings fSettings;
	
	private static final String DESCRIPTION = RefactoringMessages.getString("ExtractMethodInputPage.description");//$NON-NLS-1$
	private static final String THROW_RUNTIME_EXCEPTIONS= "ThrowRuntimeExceptions"; //$NON-NLS-1$

	public ExtractMethodInputPage() {
		super(PAGE_NAME, true);
		setImageDescriptor(JavaPluginImages.DESC_WIZBAN_REFACTOR_CU);
		setDescription(DESCRIPTION);
		fFirstTime= true;
	}

	public void createControl(Composite parent) {
		fRefactoring= (ExtractMethodRefactoring)getRefactoring();
		loadSettings();
		
		Composite result= new Composite(parent, SWT.NONE);
		setControl(result);
		GridLayout layout= new GridLayout();
		layout.numColumns= 2;
		result.setLayout(layout);
		RowLayouter layouter= new RowLayouter(2);
		GridData gd= null;
		
		initializeDialogUnits(result);
		
		Label label= new Label(result, SWT.NONE);
		label.setText(getLabelText());
		
		fTextField= createTextInputField(result, SWT.BORDER);
		fTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		layouter.perform(label, fTextField, 1);
		
		label= new Label(result, SWT.NONE);
		label.setText(RefactoringMessages.getString("ExtractMethodInputPage.access_Modifiers")); //$NON-NLS-1$
		
		Composite group= new Composite(result, SWT.NONE);
		group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		layout= new GridLayout();
		layout.numColumns= 4; layout.marginWidth= 0;
		group.setLayout(layout);
		
		String[] labels= new String[] {
			RefactoringMessages.getString("ExtractMethodInputPage.public"),  //$NON-NLS-1$
			RefactoringMessages.getString("ExtractMethodInputPage.protected"), //$NON-NLS-1$
			RefactoringMessages.getString("ExtractMethodInputPage.default"), //$NON-NLS-1$
			RefactoringMessages.getString("ExtractMethodInputPage.private") //$NON-NLS-1$
		};
		Integer[] data= new Integer[] {new Integer(Modifier.PUBLIC), new Integer(Modifier.PROTECTED), new Integer(Modifier.NONE), new Integer(Modifier.PRIVATE)};
		Integer visibility= new Integer(fRefactoring.getVisibility());
		for (int i= 0; i < labels.length; i++) {
			Button radio= new Button(group, SWT.RADIO);
			radio.setText(labels[i]);
			radio.setData(data[i]);
			if (data[i].equals(visibility))
				radio.setSelection(true);
			radio.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent event) {
					setVisibility((Integer)event.widget.getData());
				}
			});
		}
		layouter.perform(label, group, 1);
		
		if (!fRefactoring.getParameterInfos().isEmpty()) {
			ChangeParametersControl cp= new ChangeParametersControl(result, SWT.NONE, 
				RefactoringMessages.getString("ExtractMethodInputPage.parameters"), //$NON-NLS-1$
				new IParameterListChangeListener() {
				public void parameterChanged(ParameterInfo parameter) {
					parameterModified();
				}
				public void parameterListChanged() {
					updatePreview(getText());
				}
				public void parameterAdded(ParameterInfo parameter) {
					updatePreview(getText());
				}
			}, true, false, false);
			gd= new GridData(GridData.FILL_BOTH);
			gd.horizontalSpan= 2;
			cp.setLayoutData(gd);
			cp.setInput(fRefactoring.getParameterInfos());
		}
		
		Button checkBox= new Button(result, SWT.CHECK);
		checkBox.setText(RefactoringMessages.getString("ExtractMethodInputPage.throwRuntimeExceptions")); //$NON-NLS-1$
		checkBox.setSelection(fSettings.getBoolean(THROW_RUNTIME_EXCEPTIONS));
		checkBox.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				setRethrowRuntimeException(((Button)e.widget).getSelection());
			}
		});
		layouter.perform(checkBox);
		
		int dupliactes= fRefactoring.getNumberOfDuplicates();
		checkBox= new Button(result, SWT.CHECK);
		if (dupliactes == 0) {
			checkBox.setText(RefactoringMessages.getString("ExtractMethodInputPage.duplicates.none")); //$NON-NLS-1$
		} else  if (dupliactes == 1) {
			checkBox.setText(RefactoringMessages.getString("ExtractMethodInputPage.duplicates.single")); //$NON-NLS-1$
		} else {
			checkBox.setText(RefactoringMessages.getFormattedString(
				"ExtractMethodInputPage.duplicates.multi", //$NON-NLS-1$
				new Integer(dupliactes))); 
		}
		checkBox.setSelection(dupliactes > 0);
		checkBox.setEnabled(dupliactes > 0);
		checkBox.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				fRefactoring.setReplaceDuplicates(((Button)e.widget).getSelection());
			}
		});
		layouter.perform(checkBox);
		
		label= new Label(result, SWT.SEPARATOR | SWT.HORIZONTAL);
		label.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		layouter.perform(label);
		
		label= new Label(result, SWT.NONE);
		gd= new GridData();
		gd.verticalAlignment= GridData.BEGINNING;
		label.setLayoutData(gd);
		label.setText(RefactoringMessages.getString("ExtractMethodInputPage.signature_preview")); //$NON-NLS-1$
		
		fPreview= new Label(result, SWT.WRAP);
		gd= new GridData(GridData.FILL_BOTH);
		gd.widthHint= convertWidthInCharsToPixels(50);
		fPreview.setLayoutData(gd);
		
		layouter.perform(label, fPreview, 1);
		Dialog.applyDialogFont(result);
		WorkbenchHelp.setHelp(getControl(), IJavaHelpContextIds.EXTRACT_METHOD_WIZARD_PAGE);		
	}	

	private Text createTextInputField(Composite parent, int style) {
		Text result= new Text(parent, style);
		result.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				textModified(getText());
			}
		});
		return result;
	}
	
	private String getText() {
		if (fTextField == null)
			return null;
		return fTextField.getText();	
	}
	
	private String getLabelText(){
		return RefactoringMessages.getString("ExtractMethodInputPage.label_text"); //$NON-NLS-1$
	}
	
	private void setVisibility(Integer visibility) {
		fRefactoring.setVisibility(visibility.intValue());
		updatePreview(getText());
	}
	
	private void setRethrowRuntimeException(boolean value) {
		fSettings.put(THROW_RUNTIME_EXCEPTIONS, value);
		fRefactoring.setThrowRuntimeExceptions(value);
		updatePreview(getText());
	}
	
	private void updatePreview(String text) {
		if (fPreview == null)
			return;
			
		if (text.length() == 0)
			text= "someMethodName";			 //$NON-NLS-1$
			
		fPreview.setText(fRefactoring.getSignature(text));
	}
	
	private void loadSettings() {
		fSettings= getDialogSettings().getSection(ExtractMethodWizard.DIALOG_SETTING_SECTION);
		if (fSettings == null) {
			fSettings= getDialogSettings().addNewSection(ExtractMethodWizard.DIALOG_SETTING_SECTION);
			fSettings.put(THROW_RUNTIME_EXCEPTIONS, false);
		}
		fRefactoring.setThrowRuntimeExceptions(fSettings.getBoolean(THROW_RUNTIME_EXCEPTIONS));
	}
	
	//---- Input validation ------------------------------------------------------
	
	public void setVisible(boolean visible) {
		if (visible) {
			if (fFirstTime) {
				fFirstTime= false;
				setPageComplete(false);
				updatePreview(getText());
				fTextField.setFocus();
			} else {
				setPageComplete(validatePage(true));
			}
		}
		super.setVisible(visible);
	}
	
	private void textModified(String text) {
		fRefactoring.setMethodName(text);
		RefactoringStatus status= validatePage(true);
		if (!status.hasFatalError()) {
			updatePreview(text);
		} else {
			fPreview.setText(""); //$NON-NLS-1$
		}
		setPageComplete(status);
	}
	
	private void parameterModified() {
		updatePreview(getText());
		setPageComplete(validatePage(false));
	}
	
	private RefactoringStatus validatePage(boolean text) {
		RefactoringStatus result= new RefactoringStatus();
		if (text) {
			result.merge(validateMethodName());
			result.merge(validateParameters());
		} else {
			result.merge(validateParameters());
			result.merge(validateMethodName());
		}
		return result;
	}
	
	private RefactoringStatus validateMethodName() {
		RefactoringStatus result= new RefactoringStatus();
		String text= getText();
		if ("".equals(text)) { //$NON-NLS-1$
			result.addFatalError(RefactoringMessages.getString("ExtractMethodInputPage.validation.emptyMethodName")); //$NON-NLS-1$
			return result;
		}
		result.merge(fRefactoring.checkMethodName());
		return result;
	}
	
	private RefactoringStatus validateParameters() {
		RefactoringStatus result= new RefactoringStatus();
		List parameters= fRefactoring.getParameterInfos();
		for (Iterator iter= parameters.iterator(); iter.hasNext();) {
			ParameterInfo info= (ParameterInfo) iter.next();
			if ("".equals(info.getNewName())) { //$NON-NLS-1$
				result.addFatalError(RefactoringMessages.getString("ExtractMethodInputPage.validation.emptyParameterName")); //$NON-NLS-1$
				return result;
			}
		}
		result.merge(fRefactoring.checkParameterNames());
		return result;
	}
}
