package com.dijia478.visualization.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.dijia478.visualization.bean.WorkCostDataDTO;
import com.dijia478.visualization.service.WorkCostService;
import com.dijia478.visualization.util.MonitorUtil;
import com.dijia478.visualization.util.NumUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author dijia478
 * @date 2023/9/26
 */
@Service
public class WorkCostServiceImpl implements WorkCostService {

    private static final String PERCENT_FILE_NAME = "classpath:percent.json";

    private static final String WORK_COST_DATA_FILE_NAME = "classpath:workCostData.json";

    @Autowired
    private ResourceLoader resourceLoader;

    @Override
    public String getPercent(String result) throws IOException {
        synchronized (PERCENT_FILE_NAME) {
            // 读取数据
            Resource resource = resourceLoader.getResource(PERCENT_FILE_NAME);
            String content = new String(Files.readAllBytes(resource.getFile().toPath()));
            JSONArray jsonArray = JSON.parseArray(content);

            // 排名并保存数据
            jsonArray.add(result);
            List<Object> collect = jsonArray.stream().sorted(Comparator.comparingDouble(o -> Double.parseDouble(o.toString())).reversed()).collect(Collectors.toList());
            try (FileWriter writer = new FileWriter(resource.getFile())) {
                writer.write(JSON.toJSONString(collect));
            }

            // 计算结果
            String percent = "";
            int j = 0;
            for (int i = collect.size() - 1; i >= 0; i--, j++) {
                if (result.equals(collect.get(i).toString())) {
                    BigDecimal a = new BigDecimal(j * 100);
                    BigDecimal b = new BigDecimal(collect.size());
                    percent = NumUtil.div(a, b).setScale(2, RoundingMode.HALF_UP) + "%";
                    break;
                }
            }
            return percent;
        }
    }

    @Override
    public synchronized void save(WorkCostDataDTO data) throws IOException {
        synchronized (WORK_COST_DATA_FILE_NAME) {
            HttpServletRequest request = ((ServletRequestAttributes)(RequestContextHolder.currentRequestAttributes())).getRequest();
            String ip = MonitorUtil.getIpAddress(request);
            Resource resource = resourceLoader.getResource(WORK_COST_DATA_FILE_NAME);
            String content = new String(Files.readAllBytes(resource.getFile().toPath()));
            JSONObject jsonObject = JSON.parseObject(content);
            jsonObject.put(ip, JSON.toJSONString(data));
            try (FileWriter writer = new FileWriter(resource.getFile())) {
                writer.write(jsonObject.toJSONString());
            }
        }

    }

}
