package com.mmariska.springdemo2;

import com.mmariska.springdemo2.domain.Widget;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

@RequestMapping("/api")
@RestController
public class WidgetController {

    @RequestMapping(method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE})
    public Widget index() {
        return new Widget("green");
    }

    @RequestMapping(value="/{name}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE})
    public Widget getWidgetByName(@PathVariable String name) {
        return new Widget(name);
    }

    @RequestMapping(value="/generate/widgets/{count}", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE})
    public Collection<Widget> generateWidgets(@PathVariable int count) {
        List<Widget> generatedWidgets = new LinkedList<>();
        for (int i = 0; i < count; i++) {
            generatedWidgets.add(new Widget("name"+count));
        }
        return generatedWidgets;
    }
}
