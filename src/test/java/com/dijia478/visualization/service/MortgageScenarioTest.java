package com.dijia478.visualization.service;

import com.dijia478.visualization.bean.MonthLoan;
import com.dijia478.visualization.bean.PrepaymentDTO;
import com.dijia478.visualization.bean.StockLoanDTO;
import com.dijia478.visualization.bean.TotalLoan;
import com.dijia478.visualization.controller.LoanController;
import com.dijia478.visualization.service.impl.EqualPrincipalCalculator;
import com.dijia478.visualization.service.impl.EqualRepaymentCalculator;
import com.dijia478.visualization.service.impl.PrepaymentCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MortgageScenarioTest {
    private LoanController controller;

    @BeforeEach
    void setup() {
        PrepaymentCalculator prepayment = new PrepaymentCalculator();
        ReflectionTestUtils.setField(prepayment, "equalRepaymentCalculator", new EqualRepaymentCalculator());
        ReflectionTestUtils.setField(prepayment, "equalPrincipalCalculator", new EqualPrincipalCalculator());
        controller = new LoanController();
        ReflectionTestUtils.setField(controller, "prepaymentCalculator", prepayment);
    }

    @TestFactory
    Collection<DynamicTest> differentLoansAndRepeatedPrepaymentsKeepAccountingAndStrategy() {
        List<DynamicTest> tests = new ArrayList<>();
        for (int scenario = 0; scenario < 4; scenario++) {
            for (int type = 1; type <= 2; type++) {
                for (int strategy = 1; strategy <= 2; strategy++) {
                    final int loan = scenario, repaymentType = type, change = strategy;
                    tests.add(DynamicTest.dynamicTest("loan=" + loan + " type=" + type + " strategy=" + strategy, () -> {
                        StockLoanDTO input = input(loan, repaymentType);
                        TotalLoan baseline = controller.prepaymentCalculator(input);
                        LocalDate first = LocalDate.parse(input.getFirstPaymentDate());
                        input.setPrepaymentList(Arrays.asList(
                                event(first.plusMonths(14).minusDays(7), 2, repaymentType, change),
                                event(first.plusMonths(19).minusDays(3), 3, repaymentType, change),
                                event(first.plusMonths(26).minusDays(11), 1, repaymentType, change)));
                        TotalLoan result = controller.prepaymentCalculator(input);
                        reconciles(result, first);
                        equal(BigDecimal.valueOf(60000), result.getMonthLoanList().stream()
                                .map(MonthLoan::getExtraRepayment).reduce(BigDecimal.ZERO, BigDecimal::add));
                        assertEquals(3, result.getAppliedPrepayments().size());
                        assertTrue(result.getTotalInterest().compareTo(baseline.getTotalInterest()) <= 0);
                        for (int i = 0; i < 12; i++) {
                            samePaymentAndInterest(baseline.getMonthLoanList().get(i), result.getMonthLoanList().get(i));
                        }
                        if (change == 2) {
                            assertEquals(input.getYear() * 12, result.getMonthLoanList().size());
                        } else {
                            assertTrue(result.getMonthLoanList().size() < baseline.getMonthLoanList().size());
                            if (repaymentType == 1 && "FIXED".equals(input.getRateMode())) {
                                BigDecimal originalPayment = baseline.getMonthLoanList().get(0).getRepayment();
                                for (int i = 0; i < result.getMonthLoanList().size() - 1; i++) {
                                    equal(originalPayment, result.getMonthLoanList().get(i).getRepayment());
                                }
                            }
                        }
                    }));
                }
            }
        }
        return tests;
    }

    @TestFactory
    Collection<DynamicTest> sameMonthUnorderedAndSameDayPrepaymentsPreserveAllMoney() {
        List<DynamicTest> tests = new ArrayList<>();
        for (int type = 1; type <= 2; type++) {
            for (int strategy = 1; strategy <= 2; strategy++) {
                final int repaymentType = type, change = strategy;
                tests.add(DynamicTest.dynamicTest("same-month type=" + type + " strategy=" + strategy, () -> {
                    StockLoanDTO input = input(1, repaymentType);
                    LocalDate first = LocalDate.parse(input.getFirstPaymentDate());
                    LocalDate due = first.plusMonths(16);
                    List<PrepaymentDTO> ordered = Arrays.asList(
                            event(due.minusDays(18), 2, repaymentType, change),
                            event(due.minusDays(8), 3, repaymentType, change),
                            event(due.minusDays(8), 1, repaymentType, change));
                    input.setPrepaymentList(ordered);
                    TotalLoan chronological = controller.prepaymentCalculator(input);
                    List<PrepaymentDTO> reversed = new ArrayList<>(ordered);
                    Collections.reverse(reversed);
                    input.setPrepaymentList(reversed);
                    TotalLoan unordered = controller.prepaymentCalculator(input);
                    assertSameSchedule(chronological, unordered);
                    input.setPrepaymentList(Arrays.asList(
                            event(due.minusDays(18), 2, repaymentType, change),
                            event(due.minusDays(8), 4, repaymentType, change)));
                    TotalLoan merged = controller.prepaymentCalculator(input);
                    assertSameSchedule(chronological, merged);
                    reconciles(chronological, first);
                    equal(BigDecimal.valueOf(60000), chronological.getMonthLoanList().stream()
                            .map(MonthLoan::getExtraRepayment).reduce(BigDecimal.ZERO, BigDecimal::add));
                }));
            }
        }
        return tests;
    }

    @Test
    void reducingPaymentAfterAnEarlierSameMonthShorteningKeepsTheShortenedMaturity() {
        StockLoanDTO input = input(1, 1);
        input.setAmount(100);
        input.setYear(30);
        input.setRate(3.5);
        input.setFirstPaymentDate("2024-01-15");
        input.setPrepaymentList(Arrays.asList(event(LocalDate.parse("2025-01-16"), 10, 1, 1)));
        TotalLoan shortened = controller.prepaymentCalculator(input);
        input.setPrepaymentList(Arrays.asList(
                event(LocalDate.parse("2025-01-16"), 10, 1, 1),
                event(LocalDate.parse("2025-01-17"), 10, 1, 2)));
        TotalLoan lowerPayment = controller.prepaymentCalculator(input);
        assertEquals(shortened.getMonthLoanList().size(), lowerPayment.getMonthLoanList().size(),
                "减少月供应保持已缩短的期限，不能恢复原始合同期限");
        assertTrue(lowerPayment.getMonthLoanList().get(13).getRepayment()
                .compareTo(shortened.getMonthLoanList().get(13).getRepayment()) < 0);
        reconciles(lowerPayment, LocalDate.parse(input.getFirstPaymentDate()));
    }

    @Test
    void shorteningAfterAnEarlierSameMonthReductionKeepsTheNewPayment() {
        StockLoanDTO input = input(1, 1);
        LocalDate first = LocalDate.parse(input.getFirstPaymentDate());
        LocalDate due = first.plusMonths(17);
        input.setPrepaymentList(Arrays.asList(event(due.minusDays(16), 10, 1, 2)));
        TotalLoan reduced = controller.prepaymentCalculator(input);
        input.setPrepaymentList(Arrays.asList(
                event(due.minusDays(16), 10, 1, 2),
                event(due.minusDays(4), 10, 1, 1)));
        TotalLoan shortened = controller.prepaymentCalculator(input);
        equal(reduced.getMonthLoanList().get(17).getRepayment(), shortened.getMonthLoanList().get(17).getRepayment());
        assertTrue(shortened.getMonthLoanList().size() < reduced.getMonthLoanList().size());
        reconciles(shortened, first);
    }

    @Test
    void sameMonthStrategyChangesKeepTypeSwitchesAndHistoricalPolicy() {
        StockLoanDTO input = input(3, 1);
        input.setAmount(137);
        input.setYear(25);
        input.setFirstPaymentDate("2022-10-31");
        input.setPrepaymentList(Arrays.asList(
                event(LocalDate.parse("2023-09-26"), 4, 2, 2),
                event(LocalDate.parse("2023-09-28"), 3, 1, 1)));
        TotalLoan result = controller.prepaymentCalculator(input);
        reconciles(result, LocalDate.parse(input.getFirstPaymentDate()));
        assertTrue(result.getRateChanges().stream()
                .anyMatch(change -> "2023-09-25".equals(change.getEffectiveDate())));
        equal(BigDecimal.valueOf(70000), result.getMonthLoanList().stream()
                .map(MonthLoan::getExtraRepayment).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(2, result.getAppliedPrepayments().size());
    }

    @Test
    void shorteningAfterSameMonthPolicyChangeKeepsTheUpdatedPayment() {
        StockLoanDTO input = input(3, 1);
        input.setAmount(137);
        input.setYear(25);
        input.setFirstPaymentDate("2022-10-27");
        TotalLoan repriced = controller.prepaymentCalculator(input);
        input.setPrepaymentList(Arrays.asList(event(LocalDate.parse("2024-10-26"), 4, 1, 1)));
        TotalLoan shortened = controller.prepaymentCalculator(input);
        MonthLoan policyMonth = repriced.getMonthLoanList().stream()
                .filter(row -> "2024-10-27".equals(row.getDateFormat())).findFirst().get();
        MonthLoan prepaymentMonth = shortened.getMonthLoanList().stream()
                .filter(row -> "2024-10-27".equals(row.getDateFormat())).findFirst().get();
        equal(policyMonth.getRepayment(), prepaymentMonth.getRepayment());
        reconciles(shortened, LocalDate.parse(input.getFirstPaymentDate()));
    }

    private StockLoanDTO input(int scenario, int type) {
        int[] amounts = {37, 173, 64, 118};
        int[] years = {8, 23, 12, 19};
        double[] rates = {0.0, 4.91, 2.15, 5.05};
        String[] firstDates = {"2030-02-28", "2018-12-31", "2027-01-31", "2022-03-31"};
        StockLoanDTO input = new StockLoanDTO();
        input.setAmount(amounts[scenario]);
        input.setYear(years[scenario]);
        input.setRate(rates[scenario]);
        input.setType(type);
        input.setFirstPaymentDate(firstDates[scenario]);
        input.setRateAdjustmentDay(1);
        input.setRateMode(scenario == 3 ? "LPR" : "FIXED");
        input.setFirstHouse(1);
        input.setApplyHistoricalRateAdjustments(scenario == 3);
        return input;
    }

    private PrepaymentDTO event(LocalDate date, int amount, int type, int strategy) {
        PrepaymentDTO event = new PrepaymentDTO();
        event.setPrepaymentDate(date.toString());
        event.setRepayment(BigDecimal.valueOf(amount));
        event.setNewType(type);
        event.setRepaymentType(strategy);
        return event;
    }

    private void assertSameSchedule(TotalLoan expected, TotalLoan actual) {
        assertEquals(expected.getMonthLoanList().size(), actual.getMonthLoanList().size());
        equal(expected.getTotalInterest(), actual.getTotalInterest());
        for (int i = 0; i < expected.getMonthLoanList().size(); i++) {
            MonthLoan left = expected.getMonthLoanList().get(i), right = actual.getMonthLoanList().get(i);
            samePaymentAndInterest(left, right);
            equal(left.getRemainPrincipal(), right.getRemainPrincipal());
            equal(left.getExtraRepayment(), right.getExtraRepayment());
        }
    }

    private void samePaymentAndInterest(MonthLoan expected, MonthLoan actual) {
        equal(expected.getRepayment(), actual.getRepayment());
        equal(expected.getInterest(), actual.getInterest());
    }

    private void reconciles(TotalLoan result, LocalDate first) {
        BigDecimal principal = BigDecimal.ZERO, interest = BigDecimal.ZERO, payment = BigDecimal.ZERO;
        for (int i = 0; i < result.getMonthLoanList().size(); i++) {
            MonthLoan row = result.getMonthLoanList().get(i);
            assertEquals(first.plusMonths(i).toString(), row.getDateFormat());
            assertTrue(row.getPrincipal().signum() > 0);
            assertTrue(row.getInterest().signum() >= 0);
            assertTrue(row.getRemainPrincipal().signum() >= 0);
            assertTrue(row.getInterestRemainder().signum() >= 0);
            assertTrue(row.getInterestRemainder().compareTo(new BigDecimal("0.01")) < 0);
            equal(row.getRepayment(), row.getPrincipal().add(row.getInterest()));
            principal = principal.add(row.getPrincipal()).add(row.getExtraRepayment());
            interest = interest.add(row.getInterest());
            payment = payment.add(row.getRepayment()).add(row.getExtraRepayment());
            equal(result.getLoanAmount(), principal.add(row.getRemainPrincipal()));
            equal(result.getTotalRepayment(), payment.add(row.getRemainTotal()));
            if (row.getInterestSegments() != null) {
                equal(row.getInterest(), row.getInterestSegments().stream()
                        .map(segment -> segment.getInterest()).reduce(BigDecimal.ZERO, BigDecimal::add));
            }
        }
        equal(result.getLoanAmount(), principal);
        equal(result.getTotalInterest(), interest);
        equal(result.getTotalRepayment(), payment);
        equal(BigDecimal.ZERO, result.getMonthLoanList().get(result.getMonthLoanList().size() - 1).getRemainPrincipal());
    }

    private void equal(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), "expected=" + expected + " actual=" + actual);
    }
}
