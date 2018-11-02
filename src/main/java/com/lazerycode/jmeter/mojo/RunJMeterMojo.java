package com.lazerycode.jmeter.mojo;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.lazerycode.jmeter.configuration.JMeterArgumentsArray;
import com.lazerycode.jmeter.exceptions.IOException;
import com.lazerycode.jmeter.json.TestConfig;
import com.lazerycode.jmeter.testrunner.TestManager;

/**
 * Goal that runs jmeter based on configuration defined in your pom.<br/>
 * This goal runs within Lifecycle phase {@link LifecyclePhase#INTEGRATION_TEST}.
 */
@Mojo(name = "jmeter", defaultPhase = LifecyclePhase.INTEGRATION_TEST)
@Execute(goal = "configure")
public class RunJMeterMojo extends AbstractJMeterMojo {
	
	 /** Perfana: Name of application that is being tested.
	   */
	  
	  private String application = "Mean";

	  /**
	   * Perfana: Test type for this test.
	   */
	  
	  private String testType= "LoadTest";

	  /**
	   * Perfana: Test environment for this test.
	   */
	 
	  private String testEnvironment= "acc";

	  /**
	   * Perfana: Test run id.
	   */
	  
	  private String testRunId = "Jmeter-1";

	  /**
	   * Perfana: Build results url where to find the results of this load test.
	   */
	  
	  private String CIBuildResultsUrl= "http://jenkins:8080/job/PERFANA-GATLING-DEMO/18/";

	  /**
	   * Perfana: Perfana url.
	   */
	  
	  private String perfanaUrl="http://perfana:4000";

	  /**
	   * Perfana: the release number of the application.
	   */
	  
	  private String applicationRelease="1";

	  /**
	   * Perfana: Rampup time in seconds.
	   */
	  
	  private String rampupTimeInSeconds ="10";

	  /**
	   * Perfana: Constan load time in seconds.
	   */
	 
	  private String constantLoadTimeInSeconds="600";

	  /**
	   * Perfana: Parse the Perfana test asserts and fail build it not ok.
	   */
	  
	  private boolean assertResultsEnabled=false;

	  /**
	   * Perfana: Enable calls to Perfana.
	   */
	 
	  private boolean perfanaEnabled=true;

	  /**
	   * Perfana: test run annotiations passed via environment variable
	   */
	 
	  private String annotations ="webbroker";

	  /**
	   * Perfana: test run variables passed via environment variable
	   */
	  
	  private Properties variables;
	
	
	
	
	
	/**
	 * Run all the JMeter tests.
	 *
	 * @throws MojoExecutionException
	 * @throws MojoFailureException
	 * @throws java.io.IOException 
	 */
	@Override
	public void doExecute() throws MojoExecutionException, MojoFailureException, java.io.IOException {
		getLog().info(" ");
		getLog().info(LINE_SEPARATOR);
		getLog().info(" P E R F O R M A N C E    T E S T S");
		getLog().info(LINE_SEPARATOR);
		
		
		final ScheduledExecutorService exec;
	    final PerfanaClient perfanaClient = perfanaEnabled
	            ? createPerfanaClient()
	            : null;

	    if (perfanaEnabled) {
	      final int periodInSeconds = 15;
	      getLog().info(String.format("Calling Perfana (%s) keep alive every %d seconds.", perfanaUrl, periodInSeconds));
	      final PerfanaClient.KeepAliveRunner keepAliveRunner = new PerfanaClient.KeepAliveRunner(perfanaClient);
	      exec = Executors.newSingleThreadScheduledExecutor();
	      exec.scheduleAtFixedRate(keepAliveRunner, 0, periodInSeconds, TimeUnit.SECONDS);
	    }
	    else {
	      exec = null;
	    }
	    
	    if (perfanaEnabled) {
	        perfanaClient.callPerfana(true);
	        if (assertResultsEnabled) {
	          try {
	            assertResultsPerfana(perfanaClient);
	          } catch (IOException e) {
	            throw new MojoExecutionException("Perfana assertions check failed. " + e.getMessage(), e);
	          }
	        }
	        else {
	          getLog().info("Perfana assertions disabled.");
	        }
	      }
	    
	    
		
		if (!testFilesDirectory.exists()) {
			getLog().info("<testFilesDirectory>" + testFilesDirectory.getAbsolutePath() + "</testFilesDirectory> does not exist...");
			getLog().info("Performance tests are skipped.");
			return;
		}

		TestConfig testConfig = new TestConfig(new File(testConfigFile));
		JMeterArgumentsArray testArgs = computeJMeterArgumentsArray(true, testConfig.getResultsOutputIsCSVFormat());

		if (null != remoteConfig) {
			remoteConfig.setPropertiesMap(JMeterConfigurationHolder.getInstance().getPropertiesMap());
		}

		copyFilesInTestDirectory(testFilesDirectory, testFilesBuildDirectory);

		TestManager jMeterTestManager = 
		        new TestManager(testArgs, testFilesBuildDirectory, testFilesIncluded, testFilesExcluded, 
		                remoteConfig, suppressJMeterOutput, JMeterConfigurationHolder.getInstance().getWorkingDirectory(), jMeterProcessJVMSettings, 
		                JMeterConfigurationHolder.getInstance().getRuntimeJarName(), reportDirectory, generateReports);
		jMeterTestManager.setPostTestPauseInSeconds(postTestPauseInSeconds);
		getLog().info(" ");
		if (proxyConfig != null) {
			getLog().info(this.proxyConfig.toString());
		}

		testConfig.setResultsFileLocations(jMeterTestManager.executeTests());
		testConfig.writeResultFilesConfigTo(testConfigFile);
	}
	
	 private PerfanaClient createPerfanaClient() {
		    PerfanaClient client = new PerfanaClient(application, testType, testEnvironment, testRunId, CIBuildResultsUrl, applicationRelease, rampupTimeInSeconds, constantLoadTimeInSeconds, perfanaUrl, annotations, variables);
		    client.injectLogger(new PerfanaClient.Logger() {
		      @Override
		      public void info(String message) {
		        getLog().info(message);
		      }

		      @Override
		      public void warn(String message) {
		        getLog().warn(message);
		      }

		      @Override
		      public void error(String message) {
		        getLog().error(message);
		      }

		      @Override
		      public void debug(final String message) {
		        getLog().debug(message);
		      }
		    });
		    return client;
		  }
	 
	 /**
	   * Call the target io assertions and let build fail when not OK
	   * @throws MojoExecutionException when the assertion check fails or
	   * a technical issue occurs
	   * @param perfanaClient call this client
	   */


	  Configuration config = Configuration.defaultConfiguration()
	          .addOptions(Option.SUPPRESS_EXCEPTIONS);

	  private void assertResultsPerfana(PerfanaClient perfanaClient) throws MojoExecutionException, IOException, java.io.IOException {
	    final String assertions = perfanaClient.callCheckAsserts();
	    if (assertions == null) {
	      throw new MojoExecutionException("Perfana assertions could not be checked, received null");
	    }


	    Boolean benchmarkBaselineTestRunResult = JsonPath.using(config).parse(assertions).read("$.benchmarkBaselineTestRun.result");
	    String benchmarkBaselineTestRunDeeplink = JsonPath.using(config).parse(assertions).read("$.benchmarkBaselineTestRun.deeplink");
	    Boolean benchmarkPreviousTestRunResult = JsonPath.using(config).parse(assertions).read("$.benchmarkPreviousTestRun.result");
	    String benchmarkPreviousTestRunDeeplink = JsonPath.using(config).parse(assertions).read("$.benchmarkPreviousTestRun.deeplink");
	    Boolean requirementsResult = JsonPath.using(config).parse(assertions).read("$.requirements.result");
	    String requirementsDeeplink = JsonPath.using(config).parse(assertions).read("$.requirements.deeplink");

	    getLog().info("benchmarkBaselineTestRunResult: "  + benchmarkBaselineTestRunResult);
	    getLog().info("benchmarkPreviousTestRunResult: " + benchmarkPreviousTestRunResult);
	    getLog().info("requirementsResult: " + requirementsResult);

	    if (assertions.contains("false")) {

	      String assertionText = "One or more Perfana assertions are failing: \n";
	      if(requirementsResult != null && requirementsResult == false) assertionText += "Requirements failed: " + requirementsDeeplink + "\n";
	      if(benchmarkPreviousTestRunResult != null && benchmarkPreviousTestRunResult == false) assertionText += "Benchmark to previous test run failed: " + benchmarkPreviousTestRunDeeplink + "\n";
	      if(benchmarkBaselineTestRunResult != null && benchmarkBaselineTestRunResult == false) assertionText += "Benchmark to baseline test run failed: " + benchmarkBaselineTestRunDeeplink;

	      getLog().info("assertionText: " + assertionText);

	      throw new MojoExecutionException( assertionText );
	    }
	    else {

	      String assertionText = "All Perfana assertions are OK: \n";
	      if(requirementsResult) assertionText += requirementsDeeplink + "\n";
	      if(benchmarkPreviousTestRunResult) assertionText += benchmarkPreviousTestRunDeeplink + "\n";
	      if(benchmarkBaselineTestRunResult) assertionText += benchmarkBaselineTestRunDeeplink;

	      getLog().info(assertionText);
	    }
	  }
	
}