package com.mmariska.springdemo2;

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
                "GET /time/basic \n" +
                "GET /time/re \n" +
                "GET /time/callable \n" +
                "GET /time/deferred \n";
    }

}
