package com.mmariska.springdemo2.distributedTaskQueue;

import java.io.Serializable;
import java.util.Date;

public class DistributedTask implements Serializable {
    private final long timestamp;
    private final String taskId;

    public DistributedTask(String taskId) {
        this.timestamp = new Date().getTime();
        this.taskId = taskId;
    }
}
