package com.dijia478.visualization;

import com.dijia478.visualization.util.LoanUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;

@SpringBootTest
class VisualizationApplicationTests {

	@Test
	void test() {
		LoanUtil.addPrepaymentList(30, 0.0563d, 1, "2021-08-27", 1, new ArrayList<>());
	}

}
