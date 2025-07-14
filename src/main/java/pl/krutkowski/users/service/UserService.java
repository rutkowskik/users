package pl.krutkowski.users.service;

import pl.krutkowski.users.domain.User;
import pl.krutkowski.users.exception.domain.EmailExistException;
import pl.krutkowski.users.exception.domain.UserNotFoundException;
import pl.krutkowski.users.exception.domain.UsernameExistException;

import javax.mail.MessagingException;
import java.util.List;

public interface UserService {

    User registerUser(String firstName, String lastName, String username, String email) throws UserNotFoundException, EmailExistException, UsernameExistException, MessagingException;

    List<User> getUsers();

    User findUserUsername(String username);

    User findUserByEmail(String email);
}
