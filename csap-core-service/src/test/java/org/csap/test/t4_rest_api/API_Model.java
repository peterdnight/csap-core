package org.csap.test.t4_rest_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.csap.agent.CSAP;
import org.csap.agent.CsapCoreService;
import org.csap.agent.input.http.api.AgentApi;
import org.csap.agent.input.http.api.ModelApi;
import org.csap.agent.model.Application;
import org.csap.agent.stats.ServiceCollector;
import org.csap.test.InitializeLogging;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


@RunWith(SpringRunner.class)
@SpringBootTest(classes = CsapCoreService.class)
@ActiveProfiles ( profiles = { "junit", "company" } )
public class API_Model {

	final static private Logger logger = LoggerFactory.getLogger( API_Model.class );

	@Autowired
	private WebApplicationContext wac;

	private MockMvc mockMvc;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		InitializeLogging.printTestHeader( logger.getName() );
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Inject
	Application csapApp;
	

	@Inject
	AgentApi agentApi;

	ObjectMapper jacksonMapper = new ObjectMapper();

	@Before
	public void setUp() throws Exception {
		logger.info("Deleting: {}",
				csapApp.getHttpdIntegration().getHttpdWorkersFile().getParentFile().getAbsolutePath() );

		Application.setJvmInManagerMode( false );
		csapApp.setAutoReload( false );

		FileUtils.deleteQuietly( csapApp.getHttpdIntegration().getHttpdWorkersFile().getParentFile() );

		File csapApplicationDefinition = new File(
				getClass().getResource( "/org/csap/test/data/test_application_model.json" ).getPath() );

		logger.info("Loading application: {}" + csapApplicationDefinition );


		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" ).isTrue();
		StringBuffer parsingResults = csapApp.getLastTestParseResults() ;

		this.mockMvc = MockMvcBuilders.webAppContextSetup( this.wac ).build();
		csapApp.setTestMode( true );
	}

	@After
	public void tearDown() throws Exception {
	}

	/**
	 *
	 * Scenario: - test cluster def API
	 *
	 * @throws IOException
	 * @throws JsonProcessingException
	 *
	 */
	@Test
	public void validate_api_model_clusters() throws Exception {


		logger.info(InitializeLogging.TC_HEAD );

		// mock does much validation.....
		ResultActions resultActions = mockMvc.perform(
				post( CsapCoreService.API_URL + ModelApi.MODEL_CONTEXT + "/clusters" )
				.accept( MediaType.APPLICATION_JSON ) );

		// But you could do full parsing of the Json result if needed
		String responseText = resultActions
				.andExpect( status().isOk() )
				.andReturn()
				.getResponse()
				.getContentAsString();

		ObjectNode clusterJson = (ObjectNode) jacksonMapper.readTree( responseText );

		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter()
				.writeValueAsString( clusterJson ) );

		List<String> clusters = jacksonMapper.readValue(
				clusterJson.get( "all" ).toString(), List.class );

		logger.info( "allCluster: {}", clusters );
		assertThat( clusters )
				.as( "clusters" )
				.hasSize( 5 )
				.contains( "csapdb-dev01", "csap-dev01", "csap-dev02", "peter-dev01", "localhost" );

	}
//
//	/**
//	 *
//	 * Scenario: - test getHostsByPackage API
//	 *
//	 * @throws IOException
//	 * @throws JsonProcessingException
//	 *
//	 */
//	@Test
//	public void getHostsByPackage() throws JsonProcessingException, IOException {
//
//		String responseJson = agentResource.getHostsByPackage();
//
//		// Print it out for generating tests using jackson
//		ArrayNode clusterDef = (ArrayNode) jacksonMapper.readTree( responseJson );
//		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( clusterDef ) );
//
//		// Use jsonPath apis for quick processing for tests
//		assertEquals( "Server Count", 1, clusterDef.size() );
//
//		assertEquals( "Found SampleDefaultPackage", "SampleDefaultPackage",
//				clusterDef.at( "/0/packageName" ).asText() );
//	}
//
//	@Test
//	public void get_maven_versions() throws JsonProcessingException, IOException {
//
//		// Print it out for generating tests using jackson
//		ArrayNode responseJson = (ArrayNode) jacksonMapper.readTree( agentResource.getMaven() );
//		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( responseJson ) );
//
//		ArrayList<String> mavenStrings = jacksonMapper.readValue(
//				responseJson.traverse(),
//				new TypeReference<ArrayList<String>>() {
//		} );
//
//		assertThat( mavenStrings )
//				.as( "Service Count" )
//				.hasSize( 7 )
//				.contains( "com.your.group:SampleDataLoader:1.0.0:zip" );
//	}
//
//	@Test
//	public void service_url_for_lowest_load_host() throws JsonProcessingException, IOException {
//
//		File csapApplicationDefinition = new File(
//				getClass().getResource( "/org/csap/test/data/clusterConfigManager.json" ).getPath() );
//
//		String message = "Loading a manager definition with multiple supporting services: "
//				+ csapApplicationDefinition.getAbsolutePath();
//		logger.info( Boot_Container.TC_HEAD + message );
//
//		// Configure the manager...
//		Application.setJvmInManagerMode( true );
//		assertThat( csapApp.isCleanParseConfig( false, csapApplicationDefinition ) )
//				.as( "No Errors or warnings" )
//				.isTrue();
//
//		// trigger host collection
//		ArrayList<String> hostList = new ArrayList<String>(
//				csapApp.getAllHostsInAllPackagesInCurrentLifecycle() );
//		HostStatusManager testStatus = new HostStatusManager( csapApp, 2, 5000,
//				hostList );
//		csapApp.setHostStatusManager( testStatus );
//		testStatus.executeQueriesInParallelWithMaxWait( null );
//
//		// csapApp.getHostStatusManager().executeQueriesInParallelWithMaxWait(null)
//		// ;
//		String responseJson = agentResource.getServiceUrlWithLowLoad( "CsAgent" );
//
//		logger.info( "getServiceUrlWithLowLoad:   " + responseJson );
//		assertThat( responseJson )
//				.as( "Found http:// - will vary based on load in labs" )
//				.contains( "http://" );
//	}
//
//	@Test
//	public void service_urls_for_service() throws JsonProcessingException, IOException, InterruptedException {
//
//		File csapApplicationDefinition = new File(
//				getClass().getResource( "/org/csap/test/data/clusterConfigManager.json" ).getPath() );
//
//		String message = "Loading a manager definition with multiple supporting services: "
//				+ csapApplicationDefinition.getAbsolutePath();
//		logger.info( Boot_Container.TC_HEAD + message );
//
//		// Configure the manager...
//		Application.setJvmInManagerMode( true );
//		assertThat( csapApp.isCleanParseConfig( false, csapApplicationDefinition ) )
//				.as( "No Errors or warnings" )
//				.isTrue();
//
//		// trigger host collection
//		ArrayList<String> hostList = new ArrayList<String>(
//				csapApp.getAllHostsInAllPackagesInCurrentLifecycle() );
//		HostStatusManager testStatus = new HostStatusManager( csapApp, 2, 5000,
//				hostList );
//		csapApp.setHostStatusManager( testStatus );
//		testStatus.executeQueriesInParallelWithMaxWait( null );
//
//		// csapApp.getHostStatusManager().executeQueriesInParallelWithMaxWait(null)
//		// ;
//		String responseJson = agentResource.getUrlsForService( "ServletSample" );
//
//		List<String> serviceUrls = jacksonMapper.readValue(
//				responseJson, new TypeReference<List<String>>() {
//		} );
//		logger.info( "getUrlsForService:   " + responseJson );
//		assertThat( serviceUrls )
//				.hasSize( 2 )
//				.as( "service urls for service" )
//				.contains( "http://testhost.yourcompany.com:8041/ServletSample",
//						"http://csap-dev02.yourcompany.com:8041/ServletSample" );
//
//		logger.info( "agentResource.vmStatus: ", agentResource.vmStatus() );
//
//		// local host query times out in junit as everything is mocked.
//		// responseJson = agentResource.getUrlsForService( "SpringBootRest" );
//		// serviceUrls = jacksonMapper
//		// .readValue( responseJson, new TypeReference<List<String>>() {
//		// } );
//		// logger.info( "getUrlsForService: " + responseJson );
//		// assertThat( serviceUrls )
//		// .hasSize( 1 )
//		// .as( "service urls for SpringBoot without web integration" )
//		// .contains( "http://localhost:8191/" );
//	}
//
//	@Test
//	public void getMetricsConfig() throws JsonProcessingException, IOException {
//
//		String responseJson = agentResource.getMetricsConfig();
//
//		// Print it out for generating tests using jackson
//		JsonNode metricsNode = (JsonNode) jacksonMapper.readTree( responseJson );
//		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( metricsNode ) );
//
//		// Use jsonPath apis for quick processing for tests
//		ArrayNode emailIds = (ArrayNode) metricsNode.at( "/email" );
//		assertEquals( "Email Count", 2, emailIds.size() );
//
//		assertTrue( "Found yourcompany in email", emailIds.get( 0 ).asText().contains( "yourcompany.com" ) );
//		assertTrue( "Found credentials", metricsNode.has( "credentials" ) );
//		assertEquals( "Creds userid", "$csapUser1", metricsNode.at( "/credentials/user" ).asText() );
//	}
//
//	/**
//	 *
//	 * Scenario: - test getSummary API
//	 *
//	 * @throws IOException
//	 * @throws JsonProcessingException
//	 *
//	 */
//	@Test
//	public void getSummary() throws JsonProcessingException, IOException {
//
//		File csapApplicationDefinition = new File(
//				getClass().getResource( "/org/csap/test/data/clusterConfigManager.json" ).getPath() );
//
//		String message = "Loading a manager definition with multiple supporting services: "
//				+ csapApplicationDefinition.getAbsolutePath();
//		logger.info( Boot_Container.TC_HEAD + message );
//
//		// Configure the manager...
//		Application.setJvmInManagerMode( true );
//		addTestManagerData( null );
//
//		assertTrue( "No Errors or warnings", csapApp.isCleanParseConfig( false, csapApplicationDefinition ) );
//
//		String responseJson = agentResource.getSummary();
//
//		// Print it out for generating tests using jackson
//		ObjectNode clusterDef = (ObjectNode) jacksonMapper.readTree( responseJson );
//		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( clusterDef ) );
//
//		assertEquals( "Found name", "Desktop Dev2",
//				clusterDef.at( "/name" ).asText() );
//
//		assertTrue( "SampleDefaultPackage.version",
//				clusterDef.at( "/version" ).asText().contains( "Desktop" ) );
//
//		assertEquals( "SampleDefaultPackage.vms", 5,
//				clusterDef.at( "/packages/0/vms" ).asInt() );
//
//		assertEquals( "SampleDefaultPackage.service", 15,
//				clusterDef.at( "/packages/0/services" ).asInt() );
//
//		assertEquals( "SampleDefaultPackage.service", 28,
//				clusterDef.at( "/packages/0/instances/total" ).asInt() );
//
//	}
//
//	@Test
//	public void getSummarySingle() throws JsonProcessingException, IOException {
//
//		File csapApplicationDefinition = new File(
//				getClass().getResource( "/org/csap/test/data/clusterConfigManager.json" ).getPath() );
//
//		String message = "Loading a manager definition with multiple supporting services: "
//				+ csapApplicationDefinition.getAbsolutePath();
//		logger.info( Boot_Container.TC_HEAD + message );
//
//		// Configure the manager...
//		addTestManagerData( null );
//
//		assertTrue( "No Errors or warnings", csapApp.isCleanParseConfig( false, csapApplicationDefinition ) );
//
//		String responseJson = agentResource.getSummary();
//
//		// Print it out for generating tests using jackson
//		ObjectNode clusterDef = (ObjectNode) jacksonMapper.readTree( responseJson );
//		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( clusterDef ) );
//
//		assertEquals( "Found name", "Desktop Dev2",
//				clusterDef.at( "/name" ).asText() );
//
//		assertTrue( "SampleDefaultPackage.version",
//				clusterDef.at( "/version" ).asText().contains( "Desktop" ) );
//
//	}
//
//	/**
//	 *
//	 * Scenario: - test cluster def API
//	 *
//	 * @throws IOException
//	 * @throws JsonProcessingException
//	 *
//	 */
//	@Test
//	public void getClusters() throws JsonProcessingException, IOException {
//
//		File csapApplicationDefinition = new File(
//				getClass().getResource( "/org/csap/test/data/clusterConfigManager.json" ).getPath() );
//
//		String message = "Loading a manager definition with multiple supporting services: "
//				+ csapApplicationDefinition.getAbsolutePath();
//		logger.info( Boot_Container.TC_HEAD + message );
//
//		// Configure the manager...
//		addTestManagerData( null );
//
//		assertTrue( "No Errors or warnings", csapApp.isCleanParseConfig( false, csapApplicationDefinition ) );
//
//		String responseJson = agentResource.getClusters();
//
//		ObjectNode clusterDef = (ObjectNode) jacksonMapper.readTree( responseJson );
//
//		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter()
//				.writeValueAsString( clusterDef ) );
//
//		assertTrue( "Found WebServer-1 in response",
//				clusterDef.at( "/WebServer-1" ) != null );
//
//		assertEquals( "Found $.WebServer-1[0]", "csap-dev01",
//				clusterDef.at( "/WebServer-1/0" ).asText() );
//		//
//		assertTrue( "Found $.all in response",
//				clusterDef.at( "/all" ) != null );
//		//
//		// List<String> servers = JsonPath.read( responseJson, "$.all" );
//		assertEquals( "Server Count", 8,
//				((ArrayNode) clusterDef.at( "/all" )).size() );
//
//	}
//
//	/**
//	 *
//	 * Scenario: - test vmStatus API
//	 *
//	 * @throws IOException
//	 * @throws JsonProcessingException
//	 *
//	 */
//	@Test
//	public void vmStatus() throws JsonProcessingException, IOException {
//
//		String responseJson = agentResource.vmStatus();
//
//		ObjectNode clusterDef = (ObjectNode) jacksonMapper.readTree( responseJson );
//		// Print it out for generating tests using jackson
//		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter()
//				.writeValueAsString( clusterDef ) );
//
//		assertEquals( "Server Count", 8,
//				clusterDef.at( "/hostStats/cpuCount" ).asInt() );
//
//		// need to add wait fo collection here
//		// "$.hostStats.readOnlyFS"
//		// List<String> dfStats = jacksonMapper.readValue( clusterDef.at(
//		// "/hostStats/readOnlyFS" ).toString(), List.class);
//		////
//		// assertEquals( "Found hostStats.readOnlyFS value", 3,
//		// dfStats.size() );
//		//
//		assertEquals( "Found services.CsAgent_8011.serviceName value", "CsAgent",
//				clusterDef.at( "/services/CsAgent_8011/serviceName" ).asText() );
//	}
//
//	private void addTestManagerData(File stubResponseFile) {
//
//		if ( stubResponseFile == null ) {
//			stubResponseFile = new File( getClass().getResource( "/CsAgent_Host_Response.json" ).getPath() );
//		}
//
//		HostStatusManager testManager = new HostStatusManager( stubResponseFile );
//		csapApp.setHostStatusManager( testManager );
//
//		Set<String> hosts = csapApp.getAllHostsInAllPackagesInCurrentLifecycle();
//		String[] hostArray = hosts.toArray( new String[hosts.size()] );
//		testManager.executeQueriesInParallelWithMaxWait( hostArray );
//
//	}
//
//	/**
//	 *
//	 * Scenario: - test health API
//	 *
//	 * @throws IOException
//	 * @throws JsonProcessingException
//	 *
//	 */
//	@Test
//	public void heath_of_entire_cluster() throws JsonProcessingException, IOException {
//
//		Application.setJvmInManagerMode( true );
//		addTestManagerData( null );
//
//		// Application.setJvmInManagerMode(true);
//		JsonNode responseJson = jacksonMapper.readTree( agentResource.capabilityHealth() );
//
//		// Print it out for generating tests using jackson
//		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter()
//				.writeValueAsString( responseJson ) );
//
//		assertThat( responseJson.get( "Healthy" ).asBoolean() )
//				.as( "Found $.Healthy" )
//				.isFalse();
//
//		ArrayList<String> errorList = jacksonMapper.readValue(
//				responseJson.at( "/errors/localhost" ).traverse(),
//				new TypeReference<ArrayList<String>>() {
//		} );
//
//		assertThat( errorList )
//				.as( "Number of Errors found" )
//				.hasSize( 4 )
//				.contains( "localhost: SampleDataLoader_none No status found",
//						"localhost: CsAgent: rssMemory Value: \"513440\" Exceeds configured maximum: 512000" );
//
//	}
//
//	@Test
//	public void heartBeat_failure_in_cluster() throws JsonProcessingException, IOException {
//
//		Application.setJvmInManagerMode( true );
//		File heartBeatResponse = new File(
//				getClass().getResource( "HeartBeatError_Host_Response.json" ).getPath() );
//		addTestManagerData( heartBeatResponse );
//
//		// Application.setJvmInManagerMode(true);
//		JsonNode responseJson = jacksonMapper.readTree( agentResource.capabilityHealth() );
//
//		// Print it out for generating tests using jackson
//		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter()
//				.writeValueAsString( responseJson ) );
//
//		assertThat( responseJson.get( "Healthy" ).asBoolean() )
//				.as( "Found $.Healthy" )
//				.isFalse();
//
//		ArrayList<String> errorList = jacksonMapper.readValue(
//				responseJson.at( "/errors/csap-dev01" ).traverse(),
//				new TypeReference<ArrayList<String>>() {
//		} );
//
//		assertThat( errorList )
//				.as( "jmxHearBeatFailure" )
//				.contains(
//						"csap-dev01: ServletSample: jmxHeartBeat is enabled but not successful using custom: jmxHeartbeatMs" );
//
//		// update definition and response to test permutations
//	}
//
//	@Inject
//	OsManager osManager;
//
//	@Test
//	public void health_of_single_host() throws JsonProcessingException, IOException {
//
//		// First update with process data
//		osManager.checkForProcessStatusUpdate();
//
//		// update with JMX data
//		VmApplicationCollector jmxRunnable = new VmApplicationCollector( csapApp,
//				osManager, 30, false );
//		jmxRunnable.shutdown();
//		jmxRunnable.testJmxCollection();
//
//		JsonNode responseJson = jacksonMapper.readTree( agentResource.capabilityHealth() );
//
//		// Print it out for generating tests using jackson
//		logger.info( WordUtils.wrap( jacksonMapper.writerWithDefaultPrettyPrinter()
//				.writeValueAsString( responseJson ), 200 ) );
//
//		ArrayList<String> errorList = jacksonMapper.readValue(
//				responseJson.at( "/errors/localhost" ).traverse(),
//				new TypeReference<ArrayList<String>>() {
//		} );
//
//		assertThat( errorList )
//				.as( "Number of Errors found" )
//				.contains(
//						"localhost: springmvc-showcase: jmxHeartBeat is enabled but not detected using jvmThreadCount",
//						"localhost: CsAgent: diskUtil Value: \"221\" Exceeds configured maximum: 50" );
//
//	}
//
	@Test
	public void agent_health_api() throws Exception {


		logger.info(InitializeLogging.TC_HEAD );
		if ( !csapApp.isCompanyVariableConfigured( "test.variables.test-admin-host1" ) ) {
			logger.info( "company variable not set - skipping test" );
			return;
		}

		String host1 = csapApp.getCompanyConfiguration( "test.variables.test-admin-host1", "missing" ) ;
		
		// limits_with_unit_conversion
//		File csapApplicationDefinition = new File(
//				getClass().getResource( "application-with-limits.json" ).getPath() );

		assertThat( csapApp.loadDefinitionForJunits( false, InitializeLogging.DEFINITION_WITH_PUBLISH ) )
			.as( "No Errors or warnings" )
			.isTrue();

		assertThat( csapApp.getName() )
				.as( "Capability Name parsed" )
				.isEqualTo( "Publish Enabled" );
		

		assertThat( csapApp.isCollectorsStarted() )
				.as( "collectors started" )
				.isTrue();



		logger.info(InitializeLogging.TC_HEAD );
		// First update with process data
		csapApp.getOsManager().checkForProcessStatusUpdate();

		// update with JMX data
//		ServiceCollector serviceCollection = new ServiceCollector( csapApp,
//			csapApp.getOsManager(), 30, false );
//		serviceCollection.shutdown();
//		serviceCollection.testJmxCollection( "csap-dev01" );

		JsonNode responseJson = agentApi.health() ;

		// Print it out for generating tests using jackson
		logger.info( "Health: {}", CSAP.jsonPrint( jacksonMapper, responseJson ) );

		assertThat( responseJson.at( "/Healthy" ).asBoolean() )
				.as( " capabilityHealth: /healthy" )
				.isFalse();

		assertThat( responseJson.at( "/errors/localhost" ).size() )
				.as( " capabilityHealth: /errors/localhost" )
				.isGreaterThanOrEqualTo( 6 );

//		assertThat( responseJson.at( "/errors/localhost" ).toString() )
//				.as( " capabilityHealth: /errors/localhost" )
//				.contains( "CsAgent: diskUtil Value", "Exceeds configured maximum: 200" );

	}
//
//	@Test
//	public void emanHealthSingleVm() throws JsonProcessingException, IOException {
//
//		// Application.setJvmInManagerMode(true);
//		String response = agentResource.emanStatus( ServiceDefinition.ALERT_LEVEL );
//
//		// Print it out for generating tests using jackson
//		logger.info( "Response: " + response );
//
//		assertTrue( "Found Failed Response",
//				response.startsWith( "Failure" ) );
//
//	}
//
//	// Uses default config which contains publish config
//	@Test
//	public void nagios_definition() throws JsonProcessingException, IOException {
//
//		String response = agentResource.getNagios();
//
//		// Print it out for generating tests using jackson
//		logger.info( "Response: \n" + response );
//
//		assertThat( response )
//				.as( "hostgroup definition" )
//				.containsOnlyOnce( "define hostgroup{" );
//
//		assertThat( response )
//				.as( "CsAgent health check command" )
//				.contains( "check_command 	 check_http! -v -u /CsAgent/api/CapabilityHealth" );
//
//		assertThat( StringUtils.countMatches( response, "define host" ) )
//				.as( "define host" )
//				.isEqualTo( 4 );
//
//		assertThat( StringUtils.countMatches( response, "define service" ) )
//				.as( "define service" )
//				.isEqualTo( 15 );
//
//		assertThat( StringUtils.countMatches( response, "define command" ) )
//				.as( "define command" )
//				.isEqualTo( 4 );
//
//	}
//
//	// Test with no publish config
//	@Test
//	public void nagios_definition_without_publish() throws JsonProcessingException, IOException {
//
//		Application.setJvmInManagerMode( true );
//
//		File configFile = new File(
//				getClass().getResource( "/org/csap/test/data/clusterConfigMultiple.json" ).getPath() );
//
//		String message = "Loading a working definition with multiple supporting services: "
//				+ configFile.getAbsolutePath();
//		logger.info( Boot_Container.TC_HEAD + message );
//
//		assertTrue( "No Errors or warnings during parsing",
//				csapApp.isCleanParseConfig( false, configFile ) );
//
//		String response = agentResource.getNagios();
//
//		// Print it out for generating tests using jackson
//		logger.info( "Response: \n" + response );
//
//		assertTrue( "Found hostgroup in  Response",
//				response.contains( "hostgroup" ) );
//
//		assertTrue( "Found null in  Response",
//				!response.contains( "null" ) );
//
//		assertTrue( "Found CsAgent in  Response",
//				response.contains( "-u /CsAgent/api/CapabilityHealth" ) );
//
//	}
//
//	/**
//	 *
//	 * Scenario: - test userInfo API - needs security and ldap enabled
//	 *
//	 * @throws IOException
//	 * @throws JsonProcessingException
//	 *
//	 */
//	// @Ignore // enable ldap
//	@Test
//	public void get_user_information() throws JsonProcessingException, IOException {
//
//		Application.setJvmInManagerMode( true );
//		String responseJson = agentResource.userInfo( "someDeveloper", "false", "false" );
//
//		ObjectNode userInfoJson = (ObjectNode) jacksonMapper.readTree( responseJson );
//
//		// Print it out for generating tests using jackson
//		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter()
//				.writeValueAsString( userInfoJson ) );
//
//		assertEquals( "Found $.fullName", "Peter Nightingale",
//				userInfoJson.at( "/fullName" ).asText() );
//
//		String responseFullJson = agentResource.userInfo( "someDeveloper", "false", "true" );
//		userInfoJson = (ObjectNode) jacksonMapper.readTree( responseFullJson );
//
//		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter()
//				.writeValueAsString( userInfoJson ) );
//
//		assertEquals( "Found $.jobRole[0]'", "Application/Development",
//				userInfoJson.at( "/jobrole/0" ).asText() );
//
//		// Test with generic
//		responseFullJson = agentResource.userInfo( "ssplatform.gen", "false", "true" );
//
//		userInfoJson = (ObjectNode) jacksonMapper.readTree( responseFullJson );
//		// Print it out for generating tests using jackson
//		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter()
//				.writeValueAsString( userInfoJson ) );
//
//		assertEquals( "Found $.cn'", "ssplatform.gen", userInfoJson.at( "/cn/0" ).asText() );
//
//		// Test with generic
//		responseFullJson = agentResource.userInfo( "cstgbuilds.gen", "false", "true" );
//		userInfoJson = (ObjectNode) jacksonMapper.readTree( responseFullJson );
//
//		// Print it out for generating tests using jackson
//		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter()
//				.writeValueAsString( (ObjectNode) jacksonMapper.readTree( responseFullJson ) ) );
//
//		assertEquals( "Found $.cn'", "cstgbuilds.gen", userInfoJson.at( "/cn/0" ).asText() );
//
//	}
//
//	/**
//	 *
//	 * Scenario: - test userNames API
//	 *
//	 * @throws IOException
//	 * @throws JsonProcessingException
//	 *
//	 */
//	@Test
//	public void get_user_names() throws JsonProcessingException, IOException {
//
//		Application.setJvmInManagerMode( true );
//		List<String> userids = new ArrayList<String>(
//				Arrays.asList( "someDeveloper",
//						"dtandon",
//						"paranant", "nonExist" ) );
//		String responseJson = agentResource.userNames( userids, "false" );
//		ObjectNode userInfoJson = (ObjectNode) jacksonMapper.readTree( responseJson );
//
//		// Print it out for generating tests using jackson
//		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter()
//				.writeValueAsString( userInfoJson ) );
//
//		// Use jsonPath apis for quick processing for tests
//		assertThat( userInfoJson.get( "someDeveloper" ).asText() )
//				.isEqualTo( "Peter Nightingale" );
//
//	}
//
//	/**
//	 *
//	 * Scenario: - test userInfo API
//	 *
//	 * @throws IOException
//	 * @throws JsonProcessingException
//	 *
//	 */
//	@Test
//	public void vm_information_with_support_for_jsonp() throws JsonProcessingException, IOException {
//
//		Application.setJvmInManagerMode( true );
//		String responseJson = agentResource.vmSummary( "false" );
//		ObjectNode vmInfoJson = (ObjectNode) jacksonMapper.readTree( responseJson );
//
//		// Print it out for generating tests using jackson
//		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter()
//				.writeValueAsString( vmInfoJson ) );
//
//		// Use jsonPath apis for quick processing for tests
//		assertThat( vmInfoJson.get( "redhat" ) )
//				.isNotNull();
//
//		String testFunctionName = "myFunctionName";
//		String jsonpResponse = agentResource.vmSummary( testFunctionName );
//
//		logger.info( jsonpResponse );
//
//		assertThat( jsonpResponse )
//				.as( "json p support" )
//				.startsWith( testFunctionName );
//
//	}
//
//	private HttpServletRequest buildSecurityRequest() {
//
//		MockHttpServletRequest request = new MockHttpServletRequest();
//
//		ObjectNode resultJson = jacksonMapper.createObjectNode();
//
//		resultJson.put( "userid", "junit" );
//
//		request.setAttribute( SpringAuthCachingFilter.SEC_RESPONSE_ATTRIBUTE,
//				resultJson );
//		return request;
//	}
//
//	@Test
//	public void deploy_service_via_api() throws JsonProcessingException, IOException {
//
//		String cluster = "";
//
//		String responseJson = agentResource.serviceDeploy( "peter:pass",
//				"springmvc-showcase_8211",
//				"com.your.group:Cssp2FactorySample:2.0.21:war", cluster,
//				"userNotUsedInJunits",
//				"passNotUsedInJunits",
//				buildSecurityRequest() );
//		ObjectNode buildResponseJson = (ObjectNode) jacksonMapper.readTree( responseJson );
//
//		// Print it out for generating tests using jackson
//		logger.info( Boot_Container.TC_HEAD + jacksonMapper.writerWithDefaultPrettyPrinter()
//				.writeValueAsString( buildResponseJson ) );
//
//		// Use jsonPath apis for quick processing for tests
//		assertThat( buildResponseJson.get( "error" ) )
//				.isNull();
//
//		assertThat( buildResponseJson.get( "serviceName" ).asText() )
//				.isEqualTo( "springmvc-showcase_8211" );
//
//		// Error Scenario 1: JVM does not exist
//		responseJson = agentResource.serviceDeploy( "peter:pass", "WrongJvmName",
//				"com.your.group:Cssp2FactorySample:2.0.21:war", cluster,
//				"userNotUsedInJunits",
//				"passNotUsedInJunits",
//				buildSecurityRequest() );
//		buildResponseJson = (ObjectNode) jacksonMapper.readTree( responseJson );
//
//		logger.info( jacksonMapper.writerWithDefaultPrettyPrinter()
//				.writeValueAsString( buildResponseJson ) );
//
//		assertThat( buildResponseJson.get( "error" ) )
//				.as( "Jvm requested does not exist: WrongJvmName" )
//				.isNotNull();
//
//	}
//
//	@Test
//	public void start_service() throws JsonProcessingException, IOException {
//
//		String cluster = "";
//
//		// Invalid creds scenario is in init.jee tess
//		String user = "dummUserA";
//		String pass = "dummPass!";
//		JsonNode responseJson = jacksonMapper.readTree(
//				agentResource.serviceStart(
//						"peter:pass", "springmvc-showcase_8211",
//						cluster, user,
//						pass, buildSecurityRequest() ) );
//
//		// Print it out for generating tests using jackson
//		logger.info( " Success start results: \n"
//				+ jacksonMapper.writerWithDefaultPrettyPrinter()
//				.writeValueAsString( responseJson ) );
//
//		assertThat( responseJson.has( "error" ) )
//				.as( "No errors found" )
//				.isFalse();
//		// // Use jsonPath apis for quick processing for tests
//		// assertTrue( "Found no errors",
//		// JsonPath.read( responseJson, "$.error" ) == null );
//
//		assertThat( responseJson.at( "/serviceName" ).asText() )
//				.as( "serviceName" )
//				.isEqualTo( "springmvc-showcase_8211" );
//
//		assertThat( responseJson.at( "/results" ).asText() )
//				.as( "serviceName" )
//				.contains( "Request completed" );
//
//		// Error Scenario 1: JVM does not exist
//		responseJson = jacksonMapper.readTree(
//				agentResource.serviceStart( "peter:pass", "WrongJvmName",
//						cluster, user,
//						pass, buildSecurityRequest() ) );
//		// Print it out for generating tests using jackson
//		logger.info( "No jvm Results: \n"
//				+ jacksonMapper.writerWithDefaultPrettyPrinter()
//				.writeValueAsString( responseJson ) );
//
//		assertThat( responseJson.at( "/error" ).asText() )
//				.as( "JVM requested does not exist" )
//				.contains( "Jvm requested does not exist" );
//
//	}
}
