package com.mmariska;

import com.mmariska.springdemo2.distributedTaskQueue.DistributedTaskQueue;
import com.mmariska.springdemo2.distributedTaskQueue.IDistributedTask;
import com.mmariska.springdemo2.distributedTaskQueue.examples.ExampleSimpleTask;
import com.mmariska.springdemo2.distributedTaskQueue.examples.HighPriorityExampleSimpleTask;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
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
        distributedTaskQueue.startLocalWorker();
        Future<?> futureResult = distributedTaskQueue.offer(new TestDistributedTask(2));
        assertEquals("result correctly processed", 2L, futureResult.get(10, TimeUnit.SECONDS));
    }

    @Test
    public void testSimpleClassOfferAndProcessTask() throws ExecutionException, InterruptedException, TimeoutException {
        distributedTaskQueue.startLocalWorker();
        Future<?> futureResult = distributedTaskQueue.offer(new ExampleSimpleTask());
        assertEquals("result correctly processed", 0L, futureResult.get());
    }

    @Test
    public void testStopExecutionWhenErrorInChain() throws ExecutionException, InterruptedException, TimeoutException {
        distributedTaskQueue.startLocalWorker();
        IDistributedTask testDistributedTask1 = new TestDistributedTask(1);
        IDistributedTask testDistributedTask2 = new TestFailTask();
        // here it cares on order
        Future<?> futureAggregatedResult = distributedTaskQueue.offerChain(new TestAggregatedDistributedTask(testDistributedTask1.getId(), testDistributedTask2.getId()));
        distributedTaskQueue.offer(testDistributedTask1);
        CompletableFuture<Object> offerFail = distributedTaskQueue.offer(testDistributedTask2);
        try {
            try {
                Object o = offerFail.get();
            } catch (ExecutionException e) {
                assertTrue("should end with exception", true);
            }
            futureAggregatedResult.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            assertTrue("should not execute chain", true);
        }

    }


    @Test
    public void testSimpleOfferAndProcessChainedTask() throws ExecutionException, InterruptedException, TimeoutException {
        distributedTaskQueue.startLocalWorker();
        TestDistributedTask testDistributedTask1 = new TestDistributedTask(1);
        TestDistributedTask testDistributedTask2 = new TestDistributedTask(2);
        // here it cares on order
        Future<?> futureAggregatedResult = distributedTaskQueue.offerChain(new TestAggregatedDistributedTask(testDistributedTask1.getId(), testDistributedTask2.getId()));
        distributedTaskQueue.offer(testDistributedTask1);
        distributedTaskQueue.offer(testDistributedTask2);
        assertEquals("chained result correctly processed", 3L, futureAggregatedResult.get(10, TimeUnit.SECONDS));
    }

    @Test
    public void testOfferAndProcessChainedTaskForDownstreamTasksOfferedEarlier() throws ExecutionException, InterruptedException, TimeoutException {
        distributedTaskQueue.startLocalWorker();
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
        distributedTaskQueue.startLocalWorker();
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
    public void testGetFutureForNonExistentTask() {
        try {
            Future<Object> taskFuture = distributedTaskQueue.getFuture("notCurrentlyExists");
            fail("should not to run");
        } catch (IllegalStateException e) {
            assertTrue("should fail", true);
        }
    }

    @Test
    public void testTaskIsDone() throws ExecutionException, InterruptedException, TimeoutException {
        TestDistributedTask task1 = new TestDistributedTask(1);
        Future<?> taskFuture = distributedTaskQueue.offer(task1);
        assertFalse(taskFuture.isDone());
        distributedTaskQueue.startLocalWorker();
        taskFuture.get(10, TimeUnit.SECONDS);
        assertTrue(taskFuture.isDone());
    }

    @Test
    public void testCoexistenceOfDistributedQueues() throws InterruptedException {
        DistributedTaskQueue dtq1 = new DistributedTaskQueue("redis://" + redis.getContainerIpAddress() + ":" + redis.getFirstMappedPort(), "q1");
        DistributedTaskQueue dtq2 = new DistributedTaskQueue("redis://" + redis.getContainerIpAddress() + ":" + redis.getFirstMappedPort(), "q2");
        TestDistributedTask task1 = new TestDistributedTask(1);
        TestDistributedTask task2 = new TestDistributedTask(2);
        dtq1.offer(task1);
        dtq2.offer(task2);

        IDistributedTask iDistributedTask = dtq1.workerPoolLastTask();
        assertEquals(task1.getId(), iDistributedTask.getId());
        iDistributedTask = dtq1.workerPoolLastTask();
        assertNull(iDistributedTask);

        iDistributedTask = dtq2.workerPoolLastTask();
        assertEquals(task2.getId(), iDistributedTask.getId());
        iDistributedTask = dtq2.workerPoolLastTask();
        assertNull(iDistributedTask);
    }

    @Test
    public void testFailTask() throws InterruptedException, TimeoutException {
        distributedTaskQueue.startLocalWorker();
        CompletableFuture<?> taskFuture = distributedTaskQueue.offer(new TestFailTask());
        try {
            taskFuture.get(10, TimeUnit.SECONDS);
            fail("this should not be executed. exception expected");
        } catch (ExecutionException e) {
            assertEquals("Just fail", e.getCause().getMessage());
        }
    }

    @Test
    public void testInOrderExecutionOfScheduledTask() throws InterruptedException {
        IDistributedTask task1 = new TestDistributedTask(1,"task1-");
        Thread.sleep(2);
        IDistributedTask task2 = new TestDistributedTask(1,"task2-");
        Thread.sleep(2);
        IDistributedTask task3 = new TestDistributedTask(1,"task3-");
        distributedTaskQueue.offer(task1);
        distributedTaskQueue.offer(task2);
        distributedTaskQueue.offer(task3);
        IDistributedTask iDistributedTask = distributedTaskQueue.workerPoolLastTaskBlocking();
        assertEquals(task1.getId(), iDistributedTask.getId());
        iDistributedTask = distributedTaskQueue.workerPoolLastTaskBlocking();
        assertEquals(task2.getId(), iDistributedTask.getId());
        iDistributedTask = distributedTaskQueue.workerPoolLastTaskBlocking();
        assertEquals(task3.getId(), iDistributedTask.getId());
    }

    @Test
    public void testExecutionInOrderOfTaskWithChain() throws ExecutionException, InterruptedException, TimeoutException {
        IDistributedTask task1 = new TestDistributedTask(1,"task1-"); Thread.sleep(5);
        TestAggregatedDistributedTask taskAggCreatedAfterTask1 = new TestAggregatedDistributedTask(task1.getId());
        IDistributedTask task2 = new TestDistributedTask(1,"task2-"); Thread.sleep(5);
        IDistributedTask task3 = new TestDistributedTask(1,"task3-");  Thread.sleep(5);
        CompletableFuture<Object> futureAggTask = distributedTaskQueue.offerChain(taskAggCreatedAfterTask1);
        distributedTaskQueue.offer(task1);
        distributedTaskQueue.offer(task2);
        CompletableFuture<Object> futureTask3 = distributedTaskQueue.offer(task3);

        List<String> doneTasks = new LinkedList<>();
        distributedTaskQueue.subscribeListenerOnDoneTask((channel, doneTaskId) -> doneTasks.add(doneTaskId));
        distributedTaskQueue.startLocalWorker();
        assertEquals(1L, futureAggTask.get()) ;
        assertEquals(1L, futureTask3.get()) ;
        assertEquals(Arrays.asList(task1.getId(), taskAggCreatedAfterTask1.getId(), task2.getId(), task3.getId()), doneTasks) ;
    }


    @Test
    public void testPriorityOrderOfTask() throws ExecutionException, InterruptedException, TimeoutException {
        IDistributedTask task1 = new ExampleSimpleTask(); Thread.sleep(2);
        IDistributedTask task2 = new HighPriorityExampleSimpleTask(); Thread.sleep(2);
        IDistributedTask task3 = new ExampleSimpleTask(); Thread.sleep(2);
        IDistributedTask task4 = new HighPriorityExampleSimpleTask(); Thread.sleep(2);
        IDistributedTask task5 = new ExampleSimpleTask(); Thread.sleep(2);
        distributedTaskQueue.offer(task1);
        distributedTaskQueue.offer(task2);
        distributedTaskQueue.offer(task3);
        distributedTaskQueue.offer(task4);
        distributedTaskQueue.offer(task5);
        IDistributedTask iDistributedTask = distributedTaskQueue.workerPoolLastTaskBlocking();
        assertEquals(task2.getId(), iDistributedTask.getId());
        iDistributedTask = distributedTaskQueue.workerPoolLastTaskBlocking();
        assertEquals(task4.getId(), iDistributedTask.getId());
        iDistributedTask = distributedTaskQueue.workerPoolLastTaskBlocking();
        assertEquals(task1.getId(), iDistributedTask.getId());
    }
}
