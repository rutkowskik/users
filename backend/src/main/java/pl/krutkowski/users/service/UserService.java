package pl.krutkowski.users.service;

import org.springframework.web.multipart.MultipartFile;
import pl.krutkowski.users.entity.User;
import pl.krutkowski.users.exception.domain.*;
import pl.krutkowski.users.model.dto.UserResponseDto;

import javax.mail.MessagingException;
import java.io.IOException;
import java.util.List;

public interface UserService {

    UserResponseDto registerUser(String firstName, String lastName, String username, String email) throws UserNotFoundException, EmailExistException, UsernameExistException, MessagingException;

    List<UserResponseDto> getUsers();

    User findUserByUsername(String username);

    User findUserByEmail(String email);

    UserResponseDto addUser(String firstName, String lastName, String username, String email, String role, boolean isNotLocked, boolean isActive, MultipartFile file) throws UserNotFoundException, EmailExistException, UsernameExistException, IOException, MessagingException, NotAnImageFileException;

    UserResponseDto updateUser(String currentUsername, String newFirstName, String newLastName, String newUsername, String newEmail, String role, boolean isNotLocked, boolean isActive, MultipartFile file) throws UserNotFoundException, EmailExistException, UsernameExistException, IOException, NotAnImageFileException;

    void deleteUser(String username);

    void resetPassword(String email) throws EmailNotFoundException, MessagingException;

    UserResponseDto updateProfileImage(String username, MultipartFile profileImage) throws UserNotFoundException, EmailExistException, UsernameExistException, IOException, NotAnImageFileException;
}
