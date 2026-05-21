package com.aidocker.agent.repository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CloneRepositoryRequest(
        @NotBlank
        @Pattern(regexp = "^https://.+", message = "Git URL must start with https://")
        String gitUrl,
        String branch,
        String conversationId
) {
}
