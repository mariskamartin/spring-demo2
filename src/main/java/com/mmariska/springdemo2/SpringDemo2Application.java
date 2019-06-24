package com.mmariska.springdemo2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
}
