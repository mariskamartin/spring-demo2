package com.mmariska.springdemo2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * this is for spring concurrency reasons
 */
@RequestMapping("/test")
@RestController
public class TestController {
    private static final Logger log = LoggerFactory.getLogger(TestController.class);

    @RequestMapping(value="/call1", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String call1() {
        log.info("start call1 from (pod name): " + System.getenv("MY_POD_NAME"));
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity(getCall1Url(), String.class);
        return response.getBody();
    }

//    @RequestMapping(value="/sum/{count}", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
//    private String sumDefault(@PathVariable() int count) {
////        sum(count, -1);
//        return "done";
//    }

    private String getCall1Url() {
        String sd2_test_url = System.getenv("SD2_TEST_CALL1_URL");
        return sd2_test_url != null ? sd2_test_url : "http://mm-springdemo2-svc:8080/time/basic";
    }

}
