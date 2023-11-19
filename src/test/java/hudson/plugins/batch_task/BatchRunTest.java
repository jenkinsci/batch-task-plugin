package hudson.plugins.batch_task;

import hudson.model.Computer;
import hudson.model.Node;
import org.junit.Rule;

import hudson.model.FreeStyleProject;

import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class BatchRunTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void testBasic() throws Exception {
        // build on the slave
        Computer computer = r.jenkins.createComputer();
        Node node = computer.getNode();
        FreeStyleProject freeStyleProject = r.createFreeStyleProject();
        freeStyleProject.setAssignedNode(node);
        r.assertBuildStatusSuccess(freeStyleProject.scheduleBuild2(0).get());

        // add a batch task
        BatchTask batchTask = new BatchTask("test", "echo hello");
        BatchTaskProperty batchTaskProperty = new BatchTaskProperty(batchTask);
        freeStyleProject.addProperty(batchTaskProperty);
        batchTaskProperty.setOwner(freeStyleProject);

        // now this should fail
        r.jenkins.getQueue().schedule(batchTask, 0).getFuture().get();
    }
}