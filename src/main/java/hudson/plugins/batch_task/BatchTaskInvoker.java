package hudson.plugins.batch_task;

import hudson.Launcher;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Project;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
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
public class BatchTaskInvoker extends Publisher {
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
            Hudson.getInstance().getQueue().add(task,0);
            return true;
        }
    }

    private final Config[] configs;

    public BatchTaskInvoker(Config[] configs) {
        this.configs = configs;
    }

    public BatchTaskInvoker(JSONObject source) {
        List<Config> configs = new ArrayList<Config>();
        for( Object o : JSONArray.fromObject(source.get("config")) )
            configs.add(new Config((JSONObject)o));
        this.configs = configs.toArray(new Config[configs.size()]);
    }

    public List<Config> getConfigs() {
        return Collections.unmodifiableList(Arrays.asList(configs));
    }

    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        for (Config config : configs)
            config.invoke(listener);
        return true;
    }

    public Descriptor<Publisher> getDescriptor() {
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

        public static final DescriptorImpl INSTANCE = new DescriptorImpl();
    }
}
