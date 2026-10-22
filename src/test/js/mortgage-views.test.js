const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { join } = require("node:path");
const { test } = require("node:test");
const { runInNewContext } = require("node:vm");

function chartsFor(data, date = "2026-10-22") {
    const nodes = new Map(), plots = new Map();
    function element(tag) {
        return { tagName: tag, children: [], dataset: {}, hidden: false, attributes: {}, textContent: "", clientWidth: 0,
            append(...items) { this.children.push(...items); },
            replaceChildren(...items) { this.children = items; },
            setAttribute(key, value) { this.attributes[key] = value; },
            getAttribute(key) { return this.attributes[key]; },
            insertRow() { const item = element("tr"); this.append(item); return item; },
            insertCell() { const item = element("td"); this.append(item); return item; },
            createTHead() { const item = element("thead"); this.append(item); return item; },
            createTBody() { const item = element("tbody"); this.append(item); return item; },
            click() {}, scrollIntoView() {},
        };
    }
    function node(id) { if (!nodes.has(id)) nodes.set(id, element("div")); return nodes.get(id); }
    class Plot {
        constructor(id, options) { this.options = options; plots.set(id, this); }
        render() {}
        update(options) { this.options = options; }
        changeData(data) { this.options.data = data; }
    }
    const sandbox = {
        window: { matchMedia: () => ({ matches: true }), MortgageDatePicker: { today: () => date } },
        document: { createElement: element, getElementById: node, querySelector: () => ({ open: false }), querySelectorAll: () => [] },
        G2Plot: { Line: Plot, Pie: Plot, Bar: Plot },
    };
    for (const file of ["schedule.js", "charts.js"]) runInNewContext(readFileSync(join(__dirname, "../../main/resources/static/calculator/js", file), "utf8"), sandbox);
    sandbox.window.MortgageCharts.render(data, "monthly");
    const textOf = (item) => item.textContent + item.children.map(textOf).join(" ");
    return { api: sandbox.window.MortgageCharts, node, plots, textOf };
}
const row = (month, date, principal, interest) => ({ month, dateFormat: date, principal, interest, repayment: principal + interest, annualRate: 3.5 });
function plan() { return {
    loanAmount: 20000, totalInterest: 1000, totalRepayment: 21000, originalTotalInterest: 1200,
    monthLoanList: [row(1, "2026-09-27", 1000, 300), row(2, "2026-10-27", 1000, 280), row(3, "2027-10-27", 1000, 200)],
    appliedPrepayments: [{ prepaymentDate: "2026-10-04", repayment: 5000 }],
    rateChanges: [{ effectiveDate: "2026-10-01", effectiveMonth: 2, annualRate: 3.5, reason: "LPR_REPRICING" }],
}; }

test("默认完整明细，近期卡片仍按实际日期显示提前还本并保留文本表格选择", () => {
    const host = chartsFor(plan());
    assert.equal(host.api.getViewInfo().view, "all");
    assert.equal(host.node("schedule-summary").hidden, true);
    assert.equal(host.node("schedule-table").hidden, false);
    host.api.setView("recent");
    assert.equal(host.node("schedule-table").hidden, true);
    assert.equal(host.node("accessible-table").hidden, true);
    assert.equal(host.node("schedule-summary").hidden, false);
    const text = host.textOf(host.node("schedule-summary"));
    assert.match(text, /2026 年 10 月/);
    assert.match(text, /6,280\.00/);
    assert.match(text, /5,000\.00/);
    assert.doesNotMatch(text, /2026 年 09 月/);
    host.node("table-mode").setAttribute("aria-pressed", "true");
    host.api.setView("all");
    assert.equal(host.node("schedule-summary").hidden, true);
    assert.equal(host.node("accessible-table").hidden, false);
    assert.equal(host.node("schedule-table").hidden, true);
    host.api.setView("annual");
    assert.equal(host.node("accessible-table").hidden, true);
    assert.match(host.textOf(host.node("schedule-summary")), /年末剩余本金/);
    assert.match(host.api.getViewInfo().note, /实际还款日期/);
    host.api.jumpToCurrent();
    assert.equal(host.api.getViewInfo().view, "recent");
});

test("趋势以实际日期为横轴并标注今天和事件，切换范围会刷新图表配置", () => {
    const host = chartsFor(plan());
    const initial = host.plots.get("trend-chart").options;
    assert.equal(initial.xField, "date");
    assert.equal(initial.meta.date.type, "time");
    assert.ok(initial.annotations.some(annotation => annotation.text?.content === "今天"));
    assert.equal(initial.annotations.some(annotation => annotation.start[0] === "2026-10-04"), false);
    host.api.setRange("year");
    const future = host.plots.get("trend-chart").options;
    assert.equal(future.data.length, 3);
    assert.equal(future.data[0].date, "2026-10-27");
    // G2Plot只向formatter传递tooltip.fields中的字段，不能直接用原始数据假装库调用。
    const formatterInput = Object.fromEntries((future.tooltip.fields || ["date", "value", "name"])
        .map(field => [field, future.data[0][field]]));
    const tooltip = future.tooltip.formatter(formatterInput);
    assert.match(tooltip.name, /第2期.*未来计划/);
    assert.doesNotMatch(tooltip.name, /undefined/);
    host.api.setRange("five");
    assert.equal(host.plots.get("trend-chart").options.data.length, 6);
    assert.equal(host.plots.get("composition-chart").options.data[1].name, "总利息");
    assert.match(host.textOf(host.node("timeline-list")), /较上期少付 ¥ 20\.00/);
});

test("事件标记可点选且只高亮所选事件，提供日期、利率及账单变化说明", () => {
    const host = chartsFor(plan()), events = host.node("trend-event-list");
    assert.equal(events.children.length, 2);
    assert.match(host.textOf(events), /2026-10-01.*LPR 重定价/);
    assert.match(host.textOf(events), /2026-10-04.*提前还款/);
    events.children[1].onclick();
    const options = host.plots.get("trend-chart").options;
    assert.equal(options.annotations.filter(item => item.start[0] === "2026-10-04").length, 1);
    assert.equal(options.annotations.filter(item => item.start[0] === "2026-10-01").length, 0);
    assert.ok(options.annotations.some(item => item.text?.content === "提前还款" && item.text.autoRotate === false));
    assert.match(host.textOf(host.node("trend-event-detail")), /2026-10-04.*提前还款.*5,000\.00/);
    assert.match(host.textOf(host.node("trend-event-detail")), /首期账单.*2026-10-27/);
    host.api.setRange("year");
    assert.equal(host.node("trend-event-controls").hidden, true);
    assert.equal(host.plots.get("trend-chart").options.annotations.some(item => item.start[0] === "2026-10-04"), false);
});

test("时间轴及事件视图展示真实生效日，结清利息不会作为预还后的月供", () => {
    const data = plan();
    data.payoffDate = "2026-10-20";
    data.appliedPrepayments.push({ prepaymentDate: "2026-10-20", repayment: 14000 });
    data.monthLoanList = [data.monthLoanList[0], row(2, "2026-10-20", 0, 28)];
    const host = chartsFor(data);
    host.api.setView("events");
    const text = host.textOf(host.node("schedule-summary"));
    assert.match(text, /2026-10-01.*LPR 重定价/);
    assert.match(text, /2026-10-20.*提前还款/);
    assert.doesNotMatch(text, /首期账单.*28\.00/);
    assert.match(host.textOf(host.node("timeline-list")), /预计还清/);
    assert.match(host.api.getViewInfo().note, /历史金额按计划测算/);
});

test("余额与累计趋势包含实际预还当日，提前结清利息不显示成正常月供", () => {
    const data = {
        loanAmount: 20000, totalInterest: 330, totalRepayment: 20330, originalTotalInterest: 400,
        payoffDate: "2026-02-20",
        monthLoanList: [
            { ...row(1, "2026-01-15", 1000, 200), remainPrincipal: 14000, totalPrincipal: 6000 },
            row(2, "2026-02-15", 1000, 100), row(3, "2026-02-20", 0, 30),
        ],
        appliedPrepayments: [
            { prepaymentDate: "2026-01-25", repayment: 5000 },
            { prepaymentDate: "2026-02-20", repayment: 13000 },
        ],
    };
    const host = chartsFor(data, "2026-01-22");
    assert.equal(host.plots.get("trend-chart").options.data.some(point => point.date === "2026-02-20"), false);
    host.api.trend("remaining");
    let options = host.plots.get("trend-chart").options;
    assert.equal(options.stepType, "hv");
    const principal = options.data.filter(point => point.name === "剩余本金");
    assert.deepEqual(Array.from(principal, point => [point.date, point.value]), [
        ["2026-01-15", 19000], ["2026-01-25", 14000], ["2026-02-15", 13000], ["2026-02-20", 0],
    ]);
    assert.match(options.tooltip.formatter(principal[1]).name, /提前还款/);
    host.api.trend("cumulative");
    options = host.plots.get("trend-chart").options;
    const paid = options.data.filter(point => point.name === "累计已还总额");
    assert.deepEqual(Array.from(paid, point => [point.date, point.value]), [
        ["2026-01-15", 1200], ["2026-01-25", 6200], ["2026-02-15", 7300], ["2026-02-20", 20330],
    ]);
    host.api.setRange("year");
    assert.ok(host.plots.get("trend-chart").options.data.some(point => point.date === "2026-01-25"));
    assert.match(host.textOf(host.node("timeline-list")), /结清利息.*30\.00/);
});
