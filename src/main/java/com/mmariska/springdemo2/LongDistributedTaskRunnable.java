package com.mmariska.springdemo2;

public class LongDistributedTaskRunnable extends DistributedTaskRunnable {

    @Override
    protected long getSleepInMs() {
        return 20000;
    }

    @Override
    protected long getResult() {
        return super.getResult() + 20000;
    }
}
