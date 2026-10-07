package dev.toniess.splitly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SplitlyApplication {
	public static void main(String[] args) {
		SpringApplication.run(SplitlyApplication.class, args);
	}
}
