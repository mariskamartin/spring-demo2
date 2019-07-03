package com.mmariska.springdemo2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.ref.WeakReference;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * this is for spring concurrency reasons
 */
@RequestMapping("/perf")
@RestController
public class PerfController {
    private static final Logger log = LoggerFactory.getLogger(PerfController.class);
    private int sum = 0;

    @RequestMapping(value="/eatAllCPU", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String eatAllCPU() throws InterruptedException {
        return eatAllCPUWithParams(5000);
    }

    @RequestMapping(value="/eatAllCPU/time/{timeMillis}", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String eatAllCPUWithParams(@PathVariable("timeMillis") int timeMillis) throws InterruptedException {
        final long s = System.currentTimeMillis();
        final int availableProcessors = Runtime.getRuntime().availableProcessors();
        final int runTimeoutMillis = timeMillis;
        ExecutorService executor = Executors.newFixedThreadPool(availableProcessors);
        for (int i = 0; i < availableProcessors; i++) {
            executor.submit(() -> {
                long sum = 0;
                while(true) {
                    if (Thread.currentThread().isInterrupted()) return 0;
                    if((System.currentTimeMillis() - s) > runTimeoutMillis) return sum;
                    sum += Math.round(Math.random()*100);
                }
            });
        }
        executor.shutdown();
        if (!executor.awaitTermination(runTimeoutMillis * 2, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
        return "done availableProcessors = " + availableProcessors + ", elapsed = " + (System.currentTimeMillis() - s) + " ms";
    }


    private static final Vector v = new Vector();

    @RequestMapping(value="/eatMemory/{countMB}", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String eatMemory(@PathVariable() int countMB) {
        int oneMiB = 1024 * 1024;
        while (--countMB > 0)
        {
            byte b[] = new byte[oneMiB];
            v.add(b);
        }
        return getCurrentMemoryStats(oneMiB, "MiB");
    }

    @RequestMapping(value="/eatMemory/weak/{countMB}", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String eatMemoryWeak(@PathVariable() int countMB) {
        int oneMiB = 1024 * 1024;
        while (--countMB > 0)
        {
            byte b[] = new byte[oneMiB];
            v.add( new WeakReference<Object>(v));
        }
        return getCurrentMemoryStats(oneMiB, "MiB");
    }


    private String getCurrentMemoryStats(int formatSize, String size) {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        return String.format("objs=%d, [size %s] total=%d, free=%d, used=%d, max=%d",v.size(),
                size,
                runtime.totalMemory()/formatSize,
                runtime.freeMemory()/formatSize,
                used/formatSize,
                runtime.maxMemory()/formatSize);
    }

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

    @RequestMapping(value="/parallelSum/{count}/sleep/{sleep}", method = RequestMethod.GET, produces = {MediaType.TEXT_HTML_VALUE})
    private String parallelSum(@PathVariable("count") int count, @PathVariable("sleep") int sleep) {
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
