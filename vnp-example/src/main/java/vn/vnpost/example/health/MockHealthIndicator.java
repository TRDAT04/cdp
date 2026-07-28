package vn.vnpost.example.health;


import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("mock")
public class MockHealthIndicator implements HealthIndicator {

    private static volatile boolean healthy = true;

    public static void setHealthy(boolean healthy) {
        MockHealthIndicator.healthy = healthy;
    }

    @Override
    public Health health() {
        if (!healthy) {
            return Health.down()
                    .withDetail("reason", "mock failure")
                    .build();
        }

        return Health.up().build();
    }
}
