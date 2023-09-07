package com.dijia478.visualization.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 监控访问量的工具类
 *
 * @author dijia478
 * @date 2023/9/7
 */
public class MonitorUtil {

    private static final Map<String, AtomicInteger> MAP = new ConcurrentHashMap<>();

    private MonitorUtil() {}

    public static synchronized Integer add(String ip) {
        AtomicInteger atomicInteger = MAP.computeIfAbsent(ip, k -> new AtomicInteger(1));
        return atomicInteger.incrementAndGet();
    }

    public static Integer getTotalKey() {
        return MAP.keySet().size();

    }

    public static Integer getTotalValue() {
        int total = 0;
        for (AtomicInteger value : MAP.values()) {
            total += value.intValue();
        }
        return total;
    }

    public static void clear() {
        MAP.clear();
    }

}
