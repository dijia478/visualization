package com.dijia478.visualization.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 精确地加减乘除计算器
 *
 * @author dijia478
 * @date 2023/9/19
 */
public class NumUtil {

    private static final int DEFAULT_SCALE = 20;

    private NumUtil() {}

    /**
     * 两数相加
     * a + b
     *
     * @param a
     * @param b
     * @return
     */
    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return a.add(b);
    }

    /**
     * 三数相加
     * a + b + c
     *
     * @param a
     * @param b
     * @param c
     * @return
     */
    public static BigDecimal add(BigDecimal a, BigDecimal b, BigDecimal c) {
        return a.add(b).add(c);
    }

    /**
     * 两数相减
     * a - b
     *
     * @param a
     * @param b
     * @return
     */
    public static BigDecimal sub(BigDecimal a, BigDecimal b) {
        return a.subtract(b);
    }


    /**
     * 两数相乘
     * a × b
     *
     * @param a
     * @param b
     * @return
     */
    public static BigDecimal mul(BigDecimal a, BigDecimal b) {
        return a.multiply(b);
    }

    /**
     * 三数相乘
     * a × b × c
     *
     * @param a
     * @param b
     * @param c
     * @return
     */
    public static BigDecimal mul(BigDecimal a, BigDecimal b, BigDecimal c) {
        return a.multiply(b).multiply(c);
    }

    /**
     * 两数相除
     * a ÷ b
     *
     * @param a
     * @param b
     * @return
     */
    public static BigDecimal div(BigDecimal a, BigDecimal b) {
        return a.divide(b, DEFAULT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 两数相除
     * a ÷ b
     *
     * @param a
     * @param b
     * @return
     */
    public static double div(double a, double b) {
        return BigDecimal.valueOf(a).divide(BigDecimal.valueOf(b), DEFAULT_SCALE, RoundingMode.HALF_UP).doubleValue();
    }

}
