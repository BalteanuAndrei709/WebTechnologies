package org.gait.service;

import lombok.RequiredArgsConstructor;
import org.gait.dto.ClientRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final RestTemplate restTemplate; // We'll inject this via a @Bean or directly

    /**
     * Sends the given ClientRequest to some external API and returns the response as a String.
     */
    public String sendRequestToExternalApi(ClientRequest request) {
        // 1) Define the external URL (example placeholder)
        String externalApiUrl = "https://example.com/api/endpoint";

        // 2) Make a POST request with our ClientRequest as the request body
        ResponseEntity<String> response = restTemplate.postForEntity(
                externalApiUrl,
                request,            // JSON body automatically mapped from ClientRequest
                String.class        // We expect a String in the response
        );

        // 3) Return the response body
        return response.getBody();
    }
}
