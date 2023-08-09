package com.dijia478.visualization.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.dijia478.visualization.pojo.TotalLoan;
import com.dijia478.visualization.service.LoanCalculator;
import org.springframework.stereotype.Service;

/**
 * 等额本金计算器
 *
 * @author dijia478
 * @date 2023/8/9
 */
@Service("equalPrincipalPayment")
public class EqualPrincipalPaymentCalculator implements LoanCalculator {

    @Override
    public TotalLoan compute(JSONObject data) {
        return null;
    }

}
