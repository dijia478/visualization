package com.dijia478.visualization.service.impl;

import com.dijia478.visualization.bean.RouteDTO;
import com.dijia478.visualization.service.RouteService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class RouteServiceImpl implements RouteService {

    @Value("${app.routes.file.path}")
    private String filePath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 保存路线到文件
     *
     * @param route 要保存的路线对象
     */
    @Override
    public synchronized void saveRoute(RouteDTO route) throws IOException { // synchronized保证文件写入的线程安全
        List<RouteDTO> routes = loadAllRoutes();
        routes.add(route);
        objectMapper.writeValue(new File(filePath), routes);
    }

    /**
     * 从文件加载所有路线
     *
     * @return 包含所有路线的列表
     */
    @Override
    public List<RouteDTO> loadAllRoutes() throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            return new CopyOnWriteArrayList<>(); // 返回空列表，避免null
        }
        return objectMapper.readValue(file, new TypeReference<List<RouteDTO>>() {
        });
    }

}
