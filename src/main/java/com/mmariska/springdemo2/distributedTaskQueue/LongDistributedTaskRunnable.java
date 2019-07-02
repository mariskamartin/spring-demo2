package com.mmariska.springdemo2.distributedTaskQueue;

import com.mmariska.springdemo2.distributedTaskQueue.DistributedTaskRunnable;

public class LongDistributedTaskRunnable extends DistributedTaskRunnable {

    @Override
    protected long getSleepInMs() {
        return 20000;
    }

    @Override
    protected long getResult() {
        return super.getResult() + 20;
    }
}
