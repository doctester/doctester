/**
 * Copyright (C) 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.doctester;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class DocTesterTest extends DocTester {

    public static String EXPECTED_FILENAME = DocTesterTest.class.getName() + ".html";

    @Test
    public void testThatIndexFileWritingWorks() throws Exception {

        doCreateSomeTestOuputForDoctest();

        finishDocTest();

        File expectedIndex = new File("target/site/doctester/index.html");

        Assert.assertTrue(expectedIndex.exists());

        assertThatFileContainsText(expectedIndex, "index");

    }

    @Test
    public void testThatIndexWritingOutDoctestFileWorks() throws Exception {

        doCreateSomeTestOuputForDoctest();

        finishDocTest();

        File expectedDoctestfile = new File("target/site/doctester/" + EXPECTED_FILENAME);
        File expectedIndexFile = new File("target/site/doctester/index.html");

        // just a simple test to make sure the name is written somewhere in the file.
        assertThatFileContainsText(expectedDoctestfile, DocTesterTest.class.getSimpleName());

        // just a simple test to make sure that index.html contains a "link" to the doctest file.
        assertThatFileContainsText(expectedIndexFile, EXPECTED_FILENAME);

    }

    @Test
    public void testThatCopyingOfCustomDoctesterCssWorks() throws Exception {

        doCreateSomeTestOuputForDoctest();

        finishDocTest();

        File expectedDoctestfile = new File("target/site/doctester/" + EXPECTED_FILENAME);
        File expectedCustomCssFile = new File("target/site/doctester/custom_doctester_stylesheet.css");

        // just a simple test to make sure the name is written somewhere in the file.
        assertThatFileContainsText(expectedDoctestfile, "custom_doctester_stylesheet.css");

        // just a simple test to make sure that index.html contains a "link" to the doctest file.
        assertThatFileContainsText(expectedCustomCssFile, "body");

    }

    @Test(expected = IllegalStateException.class)
    public void testThatUsageOfTestBrowserWithoutSpecifyingGetTestUrlIsNotAllowed() {

        testServerUrl();

    }

    @Test
    public void testThatAssertionFailureGetsWrittenToDoctesterHtmlFile() throws Exception {

        boolean gotTestFailure = false;

        try {
            sayAndAssertThat("This will go wrong", false, is(true));
        } catch (AssertionError assertionError) {
            gotTestFailure = true;
        }

        assertThat(gotTestFailure, is(true));

        finishDocTest();

        File expectedDoctestfile = new File("target/site/doctester/" + DocTesterTest.EXPECTED_FILENAME);

		// this makes sure that the correct alert type is used together
        // with proper escaping and replacement of \n values...
        assertThatFileContainsText(expectedDoctestfile,
                "<div class=\"alert alert-danger\">\n"
                + "java.lang.AssertionError: <br/>Expected: is &lt;true&gt;<br/>     but: was &lt;false&gt;");

    }

    public void doCreateSomeTestOuputForDoctest() {

        sayNextSection("another fun heading!");
        say("and a very long text...!");

    }

    public static void assertThatFileContainsText(File file, String text) throws IOException {

        String content = Files.toString(file, Charsets.UTF_8);
        Assert.assertTrue(content.contains(text));

    }

}
