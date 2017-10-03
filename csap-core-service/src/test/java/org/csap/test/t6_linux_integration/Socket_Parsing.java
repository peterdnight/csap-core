/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.test.t6_linux_integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.csap.agent.linux.OsCommandRunner;
import org.csap.agent.linux.ServiceResourceRunnable;
import org.csap.agent.model.Application;
import org.csap.agent.model.ServiceInstance;
import org.csap.test.t1_container.Boot_Container_Test;
import org.csap.test.t3.models.no_spring.Model_As_Agent;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author someDeveloper
 */
public class Socket_Parsing {

	final static private Logger logger = LoggerFactory.getLogger( Socket_Parsing.class );

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Boot_Container_Test.printTestHeader( logger.getName() );

		Application.setJvmInManagerMode( false );

		osCollector = new ServiceResourceRunnable( csapApp, new OsCommandRunner( 2, 2, "dummy" ) );
		csapApp = new Application();
		csapApp.setAutoReload( false );

		csapApp.initialize();

		assertThat( csapApp.loadDefinitionForJunits( false, Model_As_Agent.csapApplicationDefinition ) )
				.as( "No Errors or warnings" )
				.isTrue();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	//@Inject
	static Application csapApp;
	static ServiceResourceRunnable osCollector;

	@Before
	public void setUp() throws Exception {

	}

	@After
	public void tearDown() throws Exception {
	}

	@Ignore
	@Test
	public void verify_agent_sockets_redhat6() {

		ServiceInstance agentInstance = csapApp.getServiceInstanceCurrentHost( "CsAgent_8011" ) ;
		assertThat( agentInstance.isSpringBoot() )
				.as( "server override" )
				.isTrue();

		agentInstance.setPid( Arrays.asList( "4149" ) );

		osCollector.testSocketParsing( agentInstance, true );
		//errer
		assertThat( agentInstance.getSocketCount() )
				.as( "socket count" )
				.isEqualTo( 15 );

	}
	@Test
	public void verify_agent_sockets_redhat7() {

		ServiceInstance agentInstance = csapApp.getServiceInstanceCurrentHost( "CsAgent_8011" ) ;
		assertThat( agentInstance.isSpringBoot() )
				.as( "server override" )
				.isTrue();

		agentInstance.setPid( Arrays.asList( "4149" ) );

		osCollector.testSocketParsing( agentInstance, false );

		assertThat( agentInstance.getSocketCount() )
				.as( "socket count" )
				.isEqualTo( 32 );

	}
}
