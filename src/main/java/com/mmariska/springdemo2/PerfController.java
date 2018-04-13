package com.mmariska.springdemo2;

import com.mmariska.springdemo2.domain.Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.Collection;

/**
 * this is for spring concurrency reasons
 */
@RequestMapping("/perf")
@RestController
public class PerfController {
    private static final Logger log = LoggerFactory.getLogger(PerfController.class);
    private int sum = 0;


    @RequestMapping(value="/sum1", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String sum1() {
        sum(Integer.MAX_VALUE / 2, -1);
        return "done";
    }

    @RequestMapping(value="/sum/{count}", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String sumDefault(@PathVariable() int count) {
        sum(count, -1);
        return "done";
    }

    @RequestMapping(value="/sum/{count}/sleep/{sleep}", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String sum(@PathVariable("count") int count, @PathVariable("sleep") int sleep) {
        for (int i = 0; i < count; i++) {
            if(sleep >= 0) sleep(sleep);
            sum += Math.round(Math.random()*100);
        }
        return "done";
    }

    private void sleep(@PathVariable("sleep") int sleep) {
        try {
            Thread.sleep(sleep);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
