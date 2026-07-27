package pl.krutkowski.users.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.krutkowski.users.entity.RefreshTokenData;
import pl.krutkowski.users.entity.User;
import pl.krutkowski.users.exception.ExceptionHandling;
import pl.krutkowski.users.exception.domain.*;
import pl.krutkowski.users.model.HttpResponse;
import pl.krutkowski.users.model.SessionInfo;
import pl.krutkowski.users.model.UserPrinciple;
import pl.krutkowski.users.model.dto.UserRequestDto;
import pl.krutkowski.users.model.dto.UserResponseDto;
import pl.krutkowski.users.mapper.UserMapper;
import pl.krutkowski.users.service.RedisTokenService;
import pl.krutkowski.users.service.TokenService;
import pl.krutkowski.users.service.UserService;

import javax.mail.MessagingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.IMAGE_JPEG_VALUE;
import static pl.krutkowski.users.constant.FileConstant.*;
import static pl.krutkowski.users.constant.UserConstant.USER_NOT_FOUND_BY_USERNAME;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/user")
public class UserController extends ExceptionHandling {

    public static final String EMAIL_SENT_TO_MESSAGE = "Email sent to: ";
    public static final String EMAIL_SENT_WITH_NEW_PASSWORD = "Email with new password sent to: ";
    public static final String USER_DELETED_SUCCESSFULLY = "User deleted successfully";
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final RedisTokenService redisTokenService;
    private final UserMapper userMapper;

    @PostMapping("/login")
    public ResponseEntity<UserResponseDto> loginUser(@RequestBody UserRequestDto user,
                                                     HttpServletRequest request,
                                                     HttpServletResponse response) {
        authenticateUser(user.getUsername(), user.getPassword());
        User loginUser = userService.findUserByUsername(user.getUsername());
        UserPrinciple userPrinciple = new UserPrinciple(loginUser);

        tokenService.login(userPrinciple, response, request);

        return new ResponseEntity<>(userMapper.toUserDto(loginUser), OK);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        try {
            tokenService.refresh(request, response);
            return ResponseEntity.ok().build();
        } catch (InvalidTokenException | TokenReusedException e) {
            log.error("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        tokenService.logout(request, response);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<HttpResponse> logoutAllDevices(HttpServletRequest request, HttpServletResponse response) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        tokenService.logoutAllDevices(username, response);
        return response(OK, "Logged out from all devices");
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String username = authentication.getName();
        User user = userService.findUserByUsername(username);
        UserResponseDto userResponseDto = userMapper.toUserDto(user);

        return ResponseEntity.ok(userResponseDto);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionInfo>> getActiveSessions(HttpServletRequest request) {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        List<RefreshTokenData> tokens = redisTokenService.getUserActiveSessions(username);

        String currentTokenHash = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("REFRESH_TOKEN".equals(cookie.getName())) {
                    currentTokenHash = redisTokenService.hashToken(cookie.getValue());
                    break;
                }
            }
        }

        final String currentHash = currentTokenHash;
        List<SessionInfo> sessions = tokens.stream()
                .map(token -> new SessionInfo(
                        token.getTokenHash().substring(0, 8),
                        token.getIssuedAt(),
                        token.getLastUsedAt(),
                        token.getExpiresAt(),
                        token.getUserAgent(),
                        token.getIpAddress(),
                        token.getTokenHash().equals(currentHash)
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable String sessionId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        List<RefreshTokenData> tokens = redisTokenService.getUserActiveSessions(username);

        Optional<RefreshTokenData> tokenToRevoke = tokens.stream()
                .filter(t -> t.getTokenHash().startsWith(sessionId))
                .findFirst();

        if (tokenToRevoke.isPresent()) {
            redisTokenService.revokeTokenByHash(tokenToRevoke.get().getTokenHash());
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.notFound().build();
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> registerUser(@RequestBody UserRequestDto user) throws UserNotFoundException, EmailExistException, UsernameExistException, MessagingException {
        UserResponseDto registerUser = userService.registerUser(user.getFirstName(), user.getLastName(), user.getUsername(), user.getEmail());
        return new ResponseEntity<>(registerUser,OK);
    }

    @PostMapping("/add")
    public ResponseEntity<UserResponseDto> addUser(@RequestParam("firstName") String firstName,
                                        @RequestParam("lastName") String lastName,
                                        @RequestParam("username") String username,
                                        @RequestParam("email") String email,
                                        @RequestParam("role") String role,
                                        @RequestParam("isActive") String isActive,
                                        @RequestParam("isNonLocked") String isNonLocked,
                                        @RequestParam(value = "profileImage", required = false) MultipartFile profileImage)
            throws UserNotFoundException, EmailExistException, UsernameExistException, IOException, MessagingException, NotAnImageFileException {
        UserResponseDto newUser = userService.addUser(firstName, lastName, username, email, role, Boolean.parseBoolean(isNonLocked), Boolean.parseBoolean(isActive), profileImage);
        return new ResponseEntity<>(newUser, OK);
    }

    @PostMapping("/update")
    public ResponseEntity<UserResponseDto> addUser(@RequestParam("currentUsername") String currentUsername,
                                        @RequestParam("firstName") String firstName,
                                        @RequestParam("lastName") String lastName,
                                        @RequestParam("username") String username,
                                        @RequestParam("email") String email,
                                        @RequestParam("role") String role,
                                        @RequestParam("isActive") String isActive,
                                        @RequestParam("isNonLocked") String isNonLocked,
                                        @RequestParam(value = "profileImage", required = false) MultipartFile profileImage)
            throws UserNotFoundException, EmailExistException, UsernameExistException, IOException, NotAnImageFileException {
        UserResponseDto currentUser = userService.updateUser(currentUsername, firstName, lastName, username, email, role, Boolean.parseBoolean(isNonLocked), Boolean.parseBoolean(isActive), profileImage);
        return new ResponseEntity<>(currentUser, OK);
    }

    @GetMapping("find/{username}")
    public ResponseEntity<UserResponseDto> getUserByUsername(@PathVariable("username") String username) throws UserNotFoundException {
        User foundUser = userService.findUserByUsername(username);
        if (foundUser == null) {
            String msg = String.format(USER_NOT_FOUND_BY_USERNAME, username);
            log.error(msg);
            throw new UserNotFoundException(msg);
        }
        UserResponseDto userDto = userMapper.toUserDto(foundUser);
        return new ResponseEntity<>(userDto, OK);
    }

    @GetMapping("list")
    public ResponseEntity<List<UserResponseDto>> getAllUsers() {
        List<UserResponseDto> users = userService.getUsers();
        return new ResponseEntity<>(users, OK);
    }

    @GetMapping("resertpassword/{email}")
    public ResponseEntity<HttpResponse> resetPassword(@PathVariable("email") String email) throws EmailNotFoundException, MessagingException {
        userService.resetPassword(email);
        return response(OK, EMAIL_SENT_WITH_NEW_PASSWORD + email);
    }

    @DeleteMapping("delete/{username}")
    @PreAuthorize("hasAuthority('user:delete')")
    public ResponseEntity<HttpResponse> deleteUser(@PathVariable("username") String username) {
        userService.deleteUser(username);
        return response(NO_CONTENT, USER_DELETED_SUCCESSFULLY);
    }

    @PostMapping("/updateProfileImage")
    public ResponseEntity<UserResponseDto> updateProfileImage(@RequestParam("username") String username,
                                                   @RequestParam("image") MultipartFile image)
            throws UserNotFoundException, EmailExistException, UsernameExistException, IOException, NotAnImageFileException {
        UserResponseDto user = userService.updateProfileImage(username, image);
        return new ResponseEntity<>(user, OK);
    }

    @GetMapping(value = "/image/{username}/{filename}", produces = IMAGE_JPEG_VALUE)
    public byte[] getProfileImage(
            @PathVariable("username") String username,
            @PathVariable("filename") String filename) throws IOException {
        return Files.readAllBytes(Paths.get(USER_FOLDER + username + FORWARD_SLASH + filename));
    }

    @GetMapping(value = "/image/profile/{username}", produces = IMAGE_JPEG_VALUE)
    public byte[] getTempProfileImage(
            @PathVariable("username") String username) throws IOException {
        URL url = new URL(TEMP_PROFILE_IMAGE_BASE_URL + username);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (InputStream inputStream = url.openStream()) {
            int bytesRead;
            byte [] chunk = new byte[1024];
            while ((bytesRead = inputStream.read(chunk)) > 0) {
                byteArrayOutputStream.write(chunk, 0, bytesRead);
            }
        }
        return byteArrayOutputStream.toByteArray();
    }

    private ResponseEntity<HttpResponse> response(HttpStatus httpStatus, String message) {
        return new ResponseEntity<>(
                new HttpResponse(httpStatus.value(), httpStatus, httpStatus.getReasonPhrase().toUpperCase(),
                        message.toUpperCase()), httpStatus);
    }

    public void authenticateUser(String username, String password) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
    }

}
