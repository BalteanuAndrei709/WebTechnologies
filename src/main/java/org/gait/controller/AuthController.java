package org.gait.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gait.database.entity.Role;
import org.gait.database.entity.UserEntity;
import org.gait.database.repository.RoleRepository;
import org.gait.database.repository.UserRepository;
import org.gait.dto.LoginRequest;
import org.gait.dto.RegisterRequest;
import org.gait.dto.RoleName;
import org.gait.security.JwtUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Existing login endpoint (for reference).
     * Client sends email & password, we authenticate and return a JWT.
     */
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest loginRequest) {
        // Attempt authentication
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
        );
        // If success, generate token
        String token = jwtUtils.generateToken(loginRequest.getEmail());
        log.info("User {} logged in. JWT generated", loginRequest.getEmail());
        return ResponseEntity.ok(token);
    }

    /**
     * New endpoint to register a user with the CLIENT role.
     * Example usage: POST /api/auth/register/client
     *   {
     *     "email": "john@example.com",
     *     "password": "secret123",
     *     "fullname": "John Doe",
     *     "age": 30
     *   }
     */
    @PostMapping("/register/client")
    public ResponseEntity<String> registerClient(@RequestBody RegisterRequest request) {
        // 1) Check if user (by email) already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Error: Email is already in use!");
        }

        // 2) Find the 'CLIENT' role from the DB (or create it if it doesn't exist)
        Optional<Role> clientRoleOpt = roleRepository.findByRole(RoleName.CLIENT);
        if (clientRoleOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Error: CLIENT role not found in the database!");
        }
        Role clientRole = clientRoleOpt.get();

        // 3) Build a new UserEntity
        UserEntity newUser = UserEntity.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword())) // Hash password
                .fullname(request.getFullname())
                .age(request.getAge())
                .role(clientRole)
                .build();

        // 4) Save to DB
        userRepository.save(newUser);

        // 5) Optionally return a success message or JWT
        // For example, let's just return a success string
        return ResponseEntity.ok("New CLIENT user registered successfully!");
    }
}
