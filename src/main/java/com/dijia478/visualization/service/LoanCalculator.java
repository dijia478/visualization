package com.dijia478.visualization.service;

import com.dijia478.visualization.bean.LoanBO;
import com.dijia478.visualization.bean.MonthLoan;
import com.dijia478.visualization.bean.PrepaymentDTO;
import com.dijia478.visualization.bean.TotalLoan;

import java.math.BigDecimal;
import java.util.List;

/**
 * 贷款计算器接口
 *
 * @author dijia478
 * @date 2023/8/9
 */
public interface LoanCalculator {

    /**
     * 计算还款情况
     *
     * @param data
     * @return
     */
    TotalLoan compute(LoanBO data);

    /**
     * 计算提前还款
     *
     * @param monthLoanList
     * @param prepaymentDTO
     * @return
     */
    TotalLoan computePrepayment(List<MonthLoan> monthLoanList, PrepaymentDTO prepaymentDTO);

    /**
     * 根据年利率获取月利率
     *
     * @param loanRate
     * @return
     */
    BigDecimal monthRate(BigDecimal loanRate);

    /**
     * 将贷款总额（万元）单位转成（元）
     *
     * @param loanAmount
     * @return
     */
    BigDecimal totalLoan(BigDecimal loanAmount);

    /**
     * 根据贷款年限获取贷款总期数
     *
     * @param year
     * @return
     */
    BigDecimal totalMonth(BigDecimal year);

}
