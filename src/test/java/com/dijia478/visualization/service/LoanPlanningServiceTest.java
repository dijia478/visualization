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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoanPlanningServiceTest {
    private LoanController calculator;
    private LoanPlanningService planning;

    @BeforeEach
    void setup() {
        PrepaymentCalculator prepayment = new PrepaymentCalculator();
        ReflectionTestUtils.setField(prepayment, "equalRepaymentCalculator", new EqualRepaymentCalculator());
        ReflectionTestUtils.setField(prepayment, "equalPrincipalCalculator", new EqualPrincipalCalculator());
        calculator = new LoanController();
        ReflectionTestUtils.setField(calculator, "prepaymentCalculator", prepayment);
        planning = new LoanPlanningService();
    }

    @Test
    void comparisonPreservesHistoryAndRateChangesWithoutMutatingTheInput() {
        LoanPlanningRequest request = request(1, 3.5);
        PrepaymentDTO past = event("2024-08-04", "10.123456", 1, 2, null);
        PrepaymentDTO future = event("2025-08-19", "15", 2, 2, "3.2");
        request.getLoan().setPrepaymentList(new ArrayList<>(Arrays.asList(future, past)));
        LoanPlanningResult.Comparison comparison = planning.compare(request, calculator::prepaymentCalculator);
        assertEquals(3, comparison.getScenarios().size());
        LoanPlanningResult.Scenario baseline = comparison.getScenarios().get(0);
        StockLoanDTO expected = loan(1, 3.5);
        expected.setPrepaymentList(Arrays.asList(past, event("2025-08-19", "0", 2, 2, "3.2")));
        TotalLoan direct = calculator.prepaymentCalculator(expected);
        equal(direct.getTotalInterest(), baseline.getPlan().getTotalInterest());
        assertEquals("2025-08-19", baseline.getPlan().getRateChanges().get(0).getEffectiveDate());
        equal(new BigDecimal("15"), future.getRepayment());
        assertSame(future, request.getLoan().getPrepaymentList().get(0));
        equal(new BigDecimal("10.123456"), past.getRepayment());
        for (LoanPlanningResult.Scenario scenario : comparison.getScenarios()) {
            if (!scenario.isAvailable()) continue;
            assertEquals(0, scenario.getPlan().getMonthLoanList().get(6).getRepayment()
                    .compareTo(direct.getMonthLoanList().get(6).getRepayment()));
            reconciles(scenario.getPlan());
        }
    }

    @Test
    void historicalCcbLprBillsAreIdenticalAcrossAllNewFutureStrategies() {
        LoanPlanningRequest request = request(1, 5.63);
        StockLoanDTO loan = request.getLoan();
        loan.setAmount(90);
        loan.setFirstPaymentDate("2021-08-27");
        loan.setRateMode("LPR");
        loan.setFirstHouse(1);
        loan.setApplyHistoricalRateAdjustments(true);
        loan.setPrepaymentList(Arrays.asList(event("2025-08-04", "10", 1, 2, null),
                event("2026-08-19", "15", 1, 2, null)));
        request.setComparisonDate("2026-10-22");
        request.setPrepaymentDate("2026-11-10");
        TotalLoan direct = calculator.prepaymentCalculator(loan);
        LoanPlanningResult.Comparison result = planning.compare(request, calculator::prepaymentCalculator);
        for (LoanPlanningResult.Scenario scenario : result.getScenarios()) {
            assertTrue(scenario.isAvailable(), scenario.getMessage());
            for (int i = 0; i < direct.getMonthLoanList().size(); i++) {
                MonthLoan expected = direct.getMonthLoanList().get(i);
                if (expected.getDateFormat().compareTo(request.getComparisonDate()) > 0) break;
                MonthLoan actual = scenario.getPlan().getMonthLoanList().get(i);
                equal(expected.getRepayment(), actual.getRepayment());
                equal(expected.getPrincipal(), actual.getPrincipal());
                equal(expected.getInterest(), actual.getInterest());
            }
            reconciles(scenario.getPlan());
        }
        equal(direct.getTotalInterest(), result.getScenarios().get(0).getPlan().getTotalInterest());
    }

    @Test
    void futureZeroAmountRateChangesKeepTheirStrategyAndAdoptingAProposalReproducesItsPlan() {
        LoanPlanningRequest request = request(1, 3.5);
        PrepaymentDTO futurePositive = event("2025-08-19", "15", 1, 1, "3.2");
        PrepaymentDTO zeroRateChange = event("2026-02-10", "0", 1, 1, "3.0");
        request.getLoan().setPrepaymentList(Arrays.asList(futurePositive, zeroRateChange));
        LoanPlanningResult.Scenario result = planning.compare(request, calculator::prepaymentCalculator)
                .getScenarios().get(1);
        assertTrue(result.isAvailable());
        StockLoanDTO adopted = loan(1, 3.5);
        adopted.setPrepaymentList(Arrays.asList(event("2025-08-19", "0", 1, 2, "3.2"),
                zeroRateChange, result.getProposal()));
        TotalLoan actual = calculator.prepaymentCalculator(adopted);
        equal(result.getPlan().getTotalInterest(), actual.getTotalInterest());
        assertEquals(result.getPlan().getMonthLoanList().size(), actual.getMonthLoanList().size());
        for (int i = 0; i < actual.getMonthLoanList().size(); i++) {
            equal(result.getPlan().getMonthLoanList().get(i).getRepayment(), actual.getMonthLoanList().get(i).getRepayment());
        }
        equal(new BigDecimal("15"), futurePositive.getRepayment());
        assertEquals(1, zeroRateChange.getRepaymentType());
    }

    @Test
    void summaryCountsFutureCashFlowsByActualDatesIncludingNewPrepayment() {
        LoanPlanningRequest request = request(2, 0);
        request.getLoan().setAmount(12);
        request.getLoan().setYear(1);
        request.setComparisonDate("2024-01-20");
        request.setPrepaymentDate("2024-02-10");
        request.setRepayment(new BigDecimal("2"));
        LoanPlanningResult.Comparison result = planning.compare(request, calculator::prepaymentCalculator);
        LoanPlanningResult.Scenario baseline = result.getScenarios().get(0);
        LoanPlanningResult.Scenario reduced = result.getScenarios().get(1);
        equal(new BigDecimal("110000"), baseline.getNextYearPayment());
        equal(new BigDecimal("110000"), reduced.getNextYearPayment());
        equal(BigDecimal.ZERO, reduced.getFutureInterest());
        assertEquals("2024-02-15", reduced.getNextPaymentDate());
        assertEquals(11, reduced.getRemainingMonths());
        assertEquals("2024-12-15", reduced.getPayoffDate());
        assertTrue(result.getScenarios().get(2).getMonthsSaved() > 0);
    }

    @Test
    void solvePaymentReturnsCentPreciseFeasibleAmountForBothMethodsAndZeroInterest() {
        for (int type = 1; type <= 2; type++) {
            for (double rate : new double[]{0, 3.5}) {
                LoanPlanningRequest request = request(type, rate);
                request.setGoal("PAYMENT");
                request.setTargetPayment(new BigDecimal(rate == 0 ? "2000.00" : "3500.00"));
                LoanPlanningResult.Solution result = planning.solve(request, calculator::prepaymentCalculator);
                assertTrue(result.isAvailable(), result.getMessage());
                assertTrue(result.getIterations() <= 65);
                assertTrue(result.getRepayment().scale() <= 6);
                assertTrue(result.getScenario().getNextMonthlyPayment().compareTo(request.getTargetPayment()) <= 0);
                assertEquals("2025-02-15", result.getScenario().getNextPaymentDate());
                request.setRepayment(result.getRepayment().subtract(new BigDecimal("0.000001")));
                LoanPlanningResult.Scenario oneCentLess = planning.compare(request, calculator::prepaymentCalculator)
                        .getScenarios().get(1);
                assertTrue(!oneCentLess.isAvailable() || oneCentLess.getNextMonthlyPayment().compareTo(request.getTargetPayment()) > 0,
                        "相邻少一分的金额未达到该目标");
                reconciles(result.getScenario().getPlan());
            }
        }
    }

    @Test
    void solvePayoffPreservesHistoricalPrepaymentsAndReturnsACentPreciseAmount() {
        for (int type = 1; type <= 2; type++) {
            LoanPlanningRequest request = request(type, 3.5);
            request.getLoan().setPrepaymentList(Collections.singletonList(event("2024-08-04", "10", type, 2, null)));
            request.setGoal("PAYOFF");
            request.setTargetPayoffDate("2035-12-31");
            LoanPlanningResult.Solution result = planning.solve(request, calculator::prepaymentCalculator);
            assertTrue(result.isAvailable(), result.getMessage());
            assertTrue(result.getScenario().getPayoffDate().compareTo(request.getTargetPayoffDate()) <= 0);
            assertTrue(result.getIterations() <= 65);
            request.setRepayment(result.getRepayment().subtract(new BigDecimal("0.000001")));
            LoanPlanningResult.Scenario prior = planning.compare(request, calculator::prepaymentCalculator)
                    .getScenarios().get(2);
            assertTrue(!prior.isAvailable() || prior.getPayoffDate().compareTo(request.getTargetPayoffDate()) > 0);
            assertEquals(new BigDecimal("10"), request.getLoan().getPrepaymentList().get(0).getRepayment());
        }
    }

    @Test
    void paymentSolverSearchesBothSidesOfTheIrregularFirstBillJump() {
        LoanPlanningRequest request = request(1, 3.5);
        request.setComparisonDate("2024-01-20");
        request.setPrepaymentDate("2024-02-10");
        request.setGoal("PAYMENT");
        request.setTargetPayment(new BigDecimal("3000"));
        request.setRepayment(new BigDecimal("34"));
        LoanPlanningResult.Scenario knownFeasible = planning.compare(request, calculator::prepaymentCalculator)
                .getScenarios().get(1);
        assertTrue(knownFeasible.getNextMonthlyPayment().compareTo(request.getTargetPayment()) <= 0);
        LoanPlanningResult.Solution result = planning.solve(request, calculator::prepaymentCalculator);
        assertTrue(result.getRepayment().compareTo(request.getRepayment()) <= 0,
                "不能跨过首期跳升前已经可行的较小金额: " + result.getRepayment());
        request.setRepayment(result.getRepayment().subtract(new BigDecimal("0.000001")));
        LoanPlanningResult.Scenario previous = planning.compare(request, calculator::prepaymentCalculator)
                .getScenarios().get(1);
        assertTrue(previous.getNextMonthlyPayment().compareTo(request.getTargetPayment()) > 0);
    }

    @Test
    void paymentTargetBeyondTheFirstBranchRangeIsFoundInTheSeparateInterestBranch() {
        LoanPlanningRequest request = request(1, 3.5);
        request.setComparisonDate("2024-01-20");
        request.setPrepaymentDate("2024-02-10");
        request.setGoal("PAYMENT");
        request.setTargetPayment(new BigDecimal("2600"));
        LoanPlanningResult.Solution result = planning.solve(request, calculator::prepaymentCalculator);
        assertTrue(result.isAvailable());
        assertTrue(result.getRepayment().compareTo(new BigDecimal("40")) > 0);
        assertTrue(result.getRepayment().compareTo(new BigDecimal("95")) < 0);
        assertTrue(result.getScenario().getNextMonthlyPayment().compareTo(request.getTargetPayment()) <= 0);
        request.setRepayment(result.getRepayment().subtract(new BigDecimal("0.000001")));
        LoanPlanningResult.Scenario previous = planning.compare(request, calculator::prepaymentCalculator)
                .getScenarios().get(1);
        assertTrue(previous.getNextMonthlyPayment().compareTo(request.getTargetPayment()) > 0);
        assertTrue(result.getIterations() <= 65);
    }

    @Test
    void firstBillBranchDetectionUsesTheActualPlanAfterAFutureRepaymentMethodChange() {
        LoanPlanningRequest request = request(2, 3.5);
        request.setComparisonDate("2024-01-20");
        request.setPrepaymentDate("2024-02-10");
        request.getLoan().setPrepaymentList(Collections.singletonList(event("2024-02-12", "0", 1, 2, "3.5")));
        request.setGoal("PAYMENT");
        request.setTargetPayment(new BigDecimal("3000"));
        request.setRepayment(new BigDecimal("34"));
        LoanPlanningResult.Scenario knownFeasible = planning.compare(request, calculator::prepaymentCalculator)
                .getScenarios().get(1);
        assertTrue(knownFeasible.getNextMonthlyPayment().compareTo(request.getTargetPayment()) <= 0);
        LoanPlanningResult.Solution result = planning.solve(request, calculator::prepaymentCalculator);
        assertTrue(result.getRepayment().compareTo(request.getRepayment()) <= 0,
                "后续已经切换等额本息，不能用初始方式跳过分段搜索: " + result.getRepayment());
        assertTrue(result.getScenario().getNextMonthlyPayment().compareTo(request.getTargetPayment()) <= 0);
    }

    @Test
    void roundingIslandsDoNotInvalidateTheSuggestedAmountOrChangeTheUnderlyingBills() {
        LoanPlanningRequest request = request(1, 3.5);
        request.setComparisonDate("2024-01-20");
        request.setPrepaymentDate("2024-02-10");
        request.setGoal("PAYMENT");
        request.setTargetPayment(new BigDecimal("2600"));
        // 与已知可达的邻近舍入岛比较，只验证建议量接近且真实达标，不承诺全局最小。
        request.setRepayment(new BigDecimal("91.459566"));
        LoanPlanningResult.Scenario knownFeasible = planning.compare(request, calculator::prepaymentCalculator)
                .getScenarios().get(1);
        assertTrue(knownFeasible.getNextMonthlyPayment().compareTo(request.getTargetPayment()) <= 0);
        LoanPlanningResult.Solution result = planning.solve(request, calculator::prepaymentCalculator);
        assertTrue(result.isAvailable());
        assertTrue(result.getScenario().getNextMonthlyPayment().compareTo(request.getTargetPayment()) <= 0);
        assertTrue(result.getRepayment().subtract(request.getRepayment()).compareTo(new BigDecimal("0.000223")) <= 0);
        assertFalse(result.getMessage().contains("最小"));
        StockLoanDTO adopted = loan(1, 3.5);
        adopted.setPrepaymentList(Collections.singletonList(result.getScenario().getProposal()));
        TotalLoan direct = calculator.prepaymentCalculator(adopted);
        equal(result.getScenario().getPlan().getTotalInterest(), direct.getTotalInterest());
        equal(result.getScenario().getNextMonthlyPayment(), direct.getMonthLoanList().get(1).getRepayment());
    }

    @Test
    void alreadySatisfiedGoalReturnsZeroWithoutInjectingAnEvent() {
        LoanPlanningRequest request = request(1, 3.5);
        request.setGoal("PAYMENT");
        request.setTargetPayment(new BigDecimal("10000"));
        LoanPlanningResult.Solution result = planning.solve(request, calculator::prepaymentCalculator);
        equal(BigDecimal.ZERO, result.getRepayment());
        assertTrue(result.isAvailable());
        assertTrue(result.getScenario().getPlan().getAppliedPrepayments().isEmpty());
        request.setGoal("PAYOFF");
        request.setTargetPayoffDate("2055-01-01");
        equal(BigDecimal.ZERO, planning.solve(request, calculator::prepaymentCalculator).getRepayment());
    }

    @Test
    void zeroPaymentTargetSettlesTheWholeBalanceAndClosingInterestIsNotMonthlyPayment() {
        LoanPlanningRequest request = request(1, 3.5);
        request.setGoal("PAYMENT");
        request.setTargetPayment(BigDecimal.ZERO);
        LoanPlanningResult.Solution result = planning.solve(request, calculator::prepaymentCalculator);
        assertTrue(result.isAvailable());
        equal(BigDecimal.ZERO, result.getScenario().getNextMonthlyPayment());
        assertNull(result.getScenario().getNextPaymentDate());
        assertEquals(request.getPrepaymentDate(), result.getScenario().getPayoffDate());
        assertTrue(result.getScenario().getFutureInterest().signum() > 0);
        reconciles(result.getScenario().getPlan());
    }

    @Test
    void sameDayNewPayoffIncludesItsPrincipalAndInterestButNotExistingHistory() {
        LoanPlanningRequest request = request(1, 3.6);
        request.getLoan().setAmount(12);
        request.getLoan().setYear(1);
        request.setComparisonDate("2024-02-10");
        request.setPrepaymentDate("2024-02-10");
        TotalLoan original = calculator.prepaymentCalculator(request.getLoan());
        BigDecimal balance = original.getMonthLoanList().get(0).getRemainPrincipal();
        request.setRepayment(balance.movePointLeft(4));
        LoanPlanningResult.Scenario result = planning.compare(request, calculator::prepaymentCalculator)
                .getScenarios().get(1);
        MonthLoan closing = result.getPlan().getMonthLoanList().get(1);
        equal(closing.getInterest(), result.getFutureInterest());
        equal(balance.add(closing.getInterest()), result.getNextYearPayment());
        equal(BigDecimal.ZERO, result.getNextMonthlyPayment());
        assertEquals("2024-02-10", result.getPayoffDate());
    }

    @Test
    void impossiblePayoffDateAndClosedLoanHaveExplicitUnavailableResults() {
        LoanPlanningRequest request = request(1, 3.5);
        request.setGoal("PAYOFF");
        request.setTargetPayoffDate("2025-02-09");
        LoanPlanningResult.Solution impossible = planning.solve(request, calculator::prepaymentCalculator);
        assertFalse(impossible.isAvailable());
        assertNotNull(impossible.getMessage());
        request.setComparisonDate("2055-01-01");
        request.setPrepaymentDate("2055-01-01");
        LoanPlanningResult.Solution closed = planning.solve(request, calculator::prepaymentCalculator);
        assertFalse(closed.isAvailable());
    }

    @Test
    void rejectsSubCentAmountsPastDatesAndInvalidGoalWithoutSwallowingErrors() {
        LoanPlanningRequest request = request(1, 3.5);
        request.setRepayment(new BigDecimal("1.0000001"));
        assertThrows(LoanException.class, () -> planning.compare(request, calculator::prepaymentCalculator));
        request.setRepayment(BigDecimal.ONE);
        request.setPrepaymentDate("2025-01-19");
        assertThrows(LoanException.class, () -> planning.compare(request, calculator::prepaymentCalculator));
        request.setPrepaymentDate("2025-02-10");
        request.setGoal("OTHER");
        assertThrows(LoanException.class, () -> planning.solve(request, calculator::prepaymentCalculator));
    }

    @Test
    void monthEndAndSameDayHistoricalPrepaymentAreCountedExactlyOnce() {
        LoanPlanningRequest request = request(1, 3.5);
        request.getLoan().setFirstPaymentDate("2024-01-31");
        request.setComparisonDate("2025-02-10");
        request.setPrepaymentDate("2025-02-10");
        request.getLoan().setPrepaymentList(Collections.singletonList(event("2025-02-10", "10", 1, 2, null)));
        LoanPlanningResult.Comparison result = planning.compare(request, calculator::prepaymentCalculator);
        assertEquals(1, result.getScenarios().get(0).getPlan().getAppliedPrepayments().size());
        assertEquals(2, result.getScenarios().get(1).getPlan().getAppliedPrepayments().size());
        assertEquals("2025-02-28", result.getScenarios().get(1).getNextPaymentDate());
        reconciles(result.getScenarios().get(1).getPlan());
    }

    private LoanPlanningRequest request(int type, double rate) {
        LoanPlanningRequest request = new LoanPlanningRequest();
        request.setLoan(loan(type, rate));
        request.setComparisonDate("2025-01-20");
        request.setPrepaymentDate("2025-02-10");
        request.setRepayment(new BigDecimal("10"));
        return request;
    }

    private StockLoanDTO loan(int type, double rate) {
        StockLoanDTO loan = new StockLoanDTO();
        loan.setAmount(100);
        loan.setYear(30);
        loan.setRate(rate);
        loan.setType(type);
        loan.setFirstPaymentDate("2024-01-15");
        loan.setRateAdjustmentDay(1);
        loan.setRateMode("FIXED");
        loan.setApplyHistoricalRateAdjustments(false);
        return loan;
    }

    private PrepaymentDTO event(String date, String amount, int type, int strategy, String rate) {
        PrepaymentDTO event = new PrepaymentDTO();
        event.setPrepaymentDate(date);
        event.setRepayment(new BigDecimal(amount));
        event.setNewType(type);
        event.setRepaymentType(strategy);
        if (rate != null) event.setNewRate(new BigDecimal(rate));
        return event;
    }

    private void reconciles(TotalLoan plan) {
        BigDecimal principal = plan.getMonthLoanList().stream().map(MonthLoan::getPrincipal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal extra = plan.getAppliedPrepayments().stream().map(AppliedPrepayment::getRepayment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        equal(plan.getLoanAmount(), principal.add(extra));
        equal(plan.getLoanAmount().add(plan.getTotalInterest()), plan.getTotalRepayment());
        assertEquals(0, plan.getMonthLoanList().get(plan.getMonthLoanList().size() - 1).getRemainPrincipal().signum());
    }

    private void equal(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), expected + " != " + actual);
    }
}
