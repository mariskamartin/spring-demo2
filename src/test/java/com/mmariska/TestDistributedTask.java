package com.mmariska;

import com.mmariska.springdemo2.distributedTaskQueue.AbstractDistributedTask;
import com.mmariska.springdemo2.distributedTaskQueue.DistributedTaskQueue;

public class TestDistributedTask extends AbstractDistributedTask {

    private long result;

    public TestDistributedTask(long result) {
        super("test-dist-job-");
        this.result = result;
    }

    @Override
    public Object call(DistributedTaskQueue distributedTaskQueue) {
        return this.result;
    }

}
