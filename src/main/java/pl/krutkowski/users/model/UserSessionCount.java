package pl.krutkowski.users.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserSessionCount {
    private String username;
    private long sessionCount;
}