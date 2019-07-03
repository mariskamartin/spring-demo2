package com.mmariska.springdemo2.distributedTaskQueue.examples;

import com.mmariska.springdemo2.distributedTaskQueue.DistributedTaskQueue;
import com.mmariska.springdemo2.distributedTaskQueue.IDistributedTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.UUID;

public class DistributedTaskDefault implements IDistributedTask {
    private static final Logger log = LoggerFactory.getLogger(DistributedTaskDefault.class);
    private final long createTime;
    private final String taskId;
    private final long sleepMs;

    public DistributedTaskDefault() {
        this("dist-job-");
    }

    public DistributedTaskDefault(String prefix) {
        createTime = new Date().getTime();
        taskId = prefix + UUID.randomUUID().toString();
        sleepMs = Math.round(Math.random() * 5000);
    }

    @Override
    public Object call(DistributedTaskQueue distributedTaskQueue) {
        return process();
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

    @Override
    public String getId() {
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
}
