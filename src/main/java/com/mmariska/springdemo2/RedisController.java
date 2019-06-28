package com.mmariska.springdemo2;

import com.mmariska.springdemo2.distributedTaskQueue.DistributedTaskQueue;
import org.redisson.Redisson;
import org.redisson.api.*;
import org.redisson.api.listener.MessageListener;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * this is for spring concurrency reasons
 */
@RequestMapping("/redis")
@RestController
public class RedisController {
    private static final Logger log = LoggerFactory.getLogger(RedisController.class);
    private final RedissonClient redisson;
    private final DistributedTaskQueue distributedTaskQueue;

    public RedisController (){
        Config config = new Config();
        config.useSingleServer().setAddress(getRedisAddress());
        redisson = Redisson.create(config);

        distributedTaskQueue = new DistributedTaskQueue();
    }

    private String getRedisAddress() {
        return System.getenv("REDIS_HOST") != null ? System.getenv("SD2_REDIS_HOST") : "redis://127.0.0.1:6379";
    }

    @RequestMapping(value="/schedule", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String call1() throws ExecutionException, InterruptedException {
        ExecutorOptions options = ExecutorOptions.defaults();
        // Defines task retry interval at the end of which task is executed again.
        // ExecutorService worker re-schedule task execution retry every 5 seconds.
        // Set 0 to disable.
        // Default is 5 minutes
        options.taskRetryInterval(10, TimeUnit.MINUTES);

        RExecutorService executorService = redisson.getExecutorService("myExecutor", options);
        Future<Long> future = executorService.submit(new CallableTask());

        String taskId = ((RExecutorFuture<Long>) future).getTaskId();
        log.info("started taskId = " + taskId);

        Long result = future.get();
        return "("+ System.getenv("MY_POD_NAME") + ") result from redisson myExecutor Long = " + result;
    }

    // producer put into q1 - jobs wait for customers
    // customer put from q1 a put into q2 working on it.. (atomix operation > pollLastAndOfferFirstTo via redisson )
    // consumer after completion removes item from q2 as done
    // jobs in q1 are waiting
    // jobs in q2 are in progress
    // how to wait j3 > j1,j2

    // conditional jobs map > [ j3: j1,j2 | j4: j3,j1  ]
    // evaluated when some job finished

    // producing > j1, j2, conditional j3(j1, j2)

    // listener on "job done" > process also conditional job map, and put it into queue (can also pass results)

    @RequestMapping(value="/myMap/fill", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String fillMyMap() {
        RBatch batch = redisson.createBatch();
        batch.getMap("myMap").fastPutAsync("one",1);
        batch.getMap("myMap").fastPutAsync("two",2);
        batch.getMap("myMap").fastPutAsync("three",3);
        BatchResult<?> execute = batch.execute();

        return "("+ System.getenv("MY_POD_NAME") + ") putted values into 'myMap' - " + execute.getResponses();
    }


    @RequestMapping(value="/q/list", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String qList() {
        RQueue<String> q1 = redisson.getQueue("q1");
        RQueue<String> q2 = redisson.getQueue("q2");
        return "("+ System.getenv("MY_POD_NAME") + ") dtq = " + q1.toString() + " \n q2 = " + q2.toString();
    }

    @RequestMapping(value="/q/workers", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String workers() {
        RExecutorService executorService = redisson.getExecutorService("myExecutor", ExecutorOptions.defaults());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executorService.registerWorkers(2, executor);
        RQueue<String> q1 = redisson.getQueue("q1");
        String poll = q1.poll();
        return "("+ System.getenv("MY_POD_NAME") + ") workers registered";
    }



    @RequestMapping(value="/q/consume", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String q2Put() {
        RQueue<String> q1 = redisson.getQueue("q1");
        String currentTaskId = q1.pollLastAndOfferFirstTo("q2");
        return "("+ System.getenv("MY_POD_NAME") + ") queue currentTask = "+ currentTaskId;
    }

    @RequestMapping(value="/q/done/{task}", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String doneTask(@PathVariable() String task) {
        return "Task ["+task+"] is done = " + distributedTaskQueue.isTaskDone(task);
    }

    @RequestMapping(value="/q/subondelete", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String subondelete() {
        RQueue<String> qu = redisson.getQueue("q2");
        int i = qu.addListener((DeletedObjectListener) taskId -> log.info("handle deleted job from q2 - " + taskId));
        return "subscribed - " + i;
    }

    @RequestMapping(value="/q/put", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String q1Put() {
        RQueue<String> q1 = redisson.getQueue("q1");
        return "("+ System.getenv("MY_POD_NAME") + ") queue offer = "+ q1.offer("job" + UUID.randomUUID().toString());
    }

    @RequestMapping(value="/q/sub", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String sub() throws InterruptedException {
        RTopic eb = redisson.getTopic("eb");
        return "topic listener - " + eb.addListener(String.class, new MessageListener<String>() {
            @Override
            public void onMessage(CharSequence channel, String msg) {
                log.info("received msg " + channel + " - " + msg);
            }
        });
    }

    @RequestMapping(value="/q/pub", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String pub() throws InterruptedException {
        RTopic eb = redisson.getTopic("eb");
        return "published " + eb.publish("msg"+UUID.randomUUID().toString());
    }


    @RequestMapping(value="/q/test1", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String test1() throws InterruptedException {
        RQueue<String> qu = redisson.getQueue("q2");
        int i = qu.addListener((ExpiredObjectListener) taskId -> log.info("handle expired job from q2 - " + taskId));
        int i2 = qu.addListener((DeletedObjectListener) taskId -> log.info("handle deleted job from q2 - " + taskId));
        qu.offer("test1");
        qu.expire(1, TimeUnit.MILLISECONDS);
        Thread.sleep(200);
        String poll = qu.poll();
        return "pooled item - " + poll;
    }


    // DRIVER lifecycle
    // check condition jobs
    // listen on jobDone, jobError

    // WORKER lifecycle
    // worker starts > checkJobs
    // listen for newJobEvent > checkJobs

    @RequestMapping(value="/get", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String qGet() {
        return "("+ System.getenv("MY_POD_NAME") + ") dtq = " + distributedTaskQueue.debugPrintQueues();
    }

    @RequestMapping(value="/clear", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String qClear() {
        RQueue<Object> queueWait = redisson.getQueue(DistributedTaskQueue.REDIS_SHARED_WAIT_QUEUE);
        queueWait.clear();
        RQueue<Object> queueWork = redisson.getQueue(DistributedTaskQueue.REDIS_SHARED_WORK_QUEUE);
        queueWork.clear();
        return "("+ System.getenv("MY_POD_NAME") + ") ques = " + Arrays.asList(queueWait, queueWork);
    }



    @RequestMapping(value="/worker", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String worker() {
        distributedTaskQueue.subscribeWorker();
        return "("+ System.getenv("MY_POD_NAME") + ") distributed worker registered";
    }

    @RequestMapping(value="/driver/2", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String driver2() {
        return "offer long task - taskId = " + distributedTaskQueue.offer(new LongDistributedTaskRunnable());
    }

    @RequestMapping(value="/driver/3", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String driver3() {
        return "offer fail task - taskId = " + distributedTaskQueue.offer(new FailDistributedTaskRunnable());
    }

    @RequestMapping(value="/driver/agg", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String driver0() throws InterruptedException, ExecutionException {
        DistributedTaskRunnable task1 = new LongDistributedTaskRunnable();
        distributedTaskQueue.offer(task1);
        DistributedTaskRunnable task2 = new LongDistributedTaskRunnable();
        distributedTaskQueue.offer(task2);
        AggregationDistributedTaskRunnable taskAgg = new AggregationDistributedTaskRunnable(task1.getTaskId(), task2.getTaskId());
        Future<Object> aggregFuture = distributedTaskQueue.offerChain(taskAgg, task1.getTaskId(), task2.getTaskId());
        // pujde doimplementovat distributedTaskQueue.getFuture(taskId) :)

        //tree ROOT > regions

        return "offer tasks with aggregation - donwstream taskIds = " + Arrays.asList(task1, task2, taskAgg.getTaskId()) + " agg result = " + aggregFuture.get();
    }

    @RequestMapping(value="/driver/1", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String driver1() throws InterruptedException, ExecutionException {
        DistributedTaskRunnable task1 = new DistributedTaskRunnable();
        Future<?> task1result = distributedTaskQueue.offer(task1);
        DistributedTaskRunnable task2 = new DistributedTaskRunnable();
        Future<?> task2result = distributedTaskQueue.offer(task2);
        AggregationDistributedTaskRunnable taskAgg = new AggregationDistributedTaskRunnable(task1.getTaskId(), task2.getTaskId());
        distributedTaskQueue.offerChain(taskAgg, task1.getTaskId(), task2.getTaskId());
        return String.format("offer case: %s + %s = %s",  task1.getTaskId(), task2.getTaskId(), taskAgg.getTaskId());
    }

}
