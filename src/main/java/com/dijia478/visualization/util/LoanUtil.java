package com.dijia478.visualization.util;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.dijia478.visualization.bean.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 离线LPR历史、合同重定价和提前还款使用同一条利率时间线。 */
public class LoanUtil {

    private LoanUtil() {}

    public static final String LPR_UPDATED_AT = "2026-10-20";
    private static final NavigableMap<LocalDate, BigDecimal> FIVE_YEAR_LPR = new TreeMap<>();

    static {
        // 只记录变动日；未变化月份沿用前值。2026-10-20人民银行公布5年以上LPR为3.50%。
        // https://www.pbc.gov.cn/zhengcehuobisi/125207/125213/125440/3876551/2026102008303870640/index.html
        String[][] history = {
                {"2019-08-20", "4.85"}, {"2019-11-20", "4.80"}, {"2020-02-20", "4.75"},
                {"2020-04-20", "4.65"}, {"2022-01-20", "4.60"}, {"2022-05-20", "4.45"},
                {"2022-08-22", "4.30"}, {"2023-06-20", "4.20"}, {"2024-02-20", "3.95"},
                {"2024-07-22", "3.85"}, {"2024-10-21", "3.60"}, {"2025-05-20", "3.50"}
        };
        for (String[] row : history) {
            FIVE_YEAR_LPR.put(LocalDate.parse(row[0]), new BigDecimal(row[1]));
        }
    }

    public static DateTime getLoanDate(String firstPaymentDate) {
        return DateUtil.parseDate(parseDate(firstPaymentDate).minusMonths(1).toString());
    }

    public static BigDecimal getLatestLprRate() {
        return FIVE_YEAR_LPR.lastEntry().getValue();
    }

    public static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException e) {
            throw new LoanException(ResultEnum.DATE_FORMAT_ERR);
        }
    }

    private static BigDecimal lprAt(LocalDate date) {
        Map.Entry<LocalDate, BigDecimal> entry = FIVE_YEAR_LPR.floorEntry(date);
        if (entry == null) {
            throw new LoanException(ResultEnum.LPR_HISTORY_UNAVAILABLE);
        }
        return entry.getValue();
    }

    @Getter
    public static class PreparedLoan {
        private BigDecimal initialRate;
        private List<InterestSegment> firstInterestSegments;
        private final List<PrepaymentDTO> events = new ArrayList<>();
        private final RatePreview preview = new RatePreview();
    }

    private static class RateChange {
        private LocalDate date;
        private BigDecimal rate;
        private String reason;

        private RateChange(LocalDate date, BigDecimal rate, String reason) {
            this.date = date;
            this.rate = rate;
            this.reason = reason;
        }
    }

    private static class Event {
        private LocalDate date;
        private int kind; // 0重定价，1历史政策，2用户输入；同日用户明确输入优先
        private int index = -1;
        private PrepaymentDTO input;
        private BigDecimal spreadCeiling;

        private Event(LocalDate date, int kind) {
            this.date = date;
            this.kind = kind;
        }
    }

    /** 利率与本金按生效日期分段；普通整月按30天，首尾非整月仍需合同核对。 */
    public static PreparedLoan prepare(StockLoanDTO data) {
        LocalDate first = parseDate(data.getFirstPaymentDate());
        LocalDate loanDate = data.getLoanDate() == null || data.getLoanDate().isEmpty()
                ? first.minusMonths(1) : parseDate(data.getLoanDate());
        if (!loanDate.isBefore(first)) {
            throw new LoanException(ResultEnum.DATE_FORMAT_ERR);
        }
        int months = data.getYear() * 12;
        LocalDate maturity = first.plusMonths(months - 1L);
        String mode = data.getRateMode() == null ? "LPR" : data.getRateMode();
        int cycle = data.getRepricingMonths() == null ? 12 : data.getRepricingMonths();
        if ((!"LPR".equals(mode) && !"FIXED".equals(mode)) || (cycle != 3 && cycle != 6 && cycle != 12)) {
            throw new LoanException(ResultEnum.RATE_CONFIG_ERR);
        }
        String interestMode = data.getRepricingInterestMode() == null ? "SPLIT_30_360" : data.getRepricingInterestMode();
        if (!"MONTHLY".equals(interestMode) && !"SPLIT_30_360".equals(interestMode)) {
            throw new LoanException(ResultEnum.RATE_CONFIG_ERR);
        }
        PreparedLoan prepared = new PreparedLoan();
        BigDecimal originalRate = BigDecimal.valueOf(data.getRate());
        prepared.initialRate = originalRate;
        BigDecimal spread = BigDecimal.ZERO;
        List<Event> events = new ArrayList<>();
        boolean floating = "LPR".equals(mode);
        if (floating) {
            spread = data.getLprSpreadBp() == null ? originalRate.subtract(lprAt(loanDate))
                    : data.getLprSpreadBp().movePointLeft(2);
            LocalDate anchor;
            int firstCycle = 0;
            if (data.getRepricingAnchorDate() != null && !data.getRepricingAnchorDate().isEmpty()) {
                anchor = parseDate(data.getRepricingAnchorDate());
                if (!anchor.isAfter(loanDate)) {
                    throw new LoanException(ResultEnum.RATE_CONFIG_ERR);
                }
            } else if (Integer.valueOf(1).equals(data.getRateAdjustmentDay())) {
                anchor = LocalDate.of(loanDate.getYear() + 1, 1, 1);
            } else if (Integer.valueOf(2).equals(data.getRateAdjustmentDay())) {
                anchor = loanDate;
                firstCycle = 1;
            } else {
                throw new LoanException(ResultEnum.RATE_CONFIG_ERR);
            }
            // 从固定锚点计算，避免1月31日逐月相加漂移为28日。
            for (int n = firstCycle; !anchor.plusMonths((long) n * cycle).isAfter(maturity); n++) {
                events.add(new Event(anchor.plusMonths((long) n * cycle), 0));
            }
            if (Boolean.TRUE.equals(data.getApplyHistoricalRateAdjustments())) {
                LocalDate policy2023 = LocalDate.of(2023, 9, 25);
                if (Integer.valueOf(1).equals(data.getFirstHouse()) && !loanDate.isAfter(LocalDate.of(2023, 8, 31))) {
                    Event event = new Event(policy2023, 1);
                    event.spreadCeiling = loanDate.isBefore(LocalDate.of(2022, 5, 15))
                            ? BigDecimal.ZERO : new BigDecimal("-0.20");
                    events.add(event);
                }
                LocalDate policy2024 = LocalDate.of(2024, 10, 25);
                if (loanDate.isBefore(policy2024)) {
                    Event event = new Event(policy2024, 1);
                    event.spreadCeiling = new BigDecimal("-0.30");
                    events.add(event);
                }
            }
        }
        List<PrepaymentDTO> inputs = data.getPrepaymentList() == null ? Collections.emptyList() : data.getPrepaymentList();
        List<AppliedPrepayment> previews = new ArrayList<>(Collections.nCopies(inputs.size(), null));
        for (int i = 0; i < inputs.size(); i++) {
            PrepaymentDTO input = inputs.get(i);
            LocalDate date = parseDate(input.getPrepaymentDate());
            if (date.isBefore(first) || date.isAfter(maturity)) {
                throw new LoanException(ResultEnum.DATE_FORMAT_ERR);
            }
            Event event = new Event(date, 2);
            event.index = i;
            event.input = input;
            events.add(event);
        }
        events.sort(Comparator.comparing((Event e) -> e.date).thenComparingInt(e -> e.kind).thenComparingInt(e -> e.index));
        List<RateChange> rateChanges = new ArrayList<>();
        List<PrepaymentDTO> appliedPrepayments = new ArrayList<>();
        BigDecimal currentRate = originalRate;
        int currentType = data.getType();
        for (Event event : events) {
            if (!event.date.isAfter(loanDate) || event.date.isAfter(maturity)) {
                continue;
            }
            BigDecimal before = currentRate;
            if (event.kind == 0) {
                if (event.date.isBefore(FIVE_YEAR_LPR.firstKey())) {
                    continue;
                }
                currentRate = lprAt(event.date).add(spread).max(BigDecimal.ZERO);
            } else if (event.kind == 1) {
                BigDecimal nextSpread = spread.min(event.spreadCeiling);
                currentRate = currentRate.add(nextSpread.subtract(spread)).max(BigDecimal.ZERO);
                spread = nextSpread;
            } else if (event.input.getNewRate() != null) {
                currentRate = event.input.getNewRate();
            }
            int effectiveMonth = nextMonth(first, event.date);
            if (event.kind == 2) {
                currentType = event.input.getNewType();
                appliedPrepayments.add(event.input);
                AppliedPrepayment preview = new AppliedPrepayment();
                preview.setPrepaymentDate(event.date.toString());
                preview.setEffectiveMonth(effectiveMonth);
                preview.setRepayment(BigDecimal.valueOf(event.input.getRepayment()).movePointRight(4));
                preview.setNewRate(currentRate);
                preview.setAutomatic(event.input.getNewRate() == null);
                previews.set(event.index, preview);
            }
            if (event.kind != 2 && before.compareTo(currentRate) == 0) {
                continue;
            }
            if (before.compareTo(currentRate) != 0) {
                rateChanges.add(new RateChange(event.date, currentRate, event.kind == 0 ? "LPR_REPRICING"
                        : event.kind == 1 ? "POLICY_ADJUSTMENT" : "MANUAL"));
            }
            if (effectiveMonth == 1) {
                prepared.initialRate = currentRate;
                continue;
            }
            PrepaymentDTO resolved = new PrepaymentDTO();
            resolved.setPrepaymentDate(event.date.toString());
            resolved.setPrepaymentMonth(effectiveMonth);
            resolved.setRepayment(event.kind == 2 ? event.input.getRepayment() : 0);
            resolved.setRepaymentType(event.kind == 2 ? event.input.getRepaymentType() : 2);
            resolved.setNewRate(currentRate);
            resolved.setNewType(currentType);
            resolved.setLprRate(event.kind == 2 ? 0 : 1);
            // 建行2023-09-24问答：9/25前已出账的当期金额不变，节省利息多还本金。
            // 一般提前15天出账；该历史规则不外推到2024政策或普通合同重定价。
            LocalDate due = first.plusMonths(effectiveMonth - 1L);
            resolved.setPaymentDeferred(event.kind == 1 && event.date.equals(LocalDate.of(2023, 9, 25))
                    && due.minusDays(15).isBefore(event.date));
            if ("SPLIT_30_360".equals(interestMode)) {
                // 同一期逐笔重建计划，只能加回截至本事件已经扣除的预还本金。
                // 利率变化同样取当前时间线快照，后续同日用户输入按排序依次覆盖。
                resolved.setInterestSegments(interestSegments(first, loanDate, effectiveMonth, originalRate,
                        rateChanges, appliedPrepayments));
            }
            prepared.events.add(resolved);
        }
        List<String> warnings = new ArrayList<>();
        if ("SPLIT_30_360".equals(interestMode)) {
            prepared.firstInterestSegments = interestSegments(first, loanDate, 1, originalRate, rateChanges, inputs);
            warnings.add("重定价当期按合同每月30天折算分段，日利率=年利率÷360；新利率段按调整日至还款日的日数计，旧段补足30天。未满一分的利息跨段、跨期结转，月供向上取分；该口径已与一组建行完整账单核对，其他合同仍需验证。");
            warnings.add("提前还款当期按日期分割旧、新本金；实际提前还本重建计划后，月供及后续重定价计入首期额外利息现值，不增加账面本金。此计划口径由建行样本账单推导，首尾非整月及其他银行合同仍需核对。");
        } else {
            warnings.add("采用整月计息模型（年利率÷12）；首尾非整月、重定价跨期及提前还款当期的按日利息以银行合同为准。");
        }
        if (floating) {
            warnings.add("手填某次提前还款利率将沿用至下一合同重定价日；长期加点发生变化时请结合银行账单分段核对。");
            warnings.add("离线5年以上LPR核验至" + LPR_UPDATED_AT + "；之后的未知报价暂按最近已知值估算。");
            if (loanDate.isBefore(FIVE_YEAR_LPR.firstKey())) {
                warnings.add("2019年8月20日前无对应LPR数据，该阶段沿用初始利率；转LPR后的实际加点需按合同核对。");
            }
            if (Boolean.TRUE.equals(data.getApplyHistoricalRateAdjustments())) {
                warnings.add("历史批量调息按一般适用情形估算；特殊地区、二套房及银行个别约定请关闭该选项并录入实际执行利率。");
                warnings.add("2023年建行批量调息按提前15天出账估算已出账金额与计划本金保留，利率即日分段生效，后续月供按冻结计划本金重算；其他银行出账规则可能不同。");
            } else {
                warnings.add("未自动套用存量房贷批量调息；银行实际执行的政策调整可能独立于年度重定价日，请以账单为准。");
            }
        }
        List<RateAdjustment> adjustments = new ArrayList<>();
        for (RateChange change : rateChanges) {
            RateAdjustment adjustment = new RateAdjustment();
            adjustment.setEffectiveDate(change.date.toString());
            adjustment.setEffectiveMonth(nextMonth(first, change.date));
            adjustment.setAnnualRate(change.rate);
            adjustment.setReason(change.reason);
            adjustments.add(adjustment);
        }
        prepared.preview.setRateChanges(adjustments);
        prepared.preview.setPrepaymentRates(previews);
        prepared.preview.setLprRate(getLatestLprRate());
        prepared.preview.setLprUpdatedAt(LPR_UPDATED_AT);
        prepared.preview.setWarnings(warnings);
        return prepared;
    }

    private static List<InterestSegment> interestSegments(LocalDate first, LocalDate loanDate, int month,
                                                         BigDecimal originalRate, List<RateChange> changes,
                                                         List<PrepaymentDTO> prepayments) {
        LocalDate due = first.plusMonths(month - 1L);
        LocalDate previousDue = month == 1 ? loanDate : first.plusMonths(month - 2L);
        SortedSet<LocalDate> boundaries = new TreeSet<>();
        for (RateChange change : changes) {
            if (change.date.isAfter(previousDue) && change.date.isBefore(due)) {
                boundaries.add(change.date);
            }
        }
        List<PrepaymentDTO> inPeriod = new ArrayList<>();
        for (PrepaymentDTO prepayment : prepayments) {
            LocalDate date = parseDate(prepayment.getPrepaymentDate());
            if (prepayment.getRepayment() > 0 && date.isAfter(previousDue) && date.isBefore(due)) {
                inPeriod.add(prepayment);
                boundaries.add(date);
            }
        }
        List<InterestSegment> segments = new ArrayList<>();
        int remainingDays = 30;
        LocalDate start = previousDue;
        for (LocalDate boundary : boundaries) {
            int newDays = Math.min(remainingDays, (int) ChronoUnit.DAYS.between(boundary, due));
            addSegment(segments, remainingDays - newDays, rateAt(start, originalRate, changes),
                    principalOffset(start, inPeriod));
            remainingDays = newDays;
            start = boundary;
        }
        addSegment(segments, remainingDays, rateAt(start, originalRate, changes), principalOffset(start, inPeriod));
        return segments;
    }

    private static BigDecimal rateAt(LocalDate date, BigDecimal originalRate, List<RateChange> changes) {
        BigDecimal rate = originalRate;
        for (RateChange change : changes) {
            if (!change.date.isAfter(date)) rate = change.rate;
        }
        return rate;
    }

    /** 计算器以已扣全部预还本金的余额起算，旧区间需加回当时尚未归还的本金。 */
    private static BigDecimal principalOffset(LocalDate start, List<PrepaymentDTO> prepayments) {
        BigDecimal offset = BigDecimal.ZERO;
        for (PrepaymentDTO prepayment : prepayments) {
            if (parseDate(prepayment.getPrepaymentDate()).isAfter(start)) {
                offset = offset.add(BigDecimal.valueOf(prepayment.getRepayment()).movePointRight(4));
            }
        }
        return offset;
    }

    private static void addSegment(List<InterestSegment> segments, int days, BigDecimal rate, BigDecimal offset) {
        if (days > 0) {
            InterestSegment segment = new InterestSegment();
            segment.setDays(days);
            segment.setAnnualRate(rate);
            segment.setPrincipalOffset(offset);
            segments.add(segment);
        }
    }

    private static int nextMonth(LocalDate first, LocalDate eventDate) {
        int elapsed = (int) ChronoUnit.MONTHS.between(YearMonth.from(first), YearMonth.from(eventDate));
        if (first.plusMonths(elapsed).isAfter(eventDate)) {
            elapsed--;
        }
        return elapsed + 2;
    }
}
