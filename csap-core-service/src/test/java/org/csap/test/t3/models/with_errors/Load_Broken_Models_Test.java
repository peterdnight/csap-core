package org.csap.test.t3.models.with_errors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.csap.agent.CSAP;
import org.csap.agent.CsapCoreService;
import org.csap.agent.model.Application;
import org.csap.agent.model.DefinitionParser;
import org.csap.agent.model.HttpdIntegration;
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

@RunWith ( SpringRunner.class )
@SpringBootTest ( classes = CsapCoreService.class )
@ActiveProfiles ( "junit" )
public class Load_Broken_Models_Test {

	final static private Logger logger = LoggerFactory.getLogger( Load_Broken_Models_Test.class );

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

		logger.debug( "Deleting: {}", getHttpdConfig().getHttpdWorkersFile().getParentFile().getAbsolutePath() );

		Application.setJvmInManagerMode( false );
		csapApp.setAutoReload( false );

		FileUtils.deleteQuietly( getHttpdConfig().getHttpdWorkersFile().getParentFile() );

		logger.info( Boot_Container_Test.TC_HEAD );
	}

	@After
	public void tearDown ()
			throws Exception {
	}

	@Inject
	Application csapApp;

	@Test
	public void application_definition_with_same_jvm_fails_to_load ()
			throws JsonProcessingException,
			IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "application-jvm-multiple-packages.json" ).getPath() );

		Pattern multiple_releases_pattern = Pattern.compile( ".*Service: Factory2Sample.*found in multiple release packages.*",
			Pattern.DOTALL );

		assertThat(
			csapApp.getParser().parseConfig( true, csapApplicationDefinition ).toString() )
				.as( "Warning messages" )
				.contains( CSAP.CONFIG_PARSE_ERROR )
				.doesNotContain( CSAP.CONFIG_PARSE_WARN )
				.matches( multiple_releases_pattern )
				.contains( "it must be unique" );

	}

	@Test
	public void load_application_with_invalid_json_throws_exception ()
			throws JsonProcessingException, IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/application_with_invalid_format.json" ).getPath() );

		assertThatThrownBy( () -> {
			csapApp.getParser().parseConfig( true, csapApplicationDefinition );
		} )
			.as( "JsonProcessingException is thrown when malformed mark up" )
			.isInstanceOf( JsonProcessingException.class )
			.hasMessageContaining(
				"Unexpected character (':' (code 58)): was expecting comma to separate" );

	}

	@Test
	public void load_application_with_missing_jvm_throws_exception ()
			throws JsonProcessingException, IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "/org/csap/test/data/application_with_jvm_missing_error.json" ).getPath() );

		assertThatThrownBy( () -> {
			csapApp.getParser().parseConfig( true, csapApplicationDefinition );
		} )
			.as( "IOException is thrown if a service referred to in cluster is not present" )
			.isInstanceOf( IOException.class )
			.hasMessageContaining( Application.MISSING_SERVICE_MESSAGE );

	}

	@Test
	public void load_application_with_duplicate_port ()
			throws JsonProcessingException, IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "application-duplicate-port.json" ).getPath() );

		StringBuffer parsingResults = csapApp.getParser().parseConfig( true, csapApplicationDefinition );
		logger.info( parsingResults.toString() );

		assertThat( parsingResults )
			.as( "Duplicate ports parse message" )
			.contains( CSAP.CONFIG_PARSE_ERROR )
			.contains( DefinitionParser.ERROR_DUPLICATE_HOST_PORT );

	}

	@Test
	public void load_application_with_invalid_name_throws_Exception ()
			throws JsonProcessingException, IOException {

		File csapApplicationDefinition = new File(
			getClass().getResource( "application-bad-service-name.json" ).getPath() );

		assertThatThrownBy( () -> {
			csapApp.getParser().parseConfig( true, csapApplicationDefinition );
		} )
			.as( "IOException is thrown when service name contains invalid characters" )
			.isInstanceOf( IOException.class )
			.hasMessageContaining( DefinitionParser.ERROR_INVALID_CHARACTERS );

	}

	private HttpdIntegration getHttpdConfig () {
		return csapApp.getHttpdIntegration();
	}
}
