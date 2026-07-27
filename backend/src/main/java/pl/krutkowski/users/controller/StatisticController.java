package pl.krutkowski.users.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.krutkowski.users.model.TokenStats;
import pl.krutkowski.users.model.UserSessionCount;
import pl.krutkowski.users.service.TokenMetricsService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class StatisticController {

    private final TokenMetricsService tokenMetricsService;

    @GetMapping("/admin/token-stats")
    @PreAuthorize("hasAuthority('app:monitoring')")
    public ResponseEntity<TokenStats> getTokenStats() {
        TokenStats stats = tokenMetricsService.getTokenStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/admin/top-active-users")
    @PreAuthorize("hasAuthority('app:monitoring')")
    public ResponseEntity<List<UserSessionCount>> getTopActiveUsers(
            @RequestParam(defaultValue = "10") int limit) {
        List<UserSessionCount> topUsers = tokenMetricsService.getTopActiveUsers(limit);
        return ResponseEntity.ok(topUsers);
    }
}
