package com.dsi.rfp.adapter.security;

import com.dsi.rfp.adapter.persistence.entity.UserEntity;
import com.dsi.rfp.domain.model.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class RfpUserDetails implements UserDetails {

    private final String username;
    private final String passwordHash;
    private final UserRole role;
    private final boolean enabled;

    public RfpUserDetails(UserEntity user) {
        this(
            user.getUsername(),
            user.getPasswordHash(),
            user.getRole(),
            user.isEnabled()
        );
    }

    RfpUserDetails(
        String username,
        String passwordHash,
        UserRole role,
        boolean enabled
    ) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
            new SimpleGrantedAuthority(
                String.format("ROLE_%s", role.name())
            )
        );
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
