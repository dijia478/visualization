package com.dijia478.visualization.bean;

import lombok.Data;
import java.math.BigDecimal;

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

    /** 重定价当期按整月或按合同30天折算分段计息 */
    private String repricingInterestMode = "SPLIT_30_360";

    /** LPR浮动或固定利率 */
    private String rateMode = "LPR";

    /** 重定价周期，单位月 */
    private Integer repricingMonths = 12;

    /** 合同初始加点，1BP=0.01个百分点；缺省时由初始利率推算 */
    private BigDecimal lprSpreadBp;

    /** 首个合同重定价日；缺省时按原利率调整日选项推算 */
    private String repricingAnchorDate;

    /** 实际放款日；缺省时按首次还款日减一个月估算 */
    private String loanDate;

    /** 仅显式启用时采用一般情形的2023/2024存量房贷批量调息 */
    private Boolean applyHistoricalRateAdjustments = false;

}
