(function () {
    "use strict";
    const cents = (value) => Math.round(Number(value || 0) * 100);
    const fromCents = (value) => value / 100;
    const addMonths = (month, amount) => {
        const index = Number(month.slice(0, 4)) * 12 + Number(month.slice(5, 7)) - 1 + amount;
        return Math.floor(index / 12) + "-" + String(index % 12 + 1).padStart(2, "0");
    };
    const isSettlement = (plan, row) => row.dateFormat === plan.payoffDate && Number(row.principal) === 0;
    function ledger(plan) {
        return [
            ...plan.monthLoanList.map((row) => ({
                date: row.dateFormat, principal: cents(row.principal), interest: cents(row.interest),
                payment: cents(row.repayment), prepayment: 0, row,
            })),
            ...(plan.appliedPrepayments || []).filter(event => Number(event.repayment) > 0).map(event => ({
                date: event.prepaymentDate, principal: cents(event.repayment), interest: 0,
                payment: cents(event.repayment), prepayment: cents(event.repayment), event,
            })),
        ].sort((a, b) => a.date.localeCompare(b.date) || Number(Boolean(a.event)) - Number(Boolean(b.event)));
    }
    function progress(plan) {
        const byDate = new Map();
        ledger(plan).forEach(entry => {
            if (!byDate.has(entry.date)) byDate.set(entry.date, { dateFormat: entry.date, principal: 0, interest: 0, payment: 0, entries: [] });
            const point = byDate.get(entry.date);
            point.principal += entry.principal;
            point.interest += entry.interest;
            point.payment += entry.payment;
            point.entries.push(entry);
        });
        let principal = 0, interest = 0, payment = 0;
        return Array.from(byDate.values()).map(point => {
            principal += point.principal;
            interest += point.interest;
            payment += point.payment;
            const remaining = cents(plan.loanAmount) - principal,
                remainingInterest = cents(plan.totalInterest) - interest,
                normal = point.entries.find(entry => entry.row && !isSettlement(plan, entry.row)),
                prepayment = point.entries.some(entry => entry.event);
            let periodLabel = normal ? "第" + normal.row.month + "期" : "提前还款";
            if (normal && prepayment) periodLabel += " · 同日提前还款";
            if (point.dateFormat === plan.payoffDate) periodLabel = "结清日";
            return {
                dateFormat: point.dateFormat, month: normal && normal.row.month, periodLabel,
                remainPrincipal: fromCents(remaining), remainInterest: fromCents(remainingInterest),
                remainTotal: fromCents(remaining + remainingInterest),
                totalPrincipal: fromCents(principal), totalInterest: fromCents(interest), totalRepayment: fromCents(payment),
                totalRepaymentAndRemainPrincipal: fromCents(payment + remaining),
            };
        });
    }
    function months(plan) {
        const grouped = new Map();
        let remaining = cents(plan.loanAmount);
        ledger(plan).forEach(entry => {
            const month = entry.date.slice(0, 7);
            if (!grouped.has(month)) grouped.set(month, {
                month, payment: 0, normalPayment: 0, prepayments: 0, principal: 0, interest: 0, entries: [],
            });
            const bucket = grouped.get(month);
            bucket.payment += entry.payment;
            bucket.normalPayment += entry.payment - entry.prepayment;
            bucket.prepayments += entry.prepayment;
            bucket.principal += entry.principal;
            bucket.interest += entry.interest;
            remaining -= entry.principal;
            bucket.remainingPrincipal = remaining;
            bucket.entries.push(entry);
        });
        return Array.from(grouped.values()).map(bucket => {
            ["payment", "normalPayment", "prepayments", "principal", "interest", "remainingPrincipal"]
                .forEach(key => { bucket[key] = fromCents(bucket[key]); });
            return bucket;
        });
    }
    function annual(plan) {
        const years = new Map();
        months(plan).forEach(month => {
            const year = month.month.slice(0, 4);
            if (!years.has(year)) years.set(year, { year, payment: 0, normalPayment: 0, prepayments: 0, principal: 0, interest: 0, months: [] });
            const bucket = years.get(year);
            ["payment", "normalPayment", "prepayments", "principal", "interest"]
                .forEach(key => { bucket[key] += cents(month[key]); });
            bucket.remainingPrincipal = month.remainingPrincipal;
            bucket.months.push(month);
        });
        return Array.from(years.values()).map(bucket => {
            ["payment", "normalPayment", "prepayments", "principal", "interest"]
                .forEach(key => { bucket[key] = fromCents(bucket[key]); });
            return bucket;
        });
    }
    function recent(plan, today, length = 12) {
        const all = months(plan);
        if (!all.length) return { months: [], ended: false };
        const thisMonth = today.slice(0, 7), firstMonth = all[0].month, lastMonth = all[all.length - 1].month;
        const ended = (plan.payoffDate || plan.monthLoanList[plan.monthLoanList.length - 1].dateFormat) < today;
        const start = ended ? addMonths(lastMonth, 1 - length) : thisMonth < firstMonth ? firstMonth : thisMonth;
        const end = addMonths(start, length);
        return { months: all.filter(month => month.month >= start && month.month < end), ended, start, end };
    }
    function events(plan, today) {
        const normalRows = plan.monthLoanList.filter(row => !isSettlement(plan, row));
        const paymentAfter = (date, inclusive) => {
            const index = normalRows.findIndex(row => inclusive ? row.dateFormat >= date : row.dateFormat > date);
            if (index < 0) return {};
            const row = normalRows[index], next = normalRows[index + 1], previous = normalRows[index - 1];
            return {
                paymentDate: row.dateFormat, payment: Number(row.repayment),
                previousPayment: previous && Number(previous.repayment),
                nextPaymentDate: next && next.dateFormat, nextPayment: next && Number(next.repayment),
            };
        };
        const reasonLabels = { LPR_REPRICING: "LPR 重定价", POLICY_ADJUSTMENT: "存量房贷调息", MANUAL: "手动利率调整" };
        const changes = (plan.rateChanges || []).slice().sort((a, b) => a.effectiveDate.localeCompare(b.effectiveDate));
        const result = changes.map((change, index) => {
            const previous = normalRows.filter(row => row.dateFormat < change.effectiveDate).pop(), priorChange = changes[index - 1];
            const previousRate = priorChange && (!previous || priorChange.effectiveDate >= previous.dateFormat)
                ? Number(priorChange.annualRate) : previous && Number(previous.annualRate);
            return Object.assign({
                kind: "rate", date: change.effectiveDate,
                title: reasonLabels[change.reason] || "执行利率调整",
                annualRate: Number(change.annualRate), previousRate,
            }, paymentAfter(change.effectiveDate, true));
        });
        (plan.appliedPrepayments || []).filter(event => Number(event.repayment) > 0).forEach(event => {
            result.push(Object.assign({ kind: "prepayment", date: event.prepaymentDate, title: "提前还款", amount: Number(event.repayment) },
                paymentAfter(event.prepaymentDate, false)));
        });
        const last = plan.monthLoanList[plan.monthLoanList.length - 1];
        if (last) result.push({
            kind: "payoff", title: "预计还清", date: plan.payoffDate || last.dateFormat,
            settlementInterest: fromCents(plan.monthLoanList.filter(row => isSettlement(plan, row))
                .reduce((sum, row) => sum + cents(row.interest), 0)),
        });
        return result.sort((a, b) => a.date.localeCompare(b.date) || (a.kind === "payoff" ? 1 : b.kind === "payoff" ? -1 : 0))
            .map(event => Object.assign(event, { phase: event.date <= today ? "历史测算" : "未来计划" }));
    }
    window.MortgageSchedule = { annual, months, recent, events, progress, addMonths, isSettlement };
})();
