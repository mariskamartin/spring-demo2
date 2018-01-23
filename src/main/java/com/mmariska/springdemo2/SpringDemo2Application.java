package com.mmariska.springdemo2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@SpringBootApplication
public class SpringDemo2Application {
	private static final Logger log = LoggerFactory.getLogger(SpringDemo2Application.class);

	public static void main(String[] args) {
		SpringApplication.run(SpringDemo2Application.class, args);
		log.error("hello");
		log.warn("panda");
		log.info("is");
		log.debug("happy");
	}

    /**
     * This change default spring's executor
     * @return executor
     */
	@Bean
	public Executor asyncExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(3);
		executor.setThreadNamePrefix("sd2-pool-");
		executor.initialize();
		return executor;
	}
}
