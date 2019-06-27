package com.mmariska.springdemo2.distributedTaskQueue;

import com.mmariska.springdemo2.DistributedTaskRunnable;

import java.util.Collections;
import java.util.Set;

public class ChainedDistributedTask {
    private final DistributedTaskRunnable task;
    private final Set<String> downstreamTasks;

    public ChainedDistributedTask(DistributedTaskRunnable task) {
        this.task = task;
        this.downstreamTasks = Collections.EMPTY_SET;
    }

    public Set<String> getDownstreamTasks() {
        return downstreamTasks;
    }

    public DistributedTaskRunnable getTask() {
        return task;
    }
}
