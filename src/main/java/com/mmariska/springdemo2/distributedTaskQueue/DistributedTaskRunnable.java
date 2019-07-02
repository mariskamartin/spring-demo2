package com.mmariska.springdemo2.distributedTaskQueue;

import org.redisson.api.*;
import org.redisson.api.annotation.RInject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

public class DistributedTaskRunnable implements Runnable, Serializable {
    private static final Logger log = LoggerFactory.getLogger(DistributedTaskRunnable.class);
    private final long createTime;
    private final String taskId;
    private final long sleepMs;
    private final String dtqId;

    @RInject
    private RedissonClient redisson;

    public DistributedTaskRunnable() {
        this("dist-job-", null);
    }

    public DistributedTaskRunnable(String prefix, String dtqId) {
        createTime = new Date().getTime();
        taskId = prefix + UUID.randomUUID().toString();
        sleepMs = Math.round(Math.random() * 5000);
        this.dtqId = dtqId;
    }

    @Override
    public void run() {
        DistributedTaskQueue distributedTaskQueue = new DistributedTaskQueue(redisson, dtqId); //missing name
        if (taskId == null) throw new IllegalStateException("Task is executed without taskId");
        if (!distributedTaskQueue.startWorkOnTask(taskId)) throw new IllegalStateException("Some problem with moving task(" + taskId + ") between queues.");

        //syntetic example
        long result = process();

        distributedTaskQueue.storeResults(taskId, result);
        distributedTaskQueue.checkChainedTasksAfterTaskDone(taskId);
        distributedTaskQueue.stopWorkOnTask(taskId);
    }

    /**
     * Put work code here in subClass
     * @return
     */
    protected long process() {
        long result = getResult();
        log.debug("going to sleep/work for {}[ms]", getSleepInMs());
        sleep(getSleepInMs());
        return result;
    }

    protected long getResult() {
        return getSleepInMs();
    }

    public String getTaskId() {
        return taskId;
    }

    public long getCreateTime() {
        return createTime;
    }

    protected long getSleepInMs() {
        return sleepMs;
    }

    private void sleep(long sleepMs) {
        try {
            Thread.sleep( sleepMs);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected RedissonClient getRedisson() {
        return redisson;
    }
}
