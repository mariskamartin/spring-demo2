package com.mmariska.springdemo2.distributedTaskQueue;

import java.io.Serializable;

public interface IDistributedTask extends Serializable {
    String getId();
    Object call(DistributedTaskQueue distributedTaskQueue);
}
