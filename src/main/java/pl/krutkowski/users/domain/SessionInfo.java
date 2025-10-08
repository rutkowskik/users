package pl.krutkowski.users.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class SessionInfo {
    private String sessionId;
    private LocalDateTime issuedAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime expiresAt;
    private String userAgent;
    private String ipAddress;
    private boolean current; // czy to aktualna sesja
}