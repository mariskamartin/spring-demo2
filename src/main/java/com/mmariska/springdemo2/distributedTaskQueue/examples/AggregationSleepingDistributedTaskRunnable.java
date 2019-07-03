package com.mmariska.springdemo2.distributedTaskQueue.examples;

import com.mmariska.springdemo2.distributedTaskQueue.AbstractChainedDistributedTask;
import com.mmariska.springdemo2.distributedTaskQueue.DistributedTaskQueue;
import com.mmariska.springdemo2.distributedTaskQueue.IChainedDistributedTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class AggregationSleepingDistributedTaskRunnable extends AbstractChainedDistributedTask {
    private static final Logger log = LoggerFactory.getLogger(AggregationSleepingDistributedTaskRunnable.class);
    private final SleepingDistributedTask sleepingDistributedTask;

    public AggregationSleepingDistributedTaskRunnable(String... aggregatedTasks) {
        super(aggregatedTasks);
        sleepingDistributedTask = new SleepingDistributedTask("aggSleepTask-", getSleepMs());
    }

    protected int getSleepMs() {
        return 10000;
    }

    @Override
    public Object call(DistributedTaskQueue distributedTaskQueue, Map<String, Object> results) {
        long sum = results.values().stream().mapToLong(result -> (long) result).sum();
        log.debug("aggregated result ({}) = {}", getDownstreamTaskIds(), sum);
        sleepingDistributedTask.call(distributedTaskQueue);
        return sum;
    }
}
