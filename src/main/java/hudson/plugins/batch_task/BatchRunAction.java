package hudson.plugins.batch_task;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Build;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;

/**
 * {@link Build} {@link Action} that shows the records of executed tasks.
 * @author Kohsuke Kawaguchi
 */
public final class BatchRunAction implements Action {
    public final AbstractBuild<?,?> owner;
    protected final List<BatchRun> records = new LinkedList<BatchRun>();

    public BatchRunAction(AbstractBuild<?, ?> owner) {
        this.owner = owner;
    }

    public String getIconFileName() {
        return "gear2.gif";
    }

    public String getDisplayName() {
        return Messages.BatchRunAction_DisplayName();
    }

    public String getUrlName() {
        return "batchTasks";
    }

    /**
     * Creates and adds a new reocrd.
     */
    protected BatchRun createRecord(BatchTask task) throws IOException {
        BatchRun r = new BatchRun(new GregorianCalendar(),this,records.size()+1,task);
        records.add(0,r);
        owner.save();
        return r;
    }

    /**
     * Gets run records. Newer ones first.
     */
    public List<BatchRun> getRecords() {
        return Collections.unmodifiableList(records);
    }

    /**
     * Returns the record that has the given ID.
     */
    public BatchRun getRecord(int id) {
        // normally this is where it is
        int idx = records.size()-id;
        if(idx>=0 && id!=0) {
            BatchRun r = records.get(idx);
            if(r.id==id)   return r; // bingo
        }

        // otherwise linear search
        for (BatchRun r : records)
            if (r.id == id) return r;

        return null; // not found
    }

    private Object readResolve() {
        for (BatchRun r : records)
            r.parent = this;
        Collections.sort(records);
        return this;
    }

    public BatchRun getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        return getRecord(Integer.parseInt(token));
    }
}
