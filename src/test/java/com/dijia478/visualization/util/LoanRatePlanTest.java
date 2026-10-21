package com.dijia478.visualization.util;

import com.dijia478.visualization.bean.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoanRatePlanTest {
    @Test
    void automaticRatesUseContractRepricingAndExplicitOverrideWins() {
        StockLoanDTO input = input();
        input.setPrepaymentList(Arrays.asList(event("2025-01-27", null), event("2025-03-27", "2.80"), event("2025-06-27", null), event("2026-01-27", null)));
        RatePreview preview = LoanUtil.prepare(input).getPreview();
        List<AppliedPrepayment> events = preview.getPrepaymentRates();
        rate("4.58", events.get(0).getNewRate());
        rate("2.80", events.get(1).getNewRate());
        rate("2.80", events.get(2).getNewRate());
        rate("4.48", events.get(3).getNewRate());
        assertFalse(events.get(1).getAutomatic());
        assertTrue(events.get(2).getAutomatic());
        assertEquals("2026-10-20", preview.getLprUpdatedAt());
        assertNull(input.getPrepaymentList().get(0).getNewRate(), "preview must not mutate input's auto/manual choice");
    }

    @Test
    void historicalPolicyIsDistinguishedFromAnnualLprRepricing() {
        StockLoanDTO input = input();
        input.setApplyHistoricalRateAdjustments(true);
        List<RateAdjustment> changes = LoanUtil.prepare(input).getPreview().getRateChanges();
        RateAdjustment september = changes.stream().filter(change -> "2023-09-25".equals(change.getEffectiveDate())).findFirst().get();
        assertEquals("POLICY_ADJUSTMENT", september.getReason());
        rate("4.30", september.getAnnualRate());
        RateAdjustment january = changes.stream().filter(change -> "2023-01-01".equals(change.getEffectiveDate())).findFirst().get();
        assertEquals("LPR_REPRICING", january.getReason());
    }

    @Test
    void historyNeverRaisesAlreadyLowerContractSpread() {
        StockLoanDTO input = input();
        input.setApplyHistoricalRateAdjustments(true);
        input.setRate(3.65); // origination LPR4.65 => -100BP
        input.setPrepaymentList(Arrays.asList(event("2023-10-27", null), event("2024-11-27", null), event("2025-01-27", null)));
        List<AppliedPrepayment> rates = LoanUtil.prepare(input).getPreview().getPrepaymentRates();
        rate("3.30", rates.get(0).getNewRate());
        rate("3.20", rates.get(1).getNewRate());
        rate("2.60", rates.get(2).getNewRate());
    }

    @Test
    void newLoansDoNotReceiveHistoricSpreadReset() {
        StockLoanDTO input = input();
        input.setApplyHistoricalRateAdjustments(true);
        input.setFirstPaymentDate("2025-07-27");
        input.setRate(3.0);
        input.setPrepaymentList(Arrays.asList(event("2026-01-27", null)));
        rate("3.00", LoanUtil.prepare(input).getPreview().getPrepaymentRates().get(0).getNewRate());
    }

    @Test
    void quarterlyAndSemiannualRepricingUseContractAnchor() {
        for (int cycle : new int[]{3, 6}) {
            StockLoanDTO input = input();
            input.setFirstPaymentDate("2024-09-27");
            input.setRate(3.55); // 3.85 - 30BP
            input.setLprSpreadBp(new BigDecimal("-30"));
            input.setApplyHistoricalRateAdjustments(false);
            input.setRepricingMonths(cycle);
            input.setRepricingAnchorDate("2025-01-27");
            input.setPrepaymentList(Arrays.asList(event("2025-06-27", null), event("2025-07-27", null)));
            List<AppliedPrepayment> rates = LoanUtil.prepare(input).getPreview().getPrepaymentRates();
            rate("3.30", rates.get(0).getNewRate());
            rate("3.20", rates.get(1).getNewRate());
        }
    }

    @Test
    void anniversaryRepricingRetainsOriginalDayAfterShortMonth() {
        StockLoanDTO input = input();
        input.setFirstPaymentDate("2024-02-29");
        input.setLoanDate("2024-01-31");
        input.setRepricingMonths(3);
        input.setRateAdjustmentDay(2);
        input.setApplyHistoricalRateAdjustments(false);
        input.setPrepaymentList(Arrays.asList(event("2024-07-30", null), event("2024-07-31", null)));
        List<AppliedPrepayment> rates = LoanUtil.prepare(input).getPreview().getPrepaymentRates();
        // 初始LPR4.20，4月重定价到3.95；7月31日才采用7月22日的3.85。
        rate("5.38", rates.get(0).getNewRate());
        rate("5.28", rates.get(1).getNewRate());
    }

    @Test
    void splitOnRepaymentDateUsesNewRateForWholeFollowingPeriod() {
        StockLoanDTO input = input();
        input.setFirstPaymentDate("2023-08-31");
        input.setLoanDate("2023-07-31");
        input.setRate(4.2);
        input.setApplyHistoricalRateAdjustments(false);
        input.setRepricingAnchorDate("2024-02-29");
        input.setRepricingInterestMode("SPLIT_30_360");
        PrepaymentDTO event = LoanUtil.prepare(input).getEvents().get(0);
        assertEquals("2024-02-29", event.getPrepaymentDate());
        assertEquals(1, event.getInterestSegments().size());
        assertEquals(30, event.getInterestSegments().get(0).getDays());
        rate("3.95", event.getInterestSegments().get(0).getAnnualRate());
    }

    @Test
    void splitAcrossFebruaryUsesThirtyTotalDaysWithoutDateDrift() {
        StockLoanDTO input = input();
        input.setFirstPaymentDate("2023-08-31");
        input.setLoanDate("2023-07-31");
        input.setRate(4.2);
        input.setApplyHistoricalRateAdjustments(false);
        input.setRepricingAnchorDate("2024-02-20");
        input.setRepricingInterestMode("SPLIT_30_360");
        PrepaymentDTO event = LoanUtil.prepare(input).getEvents().get(0);
        assertEquals(21, event.getInterestSegments().get(0).getDays());
        assertEquals(9, event.getInterestSegments().get(1).getDays());
    }

    @Test
    void explicitSpreadPermitsPreLprLoanAndFixedModeNeverNeedsLpr() {
        StockLoanDTO input = input();
        input.setFirstPaymentDate("2018-08-27");
        assertEquals(ResultEnum.LPR_HISTORY_UNAVAILABLE, assertThrows(LoanException.class, () -> LoanUtil.prepare(input)).getResultEnum());
        input.setLprSpreadBp(new BigDecimal("-30"));
        assertDoesNotThrow(() -> LoanUtil.prepare(input));
        input.setRateMode("FIXED");
        input.setPrepaymentList(Arrays.asList(event("2025-01-27", null)));
        rate("5.63", LoanUtil.prepare(input).getPreview().getPrepaymentRates().get(0).getNewRate());
    }

    @Test
    void invalidDatesCyclesAndPreLoanAnchorsFailClearly() {
        StockLoanDTO input = input();
        input.setRepricingMonths(4);
        assertEquals(ResultEnum.RATE_CONFIG_ERR, assertThrows(LoanException.class, () -> LoanUtil.prepare(input)).getResultEnum());
        input.setRepricingMonths(12);
        input.setRepricingAnchorDate("2020-01-01");
        assertEquals(ResultEnum.RATE_CONFIG_ERR, assertThrows(LoanException.class, () -> LoanUtil.prepare(input)).getResultEnum());
        input.setRepricingAnchorDate(null);
        input.setPrepaymentList(Arrays.asList(event("2021-02-30", null)));
        assertEquals(ResultEnum.DATE_FORMAT_ERR, assertThrows(LoanException.class, () -> LoanUtil.prepare(input)).getResultEnum());
    }

    @Test
    void previewPreservesSubmissionOrderWhileRatesFollowChronology() {
        StockLoanDTO input = input();
        input.setPrepaymentList(Arrays.asList(event("2026-01-27", null), event("2025-01-27", null)));
        List<AppliedPrepayment> rates = LoanUtil.prepare(input).getPreview().getPrepaymentRates();
        assertEquals("2026-01-27", rates.get(0).getPrepaymentDate());
        rate("4.48", rates.get(0).getNewRate());
        rate("4.58", rates.get(1).getNewRate());
    }

    @Test
    void prepaymentBeforeMonthlyDueDateUsesLastPaidInstallment() {
        StockLoanDTO input = input();
        input.setPrepaymentList(Arrays.asList(event("2022-08-26", null), event("2022-08-27", null)));
        List<AppliedPrepayment> rates = LoanUtil.prepare(input).getPreview().getPrepaymentRates();
        assertEquals(13, rates.get(0).getEffectiveMonth());
        assertEquals(14, rates.get(1).getEffectiveMonth());
    }

    @Test
    void onlyCcb2023PolicyWithAlreadyIssuedBillDefersPaymentChange() {
        StockLoanDTO input = input();
        input.setApplyHistoricalRateAdjustments(true);
        List<PrepaymentDTO> events = LoanUtil.prepare(input).getEvents();
        assertTrue(events.stream().filter(e -> "2023-09-25".equals(e.getPrepaymentDate())).findFirst().get().isPaymentDeferred());
        assertFalse(events.stream().filter(e -> "2024-10-25".equals(e.getPrepaymentDate())).findFirst().get().isPaymentDeferred());
        assertFalse(events.stream().filter(e -> "2024-01-01".equals(e.getPrepaymentDate())).findFirst().get().isPaymentDeferred());
        input.setFirstPaymentDate("2021-08-20");
        assertFalse(LoanUtil.prepare(input).getEvents().stream()
                .filter(e -> "2023-09-25".equals(e.getPrepaymentDate())).findFirst().get().isPaymentDeferred());
    }

    @Test
    void prepaymentsSplitOutstandingPrincipalAtTheirActualDates() {
        StockLoanDTO input = input();
        input.setRateMode("FIXED");
        input.setPrepaymentList(Arrays.asList(event("2025-08-04", null), event("2025-08-19", null)));
        List<PrepaymentDTO> events = LoanUtil.prepare(input).getEvents();
        List<InterestSegment> firstSegments = events.get(0).getInterestSegments();
        assertEquals(2, firstSegments.size());
        rate("10000", firstSegments.get(0).getPrincipalOffset());
        rate("0", firstSegments.get(1).getPrincipalOffset());
        List<InterestSegment> segments = events.get(1).getInterestSegments();
        assertEquals(3, segments.size());
        assertEquals(7, segments.get(0).getDays());
        assertEquals(15, segments.get(1).getDays());
        assertEquals(8, segments.get(2).getDays());
        rate("20000", segments.get(0).getPrincipalOffset());
        rate("10000", segments.get(1).getPrincipalOffset());
        rate("0", segments.get(2).getPrincipalOffset());
    }

    private StockLoanDTO input() {
        StockLoanDTO input = new StockLoanDTO();
        input.setAmount(90);
        input.setYear(30);
        input.setRate(5.63);
        input.setType(1);
        input.setFirstPaymentDate("2021-08-27");
        input.setRateAdjustmentDay(1);
        input.setFirstHouse(1);
        return input;
    }

    private PrepaymentDTO event(String date, String rate) {
        PrepaymentDTO event = new PrepaymentDTO();
        event.setPrepaymentDate(date);
        event.setRepayment(1);
        event.setNewRate(rate == null ? null : new BigDecimal(rate));
        event.setNewType(1);
        event.setRepaymentType(2);
        return event;
    }

    private void rate(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), actual.toPlainString());
    }
}
