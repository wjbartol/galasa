package dev.galasa.maven.plugin;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.artifact.versioning.VersionRange;

@SuppressWarnings("deprecation")
public class MockArtifact implements Artifact {


    public String classifier ;
    public String type ;

    @Override
    public int compareTo(Artifact o) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'compareTo'");
    }

    @Override
    public String getGroupId() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getGroupId'");
    }

    @Override
    public String getArtifactId() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getArtifactId'");
    }

    @Override
    public String getVersion() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getVersion'");
    }

    @Override
    public void setVersion(String version) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setVersion'");
    }

    @Override
    public String getScope() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getScope'");
    }

    @Override
    public String getType() {
        return this.type;
    }

    @Override
    public String getClassifier() {
        return this.classifier;
    }

    @Override
    public boolean hasClassifier() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'hasClassifier'");
    }

    @Override
    public File getFile() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getFile'");
    }

    @Override
    public void setFile(File destination) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setFile'");
    }

    @Override
    public String getBaseVersion() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getBaseVersion'");
    }

    @Override
    public void setBaseVersion(String baseVersion) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setBaseVersion'");
    }

    @Override
    public String getId() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getId'");
    }

    @Override
    public String getDependencyConflictId() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getDependencyConflictId'");
    }

    @Override
    public void addMetadata(ArtifactMetadata metadata) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addMetadata'");
    }

    @Override
    public Collection<ArtifactMetadata> getMetadataList() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMetadataList'");
    }

    @Override
    public void setRepository(ArtifactRepository remoteRepository) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setRepository'");
    }

    @Override
    public ArtifactRepository getRepository() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getRepository'");
    }

    @Override
    public void updateVersion(String version, ArtifactRepository localRepository) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateVersion'");
    }

    @Override
    public String getDownloadUrl() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getDownloadUrl'");
    }

    @Override
    public void setDownloadUrl(String downloadUrl) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setDownloadUrl'");
    }

    @Override
    public ArtifactFilter getDependencyFilter() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getDependencyFilter'");
    }

    @Override
    public void setDependencyFilter(ArtifactFilter artifactFilter) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setDependencyFilter'");
    }

    @Override
    public ArtifactHandler getArtifactHandler() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getArtifactHandler'");
    }

    @Override
    public List<String> getDependencyTrail() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getDependencyTrail'");
    }

    @Override
    public void setDependencyTrail(List<String> dependencyTrail) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setDependencyTrail'");
    }

    @Override
    public void setScope(String scope) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setScope'");
    }

    @Override
    public VersionRange getVersionRange() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getVersionRange'");
    }

    @Override
    public void setVersionRange(VersionRange newRange) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setVersionRange'");
    }

    @Override
    public void selectVersion(String version) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'selectVersion'");
    }

    @Override
    public void setGroupId(String groupId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setGroupId'");
    }

    @Override
    public void setArtifactId(String artifactId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setArtifactId'");
    }

    @Override
    public boolean isSnapshot() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isSnapshot'");
    }

    @Override
    public void setResolved(boolean resolved) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setResolved'");
    }

    @Override
    public boolean isResolved() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isResolved'");
    }

    @Override
    public void setResolvedVersion(String version) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setResolvedVersion'");
    }

    @Override
    public void setArtifactHandler(ArtifactHandler handler) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setArtifactHandler'");
    }

    @Override
    public boolean isRelease() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isRelease'");
    }

    @Override
    public void setRelease(boolean release) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setRelease'");
    }

    @Override
    public List<ArtifactVersion> getAvailableVersions() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getAvailableVersions'");
    }

    @Override
    public void setAvailableVersions(List<ArtifactVersion> versions) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setAvailableVersions'");
    }

    @Override
    public boolean isOptional() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isOptional'");
    }

    @Override
    public void setOptional(boolean optional) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setOptional'");
    }

    @Override
    public ArtifactVersion getSelectedVersion() throws OverConstrainedVersionException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSelectedVersion'");
    }

    @Override
    public boolean isSelectedVersionKnown() throws OverConstrainedVersionException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isSelectedVersionKnown'");
    }
    
}
