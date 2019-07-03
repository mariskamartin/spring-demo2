package com.mmariska;

import com.mmariska.springdemo2.LoggingTraceRepository;
import com.mmariska.springdemo2.distributedTaskQueue.AbstractDistributedTask;
import com.mmariska.springdemo2.distributedTaskQueue.DistributedTaskQueue;
import com.mmariska.springdemo2.distributedTaskQueue.IDistributedTask;
import org.omg.CORBA.Object;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class TestFailTask extends AbstractDistributedTask {
    private static final Logger log = LoggerFactory.getLogger(LoggingTraceRepository.class);

    @Override
    public Object call(DistributedTaskQueue distributedTaskQueue) {
        log.info("test task called and fail");
        throw new IllegalStateException("Just fail");
    }
}
