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
    }

}