/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
package org.jboss.as.jdr;

import org.jboss.as.jdr.commands.JdrEnvironment;
import org.jboss.as.jdr.util.JdrZipFile;
import org.jboss.as.jdr.util.PatternSanitizer;
import org.jboss.as.jdr.util.XMLSanitizer;
import org.jboss.as.jdr.vfs.Filters;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.InputStream;

import static org.junit.Assert.*;

public class JdrTestCase {

    @Test
    public void testJdrZipName() throws Exception {
        JdrEnvironment env = new JdrEnvironment();
        env.setJbossHome("/foo/bar/baz");
        env.setHostControllerName("host");
        env.setOutputDirectory("target");
        String name;
        JdrZipFile zf = new JdrZipFile(env);
        try {
            name = zf.name();
            zf.close();
        }
        finally {
            safeClose(zf);
            File f = new File(zf.name());
            f.delete();
        }

        assertTrue(name.endsWith(".zip"));
        assertTrue(name.contains("host"));
        assertTrue(name.startsWith("target"));
    }

    @Test
    public void testBlackListFilter() {
        FileFilter blf = Filters.regexBlackList();
        assertFalse(blf.accept(new File("/foo/bar/baz/mgmt-users.properties")));
        assertFalse(blf.accept(new File("/foo/bar/baz/application-users.properties")));
    }

    @Test
    public void testXMLSanitizer() throws Exception {
        String xml = "<test><password>foobar</password></test>";
        InputStream is = new ByteArrayInputStream(xml.getBytes());
        XMLSanitizer s = new XMLSanitizer("//password", Filters.TRUE);
        InputStream res = s.sanitize(is);
        byte [] buf = new byte [res.available()];
        res.read(buf);
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><test><password/></test>", new String(buf));
    }

    @Test
    public void testPatternSanitizer() throws Exception {
        String propf = "password=123456";
        InputStream is = new ByteArrayInputStream(propf.getBytes());
        PatternSanitizer s = new PatternSanitizer("password=.*", "password=*", Filters.TRUE);
        InputStream res = s.sanitize(is);
        byte [] buf = new byte [res.available()];
        res.read(buf);
        assertEquals("password=*", new String(buf));
    }

    @Test
    public void testWildcardFilterAcceptAnything() throws Exception {
        FileFilter filter = Filters.wildcard("*");
        File good = new File("/this/is/a/test.txt");
        assertTrue(filter.accept(good));
    }

    @Test
    public void testWildcardFilterPrefixGlob() throws Exception {
        FileFilter filter = Filters.wildcard("*.txt");
        File good = new File("/this/is/a/test.txt");
        File bad = new File("/this/is/a/test.xml");
        File wingood = new File("/C:/this/is/a/test.txt");
        File winbad = new File("/C:/this/is/a/test.xml");
        assertTrue(filter.accept(good));
        assertFalse(filter.accept(bad));
        assertTrue(filter.accept(wingood));
        assertFalse(filter.accept(winbad));
    }

    @Test
    public void testWildcardFilterSuffixGlob() throws Exception {
        FileFilter filter = Filters.wildcard("/this/is*");
        File good = new File("/this/is/a/test.txt");
        File bad = new File("/that/is/a/test.txt");
        File wingood = new File("/C:/this/is/a/test.txt");
        File winbad = new File("/C:/that/is/a/test.txt");
        assertTrue(filter.accept(good));
        assertFalse(filter.accept(bad));
        assertTrue(filter.accept(wingood));
        assertFalse(filter.accept(winbad));
    }

    @Test
    public void testWildcardFilterMiddleGlob() throws Exception {
        FileFilter filter = Filters.wildcard("/this*test.txt");
        File good = new File("/this/is/a/test.txt");
        File bad1 = new File("/that/is/a/test.txt");
        File bad2 = new File("/this/is/a/test.xml");
        File win = new File("/C:/this/is/a/test.txt");
        File winbad = new File("/C:/this/is/a/test.xml");
        assertTrue(filter.accept(good));
        assertTrue(filter.accept(win));
        assertFalse(filter.accept(bad1));
        assertFalse(filter.accept(bad2));
        assertFalse(filter.accept(winbad));
    }

    private void safeClose(JdrZipFile zf) {
        try {
            zf.close();
        } catch (Exception ignored) { }
    }

}
