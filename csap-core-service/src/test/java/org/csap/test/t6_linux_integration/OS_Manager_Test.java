package org.csap.test.t6_linux_integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import org.csap.agent.CsapCoreService;
import org.csap.agent.model.Application;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.services.OsManager;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = CsapCoreService.class)
@ActiveProfiles("junit")
public class OS_Manager_Test {

	final static private Logger logger = LoggerFactory.getLogger( OS_Manager_Test.class );

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Boot_Container_Test.printTestHeader( logger.getName() );
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {

		File csapApplicationDefinition = new File(
				getClass().getResource( "/org/csap/test/data/test_application_model.json" ).getPath() );

		logger.info( Boot_Container_Test.TC_HEAD + "Loading test configuration: \n"
				+ csapApplicationDefinition.getAbsolutePath() );


		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) ).as( "No Errors or warnings" ).isTrue();
		StringBuffer parsingResults = csapApp.getLastTestParseResults() ;

		//logger.warn( "Hack agent until new versions deployed" );
		//csapApp.getServiceInstanceCurrentHost( "CsAgent_8011" ).setServerType( ServiceDefinition.CSSP3 );
	}

	@After
	public void tearDown() throws Exception {
	}

	@Inject
	Application csapApp;

	@Inject
	OsManager osManager;

	@Test
	public void verify_parsing_of_os_processing_priority() throws JsonProcessingException, IOException {

		osManager.checkForProcessStatusUpdate();

		ServiceInstance csAgentInstance = csapApp.getServiceInstanceAnyPackage( Application.AGENT_NAME_PORT );

		assertThat( csAgentInstance.getOsProcessPriority() )
				.isEqualTo( "-12" );

		assertThat( csAgentInstance.getCurrentProcessPriority() )
				.isEqualTo( "0" );

	}

	ObjectMapper jacksonMapper = new ObjectMapper();

	@Test
	public void verify_parsing_of_os_data_for_service() throws JsonProcessingException, IOException {

		osManager.checkForProcessStatusUpdate();

		JsonNode serviceRuntime = osManager.getServiceMetrics( true ).at( "/ps/CsAgent" );

		logger.info( "CsAgent Runtime: {}",
				jacksonMapper
						.writerWithDefaultPrettyPrinter()
						.writeValueAsString( serviceRuntime ) );

		assertThat( serviceRuntime.at( "/cpuUtil" ).asDouble() )
				.as( "CsAgent.cpuUtil" )
				.isGreaterThan( 0.0 );

		assertThat( serviceRuntime.at( "/currentProcessPriority" ).asInt() )
				.as( "CsAgent.currentProcessPriority" )
				.isEqualTo( -12 );

		// JsonPath.read(metricsNode.toString(), "$.ps.CsAgent.cpuUtil"));
	}

	@Test
	public void verify_host_runtime_collection() throws JsonProcessingException, IOException {

		JsonNode hostStats = osManager.getHostRuntime().at( "/hostStats" );

		logger.info( "CsAgent Runtime: {}",
				jacksonMapper
						.writerWithDefaultPrettyPrinter()
						.writeValueAsString( hostStats ) );

		assertThat( hostStats.at( "/cpuCount" ).asInt() )
				.as( "cpuCount" )
				.isEqualTo( 8 );

		assertThat( hostStats.at( "/memoryAggregateFreeMb" ).asInt() )
				.as( "memoryAggregateFreeMb" )
				.isEqualTo( 14441 );

		// JsonPath.read(metricsNode.toString(), "$.ps.CsAgent.cpuUtil"));
	}


}
