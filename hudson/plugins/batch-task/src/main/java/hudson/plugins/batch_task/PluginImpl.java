package hudson.plugins.batch_task;

import hudson.Plugin;
import hudson.model.Jobs;

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
    }
}
