package dev.toniess.splitly.user;

import dev.toniess.splitly.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    @Query(value = "select u from User u where lower(u.email) = :email")
    Optional<User> findByEmail(String email);
}
