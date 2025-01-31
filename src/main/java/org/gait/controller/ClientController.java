package org.gait.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gait.database.entity.UserEntity;
import org.gait.database.repository.UserRepository;
import org.gait.database.service.EndpointCallService;
import org.gait.dto.ClientRequest;
import org.gait.security.UserDetailsImpl;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/client")
@RequiredArgsConstructor
@Slf4j
public class ClientController {

    private final UserRepository userRepository;
    private final EndpointCallService endpointCallService;

    // 1) Example POST endpoint at /client/use-api
    @PostMapping("/use-api")
    public String useOpenApi(@RequestBody ClientRequest request, Authentication authentication) {

        // 2) The 'Authentication' object has principal = our user details
        UserDetailsImpl principal = (UserDetailsImpl) authentication.getPrincipal();

        // 3) Find the user in DB (assuming the principal's email is the "username")
        UserEntity user = userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 4) For demonstration, let's log it
        log.info("CLIENT user={} is calling api={}, with prompt='{}'",
                user.getEmail(), request.getApi(), request.getPrompt());

        // 5) (Optional) Count or store the call
        endpointCallService.incrementCallCount(user, request.getApi());

        // 6) Return some response
        return "Received request for API " + request.getApi() +
                " with prompt: " + request.getPrompt();
    }
}
