package pl.krutkowski.users.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.krutkowski.users.domain.User;
import pl.krutkowski.users.domain.UserPrinciple;
import pl.krutkowski.users.exception.ExceptionHandling;
import pl.krutkowski.users.exception.domain.EmailExistException;
import pl.krutkowski.users.exception.domain.UserNotFoundException;
import pl.krutkowski.users.exception.domain.UsernameExistException;
import pl.krutkowski.users.service.UserService;
import pl.krutkowski.users.utility.JTWTokenProvider;

import static org.springframework.http.HttpStatus.OK;
import static pl.krutkowski.users.constant.SecurityConstant.JWT_TOKEN_HEADER;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/user")
public class UserController extends ExceptionHandling {

    private final UserService userService;
    private final JTWTokenProvider jtwTokenProvider;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/login")
    public ResponseEntity<User> loginUser(@RequestBody User user ) {
        authenticateUser(user.getUsername(), user.getPassword());
        User loginUser = userService.findUserUsername(user.getUsername());
        UserPrinciple userPrinciple = new UserPrinciple(loginUser);
        HttpHeaders headers = getJtwHeaders(userPrinciple);
        return new ResponseEntity<>(loginUser, headers,OK);
    }

    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody User user ) throws UserNotFoundException, EmailExistException, UsernameExistException {
        User registerUser = userService.registerUser(user.getFirstName(), user.getLastName(), user.getUsername(), user.getEmail());
        return new ResponseEntity<>(registerUser,OK);
    }

    private HttpHeaders getJtwHeaders(UserPrinciple user) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(JWT_TOKEN_HEADER, jtwTokenProvider.generateToken(user));
        return headers;
    }

    private void authenticateUser(String username, String password) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
    }
}
