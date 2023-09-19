package com.dijia478.visualization.util;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class NumUtilTest {

    @Test
    void test() {
        BigDecimal a = new BigDecimal("16.0");
        BigDecimal b = new BigDecimal("0.5");
        BigDecimal c = new BigDecimal("2.0");

        // 乘法
        BigDecimal mul1 = NumUtil.mul(a, b);
        assertEquals(mul1.setScale(2, RoundingMode.HALF_UP).toString(), "8.00");
        BigDecimal mul2 = NumUtil.mul(a, b, c);
        assertEquals(mul2.setScale(2, RoundingMode.HALF_UP).toString(), "16.00");

        // 除法
        BigDecimal div1 = NumUtil.div(a, b);
        assertEquals(div1.setScale(2, RoundingMode.HALF_UP).toString(), "32.00");
        double div2 = NumUtil.div(16.0d, 0.5d);
        assertEquals(div2, 32.0d);
    }

}