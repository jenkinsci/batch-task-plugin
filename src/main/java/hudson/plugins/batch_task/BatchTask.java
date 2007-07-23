package hudson.plugins.batch_task;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.BallColor;
import hudson.model.ResourceList;
import hudson.model.Queue.Executable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * A batch task.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class BatchTask implements Queue.Task {
    /**
     * Name of this task. Used for display.
     */
    public final String name;
    /**
     * Shell script to be executed.
     */
    public final String script;

    /*package*/ transient AbstractProject<?,?> owner;

    @DataBoundConstructor
    public BatchTask(String name, String script) {
        this.name = name;
        this.script = script;
    }

    public String getDisplayName() {
        return owner.getDisplayName()+" \u00BB "+name;
    }

    public String getFullDisplayName() {
        return owner.getFullDisplayName()+" \u00BB "+name;
    }

    public boolean isBuildBlocked() {
        return owner.isBuildBlocked();
    }

    public String getWhyBlocked() {
        return owner.getWhyBlocked();
    }

    public String getName() {
        return name;
    }

    public long getEstimatedDuration() {
        BatchRun b = getLastSuccessfulRun();
        if(b==null)     return -1;

        long duration = b.getDuration();
        if(duration==0) return -1;

        return duration;
    }

    public Label getAssignedLabel() {
        return owner.getLastBuiltOn().getSelfLabel();
    }

    public Node getLastBuiltOn() {
        return owner.getLastBuiltOn();
    }

    public String getBuildStatusUrl() {
        return getIconColor()+".gif";
    }

    public BallColor getIconColor() {
        BatchRun r = getLastRun();
        if(r==null) return BallColor.GREY;
        else        return r.getIconColor();
    }

    /**
     * Obtains the latest {@link BatchRun} record.
     */
    public BatchRun getLastRun() {
        for(AbstractBuild<?,?> b : owner.getBuilds()) {
            BatchRunAction bra = b.getAction(BatchRunAction.class);
            if(bra==null)   continue;
            for( int i=bra.records.size()-1; i>=0; i-- ) {
                BatchRun br = bra.records.get(i);
                if(br.taskName.equals(name))
                    return br;
            }
        }
        return null;
    }

    public BatchRun getLastSuccessfulRun() {
        for(BatchRun r=getLastRun(); r!=null; r=r.getPrevious())
            if(r.getResult()== Result.SUCCESS)
                return r;
        return null;
    }

    public BatchRun getLastFailedRun() {
        for(BatchRun r=getLastRun(); r!=null; r=r.getPrevious())
            if(r.getResult()==Result.FAILURE)
                return r;
        return null;
    }

    public Executable createExecutable() throws IOException {
        AbstractBuild<?,?> lb = owner.getLastBuild();
        BatchRunAction records = lb.getAction(BatchRunAction.class);
        if(records==null) {
            records = new BatchRunAction(lb);
            lb.addAction(records);
            // we don't need to save it yet.
        }

        return records.createRecord(this);
    }

    /**
     * {@link BatchTask} requires exclusive access to the workspace.
     */
    public ResourceList getResourceList() {
        return new ResourceList().w(owner.getWorkspaceResource());
    }

    /**
     * Schedules the execution
     */
    public synchronized void doExecute( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;
        Hudson.getInstance().getQueue().add(this,0);
        rsp.sendRedirect2("..");
    }
}
