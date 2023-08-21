package com.dijia478.visualization.service;

import com.dijia478.visualization.bean.LoanDTO;
import com.dijia478.visualization.bean.TotalLoan;

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
    TotalLoan compute(LoanDTO data);

}
