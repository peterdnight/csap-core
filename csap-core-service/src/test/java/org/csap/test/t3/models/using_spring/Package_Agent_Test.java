package org.csap.test.t3.models.using_spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.csap.agent.CsapCoreService;
import org.csap.agent.model.Application;
import org.csap.agent.model.HttpdIntegration;
import org.csap.agent.model.ServiceAlertsEnum;
import org.csap.test.InitializeLogging;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@RunWith ( SpringRunner.class )
@SpringBootTest ( classes = CsapCoreService.class )
@ActiveProfiles ( profiles = { "junit", "company" } )
public class Package_Agent_Test {

	final static private Logger logger = LoggerFactory.getLogger( Package_Agent_Test.class );

	@BeforeClass
	public static void setUpBeforeClass ()
			throws Exception {
		InitializeLogging.printTestHeader( logger.getName() );
	}

	@AfterClass
	public static void tearDownAfterClass ()
			throws Exception {
	}

	@Before
	public void setUp ()
			throws Exception {

		logger.info( "Deleting: {}", getHttpdConfig().getHttpdWorkersFile().getParentFile().getAbsolutePath() );
		FileUtils.deleteQuietly( getHttpdConfig().getHttpdWorkersFile().getParentFile() );

		Application.setJvmInManagerMode( false );
		csapApp.setAutoReload( false );


		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/DEFAULT_APPLICATION.json" ).getPath() );

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();

	}

	@After
	public void tearDown ()
			throws Exception {
	}

	@Inject
	Application csapApp;

	@Test
	public void get_latest_csap_version_number_from_csaptools ()
			throws JsonProcessingException, IOException {
		logger.info( InitializeLogging.TC_HEAD );

		String platformVersions = csapApp.updatePlatformVersionsFromCsapTools( true );
		logger.info( "platformVersions: {}", platformVersions );
		assertThat( platformVersions )
			.as( "Get Latest version for csap scorecards on landing page" )
			.matches( "5.*8u.*6.*" );

	}

	private ObjectMapper jacksonMapper = new ObjectMapper();

	@Test
	public void verify_alert_thresholds_for_service ()
			throws JsonProcessingException, IOException {
		logger.info( InitializeLogging.TC_HEAD );

		String targetService = "CsapSimple";
		ObjectNode limitsJson = ServiceAlertsEnum.getAdminUiLimits(
			csapApp.findFirstServiceInstanceInLifecycle( targetService ),
			csapApp.lifeCycleSettings() );

		logger.info( "{} serviceLimits: {}", targetService, limitsJson );

		assertThat( limitsJson.get( ServiceAlertsEnum.threads.value ).asInt() )
			.as( "Service thread limit" )
			.isEqualTo( 300 );

		assertThat( limitsJson.get( ServiceAlertsEnum.httpConnections.value ).asInt() )
			.as( "Service Tomcat limit" )
			.isEqualTo( 20 );

		assertThat( limitsJson.get( ServiceAlertsEnum.diskWriteRate.value ).asInt() )
			.as( "Cluster Disk default limit" )
			.isEqualTo( 15 );

	}

	@Test
	public void agent_in_release_package ()
			throws JsonProcessingException, IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "packageAgent/main.json" ).getPath() );

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();

		assertThat( csapApp.getName() )
			.as( "Capability Name parsed" )
			.isEqualTo( "TestDefinitionWithMultipleServices" );

		assertThat( csapApp.getActiveModelName() )
			.as( "Active model name" )
			.isEqualTo( "Supporting Sample A" );

		assertThat(
			csapApp.getRootModel()
				.getReleasePackageNames()
				.collect( Collectors.toList() ) )
					.as( "Release Package names" )
					.containsExactly( "SampleDefaultPackage",
						"Supporting Sample A",
						"Supporting Sample B" );

		assertThat( csapApp.getModelForHost( "sampleHostA-dev01" ).getReleasePackageName() )
			.as( "Release Packag for host" )
			.isEqualTo(
				"Supporting Sample A" );

		assertThat( csapApp.getSvcToConfigMap().get( "CsAgent" ) )
			.as( "CsAgents instances found" )
			.hasSize( 3 );

		assertThat(
			csapApp.getActiveModel().getServiceInstances( "CsAgent" ).count() )
				.as( "CsAgents instances in active model and lifecycle" )
				.isEqualTo( 2 );

		assertThat(
			csapApp.getMutableHostsInActivePackage( csapApp.activeLifecycleClusterName() ) )
				.as( "All Hosts in Current Lifecycle" )
				.hasSize( 2 )
				.contains(
					"localhost",
					"middlewareA2Host-dev98" );

		assertThat(
			csapApp.getActiveModel().getLifeCycleToGroupMap() )
				.as( "Active model Lifecycle to Groups" )
				.hasSize( 2 )
				.containsKeys( "dev", "stage" )
				.containsEntry( "dev",
					new ArrayList<String>(
						Arrays.asList(
							"middlewareA",
							"middlewareA2" ) ) );

		assertThat( csapApp.getActiveModel().getReleasePackageName() )
			.as( "Release Packag for active model" )
			.isEqualTo( "Supporting Sample A" );

		assertThat( csapApp.getModel( Application.ALL_PACKAGES ).getLifeCycleToGroupMap().get( "dev" ) )
			.as( "Groups for dev" )
			.hasSize(
				4 )
			.contains(
				"cssp",
				"middlewareA",
				"middlewareA2",
				"middlewareB" );

		assertThat(
			csapApp.getServiceInstanceCurrentHost( "CsAgent_8011" ).getMavenRepo() )
				.as( "CsAgent Maven Repo" )
				.isEqualTo(
					"https://repo.maven.apache.org/maven2/" );

		assertThat(
			csapApp.getServiceInstanceCurrentHost( "SampleJvmInA_8041" ).getServiceName() )
				.as( "Sample service loaded" )
				.isEqualTo(
					"SampleJvmInA" );

		assertThat(
			csapApp.getRootModel().getAllPackagesModel()
				.serviceInstancesInCurrentLifeByName().get( "CsspSample" ) )
					.as( "CsspSample count in all packages" )
					.hasSize( 3 );

		assertThat(
			csapApp.getRootModel().getAllPackagesModel()
				.serviceInstancesInCurrentLifeByName().get( "redis" ) )
					.as( "Service count in all packages" )
					.hasSize( 3 );

		// resolve all instances in all packages.
		List<String> redisHosts = csapApp
			.getRootModel().getAllPackagesModel()
			.getServiceInstances( "redis" )
			.map( serviceInstance -> serviceInstance.getHostName() )
			.collect( Collectors.toList() );

		assertThat( redisHosts )
			.as( "Service count in all packages" )
			.hasSize( 3 )
			.contains( "mainHostA", "mainHostB", "mainHostC" );

		assertThat(
			csapApp.getRootModel().getLifeCycleToGroupMap() )
				.as( "Lifecycles to group in root model" )
				.hasSize( 2 )
				.containsKey( "dev" );

		assertThat(
			csapApp.getRootModel().getServiceToAllInstancesMap().keySet() )
				.as( "Services in root model" )
				.hasSize( 9 )
				.contains( "CsAgent",
					"CsspSample",
					"redis",
					"FactorySample",
					"RedHatLinux",
					"ServletSample",
					"httpd",
					"oracleDriver",
					"springmvc-showcase" );

		assertThat( csapApp.getModel( Application.ALL_PACKAGES )
			.getLifeCycleToHostMap().get( Application.getCurrentLifeCycle() ) )
				.as( "Hosts in current lifecycle" )
				.hasSize(
					6 )
				.contains(
					"mainHostA",
					"localhost",
					"middlewareA2Host-dev98",
					"SampleHostB-dev99" );

		assertThat( getHttpdConfig().getHttpdWorkersFile() )
			.as( "Ensure web server files are not created as they are not in active model" )
			.doesNotExist();
	}

	private HttpdIntegration getHttpdConfig () {
		return csapApp.getHttpdIntegration();
	}

}
