package com.dijia478.visualization.bean;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.util.List;

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

    /** 剩余期数（月） */
    private Integer remainMonth;

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

    /** 还款的年月日 */
    private String dateFormat;

    /** 第几年 */
    private Integer year;

    /** 年里的第几月 */
    private Integer monthInYear;

    /** 当期月供支付后额外归还的本金（元） */
    private BigDecimal extraRepayment = BigDecimal.ZERO;

    /** 当期采用的执行年利率（百分数） */
    private BigDecimal annualRate;

    /** 跨利率调整日的当期分段明细 */
    private List<InterestSegment> interestSegments;

    /** 分以下利息连续结转，调息和提前还款不能清零。 */
    @JsonIgnore
    private BigDecimal interestRemainder = BigDecimal.ZERO;

    /** 提前还本重建后的计划在后续重定价时继续考虑首期应计息。 */
    @JsonIgnore
    private boolean adjustPaymentForFirstInterest;

}
