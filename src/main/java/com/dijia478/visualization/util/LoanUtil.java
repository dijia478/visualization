package com.dijia478.visualization.util;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
import com.dijia478.visualization.bean.PrepaymentDTO;

import java.math.BigDecimal;
import java.util.*;

/**
 * 一些工具类
 *
 * @author dijia478
 * @date 2023-9-14
 */
public class LoanUtil {

    private LoanUtil() {}

    private static final TreeMap<Date, BigDecimal> FIVE_YEAR_LPR_MAP = new TreeMap<>(Comparator.reverseOrder());

    static {
        FIVE_YEAR_LPR_MAP.put(DateUtil.parse("1970-01-01"), new BigDecimal("4.85"));
        FIVE_YEAR_LPR_MAP.put(DateUtil.parse("2019-08-20"), new BigDecimal("4.85"));
        FIVE_YEAR_LPR_MAP.put(DateUtil.parse("2019-11-20"), new BigDecimal("4.80"));
        FIVE_YEAR_LPR_MAP.put(DateUtil.parse("2020-02-20"), new BigDecimal("4.75"));
        FIVE_YEAR_LPR_MAP.put(DateUtil.parse("2020-04-20"), new BigDecimal("4.65"));
        FIVE_YEAR_LPR_MAP.put(DateUtil.parse("2022-01-20"), new BigDecimal("4.60"));
        FIVE_YEAR_LPR_MAP.put(DateUtil.parse("2022-05-20"), new BigDecimal("4.45"));
        FIVE_YEAR_LPR_MAP.put(DateUtil.parse("2022-08-22"), new BigDecimal("4.30"));
        FIVE_YEAR_LPR_MAP.put(DateUtil.parse("2023-06-20"), new BigDecimal("4.20"));
        FIVE_YEAR_LPR_MAP.put(DateUtil.parse("2024-02-20"), new BigDecimal("3.95"));
        FIVE_YEAR_LPR_MAP.put(DateUtil.parse("2024-07-22"), new BigDecimal("3.85"));
//        FIVE_YEAR_LPR_MAP.put(DateUtil.parse("2024-12-20"), new BigDecimal("3.35"));
    }

    /**
     * 获取贷款发放日
     * 一般为第一次还贷日的一个月前
     *
     * @param firstPaymentDate 第一次还贷日
     * @return
     */
    public static DateTime getLoanDate(String firstPaymentDate) {
        DateTime parse = DateUtil.parse(firstPaymentDate);
        return DateUtil.offsetMonth(parse, -1);
    }

    /**
     * 添加lpr变动导致的提前还款计划
     *
     * @param rate              原始贷款利率
     * @param type              原始还款方式
     * @param firstPaymentDate  第一次还贷日
     * @param rateAdjustmentDay 利率调整日，1：1月1日，2：放贷日
     * @param firstHouse        是否首套房
     * @param prepaymentList    提前还款计划
     */
    public static void addPrepaymentList(Integer year, Double rate, Integer type, String firstPaymentDate, Integer rateAdjustmentDay, Integer firstHouse, List<PrepaymentDTO> prepaymentList) {
        Date loanDate = getLoanDate(firstPaymentDate);
        String monthAndDay = DateUtil.format(loanDate, "MM-dd");
        BigDecimal addPoint = BigDecimal.ZERO;
        for (Map.Entry<Date, BigDecimal> entry : FIVE_YEAR_LPR_MAP.entrySet()) {
            // 倒序遍历
            if (entry.getKey().before(loanDate)) {
                addPoint = NumberUtil.sub(rate, entry.getValue());
                break;
            }
        }


        DateTime today = DateUtil.date();
        int month = 0;
        BigDecimal newRate = new BigDecimal(rate.toString());
        while (true) {
            month++;
            DateTime dateTime = DateUtil.offsetMonth(loanDate, month);
            if (dateTime.isAfter(DateUtil.offset(loanDate, DateField.YEAR, year))) {
                break;
            }

            Date rateAdjustmentDate;
            if (Integer.valueOf(1).equals(rateAdjustmentDay)) {
                rateAdjustmentDate = DateUtil.parse(DateUtil.year(dateTime) + "-" + "01-01");
            } else {
                rateAdjustmentDate = DateUtil.parse(DateUtil.year(dateTime) + "-" + monthAndDay);
            }

            if (today.isAfter(rateAdjustmentDate)) {
                if (dateTime.isAfter(DateUtil.dateNew(rateAdjustmentDate).offset(DateField.YEAR, 1).offset(DateField.MONTH, 1))) {
                    break;
                }
            } else {
                if (dateTime.isAfter(DateUtil.dateNew(rateAdjustmentDate).offset(DateField.MONTH, 1))) {
                    break;
                }
            }

            if (DateUtil.betweenMonth(dateTime, rateAdjustmentDate, true) == 0) {
                if (dateTime.isAfter(DateUtil.parseDate("2023-09-24")) && firstHouse == 1) {
                    if (DateUtil.isIn(loanDate, DateUtil.parseDate("2000-01-01"), DateUtil.parseDate("2022-05-14"))) {
                        // 2019年10月8日（含）-2022年5月14日（含），下限按LPR算
                        addPoint = BigDecimal.ZERO;
                    } else if (DateUtil.isIn(loanDate, DateUtil.parseDate("2022-05-15"), DateUtil.parseDate("2023-08-31"))) {
                        // 2022年5月15日（含）-2023年8月31日（含），下限按LPR-20个基点算
                        addPoint = new BigDecimal("-0.2");
                    }
                }

                // 2024年10月31日之后，下限统一按LPR-30个基点算
                if (dateTime.isAfter(DateUtil.parseDate("2024-10-31"))) {
                    addPoint = new BigDecimal("-0.3");
                }

                BigDecimal nowRate = BigDecimal.ZERO;
                for (Map.Entry<Date, BigDecimal> entry : FIVE_YEAR_LPR_MAP.entrySet()) {
                    if (entry.getKey().before(rateAdjustmentDate)) {
                        nowRate = NumberUtil.add(addPoint, entry.getValue());
                        break;
                    }
                }
                if (newRate.compareTo(nowRate) == 0) {
                    continue;
                }
                newRate = nowRate;
                PrepaymentDTO prepaymentDTO = new PrepaymentDTO();
                if (DateUtil.isSameDay(dateTime, rateAdjustmentDate)) {
                    prepaymentDTO.setPrepaymentMonth(month + 1);
                } else {
                    prepaymentDTO.setPrepaymentMonth(month);
                }
                prepaymentDTO.setRepayment(0);
                prepaymentDTO.setNewRate(newRate);
                // 这个type会在后面重新设置，这里这样取不对
                prepaymentDTO.setNewType(type);
                prepaymentDTO.setRepaymentType(2);
                prepaymentDTO.setLprRate(1);
                prepaymentList.add(prepaymentDTO);
            }
        }

        // 2023年9月25日存量房贷利率调整
        adjustmentOfOutstandingLoans2023(year, type, rateAdjustmentDay, firstHouse, prepaymentList, loanDate, monthAndDay);
        // 2024年10月31日存量房贷利率调整
        adjustmentOfOutstandingLoans2024(year, type, rateAdjustmentDay, firstHouse, prepaymentList, loanDate, monthAndDay);
    }

    /**
     * 2023年9月25日存量房贷利率调整
     *
     * @param year
     * @param type
     * @param rateAdjustmentDay
     * @param firstHouse
     * @param prepaymentList
     * @param loanDate
     * @param monthAndDay
     */
    private static void adjustmentOfOutstandingLoans2023(Integer year, Integer type, Integer rateAdjustmentDay, Integer firstHouse, List<PrepaymentDTO> prepaymentList, Date loanDate, String monthAndDay) {
        // 2023-9-25之后申请的贷款，不需要下面的逻辑
        if (DateUtil.parseDate("2023-09-25").isBefore(loanDate)) {
            return;
        }
        // 2023-9-25之前就还完的贷款，不需要下面的逻辑
        if (DateUtil.parseDate("2023-09-25").isAfter(DateUtil.offset(loanDate, DateField.YEAR, year))) {
            return;
        }
        // 非首套房，不需要下面的逻辑
        if (firstHouse == 0) {
            return;
        }
        String loanYear = "2023";
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(loanDate);
        if (calendar.get(Calendar.YEAR) == 2023) {
            loanYear = "2024";
        }
        Date rateAdjustmentDate;
        if (Integer.valueOf(1).equals(rateAdjustmentDay)) {
            rateAdjustmentDate = DateUtil.parse(loanYear + "-01-01");
        } else {
            rateAdjustmentDate = DateUtil.parse(loanYear + "-" + monthAndDay);
        }
        BigDecimal nowRate = BigDecimal.ZERO;
        for (Map.Entry<Date, BigDecimal> entry : FIVE_YEAR_LPR_MAP.entrySet()) {
            if (!entry.getKey().before(rateAdjustmentDate)) {
               continue;
            }
            if (DateUtil.isIn(loanDate, DateUtil.parseDate("2000-01-01"), DateUtil.parseDate("2022-05-14"))) {
                // 2019年10月8日（含）-2022年5月14日（含），下限按LPR算
                nowRate = NumberUtil.add(0, entry.getValue());
            } else if (DateUtil.isIn(loanDate, DateUtil.parseDate("2022-05-15"), DateUtil.parseDate("2023-08-31"))) {
                // 2022年5月15日（含）-2023年8月31日（含），下限按LPR-20个基点算
                nowRate = NumberUtil.add(-0.2, entry.getValue());
            }
            break;
        }

        PrepaymentDTO prepaymentDTO = new PrepaymentDTO();
        if (DateUtil.dayOfMonth(loanDate) <= 25) {
            prepaymentDTO.setPrepaymentMonth((int)DateUtil.betweenMonth(loanDate, DateUtil.parseDate("2023-09-25"), true) + 1);
        } else {
            prepaymentDTO.setPrepaymentMonth((int)DateUtil.betweenMonth(loanDate, DateUtil.parseDate("2023-09-25"), true));
        }
        prepaymentDTO.setRepayment(0);
        prepaymentDTO.setNewRate(nowRate);
        prepaymentDTO.setNewType(type);
        prepaymentDTO.setRepaymentType(2);
        prepaymentDTO.setLprRate(1);
        prepaymentList.add(prepaymentDTO);
    }

    /**
     * 2024年10月31日存量房贷利率调整
     *
     * @param year
     * @param type
     * @param rateAdjustmentDay
     * @param firstHouse
     * @param prepaymentList
     * @param loanDate
     * @param monthAndDay
     */
    private static void adjustmentOfOutstandingLoans2024(Integer year, Integer type, Integer rateAdjustmentDay, Integer firstHouse, List<PrepaymentDTO> prepaymentList, Date loanDate, String monthAndDay) {
        // 2024-10-31之后申请的贷款，不需要下面的逻辑
        if (DateUtil.parseDate("2024-10-31").isBefore(loanDate)) {
            return;
        }
        // 2024-10-31之前就还完的贷款，不需要下面的逻辑
        if (DateUtil.parseDate("2024-10-31").isAfter(DateUtil.offset(loanDate, DateField.YEAR, year))) {
            return;
        }
        String loanYear = "2024";
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(loanDate);
        if (calendar.get(Calendar.YEAR) == 2024) {
            loanYear = "2025";
        }
        Date rateAdjustmentDate;
        if (Integer.valueOf(1).equals(rateAdjustmentDay)) {
            rateAdjustmentDate = DateUtil.parse(loanYear + "-01-01");
        } else {
            rateAdjustmentDate = DateUtil.parse(loanYear + "-" + monthAndDay);
        }
        BigDecimal nowRate = BigDecimal.ZERO;
        for (Map.Entry<Date, BigDecimal> entry : FIVE_YEAR_LPR_MAP.entrySet()) {
            if (!entry.getKey().before(rateAdjustmentDate)) {
                continue;
            }

            // 下限按LPR-30个基点算
            nowRate = NumberUtil.add(-0.3, entry.getValue());
            break;
        }

        PrepaymentDTO prepaymentDTO = new PrepaymentDTO();
        if (DateUtil.dayOfMonth(loanDate) <= 31) {
            prepaymentDTO.setPrepaymentMonth((int)DateUtil.betweenMonth(loanDate, DateUtil.parseDate("2024-10-31"), true) + 1);
        } else {
            prepaymentDTO.setPrepaymentMonth((int)DateUtil.betweenMonth(loanDate, DateUtil.parseDate("2024-10-31"), true));
        }
        prepaymentDTO.setRepayment(0);
        prepaymentDTO.setNewRate(nowRate);
        prepaymentDTO.setNewType(type);
        prepaymentDTO.setRepaymentType(2);
        prepaymentDTO.setLprRate(1);
        prepaymentList.add(prepaymentDTO);
    }

}
