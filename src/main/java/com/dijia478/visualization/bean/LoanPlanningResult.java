package com.dijia478.visualization.bean;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

public class LoanPlanningResult {
    private LoanPlanningResult() {}

    @Data
    public static class Scenario {
        private String key;
        private String name;
        private boolean available = true;
        private String message;
        private TotalLoan plan;
        private PrepaymentDTO proposal;
        private BigDecimal nextMonthlyPayment;
        private String nextPaymentDate;
        private BigDecimal futureInterest;
        private BigDecimal nextYearPayment;
        private String payoffDate;
        private int remainingMonths;
        private BigDecimal savedInterest;
        private int monthsSaved;
    }

    @Data
    public static class Comparison {
        private String comparisonDate;
        private String prepaymentDate;
        private List<Scenario> scenarios;
    }

    @Data
    public static class Solution {
        private boolean available = true;
        private String message;
        /** 提前归还本金，单位万元，六位小数对应一分。 */
        private BigDecimal repayment;
        private Scenario scenario;
        private int iterations;
    }
}
