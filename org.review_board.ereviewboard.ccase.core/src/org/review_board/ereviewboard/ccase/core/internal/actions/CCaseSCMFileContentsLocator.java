package org.review_board.ereviewboard.ccase.core.internal.actions;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.text.MessageFormat;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.team.core.RepositoryProvider;
import org.review_board.ereviewboard.ccase.core.internal.Activator;
import org.review_board.ereviewboard.core.internal.scm.SCMFileContentsLocator;
import org.review_board.ereviewboard.core.model.FileDiff;
import org.review_board.ereviewboard.core.model.Repository;
import org.review_board.ereviewboard.core.model.RepositoryType;

import net.sourceforge.eclipseccase.ClearCaseProvider;
import net.sourceforge.eclipseccase.StateCache;
import net.sourceforge.eclipseccase.StateCacheFactory;

/**
 * @author Robert Munteanu
 * 
 */
@SuppressWarnings("restriction")
public class CCaseSCMFileContentsLocator implements SCMFileContentsLocator {

	private static final String CC_SEPARATOR = "/";
	private static final String DOUBLE_SNAIL = "@@";
	private Repository codeRepository;
	private String revision;
	private String filePath;

	public void init(Repository codeRepository, String filePath, String revision) {

		this.codeRepository = codeRepository;
		this.filePath = filePath;
		this.revision = revision;
	}

	public boolean isEnabled() {

		return codeRepository != null && codeRepository.getTool() == RepositoryType.ClearCase;
	}

	public byte[] getContents(IProgressMonitor monitor) throws CoreException {

		if (FileDiff.PRE_CREATION.equals(revision))
			return new byte[0];

		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {

			RepositoryProvider provider = RepositoryProvider.getProvider(project);
			System.out.println(provider);

			if (!(provider instanceof ClearCaseProvider))
				continue;

			ClearCaseProvider ccProvider = (ClearCaseProvider) provider;
			IPath rawLocation = project.getRawLocation();

			String[] projectSegments = rawLocation.segments();
			if (ArrayUtils.contains(projectSegments, codeRepository.getName())) {
				int pathPrefix = ArrayUtils.indexOf(projectSegments, codeRepository.getName());

				String[] pathAndRevision = filePath.split(DOUBLE_SNAIL);
				String realRevision = pathAndRevision[pathAndRevision.length - 1];
				String[] pathSegments = filePath.split(CC_SEPARATOR);
				int pathPostfix = ArrayUtils.indexOf(pathSegments, codeRepository.getName());
				String[] localFileSegments = (String[]) ArrayUtils.addAll(
						ArrayUtils.subarray(projectSegments, 0, pathPrefix),
						ArrayUtils.subarray(pathSegments, pathPostfix, pathSegments.length));
				String osPath = rawLocation.getDevice() + File.separator
						+ StringUtils.join(localFileSegments, File.separator);

				InputStream contents = null;
				try {
					StateCache cache = StateCacheFactory.getInstance().get(project);
					if (cache.isUninitialized()) {
						continue;
					}
					if (cache.isSnapShot()) {
						final File tempFile = File.createTempFile("eclipseccase", null);
						tempFile.delete();
						tempFile.deleteOnExit();
						ccProvider.copyVersionIntoSnapShot(tempFile.getPath(), osPath);
						// now we should have the snapshot version.
						contents = new BufferedInputStream(new FileInputStream(tempFile.getPath()));

					} else {
						// dynamic
						contents = new FileInputStream(osPath + DOUBLE_SNAIL + realRevision);
					}
					return IOUtils.toByteArray(contents);
				} catch (FileNotFoundException e) {
					throw new CoreException(new Status(IStatus.WARNING, Activator.PLUGIN_ID,
							MessageFormat.format("Internal, could not find file {0}.", filePath), e));
				} catch (Exception e) {
					throw new CoreException(new Status(IStatus.WARNING, Activator.PLUGIN_ID,
							MessageFormat.format("Internal, Could not create temp file for {0}.", filePath), e));
				} finally {
					try {
						if (contents != null) {
							contents.close();
						}
					} catch (Exception e) {

					}
				}

			}

		}

		throw new CoreException(new Status(IStatus.WARNING, Activator.PLUGIN_ID,
				"No repository found for revision id: " + revision
						+ ".\nYou should have at least one Clearcase project from the VOB "
						+ this.codeRepository.getName() + " loaded and associated with Clearcase."));
	}

}
