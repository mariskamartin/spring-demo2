package com.mmariska;

import com.mmariska.springdemo2.distributedTaskQueue.DistributedTaskQueue;
import com.mmariska.springdemo2.distributedTaskQueue.IDistributedTask;
import com.mmariska.springdemo2.distributedTaskQueue.examples.ExampleSimpleTask;
import com.mmariska.springdemo2.distributedTaskQueue.examples.HighPriorityExampleSimpleTask;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;

import java.util.concurrent.*;

import static org.junit.Assert.*;

/**
 * Test Integration test for Redis-backed Distributed Task Queue.
 */
public class DistributedTaskQueueTest {

    @Rule
    public GenericContainer redis = new GenericContainer<>("redis:5.0.3-alpine").withExposedPorts(6379);
//    @ClassRule
//    public static GenericContainer redis = new GenericContainer<>("redis:5.0.3-alpine").withExposedPorts(6379);
    private DistributedTaskQueue distributedTaskQueue;

    @Before
    public void setUp() {
        distributedTaskQueue = new DistributedTaskQueue("redis://"+ redis.getContainerIpAddress() +":"+ redis.getFirstMappedPort());
    }

    @Test
    public void testOfferAndProcessTask() throws ExecutionException, InterruptedException, TimeoutException {
        distributedTaskQueue.subscribeWorker();
        Future<?> futureResult = distributedTaskQueue.offer(new TestDistributedTask(2));
        assertEquals("result correctly processed", 2L, futureResult.get(10, TimeUnit.SECONDS));
    }

    @Test
    public void testSimpleClassOfferAndProcessTask() throws ExecutionException, InterruptedException, TimeoutException {
        distributedTaskQueue.subscribeWorker();
        Future<?> futureResult = distributedTaskQueue.offer(new ExampleSimpleTask());
        assertEquals("result correctly processed", 0L, futureResult.get());
    }

    @Test
    public void testSimpleOfferAndProcessChainedTask() throws ExecutionException, InterruptedException, TimeoutException {
        distributedTaskQueue.subscribeWorker();
        TestDistributedTask testDistributedTask1 = new TestDistributedTask(1);
        TestDistributedTask testDistributedTask2 = new TestDistributedTask(2);
        // here it cares on order
        Future<?> futureAggregatedResult = distributedTaskQueue.offerChain(new TestAggregatedDistributedTask(testDistributedTask1.getId(), testDistributedTask2.getId()));
        distributedTaskQueue.offer(testDistributedTask1);
        distributedTaskQueue.offer(testDistributedTask2);
        assertEquals("chained result correctly processed", 3L, futureAggregatedResult.get(10, TimeUnit.SECONDS));
    }

    @Test
    public void testOfferAndProcessChainedTaskOfferedEarlier() throws ExecutionException, InterruptedException, TimeoutException {
        distributedTaskQueue.subscribeWorker();
        TestDistributedTask testDistributedTask1 = new TestDistributedTask(1);
        TestDistributedTask testDistributedTask2 = new TestDistributedTask(2);
        // here, worker process first task earlier than we register chain
        distributedTaskQueue.offer(testDistributedTask1);
        distributedTaskQueue.offer(testDistributedTask2);
        Future<?> futureAggregatedResult = distributedTaskQueue.offerChain(new TestAggregatedDistributedTask(testDistributedTask1.getId(), testDistributedTask2.getId()));
        assertEquals("chained result correctly processed", 3L, futureAggregatedResult.get(10, TimeUnit.SECONDS));
    }

    @Test
    public void testGetFutureForTaskAgain() throws ExecutionException, InterruptedException, TimeoutException {
        distributedTaskQueue.subscribeWorker();
        TestDistributedTask testDistributedTask1 = new TestDistributedTask(5);
        TestDistributedTask testDistributedTask2 = new TestDistributedTask(2);
        TestAggregatedDistributedTask aggTask = new TestAggregatedDistributedTask(testDistributedTask1.getId(), testDistributedTask2.getId());
        // here also care on order
        // here is a case when first job is done earlier than chain Job and worker has no chance to find it in chain jobs
        Future<?> futureAggregatedResult = distributedTaskQueue.offerChain(aggTask);
        distributedTaskQueue.offer(testDistributedTask1);
        distributedTaskQueue.offer(testDistributedTask2);
        Future<Object> aggFuture2 = distributedTaskQueue.getFuture(aggTask.getId());
        assertEquals("chained result correctly processed", 7L, futureAggregatedResult.get(10, TimeUnit.SECONDS));
        assertEquals("chained result correctly processed and returned again", 7L, aggFuture2.get(10, TimeUnit.SECONDS));
    }

    @Test
    public void testTaskIsDone() throws ExecutionException, InterruptedException, TimeoutException {
        TestDistributedTask task1 = new TestDistributedTask(1);
        // todo should we test false before offering? :( it is strange
        Future<?> taskFuture = distributedTaskQueue.offer(task1);
        assertFalse(distributedTaskQueue.isTaskDone(task1.getId()));
        distributedTaskQueue.subscribeWorker();
        taskFuture.get(10, TimeUnit.SECONDS);
        assertTrue(distributedTaskQueue.isTaskDone(task1.getId()));
    }

    @Test
    public void testCoexistenceOfDistributedQueues() throws ExecutionException, InterruptedException, TimeoutException {
        DistributedTaskQueue dtq1 = new DistributedTaskQueue("redis://" + redis.getContainerIpAddress() + ":" + redis.getFirstMappedPort(), "q1");
        DistributedTaskQueue dtq2 = new DistributedTaskQueue("redis://" + redis.getContainerIpAddress() + ":" + redis.getFirstMappedPort(), "q2");
        dtq1.offer(new TestDistributedTask(1));
        Future<?> offer = dtq1.offer(new TestDistributedTask(2));
        Future<?> offer2 = dtq2.offer(new TestDistributedTask(3));
        dtq1.subscribeWorker();
        assertEquals(2L, offer.get(10, TimeUnit.SECONDS));
        try {
            offer2.get(5, TimeUnit.SECONDS); // fixme this is slow test, make this somehow different
        } catch (TimeoutException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testFailTask() throws ExecutionException, InterruptedException, TimeoutException {
        distributedTaskQueue.subscribeWorker();
        CompletableFuture<?> taskFuture = distributedTaskQueue.offer(new TestFailTask());
        try {
            taskFuture.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            assertEquals("Just fail", e.getCause().getMessage());
        }
    }

    @Test
    public void testPriorityOrderOfTask() throws ExecutionException, InterruptedException, TimeoutException {
        IDistributedTask task1 = new ExampleSimpleTask();
        IDistributedTask task2 = new HighPriorityExampleSimpleTask();
        IDistributedTask task3 = new ExampleSimpleTask();
        IDistributedTask task4 = new HighPriorityExampleSimpleTask();
        IDistributedTask task5 = new ExampleSimpleTask();
        distributedTaskQueue.offer(task1);
        distributedTaskQueue.offer(task2);
        distributedTaskQueue.offer(task3);
        distributedTaskQueue.offer(task4);
        distributedTaskQueue.offer(task5);
        IDistributedTask iDistributedTask = distributedTaskQueue.workerPoolLastTaskBlocking();
        assertEquals(task4.getId(), iDistributedTask.getId());
        iDistributedTask = distributedTaskQueue.workerPoolLastTaskBlocking();
        assertEquals(task2.getId(), iDistributedTask.getId());
    }
}
