package com.dijia478.visualization.service;

import com.dijia478.visualization.bean.*;
import com.dijia478.visualization.controller.LoanController;
import com.dijia478.visualization.service.impl.EqualPrincipalCalculator;
import com.dijia478.visualization.service.impl.EqualRepaymentCalculator;
import com.dijia478.visualization.service.impl.PrepaymentCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class MortgagePrepaymentEdgeTest {
    private LoanController controller;

    @BeforeEach
    void setup() {
        PrepaymentCalculator calculator = new PrepaymentCalculator();
        ReflectionTestUtils.setField(calculator, "equalRepaymentCalculator", new EqualRepaymentCalculator());
        ReflectionTestUtils.setField(calculator, "equalPrincipalCalculator", new EqualPrincipalCalculator());
        controller = new LoanController();
        ReflectionTestUtils.setField(controller, "prepaymentCalculator", calculator);
    }

    @Test
    void largePartialPrepaymentCanLowerPaymentWithoutExtendingTermOrLosingAccruedInterest() {
        StockLoanDTO input = input();
        TotalLoan original = controller.prepaymentCalculator(input);
        input.setPrepaymentList(Arrays.asList(event("2024-02-10", 40, 1, 2)));
        TotalLoan result = controller.prepaymentCalculator(input);
        assertEquals(360, result.getMonthLoanList().size());
        MonthLoan first = result.getMonthLoanList().get(0);
        assertEquals(original.getMonthLoanList().get(0).getRepayment(), first.getRepayment());
        assertEquals(original.getMonthLoanList().get(0).getInterest(), first.getInterest());
        MonthLoan irregular = result.getMonthLoanList().get(1);
        // 首期按旧本金25天、新本金5天计息，不能因降月供丢掉已发生的利息。
        BigDecimal balance = original.getMonthLoanList().get(0).getRemainPrincipal();
        BigDecimal expectedInterest = balance.multiply(new BigDecimal("3.5")).multiply(new BigDecimal("25"))
                .add(balance.subtract(new BigDecimal("400000")).multiply(new BigDecimal("3.5")).multiply(new BigDecimal("5")))
                .divide(new BigDecimal("36000"), 2, java.math.RoundingMode.DOWN);
        assertEquals(0, expectedInterest.compareTo(irregular.getInterest()));
        assertTrue(irregular.getPrincipal().signum() > 0);
        assertTrue(irregular.getRepayment().compareTo(first.getRepayment()) < 0);
        BigDecimal normalPayment = result.getMonthLoanList().get(2).getRepayment();
        assertTrue(normalPayment.compareTo(irregular.getRepayment()) < 0);
        for (int i = 2; i < 359; i++) {
            assertEquals(0, normalPayment.compareTo(result.getMonthLoanList().get(i).getRepayment()));
        }
        reconciles(result);
    }

    @Test
    void nearFullPartialPrepaymentStillKeepsPositivePrincipalAndOriginalMaturity() {
        StockLoanDTO input = input();
        input.setPrepaymentList(Arrays.asList(event("2024-01-20", 99, 1, 2)));
        TotalLoan result = controller.prepaymentCalculator(input);
        assertEquals(360, result.getMonthLoanList().size());
        assertTrue(result.getMonthLoanList().get(1).getPrincipal().signum() > 0);
        reconciles(result);
    }

    @Test
    void unchangedZeroAmountShorteningDoesNotRebuildTheScheduleOrLoseTheFinalCents() {
        for (int type : new int[]{1, 2}) {
            StockLoanDTO input = input();
            input.setAmount(1);
            input.setYear(1);
            input.setRate(0.0);
            input.setType(type);
            TotalLoan original = controller.prepaymentCalculator(input);
            input.setPrepaymentList(Arrays.asList(event("2024-01-15", 0, type, 1)));
            TotalLoan unchanged = controller.prepaymentCalculator(input);
            assertEquals(original.getMonthLoanList(), unchanged.getMonthLoanList());
            assertEquals(original.getTotalRepayment(), unchanged.getTotalRepayment());
            reconciles(unchanged);
        }
    }

    @Test
    void twoLargePrepaymentsThenShorteningKeepThePlannedNormalPaymentInsteadOfTheIrregularBill() {
        StockLoanDTO input = input();
        PrepaymentDTO first = event("2024-02-01", 40, 1, 2);
        PrepaymentDTO second = event("2024-02-10", 20, 1, 2);
        input.setPrepaymentList(Arrays.asList(first, second));
        TotalLoan lowerPayment = controller.prepaymentCalculator(input);
        assertEquals(360, lowerPayment.getMonthLoanList().size());
        assertEquals(0, new BigDecimal("600000").compareTo(lowerPayment.getMonthLoanList().get(0).getExtraRepayment()));
        reconciles(lowerPayment);

        BigDecimal plannedNormalPayment = lowerPayment.getMonthLoanList().get(2).getRepayment();
        assertTrue(lowerPayment.getMonthLoanList().get(1).getRepayment().compareTo(plannedNormalPayment) > 0);
        input.setPrepaymentList(Arrays.asList(first, second, event("2024-03-10", 5, 1, 1)));
        TotalLoan shorter = controller.prepaymentCalculator(input);
        assertTrue(shorter.getMonthLoanList().size() < 360);
        assertEquals(lowerPayment.getMonthLoanList().get(1).getRepayment(), shorter.getMonthLoanList().get(1).getRepayment());
        assertEquals(plannedNormalPayment, shorter.getMonthLoanList().get(2).getRepayment());
        reconciles(shorter);
    }

    @Test
    void shorteningAgainInTheSamePeriodAlsoUsesTheNormalPaymentAfterTheIrregularBill() {
        StockLoanDTO input = input();
        PrepaymentDTO first = event("2024-02-01", 40, 1, 2);
        PrepaymentDTO second = event("2024-02-10", 20, 1, 2);
        input.setPrepaymentList(Arrays.asList(first, second));
        TotalLoan lowerPayment = controller.prepaymentCalculator(input);
        BigDecimal plannedNormalPayment = lowerPayment.getMonthLoanList().get(2).getRepayment();
        input.setPrepaymentList(Arrays.asList(first, second, event("2024-02-12", 5, 1, 1)));
        TotalLoan shorter = controller.prepaymentCalculator(input);
        assertTrue(shorter.getMonthLoanList().size() < 360);
        assertEquals(plannedNormalPayment, shorter.getMonthLoanList().get(2).getRepayment());
        reconciles(shorter);
    }

    @Test
    void zeroAmountEventsStillApplyRealRateAndRepaymentMethodChanges() {
        for (int type : new int[]{1, 2}) {
            StockLoanDTO input = input();
            input.setType(type);
            PrepaymentDTO changedRate = event("2024-02-10", 0, type, 2);
            changedRate.setNewRate(new BigDecimal("4.2"));
            input.setPrepaymentList(Arrays.asList(changedRate));
            TotalLoan repriced = controller.prepaymentCalculator(input);
            assertEquals(0, new BigDecimal("4.2").compareTo(repriced.getMonthLoanList().get(1).getAnnualRate()));
            reconciles(repriced);
        }
        StockLoanDTO input = input();
        input.setPrepaymentList(Arrays.asList(event("2024-02-10", 0, 2, 2)));
        TotalLoan switched = controller.prepaymentCalculator(input);
        BigDecimal principal = switched.getMonthLoanList().get(1).getPrincipal();
        assertEquals(principal, switched.getMonthLoanList().get(2).getPrincipal());
        assertTrue(switched.getMonthLoanList().get(1).getRepayment().compareTo(switched.getMonthLoanList().get(2).getRepayment()) > 0);
        reconciles(switched);
    }

    private StockLoanDTO input() {
        StockLoanDTO input = new StockLoanDTO();
        input.setAmount(100);
        input.setYear(30);
        input.setRate(3.5);
        input.setType(1);
        input.setFirstPaymentDate("2024-01-15");
        input.setRateAdjustmentDay(1);
        input.setRateMode("FIXED");
        input.setFirstHouse(1);
        return input;
    }

    private PrepaymentDTO event(String date, int amount, int type, int scheme) {
        PrepaymentDTO event = new PrepaymentDTO();
        event.setPrepaymentDate(date);
        event.setRepayment(BigDecimal.valueOf(amount));
        event.setNewType(type);
        event.setRepaymentType(scheme);
        return event;
    }

    private void reconciles(TotalLoan result) {
        BigDecimal principal = BigDecimal.ZERO, interest = BigDecimal.ZERO, paid = BigDecimal.ZERO;
        for (MonthLoan row : result.getMonthLoanList()) {
            assertTrue(row.getPrincipal().signum() > 0);
            assertEquals(0, row.getPrincipal().add(row.getInterest()).compareTo(row.getRepayment()));
            principal = principal.add(row.getPrincipal()).add(row.getExtraRepayment());
            interest = interest.add(row.getInterest());
            paid = paid.add(row.getRepayment()).add(row.getExtraRepayment());
            assertEquals(0, result.getLoanAmount().subtract(principal).compareTo(row.getRemainPrincipal()));
            assertTrue(row.getRemainPrincipal().signum() >= 0);
        }
        assertEquals(0, principal.compareTo(result.getLoanAmount()));
        assertEquals(0, interest.compareTo(result.getTotalInterest()));
        assertEquals(0, paid.compareTo(result.getTotalRepayment()));
    }
}
