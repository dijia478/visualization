package com.dijia478.visualization.service;

import com.dijia478.visualization.bean.*;
import com.dijia478.visualization.controller.LoanController;
import com.dijia478.visualization.service.impl.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MortgageAmountPrecisionTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private LoanController controller;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        PrepaymentCalculator calculator = new PrepaymentCalculator();
        ReflectionTestUtils.setField(calculator, "equalRepaymentCalculator", new EqualRepaymentCalculator());
        ReflectionTestUtils.setField(calculator, "equalPrincipalCalculator", new EqualPrincipalCalculator());
        controller = new LoanController();
        ReflectionTestUtils.setField(controller, "prepaymentCalculator", calculator);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void fractionalWanAmountCanPayOffTheExactRemainingPrincipalForBothMethods() throws Exception {
        for (int type : new int[]{1, 2}) {
            BigDecimal remaining = controller.prepaymentCalculator(input(type)).getMonthLoanList().get(0).getRemainPrincipal();
            mvc.perform(post("/calculator/prepaymentCalculator").contentType(MediaType.APPLICATION_JSON)
                            .content(request(type, remaining.movePointLeft(4))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.payoffDate").value("2024-01-15"))
                    .andExpect(jsonPath("$.appliedPrepayments[0].repayment").value(remaining.doubleValue()))
                    .andExpect(jsonPath("$.monthLoanList[0].remainPrincipal").value(0))
                    .andExpect(jsonPath("$.monthLoanList.length()").value(1));
        }
    }

    @Test
    void partialPrepaymentsKeepEveryCentIncludingOneCent() throws Exception {
        for (String amount : new String[]{"10.123456", "0.000001"}) {
            BigDecimal extra = new BigDecimal(amount).movePointRight(4);
            for (int type : new int[]{1, 2}) {
                BigDecimal remaining = controller.prepaymentCalculator(input(type)).getMonthLoanList().get(0).getRemainPrincipal();
                mvc.perform(post("/calculator/prepaymentCalculator").contentType(MediaType.APPLICATION_JSON)
                                .content(request(type, new BigDecimal(amount))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.appliedPrepayments[0].repayment").value(extra.doubleValue()))
                        .andExpect(jsonPath("$.monthLoanList[0].remainPrincipal").value(remaining.subtract(extra).doubleValue()));
            }
        }
    }

    @Test
    void subCentAndNegativeAmountsAreRejectedInsteadOfTruncated() throws Exception {
        for (String amount : new String[]{"1.0000001", "-0.000001"}) {
            mvc.perform(post("/calculator/prepaymentCalculator").contentType(MediaType.APPLICATION_JSON)
                            .content(request(1, new BigDecimal(amount))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void multipleFractionalPrepaymentsKeepActualDateInterestAndSettleTheExactBalance() throws Exception {
        for (int type : new int[]{1, 2}) {
            MonthLoan first = controller.prepaymentCalculator(input(type)).getMonthLoanList().get(0);
            BigDecimal partial = new BigDecimal("200000.01");
            BigDecimal remainder = first.getRemainPrincipal().subtract(partial);
            ObjectNode json = mapper.valueToTree(input(type));
            com.fasterxml.jackson.databind.node.ArrayNode events = json.putArray("prepaymentList");
            addEvent(events.addObject(), type, "2024-01-20", partial.movePointLeft(4));
            addEvent(events.addObject(), type, "2024-01-24", remainder.movePointLeft(4));
            JsonNode result = mapper.readTree(mvc.perform(post("/calculator/prepaymentCalculator")
                            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(json)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            BigDecimal expectedInterest = first.getRemainPrincipal().multiply(new BigDecimal("5"))
                    .add(remainder.multiply(new BigDecimal("4"))).multiply(new BigDecimal("3.5"))
                    .divide(new BigDecimal("36000"), 20, RoundingMode.HALF_UP)
                    .add(first.getInterestRemainder()).setScale(2, RoundingMode.DOWN);
            assertEquals("2024-01-24", result.path("payoffDate").asText());
            assertEquals(2, result.path("monthLoanList").size());
            assertEquals(0, expectedInterest.compareTo(result.path("monthLoanList").get(1).path("interest").decimalValue()));
            assertEquals(0, result.path("monthLoanList").get(1).path("remainPrincipal").decimalValue().signum());
            assertEquals(0, partial.compareTo(result.path("appliedPrepayments").get(0).path("repayment").decimalValue()));
            assertEquals(0, remainder.compareTo(result.path("appliedPrepayments").get(1).path("repayment").decimalValue()));
        }
    }

    @Test
    void aOneCentRemainingBalanceIsSettledRatherThanRejectedAfterPrincipalRoundsToZero() {
        for (int type : new int[]{1, 2}) {
            for (double rate : new double[]{0.0, 3.5}) {
                StockLoanDTO input = input(type);
                input.setAmount(1);
                input.setYear(1);
                input.setRate(rate);
                BigDecimal remaining = controller.prepaymentCalculator(input).getMonthLoanList().get(0).getRemainPrincipal();
                PrepaymentDTO event = new PrepaymentDTO();
                event.setPrepaymentDate("2024-01-15");
                event.setRepayment(remaining.subtract(new BigDecimal("0.01")).movePointLeft(4));
                event.setNewType(type);
                event.setRepaymentType(2);
                input.setPrepaymentList(Collections.singletonList(event));
                TotalLoan result = controller.prepaymentCalculator(input);
                assertEquals(0, new BigDecimal("0.01").compareTo(result.getMonthLoanList().get(0).getRemainPrincipal()));
                BigDecimal paidPrincipal = BigDecimal.ZERO;
                for (MonthLoan row : result.getMonthLoanList()) {
                    paidPrincipal = paidPrincipal.add(row.getPrincipal()).add(row.getExtraRepayment());
                    assertTrue(row.getPrincipal().signum() >= 0);
                    assertEquals(0, row.getPrincipal().add(row.getInterest()).compareTo(row.getRepayment()));
                }
                assertEquals(0, result.getLoanAmount().compareTo(paidPrincipal));
                assertEquals(0, result.getMonthLoanList().get(result.getMonthLoanList().size() - 1).getRemainPrincipal().signum());
                if (type == 2 || rate == 0.0) {
                    assertEquals(12, result.getMonthLoanList().size());
                    assertEquals("2024-12-15", result.getMonthLoanList().get(11).getDateFormat());
                    assertEquals(0, new BigDecimal("0.01").compareTo(result.getMonthLoanList().get(11).getPrincipal()));
                }
            }
        }
    }

    private byte[] request(int type, BigDecimal amount) throws Exception {
        ObjectNode json = mapper.valueToTree(input(type));
        ObjectNode event = json.putArray("prepaymentList").addObject();
        addEvent(event, type, "2024-01-15", amount);
        return mapper.writeValueAsBytes(json);
    }

    private void addEvent(ObjectNode event, int type, String date, BigDecimal amount) {
        event.put("prepaymentDate", date);
        event.put("repayment", amount);
        event.put("newType", type);
        event.put("repaymentType", 2);
    }

    private StockLoanDTO input(int type) {
        StockLoanDTO input = new StockLoanDTO();
        input.setAmount(100);
        input.setYear(30);
        input.setRate(3.5);
        input.setType(type);
        input.setFirstPaymentDate("2024-01-15");
        input.setRateAdjustmentDay(1);
        input.setRateMode("FIXED");
        input.setFirstHouse(1);
        return input;
    }
}
