package com.dijia478.visualization.controller;

import com.dijia478.visualization.bean.WorkCostDataDTO;
import com.dijia478.visualization.service.WorkCostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
        if (Double.parseDouble(result) > 200.0d) {
            // 肯定是胡填的，直接返回
            return "100%";
        }
        if (Double.parseDouble(result) <= 0.0d) {
            return "0%";
        }
        return workCostService.getPercent(result);
    }

    @PostMapping("/workCost/save")
    public void prepaymentCalculator(@RequestBody @Validated WorkCostDataDTO data) throws Exception {
        if (Double.parseDouble(data.getResult()) > 200.0d) {
            // 肯定是胡填的，直接返回
            return;
        }
        if (Double.parseDouble(data.getResult()) <= 0.0d) {
            return;
        }
        workCostService.save(data);
    }

}
