package com.dijia478.visualization.pojo;

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

    /** 贷款期限（30年） */
    private Integer loanYear;

    /** 年利率（0.042） */
    private BigDecimal loanRate;

    /** 还款方式，1等额本息，2等额本金 */
    private int type;

    /** 总利息 */
    private BigDecimal totalInterest;

    /** 总还款额 */
    private BigDecimal totalRepayment;

    /** 每月还贷情况 */
    private List<MonthLoan> monthLoanList;

    /** 第几期之前提前还款（如13，就是在12期还完后提前还款，第13期重新计算还款额） */
    private Integer prepaymentMonth;

}
