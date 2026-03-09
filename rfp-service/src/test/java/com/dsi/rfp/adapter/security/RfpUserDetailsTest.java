package com.dsi.rfp.adapter.security;

import com.dsi.rfp.adapter.persistence.entity.UserEntity;
import com.dsi.rfp.domain.model.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RfpUserDetailsTest {

    @Test
    void shouldExposeSerializableUserDetailsFields() {
        RfpUserDetails details = new RfpUserDetails(
            UserEntity.builder()
                .username("analyst")
                .passwordHash("hash")
                .role(UserRole.ANALYST)
                .enabled(true)
                .build()
        );

        assertThat(details.getUsername()).isEqualTo("analyst");
        assertThat(details.getPassword()).isEqualTo("hash");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities())
            .extracting("authority")
            .containsExactly("ROLE_ANALYST");
    }
}
