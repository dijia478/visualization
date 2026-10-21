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
        const data = current.monthLoanList.flatMap((row) =>
            fields.map(([field, name]) => ({
                month: String(row.month),
                date: row.dateFormat,
                name,
                value: Number(row[field]),
            })),
        );
        mount(
            "trend-chart",
            G2Plot.Line,
            {
                data,
                xField: "month",
                yField: "value",
                seriesField: "name",
                smooth: false,
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
                    label: { formatter: (v) => v + "期", autoHide: true },
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
                    formatter: (item) => ({
                        name: item.name,
                        value: "¥ " + money(item.value),
                    }),
                },
                interactions: touchLayout() ? [] : [{ type: "brush" }],
                lineStyle: { lineWidth: 2 },
            },
            kind !== trendKind,
        );
        trendKind = kind;
    }
    function composition() {
        const data = [
            { name: "贷款本金", value: Number(current.loanAmount) },
            { name: "累计利息", value: Number(current.totalInterest) },
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
    function render(data, kind) {
        current = data;
        rows = makeRows(data);
        renderTextTable();
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
    window.MortgageCharts = { render, trend, breakdown, table, exportCsv };
})();
