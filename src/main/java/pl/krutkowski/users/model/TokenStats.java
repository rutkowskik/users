package pl.krutkowski.users.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenStats {
    private int totalActiveTokens;
    private int totalActiveUsers;
    private int totalTokenFamilies;
    private int totalRevokedTokens;
}
