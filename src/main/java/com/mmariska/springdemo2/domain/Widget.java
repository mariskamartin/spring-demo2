package com.mmariska.springdemo2.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

public class Widget {
    String name;
    String address;
    int age;
    Date birth;

    public Widget(String name) {
        this(name, "adr:"+name, Math.toIntExact(Math.round(Math.random() * 100)), new Date());
    }

    @JsonCreator
    public Widget(@JsonProperty("name") String name, @JsonProperty("address") String address, @JsonProperty("age") int age, @JsonProperty("birth") Date birth) {
        this.name = name;
        this.address = address;
        this.age = age;
        this.birth = birth;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public int getAge() {
        return age;
    }

    public Date getBirth() {
        return birth;
    }
}
