package pl.krutkowski.users.service.impl;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import pl.krutkowski.users.entity.RefreshTokenData;
import pl.krutkowski.users.entity.User;
import pl.krutkowski.users.model.UserPrinciple;
import pl.krutkowski.users.exception.domain.InvalidTokenException;
import pl.krutkowski.users.exception.domain.TokenReusedException;
import pl.krutkowski.users.model.DataForRefreshTokenStore;
import pl.krutkowski.users.service.RedisTokenService;
import pl.krutkowski.users.service.TokenService;
import pl.krutkowski.users.utility.JTWTokenProvider;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    public static final String USER_AGENT_HEADER_NAME = "User-Agent";
    private final JTWTokenProvider jwtTokenProvider;
    private final RedisTokenService redisTokenService;

    @Value("${jwt.access-token.expiration:900000}") // 15 minut
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token.expiration:604800000}") // 7 dni
    private long refreshTokenExpiration;

    /**
     * Login - generate token and store refresh token in redis
     * TOKENS IN COOKIES (HttpOnly, Secure, SameSite)
     */
    @Override
    public void login(UserPrinciple userPrinciple, HttpServletResponse response, HttpServletRequest request) {
        String username = userPrinciple.getUsername();

        // 1. Access Token (short TTL)
        String accessToken = jwtTokenProvider.generateToken(userPrinciple, accessTokenExpiration);

        // 2. Refresh Token (long TTL)
        String refreshToken = jwtTokenProvider.generateToken(userPrinciple, refreshTokenExpiration);

        DataForRefreshTokenStore dataForRefreshTokenStore = new DataForRefreshTokenStore(refreshToken, request, username, null);

        RefreshTokenData refreshTokenData = prepareTokenDataForRedis(dataForRefreshTokenStore);
        saveRefreshTokenInRedis(refreshTokenData);

        setAccessTokenCookie(response, accessToken);
        setRefreshTokenCookie(response, refreshToken);

        log.info("User logged in: {}, token family: {}", username, refreshTokenData.getTokenFamily());
    }

    /**
     * Refresh - check in Redis and generate new tokens
     * Return void - tokens set in cookies
     */
    @Override
    public void refresh(HttpServletRequest request, HttpServletResponse response)
            throws InvalidTokenException, TokenReusedException {

        String refreshToken = extractRefreshTokenFromCookie(request);
        if (refreshToken == null) {
            throw new InvalidTokenException("Refresh token not found in cookies");
        }
        if (!jwtTokenProvider.isTokenValid(refreshToken)) {
            throw new InvalidTokenException("Invalid refresh token");
        }
        //todo check it
        RefreshTokenData refreshTokenData = redisTokenService.refreshWithReuseDetection(refreshToken);

        if (refreshTokenData.isExpired()) {
            redisTokenService.revokeToken(refreshToken);
            throw new InvalidTokenException("Refresh token expired");
        }

        //(Token Rotation)
        redisTokenService.revokeToken(refreshToken);

        UserPrinciple userPrinciple = generateUserPrinciple(refreshToken);

        String newAccessToken = jwtTokenProvider.generateToken(userPrinciple, accessTokenExpiration);
        String newRefreshToken = jwtTokenProvider.generateToken(userPrinciple, refreshTokenExpiration);

        DataForRefreshTokenStore dataForRefreshTokenStore = new DataForRefreshTokenStore(newRefreshToken, request, userPrinciple.getUsername(), refreshTokenData.getTokenFamily());
        RefreshTokenData newRefreshTokenData = prepareTokenDataForRedis(dataForRefreshTokenStore);
        saveRefreshTokenInRedis(newRefreshTokenData);

        setAccessTokenCookie(response, newAccessToken);
        setRefreshTokenCookie(response, newRefreshToken);

        log.info("Tokens refreshed for user: {}", userPrinciple.getUsername());
    }

    /**
     * Logout - revoke token from Redis + clear cookies
     */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshTokenFromCookie(request);

        if (refreshToken != null && !refreshToken.isEmpty()) {
            redisTokenService.revokeToken(refreshToken);
        }

        // Clear cookies
        clearAuthCookies(response);

        log.info("User logged out");
    }

    /**
     * Logout all devices
     */
    @Override
    public void logoutAllDevices(String username, HttpServletResponse response) {
        redisTokenService.revokeAllUserTokens(username);
        clearAuthCookies(response);

        log.info("User logged out from all devices: {}", username);
    }

    /**
     * Validate access token
     */
    @Override
    public boolean validateAccessToken(String token) {
        return jwtTokenProvider.isTokenValid(token);
    }

    /**
     * Validate refresh token (JWT + Redis)
     */
    @Override
    public boolean validateRefreshToken(String token) {
        return jwtTokenProvider.isTokenValid(token) &&
                redisTokenService.isTokenValid(token);
    }

    /**
     * Get Authentication from token
     */
    @Override
    public Authentication getAuthenticationToken(String token) {
        return jwtTokenProvider.getAuthenticationToken(token);
    }

    // ===== Helper methods =====

    private UserPrinciple generateUserPrinciple(String refreshToken) {
        Authentication authentication = jwtTokenProvider.getAuthenticationToken(refreshToken);
        String username = authentication.getName();

        //todo DTO object not explicitly entity
        UserPrinciple userPrinciple = new UserPrinciple(new User());
        userPrinciple.getUser().setUsername(username);
        userPrinciple.getUser().setAuthorities(
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toArray(String[]::new)
        );
        return userPrinciple;
    }

    private RefreshTokenData prepareTokenDataForRedis(DataForRefreshTokenStore data) {
        String tokenFamily = data.tokenFamily() != null ? data.tokenFamily() : UUID.randomUUID().toString();

        String tokenHash = redisTokenService.hashToken(data.refreshToken());

        return RefreshTokenData.builder()
                .tokenHash(tokenHash)
                .username(data.username())
                .tokenFamily(tokenFamily)
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000))
                .userAgent(data.request().getHeader(USER_AGENT_HEADER_NAME))
                .ipAddress(getClientIpAddress(data.request()))
                .lastUsedAt(LocalDateTime.now())
                .build();
    }

    private void saveRefreshTokenInRedis(RefreshTokenData refreshTokenData) {
        long ttlSeconds = refreshTokenExpiration / 1000;
        redisTokenService.saveRefreshToken(refreshTokenData, ttlSeconds);
    }

    private void setAccessTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("ACCESS_TOKEN", token);
        cookie.setHttpOnly(true);
//        cookie.setSecure(true); // HTTPS only production
        cookie.setPath("/");
        cookie.setMaxAge((int) (accessTokenExpiration / 1000)); // 15 minut
        cookie.setAttribute("SameSite", "Strict"); // CSRF protection
        response.addCookie(cookie);
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("REFRESH_TOKEN", token);
        cookie.setHttpOnly(true);
//        cookie.setSecure(true); // HTTPS only production
        cookie.setPath("/");
        cookie.setMaxAge((int) (refreshTokenExpiration / 1000)); // 7 dni
        cookie.setAttribute("SameSite", "Strict"); // CSRF protection
        response.addCookie(cookie);
    }

    private void clearAuthCookies(HttpServletResponse response) {
        Cookie accessCookie = new Cookie("ACCESS_TOKEN", null);
        accessCookie.setPath("/");
        accessCookie.setHttpOnly(true);
        accessCookie.setMaxAge(0);

        Cookie refreshCookie = new Cookie("REFRESH_TOKEN", null);
        refreshCookie.setPath("/");
        refreshCookie.setHttpOnly(true);
        refreshCookie.setMaxAge(0);

        response.addCookie(accessCookie);
        response.addCookie(refreshCookie);
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("REFRESH_TOKEN".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}