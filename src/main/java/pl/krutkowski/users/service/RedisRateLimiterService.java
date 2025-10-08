package pl.krutkowski.users.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisRateLimiterService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Check if user not extend refresh limit
     */
    public boolean allowRefresh(String username) {
        String key = "refresh_rate:" + username;
        Long attempts = redisTemplate.opsForValue().increment(key);

        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, 1, TimeUnit.MINUTES);
        }

        // Max 10 refresh per minute
        return attempts <= 10;
    }

    public void resetRefreshRate(String username) {
        String key = "refresh_rate:" + username;
        redisTemplate.delete(key);
    }
}
