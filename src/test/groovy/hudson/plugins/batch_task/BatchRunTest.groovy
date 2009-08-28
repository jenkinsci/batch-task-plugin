package hudson.plugins.batch_task

import org.jvnet.hudson.test.HudsonTestCase

/**
 *
 * @author Kohsuke Kawaguchi
 */
public class BatchRunTest extends HudsonTestCase {
    void testBasic() {
        // build on the slave
        def s = createSlave();
        def p = createFreeStyleProject();
        p.assignedLabel = s.selfLabel;
        assertBuildStatusSuccess(p.scheduleBuild2(0).get());

        // add a batch task
        def t = new BatchTask("test", "echo hello");
        def bp = new BatchTaskProperty(t);
        p.addProperty(bp);

        // now this should fail
        BatchRun f = hudson.queue.schedule(t,0).future.get()
        println f.logFile.getText()
    }
}