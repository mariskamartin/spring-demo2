package com.mmariska.springdemo2;

import com.mmariska.springdemo2.domain.TimeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * this is for spring concurrency reasons
 */
@RequestMapping("/error")
@RestController
public class ErrorController {
    private static final Logger log = LoggerFactory.getLogger(ErrorController.class);

    @RequestMapping(value = "/e1", method = RequestMethod.GET, produces = {MediaType.TEXT_PLAIN_VALUE})
    public String getError1() {
        try {
            log.info("throwing error");
            return throwNumberFormatError();
        } catch (Exception e) {
            throw new IllegalStateException("Number parsing", e);
        }
    }

    @RequestMapping(value = "/e2", method = RequestMethod.GET, produces = {MediaType.TEXT_PLAIN_VALUE})
    public String getError2() {
        try {
            log.info("throwing error 2");
            return throwMultiCauseError2();
        } catch (Exception e) {
            log.error("stack trace log", e);
            throw new IllegalStateException("Number parsing", e);
        }
    }

    private String throwNumberFormatError() {
        return "" + Integer.parseInt("not-a-number");
    }

    private String throwMultiCauseError() {
        try {
            return throwNumberFormatError();
        } catch (Exception e) {
            throw new UnsupportedTemporalTypeException("error cause", e);
        }
    }

    private String throwMultiCauseError2() {
        try {
            return throwMultiCauseError();
        } catch (Exception e) {
            throw new IllegalArgumentException("cause", e);
        }
    }

}
