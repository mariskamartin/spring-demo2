package com.mmariska;

import com.mmariska.springdemo2.distributedTaskQueue.examples.AggregationDistributedTaskRunnable;

public class TestAggregatedDistributedTask extends AggregationDistributedTaskRunnable {

    public TestAggregatedDistributedTask(String... aggregatedTasks) {
        super(aggregatedTasks);
    }

    @Override
    protected long getSleepInMs() {
        return 0;
    }
}
