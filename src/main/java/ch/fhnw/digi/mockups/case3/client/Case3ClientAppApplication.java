package ch.fhnw.digi.mockups.case3.client;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class Case3ClientAppApplication {

	public static void main(String[] args) {
		SpringApplicationBuilder b = new SpringApplicationBuilder(Case3ClientAppApplication.class);
		b.headless(false).run(args);
	}
}
