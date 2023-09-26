package com.dijia478.visualization.controller;

import com.dijia478.visualization.service.WorkCostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 工作性价比
 *
 * @author dijia478
 * @date 2023/9/26
 */
@RestController
public class WorkCostController {

    @Autowired
    private WorkCostService workCostService;


    @GetMapping("/workCost/getPercent")
    public String getPercent(@RequestParam(value = "result") String result) throws Exception {
        return workCostService.getPercent(result);
    }
}
