package pl.krutkowski.users.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.krutkowski.users.token.TokenService;

import java.io.IOException;

import static pl.krutkowski.users.constant.SecurityConstant.OPTIONS_HTTP_METHOD;

@RequiredArgsConstructor
@Component
public class JWTAuthorizationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if(request.getMethod().equalsIgnoreCase(OPTIONS_HTTP_METHOD))
            response.setStatus(HttpServletResponse.SC_OK);
        else {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("ACCESS_TOKEN".equals(cookie.getName())) {
                        String token = cookie.getValue();
                        if (tokenService.validate(token)) {
                            Authentication auth = tokenService.getAuthenticationToken(token);
                            if (auth != null)
                                SecurityContextHolder.getContext().setAuthentication(auth);
                        }
                    }
                }
            }
            else
                SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);

    }
}
