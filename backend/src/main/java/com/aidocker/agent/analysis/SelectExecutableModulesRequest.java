package com.aidocker.agent.analysis;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SelectExecutableModulesRequest(
        @NotEmpty
        List<String> executableModules
) {
}
