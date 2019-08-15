package test.karafBuilder;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.jar.Attributes;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.bundlerepository.DataModelHelper;
import org.apache.felix.bundlerepository.Resource;
import org.apache.felix.bundlerepository.impl.DataModelHelperImpl;
import org.apache.felix.bundlerepository.impl.RepositoryImpl;
import org.apache.felix.bundlerepository.impl.ResourceImpl;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Constants;

import dev.galasa.maven.plugin.BuildKarafFeature;

public class KarafTest {

	private static final String FEATURE_NAME    = "i_am_a_feature";
	private static final String FEATURE_ID      = "i_need_you";
	private static final String PROJECT_ID      = "feature1";
	private static final String PROJECT_VERSION = "9.8.7";
	private static final String BUNDLE_URI      = "mvn:groupx/artifactx/1.1.1/jar";
	private static final String OBR_NAME        = "obr1";
	private static final String OBR_VERSION     = "1.2.6";

	private BuildKarafFeature buildKarafFeature;

	private File outputDirectory;
	private File featureXmlFile;
	private Path tempObr;

	private MavenProject project;
	private DefaultArtifact depArtifactObr;

	/**
	 * Setup a golden path Maven project with dependencies to exercise logic in the goa;
	 * 
	 * @throws Exception - test failures
	 */
	@Before
	public void setup() throws Exception {
		Path tempDir = Files.createTempDirectory("junit_test_");
		this.outputDirectory = tempDir.toFile();
		featureXmlFile = new File(outputDirectory, "feature.xml");

		DefaultArtifact artifact = new DefaultArtifact("group1", PROJECT_ID, "1.2.3", "compile", "xml", "xml", null);

		//*** Setup invalid deps, to ensure they are ignored
		DefaultArtifact depArtifactJar        = new DefaultArtifact("group1", "jar1",        "1.2.4",     "compile",  "jar", "jar", null);
		DefaultArtifact depArtifactUnresolved = new DefaultArtifact("group2", "unresolved1", "1.2.5",     "compile",  "xml", "xml", null);
		DefaultArtifact depArtifactProvided   = new DefaultArtifact("group2", "provided1",   "1.2.5",     "provided", "obr", "obr", null);

		//** Setup the valid OBR
		depArtifactObr                        = new DefaultArtifact("group3", OBR_NAME,      OBR_VERSION, "compile",  "obr", "obr", null);

		depArtifactJar.setResolved(true);
		depArtifactUnresolved.setResolved(false);
		depArtifactProvided.setResolved(true);
		depArtifactObr.setResolved(true);

		HashSet<Artifact> artifacts = new HashSet<>();
		artifacts.add(depArtifactJar);
		artifacts.add(depArtifactUnresolved);
		artifacts.add(depArtifactProvided);
		artifacts.add(depArtifactObr);
		artifacts.add(new TestArtifact());

		DataModelHelper obrDataModelHelper = new DataModelHelperImpl();
		RepositoryImpl newRepository = new RepositoryImpl();
		Attributes attributes = new Attributes();
		attributes.put(new Attributes.Name(Constants.BUNDLE_SYMBOLICNAME), "io.bundleid");
		attributes.put(new Attributes.Name(Resource.ID), "bundleid");
		ResourceImpl newResource = (ResourceImpl)obrDataModelHelper.createResource(attributes);
		newRepository.addResource(newResource);
		newResource.put(Resource.URI, new URI(BUNDLE_URI));

		tempObr = Files.createTempFile("junit_obr_", ".obr");
		try(FileWriter fw = new FileWriter(tempObr.toFile())) {
			obrDataModelHelper.writeRepository(newRepository, fw);
		}
		depArtifactObr.setFile(tempObr.toFile());

		project = new MavenProject();	
		this.project.setGroupId(artifact.getGroupId());
		this.project.setArtifactId(artifact.getArtifactId());
		this.project.setArtifact(artifact);
		this.project.setVersion(PROJECT_VERSION);
		this.project.setDependencyArtifacts(artifacts);

		buildKarafFeature = new BuildKarafFeature();
		setPrivateField(buildKarafFeature, outputDirectory, "outputDirectory");
		setPrivateField(buildKarafFeature, project, "project");
		setPrivateField(buildKarafFeature, FEATURE_NAME, "featureName");
		setPrivateField(buildKarafFeature, new String[] {FEATURE_ID}, "requiredFeatures");
	}

	/**
	 * Try and clean up to prevent filling /tmp
	 * 
	 * @throws Exception - test failures
	 */
	@After
	public void tearDown() throws Exception {
		if (featureXmlFile.exists()) {
			featureXmlFile.setWritable(true);
		}
		
		FileUtils.deleteQuietly(outputDirectory);
		FileUtils.deleteQuietly(tempObr.toFile());
	}


	/**
	 * Test the Golden path
	 * 
	 * @throws Exception - test failures
	 */
	@Test
	public void testGoldenPath() throws Exception {
		buildKarafFeature.execute();

		String featureXml = FileUtils.readFileToString(featureXmlFile);
		Assert.assertEquals("Should contain only 1 features", 1, StringUtils.countMatches(featureXml, "<features xmlns=\"http://karaf.apache.org/xmlns/features/v1.3.0\" name=\"" + PROJECT_ID + "-" + PROJECT_VERSION + "\">"));
		Assert.assertEquals("Should contain 2 <feature name=", 2, StringUtils.countMatches(featureXml, "<feature name="));
		Assert.assertEquals("Should contain 2 <feature>", 2, StringUtils.countMatches(featureXml, "<feature>" + FEATURE_ID + "</feature>"));
		Assert.assertEquals("Should contain 2 <bundle>", 2, StringUtils.countMatches(featureXml, "<bundle>" + BUNDLE_URI + "</bundle>"));
	}

	/**
	 * Ensure an output directory is created if not already there
	 * 
	 * @throws Exception - test failures
	 */
	@Test
	public void testNewOutputPath() throws Exception {
		outputDirectory.delete();
		buildKarafFeature.execute();

		String featureXml = FileUtils.readFileToString(featureXmlFile);
		Assert.assertEquals("Does not contain <features ", 1, StringUtils.countMatches(featureXml, "<features xmlns=\"http://karaf.apache.org/xmlns/features/v1.3.0\" name=\"" + PROJECT_ID + "-" + PROJECT_VERSION + "\">"));
	}

	/**
	 * Ensure defaults taken if no feature info provided
	 * 
	 * @throws Exception - test failures
	 */
	@Test
	public void testNoFeature() throws Exception {
		setPrivateField(buildKarafFeature, null, "featureName");
		setPrivateField(buildKarafFeature, null, "requiredFeatures");

		buildKarafFeature.execute();


		String featureXml = FileUtils.readFileToString(featureXmlFile);
		Assert.assertEquals("Does not contain <features ", 1, StringUtils.countMatches(featureXml, "<features xmlns=\"http://karaf.apache.org/xmlns/features/v1.3.0\" name=\"" + PROJECT_ID + "-" + PROJECT_VERSION + "\">"));
		Assert.assertEquals("Should contain 2 <feature name=", 2, StringUtils.countMatches(featureXml, "<feature name="));
		Assert.assertTrue("Should default feature name", StringUtils.contains(featureXml, "<feature name=\"feature1\""));
		Assert.assertEquals("Should contain 0 <feature>", 0, StringUtils.countMatches(featureXml, "<feature>"));
	}


	/**
	 * Ensure defaults taken if feature name is empty
	 * 
	 * @throws Exception - test failures
	 */
	@Test
	public void testNoFeatures() throws Exception {
		setPrivateField(buildKarafFeature, "", "featureName");

		buildKarafFeature.execute();


		String featureXml = FileUtils.readFileToString(featureXmlFile);
		Assert.assertEquals("Does not contain <features ", 1, StringUtils.countMatches(featureXml, "<features xmlns=\"http://karaf.apache.org/xmlns/features/v1.3.0\" name=\"" + PROJECT_ID + "-" + PROJECT_VERSION + "\">"));
		Assert.assertTrue("Should default feature name", StringUtils.contains(featureXml, "<feature name=\"feature1\""));
		Assert.assertEquals("Should contain 2 <feature>", 2, StringUtils.countMatches(featureXml, "<feature>"));
	}


	/**
	 * Ensure exception thrown in no valid OBR is found
	 * 
	 * @throws Exception - test failures
	 */
	@Test
	public void testNoFeaturesProduced() throws Exception {
		
		depArtifactObr.setResolved(false);

		try {
			buildKarafFeature.execute();
			fail("Should have failed with an exception saying no features produced");
		} catch(MojoExecutionException e) {
			if (!e.getMessage().contains("No resources have been added")) {
				fail("Should have failed with an exception saying no features produced");
			}
		}
	}
	

	/**
	 * Ensure exception is thrown if we can't write the feature.xml file
	 * 
	 * @throws Exception - test failures
	 */
	@Test
	public void testUnableToWriteToFile() throws Exception {
		
		FileUtils.write(featureXmlFile, "dummy");
		featureXmlFile.setWritable(false);

		try {
			buildKarafFeature.execute();
			fail("Should have failed with an exception saying Problem with writing feature.xml");
		} catch(MojoExecutionException e) {
			if (!e.getMessage().contains("Problem with writing feature.xml")) {
				fail("Should have failed with an exception saying Problem with writing feature.xml");
			}
		} finally {
			featureXmlFile.setWritable(true);
		}
	}


	/**
	 * Ensure exception thrown if the OBR is invalid
	 * 
	 * @throws Exception - test failures
	 */
	@Test
	public void testInvalidObr() throws Exception {
		
		FileUtils.write(tempObr.toFile(), "invalid xml");

		try {
			buildKarafFeature.execute();
			fail("Should have failed with an exception saying Unable to read existing OBR");
		} catch(MojoExecutionException e) {
			if (!e.getMessage().contains("Unable to read existing OBR")) {
				fail("Should have failed with an exception saying Unable to read existing OBR");
			}
		} finally {
			featureXmlFile.setWritable(true);
		}
	}


	private void setPrivateField(Object instance, Object data, String fieldName) throws Exception {

		Class<?> klass = instance.getClass(); 
		Field field = klass.getDeclaredField(fieldName);

		field.setAccessible(true);

		field.set(instance, data);

		field.setAccessible(false);
	}



	private static class TestArtifact implements Artifact {

		@Override
		public int compareTo(Artifact o) {
			return 0;
		}

		@Override
		public String getGroupId() {
			return null;
		}

		@Override
		public String getArtifactId() {
			return null;
		}

		@Override
		public String getVersion() {
			return null;
		}

		@Override
		public void setVersion(String version) {
		}

		@Override
		public String getScope() {
			return null;
		}

		@Override
		public String getType() {
			return null;
		}

		@Override
		public String getClassifier() {
			return null;
		}

		@Override
		public boolean hasClassifier() {
			return false;
		}

		@Override
		public File getFile() {
			return null;
		}

		@Override
		public void setFile(File destination) {
		}

		@Override
		public String getBaseVersion() {
			return null;
		}

		@Override
		public void setBaseVersion(String baseVersion) {
		}

		@Override
		public String getId() {
			return null;
		}

		@Override
		public String getDependencyConflictId() {
			return null;
		}

		@Override
		public void addMetadata(ArtifactMetadata metadata) {
		}

		@Override
		public Collection<ArtifactMetadata> getMetadataList() {
			return null;
		}

		@Override
		public void setRepository(ArtifactRepository remoteRepository) {
		}

		@Override
		public ArtifactRepository getRepository() {
			return null;
		}

		@Override
		public void updateVersion(String version, ArtifactRepository localRepository) {
		}

		@Override
		public String getDownloadUrl() {
			return null;
		}

		@Override
		public void setDownloadUrl(String downloadUrl) {
		}

		@Override
		public ArtifactFilter getDependencyFilter() {
			return null;
		}

		@Override
		public void setDependencyFilter(ArtifactFilter artifactFilter) {
		}

		@Override
		public ArtifactHandler getArtifactHandler() {
			return null;
		}

		@Override
		public List<String> getDependencyTrail() {
			return null;
		}

		@Override
		public void setDependencyTrail(List<String> dependencyTrail) {
		}

		@Override
		public void setScope(String scope) {
		}

		@Override
		public VersionRange getVersionRange() {
			return null;
		}

		@Override
		public void setVersionRange(VersionRange newRange) {
		}

		@Override
		public void selectVersion(String version) {
		}

		@Override
		public void setGroupId(String groupId) {
		}

		@Override
		public void setArtifactId(String artifactId) {
		}

		@Override
		public boolean isSnapshot() {
			return false;
		}

		@Override
		public void setResolved(boolean resolved) {
		}

		@Override
		public boolean isResolved() {
			return false;
		}

		@Override
		public void setResolvedVersion(String version) {
		}

		@Override
		public void setArtifactHandler(ArtifactHandler handler) {
		}

		@Override
		public boolean isRelease() {
			return false;
		}

		@Override
		public void setRelease(boolean release) {
		}

		@Override
		public List<ArtifactVersion> getAvailableVersions() {
			return null;
		}

		@Override
		public void setAvailableVersions(List<ArtifactVersion> versions) {
		}

		@Override
		public boolean isOptional() {
			return false;
		}

		@Override
		public void setOptional(boolean optional) {
		}

		@Override
		public ArtifactVersion getSelectedVersion() throws OverConstrainedVersionException {
			return null;
		}

		@Override
		public boolean isSelectedVersionKnown() throws OverConstrainedVersionException {
			return false;
		}

	}
}
