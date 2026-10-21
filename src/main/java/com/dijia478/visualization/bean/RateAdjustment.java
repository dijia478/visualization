package com.dijia478.visualization.bean;

import lombok.Data;
import java.math.BigDecimal;

/** 实际执行利率发生变化的原因及日期。 */
@Data
public class RateAdjustment {
    private String effectiveDate;
    private Integer effectiveMonth;
    private BigDecimal annualRate;
    private String reason;
}
