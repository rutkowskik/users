package pl.krutkowski.users.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.krutkowski.users.utility.JTWTokenProvider;

import java.io.IOException;
import java.util.List;

import static pl.krutkowski.users.constant.SecurityConstant.OPTIONS_HTTP_METHOD;
import static pl.krutkowski.users.constant.SecurityConstant.TOKEN_PREFIX;

@RequiredArgsConstructor
@Component
public class JWTAuthorizationFilter extends OncePerRequestFilter {

    private final JTWTokenProvider jtwTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if(request.getMethod().equalsIgnoreCase(OPTIONS_HTTP_METHOD))
            response.setStatus(HttpServletResponse.SC_OK);
        else {
            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            if(authHeader == null || authHeader.startsWith(TOKEN_PREFIX)) {
                filterChain.doFilter(request, response);
                return;
            }
            String token = authHeader.substring(TOKEN_PREFIX.length());
            String username = jtwTokenProvider.getSubject(token);
            if(jtwTokenProvider.isTokenValid(token, username) && SecurityContextHolder.getContext().getAuthentication() == null) {
                List<GrantedAuthority> authorities = jtwTokenProvider.getAuthorities(token);
                Authentication authentication = jtwTokenProvider.getAuthentication(username, authorities, request);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else
                SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);

    }
}
