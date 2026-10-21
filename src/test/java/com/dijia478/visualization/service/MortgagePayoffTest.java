package com.dijia478.visualization.service;

import com.dijia478.visualization.bean.*;
import com.dijia478.visualization.controller.LoanController;
import com.dijia478.visualization.service.impl.EqualPrincipalCalculator;
import com.dijia478.visualization.service.impl.EqualRepaymentCalculator;
import com.dijia478.visualization.service.impl.PrepaymentCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MortgagePayoffTest {
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
    void fullPayoffIncludesInterestUpToItsActualDateWithoutChangingPaidInstallments() {
        StockLoanDTO input = input();
        TotalLoan original = controller.prepaymentCalculator(input);
        input.setPrepaymentList(Arrays.asList(event("2024-04-20", 8, null)));
        TotalLoan result = controller.prepaymentCalculator(input);

        // 前四期利息1260元；4/15至4/20：80000 × 3.6% × 5/360 = 40元。
        money("1300.00", result.getTotalInterest());
        money("121300.00", result.getTotalRepayment());
        assertEquals(5, result.getMonthLoanList().size());
        MonthLoan closing = last(result);
        assertEquals("2024-04-20", closing.getDateFormat());
        money("0", closing.getPrincipal());
        money("40", closing.getInterest());
        money("40", closing.getRepayment());
        for (int i = 0; i < 4; i++) {
            MonthLoan before = original.getMonthLoanList().get(i), after = result.getMonthLoanList().get(i);
            money(before.getRepayment().toPlainString(), after.getRepayment());
            money(before.getPrincipal().toPlainString(), after.getPrincipal());
            money(before.getInterest().toPlainString(), after.getInterest());
        }
        money("1260", interestThrough(result, "2024-04-19"));
        money("1300", interestThrough(result, "2024-04-20"));
        reconciles(result);
    }

    @Test
    void payoffUsesActualDaysInFebruaryAndThirtyOrThirtyOneDayPeriodsInBothModes() {
        for (String first : Arrays.asList("2023-02-15", "2024-02-15", "2024-03-15", "2024-04-15")) {
            for (String mode : Arrays.asList("MONTHLY", "SPLIT_30_360")) {
                StockLoanDTO input = input();
                input.setFirstPaymentDate(first);
                input.setRepricingInterestMode(mode);
                String date = LocalDate.parse(first).plusDays(5).toString();
                input.setPrepaymentList(Arrays.asList(event(date, 11, null)));
                TotalLoan result = controller.prepaymentCalculator(input);
                money("415", result.getTotalInterest());
                money("55", last(result).getInterest());
                assertEquals(date, last(result).getDateFormat());
                assertEquals(5, last(result).getInterestSegments().get(0).getDays());
                reconciles(result);
            }
        }
    }

    @Test
    void earlierPartialPrepaymentAndFinalPayoffSplitPrincipalAtTheirOwnDates() {
        for (String mode : Arrays.asList("MONTHLY", "SPLIT_30_360")) {
            StockLoanDTO input = input();
            input.setRepricingInterestMode(mode);
            input.setPrepaymentList(Arrays.asList(event("2024-04-18", 3, null), event("2024-04-25", 5, null)));
            TotalLoan result = controller.prepaymentCalculator(input);
            // 80000 × 3.6% × 3/360 + 50000 × 3.6% × 7/360 = 59元。
            money("59", last(result).getInterest());
            money("1319", result.getTotalInterest());
            List<InterestSegment> segments = last(result).getInterestSegments();
            assertEquals(2, segments.size());
            assertEquals(3, segments.get(0).getDays());
            money("80000", segments.get(0).getPrincipalOffset());
            assertEquals(7, segments.get(1).getDays());
            money("50000", segments.get(1).getPrincipalOffset());
            reconciles(result);
        }
    }

    @Test
    void sameDaySplitAndCombinedPayoffHaveIdenticalAmounts() {
        StockLoanDTO input = input();
        input.setPrepaymentList(Arrays.asList(event("2024-04-20", 8, null)));
        TotalLoan combined = controller.prepaymentCalculator(input);
        input.setPrepaymentList(Arrays.asList(event("2024-04-20", 3, null), event("2024-04-20", 5, null)));
        TotalLoan split = controller.prepaymentCalculator(input);
        money(combined.getTotalInterest().toPlainString(), split.getTotalInterest());
        money(combined.getTotalRepayment().toPlainString(), split.getTotalRepayment());
        assertEquals(combined.getMonthLoanList().size(), split.getMonthLoanList().size());
        money("40", last(split).getInterest());
        reconciles(split);
    }

    @Test
    void zeroRateAndSameDueDateDoNotChargeInterestTwice() {
        StockLoanDTO input = input();
        input.setPrepaymentList(Arrays.asList(event("2024-04-15", 8, null)));
        TotalLoan sameDate = controller.prepaymentCalculator(input);
        assertEquals(4, sameDate.getMonthLoanList().size());
        money("1260", sameDate.getTotalInterest());
        assertEquals("2024-04-15", sameDate.getPayoffDate());
        reconciles(sameDate);

        input.setRate(0.0);
        input.setPrepaymentList(Arrays.asList(event("2024-04-20", 8, null)));
        TotalLoan zeroRate = controller.prepaymentCalculator(input);
        assertEquals(4, zeroRate.getMonthLoanList().size());
        money("0", zeroRate.getTotalInterest());
        assertEquals("2024-04-20", zeroRate.getPayoffDate());
        reconciles(zeroRate);
    }

    @Test
    void bothRepaymentMethodsSettleAcrossRateChangesWithoutApplyingThePayoffDayRateBackwards() {
        for (int type : new int[]{1, 2}) {
            StockLoanDTO input = input();
            input.setType(type);
            input.setRate(0.0);
            PrepaymentDTO change = event("2024-01-16", 0, "3.6");
            PrepaymentDTO payoff = event("2024-01-20", 11, "4.2");
            change.setNewType(type);
            payoff.setNewType(type);
            input.setPrepaymentList(Arrays.asList(change, payoff));
            TotalLoan result = controller.prepaymentCalculator(input);
            // 1/15已还首期；1/16起按3.6%计4天，结清日的新利率没有历史计息天数。
            money("44", result.getTotalInterest());
            money("44", last(result).getInterest());
            assertEquals("2024-01-20", last(result).getDateFormat());
            assertEquals(2, last(result).getInterestSegments().size());
            assertEquals(1, last(result).getInterestSegments().get(0).getDays());
            assertEquals(4, last(result).getInterestSegments().get(1).getDays());
            reconciles(result);
        }
    }

    @Test
    void payoffKeepsSubCentInterestFromPreviousInstallments() {
        StockLoanDTO input = input();
        input.setRate(3.7);
        input.setPrepaymentList(Arrays.asList(event("2024-02-20", 10, null)));
        TotalLoan result = controller.prepaymentCalculator(input);
        // 上期尾厘0.00666… + 本次51.38888…，按同一结转规则入账51.39。
        money("339.16", result.getMonthLoanList().get(1).getInterest());
        money("51.39", last(result).getInterest());
        money("760.55", result.getTotalInterest());
        reconciles(result);
    }

    @Test
    void automaticRepricingAfterPayoffInTheSameContractPeriodIsIgnored() {
        StockLoanDTO input = floatingInput();
        input.setPrepaymentList(Arrays.asList(event("2024-12-20", 22, null)));
        TotalLoan result = controller.prepaymentCalculator(input);
        money("122.22", last(result).getInterest());
        money("1688.88", result.getTotalInterest());
        assertEquals("2024-12-20", last(result).getDateFormat());
        assertTrue(result.getRateChanges().isEmpty());
        assertTrue(result.getLprMonth().isEmpty());
        assertTrue(result.getLprRate().isEmpty());
        reconciles(result);
    }

    @Test
    void payoffSplitsInterestAcrossActualLprRepricing() {
        StockLoanDTO input = floatingInput();
        input.setPrepaymentList(Arrays.asList(event("2025-01-05", 22, null)));
        TotalLoan result = controller.prepaymentCalculator(input);
        // 22万元按4%计17天、按3.75%计4天，再结转上期尾厘。
        money("507.22", last(result).getInterest());
        money("2073.88", result.getTotalInterest());
        List<InterestSegment> segments = last(result).getInterestSegments();
        assertEquals(2, segments.size());
        assertEquals(17, segments.get(0).getDays());
        money("4", segments.get(0).getAnnualRate());
        assertEquals(4, segments.get(1).getDays());
        money("3.75", segments.get(1).getAnnualRate());
        assertEquals(1, result.getRateChanges().size());
        assertEquals("2025-01-01", result.getRateChanges().get(0).getEffectiveDate());
        reconciles(result);
    }

    @Test
    void aUserPrepaymentAfterFullSettlementStillReturnsTheBusinessError() {
        StockLoanDTO input = input();
        input.setPrepaymentList(Arrays.asList(event("2024-04-20", 8, null), event("2024-04-21", 1, null)));
        LoanException error = assertThrows(LoanException.class, () -> controller.prepaymentCalculator(input));
        assertEquals(ResultEnum.PREPAYMENT_MONTH_TOO_BIG, error.getResultEnum());
    }

    @Test
    void endpointReturnsTheActualPayoffDateAndSeparateInterestTransaction() throws Exception {
        StockLoanDTO input = input();
        input.setPrepaymentList(Arrays.asList(event("2024-04-20", 8, null)));
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(post("/calculator/prepaymentCalculator").contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsBytes(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payoffDate").value("2024-04-20"))
                .andExpect(jsonPath("$.totalInterest").value(1300))
                .andExpect(jsonPath("$.monthLoanList[4].dateFormat").value("2024-04-20"))
                .andExpect(jsonPath("$.monthLoanList[4].principal").value(0))
                .andExpect(jsonPath("$.monthLoanList[4].interest").value(40))
                .andExpect(jsonPath("$.monthLoanList[4].interestSegments[0].days").value(5))
                .andExpect(jsonPath("$.appliedPrepayments[0].repayment").value(80000));
    }

    private StockLoanDTO floatingInput() {
        StockLoanDTO input = input();
        input.setAmount(24);
        input.setYear(2);
        input.setRate(4.0);
        input.setRateMode("LPR");
        input.setFirstPaymentDate("2024-11-15");
        return input;
    }

    private StockLoanDTO input() {
        StockLoanDTO input = new StockLoanDTO();
        input.setAmount(12);
        input.setYear(1);
        input.setRate(3.6);
        input.setType(2);
        input.setFirstPaymentDate("2024-01-15");
        input.setRateAdjustmentDay(1);
        input.setRateMode("FIXED");
        input.setFirstHouse(1);
        return input;
    }

    private PrepaymentDTO event(String date, int amount, String rate) {
        PrepaymentDTO event = new PrepaymentDTO();
        event.setPrepaymentDate(date);
        event.setRepayment(amount);
        event.setNewRate(rate == null ? null : new BigDecimal(rate));
        event.setNewType(2);
        event.setRepaymentType(2);
        return event;
    }

    private MonthLoan last(TotalLoan result) {
        return result.getMonthLoanList().get(result.getMonthLoanList().size() - 1);
    }

    private BigDecimal interestThrough(TotalLoan result, String date) {
        return result.getMonthLoanList().stream().filter(row -> row.getDateFormat().compareTo(date) <= 0)
                .map(MonthLoan::getInterest).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void reconciles(TotalLoan result) {
        BigDecimal principal = BigDecimal.ZERO, interest = BigDecimal.ZERO, payment = BigDecimal.ZERO;
        for (MonthLoan row : result.getMonthLoanList()) {
            principal = principal.add(row.getPrincipal()).add(row.getExtraRepayment());
            interest = interest.add(row.getInterest());
            payment = payment.add(row.getRepayment()).add(row.getExtraRepayment());
            money(row.getRepayment().toPlainString(), row.getPrincipal().add(row.getInterest()));
            money(result.getLoanAmount().toPlainString(), principal.add(row.getRemainPrincipal()));
            money(result.getTotalRepayment().toPlainString(), payment.add(row.getRemainTotal()));
            assertTrue(row.getRemainPrincipal().signum() >= 0);
            if (row.getInterestSegments() != null) {
                money(row.getInterest().toPlainString(), row.getInterestSegments().stream()
                        .map(InterestSegment::getInterest).reduce(BigDecimal.ZERO, BigDecimal::add));
            }
        }
        money(result.getLoanAmount().toPlainString(), principal);
        money(result.getTotalInterest().toPlainString(), interest);
        money(result.getTotalRepayment().toPlainString(), payment);
        money("0", last(result).getRemainPrincipal());
    }

    private void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected=" + expected + " actual=" + actual);
    }
}
