package hudson.plugins.batch_task;

import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;


/**
 * @author Kohsuke Kawaguchi
 */
@Issue("2917")
public class RestartTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void testRestart() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.scheduleBuild2(0).get();

        // block the build so that nothing escapes from the queue
        r.jenkins.setNumExecutors(0);

        BatchTask t = new BatchTask("test", "echo hello");
        BatchTaskProperty bp = new BatchTaskProperty(t);
        p.addProperty(bp);
        
        // schedule a build but make sure it stays in the queue
        Queue q = r.jenkins.getQueue();
        q.schedule(t,9999);
        // reload the queue and make sure it persists fine
        q.save();
        q.clear();
        assertFalse(q.contains(t));
        q.load(); // make sure it's the load operation that resurrected the task correctly.
        assertTrue(q.contains(t));
    }
}
