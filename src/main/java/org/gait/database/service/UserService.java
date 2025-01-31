package org.gait.database.service;

import lombok.RequiredArgsConstructor;
import org.gait.database.entity.UserEntity;
import org.gait.database.repository.UserRepository;
import org.gait.security.UserDetailsImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserEntity getUserEntity(Authentication authentication) {
        // 2) The 'Authentication' object has principal = our user details
        UserDetailsImpl principal = (UserDetailsImpl) authentication.getPrincipal();

        // 3) Find the user in DB (assuming the principal's email is the "username")
        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
