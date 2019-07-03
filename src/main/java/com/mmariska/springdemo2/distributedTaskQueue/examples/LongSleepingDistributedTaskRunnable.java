package com.mmariska.springdemo2.distributedTaskQueue.examples;

public class LongSleepingDistributedTaskRunnable extends SleepingDistributedTask {

    @Override
    protected long getSleepInMs() {
        return 20000;
    }

    @Override
    protected long getResult() {
        return super.getResult() + 20;
    }
}
