package com.mmariska.springdemo2.distributedTaskQueue;

import com.mmariska.springdemo2.distributedTaskQueue.examples.DistributedTaskDefault;
import org.redisson.Redisson;
import org.redisson.api.*;
import org.redisson.api.listener.MessageListener;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;


/**
 * Todo
 *      done - queue name handeling
 *      done - runnable/callable decorator
 *      - types handling
 *      - error handeling > maybe to future
 *      - results lifecycle (aggregation tasks? TTL?)
 */
public class DistributedTaskQueue {
    private static final Logger log = LoggerFactory.getLogger(DistributedTaskDefault.class);

    private final String redisSharedWaitQueue; //waiting for workers
    private final String redisSharedWorkQueue; //workers already started work on these tasks
    private final String redisSharedChainTaskMap;
    private final String redissonResultsMap;
    private final String redissonDoneTopic;
    private final String redisSharedExecutor;
    private final String dtqId;
    private final RedissonClient redisson;



    public DistributedTaskQueue() {
        this(getRedissonClient(null), "defaultDtq");
    }

    public DistributedTaskQueue(String redisUrl) {
        this(getRedissonClient(redisUrl), "defaultDtq");
    }

    public DistributedTaskQueue(RedissonClient redissonClient) {
        this(redissonClient, "defaultDtq");
    }

    public DistributedTaskQueue(String redisUrl, String uniqueName) {
        this(getRedissonClient(redisUrl), uniqueName);
    }

    public DistributedTaskQueue(RedissonClient redisson, String uniqueName) {
        this.redisson = redisson;
        dtqId = uniqueName != null ? uniqueName : "defaultDtq";
        redisSharedExecutor = dtqId + "QueueExecutor";
        redisSharedWaitQueue = dtqId + "WaitQueue";
        redisSharedWorkQueue = dtqId + "WorkQueue";
        redisSharedChainTaskMap = dtqId + "ChainedTasks";
        redissonResultsMap = dtqId + "Results";
        redissonDoneTopic = dtqId + "DoneTopic";
    }

    public boolean subscribeWorker() {
        RExecutorService executorService = redisson.getExecutorService(redisSharedExecutor, ExecutorOptions.defaults());
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executorService.registerWorkers(1, executor);
        return true; //for now there is no more logic around
    }


    public Future<?> offer(IDistributedTask task) {
        IDistributedTask decoratedTask = new DistributedTaskDefaultDecorator(task, dtqId);
        if( ! redisson.getQueue(redisSharedWaitQueue).offer(decoratedTask.getId()) ) {
            throw new IllegalStateException("Problem with scheduling task " + decoratedTask.getId() + " - " + task);
        }
        //just and schedule concrete task - queue is created on redis executor side
        ExecutorOptions options = ExecutorOptions.defaults();
        options.taskRetryInterval(0, TimeUnit.SECONDS);
        /* todo - we do not want to reschedule automatically, when all apps are down and some task is in redis, Redis will reschedule it or we can reuse of this functionality and refind job in work queue also */
        RExecutorService executorService = redisson.getExecutorService(redisSharedExecutor, options);
        RExecutorFuture<?> future = executorService.submit(decoratedTask);
//        String taskId = future.getId(); // do not use redis task id - we have problems hot to obtain taskId for chainedTasks
        log.debug("[{}] scheduled task Id = {}", dtqId, decoratedTask.getId());
        return listenOnTaskResult(decoratedTask.getId());
    }

    public Future<Object> offerChain(IChainedDistributedTask task) {
        RMap<String, ChainedDistributedTask> chainedTasksMap = redisson.getMap(redisSharedChainTaskMap);
        ChainedDistributedTask chainedTask = new ChainedDistributedTask(task);
        chainedTask.getDownstreamTasks().addAll(Arrays.asList(task.getDownstreamTaskIds()));
        chainedTasksMap.put(task.getId(), chainedTask);
        log.debug("[{}] scheduled chain for task Id = {}", dtqId, task.getId());
        return listenOnTaskResult(task.getId());
    }

    //fixme (encapsulation?) this is called from workers after done task
    public void checkChainedTasksAfterTaskDone(String doneTask) {
        RMap<String, ChainedDistributedTask> chainedTasksMap = redisson.getMap(redisSharedChainTaskMap);
        log.trace("[{}] chainedTasks definitions = {}", dtqId, chainedTasksMap.keySet().size());
        for (Map.Entry<String, ChainedDistributedTask> entry : chainedTasksMap.entrySet()) {
            ChainedDistributedTask chainedTask = entry.getValue();
            if(chainedTask.getDownstreamTasks().remove(doneTask)) {
                chainedTasksMap.put(entry.getKey(), chainedTask); //update map
                log.trace("[{}] removed task ({}) from {}", dtqId, doneTask, chainedTask.getTask());
                if (chainedTask.getDownstreamTasks().isEmpty()) {
                    offer(chainedTask.getTask());
                    chainedTasksMap.remove(entry.getKey()); // remove itself from map
                }
            }
        }
    }

    public boolean isTaskDone(String taskId) {
        RBatch batch = redisson.createBatch();
        batch.getQueue(redisSharedWaitQueue).containsAsync(taskId);
        batch.getQueue(redisSharedWorkQueue).containsAsync(taskId);
        batch.getMap(redisSharedChainTaskMap).containsKeyAsync(taskId);
        BatchResult<?> execute = batch.execute();
        for (Object resp : execute.getResponses()) {
            if ((boolean) resp) {
                return false; //still running somewhere
            }
        }
        return true; //task done
    }

    public boolean recheckFailures() {
        // todo needs to reschedule failed tasks pending in queues
        // do something with errors in tasks
        return false;
    }

    /**
     * Fixme this implementation is not cheap to call! Itroduce cache or do not print queues at all. This is for debug only
     * @return
     */
    public String debugPrintQueues() {
        return String.format("distributedTaskQueue [waitQueue = %s, workQueue = %s, chainedTasks = %s]", redisson.getQueue(redisSharedWaitQueue), redisson.getQueue(redisSharedWorkQueue), redisson.getMap(redisSharedChainTaskMap).readAllKeySet());
    }

    /**
     * This defaults to localhost or it can be used in containerised system via ENV property
     * @return redis address
     */
    private static RedissonClient getRedissonClient(String redisUrl) {
        String defaultUrl = System.getenv("REDIS_HOST") != null ? System.getenv("REDIS_HOST") : "redis://127.0.0.1:6379";
        String address = redisUrl != null ? redisUrl : defaultUrl;
        Config config = new Config();
        config.useSingleServer().setAddress(address);
        return Redisson.create(config);
    }

    public Object getResult(String taskId) {
        return getResultsMap().get(taskId);
    }

    /**
     * It returns future for concrete task. Result of task is accesible via get() method.
     * @param taskId
     * @return Future
     */
    public Future<Object> getFuture(String taskId) {
        //check result map -> we already have result
        //todo check for stucked job
        //return future with listener.. we still waiting
        if (getResultsMap().containsKey(taskId)) {
            CompletableFuture<Object> completedFuture = new CompletableFuture<>();
            completedFuture.complete(getResultsMap().get(taskId));
            return completedFuture;
        } else {
            CompletableFuture<Object> future = listenOnTaskResult(taskId);
            return future;
        }
    }

    private CompletableFuture<Object> listenOnTaskResult(String taskId) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        RTopic taskDoneTopic = redisson.getTopic(redissonDoneTopic);

        // todo - probably one centralized listener will be more efficient
        MessageListener<String> messageListener = new MessageListener<String>() {
            @Override
            public void onMessage(CharSequence channel, String doneTaskId) {
                log.trace("[{}] {} on message {}", dtqId, this, doneTaskId);
                if (doneTaskId.equals(taskId)) {
                    try {
                        future.complete(getResultsMap().get(doneTaskId));
                    } finally {
                        taskDoneTopic.removeListener(this);
                    }
                }
            }
        };
        taskDoneTopic.addListener(String.class, messageListener);
        return future;
    }

    private RMap<String, Object> getResultsMap() {
        return redisson.getMap(redissonResultsMap);
    }

    /**
     * Package private, allows to call from distributed runnable (decorator)
     * @param taskId
     * @return can start on task
     */
    public boolean startWorkOnTask(String taskId) {
        log.debug("[{}] working on task {}", dtqId, taskId);
        log.debug("[{}] print: ", dtqId, debugPrintQueues());
        RBatch batch = redisson.createBatch();
        batch.getList(redisSharedWorkQueue).addAsync(0,taskId);
        batch.getList(redisSharedWaitQueue).removeAsync(taskId,1);
        BatchResult<Boolean> batchResult = (BatchResult<Boolean>) batch.execute();
        log.debug("[{}] print after: ", dtqId, debugPrintQueues());
        return !batchResult.getResponses().contains(false);
    }

    /**
     * Package private, allows to call from distributed runnable (decorator)
     * @param taskId
     */
    public void stopWorkOnTask(String taskId) {
        log.debug("[{}] stop working on task {}", dtqId, taskId);
        redisson.getQueue(redisSharedWorkQueue).remove(taskId);
        redisson.getTopic(redissonDoneTopic).publish(taskId);
    }

    /**
     * Package private, allows to call from distributed runnable (decorator)
     * @param taskId
     * @param result
     */
    public void storeResults(String taskId, Object result) {
        log.trace("[{}] write result to redis resultMap <taskId, results>", dtqId);
        RMap<String, Object> results = redisson.getMap(redissonResultsMap);
        results.put(taskId, result);
    }


    /**
     * Compose chained task for internal usage
     */
    public static class ChainedDistributedTask implements Serializable {
        private final IDistributedTask task;
        private final Set<String> downstreamTasks;

        public ChainedDistributedTask(IDistributedTask task) {
            this.task = task;
            this.downstreamTasks = new HashSet<>();
        }

        public Set<String> getDownstreamTasks() {
            return downstreamTasks;
        }

        public IDistributedTask getTask() {
            return task;
        }
    }
}
