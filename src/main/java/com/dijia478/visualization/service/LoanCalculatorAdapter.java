package com.dijia478.visualization.service;

import com.dijia478.visualization.bean.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** 两种还款方式共享逐期记账和结清规则，分以下应计息连续结转。 */
public abstract class LoanCalculatorAdapter implements LoanCalculator {

    protected static final MathContext PRECISION = MathContext.DECIMAL128;

    protected TotalLoan schedule(LoanBO data, BigDecimal fixedPayment, BigDecimal fixedPrincipal) {
        BigDecimal balance = money(data.getAmount());
        BigDecimal monthlyRate = monthRate(data.getRate());
        BigDecimal interestRemainder = data.getInterestRemainder() == null ? BigDecimal.ZERO : data.getInterestRemainder();
        int term = data.getMonth().intValueExact();
        List<MonthLoan> rows = new ArrayList<>();
        boolean shortened = fixedPayment != null || fixedPrincipal != null;
        BigDecimal payment = fixedPayment;
        BigDecimal principalPayment = fixedPrincipal;
        if (data.getType() == 1 && payment == null) {
            BigDecimal paymentBalance = balance;
            if (data.isAdjustPaymentForFirstInterest() && data.getFirstInterestSegments() != null) {
                BigDecimal extraInterest = rawInterest(balance, data.getRate(), data.getFirstInterestSegments())
                        .subtract(rawInterest(balance, data.getRate(), null));
                // 首期额外应计息的现值只影响月供计算基数，不增加账面本金或重复计息。
                paymentBalance = paymentBalance.add(extraInterest.divide(BigDecimal.ONE.add(monthlyRate), PRECISION));
            }
            payment = annuityPayment(paymentBalance, monthlyRate, term);
        } else if (data.getType() == 2 && principalPayment == null) {
            principalPayment = money(balance.divide(BigDecimal.valueOf(term), PRECISION));
        }
        // 缩期方案逐期偿还，避免double对数求期数后再次重算月供改变“月供不变”的约定。
        int limit = term;
        for (int i = 0; i < limit && balance.signum() > 0; i++) {
            if (i == 1 && data.getFirstRepayment() != null && data.getType() == 1 && !shortened) {
                payment = data.getSubsequentRepayment() == null ? annuityPayment(balance, monthlyRate, term - 1)
                        : data.getSubsequentRepayment();
            }
            List<InterestSegment> segments = i == 0 ? data.getFirstInterestSegments() : null;
            BigDecimal accrued = rawInterest(balance, data.getRate(), segments).add(interestRemainder);
            BigDecimal interest = accrued.setScale(2, RoundingMode.DOWN);
            BigDecimal previousRemainder = interestRemainder;
            interestRemainder = accrued.subtract(interest);
            BigDecimal currentPayment = i == 0 && data.getFirstRepayment() != null ? data.getFirstRepayment() : payment;
            BigDecimal principal = data.getType() == 1 ? currentPayment.subtract(interest) : principalPayment;
            if (principal.signum() <= 0) {
                throw new LoanException(ResultEnum.PAYMENT_TOO_SMALL);
            }
            if ((!shortened && i == term - 1) || principal.compareTo(balance) > 0) {
                principal = balance;
            }
            balance = balance.subtract(principal);
            MonthLoan row = new MonthLoan();
            row.setPrincipal(principal);
            row.setInterest(interest);
            row.setRepayment(principal.add(interest));
            row.setAnnualRate(data.getRate());
            row.setInterestRemainder(interestRemainder);
            row.setAdjustPaymentForFirstInterest(data.isAdjustPaymentForFirstInterest());
            if (segments != null && segments.size() > 1) {
                List<InterestSegment> details = new ArrayList<>();
                for (InterestSegment segment : segments) {
                    InterestSegment detail = new InterestSegment();
                    detail.setDays(segment.getDays());
                    detail.setAnnualRate(segment.getAnnualRate());
                    detail.setPrincipalOffset(segment.getPrincipalOffset());
                    BigDecimal segmentAccrued = rawSegmentInterest(balance.add(principal), segment).add(previousRemainder);
                    detail.setInterest(segmentAccrued.setScale(2, RoundingMode.DOWN));
                    previousRemainder = segmentAccrued.subtract(detail.getInterest());
                    details.add(detail);
                }
                row.setInterestSegments(details);
            }
            rows.add(row);
        }
        if (balance.signum() > 0) {
            throw new LoanException(ResultEnum.SHORTENING_UNAVAILABLE);
        }
        TotalLoan result = new TotalLoan();
        result.setLoanAmount(data.getAmount());
        result.setLoanRate(data.getRate());
        result.setType(data.getType());
        result.setMonthLoanList(rows);
        summarize(result);
        return result;
    }

    @Override
    public TotalLoan computePrepayment(List<MonthLoan> monthLoanList, PrepaymentDTO event) {
        int paid = event.getPrepaymentMonth() - 1;
        if (paid < 1 || paid > monthLoanList.size()) {
            throw new LoanException(ResultEnum.PREPAYMENT_MONTH_TOO_BIG);
        }
        MonthLoan previous = monthLoanList.get(paid - 1);
        BigDecimal extra = totalLoan(BigDecimal.valueOf(event.getRepayment()));
        BigDecimal remaining = previous.getRemainPrincipal().subtract(extra);
        if (remaining.signum() < 0) {
            throw new LoanException(ResultEnum.REPAYMENT_TOO_BIG);
        }
        List<MonthLoan> rows = new ArrayList<>(monthLoanList.subList(0, paid));
        BigDecimal originalAmount = monthLoanList.get(0).getPrincipal()
                .add(monthLoanList.get(0).getExtraRepayment()).add(monthLoanList.get(0).getRemainPrincipal());
        if (remaining.signum() > 0) {
            boolean shorten = event.getRepaymentType() == 1;
            BigDecimal subsequentPayment = null;
            int remainingTerm = monthLoanList.size() - paid;
            if (event.isPaymentDeferred() && event.getNewType() == 1 && !shorten && remainingTerm > 1) {
                // 调息日已出账的计划本金冻结；当期省下利息多还本金，不立即再次降低后续月供。
                BigDecimal plannedBalance = monthLoanList.get(paid).getRemainPrincipal().subtract(extra);
                subsequentPayment = annuityPayment(plannedBalance, monthRate(event.getNewRate()), remainingTerm - 1);
            }
            LoanBO next = LoanBO.builder().amount(remaining).month(BigDecimal.valueOf(monthLoanList.size() - paid))
                    .rate(event.getNewRate()).type(event.getNewType()).firstInterestSegments(event.getInterestSegments())
                    .firstRepayment(event.isPaymentDeferred() && event.getNewType() == 1 && !shorten
                            ? monthLoanList.get(paid).getRepayment() : null)
                    .subsequentRepayment(subsequentPayment).interestRemainder(previous.getInterestRemainder())
                    .adjustPaymentForFirstInterest(extra.signum() > 0
                            || monthLoanList.get(paid).isAdjustPaymentForFirstInterest()).build();
            // 同期前一笔已改月供时，缩期沿用刚重建的计划，不能退回上个还款日的旧月供。
            BigDecimal referencePayment = event.isReplannedInPeriod() || previous.getExtraRepayment().signum() > 0
                    ? monthLoanList.get(paid).getRepayment() : previous.getRepayment();
            BigDecimal payment = shorten && event.getNewType() == 1 ? referencePayment : null;
            BigDecimal principal = shorten && event.getNewType() == 2
                    ? referencePayment.subtract(rawInterest(remaining, event.getNewRate(), event.getInterestSegments())
                            .add(previous.getInterestRemainder()).setScale(2, RoundingMode.DOWN)) : null;
            rows.addAll(schedule(next, payment, principal).getMonthLoanList());
        }
        previous.setExtraRepayment(previous.getExtraRepayment().add(extra));
        TotalLoan result = new TotalLoan();
        result.setLoanAmount(originalAmount);
        result.setLoanRate(event.getNewRate());
        result.setType(event.getNewType());
        result.setMonthLoanList(rows);
        summarize(result);
        return result;
    }

    protected void summarize(TotalLoan result) {
        List<MonthLoan> rows = result.getMonthLoanList();
        BigDecimal totalInterest = BigDecimal.ZERO;
        for (MonthLoan row : rows) {
            totalInterest = totalInterest.add(row.getInterest());
        }
        BigDecimal totalRepayment = result.getLoanAmount().add(totalInterest);
        BigDecimal paidPrincipal = BigDecimal.ZERO;
        BigDecimal paidInterest = BigDecimal.ZERO;
        for (int i = 0; i < rows.size(); i++) {
            MonthLoan row = rows.get(i);
            paidPrincipal = paidPrincipal.add(row.getPrincipal()).add(row.getExtraRepayment());
            paidInterest = paidInterest.add(row.getInterest());
            BigDecimal paid = paidPrincipal.add(paidInterest);
            row.setMonth(i + 1);
            row.setYear(i / 12 + 1);
            row.setMonthInYear(i % 12 + 1);
            row.setRemainMonth(rows.size() - i - 1);
            row.setRemainPrincipal(result.getLoanAmount().subtract(paidPrincipal));
            row.setTotalPrincipal(paidPrincipal);
            row.setTotalInterest(paidInterest);
            row.setTotalRepayment(paid);
            row.setRemainInterest(totalInterest.subtract(paidInterest));
            row.setRemainTotal(totalRepayment.subtract(paid));
            row.setTotalRepaymentAndRemainPrincipal(paid.add(row.getRemainPrincipal()));
        }
        result.setLoanMonth(BigDecimal.valueOf(rows.size()));
        result.setTotalInterest(totalInterest);
        result.setTotalRepayment(totalRepayment);
    }

    private BigDecimal rawInterest(BigDecimal balance, BigDecimal rate, List<InterestSegment> segments) {
        if (segments == null) {
            return balance.multiply(monthRate(rate), PRECISION);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (InterestSegment segment : segments) {
            total = total.add(rawSegmentInterest(balance, segment));
        }
        return total;
    }

    private BigDecimal rawSegmentInterest(BigDecimal balance, InterestSegment segment) {
        return balance.add(segment.getPrincipalOffset()).multiply(segment.getAnnualRate(), PRECISION)
                .multiply(BigDecimal.valueOf(segment.getDays()), PRECISION).divide(new BigDecimal("36000"), PRECISION);
    }

    private BigDecimal annuityPayment(BigDecimal balance, BigDecimal monthlyRate, int term) {
        if (monthlyRate.signum() == 0) {
            return money(balance.divide(BigDecimal.valueOf(term), PRECISION));
        }
        BigDecimal factor = BigDecimal.ONE.add(monthlyRate).pow(term, PRECISION);
        return balance.multiply(monthlyRate, PRECISION).multiply(factor, PRECISION)
                .divide(factor.subtract(BigDecimal.ONE), PRECISION).setScale(2, RoundingMode.CEILING);
    }

    protected static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal monthRate(BigDecimal loanRate) {
        return loanRate.divide(new BigDecimal("1200"), PRECISION);
    }

    @Override
    public BigDecimal totalLoan(BigDecimal loanAmount) {
        return loanAmount.movePointRight(4);
    }

    @Override
    public BigDecimal totalMonth(BigDecimal year) {
        return year.multiply(new BigDecimal("12"));
    }
}
