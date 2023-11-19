/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Alan Harder
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.batch_task;

import hudson.widgets.HistoryWidget;
import org.htmlunit.html.HtmlPage;

import hudson.Functions;
import hudson.Util;
import hudson.model.Cause.UpstreamCause;
import hudson.model.Cause.UserCause;
import hudson.model.CauseAction;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * Tests for batch tasks plugin.
 * @author Alan Harder
 */
public class BatchTaskTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    /**
     * Verify redirect on attempt to run task when there are no builds.
     */
    @Test
    public void testNoBuilds() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.addProperty(new BatchTaskProperty(new BatchTask("test", "echo hello")));
        JenkinsRule.WebClient wc = r.createWebClient();
        HtmlPage page = TestHelper.assertPost(wc, p.getUrl() + "batchTasks/task/test/execute", "text/html", 200);
        String path = page.getWebResponse().getWebRequest().getUrl().getPath();
        assertTrue("should redirect to noBuilds page: " + path, path.endsWith("/noBuild"));
    }

    /**
     * Verify UserCause is added when user triggers a task.  Check env vars too:
     *  TASK_ID for this run, global and node properties, HUDSON_USER if triggered
     *  by a user.
     */
    @Test
    public void testExecute() throws Exception {
        r.jenkins.getGlobalNodeProperties().add(new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("GLOBAL", "global-property"),
                new EnvironmentVariablesNodeProperty.Entry("OVERRIDE_ME", "foo")));
        r.jenkins.getNodeProperties().add(new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("OVERRIDE_ME", "bar")));
        FreeStyleProject p = r.createFreeStyleProject("execute");
        BatchTask task;
        if (Functions.isWindows()) {
            task = new BatchTask("test",
                    "echo \"%TASK_ID%:%GLOBAL%:%OVERRIDE_ME%:%HUDSON_USER%\"");
        }
        else {
            task = new BatchTask("test",
                    "echo \"$TASK_ID:$GLOBAL:$OVERRIDE_ME:$HUDSON_USER\"\n");
        }
        BatchTaskProperty batchTaskProperty = new BatchTaskProperty(task);
        p.addProperty(batchTaskProperty);
        batchTaskProperty.setOwner(p);
        FreeStyleBuild freeStyleBuild = p.scheduleBuild2(0).get();
        while (freeStyleBuild.isBuilding()) {
            Thread.sleep(100);
        }
        TestHelper.assertPost(r.createWebClient(), p.getUrl() + "batchTasks/task/test/execute", "text/html", 200);
        BatchRun run = task.getLastRun();
        assertNotNull("task did not run", run);
        CauseAction ca = run.getAction(CauseAction.class);
        assertNotNull("CauseAction not found", ca);
        assertEquals("Cause type", UserCause.class.getName(),
                ca.getCauses().get(0).getClass().getName());
        String log = Util.loadFile(run.getLogFile());
        assertTrue("Expected 1-1:global-property:bar:anonymous in task output: " + log,
                log.contains("1-1:global-property:bar:anonymous"));

        Iterable<BatchRun> iterable = task.getRuns();
        List<BatchRun> runs = new ArrayList<>();
        iterable.forEach(runs::add);

        assertEquals("Runs count is 1", 1, runs.size());
    }

    /**
     * Verify UpstreamCause is added when a job triggers a task.
     */
    @Test
    public void testInvoker() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("tasker");
        BatchTask task = new BatchTask("test", "echo hello\n");
        BatchTaskProperty batchTaskProperty = new BatchTaskProperty(task);
        p.addProperty(batchTaskProperty);
        batchTaskProperty.setOwner(p);
        p.scheduleBuild2(0).get();
        FreeStyleProject up = r.createFreeStyleProject("invoker");
        up.getPublishersList().add(new BatchTaskInvoker(
                new BatchTaskInvoker.Config[] { new BatchTaskInvoker.Config(p.getFullName(), "test") },
                Result.SUCCESS));
        up.scheduleBuild2(0).get();
        Queue.Item q = r.jenkins.getQueue().getItem(task);
        if (q!=null) q.getFuture().get(5, TimeUnit.SECONDS);
        BatchRun run = task.getLastRun();
        assertNotNull("task did not run", run);
        CauseAction ca = run.getAction(CauseAction.class);
        assertNotNull("CauseAction not found", ca);
        assertEquals("Cause type", UpstreamCause.class.getName(),
                ca.getCauses().get(0).getClass().getName());
    }
}
