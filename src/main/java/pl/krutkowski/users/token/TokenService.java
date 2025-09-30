package pl.krutkowski.users.token;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import pl.krutkowski.users.domain.UserPrinciple;
import pl.krutkowski.users.utility.JTWTokenProvider;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JTWTokenProvider jtwTokenProvider;

    public void login(UserPrinciple userPrinciple, HttpServletResponse response) {
        String accessToken = jtwTokenProvider.generateToken(userPrinciple, 1000 * 60 * 150);
        String refreshToken = jtwTokenProvider.generateToken(userPrinciple, 1000 * 60 * 60 * 240);

        addCookie(response, "ACCESS_TOKEN", accessToken, 600);
        addCookie(response, "REFRESH_TOKEN", refreshToken, 60 * 24 * 30);

    }

    private void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
//        cookie.setSecure(true);  // HTTPS only
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
    }

    public boolean validate(String token) {
        return jtwTokenProvider.isTokenValid(token);
    }

    public Authentication getAuthenticationToken(String token) {
        String username = jtwTokenProvider.getSubject(token);
        List<GrantedAuthority> authorities = jtwTokenProvider.getAuthorities(token);
        return new UsernamePasswordAuthenticationToken(username, null, authorities);
    }
}
