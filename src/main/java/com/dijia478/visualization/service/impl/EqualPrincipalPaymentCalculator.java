package com.dijia478.visualization.service.impl;

import cn.hutool.core.util.NumberUtil;
import com.alibaba.fastjson2.JSONObject;
import com.dijia478.visualization.pojo.MonthLoan;
import com.dijia478.visualization.pojo.TotalLoan;
import com.dijia478.visualization.service.LoanCalculator;
import com.dijia478.visualization.util.LoanUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
        BigDecimal loanAmount = data.getBigDecimal("amount");
        Integer totalYear = data.getInteger("year");
        BigDecimal loanRate = data.getBigDecimal("rate");
        Integer type = data.getInteger("type");

        TotalLoan loan = new TotalLoan();
        loan.setLoanAmount(loanAmount);
        loan.setTotalYear(totalYear);
        loan.setLoanRate(loanRate);
        loan.setType(type);

        // 贷款总期数（月）
        int totalMonth = LoanUtil.totalMonth(totalYear);
        // 月利率
        BigDecimal loanRateMonth = NumberUtil.div(NumberUtil.div(loanRate, 100), 12);
        // 每月要还本金 = 总贷款额 ÷贷款总期数
        BigDecimal principal = NumberUtil.div(loanAmount, totalMonth);

        // 累积所还本金
        BigDecimal totalPrincipal = new BigDecimal("0");
        // 总利息
        BigDecimal totalInterest = new BigDecimal("0");
        // 已还款总数
        BigDecimal totalRepayment = new BigDecimal("0");
        List<MonthLoan> monthLoanList = new ArrayList<>();
        int year = 0;
        int monthInYear = 0;
        for (int i = 0; i < totalMonth; i++) {
            MonthLoan monthLoan = new MonthLoan();
            monthLoan.setMonth(i + 1);
            monthLoan.setYear(year + 1);
            monthLoan.setMonthInYear(++monthInYear);
            if ((i + 1) % 12 == 0) {
                year++;
                monthInYear = 0;
            }
            totalPrincipal = NumberUtil.add(totalPrincipal, principal);
            monthLoan.setPrincipal(principal);
            BigDecimal interest = NumberUtil.mul(NumberUtil.sub(loanAmount, totalPrincipal), loanRateMonth);
            monthLoan.setInterest(interest);
            totalInterest = NumberUtil.add(totalInterest, interest);
            monthLoan.setRepayment(NumberUtil.add(principal, interest));
            totalRepayment = NumberUtil.add(totalRepayment, monthLoan.getRepayment());
            monthLoan.setRemainPrincipal(NumberUtil.sub(loanAmount, totalPrincipal));
            monthLoanList.add(monthLoan);
        }
        loan.setTotalRepayment(totalRepayment);
        loan.setTotalInterest(totalInterest);
        BigDecimal totalPayedRepayment = new BigDecimal("0");
        for (MonthLoan monthLoan : monthLoanList) {
            totalPayedRepayment = NumberUtil.add(totalPayedRepayment, monthLoan.getRepayment());
            monthLoan.setRemainTotal(NumberUtil.sub(totalRepayment, totalPayedRepayment));
        }
        loan.setMonthLoanList(monthLoanList);
        return loan;
    }

}
