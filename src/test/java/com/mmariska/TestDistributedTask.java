package com.mmariska;

import com.mmariska.springdemo2.distributedTaskQueue.examples.DistributedTaskDefault;

public class TestDistributedTask extends DistributedTaskDefault {

    private long result;

    public TestDistributedTask(long result) {
        super("test-dist-job-");
        this.result = result;
    }

    @Override
    protected long process() {
        return this.result; //just return
    }
}
