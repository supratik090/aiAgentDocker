package com.aidocker.agent.sprint2;

import java.nio.file.Path;
import java.time.Clock;
import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Service;

@Service
public class BranchManagementService {

    private final Clock clock;

    public BranchManagementService(Clock clock) {
        this.clock = clock;
    }

    public String createDeployReadyBranch(Path repositoryPath) {
        String branchName = "aiDocker/deploy-ready-" + clock.instant().toEpochMilli();
        try (Git git = Git.open(repositoryPath.toFile())) {
            git.checkout()
                    .setCreateBranch(true)
                    .setName(branchName)
                    .call();
            return branchName;
        } catch (Exception exception) {
            throw new Sprint2Exception("Unable to create deploy-ready branch.", exception);
        }
    }
}
