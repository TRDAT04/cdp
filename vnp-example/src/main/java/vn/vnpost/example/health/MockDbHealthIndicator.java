package vn.vnpost.example.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("mockDb")
public class MockDbHealthIndicator implements HealthIndicator {

    private static volatile boolean dbUp = true;

    public static void setDbUp(boolean dbUp) {
        MockDbHealthIndicator.dbUp = dbUp;
    }

    @Override
    public Health health() {
        if (!dbUp) {
            return Health.down()
                    .withDetail("error", "Database unavailable")
                    .build();
        }

        return Health.up().build();
    }
}
