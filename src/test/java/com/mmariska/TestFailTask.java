package com.mmariska;

import com.mmariska.springdemo2.LoggingTraceRepository;
import com.mmariska.springdemo2.distributedTaskQueue.AbstractDistributedTask;
import com.mmariska.springdemo2.distributedTaskQueue.IDistributedTaskQueue;
import org.omg.CORBA.Object;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestFailTask extends AbstractDistributedTask {
    private static final Logger log = LoggerFactory.getLogger(LoggingTraceRepository.class);

    @Override
    public Object call(IDistributedTaskQueue distributedTaskQueue) {
        log.info("test task called and fail");
        throw new IllegalStateException("Just fail");
    }
}
