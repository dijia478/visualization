package com.dijia478.visualization.service.impl;

import com.dijia478.visualization.bean.*;
import com.dijia478.visualization.service.LoanCalculator;
import com.dijia478.visualization.service.LoanCalculatorAdapter;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;

/** 按月顺序应用利率调整与提前还款，复用两种还款策略。 */
@Service("prepaymentCalculator")
public class PrepaymentCalculator extends LoanCalculatorAdapter {

    @Resource(name = "equalRepaymentCalculator")
    private LoanCalculator equalRepaymentCalculator;

    @Resource(name = "equalPrincipalCalculator")
    private LoanCalculator equalPrincipalCalculator;

    @Override
    public TotalLoan compute(LoanBO data) {
        List<PrepaymentDTO> events = data.getPrepaymentList() == null ? Collections.emptyList()
                : new ArrayList<>(data.getPrepaymentList());
        // 同期也逐笔重排计划，保留先缩期、后降月供等操作的真实顺序。
        events.sort(Comparator.comparingInt(PrepaymentDTO::getPrepaymentMonth));
        TotalLoan actual = apply(data, events, true);
        // 比较场景保留相同利率/还款方式，排除LPR下降本身带来的“提前还款节省”。
        TotalLoan baseline = events.stream().anyMatch(e -> e.getRepayment() > 0)
                ? apply(data, events, false) : actual;
        actual.setOriginalTotalInterest(baseline.getTotalInterest());
        List<Integer> rateMonths = new ArrayList<>();
        List<BigDecimal> rates = new ArrayList<>();
        for (PrepaymentDTO event : events) {
            if (Integer.valueOf(1).equals(event.getLprRate()) && event.getPrepaymentMonth() <= actual.getMonthLoanList().size()) {
                rateMonths.add(event.getPrepaymentMonth());
                rates.add(event.getNewRate());
            }
        }
        actual.setLprMonth(rateMonths);
        actual.setLprRate(rates);
        actual.setLoanRate(data.getRate());
        actual.setType(data.getType());
        return actual;
    }

    private LoanCalculator calculator(int type) {
        return type == 1 ? equalRepaymentCalculator : equalPrincipalCalculator;
    }

    private TotalLoan apply(LoanBO data, List<PrepaymentDTO> events, boolean prepay) {
        TotalLoan result = calculator(data.getType()).compute(data);
        int lastAppliedMonth = 0;
        for (PrepaymentDTO input : events) {
            PrepaymentDTO event = copy(input);
            if (!prepay) {
                event.setRepayment(0);
                event.setRepaymentType(2);
                event.setInterestSegments(withoutPrepaymentOffsets(event.getInterestSegments()));
            }
            int paid = event.getPrepaymentMonth() - 1;
            if (paid >= result.getMonthLoanList().size() && event.getRepayment() == 0) {
                continue;
            }
            // 对比方案移除纯提前还本事件；无利率/方式变化时重排月供会引入额外舍入漂移。
            if (!prepay && !Integer.valueOf(1).equals(event.getLprRate())
                    && event.getNewRate().compareTo(result.getLoanRate()) == 0
                    && event.getNewType().equals(result.getType())) {
                continue;
            }
            event.setReplannedInPeriod(lastAppliedMonth == event.getPrepaymentMonth());
            result = calculator(event.getNewType()).computePrepayment(result.getMonthLoanList(), event);
            lastAppliedMonth = event.getPrepaymentMonth();
        }
        return result;
    }

    private PrepaymentDTO copy(PrepaymentDTO source) {
        PrepaymentDTO copy = new PrepaymentDTO();
        copy.setPrepaymentDate(source.getPrepaymentDate());
        copy.setPrepaymentMonth(source.getPrepaymentMonth());
        copy.setRepayment(source.getRepayment());
        copy.setRepaymentType(source.getRepaymentType());
        copy.setNewRate(source.getNewRate());
        copy.setNewType(source.getNewType());
        copy.setLprRate(source.getLprRate());
        copy.setInterestSegments(source.getInterestSegments());
        copy.setPaymentDeferred(source.isPaymentDeferred());
        return copy;
    }

    private List<InterestSegment> withoutPrepaymentOffsets(List<InterestSegment> source) {
        if (source == null) {
            return null;
        }
        List<InterestSegment> segments = new ArrayList<>();
        for (InterestSegment input : source) {
            InterestSegment previous = segments.isEmpty() ? null : segments.get(segments.size() - 1);
            if (previous != null && previous.getAnnualRate().compareTo(input.getAnnualRate()) == 0) {
                previous.setDays(previous.getDays() + input.getDays());
            } else {
                InterestSegment segment = new InterestSegment();
                segment.setDays(input.getDays());
                segment.setAnnualRate(input.getAnnualRate());
                segments.add(segment);
            }
        }
        return segments;
    }
}
