package com.mmariska;

import com.mmariska.springdemo2.distributedTaskQueue.AbstractDistributedTask;
import com.mmariska.springdemo2.distributedTaskQueue.IDistributedTaskQueue;

public class TestDistributedTask extends AbstractDistributedTask {

    private long result;

    public TestDistributedTask(long result) {
        this(result,"test-dist-job-");
    }

    public TestDistributedTask(long result, String prefix) {
        super(prefix);
        this.result = result;
    }

    @Override
    public Object call(IDistributedTaskQueue distributedTaskQueue) {
        return this.result;
    }

}
