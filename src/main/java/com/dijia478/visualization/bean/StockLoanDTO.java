package com.dijia478.visualization.bean;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 前端请求的参数
 *
 * @author dijia478
 * @date 2023/9/13
 */
@Data
public class StockLoanDTO extends LoanDTO {

    @NotBlank(message = "第一次还款日不能为空")
    private String firstPaymentDate;

    @NotNull(message = "利率调整日不能为空")
    private Integer rateAdjustmentDay;

}
