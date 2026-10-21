package com.dijia478.visualization.bean;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 贷款计算业务对象
 *
 * @author dijia478
 * @date 2023/8/21
 */
@Builder
@Data
public class LoanBO {

    private BigDecimal amount;

    private BigDecimal month;

    private BigDecimal rate;

    private Integer type;

    private String firstPaymentDate;

    private List<PrepaymentDTO> prepaymentList;

    private List<InterestSegment> firstInterestSegments;

    /** 已出账首期的扣款额。 */
    private BigDecimal firstRepayment;

    /** 已出账计划冻结时，按原计划本金计算的后续月供。 */
    private BigDecimal subsequentRepayment;

    /** 上一期尚未入账、需继续结转的不足一分利息。 */
    private BigDecimal interestRemainder;

    /** 实际提前还本重建计划后，月供及后续重定价纳入首期应计息现值。 */
    private boolean adjustPaymentForFirstInterest;

}
