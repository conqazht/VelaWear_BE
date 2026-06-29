package vn.conganh.commercial;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CommercialApplication {

	public static void main(String[] args) {
		SpringApplication.run(CommercialApplication.class, args);
	}

}
