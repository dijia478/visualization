package com.dijia478.visualization.service;

import cn.hutool.core.util.NumberUtil;
import com.dijia478.visualization.bean.LoanBO;
import com.dijia478.visualization.bean.MonthLoan;
import com.dijia478.visualization.bean.PrepaymentDTO;
import com.dijia478.visualization.bean.TotalLoan;
import com.dijia478.visualization.util.NumUtil;

import java.math.BigDecimal;
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


    @Override
    public BigDecimal monthRate(BigDecimal loanRate) {
        // 我也不懂，为什么将下面的NumberUtil换成NumUtil，整个接口性能就差很多
        return NumberUtil.div(NumberUtil.div(loanRate, new BigDecimal("100")), new BigDecimal("12"));
    }

    @Override
    public BigDecimal totalLoan(BigDecimal loanAmount) {
        return NumUtil.mul(loanAmount, new BigDecimal("10000"));
    }

    @Override
    public BigDecimal totalMonth(BigDecimal year) {
        return NumUtil.mul(year, new BigDecimal("12"));
    }

}
