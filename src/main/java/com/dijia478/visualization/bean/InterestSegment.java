package com.dijia478.visualization.bean;

import lombok.Data;
import java.math.BigDecimal;

/** 合同每月30天口径下的利率分段；未满分的应计息跨段、跨期结转。 */
@Data
public class InterestSegment {
    private int days;
    private BigDecimal annualRate;
    /** 相对本期已扣提前还款后的余额，该段尚未归还的本金（元）。 */
    private BigDecimal principalOffset = BigDecimal.ZERO;
    private BigDecimal interest;
}
