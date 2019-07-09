package com.mmariska;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.redisson.Redisson;
import org.redisson.api.RPriorityBlockingQueue;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

import java.io.Serializable;
import java.util.Comparator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test Integration test for Redis-backed Distributed Task Queue.
 */
public class ReddisonIntegrationTest {

    public static final String REDISSON_TEST_QUEUE = "testQueue1";
    //    @Rule
    @ClassRule
    public static GenericContainer redis = new GenericContainer<>("redis:5.0.3-alpine").withExposedPorts(6379);
    private RedissonClient redisson;

    @Before
    public void setUp() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://"+ redis.getContainerIpAddress() +":"+ redis.getFirstMappedPort());
        redisson = Redisson.create(config);
    }

    @Test
    public void testSimplePutAndGet() {
        redisson.getQueue(REDISSON_TEST_QUEUE).offer("example");
        String retrieved = (String) redisson.getQueue(REDISSON_TEST_QUEUE).poll();
        assertEquals("example", retrieved);
    }

    @Test
    public void testSortingInPriorityQueue() {
        RPriorityBlockingQueue<String> priorityBlockingQueue = redisson.getPriorityBlockingQueue("test-priority-queue");
        priorityBlockingQueue.trySetComparator(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o2.compareTo(o1);
            }
        });
        priorityBlockingQueue.offer("Nclass");
        priorityBlockingQueue.offer("Gclass");
        priorityBlockingQueue.offer("Aclass");
        priorityBlockingQueue.offer("Xclass");

        //pool Last method is used in our implementations
        assertEquals("Aclass", priorityBlockingQueue.pollLastAndOfferFirstTo("some"));
        assertEquals("Gclass", priorityBlockingQueue.pollLastAndOfferFirstTo("some"));

        priorityBlockingQueue.offer("Bclass");

        assertEquals("Bclass", priorityBlockingQueue.pollLastAndOfferFirstTo("some"));
        assertEquals("Nclass", priorityBlockingQueue.pollLastAndOfferFirstTo("some"));
        assertEquals("Xclass", priorityBlockingQueue.pollLastAndOfferFirstTo("some"));
        assertTrue(priorityBlockingQueue.isEmpty());

    }

    public static interface ITask {
        String getId();
    }

    public static class TaskTestObject implements Serializable, ITask {
        String id;
        String name;

        public TaskTestObject(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String getId() {
            return id;
        }
    }

    public static class ExTaskTest extends TaskTestObject {
        long l = 2L;

        public ExTaskTest(String id, String name) {
            super(id, name);
        }
    }

    @Test
    public void testContainsObjectInObjectQueue() {
        RQueue<ITask> queue = redisson.<ITask>getQueue("queue1");
        queue.offer(new TaskTestObject("1", "some1"));
        TaskTestObject some2 = new TaskTestObject("2", "some2");
        TaskTestObject some2newReference = new TaskTestObject("2", "some2");
        queue.offer(some2);
        assertTrue(queue.contains(some2));
        assertTrue(queue.contains(some2newReference));
    }

    @Test
    public void testContainsTaskIdInObjectQueue() {
        RQueue<ITask> queue = redisson.<ITask>getQueue("queue2");
        queue.offer(new TaskTestObject("1", "some1"));
        queue.offer(new TaskTestObject("2", "some2"));
        queue.offer(new TaskTestObject("3", "some3"));
        final String id = "2";
        assertTrue(queue.stream().filter(t -> t.getId().equals(id)).count() > 0);
        assertTrue(queue.parallelStream().filter(t -> t.getId().equals(id)).count() > 0);
    }

}
