package pl.krutkowski.users.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import pl.krutkowski.users.domain.RefreshTokenData;
import pl.krutkowski.users.exception.domain.InvalidTokenException;
import pl.krutkowski.users.exception.domain.TokenReusedException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisTokenService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String TOKEN_PREFIX = "refresh_token:";
    private static final String USER_TOKENS_PREFIX = "user_tokens:";
    private static final String TOKEN_FAMILY_PREFIX = "token_family:";
    private static final String REVOKED_PREFIX = "revoked_token:";

    /**
     * Save refresh token in Redis
     */
    public void saveRefreshToken(RefreshTokenData tokenData, long ttlSeconds) {
        String tokenHash = tokenData.getTokenHash();
        String username = tokenData.getUsername();
        String tokenFamily = tokenData.getTokenFamily();

        // 1. Save token (key: token hash, value: token data)
        String tokenKey = TOKEN_PREFIX + tokenHash;
        redisTemplate.opsForValue().set(tokenKey, tokenData, ttlSeconds, TimeUnit.SECONDS);

        // 2. Add token for user ("logout all")
        String userTokensKey = USER_TOKENS_PREFIX + username;
        redisTemplate.opsForSet().add(userTokensKey, tokenHash);
        redisTemplate.expire(userTokensKey, ttlSeconds, TimeUnit.SECONDS);

        // 3. Save token family (rotation detection)
        if (tokenFamily != null) {
            String familyKey = TOKEN_FAMILY_PREFIX + tokenFamily;
            redisTemplate.opsForSet().add(familyKey, tokenHash);
            redisTemplate.expire(familyKey, ttlSeconds, TimeUnit.SECONDS);
        }

        log.info("Refresh token saved for user: {}, family: {}", username, tokenFamily);
    }

    /**
     * Get data for refresh token
     */
    public Optional<RefreshTokenData> getRefreshToken(String token) {
        String tokenHash = hashToken(token);
        String tokenKey = TOKEN_PREFIX + tokenHash;

        if (isTokenRevoked(tokenHash)) {
            log.warn("Attempted to use revoked token: {}", tokenHash.substring(0, 8));
            return Optional.empty();
        }

        Object data = redisTemplate.opsForValue().get(tokenKey);

        if(data instanceof RefreshTokenData tokenData){
            tokenData.setLastUsedAt(LocalDateTime.now());
            redisTemplate.opsForValue().set(tokenKey, tokenData);
            return Optional.of(tokenData);
        }

        return Optional.empty();
    }

    /**
     * Validate token
     */
    public boolean isTokenValid(String token) {
        String tokenHash = hashToken(token);
        String tokenKey = TOKEN_PREFIX + tokenHash;

        if (isTokenRevoked(tokenHash)) {
            return false;
        }

        Boolean exists = redisTemplate.hasKey(tokenKey);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Revoke token
     */
    public void revokeToken(String token) {
        String tokenHash = hashToken(token);
        revokeTokenByHash(tokenHash);
    }

    /**
     * Revoke token by hash (session)
     */
    public void revokeTokenByHash(String tokenHash) {
        String tokenKey = TOKEN_PREFIX + tokenHash;

        RefreshTokenData tokenData = (RefreshTokenData) redisTemplate.opsForValue().get(tokenKey);

        if (tokenData != null) {
            saveRevokedTokenInfo(tokenData);

            // delete for main key
            redisTemplate.delete(tokenKey);

            String userTokensKey = USER_TOKENS_PREFIX + tokenData.getUsername();
            redisTemplate.opsForSet().remove(userTokensKey, tokenHash);

            if (tokenData.getTokenFamily() != null) {
                String familyKey = TOKEN_FAMILY_PREFIX + tokenData.getTokenFamily();
                redisTemplate.opsForSet().remove(familyKey, tokenHash);
            }

            markAsRevoked(tokenHash, 3600); // 1 godzina

            log.info("Token revoked for user: {}", tokenData.getUsername());
        }
    }

    /**
     * Revoke all user's token (logout all devices)
     */
    public void revokeAllUserTokens(String username) {
        String userTokensKey = USER_TOKENS_PREFIX + username;
        Set<Object> tokenHashes = redisTemplate.opsForSet().members(userTokensKey);

        if (tokenHashes != null && !tokenHashes.isEmpty()) {
            for (Object tokenHashObj : tokenHashes) {
                String tokenHash = (String) tokenHashObj;
                String tokenKey = TOKEN_PREFIX + tokenHash;

                RefreshTokenData tokenData = (RefreshTokenData) redisTemplate.opsForValue().get(tokenKey);

                redisTemplate.delete(tokenKey);

                if (tokenData != null && tokenData.getTokenFamily() != null) {
                    String familyKey = TOKEN_FAMILY_PREFIX + tokenData.getTokenFamily();
                    redisTemplate.opsForSet().remove(familyKey, tokenHash);
                }

                markAsRevoked(tokenHash, 3600);
            }

            redisTemplate.delete(userTokensKey);

            log.info("All tokens revoked for user: {}, count: {}", username, tokenHashes.size());
        }
    }

    /**
     * Revoke token family (when token reuse)
     */
    public void revokeTokenFamily(String tokenFamily) {
        String familyKey = TOKEN_FAMILY_PREFIX + tokenFamily;
        Set<Object> tokenHashes = redisTemplate.opsForSet().members(familyKey);

        if (tokenHashes != null && !tokenHashes.isEmpty()) {
            log.warn("Token family revoked due to reuse detection: {}, tokens: {}",
                    tokenFamily, tokenHashes.size());

            for (Object tokenHashObj : tokenHashes) {
                String tokenHash = (String) tokenHashObj;
                String tokenKey = TOKEN_PREFIX + tokenHash;

                RefreshTokenData tokenData = (RefreshTokenData) redisTemplate.opsForValue().get(tokenKey);

                redisTemplate.delete(tokenKey);

                if (tokenData != null) {
                    String userTokensKey = USER_TOKENS_PREFIX + tokenData.getUsername();
                    redisTemplate.opsForSet().remove(userTokensKey, tokenHash);
                }

                markAsRevoked(tokenHash, 3600);
            }

            redisTemplate.delete(familyKey);
        }
    }

    /**
     * Get all user's session
     */
    public List<RefreshTokenData> getUserActiveSessions(String username) {
        String userTokensKey = USER_TOKENS_PREFIX + username;
        Set<Object> tokenHashes = redisTemplate.opsForSet().members(userTokensKey);

        if (tokenHashes == null || tokenHashes.isEmpty()) {
            return Collections.emptyList();
        }

        List<RefreshTokenData> sessions = new ArrayList<>();
        for (Object tokenHashObj : tokenHashes) {
            String tokenHash = (String) tokenHashObj;
            String tokenKey = TOKEN_PREFIX + tokenHash;
            RefreshTokenData tokenData = (RefreshTokenData) redisTemplate.opsForValue().get(tokenKey);

            if (tokenData != null && !tokenData.isExpired()) {
                sessions.add(tokenData);
            }
        }

        // Sort to find the latest
        sessions.sort((a, b) -> b.getIssuedAt().compareTo(a.getIssuedAt()));

        return sessions;
    }

    /**
     * Set token to revoked
     */
    private void markAsRevoked(String tokenHash, long ttlSeconds) {
        String revokedKey = REVOKED_PREFIX + tokenHash;
        redisTemplate.opsForValue().set(revokedKey, "revoked", ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * Check if token is revoked
     */
    private boolean isTokenRevoked(String tokenHash) {
        String revokedKey = REVOKED_PREFIX + tokenHash;
        return Boolean.TRUE.equals(redisTemplate.hasKey(revokedKey));
    }

    /**
     * Hash token (SHA-256)
     */
    public String hashToken(String token) {
        return DigestUtils.sha256Hex(token);
    }

    /**
     * Refresh with reuse detection
     */
    public RefreshTokenData refreshWithReuseDetection(String refreshToken)
            throws TokenReusedException, InvalidTokenException {

        String tokenHash = hashToken(refreshToken);

        if (isTokenRevoked(tokenHash)) {
            RefreshTokenData revokedToken = getRevokedTokenInfo(tokenHash);
            if (revokedToken != null && revokedToken.getTokenFamily() != null) {
                log.error("TOKEN REUSE DETECTED! Revoking family: {}",
                        revokedToken.getTokenFamily());
                revokeTokenFamily(revokedToken.getTokenFamily());

                throw new TokenReusedException(
                        "Token reuse detected. All sessions have been terminated for security.");
            }
        }

        return getRefreshToken(refreshToken)
                .orElseThrow(() -> new InvalidTokenException("Token not found"));
    }

    /**
     * Save information revoked token (reuse detection)
     */
    private void saveRevokedTokenInfo(RefreshTokenData tokenData) {
        String key = "revoked_info:" + tokenData.getTokenHash();
        redisTemplate.opsForValue().set(key, tokenData, 24, TimeUnit.HOURS);
    }

    private RefreshTokenData getRevokedTokenInfo(String tokenHash) {
        String key = "revoked_info:" + tokenHash;
        return (RefreshTokenData) redisTemplate.opsForValue().get(key);
    }
}