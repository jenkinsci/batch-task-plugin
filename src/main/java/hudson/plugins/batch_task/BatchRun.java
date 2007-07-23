package hudson.plugins.batch_task;

import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BallColor;
import hudson.model.Executor;
import hudson.model.LargeText;
import hudson.model.ModelObject;
import hudson.model.Queue.Executable;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.tasks.Shell;
import hudson.util.StreamTaskListener;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Record of {@link BatchTask} execution.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class BatchRun implements Executable, ModelObject {
    /**
     * Build result.
     * If null, we are still building.
     */
    protected Result result;
    public final Calendar timestamp;

    protected transient BatchRunAction parent;

    /**
     * Unique number that identifie this record among {@link BatchRunAction}.
     */
    public final int id;

    /**
     * Pointer that connects us back to {@link BatchTask}
     * @see #getParent()
     */
    public final String taskName;

    /**
     * Number of milli-seconds it took to run this build.
     */
    protected long duration;

    protected BatchRun(Calendar timestamp, BatchRunAction parent, int id, BatchTask task) {
        this.timestamp = timestamp;
        this.parent = parent;
        this.id = id;
        this.taskName = task.name;
    }

    public Result getResult() {
        return result;
    }

    /**
     * Is this task still running?
     */
    public boolean isRunning() {
        return result==null;
    }

    /**
     * Gets the string that says how long since this run has started.
     *
     * @return
     *      string like "3 minutes" "1 day" etc.
     */
    public String getTimestampString() {
        long duration = new GregorianCalendar().getTimeInMillis()-timestamp.getTimeInMillis();
        return Util.getTimeSpanString(duration);
    }

    /**
     * Gets the log file that stores the execution result.
     */
    public File getLogFile() {
        return new File(parent.owner.getRootDir(),"task-"+id+".log");
    }

    public BatchTask getParent() {
        BatchTaskAction jta = parent.owner.getProject().getAction(BatchTaskAction.class);
        if(jta==null)   return null;
        return jta.getTask(taskName);
    }

    public BatchRunAction getOwner() {
        return parent;
    }

    /**
     * Gets the icon color for display.
     */
    public BallColor getIconColor() {
        if(!isRunning()) {
            // already built
            return getResult().color;
        }

        // a new build is in progress
        BatchRun previous = getPrevious();
        BallColor baseColor;
        if(previous==null)
            baseColor = BallColor.GREY_ANIME;
        else
            baseColor = previous.getIconColor();

        return baseColor.anime();
    }

    public String getBuildStatusUrl() {
        return getIconColor()+".gif";
    }

    /**
     * Obtains the previous execution record, or null if no such record is available.
     */
    public BatchRun getPrevious() {
        // check siblings
        for( AbstractBuild<?,?> b=parent.owner; b!=null; b=b.getPreviousBuild()) {
            BatchRunAction records = b.getAction(BatchRunAction.class);
            for(int i=records.records.size()-1; i>=0; i--) {
                BatchRun r = records.records.get(i);
                if( r.taskName.equals(taskName)
                 && r.timestamp.compareTo(this.timestamp)<0 ) // must be older than this
                return r;
            }
        }
        return null;
    }

    /**
     * Obtains the next execution record, or null if no such record is available.
     */
    public BatchRun getNext() {
        // check siblings
        for( AbstractBuild<?,?> b=parent.owner; b!=null; b=b.getNextBuild()) {
            BatchRunAction records = b.getAction(BatchRunAction.class);
            for (BatchRun r : records.records) {
                if (r.taskName.equals(taskName)
                    && r.timestamp.compareTo(this.timestamp) > 0) // must be newer than this
                    return r;
            }
        }
        return null;
    }

    /**
     * Gets the URL (under the context root) that points to this record.
     *
     * @return
     *      URL like "job/foo/53/batchTasks/0"
     */
    public String getUrl() {
        return parent.owner.getUrl()+"batchTasks/"+id;
    }

    public String getDisplayName() {
        return taskName+' '+getBuildNumber();
    }

    public String getBuildNumber() {
        return "#"+parent.owner.getNumber()+'-'+id;
    }

    /**
     * Gets the string that says how long the build took to run.
     */
    public String getDurationString() {
        return Util.getTimeSpanString(duration);
    }

    /**
     * Gets the millisecond it took to build.
     */
    @Exported
    public long getDuration() {
        return duration;
    }

    public void run() {
        try {
            long start = System.currentTimeMillis();
            TaskListener listener = new StreamTaskListener(new FileOutputStream(getLogFile()));
            Launcher launcher = Executor.currentExecutor().getOwner().getNode().createLauncher(listener);

            BatchTask task = getParent();
            if(task==null) {
                listener.getLogger().println("ERROR: undefined taask \""+taskName+"\"");
                result = Result.FAILURE;
            } else {
                try {
                    result = new Shell(task.script)
                        .perform(task.owner.getLastBuild(),launcher,listener) ? Result.SUCCESS : Result.FAILURE;
                } catch (InterruptedException e) {
                    listener.getLogger().println("ABORTED");
                    result = Result.ABORTED;
                }
            }
            duration = System.currentTimeMillis()-start;

            // save the build result
            parent.owner.save();
        } catch (IOException e) {
            result = Result.FAILURE;
            LOGGER.log(Level.SEVERE, "Failed to write "+getLogFile(),e);
        }
    }


    /**
     * Handles incremental log output.
     */
    public void doProgressiveLog( StaplerRequest req, StaplerResponse rsp) throws IOException {
        new LargeText(getLogFile(),!isRunning()).doProgressText(req,rsp);
    }

    private static final Logger LOGGER = Logger.getLogger(BatchRun.class.getName());
}
