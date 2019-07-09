package com.mmariska.springdemo2.distributedTaskQueue.examples;

import com.mmariska.springdemo2.distributedTaskQueue.AbstractDistributedTask;
import com.mmariska.springdemo2.distributedTaskQueue.IDistributedTaskQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SleepingDistributedTask extends AbstractDistributedTask {
    private static final Logger log = LoggerFactory.getLogger(SleepingDistributedTask.class);
    private final long sleepMs;

    public SleepingDistributedTask() {
        this("sleepTask-", -1);
    }

    public SleepingDistributedTask(String prefix, long sleepMs) {
        super(prefix);
        this.sleepMs = sleepMs < 0 ? Math.round(Math.random() * 2000) : sleepMs;
    }

    @Override
    public Object call(IDistributedTaskQueue distributedTaskQueue) {
        long result = getResult();
        log.debug("going to sleep/work for {}[ms]", getSleepInMs());
        sleep(getSleepInMs());
        return result;
    }

    protected long getResult() {
        return getSleepInMs();
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
