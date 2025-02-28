/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.smoke.welcome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.net.URL;

import org.htmlunit.WebClient;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.DomNodeList;
import org.htmlunit.html.HtmlPage;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for the WildFly welcome page.
 * <p>
 * Verifies the title of the page and links to the documentation, quickstarts and the admin console.
 */
@RunWith(Arquillian.class)
@RunAsClient
public class WelcomePageTestCase {

    public static final String TITLE = "Welcome to WildFly";
    public static final String HEADER_TEXT = "Welcome to WildFly";
    public static final String DOCUMENTATION_LINK_TEXT = "Documentation";
    public static final String QUICKSTARTS_LINK_TEXT = "Quickstarts";
    public static final String ADMINISTRATION_CONSOLE_LINK_TEXT = "Administration Console";

    @Test
    public void testWelcomePage() throws Exception {
        try (WebClient webClient = new WebClient()) {
            URL url = TestSuiteEnvironment.getHttpUrl();
            HtmlPage welcomePage = webClient.getPage(url.toExternalForm());
            assertEquals(TITLE, welcomePage.getTitleText());

            DomNode header = welcomePage.querySelector("h1");
            assertEquals(HEADER_TEXT, header.asNormalizedText());

            DomNodeList<DomNode> links = welcomePage.querySelectorAll("a");
            assertLink(links, DOCUMENTATION_LINK_TEXT);
            assertLink(links, QUICKSTARTS_LINK_TEXT);
            assertLink(links, ADMINISTRATION_CONSOLE_LINK_TEXT);
        }
    }

    private void assertLink(DomNodeList<DomNode> links, String text) {
        for (DomNode link : links) {
            if (text.equals(link.asNormalizedText())) {
                return;
            }
        }
        fail("Link " + text + " not found!");
    }
}
