package com.dijia478.visualization.service;

import com.dijia478.visualization.bean.LoanBO;
import com.dijia478.visualization.bean.LoanDTO;
import com.dijia478.visualization.bean.StockLoanDTO;
import com.dijia478.visualization.bean.TotalLoan;
import com.dijia478.visualization.controller.LoanController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private LoanController loanController;

    @Test
    void compute1() {
        StockLoanDTO data = new StockLoanDTO();
        data.setAmount(150);
        data.setYear(30);
        data.setRate(4.0);
        data.setType(1);
        data.setFirstPaymentDate("2021-05-15");
        LoanBO loanBO = loanController.convertParam(data);
        TotalLoan compute = equalRepaymentCalculator.compute(loanBO);
        loanController.setScale(compute);
    }

    @Test
    void compute2() {
        StockLoanDTO data = new StockLoanDTO();
        data.setAmount(150);
        data.setYear(30);
        data.setRate(4.0);
        data.setType(2);
        data.setFirstPaymentDate("2021-05-15");
        LoanBO loanBO = loanController.convertParam(data);
        TotalLoan compute = equalPrincipalCalculator.compute(loanBO);
        loanController.setScale(compute);
    }

}