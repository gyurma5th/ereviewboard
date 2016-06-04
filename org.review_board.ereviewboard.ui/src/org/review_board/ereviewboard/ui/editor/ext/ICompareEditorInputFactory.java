package org.review_board.ereviewboard.ui.editor.ext;

import org.eclipse.compare.CompareEditorInput;
import org.eclipse.mylyn.reviews.core.model.IFileItem;
import org.eclipse.mylyn.tasks.core.data.TaskData;
import org.review_board.ereviewboard.core.internal.scm.SCMFileContentsLocator;
import org.review_board.ereviewboard.core.model.Repository;
import org.review_board.ereviewboard.ui.editor.ReviewboardReviewBehaviour;

@SuppressWarnings("restriction")
public interface ICompareEditorInputFactory {
    
    boolean isEnabled(Repository codeRepository);

    CompareEditorInput createInput(IFileItem item, ReviewboardReviewBehaviour reviewBehaviour,
            TaskData taskData, SCMFileContentsLocator baseLocator,
            SCMFileContentsLocator targetLocator, int diffRevision);
}
