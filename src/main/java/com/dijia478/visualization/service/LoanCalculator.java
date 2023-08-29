package com.dijia478.visualization.service;

import com.dijia478.visualization.bean.LoanBO;
import com.dijia478.visualization.bean.MonthLoan;
import com.dijia478.visualization.bean.PrepaymentDTO;
import com.dijia478.visualization.bean.TotalLoan;

import java.util.List;

/**
 * 贷款计算器接口
 *
 * @author dijia478
 * @date 2023/8/9
 */
public interface LoanCalculator {

    /**
     * 计算还款情况
     *
     * @param data
     * @return
     */
    TotalLoan compute(LoanBO data);

    /**
     * 计算提前还款
     *
     * @param monthLoanList
     * @param prepaymentDTO
     * @return
     */
    TotalLoan computePrepayment(List<MonthLoan> monthLoanList, PrepaymentDTO prepaymentDTO);

}
