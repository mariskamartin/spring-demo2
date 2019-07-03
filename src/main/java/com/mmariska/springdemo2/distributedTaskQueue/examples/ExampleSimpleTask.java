package com.mmariska.springdemo2.distributedTaskQueue.examples;

import com.mmariska.springdemo2.LoggingTraceRepository;
import com.mmariska.springdemo2.distributedTaskQueue.AbstractDistributedTask;
import com.mmariska.springdemo2.distributedTaskQueue.DistributedTaskQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleSimpleTask extends AbstractDistributedTask {
    private static final Logger log = LoggerFactory.getLogger(LoggingTraceRepository.class);

    public ExampleSimpleTask() {
        super();
    }

    @Override
    public Object call(DistributedTaskQueue distributedTaskQueue) {
        log.info("test task called and done");
        return 0L;
    }
}
