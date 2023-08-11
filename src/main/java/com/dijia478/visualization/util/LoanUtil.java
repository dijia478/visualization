package com.dijia478.visualization.util;

import cn.hutool.core.util.NumberUtil;
import com.dijia478.visualization.bean.MonthLoan;
import com.dijia478.visualization.bean.TotalLoan;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 一些工具类
 *
 * @author dijia478
 * @date 2023/8/9
 */
public class LoanUtil {

    /**
     * 对最终结果进行四舍五入保留两位小数，用于给前端展示
     *
     * @param totalLoan
     */
    public static void setScale(TotalLoan totalLoan) {
        totalLoan.setTotalInterest(totalLoan.getTotalInterest().setScale(2, RoundingMode.HALF_UP));
        totalLoan.setTotalRepayment(totalLoan.getTotalRepayment().setScale(2, RoundingMode.HALF_UP));
        for (MonthLoan monthLoan : totalLoan.getMonthLoanList()) {
            monthLoan.setRepayment(monthLoan.getRepayment().setScale(2, RoundingMode.HALF_UP));
            monthLoan.setPrincipal(monthLoan.getPrincipal().setScale(2, RoundingMode.HALF_UP));
            monthLoan.setInterest(monthLoan.getInterest().setScale(2, RoundingMode.HALF_UP));
            monthLoan.setRemainTotal(monthLoan.getRemainTotal().setScale(2, RoundingMode.HALF_UP));
            monthLoan.setRemainInterest(monthLoan.getRemainInterest().setScale(2, RoundingMode.HALF_UP));
            monthLoan.setRemainPrincipal(monthLoan.getRemainPrincipal().setScale(2, RoundingMode.HALF_UP));
            monthLoan.setTotalRepayment(monthLoan.getTotalRepayment().setScale(2, RoundingMode.HALF_UP));
            monthLoan.setTotalRepaymentAndRemainPrincipal(monthLoan.getTotalRepaymentAndRemainPrincipal().setScale(2, RoundingMode.HALF_UP));
        }
    }

    /**
     * 根据年利率获取月利率
     *
     * @param loanRate
     * @return
     */
    public static BigDecimal loanRateMonth(BigDecimal loanRate) {
        return NumberUtil.div(NumberUtil.div(loanRate, new BigDecimal("100")), new BigDecimal("12"));
    }

    /**
     * 将贷款总额（万元）单位转成（元）
     *
     * @param loanAmount
     * @return
     */
    public static BigDecimal totalLoan(BigDecimal loanAmount) {
        return NumberUtil.mul(loanAmount, new BigDecimal("10000"));
    }

    /**
     * 根据贷款年限获取贷款总期数
     *
     * @param year
     * @return
     */
    public static BigDecimal totalMonth(BigDecimal year) {
        return NumberUtil.mul(year, new BigDecimal("12"));
    }

}
