package com.mmariska.springdemo2.distributedTaskQueue;

import com.mmariska.springdemo2.distributedTaskQueue.DistributedTaskRunnable;

public class FailDistributedTaskRunnable extends DistributedTaskRunnable {

    @Override
    protected long getSleepInMs() {
        throw new IllegalStateException("Simulate some exception in task");
    }
}
