package com.dijia478.visualization.controller;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.dijia478.visualization.bean.*;
import com.dijia478.visualization.service.LoanCalculator;
import com.dijia478.visualization.util.LoanUtil;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

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
    @PostMapping("/calculator/loanCalculator")
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

    /**
     * 提前还款计算接口
     *
     * @param data
     * @return
     */
    @PostMapping("/calculator/prepaymentCalculator")
    public TotalLoan prepaymentCalculator(@RequestBody @Validated LoanDTO data) {
        validatedPrepayment(data);
        System.out.println(data);
        TotalLoan totalLoan = new TotalLoan();
        return totalLoan;
    }

    /**
     * 参数校验
     *
     * @param data
     */
    private void validatedPrepayment(LoanDTO data) {
        int totalMonth = data.getYear() * 12;
        int amount = data.getAmount();
        List<PrepaymentDTO> prepaymentList = data.getPrepaymentList();
        for (PrepaymentDTO prepaymentDTO : prepaymentList) {
            if (prepaymentDTO.getPrepaymentMonth() > totalMonth) {
                throw new LoanException(ResultEnum.PREPAYMENT_MONTH_TOO_BIG);
            }
            if (prepaymentDTO.getRepayment() > amount) {
                throw new LoanException(ResultEnum.REPAYMENT_TOO_BIG);
            }
        }
    }

}
