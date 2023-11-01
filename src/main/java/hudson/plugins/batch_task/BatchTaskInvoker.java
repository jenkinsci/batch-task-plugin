package hudson.plugins.batch_task;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Cause.UpstreamCause;
import hudson.model.CauseAction;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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
    public static final class Config extends AbstractDescribableImpl<Config> {
        public final String project;
        public final String task;

        @DataBoundConstructor
        public Config(String project, String task) {
            this.project = project;
            this.task = task;
        }

        /**
         * Finds the target {@link BatchTaskProperty}.
         */
        public BatchTaskProperty resolveProperty(ItemGroup context) {
            AbstractProject<?,?> p = Jenkins.getInstance().getItem(project, context, AbstractProject.class);
            if(p==null)     return null;
            return p.getProperty(BatchTaskProperty.class);
        }

        /**
         * Finds the target {@link BatchTask} that this configuration points to,
         * or null if not found.
         */
        public BatchTask resolve(ItemGroup context) {
            BatchTaskProperty bp = resolveProperty(context);
            if(bp==null)    return null;

            return bp.getTask(this.task);
        }

        /**
         * @deprecated as of 1.15
         */
        @Deprecated
        public BatchTask resolve() {
            return resolve(Jenkins.getInstance());
        }

        /**
         * @deprecated as of 1.15
         */
        @Deprecated
        public BatchTaskProperty resolveProperty() {
            return resolveProperty(null);
        }

        public boolean invoke(AbstractBuild<?,?> build, BuildListener listener, HashSet<String> seenJobs) {
            PrintStream logger = listener.getLogger();

            AbstractProject<?,?> p = Jenkins.getInstance().getItem(project, build.getProject(), AbstractProject.class);
            if(p==null) {
                listener.error(Messages.BatchTaskInvoker_NoSuchProject(project));
                return false;
            }

            BatchTaskProperty bp = p.getProperty(BatchTaskProperty.class);
            if(bp==null) {
                listener.error(Messages.BatchTaskInvoker_NoBatchTaskExists(task));
                return false;
            }

            BatchTask taskObj = bp.getTask(task);
            if(taskObj==null) {
                listener.error(Messages.BatchTaskInvoker_NoSuchTask(task,bp.findNearestTask(task).name));
                return false;
            }

            // Only report nextBuildNumber once per project
            String buildNum = "";
            if (!seenJobs.contains(project)) {
                buildNum = " #" + taskObj.getNextBuildNumber();
                seenJobs.add(project);
            }
            logger.println(Messages.BatchTaskInvoker_Invoking(project,task,buildNum));
            Jenkins.getInstance().getQueue().schedule(taskObj,0,
                    new CauseAction(new UpstreamCause((Run)build)));
            return true;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<Config> {
            @Override
            public String getDisplayName() {
                return "";
            }

            public ListBoxModel doFillTaskItems(@QueryParameter String project, @AncestorInPath AbstractProject context) {
                // when the item is not found, the user should be getting an error from elsewhere.
                ListBoxModel r = new ListBoxModel();

                AbstractProject<?,?> p = Jenkins.getInstance().getItem(project, context, AbstractProject.class);
                if(p!=null) {
                    BatchTaskProperty bp = p.getProperty(BatchTaskProperty.class);
                    if(bp!=null) {
                        for(BatchTask task : bp.getTasks())
                            r.add(task.getDisplayName(), task.getName());
                    }
                }
                return r;
            }

            @Restricted(NoExternalUse.class)
            public FormValidation doCheckProject(@QueryParameter String project) {
                if (project.startsWith("/")) {
                    return FormValidation.warning(Messages.BatchTaskInvoker_ForwardSlash());
                }
                Jenkins jenkins = Jenkins.getInstance();
                if (jenkins != null) {
                    Item item = jenkins.getItemByFullName(project);
                    if (item == null) {
                        return FormValidation.warning(Messages.BatchTaskInvoker_NoSuchProject(project));
                    }
                }
                return FormValidation.ok();
            }

            @Restricted(NoExternalUse.class)
            public FormValidation doCheckTask(@QueryParameter String project, @QueryParameter String task) {
                if (!project.isEmpty() && task.isEmpty()) {
                    return FormValidation.warning(Messages.BatchTaskInvoker_NoBatchTaskExists(task));
                }
                return FormValidation.ok();
            }
        }
    }

    private final Config[] configs;
    private /*almost final*/ Result threshold;

    private Object readResolve() {
        if (threshold==null) threshold = Result.UNSTABLE;
        return this;
    }

    @DataBoundConstructor
    public BatchTaskInvoker(Config[] configs, boolean evenIfUnstable) {
        this(configs,evenIfUnstable ? Result.UNSTABLE : Result.SUCCESS);
    }

    public BatchTaskInvoker(Config[] configs, Result threshold) {
        this.configs = configs;
        this.threshold = threshold;
    }

    public List<Config> getConfigs() {
        return Collections.unmodifiableList(Arrays.asList(configs));
    }

    public Result getThreshold() {
        return threshold;
    }

    public boolean isEvenIfUnstable() {
        return threshold.isWorseOrEqualTo(Result.UNSTABLE);
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        HashSet<String> seenJobs = new HashSet<String>();
        if (build != null) {
            Result result = build.getResult();
            if (result != null) {
                if (result.isBetterOrEqualTo(threshold)) {
                    for (Config config : configs)
                        config.invoke(build, listener, seenJobs);
                }
            }
        }
        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        return Collections.singletonList(new DownstreamTasksAction(this));
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public String getDisplayName() {
            return Messages.BatchTaskInvoker_DisplayName();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
