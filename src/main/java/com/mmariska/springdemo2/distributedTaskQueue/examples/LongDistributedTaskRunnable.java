package com.mmariska.springdemo2.distributedTaskQueue.examples;

public class LongDistributedTaskRunnable extends DistributedTaskDefault {

    @Override
    protected long getSleepInMs() {
        return 20000;
    }

    @Override
    protected long getResult() {
        return super.getResult() + 20;
    }
}
