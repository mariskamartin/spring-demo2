package com.mmariska.springdemo2;

import com.mmariska.springdemo2.distributedTaskQueue.DistributedTaskQueue;
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

    @RInject
    private RedissonClient redisson;

    public DistributedTaskRunnable() {
        this("dist-job-");
    }

    public DistributedTaskRunnable(String prefix) {
        this.createTime = new Date().getTime();
        this.taskId = prefix + UUID.randomUUID().toString();
        this.sleepMs = Math.round(Math.random() * 5000);
    }

    @Override
    public void run() {
        if (taskId == null) throw new IllegalStateException("Task is executed without taskId");
        log.info("move task({}) Qwait > Qwork", taskId);
        RList<String> waitQueue = redisson.getList(DistributedTaskQueue.REDIS_SHARED_WAIT_QUEUE);
        RList<String> workQueue = redisson.getList(DistributedTaskQueue.REDIS_SHARED_WORK_QUEUE);

        RBatch batch = redisson.createBatch();
        batch.getList(DistributedTaskQueue.REDIS_SHARED_WORK_QUEUE).addAsync(0,taskId);
        batch.getList(DistributedTaskQueue.REDIS_SHARED_WAIT_QUEUE).removeAsync(taskId,1);
        BatchResult<Boolean> batchResult = (BatchResult<Boolean>) batch.execute();
        if (batchResult.getResponses().contains(false)) throw new IllegalStateException("Some problem with moving task(" + taskId + ") between queues.");

        //syntetic example
        long result = getResult();
        log.info("going to sleep/work for {}[ms]", getSleepInMs());
        sleep(getSleepInMs());

        log.info("write result to redis resultMap <taskId, results>");
        RMap<String, Object> results = redisson.getMap(DistributedTaskQueue.REDISSON_RESULTS_MAP);
        results.put(taskId, result);
        log.info("remove job {} from Qwork", taskId);
        workQueue.remove(taskId);
        DistributedTaskQueue.checkChainedTasksAfterTaskDone(redisson, taskId);
        log.info("worker checked chainedTasks");
        redisson.getTopic(DistributedTaskQueue.REDISSON_DONE_TOPIC).publish(taskId); //fixme static access
        log.info("worker done for task {}", taskId);
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
