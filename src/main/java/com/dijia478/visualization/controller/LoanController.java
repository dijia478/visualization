package com.dijia478.visualization.controller;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.dijia478.visualization.bean.BaseResponse;
import com.dijia478.visualization.bean.LoanDTO;
import com.dijia478.visualization.bean.TotalLoan;
import com.dijia478.visualization.service.LoanCalculator;
import com.dijia478.visualization.util.LoanUtil;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author dijia478
 * @date 2023/8/4
 */
@RestController
public class LoanController {

    /** 等额本息计算器 */
    @Resource(name = "equalLoanPayment")
    private LoanCalculator equalLoanPayment;

    /** 等额本金计算器 */
    @Resource(name = "equalPrincipalPayment")
    private LoanCalculator equalPrincipalPayment;

    /**
     * 贷款计算接口
     *
     * @param data
     * @return
     */
    @PostMapping("loanCalculator")
    public TotalLoan loanCalculator(@RequestBody @Validated LoanDTO data) {
        TotalLoan totalLoan;
        if (data.getType() == 1) {
            totalLoan = equalLoanPayment.compute(data);
        } else {
            totalLoan = equalPrincipalPayment.compute(data);
        }
        LoanUtil.setScale(totalLoan);
        return totalLoan;
    }

}
