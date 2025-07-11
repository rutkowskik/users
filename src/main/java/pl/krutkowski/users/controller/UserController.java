package pl.krutkowski.users.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.krutkowski.users.domain.User;
import pl.krutkowski.users.exception.ExceptionHandling;
import pl.krutkowski.users.exception.domain.EmailExistException;
import pl.krutkowski.users.exception.domain.UserNotFoundException;
import pl.krutkowski.users.exception.domain.UsernameExistException;
import pl.krutkowski.users.service.UserService;

import static org.springframework.http.HttpStatus.OK;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/user")
public class UserController extends ExceptionHandling {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody User user ) throws UserNotFoundException, EmailExistException, UsernameExistException {
        User registerUser = userService.registerUser(user.getFirstName(), user.getLastName(), user.getUsername(), user.getEmail());
        return new ResponseEntity<>(registerUser,OK);
    }
}
