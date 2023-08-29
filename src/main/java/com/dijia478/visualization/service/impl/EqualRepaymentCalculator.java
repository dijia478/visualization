package com.dijia478.visualization.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.NumberUtil;
import com.dijia478.visualization.bean.*;
import com.dijia478.visualization.service.LoanCalculatorAdapter;
import com.dijia478.visualization.util.LoanUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 等额本息计算器
 *
 * @author dijia478
 * @date 2023/8/9
 */
@Service("equalRepaymentCalculator")
public class EqualRepaymentCalculator extends LoanCalculatorAdapter {

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
        // （1+月利率）^ 还款月数
        BigDecimal factor = NumberUtil.add(loanRateMonth, 1).pow(totalMonth.intValue());
        // 每月还款额 = [贷款本金 ×月利率 ×（1+月利率）^ 还款月数] ÷[（1+月利率）^ 还款月数－1]
        BigDecimal repayment = NumberUtil.div(NumberUtil.mul(loanAmount, loanRateMonth, factor), NumberUtil.sub(factor, 1));
        // 总还款额 = 每月还款额 ×贷款总期数
        loan.setTotalRepayment(NumberUtil.mul(repayment, totalMonth));

        // 累积已还本金
        BigDecimal totalPrincipal = new BigDecimal("0");
        // 累计已还利息
        BigDecimal totalInterest = new BigDecimal("0");
        // 累计已还总额
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
            BigDecimal principal = NumberUtil.sub(repayment, interest);
            totalPrincipal = NumberUtil.add(totalPrincipal, principal);
            remainPrincipal = NumberUtil.sub(loanAmount, totalPrincipal);
            monthLoan.setInterest(interest);
            monthLoan.setPrincipal(principal);
            monthLoan.setRepayment(repayment);
            monthLoan.setRemainPrincipal(remainPrincipal);
            monthLoan.setRemainMonth(totalMonth.intValue() - i - 1);

            totalRepayment = NumberUtil.add(totalRepayment, monthLoan.getRepayment());
            monthLoan.setTotalRepayment(totalRepayment);
            monthLoan.setTotalPrincipal(totalPrincipal);
            monthLoan.setTotalInterest(totalInterest);
            monthLoan.setTotalRepaymentAndRemainPrincipal(NumberUtil.add(totalRepayment, remainPrincipal));
            monthLoan.setRemainTotal(NumberUtil.sub(loan.getTotalRepayment(), totalRepayment));
            monthLoan.setRemainInterest(NumberUtil.sub(monthLoan.getRemainTotal(), monthLoan.getRemainPrincipal()));
            monthLoanList.add(monthLoan);
        }
        loan.setTotalInterest(totalInterest);
        loan.setMonthLoanList(monthLoanList);
        return loan;
    }

    @Override
    public TotalLoan computePrepayment(List<MonthLoan> monthLoanList, PrepaymentDTO prepaymentDTO) {
        BigDecimal repayment = new BigDecimal(prepaymentDTO.getRepayment().toString());
        repayment = LoanUtil.totalLoan(repayment);
        BigDecimal newRate = prepaymentDTO.getNewRate();
        Integer prepaymentMonth = prepaymentDTO.getPrepaymentMonth();
        Integer repaymentType = prepaymentDTO.getRepaymentType();

        MonthLoan lastMonthLoan = monthLoanList.get(prepaymentMonth - 2);
        // 剩余本金
        BigDecimal remainPrincipal = NumberUtil.sub(lastMonthLoan.getRemainPrincipal(), repayment);
        if (remainPrincipal.compareTo(BigDecimal.ZERO) < 0) {
            throw new LoanException(ResultEnum.REPAYMENT_TOO_BIG);
        }
        TotalLoan totalLoan;
        if (repaymentType == 1) {
            // 月供不变，期限缩短
            // 月利率
            BigDecimal loanRateMonth = LoanUtil.loanRateMonth(newRate);
            // ln（原月供 / （原月供 - 剩余本金 × 月利率））
            double numerator = Math.log(NumberUtil.div(lastMonthLoan.getRepayment(), NumberUtil.sub(lastMonthLoan.getRepayment(), NumberUtil.mul(remainPrincipal, loanRateMonth))).doubleValue());
            // 新贷款期限 = numerator / ln（月利率 + 1）
            double totalMonth = NumberUtil.div(numerator, Math.log(NumberUtil.add(loanRateMonth, 1).doubleValue()));
            totalMonth = Math.ceil(totalMonth);
            LoanBO loanBO = LoanBO.builder()
                    .amount(remainPrincipal)
                    .month(new BigDecimal(String.valueOf(totalMonth)))
                    .rate(newRate)
                    .type(prepaymentDTO.getNewType())
                    .build();
            totalLoan = compute(loanBO);
        } else {
            // 期限不变，月供减少
            LoanBO loanBO = LoanBO.builder()
                    .amount(remainPrincipal)
                    .month(new BigDecimal(String.valueOf(monthLoanList.size() - lastMonthLoan.getMonth())))
                    .rate(newRate)
                    .type(prepaymentDTO.getNewType())
                    .build();
            totalLoan = compute(loanBO);
        }

        List<MonthLoan> sub = ListUtil.sub(monthLoanList, 0, prepaymentMonth - 1);
        for (int i = sub.size() - 1; i >= 0; i--) {
            totalLoan.getMonthLoanList().add(0, sub.get(i));
        }
        return totalLoan;
    }

}
