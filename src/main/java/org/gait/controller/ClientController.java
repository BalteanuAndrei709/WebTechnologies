package org.gait.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gait.database.entity.UserEntity;
import org.gait.database.service.EndpointCallService;
import org.gait.database.service.UserService;
import org.gait.dto.ClientRequest;
import org.gait.service.ClientService;
import org.gait.service.UserHistoryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/client")
@RequiredArgsConstructor
@Slf4j
public class ClientController {

    private final EndpointCallService endpointCallService;
    private final ClientService clientService;
    private final UserService userService;
    private final UserHistoryService userHistoryService;

    // POST endpoint: process a client prompt and return the GraphQL API result.
    @PostMapping("/use-api")
    public String processClientRequest(@RequestBody ClientRequest request, Authentication authentication) {
        UserEntity user = userService.getUserEntity(authentication);
        log.info("Client user={} is calling API={}, with prompt='{}'",
                user.getEmail(), request.getApi(), request.getPrompt());

        // Process the prompt and obtain the GraphQL response.
        String graphQLResponse = clientService.handleClientPrompt(request);

        // Increment the call count.
        endpointCallService.incrementCallCount(user, request.getApi());
        userHistoryService.saveUserHistory(String.valueOf(user.getId()),request.getPrompt());

        // Return the GraphQL result to the caller.
        return graphQLResponse;
    }

    @GetMapping("/{userId}")
    public List<UserHistoryService.UserHistoryEntry> getUserHistory(@PathVariable String userId) {
        return userHistoryService.getHistoryForUser(userId);
    }
}
