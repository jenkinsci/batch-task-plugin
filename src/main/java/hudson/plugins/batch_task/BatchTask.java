package hudson.plugins.batch_task;

import hudson.model.*;
import hudson.model.Cause.UserCause;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.SubTask;
import hudson.security.AccessControlled;
import hudson.util.Iterators;
import hudson.widgets.BuildHistoryWidget;
import hudson.widgets.HistoryWidget;
import hudson.widgets.HistoryWidget.Adapter;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import jenkins.model.lazy.LazyBuildMixIn;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.thoughtworks.xstream.converters.basic.AbstractSingleValueConverter;
import org.kohsuke.stapler.verb.POST;
import org.springframework.security.access.AccessDeniedException;

/**
 * A batch task.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class BatchTask extends AbstractModelObject implements Queue.Task, AccessControlled {
    private static final Logger LOGGER = Logger.getLogger(BatchTask.class.getName());
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

    /*package*/ transient BatchJob job;

    @DataBoundConstructor
    public BatchTask(String name, String script) {
        this.name = name.trim();
        this.script = script;
    }

    void setParentProperty(BatchTaskProperty parent) {
        this.parent = parent;
        this.owner = parent.getOwner();
        this.job = new BatchJob(new BatchJobItemGroup(parent), name, this);
    }

    public static class BatchJobItemGroup implements ItemGroup {

        /*package*/ transient BatchTaskProperty parent;

        public BatchJobItemGroup(BatchTaskProperty parent) {
            this.parent = parent;
        }

        @Override
        public String getFullName() {
            return parent.getOwner().getFullName();
        }

        @Override
        public String getFullDisplayName() {
            return parent.getOwner().getFullDisplayName();
        }

        @Override
        public Collection getItems() {
            return parent.getTasks();
        }

        @Override
        public String getUrl() {
            return parent.getOwner().getUrl();
        }

        @Override
        public String getUrlChildPrefix() {
            return "FIXME";
        }

        @Override
        public Item getItem(String name) throws AccessDeniedException {
            return null;
        }

        @Override
        public File getRootDirFor(Item child) {
            return new File(parent.getOwner().getRootDir(), "batchTasks");
        }

        @Override
        public void onDeleted(Item item) throws IOException {
            // FIXME
            //parent.removeTask();
        }

        @Override
        public String getDisplayName() {
            return parent.getOwner().getDisplayName();
        }

        @Override
        public File getRootDir() {
            return parent.getOwner().getRootDir();
        }

        @Override
        public void save() throws IOException {

        }
    }

    /*private LazyBuildMixIn<BatchJob, BatchRun> createBuildMixIn() {
        return new LazyBuildMixIn<BatchJob, BatchRun>() {
            protected BatchJob asJob() {
                return job;
            }

            protected Class<BatchRun> getBuildClass() {
                return BatchTask.this.getBuildClass();
            }
        };
    }*/
    public static class BatchJob extends Job<BatchJob, BatchRun> implements Queue.Task {

        transient BatchTask task;

        protected BatchJob(ItemGroup parent, String name, BatchTask task) {
            super(parent, name);
            this.task = task;
        }

        @Override
        public boolean isBuildable() {
            return !task.isBuildBlocked(); // FIXME
        }

        @Override
        protected SortedMap<Integer, BatchRun> _getRuns() {
            Iterable<BatchRun> runs = task.getRuns();
            SortedMap<Integer, BatchRun> results = new RunMap();
            runs.forEach(batchRun ->
                results.put(batchRun.id, batchRun)
            );
            return results;
        }

        @Override
        protected void removeRun(BatchRun run) {
            try {
                task.parent.removeTask(run.getParentTask());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }


        @Override
        public void checkAbortPermission() {

        }

        @Override
        public boolean hasAbortPermission() {
            return false;
        }

        @Override
        public Queue.Executable createExecutable() throws IOException {
            return task.createExecutable();
        }
    }
    BatchJob getJob() {
        return job;
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

    @SuppressWarnings("deprecation")
    public String getWhyBlocked() {
        return owner.getWhyBlocked();
    }

    public CauseOfBlockage getCauseOfBlockage() {
        return owner.getCauseOfBlockage();
    }

    public String getName() {
        return name;
    }

    public boolean isConcurrentBuild() {
        return false;
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
    public Iterable<BatchRun> getRuns() {
        return new Iterable<BatchRun>() {
            public Iterator<BatchRun> iterator() {
                return new Iterators.FlattenIterator<BatchRun,AbstractBuild<?,?>>(owner.getBuilds().iterator()) {
                    protected Iterator<BatchRun> expand(AbstractBuild<?,?> b) {
                        BatchRunAction a = b.getAction(BatchRunAction.class);
                        if(a==null) return Iterators.empty();
                        else        return a.getRecords(name).iterator();
                    }
                };
            }
        };
    }

    public HistoryWidget createHistoryWidget() {
        Iterable<BatchRun> iterable = getRuns();
        List<BatchRun> runs = new ArrayList<>();
        iterable.forEach(runs::add);

        LOGGER.log(Level.SEVERE, "createHistoryWidget() runs count: " + runs.size());

        return new BuildHistoryWidget<BatchRun>(this,getRuns(),ADAPTER);
    }

    public BatchRun createExecutable() throws IOException {
        AbstractBuild<?,?> lb = owner.getLastBuild();
        if (lb == null) return null;
        LOGGER.log(Level.INFO, "createExecutable() with job " + job);
        if (job==null) return null; // not fully initialized
        BatchRunAction records;
        synchronized (lb) {
            records = lb.getAction(BatchRunAction.class);
            if(records==null) {
                records = new BatchRunAction(lb);
                lb.addAction(records);
                // we don't need to save it yet.
            }
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

        int id=1;
        BatchRunAction records = lb.getAction(BatchRunAction.class);
        if(records!=null)
            id=records.getRecords().size()+1;

        return lb.getNumber()+"-"+id;
    }

    /**
     * Returns the {@link ACL} for this object.
     */
    public ACL getACL() {
    	return owner.getACL();
    }

    public void checkAbortPermission() {
        getACL().checkPermission(AbstractProject.ABORT);
    }

    public boolean hasAbortPermission() {
        return getACL().hasPermission(AbstractProject.ABORT);
    }
    
    public boolean hasBuildPermission() {
    	return getACL().hasPermission(AbstractProject.BUILD);
    }
    
    public boolean hasDeletePermission() {
    	return getACL().hasPermission(AbstractProject.DELETE);
    }
    
    public boolean hasConfigurePermission() {
    	return getACL().hasPermission(AbstractProject.CONFIGURE);
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
    @POST
    public synchronized void doExecute( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        getACL().checkPermission(AbstractProject.BUILD);

        if (owner.getLastBuild() != null) {
            Jenkins.getInstance().getQueue().schedule(this,0,new CauseAction(new UserCause()));
            rsp.forwardToPreviousPage(req);
        } else {
            rsp.sendRedirect2("noBuild");
        }
    }

    /**
     * Deletes this task.
     */
    @POST
    public synchronized void doDoDelete(StaplerResponse rsp) throws IOException, ServletException {
        getACL().checkPermission(AbstractProject.DELETE);

        getParent().removeTask(this);
        rsp.sendRedirect2("../..");
    }

    private static final Adapter<BatchRun> ADAPTER = new Adapter<BatchRun>() {
        public int compare(BatchRun record, String key) {
            int[] lhs = parse(record.getNumberAsString());
            int[] rhs = parse(key);

            int d = lhs[0]-rhs[0];
            if(d!=0)    return d;
            return lhs[1]-rhs[1];
        }

        public String getKey(BatchRun record) {
            return record.getNumberAsString();
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

    @POST
	public void doCancelQueue(StaplerRequest req, StaplerResponse rsp)
			throws IOException, ServletException {
        checkAbortPermission();

        Jenkins.getInstance().getQueue().cancel(this);
        rsp.forwardToPreviousPage(req);
	}

    public String getUrl() {
    	return owner.getUrl() + "batchTasks/task/" + name + "/";
    }

    static {
        // Used when BatchTask is in Queue at Hudson shutdown
        Queue.XSTREAM.registerConverter(new AbstractSingleValueConverter() {

            @Override
            public boolean canConvert(Class klazz) {
                return BatchTask.class==klazz;
            }

            @Override
            public Object fromString(String str) {
                int idx=str.lastIndexOf('/');
                if(idx<0)   throw new NoSuchElementException("Illegal format: "+str);

                String projectName = str.substring(0, idx);
                Job<?,?> job = (Job<?,?>) Jenkins.getInstance().getItemByFullName(projectName);
                if(job==null)  throw new NoSuchElementException("No such job exists: "+projectName);
                BatchTaskProperty bp = job.getProperty(BatchTaskProperty.class);
                if(bp==null)  throw new NoSuchElementException(projectName+" doesn't have the batck task anymore");
                return bp.getTask(str.substring(idx+1));                
            }

            @Override
            public String toString(Object item) {
                BatchTask bt = (BatchTask) item;
                return bt.owner.getFullName()+"/"+bt.name;
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    public Collection<? extends SubTask> getSubTasks() {
        return Collections.singleton(this);
    }

    /** {@inheritDoc} */
    @Nonnull
    @Override
    public Authentication getDefaultAuthentication() {
        return ACL.SYSTEM;
    }

    /**
     * {@inheritDoc}
     */
    public Task getOwnerTask() {
       return this;
    }

    /**
     * {@inheritDoc}
     */
    public Object getSameNodeConstraint() {
        return null;
    }
}
