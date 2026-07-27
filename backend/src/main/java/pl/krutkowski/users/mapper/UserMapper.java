package pl.krutkowski.users.mapper;

import org.springframework.stereotype.Component;
import pl.krutkowski.users.entity.User;
import pl.krutkowski.users.model.dto.UserResponseDto;

@Component
public class UserMapper {

    public UserResponseDto toUserDto(User user) {
        return UserResponseDto.builder()
                .userId(user.getUserId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .username(user.getUsername())
                .email(user.getEmail())
                .profileImageUrl(user.getProfileImageUrl())
                .lastLoginDate(user.getLastLoginDate())
                .joinDate(user.getJoinDate())
                .role(user.getRole())
                .authorities(user.getAuthorities())
                .isActive(user.isActive())
                .isNotLocked(user.isNotLocked())
                .build();
    }
}
