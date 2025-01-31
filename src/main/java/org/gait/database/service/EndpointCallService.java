package org.gait.database.service;

import lombok.RequiredArgsConstructor;
import org.gait.database.entity.EndpointCallEntity;
import org.gait.database.entity.UserEntity;
import org.gait.database.repository.EndpointCallRepository;
import org.gait.database.repository.UserRepository;
import org.gait.dto.Api;
import org.gait.dto.EndpointCall;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EndpointCallService {

    private final UserRepository userRepository;
    private final EndpointCallRepository endpointCallRepository;

    /**
     * Optionally increment a call counter using your EndpointCall entity.
     * This can go in a service, but for brevity, we'll do it here.
     */
    public void incrementCallCount(UserEntity user, Api api) {
        String endpointName = api.toString();
        var endpointCallOpt = endpointCallRepository.findByUserAndEndpointName(user, endpointName);

        EndpointCallEntity endpointCall = endpointCallOpt.orElseGet(() -> {
            // create a new record for (user, api)
            EndpointCallEntity newCall = EndpointCallEntity.builder()
                    .user(user)
                    .endpointName(endpointName)
                    .callCount(0L)
                    .build();
            return endpointCallRepository.save(newCall);
        });

        // Increment
        endpointCall.setCallCount(endpointCall.getCallCount() + 1);
        endpointCallRepository.save(endpointCall);
    }

    public List<EndpointCall> getCallStats() {

        List<EndpointCallEntity> endpointCallEntityList = endpointCallRepository.findAll();

        // Map each EndpointCallEntity to an EndpointCallDto
        return endpointCallEntityList
                .stream()
                .map(entity ->
                        new EndpointCall(
                                entity.getUser().getEmail(),
                                entity.getEndpointName(),
                                entity.getCallCount()))
                .toList();
    }

}
