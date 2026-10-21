package com.dijia478.visualization.bean;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 提前还款计划请求参数
 *
 * @author dijia478
 * @date 2023/8/16
 */
@Data
public class PrepaymentDTO {

    /** 还款日期 */
    @NotNull(message = "还款日期不能为空")
    private String prepaymentDate;

    /** 还款期数 */
    private Integer prepaymentMonth;

    /** 还款金额 */
    @NotNull(message = "还款金额不能为空")
    @Min(value = 0, message = "还款金额不能小于0万元")
    private Integer repayment;

    /** 新执行年利率（百分数）；不传时按贷款重定价规则自动计算 */
    @Min(value = 0, message = "新贷款利率不能小于0")
    private BigDecimal newRate;

    /** 新还款方式 1等额本息、2等额本金 */
    @NotNull(message = "新还款方式不能为空")
    @Min(value = 1, message = "新还款方式必须为1或2")
    @Max(value = 2, message = "新还款方式必须为1或2")
    private Integer newType;

    /** 还款方案 1缩短期限，月还款不变、2减少月还款，期限不变 */
    @NotNull(message = "还款方案不能为空")
    @Min(value = 1, message = "还款方案必须为1或2")
    @Max(value = 2, message = "还款方案必须为1或2")
    private Integer repaymentType;

    /** 是否是lpr利率调整，1是0否 */
    private Integer lprRate;

    @JsonIgnore
    private List<InterestSegment> interestSegments;

    /** 利率已生效，但已出账的首期扣款金额保持原计划。 */
    @JsonIgnore
    private boolean paymentDeferred;

    /** 同一还款周期已有更早事件重建计划，后续缩期须沿用该计划月供。 */
    @JsonIgnore
    private boolean replannedInPeriod;

}
