package com.dijia478.visualization.util;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
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

    /**
     * 获取请求方的ip地址
     *
     * @param request
     * @return
     * @throws IOException
     */
    public static String getIpAddress(HttpServletRequest request) throws IOException {
        // 获取请求主机IP地址,如果通过代理进来，则透过防火墙获取真实IP地址
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("Proxy-Client-IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_CLIENT_IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
        } else if (ip.length() > 15) {
            String[] ips = ip.split(",");
            for (String strIp : ips) {
                if (!("unknown".equalsIgnoreCase(strIp))) {
                    ip = strIp;
                    break;
                }
            }
        }
        return ip;
    }

}
