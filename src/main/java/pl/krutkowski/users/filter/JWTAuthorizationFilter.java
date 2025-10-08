package pl.krutkowski.users.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.krutkowski.users.constant.SecurityConstant;
import pl.krutkowski.users.token.TokenService;

import java.io.IOException;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpStatus.OK;
import static pl.krutkowski.users.constant.SecurityConstant.OPTIONS_HTTP_METHOD;

@Slf4j
@Component
@RequiredArgsConstructor
public class JWTAuthorizationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip for public endpoints
        if (request.getMethod().equalsIgnoreCase(OPTIONS_HTTP_METHOD) ||
                request.getRequestURI().contains("/login") ||
                request.getRequestURI().contains("/register") ||
                request.getRequestURI().contains("/refresh")) {
            filterChain.doFilter(request, response);
            return;
        }
        String accessToken = extractAccessTokenFromCookie(request);

        if (accessToken == null || !tokenService.validateAccessToken(accessToken)) {
            log.debug("No valid access token found for: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        try {
            Authentication authentication = tokenService.getAuthenticationToken(accessToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("User authenticated: {}", authentication.getName());
        } catch (Exception e) {
            log.error("Failed to set authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extractAccessTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("ACCESS_TOKEN".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}