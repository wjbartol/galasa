package io.ejat.maven.plugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
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
	
	@Parameter( property = "featureName", required = false )
	private String featureName;

	public void execute() throws MojoExecutionException, MojoFailureException {

		DataModelHelper obrDataModelHelper = new DataModelHelperImpl();

		if (!outputDirectory.exists()) {
			outputDirectory.mkdirs();
		}

		File featureFile = new File(outputDirectory, "feature.xml");
		project.getArtifact().setFile(featureFile);
		
		if (featureName == null || featureName.isEmpty()) {
			featureName = project.getArtifactId();
		}

		Features newFeatures = new Features(project.getArtifactId() + "-" + project.getVersion());
		Feature uberFeature = new Feature(this.featureName, project.getVersion());
		newFeatures.addFeature(uberFeature);

		for(Object dependency : project.getDependencyArtifacts()) {
			if (dependency instanceof DefaultArtifact) {
				DefaultArtifact artifact = (DefaultArtifact)dependency;
				if (artifact.isResolved() 
						&& artifact.getScope().equals("compile")
						&& artifact.getFile().getName().endsWith(".obr")) {
					processObr(artifact, newFeatures, uberFeature, obrDataModelHelper);
				}
			}
		}

		if (newFeatures.getFeatures().isEmpty()) {
			throw new MojoFailureException("No resources have been added to the feature");
		}

		try {
			FileWriter fw = new FileWriter(featureFile);
			fw.write(newFeatures.toXml());
			fw.close();
		} catch(Exception e) {
			throw new MojoExecutionException("Problem with writing repository.xml", e);
		}

		getLog().info("BuildKarafFeature: Karaf feature.xml created with " + newFeatures.getFeatures().size() + " features and " + uberFeature.getBundles().size() + " bundles");
	}

	private void processObr(Artifact artifact, 
			Features newFeatures, 
			Feature uberFeature,
			DataModelHelper obrDataModelHelper) throws MojoExecutionException {
		
		Feature obrFeature = new Feature(artifact.getArtifactId(), artifact.getVersion());
		newFeatures.addFeature(obrFeature);

		try (FileReader fr = new FileReader(artifact.getFile())) {
			Repository mergeRepository = obrDataModelHelper.readRepository(fr);

			for(Resource resource : mergeRepository.getResources()) {
				Bundle bundle = new Bundle(resource.getURI());
				obrFeature.addBundle(bundle);
				uberFeature.addBundle(bundle);
				getLog().info("BuildKarafFeature: Merged bundle " + resource.getPresentationName() + " - " + resource.getId() + " to feature");
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
		private final ArrayList<Bundle> bundles = new ArrayList<>();
		
		public Feature(String name, String version) {
			this.name = name;
			this.version = version;
		}

		public void toXml(StringBuilder sb) {
			sb.append("    <feature name=\"" + this.name + "\" version=\"" + this.version + "\">\n");
			
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
