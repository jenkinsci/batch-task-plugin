package hudson.plugins.batch_task;

import hudson.model.AbstractProject;
import hudson.model.Action;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.util.List;

/**
 * Shows all the tasks.
 *
 * @author Kohsuke Kawaguchi
 */
public class BatchTaskAction implements Action {
    public final AbstractProject<?,?> project;
    public final List<BatchTask> tasks;

    public BatchTaskAction(AbstractProject<?,?> project, List<BatchTask> tasks) {
        this.project = project;
        this.tasks = tasks;
    }

    public String getIconFileName() {
        if(tasks.isEmpty()) return null;
        return "gear2.gif";
    }

    public String getDisplayName() {
        if(tasks.size()>1)  return "Tasks";
        return "Task";
    }

    public String getUrlName() {
        return "batchTasks";
    }

    public BatchTask getDynamic(String name, StaplerRequest req, StaplerResponse rsp) {
        return getTask(name);
    }

    public BatchTask getTask(String name) {
        for (BatchTask t : tasks) {
            if(t.name.equals(name))
                return t;
        }
        return null;
    }
}
