package com.dijia478.visualization.task;

import cn.hutool.core.date.DateUtil;
import com.dijia478.visualization.util.MonitorUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 监控定时任务
 *
 * @author dijia478
 * @date 2023/9/7
 */
@Component
@Slf4j
public class MonitorTask {

    /**
     * 每10分钟执行一次，统计访问量
     */
    @Scheduled(cron = "0 0/10 * * * ?")
    public void monitor() {
        Integer totalKey = MonitorUtil.getTotalKey();
        // 一分钟内没有访问，不用处理
        if (totalKey == 0) {
            return;
        }
        String now = DateUtil.now();
        String oneMinAgo = DateUtil.formatDateTime(DateUtil.offsetMinute(new Date(), -10));
        Integer totalValue = MonitorUtil.getTotalValue();
        log.info("{} 至 {}，访问人数为：{}，请求接口数为：{}", oneMinAgo, now, totalKey, totalValue);
        MonitorUtil.clear();
    }

}
