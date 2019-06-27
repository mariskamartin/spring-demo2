package com.mmariska.springdemo2;

import com.mmariska.springdemo2.distributedTaskQueue.DistributedTaskQueue;
import org.redisson.api.RMap;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

public class DistributedTaskRunnable implements Runnable, Serializable {
    private static final Logger log = LoggerFactory.getLogger(DistributedTaskRunnable.class);
    //fixme duplicated in distributed Queue class
    public static final String REDIS_SHARED_WAIT_QUEUE = "waitQueue"; //waiting for workers
    public static final String REDIS_SHARED_WORK_QUEUE = "workQueue"; //workers already started work on these tasks
    private final long createTime;
    private final String taskId;

    @RInject
    private RedissonClient redisson;

    public DistributedTaskRunnable() {
        this.createTime = new Date().getTime();
        this.taskId = "dist-job-" + UUID.randomUUID().toString();
    }

    @Override
    public void run() {
        log.info("({}) pool new task Qwait > Qwork", System.getenv("MY_POD_NAME"));
        RQueue<String> waitQueue = redisson.getQueue(REDIS_SHARED_WAIT_QUEUE);
        String currentTaskId = waitQueue.pollLastAndOfferFirstTo(REDIS_SHARED_WORK_QUEUE);

        if (currentTaskId == null) throw new IllegalStateException("Task is executed without taskId");

        long sleepMs = getSleepInMs();
        log.info("({}) going to sleep/work for {}[ms]", System.getenv("MY_POD_NAME"), sleepMs);
        sleep(sleepMs);

        RMap<String, Integer> map = redisson.getMap("myMap");
        long result = 0;
        for (Integer value : map.values()) {

            result += value;
        }

        log.info("({}) write result to redis resultMap <taskId, results>", System.getenv("MY_POD_NAME"));
        RMap<String, Object> results = redisson.getMap("results");
        results.put(currentTaskId, result);
        log.info("({}) remove job {} from Qwork", System.getenv("MY_POD_NAME"), currentTaskId);
        redisson.getList(REDIS_SHARED_WORK_QUEUE).remove(currentTaskId);

        DistributedTaskQueue.checkChainedTasksAfterTaskDone(redisson, currentTaskId);
        log.info("({}) worker checked chainedTasks", System.getenv("MY_POD_NAME"));

        log.info("({}) worker done for task {}", System.getenv("MY_POD_NAME"), currentTaskId);
    }

    public String getTaskId() {
        return taskId;
    }

    public long getCreateTime() {
        return createTime;
    }

    protected long getSleepInMs() {
        return Math.round(Math.random() * 5000);
    }

    private void sleep(long sleepMs) {
        try {
            Thread.sleep( sleepMs);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
