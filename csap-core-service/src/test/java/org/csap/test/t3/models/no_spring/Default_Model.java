/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.test.t3.models.no_spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.csap.agent.model.Application;
import org.csap.test.InitializeLogging;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author someDeveloper
 */

public class Default_Model {

	final static private Logger logger = LoggerFactory.getLogger( Default_Model.class );

	final static public File csapApplicationDefinition = new File(
		Default_Model.class.getResource( "/org/csap/test/data/DEFAULT_APPLICATION.json" ).getPath() );

	@BeforeClass
	public static void setUpBeforeClass ()
			throws Exception {
		InitializeLogging.printTestHeader( logger.getName() );

		Application.setJvmInManagerMode( false );
		csapApplication = new Application();
		csapApplication.setAutoReload( false );

		assertThat( csapApplication.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();

		logger.info( InitializeLogging.SETUP_HEAD + "Using: " + csapApplicationDefinition.getAbsolutePath() );
	}

	@AfterClass
	public static void tearDownAfterClass ()
			throws Exception {
	}

	// @Inject
	static Application csapApplication;

	@Before
	public void setUp ()
			throws Exception {

	}

	@After
	public void tearDown ()
			throws Exception {
	}



	@Test
	public void default_application_loaded () {

		logger.info( InitializeLogging.TC_HEAD );

		assertThat( csapApplication.lifeCycleSettings().getAdminToAgentTimeoutSeconds() )
			.as( "agent time out" )
			.isEqualTo( 6 );

		assertThat( csapApplication.lifeCycleSettings().isEventPublishEnabled() )
			.as( "isEventPublishEnabled" )
			.isFalse();

		logger.info( "infra settings: {}", csapApplication.lifeCycleSettings().getInfraTests() );
		assertThat( csapApplication.lifeCycleSettings().getInfraTests().getCpuLoopsMillions() )
			.as( "cpu loops" )
			.isEqualTo( 1 );

	}


}
