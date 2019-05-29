package io.ejat.maven.plugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.felix.bundlerepository.DataModelHelper;
import org.apache.felix.bundlerepository.Repository;
import org.apache.felix.bundlerepository.Resource;
import org.apache.felix.bundlerepository.impl.DataModelHelperImpl;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
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
@Mojo(name = "karaffeature", 
defaultPhase = LifecyclePhase.PROCESS_RESOURCES , 
threadSafe = true,
requiresDependencyCollection = ResolutionScope.COMPILE,
requiresDependencyResolution = ResolutionScope.COMPILE)
public class BuildKarafFeature extends AbstractMojo
{
	@Parameter( defaultValue = "${project}", readonly = true )
	private MavenProject project;

	@Parameter( defaultValue = "${project.build.directory}", property = "outputDir", required = true )
	private File outputDirectory;

	/**
	 * Contains the name to be used for the uber feature
	 */
	@Parameter( property = "featureName", required = false )
	private String featureName;

	/**
	 * What features are required for our features.   will be added to each of our generated features
	 */
	@Parameter( property = "requiredFeatures", required = false )
	private String[] requiredFeatures;

	/**
	 * What bundles to exclude 
	 */
	@Parameter( property = "excludes", required = false )
	private String[] excludes;

	private HashSet<String> actualExcludes = new HashSet<>();

	public void execute() throws MojoExecutionException, MojoFailureException {

		DataModelHelper obrDataModelHelper = new DataModelHelperImpl();

		if (!outputDirectory.exists()) {
			outputDirectory.mkdirs();
		}

		File featureFile = new File(outputDirectory, "feature.xml");
		project.getArtifact().setFile(featureFile);


		//*** If no feature name provided,  use the artifact id
		if (featureName == null || featureName.isEmpty()) {
			featureName = project.getArtifactId();
		}

		if (requiredFeatures == null) {
			requiredFeatures = new String[0];
		}

		if (excludes != null) {
			for(String exclude : excludes) {
				exclude = exclude.trim();
				if (!exclude.isEmpty()) {
					actualExcludes.add(exclude);
				}
			}
		}

		Features newFeatures = new Features(project.getArtifactId() + "-" + project.getVersion());
		Feature uberFeature = new Feature(this.featureName, project.getVersion(), requiredFeatures);

		//** Go through all the dependencies and only use those that are:-
		//** Resolved,  scope=compile and is an OBR
		for(Object dependency : project.getDependencyArtifacts()) {
			if (dependency instanceof DefaultArtifact) {
				DefaultArtifact artifact = (DefaultArtifact)dependency;
				if (artifact.isResolved() 
						&& "compile".equals(artifact.getScope())
						&& "obr".equals(artifact.getType())) {
					processObr(artifact, newFeatures, uberFeature, obrDataModelHelper);
				}
			}
		}

		//** Must find atleast 1 OBR
		if (newFeatures.getFeatures().isEmpty()) {
			throw new MojoExecutionException("No resources have been added to the feature");
		}
		newFeatures.addFeature(uberFeature);

		try(FileWriter fw = new FileWriter(featureFile)) {
			fw.write(newFeatures.toXml());
		} catch(Exception e) {
			throw new MojoExecutionException("Problem with writing feature.xml", e);
		}

		getLog().info("BuildKarafFeature: Karaf feature.xml created with " + newFeatures.getFeatures().size() + " features and " + uberFeature.getBundles().size() + " bundles");
	}

	private void processObr(Artifact artifact, 
			Features newFeatures, 
			Feature uberFeature,
			DataModelHelper obrDataModelHelper) throws MojoExecutionException {

		Feature obrFeature = new Feature(artifact.getArtifactId(), artifact.getVersion(), requiredFeatures);
		newFeatures.addFeature(obrFeature);

		try (FileReader fr = new FileReader(artifact.getFile())) {
			Repository mergeRepository = obrDataModelHelper.readRepository(fr);

			for(Resource resource : mergeRepository.getResources()) {
				System.out.println(resource.getSymbolicName());
				if (actualExcludes.contains(resource.getSymbolicName())) {
					getLog().info("BuildKarafFeature: Excluding OBR " + resource.getPresentationName() + " - " + resource.getId() + " from feature");
					continue;
				}


				Bundle bundle = new Bundle(resource.getURI());
				obrFeature.addBundle(bundle);
				uberFeature.addBundle(bundle);
				getLog().info("BuildKarafFeature: Merged OBR " + resource.getPresentationName() + " - " + resource.getId() + " to feature");
			}
		} catch(Exception e) {
			throw new MojoExecutionException("Unable to read existing OBR", e);
		}
	}


	public static class Features {
		private final String name;
		private final ArrayList<Feature> features = new ArrayList<>();

		public Features(String name) {
			this.name = name;
		}

		public void addFeature(Feature feature) {
			this.features.add(feature);
		}

		public String toXml() {
			StringBuilder sb = new StringBuilder();

			sb.append("<features xmlns=\"http://karaf.apache.org/xmlns/features/v1.3.0\" name=\"" + this.name + "\">\n");

			for(Feature feature : features) {
				feature.toXml(sb);
			}

			sb.append("</features>");

			return sb.toString();
		}

		public List<Feature> getFeatures() {
			return features;
		}

	}

	public static class Feature {
		private final String name;
		private final String version;
		private final String[] features;
		private final ArrayList<Bundle> bundles = new ArrayList<>();

		public Feature(String name, String version, String[] features) {
			this.name = name;
			this.version = version;
			this.features = features;
		}

		public void toXml(StringBuilder sb) {
			sb.append("    <feature name=\"" + this.name + "\" version=\"" + this.version + "\">\n");

			for(String feature : features) {
				sb.append("        <feature>" + feature + "</feature>\n");
			}

			for(Bundle bundle : bundles) {
				bundle.toXml(sb);
			}

			sb.append("    </feature>\n");
		}

		public void addBundle(Bundle bundle) {
			this.bundles.add(bundle);
		}

		public List<Bundle> getBundles() {
			return bundles;
		}
	}

	public static class Bundle {
		private final String uri;

		public Bundle(String uri) {
			this.uri = uri;
		}

		public void toXml(StringBuilder sb) {
			sb.append("        <bundle>" + this.uri + "</bundle>\n");
		}
	}

}
