package org.jboss.as.jdr.util;

import org.jboss.as.jdr.vfs.Filters;

import java.io.File;
import java.io.FileFilter;

import java.io.InputStream;

/**
 * Provides a default implementation of {@link Sanitizer} that uses default
 * filtering. Sanitizers should subclass this unless they wish to use complex
 * accepts filtering.
 */
abstract class AbstractSanitizer implements Sanitizer {

    protected FileFilter filter = Filters.TRUE;

    @Override
    public abstract InputStream sanitize(InputStream in) throws Exception;


    /**
     * returns whether or not a VirtualFile should be processed by this sanitizer.
     *
     * @param resource file resource to test
     * @return
     */
    @Override
    public boolean accepts(File resource) {
        return filter.accept(resource);
    }
}
