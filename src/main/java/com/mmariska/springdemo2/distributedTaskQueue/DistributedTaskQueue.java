package com.mmariska.springdemo2.distributedTaskQueue;

import com.mmariska.springdemo2.distributedTaskQueue.examples.SleepingDistributedTask;
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
 *      done - error handeling > maybe to future
 *      done - priority of tasks
 *      - types handling > is needed ?
 *      - results lifecycle (aggregation tasks? TTL?) > null is not stored at all
 */
public class DistributedTaskQueue {
    private static final Logger log = LoggerFactory.getLogger(SleepingDistributedTask.class);

    private final String redisSharedWaitQueue; //waiting for workers
    private final String redisSharedWorkQueue; //workers already started work on these tasks
    private final String redisSharedChainTaskMap;
    private final String redissonResultsMap;
    private final String redissonDoneTopic;
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
        redisSharedWaitQueue = dtqId + "WaitQueue";
        redisSharedWorkQueue = dtqId + "WorkQueue";
        redisSharedChainTaskMap = dtqId + "ChainedTasks";
        redissonResultsMap = dtqId + "Results";
        redissonDoneTopic = dtqId + "DoneTopic";
    }

    public boolean subscribeWorker() {
        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.submit(new QueueWorker(this));
        return true; //for now there is no more logic around
    }


    public CompletableFuture<Object> offer(IDistributedTask task) {
        RPriorityBlockingQueue<IDistributedTask> priorityBlockingWaitingQueue = getPriorityBlockingWaitingQueue();
        if (priorityBlockingWaitingQueue.isEmpty()) priorityBlockingWaitingQueue.trySetComparator(new PriorityTaskIdComparator());
        if(!priorityBlockingWaitingQueue.offer(task)) {
            throw new IllegalStateException("Problem with scheduling task " + task.getId() + " - " + task);
        }
        log.debug("[{}] scheduled task Id = {}", dtqId, task.getId());
        return listenOnTaskResult(task.getId());
    }

    public CompletableFuture<Object> offerChain(IChainedDistributedTask task) {
        RMap<String, ChainedDistributedTask> chainedTasksMap = redisson.getMap(redisSharedChainTaskMap);
        ChainedDistributedTask chainedTask = new ChainedDistributedTask(task);
        chainedTask.getDownstreamTasks().addAll(Arrays.asList(task.getDownstreamTaskIds()));
        chainedTasksMap.put(task.getId(), chainedTask);
        log.debug("[{}] scheduled chain for task Id = {}", dtqId, task.getId());
        return listenOnTaskResult(task.getId());
    }

    //fixme (encapsulation?) this is called from workers after done task
    public void checkChainedTasks(String doneTask) {
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
        return String.format("distributedTaskQueue [waitQueue = %s, workQueue = %s, chainedTasks = %s]", getPriorityBlockingWaitingQueue(), redisson.getQueue(redisSharedWorkQueue), redisson.getMap(redisSharedChainTaskMap).readAllKeySet());
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
    public CompletableFuture<Object> getFuture(String taskId) {
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
                        Object value = getResultsMap().get(doneTaskId);
                        // plan eviction on result when received somewhere
//                        getResultsMap().put(doneTaskId, value, 30, TimeUnit.SECONDS); // this is working but what it influence?
                        if (value instanceof Throwable) {
                            future.completeExceptionally((Throwable) value);
                        } else {
                            future.complete(value);
                        }
                    } finally {
                        taskDoneTopic.removeListener(this);
                    }
                }
            }
        };
        taskDoneTopic.addListener(String.class, messageListener);
        return future;
    }

    private RMapCache<String, Object> getResultsMap() {
        return redisson.<String, Object>getMapCache(redissonResultsMap);
    }

    private RQueue<IDistributedTask> getWorkQueue() {
        return redisson.<IDistributedTask>getQueue(redisSharedWorkQueue);
    }

    private RPriorityBlockingQueue<IDistributedTask> getPriorityBlockingWaitingQueue() {
        //alphabeticalOrder Z (low), A (high)
        //HIGH_taskID
        //NORMAL_taskID
        //OTHERS_taskID
        // [otherTasksIds, normalTaskId, highTaskIds], we pool Last item
        return redisson.<IDistributedTask>getPriorityBlockingQueue(redisSharedWaitQueue);
    }

    public IDistributedTask workerPoolLastTaskBlocking() {
        IDistributedTask task = getPriorityBlockingWaitingQueue().pollLastAndOfferFirstTo(redisSharedWorkQueue);
        log.debug("worker {} take task {}", this, task.getId());
        if (task == null)
            throw new IllegalStateException("after take task is null!");
        return task;
    }

    public void workerSuccessfullyEnd(IDistributedTask task) {
        log.debug("[{}] end working on task {}", dtqId, task.getId());
        getWorkQueue().remove(task);
    }

    public void workerPublishDone(String taskId) {
        log.debug("[{}] publish done for task {}", dtqId, taskId);
        redisson.getTopic(redissonDoneTopic).publish(taskId);
    }

    public void workerStoreResults(String taskId, Object result) {
        log.trace("[{}] write result to redis resultMap <taskId, results>", dtqId);
        getResultsMap().put(taskId, result);
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

    /**
     * Compare two tasks, based on task priority
     */
    public static class PriorityTaskIdComparator implements java.util.Comparator<IDistributedTask> {
        @Override
        public int compare(IDistributedTask o1, IDistributedTask o2) {
            int priority = o1.getPriority() - o2.getPriority();
            return priority == 0 ? Math.toIntExact(o2.getCreatedTime() - o1.getCreatedTime()) : priority;
        }
    }
}
