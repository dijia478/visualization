package com.dijia478.visualization.bean;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class RatePreview {
    private List<AppliedPrepayment> prepaymentRates;
    private String lprUpdatedAt;
    private BigDecimal lprRate;
    private List<String> warnings;
    private List<RateAdjustment> rateChanges;

}
