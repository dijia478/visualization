package com.dijia478.visualization.service;

import cn.hutool.core.util.NumberUtil;
import com.dijia478.visualization.bean.LoanBO;
import com.dijia478.visualization.bean.MonthLoan;
import com.dijia478.visualization.bean.PrepaymentDTO;
import com.dijia478.visualization.bean.TotalLoan;

import java.math.BigDecimal;
import java.util.List;

/**
 * 贷款计算器适配器
 *
 * @author dijia478
 * @date 2023/8/21
 */
public abstract class LoanCalculatorAdapter implements LoanCalculator {

    protected static final int DEFAULT_SCALE = 100;

    @Override
    public TotalLoan compute(LoanBO data) {
        return null;
    }

    @Override
    public TotalLoan computePrepayment(List<MonthLoan> monthLoanList, PrepaymentDTO prepaymentDTO) {
        return null;
    }


    @Override
    public BigDecimal monthRate(BigDecimal loanRate) {
        return NumberUtil.div(NumberUtil.div(loanRate, new BigDecimal("100")), new BigDecimal("12"));
    }

    @Override
    public BigDecimal totalLoan(BigDecimal loanAmount) {
        return NumberUtil.mul(loanAmount, new BigDecimal("10000"));
    }

    @Override
    public BigDecimal totalMonth(BigDecimal year) {
        return NumberUtil.mul(year, new BigDecimal("12"));
    }

}
