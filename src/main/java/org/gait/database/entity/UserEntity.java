package org.gait.database.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "APP_USER")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @Column(name = "USER_ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMAIL", nullable = false, unique = true)
    private String email;

    @Column(name = "FULLNAME")
    private String fullname;

    @Column(name = "AGE")
    private Integer age;

    @Column(name = "PASSWORD", nullable = false)
    private String password;

    /**
     * Each user has exactly one role.
     * We'll store ROLE_ID as a foreign key in this table.
     */
    @OneToOne
    @JoinColumn(name = "ROLE_ID", referencedColumnName = "id")
    private Role role;
}
