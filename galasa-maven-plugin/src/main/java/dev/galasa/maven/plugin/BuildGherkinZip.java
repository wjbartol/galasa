package dev.galasa.maven.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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

@Mojo(name = "gherkinzip", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
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

            List<File> featureFiles = new ArrayList<>();
            Files.list(project.getBasedir().toPath()).forEach(new ConsumeDirectory(featureFiles));

            File zipFile = new File(outputDirectory, project.getArtifactId() + "-" + project.getVersion() + ".zip");

            FileOutputStream fos = new FileOutputStream(zipFile.getPath());
            ZipOutputStream zos = new ZipOutputStream(fos);
            for(File file : featureFiles){
                String filePath = file.getPath();
                getLog().info("Zipping "+filePath);
                ZipEntry ze = new ZipEntry(filePath.substring(project.getBasedir().getAbsolutePath().length()+1, filePath.length()));
                zos.putNextEntry(ze);
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

    private static class ConsumeDirectory implements Consumer<Path> {

        private final List<File> files;

        public ConsumeDirectory(List<File> files) {
            this.files = files;
        }

        @Override
        public void accept(Path path) {
            try {
                if(Files.isDirectory(path)) {
                    Files.list(path).forEach(new ConsumeDirectory(files));
                } else {
                    if(path.toFile().getName().endsWith(".feature")) {
                        files.add(path.toFile());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}