package pl.krutkowski.users.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int ATTEMPT_INCREMENT = 1;
    private final LoadingCache<String, Integer> loginAttemptCache;

    public LoginAttemptService() {
        super();
        loginAttemptCache = CacheBuilder.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(100).build(new CacheLoader<>() {
                    public Integer load(String key) throws Exception {
                        return 0;
                    }
                });
    }

    public void evictUserFromCache(String username) {
        loginAttemptCache.invalidate(username);
    }

    public void addUserToCache(String username) {
        int attempts;
        try {
            attempts = ATTEMPT_INCREMENT + loginAttemptCache.get(username);
            loginAttemptCache.put(username, attempts);
        } catch (ExecutionException e) {
            log.error("Failed to add user to Cache", e);
        }
    }

    public boolean hasExceededMaxAttempt(String username) {
        try {
            return loginAttemptCache.get(username) >= MAX_ATTEMPTS;
        } catch (ExecutionException e) {
            log.error("Failed to check if user is exceed max attempts", e);
        }
        return false;
    }

}
