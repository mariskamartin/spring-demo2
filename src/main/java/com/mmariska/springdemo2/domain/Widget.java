package com.mmariska.springdemo2.domain;

import java.util.Date;

public class Widget {
    String name;
    String address;
    int age;
    Date birth;

    public Widget(String name) {
        this.name = name;
        this.address = "adr:"+name;
        this.age = Math.toIntExact(Math.round(Math.random() * 100));
        this.birth = new Date();
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
