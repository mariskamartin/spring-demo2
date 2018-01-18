package com.mmariska.springdemo2;

import com.mmariska.springdemo2.domain.Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

@RequestMapping("/api/widget")
@RestController
public class WidgetController {
    private static final Logger log = LoggerFactory.getLogger(WidgetController.class);

    @RequestMapping(method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE})
    public Widget index() {
        return new Widget("green");
    }

    @RequestMapping(value="/{name}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE})
    public Widget getWidgetByName(@PathVariable String name) {
        return new Widget(name);
    }

    @RequestMapping(value="/generate/{count}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE})
    public Collection<Widget> generateWidgets(@PathVariable int count) {
        List<Widget> generatedWidgets = new LinkedList<>();
        log.info("generating {}",count);
        for (int i = 0; i < count; i++) {
            generatedWidgets.add(new Widget("name"+count));
        }
        return generatedWidgets;
    }
}
