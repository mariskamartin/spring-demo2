package com.mmariska.springdemo2.distributedTaskQueue;

import java.util.concurrent.CompletableFuture;

public interface IDistributedTaskQueue {
    boolean startLocalWorker();

    CompletableFuture<Object> offer(IDistributedTask task);

    CompletableFuture<Object> offerChain(IChainedDistributedTask task);

    CompletableFuture<Object> getFuture(String taskId);

    Object getResult(String taskId);

    boolean checkChainedTasksViaResults();

    IDistributedTask workerPoolLastTask() throws InterruptedException;

    IDistributedTask workerPoolLastTaskBlocking() throws InterruptedException;

    void workerEndOnTask(IDistributedTask task);

    void workerFinallyDoneAndCleanup(String taskId);

    void workerStoreResults(String taskId, Object result);

    void workerStoreError(String taskId, Exception e);
}
