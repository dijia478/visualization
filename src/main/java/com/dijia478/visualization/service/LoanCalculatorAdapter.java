package com.dijia478.visualization.service;

import com.dijia478.visualization.bean.LoanBO;
import com.dijia478.visualization.bean.MonthLoan;
import com.dijia478.visualization.bean.PrepaymentDTO;
import com.dijia478.visualization.bean.TotalLoan;

import java.util.List;

/**
 * 贷款计算器适配器
 *
 * @author dijia478
 * @date 2023/8/21
 */
public abstract class LoanCalculatorAdapter implements LoanCalculator {

    @Override
    public TotalLoan compute(LoanBO data) {
        return null;
    }

    @Override
    public TotalLoan computePrepayment(List<MonthLoan> monthLoanList, PrepaymentDTO prepaymentDTO) {
        return null;
    }

}
