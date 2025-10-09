package pl.krutkowski.users.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import pl.krutkowski.users.exception.domain.InvalidTokenException;
import pl.krutkowski.users.exception.domain.TokenReusedException;
import pl.krutkowski.users.model.UserPrinciple;

public interface TokenService {

    void login(UserPrinciple userPrinciple, HttpServletResponse response, HttpServletRequest request);

    void refresh(HttpServletRequest request, HttpServletResponse response) throws InvalidTokenException, TokenReusedException;

    void logout(HttpServletRequest request, HttpServletResponse response);

    void logoutAllDevices(String username, HttpServletResponse response);

    boolean validateAccessToken(String token);

    boolean validateRefreshToken(String token);

    Authentication getAuthenticationToken(String token);


}
