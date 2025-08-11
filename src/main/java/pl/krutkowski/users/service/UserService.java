package pl.krutkowski.users.service;

import org.springframework.web.multipart.MultipartFile;
import pl.krutkowski.users.domain.User;
import pl.krutkowski.users.exception.domain.*;

import javax.mail.MessagingException;
import java.io.IOException;
import java.util.List;

public interface UserService {

    User registerUser(String firstName, String lastName, String username, String email) throws UserNotFoundException, EmailExistException, UsernameExistException, MessagingException;

    List<User> getUsers();

    User findUserUsername(String username);

    User findUserByEmail(String email);

    User addUser(String firstName, String lastName, String username, String email, String role, boolean isNotLocked, boolean isActive, MultipartFile file) throws UserNotFoundException, EmailExistException, UsernameExistException, IOException, MessagingException, NotAnImageFileException;

    User updateUser(String currentUsername, String newFirstName, String newLastName, String newUsername, String newEmail, String role, boolean isNotLocked, boolean isActive, MultipartFile file) throws UserNotFoundException, EmailExistException, UsernameExistException, IOException, NotAnImageFileException;

    void deleteUser(String username);

    void resetPassword(String email) throws EmailNotFoundException, MessagingException;

    User updateProfileImage(String username, MultipartFile profileImage) throws UserNotFoundException, EmailExistException, UsernameExistException, IOException, NotAnImageFileException;
}
