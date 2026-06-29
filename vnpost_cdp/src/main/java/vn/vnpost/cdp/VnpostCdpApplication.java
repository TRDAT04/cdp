package vn.vnpost.cdp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import vn.vnpost.cdp.config.SecurityProperties;
import vn.vnpost.cdp.config.UnomiProperties;

@SpringBootApplication
@EnableConfigurationProperties({UnomiProperties.class, SecurityProperties.class})
public class VnpostCdpApplication {

    public static void main(String[] args) {
        SpringApplication.run(VnpostCdpApplication.class, args);
    }
}
