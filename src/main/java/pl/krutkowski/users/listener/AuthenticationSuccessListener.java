package pl.krutkowski.users.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import pl.krutkowski.users.domain.UserPrinciple;
import pl.krutkowski.users.service.LoginAttemptService;

@Component
@RequiredArgsConstructor
public class AuthenticationSuccessListener {

    private final LoginAttemptService loginAttemptService;

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        if(principal instanceof UserPrinciple user) {
            loginAttemptService.evictUserFromCache(user.getUsername());
        }
    }
}
