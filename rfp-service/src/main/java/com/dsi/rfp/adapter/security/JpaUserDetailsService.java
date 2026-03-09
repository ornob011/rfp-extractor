package com.dsi.rfp.adapter.security;

import com.dsi.rfp.adapter.persistence.entity.UserEntity;
import com.dsi.rfp.adapter.persistence.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class JpaUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public JpaUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException(
                String.format("User not found: %s", username)
            ));

        if (!user.isEnabled()) {
            throw new UsernameNotFoundException(
                String.format("User is disabled: %s", username)
            );
        }

        return new RfpUserDetails(user);
    }
}
