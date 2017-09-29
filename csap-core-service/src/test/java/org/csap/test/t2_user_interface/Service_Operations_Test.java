package org.csap.test.t2_user_interface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.csap.agent.CSAP;
import org.csap.agent.CsapCoreService;
import org.csap.agent.input.http.ui.rest.ServiceRequests;
import org.csap.agent.linux.HostStatusManager;
import org.csap.agent.linux.OutputFileMgr;
import org.csap.agent.model.Application;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.services.ServiceOsManager;
import org.csap.agent.services.SourceControlManager;
import org.csap.test.InitializeLogging;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@RunWith ( SpringRunner.class )
@SpringBootTest ( classes = CsapCoreService.class )
@ActiveProfiles ( profiles = { "junit", "company" } )
public class Service_Operations_Test {

	final static private Logger logger = LoggerFactory.getLogger( Service_Operations_Test.class );

	@Autowired
	private WebApplicationContext wac;

	private MockMvc mockMvc;

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

		this.mockMvc = MockMvcBuilders.webAppContextSetup( this.wac ).build();

		Application.setJvmInManagerMode( false );
		csapApp.setAutoReload( false );

		File buildFolder = new File( csapApp.getBUILD_DIR() );
		logger.info( "Deleting: {}", buildFolder.getAbsolutePath() );
		FileUtils.deleteQuietly( buildFolder );
	}

	@After
	public void tearDown ()
			throws Exception {
	}

	ObjectMapper jacksonMapper = new ObjectMapper();

	@Test
	public void activity_count_from_event_service ()
			throws Exception {

		// First load a config with supporting services
		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/clusterConfigMultiple.json" ).getPath() );
		String message = "Loading a working definition with multiple supporting services: "
				+ csapApplicationDefinition.getAbsolutePath();

		logger.info( InitializeLogging.TC_HEAD + message );

		if ( !csapApp.isCompanyVariableConfigured( "test.variables.user" ) ) {
			logger.info( "company variable not set - skipping test" );
			return;
		}

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" ).isTrue();

		message = "Hitting /getServicesInLifeCycleSummary "
				+ "releasePackage: SampleDefaultPackage";
		logger.info( InitializeLogging.TC_HEAD + message );

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform( post( CsapCoreService.SERVICE_URL + "/activityCount" )
			.accept( MediaType.APPLICATION_JSON ) );

		// But you could do full parsing of the Json result if needed
		String responseText = resultActions
			.andExpect( status().isOk() )
			.andReturn()
			.getResponse()
			.getContentAsString();

		ObjectNode responseJson = (ObjectNode) jacksonMapper.readTree( responseText );

		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter()
			.writeValueAsString( responseJson ) );

		assertThat( responseJson.at( "/count" ).asInt() )
			.as( "activityCount" )
			.isGreaterThanOrEqualTo( 0 );

	}

	@Test
	public void get_service_report_from_analytics_service ()
			throws Exception {

		// First load a config with supporting services
		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/clusterConfigMultiple.json" ).getPath() );
		String message = "Loading a working definition with multiple supporting services: "
				+ csapApplicationDefinition.getAbsolutePath();
		logger.info( InitializeLogging.TC_HEAD + message );

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" ).isTrue();

		if ( !csapApp.isCompanyVariableConfigured( "test.variables.user" ) ) {
			logger.info( "company variable not set - skipping test" );
			return;
		}

		message = "Hitting /report ";
		logger.info( InitializeLogging.TC_HEAD + message );

		ResultActions resultActions = mockMvc.perform( post( CsapCoreService.SERVICE_URL + "/report" )
			.param( "appId", csapApp.getCompanyConfiguration( "test.variables.testAppId", "" ) )
			.param( "project", "CSAP Engineering" )
			.param( "report", "service" )
			.param( "life", "dev" )
			.param( "numDays", "1" )
			.accept( MediaType.APPLICATION_JSON ) );

		// But you could do full parsing of the Json result if needed
		String responseText = resultActions
			.andExpect( status().isOk() )
			.andReturn()
			.getResponse()
			.getContentAsString();

		ObjectNode responseJson = (ObjectNode) jacksonMapper.readTree( responseText );

		logger.info( "report: {}", CSAP.jsonPrint( jacksonMapper, responseJson ) );

		assertThat( responseJson.at( "/numDaysAvailable" ).asInt() )
			.as( "Number of days available" )
			.isGreaterThan( 1 );

	}

	/**
	 *
	 * Scenario: load multiple package cluster. Active model will be child....
	 *
	 * @throws Exception
	 */
	@Test
	public void verify_getServicesInLifeCycleSummary_for_package_agent ()
			throws Exception {

		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/clusterConfigMultiple.json" ).getPath() );

		String message = "Loading a working definition with multiple supporting services: "
				+ csapApplicationDefinition.getAbsolutePath();

		logger.info( InitializeLogging.TC_HEAD + message );

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();

		message = "Hitting /getServicesInLifeCycleSummary releasePackage: Supporting Sample A";
		logger.info( InitializeLogging.TC_HEAD + message );

		// mock does much validation.....
		ResultActions resultActions = mockMvc
			.perform(
				post( CsapCoreService.SERVICE_URL + "/getServicesInLifeCycleSummary" )
					.param( "blocking", "true" )
					.param( CSAP.PACKAGE_PARAM, "Supporting Sample A" )
					.accept( MediaType.APPLICATION_JSON ) );

		// But you could do full parsing of the Json result if needed
		String responseText = resultActions
			.andExpect( status().isOk() )
			.andReturn()
			.getResponse()
			.getContentAsString();

		ObjectNode serviceSummaryJson = (ObjectNode) jacksonMapper.readTree( responseText );

		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter()
			.writeValueAsString( serviceSummaryJson ) );

		assertThat( serviceSummaryJson.at( "/totalServices" ).asInt() )
			.as( "Service in definition" )
			.isEqualTo( 3 );

		assertThat( serviceSummaryJson.at( "/hostStatus/localhost/cpuCount" ).asInt() )
			.as( "Cpu count" )
			.isGreaterThanOrEqualTo( 2 );

		assertThat( serviceSummaryJson.at( "/hostStatus/localhost/vmLoggedIn/0" ).asText() )
			.as( "rtp found in vm messages" )
			.contains( "rtp" );

		List<String> agentErrors = jacksonMapper.readValue( serviceSummaryJson.get( "errors" ).toString(),
			new TypeReference<List<String>>() {
			} );

		assertThat( Arrays.asList( agentErrors ).toString() )
			.as( "agentErrors contains: Running in single node mode" )
			.contains( "Running in single node mode" );
		//
		logger.info( "Errors: {}", agentErrors.toString().replaceAll( ",", ",\n" ) );
		// errer

		assertThat( agentErrors )
			.contains( "localhost: CsAgent: Disk Space: 73Mb, Alert threshold: 50Mb" );

		List<String> clusters = jacksonMapper.readValue(
			serviceSummaryJson.get( "clusters" ).toString(),
			new TypeReference<List<String>>() {
			} );

		assertThat( clusters )
			.as( "Clusters in definition" )
			.hasSize( 3 );

	}

	@Test
	public void getServicesInLifeCycleSummaryAllPackages ()
			throws Exception {

		// First load a config with supporting services

		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/clusterConfigMultiple.json" ).getPath() );

		String message = "Loading a working definition with multiple supporting services: "
				+ csapApplicationDefinition.getAbsolutePath();
		logger.info( InitializeLogging.TC_HEAD + message );

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" ).isTrue();

		message = "Hitting /getServicesInLifeCycleSummary "
				+ "releasePackage: ALL_PACKAGES";
		logger.info( InitializeLogging.TC_HEAD + message );

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform( post( CsapCoreService.SERVICE_URL + "/getServicesInLifeCycleSummary" )
			.param( "block", "false" ).param( "releasePackage", Application.ALL_PACKAGES )
			.accept( MediaType.APPLICATION_JSON ) );

		// But you could do full parsing of the Json result if needed
		String responseText = resultActions
			.andExpect( status().isOk() )
			.andReturn()
			.getResponse()
			.getContentAsString();

		ObjectNode responseJson = (ObjectNode) jacksonMapper.readTree( responseText );

		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter()
			.writeValueAsString( responseJson ) );

		assertEquals( "Found total services", 3, responseJson.get( "totalServices" ).asInt() );

		assertTrue( "CpuCount >= 2", responseJson.at( "/hostStatus/localhost/cpuCount" ).asInt() >= 2 );

		List<String> errors = jacksonMapper.readValue(
			responseJson.get( "errors" ).toString(),
			new TypeReference<List<String>>() {
			} );
		// errer
		assertTrue( "Found errors value", 6 <= errors.size() );

		List<String> clusters = jacksonMapper.readValue(
			responseJson.get( "clusters" ).toString(),
			new TypeReference<List<String>>() {
			} );

		assertEquals( "Found clusters value", 7,
			clusters.size() );

	}

	/**
	 *
	 * Scenario: Load a multi file package, and get service summary using sub
	 * package filter and cluster filter
	 *
	 * @throws Exception
	 */
	@Test
	public void getServicesInLifeCycleSummaryMultiple ()
			throws Exception {

		// First load a config with supporting services

		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/clusterConfigMultiple.json" ).getPath() );

		String message = "Loading a working definition with multiple supporting services: "
				+ csapApplicationDefinition.getAbsolutePath();
		logger.info( InitializeLogging.TC_HEAD + message );

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" ).isTrue();

		message = "Invoking getServicesInLifeCycleSummary..... returns JSON";
		logger.info( InitializeLogging.TC_HEAD + message );

		//
		// First get the default list
		//
		ResultActions resultActions = mockMvc.perform(
			post( CsapCoreService.SERVICE_URL + "/getServicesInLifeCycleSummary" )
				.param( "block", "false" ).param( "releasePackage", "SampleDefaultPackage" )
				.accept( MediaType.APPLICATION_JSON ) );

		// But you could do full parsing of the Json result if needed
		JsonNode responseJsonNode = jacksonMapper.readTree( resultActions.andReturn().getResponse()
			.getContentAsString() );

		logger.info( "result:\n"
				+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
					responseJsonNode ) );

		MvcResult mvcResult = resultActions.andExpect( status().isOk() )
			.andExpect( content().contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) ).andReturn();

		assertTrue( message,
			responseJsonNode.get( "hostStatus" ).get( "localhost" ).get( "cpuCount" ) != null );
		assertTrue( message, responseJsonNode.get( "hostStatus" ).get( "localhost" ).get( "cpuCount" )
			.asInt() >= 2 );

		//
		// Now get the release package list
		//
		message = "Invoking getServicesInLifeCycleSummary with SSO Filter..... returns JSON";
		resultActions = mockMvc.perform(
			post( CsapCoreService.SERVICE_URL + "/getServicesInLifeCycleSummary" )
				.param( "block", "false" ).param( "releasePackage", "Supporting Sample A" )
				.accept( MediaType.APPLICATION_JSON ) );

		// But you could do full parsing of the Json result if needed
		responseJsonNode = jacksonMapper.readTree( resultActions.andReturn().getResponse()
			.getContentAsString() );
		logger.info( "result:\n"
				+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
					responseJsonNode ) );

		mvcResult = resultActions.andExpect( status().isOk() )
			.andExpect( content().contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) ).andReturn();

		assertTrue( "Verifying service is present",
			responseJsonNode.get( "servicesTotal" ).get( "SampleJvmInA" ) != null );
		assertEquals( "Verifying we found the service totals for SampleJvmInA", 1, responseJsonNode
			.get( "servicesTotal" ).get( "SampleJvmInA" ).asInt() );

		//
		// Now get the release package list using cluster filter. Since we are
		// running in standalone - it should be empty
		//
		message = "Invoking getServicesInLifeCycleSummary with SSO Filter..... returns JSON";
		resultActions = mockMvc.perform(
			post( CsapCoreService.SERVICE_URL + "/getServicesInLifeCycleSummary" )
				.param( "block", "false" )
				.param( "releasePackage", "Supporting Sample A" )
				.param( "cluster", "dev-middlewareA2-1" )
				.accept( MediaType.APPLICATION_JSON ) );

		// But you could do full parsing of the Json result if needed
		responseJsonNode = jacksonMapper.readTree( resultActions.andReturn().getResponse()
			.getContentAsString() );

		logger.info( "result:\n"
				+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
					responseJsonNode ) );

		mvcResult = resultActions
			.andExpect( status().isOk() )
			.andExpect( content().contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) ).andReturn();

		assertEquals( "Verifying standalone mode that sup package services are not active", 0,
			responseJsonNode.get( "totalHostsActive" ).asInt() );

	}

	@Test
	public void getServicesMultipleManager ()
			throws Exception {

		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/clusterConfigManager.json" ).getPath() );

		String message = "Loading a working definition with multiple supporting services: "
				+ csapApplicationDefinition.getAbsolutePath();

		logger.info( InitializeLogging.TC_HEAD + message );

		// Configure the manager...
		Application.setJvmInManagerMode( true );
		HostStatusManager testHostStatusManager = new HostStatusManager(
			new File(
				getClass()
					.getResource( "/CsAgent_Host_Response.json" )
					.getPath() ) );

		csapApp.setHostStatusManager( testHostStatusManager );

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" ).isTrue();

		message = "Invoking getServicesInLifeCycleSummary..... returns JSON";
		logger.info( InitializeLogging.TC_HEAD + message );

		//
		// First get the default list
		//
		ResultActions resultActions = mockMvc.perform(
			post( CsapCoreService.SERVICE_URL + "/getServicesInLifeCycleSummary" )
				.param( "blocking", "true" ).param( "releasePackage", "SampleDefaultPackage" )
				.accept( MediaType.APPLICATION_JSON ) );

		// But you could do full parsing of the Json result if needed
		JsonNode responseJsonNode = jacksonMapper.readTree( resultActions.andReturn().getResponse()
			.getContentAsString() );
		logger.info( "/getServicesInLifeCycleSummary result:\n"
				+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
					responseJsonNode ) );

		MvcResult mvcResult = resultActions.andExpect( status().isOk() )
			.andExpect( content().contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) ).andReturn();
		//
		assertTrue( message,
			responseJsonNode.get( "hostStatus" ).get( "localhost" ).get( "cpuCount" ) != null );
		assertTrue( message, responseJsonNode.get( "hostStatus" ).get( "localhost" ).get( "cpuCount" )
			.asInt() >= 2 );

		assertTrue( message,
			responseJsonNode.get( "hostStatus" ).get( "csap-dev01" ).get( "cpuCount" ) != null );
		assertTrue( message, responseJsonNode.get( "hostStatus" ).get( "csap-dev01" ).get( "cpuCount" )
			.asInt() >= 2 );

		assertEquals( "Factory2Sample instance count", 2,
			responseJsonNode.get( "servicesTotal" ).get( "Factory2Sample" ).asInt() );

		//
		// Now get the service instance filtered by default release package
		//
		message = "Invoking getServiceInstances with releaseFilter ..... returns JSON";
		logger.info( InitializeLogging.TC_HEAD + message );

		resultActions = mockMvc.perform(
			post( CsapCoreService.SERVICE_URL + "/getServiceInstances" ).param( "blocking", "false" )
				.param( "serviceName", "Factory2Sample" )
				.param( "releasePackage", "SampleDefaultPackage" )
				.accept( MediaType.APPLICATION_JSON ) );

		// But you could do full parsing of the Json result if needed
		responseJsonNode = jacksonMapper.readTree( resultActions.andReturn().getResponse()
			.getContentAsString() );
		logger.info( "/getServiceInstances result:\n"
				+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
					responseJsonNode ) );

		mvcResult = resultActions
			.andExpect( status().isOk() )
			.andExpect( content().contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) )
			.andReturn();

		assertEquals( "Found at least 1 service status", 2, responseJsonNode.get( "serviceStatus" )
			.size() );

		assertEquals( "SampleJvmInA instance count", "localhost",
			responseJsonNode.get( "serviceStatus" ).get( 0 ).get( "host" ).asText() );

		//
		// Now get the service summary filtered by release package
		//
		message = "Invoking getServicesInLifeCycleSummary with releaseFilter ..... returns JSON";
		logger.info( InitializeLogging.TC_HEAD + message );

		resultActions = mockMvc.perform(
			post( CsapCoreService.SERVICE_URL + "/getServicesInLifeCycleSummary" )
				.param( "blocking", "false" )
				.param( "releasePackage", "Supporting Sample A" )
				.accept( MediaType.APPLICATION_JSON ) );

		// But you could do full parsing of the Json result if needed
		responseJsonNode = jacksonMapper.readTree(
			resultActions.andReturn().getResponse().getContentAsString() );

		logger.info( "/getServicesInLifeCycleSummary result:\n"
				+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
					responseJsonNode ) );

		mvcResult = resultActions
			.andExpect( status().isOk() )
			.andExpect( content().contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) ).andReturn();

		assertTrue( message,
			responseJsonNode.get( "hostStatus" ).get( "sampleHostA-dev01" ).get( "cpuCount" ) != null );
		assertTrue(
			message,
			responseJsonNode.get( "hostStatus" ).get( "sampleHostA-dev01" ).get( "cpuCount" ).asInt() >= 2 );

		assertEquals( "SampleJvmInA instance count", 1,
			responseJsonNode.get( "servicesTotal" ).get( "SampleJvmInA" ).asInt() );

		//
		// Now get the service summary filtered by release package and cluster
		// name
		//
		message = "Invoking getServicesInLifeCycleSummary with releaseFilter ..... returns JSON";
		logger.info( InitializeLogging.TC_HEAD + message );

		resultActions = mockMvc.perform(
			post( CsapCoreService.SERVICE_URL + "/getServicesInLifeCycleSummary" )
				.param( "blocking", "false" )
				.param( "releasePackage", "Supporting Sample A" )
				.param( "cluster", "dev-middlewareA2-1" )
				.accept( MediaType.APPLICATION_JSON ) );

		// But you could do full parsing of the Json result if needed
		responseJsonNode = jacksonMapper.readTree(
			resultActions.andReturn().getResponse()
				.getContentAsString() );

		logger.info( "/getServicesInLifeCycleSummary result:\n"
				+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
					responseJsonNode ) );

		mvcResult = resultActions
			.andExpect( status().isOk() )
			.andExpect( content().contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) ).andReturn();

		assertTrue( message, responseJsonNode.get( "hostStatus" ).get( "sampleHostA-dev01" ) == null );

		assertTrue( message,
			responseJsonNode.get( "hostStatus" ).get( "sampleHostA2-dev02" ).get( "cpuCount" )
				.asInt() >= 2 );

		assertEquals( "SampleJvmInA instance count", 1,
			responseJsonNode.get( "servicesTotal" ).get( "SampleJvmInA2" ).asInt() );

		//
		// Now get the service instance filtered by release package
		//
		message = "Invoking getServiceInstances with releaseFilter ..... returns JSON";
		logger.info( InitializeLogging.TC_HEAD + message );

		resultActions = mockMvc.perform(
			post( CsapCoreService.SERVICE_URL + "/getServiceInstances" )
				.param( "blocking", "false" )
				.param( "serviceName", "SampleJvmInA" )
				.param( "releasePackage", "Supporting Sample A" ).accept( MediaType.APPLICATION_JSON ) );

		// But you could do full parsing of the Json result if needed
		responseJsonNode = jacksonMapper.readTree( resultActions.andReturn().getResponse()
			.getContentAsString() );

		logger.info( "/getServiceInstances result:\n"
				+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
					responseJsonNode ) );

		mvcResult = resultActions
			.andExpect( status().isOk() )
			.andExpect( content().contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) ).andReturn();

		assertTrue( "Found at least 1 service status",
			responseJsonNode.get( "serviceStatus" ).size() >= 1 );

		assertEquals( "SampleJvmInA instance count", "sampleHostA-dev01",
			responseJsonNode.get( "serviceStatus" ).get( 0 ).get( "host" ).asText() );

		//
		// Now get the service instance using all package
		//
		message = "Invoking getServicesInLifeCycleSummary with releaseFilter ..... returns JSON";
		logger.info( InitializeLogging.TC_HEAD + message );

		resultActions = mockMvc.perform( post( CsapCoreService.SERVICE_URL + "/getServicesInLifeCycleSummary" )
			.param( "blocking", "false" )
			.param( "releasePackage", Application.ALL_PACKAGES )
			.accept( MediaType.APPLICATION_JSON ) );

		// But you could do full parsing of the Json result if needed
		responseJsonNode = jacksonMapper.readTree(
			resultActions.andReturn().getResponse()
				.getContentAsString() );

		logger.info( "ALL /getServicesInLifeCycleSummary result:\n"
				+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
					responseJsonNode ) );

		mvcResult = resultActions
			.andExpect( status().isOk() )
			.andExpect( content().contentTypeCompatibleWith( MediaType.APPLICATION_JSON ) ).andReturn();

		logger.info( "All Services :\n"
				+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
					responseJsonNode.get( "servicesTotal" ) ) );

		assertEquals( " Total Services found: ", 18, responseJsonNode.get( "servicesTotal" ).size() );
		assertEquals( " Total CsAgent instances", 8,
			responseJsonNode.get( "servicesTotal" ).get( "CsAgent" ).asInt() );

		// logger.info("All Hosts :\n"
		// + jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
		// responseJsonNode.get("hostStatus")));
		assertEquals( message, 8, responseJsonNode.get( "hostStatus" ).size() );

		assertTrue( message, responseJsonNode.get( "hostStatus" ).get( "sampleHostA-dev01" ) != null );

		assertTrue( message,
			responseJsonNode.get( "hostStatus" ).get( "sampleHostA2-dev02" ).get( "cpuCount" )
				.asInt() >= 2 );

		assertEquals( "SampleJvmInA instance count", 1,
			responseJsonNode.get( "servicesTotal" ).get( "SampleJvmInA2" ).asInt() );

	}

	@Inject
	Application csapApp;

	@Inject
	ServiceOsManager serviceManager;
	// ServiceRequests serviceController;

	@Inject
	private StandardPBEStringEncryptor encryptor;

	@Test
	public void build_service_using_git_no_auth_required ()
			throws Exception {

		File csapApplicationDefinition = new File(getClass().getResource( "/org/csap/test/data/DEFAULT_APPLICATION.json" ).getPath() );

		String message = "Loading a working definition with multiple supporting services: "
				+ csapApplicationDefinition.getAbsolutePath();
		logger.info( InitializeLogging.TC_HEAD + message );

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" ).isTrue();

		String svcName = "CsapSimple_8241";
		logger.debug( "git Service: {}", csapApp.getServiceInstanceCurrentHost( svcName ) );
		assertThat( csapApp.getServiceInstanceCurrentHost( svcName ) ).isNotNull();

		String scmCommand = "-Dmaven.test.skip=true clean package deploy";
		String javaOpts = "-Xms128M -Xmx128M -XX:MaxPermSize=128m";
		String scmUserid = ""; // general service - use empty userid to bypass
								// authentication checks
		String scmPass = "";
		String mavenDeployArtifact = null;
		String targetScpHosts = "";
		String hotDeploy = null;
		String scmBranch = SourceControlManager.GIT_NO_BRANCH;
		OutputFileMgr outputFm = new OutputFileMgr( csapApp.getProcessingDir(), "/"
				+ svcName + "_testDeploy" );
		
		ServiceInstance serviceInstance = csapApp.getServiceInstanceCurrentHost( svcName ) ;

		boolean sourceCheckoutOk = serviceManager.deployService(
			serviceInstance, null, "ms" + System.currentTimeMillis(), "junit",
			scmUserid, encryptor.encrypt( scmPass ),
			scmBranch, mavenDeployArtifact,
			scmCommand, targetScpHosts, hotDeploy, javaOpts, ServiceInstance.CSSP3, outputFm );

		assertThat( sourceCheckoutOk )
			.as( "Source Checked out ok" )
			.isTrue();

		logger.info( "serviceManager.deployService output: {}", outputFm.getOutputFile().getAbsolutePath() );

		assertThat( outputFm.getOutputFile() )
			.as( "buildOutput file found" )
			.exists()
			.isFile();

		String buildOutput = FileUtils.readFileToString( outputFm.getOutputFile() );
		assertThat( buildOutput )
			.as( "Not updating an existing branch" )
			.doesNotContain( "Updating existing branch on git repository" );
		

		File serviceBuildFolder=new File( csapApp.getBUILD_DIR(), svcName ) ;
		File buildPom = new File( serviceBuildFolder,  serviceInstance.getScmBuildLocation()+ "/pom.xml" );

		logger.info( "Verifying maven build file: {}", buildPom.getAbsolutePath() );

		assertThat( buildPom )
			.as( "Pom file found" )
			.exists().isFile();
		
		

		logger.info( "Deleting: {}", serviceBuildFolder.getAbsolutePath()  );
		FileUtils.deleteQuietly( serviceBuildFolder );

	}

	/**
	 *
	 * Scenario: Issue rebuild, ensure password is encrypted
	 *
	 * @throws Exception
	 */
	@Ignore
	public void build_service_using_svn ()
			throws Exception {

		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/clusterConfigMultiple.json" ).getPath() );

		String message = "Loading a working definition with multiple supporting services: "
				+ csapApplicationDefinition.getAbsolutePath();
		logger.info( InitializeLogging.TC_HEAD + message );

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" ).isTrue();

		//
		ServiceInstance testInstance = csapApp.getServiceInstanceCurrentHost( "SampleJvmInA_8041" );

		assertTrue( "Found SampleJvmInA_8041", testInstance != null );
		File deployLogFile = new File( csapApp.getProcessingDir(), testInstance.getServiceName_Port()
				+ ServiceRequests.DEPLOY_OP + ".log" );
		FileUtils.deleteQuietly( deployLogFile );

		assertTrue( "Log file does not exist", deployLogFile.exists() == false );

		message = "Hitting /rebuildServer "
				+ "releasePackage: SampleDefaultPackage. service: " + testInstance.getServiceName_Port();

		logger.info( InitializeLogging.TC_HEAD + message );

		String testPassword = "shouldNeverBeInLogs";

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
			post( CsapCoreService.SERVICE_URL + "/rebuildServer" )
				.param( "scmUserid", "testUser" )
				.param( "scmPass", testPassword )
				.param( "scmBranch", "false" )
				.param( "hostName", "localhost" )
				.param( "serviceName", "SampleJvmInA_8041" )
				.accept( MediaType.APPLICATION_JSON ) );

		// But you could do full parsing of the Json result if needed
		String buildResponse = resultActions
			.andExpect( status().isOk() )
			.andReturn().getResponse().getContentAsString();

		logger.info( "result:\n" + buildResponse + "\n deployLogFile: " + deployLogFile.getAbsolutePath() );

		// RaceCondition on async build
		// assertTrue("Assert Deploy Log file created: ",
		// deployLogFile.exists());
		// junitLogs will be relative to run dir
		File jvmLogFile = new File( "target/logs/junit-logs.txt" );
		String jvmLogs = FileUtils.readFileToString( jvmLogFile );
		// We removed the password from the build output

		assertFalse( "Log File contains plain text password: ", jvmLogs.contains( testPassword ) );

		// Builds are done on background thread because they can take a while.
		// Junit needs to poll waiting for completion
		//
		String deployLogs = FileUtils.readFileToString( deployLogFile );
		int numAttempts = 0;
		while (numAttempts++ < 20) {
			deployLogs = FileUtils.readFileToString( deployLogFile );
			if ( deployLogs.contains( OutputFileMgr.OUTPUT_COMPLETE_TOKEN ) ) {
				break;
			}
			logger.info( "Sleeping for build attempt to complete" );
			Thread.sleep( 1000 );
		}

		assertTrue( "Build with invalid password: ", deployLogs.contains( "__ERROR: SVN Failure" ) );

	}

}
