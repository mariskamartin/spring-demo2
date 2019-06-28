package com.mmariska.springdemo2;

import com.mmariska.springdemo2.distributedTaskQueue.DistributedTaskQueue;
import org.redisson.api.RMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class AggregationDistributedTaskRunnable extends DistributedTaskRunnable {
    private static final Logger log = LoggerFactory.getLogger(AggregationDistributedTaskRunnable.class);
    private final String[] aggregatedTaskIds;

    public AggregationDistributedTaskRunnable(String... aggregatedTasks) {
        super("aggreg-job-");
        this.aggregatedTaskIds = aggregatedTasks;
    }

    @Override
    protected long getSleepInMs() {
        return 10000;
    }

    @Override
    protected long getResult() {
        RMap<Object, Object> map = getRedisson().getMap(DistributedTaskQueue.REDISSON_RESULTS_MAP);
        long sum = Arrays.stream(aggregatedTaskIds).mapToLong(taskId -> (long) map.get(taskId)).sum();
        log.debug("aggregated result ({}) = {}", aggregatedTaskIds, sum);
        return sum;
    }
}
