package com.aidocker.agent.deployment;

import java.nio.file.Path;
import java.time.Clock;
import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Service;

@Service
public class DeploymentBranchService {

    private final Clock clock;

    public DeploymentBranchService(Clock clock) {
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
            throw new DeploymentPermissionException("Unable to create deploy-ready branch.", exception);
        }
    }

    public void checkoutBranch(Path repositoryPath, String branchName) {
        try (Git git = Git.open(repositoryPath.toFile())) {
            git.checkout()
                    .setName(branchName)
                    .call();
        } catch (Exception exception) {
            throw new DeploymentPermissionException("Unable to checkout deployment branch.", exception);
        }
    }
}
