package com.dijia478.visualization.service.impl;

import com.dijia478.visualization.bean.LoanDTO;
import com.dijia478.visualization.bean.PrepaymentDTO;
import com.dijia478.visualization.bean.TotalLoan;
import com.dijia478.visualization.service.LoanCalculator;
import com.dijia478.visualization.service.LoanCalculatorAdapter;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 提前还款计算器
 *
 * @author dijia478
 * @date 2023/8/17
 */
@Service("prepaymentCalculator")
public class PrepaymentCalculator extends LoanCalculatorAdapter {

    /** 等额本息计算器 */
    @Resource(name = "equalRepaymentCalculator")
    private LoanCalculator equalRepaymentCalculator;

    /** 等额本金计算器 */
    @Resource(name = "equalPrincipalCalculator")
    private LoanCalculator equalPrincipalCalculator;

    @Override
    public TotalLoan compute(LoanDTO data) {
        TotalLoan totalLoan;
        if (data.getType() == 1) {
            totalLoan = equalRepaymentCalculator.compute(data);
        } else {
            totalLoan = equalPrincipalCalculator.compute(data);
        }

        List<PrepaymentDTO> prepaymentList = data.getPrepaymentList();
        for (PrepaymentDTO prepaymentDTO : prepaymentList) {

        }
        return null;
    }

}
