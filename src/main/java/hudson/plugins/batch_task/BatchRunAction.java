package hudson.plugins.batch_task;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Build;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * {@link Build} {@link Action} that shows the records of executed tasks.
 * @author Kohsuke Kawaguchi
 */
public final class BatchRunAction implements Action {
    public final AbstractBuild<?,?> owner;
    protected final List<BatchRun> records = new ArrayList<BatchRun>();

    public BatchRunAction(AbstractBuild<?, ?> owner) {
        this.owner = owner;
    }

    public String getIconFileName() {
        return "gear2.gif";
    }

    public String getDisplayName() {
        return "Executed Tasks";
    }

    public String getUrlName() {
        return "batchTasks";
    }

    /**
     * Creates and adds a new reocrd.
     */
    protected BatchRun createRecord(BatchTask task) throws IOException {
        BatchRun r = new BatchRun(new GregorianCalendar(),this,records.size(),task);
        records.add(r);
        owner.save();
        return r;
    }

    public List<BatchRun> getRecords() {
        return Collections.unmodifiableList(records);
    }

    private Object readResolve() {
        for (BatchRun r : records)
            r.parent = this;
        return this;
    }

    public BatchRun getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        int i = Integer.parseInt(token);
        return records.get(i);
    }
}
