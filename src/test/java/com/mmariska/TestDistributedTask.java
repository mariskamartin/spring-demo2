package com.mmariska;

import com.mmariska.springdemo2.distributedTaskQueue.DistributedTaskRunnable;

public class TestDistributedTask extends DistributedTaskRunnable {

    private long result;

    public TestDistributedTask(long result) {
        this.result = result;
    }

    @Override
    protected long process() {
        return this.result;
    }
}
