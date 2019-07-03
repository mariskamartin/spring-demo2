package com.mmariska.springdemo2.distributedTaskQueue;

import java.io.Serializable;

public interface IDistributedTask extends Serializable {
    String getId();
    long getCreatedTime();
    Object call(DistributedTaskQueue distributedTaskQueue);
}
