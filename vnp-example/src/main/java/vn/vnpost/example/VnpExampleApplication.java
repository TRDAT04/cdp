package vn.vnpost.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author Vu Sy Vuong (vusyvuong@gmail.com)
 */
@SpringBootApplication
@ComponentScan(basePackages = { "vn.vnpost.shared.syslog", "vn.vnpost.example" })
public class
VnpExampleApplication {

	public static void main(String[] args) {
		SpringApplication.run(VnpExampleApplication.class, args);
	}

}
