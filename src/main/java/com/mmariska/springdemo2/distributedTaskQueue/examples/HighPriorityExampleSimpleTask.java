package com.mmariska.springdemo2.distributedTaskQueue.examples;

import com.mmariska.springdemo2.LoggingTraceRepository;
import com.mmariska.springdemo2.distributedTaskQueue.AbstractDistributedTask;
import com.mmariska.springdemo2.distributedTaskQueue.DistributedTaskQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HighPriorityExampleSimpleTask extends AbstractDistributedTask {
    private static final Logger log = LoggerFactory.getLogger(LoggingTraceRepository.class);

    public HighPriorityExampleSimpleTask() {
        super("A-exampleTask-");
    }

    @Override
    public Object call(DistributedTaskQueue distributedTaskQueue) {
        log.info("test A priority task called and done");
        return 0L;
    }
}
