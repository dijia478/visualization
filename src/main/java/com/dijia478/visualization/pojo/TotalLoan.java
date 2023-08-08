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
    private int totalYear;

    /** 年利率（0.042） */
    private BigDecimal loanRate;

    /** 总利息 */
    private BigDecimal totalInterest;

    /** 总还款额 */
    private BigDecimal totalRepayment;

    /** 每月贷款情况 */
    private List<MonthLoan> monthLoan;

}
