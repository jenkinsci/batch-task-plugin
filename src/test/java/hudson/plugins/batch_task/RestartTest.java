package hudson.plugins.batch_task;

import hudson.model.FreeStyleProject;
import hudson.model.Queue;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

/**
 * @author Kohsuke Kawaguchi
 */
@Bug(2917)
public class RestartTest {
  
    @Rule 
    public JenkinsRule jenkinsRule = new JenkinsRule();
    
    @Test
    public void testRestart() throws Exception {
        FreeStyleProject p = jenkinsRule.createFreeStyleProject();
        p.scheduleBuild2(0).get();

        // block the build so that nothing escapes from the queue
        jenkinsRule.jenkins.setNumExecutors(0);

        BatchTask t = new BatchTask("test", "echo hello");
        BatchTaskProperty bp = new BatchTaskProperty(t);
        p.addProperty(bp);
        
        // schedule a build but make sure it stays in the queue
        Queue q = jenkinsRule.jenkins.getQueue();
        q.schedule(t,9999);
        // reload the queue and make sure it persists fine
        q.save();
        q.clear();
        assertFalse(q.contains(t));
        q.load(); // make sure it's the load operation that resurrected the task correctly.
        assertTrue(q.contains(t));
    }
}
