package com.mmariska.springdemo2.distributedTaskQueue;

import java.util.Map;

/**
 * Default skeletal implementation of chained distributed task for {@link IChainedDistributedTask}
 */
public abstract class AbstractChainedDistributedTask extends AbstractDistributedTask implements IChainedDistributedTask {
    private final String[] downstreamTasks;
    private Map<String, Object> results;

    public AbstractChainedDistributedTask(String... downstreamTasks) {
        super("chainTask-");
        this.downstreamTasks = downstreamTasks;
    }

    @Override
    public String[] getDownstreamTaskIds() {
        return downstreamTasks;
    }

    @Override
    public void injectResults(Map<String, Object> results) {
        this.results = results;
    }

    @Override
    public Object call(DistributedTaskQueue distributedTaskQueue) {
        return call(distributedTaskQueue, results);
    }

    public abstract Object call(DistributedTaskQueue distributedTaskQueue, Map<String, Object> results);
}
