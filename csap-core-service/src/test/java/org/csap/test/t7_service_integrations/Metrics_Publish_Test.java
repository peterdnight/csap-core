package org.csap.test.t7_service_integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.csap.agent.CSAP;
import org.csap.agent.CsapCoreService;
import org.csap.agent.model.Application;
import org.csap.agent.stats.MetricsPublisher;
import org.csap.agent.stats.NagiosIntegration;
import org.csap.test.t1_container.Boot_Container_Test;
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
public class Metrics_Publish_Test {
	final static private Logger logger = LoggerFactory.getLogger( Metrics_Publish_Test.class );

	@BeforeClass
	public static void setUpBeforeClass ()
			throws Exception {
		Boot_Container_Test.printTestHeader( logger.getName() );
	}

	@AfterClass
	public static void tearDownAfterClass ()
			throws Exception {
	}

	@Before
	public void setUp ()
			throws Exception {

		logger.info( Boot_Container_Test.TC_HEAD + "Deleting: "
				+ csapApp.getHttpdIntegration().getHttpdWorkersFile().getParentFile().getAbsolutePath() );

		Application.setJvmInManagerMode( false );
		csapApp.setAutoReload( false );

		FileUtils.deleteQuietly( csapApp.getHttpdIntegration().getHttpdWorkersFile().getParentFile() );

		// String path =
		// Bootstrap.getThreadContextLoader().getResource("clusterConfig.js").getPath();
		// logger.info(GlobalContextTest.TC_HEAD +
		// "Loading test configuration: \n" + path);
		// File testConfig = new File(path);
		// csapApp.parseConfig(false, testConfig);
	}

	@After
	public void tearDown ()
			throws Exception {
	}

	@Inject
	Application csapApp;

	private ObjectMapper jacksonMapper = new ObjectMapper();

	/**
	 *
	 * Scenario: - validate publishing of CSAP data
	 *
	 */
	@Test
	public void verify_publish_to_csaptools ()
			throws JsonProcessingException, IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/test_application_model.json" ).getPath() );

		String message = "Loading a working definition: " + csapApplicationDefinition.getAbsolutePath();
		logger.info( Boot_Container_Test.TC_HEAD + message );

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" ).isTrue();

		assertEquals( message + "Capability Name present", "TestDefinitionForAutomatedTesting",
			csapApp.getName() );

		// Test MetricsPublishing
		assertTrue( "Metrics Publishing Enabled",
			csapApp.lifeCycleSettings().isMetricsPublication() );

		ObjectNode pubInfoJson = jacksonMapper.createObjectNode();
		pubInfoJson.put( "type", "csapCallHome" );
		pubInfoJson.put( "intervalInSeconds", -1 );
		pubInfoJson.put( "url", "http://csaptools.yourcompany.com/CsapGlobalAnalytics/rest/vm/healthInfo2" );
		pubInfoJson.put( "token", "notUsed" );

		// { "type" : "csapCallHome", "intervalInSeconds" : 300 , "url":
		// "http://csaptools.yourcompany.com/CsapGlobalAnalytics/api/vm/health" ,
		// "token": "notUsed"}
		CSAP.setLogToInfo( MetricsPublisher.class.getName() );
		MetricsPublisher publisher = new MetricsPublisher( csapApp, pubInfoJson );
		publisher.setIntegrationEnabled( true ); // Uncomment to hit
		publisher.run();

		assertFalse( "Publish succeeded", publisher.getLastResults().contains( "Failed" ) );
	}

	@Test
	public void verify_publish_to_nagios_eet ()
			throws JsonProcessingException, IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/test_application_model.json" ).getPath() );

		String message = "Loading a working definition: " + csapApplicationDefinition.getAbsolutePath();
		logger.info( Boot_Container_Test.TC_HEAD + message );

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" ).isTrue();

		assertEquals( message + "Capability Name present", "TestDefinitionForAutomatedTesting",
			csapApp.getName() );

		// Test MetricsPublishing
		assertTrue( "Metrics Publishing Enabled",
			csapApp.lifeCycleSettings().isMetricsPublication() );
		
		
		
		if ( ! csapApp.isCompanyVariableConfigured( "test.variables.nagios-url" ) ) {
			logger.info( "company variable not set - skipping test"  );
			return ;
		}
		String nagiosUrl = csapApp.getCompanyConfiguration( "test.variables.nagios-url", "" ) ;
		logger.info( "nagiosUrl: {}", nagiosUrl );

		ObjectNode nagiosDefinition = jacksonMapper.createObjectNode();
		nagiosDefinition.put( "type", "nagios" );
		nagiosDefinition.put( "intervalInSeconds", -1 );
		nagiosDefinition.put( "url", nagiosUrl );
		nagiosDefinition.put( "token", csapApp.getCompanyConfiguration( "test.variables.nagios-token", "" ) );
		nagiosDefinition.put( "user", csapApp.getCompanyConfiguration( "test.variables.nagios-user", "" ) );
		nagiosDefinition.put( "pass", csapApp.getCompanyConfiguration( "test.variables.nagios-pass", "" ) );

		CSAP.setLogToInfo( MetricsPublisher.class.getName() );
		CSAP.setLogToDebug( NagiosIntegration.class.getName() );
		MetricsPublisher publisher = new MetricsPublisher( csapApp, nagiosDefinition );
		publisher.setIntegrationEnabled( true );
		publisher.run();

		logger.info( "Results from publish: {}" , publisher.getLastResults() );

		assertThat( publisher.getLastResults() )
			.as( "pubish success" )
			.contains( "<message>OK</message>" );
		
		
		assertTrue( "Checks were processed",
			publisher.getLastResults().contains( "<output>4 checks processed.</output>" ) );
		assertFalse( "Failed not found", publisher.getLastResults().contains( "Failed" ) );
		assertFalse( "Nagios Token Accepted", publisher.getLastResults().contains( "BAD TOKEN" ) );
		assertFalse( "XML Formated correctly", publisher.getLastResults().contains( "BAD XML" ) );
	}

}
