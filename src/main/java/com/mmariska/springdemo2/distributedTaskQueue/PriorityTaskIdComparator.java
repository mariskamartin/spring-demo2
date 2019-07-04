package com.mmariska.springdemo2.distributedTaskQueue;

public class PriorityTaskIdComparator implements java.util.Comparator<String> {
    @Override
    public int compare(String o1, String o2) {
        return Character.compare(o2.charAt(0), o1.charAt(0));
    }
}
