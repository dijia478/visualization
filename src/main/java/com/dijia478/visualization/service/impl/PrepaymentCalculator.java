package com.dijia478.visualization.service.impl;

import cn.hutool.core.util.NumberUtil;
import com.dijia478.visualization.bean.LoanBO;
import com.dijia478.visualization.bean.MonthLoan;
import com.dijia478.visualization.bean.PrepaymentDTO;
import com.dijia478.visualization.bean.TotalLoan;
import com.dijia478.visualization.service.LoanCalculator;
import com.dijia478.visualization.service.LoanCalculatorAdapter;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

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
    public TotalLoan compute(LoanBO data) {
        TotalLoan totalLoan;
        if (data.getType() == 1) {
            totalLoan = equalRepaymentCalculator.compute(data);
        } else {
            totalLoan = equalPrincipalCalculator.compute(data);
        }
        BigDecimal originalTotalInterest = totalLoan.getTotalInterest();
        totalLoan.setOriginalTotalInterest(originalTotalInterest);
        List<PrepaymentDTO> prepaymentList = data.getPrepaymentList();
        if (CollectionUtils.isEmpty(prepaymentList)) {
            return totalLoan;
        }

        List<Integer> lprMonth = new ArrayList<>();
        prepaymentList.sort(Comparator.comparingInt(PrepaymentDTO::getPrepaymentMonth));
        for (int i = 0; i < prepaymentList.size(); i++) {
            PrepaymentDTO prepaymentDTO = prepaymentList.get(i);
            if (Integer.valueOf(1).equals(prepaymentDTO.getLprRate())) {
                lprMonth.add(prepaymentDTO.getPrepaymentMonth());
            }

            if (i > 0 && prepaymentDTO.getRepayment() == 0 ) {
                PrepaymentDTO beforePrepaymentDTO = prepaymentList.get(i - 1);
                if (beforePrepaymentDTO.getNewRate().compareTo(prepaymentDTO.getNewRate()) == 0) {
                    continue;
                }
            }

            List<MonthLoan> monthLoanList = totalLoan.getMonthLoanList();
            if (prepaymentDTO.getNewType() == 1) {
                totalLoan = equalRepaymentCalculator.computePrepayment(monthLoanList, prepaymentDTO);
            } else {
                totalLoan = equalPrincipalCalculator.computePrepayment(monthLoanList, prepaymentDTO);
            }

            int year = 0;
            int monthInYear = 0;
            int size = totalLoan.getMonthLoanList().size();
            for (int j = 0; j < size; j++) {
                MonthLoan monthLoan = totalLoan.getMonthLoanList().get(j);
                monthLoan.setMonth(j + 1);
                monthLoan.setYear(year + 1);
                monthLoan.setMonthInYear(++monthInYear);
                if ((j + 1) % 12 == 0) {
                    year++;
                    monthInYear = 0;
                }
            }
        }

        Map<Integer, BigDecimal> collect = prepaymentList.stream().collect(Collectors.toMap(p -> p.getPrepaymentMonth() - 1, p -> totalLoan(new BigDecimal(p.getRepayment().toString())), (a, b) -> a));
        Set<Integer> keySet = collect.keySet();
        BigDecimal totalRepayment = new BigDecimal("0");
        BigDecimal totalPrincipal = new BigDecimal("0");
        BigDecimal totalInterest = new BigDecimal("0");
        int year = 0;
        int monthInYear = 0;
        int size = totalLoan.getMonthLoanList().size();
        for (int i = 0; i < size; i++) {
            MonthLoan monthLoan = totalLoan.getMonthLoanList().get(i);
            monthLoan.setMonth(i + 1);
            monthLoan.setYear(year + 1);
            monthLoan.setMonthInYear(++monthInYear);
            if ((i + 1) % 12 == 0) {
                year++;
                monthInYear = 0;
            }

            if (keySet.contains(i)) {
                totalRepayment = NumberUtil.add(totalRepayment, monthLoan.getRepayment(), collect.get(i));
                totalPrincipal = NumberUtil.add(totalPrincipal, monthLoan.getPrincipal(), collect.get(i));
            } else {
                totalRepayment = NumberUtil.add(totalRepayment, monthLoan.getRepayment());
                totalPrincipal = NumberUtil.add(totalPrincipal, monthLoan.getPrincipal());
            }
            totalInterest = NumberUtil.add(totalInterest, monthLoan.getInterest());
            monthLoan.setTotalRepayment(totalRepayment);
            monthLoan.setTotalPrincipal(totalPrincipal);
            monthLoan.setTotalInterest(totalInterest);
            monthLoan.setTotalRepaymentAndRemainPrincipal(NumberUtil.add(totalRepayment, monthLoan.getRemainPrincipal()));
        }
        totalLoan.setLoanAmount(data.getAmount());
        totalLoan.setLoanMonth(new BigDecimal(size));
        totalLoan.setLoanRate(data.getRate());
        totalLoan.setType(data.getType());
        totalLoan.setTotalRepayment(totalLoan.getMonthLoanList().get(size - 1).getTotalRepayment());
        totalLoan.setTotalInterest(totalLoan.getMonthLoanList().get(size - 1).getTotalInterest());
        totalLoan.setOriginalTotalInterest(originalTotalInterest);
        totalLoan.setLprMonth(lprMonth);
        return totalLoan;
    }

}
