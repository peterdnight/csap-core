package org.csap.test.t7_service_integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.csap.agent.CSAP;
import org.csap.agent.CsapCoreService;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.Application;
import org.csap.test.InitializeLogging;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@RunWith ( SpringRunner.class )
@SpringBootTest ( classes = CsapCoreService.class )
@ActiveProfiles ( profiles = { "junit", "company" } )
public class Csap_Event_Test {
	final static private Logger logger = LoggerFactory.getLogger( Csap_Event_Test.class );

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

		Application.setJvmInManagerMode( false );
		csapApplication.setAutoReload( false );

	}

	@After
	public void tearDown ()
			throws Exception {
	}

	File testDefinitionModel = new File(getClass().getResource( "/org/csap/test/data/test_application_model.json" ).getPath() );
	@Inject
	Application csapApplication;

	@Inject
	CsapEventClient csapEventClient;

	DateFormat shortFormatter = new SimpleDateFormat( "HH:mm:ss MMM-dd" );

	@Test
	public void publish_event_with_remote_disabled ()
			throws Exception {

		

		logger.info( "Loading definition: {}", testDefinitionModel.getAbsolutePath() );
		assertThat( csapApplication.loadDefinitionForJunits( false, testDefinitionModel ) ).as( "No Errors or warnings" ).isTrue();

		csapEventClient.publishEvent(
			"/junit/summaryTest",
			shortFormatter.format( new Date() ), null,
			csapApplication.getCapabilitySummary( false ) );

		assertThat( csapEventClient.waitForFlushOfAllEvents( 5 ) ).isTrue();

	}

	@Test
	public void publish_simple_json_event ()
			throws Exception {

		File configFile = new File( this.getClass().getResource( "appWithEventPublishEnabled.json" ).getPath() );
		logger.info( InitializeLogging.TC_HEAD + " Loading test application with remote enabled: \n" + configFile );

		assertThat( csapApplication.loadDefinitionForJunits( false, configFile ) )
			.isTrue()
			.as( "Definition parsed cleanly" );

		assertThat( csapApplication.lifeCycleSettings().isEventPublishEnabled() )
			.isTrue();

		if ( !csapApplication.isCompanyVariableConfigured( "test.variables.user" ) ) {
			logger.info( "company variable not set - skipping test" );
			return;
		}

		CSAP.setLogToDebug( csapEventClient.getClass().getName() );
		// fail("Not yet implemented");
		csapEventClient.getNumberOfPostedEventsAndReset(); // resets to 0
		csapEventClient.publishEvent(
			"/junit/summaryTest",
			shortFormatter.format( new Date() ), null,
			csapApplication.getCapabilitySummary( false ) );

		boolean flushedEvents = csapEventClient.waitForFlushOfAllEvents( 5 );

		CSAP.setLogToDebug( csapEventClient.getClass().getName() );
		// csapEventClient.shutdown();

		assertThat( csapEventClient.getBacklogCount() )
			.isEqualTo( 0 )
			.as( "All messages posted" );

		assertThat( csapEventClient.getNumberOfPostedEventsAndReset() )
			.isGreaterThanOrEqualTo( 1 )
			.as( "message posted" );

		assertTrue( "Flushed Events", flushedEvents );

		// todo : add checks for event
	}

	final static String TEST_CONTENT = "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789";

	@Test
	public void publish_event_50kb_payload ()
			throws Exception {
		File configFile = new File( this.getClass().getResource( "appWithEventPublishEnabled.json" ).getPath() );
		logger.info( InitializeLogging.TC_HEAD + " Loading test application with remote enabled: \n" + configFile );

		assertThat( csapApplication.loadDefinitionForJunits( false, configFile ) )
			.isTrue()
			.as( "Definition parsed cleanly" );

		assertThat( csapEventClient.getLifeCycleMetaData().isEventPublishEnabled() )
			.isTrue();

		if ( !csapApplication.isCompanyVariableConfigured( "test.variables.user" ) ) {
			logger.info( "company variable not set - skipping test" );
			return;
		}

		StringBuilder testPostContent = new StringBuilder();
		for ( int i = 0; i < 500; i++ ) {
			testPostContent.append( TEST_CONTENT );
		}

		logger.info( "Posting event with size: {}", testPostContent.length() );
		csapEventClient.getNumberOfPostedEventsAndReset() ;
		csapEventClient.publishEvent(
			"/junit/summaryTest",
			shortFormatter.format( new Date() ),
			testPostContent.toString() );

		boolean flushedEvents = csapEventClient.waitForFlushOfAllEvents( 5 );
		// csapEventClient.shutdown();

		assertThat( csapEventClient.getBacklogCount() )
			.isEqualTo( 0 )
			.as( "All messages posted" );

		assertThat( csapEventClient.getNumberOfPostedEventsAndReset() )
			.isGreaterThanOrEqualTo( 1 )
			.as( "StartUp messages plus the above" );

		assertTrue( "Flushed Events", flushedEvents );

		// todo : add checks for event
	}

	@Test
	public void publish_event_truncate_payload ()
			throws Exception {
		File configFile = new File( this.getClass().getResource( "appWithEventPublishEnabled.json" ).getPath() );
		logger.info( InitializeLogging.TC_HEAD + " Loading test application with remote enabled: \n" + configFile );

		assertThat( csapApplication.loadDefinitionForJunits( false, configFile ) )
			.isTrue()
			.as( "Definition parsed cleanly" );

		assertThat( csapEventClient.getLifeCycleMetaData().isEventPublishEnabled() )
			.isTrue();

		if ( !csapApplication.isCompanyVariableConfigured( "test.variables.user" ) ) {
			logger.info( "company variable not set - skipping test" );
			return;
		}

		csapEventClient.setMaxTextSize( 5000 );
		int overflow = (csapEventClient.getMaxTextSize() * 2) / TEST_CONTENT.length(); // 2MB
																						// is
																						// max
																						// size
		StringBuilder testPostContent = new StringBuilder();
		for ( int i = 0; i < overflow; i++ ) {
			testPostContent.append( TEST_CONTENT );
		}

		logger.info( "Posting event with size: {}", testPostContent.length() );
		// fail("Not yet implemented");

		csapEventClient.getNumberOfPostedEventsAndReset();
		csapEventClient.publishEvent(
			"/junit/summaryTest",
			shortFormatter.format( new Date() ),
			testPostContent.toString() );

		boolean flushedEvents = csapEventClient.waitForFlushOfAllEvents( 10 );
		// csapEventClient.shutdown();

		assertThat( csapEventClient.getBacklogCount() )
			.isEqualTo( 0 )
			.as( "All messages posted" );

		assertThat( csapEventClient.getNumberOfPostedEventsAndReset() )
			.isGreaterThanOrEqualTo( 1 )
			.as( "StartUp messages plus the above" );

		assertTrue( "Flushed Events", flushedEvents );

		// todo : add checks for event
	}

	@Ignore
	@Test
	public void publish_event_overflow_payload ()
			throws Exception {
		File configFile = new File( this.getClass().getResource( "appWithEventPublishEnabled.json" ).getPath() );
		logger.info( InitializeLogging.TC_HEAD + " Loading test application with remote enabled: \n" + configFile );

		assertThat( csapApplication.loadDefinitionForJunits( false, configFile ) )
			.isTrue()
			.as( "Definition parsed cleanly" );

		assertThat( csapEventClient.getLifeCycleMetaData().isEventPublishEnabled() )
			.isTrue();

		if ( !csapApplication.isCompanyVariableConfigured( "test.variables.user" ) ) {
			logger.info( "company variable not set - skipping test" );
			return;
		}

		csapEventClient.setMaxTextSize( 1024 * 1024 * 3 );

		int overflow = (csapEventClient.getMaxTextSize() * 2) / TEST_CONTENT.length(); // 2MB
																						// is
																						// max
																						// size

		StringBuilder testPostContent = new StringBuilder();
		for ( int i = 0; i < overflow; i++ ) {
			testPostContent.append( TEST_CONTENT );
		}

		logger.info( "Posting event with size: {}", testPostContent.length() );
		// fail("Not yet implemented");
		csapEventClient.publishEvent(
			"/junit/summaryTest",
			shortFormatter.format( new Date() ),
			testPostContent.toString() );

		boolean flushedEvents = csapEventClient.waitForFlushOfAllEvents( 20 );
		// csapEventClient.shutdown();

		assertThat( csapEventClient.getBacklogCount() )
			.isEqualTo( 0 )
			.as( "All messages posted" );

		assertThat( csapEventClient.getEventPostFailures() )
			.isEqualTo( 1 )
			.as( "Large payload will fail to post" );

		assertThat( csapEventClient.getNumberOfPostedEventsAndReset() )
			.isGreaterThanOrEqualTo( 1 )
			.as( "StartUp messages plus the above" );

		assertTrue( "Flushed Events", flushedEvents );

		// todo : add checks for event
	}

	ObjectMapper jacksonMapper = new ObjectMapper();

	@Test
	public void publish_jmx_metrics_with_remote_disabled ()
			throws InterruptedException, IOException {
		// fail("Not yet implemented");
		logger.info( "Loading definition: {}", testDefinitionModel.getAbsolutePath() );
		assertThat( csapApplication.loadDefinitionForJunits( false, testDefinitionModel ) ).as( "No Errors or warnings" ).isTrue();
		
		File sampleMetricsData = new File(
			getClass().getResource( "csap-event-sample-data.json" ).getPath() );

		ObjectNode attJson = (ObjectNode) jacksonMapper.readTree( FileUtils.readFileToString( sampleMetricsData ) );

		csapEventClient.publishEvent(
			"/junit/jmx30Post",
			shortFormatter.format( new Date() ), null, attJson );

		assertTrue( "Flushed Events", csapEventClient.waitForFlushOfAllEvents( 5 ) );

		// todo : add checks for event
	}

}
