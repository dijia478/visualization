package com.dijia478.visualization.service;

import cn.hutool.core.util.NumberUtil;
import com.alibaba.fastjson2.JSONObject;
import com.dijia478.visualization.pojo.MonthLoan;
import com.dijia478.visualization.pojo.TotalLoan;
import com.dijia478.visualization.util.LoanUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;

@SpringBootTest
class LoanCalculatorTest {

    /** 等额本息计算器 */
    @Resource(name = "equalLoanPayment")
    private LoanCalculator equalLoanPayment;

    /** 等额本金计算器 */
    @Resource(name = "equalPrincipalPayment")
    private LoanCalculator equalPrincipalPayment;

    @Test
    void compute1() {
        JSONObject data = new JSONObject();
        data.put("amount", 150);
        data.put("year", 30);
        data.put("rate", 4.0);
        data.put("type", 1);
        TotalLoan compute = equalLoanPayment.compute(data);
        LoanUtil.setScale(compute);
        MonthLoan monthLoan = compute.getMonthLoanList().get(compute.getMonthLoanList().size() - 1);
        System.out.println(compute);
        System.out.println(monthLoan);
    }

    @Test
    void compute2() {
        JSONObject data = new JSONObject();
        data.put("amount", 150);
        data.put("year", 30);
        data.put("rate", 4);
        data.put("type", 2);
        TotalLoan compute = equalPrincipalPayment.compute(data);
        LoanUtil.setScale(compute);
        MonthLoan monthLoan = compute.getMonthLoanList().get(compute.getMonthLoanList().size() - 1);
        System.out.println(compute);
        System.out.println(monthLoan);
    }

}