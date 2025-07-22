package pl.krutkowski.users.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pl.krutkowski.users.domain.User;
import pl.krutkowski.users.domain.UserPrinciple;
import pl.krutkowski.users.enumeration.Role;
import pl.krutkowski.users.exception.domain.EmailExistException;
import pl.krutkowski.users.exception.domain.EmailNotFoundException;
import pl.krutkowski.users.exception.domain.UserNotFoundException;
import pl.krutkowski.users.exception.domain.UsernameExistException;
import pl.krutkowski.users.repository.UserRepository;
import pl.krutkowski.users.service.EmailService;
import pl.krutkowski.users.service.LoginAttemptService;
import pl.krutkowski.users.service.UserService;

import javax.mail.MessagingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static pl.krutkowski.users.constant.FileConstant.*;
import static pl.krutkowski.users.constant.UserConstant.*;
import static pl.krutkowski.users.enumeration.Role.ROLE_USER;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional
@Qualifier("UserDetailService")
public class UserServiceImpl implements UserService, UserDetailsService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final EmailService emailService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findUserByUsername(username);
        if (user == null) {
            String msg = String.format(USER_NOT_FOUND_BY_USERNAME, username);
            log.error(msg);
            throw new UsernameNotFoundException(msg);
        }
        validateLoginAttempt(user);
        user.setLastLoginDateDisplay(user.getLastLoginDate());
        user.setLastLoginDate(new Date());
        userRepository.save(user);
        UserPrinciple userPrinciple = new UserPrinciple(user);
        log.info("Returning found user by username: {}", username);
        return userPrinciple;
    }

    @Override
    public User registerUser(String firstName, String lastName, String username, String email) throws UserNotFoundException, EmailExistException, UsernameExistException, MessagingException {
        validateUsernameAndEmail(StringUtils.EMPTY, username, email);
        User user = new User();
        user.setUserId(generateUserId());
        String password = generatePassword();
        String passwordEncoded = encodePassword(password);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoded);
        user.setJoinDate(new Date());
        user.setActive(true);
        user.setNotLocked(true);
        user.setRole(ROLE_USER.name());
        user.setAuthorities(ROLE_USER.getAuthorities());
        user.setProfileImageUrl(getTemporaryImageUrl(username));
        userRepository.save(user);
        emailService.sendNewPasswordEmail(firstName, email, password);
        return user;
    }

    @Override
    public User addUser(String firstName, String lastName, String username, String email, String role, boolean isNotLocked, boolean isActive, MultipartFile profileImage) throws UserNotFoundException, EmailExistException, UsernameExistException, IOException, MessagingException {
        validateUsernameAndEmail(StringUtils.EMPTY, username, email);
        User user = new User();
        String password = generatePassword();
        String passwordEncoded = encodePassword(password);
        user.setUserId(generateUserId());
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setJoinDate(new Date());
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoded);
        user.setActive(isActive);
        user.setNotLocked(isNotLocked);
        user.setRole(getRoleEnumName(role).name());
        user.setAuthorities(getRoleEnumName(role).getAuthorities());
        user.setProfileImageUrl(getTemporaryImageUrl(username));
        userRepository.save(user);
        saveProfileImage(user, profileImage);
        emailService.sendNewPasswordEmail(firstName, email, password);
        return user;
    }

    @Override
    public User updateUser(String currentUsername, String newFirstName, String newLastName, String newUsername, String newEmail, String role, boolean isNotLocked, boolean isActive, MultipartFile profileImage) throws UserNotFoundException, EmailExistException, UsernameExistException, IOException {
        User currentUser = validateUsernameAndEmail(currentUsername, newUsername, newEmail);
        currentUser.setFirstName(newFirstName);
        currentUser.setLastName(newLastName);
        currentUser.setUsername(newUsername);
        currentUser.setEmail(newEmail);
        currentUser.setActive(isActive);
        currentUser.setNotLocked(isNotLocked);
        currentUser.setRole(getRoleEnumName(role).name());
        currentUser.setAuthorities(getRoleEnumName(role).getAuthorities());
        saveProfileImage(currentUser, profileImage);
        userRepository.save(currentUser);
        return currentUser;
    }

    @Override
    public void deleteUser(long id) {
        userRepository.deleteById(id);
    }

    @Override
    public void resetPassword(String email) throws EmailNotFoundException, MessagingException {
        User user = userRepository.findUserByEmail(email);
        if(user == null)
            throw new EmailNotFoundException(String.format(USER_NOT_FOUND_BY_USERNAME, email));

        String newPassword = generatePassword();
        user.setPassword(encodePassword(newPassword));
        userRepository.save(user);
        emailService.sendNewPasswordEmail(user.getFirstName(), user.getEmail(), newPassword);
    }

    @Override
    public User updateProfileImage(String username, MultipartFile profileImage) throws UserNotFoundException, EmailExistException, UsernameExistException, IOException {
        User user = validateUsernameAndEmail(username, null, null);
        saveProfileImage(user, profileImage);
        return user;
    }

    private String getTemporaryImageUrl(String username) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(DEFAULT_USER_IMAGE_PATH + username).toUriString();
    }

    private String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }

    private String generatePassword() {
            return RandomStringUtils.randomAlphanumeric(10);
    }

    private String generateUserId() {
        return RandomStringUtils.randomNumeric(10);
    }

    @Override
    public List<User> getUsers() {
        return userRepository.findAll();
    }

    @Override
    public User findUserUsername(String username) {
        return userRepository.findUserByUsername(username);
    }

    @Override
    public User findUserByEmail(String email) {
        return userRepository.findUserByEmail(email);
    }

    private User validateUsernameAndEmail(String currentUsername, String newUsername, String newEmail) throws UsernameExistException, UserNotFoundException, EmailExistException {
        String msg;
        User userNewByUsername = findUserUsername(newUsername);
        User userNewByEmail = findUserByEmail(newEmail);

        if(!StringUtils.isBlank(currentUsername)) {
            User currentUser = findUserUsername(currentUsername);
            if(currentUser == null){
                msg = String.format(USER_NOT_FOUND_BY_USERNAME, currentUsername);
                log.error(msg);
                throw new UserNotFoundException(msg);
            }
            if(userNewByUsername != null && !currentUser.getId().equals(userNewByUsername.getId())) {
                msg = String.format(USERNAME_ALREADY_TAKEN, newUsername);
                log.error(msg);
                throw new UsernameExistException(msg);
            }
            if(userNewByEmail != null && !currentUser.getId().equals(userNewByEmail.getId())){
                msg = String.format(EMAIL_ALREADY_TAKEN, newEmail);
                log.error(msg);
                throw new EmailExistException(String.format(EMAIL_ALREADY_TAKEN, newEmail));
            }
            return currentUser;
        } else {
            if(userNewByUsername != null)   {
                 msg = String.format(USERNAME_ALREADY_TAKEN, newUsername);
                 log.error(msg);
                 throw new UsernameExistException(msg);
            }
            if(userNewByEmail != null) {
                msg = String.format(EMAIL_ALREADY_TAKEN, newEmail);
                log.error(msg);
                throw new EmailExistException(msg);
            }
            return null;
        }
    }

    private void validateLoginAttempt(User user) {
        if(user.isNotLocked()){
            user.setNotLocked(!loginAttemptService.hasExceededMaxAttempt(user.getUsername()));
        } else {
            loginAttemptService.evictUserFromCache(user.getUsername());
        }
    }

    private void saveProfileImage(User user, MultipartFile profileImage) throws IOException {
        if(profileImage != null) {
            Path userFolder = Paths.get(USER_FOLDER + user.getUsername()).toAbsolutePath().normalize();
            if(!Files.exists(userFolder)) {
                Files.createDirectories(userFolder);
                log.info(DIRECTORY_CREATED);
            }
            Files.deleteIfExists(Paths.get(userFolder + user.getUserId() + DOT + JPG_EXTENSION));
            Files.copy(profileImage.getInputStream(), userFolder.resolve(user.getUsername() + DOT + JPG_EXTENSION), REPLACE_EXISTING);
            user.setProfileImageUrl(setProfileImageUrl(user.getUsername()));
            userRepository.save(user);
            log.info(FILE_SAVED_IN_FILE_SYSTEM + profileImage.getOriginalFilename());
        }
    }

    private String setProfileImageUrl(String username) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(USER_IMAGE_PATH + username + FORWARD_SLASH + username + DOT + JPG_EXTENSION).toUriString();
    }

    private Role getRoleEnumName(String role) {
        return Role.valueOf(role.toUpperCase());
    }
}
