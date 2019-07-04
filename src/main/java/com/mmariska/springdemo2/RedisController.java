package com.mmariska.springdemo2;

import com.mmariska.springdemo2.distributedTaskQueue.*;
import com.mmariska.springdemo2.distributedTaskQueue.examples.AggregationSleepingDistributedTaskRunnable;
import com.mmariska.springdemo2.distributedTaskQueue.examples.SleepingDistributedTask;
import com.mmariska.springdemo2.distributedTaskQueue.examples.ExampleSimpleTask;
import com.mmariska.springdemo2.distributedTaskQueue.examples.LongSleepingDistributedTaskRunnable;
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

//        RExecutorService executorService = redisson.getExecutorService("myExecutor", options);
//        Future<Long> future = executorService.submit(new CallableTask());
//
//        String taskId = ((RExecutorFuture<Long>) future).getTaskId();
//        log.info("started taskId = " + taskId);
//
//        Long result = future.get();
        long result = 0L;
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
        return "Task ["+task+"] is done = " + distributedTaskQueue.getFuture(task).isDone();
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

    @RequestMapping(value="/worker", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String worker() {
        distributedTaskQueue.subscribeWorker();
        return "("+ System.getenv("MY_POD_NAME") + ") distributed worker registered";
    }

    @RequestMapping(value="/driver/2", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String driver2() {
        return "offer long task - taskId = " + distributedTaskQueue.offer(new LongSleepingDistributedTaskRunnable());
    }

    @RequestMapping(value="/driver/3", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String driver3() {
//        return "offer fail task - taskId = " + distributedTaskQueue.offer(new FailDistributedTaskRunnable());
        return "offer fail task - taskId = " + distributedTaskQueue.offer(new ExampleSimpleTask());
    }

    @RequestMapping(value="/result/{taskId}", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String getDistributedResult(@PathVariable() String taskId) throws ExecutionException, InterruptedException {
        return "Task ["+taskId+"] distributed result = " + distributedTaskQueue.getFuture(taskId).get();
    }

    @RequestMapping(value="/driver/agg", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String driver0() throws InterruptedException, ExecutionException {
        SleepingDistributedTask task1 = new LongSleepingDistributedTaskRunnable();
        distributedTaskQueue.offer(task1);
        SleepingDistributedTask task2 = new LongSleepingDistributedTaskRunnable();
        distributedTaskQueue.offer(task2);
        AggregationSleepingDistributedTaskRunnable taskAgg = new AggregationSleepingDistributedTaskRunnable(task1.getId(), task2.getId());
        CompletableFuture<Object> aggregFuture = distributedTaskQueue.offerChain(taskAgg);
        return "offer tasks with aggregation - donwstream taskIds = " + Arrays.asList(task1, task2, taskAgg.getId()) + " agg result = " + aggregFuture.get();
    }

    @RequestMapping(value="/driver/1", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String driver1() throws InterruptedException, ExecutionException {
        SleepingDistributedTask task1 = new SleepingDistributedTask();
        Future<?> task1result = distributedTaskQueue.offer(task1);
        SleepingDistributedTask task2 = new SleepingDistributedTask();
        Future<?> task2result = distributedTaskQueue.offer(task2);
        AggregationSleepingDistributedTaskRunnable taskAgg = new AggregationSleepingDistributedTaskRunnable(task1.getId(), task2.getId());
        distributedTaskQueue.offerChain(taskAgg);
        return String.format("offer case: %s + %s = %s",  task1.getId(), task2.getId(), taskAgg.getId());
    }

    @RequestMapping(value="/driver/s", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String driverS() throws InterruptedException, ExecutionException {
        Future<?> future = distributedTaskQueue.offer(new ExampleSimpleTask());
        return String.format("offer case: %s + %s = %s",  future.get());
    }

}
