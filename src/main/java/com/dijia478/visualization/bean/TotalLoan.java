package com.dijia478.visualization.bean;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 总贷款情况
 *
 * @author dijia478
 * @date 2023/8/8
 */
@Data
public class TotalLoan {

    /** 贷款金额（1000000元） */
    private BigDecimal loanAmount;

    /** 贷款期限（360期） */
    private BigDecimal loanMonth;

    /** 年利率（0.042） */
    private BigDecimal loanRate;

    /** 还款方式，1等额本息，2等额本金 */
    private Integer type;

    /** 提前还款前原总利息 */
    private BigDecimal originalTotalInterest;

    /** 总利息 */
    private BigDecimal totalInterest;

    /** 总还款额 */
    private BigDecimal totalRepayment;

    /** 每月还贷情况 */
    private List<MonthLoan> monthLoanList;

    /** lpr变更期数 */
    private List<Integer> lprMonth;

    /** lpr变更数值 */
    private List<BigDecimal> lprRate;

}
