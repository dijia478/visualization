package com.dijia478.visualization.bean;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

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

    /** 贷款利率 */
    @NotNull(message = "新贷款利率不能为空")
    @Min(value = 0, message = "新贷款利率不能小于0")
    private BigDecimal newRate;

    /** 新还款方式 1等额本息、2等额本金 */
    @NotNull(message = "新还款方式不能为空")
    private Integer newType;

    /** 还款方案 1缩短期限，月还款不变、2减少月还款，期限不变 */
    @NotNull(message = "还款方案不能为空")
    private Integer repaymentType;

    /** 是否是lpr利率调整，1是0否 */
    private Integer lprRate;

}
