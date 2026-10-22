const { test } = require("node:test");
const assert = require("node:assert/strict");
const { join } = require("node:path");
const planning = () => require(join(__dirname, "../../main/resources/static/calculator/js/planning.js"));

test("采用方案保留历史预还与未来利率变化，只替换未来提前还本，且不修改原输入", () => {
    const loan = { type: 1, prepaymentList: [
        { prepaymentDate: "2025-08-04", repayment: 10, newType: 1, repaymentType: 2 },
        { prepaymentDate: "2026-10-22", repayment: 2.123456, newType: 1, repaymentType: 2 },
        { prepaymentDate: "2027-08-19", repayment: 15, newType: 2, newRate: 3.1, repaymentType: 1 },
    ] };
    const before = JSON.stringify(loan);
    const proposal = { prepaymentDate: "2026-11-01", repayment: 12.345678, newType: 1, newRate: 3.3, repaymentType: 2 };
    const result = planning().proposalLoan(loan, "2026-10-22", proposal);
    assert.equal(JSON.stringify(loan), before);
    assert.equal(result.prepaymentList[0].repayment, 10);
    assert.equal(result.prepaymentList[1].repayment, 2.123456);
    assert.deepEqual(result.prepaymentList[2], { ...loan.prepaymentList[2], repayment: 0, repaymentType: 2 });
    assert.deepEqual(result.prepaymentList[3], proposal);
    result.prepaymentList[0].repayment = 0;
    assert.equal(loan.prepaymentList[0].repayment, 10);
});

test("方案剩余本金曲线按真实日期扣除预还，不把期后预还提前画在账单日", () => {
    const plan = { loanAmount: 120000, monthLoanList: [
        { dateFormat: "2026-10-15", principal: 10000, repayment: 10200, remainPrincipal: 90000 },
        { dateFormat: "2026-11-15", principal: 10000, repayment: 10150, remainPrincipal: 80000 },
    ], appliedPrepayments: [{ prepaymentDate: "2026-10-25", repayment: 20000 }] };
    const points = planning().comparisonSeries([{ key: "reduce", name: "减少月供", available: true, plan }], "principal", "2026-10-22");
    assert.deepEqual(points.map(({ date, value }) => [date, value]), [
        ["2026-10-22", 110000], ["2026-10-25", 90000], ["2026-11-15", 80000],
    ]);
});

test("对比月供排除历史和实际结清利息，正常零本金账单仍保留", () => {
    const plan = { loanAmount: 10000, payoffDate: "2026-12-05", monthLoanList: [
        { dateFormat: "2026-09-15", repayment: 1000, principal: 900 },
        { dateFormat: "2026-11-15", repayment: 0.01, principal: 0 },
        { dateFormat: "2026-12-05", repayment: 30, principal: 0 },
    ] };
    const scenarios = [
        { key: "baseline", name: "暂不提前还", available: true, plan },
        { key: "shorten", name: "缩短期限", available: false },
    ];
    const points = planning().comparisonSeries(scenarios, "repayment", "2026-10-22");
    assert.equal(points.length, 1);
    assert.equal(points[0].date, "2026-11-15");
    assert.equal(points[0].value, 0.01);
});

function client(request) {
    const nodes = new Map();
    function element(id = "") {
        return {
            id, value: "", children: [], dataset: {}, hidden: false, disabled: false, textContent: "",
            handlers: {}, attributes: {},
            append(...items) { this.children.push(...items); },
            replaceChildren() { this.children = []; },
            setAttribute(name, value) { this.attributes[name] = value; },
            addEventListener(name, handler) { this.handlers[name] = handler; },
            checkValidity() { return true; }, focus() {}, closest() { return null; },
        };
    }
    function node(selector) {
        if (!nodes.has(selector)) nodes.set(selector, element(selector.slice(1)));
        return nodes.get(selector);
    }
    node("#planning-goal").value = "PAYMENT";
    const api = planning().createPlanner({
        document: { querySelector: node, querySelectorAll: () => [], createElement: () => element() },
        request, today: () => "2026-10-22", updateDate() {}, adopt() {},
    });
    api.setLoan({ amount: 90, type: 1, firstPaymentDate: "2021-08-27", prepaymentList: [] }, {
        monthLoanList: [{ dateFormat: "2026-10-27", principal: 1000, repayment: 3000 }],
    });
    return { api, node, submit: () => node("#planning-form").handlers.submit({ preventDefault() {} }) };
}

test("贷款条件改变后，已经发出的比较请求即使返回也不能覆盖页面", async () => {
    let resolve, signal;
    const { api, node, submit } = client((url, body, abortSignal) => {
        signal = abortSignal;
        return new Promise((done) => { resolve = done; });
    });
    submit();
    assert.equal(node("#compare-btn").disabled, true);
    api.invalidate();
    assert.equal(signal.aborted, true);
    resolve({ scenarios: [{ key: "baseline", name: "过期结果", available: false }] });
    await new Promise(setImmediate);
    assert.equal(node("#scenario-results").hidden, true);
    assert.equal(node("#scenario-results").children.length, 0);
    assert.equal(node("#compare-btn").disabled, true);
});

test("反推金额或日期编辑会取消进行中的请求，且不允许旧结果重新出现", async () => {
    let resolve;
    const { node, submit } = client(() => new Promise((done) => { resolve = done; }));
    submit();
    node("#planning-form").handlers.input();
    resolve({ scenarios: [{ key: "baseline", name: "旧方案", available: false }] });
    await new Promise(setImmediate);
    assert.equal(node("#scenario-results").hidden, true);
    assert.equal(node("#compare-btn").disabled, false);
    assert.equal(node("#planning-status").textContent, "");
});

test("重新计算贷款后默认目标随新计划更新，用户手填的目标保留", () => {
    const { api, node } = client(() => {});
    assert.equal(node("#target-payment").value, "2400.00");
    const loan = { firstPaymentDate: "2021-08-27", prepaymentList: [] },
        plan = { monthLoanList: [{ dateFormat: "2027-01-27", principal: 100, repayment: 2000 }] };
    api.setLoan(loan, plan);
    assert.equal(node("#target-payment").value, "1600.00");
    assert.equal(node("#target-payoff").value, "2027-01-27");
    node("#target-payment").value = "1234.56";
    node("#planning-form").handlers.input({ target: node("#target-payment") });
    api.setLoan(loan, { monthLoanList: [{ dateFormat: "2027-02-27", principal: 100, repayment: 2500 }] });
    assert.equal(node("#target-payment").value, "1234.56");
    assert.equal(node("#target-payoff").value, "2027-02-27");
});
