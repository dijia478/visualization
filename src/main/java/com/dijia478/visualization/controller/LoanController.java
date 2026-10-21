package com.dijia478.visualization.controller;

import com.dijia478.visualization.bean.*;
import com.dijia478.visualization.service.LoanCalculator;
import com.dijia478.visualization.util.LoanUtil;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class LoanController {

    @Resource(name = "prepaymentCalculator")
    private LoanCalculator prepaymentCalculator;

    /** 默认利率与计算器共享随程序携带的最新已核验LPR，不依赖外部网络。 */
    @GetMapping("/calculator/defaults")
    public Map<String, Object> defaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("annualRate", LoanUtil.getLatestLprRate());
        defaults.put("lprUpdatedAt", LoanUtil.LPR_UPDATED_AT);
        return defaults;
    }

    @PostMapping("/calculator/prepaymentCalculator")
    public TotalLoan prepaymentCalculator(@RequestBody @Validated StockLoanDTO data) {
        validatedPrepayment(data);
        LoanUtil.PreparedLoan prepared = LoanUtil.prepare(data);
        TotalLoan totalLoan = prepaymentCalculator.compute(convertParam(data, prepared));
        setScale(totalLoan, data);
        List<AppliedPrepayment> applied = new ArrayList<>(prepared.getPreview().getPrepaymentRates());
        totalLoan.setAppliedPrepayments(applied);
        totalLoan.setRateChanges(prepared.getPreview().getRateChanges().stream()
                .filter(change -> change.getEffectiveMonth() <= totalLoan.getMonthLoanList().size())
                .filter(change -> totalLoan.getPayoffDate() == null
                        || change.getEffectiveDate().compareTo(totalLoan.getPayoffDate()) <= 0)
                .collect(Collectors.toList()));
        totalLoan.setLprUpdatedAt(prepared.getPreview().getLprUpdatedAt());
        totalLoan.setWarnings(prepared.getPreview().getWarnings());
        return totalLoan;
    }

    @PostMapping("/calculator/ratePreview")
    public RatePreview ratePreview(@RequestBody @Validated StockLoanDTO data) {
        validatedPrepayment(data);
        return LoanUtil.prepare(data).getPreview();
    }

    public LoanBO convertParam(StockLoanDTO data) {
        return convertParam(data, LoanUtil.prepare(data));
    }

    private LoanBO convertParam(StockLoanDTO data, LoanUtil.PreparedLoan prepared) {
        return LoanBO.builder()
                .amount(BigDecimal.valueOf(data.getAmount()).movePointRight(4))
                .month(BigDecimal.valueOf(data.getYear() * 12L))
                .rate(prepared.getInitialRate())
                .type(data.getType())
                .firstPaymentDate(data.getFirstPaymentDate())
                .prepaymentList(prepared.getEvents())
                .firstInterestSegments(prepared.getFirstInterestSegments())
                .build();
    }

    /** 金额按分展示；每一期独立从原始还款日偏移，保留月末还款日。 */
    public void setScale(TotalLoan totalLoan, StockLoanDTO data) {
        if (totalLoan.getOriginalTotalInterest() != null) {
            totalLoan.setOriginalTotalInterest(totalLoan.getOriginalTotalInterest().setScale(2, RoundingMode.HALF_UP));
        }
        totalLoan.setTotalInterest(totalLoan.getTotalInterest().setScale(2, RoundingMode.HALF_UP));
        totalLoan.setTotalRepayment(totalLoan.getTotalRepayment().setScale(2, RoundingMode.HALF_UP));
        LocalDate first = LoanUtil.parseDate(data.getFirstPaymentDate());
        for (int i = 0; i < totalLoan.getMonthLoanList().size(); i++) {
            MonthLoan row = totalLoan.getMonthLoanList().get(i);
            if (row.getDateFormat() == null) {
                row.setDateFormat(first.plusMonths(i).toString());
            }
        }
    }

    private void validatedPrepayment(LoanDTO data) {
        if (data.getPrepaymentList() == null) {
            return;
        }
        for (PrepaymentDTO event : data.getPrepaymentList()) {
            if (event.getRepayment() > data.getAmount()) {
                throw new LoanException(ResultEnum.REPAYMENT_TOO_BIG);
            }
        }
    }
}
