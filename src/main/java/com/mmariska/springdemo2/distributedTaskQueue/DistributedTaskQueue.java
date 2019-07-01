package com.mmariska.springdemo2.distributedTaskQueue;

import com.mmariska.springdemo2.DistributedTaskRunnable;
import org.redisson.Redisson;
import org.redisson.api.*;
import org.redisson.api.listener.MessageListener;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;


/**
 * Todo - types handling
*       - error handeling
 *      - results lifecycle (aggregation tasks? TTL?)
 */
public class DistributedTaskQueue {
    private static final Logger log = LoggerFactory.getLogger(DistributedTaskRunnable.class);

    public static final String REDIS_SHARED_EXECUTOR = "queueExecutor";
    public static final String REDIS_SHARED_WAIT_QUEUE = "waitQueue"; //waiting for workers
    public static final String REDIS_SHARED_WORK_QUEUE = "workQueue"; //workers already started work on these tasks
    public static final String REDIS_SHARED_CHAIN_TASK_MAP = "chainedTasks";
    public static final String REDISSON_RESULTS_MAP = "results";
    public static final String REDISSON_DONE_TOPIC = "taskDoneTopic";
    private final RedissonClient redisson;

    public DistributedTaskQueue() {
        // fixme - blbost vyrabet bez moznosti konfigurace... ?? fasada nema byt zavisla na redissonu
        Config config = new Config();
        config.useSingleServer().setAddress(getRedisAddress());
        redisson = Redisson.create(config);
    }

    public boolean subscribeWorker() {
        RExecutorService executorService = redisson.getExecutorService(REDIS_SHARED_EXECUTOR, ExecutorOptions.defaults());
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executorService.registerWorkers(1, executor);
        return true; //fixme
    }


    public Future<?> offer(DistributedTaskRunnable task) {
        return offer(redisson, task);
    }

    public static Future<?> offer(RedissonClient redissonClient, DistributedTaskRunnable task) {
        if( ! redissonClient.getQueue(REDIS_SHARED_WAIT_QUEUE).offer(task.getTaskId()) ) {
            throw new IllegalStateException("Problem with scheduling task " + task.getTaskId() + " - " + task);
        }

        //just and schedule concrete task - queue is created on redis executor side
        ExecutorOptions options = ExecutorOptions.defaults();
        options.taskRetryInterval(0, TimeUnit.SECONDS);
        /* todo - we do not want to reschedule automatically, when all apps are down and some task is in redis, Redis will reschedule it or we can reuse of this functionality and refind job in work queue also */
        RExecutorService executorService = redissonClient.getExecutorService(REDIS_SHARED_EXECUTOR, options);
        RExecutorFuture<?> future = executorService.submit(task);
//        String taskId = future.getTaskId(); // do not use redis task id - we have problems hot to obtain taskId for chainedTasks
        log.debug("scheduled task Id = {}", task.getTaskId());
        return future;
    }

    public Future<Object> offerChain(DistributedTaskRunnable task, String... downStreamTasks) {
        RMap<String, ChainedDistributedTask> chainedTasksMap = redisson.getMap(REDIS_SHARED_CHAIN_TASK_MAP);
        ChainedDistributedTask chainedTask = new ChainedDistributedTask(task);
        chainedTask.getDownstreamTasks().addAll(Arrays.asList(downStreamTasks));
        chainedTasksMap.put(task.getTaskId(), chainedTask);
        log.debug("scheduled chain for task Id = {}", task.getTaskId());
        return listenOnTaskResult(task.getTaskId());
    }

    public static void checkChainedTasksAfterTaskDone(RedissonClient redissonClient, String doneTask) {
        RMap<String, ChainedDistributedTask> chainedTasksMap = redissonClient.getMap(REDIS_SHARED_CHAIN_TASK_MAP);
        log.trace("chainedTasks definitions = {}", chainedTasksMap.keySet().size());
        for (Map.Entry<String, ChainedDistributedTask> entry : chainedTasksMap.entrySet()) {
            ChainedDistributedTask chainedTask = entry.getValue();
            if(chainedTask.getDownstreamTasks().remove(doneTask)) {
                chainedTasksMap.put(entry.getKey(), chainedTask); //update map
                log.trace("removed task ({}) from {}", doneTask, chainedTask.getTask());
                if (chainedTask.getDownstreamTasks().isEmpty()) {
                    offer(redissonClient, chainedTask.getTask());
                    chainedTasksMap.remove(entry.getKey()); // remove itself from map
                }
            }
        }
    }

    public boolean isTaskDone(String taskId) {
        RBatch batch = redisson.createBatch();
        batch.getQueue(REDIS_SHARED_WAIT_QUEUE).containsAsync(taskId);
        batch.getQueue(REDIS_SHARED_WORK_QUEUE).containsAsync(taskId);
        batch.getMap(REDIS_SHARED_CHAIN_TASK_MAP).containsKeyAsync(taskId);
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
        return String.format("distributedTaskQueue [waitQueue = %s, workQueue = %s, chainedTasks = %s]", redisson.getQueue(REDIS_SHARED_WAIT_QUEUE), redisson.getQueue(REDIS_SHARED_WORK_QUEUE), redisson.getMap(REDIS_SHARED_CHAIN_TASK_MAP).readAllKeySet());
    }

    private String getRedisAddress() {
        //fixme refactor
        return System.getenv("REDIS_HOST") != null ? System.getenv("SD2_REDIS_HOST") : "redis://127.0.0.1:6379";
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
            boolean complete = completedFuture.complete(getResultsMap().get(taskId));
            return completedFuture;
        } else {
            CompletableFuture<Object> future = listenOnTaskResult(taskId);
            return future;
        }
    }

    private CompletableFuture<Object> listenOnTaskResult(String taskId) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        RTopic taskDoneTopic = redisson.getTopic(REDISSON_DONE_TOPIC);

        // todo - probably one centralized listener will be more efficient
        MessageListener<String> messageListener = new MessageListener<String>() {
            @Override
            public void onMessage(CharSequence channel, String doneTaskId) {
                log.trace("{} on message {}", this, doneTaskId);
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
        return redisson.getMap(REDISSON_RESULTS_MAP);
    }
}
