package com.dijia478.visualization.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.dijia478.visualization.service.WorkCostService;
import com.dijia478.visualization.util.NumUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import javax.servlet.ServletContext;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author dijia478
 * @date 2023/9/26
 */
@Service
public class WorkCostServiceImpl implements WorkCostService {

    private static final String FILE_NAME = "classpath:percent.json";

    @Autowired
    private ResourceLoader resourceLoader;

    @Override
    public synchronized String getPercent(String result) throws IOException {
        if (Double.parseDouble(result) > 200.0) {
            // 肯定是胡填的，直接返回
            return "100%";
        }
        Resource resource = resourceLoader.getResource(FILE_NAME);
        String content = new String(Files.readAllBytes(resource.getFile().toPath()));
        JSONArray jsonArray = JSON.parseArray(content);
        jsonArray.add(result);

        List<Object> collect = jsonArray.stream().sorted(Comparator.comparingDouble(o -> Double.parseDouble(o.toString())).reversed()).collect(Collectors.toList());
        try (FileWriter writer = new FileWriter(resource.getFile())) {
            writer.write(JSON.toJSONString(collect));
        }

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
