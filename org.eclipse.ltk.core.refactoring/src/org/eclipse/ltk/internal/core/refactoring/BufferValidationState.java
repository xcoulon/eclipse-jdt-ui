/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ltk.internal.core.refactoring;

import org.eclipse.core.runtime.IPath;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.IFileBuffer;
import org.eclipse.core.filebuffers.IFileBufferListener;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;

import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;

import org.eclipse.ltk.core.refactoring.ContentStamp;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;


public abstract class BufferValidationState {
	
	protected IFile fFile;
	protected boolean fExisted;
	
	public static BufferValidationState create(IFile file) {
		ITextFileBuffer buffer= getBuffer(file);
		if (buffer == null) {
			return new SavedBufferValidationState(file);
		} else if (buffer.isDirty()) {
			return new DirtyBufferValidationState(file);
		} else {
			return new SavedBufferValidationState(file);
		}
	}
	
	public RefactoringStatus isValid() {
		if (!fExisted) {
			if (fFile.exists())
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getFormattedString(
					"TextChanges.error.existing", //$NON-NLS-1$
					fFile.getFullPath().toString()
					));
		} else {
			if (!fFile.exists())
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getFormattedString(
					"TextChanges.error.not_existing", //$NON-NLS-1$
					fFile.getFullPath().toString()
					));
		}
		return new RefactoringStatus();
	}
	
	public void dispose() {
	}
	
	
	protected BufferValidationState(IFile file) {
		fFile= file;
		fExisted= file.exists();
	}
	
	protected IDocument getDocument() {
		ITextFileBuffer buffer= getBuffer(fFile);
		if (buffer == null)
			return null;
		return buffer.getDocument();
		
	}
	
	protected static boolean isDirty(IFile file) {
		ITextFileBuffer buffer= getBuffer(file);
		if (buffer == null)
			return false;
		return buffer.isDirty();
	}
	
	protected static ITextFileBuffer getBuffer(IFile file) {
		ITextFileBufferManager manager= FileBuffers.getTextFileBufferManager();
		IPath path= file.getFullPath();
		ITextFileBuffer buffer= manager.getTextFileBuffer(path);
		return buffer;
	}
}

class DirtyBufferValidationState extends BufferValidationState {
	
	private IDocumentListener fDocumentListener;
	private FileBufferListener fFileBufferListener;
	private boolean fChanged;
	private ContentStamp fContentStamp= null;
	
	class DocumentChangedListener implements IDocumentListener {
		public void documentAboutToBeChanged(DocumentEvent event) {
		}
		public void documentChanged(DocumentEvent event) {
			DirtyBufferValidationState.this.documentChanged();
		}
	}
	
	class FileBufferListener implements IFileBufferListener {
		public void bufferCreated(IFileBuffer buffer) {
			if (buffer.equals(getBuffer(fFile))) {
				if (fDocumentListener == null)
					fDocumentListener= new DocumentChangedListener();
				getDocument().addDocumentListener(fDocumentListener);
			}
		}
		public void bufferDisposed(IFileBuffer buffer) {
			if (fDocumentListener != null && buffer.equals(getBuffer(fFile))) {
				getDocument().removeDocumentListener(fDocumentListener);
				fDocumentListener= null;
				fContentStamp= ContentStamps.get(fFile, true);
			}
		}
		public void bufferContentAboutToBeReplaced(IFileBuffer buffer) {
		}
		public void bufferContentReplaced(IFileBuffer buffer) {
		}
		public void stateChanging(IFileBuffer buffer) {
		}
		public void dirtyStateChanged(IFileBuffer buffer, boolean isDirty) {
		}
		public void stateValidationChanged(IFileBuffer buffer, boolean isStateValidated) {
		}
		public void underlyingFileMoved(IFileBuffer buffer, IPath path) {
		}
		public void underlyingFileDeleted(IFileBuffer buffer) {
		}
		public void stateChangeFailed(IFileBuffer buffer) {
		}
	}
	
	public DirtyBufferValidationState(IFile file) {
		super(file);
		fFileBufferListener= new FileBufferListener();
		FileBuffers.getTextFileBufferManager().addFileBufferListener(fFileBufferListener);
		fDocumentListener= new DocumentChangedListener();
		getDocument().addDocumentListener(fDocumentListener);
	}

	public RefactoringStatus isValid() {
		RefactoringStatus result= super.isValid();
		if (result.hasFatalError())
			return result;
		if (fChanged || (fContentStamp != null && !fContentStamp.equals(ContentStamps.get(fFile)))) {
			result.addFatalError(RefactoringCoreMessages.getFormattedString(
				"TextChanges.error.content_changed", //$NON-NLS-1$
				fFile.getFullPath().toString()
				)); 
		}
		return result;
	}
	
	public void dispose() {
		if (fFileBufferListener != null) {
			FileBuffers.getTextFileBufferManager().removeFileBufferListener(fFileBufferListener);
		}
		if (fDocumentListener != null) {
			getDocument().removeDocumentListener(fDocumentListener);
		}
	}
	
	private void documentChanged() {
		fChanged= true;
		getDocument().removeDocumentListener(fDocumentListener);
		FileBuffers.getTextFileBufferManager().removeFileBufferListener(fFileBufferListener);
		fFileBufferListener= null;
		fDocumentListener= null;
	}
}

class SavedBufferValidationState extends BufferValidationState {
	private ContentStamp fContentStamp;
	
	public SavedBufferValidationState(IFile file) {
		super(file);
		fContentStamp= ContentStamps.get(file, true);
	}

	public RefactoringStatus isValid() {
		RefactoringStatus result= super.isValid();
		if (result.hasFatalError())
			return result;
		if (fContentStamp == null) {
			result.addFatalError(RefactoringCoreMessages.getFormattedString(
				"TextChanges.error.missing_stamp", //$NON-NLS-1$
				fFile.getFullPath().toString()
				)); 
		} if (!fContentStamp.equals(ContentStamps.get(fFile))) {
			result.addFatalError(RefactoringCoreMessages.getFormattedString(
				"TextChanges.error.content_changed", //$NON-NLS-1$
				fFile.getFullPath().toString()
				)); 
		} else if (fFile.isReadOnly()) {
			result.addFatalError(RefactoringCoreMessages.getFormattedString(
				"TextChanges.error.read_only", //$NON-NLS-1$
				fFile.getFullPath().toString()
				));
		} else if (!fFile.isSynchronized(IResource.DEPTH_ZERO)) { 
			result.addFatalError(RefactoringCoreMessages.getFormattedString(
				"TextChanges.error.outOfSync", //$NON-NLS-1$
				fFile.getFullPath().toString()
				));
		} else if (isDirty(fFile)){
			result.addFatalError(RefactoringCoreMessages.getFormattedString(
				"TextChanges.error.unsaved_changes", //$NON-NLS-1$
				fFile.getFullPath().toString()
				)); 
		}
		return result;
	}
}	

