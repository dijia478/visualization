/* Local chart libraries are shipped with the application. No CDN requests. */
(function () {
    "use strict";
    const touchLayout = () =>
        window.matchMedia("(max-width: 767px), (pointer: coarse)").matches;
    const palette = ["#28745d", "#c5a270", "#7196a1", "#9b87ad"];
    const labels = {
        month: "期数 / 日期",
        repayment: "当月还款",
        principal: "其中本金",
        interest: "其中利息",
        remainTotal: "剩余贷款",
        remainPrincipal: "剩余本金",
        remainInterest: "剩余利息",
        totalRepayment: "累计已还总额",
        totalPrincipal: "累计已还本金",
        totalInterest: "累计已还利息",
        annualRate: "执行年利率",
        interestBasis: "当期计息",
    };
    const columns = Object.keys(labels);
    const plots = new Map();
    let sheet,
        observer,
        current,
        trendKind,
        scheduleView = "all",
        trendRange = "all",
        selectedTrendEvent = null,
        breakdownMonths,
        sheetRows,
        sheetWidth = 0,
        resizeTimer,
        rows = [];
    const money = (n) =>
        Number(n || 0).toLocaleString("zh-CN", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
        });
    function mount(id, Kind, options, updateOptions = false) {
        const existing = plots.get(id);
        if (existing) {
            if (updateOptions) existing.update(options);
            else existing.changeData(options.data);
            return;
        }
        const theme = {
            styleSheet: {
                fontFamily:
                    "-apple-system, BlinkMacSystemFont, PingFang SC, Microsoft YaHei, sans-serif",
                brandColor: palette[0],
            },
        };
        const plot = new Kind(
            id,
            Object.assign(
                {
                    autoFit: true,
                    animation: false,
                    color: palette,
                    theme,
                },
                options,
            ),
        );
        plots.set(id, plot);
        plot.render();
    }
    function today() {
        if (window.MortgageDatePicker) return window.MortgageDatePicker.today();
        const date = new Date();
        return date.getFullYear() + "-" + String(date.getMonth() + 1).padStart(2, "0") + "-" + String(date.getDate()).padStart(2, "0");
    }
    function trend(kind) {
        if (!current) return;
        const fields =
            kind === "remaining"
                ? [
                      ["remainTotal", "剩余贷款"],
                      ["remainPrincipal", "剩余本金"],
                      ["remainInterest", "剩余利息"],
                  ]
                : kind === "cumulative"
                  ? [
                        ["totalRepayment", "累计已还总额"],
                        ["totalPrincipal", "累计已还本金"],
                        ["totalInterest", "累计已还利息"],
                        [
                            "totalRepaymentAndRemainPrincipal",
                            "累计已还总额 + 剩余本金",
                        ],
                    ]
                  : [
                        ["repayment", "当月还款"],
                        ["principal", "其中本金"],
                        ["interest", "其中利息"],
                    ];
        const progress = kind === "remaining" || kind === "cumulative";
        let visibleRows = progress ? window.MortgageSchedule.progress(current)
            : current.monthLoanList.filter(row => !window.MortgageSchedule.isSettlement(current, row));
        if (trendRange !== "all") {
            const range = window.MortgageSchedule.recent(current, today(), trendRange === "five" ? 60 : 12);
            visibleRows = visibleRows.filter(row => row.dateFormat.slice(0, 7) >= range.start && row.dateFormat.slice(0, 7) < range.end);
        }
        const data = visibleRows.flatMap((row) =>
            fields.map(([field, name]) => ({
                month: String(row.month),
                periodLabel: row.periodLabel || "第" + row.month + "期",
                date: row.dateFormat,
                phase: row.dateFormat <= today() ? "历史测算" : "未来计划",
                name,
                value: Number(row[field]),
            })),
        );
        const events = visibleTrendEvents(visibleRows);
        if (!events.some(event => event.key === selectedTrendEvent)) selectedTrendEvent = null;
        renderTrendEvents(events, visibleRows);
        mount(
            "trend-chart",
            G2Plot.Line,
            {
                data,
                xField: "date",
                meta: { date: { type: "time" } },
                yField: "value",
                seriesField: "name",
                smooth: false,
                stepType: progress ? "hv" : "line",
                padding: "auto",
                appendPadding: [12, 14, 0, 0],
                legend: {
                    position: "bottom",
                    maxHeightRatio: touchLayout() ? 0.4 : 0.22,
                    flipPage: false,
                    itemMarginBottom: 8,
                    itemName: { style: { fontSize: 12, fill: "#65746f" } },
                },
                xAxis: {
                    tickCount: 6,
                    label: {
                        formatter: (value) => {
                            const date = new Date(/^\d+$/.test(String(value)) ? Number(value) : value);
                            return Number.isNaN(date.getTime()) ? String(value).slice(0, 7) : date.toISOString().slice(0, 7);
                        },
                        autoHide: true,
                    },
                },
                yAxis: {
                    tickCount: 5,
                    label: {
                        formatter: (v) =>
                            Number(v) >= 10000
                                ? (Number(v) / 10000).toFixed(1) + "万"
                                : Number(v).toLocaleString("zh-CN"),
                    },
                },
                tooltip: {
                    showTitle: true,
                    title: "date",
                    fields: ["date", "value", "name", "periodLabel", "phase"],
                    formatter: (item) => ({
                        name: item.name + " · " + item.periodLabel + " · " + item.phase,
                        value: "¥ " + money(item.value),
                    }),
                },
                interactions: touchLayout() ? [] : [{ type: "brush" }],
                lineStyle: { lineWidth: 2 },
                annotations: trendAnnotations(visibleRows, events),
            },
            true,
        );
        trendKind = kind;
    }
    function visibleTrendEvents(visibleRows) {
        if (!visibleRows.length) return [];
        const first = visibleRows[0].dateFormat, last = visibleRows[visibleRows.length - 1].dateFormat;
        return window.MortgageSchedule.events(current, today())
            .map((event, index) => Object.assign(event, { key: "trend-event-" + index }))
            .filter(event => ["rate", "prepayment"].includes(event.kind) && event.date >= first && event.date <= last);
    }
    function renderTrendEvents(events, visibleRows) {
        const controls = document.getElementById("trend-event-controls"),
            host = document.getElementById("trend-event-list"),
            detail = document.getElementById("trend-event-detail"),
            note = document.getElementById("trend-marker-note");
        if (!controls || !host || !detail) return;
        controls.hidden = !events.length;
        host.replaceChildren();
        detail.replaceChildren();
        const selected = events.find(event => event.key === selectedTrendEvent);
        detail.hidden = !selected;
        events.forEach(event => {
            const button = element("button", event.date + " · " + event.title, "trend-event-button");
            button.id = event.key;
            button.type = "button";
            button.dataset.kind = event.kind;
            button.setAttribute("aria-pressed", String(event.key === selectedTrendEvent));
            button.setAttribute("aria-controls", "trend-event-detail");
            button.onclick = () => {
                selectedTrendEvent = selectedTrendEvent === event.key ? null : event.key;
                trend(trendKind || "monthly");
                document.getElementById(event.key)?.focus?.();
            };
            host.append(button);
        });
        if (selected) detail.append(eventList([selected]));
        if (note) {
            const todayVisible = visibleRows.length && today() >= visibleRows[0].dateFormat && today() <= visibleRows[visibleRows.length - 1].dateFormat;
            note.textContent = (todayVisible ? "灰色虚线表示今天。" : "") +
                (events.length ? "点选下方事件，高亮对应日期并查看利率与月供变化。" : "当前范围没有调息或提前还款事件。");
        }
    }
    function trendAnnotations(visibleRows, events) {
        if (!visibleRows.length) return [];
        const first = visibleRows[0].dateFormat, last = visibleRows[visibleRows.length - 1].dateFormat;
        const annotations = [], selected = events.find(event => event.key === selectedTrendEvent);
        if (today() >= first && today() <= last && selected?.date !== today()) annotations.push({
            type: "line", start: [today(), "min"], end: [today(), "max"],
            style: { stroke: "#65746f", lineDash: [4, 4] },
            text: { content: "今天", position: "end", autoRotate: false, style: { fill: "#65746f", fontSize: 12 } },
        });
        if (selected) annotations.push({
            type: "line", start: [selected.date, "min"], end: [selected.date, "max"],
            style: { stroke: selected.kind === "rate" ? "#526d76" : "#946824", lineDash: [4, 4], lineWidth: 2 },
            text: { content: (selected.date === today() ? "今天 · " : "") + selected.title,
                position: "end", autoRotate: false, style: { fill: "#203b36", fontSize: 12 } },
        });
        return annotations;
    }
    function setRange(range) {
        if (!["year", "five", "all"].includes(range)) return;
        trendRange = range;
        if (current) trend(trendKind || "monthly");
    }
    function composition() {
        const data = [
            { name: "贷款本金", value: Number(current.loanAmount) },
            { name: "总利息", value: Number(current.totalInterest) },
        ];
        mount(
            "composition-chart",
            G2Plot.Pie,
            {
                data,
                angleField: "value",
                colorField: "name",
                radius: 0.9,
                innerRadius: 0.77,
                padding: [8, 0, 28, 0],
                legend: {
                    position: "bottom",
                    itemName: { style: { fill: "#65746f", fontSize: 12 } },
                },
                label: false,
                tooltip: {
                    formatter: (item) => ({
                        name: item.name,
                        value: "¥ " + money(item.value),
                    }),
                },
                statistic: {
                    title: {
                        content: "利息占比",
                        style: { fontSize: "12px", color: "#65746f" },
                    },
                    content: {
                        content:
                            (current.totalRepayment > 0
                                ? (current.totalInterest /
                                      current.totalRepayment) *
                                  100
                                : 0
                            ).toFixed(1) + "%",
                        style: {
                            fontSize: "30px",
                            color: "#203b36",
                            fontWeight: 500,
                        },
                    },
                },
                interactions: [{ type: "element-active" }],
            },
            true,
        );
        const original = Number(current.originalTotalInterest || 0),
            actual = Number(current.totalInterest || 0);
        mount("comparison-chart", G2Plot.Bar, {
            data: [
                { name: "不提前还款", value: original },
                { name: "当前方案", value: actual },
            ],
            xField: "value",
            yField: "name",
            seriesField: "name",
            color: ["#cbd5c8", palette[0]],
            legend: false,
            barWidthRatio: 0.35,
            padding: [14, 64, 34, 78],
            xAxis: {
                tickCount: 3,
                label: {
                    formatter: (v) => (Number(v) / 10000).toFixed(0) + "万",
                    style: { fontSize: 12 },
                },
            },
            yAxis: { label: { style: { fontSize: 12, fill: "#65746f" } } },
            label: {
                position: "right",
                formatter: (row) => (row.value / 10000).toFixed(2) + "万",
                style: { fill: "#203b36", fontSize: 12 },
            },
            tooltip: {
                formatter: (item) => ({
                    name: item.name,
                    value: "¥ " + money(item.value),
                }),
            },
        });
    }
    function breakdown() {
        if (!current) return;
        const data = current.monthLoanList.flatMap((row) => [
            {
                month: row.month + "期",
                name: "本金",
                value: Number(row.principal),
            },
            {
                month: row.month + "期",
                name: "利息",
                value: Number(row.interest),
            },
        ]);
        mount(
            "breakdown-chart",
            G2Plot.Column,
            {
                data,
                isStack: true,
                xField: "month",
                yField: "value",
                seriesField: "name",
                legend: { position: "top" },
                label: false,
                slider: {
                    start: 0,
                    end: Math.min(1, 24 / current.monthLoanList.length),
                },
                tooltip: {
                    formatter: (item) => ({
                        name: item.name,
                        value: "¥ " + money(item.value),
                    }),
                },
                xAxis: { label: { autoHide: true } },
                yAxis: {
                    label: {
                        formatter: (v) => Number(v).toLocaleString("zh-CN"),
                    },
                },
            },
            current.monthLoanList.length !== breakdownMonths,
        );
        breakdownMonths = current.monthLoanList.length;
    }
    function makeRows(data) {
        const result = [
            {
                month: "总计",
                repayment: Number(data.totalRepayment).toFixed(2),
                principal: Number(data.loanAmount).toFixed(2),
                interest: Number(data.totalInterest).toFixed(2),
                event: "total",
            },
        ];
        const changes = data.rateChanges || [];
        const events = data.appliedPrepayments || [];
        const reasonLabels = {
            LPR_REPRICING: "LPR 重定价",
            POLICY_ADJUSTMENT: "存量房贷调息",
            MANUAL: "手动利率",
        };
        data.monthLoanList.forEach((row) => {
            changes
                .filter((change) => Number(change.effectiveMonth) === row.month)
                .forEach((change) => {
                    result.push({
                        month:
                            (reasonLabels[change.reason] || "利率调整") +
                            " · " +
                            change.effectiveDate,
                        annualRate: Number(change.annualRate).toFixed(3) + "%",
                        event: "rate",
                    });
                });
            const settlement = row.dateFormat === data.payoffDate && Number(row.principal) === 0;
            const entry = {
                month: settlement ? "结清利息 · " + row.dateFormat : row.month + "（" + row.dateFormat + "）",
            };
            columns.slice(1).forEach((key) => {
                if (key === "annualRate")
                    entry[key] = Number(row.annualRate).toFixed(3) + "%";
                else if (key === "interestBasis")
                    entry[key] =
                        row.interestSegments?.length && (row.interestSegments.length > 1 || settlement)
                            ? row.interestSegments
                                  .map(
                                      (segment) =>
                                          segment.days +
                                          "天 × " +
                                          Number(segment.annualRate).toFixed(
                                              3,
                                          ) +
                                          "% = " +
                                          money(segment.interest),
                                  )
                                  .join("；")
                            : "整月";
                else entry[key] = Number(row[key] || 0).toFixed(2);
            });
            result.push(entry);
            events
                .filter(
                    (event) => Number(event.effectiveMonth) === row.month + 1,
                )
                .forEach((event) =>
                    result.push({
                        month: "提前还款 · " + event.prepaymentDate,
                        repayment: Number(event.repayment).toFixed(2),
                        principal: Number(event.repayment).toFixed(2),
                        interest: "0.00",
                        event: "prepayment",
                    }),
                );
        });
        return result;
    }
    function renderTextTable() {
        const host = document.getElementById("accessible-table");
        host.replaceChildren();
        const table = document.createElement("table"),
            head = table.createTHead().insertRow(),
            body = table.createTBody();
        columns.forEach((key) => {
            const cell = document.createElement("th");
            cell.scope = "col";
            cell.textContent = labels[key];
            head.append(cell);
        });
        rows.forEach((row) => {
            const tr = body.insertRow();
            if (row.event) tr.dataset.event = row.event;
            columns.forEach((key) => {
                const td = tr.insertCell();
                td.textContent = row[key] === undefined ? "—" : row[key];
            });
        });
        host.append(table);
    }
    function table() {
        const host = document.getElementById("schedule-table");
        if (!current || host.hidden) return;
        const width = Math.round(host.clientWidth);
        if (width <= 0) return;
        if (sheet) {
            const dataChanged = sheetRows !== rows,
                sizeChanged = sheetWidth !== width;
            if (dataChanged)
                sheet.setDataCfg(
                    {
                        fields: { columns },
                        meta: columns.map((field) => ({
                            field,
                            name: labels[field],
                        })),
                        data: rows,
                    },
                    true,
                );
            if (sizeChanged) sheet.changeSheetSize(width, 490);
            if (dataChanged || sizeChanged) sheet.render(dataChanged);
            sheetRows = rows;
            sheetWidth = width;
            return;
        }
        sheet = new S2.TableSheet(
            host,
            {
                fields: { columns },
                meta: columns.map((field) => ({ field, name: labels[field] })),
                data: rows,
            },
            {
                width,
                height: 490,
                style: { colCfg: { width: 130 } },
                frozenColCount: 1,
                conditions: {
                    background: [
                        {
                            field: /.*/,
                            mapping: (value, row) => ({
                                fill:
                                    row.event === "prepayment"
                                        ? "#fff3df"
                                        : row.event === "rate"
                                          ? "#edf5e7"
                                          : row.event === "total"
                                            ? "#e8f1e9"
                                            : "#ffffff",
                            }),
                        },
                    ],
                },
                interaction: {
                    enableCopy: true,
                    copyWithHeader: true,
                    brushSelection: { data: true, row: true, col: true },
                },
            },
        );
        sheet.render();
        sheetRows = rows;
        sheetWidth = width;
        observer = new ResizeObserver(() => {
            if (host.hidden || Math.round(host.clientWidth) === sheetWidth)
                return;
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(() => {
                const next = Math.round(host.clientWidth);
                if (!host.hidden && next > 0 && next !== sheetWidth) {
                    sheetWidth = next;
                    sheet.changeSheetSize(next, 490);
                    sheet.render(false);
                }
            }, 120);
        });
        observer.observe(host);
    }
    function element(tag, text, className) {
        const node = document.createElement(tag);
        if (text !== undefined) node.textContent = text;
        if (className) node.className = className;
        return node;
    }
    function metrics(items, className) {
        const list = element("dl", undefined, className);
        items.forEach(([label, value]) => {
            const pair = element("div");
            pair.append(element("dt", label), element("dd", "¥ " + money(value)));
            list.append(pair);
        });
        return list;
    }
    function monthCard(month) {
        const card = element("article", undefined, "schedule-month-card"),
            heading = element("div", undefined, "schedule-month-heading");
        heading.append(element("strong", month.month.replace("-", " 年 ") + " 月"),
            element("span", month.month < today().slice(0, 7) ? "历史测算" : "计划还款"));
        const total = element("p", undefined, "schedule-month-total");
        total.append(element("span", "当月计划支出"), element("strong", "¥ " + money(month.payment)));
        card.append(heading, total, metrics([
            ["正常还款", month.normalPayment], ["提前还本", month.prepayments],
            ["其中利息", month.interest], ["月底剩余本金", month.remainingPrincipal],
        ], "schedule-month-metrics"));
        const details = element("details", undefined, "schedule-month-details"), list = element("ul");
        details.append(element("summary", "查看本月明细"));
        month.entries.forEach(entry => {
            const name = entry.event ? "提前还本" : window.MortgageSchedule.isSettlement(current, entry.row) ? "结清利息" : "第 " + entry.row.month + " 期";
            list.append(element("li", entry.date + " · " + name + " · ¥ " + money(entry.payment / 100)));
        });
        details.append(list);
        card.append(details);
        return card;
    }
    function eventList(events) {
        const list = element("ol", undefined, "timeline-items");
        events.forEach(event => {
            const item = element("li", undefined, "timeline-item");
            item.dataset.phase = event.phase === "历史测算" ? "history" : "future";
            const date = element("time", event.date, "timeline-date");
            date.dateTime = event.date;
            item.append(date, element("strong", event.title), element("span", event.phase, "timeline-tag"));
            if (event.kind === "rate") item.append(element("p",
                (event.previousRate === undefined ? "执行年利率 " : event.previousRate.toFixed(3) + "% → ") + event.annualRate.toFixed(3) + "% · 自当日生效"));
            if (event.kind === "prepayment") item.append(element("p", "提前偿还本金 ¥ " + money(event.amount)));
            if (event.kind === "payoff" && event.settlementInterest > 0)
                item.append(element("p", "结清利息 ¥ " + money(event.settlementInterest) + " · 单独结息，不作为正常月供"));
            if (event.paymentDate) {
                let comparison = "";
                if (event.previousPayment !== undefined) {
                    const difference = Math.round(event.payment * 100) - Math.round(event.previousPayment * 100);
                    comparison = difference === 0 ? "（与上期相同）" : "（较上期" + (difference < 0 ? "少付" : "多付") + " ¥ " + money(Math.abs(difference) / 100) + "）";
                }
                item.append(element("p", (event.kind === "rate" ? "关联账单 " : "之后首期账单 ") + event.paymentDate + " · ¥ " + money(event.payment) + comparison));
                if (event.kind === "rate" && event.previousPayment !== undefined &&
                    Math.round(event.payment * 100) === Math.round(event.previousPayment * 100) &&
                    event.nextPaymentDate && Math.round(event.nextPayment * 100) !== Math.round(event.payment * 100)) {
                    item.append(element("p", "该期扣款与上期相同，后续一期 " + event.nextPaymentDate + " · ¥ " + money(event.nextPayment)));
                }
            }
            list.append(item);
        });
        return list;
    }
    function getViewInfo() {
        if (!current) return { view: scheduleView, note: "计算后查看还款明细" };
        const recent = window.MortgageSchedule.recent(current, today());
        const notes = {
            recent: recent.ended ? "计划已结束 · 展示最后 12 个月" : "从当前月份起展示 12 个月，尚未开始时从首月起展示",
            annual: "按实际还款日期归属自然年 · 点击年份展开月份",
            all: "完整逐期明细 · 金额单位：元 · 支持切换文本表格",
            events: "仅展示执行利率调整、提前还款及预计结清事件",
        };
        return { view: scheduleView, note: notes[scheduleView] + " · 历史金额按计划测算" };
    }
    function renderSchedule() {
        if (!current) return;
        const host = document.getElementById("schedule-summary");
        if (!host) return;
        const all = scheduleView === "all", mode = document.getElementById("table-mode");
        const textMode = mode && mode.getAttribute && mode.getAttribute("aria-pressed") === "true";
        host.hidden = all;
        document.getElementById("schedule-table").hidden = !all || textMode;
        document.getElementById("accessible-table").hidden = !all || !textMode;
        if (mode) mode.hidden = !all;
        const note = document.getElementById("schedule-view-note");
        if (note) note.textContent = getViewInfo().note;
        host.replaceChildren();
        if (scheduleView === "recent") {
            window.MortgageSchedule.recent(current, today()).months.forEach(month => host.append(monthCard(month)));
        } else if (scheduleView === "annual") {
            window.MortgageSchedule.annual(current).forEach(year => {
                const item = element("details", undefined, "schedule-year"), heading = element("summary"),
                    monthList = element("div", undefined, "schedule-year-months");
                heading.append(element("strong", year.year + " 年"), element("span", "计划支出 ¥ " + money(year.payment)), element("small", "利息 ¥ " + money(year.interest)));
                item.append(heading, metrics([["正常还款", year.normalPayment], ["提前还本", year.prepayments], ["其中利息", year.interest], ["年末剩余本金", year.remainingPrincipal]], "schedule-year-metrics"));
                year.months.forEach(month => monthList.append(monthCard(month)));
                item.append(monthList);
                host.append(item);
            });
        } else if (scheduleView === "events") host.append(eventList(window.MortgageSchedule.events(current, today())));
        if (all && !textMode) table();
    }
    function setView(view) {
        if (!["recent", "annual", "all", "events"].includes(view)) return;
        scheduleView = view;
        renderSchedule();
        document.querySelectorAll("[data-schedule-view]").forEach(button =>
            button.setAttribute("aria-pressed", String(button.dataset.scheduleView === view)));
    }
    function jumpToCurrent() {
        setView("recent");
        const host = document.getElementById("schedule-summary");
        if (host && host.scrollIntoView) host.scrollIntoView({ block: "nearest", behavior: "smooth" });
    }
    function renderTimeline() {
        const host = document.getElementById("timeline-list");
        if (host) {
            host.replaceChildren();
            host.append(eventList(window.MortgageSchedule.events(current, today())));
        }
    }
    function render(data, kind) {
        current = data;
        selectedTrendEvent = null;
        rows = makeRows(data);
        renderTextTable();
        renderSchedule();
        renderTimeline();
        trend(kind);
        composition();
        table();
        if (document.querySelector(".monthly-breakdown").open) breakdown();
    }
    function exportCsv() {
        const escape = (value) =>
            '"' +
            String(value === undefined ? "" : value).replace(/"/g, '""') +
            '"';
        const content =
            "\ufeff" +
            [
                columns.map((key) => labels[key]),
                ...rows.map((row) => columns.map((key) => row[key])),
            ]
                .map((row) => row.map(escape).join(","))
                .join("\r\n");
        const url = URL.createObjectURL(
            new Blob([content], { type: "text/csv;charset=utf-8" }),
        );
        const link = document.createElement("a");
        link.href = url;
        link.download = "房贷还款计划.csv";
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
    window.MortgageCharts = { render, trend, breakdown, table, exportCsv, setView, getViewInfo, jumpToCurrent, setRange };
})();
