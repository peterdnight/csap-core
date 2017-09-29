package org.csap.test.t3.models.using_spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.csap.agent.CSAP;
import org.csap.agent.CsapCoreService;
import org.csap.agent.linux.HostStatusManager;
import org.csap.agent.model.Application;
import org.csap.agent.model.HttpdIntegration;
import org.csap.agent.model.LifeCycleSettings;
import org.csap.agent.model.ServiceInstance;
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

public class Load_Working_Models_Test {

	final static private Logger logger = LoggerFactory.getLogger( Load_Working_Models_Test.class );

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

		logger.info( InitializeLogging.TC_HEAD + "Deleting: "
				+ getHttpdConfig().getHttpdWorkersFile().getParentFile().getAbsolutePath() );

		Application.setJvmInManagerMode( false );
		csapApplication.setAutoReload( false );

		FileUtils.deleteQuietly( getHttpdConfig().getHttpdWorkersFile().getParentFile() );

	}

	@After
	public void tearDown ()
			throws Exception {
	}

	@Inject
	Application csapApplication;

	@Test
	public void get_latest_csap_version_number_from_csaptools ()
			throws JsonProcessingException, IOException {
		logger.info( InitializeLogging.TC_HEAD );

		CSAP.setLogToDebug( Application.class.getName() );
		assertThat( csapApplication.updatePlatformVersionsFromCsapTools( true ) )
			.as( "Get Latest version for csap scorecards on landing page" )
			.matches( "6.*8u.*6.*" );
		CSAP.setLogToInfo( Application.class.getName() );

	}

	@Test
	public void verify_platform_score_from_csaptools ()
			throws JsonProcessingException, IOException {
		logger.info( InitializeLogging.TC_HEAD );

		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/clusterConfigManager.json" ).getPath() );

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();

		CSAP.setLogToDebug( Application.class.getName() );
		ObjectNode score = csapApplication.getPlatformScore( true );

		logger.info( "Score: {}", CSAP.jsonPrint( jacksonMapper, score ) );

		assertThat( score.get( Application.AGENT_ID ).asText() )
			.as( "Platform scorecards on landing page" )
			.contains( " of 8" );

		CSAP.setLogToInfo( Application.class.getName() );
	}

	@Test
	public void verify_application_score ()
			throws JsonProcessingException, IOException {
		logger.info( InitializeLogging.TC_HEAD );

		ObjectNode score = csapApplication.getApplicationScore();

		logger.info( "Score: " + jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( score ) );

		assertThat( score.get( "total" ).asInt() )
			.as( "Application  scorecards on landing page" )
			.isGreaterThan( 1 );

	}

	@Test
	public void load_application_with_hosts_excluded_from_httpd ()
			throws JsonProcessingException, IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "application-hosts-filtered.json" ).getPath() );

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();

		assertThat( csapApplication.getName() )
			.as( "Capability Name parsed" )
			.isEqualTo( "DEFAULT APPLICATION FOR JUNITS" );

		assertThat( csapApplication.getLifecycleList() )
			.as( "Lifecycles found" )
			.hasSize( 4 )
			.contains( "dev", "dev-csspLocal-1", "stage",
				"stage-cssp-1" );

		assertThat( csapApplication.getSvcToConfigMap().get( "CsAgent" ) )
			.as( "CsAgents instances found" )
			.hasSize( 4 );

		assertThat( csapApplication.getServiceInstanceAnyPackage( Application.AGENT_NAME_PORT ).getOsProcessPriority() )
			.as( "CsAgent OS Priority" )
			.isEqualTo(
				"-10" );

		assertThat( csapApplication.getServiceInstanceCurrentHost( "CsAgent_8011" ).getMavenRepo() )
			.as( "CsAgent Maven Repo" )
			.isEqualTo(
				"https://repo.maven.apache.org/maven2/" );

		assertThat( getHttpdConfig().getHttpdWorkersFile() )
			.as( "Web Servers Worker file created" )
			.exists();

		assertThat( contentOf( getHttpdConfig().getHttpdWorkersFile() ) )
			.as( "Ensure filtered VMs are not in web server config files" )
			.doesNotContain(
				"balance_workers=Cssp3ReferenceMq_8241localhost,Cssp3ReferenceMq_8241host2" )
			.contains(
				"worker.Cssp3ReferenceMq_8241localhost.type=ajp13" )
			.contains(
				"balance_workers=Cssp3ReferenceMq_8241localhost" );

		assertThat( getHttpdConfig().getHttpdModjkFile() )
			.as( "Web Servers ModJK file created" )
			.exists();

		assertThat( contentOf( getHttpdConfig().getHttpdModjkFile() ) )
			.contains( "Cssp3ReferenceMq" );

	}

	/**
	 *
	 * Scenario: - load a config file without package definition and 1 jvm and 1
	 * os
	 *
	 */
	@Test
	public void load_simple_application_as_agent ()
			throws JsonProcessingException, IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "application-simple.json" ).getPath() );

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();

		assertThat( csapApplication.getName() )
			.as( "Capability Name parsed" )
			.isEqualTo( "DEFAULT APPLICATION FOR JUNITS" );

		assertThat( csapApplication.getLifecycleList() )
			.as( "Lifecycles found" )
			.hasSize( 4 )
			.contains( "dev", "stage" );

		assertThat( csapApplication.getSvcToConfigMap().get( "CsAgent" ) )
			.as( "CsAgent Instances found" )
			.hasSize( 3 );

		ServiceInstance agentInstance = csapApplication.getServiceInstanceCurrentHost( "CsAgent_8011" );
		csapApplication.getOsManager().checkForProcessStatusUpdate();

		assertThat( agentInstance.isAutoStart() )
			.as( "CsAgent autostart" )
			.isTrue();

		assertThat( !agentInstance.isInactive() )
			.as( "CsAgent Started" )
			.isTrue();

		assertThat( agentInstance.getPid() )
			.as( "CsAgent pid" )
			.contains( "4149" );

		assertThat( agentInstance.getPidsAsString() )
			.as( "CsAgent pid string" )
			.isEqualTo( "4149" );

		assertThat( agentInstance.getOsProcessPriority() )
			.as( "CsAgent process priority" )
			.isEqualTo( "-10" );

		assertThat( agentInstance.getDefaultLogToShow() )
			.as( "Default log file for service" )
			.isEqualTo( "consoleLogs.txt" );

		assertThat( agentInstance.getOsProcessPriority() )
			.as( "CsAgent OS Priority" )
			.isEqualTo( "-10" );

		assertThat( agentInstance.getMavenRepo() )
			.as( "CsAgent Maven Repo" )
			.isEqualTo(
				"https://repo.maven.apache.org/maven2/" );

		ServiceInstance jdkInstance = csapApplication.getServiceInstanceCurrentHost( "jdk_0" );
		assertThat( jdkInstance.getPidsAsString() )
			.as( "jdk pid string" )
			.isEqualTo( "noMatches" );

		ServiceInstance refService = csapApplication.getSvcToConfigMap().get( "Cssp3ReferenceMq" ).get( 0 );
		assertThat( refService.isTomcatPackaging() )
			.as( "server override" )
			.isTrue();

		ServiceInstance httpdService = csapApplication.getSvcToConfigMap().get( "httpd" ).get( 0 );

		assertThat( httpdService.isAutoStart() )
			.as( "httpdService autostart" )
			.isTrue();

		assertThat( httpdService.isWrapper() )
			.as( "httpdService autostart" )
			.isTrue();

		assertThat( httpdService.getDefaultLogToShow() )
			.as( "Default log file for service" )
			.isEqualTo( "access.log" );

		logger.info( "Verifying Apache Workers file: {}", getHttpdConfig().getHttpdWorkersFile().getAbsolutePath() );
		assertThat( getHttpdConfig().getHttpdWorkersFile() )
			.as( "Web Servers Worker file created" )
			.exists();

		assertThat( contentOf( getHttpdConfig().getHttpdWorkersFile() ) )
			.contains(
				"worker.Cssp3ReferenceMq_8241localhost.type=ajp13" );

		assertThat( getHttpdConfig().getHttpdModjkFile() )
			.as( "Web Servers ModJK file created" )
			.exists();

		assertThat( contentOf( getHttpdConfig().getHttpdModjkFile() ) )
			.contains( "Cssp3ReferenceMq" );

	}

	/**
	 *
	 * Scenario: - load a config file with multiple services, some shared
	 * nothing, some standard - httpd instance has generateWorkerProperties
	 * metadata in cluster.js - verify that mount points get generated
	 *
	 */
	@Test
	public void load_application_with_cluster_types ()
			throws JsonProcessingException, IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/test_application_model.json" ).getPath() );

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();

		assertThat( csapApplication.getName() )
			.as( "Capability Name parsed" )
			.isEqualTo( "TestDefinitionForAutomatedTesting" );

		assertThat( csapApplication.getRootModel().getHostToConfigMap().keySet() )
			.as( "All Hosts found" )
			.hasSize( 9 )
			.contains( "csap-dev01",
				"csap-dev02",
				"csapdb-dev01", "localhost",
				"peter-dev01", "xcssp-qa01",
				"xcssp-qa02",
				"xfactory-qa01",
				"xfactory-qa02" );

		assertThat( csapApplication.getAllHostsInAllPackagesInCurrentLifecycle() )
			.as( "Lifecycles Hosts found" )
			.hasSize( 5 )
			.contains( "localhost",
				"csapdb-dev01" );

		assertThat( csapApplication.getLifecycleList() )
			.as( "Lifecycles found" )
			.hasSize( 10 )
			.contains( "dev", "dev-WebServer-1", "stage" );

		assertThat( csapApplication.getSvcToConfigMap().get( "CsAgent" ) )
			.as( "CsAgents instances found" )
			.hasSize( 9 );

		assertThat( csapApplication.getSvcToConfigMap().keySet() )
			.as( "Service instances found" )
			.hasSize( 15 )
			.contains( "AuditService", "CsAgent", "CsspSample",
				"Factory2Sample",
				"FactorySample", "SampleDataLoader",
				"ServletSample", "activemq",
				"admin", "denodo", "httpd", "oracle",
				"sampleOsWrapper",
				"springmvc-showcase", "vmmemctl" );

		// New instance meta data
		ServiceInstance csAgentInstance = csapApplication
			.getServiceInstanceAnyPackage( Application.AGENT_NAME_PORT );

		assertThat( csAgentInstance.getOsProcessPriority() )
			.as( "CsAgent OS Priority" )
			.isEqualTo( "-12" );

		// logger.info("Workers file: " +
		// getHttpdConfig().getHttpdWorkersFile().getAbsolutePath());
		assertThat( getHttpdConfig().getHttpdWorkersFile() )
			.as( "Web Servers Worker file created" )
			.exists();

		assertThat( contentOf( getHttpdConfig().getHttpdWorkersFile() ) )
			.contains( "worker.FactorySample-dev01_LB" )
			.contains( "worker.AuditService_LB" );

		assertThat( getHttpdConfig().getHttpdModjkFile() )
			.as( "Web Servers ModJK file created" )
			.exists();

		assertThat( contentOf( getHttpdConfig().getHttpdModjkFile() ) )
			.as( "Mounts include both singleVm partion suffix and non suffix for enterpise" )
			.contains( "springmvc-showcase" )
			.contains( "Factory2Sample-dev01" );

	}

	/**
	 * 
	 * Quick way to validate applications reporting issues
	 * 
	 */
	@Test
	public void validate_application_in_home_folder ()
			throws JsonProcessingException, IOException {

		if ( !csapApplication.isCompanyVariableConfigured( "test.variables.test-external-application" ) ) {
			logger.info( "company variable not set - skipping test" );
			return;
		}

		File csapApplicationDefinition = new File(
			csapApplication.getCompanyConfiguration( "test.variables.test-external-application", "none" ) );

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isFalse();
		
		
		
		StringBuffer parsingResults = csapApplication.getLastTestParseResults();
		logger.info( "Sntc Parsing Results:\n {}", parsingResults );
		assertThat( parsingResults )
			.as( "SNTC 3 loads with warnings" )
			.doesNotContain( CSAP.CONFIG_PARSE_ERROR )
			.contains( CSAP.CONFIG_PARSE_WARN );

		assertThat( csapApplication.getName() )
			.as( "Capability Name parsed" )
			.isEqualTo( "Smart Services Platform" );

		assertThat( csapApplication.getRootModel()
			.getReleasePackageNames()
			.collect( Collectors.toList() ) )
				.as( "Release Package names" )
				.containsExactly( "IBM",
					"Jolt",
					"Net Authenticate",
					"SC2SNTC Convergence",
					"SFC",
					"SNAS Dev",
					"SNTC and PSS",
					"SSP Shared",
					"Titanium" );

		assertThat( csapApplication.getAllHostsInAllPackagesInCurrentLifecycle() )
			.as( "All Hosts in All Packages in Current Lifecycle" )
			.hasSize( 97 )
			.contains( "localhost", "v01app-dev801" );

		assertThat( csapApplication.getRootModel().getAllPackagesModel()
			.getServiceNameStream()
			.collect( Collectors.toList() ) )
				.as( "All Services in all packages in Current Lifecycle" )
				.hasSize( 120 )
				.contains(
					"SparkMaster01",
					"SparkMaster02",
					"SparkSlave",
					"SparkSlave01",
					"SparkSlave02",
					"SsueMetaSvc",
					"SsueService" );

		assertThat( getHttpdConfig().getHttpdWorkersFile() )
			.as( "Web Servers Worker file created" )
			.exists();

		assertThat( contentOf( getHttpdConfig().getHttpdWorkersFile() ) )
			.contains( "DataReaderSvc_LB", "admin_LB" );

		assertThat( getHttpdConfig().getHttpdModjkFile() )
			.as( "Web Servers ModJK file created" )
			.exists();

		assertThat( contentOf( getHttpdConfig().getHttpdModjkFile() ) )
			.contains(
				"JkMount /DataReaderSvc* DataReaderSvc_LB" );
	}

	/**
	 *
	 * Scenario loads a definition with a non-existant package file. File should
	 * be created using release template
	 *
	 * @throws JsonProcessingException
	 * @throws IOException
	 */
	@Test
	public void load_definition_with_new_package_creates_new_one ()
			throws JsonProcessingException, IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/application_with_new_package.json" ).getPath() );

		File generatedPackageFromTemplate = new File( csapApplicationDefinition.getParentFile(),
			"newReleaseFile.js" );
		logger.info( "Deleting the autogenerated runs as it might exist from previous runs"
				+ generatedPackageFromTemplate.getAbsolutePath() );
		FileUtils.deleteQuietly( generatedPackageFromTemplate );

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();

		assertThat( csapApplication.getName() )
			.as( "Capability Name present" )
			.isEqualTo( "CLuster Config With Missing Package" );

		File newPackageTemplatePath = new File( getClass().getResource( "/org/csap/test/data/newReleaseFile.js" )
			.getPath() );

		assertThat( newPackageTemplatePath.exists() )
			.as( "New package was created from template" )
			.isTrue();

		ServiceInstance newService = csapApplication.getServiceInstance( "spring-PetClinic_8141",
			"changeToYourHost-extension", "changeToYourPackageName" );

		assertThat( newService )
			.as( "Manager was able to load services in new package" )
			.isNotNull();

		assertThat( newService.getServiceName_Port() )
			.as( "Manager was able to load services in new package" )
			.isEqualTo( "spring-PetClinic_8141" );

		newPackageTemplatePath.delete();

		logger.info( "Deleted: " + newPackageTemplatePath.getAbsolutePath() );

	}

	private ObjectMapper jacksonMapper = new ObjectMapper();

	@Test
	public void validate_all_packages_api_call ()
			throws JsonProcessingException, IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/clusterConfigManager.json" ).getPath() );

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();

		logger.info( "parse results: {}", csapApplication.getLastTestParseResults() );

		assertThat( csapApplication.getName() )
			.as( "Capability Name parsed" )
			.isEqualTo( "Desktop Dev2" );

		csapApplication.setTestMode( true );

		logger.info( "Application Definition\n {}",
			jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( csapApplication.getDefinitionForAllPackages() ) );

		// Note that JUNITs use a different default name and location
		assertThat( csapApplication.getDefinitionForAllPackages().at( "/definitions/0/fileName" ).asText() )
			.as( "verify fileName" )
			.isEqualTo( "clusterConfigManager.json" );

		assertThat( csapApplication.getDefinitionForAllPackages().at( "/definitions/0/content" ).asText() )
			.as( "verify content" )
			.contains( "clusterConfigManagerB.json" );

	}

	@Test
	public void load_application_with_release_packages ()
			throws JsonProcessingException, IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/clusterConfigMultiple.json" ).getPath() );

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();

		assertThat( csapApplication.getName() )
			.as( "Capability Name parsed" )
			.isEqualTo( "TestDefinitionWithMultipleServices" );

		assertThat( csapApplication.getRootModel()
			.getReleasePackageNames()
			.collect( Collectors.toList() ) )
				.as( "Release Package names" )
				.containsExactly( "SampleDefaultPackage",
					"Supporting Sample A",
					"Supporting Sample B" );

		assertThat( csapApplication.getModelForHost( "sampleHostA-dev01" ).getReleasePackageName() )
			.as( "Release Packag for host" )
			.isEqualTo(
				"Supporting Sample A" );

		assertThat( csapApplication.getSvcToConfigMap().get( "CsAgent" ) )
			.as( "CsAgents instances found" )
			.hasSize( 3 );

		assertThat( csapApplication.getActiveModel().getServiceInstances( "CsAgent" ).count() )
			.as( "CsAgents instances in active model and lifecycle" )
			.isEqualTo( 2 );

		assertThat( csapApplication.getMutableHostsInActivePackage( csapApplication.activeLifecycleClusterName() ) )
			.as( "All Hosts in Current Lifecycle" )
			.hasSize( 2 )
			.contains(
				"localhost",
				"middlewareA2Host-dev98" );

		assertThat( csapApplication.getActiveModel().getLifeCycleToGroupMap() )
			.as( "Active model Lifecycle to Groups" )
			.hasSize( 2 )
			.containsKeys( "dev", "stage" )
			.containsEntry( "dev",
				new ArrayList<String>(
					Arrays.asList(
						"middlewareA",
						"middlewareA2" ) ) );

		assertThat( csapApplication.getActiveModel().getReleasePackageName() )
			.as( "Release Packag for active model" )
			.isEqualTo( "Supporting Sample A" );

		assertThat( csapApplication.getModel( Application.ALL_PACKAGES ).getLifeCycleToGroupMap().get( "dev" ) )
			.as( "Groups for dev" )
			.hasSize(
				4 )
			.contains(
				"cssp",
				"middlewareA",
				"middlewareA2",
				"middlewareB" );

		assertThat( csapApplication.getServiceInstanceCurrentHost( "CsAgent_8011" ).getMavenRepo() )
			.as( "CsAgent Maven Repo" )
			.isEqualTo(
				"https://repo.maven.apache.org/maven2/" );

		assertThat( csapApplication.getServiceInstanceCurrentHost( "SampleJvmInA_8041" ).getServiceName() )
			.as( "Sample service loaded" )
			.isEqualTo(
				"SampleJvmInA" );

		assertThat( csapApplication.getRootModel().getAllPackagesModel()
			.serviceInstancesInCurrentLifeByName().get( "CsspSample" ) )
				.as( "Service count in all packages" )
				.hasSize( 1 );

		assertThat( csapApplication.getRootModel().getLifeCycleToGroupMap() )
			.as( "Lifecycles to group in root model" )
			.hasSize( 2 )
			.containsKey( "dev" );

		assertThat( csapApplication.getRootModel().getServiceToAllInstancesMap().keySet() )
			.as( "Services in root model" )
			.hasSize( 8 )
			.contains( "CsAgent",
				"CsspSample",
				"FactorySample",
				"RedHatLinux",
				"ServletSample",
				"httpd",
				"oracleDriver",
				"springmvc-showcase" );

		assertThat( csapApplication.getModel( Application.ALL_PACKAGES )
			.getLifeCycleToHostMap().get( Application.getCurrentLifeCycle() ) )
				.as( "Hosts in current lifecycle" )
				.hasSize(
					4 )
				.contains(
					"mainHostA",
					"localhost",
					"middlewareA2Host-dev98",
					"SampleHostB-dev99" );

		assertThat( getHttpdConfig().getHttpdWorkersFile() )
			.as( "Ensure web server files are not created as they are not in active model" )
			.doesNotExist();
	}

	/**
	 *
	 * Scenario: Load definition with sub-packages, on a NON-manger jvm
	 *
	 * Verify: http config files INCLUDE the sub package mount points
	 *
	 * @throws JsonProcessingException
	 * @throws IOException
	 */
	@Test
	public void load_application_with_web_server_enabled ()
			throws JsonProcessingException,
			IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/clusterConfigManager.json" ).getPath() );

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();

		assertThat( csapApplication.getName() )
			.as( "Capability Name parsed" )
			.isEqualTo( "Desktop Dev2" );

		assertThat( csapApplication.getActiveModel().getReleasePackageName() )
			.as( "Release Package" )
			.isEqualTo( "SampleDefaultPackage" );

		assertThat( csapApplication.getSvcToConfigMap().get( "CsAgent" ) )
			.as( "CsAgents instances found" )
			.hasSize( 9 );

		assertThat( csapApplication.getActiveModel().getLifeCycleToGroupMap() )
			.as( "Active model Lifecycle to Groups" )
			.hasSize( 2 )
			.containsKeys( "dev", "stage" )
			.containsEntry( "dev",
				new ArrayList<String>(
					Arrays.asList(
						"WebServer",
						"csspLocal",
						"cssp",
						"middleware" ) ) );

		assertThat( csapApplication.getRootModel().getLifeCycleToGroupMap() )
			.as( "Lifecycles to group in root model" )
			.hasSize( 2 )
			.containsKey( "dev" );

		assertThat( getHttpdConfig().getHttpdWorkersFile() )
			.as( "Web Servers Worker file created" )
			.exists();

		assertThat( contentOf( getHttpdConfig().getHttpdWorkersFile() ) )
			.contains(
				"worker.ServletSample_8041csap-dev01.port=8042" );

		assertThat( contentOf( getHttpdConfig().getHttpdWorkersFile() ) )
			.as( "SpringBoot is excluded unless it has metadata tag" )
			.contains( "worker.ServletSample_8041" )
			.doesNotContain( "worker.SpringBootRest" );

		assertThat( contentOf( getHttpdConfig().getHttpdWorkersFile() ) )
			.as( "SpringBoot will not generate worker._LB" )
			.doesNotContain( "worker._LB" );

		assertThat( getHttpdConfig().getHttpdModjkFile() )
			.as( "Web Servers ModJK file created" )
			.exists();

		assertThat( contentOf( getHttpdConfig().getHttpdModjkFile() ) )
			.contains( "springmvc-showcase",
				"SampleJvmInA" );

	}

	/**
	 *
	 * Application has split personality: Node Manager and Node Agent
	 *
	 * Node Manager mode will not change active lifecycle
	 *
	 * @throws JsonProcessingException
	 * @throws IOException
	 */
	@Test
	public void load_application_using_manager_mode ()
			throws JsonProcessingException, IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/clusterConfigManager.json" ).getPath() );

		// Configure the manager...
		Application.setJvmInManagerMode( true );
		HostStatusManager testStatus = new HostStatusManager(
			new File( getClass().getResource( "/CsAgent_Host_Response.json" ).getPath() ) );

		csapApplication.setHostStatusManager( testStatus );

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();

		assertThat( csapApplication.getName() )
			.as( "Capability Name parsed" )
			.isEqualTo( "Desktop Dev2" );

		assertThat( csapApplication.getModelForHost( "sampleHostA-dev01" ).getReleasePackageName() )
			.as( "Release Packag for host" )
			.isEqualTo(
				"Supporting Sample A" );

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

	}

	/**
	 *
	 * Verify presence of lifecycle meta data
	 *
	 * @throws JsonProcessingException
	 * @throws IOException
	 */
	@Test
	public void load_application_and_verify_lifecycle_settings ()
			throws JsonProcessingException, IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/clusterConfigManager.json" ).getPath() );

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();

		assertThat( csapApplication.getName() )
			.as( "Capability Name parsed" )
			.isEqualTo( "Desktop Dev2" );

		LifeCycleSettings lifeCycleConfig = csapApplication.lifeCycleSettings();

		assertThat( lifeCycleConfig.getNumberWorkerThreads() )
			.as( "Worker Thread Count" )
			.isEqualTo( 4 );

		assertThat( lifeCycleConfig.getNewsJsonArray() )
			.as( "News Items" )
			.hasSize( 1 );

		assertThat( lifeCycleConfig.isMetricsPublication() )
			.as( "metricsPublication enabled" )
			.isTrue();

		assertThat( lifeCycleConfig.getMaxHostCpuLoad( "localhost" ) )
			.as( "Max Host Cpu Load" )
			.isEqualTo( 2 );

		assertThat( lifeCycleConfig.getMaxHostCpu( "localhost" ) )
			.as( "Max Host Cpu " )
			.isEqualTo( 80 );

		assertThat( lifeCycleConfig.getMaxHostCpuIoWait( "localhost" ) )
			.as( "Max Host CPU IO Wait " )
			.isEqualTo( 11 );

		assertThat( lifeCycleConfig.getMetricsPublicationNode() )
			.as( "Publication Endpoints" )
			.hasSize( 2 );

		assertThat( lifeCycleConfig.getAutoStopServiceThreshold( "localhost" ) )
			.as( "autoStopServiceThreshold" )
			.isEqualTo( 1.2 );

		assertThat( lifeCycleConfig.isJmxHeatbeat( "localhost" ) )
			.as( "Jmx Heartbeat enabled localhost" )
			.isTrue();

		assertThat( lifeCycleConfig.isJmxHeatbeat( "csapdb-dev01" ) )
			.as( "Jmx Heartbeat enabled csapdb-dev01" )
			.isFalse();

	}

	@Test
	public void load_application_with_warnings ()
			throws JsonProcessingException, IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/application_with_warnings.json" ).getPath() );

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isFalse();

		logger.info( "Definition results: \n {}", csapApplication.getLastTestParseResults().toString() );

	}

	/**
	 *
	 * Scenario: - load a config file with multiple services using
	 * multiVmPartition cluster type g, some standard - httpd instance has
	 * generateWorkerProperties metadata in cluster.js - verify that mount
	 * points get generated
	 *
	 */
	@Test
	public void load_application_with_multiple_host_partition ()
			throws JsonProcessingException, IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "application-multi-host-partition.json" ).getPath() );

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();

		assertThat( csapApplication.getName() )
			.as( "Capability Name parsed" )
			.isEqualTo( "ParitionExample" );

		assertThat( csapApplication.getLifecycleList() )
			.as( "Lifecycles found" )
			.hasSize( 11 )
			.containsExactly( "dev", "dev-WebServer-1",
				"dev-WebServer-2", "dev-csspLocal-1",
				"dev-csspLocal-2",
				"dev-cssp-1", "dev-middleware-1", "stage",
				"stage-cssp-1", "stage-factory-1",
				"stage-factory-2" );

		assertThat( csapApplication.getSvcToConfigMap().get( "CsAgent" ) )
			.as( "CsAgent Instances found" )
			.hasSize( 11 );

		assertThat( csapApplication.getRootModel().getHostToConfigMap().keySet() )
			.as( "All Hosts found" )
			.hasSize( 11 )
			.contains( "csap-dev01",
				"csap-dev02",
				"csapdb-dev01", "localhost",
				"peter-dev01",
				"peterDummyVmA",
				"peterDummyVmB",
				"xcssp-qa01", "xcssp-qa02",
				"xfactory-qa01",
				"xfactory-qa02" );

		assertThat( csapApplication.getAllHostsInAllPackagesInCurrentLifecycle() )
			.as( "All Hosts in current lifecycle" )
			.hasSize( 7 );

		assertThat( csapApplication.getLifecycleList() )
			.as( "Lifecycles found" )
			.hasSize( 11 )
			.contains( "dev", "dev-WebServer-1", "stage" );

		assertThat( csapApplication.getServiceInstanceAnyPackage( Application.AGENT_NAME_PORT ).getOsProcessPriority() )
			.as( "CsAgent OS Priority" )
			.isEqualTo(
				"-10" );

		assertThat( getHttpdConfig().getHttpdWorkersFile() )
			.as( "Web Servers Worker file created" )
			.exists();

		assertThat( contentOf( getHttpdConfig().getHttpdWorkersFile() ) )
			.as( "Ensure filtered VMs are not in web server config files" )
			.contains( "worker.FactorySample-dev01_LB" )
			.contains( "worker.AuditService-1_LB" )
			.contains( "worker.AuditService-2_LB" )
			.contains(
				"worker.AuditService-1_LB.balance_workers=AuditService-1_8191localhost" );

		assertThat( getHttpdConfig().getHttpdModjkFile() )
			.as( "Web Servers ModJK file created" )
			.exists();

		assertThat( contentOf( getHttpdConfig().getHttpdModjkFile() ) )
			.contains( "springmvc-showcase" )
			.contains( "Factory2Sample-dev01" );

		// assertEquals(
		// "Worker properties should contain only 1 StringUtils", 1,
		// StringUtils.countMatches(workerContents,
		// "worker.AuditService-1_LB.type"));
		//
	}

	/**
	 *
	 * Scenario: - load a config file with multiple services using
	 * singleVmPartition cluster type g, some standard - httpd instance has
	 * generateWorkerProperties metadata in cluster.js - verify that mount
	 * points get generated
	 *
	 */
	@Test
	public void load_application_with_single_host_partition ()
			throws JsonProcessingException, IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "application-single-host-partition.json" ).getPath() );

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();

		assertThat( csapApplication.getName() )
			.as( "Capability Name parsed" )
			.isEqualTo( "Single host Partition" );

		assertThat( csapApplication.getLifecycleList() )
			.as( "Lifecycles found" )
			.hasSize( 10 )
			.containsExactly( "dev", "dev-WebServer-1",
				"dev-WebServer-2", "dev-csspLocal-1",
				"dev-cssp-1", "dev-middleware-1", "stage",
				"stage-cssp-1", "stage-factory-1",
				"stage-factory-2" );

		assertThat( csapApplication.getSvcToConfigMap().get( "CsAgent" ) )
			.as( "CsAgent Instances found" )
			.hasSize( 10 );

		assertThat( csapApplication.getRootModel().getHostToConfigMap().keySet() )
			.as( "All Hosts found" )
			.hasSize( 10 )
			.contains( "csap-dev01",
				"csap-dev02",
				"csapdb-dev01", "localhost",
				"peter-dev01",
				"peterDummyVm-dev99",
				"xcssp-qa01", "xcssp-qa02",
				"xfactory-qa01",
				"xfactory-qa02" );

		assertThat( csapApplication.getAllHostsInAllPackagesInCurrentLifecycle() )
			.as( "All Hosts in current lifecycle" )
			.hasSize( 6 );

		assertThat( csapApplication.getLifecycleList() )
			.as( "Lifecycles found" )
			.hasSize( 10 )
			.contains( "dev", "dev-WebServer-1", "dev-WebServer-2",
				"dev-csspLocal-1",
				"dev-cssp-1", "dev-middleware-1", "stage",
				"stage-cssp-1", "stage-factory-1",
				"stage-factory-2" );

		assertThat( csapApplication.getServiceInstanceAnyPackage( Application.AGENT_NAME_PORT ).getOsProcessPriority() )
			.as( "CsAgent OS Priority" )
			.isEqualTo(
				"-10" );

		assertThat( getHttpdConfig().getHttpdWorkersFile() )
			.as( "Web Servers Worker file created" )
			.exists();

		assertThat( contentOf( getHttpdConfig().getHttpdWorkersFile() ) )
			.as( "Ensure filtered VMs are not in web server config files" )
			.contains( "worker.FactorySample-dev01_LB" )
			.contains( "worker.AuditService-dev99_LB" );

		assertThat( getHttpdConfig().getHttpdModjkFile() )
			.as( "Web Servers ModJK file created" )
			.exists();

		assertThat( contentOf( getHttpdConfig().getHttpdModjkFile() ) )
			.contains( "springmvc-showcase" )
			.contains( "Factory2Sample-dev01" );

	}

	private HttpdIntegration getHttpdConfig () {
		return csapApplication.getHttpdIntegration();
	}
}
