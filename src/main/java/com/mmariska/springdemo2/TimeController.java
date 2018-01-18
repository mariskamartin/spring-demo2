package com.mmariska.springdemo2;

import com.mmariska.springdemo2.domain.TimeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * this is for spring concurrency reasons
 */
@RequestMapping("/time")
@RestController
public class TimeController {
    private static final Logger log = LoggerFactory.getLogger(TimeController.class);

    @RequestMapping(value = "/basic", method = RequestMethod.GET)
    public TimeResponse timeBasic() {
        log.info("Basic time request");
        return now();
    }

    @RequestMapping(value = "/re", method = RequestMethod.GET)
    public ResponseEntity<?> timeResponseEntity() {
        log.info("Response entity request");
        return ResponseEntity.ok(now());
    }

    private static TimeResponse now() {
        log.info("Creating TimeResponse");
        return new TimeResponse(LocalDateTime
                .now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}
