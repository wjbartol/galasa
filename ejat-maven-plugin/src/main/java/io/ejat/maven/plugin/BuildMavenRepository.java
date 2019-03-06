package io.ejat.maven.plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

import org.apache.commons.codec.binary.Hex;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 *  This goal will take a set of OBRs and create the equivalent Karaf Feature XML file.
 * 
 *  It will create a feature per OBR and a feature containing all resources from each of the OBRs. 
 * 
 * @author Michael Baylis
 *
 */
@Mojo(name = "mavenrepository", 
defaultPhase = LifecyclePhase.PROCESS_RESOURCES , 
requiresDependencyResolution = ResolutionScope.COMPILE,
threadSafe = true)
public class BuildMavenRepository extends AbstractMojo
{
    @Parameter( defaultValue = "${project}", readonly = true )
    private MavenProject project;

    @Parameter( defaultValue = "${project.build.directory}", property = "outputDir", required = true )
    private File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new MojoExecutionException("Unable to locate SHA-1 disgest", e);
        }

        Path targetDirectory;
        try {
            targetDirectory = Paths.get(outputDirectory.toURI());
            Files.createDirectories(targetDirectory);
        } catch(Exception e) {
            throw new MojoExecutionException("Unable to create the target repository directory", e);
        }

        Set<Artifact> artifacts = project.getArtifacts();
        for(Artifact artifact : artifacts) {
            if (artifact.getScope().equals(Artifact.SCOPE_COMPILE)) {
                Path artifactFile = Paths.get(artifact.getFile().toURI());

                String pomFilename = artifact.getArtifactId() + "-" + artifact.getBaseVersion() + ".pom";
                Path pomFile = artifactFile.resolveSibling(pomFilename);

                String[] groupSplit = artifact.getGroupId().split("\\.");

                Path groupPath = Paths.get("repository", groupSplit);
                Path targetGroupDirectory = targetDirectory.resolve(groupPath);
                Path targetArtifactDirectory = targetGroupDirectory.resolve(artifact.getArtifactId());
                Path targetVersionDirectory = targetArtifactDirectory.resolve(artifact.getBaseVersion());
           
                try {
                    Files.createDirectories(targetVersionDirectory);

                    Files.copy(artifactFile, targetVersionDirectory.resolve(artifactFile.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    if (Files.exists(pomFile)) {
                        Files.copy(pomFile, targetVersionDirectory.resolve(pomFile.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch(Exception e) {
                    throw new MojoExecutionException("Unable to copy artifact " + artifact, e);
                }


                Path artifactHash = artifactFile.resolveSibling(artifactFile.getFileName() + ".sha1");
                Path pomHash = pomFile.resolveSibling(pomFile.getFileName() + ".sha1");

                createHash(artifactFile, artifactHash, targetVersionDirectory, digest);
                createHash(pomFile, pomHash, targetVersionDirectory, digest);
                
                System.out.println("Copied artifact " + artifact.toString());
            }
        }
    }

    private void createHash(Path file, Path hash, Path targetDirectory, MessageDigest digest) throws MojoExecutionException {
        try {
            if (Files.exists(hash)) {
                Files.copy(hash, targetDirectory.resolve(hash.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            } else {
                String hex = Hex.encodeHexString(digest.digest(Files.readAllBytes(file)));
                Files.write(targetDirectory.resolve(hash.getFileName()), hex.getBytes("utf-8"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch(Exception e) {
            throw new MojoExecutionException("Unable to create/copy the hash file", e);
        }
    }

}
