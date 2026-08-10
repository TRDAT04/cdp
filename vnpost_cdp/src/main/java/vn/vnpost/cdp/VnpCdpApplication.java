package vn.vnpost.cdp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author Vu Sy Vuong (vusyvuong@gmail.com)
 */
@SpringBootApplication
@ComponentScan(basePackages = { "vn.vnpost.shared.syslog", "vn.vnpost.cdp" })
public class
VnpCdpApplication {

	public static void main(String[] args) {
		SpringApplication.run(VnpCdpApplication.class, args);
	}

}
