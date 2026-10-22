const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { join } = require("node:path");
const { test } = require("node:test");
const { runInNewContext } = require("node:vm");

// 仅替代 DOM、绘图和当天日期，汇总仍执行页面实际的 applyResult。
function summaryFor(data, today) {
    const nodes = new Map();
    function node(selector) {
        if (!nodes.has(selector)) nodes.set(selector, {
            value: selector === "#rateMode" ? "FIXED" : "",
            textContent: "", children: [], dataset: {}, hidden: false,
            querySelectorAll() { return []; }, addEventListener() {},
            setAttribute() {}, removeAttribute() {},
            classList: { add() {}, remove() {} },
        });
        return nodes.get(selector);
    }
    const sandbox = {
        document: { querySelector: node, querySelectorAll() { return []; } },
        window: { matchMedia: () => ({ matches: false }), MortgageDatePicker: { today: () => today } },
        MortgageCharts: { render() {} }, AbortController, setTimeout() {}, clearTimeout() {},
    };
    const code = readFileSync(join(__dirname, "../../main/resources/static/calculator/js/index.js"), "utf8")
        .replace("    initializeDefaults();\n})();", "    window.applyResult = applyResult;\n})();");
    runInNewContext(code, sandbox);
    sandbox.window.applyResult(data, { amount: 12, year: 1, rate: 3.6, type: 2 });
    return {
        monthly: node("#monthly-payment").textContent,
        status: node("#loan-summary").textContent,
        payoff: node("#payoff-date").textContent,
        remaining: node("#payoff-remaining").textContent,
    };
}

function payoffPlan(date = "2026-10-05", interest = 140) {
    return {
        loanAmount: 120000, totalInterest: 1640, totalRepayment: 121640,
        originalTotalInterest: 2340, payoffDate: date,
        appliedPrepayments: [{ prepaymentDate: date, effectiveMonth: 2, repayment: 70000 }],
        monthLoanList: [
            { dateFormat: "2026-09-15", repayment: 10210, principal: 10000, interest: 210 },
            ...(interest ? [{ dateFormat: date, repayment: interest, principal: 0, interest }] : []),
        ],
    };
}

test("本月已经结清时，结清利息不再显示为当前月供", () => {
    const result = summaryFor(payoffPlan(), "2026-10-22");
    assert.equal(result.monthly, "0.00");
    assert.equal(result.status, "已还清 · 最后还款 2026-10-05");
    assert.equal(result.remaining, "（已还清）");
});

test("预计还清日期后显示距当前月份剩余的年数和月数", () => {
    const result = summaryFor(payoffPlan("2051-07-27"), "2026-10-22");
    assert.equal(result.payoff, "2051 年 07 月");
    assert.equal(result.remaining, "（还剩24年9个月）");
});

test("跨年、整年和当月结清的剩余时间不多算一个月", () => {
    for (const [date, expected] of [
        ["2027-01-27", "（还剩0年3个月）"],
        ["2027-10-27", "（还剩1年0个月）"],
        ["2026-10-28", "（还剩0年0个月）"],
    ]) {
        assert.equal(summaryFor(payoffPlan(date), "2026-10-22").remaining, expected);
    }
});

test("结清当天按完整日期显示已还清", () => {
    const result = summaryFor(payoffPlan(), "2026-10-05");
    assert.equal(result.monthly, "0.00");
    assert.equal(result.status, "已还清 · 最后还款 2026-10-05");
});

test("本月未来的结清利息不冒充月供，也不提前显示已还清", () => {
    const result = summaryFor(payoffPlan("2026-10-28"), "2026-10-22");
    assert.equal(result.monthly, "0.00");
    assert.equal(result.status, "本月无月供 · 计划于 2026-10-28 结清");
});

test("零利率没有结清利息行时也按实际结清日判断状态", () => {
    const plan = payoffPlan("2026-10-05", 0);
    plan.totalInterest = plan.originalTotalInterest = 0;
    plan.totalRepayment = plan.loanAmount;
    plan.monthLoanList[0].interest = 0;
    plan.monthLoanList[0].repayment = plan.monthLoanList[0].principal;
    assert.equal(summaryFor(plan, "2026-10-05").status,
        "已还清 · 最后还款 2026-10-05");
    assert.equal(summaryFor(plan, "2026-10-01").status,
        "本月无月供 · 计划于 2026-10-05 结清");
});

test("后端明确返回的结清日期优先于最后一条正常还款记录", () => {
    const plan = payoffPlan("2026-10-05", 0);
    plan.appliedPrepayments = [];
    const result = summaryFor(plan, "2026-10-01");
    assert.equal(result.monthly, "0.00");
    assert.equal(result.status, "本月无月供 · 计划于 2026-10-05 结清");
    assert.equal(result.payoff, "2026 年 10 月");
});

test("尚未结清的贷款仍显示正常当月月供", () => {
    const plan = payoffPlan("2026-10-28");
    plan.monthLoanList.splice(1, 0,
        { dateFormat: "2026-10-15", repayment: 10180, principal: 10000, interest: 180 });
    const result = summaryFor(plan, "2026-10-22");
    assert.equal(result.monthly, "10,180.00");
    assert.equal(result.status, "12 万元 / 1 年 / 初始年利率 3.6%");
});

test("未提前还款的计划保留正常月供和预计结清月份", () => {
    const plan = payoffPlan();
    plan.payoffDate = null;
    plan.appliedPrepayments = [];
    plan.monthLoanList[1] = { dateFormat: "2026-10-15", repayment: 10180, principal: 10000, interest: 180 };
    plan.monthLoanList.push({ dateFormat: "2027-04-15", repayment: 10030, principal: 10000, interest: 30 });
    const result = summaryFor(plan, "2026-10-22");
    assert.equal(result.monthly, "10,180.00");
    assert.equal(result.payoff, "2027 年 04 月");
});

test("未结清计划中本金为零的正常账单仍显示本期应还利息", () => {
    const plan = payoffPlan();
    plan.payoffDate = null;
    plan.appliedPrepayments = [];
    plan.monthLoanList[1] = { dateFormat: "2026-10-15", repayment: 180, principal: 0, interest: 180 };
    plan.monthLoanList.push({ dateFormat: "2027-04-15", repayment: 0.01, principal: 0.01, interest: 0 });
    const result = summaryFor(plan, "2026-10-22");
    assert.equal(result.monthly, "180.00");
    assert.equal(result.status, "12 万元 / 1 年 / 初始年利率 3.6%");
});
