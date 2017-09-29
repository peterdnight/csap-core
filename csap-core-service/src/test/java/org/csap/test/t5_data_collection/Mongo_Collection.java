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
public class Mongo_Collection {

	final static private Logger logger = LoggerFactory.getLogger( Mongo_Collection.class );

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
		Mongo_Collection.class.getResource( "application-collection.json" ).getPath() );

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
	public void mongo_collection_remote ()
			throws Exception {

		logger.info( InitializeLogging.TC_HEAD );

		if ( !isSetupOk() || testMongoCollection == null )
			return;

		File csapApplicationDefinition = new File( testMongoCollection );
		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();

		// csapApp.shutdown();
		ServiceCollector serviceCollector = new ServiceCollector( csapApplication,
			osManager, 30, false );

		serviceCollector.shutdown();

		// This will trigger a remote procedure call with sufficient time to
		// process
		CSAP.setLogToDebug( ServiceCollector.class.getName() );
		serviceCollector.testHttpCollection( 5000 );
		CSAP.setLogToInfo( ServiceCollector.class.getName() );

		String[] mongoServices = { "mongoDb_27017" }; // "Cssp3ReferenceMq_8241"
		// "CsAgent_8011"
		ObjectNode mongoStatistics = serviceCollector.getCSVdata( false, mongoServices, 999, 0,
			"CustomJmxIsBeingUsed" );
		logger.info( "Results: \n"
				+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( mongoStatistics ) );

		assertThat(
			mongoStatistics.at( "/data/MongoActiveConnections/0" ).isMissingNode() ).isFalse();

		assertThat(
			mongoStatistics.at( "/data/MongoActiveConnections/0" ).asInt() )
				.as( "Verifying active connections" )
				.isGreaterThan( 4 );
	}

	@Test
	public void mongo_collection_http_timeout ()
			throws Exception {

		logger.info( InitializeLogging.TC_HEAD );

		if ( !isSetupOk() || testMongoCollection == null )
			return;

		File csapApplicationDefinition = new File( testMongoCollection );
		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();
		
		
		// csapApp.shutdown();
		ServiceCollector applicationCollector = new ServiceCollector( csapApplication,
			osManager, 30, false );

		applicationCollector.shutdown();

		// This will trigger a remote procedure call with very short wait time
		applicationCollector.testHttpCollection( 1 );

		String[] mongoServices = { "mongoDb_27017" }; // "Cssp3ReferenceMq_8241"
		// "CsAgent_8011"
		ObjectNode mongoStatistics = applicationCollector.getCSVdata( false, mongoServices, 999, 0,
			"CustomJmxIsBeingUsed" );
		logger.info( "Results: \n"
				+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( mongoStatistics ) );

		assertThat(
			mongoStatistics.at( "/data/MongoActiveConnections/0" ).isMissingNode() ).isFalse();

		assertThat(
			mongoStatistics.at( "/data/MongoActiveConnections/0" ).asInt() )
				.as( "Verifying active connections" )
				.isEqualTo( 0 );
	}

}
