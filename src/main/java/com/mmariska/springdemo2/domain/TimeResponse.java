package com.mmariska.springdemo2.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TimeResponse {
    private String time;

    @JsonCreator
    public TimeResponse(@JsonProperty("time") String time) {
        this.time = time;
    }

    public String getTime() {
        return time;
    }
}
