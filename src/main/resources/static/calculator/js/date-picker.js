(function () {
    "use strict";

    const dialog = document.querySelector("#date-picker-dialog"),
        context = document.querySelector("#date-picker-context"),
        yearButton = document.querySelector("#date-picker-year"),
        yearValue = yearButton.querySelector("span"),
        yearPanel = document.querySelector("#date-picker-years"),
        monthButton = document.querySelector("#date-picker-month"),
        monthValue = monthButton.querySelector("span"),
        monthPanel = document.querySelector("#date-picker-months"),
        calendar = document.querySelector("#date-picker-calendar"),
        grid = document.querySelector("#date-picker-grid"),
        previousButton = document.querySelector("#date-picker-previous"),
        nextButton = document.querySelector("#date-picker-next"),
        todayButton = document.querySelector("#date-picker-today"),
        confirmButton = document.querySelector("#date-picker-confirm"),
        closeButtons = dialog.querySelectorAll("[data-date-picker-close]");

    let activeInput = null,
        selectedDate = null,
        viewYear = 0,
        viewMonth = 0,
        restoreFocus = null;

    const pad = (value) => String(value).padStart(2, "0");
    const dateKey = (date) =>
        date.getFullYear() * 10000 +
        (date.getMonth() + 1) * 100 +
        date.getDate();
    const isoDate = (date) =>
        [date.getFullYear(), pad(date.getMonth() + 1), pad(date.getDate())].join(
            "-",
        );
    const displayDate = (value) => value.replace(/-/g, "/");

    function today() {
        const parts = new Intl.DateTimeFormat("zh-CN", {
            timeZone: "Asia/Shanghai",
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
        }).formatToParts(new Date());
        return ["year", "month", "day"]
            .map((type) => parts.find((part) => part.type === type).value)
            .join("-");
    }

    function parseDate(value) {
        const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value || "");
        if (!match) return null;
        const date = new Date(
            Number(match[1]),
            Number(match[2]) - 1,
            Number(match[3]),
            12,
        );
        return date.getFullYear() === Number(match[1]) &&
            date.getMonth() === Number(match[2]) - 1 &&
            date.getDate() === Number(match[3])
            ? date
            : null;
    }

    function copyDate(date) {
        return new Date(
            date.getFullYear(),
            date.getMonth(),
            date.getDate(),
            12,
        );
    }

    function limits() {
        return {
            min: parseDate(activeInput.min) || new Date(1900, 0, 1, 12),
            max: parseDate(activeInput.max) || new Date(2099, 11, 31, 12),
        };
    }

    function clamp(date) {
        const { min, max } = limits();
        if (dateKey(date) < dateKey(min)) return copyDate(min);
        if (dateKey(date) > dateKey(max)) return copyDate(max);
        return copyDate(date);
    }

    function labelFor(input) {
        return (
            input
                .closest(".field")
                ?.querySelector("label,legend")
                ?.textContent.replace(/\s+/g, " ")
                .replace("选填", "")
                .trim() || "日期"
        );
    }

    function updateTrigger(input) {
        const trigger = input
            .closest(".date-control")
            ?.querySelector(".date-picker-trigger");
        if (!trigger) return;
        const value = input.value ? displayDate(input.value) : "请选择日期";
        trigger.querySelector(".date-picker-value").textContent = value;
        trigger.setAttribute(
            "aria-label",
            labelFor(input) + "：" + value + "，打开日期选择器",
        );
        trigger.disabled = input.disabled;
    }

    function enhance(input) {
        if (!input || input.dataset.datePickerEnhanced === "true")
            return input?.closest(".date-control")?.querySelector("button");

        input.dataset.datePickerEnhanced = "true";
        input.classList.add("date-native-input");
        input.tabIndex = -1;
        input.setAttribute("aria-hidden", "true");

        const wrapper = document.createElement("div"),
            trigger = document.createElement("button"),
            label = input.closest(".field")?.querySelector("label,legend");
        wrapper.className = "date-control";
        input.parentNode.insertBefore(wrapper, input);
        wrapper.append(input);

        trigger.type = "button";
        trigger.className = "date-picker-trigger";
        trigger.id = input.id + "-trigger";
        trigger.dataset.dateInput = input.id;
        trigger.setAttribute("aria-haspopup", "dialog");
        trigger.setAttribute("aria-controls", dialog.id);
        trigger.innerHTML =
            '<span class="date-picker-value"></span>' +
            '<span class="date-picker-icon" aria-hidden="true">' +
            '<svg viewBox="0 0 24 24"><path d="M7 3v3M17 3v3M4.5 9h15M6 5h12a2 2 0 0 1 2 2v12H4V7a2 2 0 0 1 2-2Z"/></svg>' +
            "</span>";
        trigger.addEventListener("click", () => open(input));
        wrapper.append(trigger);

        if (label && label.tagName === "LABEL") label.htmlFor = trigger.id;
        input.addEventListener("change", () => updateTrigger(input));
        new MutationObserver(() => updateTrigger(input)).observe(input, {
            attributes: true,
            attributeFilter: ["disabled"],
        });
        updateTrigger(input);
        return trigger;
    }

    function monthAllowed(year, month) {
        const { min, max } = limits(),
            first = new Date(year, month, 1, 12),
            last = new Date(year, month + 1, 0, 12);
        return dateKey(last) >= dateKey(min) && dateKey(first) <= dateKey(max);
    }

    function selectInView(day) {
        const days = new Date(viewYear, viewMonth + 1, 0, 12).getDate(),
            candidate = new Date(
                viewYear,
                viewMonth,
                Math.min(day || 1, days),
                12,
            );
        selectedDate = clamp(candidate);
        viewYear = selectedDate.getFullYear();
        viewMonth = selectedDate.getMonth();
    }

    function renderYears(focusSelected) {
        const { min, max } = limits();
        yearPanel.replaceChildren();
        for (let year = min.getFullYear(); year <= max.getFullYear(); year++) {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "date-picker-year-option";
            button.id = "date-picker-year-" + year;
            button.dataset.year = String(year);
            button.setAttribute("role", "option");
            button.setAttribute("aria-selected", String(year === viewYear));
            button.textContent = year + " 年";
            if (year === viewYear) button.dataset.selected = "true";
            button.addEventListener("click", () => {
                viewYear = year;
                selectInView(selectedDate?.getDate());
                showYears(false);
                render(true);
            });
            yearPanel.append(button);
        }
        if (focusSelected)
            requestAnimationFrame(() => {
                const selected = yearPanel.querySelector(
                    "[data-selected='true']",
                );
                selected?.scrollIntoView({ block: "center" });
                selected?.focus({ preventScroll: true });
            });
    }

    function showYears(show) {
        if (!show) return showCalendar();
        yearPanel.hidden = false;
        monthPanel.hidden = true;
        calendar.hidden = true;
        yearButton.setAttribute("aria-expanded", "true");
        monthButton.setAttribute("aria-expanded", "false");
        renderYears(true);
    }

    function renderMonths(focusSelected) {
        monthPanel.replaceChildren();
        for (let month = 0; month < 12; month++) {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "date-picker-month-option";
            button.id = "date-picker-month-" + (month + 1);
            button.dataset.month = String(month);
            button.setAttribute("role", "option");
            button.setAttribute("aria-selected", String(month === viewMonth));
            button.textContent = month + 1 + " 月";
            if (month === viewMonth) button.dataset.selected = "true";
            button.addEventListener("click", () => {
                viewMonth = month;
                selectInView(selectedDate?.getDate());
                showCalendar();
                render(true);
            });
            monthPanel.append(button);
        }
        if (focusSelected)
            requestAnimationFrame(() =>
                monthPanel
                    .querySelector("[data-selected='true']")
                    ?.focus({ preventScroll: true }),
            );
    }

    function showMonths(show) {
        if (!show) return showCalendar();
        yearPanel.hidden = true;
        monthPanel.hidden = false;
        calendar.hidden = true;
        yearButton.setAttribute("aria-expanded", "false");
        monthButton.setAttribute("aria-expanded", "true");
        renderMonths(true);
    }

    function showCalendar() {
        yearPanel.hidden = true;
        monthPanel.hidden = true;
        calendar.hidden = false;
        yearButton.setAttribute("aria-expanded", "false");
        monthButton.setAttribute("aria-expanded", "false");
    }

    function render(focusDate) {
        const { min, max } = limits(),
            todayDate = parseDate(today()),
            firstWeekday = new Date(viewYear, viewMonth, 1, 12).getDay(),
            days = new Date(viewYear, viewMonth + 1, 0, 12).getDate();

        yearValue.textContent = viewYear + " 年";
        monthValue.textContent = viewMonth + 1 + " 月";
        previousButton.disabled = !monthAllowed(
            viewMonth === 0 ? viewYear - 1 : viewYear,
            viewMonth === 0 ? 11 : viewMonth - 1,
        );
        nextButton.disabled = !monthAllowed(
            viewMonth === 11 ? viewYear + 1 : viewYear,
            viewMonth === 11 ? 0 : viewMonth + 1,
        );
        grid.replaceChildren();

        for (let index = 0; index < firstWeekday; index++) {
            const blank = document.createElement("span");
            blank.className = "date-picker-blank";
            blank.setAttribute("aria-hidden", "true");
            grid.append(blank);
        }

        for (let day = 1; day <= days; day++) {
            const date = new Date(viewYear, viewMonth, day, 12),
                key = dateKey(date),
                button = document.createElement("button");
            button.type = "button";
            button.className = "date-picker-day";
            button.textContent = String(day);
            button.dataset.iso = isoDate(date);
            button.disabled = key < dateKey(min) || key > dateKey(max);
            const isToday = key === dateKey(todayDate),
                isSelected =
                    selectedDate && key === dateKey(selectedDate);
            button.setAttribute(
                "aria-label",
                viewYear +
                    "年" +
                    (viewMonth + 1) +
                    "月" +
                    day +
                    "日" +
                    (isToday ? "，今天" : "") +
                    (isSelected ? "，已选择" : ""),
            );
            if (isToday) button.dataset.today = "true";
            if (isSelected) {
                button.dataset.selected = "true";
                button.setAttribute("aria-current", "date");
            }
            button.addEventListener("click", () => {
                selectedDate = date;
                render(true);
            });
            grid.append(button);
        }

        while (grid.children.length < 42) {
            const blank = document.createElement("span");
            blank.className = "date-picker-blank";
            blank.setAttribute("aria-hidden", "true");
            grid.append(blank);
        }

        confirmButton.disabled = !selectedDate;
        if (focusDate)
            requestAnimationFrame(() =>
                grid
                    .querySelector("[data-selected='true']")
                    ?.focus({ preventScroll: true }),
            );
    }

    function moveMonth(offset) {
        const day = selectedDate?.getDate() || 1,
            next = new Date(viewYear, viewMonth + offset, 1, 12);
        viewYear = next.getFullYear();
        viewMonth = next.getMonth();
        selectInView(day);
        showCalendar();
        render(true);
    }

    function open(input) {
        activeInput = input;
        restoreFocus = document.activeElement;
        selectedDate = clamp(parseDate(input.value) || parseDate(today()));
        viewYear = selectedDate.getFullYear();
        viewMonth = selectedDate.getMonth();
        context.textContent = labelFor(input);
        showCalendar();
        render();
        document.body.classList.add("date-picker-open");
        if (typeof dialog.showModal === "function") dialog.showModal();
        else dialog.setAttribute("open", "");
        requestAnimationFrame(() =>
            grid
                .querySelector("[data-selected='true']")
                ?.focus({ preventScroll: true }),
        );
    }

    function close() {
        if (typeof dialog.close === "function" && dialog.open) dialog.close();
        else {
            dialog.removeAttribute("open");
            document.body.classList.remove("date-picker-open");
            restoreFocus?.focus({ preventScroll: true });
        }
    }

    previousButton.addEventListener("click", () => moveMonth(-1));
    nextButton.addEventListener("click", () => moveMonth(1));
    yearButton.addEventListener("click", () => {
        const show = yearPanel.hidden;
        showYears(show);
        if (!show) render(true);
    });
    monthButton.addEventListener("click", () => {
        const show = monthPanel.hidden;
        showMonths(show);
        if (!show) render(true);
    });
    todayButton.addEventListener("click", () => {
        selectedDate = clamp(parseDate(today()));
        viewYear = selectedDate.getFullYear();
        viewMonth = selectedDate.getMonth();
        showCalendar();
        render(true);
    });
    confirmButton.addEventListener("click", () => {
        if (!activeInput || !selectedDate) return;
        activeInput.value = isoDate(selectedDate);
        updateTrigger(activeInput);
        activeInput.dispatchEvent(new Event("input", { bubbles: true }));
        activeInput.dispatchEvent(new Event("change", { bubbles: true }));
        close();
    });
    closeButtons.forEach((button) => button.addEventListener("click", close));
    dialog.addEventListener("cancel", (event) => {
        event.preventDefault();
        if (!yearPanel.hidden || !monthPanel.hidden) {
            showCalendar();
            render(true);
        } else close();
    });
    dialog.addEventListener("click", (event) => {
        if (event.target === dialog) close();
    });
    dialog.addEventListener("close", () => {
        document.body.classList.remove("date-picker-open");
        restoreFocus?.focus({ preventScroll: true });
        activeInput = null;
    });
    grid.addEventListener("keydown", (event) => {
        const date = parseDate(event.target.dataset?.iso);
        if (!date) return;
        const offsets = {
            ArrowLeft: -1,
            ArrowRight: 1,
            ArrowUp: -7,
            ArrowDown: 7,
        };
        if (!(event.key in offsets)) return;
        event.preventDefault();
        date.setDate(date.getDate() + offsets[event.key]);
        selectedDate = clamp(date);
        viewYear = selectedDate.getFullYear();
        viewMonth = selectedDate.getMonth();
        render(true);
    });

    window.MortgageDatePicker = { enhance, updateTrigger, today };
    document.querySelectorAll('input[type="date"]').forEach(enhance);
})();
