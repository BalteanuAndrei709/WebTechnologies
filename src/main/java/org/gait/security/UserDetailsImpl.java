package org.gait.security;

import lombok.*;
import org.gait.database.entity.UserEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * A custom UserDetails that wraps our UserEntity.
 */
@Getter
@RequiredArgsConstructor
public class UserDetailsImpl implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final GrantedAuthority authority;

    /**
     * Static factory: build UserDetailsImpl from a UserEntity.
     */
    public static UserDetailsImpl build(UserEntity userEntity) {
        // We'll name the authority "ROLE_<ROLE_NAME>"
        String roleName = userEntity.getRole().getRole().name();
        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + roleName);

        return new UserDetailsImpl(
                userEntity.getId(),
                userEntity.getEmail(),
                userEntity.getPassword(),
                authority
        );
    }

    @Override
    public String getUsername() {
        // "username" in Spring Security terms → we use the email field
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Return a single-element list
        return List.of(authority);
    }

    /* The following can be customized or simply return true if not used */
    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
