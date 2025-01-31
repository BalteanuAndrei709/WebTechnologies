package org.gait.database.repository;

import org.gait.database.entity.EndpointCallEntity;
import org.gait.database.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EndpointCallRepository extends JpaRepository<EndpointCallEntity, Long> {

    // Find record by user + endpointName
    Optional<EndpointCallEntity> findByUserAndEndpointName(UserEntity user, String endpointName);
}
