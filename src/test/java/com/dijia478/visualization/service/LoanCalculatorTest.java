package com.dijia478.visualization.service;

import com.dijia478.visualization.bean.LoanBO;
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
    @Resource(name = "equalRepaymentCalculator")
    private LoanCalculator equalRepaymentCalculator;

    /** 等额本金计算器 */
    @Resource(name = "equalPrincipalCalculator")
    private LoanCalculator equalPrincipalCalculator;

    @Test
    void compute1() {
        LoanDTO data = new LoanDTO();
        data.setAmount(150);
        data.setYear(30);
        data.setRate(4.0);
        data.setType(1);
        LoanBO loanBO = LoanUtil.convertParam(data);
        TotalLoan compute = equalRepaymentCalculator.compute(loanBO);
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
        data.setType(2);
        LoanBO loanBO = LoanUtil.convertParam(data);
        TotalLoan compute = equalPrincipalCalculator.compute(loanBO);
        LoanUtil.setScale(compute);
        MonthLoan monthLoan = compute.getMonthLoanList().get(compute.getMonthLoanList().size() - 1);
        System.out.println(compute);
        System.out.println(monthLoan);
    }

}