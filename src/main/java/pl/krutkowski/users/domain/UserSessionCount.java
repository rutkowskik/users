package pl.krutkowski.users.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserSessionCount {
    private String username;
    private long sessionCount;
}