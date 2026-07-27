package pl.krutkowski.users.model;

import jakarta.servlet.http.HttpServletRequest;

public record DataForRefreshTokenStore(String refreshToken,
                                       HttpServletRequest request,
                                       String username,
                                       String tokenFamily) {
}
