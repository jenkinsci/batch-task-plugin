package hudson.plugins.batch_task;

import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
@Bug(2917)
public class RestartTest extends HudsonTestCase {
    public void testRestart() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.scheduleBuild2(0).get();

        // block the build so that nothing escapes from the queue
        hudson.setNumExecutors(0);

        BatchTask t = new BatchTask("test", "echo hello");
        BatchTaskProperty bp = new BatchTaskProperty(t);
        p.addProperty(bp);
        bp.setOwner(p); // work around until 1.279 release
        
        // schedule a build but make sure it stays in the queue
        Queue q = hudson.getQueue();
        q.add(t,9999);
        // reload the queue and make sure it persists fine
        q.save();
        q.clear();
        assertFalse(q.contains(t));
        q.load(); // make sure it's the load operation that resurrected the task correctly.
        assertTrue(q.contains(t));
    }
}
