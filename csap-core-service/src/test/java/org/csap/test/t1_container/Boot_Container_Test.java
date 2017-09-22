package org.csap.test.t1_container;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;

import javax.inject.Inject;

import org.csap.agent.CsapCoreService;
import org.csap.agent.input.http.api.AgentApi;
import org.csap.agent.input.http.ui.windows.CorePortals;
import org.csap.agent.misc.HelloService;
import org.csap.agent.model.Application;
import org.csap.test.InitializeLogging;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@RunWith ( SpringRunner.class )
@SpringBootTest ( classes = CsapCoreService.class )
@ActiveProfiles ( "junit,company" )
public class Boot_Container_Test {

	final static private Logger logger = LoggerFactory.getLogger( Boot_Container_Test.class );

	public static String TC_HEAD = InitializeLogging.TC_HEAD;
	public static String SETUP_HEAD = InitializeLogging.SETUP_HEAD;

	@BeforeClass
	public static void setUpBeforeClass ()
			throws Exception {
		InitializeLogging.printTestHeader( logger.getName() );

	}

	/** references should migrate **/
	public static void printTestHeader ( String description ) {

		InitializeLogging.printTestHeader( description );

	}

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	Application csapApplication;

	@Test
	public void default_application_loaded () {
		logger.info( Boot_Container_Test.TC_HEAD );

		assertThat( applicationContext.getBeanDefinitionCount() )
			.as( "Spring Bean count" )
			.isGreaterThan( 100 );

		assertThat( applicationContext.getBean( CorePortals.class ) )
			.as( "SpringRequests controller loaded" )
			.isNotNull();

		assertThat( applicationContext.getBean( HelloService.class ) )
			.as( "Demo_DataAccessObject  loaded" )
			.isNotNull();
		

//		assertThat( csapApplication.lifeCycleSettings().getEventDataUser() )
//			.as( "event user" )
//			.isEqualTo( "$user" );
		
		

	}

	@Inject
	private StandardPBEStringEncryptor encryptor;

	@Test
	public void testEncryption () {

		String testSample = "Testing encyrpt";
		String encSample = encryptor.encrypt( testSample );

		String message = "Encoding of  " + testSample + " is " + encSample;
		logger.info( TC_HEAD + message );

		assertThat( testSample ).isNotEqualTo( encSample );

		assertThat( testSample ).isEqualTo( encryptor.decrypt( encSample ) );
		// assertTrue( encryptor.decrypt( encSample).equals( testSample) ) ;

	}

	ObjectMapper jacksonMapper = new ObjectMapper();

	@Inject
	@Qualifier ( "analyticsRest" )
	private RestTemplate analyticsTemplate;

	
	@Ignore
	@Test
	public void testRest ()
			throws JsonProcessingException {

		String url = "http://csaptools.yourcompany.com/admin/api/model/clusters";
		ObjectNode restResponse = analyticsTemplate.getForObject( url, ObjectNode.class );

		logger.info( "Url: {} response: {}", url, jacksonMapper.writerWithDefaultPrettyPrinter()
			.writeValueAsString( restResponse ) );

	}

	@Autowired
	AgentApi performanceHost;

	@Ignore
	@Test
	public void validate_spring_expression ()
			throws Exception {

		// String url = "http://csaptools.yourcompany.com/admin/api/clusters";
		String url = "http://testhost.yourcompany.com:8011/CsAgent/api/collection/application/CsAgent_8011/30/10";
		ObjectNode restResponse = analyticsTemplate.getForObject( url, ObjectNode.class );

		logger.info( "Url: {} response: {}", url, jacksonMapper.writerWithDefaultPrettyPrinter()
			.writeValueAsString( restResponse ) );

		ArrayList<Integer> publishvals = jacksonMapper.readValue(
			restResponse.at( "/data/publishEvents" )
				.traverse(),
			new TypeReference<ArrayList<Integer>>() {
			} );

		int total = publishvals.stream().mapToInt( Integer::intValue ).sum();

		logger.info( "Total: {} publishvals: {}", total, publishvals );

		EvaluationContext context = new StandardEvaluationContext();
		context.setVariable( "total", total );

		ExpressionParser parser = new SpelExpressionParser();
		Expression exp = parser.parseExpression( "#total.toString()" );
		logger.info( "SPEL evalutation: {}", (String) exp.getValue( context ) );

		exp = parser.parseExpression( "#total > 99" );
		logger.info( "#total > 99 SPEL evalutation: {}", (Boolean) exp.getValue( context ) );

		exp = parser.parseExpression( "#total > 3" );
		logger.info( "#total > 3 SPEL evalutation: {}", (Boolean) exp.getValue( context ) );

	}

}
