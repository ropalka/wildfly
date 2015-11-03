package org.jboss.as.service;

import static org.jboss.as.server.loaders.Utils.getResourceName;
import static org.jboss.as.server.loaders.Utils.getChildArchives;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ModuleRootMarker;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.loaders.ResourceLoader;
import org.jboss.as.server.loaders.ResourceLoaders;
import org.jboss.as.service.logging.SarLogger;

/**
 * @author Tomasz Adamski
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class SarStructureProcessor implements DeploymentUnitProcessor {

    private static final String SAR_EXTENSION = ".sar";
    private static final String JAR_EXTENSION = ".jar";

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ResourceRoot resourceRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        if (resourceRoot == null) {
            return;
        }
        final ResourceLoader parentLoader = resourceRoot.getLoader();
        if (parentLoader == null) {
            return;
        }

        final String deploymentRootName = parentLoader.getRootName().toLowerCase(Locale.ENGLISH);
        if (!deploymentRootName.endsWith(SAR_EXTENSION)) {
            return;
        }

        ModuleRootMarker.mark(resourceRoot, true);
        try {
            final Collection<String> childArchives = getChildArchives(parentLoader, true, JAR_EXTENSION);
            String archiveName;
            for (final String archivePath : childArchives) {
                archiveName = getResourceName(archivePath);
                final ResourceLoader loader = ResourceLoaders.newResourceLoader(archiveName, resourceRoot.getLoader(), archivePath);
                // TODO: close loaders on cleanup in undeploy()
                final ResourceRoot childResource = new ResourceRoot(loader);
                ModuleRootMarker.mark(childResource);
                deploymentUnit.addToAttachmentList(Attachments.RESOURCE_ROOTS, childResource);
                resourceRoot.addToAttachmentList(Attachments.INDEX_IGNORE_PATHS, archivePath);
            }
        } catch (IOException e) {
            throw SarLogger.ROOT_LOGGER.failedToProcessSarChild(e, parentLoader.getRootName());
        }

    }

    @Override
    public void undeploy(DeploymentUnit context) {
        final List<ResourceRoot> childRoots = context.removeAttachment(Attachments.RESOURCE_ROOTS);
        if (childRoots != null) {
            for (ResourceRoot childRoot : childRoots) {
                safeClose(childRoot.getLoader());
            }
        }
    }

    static void safeClose(final AutoCloseable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                SarLogger.ROOT_LOGGER.trace("Failed to close resource", e);
            }
        }
    }

}
