package pl.krutkowski.users.token;

import jakarta.servlet.http.HttpServletRequest;

public record DataForRefreshTokenStore(String refreshToken, HttpServletRequest request, String username, String tokenFamily) {
}
