/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.web.headers;

import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.integration.jaxrs.packaging.war.WebXml;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test if certain response code wont erase content-type
 *
 * @author baranowb
 */
@RunWith(Arquillian.class)
@RunAsClient
public class ResponseCodeTestCase {
    private static final HttpClient HTTP_CLIENT = HttpClients.createDefault();

    @Deployment(testable = false)
    public static Archive<?> deploy() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, "jaxrsnoap.war");
        war.addClass(RSCodeResponder.class);
        war.addAsWebInfResource(WebXml.get("<servlet-mapping>\n"
                + "        <servlet-name>jakarta.ws.rs.core.Application</servlet-name>\n"
                + "        <url-pattern>/jaxrs/*</url-pattern>\n" + "    </servlet-mapping>\n" + "\n"), "web.xml");
        return war;
    }

    @ArquillianResource
    private URL url;

    // TODO: redo once/if Arq will support JUnitParams?

    @Test
    public void test200() throws Exception {
        final HttpGet get = new HttpGet(url.toExternalForm() + "jaxrs/test/returnCode/200");
        final HttpResponse response = HTTP_CLIENT.execute(get);
        doContentTypeChecks(response, 200);
    }

    @Test
    public void test300() throws Exception {
        final HttpGet get = new HttpGet(url.toExternalForm() + "jaxrs/test/returnCode/300");
        final HttpResponse response = HTTP_CLIENT.execute(get);
        doContentTypeChecks(response, 300);
    }

    @Test
    public void test400() throws Exception {
        final HttpGet get = new HttpGet(url.toExternalForm() + "jaxrs/test/returnCode/400");
        final HttpResponse response = HTTP_CLIENT.execute(get);
        doContentTypeChecks(response, 400);
    }

    @Test
    public void test404() throws Exception {
        final HttpGet get = new HttpGet(url.toExternalForm() + "jaxrs/test/returnCode/404");
        final HttpResponse response = HTTP_CLIENT.execute(get);
        doContentTypeChecks(response, 404);
    }

    @Test
    public void test500() throws Exception {
        final HttpGet get = new HttpGet(url.toExternalForm() + "jaxrs/test/returnCode/500");
        final HttpResponse response = HTTP_CLIENT.execute(get);
        doContentTypeChecks(response, 500);
    }
    /*
    This test is commented out as it is testing server info data and not response codes.
    It should be moved to maybe somke testsuite that works on top of "real" distribution
    and not trimmed down one that is used in rest of testsuite.
     */
   /*  @Test
    public void testServerInfo() throws Exception {
        final HttpGet get = new HttpGet(url.toExternalForm() + "jaxrs/test/server/info");
        final HttpResponse response = HTTP_CLIENT.execute(get);
         final HttpEntity entity = response.getEntity();
         Assert.assertNotNull("Null entity!", entity);
         final String content = EntityUtils.toString(response.getEntity());
         Assert.assertTrue("Wrong content! " + content, content.matches("WildFly .*\\(WildFly Core .*\\) - .*"));
    }*/

    private void doContentTypeChecks(final HttpResponse response, final int code) throws Exception {
        doContentTypeChecks(response, code, true);
    }

    public void doContentTypeChecks(final HttpResponse response, final int code, final boolean expectContent) throws Exception {
        Assert.assertEquals("Wrong response code!", code, response.getStatusLine().getStatusCode());
        Assert.assertEquals("Missing content type!", 1, response.getHeaders("Content-Type").length);
        if (expectContent) {
            final HttpEntity entity = response.getEntity();
            Assert.assertNotNull("Null entity!", entity);
            final String content = EntityUtils.toString(response.getEntity());
            Assert.assertEquals("Wrong content!", RSCodeResponder.CONTENT, content);
        }
    }

}
