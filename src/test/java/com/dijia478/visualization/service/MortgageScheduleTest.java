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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MortgageScheduleTest {
    private LoanController controller;
    private EqualRepaymentCalculator annuity;
    private EqualPrincipalCalculator equalPrincipal;

    @BeforeEach
    void setup() {
        annuity = new EqualRepaymentCalculator();
        equalPrincipal = new EqualPrincipalCalculator();
        PrepaymentCalculator prepayment = new PrepaymentCalculator();
        ReflectionTestUtils.setField(prepayment, "equalRepaymentCalculator", annuity);
        ReflectionTestUtils.setField(prepayment, "equalPrincipalCalculator", equalPrincipal);
        controller = new LoanController();
        ReflectionTestUtils.setField(controller, "prepaymentCalculator", prepayment);
    }

    @Test
    void reproducesProvidedBankFirstInstallment() {
        StockLoanDTO input = input();
        input.setAmount(90);
        input.setYear(30);
        input.setRate(5.63);
        input.setFirstPaymentDate("2021-08-27");
        TotalLoan result = controller.prepaymentCalculator(input);
        MonthLoan first = result.getMonthLoanList().get(0);
        money("5183.75", first.getRepayment());
        money("4222.50", first.getInterest());
        money("961.25", first.getPrincipal());
        money("899038.75", first.getRemainPrincipal());
        settled(result);
    }

    @Test
    void reproducesCcbJanuary2023StatementWithContractThirtyDaySplit() {
        StockLoanDTO input = input();
        input.setAmount(90);
        input.setRate(5.63);
        input.setRateMode("LPR");
        input.setFirstPaymentDate("2021-08-27");
        TotalLoan result = controller.prepaymentCalculator(input);
        MonthLoan january = result.getMonthLoanList().get(17);
        money("4992.85", january.getRepayment());
        money("3919.67", january.getInterest());
        money("1073.18", january.getPrincipal());
        assertEquals(2, january.getInterestSegments().size());
        assertEquals(4, january.getInterestSegments().get(0).getDays());
        assertEquals(26, january.getInterestSegments().get(1).getDays());
        money("552.38", january.getInterestSegments().get(0).getInterest());
        money("3367.29", january.getInterestSegments().get(1).getInterest());
        // 分段利息必须进入真实余额，影响后续计息，不能只改显示值。
        money("881957.60", january.getRemainPrincipal());
        money("3880.61", result.getMonthLoanList().get(18).getInterest());
        settled(result);
    }

    @Test
    void defaultsAndNullContractOptionsKeepAnnualRepricingWithoutPolicyReset() {
        for (boolean explicitNull : new boolean[]{false, true}) {
            StockLoanDTO input = input();
            input.setAmount(90);
            input.setRate(5.63);
            input.setRateMode("LPR");
            input.setFirstPaymentDate("2021-08-27");
            if (explicitNull) {
                input.setRepricingInterestMode(null);
                input.setApplyHistoricalRateAdjustments(null);
            }
            TotalLoan result = controller.prepaymentCalculator(input);
            MonthLoan january = result.getMonthLoanList().get(17);
            money("3919.67", january.getInterest());
            money("1073.18", january.getPrincipal());
            for (int month = 8; month <= 10; month++) {
                MonthLoan row = result.getMonthLoanList().get(month + 16);
                assertEquals("2023-" + (month < 10 ? "0" : "") + month + "-27", row.getDateFormat());
                money("4992.85", row.getRepayment());
                money("5.28", row.getAnnualRate());
            }
            assertFalse(result.getRateChanges().isEmpty());
            for (RateAdjustment change : result.getRateChanges()) {
                assertEquals("LPR_REPRICING", change.getReason());
                assertTrue(change.getEffectiveDate().endsWith("-01-01"));
            }
            settled(result);
        }
    }

    @Test
    void explicitMonthlyInterestModeRemainsAvailable() {
        StockLoanDTO input = input();
        input.setAmount(90);
        input.setRate(5.63);
        input.setRateMode("LPR");
        input.setFirstPaymentDate("2021-08-27");
        input.setRepricingInterestMode("MONTHLY");
        MonthLoan january = controller.prepaymentCalculator(input).getMonthLoanList().get(17);
        money("4992.85", january.getRepayment());
        money("3885.33", january.getInterest());
        money("1107.52", january.getPrincipal());
        assertNull(january.getInterestSegments());
    }

    @Test
    void zeroInterestAndFinalCentSettlementWorkForBothStrategies() {
        for (int type : new int[]{1, 2}) {
            StockLoanDTO input = input();
            input.setRate(0.0);
            input.setType(type);
            input.setAmount(1);
            input.setYear(1);
            TotalLoan result = controller.prepaymentCalculator(input);
            money("833.33", result.getMonthLoanList().get(0).getRepayment());
            money("833.37", result.getMonthLoanList().get(11).getRepayment());
            money("0.00", result.getTotalInterest());
            settled(result);
        }
    }

    @Test
    void normalSchedulesReconcileEveryCent() {
        for (int type : new int[]{1, 2}) {
            StockLoanDTO input = input();
            input.setType(type);
            input.setRate(4.2);
            TotalLoan result = controller.prepaymentCalculator(input);
            settled(result);
        }
    }

    @Test
    void monthEndDatesStayAnchoredAcrossFebruary() {
        StockLoanDTO input = input();
        input.setFirstPaymentDate("2024-01-31");
        TotalLoan result = controller.prepaymentCalculator(input);
        assertEquals("2024-02-29", result.getMonthLoanList().get(1).getDateFormat());
        assertEquals("2024-03-31", result.getMonthLoanList().get(2).getDateFormat());
        assertEquals("2025-01-31", result.getMonthLoanList().get(12).getDateFormat());
    }

    @Test
    void mergesThreePrepaymentsInOneMonthWithoutLosingMoney() {
        StockLoanDTO input = input();
        input.setPrepaymentList(Arrays.asList(event("2025-01-15", 1, null, 2),
                event("2025-01-16", 2, null, 2), event("2025-01-17", 3, null, 2)));
        TotalLoan result = controller.prepaymentCalculator(input);
        money("60000", result.getMonthLoanList().get(12).getExtraRepayment());
        assertEquals(3, result.getAppliedPrepayments().size());
        assertTrue(result.getOriginalTotalInterest().compareTo(result.getTotalInterest()) > 0);
        settled(result);
    }

    @Test
    void shortenAnnuityKeepsOriginalPaymentUntilLastInstallment() {
        StockLoanDTO input = input();
        input.setPrepaymentList(Arrays.asList(event("2025-01-15", 10, null, 1)));
        TotalLoan result = controller.prepaymentCalculator(input);
        assertTrue(result.getMonthLoanList().size() < 360);
        money(result.getMonthLoanList().get(0).getRepayment().toPlainString(), result.getMonthLoanList().get(13).getRepayment());
        settled(result);
    }

    @Test
    void shorteningEqualPrincipalKeepsFirstNewPaymentThenDeclines() {
        StockLoanDTO input = input();
        input.setType(2);
        PrepaymentDTO extra = event("2025-01-15", 10, null, 1);
        extra.setNewType(2);
        input.setPrepaymentList(Arrays.asList(extra));
        TotalLoan result = controller.prepaymentCalculator(input);
        assertTrue(result.getMonthLoanList().size() < 360);
        money(result.getMonthLoanList().get(12).getRepayment().toPlainString(), result.getMonthLoanList().get(13).getRepayment());
        assertTrue(result.getMonthLoanList().get(14).getRepayment().compareTo(result.getMonthLoanList().get(13).getRepayment()) < 0);
        settled(result);
    }

    @Test
    void switchingToEqualPrincipalDoesNotTurnShorteningIntoExtension() {
        StockLoanDTO input = input();
        input.setAmount(90);
        input.setRate(5.63);
        input.setFirstPaymentDate("2021-08-27");
        PrepaymentDTO extra = event("2021-08-27", 40, null, 1);
        extra.setNewType(2);
        input.setPrepaymentList(Arrays.asList(extra));
        TotalLoan result = controller.prepaymentCalculator(input);
        assertTrue(result.getMonthLoanList().size() < 360);
        money("5183.75", result.getMonthLoanList().get(1).getRepayment());
        settled(result);
    }

    @Test
    void infeasibleCrossStrategyShorteningDoesNotSilentlyExtendLoan() {
        StockLoanDTO input = input();
        input.setAmount(90);
        input.setRate(5.63);
        input.setFirstPaymentDate("2021-08-27");
        PrepaymentDTO extra = event("2021-08-27", 10, null, 1);
        extra.setNewType(2);
        input.setPrepaymentList(Arrays.asList(extra));
        LoanException error = assertThrows(LoanException.class, () -> controller.prepaymentCalculator(input));
        assertEquals(ResultEnum.SHORTENING_UNAVAILABLE, error.getResultEnum());
    }

    @Test
    void fullPayoffEndsAtPaidMonthAndIgnoresLaterAutomaticChanges() {
        StockLoanDTO input = input();
        input.setAmount(12);
        input.setYear(1);
        input.setRate(0.0);
        input.setPrepaymentList(Arrays.asList(event("2024-02-15", 10, null, 2)));
        TotalLoan result = controller.prepaymentCalculator(input);
        assertEquals(2, result.getMonthLoanList().size());
        money("100000", result.getMonthLoanList().get(1).getExtraRepayment());
        settled(result);
    }

    @Test
    void tooMuchPrepaymentUsesBusinessErrorInsteadOfNegativeBalance() {
        StockLoanDTO input = input();
        input.setAmount(12);
        input.setYear(1);
        input.setRate(0.0);
        input.setPrepaymentList(Arrays.asList(event("2024-02-15", 11, null, 2)));
        LoanException error = assertThrows(LoanException.class, () -> controller.prepaymentCalculator(input));
        assertEquals(ResultEnum.REPAYMENT_TOO_BIG, error.getResultEnum());
    }

    @Test
    void userEventAfterEarlierPayoffReturnsBusinessError() {
        StockLoanDTO input = input();
        input.setAmount(12);
        input.setYear(1);
        input.setRate(0.0);
        input.setPrepaymentList(Arrays.asList(event("2024-02-15", 10, null, 2), event("2024-03-15", 1, null, 2)));
        LoanException error = assertThrows(LoanException.class, () -> controller.prepaymentCalculator(input));
        assertEquals(ResultEnum.PREPAYMENT_MONTH_TOO_BIG, error.getResultEnum());
    }

    @Test
    void bankLoanRepricesAtJanuaryUsingPreviousPublishedLpr() {
        StockLoanDTO input = input();
        input.setAmount(90);
        input.setRate(5.63);
        input.setRateMode("LPR");
        input.setFirstPaymentDate("2021-08-27");
        input.setApplyHistoricalRateAdjustments(false);
        TotalLoan result = controller.prepaymentCalculator(input);
        // 利息不足一分结转后，17期余额与实账883030.78一致；剩余343期按5.28%。
        money("883030.78", result.getMonthLoanList().get(16).getRemainPrincipal());
        MonthLoan january2023 = result.getMonthLoanList().get(17);
        assertEquals("2023-01-27", january2023.getDateFormat());
        money("5.28", january2023.getAnnualRate());
        money("4992.85", january2023.getRepayment());
        // 纯LPR调息不应被计为提前还款节省利息。
        money(result.getTotalInterest().toPlainString(), result.getOriginalTotalInterest());
        settled(result);
    }

    @Test
    void allCcbStatementTransactionsStayWithinTwoCents() throws Exception {
        StockLoanDTO input = ccbStatementInput();
        TotalLoan result = controller.prepaymentCalculator(input);
        int installments = 0;
        int prepayments = 0;
        // 月供、当期本息和实际交易后余额均受同一严格容差约束。
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/mortgage/ccb-statement-anonymous.csv"), StandardCharsets.UTF_8))) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] bank = line.split(",");
                if (bank[0].endsWith("-27")) {
                    MonthLoan row = result.getMonthLoanList().stream()
                            .filter(month -> bank[0].equals(month.getDateFormat())).findFirst().get();
                    near(bank[1], row.getRepayment(), "0.02", bank[0] + " 月供");
                    near(bank[2], row.getPrincipal(), "0.02", bank[0] + " 本金");
                    near(bank[3], row.getInterest(), "0.02", bank[0] + " 利息");
                    // API将期后预还挂在前一已还期；账单在该还款日尚未扣此笔款，核对时加回。
                    near(bank[4], row.getRemainPrincipal().add(row.getExtraRepayment()), "0.02", bank[0] + " 余额");
                    installments++;
                } else {
                    MonthLoan paid = result.getMonthLoanList().stream()
                            .filter(month -> month.getDateFormat().compareTo(bank[0]) < 0)
                            .reduce((previous, current) -> current).get();
                    money(bank[2], paid.getExtraRepayment());
                    near(bank[4], paid.getRemainPrincipal(), "0.02", bank[0] + " 提前还款后余额");
                    prepayments++;
                }
            }
        }
        assertEquals(62, installments);
        assertEquals(2, prepayments);
        money("4992.85", result.getMonthLoanList().get(25).getRepayment());
        money("4.30", result.getMonthLoanList().get(25).getAnnualRate());
        money("4486.46", result.getMonthLoanList().get(26).getRepayment());
        money("3497.46", result.getMonthLoanList().get(53).getRepayment());
        money("3536.29", result.getMonthLoanList().get(48).getRepayment());
        money("2771.86", result.getMonthLoanList().get(60).getRepayment());
        money("3.30", result.getAppliedPrepayments().get(0).getNewRate());
        money("3.20", result.getAppliedPrepayments().get(1).getNewRate());
        settled(result);
    }

    @Test
    void prepaymentComparisonDoesNotAddOldPrincipalInterestToNoPrepaymentScenario() {
        StockLoanDTO input = ccbStatementInput();
        TotalLoan actual = controller.prepaymentCalculator(input);
        input.setPrepaymentList(new ArrayList<>());
        TotalLoan baseline = controller.prepaymentCalculator(input);
        money(baseline.getTotalInterest().toPlainString(), actual.getOriginalTotalInterest());
    }

    @Test
    void futurePrepaymentDoesNotChangeEarlierPaymentOrInterestRemainder() {
        StockLoanDTO input = ccbStatementInput();
        TotalLoan before = controller.prepaymentCalculator(input);
        List<PrepaymentDTO> events = new ArrayList<>(input.getPrepaymentList());
        events.add(event("2027-08-04", 5, null, 2));
        input.setPrepaymentList(events);
        TotalLoan after = controller.prepaymentCalculator(input);
        for (int i = 0; i < 72; i++) {
            MonthLoan original = before.getMonthLoanList().get(i);
            MonthLoan changed = after.getMonthLoanList().get(i);
            money(original.getRepayment().toPlainString(), changed.getRepayment());
            money(original.getInterest().toPlainString(), changed.getInterest());
            money(original.getInterestRemainder().toPlainString(), changed.getInterestRemainder());
        }
        settled(after);
    }

    private StockLoanDTO ccbStatementInput() {
        StockLoanDTO input = input();
        input.setAmount(90);
        input.setRate(5.63);
        input.setRateMode("LPR");
        input.setFirstPaymentDate("2021-08-27");
        input.setApplyHistoricalRateAdjustments(true);
        input.setPrepaymentList(Arrays.asList(event("2025-08-04", 10, null, 2), event("2026-08-19", 15, null, 2)));
        return input;
    }

    private void near(String expected, BigDecimal actual, String tolerance, String context) {
        BigDecimal difference = actual.subtract(new BigDecimal(expected)).abs();
        assertTrue(difference.compareTo(new BigDecimal(tolerance)) <= 0,
                context + " bank=" + expected + " model=" + actual + " difference=" + difference);
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

    private PrepaymentDTO event(String date, int amount, String rate, int scheme) {
        PrepaymentDTO event = new PrepaymentDTO();
        event.setPrepaymentDate(date);
        event.setRepayment(amount);
        event.setNewRate(rate == null ? null : new BigDecimal(rate));
        event.setNewType(1);
        event.setRepaymentType(scheme);
        return event;
    }

    private void settled(TotalLoan result) {
        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal interest = BigDecimal.ZERO;
        BigDecimal repayment = BigDecimal.ZERO;
        List<MonthLoan> rows = result.getMonthLoanList();
        for (MonthLoan row : rows) {
            principal = principal.add(row.getPrincipal()).add(row.getExtraRepayment());
            interest = interest.add(row.getInterest());
            repayment = repayment.add(row.getRepayment()).add(row.getExtraRepayment());
            assertEquals(0, row.getRepayment().compareTo(row.getPrincipal().add(row.getInterest())));
            assertEquals(0, row.getRemainPrincipal().add(principal).compareTo(result.getLoanAmount()));
            assertTrue(row.getRemainPrincipal().signum() >= 0);
            assertEquals(0, row.getRemainTotal().add(repayment).compareTo(result.getTotalRepayment()));
            assertEquals(0, row.getRemainInterest().add(interest).compareTo(result.getTotalInterest()));
        }
        money(result.getLoanAmount().toPlainString(), principal);
        money(result.getTotalInterest().toPlainString(), interest);
        money(result.getTotalRepayment().toPlainString(), repayment);
        money("0", rows.get(rows.size() - 1).getRemainPrincipal());
    }

    private void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " actual " + actual);
    }
}
