package vn.vnpost.example.health;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/health-test")
public class HealthTestController {

    @PostMapping("/fail")
    public String fail() {
        MockHealthIndicator.setHealthy(false);
        return "health set to DOWN";
    }

    @PostMapping("/ok")
    public String ok() {
        MockHealthIndicator.setHealthy(true);
        return "health set to UP";
    }

    @PostMapping("/db-down")
    public String dbDown() {
        MockDbHealthIndicator.setDbUp(false);
        return "DB simulated DOWN";
    }

    @PostMapping("/db-up")
    public String dbUp() {
        MockDbHealthIndicator.setDbUp(true);
        return "DB simulated UP";
    }
}
