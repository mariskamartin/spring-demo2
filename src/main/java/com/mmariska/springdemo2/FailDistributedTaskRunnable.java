package com.mmariska.springdemo2;

public class FailDistributedTaskRunnable extends DistributedTaskRunnable {

    @Override
    protected long getSleepInMs() {
        throw new IllegalStateException("Simulate some exception in task");
    }
}
