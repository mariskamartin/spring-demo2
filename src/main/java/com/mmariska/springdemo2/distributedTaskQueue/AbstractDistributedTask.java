package com.mmariska.springdemo2.distributedTaskQueue;

import java.util.Date;
import java.util.UUID;

/**
 * Default skeletal implementation of distributed task for {@link IDistributedTask}
 */
public abstract class AbstractDistributedTask implements IDistributedTask {
    private final long createTime;
    private final String taskId;

    public AbstractDistributedTask() {
        this("dTask-");
    }

    public AbstractDistributedTask(String prefix) {
        createTime = new Date().getTime();
        taskId = prefix + UUID.randomUUID().toString();
    }

    @Override
    public String getId() {
        return taskId;
    }

    @Override
    public byte getPriority() {
        return 0;
    }

    @Override
    public long getCreatedTime() {
        return createTime;
    }

}
