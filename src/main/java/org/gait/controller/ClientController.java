package org.gait.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gait.database.entity.UserEntity;
import org.gait.database.repository.UserRepository;
import org.gait.database.service.EndpointCallService;
import org.gait.database.service.UserService;
import org.gait.dto.ClientRequest;
import org.gait.security.UserDetailsImpl;
import org.gait.service.ClientService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/client")
@RequiredArgsConstructor
@Slf4j
public class ClientController {

    private final EndpointCallService endpointCallService;
    private final ClientService clientService;
    private final UserService  userService;

    // 1) Example POST endpoint at /client/use-api
    @PostMapping("/use-api")
    public String createPrompt(@RequestBody ClientRequest request, Authentication authentication) {

        UserEntity user = userService.getUserEntity(authentication);

        // 4) For demonstration, let's log it
        log.info("CLIENT user={} is calling api={}, with prompt='{}'",
                user.getEmail(), request.getApi(), request.getPrompt());

        clientService.createPrompt(request);

        // 5) (Optional) Count or store the call
        endpointCallService.incrementCallCount(user, request.getApi());

        // 6) Return some response
        return "Received request for API " + request.getApi() +
                " with prompt: " + request.getPrompt();
    }
}
