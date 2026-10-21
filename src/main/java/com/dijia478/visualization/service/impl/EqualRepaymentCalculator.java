package com.dijia478.visualization.service.impl;

import com.dijia478.visualization.bean.LoanBO;
import com.dijia478.visualization.bean.TotalLoan;
import com.dijia478.visualization.service.LoanCalculatorAdapter;
import org.springframework.stereotype.Service;

/** 等额本息：固定月供中先记当期利息，再冲减本金，最后一期结清尾差。 */
@Service("equalRepaymentCalculator")
public class EqualRepaymentCalculator extends LoanCalculatorAdapter {
    @Override
    public TotalLoan compute(LoanBO data) {
        return schedule(data, null, null);
    }
}
