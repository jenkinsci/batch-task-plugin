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

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Util;
import hudson.model.Cause.UpstreamCause;
import hudson.model.Cause.UserCause;
import hudson.model.CauseAction;
import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import java.util.concurrent.TimeUnit;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * Tests for batch tasks plugin.
 * @author Alan.Harder@sun.com
 */
public class BatchTaskTest extends HudsonTestCase {

    /**
     * Verify redirect on attempt to run task when there are no builds.
     */
    public void testNoBuilds() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.addProperty(new BatchTaskProperty(new BatchTask("test", "echo hello")));
        WebClient wc = new WebClient();
        HtmlPage page = wc.getPage(p, "batchTasks/task/test/execute");
        String path = page.getWebResponse().getUrl().getPath();
        assertTrue("should redirect to noBuilds page: " + path, path.endsWith("/noBuild"));
    }

    /**
     * Verify UserCause is added when user triggers a task.  Check env vars too:
     *  TASK_ID for this run, global and node properties, HUDSON_USER if triggered
     *  by a user.
     */
    public void testExecute() throws Exception {
        hudson.getGlobalNodeProperties().add(new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("GLOBAL", "global-property"),
                new EnvironmentVariablesNodeProperty.Entry("OVERRIDE_ME", "foo")));
        hudson.getNodeProperties().add(new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("OVERRIDE_ME", "bar")));
        FreeStyleProject p = createFreeStyleProject("execute");
        BatchTask task = new BatchTask("test",
                "echo \"$TASK_ID:$GLOBAL:$OVERRIDE_ME:$HUDSON_USER\"\n");
        p.addProperty(new BatchTaskProperty(task));
        p.scheduleBuild2(0).get();
        new WebClient().getPage(p, "batchTasks/task/test/execute");
        Queue.Item q = hudson.getQueue().getItem(task);
        if (q!=null) q.getFuture().get(5, TimeUnit.SECONDS);
        BatchRun run = task.getLastRun();
        assertNotNull("task did not run", run);
        CauseAction ca = run.getAction(CauseAction.class);
        assertNotNull("CauseAction not found", ca);
        assertEquals("Cause type", UserCause.class.getName(),
                ca.getCauses().get(0).getClass().getName());
        String log = Util.loadFile(run.getLogFile());
        assertTrue("Expected 1-1:global-property:bar:anonymous in task output: " + log,
                log.contains("1-1:global-property:bar:anonymous"));
    }

    /**
     * Verify UpstreamCause is added when a job triggers a task.
     */
    public void testInvoker() throws Exception {
        FreeStyleProject p = createFreeStyleProject("tasker");
        BatchTask task = new BatchTask("test", "echo hello\n");
        p.addProperty(new BatchTaskProperty(task));
        p.scheduleBuild2(0).get();
        FreeStyleProject up = createFreeStyleProject("invoker");
        up.getPublishersList().add(new BatchTaskInvoker(
                new BatchTaskInvoker.Config[] { new BatchTaskInvoker.Config(p.getFullName(), "test") },
                Result.SUCCESS));
        up.scheduleBuild2(0).get();
        Queue.Item q = hudson.getQueue().getItem(task);
        if (q!=null) q.getFuture().get(5, TimeUnit.SECONDS);
        BatchRun run = task.getLastRun();
        assertNotNull("task did not run", run);
        CauseAction ca = run.getAction(CauseAction.class);
        assertNotNull("CauseAction not found", ca);
        assertEquals("Cause type", UpstreamCause.class.getName(),
                ca.getCauses().get(0).getClass().getName());
    }
}
