package com.mmariska.springdemo2.distributedTaskQueue;

import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DistributedTaskDefaultDecorator implements IDistributedTask {
    private static final Logger log = LoggerFactory.getLogger(DistributedTaskDefaultDecorator.class);
    private final String dtqId;
    private final IDistributedTask decoratedTask;
    private final String taskId;

    public DistributedTaskDefaultDecorator(IDistributedTask decoratedTask, String dtqId) {
        this.dtqId = dtqId;
        this.decoratedTask = decoratedTask;
        this.taskId = decoratedTask.getId();
    }

    @Override
    public String getId() {
        return decoratedTask.getId();
    }

    @Override
    public Object call(DistributedTaskQueue distributedTaskQueue) {
        if (taskId == null)
            throw new IllegalStateException("Task is executed without taskId.");
        if (!distributedTaskQueue.startWorkOnTask(taskId))
            throw new IllegalStateException("Some problem with moving task(" + taskId + ") between queues.");

        Object result = null;
        try {
            // client task part
            if (decoratedTask instanceof IChainedDistributedTask) {
                IChainedDistributedTask decoratedTask = (IChainedDistributedTask) this.decoratedTask;
                Map<String, Object> results = new HashMap<>();
                for (String taskId : decoratedTask.getDownstreamTaskIds()) {
                    results.put(taskId, distributedTaskQueue.getResult(taskId));
                }
                decoratedTask.setDownstreamResults(results);
            }
            result = decoratedTask.call(distributedTaskQueue);
            // end client task part
        } catch (Exception e) { // when something went wrong store it for later execution
            result = e;
        }
        distributedTaskQueue.storeResults(taskId, result);
        distributedTaskQueue.checkChainedTasksAfterTaskDone(taskId);
        distributedTaskQueue.stopWorkOnTask(taskId);
        return result;
    }

}
