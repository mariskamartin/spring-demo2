package com.mmariska.springdemo2.distributedTaskQueue;

import com.mmariska.springdemo2.DistributedTaskRunnable;
import org.redisson.Redisson;
import org.redisson.api.*;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

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
        options.taskRetryInterval(15, TimeUnit.SECONDS);
        RExecutorService executorService = redissonClient.getExecutorService(REDIS_SHARED_EXECUTOR, options);
        RExecutorFuture<?> future = executorService.submit(task);
//        String taskId = future.getTaskId(); //do not use redis task id
        log.info("scheduled task Id = " + task.getTaskId());
        return future;
    }

    public Future<Object> offerChain(DistributedTaskRunnable task, String... downStreamTasks) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        RTopic taskDoneTopic = redisson.getTopic(REDISSON_DONE_TOPIC);

        //fixme this is not efficient... this creates new and new listeners :/ better to have map of futures and ids and one listener :)
        taskDoneTopic.addListener(String.class, (channel, doneTaskId) -> {
            if (doneTaskId.equals(task.getTaskId())) {
                future.complete(redisson.getMap(REDISSON_RESULTS_MAP).get(doneTaskId));
            }
        });

        RMap<DistributedTaskRunnable, Set<String>> chainedTasksMap = redisson.getMap(REDIS_SHARED_CHAIN_TASK_MAP);
        Set<String> stringSet = new HashSet<>(Arrays.asList(downStreamTasks));
        chainedTasksMap.put(task, stringSet);
        return future;
    }

    public static void checkChainedTasksAfterTaskDone(RedissonClient redissonClient, String doneTask) {
        RMap<DistributedTaskRunnable, Set<String>> chainedTasksMap = redissonClient.getMap(REDIS_SHARED_CHAIN_TASK_MAP);
        log.info("chainedTasks definitions = {}", chainedTasksMap.keySet().size());
        for (Map.Entry<DistributedTaskRunnable, Set<String>> entry : chainedTasksMap.entrySet()) {
            Set<String> downStreamTasks = entry.getValue();
            if(downStreamTasks.remove(doneTask)) {
                chainedTasksMap.put(entry.getKey(), downStreamTasks); //update map
                DistributedTaskRunnable chainedTask = entry.getKey();
                log.info("removed task ({}) from {}", doneTask, chainedTask);
                if (entry.getValue().isEmpty()) {
                    offer(redissonClient, chainedTask);
                    chainedTasksMap.remove(entry.getKey()); // remove itself from map
                }
            }
        }
    }

    public boolean isTaskDone(String taskId) {
        RBatch batch = redisson.createBatch();
        batch.getQueue(REDIS_SHARED_WAIT_QUEUE).containsAsync(taskId);
        batch.getQueue(REDIS_SHARED_WORK_QUEUE).containsAsync(taskId);
//        batch.getMap(REDIS_SHARED_CHAIN_TASK_MAP).containsKeyAsync(taskId) // fixme we cannot directly ask.. there is object not id
        BatchResult<?> execute = batch.execute();
        for (Object resp : execute.getResponses()) {
            if ((boolean) resp) {
                return false; //still running somewhere
            }
        }
        return true; //task done
    }

    public boolean recheckFailures() {
        //needs to reschedule failed tasks pending in queues
        // do something with errors in tasks
        return false;
    }

    /**
     * Fixme this implementation is not cheap to call! Itroduce cache or do not print queues at all. This is for debug only
     * @return
     */
    public String debugPrintQueues() {
        return String.format("distributedTaskQueue [waitQueue = %s, workQueue = %s]", redisson.getQueue(REDIS_SHARED_WAIT_QUEUE), redisson.getQueue(REDIS_SHARED_WORK_QUEUE));
    }

    private String getRedisAddress() {
        //fixme refactor
        return System.getenv("REDIS_HOST") != null ? System.getenv("SD2_REDIS_HOST") : "redis://127.0.0.1:6379";
    }

    public Object getResult(String taskId) {
        RMap<String, Object> results = redisson.getMap(REDISSON_RESULTS_MAP);
        return results.get(taskId);
    }
}
