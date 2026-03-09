package com.dsi.rfp.adapter.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @NotBlank String username,
    @NotBlank @Size(min = 8) String password
) {
}
