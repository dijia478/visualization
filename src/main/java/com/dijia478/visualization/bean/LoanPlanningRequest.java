package com.dijia478.visualization.bean;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Digits;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 规划工具中的金额沿用提前还款接口的万元单位；目标月供使用元。 */
@Data
public class LoanPlanningRequest {
    @Valid
    @NotNull(message = "贷款条件不能为空")
    private StockLoanDTO loan;
    @NotBlank(message = "比较日期不能为空")
    private String comparisonDate;
    @NotBlank(message = "提前还款日期不能为空")
    private String prepaymentDate;
    @Min(value = 0, message = "提前还款金额不能小于0")
    @Digits(integer = 10, fraction = 6, message = "提前还款金额需精确到分")
    private BigDecimal repayment;
    private String goal;
    @Min(value = 0, message = "目标月供不能小于0")
    @Digits(integer = 14, fraction = 2, message = "目标月供需精确到分")
    private BigDecimal targetPayment;
    private String targetPayoffDate;
}
