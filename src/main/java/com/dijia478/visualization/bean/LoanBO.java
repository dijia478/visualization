package com.dijia478.visualization.bean;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 贷款计算业务对象
 *
 * @author dijia478
 * @date 2023/8/21
 */
@Builder
@Data
public class LoanBO {

    private BigDecimal amount;

    private BigDecimal month;

    private BigDecimal rate;

    private Integer type;

    private String firstPaymentDate;

    private List<PrepaymentDTO> prepaymentList;

}
