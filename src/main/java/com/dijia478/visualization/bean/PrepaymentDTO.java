package com.dijia478.visualization.bean;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 提前还款计划请求参数
 *
 * @author dijia478
 * @date 2023/8/16
 */
@Data
public class PrepaymentDTO {

    /** 还款期数 */
    private Integer prepaymentMonth;

    /** 还款金额 */
    private Integer repayment;

    /** 贷款利率 */
    private BigDecimal newRate;

    /** 新还款方式 1等额本息、2等额本金 */
    private Integer newType;

    /** 还款方案 1缩短期限，月还款不变、2减少月还款，期限不变 */
    private Integer repaymentType;

}
