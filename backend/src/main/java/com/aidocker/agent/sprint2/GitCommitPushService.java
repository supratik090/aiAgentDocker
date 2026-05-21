package com.aidocker.agent.sprint2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;

@Service
public class GitCommitPushService {

    private final Clock clock;

    public GitCommitPushService(Clock clock) {
        this.clock = clock;
    }

    public String createDummyFileCommitAndPush(Path repositoryPath, String branchName, String accessToken) {
        try (Git git = Git.open(repositoryPath.toFile())) {
            Path dummyFile = repositoryPath.resolve("aiDocker-pr-check.txt");
            Files.writeString(
                    dummyFile,
                    "AI Docker Agent PR check\nGenerated at: " + clock.instant() + "\n"
            );

            git.add().addFilepattern("aiDocker-pr-check.txt").call();
            String commitId = git.commit()
                    .setMessage("Add AI Docker Agent PR check file")
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
            throw new Sprint2Exception("Unable to write dummy PR check file.", exception);
        } catch (Exception exception) {
            throw new Sprint2Exception("Unable to commit and push dummy PR check file.", exception);
        }
    }
}
