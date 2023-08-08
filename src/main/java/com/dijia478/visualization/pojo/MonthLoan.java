package com.dijia478.visualization.pojo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 月贷款情况
 *
 * @author dijia478
 * @date 2023/8/8
 */
@Data
public class MonthLoan {

    /** 月份（期数） */
    private int month;

    /** 月还款（元） */
    private BigDecimal repayment;

    /** 其中本金（元） */
    private BigDecimal principal;

    /** 其中利息（元） */
    private BigDecimal interest;

    /** 剩余贷款（元） */
    private BigDecimal remainTotal;

    /** 剩余本金（元） */
    private BigDecimal remainPrincipal;

}
