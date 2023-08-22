package com.dijia478.visualization.service;

import com.dijia478.visualization.bean.*;

import java.util.List;

/**
 * 贷款计算器适配器
 *
 * @author dijia478
 * @date 2023/8/21
 */
public class LoanCalculatorAdapter implements LoanCalculator {

    @Override
    public TotalLoan compute(LoanBO data) {
        return null;
    }

    @Override
    public TotalLoan computePrepayment(List<MonthLoan> monthLoanList, PrepaymentDTO prepaymentDTO) {
        return null;
    }

}
