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

    @RInject
    private RedissonClient redisson;

    public DistributedTaskRunnable() {
        this.createTime = new Date().getTime();
        this.taskId = "dist-job-" + UUID.randomUUID().toString();
    }

    @Override
    public void run() {
        log.info("({}) move task Qwait > Qwork", System.getenv("MY_POD_NAME"));
        RList<String> waitQueue = redisson.getList(DistributedTaskQueue.REDIS_SHARED_WAIT_QUEUE);
        RList<String> workQueue = redisson.getList(DistributedTaskQueue.REDIS_SHARED_WORK_QUEUE);

        RBatch batch = redisson.createBatch();
        batch.getList(DistributedTaskQueue.REDIS_SHARED_WORK_QUEUE).addAsync(0,taskId);
        batch.getList(DistributedTaskQueue.REDIS_SHARED_WAIT_QUEUE).removeAsync(taskId,1);
        BatchResult<?> execute = batch.execute();
        //todo check result if all is done

        //fixme Atomically remove and add task?
//        workQueue.add(taskId); // first add.. for visibility, when app is killed here
//        waitQueue.remove(taskId);

        if (taskId == null) throw new IllegalStateException("Task is executed without taskId");

        long sleepMs = getSleepInMs();
        log.info("({}) going to sleep/work for {}[ms]", System.getenv("MY_POD_NAME"), sleepMs);
        sleep(sleepMs);

        //syntetic example
        long result = getResult();

        log.info("({}) write result to redis resultMap <taskId, results>", System.getenv("MY_POD_NAME"));
        RMap<String, Object> results = redisson.getMap(DistributedTaskQueue.REDISSON_RESULTS_MAP);
        results.put(taskId, result);
        log.info("({}) remove job {} from Qwork", System.getenv("MY_POD_NAME"), taskId);
        workQueue.remove(taskId);
        DistributedTaskQueue.checkChainedTasksAfterTaskDone(redisson, taskId);
        log.info("({}) worker checked chainedTasks", System.getenv("MY_POD_NAME"));
        redisson.getTopic(DistributedTaskQueue.REDISSON_DONE_TOPIC).publish(taskId); //fixme static access
        log.info("({}) worker done for task {}", System.getenv("MY_POD_NAME"), taskId);
    }

    protected long getResult() {
        RMap<String, Integer> map = redisson.getMap("myMap");
        long result = 0;
        for (Integer value : map.values()) {

            result += value;
        }
        return result;
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
