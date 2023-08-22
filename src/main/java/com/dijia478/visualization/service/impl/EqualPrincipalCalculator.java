package com.dijia478.visualization.service.impl;

import cn.hutool.core.util.NumberUtil;
import com.dijia478.visualization.bean.*;
import com.dijia478.visualization.service.LoanCalculatorAdapter;
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
@Service("equalPrincipalCalculator")
public class EqualPrincipalCalculator extends LoanCalculatorAdapter {

    @Override
    public TotalLoan compute(LoanBO data) {
        BigDecimal loanAmount = data.getAmount();
        BigDecimal totalMonth = data.getMonth();
        BigDecimal loanRate = data.getRate();
        Integer type = data.getType();

        TotalLoan loan = new TotalLoan();
        loan.setLoanAmount(loanAmount);
        loan.setLoanMonth(totalMonth);
        loan.setLoanRate(loanRate);
        loan.setType(type);

        // 月利率
        BigDecimal loanRateMonth = LoanUtil.loanRateMonth(loanRate);
        // 每月要还本金 = 总贷款额 ÷贷款总期数
        BigDecimal principal = NumberUtil.div(loanAmount, totalMonth);

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
        for (int i = 0; i < totalMonth.intValue(); i++) {
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
            totalPrincipal = NumberUtil.add(totalPrincipal, principal);
            remainPrincipal = NumberUtil.sub(loanAmount, totalPrincipal);
            monthLoan.setInterest(interest);
            monthLoan.setPrincipal(principal);
            monthLoan.setRepayment(NumberUtil.add(principal, interest));
            monthLoan.setRemainPrincipal(remainPrincipal);

            totalRepayment = NumberUtil.add(totalRepayment, monthLoan.getRepayment());
            monthLoan.setTotalRepayment(totalRepayment);
            monthLoan.setTotalPrincipal(totalPrincipal);
            monthLoan.setTotalInterest(totalInterest);
            monthLoan.setTotalRepaymentAndRemainPrincipal(NumberUtil.add(totalRepayment,monthLoan.getRemainPrincipal()));
            monthLoanList.add(monthLoan);
        }
        loan.setTotalRepayment(totalRepayment);
        loan.setTotalInterest(totalInterest);

        BigDecimal totalPayedRepayment = new BigDecimal("0");
        for (MonthLoan monthLoan : monthLoanList) {
            totalPayedRepayment = NumberUtil.add(totalPayedRepayment, monthLoan.getRepayment());
            monthLoan.setRemainTotal(NumberUtil.sub(totalRepayment, totalPayedRepayment));
            monthLoan.setRemainInterest(NumberUtil.sub(monthLoan.getRemainTotal(), monthLoan.getRemainPrincipal()));
        }
        loan.setMonthLoanList(monthLoanList);
        return loan;
    }

    @Override
    public TotalLoan computePrepayment(List<MonthLoan> monthLoanList, PrepaymentDTO prepaymentDTO) {
        return super.computePrepayment(monthLoanList, prepaymentDTO);
    }

}