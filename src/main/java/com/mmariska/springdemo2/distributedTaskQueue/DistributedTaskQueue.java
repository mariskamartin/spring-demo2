package com.mmariska.springdemo2.distributedTaskQueue;

import com.mmariska.springdemo2.DistributedTaskRunnable;
import org.redisson.Redisson;
import org.redisson.api.*;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DistributedTaskQueue {
    private static final Logger log = LoggerFactory.getLogger(DistributedTaskRunnable.class);

    public static final String REDIS_SHARED_EXECUTOR = "queueExecutor";
    public static final String REDIS_SHARED_WAIT_QUEUE = "waitQueue"; //waiting for workers
    public static final String REDIS_SHARED_WORK_QUEUE = "workQueue"; //workers already started work on these tasks
    public static final String REDIS_SHARED_CHAIN_TASK_MAP = "chainedTasks";
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


    public String offer(DistributedTaskRunnable task) {
        return offer(redisson, task);
    }

    public static String offer(RedissonClient redissonClient, DistributedTaskRunnable task) {
        ExecutorOptions options = ExecutorOptions.defaults();
        options.taskRetryInterval(15, TimeUnit.SECONDS);
        RExecutorService executorService = redissonClient.getExecutorService(REDIS_SHARED_EXECUTOR, options);
        RExecutorFuture<?> future = ((RScheduledExecutorService) executorService).schedule(task,200, TimeUnit.MILLISECONDS);
        String taskId = future.getTaskId();
        redissonClient.getQueue(REDIS_SHARED_WAIT_QUEUE).offer(taskId);// fixme THIS IS WRONG...
        log.info("scheduled task Id = " + taskId);
        return taskId;

    // producer put into q1 - jobs wait for customers
    // customer put from q1 a put into q2 working on it.. (atomix operation > pollLastAndOfferFirstTo via redisson )
    // consumer after completion removes item from q2 as done
//        return false;
    }

    public void offerChain(DistributedTaskRunnable task, String... downStreamTasks) {
        RMap<DistributedTaskRunnable, Set<String>> chainedTasksMap = redisson.getMap(REDIS_SHARED_CHAIN_TASK_MAP);
        Set<String> stringSet = new HashSet<>(Arrays.asList(downStreamTasks));
        chainedTasksMap.put(task, stringSet);
    }

    public static void checkChainedTasksAfterTaskDone(RedissonClient redissonClient, String doneTask) {
        RMap<DistributedTaskRunnable, Set<String>> chainedTasksMap = redissonClient.getMap(REDIS_SHARED_CHAIN_TASK_MAP);
        for (Map.Entry<DistributedTaskRunnable, Set<String>> entry : chainedTasksMap.entrySet()) {
            Set<String> downStreamTasks = entry.getValue();
            if(downStreamTasks.remove(doneTask)) {
                chainedTasksMap.put(entry.getKey(), downStreamTasks); //update map
                DistributedTaskRunnable chainedTask = entry.getKey();
                log.info("removed task ({}) from {}", doneTask, chainedTask);
                if (entry.getValue().isEmpty()) {
                    offer(redissonClient, chainedTask);
                }
            }
        }
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

}
