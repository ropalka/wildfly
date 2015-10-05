/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.jdr.commands;

import org.jboss.as.jdr.util.Utils;
import org.jboss.as.jdr.util.Sanitizer;
import org.jboss.as.jdr.vfs.Filters;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class CollectFiles extends JdrCommand {

    private FileFilter filter = Filters.TRUE;
    private Filters.BlacklistFilter blacklistFilter = Filters.wildcardBlackList();
    private LinkedList<Sanitizer> sanitizers = new LinkedList<Sanitizer>();
    private Comparator<File> sorter = new Comparator<File>() {
        @Override
        public int compare(File resource, File resource1) {
            return Long.signum(resource.lastModified() - resource1.lastModified());
        }
    };

    // -1 means no limit
    private long limit = -1;

    public CollectFiles(FileFilter filter) {
        this.filter = filter;
    }

    public CollectFiles(String pattern) {
        this.filter = Filters.wildcard(pattern);
    }

    public CollectFiles sanitizer(Sanitizer ... sanitizers) {
        for (Sanitizer s : sanitizers) {
            this.sanitizers.add(s);
        }
        return this;
    }

    public CollectFiles sorter(Comparator<File> sorter){
        this.sorter = sorter;
        return this;
    }

    public CollectFiles limit(final long limit){
        this.limit = limit;
        return this;
    }

    public CollectFiles omit(String pattern) {
        blacklistFilter.add(pattern);
        return this;
    }

    private void getChildrenRecursively(final File file, final FileFilter filter, final List<File> matches) {
        final File[] files = file.listFiles();
        for (final File f : files) {
            if (f.isDirectory()) {
                getChildrenRecursively(f, filter, matches);
            } else {
                if (filter.accept(f)) {
                    matches.add(f);
                }
            }
        }
    }

    @Override
    public void execute() throws Exception {
        File root = new File(this.env.getJbossHome());
        List<File> matches = new ArrayList<File>();
        getChildrenRecursively(root, Filters.and(this.filter, this.blacklistFilter), matches);

        // order the files in some arbitrary way.. basically prep for the limiter so things like log files can
        // be gotten in chronological order.  Keep in mind everything that might be collected per the filter for
        // this collector. If the filter is too broad, you may collect unrelated logs, sort them, and then
        // get some limit on that set, which probably would be wrong.
        if(sorter != null){
            Collections.sort(matches, sorter);
        }

        // limit how much data we collect
        Limiter limiter = new Limiter(limit);
        Iterator<File> iter = matches.iterator();

        while(iter.hasNext() && !limiter.isDone()) {

            File f = iter.next();
            InputStream stream = limiter.limit(f);

            for (Sanitizer sanitizer : this.sanitizers) {
                if(sanitizer.accepts(f)){
                    stream = sanitizer.sanitize(stream);
                }
            }

            this.env.getZip().add(f, stream);
            Utils.safelyClose(stream);
        }
    }

    /**
     * A Limiter is constructed with a number, and it can be repeatedly given VirtualFiles for which it will return an
     * InputStream that possibly is adjusted so that the number of bytes the stream can provide, when added to what the
     * Limiter already has seen, won't be more than the limit.
     *
     * If the VirtualFiles's size minus the amount already seen by the Limiter is smaller than the limit, the
     * VirtualFiles's InputStream is simply returned and its size added to the number of bytes the Limiter has seen.
     * Otherwise, the VirtualFiles's InputStream is skipped ahead so that the total number of bytes it will provide
     * before exhaustion will make the total amount seen by the Limiter equal to its limit.
     */
    private static class Limiter {

        private long amountRead = 0;
        private long limit = -1;
        private boolean done = false;

        public Limiter(long limit){
            this.limit = limit;
        }

        public boolean isDone(){
            return done;
        }

        /**
         * @return
         * @throws IOException
         */
        public InputStream limit(File resource) throws IOException {

            InputStream is = new FileInputStream(resource);
            long resourceSize = resource.length();

            // if we're limiting and know we're not going to consume the whole file, we skip
            // ahead so that we get the tail of the file instead of the beginning of it, and we
            // toggle the done switch.
            if(limit != -1){
                long leftToRead = limit - amountRead;
                if(leftToRead < resourceSize){
                    Utils.skip(is, resourceSize - leftToRead);
                    done = true;
                } else {
                    amountRead += resourceSize;
                }
            }
            return is;

        }

    }
}
