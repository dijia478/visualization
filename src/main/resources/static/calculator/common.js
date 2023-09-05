function sendRequest() {
    $('#submit-btn').click(function (e) {
        $('#errorMsg').html("");
        $('#container1').html("");
        $('#container2').html("");
        $('#container3').html("");
        $('#container4').html("");
        $('#container5').html("");
        $('#container6').html("");
        $('#container7').html("");

        let prepayment = $('input[name="prepayment"]:checked').val();
        if (prepayment == 0) {
            const prepaymentList = []
            // ajax提交
            $.ajax({
                method: 'POST',
                url: '/calculator/loanCalculator',
                dataType: 'json',
                contentType: 'application/json;charset=UTF-8',
                data: JSON.stringify({
                    amount: $("#amount").val(),
                    rate: $("#rate").val(),
                    year: $("#year").val(),
                    type: $("#type").val(),
                }),
            }).done(function (response) {
                if (response.code == 200) {
                    // 数据提交成功后处理返回结果
                    drawPicture6(response);
                    drawPicture345(response);
                    drawPicture2(response);
                    drawPicture1(response, prepaymentList);
                } else if (response.code == 602) {
                    // 数据提交失败后处理
                    $('#errorMsg').html('<div class="alert alert-danger alert-dismissible fade show" role="alert"><strong>输入错误！</strong>'+response.data+'<button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button></div>').show();
                } else {
                    $('#errorMsg').html('<div class="alert alert-danger alert-dismissible fade show" role="alert"><strong>请到万户楼台联系管理员！</strong>'+response.message+'<button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button></div>').show();
                }
            }).fail(function (xhr, status) {
                $('#errorMsg').html('<div class="alert alert-danger alert-dismissible fade show" role="alert"><strong>请到万户楼台联系管理员！</strong>'+status+'<button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button></div>').show();
            });
        } else {
            // 有提前还款的提交
            const prepaymentElements = document.getElementsByName('prepayment-template');
            const prepaymentList = [];
            for (let i = 0; i < prepaymentElements.length; i++) {
                const element = prepaymentElements[i];
                const prepayment = {
                    prepaymentMonth: element.querySelector('input[name="prepaymentMonth"]').value,
                    repayment: element.querySelector('input[name="repayment"]').value,
                    newRate: element.querySelector('input[name="newRate"]').value,
                    newType: element.querySelector('select[name="newType"]').value,
                    repaymentType: element.querySelector('select[name="repaymentType"]').value,
                };
                prepaymentList.push(prepayment);
            }
            $.ajax({
                method: 'POST',
                url: '/calculator/prepaymentCalculator',
                dataType: 'json',
                contentType: 'application/json;charset=UTF-8',
                data: JSON.stringify({
                    amount: $("#amount").val(),
                    rate: $("#rate").val(),
                    year: $("#year").val(),
                    type: $("#type").val(),
                    prepaymentList: prepaymentList,
                }),
            }).done(function (response) {
                if (response.code == 200) {
                    // 数据提交成功后处理返回结果
                    drawPicture7(response);
                    drawPicture6(response);
                    drawPicture345(response);
                    drawPicture2(response);
                    drawPicture1(response, prepaymentList);
                } else if (response.code == 602) {
                    // 数据提交失败后处理
                    $('#errorMsg').html('<div class="alert alert-danger alert-dismissible fade show" role="alert"><strong>输入错误！</strong>'+response.data+'<button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button></div>').show();
                } else if (response.code > 700) {
                    $('#errorMsg').html('<div class="alert alert-danger alert-dismissible fade show" role="alert"><strong>输入错误！</strong>'+response.message+'<button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button></div>').show();
                } else {
                    $('#errorMsg').html('<div class="alert alert-danger alert-dismissible fade show" role="alert"><strong>请到万户楼台联系管理员！</strong>'+response.message+'<button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button></div>').show();
                }
            }).fail(function (xhr, status) {
                $('#errorMsg').html('<div class="alert alert-danger alert-dismissible fade show" role="alert"><strong>请到万户楼台联系管理员！</strong>'+status+'<button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button></div>').show();
            });
        }
    });
}

function drawPicture1(response, prepaymentList) {
    let arr = [];
    let respData = response.data;
    let repayment = 0;
    for (let i = 0, n = respData.monthLoanList.length; i < n; i++) {
        let monthLoan = respData.monthLoanList[i];
        repayment += monthLoan.repayment;
        arr.push({
            month: monthLoan.month + "（" + (monthLoan.year - 1) + "年" + monthLoan.monthInYear + "月）",
            repayment: monthLoan.repayment.toFixed(2),
            principal: monthLoan.principal.toFixed(2),
            interest: monthLoan.interest.toFixed(2),
            remainTotal: monthLoan.remainTotal.toFixed(2),
            remainPrincipal: monthLoan.remainPrincipal.toFixed(2),
            remainInterest: monthLoan.remainInterest.toFixed(2),
            totalRepayment: monthLoan.totalRepayment.toFixed(2),
            totalPrincipal: monthLoan.totalPrincipal.toFixed(2),
            totalInterest: monthLoan.totalInterest.toFixed(2),
        });
    }
    for (let i = 0, n = prepaymentList.length; i < n; i++) {
        let prepaymentElement = prepaymentList[i];
        let prepaymentMonth = prepaymentElement.prepaymentMonth;
        repayment += prepaymentElement.repayment * 10000;
        arr.splice(prepaymentMonth - 1 + i, 0, {
            month: "提前还款",
            repayment: (prepaymentElement.repayment * 10000).toFixed(2),
            principal: (prepaymentElement.repayment * 10000).toFixed(2),
            interest: "0.00",
        });
    }
    let loanAmount = respData.loanAmount;
    arr.unshift({
        month: "总计",
        repayment: repayment.toFixed(2),
        principal: loanAmount.toFixed(2),
        interest: (repayment - loanAmount).toFixed(2),
    });

    const container = document.getElementById('container1');
    const s2DataConfig = {
        fields: {
            columns: ['month', 'repayment', 'principal', 'interest', 'remainTotal', 'remainPrincipal', 'remainInterest', 'totalRepayment', 'totalPrincipal', 'totalInterest'],
        },
        meta: [
            {
                field: 'month',
                name: '期数',
            },
            {
                field: 'repayment',
                name: '当月还款',
            },
            {
                field: 'principal',
                name: '其中本金',
            },
            {
                field: 'interest',
                name: '其中利息',
            },
            {
                field: 'remainTotal',
                name: '剩余贷款',
            },
            {
                field: 'remainPrincipal',
                name: '剩余本金',
            },
            {
                field: 'remainInterest',
                name: '剩余利息',
            },
            {
                field: 'totalRepayment',
                name: '累计已还总额',
            },
            {
                field: 'totalPrincipal',
                name: '累计已还本金',
            },
            {
                field: 'totalInterest',
                name: '累计已还利息',
            },
        ],
        data: arr,
    };

    const s2Options = {
        width: 1000,
        height: 600,
        conditions: {
            background: [
                {
                    field: new RegExp('.*'),
                    mapping(fieldValue, data) {
                        if (data.month === '提前还款') {
                            return {
                                fill: '#ffe9e9',
                            };
                        }
                    },
                },
            ],
        },
    };
    const s2 = new TableSheet(container, s2DataConfig, s2Options);
    s2.render();

    const debounceRender = _.debounce((width, height) => {
        s2.changeSheetSize(width, height)
        s2.render(false) // 不重新加载数据
    }, 200)

    const resizeObserver = new ResizeObserver(([entry] = []) => {
        const [size] = entry.borderBoxSize || [];
        debounceRender(size.inlineSize, size.blockSize)
    });

    resizeObserver.observe(container);
}

function drawPicture2(response) {
    let arr = [];
    let data = response.data;
    for (let i = 0, n = data.monthLoanList.length; i < n; i++) {
        let monthLoan = data.monthLoanList[i];
        arr.push({
            months: monthLoan.month + "期",
            value: monthLoan.principal,
            type: "本金",
        });
        arr.push({
            months: monthLoan.month + "期",
            value: monthLoan.interest,
            type: "利息",
        });
    }

    const stackedBarPlot = new Bar('container2', {
        data: arr,
        isStack: true,
        xField: 'value',
        yField: 'months',
        seriesField: 'type',
        label: {
            // 可手动配置 label 数据标签位置
            position: 'middle', // 'left', 'middle', 'right'
            // 可配置附加的布局方法
            layout: [
                // 柱形图数据标签位置自动调整
                { type: 'interval-adjust-position' },
                // 数据标签防遮挡
                { type: 'interval-hide-overlap' },
                // 数据标签文颜色自动调整
                { type: 'adjust-color' },
            ],
        },
        yAxis: {
            label: {
                autoRotate: false,
            },
        },
        scrollbar: {
            type: 'vertical',
        },
        slider: {
            start: 0.0,
            end: 0.5,
            trendCfg: {
                isArea: true,
            },
        },
        width: 400,
        height: 600,
    });
    stackedBarPlot.render();
}

function drawPicture345(response) {
    let arr1 = [];
    let arr2 = [];
    let arr3 = [];
    let respData = response.data;

    for (let i = 0, n = respData.monthLoanList.length; i < n; i++) {
        let monthLoan = respData.monthLoanList[i];
        let month = monthLoan.month + '';
        arr1.push({
            name: "当月还款",
            month: month,
            value: monthLoan.repayment,
        });
        arr1.push({
            name: "其中本金",
            month: month,
            value: monthLoan.principal,
        });
        arr1.push({
            name: "其中利息",
            month: month,
            value: monthLoan.interest,
        });
        arr2.push({
            name: "剩余贷款",
            month: month,
            value: monthLoan.remainTotal,
        });
        arr2.push({
            name: "剩余本金",
            month: month,
            value: monthLoan.remainPrincipal,
        });
        arr2.push({
            name: "剩余利息",
            month: month,
            value: monthLoan.remainInterest,
        });
        arr3.push({
            name: "累计已还总额",
            month: month,
            value: monthLoan.totalRepayment,
        });
        arr3.push({
            name: "累计已还本金",
            month: month,
            value: monthLoan.totalPrincipal,
        });
        arr3.push({
            name: "累计已还利息",
            month: month,
            value: monthLoan.totalInterest,
        });
        arr3.push({
            name: "累计已还总额+剩余本金",
            month: month,
            value: monthLoan.totalRepaymentAndRemainPrincipal,
        });
    }

    const linePlot = new Line('container5', {
        data: arr1,
        padding: "auto",
        xField: 'month',
        yField: 'value',
        seriesField: 'name',
        yAxis: {
            title: {
                text: "金额（元）",
            }
        },
        xAxis: {
            title: {
                text: "还款期数（月）",
            },
            tickInterval: 12,
        },
        legend: {
            position: 'top',
        },
        smooth: true,
        tooltip: {
            customItems: originalItems => originalItems.sort(function (a, b) {
                return b.value - a.value
            })
        },
        animation: {
            appear: {
                animation: 'path-in',
                duration: 10000,
            },
        },
        interactions: [{ type: 'brush' }],
    });

    const linePlot2 = new Line('container4', {
        data: arr2,
        padding: "auto",
        xField: 'month',
        yField: 'value',
        seriesField: 'name',
        yAxis: {
            title: {
                text: "金额（元）",
            }
        },
        xAxis: {
            title: {
                text: "还款期数（月）",
            },
            tickInterval: 12,
        },
        legend: {
            position: 'top',
        },
        smooth: true,
        tooltip: {
            customItems: originalItems => originalItems.sort(function (a, b) {
                return b.value - a.value
            })
        },
        animation: {
            appear: {
                animation: 'path-in',
                duration: 10000,
            },
        },
        interactions: [{ type: 'brush' }],
    });

    const linePlot3 = new Line('container3', {
        data: arr3,
        padding: "auto",
        xField: 'month',
        yField: 'value',
        seriesField: 'name',
        yAxis: {
            title: {
                text: "金额（元）",
            }
        },
        xAxis: {
            title: {
                text: "还款期数（月）",
            },
            tickInterval: 12,
        },
        legend: {
            position: 'top',
        },
        smooth: true,
        tooltip: {
            customItems: originalItems => originalItems.sort(function (a, b) {
                return b.value - a.value
            })
        },
        animation: {
            appear: {
                animation: 'path-in',
                duration: 10000,
            },
        },
        interactions: [{ type: 'brush' }],
    });

    linePlot.render();
    linePlot2.render();
    linePlot3.render();
}

function drawPicture6(response) {
    let respData = response.data;
    const G = G2.getEngine('canvas');
    const data = [
        { type: '总本金', value: respData.loanAmount },
        { type: '总利息', value: respData.totalInterest },
    ];
    const piePlot = new Pie('container6', {
        appendPadding: 10,
        data,
        angleField: 'value',
        colorField: 'type',
        radius: 1,
        innerRadius: 0.64,
        meta: {
            value: {
                formatter: (v) => `${v} ¥`,
            },
        },
        legend: false,
        label: {
            type: 'spider',
            labelHeight: 40,
            formatter: (data, mappingData) => {
                const group = new G.Group({});
                group.addShape({
                    type: 'circle',
                    attrs: {
                        x: 0,
                        y: 0,
                        width: 40,
                        height: 50,
                        r: 5,
                        fill: mappingData.color
                    },
                });
                group.addShape({
                    type: 'text',
                    attrs: {
                        x: 10,
                        y: 8,
                        text: `${data.type}`,
                        fill: mappingData.color,
                    },
                });
                group.addShape({
                    type: 'text',
                    attrs: {
                        x: 0,
                        y: 25,
                        text: `¥${data.value} / `+ (data.percent * 100).toFixed(2) +`%`,
                        fill: 'rgba(0, 0, 0, 0.65)',
                        fontWeight: 700,
                    },
                });
                return group;
            },
        },
        statistic: {
            title: {
                offsetY: -4,
                customHtml: (container, view, datum) => {
                    const { width, height } = container.getBoundingClientRect();
                    const d = Math.sqrt(Math.pow(width / 2, 2) + Math.pow(height / 2, 2));
                    const text = datum ? datum.type : '理论总还款';
                    return renderStatistic(d, text, { fontSize: 28 });
                },
            },
            content: {
                offsetY: 4,
                style: {
                    fontSize: '32px',
                },
                customHtml: (container, view, datum, data) => {
                    const { width } = container.getBoundingClientRect();

                    const text = datum ? `¥ ${datum.value}` : `¥ ` + (data.reduce((r, d) => r + d.value, 0)).toFixed(2);
                    return renderStatistic(width, text, { fontSize: 32 });
                },
            },
        },
        // 添加 中心统计文本 交互
        interactions: [{ type: 'element-selected' }, { type: 'element-active' }, { type: 'pie-statistic-active' }],
    });

    piePlot.render();
}

function drawPicture7(response) {
    let respData = response.data;
    const G = G2.getEngine('canvas');
    const data = [
        { type: '不提前还总利息', value: respData.originalTotalInterest },
        { type: '提前还后总利息', value: respData.totalInterest },
    ];
    const piePlot = new Pie('container7', {
        appendPadding: 10,
        data,
        angleField: 'value',
        colorField: 'type',
        radius: 1,
        innerRadius: 0.64,
        meta: {
            value: {
                formatter: (v) => `${v} ¥`,
            },
        },
        legend: false,
        label: {
            type: 'spider',
            labelHeight: 40,
            formatter: (data, mappingData) => {
                const group = new G.Group({});
                group.addShape({
                    type: 'circle',
                    attrs: {
                        x: 0,
                        y: 0,
                        width: 40,
                        height: 50,
                        r: 5,
                        fill: mappingData.color
                    },
                });
                group.addShape({
                    type: 'text',
                    attrs: {
                        x: 10,
                        y: 8,
                        text: `${data.type}`,
                        fill: mappingData.color,
                    },
                });
                group.addShape({
                    type: 'text',
                    attrs: {
                        x: 0,
                        y: 25,
                        text: `¥${data.value} / `+ (data.percent * 100).toFixed(2) +`%`,
                        fill: 'rgba(0, 0, 0, 0.65)',
                        fontWeight: 700,
                    },
                });
                return group;
            },
        },
        statistic: {
            title: {
                offsetY: -4,
                customHtml: (container, view, datum) => {
                    const { width, height } = container.getBoundingClientRect();
                    const d = Math.sqrt(Math.pow(width / 2, 2) + Math.pow(height / 2, 2));
                    const text = datum ? datum.type : '共节省利息';
                    return renderStatistic(d, text, { fontSize: 28 });
                },
            },
            content: {
                offsetY: 4,
                style: {
                    fontSize: '32px',
                },
                customHtml: (container, view, datum, data) => {
                    const { width } = container.getBoundingClientRect();

                    const text = datum ? `¥ ${datum.value}` : `¥ ` + (data[0].value - data[1].value).toFixed(2);
                    return renderStatistic(width, text, { fontSize: 32 });
                },
            },
        },
        // 添加 中心统计文本 交互
        interactions: [{ type: 'element-selected' }, { type: 'element-active' }, { type: 'pie-statistic-active' }],
    });

    piePlot.render();
}

function renderStatistic(containerWidth, text, style) {
    const textWidth = measureTextWidth(text, style);
    const textHeight = style.lineHeight || style.fontSize
    const R = containerWidth / 2;
    // r^2 = (w / 2)^2 + (h - offsetY)^2
    let scale = 1;
    if (containerWidth < textWidth) {
        scale = Math.min(Math.sqrt(Math.abs(Math.pow(R, 2) / (Math.pow(textWidth / 2, 2) + Math.pow(textHeight, 2)))), 1);
    }
    const textStyleStr = `width:${containerWidth}px;`;
    return `<div style="${textStyleStr};font-size:${scale}em;line-height:${scale < 1.2 ? 1.2 : 'inherit'};">${text}</div>`;
}

function addPrepayment() {
    let prepaymentIndex = 0;
    $('#addPrepaymentBtn').click(function () {
        let $new = $('#prepayment-template').clone();
        // 修改id,使其不重复
        $new.each(function () {
            $(this).append('<button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button>');
            this.id += '-' + (prepaymentIndex);
        });
        $new.find('[id]').each(function () {
            this.id += '-' + (prepaymentIndex);
        });
        ++prepaymentIndex;
        // 插入到表单末尾
        $new.insertBefore('#addPrepaymentBtn');
        validatedPrepayment();
    });
}

// 校验输入的参数合法性
function validated() {
    const amountInput = document.getElementById('amount');
    amountInput.addEventListener('input', function (e) {
        const value = e.target.value;
        if (!/^\d+$/.test(value)) {
            e.target.value = value.replace(/\D/g, '');
        }
    });
    const yearInput = document.getElementById('year');
    yearInput.addEventListener('input', function (e) {
        const value = e.target.value;
        if (!/^\d+$/.test(value)) {
            e.target.value = value.replace(/\D/g, '');
        }
    });

    const rateInput = document.getElementById('rate');
    rateInput.addEventListener('input', function(e) {
        const value = e.target.value;

        // 整数或1-2位小数的正则表达式
        const regex = /^\d+(\.\d{1,2})?$|^[1-9]\d*$/;
        if (!regex.test(value)) {
            e.target.value = value.replace(/[^\d.]/g, '');
            // 保留最多两位小数
            if (value.split('.').length > 1 && value.split('.')[1].length > 2) {
                e.target.value = parseFloat(value).toFixed(2);
            }
        }
    });
}

function validatedPrepayment() {
    const prepaymentMonthInputs = document.getElementsByName('prepaymentMonth');
    for (let i = 0; i < prepaymentMonthInputs.length; i++) {
        const prepaymentMonthInput = prepaymentMonthInputs[i];
        // 遍历每个input
        prepaymentMonthInput.addEventListener('input', function (e) {
            const value = e.target.value;
            if (!/^\d+$/.test(value)) {
                e.target.value = value.replace(/\D/g, '');
            }
        });
    }

    const repaymentInputs = document.getElementsByName('repayment');
    for (let i = 0; i < repaymentInputs.length; i++) {
        const repaymentInput = repaymentInputs[i];
        // 遍历每个input
        repaymentInput.addEventListener('input', function (e) {
            const value = e.target.value;
            if (!/^\d+$/.test(value)) {
                e.target.value = value.replace(/\D/g, '');
            }
        });
    }

    const rateInput = document.getElementById('newRate');
    rateInput.addEventListener('input', function(e) {
        const value = e.target.value;
        // 整数或1-2位小数的正则表达式
        const regex = /^\d+(\.\d{1,2})?$|^[1-9]\d*$/;
        if (!regex.test(value)) {
            e.target.value = value.replace(/[^\d.]/g, '');
            // 保留最多两位小数
            if (value.split('.').length > 1 && value.split('.')[1].length > 2) {
                e.target.value = parseFloat(value).toFixed(2);
            }
        }
    });
}

function showPrepayment() {
    $('#prepayment1').click(function () {
        let prepayments = document.getElementsByName('prepayment-template');
        for (let i = 0; i < prepayments.length; i++) {
            prepayments[i].style.display = 'none';
        }
        $('#addPrepaymentBtn').hide();
    });

    $('#prepayment2').click(function () {
        let prepayments = document.getElementsByName('prepayment-template');
        for (let i = 0; i < prepayments.length; i++) {
            prepayments[i].style.display = 'block';
        }
        $('#addPrepaymentBtn').show();
    });
}