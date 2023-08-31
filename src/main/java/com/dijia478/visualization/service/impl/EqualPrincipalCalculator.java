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
        // 月供中本金
        BigDecimal principal = getPrincipal(loanAmount, totalMonth);
        // 总利息
        loan.setTotalInterest(getTotalInterest(loanAmount, loanRateMonth, totalMonth));
        // 总还款额
        loan.setTotalRepayment(getTotalRepayment(loanAmount, loan.getTotalInterest()));

        // 累积所还本金
        BigDecimal totalPrincipal = new BigDecimal("0");
        // 总利息
        BigDecimal totalInterest = new BigDecimal("0");
        // 已还款总数
        BigDecimal totalRepayment = new BigDecimal("0");

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

            BigDecimal interest = getInterest(loanAmount, loanRateMonth, totalMonth, i + 1);
            BigDecimal repayment = getRepayment(principal, interest);
            totalRepayment = NumberUtil.add(totalRepayment, repayment);
            totalPrincipal = NumberUtil.add(totalPrincipal, principal);
            totalInterest = NumberUtil.add(totalInterest, interest);
            BigDecimal remainTotal = getRemainTotal(loan.getTotalRepayment(), totalRepayment);
            BigDecimal remainPrincipal = getRemainPrincipal(loanAmount, totalMonth, i + 1);
            BigDecimal remainInterest = getRemainInterest(loan.getTotalInterest(), totalInterest);

            monthLoan.setRepayment(repayment);
            monthLoan.setPrincipal(principal);
            monthLoan.setInterest(interest);
            monthLoan.setRemainPrincipal(remainPrincipal);
            monthLoan.setRemainMonth(totalMonth.intValue() - i - 1);
            monthLoan.setTotalRepayment(totalRepayment);
            monthLoan.setTotalPrincipal(totalPrincipal);
            monthLoan.setTotalInterest(totalInterest);
            monthLoan.setTotalRepaymentAndRemainPrincipal(NumberUtil.add(totalRepayment, remainPrincipal));
            monthLoan.setRemainTotal(remainTotal);
            monthLoan.setRemainInterest(remainInterest);
            monthLoanList.add(monthLoan);
        }
        loan.setMonthLoanList(monthLoanList);
        return loan;
    }

    /**
     * 获取月供
     *
     * @param mp 月供中本金
     * @param mi 月供中利息
     * @return 月供
     */
    private BigDecimal getRepayment(BigDecimal mp, BigDecimal mi) {
        return NumberUtil.add(mp, mi);
    }

    /**
     * 获取月供中本金
     *
     * @param p 本金
     * @param n 贷款期数
     * @return 月供中本金
     */
    private BigDecimal getPrincipal(BigDecimal p, BigDecimal n) {
        return NumberUtil.div(p, n);
    }

    /**
     * 获取月供中利息
     *
     * @param p 本金
     * @param i 月利率
     * @param n 贷款期数
     * @param k 当前期数
     * @return 月供中利息
     */
    private BigDecimal getInterest(BigDecimal p, BigDecimal i, BigDecimal n, Integer k) {
        BigDecimal b1 = NumberUtil.div(p, n);
        BigDecimal b2 = NumberUtil.sub(p, NumberUtil.mul(b1, NumberUtil.sub(k, BigDecimal.ONE)));
        return NumberUtil.mul(i, b2);
    }

    /**
     * 获取总还款额
     *
     * @param p 本金
     * @param ti 总利息
     * @return 总还款额
     */
    private BigDecimal getTotalRepayment(BigDecimal p, BigDecimal ti) {
        return NumberUtil.add(p, ti);
    }

    /**
     * 获取总利息
     *
     * @param p 本金
     * @param i 月利率
     * @param n 贷款期数
     * @return 总利息
     */
    private BigDecimal getTotalInterest(BigDecimal p, BigDecimal i, BigDecimal n) {
        BigDecimal b1 = NumberUtil.div(NumberUtil.add(n, BigDecimal.ONE), 2);
        return NumberUtil.mul(p, i, b1);
    }

    /**
     * 获取月供后剩余贷款
     *
     * @param t 总还款额
     * @param totalRepayment 累计已还总额
     * @return 月供后剩余贷款
     */
    private BigDecimal getRemainTotal(BigDecimal t, BigDecimal totalRepayment) {
        return NumberUtil.sub(t, totalRepayment);
    }

    /**
     * 获取月供后剩余本金
     *
     * @param p 本金
     * @param n 贷款期数
     * @param k 当前期数
     * @return 月供后剩余本金
     */
    private BigDecimal getRemainPrincipal(BigDecimal p, BigDecimal n, Integer k) {
        BigDecimal b1 = NumberUtil.div(p, n);
        return NumberUtil.sub(p, NumberUtil.mul(b1, k));
    }

    /**
     * 获取月供后剩余利息
     *
     * @param ti 总利息
     * @param totalInterest 累计已还利息
     * @return 月供后剩余利息
     */
    private BigDecimal getRemainInterest(BigDecimal ti, BigDecimal totalInterest) {
        return NumberUtil.sub(ti, totalInterest);
    }


    @Override
    public TotalLoan computePrepayment(List<MonthLoan> monthLoanList, PrepaymentDTO prepaymentDTO) {
        BigDecimal repayment = new BigDecimal(prepaymentDTO.getRepayment().toString());
        repayment = LoanUtil.totalLoan(repayment);
        BigDecimal newRate = prepaymentDTO.getNewRate();
        Integer prepaymentMonth = prepaymentDTO.getPrepaymentMonth();
        Integer repaymentType = prepaymentDTO.getRepaymentType();

        MonthLoan firstMonthLoan = monthLoanList.get(0);
        MonthLoan lastMonthLoan = monthLoanList.get(prepaymentMonth - 2);

        // 剩余本金
        BigDecimal remainPrincipal = NumberUtil.sub(lastMonthLoan.getRemainPrincipal(), repayment);
        if (remainPrincipal.compareTo(BigDecimal.ZERO) < 0) {
            throw new LoanException(ResultEnum.REPAYMENT_TOO_BIG);
        }
        TotalLoan totalLoan;
        if (repaymentType == 1) {
            // 月供不变，期限缩短
            // 原贷款总额
            BigDecimal amount = NumberUtil.add(firstMonthLoan.getRemainPrincipal(), firstMonthLoan.getTotalPrincipal());
            // 新贷款期限 = 剩余本金 / 原贷款总额 ×原贷款期限
            double totalMonth = NumberUtil.mul(NumberUtil.div(remainPrincipal, amount), firstMonthLoan.getMonth() + firstMonthLoan.getRemainMonth()).doubleValue();
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
