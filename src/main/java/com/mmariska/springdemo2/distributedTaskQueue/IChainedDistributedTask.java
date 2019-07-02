package com.mmariska.springdemo2.distributedTaskQueue;

import java.util.Map;

public interface IChainedDistributedTask extends IDistributedTask {
    String[] getDownstreamTaskIds();
    void setDownstreamResults(Map<String, Object> results);
}
