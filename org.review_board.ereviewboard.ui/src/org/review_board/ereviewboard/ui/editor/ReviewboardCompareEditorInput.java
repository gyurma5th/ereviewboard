/*******************************************************************************
 * Copyright (c) 2011 Robert Munteanu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Robert Munteanu - initial API and implementation
 *******************************************************************************/
package org.review_board.ereviewboard.ui.editor;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.patch.ApplyPatchOperation;
import org.eclipse.compare.patch.IFilePatch;
import org.eclipse.compare.patch.IFilePatchResult;
import org.eclipse.compare.patch.PatchConfiguration;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.mylyn.internal.reviews.ui.compare.FileItemCompareEditorInput;
import org.eclipse.mylyn.reviews.core.model.IFileItem;
import org.eclipse.mylyn.tasks.core.data.TaskData;
import org.eclipse.mylyn.tasks.ui.TasksUi;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.statushandlers.StatusManager;
import org.json.JSONException;
import org.review_board.ereviewboard.core.ReviewboardCorePlugin;
import org.review_board.ereviewboard.core.ReviewboardRepositoryConnector;
import org.review_board.ereviewboard.core.client.DiffCommentLineMapper;
import org.review_board.ereviewboard.core.client.ReviewboardClient;
import org.review_board.ereviewboard.core.exception.ReviewboardException;
import org.review_board.ereviewboard.core.internal.scm.SCMFileContentsLocator;
import org.review_board.ereviewboard.core.model.DiffData;
import org.review_board.ereviewboard.core.model.reviews.ReviewModelFactory;
import org.review_board.ereviewboard.core.util.ByteArrayStorage;

/**
 * @author Robert Munteanu
 *
 */
@SuppressWarnings("restriction")
class ReviewboardCompareEditorInput extends FileItemCompareEditorInput {

    private final TaskData _taskData;
    private final SCMFileContentsLocator _baseLocator;
    private final SCMFileContentsLocator _targetLocator;
    private final int _diffRevisionId;
    protected IFileItem _file;

    /**
     * @param file
     * @param reviewBehaviour
     * @param taskData
     * @param codeRepository
     * @param baseLocator the locator for the base revision, already initialised
     *            for the specified <tt>file</tt> )
     * @param targetLocator the locator for the target revision, already
     *            initialised for the specified <tt>file</tt> )
     * @param targetLocator
     */
    ReviewboardCompareEditorInput(IFileItem file, ReviewboardReviewBehaviour reviewBehaviour, TaskData taskData, SCMFileContentsLocator baseLocator, SCMFileContentsLocator targetLocator, int diffRevisionId) {
        super(new CompareConfiguration(), file, reviewBehaviour);
        _taskData = taskData;
        _baseLocator = baseLocator;
        _targetLocator = targetLocator;
        this._diffRevisionId = diffRevisionId;
        this._file=file;
    }
    
    public IFileItem getFile(){
        return this._file;
    }

    @Override
    protected Object prepareInput(IProgressMonitor monitor)
            throws InvocationTargetException, InterruptedException {
        
        ReviewboardRepositoryConnector connector = ReviewboardCorePlugin.getDefault().getConnector();
        
        ReviewboardClient client = connector.getClientManager().getClient(TasksUi.getRepositoryManager().getRepository(ReviewboardCorePlugin.REPOSITORY_KIND, _taskData.getRepositoryUrl()));
        
        try {
            monitor.beginTask("Loading file contents and comments", 5);
            
            loadContents(monitor);
            appendComments(monitor, client);
            
            
            return super.prepareInput(monitor);
            
        } catch (ReviewboardException e) {
            throw new InvocationTargetException(e);
        } catch (CoreException e) {
            throw new InvocationTargetException(e);
        } catch (IOException e) {
            throw new InvocationTargetException(e);
        } finally {
            monitor.done();
        }
    }

    private void loadContents(IProgressMonitor monitor) throws ReviewboardException, CoreException, IOException {

        
        if ( getFile().getBase().getContent() == null && getFile().getTarget().getContent() == null ) {
            
            ReviewboardRepositoryConnector connector = ReviewboardCorePlugin.getDefault().getConnector();
            
            ReviewboardClient client = connector.getClientManager().getClient(TasksUi.getRepositoryManager().getRepository(ReviewboardCorePlugin.REPOSITORY_KIND, _taskData.getRepositoryUrl()));

            IFilePatch patch = getPatchForFile(monitor, Integer.parseInt(_taskData.getTaskId()), _diffRevisionId, Integer.parseInt(getFile().getId()), client);
            IFilePatchResult result = applyPatch(monitor, patch);
            getFile().getBase().setContent(IOUtils.toString(result.getOriginalContents()));
            getFile().getTarget().setContent(IOUtils.toString(result.getPatchedContents()));

            // if the two contents are the same assume error in patch and use
            // the revisions directly
            if (_targetLocator != null && getFile().getBase().getContent().equals(
                    getFile().getTarget().getContent())) {
                getFile().getBase().setContent(new String(_baseLocator.getContents(monitor)));
                getFile().getTarget().setContent(new String(_targetLocator.getContents(monitor)));
            }

            // remove version from version extended clearcase pathes, so file
            // type can be deducted from extension
            getFile().getBase().setPath(normalizePath(getFile().getBase().getPath()));
            getFile().getTarget().setPath(normalizePath(getFile().getTarget().getPath()));
        }
        
        monitor.worked(1);
    }

    /**
     * removes eclipse version info from the path
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

    private void appendComments(IProgressMonitor monitor, ReviewboardClient client) throws ReviewboardException {
        
        int reviewRequestId = Integer.parseInt(_taskData.getTaskId());
        int diffId =  _diffRevisionId;
        int fileDiffId = Integer.parseInt(getFile().getId());
        
        // do not add comments twice
        if ( getFile().getBase().getComments().isEmpty() && getFile().getTarget().getComments().isEmpty() ) {
            DiffData diffData = null;
            try {
                diffData = client.getDiffData(reviewRequestId, diffId, fileDiffId, monitor);
            } catch (final ReviewboardException e) {
                e.printStackTrace();
                if (!(e.getCause() instanceof JSONException)) {
                    throw e;
                }
                diffData = new DiffData();
                if (StatusManager.getManager() != null) {
                    Display.getDefault().asyncExec(new Runnable() {

                        public void run() {
                            StatusManager.getManager().handle(
                                    new Status(Status.ERROR, ReviewboardCorePlugin.PLUGIN_ID,
                                            "Cannot load diff from Reviewboard. Inline comments are not displayed.",
                                            e),
                                    StatusManager.BLOCK);
                        }
                    });
                }
            }
            monitor.worked(1);
            new ReviewModelFactory(client).appendComments(getFile(), client.readDiffCommentsForFileDiff(reviewRequestId, diffId, fileDiffId, monitor), new DiffCommentLineMapper(diffData));
            monitor.worked(1);
        } else {
            monitor.worked(2);
        }
        
    }

    /**
     * Retrieves and parses a diff from the remote reviewboard instance
     * @param monitor
     * @param taskId
     * @param diffRevision
     * @param fileId
     * @param client
     * @return
     * @throws ReviewboardException
     * @throws CoreException
     */
    private IFilePatch getPatchForFile(IProgressMonitor monitor, int taskId, int diffRevision, int fileId, ReviewboardClient client)
            throws ReviewboardException, CoreException {
        
        final byte[] diff = client.getRawFileDiff(taskId, diffRevision, fileId, monitor);
        monitor.worked(1);

        IFilePatch[] parsedPatches = ApplyPatchOperation.parsePatch(new ByteArrayStorage(diff));
        
        if ( parsedPatches.length == 0 )
            throw new ReviewboardException("Repository returned no diff for this file.");
        
        if ( parsedPatches.length != 1 )
            throw new ReviewboardException("Parsed " + parsedPatches.length + ", expected 1.");
        
        return parsedPatches[0];
    }

    private IFilePatchResult applyPatch(IProgressMonitor monitor, IFilePatch patch) throws CoreException {
        
        PatchConfiguration patchConfiguration = new PatchConfiguration();
        IStorage source = lookupResource(monitor);
        monitor.worked(1);
        
        IFilePatchResult patchResult = patch.apply(source, patchConfiguration, monitor);
        monitor.worked(1);
        return patchResult;
    }

    private IStorage lookupResource(IProgressMonitor monitor) throws CoreException {
        
        return new ByteArrayStorage(_baseLocator.getContents(monitor));
    }
}