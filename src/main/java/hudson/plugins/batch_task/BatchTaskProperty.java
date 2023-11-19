package hudson.plugins.batch_task;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.*;
import hudson.util.EditDistance;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.io.IOException;

/**
 * Batch tasks added as {@link JobProperty}.
 *
 * @author Kohsuke Kawaguchi
 */
public class BatchTaskProperty extends JobProperty<AbstractProject<?,?>> {

    private volatile BatchTask[] tasks;

    public BatchTaskProperty(BatchTask... tasks) {
        this.tasks = tasks;
    }

    public BatchTaskProperty(Collection<BatchTask> tasks) {
        this((BatchTask[])tasks.toArray(new BatchTask[tasks.size()]));
    }

    @Override
    protected void setOwner(AbstractProject<?, ?> owner) {
        super.setOwner(owner);
        for (BatchTask t : tasks) {
            t.setParentProperty(this);
        }
    }

    public AbstractProject<?,?> getOwner() {
        return owner;
    }

    public BatchTask getTask(String name) {
        for (BatchTask t : tasks)
            if(t.name.equals(name))
                return t;
        return null;
    }

    public List<BatchTask> getTasks() {
        return Collections.unmodifiableList(Arrays.asList(tasks));
    }

    public synchronized void removeTask(BatchTask t) throws IOException {
        ArrayList<BatchTask> l = new ArrayList<BatchTask>(Arrays.asList(tasks));
        if(l.remove(t)) {
            tasks = l.toArray(new BatchTask[l.size()]);
            getOwner().save();
        }
    }

    /**
     * Finds the {@link BatchTask} that has the closest name. Used for error diagnostics.
     */
    public BatchTask findNearestTask(String name) {
        String[] names = new String[tasks.length];
        for (int i = 0; i < tasks.length; i++)
            names[i] = tasks[i].name;

        name = EditDistance.findNearest(name,names);
        return getTask(name);
    }

    @Override
    public Collection<? extends Action> getJobActions(AbstractProject<?,?> job) {
        return Collections.singletonList(new BatchTaskAction(job, this));
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends JobPropertyDescriptor {
        public DescriptorImpl() {
            super(BatchTaskProperty.class);
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return AbstractProject.class.isAssignableFrom(jobType);
        }

        public String getDisplayName() {
            return Messages.BatchTaskProperty_DisplayName();
        }

        @Override
        public BatchTaskProperty newInstance(@Nullable StaplerRequest req, JSONObject formData) throws FormException {
            if(req != null && req.getParameter("batch-tasks.on")!=null)
                return new BatchTaskProperty(req.bindParametersToList(BatchTask.class, "batch-task."));
            else
                return null;
        }
    }
}
