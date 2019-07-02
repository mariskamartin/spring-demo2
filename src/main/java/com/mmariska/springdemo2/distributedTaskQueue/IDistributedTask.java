package com.mmariska.springdemo2.distributedTaskQueue;

import java.io.Serializable;
import java.util.concurrent.Callable;

public interface IDistributedTask extends Callable<Object>, Serializable {
    String getId();
}
