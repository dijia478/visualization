package com.dijia478.visualization.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LoanPlanningApiTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;

    @Test
    void comparisonAndSolverUseTheExistingWrappedApiContract() throws Exception {
        Map<String, Object> request = request();
        mvc.perform(post("/calculator/planning/compare").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.scenarios[0].key").value("baseline"))
                .andExpect(jsonPath("$.data.scenarios[1].proposal.repayment").value(10))
                .andExpect(jsonPath("$.data.scenarios[1].proposal.newType").value(1))
                .andExpect(jsonPath("$.data.scenarios[1].nextPaymentDate").value("2025-02-15"));
        request.put("goal", "PAYMENT");
        request.put("targetPayment", 3500);
        mvc.perform(post("/calculator/planning/solve").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.scenario.key").value("reduce"))
                .andExpect(jsonPath("$.data.scenario.proposal.repayment").isNumber());
    }

    @Test
    void nestedLoanAndMoneyPrecisionAreValidatedAtTheHttpBoundary() throws Exception {
        Map<String, Object> request = request();
        request.put("repayment", 0.0000001);
        mvc.perform(post("/calculator/planning/compare").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(request)))
                .andExpect(jsonPath("$.code").value(602));
        request = request();
        Map<String, Object> invalidLoan = new LinkedHashMap<>();
        invalidLoan.put("amount", 0);
        request.put("loan", invalidLoan);
        mvc.perform(post("/calculator/planning/compare").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(request)))
                .andExpect(jsonPath("$.code").value(602));
        request = request();
        request.put("prepaymentDate", "2025-01-19");
        mvc.perform(post("/calculator/planning/compare").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(request)))
                .andExpect(jsonPath("$.code").value(801));
    }

    private Map<String, Object> request() {
        Map<String, Object> loan = new LinkedHashMap<>();
        loan.put("amount", 100);
        loan.put("year", 30);
        loan.put("rate", 3.5);
        loan.put("type", 1);
        loan.put("firstPaymentDate", "2024-01-15");
        loan.put("rateAdjustmentDay", 1);
        loan.put("rateMode", "FIXED");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("loan", loan);
        request.put("comparisonDate", "2025-01-20");
        request.put("prepaymentDate", "2025-02-10");
        request.put("repayment", 10);
        return request;
    }
}
