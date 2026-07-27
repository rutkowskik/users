package redis;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisServer;

import java.io.IOException;

@TestConfiguration
public class RedisTestConfiguration {
    @Bean
    public RedisServer redisServer() throws IOException {
        // Inny port niż produkcja
        return new RedisServer("localhost",6370);
    }
}
