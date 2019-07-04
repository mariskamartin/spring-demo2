package com.mmariska;

import com.mmariska.springdemo2.distributedTaskQueue.PriorityTaskIdComparator;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.redisson.Redisson;
import org.redisson.api.RPriorityBlockingQueue;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

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
        priorityBlockingQueue.trySetComparator(new PriorityTaskIdComparator());
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

}
