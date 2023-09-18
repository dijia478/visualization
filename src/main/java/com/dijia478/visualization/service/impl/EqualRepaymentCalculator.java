package com.dijia478.visualization.service.impl;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
import com.dijia478.visualization.bean.*;
import com.dijia478.visualization.service.LoanCalculatorAdapter;
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
        BigDecimal monthRate = monthRate(loanRate);
        // 月供
        BigDecimal repayment = getRepayment(loanAmount, monthRate, totalMonth);
        // 总还款额
        loan.setTotalRepayment(getTotalRepayment(repayment, totalMonth));
        // 总利息
        loan.setTotalInterest(getTotalInterest(loan.getTotalRepayment(), loanAmount));

        // 累积已还本金
        BigDecimal totalPrincipal = new BigDecimal("0");
        // 累计已还利息
        BigDecimal totalInterest = new BigDecimal("0");
        // 累计已还总额
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

            BigDecimal principal = getPrincipal(loanAmount, monthRate, totalMonth, i + 1);
            BigDecimal interest = getInterest(repayment, principal);
            totalRepayment = NumberUtil.add(totalRepayment, repayment);
            totalPrincipal = NumberUtil.add(totalPrincipal, principal);
            totalInterest = NumberUtil.add(totalInterest, interest);
            BigDecimal remainTotal = getRemainTotal(loan.getTotalRepayment(), totalRepayment);
            BigDecimal remainPrincipal = getRemainPrincipal(loan.getLoanAmount(), monthRate, repayment, i + 1);
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
            monthLoan.setDateFormat(DateUtil.parse(data.getFirstPaymentDate()).offset(DateField.MONTH, i).toDateStr());
            monthLoanList.add(monthLoan);
        }
        loan.setMonthLoanList(monthLoanList);
        return loan;
    }

    /**
     * 获取月供
     *
     * @param p 本金
     * @param i 月利率
     * @param n 贷款期数
     * @return 月供
     */
    private BigDecimal getRepayment(BigDecimal p, BigDecimal i, BigDecimal n) {
        BigDecimal b1 = NumberUtil.add(i, BigDecimal.ONE).pow(n.intValue());
        return NumberUtil.div(NumberUtil.mul(p, i, b1), NumberUtil.sub(b1, BigDecimal.ONE), DEFAULT_SCALE);
    }

    /**
     * 获取月供中本金
     *
     * @param p 本金
     * @param i 月利率
     * @param n 贷款期数
     * @param k 当前期数
     * @return 月供中本金
     */
    private BigDecimal getPrincipal(BigDecimal p, BigDecimal i, BigDecimal n, Integer k) {
        BigDecimal b1 = NumberUtil.add(i, BigDecimal.ONE).pow(n.intValue());
        BigDecimal b2 = NumberUtil.add(i, BigDecimal.ONE).pow(k - 1);
        return NumberUtil.div(NumberUtil.mul(p, i, b2), NumberUtil.sub(b1, BigDecimal.ONE), DEFAULT_SCALE);
    }

    /**
     * 获取月供中利息
     *
     * @param m 月供
     * @param mp 月供中本金
     * @return 月供中利息
     */
    private BigDecimal getInterest(BigDecimal m, BigDecimal mp) {
        return NumberUtil.sub(m, mp);
    }

    /**
     * 获取总还款额
     *
     * @param n 贷款期数
     * @param m 月供
     * @return 总还款额
     */
    private BigDecimal getTotalRepayment(BigDecimal n, BigDecimal m) {
        return NumberUtil.mul(n, m);
    }

    /**
     * 获取总利息
     *
     * @param t 总还款额
     * @param p 本金
     * @return 总利息
     */
    private BigDecimal getTotalInterest(BigDecimal t, BigDecimal p) {
        return NumberUtil.sub(t, p);
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
     * @param i 月利率
     * @param m 月供
     * @param k 当前期数
     * @return 月供后剩余本金
     */
    private BigDecimal getRemainPrincipal(BigDecimal p, BigDecimal i, BigDecimal m, Integer k) {
        BigDecimal b1 = NumberUtil.add(i, BigDecimal.ONE).pow(k);
        BigDecimal b2 = NumberUtil.div(m, i, DEFAULT_SCALE);
        return NumberUtil.add(NumberUtil.mul(b1, NumberUtil.sub(p, b2)), b2);
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
        repayment = totalLoan(repayment);
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
            BigDecimal monthRate = monthRate(newRate);
            // ln（原月供 / （原月供 - 剩余本金 × 月利率））
            double numerator = Math.log(NumberUtil.div(lastMonthLoan.getRepayment(), NumberUtil.sub(lastMonthLoan.getRepayment(), NumberUtil.mul(remainPrincipal, monthRate)), DEFAULT_SCALE).doubleValue());
            // 新贷款期限 = numerator / ln（月利率 + 1）
            double totalMonth = NumberUtil.div(numerator, Math.log(NumberUtil.add(monthRate, 1).doubleValue()), DEFAULT_SCALE);
            totalMonth = Math.ceil(totalMonth);
            LoanBO loanBO = LoanBO.builder()
                    .amount(remainPrincipal)
                    .month(new BigDecimal(String.valueOf(totalMonth)))
                    .rate(newRate)
                    .type(prepaymentDTO.getNewType())
                    .firstPaymentDate(monthLoanList.get(0).getDateFormat())
                    .build();
            totalLoan = compute(loanBO);
        } else {
            // 期限不变，月供减少
            LoanBO loanBO = LoanBO.builder()
                    .amount(remainPrincipal)
                    .month(new BigDecimal(String.valueOf(monthLoanList.size() - lastMonthLoan.getMonth())))
                    .rate(newRate)
                    .type(prepaymentDTO.getNewType())
                    .firstPaymentDate(monthLoanList.get(0).getDateFormat())
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
