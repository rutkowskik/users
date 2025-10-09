package pl.krutkowski.users.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import pl.krutkowski.users.model.TokenStats;
import pl.krutkowski.users.model.UserSessionCount;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TokenMetricsService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Token statistic
     */
    public TokenStats getTokenStats() {
        Set<String> allTokens = redisTemplate.keys("refresh_token:*");
        Set<String> allUsers = redisTemplate.keys("user_tokens:*");
        Set<String> allFamilies = redisTemplate.keys("token_family:*");
        Set<String> revokedTokens = redisTemplate.keys("revoked_token:*");

        return TokenStats.builder()
                .totalActiveTokens(allTokens != null ? allTokens.size() : 0)
                .totalActiveUsers(allUsers != null ? allUsers.size() : 0)
                .totalTokenFamilies(allFamilies != null ? allFamilies.size() : 0)
                .totalRevokedTokens(revokedTokens != null ? revokedTokens.size() : 0)
                .build();
    }

    /**
     * Top users (count session)
     */
    public List<UserSessionCount> getTopActiveUsers(int limit) {
        Set<String> userTokenKeys = redisTemplate.keys("user_tokens:*");
        if (userTokenKeys == null) return Collections.emptyList();

        return userTokenKeys.stream()
                .map(key -> {
                    String username = key.replace("user_tokens:", "");
                    Long count = redisTemplate.opsForSet().size(key);
                    return new UserSessionCount(username, count != null ? count : 0);
                })
                .sorted((a, b) -> Long.compare(b.getSessionCount(), a.getSessionCount()))
                .limit(limit)
                .collect(Collectors.toList());
    }
}






