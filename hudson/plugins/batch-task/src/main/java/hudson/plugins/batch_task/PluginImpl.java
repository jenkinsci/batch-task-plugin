package hudson.plugins.batch_task;

import hudson.Plugin;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Jobs;
import hudson.tasks.BuildStep;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Entry point of the plugin.
 *
 * @author Kohsuke Kawaguchi
 * @plugin
 */
public class PluginImpl extends Plugin {
    @Override
    public void start() throws Exception {
        Jobs.PROPERTIES.add(BatchTaskProperty.DESCRIPTOR);
        BuildStep.PUBLISHERS.addNotifier(BatchTaskInvoker.DescriptorImpl.INSTANCE);
    }

    public void doGetTaskListJson(StaplerRequest req, StaplerResponse rsp,@QueryParameter("name") String name) throws IOException, ServletException {
        // when the item is not found, the user should be getting an error from elsewhere.
        ListBoxModel r = new ListBoxModel();

        AbstractProject<?,?> p = Hudson.getInstance().getItemByFullName(name, AbstractProject.class);
        if(p!=null) {
            BatchTaskProperty bp = p.getProperty(BatchTaskProperty.class);
            if(bp!=null) {
                for(BatchTask task : bp.getTasks())
                    r.add(new ListBoxModel.Option(task.getName()));
            }
        }

        r.writeTo(req,rsp);
    }
}
