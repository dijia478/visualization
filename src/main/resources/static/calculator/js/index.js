(function () {
    "use strict";
    const $ = (selector) => document.querySelector(selector);
    const form = $("#form1"),
        list = $("#prepayment-list");
    let nextPlan = 0,
        revision = 0,
        currentResult = null,
        currentTrend = "monthly",
        previewRequest = null,
        calculationRequest = null,
        planner = null,
        initialRateEdited = false,
        previewTimer;
    const money = (value) =>
        Number(value || 0).toLocaleString("zh-CN", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
        });
    const numeric = (id) => Number($("#" + id).value);
    function fieldError(input, message) {
        const control =
            input.type === "date"
                ? input
                      .closest(".date-control")
                      ?.querySelector(".date-picker-trigger") || input
                : input;
        control.setAttribute("aria-invalid", "true");
        const note = document.createElement("p");
        note.className = "field-error";
        note.id = input.id + "-error";
        note.textContent = message;
        control.setAttribute("aria-describedby", note.id);
        input.closest(".field").append(note);
    }
    function clearErrors() {
        form.querySelectorAll(".field-error").forEach((node) => node.remove());
        form.querySelectorAll("[aria-invalid]").forEach((node) => {
            node.removeAttribute("aria-invalid");
            node.removeAttribute("aria-describedby");
        });
        $("#errorMsg").hidden = true;
        $("#errorMsg").replaceChildren();
    }
    function showError(message, invalid = []) {
        const box = $("#errorMsg");
        box.replaceChildren();
        const title = document.createElement("strong");
        title.textContent = message;
        box.append(title);
        if (invalid.length) {
            const links = document.createElement("ul");
            invalid.forEach(({ input, message }) => {
                const li = document.createElement("li"),
                    a = document.createElement("a");
                a.href = "#" + input.id;
                a.textContent = message;
                a.onclick = (event) => {
                    event.preventDefault();
                    for (
                        let parent = input.parentElement;
                        parent;
                        parent = parent.parentElement
                    ) {
                        if (parent.tagName === "DETAILS") parent.open = true;
                    }
                    const control =
                        input.type === "date"
                            ? input
                                  .closest(".date-control")
                                  ?.querySelector(".date-picker-trigger") ||
                              input
                            : input;
                    control.focus();
                };
                li.append(a);
                links.append(li);
            });
            box.append(links);
        }
        box.hidden = false;
        box.focus();
    }
    function validate() {
        clearErrors();
        const invalid = [];
        form.querySelectorAll("input,select").forEach((input) => {
            if (
                input.disabled ||
                input.readOnly ||
                input.type === "radio" ||
                input.type === "checkbox"
            )
                return;
            if (!input.checkValidity()) {
                const label =
                    input
                        .closest(".field")
                        ?.querySelector("label,legend")
                        ?.textContent.replace(/\s+/g, " ")
                        .replace("选填", "")
                        .trim() || "此项";
                let message = label + "请填写有效值";
                if (input.validity.valueMissing) message = "请填写" + label;
                else if (
                    input.validity.rangeUnderflow ||
                    input.validity.rangeOverflow
                )
                    message =
                        label +
                        "应在 " +
                        input.min +
                        " 至 " +
                        (input.max || "合理范围") +
                        " 之间";
                else if (input.validity.stepMismatch)
                    message = label + "的数值精度不正确";
                invalid.push({ input, message });
                fieldError(input, message);
            }
        });
        if (invalid.length) {
            showError("请检查以下贷款条件", invalid);
            return false;
        }
        return true;
    }
    function payload() {
        const prepaymentList = $("#prepayment-enabled").checked
            ? Array.from(list.children).map((row) => ({
                  prepaymentDate: row.querySelector("[name=prepaymentDate]")
                      .value,
                  repayment: Number(
                      row.querySelector("[name=repayment]").value,
                  ),
                  newRate:
                      row.querySelector("[name=rateSource]").value === "auto"
                          ? null
                          : Number(row.querySelector("[name=newRate]").value),
                  newType: Number(row.querySelector("[name=newType]").value),
                  repaymentType: Number(
                      row.querySelector("[name=repaymentType]").value,
                  ),
              }))
            : [];
        return {
            amount: numeric("amount"),
            year: numeric("year"),
            rate: numeric("rate"),
            type: Number($("[name=type]:checked").value),
            firstPaymentDate: $("#firstPaymentDate").value,
            rateAdjustmentDay: numeric("rateAdjustmentDay"),
            firstHouse: 1,
            prepayment: prepaymentList.length ? 1 : 0,
            prepaymentList,
            rateMode: $("#rateMode").value,
            repricingInterestMode: "SPLIT_30_360",
            repricingMonths: numeric("repricingMonths"),
            lprSpreadBp: null,
            loanDate: null,
            repricingAnchorDate: null,
            applyHistoricalRateAdjustments: true,
        };
    }
    async function request(url, data, signal) {
        const response = await fetch(url, {
            method: data === undefined ? "GET" : "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data),
            signal,
        });
        if (!response.ok) throw new Error("服务暂时无法完成计算，请稍后重试。");
        let body;
        try {
            body = await response.json();
        } catch (error) {
            throw new Error("未收到有效结果，请确认本地 Java 服务已启动。");
        }
        if (body.code !== 200)
            throw new Error(
                typeof body.data === "string" && body.code === 602
                    ? body.data
                    : body.message || "请检查贷款条件后重试。",
            );
        return body.data;
    }
    function markChanged() {
        revision++;
        if (planner) planner.invalidate();
        $("#download-btn").disabled = true;
        if (calculationRequest) {
            calculationRequest.abort();
            calculationRequest = null;
            setBusy(false);
        }
        if (currentResult) {
            $("#result-status").textContent = "条件已修改 · 请重新计算";
            $("#result-status").classList.add("stale");
        }
        clearTimeout(previewTimer);
        if (previewRequest) {
            previewRequest.abort();
            previewRequest = null;
        }
        list.querySelectorAll(".prepayment-card").forEach((row) => {
            if (row.querySelector("[name=rateSource]").value === "auto") {
                row.querySelector(".rate-hint").textContent =
                    "条件已修改 · 建议利率待更新";
            }
        });
        previewTimer = setTimeout(previewRates, 350);
    }
    function setBusy(busy) {
        $("#submit-btn").disabled = busy;
        $(".button-label").textContent = busy ? "正在计算…" : "计算还款方案";
        $(".results-panel").setAttribute("aria-busy", String(busy));
    }
    function setRateSource(row) {
        const manual =
                row.querySelector("[name=rateSource]").value === "manual",
            input = row.querySelector("[name=newRate]");
        input.readOnly = !manual;
        input.required = manual;
        row.querySelector(".rate-hint").textContent = manual
            ? "请填写银行当时实际执行的年利率"
            : "根据贷款条件与本地 LPR 历史计算";
    }
    function addPlan() {
        const row = $(
                "#prepayment-template",
            ).content.firstElementChild.cloneNode(true),
            id = ++nextPlan;
        row.querySelectorAll("input,select").forEach((input) => {
            input.id = "plan-" + id + "-" + input.name;
            const label = row.querySelector('[data-for="' + input.name + '"]');
            if (label) label.htmlFor = input.id;
        });
        row.querySelector("[name=newType]").value = $(
            "[name=type]:checked",
        ).value;
        row.querySelector("[name=prepaymentDate]").value =
            window.MortgageDatePicker.today();
        row.querySelector("[name=repayment]").value = "10";
        row.querySelector(".remove-plan").onclick = () => {
            row.remove();
            renumber();
            if (!list.children.length) {
                $("#prepayment-enabled").checked = false;
                togglePrepayment();
            }
            markChanged();
        };
        row.querySelector("[name=rateSource]").onchange = () =>
            setRateSource(row);
        list.append(row);
        window.MortgageDatePicker.enhance(
            row.querySelector("[name=prepaymentDate]"),
        );
        renumber();
        return row;
    }
    function renumber() {
        Array.from(list.children).forEach(
            (row, i) =>
                (row.querySelector(".plan-number").textContent = String(
                    i + 1,
                ).padStart(2, "0")),
        );
    }
    function togglePrepayment() {
        const enabled = $("#prepayment-enabled").checked;
        if (enabled && !list.children.length) addPlan();
        list.hidden = !enabled;
        $("#addPrepaymentBtn").hidden = !enabled;
        $("#prepay-intro").hidden = enabled;
        list.querySelectorAll("input,select").forEach(
            (input) => (input.disabled = !enabled),
        );
    }
    function toggleFloating() {
        const fixed = $("#rateMode").value === "FIXED";
        $("#floating-fields").hidden = fixed;
        $("#floating-fields")
            .querySelectorAll("select")
            .forEach((input) => (input.disabled = fixed));
    }
    function applyAutomaticRates(values) {
        Array.from(list.children).forEach((row, i) => {
            if (
                row.querySelector("[name=rateSource]").value === "auto" &&
                values[i]
            ) {
                row.querySelector("[name=newRate]").value = Number(
                    values[i].newRate,
                ).toFixed(3);
                row.querySelector(".rate-hint").textContent =
                    "自动估算 · 可切换手动填写银行执行利率";
            }
        });
    }
    async function previewRates() {
        const hasAutomaticRate =
            $("#prepayment-enabled").checked &&
            Array.from(list.children).some(
                (row) =>
                    row.querySelector("[name=rateSource]").value === "auto",
            );
        if (!hasAutomaticRate) return;
        if (
            Array.from(form.querySelectorAll("input,select")).some(
                (input) =>
                    !input.disabled && !input.readOnly && !input.validity.valid,
            )
        )
            return;
        const data = payload();
        if (
            !data.amount ||
            !data.year ||
            !data.firstPaymentDate ||
            data.prepaymentList.some((p) => !p.prepaymentDate)
        )
            return;
        const seq = revision,
            controller = new AbortController();
        previewRequest = controller;
        try {
            const preview = await request(
                "/calculator/ratePreview",
                data,
                controller.signal,
            );
            if (seq !== revision || previewRequest !== controller) return;
            applyAutomaticRates(preview.prepaymentRates || []);
        } catch (error) {
            if (
                error.name !== "AbortError" &&
                seq === revision &&
                previewRequest === controller
            )
                list.querySelectorAll(".rate-hint").forEach((node) => {
                    if (
                        node
                            .closest(".prepayment-card")
                            .querySelector("[name=rateSource]").value === "auto"
                    )
                        node.textContent = node
                            .closest(".prepayment-card")
                            .querySelector("[name=newRate]").value
                            ? "更新失败 · 显示上次利率；计算时重试，也可手动填写。"
                            : "暂未取得建议利率；计算时将重新解析，也可手动填写。";
                });
        } finally {
            if (previewRequest === controller) previewRequest = null;
        }
    }
    function applyResult(data, submitted) {
        currentResult = data;
        const first = data.monthLoanList[0],
            last = data.monthLoanList[data.monthLoanList.length - 1],
            currentDate = window.MortgageDatePicker.today(),
            currentMonth = currentDate.slice(0, 7),
            current = data.monthLoanList.find(
                (row) => row.dateFormat.slice(0, 7) === currentMonth
                    && !(row.dateFormat === data.payoffDate && Number(row.principal) === 0),
            );
        const payoffDate = data.payoffDate || (data.appliedPrepayments || [])
            .filter(
                (event) =>
                    Number(event.effectiveMonth) ===
                    data.monthLoanList.length + 1,
            )
            .reduce(
                (date, event) =>
                    event.prepaymentDate > date ? event.prepaymentDate : date,
                last.dateFormat,
            ),
            paidOff = payoffDate <= currentDate,
            remainingMonths =
                (Number(payoffDate.slice(0, 4)) - Number(currentDate.slice(0, 4))) * 12 +
                Number(payoffDate.slice(5, 7)) - Number(currentDate.slice(5, 7));
        $("#monthly-payment").textContent = money(!paidOff && current ? current.repayment : 0);
        $("#payoff-date").textContent =
            payoffDate.slice(0, 7).replace("-", " 年 ") + " 月";
        $("#payoff-remaining").textContent = paidOff
            ? "（已还清）"
            : "（还剩" + Math.floor(remainingMonths / 12) + "年" + remainingMonths % 12 + "个月）";
        $("#duration-summary").textContent =
            "共 " +
            data.monthLoanList.length +
            " 期 · " +
            (submitted.type === 1 ? "等额本息" : "等额本金");
        $("#loan-summary").textContent =
            submitted.amount +
            " 万元 / " +
            submitted.year +
            " 年 / 初始年利率 " +
            submitted.rate +
            "%";
        if (paidOff) {
            $("#loan-summary").textContent = "已还清 · 最后还款 " + payoffDate;
        } else if (!current) {
            $("#loan-summary").textContent =
                first.dateFormat > currentDate
                    ? "尚未开始还款 · 首次还款 " + first.dateFormat
                    : "本月无月供 · 计划于 " + payoffDate + " 结清";
        }
        $("#total-payment").textContent =
            (Number(data.totalRepayment) / 10000).toFixed(2) + " 万";
        $("#total-interest").textContent =
            (Number(data.totalInterest) / 10000).toFixed(2) + " 万";
        const paidRows = data.monthLoanList.filter(
                (row) => row.dateFormat <= currentDate,
            ),
            // 按分汇总；期后预还在API中挂前一还款期，必须单独按实际日期计入。
            paidPayment =
                paidRows.reduce(
                    (total, row) => total + Math.round(Number(row.repayment) * 100),
                    0,
                ) +
                (data.appliedPrepayments || [])
                    .filter((event) => event.prepaymentDate <= currentDate)
                    .reduce(
                        (total, event) => total + Math.round(Number(event.repayment) * 100),
                        0,
                    ),
            paidInterest = paidRows.reduce(
                (total, row) => total + Math.round(Number(row.interest) * 100),
                0,
            ),
            paidPrincipal = paidPayment - paidInterest,
            remainingPrincipal = Math.round(Number(data.loanAmount) * 100) - paidPrincipal;
        $("#paid-payment").textContent = (paidPayment / 1000000).toFixed(2) + " 万";
        $("#paid-interest").textContent = (paidInterest / 1000000).toFixed(2) + " 万";
        $("#paid-principal").textContent = (paidPrincipal / 1000000).toFixed(2) + " 万";
        $("#remaining-principal").textContent = (remainingPrincipal / 1000000).toFixed(2) + " 万";
        $("#paid-payment-note").textContent =
            "截至 " + currentDate + " · 含提前还款";
        $("#paid-interest-note").textContent =
            "截至 " + currentDate + " · 按计划测算";
        $("#paid-principal-note").textContent =
            "截至 " + currentDate + " · 含提前还款";
        $("#remaining-principal-note").textContent =
            "截至 " + currentDate + " · 按计划测算";
        const baselineInterest = Number(data.originalTotalInterest),
            saved = Math.round((baselineInterest - Number(data.totalInterest)) * 100) / 100,
            savingsRatio = baselineInterest > 0
                ? (Math.abs(saved) / baselineInterest * 100).toFixed(1) + "%"
                : "";
        $("#savings-card").dataset.state = saved > 0 ? "positive" : saved < 0 ? "negative" : "neutral";
        $("#savings-title").textContent = saved < 0 ? "预计多付利息" : "预计少付利息";
        $("#saved-interest").textContent = money(Math.abs(saved));
        $("#savings-change").textContent = saved > 0
            ? "利息减少 " + savingsRatio
            : saved < 0
              ? "利息增加 " + savingsRatio
              : "暂未减少利息";
        $("#baseline-interest").textContent = (baselineInterest / 10000).toFixed(2) + " 万";
        $("#planned-interest").textContent = (Number(data.totalInterest) / 10000).toFixed(2) + " 万";
        $("#savings-note").textContent = saved === 0
            ? "当前方案与未提前还款方案的预计利息相同"
            : "按相同利率与还款方式比较全期利息";
        $("#interest-ratio").textContent =
            "占总还款 " +
            (data.totalRepayment > 0
                ? (data.totalInterest / data.totalRepayment) * 100
                : 0
            ).toFixed(1) +
            "%";
        $("#schedule-count").textContent =
            data.monthLoanList.length + " 期还款计划 · 金额单位：元";
        $("#result-status").textContent = "计算完成 · 分段计息";
        $("#result-status").classList.remove("stale");
        $("#download-btn").disabled = false;
        MortgageCharts.render(data, currentTrend);
        applyAutomaticRates(data.appliedPrepayments || []);
        if (planner) planner.setLoan(submitted, data);
    }
    async function calculate(scrollToResult = false) {
        if (!validate()) return;
        clearTimeout(previewTimer);
        if (previewRequest) {
            previewRequest.abort();
            previewRequest = null;
        }
        if (calculationRequest) calculationRequest.abort();
        const controller = new AbortController(),
            seq = revision,
            submitted = payload();
        calculationRequest = controller;
        setBusy(true);
        try {
            const data = await request(
                "/calculator/prepaymentCalculator",
                submitted,
                controller.signal,
            );
            if (seq !== revision || calculationRequest !== controller) return;
            if (!data.monthLoanList?.length)
                throw new Error("未生成还款计划，请检查贷款条件。");
            applyResult(data, submitted);
            if (
                scrollToResult &&
                window.matchMedia("(max-width: 767px)").matches
            ) {
                $("#results-title").focus({ preventScroll: true });
                $("#results-title").scrollIntoView({
                    behavior: "auto",
                    block: "start",
                });
            }
        } catch (error) {
            if (
                error.name !== "AbortError" &&
                seq === revision &&
                calculationRequest === controller
            ) {
                showError(error.message);
                $("#result-status").textContent = currentResult
                    ? "计算失败 · 保留上次结果"
                    : "等待计算";
            }
        } finally {
            if (calculationRequest === controller) {
                calculationRequest = null;
                setBusy(false);
            }
        }
    }
    function selectTrend(button) {
        currentTrend = button.dataset.trend;
        document.querySelectorAll("[data-trend]").forEach((item) => {
            item.setAttribute("aria-selected", String(item === button));
            item.tabIndex = item === button ? 0 : -1;
        });
        $("#trend-chart").setAttribute("aria-labelledby", button.id);
        MortgageCharts.trend(currentTrend);
    }
    form.addEventListener("submit", (event) => {
        event.preventDefault();
        calculate(true);
    });
    form.addEventListener("input", (event) => {
        if (event.target.id === "rate") initialRateEdited = true;
        if (
            event.target.tagName !== "SELECT" &&
            event.target.type !== "checkbox" &&
            event.target.type !== "radio"
        )
            markChanged();
    });
    form.addEventListener("change", (event) => {
        if (event.target.id === "prepayment-enabled") togglePrepayment();
        if (event.target.id === "rateMode") toggleFloating();
        if (
            event.target.tagName === "SELECT" ||
            event.target.type === "checkbox" ||
            event.target.type === "radio"
        )
            markChanged();
    });
    $("#addPrepaymentBtn").onclick = () => {
        const row = addPlan();
        markChanged();
        row.querySelector(".date-picker-trigger").focus();
    };
    document.querySelectorAll("[data-trend]").forEach((button) => {
        button.onclick = () => selectTrend(button);
        button.onkeydown = (event) => {
            if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key))
                return;
            event.preventDefault();
            const tabs = Array.from(document.querySelectorAll("[data-trend]")),
                index = tabs.indexOf(button),
                next =
                    event.key === "Home"
                        ? 0
                        : event.key === "End"
                          ? tabs.length - 1
                          : (index +
                                (event.key === "ArrowRight" ? 1 : -1) +
                                tabs.length) %
                            tabs.length;
            tabs[next].focus();
            selectTrend(tabs[next]);
        };
    });
    $(".monthly-breakdown").addEventListener("toggle", (event) => {
        if (event.target.open) MortgageCharts.breakdown();
    });
    $("#download-btn").onclick = () => MortgageCharts.exportCsv();
    function setTableMode(showText) {
        $("#accessible-table").hidden = !showText;
        $("#schedule-table").hidden = showText;
        $("#table-mode").textContent = showText
            ? "切换为交互表格"
            : "切换为文本表格";
        $("#table-mode").setAttribute("aria-pressed", String(showText));
        if (!showText && currentResult) MortgageCharts.table();
    }
    $("#table-mode").onclick = () =>
        setTableMode($("#accessible-table").hidden);
    function selectSchedule(view) {
        MortgageCharts.setView(view);
        document.querySelectorAll("[data-schedule-view]").forEach((button) =>
            button.setAttribute("aria-pressed", String(button.dataset.scheduleView === view)));
    }
    document.querySelectorAll("[data-schedule-view]").forEach((button) => {
        button.onclick = () => selectSchedule(button.dataset.scheduleView);
    });
    $("#jump-current").onclick = () => {
        selectSchedule("recent");
        MortgageCharts.jumpToCurrent();
    };
    document.querySelectorAll("[data-trend-range]").forEach((button) => {
        button.onclick = () => {
            MortgageCharts.setRange(button.dataset.trendRange);
            document.querySelectorAll("[data-trend-range]").forEach((item) =>
                item.setAttribute("aria-pressed", String(item === button)));
        };
    });
    setTableMode(window.matchMedia("(max-width: 767px)").matches);
    toggleFloating();
    function adoptPlanning(value) {
        list.replaceChildren();
        (value.prepaymentList || []).forEach((event) => {
            const row = addPlan();
            ["prepaymentDate", "repayment", "newType", "repaymentType"].forEach((name) => {
                row.querySelector("[name=" + name + "]").value = event[name];
            });
            row.querySelector("[name=rateSource]").value = event.newRate == null ? "auto" : "manual";
            row.querySelector("[name=newRate]").value = event.newRate == null ? "" : event.newRate;
            setRateSource(row);
            window.MortgageDatePicker.updateTrigger(row.querySelector("[name=prepaymentDate]"));
        });
        $("#prepayment-enabled").checked = list.children.length > 0;
        togglePrepayment();
        markChanged();
        calculate(true);
    }
    if (window.MortgagePlanning) planner = window.MortgagePlanning.createPlanner({
        document,
        request,
        today: () => window.MortgageDatePicker.today(),
        updateDate: (input) => window.MortgageDatePicker.updateTrigger(input),
        adopt: adoptPlanning,
    });
    async function initializeDefaults() {
        $("#firstPaymentDate").value = window.MortgageDatePicker.today();
        window.MortgageDatePicker.updateTrigger($("#firstPaymentDate"));
        try {
            const defaults = await request("/calculator/defaults");
            // 请求返回前用户可能已开始编辑，不能覆盖手填利率或自动提交新条件。
            if (!initialRateEdited) $("#rate").value = defaults.annualRate;
            $("#rate").removeAttribute("placeholder");
            if (revision === 0) calculate();
            else previewRates();
        } catch (error) {
            $("#rate").placeholder = "请填写执行利率";
            if (!initialRateEdited)
                showError("未能读取默认利率，请填写初始年利率后计算。");
        }
    }
    initializeDefaults();
})();
