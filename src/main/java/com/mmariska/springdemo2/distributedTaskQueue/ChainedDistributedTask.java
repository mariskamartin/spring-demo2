package com.mmariska.springdemo2.distributedTaskQueue;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class ChainedDistributedTask implements Serializable {
    private final DistributedTaskRunnable task;
    private final Set<String> downstreamTasks;

    public ChainedDistributedTask(DistributedTaskRunnable task) {
        this.task = task;
        this.downstreamTasks = new HashSet<>();
    }

    public Set<String> getDownstreamTasks() {
        return downstreamTasks;
    }

    public DistributedTaskRunnable getTask() {
        return task;
    }
}
