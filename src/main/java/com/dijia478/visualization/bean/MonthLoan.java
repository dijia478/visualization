package com.dijia478.visualization.bean;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 月还贷情况
 *
 * @author dijia478
 * @date 2023/8/8
 */
@Data
public class MonthLoan {

    /** 月份（期数） */
    private Integer month;

    /** 月还款（元） */
    private BigDecimal repayment;

    /** 其中本金（元） */
    private BigDecimal principal;

    /** 其中利息（元） */
    private BigDecimal interest;

    /** 剩余贷款（元） */
    private BigDecimal remainTotal;

    /** 剩余利息（元） */
    private BigDecimal remainInterest;

    /** 剩余本金（元） */
    private BigDecimal remainPrincipal;

    /** 累计已还款总额（元） */
    private BigDecimal totalRepayment;

    /** 累计已还本金（元） */
    private BigDecimal totalPrincipal;

    /** 累计已还利息（元） */
    private BigDecimal totalInterest;

    /** 累计已还款总额（元） + 剩余本金（元） */
    private BigDecimal totalRepaymentAndRemainPrincipal;

    /** 第几年 */
    private Integer year;

    /** 年里的第几月 */
    private Integer monthInYear;

}
