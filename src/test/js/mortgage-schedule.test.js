const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { join } = require("node:path");
const { test } = require("node:test");
const { runInNewContext } = require("node:vm");

function schedule() {
    const sandbox = { window: {} };
    runInNewContext(readFileSync(join(__dirname, "../../main/resources/static/calculator/js/schedule.js"), "utf8"), sandbox);
    return sandbox.window.MortgageSchedule;
}
const row = (date, principal, interest, month = 1) => ({
    dateFormat: date, month, principal, interest, repayment: principal + interest, annualRate: 3.5,
});

test("年度与月份按实际日期归属提前还款，不使用期后挂靠的余额", () => {
    const model = schedule();
    const plan = { loanAmount: 100000, monthLoanList: [
        { ...row("2025-12-27", 1000, 300), remainPrincipal: 79000 },
        row("2026-01-27", 1000, 250, 2),
    ], appliedPrepayments: [{ prepaymentDate: "2026-01-04", repayment: 20000, effectiveMonth: 2 }] };
    const years = model.annual(plan);
    assert.equal(years[0].year, "2025");
    assert.equal(years[0].prepayments, 0);
    assert.equal(years[0].remainingPrincipal, 99000);
    assert.equal(years[1].normalPayment, 1250);
    assert.equal(years[1].prepayments, 20000);
    assert.equal(years[1].payment, 21250);
    assert.equal(years[1].principal, 21000);
    assert.equal(years[1].remainingPrincipal, 78000);
    assert.equal(years[1].months[0].remainingPrincipal, 78000);
});

test("分币汇总，利息结清行和多次预还各计一次，零金额调息不作提前还本", () => {
    const model = schedule();
    const plan = { loanAmount: 100, payoffDate: "2026-02-20", monthLoanList: [
        row("2026-01-15", 10.01, 0.1), row("2026-02-15", 10.02, 0.2, 2),
        row("2026-02-20", 0, 0.03, 3),
    ], appliedPrepayments: [
        { prepaymentDate: "2026-01-20", repayment: 20.01 },
        { prepaymentDate: "2026-02-20", repayment: 59.96 },
        { prepaymentDate: "2026-02-01", repayment: 0 },
    ] };
    const year = model.annual(plan)[0];
    assert.equal(year.principal, 100);
    assert.equal(year.payment, 100.33);
    assert.equal(year.interest, 0.33);
    assert.equal(year.prepayments, 79.97);
    assert.equal(year.remainingPrincipal, 0);
    assert.equal(model.events(plan, "2026-02-22").filter(event => event.kind === "prepayment").length, 2);
});

test("近期包含当月与之后12个自然月，未开始与已结束有明确范围", () => {
    const model = schedule();
    const plan = { loanAmount: 24000, monthLoanList: Array.from({ length: 24 }, (_, i) =>
        row(`${2025 + Math.floor(i / 12)}-${String(i % 12 + 1).padStart(2, "0")}-27`, 1000, 30, i + 1)) };
    const current = model.recent(plan, "2025-08-22");
    assert.equal(current.months.length, 12);
    assert.equal(current.months[0].month, "2025-08");
    assert.equal(current.months[11].month, "2026-07");
    assert.equal(current.ended, false);
    assert.equal(model.recent(plan, "2024-01-01").months[0].month, "2025-01");
    const ended = model.recent(plan, "2027-01-01");
    assert.equal(ended.ended, true);
    assert.equal(ended.months[0].month, "2026-01");
    assert.equal(ended.months[11].month, "2026-12");
});

test("时间轴区分调息生效日和已出账后月供变化，历史只标记测算", () => {
    const model = schedule();
    const plan = { loanAmount: 900000, loanRate: 0.0563,
        monthLoanList: [row("2023-08-27", 1142.08, 3850.77, 25),
            row("2023-09-27", 1194.53, 3798.32, 26), row("2023-10-27", 1358.41, 3127.8, 27)],
        rateChanges: [{ effectiveDate: "2023-09-25", effectiveMonth: 26, annualRate: 4.3, reason: "POLICY_ADJUSTMENT" }],
    };
    const event = model.events(plan, "2023-09-28").find(e => e.kind === "rate");
    assert.equal(event.date, "2023-09-25");
    assert.equal(event.paymentDate, "2023-09-27");
    assert.equal(event.nextPaymentDate, "2023-10-27");
    assert.equal(event.payment, 4992.85);
    assert.equal(event.nextPayment, 4486.21);
    assert.equal(event.phase, "历史测算");
    assert.match(event.title, /存量房贷/);
});

test("同日预还后的账单取后一期且排除结清利息，事件按实际日期排序", () => {
    const model = schedule();
    const plan = { loanAmount: 10000, payoffDate: "2026-03-20", monthLoanList: [
        row("2026-02-15", 1000, 30), row("2026-03-15", 1000, 20, 2), row("2026-03-20", 0, 1, 3),
    ], appliedPrepayments: [{ prepaymentDate: "2026-02-15", repayment: 1000 }, { prepaymentDate: "2026-03-20", repayment: 7000 }] };
    const events = model.events(plan, "2026-02-22");
    assert.equal(events[0].paymentDate, "2026-03-15");
    assert.equal(events[0].payment, 1020);
    assert.equal(events[1].paymentDate, undefined);
    assert.equal(events[events.length - 1].kind, "payoff");
    assert.equal(events[events.length - 1].phase, "未来计划");
});


test("同一还款期间多次执行利率调整显示连续的上一档利率", () => {
    const model = schedule();
    const plan = { loanAmount: 10000, monthLoanList: [row("2026-01-15", 100, 30), row("2026-02-15", 100, 20, 2)],
        rateChanges: [
            { effectiveDate: "2026-01-20", annualRate: 3.2 },
            { effectiveDate: "2026-01-25", annualRate: 3 },
        ] };
    const events = model.events(plan, "2026-01-22").filter(event => event.kind === "rate");
    assert.equal(events[0].previousRate, 3.5);
    assert.equal(events[1].previousRate, 3.2);
    assert.equal(events[0].phase, "历史测算");
    assert.equal(events[1].phase, "未来计划");
});

test("余额与累计流水仅在实际预还日跳变，同日多次预还和结清利息合并且总数守恒", () => {
    const model = schedule();
    const plan = { loanAmount: 100000, totalInterest: 182.46, totalRepayment: 100182.46, payoffDate: "2026-02-20",
        monthLoanList: [
            { ...row("2026-01-15", 10000, 100), remainPrincipal: 70000, totalPrincipal: 30000, totalRepayment: 30100 },
            { ...row("2026-02-15", 10000, 80, 2), remainPrincipal: 0, totalPrincipal: 100000, totalRepayment: 100180 },
            row("2026-02-20", 0, 2.46, 3),
        ], appliedPrepayments: [
            { prepaymentDate: "2026-01-25", repayment: 12000.01, effectiveMonth: 2 },
            { prepaymentDate: "2026-01-25", repayment: 7999.99, effectiveMonth: 2 },
            { prepaymentDate: "2026-02-20", repayment: 60000, effectiveMonth: 3 },
        ] };
    const points = model.progress(plan);
    assert.equal(points.length, 4);
    assert.equal(points[0].dateFormat, "2026-01-15");
    assert.equal(points[0].remainPrincipal, 90000);
    assert.equal(points[0].totalPrincipal, 10000);
    assert.equal(points[0].totalRepayment, 10100);
    assert.equal(points[1].dateFormat, "2026-01-25");
    assert.equal(points[1].remainPrincipal, 70000);
    assert.equal(points[1].totalPrincipal, 30000);
    assert.equal(points[1].totalRepayment, 30100);
    assert.equal(points[1].remainInterest, 82.46);
    assert.equal(points[1].periodLabel, "提前还款");
    const closing = points[3];
    assert.equal(closing.dateFormat, "2026-02-20");
    assert.equal(closing.totalPrincipal, plan.loanAmount);
    assert.equal(closing.totalInterest, plan.totalInterest);
    assert.equal(closing.totalRepayment, plan.totalRepayment);
    assert.equal(closing.remainPrincipal, 0);
    assert.equal(closing.remainInterest, 0);
    assert.equal(closing.remainTotal, 0);
});
