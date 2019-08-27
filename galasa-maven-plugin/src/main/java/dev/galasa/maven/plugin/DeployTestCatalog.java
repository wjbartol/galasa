package dev.galasa.maven.plugin;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 *  Merge all the test catalogs on the dependency list
 * 
 * @author Michael Baylis
 *
 */
@Mojo(name = "deploytestcat", 
defaultPhase = LifecyclePhase.DEPLOY , 
threadSafe = true)
public class DeployTestCatalog extends AbstractMojo
{
	@Parameter( defaultValue = "${project}", readonly = true )
	private MavenProject project;

	@Parameter( defaultValue = "${galasa.skip.bundletestcatatlog}", readonly = true, required = false )
	private boolean skip;

	@Parameter( defaultValue = "${galasa.skip.deploytestcatatlog}", readonly = true, required = false )
	private boolean skipDeploy;

	@Parameter( defaultValue = "${galasa.test.stream}", readonly = true, required = true )
	private String testStream;

	public void execute() throws MojoExecutionException, MojoFailureException {

		if (skip || skipDeploy) {
			getLog().info("Skipping Deploy Test Catalog");
			return;
		}

		if (!"galasa-obr".equals(project.getPackaging())) {
			getLog().info("Skipping Bundle Test Catalog deploy, not a galasa-obr project");
			return;
		}

		try {
			Artifact artifact = null;
			for(Artifact a : project.getAttachedArtifacts()) {
				if ("testcatalog".equals(a.getClassifier())
						&& "json".equals(a.getType())) {
					artifact = a;
					break;
				}
			}
			if (artifact == null) {
				getLog().info("Skipping Bundle Test Catalog deploy, no test catalog artifact present");
				return;
			}



			//*** Get the bootstrap

			//*** Get the test catalog url
			URL testCatalogUrl = new URL("http://127.0.0.1:8181/testcatalog" + "/" + testStream);

			//*** authenticate

			HttpURLConnection conn = (HttpURLConnection)testCatalogUrl.openConnection();
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setRequestMethod("PUT");
			conn.addRequestProperty("Content-Type", "application/json");
			conn.addRequestProperty("Accept", "application/json");

			FileUtils.copyFile(artifact.getFile(), conn.getOutputStream());
			int rc = conn.getResponseCode();
			String message = conn.getResponseMessage();

			InputStream is = null;
			if (rc < HttpURLConnection.HTTP_BAD_REQUEST) {
				is = conn.getInputStream();
			} else {
				is = conn.getErrorStream();
			}

			String response = "";
			if (is != null) {
				response = IOUtils.toString(is, "utf-8");
			}

			conn.disconnect();


			if (rc >= HttpURLConnection.HTTP_BAD_REQUEST) {
				getLog().error("Deploy to Test Catalog Store failed:-");
				getLog().error(Integer.toString(rc) + " - " + message);
				if (!response.isEmpty()) {
					getLog().error(response);
				}

				throw new MojoExecutionException("Deploy to Test Catalog Store failed");
			}

			
			getLog().info("Test Catalog successfully deployed to " + testCatalogUrl.toString());

		} catch(Throwable t) {
			throw new MojoExecutionException("Problem merging the test catalog", t);
		}

	}

}
