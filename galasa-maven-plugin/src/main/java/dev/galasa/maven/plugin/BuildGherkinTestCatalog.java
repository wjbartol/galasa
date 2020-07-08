package dev.galasa.maven.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

@Mojo(name = "gherkintestcat", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class BuildGherkinTestCatalog extends AbstractMojo {

    @Component
    private MavenProjectHelper projectHelper;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject       project;

    @Parameter(defaultValue = "${project.build.directory}", property = "outputDir", required = true)
    private File               outputDirectory;

    @Parameter(defaultValue = "${galasa.skip.gherkintestcatatlog}", readonly = true, required = false)
    private boolean            skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            if (skip) {
                getLog().info("Skipping Gherkin Test Catalog build");
                return;
            }

            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }

            // *** Create the JSON Template
            JsonObject jsonRoot = new JsonObject();
            JsonObject jsonFeatures = new JsonObject();
            jsonRoot.add("gherkin", jsonFeatures);

            List<File> featureFiles = retrieveFeatureFiles(project.getBasedir());

            for(File feature : featureFiles) {
                String featureName = feature.getPath().replace(project.getBasedir().getPath() + "/", "");
                JsonObject featureJson = new JsonObject();
                featureJson.addProperty("name", featureName);
                featureJson.addProperty("shortName", feature.getName());
                JsonObject maven = new JsonObject();
                maven.addProperty("groupId", project.getGroupId());
                maven.addProperty("artifactId", project.getArtifactId());
                maven.addProperty("version", project.getVersion());
                featureJson.add("maven", maven);

                jsonFeatures.add(featureName, featureJson);
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String testCatalog = gson.toJson(jsonRoot);

            File fileTestCatalog = new File(outputDirectory, "testcatalog.json");
            FileUtils.writeStringToFile(fileTestCatalog, testCatalog, "utf-8");

            projectHelper.attachArtifact(project, "json", "testcatalog", fileTestCatalog);
        } catch (Throwable t) {
            throw new MojoExecutionException("Problem processing the test catalog for the bundle", t);
        }
    }

    private List<File> retrieveFeatureFiles(File file) {
        List<File> files = new ArrayList<>();
        if(file.isFile() && file.getName().endsWith(".feature")) {
            files.add(file);
        } else if(file.isDirectory()) {
            for(File child : file.listFiles()) {
                files.addAll(retrieveFeatureFiles(child));
            }
        }
        return files;
    }
    
}