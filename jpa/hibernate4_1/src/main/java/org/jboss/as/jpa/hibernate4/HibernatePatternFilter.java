/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.jpa.hibernate4;

import static org.jipijapa.JipiLogger.JPA_LOGGER;

import org.jboss.modules.Resource;

/**
 * Mock work of NativeScanner matching.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author Scott Marlow
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class HibernatePatternFilter {

    private static final String SLASH = "/";
    private static final String PREFIX = "**/*";

    private HibernatePatternFilter() {
        // forbidden instantiation
    }

    static boolean matches(String pattern, final Resource resource) {
        if (pattern == null) {
            throw JPA_LOGGER.nullVar("pattern");
        }

        boolean exact = !pattern.contains(SLASH); // no path split or glob
        pattern = pattern.startsWith(PREFIX) ? pattern.substring(4) : pattern;
        return exact ? resource.getName().equals(pattern) : resource.getName().endsWith(pattern);
    }

}
