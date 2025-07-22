package pl.krutkowski.users.utility;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import pl.krutkowski.users.domain.UserPrinciple;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;
import static pl.krutkowski.users.constant.SecurityConstant.*;

@Component
public class JTWTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    public String generateToken(UserPrinciple userPrinciple) {
        String [] claims = getClaimsForUser(userPrinciple);
        return JWT.create().withIssuer(K_RUTKOWSKI).withAudience(CAR_APP)
                .withIssuedAt(new Date()).withSubject(userPrinciple.getUsername())
                .withArrayClaim(AUTHORITIES, claims).withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .sign(Algorithm.HMAC512(secret.getBytes()));

    }

    public List<GrantedAuthority> getAuthorities(String token) {
        String [] claims = getClaimsFromToken(token);
        return stream(claims).map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }

    public Authentication getAuthentication(String userName, List<GrantedAuthority> authorities, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken userPasswordAuthToken =
                new UsernamePasswordAuthenticationToken(userName, null, authorities);
        userPasswordAuthToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return userPasswordAuthToken;
    }

    public boolean isTokenValid(String userName, String token) {
        JWTVerifier verifier = getJWTVerier();
        return StringUtils.isNotEmpty(userName) && !isTokenExpired(verifier, token);
    }

    private boolean isTokenExpired(JWTVerifier verifier, String token) {
        Date expiresAt = verifier.verify(token).getExpiresAt();
        return expiresAt.before(new Date());
    }

    public String getSubject(String token) {
        JWTVerifier verifier = getJWTVerier();
        return verifier.verify(token).getSubject();
    }

    private String[] getClaimsForUser(UserPrinciple userPrinciple) {
        List<String> authorities = new ArrayList<>();
        for (GrantedAuthority authority : userPrinciple.getAuthorities()) {
            authorities.add(authority.getAuthority());
        }
        return authorities.toArray(new String[0]);
    }

    private String[] getClaimsFromToken(String token) {
        JWTVerifier verifier = getJWTVerier();
        return verifier.verify(token).getClaim(AUTHORITIES).asArray(String.class);
    }

    private JWTVerifier getJWTVerier() {
        JWTVerifier verifier;
        try {
            Algorithm algorithm = Algorithm.HMAC512(secret.getBytes());
            verifier = JWT.require(algorithm).withIssuer(K_RUTKOWSKI).build();
        } catch (JWTVerificationException e) {
            throw new JWTVerificationException(TOKEN_CANNOT_BE_VERIFIED);
        }
        return verifier;
    }
}
