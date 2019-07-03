package com.mmariska;

import com.mmariska.springdemo2.distributedTaskQueue.examples.AggregationSleepingDistributedTaskRunnable;

public class TestAggregatedDistributedTask extends AggregationSleepingDistributedTaskRunnable {

    public TestAggregatedDistributedTask(String... aggregatedTasks) {
        super(aggregatedTasks);
    }

    @Override
    protected int getSleepMs() {
        return 0;
    }
}
