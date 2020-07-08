package dev.galasa.maven.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

@Mojo(name = "gherkinzip", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class BuildGherkinZip extends AbstractMojo {

    @Component
    private MavenProjectHelper projectHelper;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject       project;

    @Parameter(defaultValue = "${project.build.directory}", property = "outputDir", required = true)
    private File               outputDirectory;

    @Parameter(defaultValue = "${galasa.skip.gherkinzip}", readonly = true, required = false)
    private boolean            skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            if (skip) {
                getLog().info("Skipping Gherkin Zip build");
                return;
            }

            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }

            List<File> featureFiles = retrieveFeatureFiles(project.getBasedir());

            File zipFile = new File(outputDirectory, project.getArtifactId() + "-" + project.getVersion() + ".zip");

            FileOutputStream fos = new FileOutputStream(zipFile.getPath());
            ZipOutputStream zos = new ZipOutputStream(fos);
            for(File file : featureFiles){
                String filePath = file.getPath();
                System.out.println("Zipping "+filePath);
                //for ZipEntry we need to keep only relative file path, so we used substring on absolute path
                ZipEntry ze = new ZipEntry(filePath.substring(project.getBasedir().getAbsolutePath().length()+1, filePath.length()));
                zos.putNextEntry(ze);
                //read the file and write to ZipOutputStream
                FileInputStream fis = new FileInputStream(filePath);
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
                zos.closeEntry();
                fis.close();
            }
            zos.close();
            fos.close();

            projectHelper.attachArtifact(project, "zip", "gherkinzip", zipFile);

        } catch (Throwable t) {
            throw new MojoExecutionException("Problem processing creating zip for gherkin", t);
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