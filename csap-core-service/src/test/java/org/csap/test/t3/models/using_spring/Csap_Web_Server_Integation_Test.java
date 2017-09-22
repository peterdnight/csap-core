package org.csap.test.t3.models.using_spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.csap.agent.CsapCoreService;
import org.csap.agent.model.Application;
import org.csap.agent.model.HttpdIntegration;
import org.csap.agent.model.ServiceInstance;
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

@RunWith(SpringRunner.class)
@SpringBootTest(classes = CsapCoreService.class)
@ActiveProfiles("junit")
public class Csap_Web_Server_Integation_Test {

	final static private Logger logger = LoggerFactory.getLogger( Csap_Web_Server_Integation_Test.class );

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Boot_Container_Test.printTestHeader( logger.getName() );
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {

		logger.info( Boot_Container_Test.TC_HEAD + "Deleting: "
				+ getHttpdConfig().getHttpdWorkersFile().getParentFile().getAbsolutePath() );

		Application.setJvmInManagerMode( false );
		csapApp.setAutoReload( false );

		FileUtils.deleteQuietly( getHttpdConfig().getHttpdWorkersFile().getParentFile() );

	}

	private HttpdIntegration getHttpdConfig() {
		return csapApp.getHttpdIntegration();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Inject
	Application csapApp;

	/**
	 *
	 * Scenario: - load a config file without package definition and 1 jvm and 1 os
	 *
	 */
	@Test
	public void verify_web_server_files_updated_from_model() throws JsonProcessingException, IOException {

		File csapApplicationDefinition = new File(
				getClass().getResource( "web_server_model.json" ).getPath() );

		String message = "Loading a working definition: " + csapApplicationDefinition.getAbsolutePath();
		logger.info( Boot_Container_Test.TC_HEAD + message );

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue();

		assertThat( csapApp.getName() )
				.as( "Name" )
				.isEqualTo( "DEFAULT APPLICATION FOR JUNITS" );


		assertThat( csapApp.getLifecycleList().size() )
				.as( "lifecycles" )
				.isEqualTo( 5 );

		assertThat( csapApp.getSvcToConfigMap().get( "CsAgent" ).size() )
				.as( "agents allocated" )
				.isEqualTo( 4 );
		

		// New instance meta data
		ServiceInstance csAgentInstance = csapApp
				.getServiceInstanceAnyPackage( Application.AGENT_NAME_PORT );

		assertThat( csAgentInstance.getOsProcessPriority() )
				.as( "OS priority" )
				.isEqualTo( "-10" );


		logger.info( "Workers file: " + getHttpdConfig().getHttpdWorkersFile().getAbsolutePath() );
		assertThat( getHttpdConfig().getHttpdWorkersFile() )
				.as( "Web Servers Worker file created" )
				.exists();

		assertThat( contentOf( getHttpdConfig().getHttpdWorkersFile() ) )
				.as( "Both factory and enterprise workers" )
				.contains(
						"worker.Cssp3ReferenceMq_8241localhost.port=8242" )
				.doesNotContain(
						"worker.Cssp3ReferenceMq_8241peter.port=8242" )
				.contains(
						"worker.Cssp3ReferenceMq-dev01_LB8241.port=8242" )
				.contains(
						"worker.Cssp3ReferenceMq-dev002_LB8241.port=8242" )
				.contains(
						"balance_workers=Cssp3ReferenceMq_8241localhost" );

		assertThat( contentOf( getHttpdConfig().getHttpdWorkersFile() ) )
				.as( "Custom Routing Rules" )
				.contains(
						"worker.ServiceWithCustomRouting_LB.method=Next" )
				.contains(
						"worker.ServiceWithCustomRouting_8251localhost.reply_timeout=10000" )
				.contains(
						"worker.ServiceWithCustomRouting_LB.sticky_session=1" );

		assertThat( getHttpdConfig().getHttpdModjkFile() )
				.as( "Web Servers ModJK file created" )
				.exists();

		assertThat( contentOf( getHttpdConfig().getHttpdModjkFile() ) )
				.contains( "JkMount /Cssp3ReferenceMq* Cssp3ReferenceMq_LB" )
				.contains( "JkMount /ServiceWithCustomRouting* ServiceWithCustomRouting_LB" )
				.contains( "JkMount /Cssp3ReferenceMq-dev01* Cssp3ReferenceMq-dev01_LB" )
				.contains( "JkMount /Cssp3ReferenceMq-dev002* Cssp3ReferenceMq-dev002_LB" );
		

		assertThat( getHttpdConfig().getHttpdModReWriteFile() )
				.as( "Web Servers mod rewrite file created" )
				.exists();

		assertThat( contentOf( getHttpdConfig().getHttpdModReWriteFile() ) )
				.contains( "RewriteRule ^/test1/(.*)$  /ServiceWithCustomRouting/$1 [PT]" ) ;

	}

}
