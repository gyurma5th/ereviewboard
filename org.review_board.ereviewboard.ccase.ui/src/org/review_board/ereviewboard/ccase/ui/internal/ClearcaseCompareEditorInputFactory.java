package org.review_board.ereviewboard.ccase.ui.internal;

import org.eclipse.compare.CompareEditorInput;
import org.eclipse.mylyn.reviews.core.model.IFileItem;
import org.eclipse.mylyn.tasks.core.data.TaskData;
import org.review_board.ereviewboard.core.internal.scm.SCMFileContentsLocator;
import org.review_board.ereviewboard.core.model.Repository;
import org.review_board.ereviewboard.core.model.RepositoryType;
import org.review_board.ereviewboard.ui.editor.ClearCaseCompareEditorInput;
import org.review_board.ereviewboard.ui.editor.ReviewboardReviewBehaviour;
import org.review_board.ereviewboard.ui.editor.ext.ICompareEditorInputFactory;

@SuppressWarnings("restriction")
public class ClearcaseCompareEditorInputFactory implements ICompareEditorInputFactory {

	public boolean isEnabled(Repository codeRepository) {
		return codeRepository.getTool() == RepositoryType.ClearCase;
	}

	public CompareEditorInput createInput(IFileItem item, ReviewboardReviewBehaviour reviewBehaviour, TaskData taskData,
			SCMFileContentsLocator baseLocator, SCMFileContentsLocator targetLocator, int diffRevision) {
		return new ClearCaseCompareEditorInput(item, reviewBehaviour, taskData, baseLocator, targetLocator, diffRevision);
	}

}
