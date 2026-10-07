package dev.toniess.splitly.user;

import dev.toniess.splitly.user.dto.UserResponse;
import dev.toniess.splitly.user.entity.User;
import dev.toniess.splitly.user.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse getUser(UUID userId) {
        final User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        return UserResponse.fromUser(user);
    }
}
