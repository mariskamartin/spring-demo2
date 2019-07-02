package com.mmariska.springdemo2.distributedTaskQueue;

import org.redisson.api.RMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class AggregationDistributedTaskRunnable extends DistributedTaskRunnable {
    private static final Logger log = LoggerFactory.getLogger(AggregationDistributedTaskRunnable.class);
    private final String[] aggregatedTaskIds;

    public AggregationDistributedTaskRunnable(String... aggregatedTasks) {
        super("aggreg-job-", null);
        this.aggregatedTaskIds = aggregatedTasks;
    }

    @Override
    protected long getSleepInMs() {
        return 10000;
    }

    @Override
    protected long getResult() {
        DistributedTaskQueue distributedTaskQueue = new DistributedTaskQueue(getRedisson());
        long sum = Arrays.stream(aggregatedTaskIds).mapToLong(taskId -> (long) distributedTaskQueue.getResult(taskId)).sum();
        log.debug("aggregated result ({}) = {}", aggregatedTaskIds, sum);
        return sum;
    }
}
