package hudson.plugins.batch_task;

import hudson.model.AbstractBuild;
import hudson.model.AbstractModelObject;
import hudson.model.AbstractProject;
import hudson.model.BallColor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.Executable;
import hudson.model.ResourceList;
import hudson.model.Result;
import hudson.util.Iterators;
import hudson.widgets.BuildHistoryWidget;
import hudson.widgets.HistoryWidget;
import hudson.widgets.HistoryWidget.Adapter;
import hudson.security.ACL;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A batch task.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class BatchTask extends AbstractModelObject implements Queue.Task {
    /**
     * Name of this task. Used for display.
     */
    public final String name;
    /**
     * Shell script to be executed.
     */
    public final String script;

    /*package*/ transient AbstractProject<?,?> owner;

    /*package*/ transient BatchTaskProperty parent;

    @DataBoundConstructor
    public BatchTask(String name, String script) {
        this.name = name;
        this.script = script;
    }

    public BatchTaskProperty getParent() {
        return parent;
    }

    public String getSearchUrl() {
        return name;
    }

    public String getDisplayName() {
        return name;
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
        Node on = owner.getLastBuiltOn();
        if(on==null)    return null;
        return on.getSelfLabel();
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
            for (BatchRun br : bra.records) {
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

    /**
     * Gets all the run records.
     */
    public Iterable<BatchRun>  getRuns() {
        return new Iterable<BatchRun>() {
            public Iterator<BatchRun> iterator() {
                return new Iterators.FlattenIterator<BatchRun,AbstractBuild<?,?>>(owner.getBuilds().iterator()) {
                    protected Iterator<BatchRun> expand(AbstractBuild<?,?> b) {
                        BatchRunAction a = b.getAction(BatchRunAction.class);
                        if(a==null) return Iterators.empty();
                        else        return a.getRecords().iterator();
                    }
                };
            }
        };
    }

    public HistoryWidget createHistoryWidget() {
        return new BuildHistoryWidget<BatchRun>(this,getRuns(),ADAPTER);
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
     * Gets the expected build number assigned to the next run.
     *
     * @return string like "5-3"
     */
    public String getNextBuildNumber() {
        AbstractBuild<?,?> lb = owner.getLastBuild();
        if(lb==null)    return "0-0";

        int id=0;
        BatchRunAction records = lb.getAction(BatchRunAction.class);
        if(records!=null)
            id=records.getRecords().size();

        return lb.getNumber()+"-"+id;
    }

    /**
     * Returns the {@link ACL} for this object.
     */
    public ACL getACL() {
        // TODO: this object should have its own ACL
        return Hudson.getInstance().getACL();
    }

    public void checkAbortPermission() {
        // TODO: shall we define our own permission here?
        // see the hasAbortPermission method below
        // replace to AbstractProject.ABORT after 1.169 release
        getACL().checkPermission(AbstractProject.BUILD);
    }

    public boolean hasAbortPermission() {
        return getACL().hasPermission(AbstractProject.BUILD);
    }

    /**
     * {@link BatchTask} requires exclusive access to the workspace.
     */
    public ResourceList getResourceList() {
        return new ResourceList().w(owner.getWorkspaceResource());
    }

    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        Matcher m = BUILD_NUMBER_PATTERN.matcher(token);
        if(m.matches()) {
            AbstractBuild<?,?> b = owner.getBuildByNumber(Integer.parseInt(m.group(1)));
            if(b==null)     return null;
            BatchRunAction a = b.getAction(BatchRunAction.class);
            if(a==null)     return null;
            return a.getRecord(Integer.parseInt(m.group(2)));
        }
        return null;
    }

    /**
     * Schedules the execution
     */
    public synchronized void doExecute( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;
        Hudson.getInstance().getQueue().add(this,0);
        rsp.forwardToPreviousPage(req);
    }

    /**
     * Deletes this task.
     */
    public synchronized void doDoDelete( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;
        getParent().removeTask(this);
        rsp.sendRedirect2("../..");
    }

    private static final Adapter<BatchRun> ADAPTER = new Adapter<BatchRun>() {
        public int compare(BatchRun record, String key) {
            int[] lhs = parse(record.getNumber());
            int[] rhs = parse(key);

            int d = lhs[0]-rhs[0];
            if(d!=0)    return d;
            return lhs[1]-rhs[1];
        }

        public String getKey(BatchRun record) {
            return record.getNumber();
        }

        public boolean isBuilding(BatchRun record) {
            return record.isRunning();
        }

        public String getNextKey(String key) {
            int[] r = parse(key);
            r[1]++;
            return r[0]+"-"+r[1];
        }

        private int[] parse(String num) {
            Matcher m = BUILD_NUMBER_PATTERN.matcher(num);
            if(m.matches())
                return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) };
            return new int[]{0,0};
        }
    };

    private static final Pattern BUILD_NUMBER_PATTERN = Pattern.compile("(\\d+)-(\\d+)");
}
