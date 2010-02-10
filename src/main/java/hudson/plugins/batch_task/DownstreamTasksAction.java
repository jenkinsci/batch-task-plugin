package hudson.plugins.batch_task;

import hudson.model.InvisibleAction;
import java.util.List;

/**
 * Shows the downstream tasks.
 *
 * @author Alan.Harder@Sun.Com
 */
public class DownstreamTasksAction extends InvisibleAction {
    public final BatchTaskInvoker downstream;

    public DownstreamTasksAction(BatchTaskInvoker invoker) {
        this.downstream = invoker;
    }

    public List<BatchTaskInvoker.Config> getTaskConfigs() {
        return downstream.getConfigs();
    }
}
