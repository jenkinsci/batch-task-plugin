package hudson.plugins.batch_task;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@link Publisher} that triggers batch tasks of other projects.
 *
 * @author Kohsuke Kawaguchi
 */
public class BatchTaskInvoker extends Notifier {
    /**
     * What task to invoke?
     */
    public static final class Config {
        public final String project;
        public final String task;

        public Config(String project, String task) {
            this.project = project;
            this.task = task;
        }

        public Config(JSONObject source) {
            this(source.getString("project").trim(), source.getString("task").trim());
        }

        /**
         * Finds the target {@link BatchTaskProperty}.
         */
        public BatchTaskProperty resolveProperty() {
            AbstractProject<?,?> p = Hudson.getInstance().getItemByFullName(project, AbstractProject.class);
            if(p==null)     return null;
            return p.getProperty(BatchTaskProperty.class);
        }

        /**
         * Finds the target {@link BatchTask} that this configuration points to,
         * or null if not found.
         */
        public BatchTask resolve() {
            BatchTaskProperty bp = resolveProperty();
            if(bp==null)    return null;

            return bp.getTask(this.task);
        }

        public boolean invoke(BuildListener listener) {
            PrintStream logger = listener.getLogger();

            AbstractProject<?,?> p = Hudson.getInstance().getItemByFullName(project, AbstractProject.class);
            if(p==null) {
                listener.error(Messages.BatchTaskInvoker_NoSuchProject(project));
                return false;
            }

            BatchTaskProperty bp = p.getProperty(BatchTaskProperty.class);
            if(bp==null) {
                listener.error(Messages.BatchTaskInvoker_NoBatchTaskExists(task));
                return false;
            }

            BatchTask task = bp.getTask(this.task);
            if(task==null) {
                listener.error(Messages.BatchTaskInvoker_NoSuchTask(task,bp.findNearestTask(this.task).name));
                return false;
            }

            logger.println(Messages.BatchTaskInvoker_Invoking(project,this.task,task.getNextBuildNumber()));
            Hudson.getInstance().getQueue().schedule(task,0);
            return true;
        }
    }

    private final Config[] configs;
    private /*almost final*/ Result threshold;

    private Object readResolve() {
        if (threshold==null) threshold = Result.UNSTABLE;
        return this;
    }

    public BatchTaskInvoker(Config[] configs, Result threshold) {
        this.configs = configs;
        this.threshold = threshold;
    }

    public BatchTaskInvoker(JSONObject source) {
        List<Config> configList = new ArrayList<Config>();
        for( Object o : JSONArray.fromObject(source.get("config")) )
            configList.add(new Config((JSONObject)o));
        this.configs = configList.toArray(new Config[configList.size()]);
        this.threshold = source.getBoolean("evenIfUnstable") ? Result.UNSTABLE : Result.SUCCESS;
    }

    public List<Config> getConfigs() {
        return Collections.unmodifiableList(Arrays.asList(configs));
    }

    public Result getThreshold() {
        return threshold;
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        if (build.getResult().isBetterOrEqualTo(threshold)) {
            for (Config config : configs)
                config.invoke(listener);
        }
        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public BuildStepDescriptor<Publisher> getDescriptor() {
        return DescriptorImpl.INSTANCE;
    }

    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        private DescriptorImpl() {
            super(BatchTaskInvoker.class);
        }

        public String getDisplayName() {
            return Messages.BatchTaskInvoker_DisplayName();
        }

        @Override
        public BatchTaskInvoker newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new BatchTaskInvoker(formData);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Extension
        public static final DescriptorImpl INSTANCE = new DescriptorImpl();
    }
}
