package com.mmariska;

import com.mmariska.springdemo2.distributedTaskQueue.DistributedTaskQueue;
import com.mmariska.springdemo2.distributedTaskQueue.examples.ExampleSimpleTask;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
        distributedTaskQueue.offer(testDistributedTask1);
        TestDistributedTask testDistributedTask2 = new TestDistributedTask(2);
        distributedTaskQueue.offer(testDistributedTask2);
        Future<?> futureAggregatedResult = distributedTaskQueue.offerChain(new TestAggregatedDistributedTask(testDistributedTask1.getId(), testDistributedTask2.getId()));
        assertEquals("chained result correctly processed", 3L, futureAggregatedResult.get(10, TimeUnit.SECONDS));
    }

    @Test
    public void testGetFutureForTaskAgain() throws ExecutionException, InterruptedException, TimeoutException {
        distributedTaskQueue.subscribeWorker();
        TestDistributedTask testDistributedTask1 = new TestDistributedTask(5);
        distributedTaskQueue.offer(testDistributedTask1);
        TestDistributedTask testDistributedTask2 = new TestDistributedTask(2);
        distributedTaskQueue.offer(testDistributedTask2);
        TestAggregatedDistributedTask aggTask = new TestAggregatedDistributedTask(testDistributedTask1.getId(), testDistributedTask2.getId());
        Future<?> futureAggregatedResult = distributedTaskQueue.offerChain(aggTask);
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
}
