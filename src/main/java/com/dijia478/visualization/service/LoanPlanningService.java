package com.dijia478.visualization.service;

import com.dijia478.visualization.bean.*;
import com.dijia478.visualization.bean.LoanPlanningResult.*;
import com.dijia478.visualization.util.LoanUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** 只编排既有计算器，不引入第二套本息公式。 */
@Service
public class LoanPlanningService {
    public Comparison compare(LoanPlanningRequest request, Function<StockLoanDTO, TotalLoan> calculate) {
        Context context = context(request, calculate);
        BigDecimal amount = checkedMoney(request.getRepayment(), 6);
        if (amount.movePointRight(4).compareTo(context.balance) > 0) {
            throw new LoanException(ResultEnum.REPAYMENT_TOO_BIG);
        }
        Comparison result = new Comparison();
        result.setComparisonDate(request.getComparisonDate());
        result.setPrepaymentDate(request.getPrepaymentDate());
        result.setScenarios(Arrays.asList(context.baseline,
                candidate(context, amount, 2, calculate), candidate(context, amount, 1, calculate)));
        return result;
    }

    public Solution solve(LoanPlanningRequest request, Function<StockLoanDTO, TotalLoan> calculate) {
        if (!"PAYMENT".equals(request.getGoal()) && !"PAYOFF".equals(request.getGoal())) {
            throw new LoanException(ResultEnum.PLANNING_CONFIG_ERR);
        }
        boolean paymentGoal = "PAYMENT".equals(request.getGoal());
        BigDecimal target = paymentGoal ? checkedMoney(request.getTargetPayment(), 2) : null;
        LocalDate payoffTarget = paymentGoal ? null : LoanUtil.parseDate(request.getTargetPayoffDate());
        Context context = context(request, calculate);
        Solution solution = new Solution();
        if (context.balance.signum() == 0) {
            solution.setAvailable(false);
            solution.setMessage("所选提前还款日已无剩余本金，请选择结清之前的日期");
            return solution;
        }
        if (!paymentGoal && payoffTarget.isBefore(context.prepaymentDate)) {
            solution.setAvailable(false);
            solution.setMessage("目标还清日期不能早于本次提前还款日期");
            return solution;
        }
        if (meets(context.baseline, paymentGoal, target, payoffTarget)) {
            solution.setRepayment(BigDecimal.ZERO);
            solution.setScenario(context.baseline);
            solution.setMessage("当前计划已满足目标，无需新增提前还款");
            return solution;
        }
        long maximum = context.balance.movePointRight(2).longValueExact();
        Search search = new Search(context, calculate, paymentGoal, target, payoffTarget);
        Scenario fullPayoff = search.evaluate(maximum);
        if (!search.meets(fullPayoff)) {
            solution.setAvailable(false);
            solution.setMessage("在当前贷款条件下无法达到该目标");
            solution.setIterations(search.iterations);
            return solution;
        }
        Match match = null;
        if (paymentGoal && maximum > 1
                && irregularFirstBill(search.evaluate(maximum - 1), context)) {
            // 首期额外利息另计会使首期月供上跳，必须先找到规则切换点，再分别查找。
            long normal = 0, irregular = maximum - 1;
            while (normal + 1 < irregular) {
                long middle = normal + (irregular - normal) / 2;
                if (irregularFirstBill(search.evaluate(middle), context)) irregular = middle;
                else normal = middle;
            }
            match = search.firstFeasible(1, irregular - 1);
            if (match == null) match = search.firstFeasible(irregular, maximum - 1);
        } else {
            match = search.firstFeasible(1, maximum - 1);
        }
        if (match == null) match = new Match(maximum, fullPayoff);
        // 建议金额的实际计划已经由原计算器达标校验；分币舍入下不承诺全局最小本金。
        solution.setRepayment(BigDecimal.valueOf(match.cents, 6));
        solution.setScenario(match.scenario);
        solution.setIterations(search.iterations);
        solution.setMessage("可达到目标的建议提前还款本金，精确到分；结清当期利息另计");
        return solution;
    }

    private Context context(LoanPlanningRequest request, Function<StockLoanDTO, TotalLoan> calculate) {
        if (request == null || request.getLoan() == null) {
            throw new LoanException(ResultEnum.PLANNING_CONFIG_ERR);
        }
        Context context = new Context();
        context.comparisonDate = LoanUtil.parseDate(request.getComparisonDate());
        context.prepaymentDate = LoanUtil.parseDate(request.getPrepaymentDate());
        StockLoanDTO source = request.getLoan();
        if (context.prepaymentDate.isBefore(context.comparisonDate)
                || context.prepaymentDate.isBefore(LoanUtil.parseDate(source.getFirstPaymentDate()))) {
            throw new LoanException(ResultEnum.DATE_FORMAT_ERR);
        }
        context.loan = copyLoan(source);
        context.type = source.getType();
        List<PrepaymentDTO> events = new ArrayList<>();
        List<PrepaymentDTO> input = source.getPrepaymentList() == null ? new ArrayList<>()
                : new ArrayList<>(source.getPrepaymentList());
        input.sort(Comparator.comparing(PrepaymentDTO::getPrepaymentDate));
        for (PrepaymentDTO original : input) {
            PrepaymentDTO event = copyEvent(original);
            LocalDate date = LoanUtil.parseDate(event.getPrepaymentDate());
            if (date.isAfter(context.comparisonDate) && event.getRepayment().signum() > 0) {
                event.setRepayment(BigDecimal.ZERO);
                event.setRepaymentType(2);
            }
            if (!date.isAfter(context.prepaymentDate)) context.type = event.getNewType();
            events.add(event);
        }
        context.loan.setPrepaymentList(events);
        TotalLoan baselinePlan = calculate.apply(context.loan);
        context.knownInterest = baselinePlan.getMonthLoanList().stream()
                .filter(row -> row.getDateFormat().compareTo(request.getComparisonDate()) <= 0)
                .map(MonthLoan::getInterest).reduce(BigDecimal.ZERO, BigDecimal::add);
        context.knownPayment = baselinePlan.getMonthLoanList().stream()
                .filter(row -> row.getDateFormat().compareTo(request.getComparisonDate()) <= 0)
                .map(MonthLoan::getRepayment).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(baselinePlan.getAppliedPrepayments().stream()
                        .filter(event -> event.getPrepaymentDate().compareTo(request.getComparisonDate()) <= 0)
                        .map(AppliedPrepayment::getRepayment).reduce(BigDecimal.ZERO, BigDecimal::add));
        context.baseline = summarize("baseline", "不新增提前还款", baselinePlan, context, null);
        BigDecimal paidPrincipal = baselinePlan.getMonthLoanList().stream()
                .filter(row -> row.getDateFormat().compareTo(request.getPrepaymentDate()) <= 0)
                .map(MonthLoan::getPrincipal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal prepaid = baselinePlan.getAppliedPrepayments().stream()
                .filter(event -> event.getPrepaymentDate().compareTo(request.getPrepaymentDate()) <= 0)
                .map(AppliedPrepayment::getRepayment).reduce(BigDecimal.ZERO, BigDecimal::add);
        context.balance = baselinePlan.getLoanAmount().subtract(paidPrincipal).subtract(prepaid).max(BigDecimal.ZERO);
        return context;
    }

    private Scenario candidate(Context context, BigDecimal repayment, int strategy,
                               Function<StockLoanDTO, TotalLoan> calculate) {
        String key = strategy == 2 ? "reduce" : "shorten";
        String name = strategy == 2 ? "减少月供" : "缩短期限";
        if (repayment.signum() == 0) return summarize(key, name, context.baseline.getPlan(), context, context.baseline);
        StockLoanDTO input = copyLoan(context.loan);
        List<PrepaymentDTO> events = new ArrayList<>(context.loan.getPrepaymentList());
        PrepaymentDTO extra = new PrepaymentDTO();
        extra.setPrepaymentDate(context.prepaymentDate.toString());
        extra.setRepayment(repayment);
        extra.setNewType(context.type);
        extra.setRepaymentType(strategy);
        events.add(extra);
        input.setPrepaymentList(events);
        try {
            Scenario result = summarize(key, name, calculate.apply(input), context, context.baseline);
            result.setProposal(extra);
            return result;
        } catch (LoanException e) {
            if (e.getResultEnum() != ResultEnum.SHORTENING_UNAVAILABLE
                    && e.getResultEnum() != ResultEnum.PAYMENT_TOO_SMALL) throw e;
            Scenario result = new Scenario();
            result.setKey(key);
            result.setName(name);
            result.setAvailable(false);
            result.setProposal(extra);
            result.setMessage(e.getMessage());
            return result;
        }
    }

    private Scenario summarize(String key, String name, TotalLoan plan, Context context, Scenario baseline) {
        Scenario result = new Scenario();
        result.setKey(key);
        result.setName(name);
        result.setPlan(plan);
        List<MonthLoan> rows = plan.getMonthLoanList();
        String payoff = plan.getPayoffDate() == null ? rows.get(rows.size() - 1).getDateFormat() : plan.getPayoffDate();
        result.setPayoffDate(payoff);
        // 从同一份已发生历史扣除；本次假设在比较日当天结清的利息、本金仍属于决策成本。
        BigDecimal futureInterest = plan.getTotalInterest().subtract(context.knownInterest);
        BigDecimal nextYearPayment = context.knownPayment.negate();
        int remainingMonths = 0;
        for (MonthLoan row : rows) {
            LocalDate date = LoanUtil.parseDate(row.getDateFormat());
            boolean closing = row.getDateFormat().equals(plan.getPayoffDate()) && row.getPrincipal().signum() == 0;
            if (date.isAfter(context.comparisonDate)) {
                if (!closing) remainingMonths++;
            }
            if (!date.isAfter(context.comparisonDate.plusYears(1))) nextYearPayment = nextYearPayment.add(row.getRepayment());
            if (date.isAfter(context.prepaymentDate) && !closing && result.getNextMonthlyPayment() == null) {
                result.setNextMonthlyPayment(row.getRepayment());
                result.setNextPaymentDate(row.getDateFormat());
            }
        }
        for (AppliedPrepayment event : plan.getAppliedPrepayments()) {
            LocalDate date = LoanUtil.parseDate(event.getPrepaymentDate());
            if (!date.isAfter(context.comparisonDate.plusYears(1))) {
                nextYearPayment = nextYearPayment.add(event.getRepayment());
            }
        }
        if (result.getNextMonthlyPayment() == null) result.setNextMonthlyPayment(BigDecimal.ZERO.setScale(2));
        result.setFutureInterest(futureInterest.setScale(2, RoundingMode.HALF_UP));
        result.setNextYearPayment(nextYearPayment.setScale(2, RoundingMode.HALF_UP));
        result.setRemainingMonths(remainingMonths);
        result.setSavedInterest(baseline == null ? BigDecimal.ZERO : baseline.getFutureInterest().subtract(futureInterest));
        result.setMonthsSaved(baseline == null ? 0 : baseline.getRemainingMonths() - remainingMonths);
        return result;
    }

    private boolean irregularFirstBill(Scenario scenario, Context context) {
        if (!scenario.isAvailable()) return true;
        MonthLoan first = null;
        int normalBills = 0;
        for (MonthLoan row : scenario.getPlan().getMonthLoanList()) {
            if (row.getDateFormat().compareTo(context.prepaymentDate.toString()) <= 0
                    || (row.getDateFormat().equals(scenario.getPlan().getPayoffDate())
                    && row.getPrincipal().signum() == 0)) continue;
            if (first == null) first = row;
            normalBills++;
        }
        // 极小剩余本金可能首期即归零，因没有第二期而不会写入 subsequentRepayment。
        return first != null && (first.getSubsequentRepayment() != null || normalBills == 1);
    }

    private class Search {
        private final Context context;
        private final Function<StockLoanDTO, TotalLoan> calculate;
        private final boolean paymentGoal;
        private final BigDecimal target;
        private final LocalDate payoffTarget;
        private int iterations;

        private Search(Context context, Function<StockLoanDTO, TotalLoan> calculate, boolean paymentGoal,
                       BigDecimal target, LocalDate payoffTarget) {
            this.context = context;
            this.calculate = calculate;
            this.paymentGoal = paymentGoal;
            this.target = target;
            this.payoffTarget = payoffTarget;
        }

        private Scenario evaluate(long cents) {
            iterations++;
            return candidate(context, BigDecimal.valueOf(cents, 6), paymentGoal ? 2 : 1, calculate);
        }

        private boolean meets(Scenario scenario) {
            return LoanPlanningService.this.meets(scenario, paymentGoal, target, payoffTarget);
        }

        private Match firstFeasible(long first, long last) {
            if (first > last) return null;
            Scenario winner = evaluate(last);
            if (!meets(winner)) return null;
            long low = first - 1, high = last;
            while (low + 1 < high) {
                long middle = low + (high - low) / 2;
                Scenario trial = evaluate(middle);
                if (meets(trial)) {
                    high = middle;
                    winner = trial;
                } else low = middle;
            }
            return new Match(high, winner);
        }
    }

    private static class Match {
        private final long cents;
        private final Scenario scenario;
        private Match(long cents, Scenario scenario) {
            this.cents = cents;
            this.scenario = scenario;
        }
    }

    private boolean meets(Scenario scenario, boolean paymentGoal, BigDecimal target, LocalDate payoffTarget) {
        return scenario.isAvailable() && (paymentGoal
                ? scenario.getNextMonthlyPayment().compareTo(target) <= 0
                : scenario.getPayoffDate().compareTo(payoffTarget.toString()) <= 0);
    }

    private BigDecimal checkedMoney(BigDecimal value, int scale) {
        try {
            if (value == null || value.signum() < 0) throw new ArithmeticException();
            return value.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new LoanException(ResultEnum.PLANNING_CONFIG_ERR);
        }
    }

    private StockLoanDTO copyLoan(StockLoanDTO source) {
        StockLoanDTO result = new StockLoanDTO();
        BeanUtils.copyProperties(source, result);
        return result;
    }

    private PrepaymentDTO copyEvent(PrepaymentDTO source) {
        PrepaymentDTO result = new PrepaymentDTO();
        BeanUtils.copyProperties(source, result);
        return result;
    }

    private static class Context {
        private StockLoanDTO loan;
        private LocalDate comparisonDate;
        private LocalDate prepaymentDate;
        private Integer type;
        private BigDecimal balance;
        private Scenario baseline;
        private BigDecimal knownInterest;
        private BigDecimal knownPayment;
    }
}
