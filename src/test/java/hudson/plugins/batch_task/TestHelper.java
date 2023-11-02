package hudson.plugins.batch_task;

import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

public class TestHelper {
    /**
     * Performs an HTTP POST request to the relative url.
     *
     * @param webClient the client
     * @param relative the url relative to the context path
     * @param expectedContentType if expecting specific content type or null if not
     * @param expectedStatus if expecting a http status code or null if not
     * @throws IOException if so
     */
    public static HtmlPage assertPost(JenkinsRule.WebClient webClient, String relative,
                                      String expectedContentType, Integer expectedStatus) throws IOException {
        WebRequest request = new WebRequest(webClient.createCrumbedUrl(relative), HttpMethod.POST);
        try {
            HtmlPage p = webClient.getPage(request);
            if (expectedContentType != null) {
                assertThat(p.getWebResponse().getContentType(), is(expectedContentType));
            }
            if (expectedStatus != null) {
                assertEquals(expectedStatus.intValue(), p.getWebResponse().getStatusCode());
            }
            return p;
        } catch (FailingHttpStatusCodeException e) {
            if (expectedStatus != null) {
                assertEquals(expectedStatus.intValue(), e.getStatusCode());
                return null;
            } else {
                throw e;
            }
        }
    }
}
