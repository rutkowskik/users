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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pl.krutkowski.users.domain.User;
import pl.krutkowski.users.domain.UserPrinciple;
import pl.krutkowski.users.exception.domain.EmailExistException;
import pl.krutkowski.users.exception.domain.UserNotFoundException;
import pl.krutkowski.users.exception.domain.UsernameExistException;
import pl.krutkowski.users.repository.UserRepository;
import pl.krutkowski.users.service.UserService;

import java.util.Date;
import java.util.List;

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

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findUserByUsername(username);
        if (user == null) {
            String msg = String.format(USER_NOT_FOUND_BY_USERNAME, username);
            log.error(msg);
            throw new UsernameNotFoundException(msg);
        }
        user.setLastLoginDateDisplay(user.getLastLoginDate());
        user.setLastLoginDate(new Date());
        userRepository.save(user);
        UserPrinciple userPrinciple = new UserPrinciple(user);
        log.info("Returning found user by username: {}", username);
        return userPrinciple;
    }

    @Override
    public User registerUser(String firstName, String lastName, String username, String email) throws UserNotFoundException, EmailExistException, UsernameExistException {
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
        user.setProfileImageUrl(getTemporaryImageUrl());
        userRepository.save(user);
        log.info("New user password: {}", password);
        return user;
    }

    private String getTemporaryImageUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(DEFAULT_IMAGE_PATH).toUriString();
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

    public void validateUsernameAndEmail(String currentUsername, String newUsername, String newEmail) throws UsernameExistException, UserNotFoundException, EmailExistException {
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
        }
    }
}
