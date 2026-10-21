const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { join } = require("node:path");
const { test } = require("node:test");
const { runInNewContext } = require("node:vm");

// 只替代绘图和DOM宿主；CSV内容仍由正式 render/exportCsv 路径生成。
async function csvFor(data) {
    let csv;
    const element = () => ({
        dataset: {},
        append() {},
        replaceChildren() {},
        insertRow: element,
        insertCell: element,
        createTHead: element,
        createTBody: element,
        click() {},
    });
    const host = element();
    class Plot { render() {} }
    const sandbox = {
        window: { matchMedia: () => ({ matches: false }) },
        document: {
            createElement: element,
            getElementById: (id) => id === "schedule-table" ? { hidden: true } : host,
            querySelector: () => ({ open: false }),
        },
        G2Plot: { Line: Plot, Pie: Plot, Bar: Plot },
        Blob,
        URL: {
            createObjectURL(blob) { csv = blob; return "blob:test"; },
            revokeObjectURL() {},
        },
        setTimeout() {},
    };
    runInNewContext(readFileSync(join(__dirname, "../../main/resources/static/calculator/js/charts.js"), "utf8"), sandbox);
    sandbox.window.MortgageCharts.render(data, "monthly");
    sandbox.window.MortgageCharts.exportCsv();
    return csv.text();
}

test("结清利息按实际日期和单段日数导出，预还本金不重复计入", async () => {
    const csv = await csvFor({
        loanAmount: 120000,
        totalInterest: 1300,
        totalRepayment: 121300,
        originalTotalInterest: 2340,
        payoffDate: "2024-04-20",
        appliedPrepayments: [{ effectiveMonth: 5, prepaymentDate: "2024-04-20", repayment: 80000 }],
        monthLoanList: [
            { month: 4, dateFormat: "2024-04-15", repayment: 10270, principal: 10000, interest: 270, annualRate: 3.6 },
            { month: 5, dateFormat: "2024-04-20", repayment: 40, principal: 0, interest: 40, annualRate: 3.6,
                interestSegments: [{ days: 5, annualRate: 3.6, interest: 40 }] },
        ],
    });
    const closing = csv.split("\r\n").find((line) => line.includes("结清利息 · 2024-04-20"));
    assert.ok(closing);
    assert.match(closing, /"40\.00","0\.00","40\.00"/);
    assert.match(closing, /5天 × 3\.600% = 40\.00/);
    assert.doesNotMatch(closing, /整月/);
    assert.match(csv, /"提前还款 · 2024-04-20","80000\.00","80000\.00","0\.00"/);
    assert.equal(csv.split("\r\n").filter((line) => line.includes('"80000.00","80000.00"')).length, 1);
    assert.match(csv.split("\r\n")[2], /整月/);
});
