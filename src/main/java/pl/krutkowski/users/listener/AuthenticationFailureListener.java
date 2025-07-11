package pl.krutkowski.users.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.stereotype.Component;
import pl.krutkowski.users.service.LoginAttemptService;

@Component
@RequiredArgsConstructor
public class AuthenticationFailureListener {

    private final LoginAttemptService loginAttemptService;

    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        if(principal instanceof String username){
            loginAttemptService.addUserToCache(username);
        }
    }

}
