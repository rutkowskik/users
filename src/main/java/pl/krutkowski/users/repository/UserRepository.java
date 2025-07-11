package pl.krutkowski.users.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.krutkowski.users.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {
    User findUserByUsername(String username);
    User findUserByEmail(String email);
}
