package org.gait.database.entity;

import jakarta.persistence.*;
import lombok.*;
import org.gait.database.entity.UserEntity;

@Entity
@Table(name = "endpoint_call",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "endpoint_name"})
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EndpointCallEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user who made the call (must be a CLIENT,
     * but from the DB perspective it's just a foreign key to user).
     */
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /**
     * The identifier/name of the endpoint that was called.
     */
    @Column(name = "endpoint_name", nullable = false)
    private String endpointName;

    /**
     * How many times the given user called this endpoint.
     */
    @Column(name = "call_count", nullable = false)
    private Long callCount;
}
