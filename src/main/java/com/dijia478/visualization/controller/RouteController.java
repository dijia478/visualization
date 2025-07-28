package com.dijia478.visualization.controller;

import com.dijia478.visualization.bean.RouteDTO;
import com.dijia478.visualization.service.RouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController("/routes")
public class RouteController {

    @Autowired
    private RouteService routeService;

    /**
     * 保存路线的API端点
     *
     * @param route 从前端接收的路线数据
     * @return ResponseEntity indicating success or failure
     */
    @PostMapping("/save")
    public ResponseEntity<String> saveRoute(@RequestBody RouteDTO route) {
        try {
            routeService.saveRoute(route);
            return ResponseEntity.ok("Route saved successfully!");
        } catch (IOException e) {
            e.printStackTrace(); // 实际项目中应使用日志
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to save route: " + e.getMessage());
        }
    }

    /**
     * 获取所有路线的API端点
     *
     * @return 包含所有路线的ResponseEntity
     */
    @GetMapping("/all")
    public ResponseEntity<List<RouteDTO>> getAllRoutes() {
        try {
            List<RouteDTO> routes = routeService.loadAllRoutes();
            return ResponseEntity.ok(routes);
        } catch (IOException e) {
            e.printStackTrace(); // 实际项目中应使用日志
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

}
