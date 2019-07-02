package com.mmariska.springdemo2.distributedTaskQueue.examples;

import com.mmariska.springdemo2.distributedTaskQueue.IChainedDistributedTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class AggregationDistributedTaskRunnable extends DistributedTaskDefault implements IChainedDistributedTask {
    private static final Logger log = LoggerFactory.getLogger(AggregationDistributedTaskRunnable.class);
    private final String[] aggregatedTaskIds;
    private String dtqId;
    private Map<String, Object> results;

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
        long sum = results.values().stream().mapToLong(result -> (long) result).sum();
        log.debug("aggregated result ({}) = {}", aggregatedTaskIds, sum);
        return sum;
    }

    @Override
    public String[] getDownstreamTaskIds() {
        return aggregatedTaskIds;
    }

    @Override
    public void setDownstreamResults(Map<String, Object> results) {
        this.results = results;
    }
}
