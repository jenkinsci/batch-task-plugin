package hudson.plugins.batch_task;

import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Batch tasks added as {@link JobProperty}.
 *
 * @author Kohsuke Kawaguchi
 */
public class BatchTaskProperty extends JobProperty<AbstractProject<?,?>> {

    private final BatchTask[] tasks;

    public BatchTaskProperty(BatchTask... tasks) {
        this.tasks = tasks;
    }

    public BatchTaskProperty(Collection<BatchTask> tasks) {
        this((BatchTask[])tasks.toArray(new BatchTask[tasks.size()]));
    }

    protected void setOwner(AbstractProject<?, ?> owner) {
        super.setOwner(owner);
        readResolve(); // set up owners
    }

    public List<BatchTask> getTasks() {
        return Collections.unmodifiableList(Arrays.asList(tasks));
    }

    @Override
    public Action getJobAction(AbstractProject<?,?> job) {
        return new BatchTaskAction(job, getTasks());
    }

    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    protected Object readResolve() {
        //for (BatchTask t : tasks)
        //    t.owner = owner;
        return this;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends JobPropertyDescriptor {
        public DescriptorImpl() {
            super(BatchTaskProperty.class);
            load();
        }

        public boolean isApplicable(Class<? extends Job> jobType) {
            return AbstractProject.class.isAssignableFrom(jobType);
        }

        public String getDisplayName() {
            return "Batch tasks";
        }

        public BatchTaskProperty newInstance(StaplerRequest req) throws FormException {
            if(req.getParameter("batch-tasks.on")!=null)
                return new BatchTaskProperty(req.bindParametersToList(BatchTask.class, "batch-task."));
            else
                return null;
        }
    }
}
