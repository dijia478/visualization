package com.dijia478.visualization.service.impl;

import com.dijia478.visualization.bean.LoanBO;
import com.dijia478.visualization.bean.TotalLoan;
import com.dijia478.visualization.service.LoanCalculatorAdapter;
import org.springframework.stereotype.Service;

/** 等额本金：每期本金固定，利息按剩余本金计算，最后一期结清尾差。 */
@Service("equalPrincipalCalculator")
public class EqualPrincipalCalculator extends LoanCalculatorAdapter {
    @Override
    public TotalLoan compute(LoanBO data) {
        return schedule(data, null, null);
    }
}
