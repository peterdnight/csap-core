/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.test.t3.models.no_spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import org.csap.agent.linux.HostStatusManager;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.Application;
import org.csap.agent.model.ReleasePackage;
import org.csap.agent.model.ServiceAttributes;
import org.csap.agent.model.ServiceInstance;
import org.csap.test.t1_container.Boot_Container_Test;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author someDeveloper
 */
public class Model_As_Manager {

	final static private Logger logger = LoggerFactory.getLogger( Model_As_Manager.class );

	@BeforeClass
	public static void setUpBeforeClass ()
			throws Exception {
		Boot_Container_Test.printTestHeader( logger.getName() );

		Application.setJvmInManagerMode( true );
		csapApplication = new Application();
		csapApplication.setEventClient( new CsapEventClient() );
		csapApplication.initialize();

		csapApplication.setAutoReload( false );

		File csapApplicationDefinition = new File(
			Model_As_Manager.class.getResource( "/org/csap/test/data/clusterConfigManager.json" ).getPath() );

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();

		HostStatusManager testStatus = new HostStatusManager(
			new File( Model_As_Manager.class.getResource( "/CsAgent_Host_Response.json" ).getPath() ) );
		csapApplication.setHostStatusManager( testStatus );

		logger.info( Boot_Container_Test.SETUP_HEAD + "Using: " + csapApplicationDefinition.getAbsolutePath() );
	}

	@AfterClass
	public static void tearDownAfterClass ()
			throws Exception {
	}

	// @Inject
	static Application csapApplication;

	@Before
	public void setUp ()
			throws Exception {

	}

	@After
	public void tearDown ()
			throws Exception {
	}

	@Test
	public void verify_application_loaded () {

		logger.info( Boot_Container_Test.TC_HEAD );

		assertThat( csapApplication.getName() )
			.as( "Capability Name parsed" )
			.isEqualTo( "Desktop Dev2" );

		assertThat( csapApplication.getModelForHost( "sampleHostA-dev01" ).getReleasePackageName() )
			.as( "Release Packag for host" )
			.isEqualTo(
				"Supporting Sample A" );

		List<String> fileNames = csapApplication.getPackageModels().stream()
			.filter( model -> !model.getReleasePackageName().equals( ReleasePackage.GLOBAL_PACKAGE ) )
			.map( ReleasePackage::getReleasePackageFileName )
			.collect( Collectors.toList() );

		assertThat( fileNames )
			.as( "Files loaded" )
			.contains( "clusterConfigManager.json",
				"clusterConfigManagerA.json",
				"clusterConfigManagerB.json" );

		assertThat( csapApplication.getSvcToConfigMap().get( "CsAgent" ) )
			.as( "CsAgents instances found" )
			.hasSize( 9 );

		assertThat( csapApplication.getLifecycleList() )
			.as( "Lifecycles found" )
			.hasSize( 10 )
			.contains( "dev", "dev-WebServer-1", "stage" );

		assertThat( csapApplication.getModel( "Supporting Sample A" ).getLifecycleList() )
			.as( "Lifecycles for model" )
			.hasSize( 4 )
			.contains( "dev",
				"dev-middlewareA-1" );

		assertThat( csapApplication.getModel( Application.ALL_PACKAGES )
			.getServiceToAllInstancesMap().keySet() )
				.as( "All service instanaces in all models" )
				.hasSize( 18 )
				.contains( "SpringBootRest",
					"CsAgent", "CsspSample",
					"Factory2Sample",
					"FactorySample",
					"SampleDataLoader",
					"SampleJvmInA",
					"SampleJvmInA2",
					"SampleJvmInB" );

		assertThat( csapApplication.getModel( Application.ALL_PACKAGES ).getHostToConfigMap().keySet() )
			.as( "All Hosts found" )
			.hasSize( 12 )
			.contains(
				"SampleHostB-dev03",
				"csap-dev01",
				"csap-dev02",
				"csapdb-dev01",
				"localhost",
				"peter-dev01",
				"sampleHostA-dev01",
				"sampleHostA2-dev02",
				"xcssp-qa01",
				"xcssp-qa02" );

		List<ServiceInstance> services = csapApplication.getModel( Application.ALL_PACKAGES ).getServiceToAllInstancesMap()
			.get( "CsAgent" );
		//logger.info( "services: {}" , services);
		assertThat( services )
			.as( "All Hosts found" )
			.hasSize( 12 );


		assertThat( csapApplication.getMaxDeploySecondsForService( "CsAgent" ) )
			.as( "Maximum Deploy for Agent" )
			.isEqualTo( 300 );

		assertThat( csapApplication.getMaxDeploySecondsForService( "Factory2Sample" ) )
			.as( "Maximum Deploy for Factory2Sample" )
			.isEqualTo( 900 );
	}

	@Test
	public void verify_agent_attributes () {

		logger.info( Boot_Container_Test.TC_HEAD );
		ServiceInstance agentInstance = csapApplication.getSvcToConfigMap().get( "CsAgent" ).get( 0 );

		assertThat( agentInstance.isSpringBoot() )
			.as( "server override" )
			.isTrue();

		assertThat( agentInstance.getProcessFilter() )
			.as( "process filter" )
			.isEqualTo( ".*java.*csapProcessId=CsAgent_8011.*" );

		assertThat( agentInstance.getOsProcessPriority() )
			.as( "os priority" )
			.isEqualTo( "-99" );

		assertThat(agentInstance.getAttribute(ServiceAttributes.parameters ) )
			.as( "parameters" )
			.contains( "-Doverride=true" );

		assertThat( agentInstance.getMavenId() )
			.as( "maven override" )
			.contains( "9.9.9" );

		assertThat( agentInstance.getScm() )
			.as( "scm type" )
			.isEqualTo( "svn" );

		assertThat( agentInstance.getPerformanceConfiguration().at( "/SpringMvcRequests/mbean" ).asText() )
			.as( "performance mbean" )
			.isEqualTo( "Tomcat:j2eeType=Servlet,WebModule=__CONTEXT__,name=dispatcherServlet,J2EEApplication=none,J2EEServer=none" );
	}

	@Test
	public void verify_release_files_found () {

		logger.info( Boot_Container_Test.TC_HEAD );
		assertThat( csapApplication.getLastTestParseResults() )
			.as( "release files found" )
			.doesNotContain( "No release files found" )
			.contains( "clusterConfigManagerA-release" );

		ServiceInstance sampleService = csapApplication.findFirstServiceInstanceInLifecycle( "SampleJvmInA" );
		// getSvcToConfigMap().get( "SampleJvmInA" ).get( 0 );

		assertThat( sampleService.getMavenId() )
			.as( "mavenId" )
			.isEqualTo( "org.demo:TestForVersionReleaseFile:9.9.9-SNAPSHOT:jar" );
	}

	@Test
	public void verify_httpd_attributes () {

		logger.info( Boot_Container_Test.TC_HEAD );
		ServiceInstance httpdInstance = csapApplication.getSvcToConfigMap().get( "httpd" ).get( 0 );

		assertThat( httpdInstance.isWrapper() )
			.as( "is wrapper" )
			.isTrue();

		assertThat( httpdInstance.getProcessFilter() )
			.as( "process" )
			.isEqualTo( "httpd_8080" );

		assertThat( httpdInstance.getMonitors() )
			.as( "disk limit" )
			.isNull();

		assertThat( httpdInstance.getPerformanceConfiguration() )
			.as( "colleciton url" )
			.isNull();

		assertThat( httpdInstance.getLifeEnvironmentVariables().fieldNames() )
			.as( "environmentVariabls" )
			.hasSize( 0 );

		assertThat( httpdInstance.getDefaultLogToShow() )
			.as( "log to show" )
			.isEqualTo( "catalina.out" );

		assertThat( httpdInstance.getPropDirectory() )
			.as( "propery folder" )
			.isEqualTo( "/home/ssadmin/staging/httpdConf" );
	}

}
