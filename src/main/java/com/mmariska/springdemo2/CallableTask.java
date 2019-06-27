package com.mmariska.springdemo2;

import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.concurrent.Callable;

public class CallableTask implements Callable<Long>, Serializable {
    private static final Logger log = LoggerFactory.getLogger(CallableTask.class);

    @RInject
    private RedissonClient redissonClient;

    @Override
    public Long call() throws Exception {
        log.info("({}) start execute CallableTask", System.getenv("MY_POD_NAME"));
        RMap<String, Integer> map = redissonClient.getMap("myMap");
        long result = 0;
        for (Integer value : map.values()) {
            result += value;
        }
        return result;
    }

}
