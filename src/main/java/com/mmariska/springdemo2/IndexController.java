package com.mmariska.springdemo2;

import com.mmariska.springdemo2.domain.Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/")
@RestController
public class IndexController {
    private static final Logger log = LoggerFactory.getLogger(IndexController.class);

    @RequestMapping(method = RequestMethod.GET, produces = {MediaType.TEXT_PLAIN_VALUE})
    public String index() {
        return "GET /api/widget \n" +
                "GET /api/widget/{name} \n" +
                "GET /api/widget/generate/{count} \n" +
                "\n" +
                "GET /error/e1 \n" +
                "GET /error/e2 (this logs error in code)\n" +
                "\n" +
                "GET /perf/eatAllCPU (default is for 5_000 ms)\n" +
                "GET /perf/eatAllCPU/time/{timeMillis}\n" +
                "GET /perf/eatMemory/{countMB}\n" +
                "GET /perf/eatMemory/weak/{countMB}\n" +
                "GET /perf/sum1\n" +
                "GET /perf/sum/{count} (no sleep time)\n" +
                "GET /perf/sum/{count}/sleep/{sleep} (could be CPU heavy)\n" +
                "\n" +
                "GET /test/call1 \n" +
                "\n" +
                "GET /redis/get ... get current state of shared tasks queue\n" +
                "GET /redis/worker ... subscribe executor thread in application \n" +
                "GET /redis/driver/agg ... produce aggregation tasks example \n" +
                "GET /redis/driver/1 ... produce tasks example \n" +
                "\n" +
                "GET /time/basic \n" +
                "GET /time/re \n" +
                "GET /time/callable \n" +
                "GET /time/deferred \n";
    }

}
