package pl.krutkowski.users.token;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RefreshResponse {
    private String accessToken;
    private String refreshToken;
}