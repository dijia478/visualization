(function (root) {
    "use strict";
    const clone = (value) => JSON.parse(JSON.stringify(value));
    const cents = (value) => Math.round(Number(value || 0) * 100);
    const money = (value) => Number(value || 0).toLocaleString("zh-CN", {
        minimumFractionDigits: 2, maximumFractionDigits: 2,
    });

    function proposalLoan(loan, comparisonDate, proposal) {
        const result = clone(loan);
        result.prepaymentList = (result.prepaymentList || []).map((event) =>
            event.prepaymentDate > comparisonDate && Number(event.repayment) > 0
                ? Object.assign(event, { repayment: 0, repaymentType: 2 }) : event,
        );
        if (proposal && Number(proposal.repayment) > 0)
            result.prepaymentList.push(clone(proposal));
        result.prepayment = result.prepaymentList.length ? 1 : 0;
        return result;
    }

    function comparisonSeries(scenarios, kind, comparisonDate) {
        return scenarios.filter((scenario) => scenario.available && scenario.plan).flatMap((scenario) => {
            const plan = scenario.plan;
            if (kind === "repayment") return plan.monthLoanList
                .filter((row) => row.dateFormat > comparisonDate &&
                    !(row.dateFormat === plan.payoffDate && Number(row.principal) === 0))
                .map((row) => ({ name: scenario.name, date: row.dateFormat, value: Number(row.repayment) }));
            // 期后预还可能已挂到前一账单的余额上，曲线改用真实日期的本金流水。
            const flows = new Map();
            function add(date, principal) {
                flows.set(date, (flows.get(date) || 0) + cents(principal));
            }
            plan.monthLoanList.forEach((row) => add(row.dateFormat, row.principal));
            (plan.appliedPrepayments || []).forEach((event) => add(event.prepaymentDate, event.repayment));
            let balance = cents(plan.loanAmount);
            const dates = Array.from(new Set([comparisonDate, ...flows.keys()])).sort();
            return dates.flatMap((date) => {
                balance -= flows.get(date) || 0;
                return date < comparisonDate ? [] : [{ name: scenario.name, date, value: balance / 100 }];
            });
        });
    }

    function createPlanner(options) {
        const doc = options.document,
            $ = (selector) => doc.querySelector(selector),
            today = options.today;
        let loan = null, revision = 0, pending = null, scenarios = [], comparisonDate,
            chart, chartKind = "repayment";
        const edited = new Set();
        function element(tag, text, className) {
            const node = doc.createElement(tag);
            if (text !== undefined) node.textContent = text;
            if (className) node.className = className;
            return node;
        }
        function stop() {
            revision++;
            if (pending) pending.abort();
            pending = null;
        }
        function busy(value) {
            $("#compare-btn").disabled = value || !loan;
            $("#solve-btn").disabled = value || !loan;
            $("#compare-btn").textContent = value ? "正在计算…" : "比较三种方案 →";
            $("#planning-form").setAttribute("aria-busy", String(value));
        }
        function clear() {
            stop();
            scenarios = [];
            ["#scenario-results", "#scenario-chart-section", "#goal-result", "#planning-error"]
                .forEach((selector) => { $(selector).hidden = true; });
            $("#planning-status").textContent = "";
            busy(false);
        }
        function error(message) {
            $("#planning-error").textContent = message;
            $("#planning-error").hidden = false;
            $("#planning-error").focus();
        }
        function validate(ids) {
            for (const id of ids) {
                const input = $(id);
                if (!input.checkValidity()) {
                    error("请检查" + (doc.querySelector('label[for="' + input.id + '"]')?.textContent || "目标金额或日期") + "，填写有效值后重试。");
                    (input.closest(".date-control")?.querySelector("button") || input).focus();
                    return false;
                }
            }
            return true;
        }
        function adopt(scenario) {
            const proposed = scenario.proposal;
            if (!loan || !proposed || Number(proposed.repayment) <= 0) return;
            options.adopt(proposalLoan(loan, comparisonDate, proposed));
        }
        function adoptButton(scenario) {
            const button = element("button", "采用此方案并重新计算", "secondary-button");
            button.type = "button";
            button.onclick = () => adopt(scenario);
            return button;
        }
        function metric(list, title, value) {
            const pair = element("div");
            pair.append(element("dt", title), element("dd", value));
            list.append(pair);
        }
        function renderScenarios() {
            const host = $("#scenario-results");
            host.replaceChildren();
            scenarios.forEach((scenario) => {
                const card = element("article", undefined, "scenario-card");
                card.dataset.key = scenario.key;
                card.append(element("h4", scenario.name));
                if (!scenario.available) {
                    card.append(element("p", scenario.message || "当前条件无法采用该方案", "field-hint"));
                } else {
                    const payment = element("p", money(scenario.nextMonthlyPayment), "scenario-payment");
                    payment.append(element("small", "元"));
                    card.append(payment, element("p", scenario.nextPaymentDate
                        ? "首期月供 · " + scenario.nextPaymentDate : "已结清，无后续月供", "scenario-caption"));
                    const list = element("dl", undefined, "scenario-metrics");
                    metric(list, "未来还需利息", money(scenario.futureInterest) + " 元");
                    metric(list, "未来一年总支出（含预还）", money(scenario.nextYearPayment) + " 元");
                    metric(list, "预计还清日期", scenario.payoffDate || "—");
                    metric(list, "相比不新增提前还款", (Number(scenario.savedInterest) < 0 ? "多付利息 " : "少付利息 ") + money(Math.abs(Number(scenario.savedInterest))) + " 元");
                    metric(list, "还款期限", Number(scenario.monthsSaved) === 0 ? "期数不变"
                        : (Number(scenario.monthsSaved) > 0 ? "减少 " : "增加 ") + Math.abs(Number(scenario.monthsSaved)) + " 期");
                    card.append(list);
                    if (scenario.proposal && Number(scenario.proposal.repayment) > 0)
                        card.append(adoptButton(scenario));
                }
                host.append(card);
            });
            host.hidden = false;
            renderChart();
        }
        function renderChart() {
            const data = comparisonSeries(scenarios, chartKind, comparisonDate),
                section = $("#scenario-chart-section");
            section.hidden = !data.length;
            if (!data.length || !root.G2Plot) return;
            const config = {
                data, xField: "date", yField: "value", seriesField: "name",
                meta: { date: { type: "time" } },
                animation: false, smooth: false, autoFit: true,
                color: ["#9aa89e", "#28745d", "#a58049"],
                legend: { position: "bottom", flipPage: false, maxHeightRatio: 0.35 },
                padding: "auto", appendPadding: [12, 12, 0, 0],
                xAxis: { tickCount: 4, label: { formatter: (value) => {
                    const date = new Date(/^\d+$/.test(String(value)) ? Number(value) : value);
                    return Number.isNaN(date.getTime()) ? String(value).slice(0, 7) : date.toISOString().slice(0, 7);
                }, autoHide: true } },
                yAxis: { min: 0, label: { formatter: (value) => Number(value) >= 10000
                    ? (Number(value) / 10000).toFixed(1) + "万" : String(value) } },
                tooltip: { formatter: (item) => ({ name: item.name, value: money(item.value) + " 元" }) },
                lineStyle: ({ name }) => ({ lineWidth: 2, lineDash: name === "不新增提前还款" ? [4, 4] : [] }),
            };
            if (chart) chart.update(config);
            else { chart = new root.G2Plot.Line("scenario-chart", config); chart.render(); }
        }
        function renderSolution(result) {
            const host = $("#goal-result");
            host.replaceChildren();
            if (result.available === false) {
                error(result.message || "该目标在当前条件下无法实现。");
                return;
            }
            const scenario = result.scenario;
            host.append(element("p", Number(result.repayment) === 0
                ? "当前计划已经满足目标" : "达到目标，测算建议提前还本"));
            host.append(element("strong", money(Number(result.repayment) * 10000) + " 元"));
            host.append(element("p", "首期月供 " + money(scenario.nextMonthlyPayment) + " 元" +
                (scenario.nextPaymentDate ? "（" + scenario.nextPaymentDate + "）" : "（无后续月供）") +
                "；预计 " + scenario.payoffDate + " 还清。"));
            if (scenario.proposal && Number(result.repayment) > 0)
                host.append(adoptButton(scenario));
            host.append(element("p", "建议金额已按当前算法校验达标，精确到分；分币舍入可能使建议本金略高于绝对最小值。结清当期利息另计。", "field-hint"));
            host.hidden = false;
        }
        async function run(mode) {
            if (!loan) return;
            clear();
            const goal = $("#planning-goal").value,
                ids = mode === "compare" ? ["#planning-date", "#planning-amount"]
                    : ["#planning-date", goal === "PAYMENT" ? "#target-payment" : "#target-payoff"];
            if (!validate(ids)) return;
            const controller = new AbortController(), token = revision;
            pending = controller;
            comparisonDate = today();
            const body = { loan: clone(loan), comparisonDate, prepaymentDate: $("#planning-date").value };
            if (mode === "compare") body.repayment = Number($("#planning-amount").value);
            else {
                body.goal = goal;
                if (goal === "PAYMENT") body.targetPayment = Number($("#target-payment").value);
                else body.targetPayoffDate = $("#target-payoff").value;
            }
            busy(true);
            $("#planning-status").textContent = mode === "compare" ? "正在比较三种还款选择…" : "正在按分查找满足目标的提前还款额…";
            try {
                const result = await options.request("/calculator/planning/" + mode, body, controller.signal);
                if (pending !== controller || token !== revision) return;
                if (mode === "compare") {
                    scenarios = result.scenarios;
                    renderScenarios();
                } else renderSolution(result);
                $("#planning-status").textContent = "已按 " + comparisonDate + " 的历史计划完成测算。";
            } catch (failure) {
                if (failure.name !== "AbortError" && pending === controller && token === revision) {
                    error(failure.message);
                    $("#planning-status").textContent = "计算未完成，请调整条件后重试。";
                }
            } finally {
                if (pending === controller) { pending = null; busy(false); }
            }
        }
        function setLoan(value, plan) {
            clear();
            loan = clone(value);
            const now = today(), firstDate = value.firstPaymentDate;
            $("#planning-date").min = now > firstDate ? now : firstDate;
            if (!edited.has("planning-date")) $("#planning-date").value = $("#planning-date").min;
            const lastDate = plan.payoffDate || plan.monthLoanList[plan.monthLoanList.length - 1].dateFormat,
                next = plan.monthLoanList.find((row) => row.dateFormat > now &&
                    !(row.dateFormat === plan.payoffDate && Number(row.principal) === 0));
            if (!edited.has("target-payoff")) $("#target-payoff").value = lastDate;
            if (!edited.has("target-payment"))
                $("#target-payment").value = (Number(next?.repayment || 0) * 0.8).toFixed(2);
            $("#target-payoff").min = $("#planning-date").value;
            ["#planning-date", "#target-payoff"].forEach((id) => options.updateDate($(id)));
            $("#planning-context").textContent = "以今天（" + now + "）为比较起点。已经发生的提前还款保留，未来提前还本计划由本次选择替代。";
            busy(false);
        }
        function invalidate() {
            loan = null;
            clear();
            $("#planning-context").textContent = "贷款条件已修改，请先重新计算贷款方案。";
        }
        $("#planning-form").addEventListener("submit", (event) => { event.preventDefault(); run("compare"); });
        $("#solve-btn").onclick = () => run("solve");
        function remember(event) {
            if (event?.target) edited.add(event.target.id);
        }
        $("#planning-form").addEventListener("input", (event) => { remember(event); clear(); });
        $("#planning-form").addEventListener("change", (event) => {
            remember(event);
            clear();
            const paymentGoal = $("#planning-goal").value === "PAYMENT";
            $("#target-payment-field").hidden = !paymentGoal;
            $("#target-payoff-field").hidden = paymentGoal;
            $("#target-payment").disabled = !paymentGoal;
            $("#target-payoff").disabled = paymentGoal;
            $("#target-payoff").min = $("#planning-date").value;
            options.updateDate($("#target-payoff"));
        });
        doc.querySelectorAll("[data-comparison-trend]").forEach((button) => {
            button.onclick = () => {
                chartKind = button.dataset.comparisonTrend;
                doc.querySelectorAll("[data-comparison-trend]").forEach((item) =>
                    item.setAttribute("aria-pressed", String(item === button)));
                renderChart();
            };
        });
        return { setLoan, invalidate };
    }

    const api = { proposalLoan, comparisonSeries, createPlanner };
    if (typeof module !== "undefined" && module.exports) module.exports = api;
    else root.MortgagePlanning = api;
})(typeof window === "undefined" ? globalThis : window);
