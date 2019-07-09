package com.mmariska.springdemo2.distributedTaskQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class QueueWorker implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(QueueWorker.class);

    private final IDistributedTaskQueue distributedTaskQueue;
    private AtomicBoolean isRunning = new AtomicBoolean(true);

    public QueueWorker(IDistributedTaskQueue distributedTaskQueue){
        log.info("created new worker {} - {}", this, Thread.currentThread().getName());
        this.distributedTaskQueue = distributedTaskQueue;
    }

    @Override
    public void run() {
        while(isRunning.get()) {
            IDistributedTask task = null;
            try {
                task = distributedTaskQueue.workerPoolLastTaskBlocking();

                try {
                    // client task part
                    if (task instanceof IChainedDistributedTask) {
                        IChainedDistributedTask decoratedTask = (IChainedDistributedTask) task;
                        Map<String, Object> results = new HashMap<>();
                        for (String taskId : decoratedTask.getDownstreamTaskIds()) {
                            results.put(taskId, distributedTaskQueue.getResult(taskId));
                        }
                        decoratedTask.injectResults(results);
                    }
                    distributedTaskQueue.workerStoreResults(task.getId(), task.call(distributedTaskQueue));
                    // end client task part
                } catch (Exception e) { // when something went wrong store it for later execution
                    distributedTaskQueue.workerStoreError(task.getId(), e);
                }
                distributedTaskQueue.checkChainedTasksViaResults();
                distributedTaskQueue.workerEndOnTask(task);
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
            } finally {
                if (task != null) distributedTaskQueue.workerFinallyDoneAndCleanup(task.getId());
            }
        }
    }
}
