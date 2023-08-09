package com.dijia478.visualization.service;

import com.alibaba.fastjson2.JSONObject;
import com.dijia478.visualization.bean.LoanDTO;
import com.dijia478.visualization.bean.MonthLoan;
import com.dijia478.visualization.bean.TotalLoan;
import com.dijia478.visualization.util.LoanUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

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
        LoanDTO data = new LoanDTO();
        data.setAmount(150);
        data.setYear(30);
        data.setRate(4.0);
        data.setType(1);
        TotalLoan compute = equalLoanPayment.compute(data);
        LoanUtil.setScale(compute);
        MonthLoan monthLoan = compute.getMonthLoanList().get(compute.getMonthLoanList().size() - 1);
        System.out.println(compute);
        System.out.println(monthLoan);
    }

    @Test
    void compute2() {
        LoanDTO data = new LoanDTO();
        data.setAmount(150);
        data.setYear(30);
        data.setRate(4.0);
        data.setType(1);
        TotalLoan compute = equalPrincipalPayment.compute(data);
        LoanUtil.setScale(compute);
        MonthLoan monthLoan = compute.getMonthLoanList().get(compute.getMonthLoanList().size() - 1);
        System.out.println(compute);
        System.out.println(monthLoan);
    }

}