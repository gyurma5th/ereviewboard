package org.review_board.ereviewboard.ui.editor;

import java.lang.reflect.InvocationTargetException;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.mylyn.internal.reviews.ui.compare.FileItemNode;
import org.eclipse.mylyn.reviews.core.model.IFileItem;
import org.eclipse.mylyn.tasks.core.data.TaskData;
import org.review_board.ereviewboard.core.internal.scm.SCMFileContentsLocator;

@SuppressWarnings("restriction")
public class ClearCaseCompareEditorInput extends ReviewboardCompareEditorInput {
 
    private SCMFileContentsLocator baseLocator;
    private SCMFileContentsLocator targetLocator;

    public ClearCaseCompareEditorInput(IFileItem file, ReviewboardReviewBehaviour reviewBehaviour,
            TaskData taskData, SCMFileContentsLocator baseLocator,
            SCMFileContentsLocator targetLocator, int diffRevisionId) {
        super(file, reviewBehaviour, taskData, baseLocator, targetLocator, diffRevisionId);
    }

    @Override
    protected Object prepareInput(IProgressMonitor monitor)
            throws InvocationTargetException, InterruptedException {
        
        SubMonitor progress = SubMonitor.convert(monitor, 4);
        Object retVal = super.prepareInput(progress.newChild(3));

        if (retVal instanceof FileItemNode) {
            FileItemNode node = (FileItemNode) retVal;
            try {
                postProcess(node.getFileItem(), baseLocator, targetLocator, progress.newChild(1));
            } catch (CoreException e) {
                throw new InvocationTargetException(e);
            }
        }
        return retVal;
    }
    
    private void postProcess(IFileItem file, SCMFileContentsLocator baseLocator,
            SCMFileContentsLocator targetLocator, IProgressMonitor monitor) throws CoreException {

        // if the two contents are the same assume error in patch and use
        // the revisions directly
        if (targetLocator != null && file.getBase().getContent().equals(
                file.getTarget().getContent())) {
            file.getBase().setContent(new String(baseLocator.getContents(monitor)));
            file.getTarget().setContent(new String(targetLocator.getContents(monitor)));
        }

        // remove version from version extended clearcase pathes, so file
        // type can be deducted from extension
        file.getBase().setPath(normalizePath(file.getBase().getPath()));
        file.getTarget().setPath(normalizePath(file.getTarget().getPath()));
    }

    /**
     * removes clearcase version info from the path
     * 
     * @param path
     * @return
     */
    private String normalizePath(String path) {
        String[] elements = path.split("@@");
        if (elements.length == 1) {
            return path;
        }
        return StringUtils.join(ArrayUtils.subarray(elements, 0, elements.length - 1));
    }

}
