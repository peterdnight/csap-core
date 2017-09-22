package org.csap.test.t5_data_collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.csap.agent.CSAP;
import org.csap.agent.CsapCoreService;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.Application;
import org.csap.agent.model.ServiceAlertsEnum;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.services.OsManager;
import org.csap.agent.stats.HttpCollector;
import org.csap.agent.stats.OsProcessCollector;
import org.csap.agent.stats.ServiceCollector;
import org.csap.alerts.MonitorMbean.Report;
import org.csap.test.InitializeLogging;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@RunWith ( SpringRunner.class )
@SpringBootTest
@ConfigurationProperties ( prefix = "test.variables" )
@ActiveProfiles ( profiles = { "junit", "company" } )
public class Service_Collection_Test {

	final static private Logger logger = LoggerFactory.getLogger( Service_Collection_Test.class );

	final static String CSAP_SIMPLE_SERVICE = "CsapSimple_8291";
	final static String CSAP_AGENT = "CsAgent_8011";

	// needed to resolve test properties
	@SpringBootApplication
	static class SimpleConfiguration {
	}

	private String testDbHost = null;
	private String testAdminHost1 = null;
	private String testMongoCollection = null;

	public void setTestMongoCollection ( String testMongoCollection ) {
		this.testMongoCollection = testMongoCollection;
	}

	public void setTestAdminHost1 ( String testAdminHost1 ) {
		this.testAdminHost1 = testAdminHost1;
	}

	public void setTestDbHost ( String testDbHost ) {
		this.testDbHost = testDbHost;
	}

	@Test
	public void verify_setup () {

		logger.info( "Got here: {}", testDbHost );

		assertThat( isSetupOk() ).as( "setup ok, ~home/csap/application-company.yml loaded" ).isTrue();

		assertThat( csapApplication.isCollectorsStarted() )
			.as( "collectionStarted" ).isTrue();

	}

	private boolean isSetupOk () {

		if ( testDbHost == null || testAdminHost1 == null )
			return false;

		logger.info( "testDbHost: {}, testAdminHost1: {}", testDbHost, testAdminHost1 );

		return true;
	}

	// private static final String testDbHost = "";

	final static public File collectionDefinition = new File(
		Service_Collection_Test.class.getResource( "application-collection.json" ).getPath() );

	@BeforeClass
	public static void setUpBeforeClass ()
			throws Exception {

		InitializeLogging.printTestHeader( logger.getName() );
		Application.setJvmInManagerMode( false );

		osManager = new OsManager();
		csapApplication = new Application( osManager );
		osManager.setTestApp( csapApplication );
		csapApplication.setAutoReload( false );
		csapApplication.setCsapCoreService( new CsapCoreService() );
		csapApplication.setEventClient( new CsapEventClient() );

		csapApplication.initialize();

		assertThat(
			csapApplication.loadDefinitionForJunits( false, collectionDefinition ) )
				.as( "No Errors or warnings" )
				.isFalse();

		csapApplication.startCollectorsForJunit();

		logger.info( "csapApplication.isBootstrapComplete {}", csapApplication.isBootstrapComplete() );
	}

	static Application csapApplication;

	private static OsManager osManager;

	ObjectMapper jacksonMapper = new ObjectMapper();

	@Test
	public void process_collection_for_host ()
			throws Exception {
		
		logger.info( InitializeLogging.TC_HEAD );

		if ( !isSetupOk() )
			return;

		// csapApp.shutdown();
		OsProcessCollector osProcessCollection = new OsProcessCollector( csapApplication,
			osManager, 30, false );

		// shutdown to keep logs reasonable to trace during test.
		osProcessCollection.shutdown();

		// runs collection based on application
		long now = System.currentTimeMillis();
		CSAP.setLogToInfo( OsProcessCollector.class.getName() );
		osProcessCollection.testCollection();
		CSAP.setLogToInfo( OsProcessCollector.class.getName() );

		String[] services = { "CsAgent", CSAP_SIMPLE_SERVICE };
		// String[] services = null;
		ObjectNode results = osProcessCollection.getCSVdata( false, services, 999, 0 );

		logger.info( "Results: \n {} ", CSAP.jsonPrint( jacksonMapper, results ) );

		assertThat( results.at( "/data/timeStamp/0" ).asLong() )
			.as( "/data/timeStamp/0" )
			.isGreaterThan( now );

		assertThat( results.at( "/data/threadCount_" + CSAP_SIMPLE_SERVICE + "/0" ).asInt() )
			.as( "threadCount" )
			.isGreaterThan( 10 );

		assertThat( results.at( "/data/threadCount_CsAgent/0" ).asInt() )
			.as( "threadCount_CsAgent" )
			.isGreaterThan( 50 );

		// assertThat( results.at( "/data/heapUsed_CsAgent_8011/0" ).asInt() )
		// .as( "/data/heapUsed_CsAgent_8011/0" )
		// .isGreaterThan( 10 );
		//
		// assertThat( results.at( "/data/openFiles_Cssp3ReferenceMq_8241/0"
		// ).asInt() )
		// .as( "/data/Cssp3ReferenceMq_8241/0" )
		// .isGreaterThan( 250 );
	}

	@Ignore
	@Test
	public void verify_service_with_remote_collection ()
			throws Exception {
		logger.info( InitializeLogging.TC_HEAD + "Start" );

		// csapApp.shutdown();
		ServiceCollector serviceCollector = new ServiceCollector( csapApplication,
			osManager, 30, false );

		// shutdown to keep logs reasonable to trace during test.
		serviceCollector.shutdown();

		// runs collection based on application
		CSAP.setLogToInfo( ServiceCollector.class.getName() );
		serviceCollector.testJmxCollection( testDbHost );
		CSAP.setLogToInfo( ServiceCollector.class.getName() );
		//

		String[] services = { "JmxRemoteService_8491" };
		ObjectNode hostJmxCollectionResults = serviceCollector.getCSVdata( false, services, 999, 0 );

		// data, attributes, etc.
		logger.info( "Service Collection - standard JMX: \n {}",
			jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( hostJmxCollectionResults.get( "attributes" ) ) );

		ArrayList<String> servicesAvailable = jacksonMapper.readValue(
			hostJmxCollectionResults.at( "/attributes/servicesAvailable" ).traverse(),
			new TypeReference<ArrayList<String>>() {
			} );
		assertThat( servicesAvailable )
			.as( "servicesAvailable" )
			.contains( "activemq_8161", "CsAgent_8011", "Cssp3ReferenceMq_8251", "Cssp3ReferenceMq_8241", "JmxRemoteService_8491" );

		assertThat( hostJmxCollectionResults.at( "/attributes/remoteCollect" ).isMissingNode() )
			.as( "collected From data is missing" )
			.isFalse();
		HashMap<String, String> collectedFrom = jacksonMapper.readValue(
			hostJmxCollectionResults.at( "/attributes/remoteCollect" ).traverse(),
			new TypeReference<HashMap<String, String>>() {
			} );
		assertThat( collectedFrom.get( "JmxRemoteService_8491" ) )
			.as( "collectedFrom" )
			.isEqualTo( testAdminHost1 );

		assertThat( hostJmxCollectionResults.at( "/data/openFiles_JmxRemoteService_8491" ).isMissingNode() )
			.as( "JmxRemoteService_8491 data is missing" )
			.isFalse();

		assertThat( hostJmxCollectionResults.at( "/data/openFiles_JmxRemoteService_8491/0" ).asInt() )
			.as( "JmxRemoteService_8491 open Files" )
			.isGreaterThan( 100 );

		ObjectNode hostCustomJmxResults = serviceCollector.getCSVdata( false, services, 999, 0, "CustomJmxIsBeingUsed" );

		logger.info( "Service Collection - custom JMX: \n {}",
			jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( hostCustomJmxResults.get( "data" ) ) );

		assertThat( hostCustomJmxResults.at( "/data/classesLoaded/0" ).asInt() )
			.as( "JmxRemoteService_8491 classes loaded" )
			.isGreaterThan( 1000 );
	}

	@Test
	public void verify_java_collection_for_CsapSimple ()
			throws Exception {
		logger.info( InitializeLogging.TC_HEAD );
		if ( !isSetupOk() )
			return;

		String[] services = { CSAP_SIMPLE_SERVICE };
		ObjectNode javaCollection = performJavaCommonCollection( services, testAdminHost1 );

		logger.info( "Service Collection: \n {}",
			CSAP.jsonPrint( jacksonMapper, javaCollection.get( "data" ) ) );

		assertThat( javaCollection.at( "/data/openFiles_" + CSAP_SIMPLE_SERVICE + "/0" ).asInt() )
			.as( "openFiles_CsapSimple_8291" )
			.isGreaterThan( 0 );
	}

	@Test
	public void verify_java_collection_for_CsapTestDocker ()
			throws Exception {
		logger.info( InitializeLogging.TC_HEAD );

		if ( !isSetupOk() )
			return;

		String[] services = { "CsapTestDocker_7080" };
		ObjectNode javaCollection = performJavaCommonAndCustomCollection( services, testAdminHost1 );

		logger.info( "Service Collection: \n {}",
			CSAP.jsonPrint( jacksonMapper, javaCollection.get( "data" ) ) );

		JsonNode custCollectionData = javaCollection.get( "custom" + services[0] ).get( "data" );

		logger.info( "Java Custom: \n {}",
			CSAP.jsonPrint( jacksonMapper, custCollectionData ) );

		assertThat( custCollectionData.at( "/SpringJmsListeners/0" ).asInt() )
			.as( "SpringJmsListeners" )
			.isGreaterThan( 0 );

	}

	@Test
	public void verify_agent_standard_collection ()
			throws Exception {

		logger.info( InitializeLogging.TC_HEAD );

		if ( !isSetupOk() )
			return;

		String[] services = { "CsAgent_8011" };

		ObjectNode standardJavaCollection = performJavaCommonCollection( services, testAdminHost1 );

		logger.info( "collected: \n {}", CSAP.jsonPrint( jacksonMapper, standardJavaCollection ) );

		ArrayList<String> servicesAvailable = jacksonMapper.readValue(
			standardJavaCollection.at( "/attributes/servicesAvailable" ).traverse(),
			new TypeReference<ArrayList<String>>() {
			} );

		assertThat( servicesAvailable )
			.as( "servicesAvailable" )
			.contains( "CsAgent_8011" );

		assertThat( standardJavaCollection.at( "/data/openFiles_CsAgent_8011/0" ).asInt() )
			.as( "open files" )
			.isGreaterThan( 100 );

		assertThat( standardJavaCollection.at( "/data/heapUsed_CsAgent_8011/0" ).asInt() )
			.as( "heap used" )
			.isGreaterThan( 10 );

	}

	@Test
	public void verify_agent_standard_and_application_collection ()
			throws Exception {

		logger.info( InitializeLogging.TC_HEAD );

		if ( !isSetupOk() )
			return;

		String[] services = { "CsAgent_8011" };
		ObjectNode commonAndCustomResults = performJavaCommonAndCustomCollection( services, testAdminHost1 );

		ObjectNode agentApplicationCollection = (ObjectNode) commonAndCustomResults.get( "custom" + services[0] );

		logger.info( "collected: \n {}", CSAP.jsonPrint( jacksonMapper, agentApplicationCollection ) );

		assertThat( agentApplicationCollection.at( "/data/getVmStatsMeanMs/0" ).asInt() )
			.as( "/data/getVmStatsMeanMs/0 : " )
			.isGreaterThanOrEqualTo( 0 );

		logger.warn(
			"Custom JMX collections using contexts require clusterStub.js to be updated with CsAgent version on csapdb-dev01" );
		// Delta requests should be low

		assertThat( agentApplicationCollection.at( "/data/SpringMvcRequests/0" ).asInt() )
			.as( "SpringMvcRequests is in delta mode - so unless we collect twice, it will be 0" )
			.isLessThan( 10 );

		assertThat( agentApplicationCollection.at( "/data/OsCommandsCounter/0" ).asInt() )
			.as( "/data/OsCommandsCounter/0" )
			.isGreaterThan( 1 );

		assertThat( agentApplicationCollection.at( "/data/CommandsSinceRestart/0" ).asInt() )
			.as( "/data/CommandsSinceRestart/0" )
			.isGreaterThan( 1 );

		List<String> servicesAvailable = jacksonMapper.readValue(
			agentApplicationCollection.at( "/attributes/servicesAvailable" ).traverse(), new TypeReference<List<String>>() {
			} );
		assertThat( servicesAvailable )
			.as( "servicesAvailable" )
			.contains( "CsAgent_8011" );

	}

	private ObjectNode performJavaCommonCollection ( String[] services, String host ) {
		return performJavaCollection( services, host, false );
	}

	private ObjectNode performJavaCommonAndCustomCollection ( String[] services, String host ) {
		return performJavaCollection( services, host, true );
	}

	private ObjectNode performJavaCollection ( String[] services, String host, boolean isCustom ) {
		// csapApp.shutdown();
		ServiceCollector serviceCollector = new ServiceCollector( csapApplication,
			osManager, 30, false );

		// shutdown to keep logs reasonable to trace during test.
		serviceCollector.shutdown();

		// runs collection based on application
		CSAP.setLogToDebug( ServiceCollector.class.getName() );

		for ( String serviceName : services ) {
			serviceCollector.testJmxCollection( csapApplication.getServiceInstanceCurrentHost( serviceName ), host );

		}
		CSAP.setLogToInfo( ServiceCollector.class.getName() );
		//

		ObjectNode standardJavaData = serviceCollector.getCSVdata( false, services, 999, 0 );

		if ( isCustom ) {
			// add custom data into standard result for testing ease.
			for ( String serviceName : services ) {
				String[] customServices = { serviceName };
				ObjectNode customJavaData = serviceCollector.getCSVdata( false, customServices, 999, 0, "CustomJmxIsBeingUsed" );
				standardJavaData.set( "custom" + serviceName, customJavaData );
			}
		}

		return standardJavaData;
	}

	@Test
	public void verify_java_health_collection ()
			throws Exception {
		logger.info( InitializeLogging.TC_HEAD );

		if ( !isSetupOk() )
			return;

		String[] services = { CSAP_SIMPLE_SERVICE };
		ObjectNode commonAndCustomResults = performJavaCommonAndCustomCollection( services, testAdminHost1 );

		logger.info( "Service Collection: \n {}",
			jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( commonAndCustomResults ) );

		ArrayList<String> servicesAvailable = jacksonMapper.readValue(
			commonAndCustomResults.at( "/attributes/servicesAvailable" ).traverse(),
			new TypeReference<ArrayList<String>>() {
			} );

		assertThat( servicesAvailable )
			.as( "servicesAvailable" )
			.contains( services[0] );

		assertThat( commonAndCustomResults.at( "/data/openFiles_" + services[0] + "/0" ).asInt() )
			.as( "open files" )
			.isGreaterThan( 50 );

		assertThat( commonAndCustomResults.at( "/data/heapUsed_" + services[0] + "/0" ).asInt() )
			.as( "java heap" )
			.isGreaterThan( 10 );

		ObjectNode refCustomResults = (ObjectNode) commonAndCustomResults.get( "custom" + services[0] );

		logger.info( "refCustomResults: \n {}", jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( refCustomResults ) );

		assertThat( refCustomResults.at( "/data/" + ServiceAlertsEnum.JAVA_HEARTBEAT + "/0" ).asInt() )
			.as( "java heartbeat" )
			.isGreaterThan( 0 );

		ServiceInstance service = csapApplication.getServiceInstanceCurrentHost( services[0] );

		assertThat( service.getJvmThreadCount() )
			.as( "java threadcount" )
			.isGreaterThan( 10 );

		assertThat( service.getJmxHeartbeatMs() )
			.as( "heartBeat" )
			.isGreaterThan( 10 );

		assertThat( service.isHealthReportConfigured() )
			.as( "Health Configures" )
			.isTrue();

		assertThat( service.getHealthReportCollected() )
			.as( "Health Configures" )
			.isNotNull();

		logger.info( "service.getHealthReportCollected(): \n {}",
			jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( service.getHealthReportCollected() ) );

		assertThat( service.getHealthReportCollected().get( Report.healthy.json ).asBoolean() )
			.as( "Report passed" )
			.isTrue();

		// ObjectNode healthReport = ;

	}

	@Ignore
	@Test
	public void jmx_collection_with_retries_all_failures ()
			throws Exception {

		logger.info( InitializeLogging.TC_HEAD );

		if ( !isSetupOk() )
			return;

		// csapApp.shutdown();
		ServiceCollector serviceCollector = new ServiceCollector( csapApplication,
			osManager, 30, false );

		// shutdown to keep logs reasonable to trace during test.
		serviceCollector.shutdown();

		// runs collection based on application
		serviceCollector.setTestServiceTimeout( 75, CSAP_SIMPLE_SERVICE, 999 );
		serviceCollector.testJmxCollection( testDbHost );
		String[] services = { "CsAgent_8011", CSAP_SIMPLE_SERVICE };
		ObjectNode results = serviceCollector.getCSVdata( false, services, 999, 0 );

		logger.info( "Results: \n"
				+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( results ) );

		assertThat( serviceCollector.getNumberOfCollectionAttempts() )
			.as( "getNumberOfCollectionAttempts" )
			.isGreaterThan( 1 );

		assertThat( results.at( "/data/openFiles_CsAgent_8011/0" ).asInt() )
			.as( "/data/openFiles_CsAgent_8011/0" )
			.isGreaterThan( 100 );

		assertThat( results.at( "/data/heapUsed_CsAgent_8011/0" ).asInt() )
			.as( "/data/heapUsed_CsAgent_8011/0" )
			.isGreaterThan( 10 );

		assertThat( results.at( "/data/jvmThreadCount_" + CSAP_SIMPLE_SERVICE + "/0" ).asInt() )
			.as( "threadCOunt" )
			.isEqualTo( 0 );

	}

	@Ignore
	@Test
	public void jmx_collection_with_3_retries_and_success ()
			throws Exception {

		logger.info( InitializeLogging.TC_HEAD );

		if ( !isSetupOk() )
			return;

		// csapApp.shutdown();
		ServiceCollector jmxRunnable = new ServiceCollector( csapApplication,
			osManager, 30, false );

		// shutdown to keep logs reasonable to trace during test.
		jmxRunnable.shutdown();

		// runs collection based on application
		jmxRunnable.setTestServiceTimeout( 75, CSAP_SIMPLE_SERVICE, 3 );
		jmxRunnable.testJmxCollection( testDbHost );
		String[] services = { "CsAgent_8011", CSAP_SIMPLE_SERVICE };
		ObjectNode results = jmxRunnable.getCSVdata( false, services, 999, 0 );

		logger.info( "Results: \n"
				+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( results ) );

		assertThat( jmxRunnable.getNumberOfCollectionAttempts() )
			.as( "getNumberOfCollectionAttempts" )
			.isGreaterThan( 2 );

		assertThat( results.at( "/data/openFiles_CsAgent_8011/0" ).asInt() )
			.as( "/data/openFiles_CsAgent_8011/0" )
			.isGreaterThan( 100 );

		assertThat( results.at( "/data/heapUsed_CsAgent_8011/0" ).asInt() )
			.as( "/data/heapUsed_CsAgent_8011/0" )
			.isGreaterThan( 10 );

		List<String> servicesAvailable = jacksonMapper.readValue(
			results.at( "/attributes/servicesAvailable" ).traverse(), new TypeReference<List<String>>() {
			} );
		assertThat( servicesAvailable )
			.as( "servicesAvailable" )
			.contains( "activemq_8161", "CsAgent_8011", CSAP_SIMPLE_SERVICE );

	}

	@Test
	public void verify_activemq_collection ()
			throws Exception {

		logger.info( InitializeLogging.TC_HEAD );

		if ( !isSetupOk() )
			return;

		String[] services = { "activemq_8161" };
		ObjectNode commonAndCustomResults = performJavaCommonAndCustomCollection( services, testDbHost );

		ObjectNode applicationCollection = (ObjectNode) commonAndCustomResults.get( "custom" + services[0] );

		logger.info( "collected: \n {}", CSAP.jsonPrint( jacksonMapper, commonAndCustomResults ) );

		// ensure non tomcat jvms skip jmx reporting of tomcat only attributes.
		assertThat( commonAndCustomResults.at( "/attributes/graphs/httpKbytesReceived" ).fieldNames().hasNext() )
			.as( "/attributes/graphs/httpKbytesReceived" )
			.isFalse();
		assertThat( commonAndCustomResults.at( "/attributes/graphs/httpKbytesSent" ).fieldNames().hasNext() )
			.as( "/attributes/graphs/httpKbytesSent" )
			.isFalse();

		assertThat( commonAndCustomResults.at( "/data/openFiles_activemq_8161/0" ).asInt() )
			.as( "/data/openFiles_activemq_8161/0" )
			.isGreaterThan( 100 );

		assertThat( commonAndCustomResults.at( "/data" ).has( "sessionsActive_activemq_8161" ) )
			.as( "/data should not have sessionsActive_activemq_8161" )
			.isFalse();

		assertThat( applicationCollection.at( "/data/JvmThreadCount/0" ).asInt() )
			.as( "JvmThreadCount" )
			.isGreaterThan( 50 );
	}

	@Test
	public void testJmxUpload ()
			throws Exception {

		logger.info( InitializeLogging.TC_HEAD );

		if ( !isSetupOk() )
			return;

		// csapApp.shutdown();
		ServiceCollector jmxRunnable = new ServiceCollector( csapApplication,
			osManager, 30, true );

		// This will trigger a remote procedure call
		jmxRunnable.shutdown();

		jmxRunnable.testJmxCollection( testDbHost );
		String[] services = { "CsAgent_8011" };
		ObjectNode results = jmxRunnable.getCSVdata( true, services, 999, 0 );

		logger.info( "Results: \n"
				+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( results ) );

		assertTrue(
			"/data/openFiles_CsAgent_8011/0 : "
					+ results.at( "/data/openFiles_CsAgent_8011/0" ).asInt() + " > 100",
			results.at( "/data/openFiles_CsAgent_8011/0" ).asInt() > 100 );

		logger.info( "************** Triggering upload" );
		// jmxRunnable.logger.setLevel(Level.DEBUG);
		String result = jmxRunnable.uploadMetrics( 1 );
		assertFalse( "Did not find exception in results", result.contains( "Exception" ) );
	}

	@Test
	public void verify_java_collection_for_multiple_services ()
			throws Exception {

		logger.info( InitializeLogging.TC_HEAD );

		if ( !isSetupOk() )
			return;

		String[] services = { "CsAgent_8011", "Cssp3ReferenceMq_8241", "activemq_8161" };
		ObjectNode commonAndCustomResults = performJavaCommonAndCustomCollection( services, testDbHost );

		ArrayList<String> servicesAvailable = jacksonMapper.readValue(
			commonAndCustomResults.at( "/attributes/servicesAvailable" ).traverse(),
			new TypeReference<ArrayList<String>>() {
			} );
		assertThat( servicesAvailable )
			.as( "servicesAvailable" )
			.contains( "CsAgent_8011", "Cssp3ReferenceMq_8241" );

		assertThat( commonAndCustomResults.at( "/data/openFiles_CsAgent_8011/0" ).asInt() )
			.as( "open files" )
			.isGreaterThan( 100 );

		assertThat( commonAndCustomResults.at( "/data/heapUsed_CsAgent_8011/0" ).asInt() )
			.as( "heap used" )
			.isGreaterThan( 10 );

		assertThat( commonAndCustomResults.at( "/data/openFiles_Cssp3ReferenceMq_8241/0" ).asInt() )
			.as( "/data/Cssp3ReferenceMq_8241/0" )
			.isGreaterThan( 250 );

		ObjectNode agentCustomResults = (ObjectNode) commonAndCustomResults.get( "custom" + services[0] );

		logger.info( "agentCustomResults: \n {}", CSAP.jsonPrint( jacksonMapper, agentCustomResults ) );

		assertThat( agentCustomResults.at( "/data/SpringMvcRequests/0" ).asInt() )
			.as( "SpringMvcRequests is in delta mode - so unless we collect twice, it will be 0" )
			.isLessThan( 10 );

		assertThat( agentCustomResults.at( "/data/OsCommandsCounter/0" ).asInt() )
			.as( "/data/UptimeInSeconds/0" )
			.isGreaterThan( 1 );

		ObjectNode mqCustomResults = (ObjectNode) commonAndCustomResults.get( "custom" + services[2] );

		logger.info( "mqCustomResults: \n {}", CSAP.jsonPrint( jacksonMapper, mqCustomResults ) );

		assertThat( mqCustomResults.at( "/data/TotalConsumerCount/0" ).asInt() )
			.as( "TotalConsumerCount" )
			.isGreaterThan( 5 );
	}

	@Test
	public void apache_web_server_collection ()
			throws Exception {

		logger.info( InitializeLogging.TC_HEAD );

		if ( !isSetupOk() )
			return;

		// csapApp.shutdown();
		ServiceCollector applicationCollector = new ServiceCollector( csapApplication,
			osManager, 30, false );

		// This will trigger a remote procedure call
		applicationCollector.shutdown();

		CSAP.setLogToDebug( HttpCollector.class.getName() );
		// CSAP.setLogToInfo( OsManager.class.getName(), Level.DEBUG );
		applicationCollector.testHttpCollection( 10000 );
		CSAP.setLogToInfo( HttpCollector.class.getName() );

		String[] services = { "httpd_8080" }; // "Cssp3ReferenceMq_8241"
		// "CsAgent_8011"
		ObjectNode httpStatistics = applicationCollector.getCSVdata( false, services, 999, 0, "CustomJmxIsBeingUsed" );

		logger.info( "Results: \n {}",
			CSAP.jsonPrint( jacksonMapper, httpStatistics ) );

		assertThat(
			httpStatistics.at( "/data/IdleWorkers/0" ).asInt() )
				.as( "Verifying Idle Workers" )
				.isEqualTo( 99 );

		assertThat(
			httpStatistics.at( "/data/BrokenConfg/0" ).asInt() )
				.as( "Verifying Broken Config" )
				.isEqualTo( 0 );

		List<String> servicesAvailable = jacksonMapper.readValue(
			httpStatistics.at( "/attributes/servicesAvailable" ).traverse(), new TypeReference<List<String>>() {
			} );
		assertThat( servicesAvailable )
			.as( "servicesAvailable" )
			.contains( "httpd_8080" );

	}

	@Test
	public void mongo_test_collection ()
			throws Exception {

		logger.info( InitializeLogging.TC_HEAD );

		if ( !isSetupOk() )
			return;

		// csapApp.shutdown();
		ServiceCollector applicationCollector = new ServiceCollector( csapApplication,
			osManager, 30, false );

		// This will trigger a remote procedure call
		applicationCollector.shutdown();

		applicationCollector.testHttpCollection( 10000 );

		String[] mongoServices = { "mongoDb_27017" }; // "Cssp3ReferenceMq_8241"
		// "CsAgent_8011"
		ObjectNode mongoStatistics = applicationCollector.getCSVdata( false, mongoServices, 999, 0,
			"CustomJmxIsBeingUsed" );
		logger.info( "Results: \n"
				+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( mongoStatistics ) );

		assertThat(
			mongoStatistics.at( "/data/MongoActiveConnections/0" ).asInt() )
				.as( "Verifying active connections" )
				.isEqualTo( 14 );
		assertThat(
			mongoStatistics.at( "/data/MongoKbTotalIn/0" ).asInt() )
				.as( "Verifying Total network bytes in" )
				.isGreaterThan( 0 );
		assertThat(
			mongoStatistics.at( "/data/MongoKbTotalAssumedType/0" ).asDouble() )
				.as( "Verifying Mongo hack for optional $numberLong works" )
				.isEqualTo( 581.2 );

	}

}
