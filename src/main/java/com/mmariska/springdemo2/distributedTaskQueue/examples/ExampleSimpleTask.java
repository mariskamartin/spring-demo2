package com.mmariska.springdemo2.distributedTaskQueue.examples;

import com.mmariska.springdemo2.LoggingTraceRepository;
import com.mmariska.springdemo2.distributedTaskQueue.DistributedTaskQueue;
import com.mmariska.springdemo2.distributedTaskQueue.IDistributedTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class ExampleSimpleTask implements IDistributedTask {
    private static final Logger log = LoggerFactory.getLogger(LoggingTraceRepository.class);
    private final String id;

    public ExampleSimpleTask() {
        this.id = "test-task-" + UUID.randomUUID().toString();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Object call(DistributedTaskQueue distributedTaskQueue) {
        log.info("test task called and done");
        return 0L;
    }
}
