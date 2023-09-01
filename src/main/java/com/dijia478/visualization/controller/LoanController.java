package com.dijia478.visualization.controller;

import com.dijia478.visualization.bean.*;
import com.dijia478.visualization.service.LoanCalculator;
import com.dijia478.visualization.util.LoanUtil;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * @author dijia478
 * @date 2023/8/4
 */
@RestController
public class LoanController {

    /** 等额本息计算器 */
    @Resource(name = "equalRepaymentCalculator")
    private LoanCalculator equalRepaymentCalculator;

    /** 等额本金计算器 */
    @Resource(name = "equalPrincipalCalculator")
    private LoanCalculator equalPrincipalCalculator;

    /** 提前还款计算器 */
    @Resource(name = "prepaymentCalculator")
    private LoanCalculator prepaymentCalculator;

    /**
     * 贷款计算接口
     *
     * @param data
     * @return
     */
    @PostMapping("/calculator/loanCalculator")
    public TotalLoan loanCalculator(@RequestBody @Validated LoanDTO data) {
        TotalLoan totalLoan;
        LoanBO loanBO = convertParam(data);
        if (data.getType() == 1) {
            totalLoan = equalRepaymentCalculator.compute(loanBO);
        } else {
            totalLoan = equalPrincipalCalculator.compute(loanBO);
        }
        setScale(totalLoan);
        return totalLoan;
    }

    /**
     * 提前还款计算接口
     *
     * @param data
     * @return
     */
    @PostMapping("/calculator/prepaymentCalculator")
    public TotalLoan prepaymentCalculator(@RequestBody @Validated LoanDTO data) {
        validatedPrepayment(data);
        LoanBO loanBO = convertParam(data);
        TotalLoan totalLoan = prepaymentCalculator.compute(loanBO);
        setScale(totalLoan);
        return totalLoan;
    }

    /**
     * 参数转换
     *
     * @param data
     * @return
     */
    public LoanBO convertParam(LoanDTO data) {
        return LoanBO.builder()
                .amount(equalRepaymentCalculator.totalLoan(new BigDecimal(data.getAmount().toString())))
                .month(equalRepaymentCalculator.totalMonth(new BigDecimal(data.getYear().toString())))
                .rate(new BigDecimal(data.getRate().toString()))
                .type(data.getType())
                .prepaymentList(data.getPrepaymentList())
                .build();
    }

    /**
     * 对最终结果进行四舍五入保留两位小数，用于给前端展示
     *
     * @param totalLoan
     */
    public void setScale(TotalLoan totalLoan) {
        if (totalLoan.getOriginalTotalInterest() != null) {
            totalLoan.setOriginalTotalInterest(totalLoan.getOriginalTotalInterest().setScale(2, RoundingMode.HALF_UP));
        }
        totalLoan.setTotalInterest(totalLoan.getTotalInterest().setScale(2, RoundingMode.HALF_UP));
        totalLoan.setTotalRepayment(totalLoan.getTotalRepayment().setScale(2, RoundingMode.HALF_UP));
        for (MonthLoan monthLoan : totalLoan.getMonthLoanList()) {
            monthLoan.setRepayment(monthLoan.getRepayment().setScale(2, RoundingMode.HALF_UP));
            monthLoan.setPrincipal(monthLoan.getPrincipal().setScale(2, RoundingMode.HALF_UP));
            monthLoan.setInterest(monthLoan.getInterest().setScale(2, RoundingMode.HALF_UP));
            monthLoan.setRemainTotal(monthLoan.getRemainTotal().setScale(2, RoundingMode.HALF_UP));
            monthLoan.setRemainInterest(monthLoan.getRemainInterest().setScale(2, RoundingMode.HALF_UP));
            monthLoan.setRemainPrincipal(monthLoan.getRemainPrincipal().setScale(2, RoundingMode.HALF_UP));
            monthLoan.setTotalRepayment(monthLoan.getTotalRepayment().setScale(2, RoundingMode.HALF_UP));
            monthLoan.setTotalPrincipal(monthLoan.getTotalPrincipal().setScale(2, RoundingMode.HALF_UP));
            monthLoan.setTotalInterest(monthLoan.getTotalInterest().setScale(2, RoundingMode.HALF_UP));
            monthLoan.setTotalRepaymentAndRemainPrincipal(monthLoan.getTotalRepaymentAndRemainPrincipal().setScale(2, RoundingMode.HALF_UP));
        }
    }

    /**
     * 参数校验
     *
     * @param data
     */
    private void validatedPrepayment(LoanDTO data) {
        int totalMonth = data.getYear() * 12;
        int amount = data.getAmount();
        List<PrepaymentDTO> prepaymentList = data.getPrepaymentList();
        for (PrepaymentDTO prepaymentDTO : prepaymentList) {
            if (prepaymentDTO.getPrepaymentMonth() > 12 && prepaymentDTO.getPrepaymentMonth() > totalMonth) {
                throw new LoanException(ResultEnum.PREPAYMENT_MONTH_TOO_BIG);
            }
            if (prepaymentDTO.getRepayment() > amount) {
                throw new LoanException(ResultEnum.REPAYMENT_TOO_BIG);
            }
        }
    }

}
