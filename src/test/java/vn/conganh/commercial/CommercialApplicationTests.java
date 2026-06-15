package vn.conganh.commercial;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CommercialApplicationTests {

	@Test
	@DisplayName("Should load Spring application context")
	void contextLoads() {
	}

}
