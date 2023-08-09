package com.dijia478.visualization.service.impl;

import cn.hutool.core.util.NumberUtil;
import com.alibaba.fastjson2.JSONObject;
import com.dijia478.visualization.pojo.MonthLoan;
import com.dijia478.visualization.pojo.TotalLoan;
import com.dijia478.visualization.service.LoanCalculator;
import com.dijia478.visualization.util.LoanUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 等额本息计算器
 *
 * @author dijia478
 * @date 2023/8/9
 */
@Service("equalLoanPayment")
public class EqualLoanPaymentCalculator implements LoanCalculator {

    @Override
    public TotalLoan compute(JSONObject data) {
        BigDecimal loanAmount = data.getBigDecimal("amount");
        loanAmount = LoanUtil.totalLoan(loanAmount);
        Integer loanYear = data.getInteger("year");
        BigDecimal loanRate = data.getBigDecimal("rate");
        Integer type = data.getInteger("type");

        TotalLoan loan = new TotalLoan();
        loan.setLoanAmount(loanAmount);
        loan.setLoanYear(loanYear);
        loan.setLoanRate(loanRate);
        loan.setType(type);

        // 贷款总期数（月）
        int totalMonth = LoanUtil.totalMonth(loanYear);
        // 月利率
        BigDecimal loanRateMonth = LoanUtil.loanRateMonth(loanRate);
        // （1+月利率）^ 还款月数
        BigDecimal factor = NumberUtil.add(loanRateMonth, 1).pow(totalMonth);
        // 每月还款额 = [贷款本金 ×月利率 ×（1+月利率）^ 还款月数] ÷[（1+月利率）^ 还款月数－1]
        BigDecimal repayment = NumberUtil.div(NumberUtil.mul(loanAmount, loanRateMonth, factor), NumberUtil.sub(factor, 1));
        // 总还款额 = 每月还款额 ×贷款总期数
        loan.setTotalRepayment(NumberUtil.mul(repayment, totalMonth));

        // 累积所还本金
        BigDecimal totalPrincipal = new BigDecimal("0");
        // 总利息
        BigDecimal totalInterest = new BigDecimal("0");
        // 已还款总数
        BigDecimal totalRepayment = new BigDecimal("0");
        // 剩余本金
        BigDecimal remainPrincipal = new BigDecimal(loanAmount.toString());
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

            BigDecimal interest = NumberUtil.mul(remainPrincipal, loanRateMonth);
            totalInterest = NumberUtil.add(totalInterest, interest);
            BigDecimal principal = NumberUtil.sub(repayment, interest);
            totalPrincipal = NumberUtil.add(totalPrincipal, principal);
            remainPrincipal = NumberUtil.sub(loanAmount, totalPrincipal);
            monthLoan.setInterest(interest);
            monthLoan.setPrincipal(principal);
            monthLoan.setRepayment(repayment);
            monthLoan.setRemainPrincipal(remainPrincipal);

            totalRepayment = totalRepayment.add(monthLoan.getRepayment());
            monthLoan.setTotalRepayment(totalRepayment);
            monthLoan.setTotalRepaymentAndRemainPrincipal(NumberUtil.add(totalRepayment, remainPrincipal));
            monthLoan.setRemainTotal(NumberUtil.sub(loan.getTotalRepayment(), totalRepayment));
            monthLoanList.add(monthLoan);
        }
        loan.setTotalInterest(totalInterest);
        loan.setMonthLoanList(monthLoanList);
        return loan;
    }

}
