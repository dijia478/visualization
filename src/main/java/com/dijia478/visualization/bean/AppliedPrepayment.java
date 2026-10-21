package com.dijia478.visualization.bean;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AppliedPrepayment {
    private String prepaymentDate;
    private Integer effectiveMonth;
    private BigDecimal repayment;
    private BigDecimal newRate;
    private Boolean automatic;
}
