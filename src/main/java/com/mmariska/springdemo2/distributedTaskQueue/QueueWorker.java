package com.mmariska.springdemo2.distributedTaskQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueWorker implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(QueueWorker.class);

    private final DistributedTaskQueue distributedTaskQueue;
    private boolean isRunning = true;

    public QueueWorker(DistributedTaskQueue distributedTaskQueue){
        log.info("created new worker {} - {}", this, Thread.currentThread().getName());
        this.distributedTaskQueue = distributedTaskQueue;
    }

    @Override
    public void run() {
        while(isRunning) {
            try {
                IDistributedTask task = distributedTaskQueue.takeTask();
                log.debug("worker {} take task {}", this, task.getId());
                Object restult = task.call(distributedTaskQueue);
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
            }
        }
    }
}
