package com.aidocker.agent.deployment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;

@Service
public class PermissionCheckCommitService {

    private final Clock clock;

    public PermissionCheckCommitService(Clock clock) {
        this.clock = clock;
    }

    public String createPermissionCheckCommitAndPush(Path repositoryPath, String branchName, String accessToken) {
        try (Git git = Git.open(repositoryPath.toFile())) {
            Path permissionCheckFile = repositoryPath.resolve("aiDocker-permission-check.txt");
            Files.writeString(
                    permissionCheckFile,
                    "AI Docker Agent permission check\nGenerated at: " + clock.instant() + "\n"
            );

            git.add().addFilepattern("aiDocker-permission-check.txt").call();
            String commitId = git.commit()
                    .setMessage("Add AI Docker Agent permission check file")
                    .call()
                    .getId()
                    .getName();

            git.push()
                    .setRemote("origin")
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider("x-access-token", accessToken))
                    .add("refs/heads/" + branchName + ":refs/heads/" + branchName)
                    .call();

            return commitId;
        } catch (IOException exception) {
            throw new DeploymentPermissionException("Unable to write permission check file.", exception);
        } catch (Exception exception) {
            throw new DeploymentPermissionException("Unable to commit and push permission check file.", exception);
        }
    }

    public String commitFilesAndPush(
            Path repositoryPath,
            String branchName,
            String accessToken,
            List<String> filePatterns,
            String commitMessage
    ) {
        try (Git git = Git.open(repositoryPath.toFile())) {
            for (String filePattern : filePatterns) {
                git.add().addFilepattern(filePattern).call();
            }

            String commitId = git.status().call().isClean()
                    ? git.getRepository().resolve("HEAD").getName()
                    : git.commit()
                            .setMessage(commitMessage)
                            .call()
                            .getId()
                            .getName();

            git.push()
                    .setRemote("origin")
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider("x-access-token", accessToken))
                    .add("refs/heads/" + branchName + ":refs/heads/" + branchName)
                    .call();

            return commitId;
        } catch (Exception exception) {
            throw new DeploymentPermissionException("Unable to commit and push generated files.", exception);
        }
    }
}
