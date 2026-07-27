package pl.krutkowski.users.configuration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

/**
 * Configuration class to display active profile on startup
 * and validate environment-specific settings
 */
@Slf4j
@Configuration
public class ProfileConfiguration {

    private final Environment environment;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.port}")
    private int serverPort;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.data.redis.host}")
    private String redisHost;

    public ProfileConfiguration(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void logActiveProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        
        log.info("=".repeat(80));
        log.info("{} Started", applicationName);
        log.info("=".repeat(80));
        log.info("Active Profiles: {}", Arrays.toString(activeProfiles));
        log.info("Server Port: {}", serverPort);
        log.info("Database URL: {}", maskSensitiveInfo(datasourceUrl));
        log.info("Redis Host: {}", redisHost);
        log.info("=".repeat(80));

        // Validate profile-specific requirements
        if (Arrays.asList(activeProfiles).contains("prod")) {
            validateProductionProfile();
        }
    }

    private void validateProductionProfile() {
        log.info("Running PRODUCTION profile validation...");
        
        // Check if using default/insecure secrets
        String jwtSecret = environment.getProperty("jwt.secret");
        if (jwtSecret != null && (jwtSecret.contains("LOCAL") || jwtSecret.contains("TEST"))) {
            log.error("PRODUCTION ERROR: Using development JWT secret!");
            throw new IllegalStateException("Production must use secure JWT secret!");
        }

        // Check if SSL is enabled for Redis
        Boolean redisSSL = environment.getProperty("spring.data.redis.ssl.enabled", Boolean.class);
        if (Boolean.FALSE.equals(redisSSL)) {
            log.warn("WARNING: Redis SSL is disabled in production");
        }

        log.info("Production profile validation passed");
    }

    private String maskSensitiveInfo(String value) {
        if (value == null || value.length() < 10) {
            return "***";
        }
        // Mask password in connection string
        return value.replaceAll("password=[^&;]+", "password=***");
    }
}
