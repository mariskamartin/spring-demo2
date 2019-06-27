package com.mmariska.springdemo2;

public class LongDistributedTaskRunnable extends DistributedTaskRunnable {

    @Override
    protected long getSleepInMs() {
        return 20000;
    }
}
