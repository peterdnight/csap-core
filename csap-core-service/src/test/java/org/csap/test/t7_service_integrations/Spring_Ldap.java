package org.csap.test.t7_service_integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import javax.annotation.PostConstruct;
import javax.naming.directory.DirContext;
import javax.naming.ldap.LdapName;

import org.csap.agent.CSAP;
import org.csap.agent.model.ServiceInstance;
import org.csap.security.CsapUser;
import org.csap.security.CsapUserContextCallback;
import org.csap.test.InitializeLogging;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.env.Environment;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 
 * Simple tests to validate Spring LDAP Template.
 * 
 * Similar to sql - LDAP has a DSL for interacting with provider, which in turn
 * is abstracted somewhat by Java nameing apis. Spring Ldap makes this much more
 * developer friendly.
 * 
 * Prior to jumping to code, it is highly recommended to make use of a desktop
 * LDAP browser to browse LDAP tree to familiarize your self with syntax and
 * available attributes.
 * 
 * Softerra ldap browser is nice way to approach
 * 
 * 
 */

@RunWith ( SpringRunner.class )
@TestPropertySource ( locations = "file:${csapTest}/csapSecurity.properties" )
@ActiveProfiles ( profiles = { "junit", "company" } )
public class Spring_Ldap {
	final static private Logger logger = LoggerFactory.getLogger( Spring_Ldap.class );

	@Value ( "${security.dir.url:overWritten}" )
	private String ldapUrl = "example: ldap://your.ldap.com:389/";

	@Value ( "${security.dir.password:overWritten}" )
	private String ldapPass = "overWritten";

	@Value ( "${security.dir.user:overWritten}" )
	private String ldapUser = "overWritten";

	@Value ( "${security.dir.search.user:overWritten}" )
	private String ldapSearchUser = "overWritten";

	//
	@Value ( "${security.dir.tree:overWritten}" )
	private String genericTree = "overWritten";

	@Value ( "${user.name:overWritten}" )
	private String currentUser = "overWritten";

	@Autowired
	private Environment springEnvironment;

	@TestConfiguration
	static class SimpletConfiguration {

	}

	// needed for property placedholder context
	@SpringBootApplication
	static class LdapTestConfiguration {
	}
	
	@Test
	public void verify_setup () throws InterruptedException {

		logger.info( InitializeLogging.TC_HEAD );

		logger.info( "ldapUser: {}, ldapPass: {}, ldapUrl: {}, ldapSearchUser: {}"
				+ "\n current user: {}",
			ldapUser, ldapPass, ldapUrl, ldapSearchUser, currentUser );

		// assertThat( isSetupOk() ).as( "setup ok, ~home/csap/application-company.yml loaded" ).isTrue();
		if ( ! isSetupOk() ) {
			logger.warn( "junits requiring a working ldap to test will be skipped;  update application-company.yml");
			Thread.sleep( 3000 );
		}

	}

	private boolean isSetupOk () {

		//currentUser = springEnvironment.getProperty( "user.name" );
		

		if ( ldapUrl == null
				|| ldapUser == null
				|| ldapSearchUser == null
				|| genericTree == null
				|| currentUser == null )
			return false;
		
		if ( ldapUser.equals( "overWritten" ) ) {
			logger.info( "ldapUser is not set" );
			return false;
		}


		return true;
	}

//	@PostConstruct
//	void printVals () {
//		// ldapUserFull = environment.getProperty( "security.dir.user" );
//		// ldapPass = environment.getProperty( "security.dir.password" );
//		// ldapUrl = environment.getProperty( "security.dir.url" );
//
//
//
//		assertThat( ldapUrl ).isNotNull();
//		assertThat( ldapUser ).isNotNull();
//		assertThat( ldapUser ).isNotEqualTo( "overWritten" );
//		assertThat( ldapPass ).isNotNull();
//	}

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

	}

	@After
	public void tearDown ()
			throws Exception {
	}

	ObjectMapper jacksonMapper = new ObjectMapper();

	/**
	 * 
	 * Scenario: - retrieve user attributes using a generic search id/password
	 * 
	 * @throws IOException
	 * @throws JsonProcessingException
	 * 
	 * 
	 * @see <a href=
	 *      "http://docs.spring.io/spring-ldap/docs/1.3.2.RELEASE/reference/htmlsingle/#introduction-overview">
	 *      Spring LDAP lookup </a>
	 * 
	 */
	@Test
	@SuppressWarnings ( "unchecked" )
	public void validate_user_lookup ()
			throws Exception {


		if ( !isSetupOk() )
			return;
		
		int maxAttempts = 1;
		int numFailures = 0;
		for ( int i = 0; i < maxAttempts; i++ ) {

			logger.info( "\n\n *****************   Attempt: " + i );

			LdapTemplate template = buildContextTemplate();

			StringBuilder builder = new StringBuilder();
			builder.append( "\n\n ==========================" );
			builder.append( "\n LdapTemplate:" );

			logger.info( builder.toString() );

			LdapName ldapName = LdapNameBuilder.newInstance( ldapSearchUser )
				.add( "uid", currentUser )
				.build();

			logger.info( "Looking up: {}", ldapName );

			CsapUser csapUser = null;
			try {
				csapUser = (CsapUser) template.lookup( ldapName, new CsapUser() );
				logger.info( "CsapUser Raw Attributes: \n\t"
						+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
							csapUser.getAttibutesJson() ) );

				assertThat( csapUser.getMail() ).startsWith( currentUser + "@" );

				csapUser = (CsapUser) template.lookup( ldapName, CsapUser.PRIMARY_ATTRIBUTES, new CsapUser() );
				logger.info( "CsapUser.PRIMARY_ATTRIBUTES  filter: \n\t"
						+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
							csapUser.getAttibutesJson() ) );

				assertThat( csapUser.getMail() ).startsWith( currentUser + "@" );

			} catch (Exception e) {
				logger.error( "Failed lookup {}", CSAP.getCsapFilteredStackTrace( e ) );
				numFailures++;
			}

			assertThat( csapUser ).isNotNull();

		}
		logger.info( "maxAttempts :" + maxAttempts + " numFailures: " + numFailures );

	}

	@Test
	public void validate_login_using_context ()
			throws Exception {

		if ( !isSetupOk() )
			return;
		
		LdapTemplate ldapSpringTemplate = buildContextTemplate();

		try {

			boolean authenticated = ldapSpringTemplate.authenticate(
				genericTree,
				"(uid=" + ldapUser + ")",
				ldapPass );

			assertThat( authenticated ).as( "Using configured tree" ).isTrue();

			authenticated = ldapSpringTemplate.authenticate(
				genericTree,
				"(uid=" + ldapUser + ")",
				"Wrong Password" );

			assertThat( authenticated ).as( "Incorrect password attempt" ).isFalse();

			String rootTree = genericTree.split( "," )[1];
			authenticated = ldapSpringTemplate
				.authenticate(
					rootTree,
					"(uid=" + ldapUser + ")",
					ldapPass );

			assertThat( authenticated ).as( "Using root tree" ).isTrue();

			assertThat(

				ldapSpringTemplate.authenticate(
					ldapSearchUser,
					"(uid=" + currentUser + ")",
					"Wrong Password" ) )

						.as( "Current User attempt" )

						.isFalse();

			logger.info( "All Scenarios passed" );

		} catch (Exception e) {
			logger.error( "Failed to init LDAP", e );
		}

	}

	@Test
	public void validate_login_using_bind ()
			throws Exception {


		if ( !isSetupOk() )
			return;
		
		LdapTemplate ldapSpringTemplate = new LdapTemplate();
		LdapContextSource contextSource = new LdapContextSource();
		contextSource.setUrl( ldapUrl );

		// Critical: Spring beans must be initialized, and JavaConfig requires
		// explicity calls

		contextSource.afterPropertiesSet();
		ldapSpringTemplate.setContextSource( contextSource );
		ldapSpringTemplate.afterPropertiesSet();

		try {

			AndFilter filter = new AndFilter();

			filter
				.and( new EqualsFilter( "uid", currentUser ) )
				.and( new EqualsFilter( "objectclass", "person" ) );

			logger.info( "Filter: " + filter.toString() );

			assertThat(

				ldapSpringTemplate.authenticate(
					ldapSearchUser,
					filter.toString(),
					"Wrong Password" ) )

						.as( "Current User bind attempt" )

						.isFalse();

		} catch (Exception e) {
			logger.error( "Failed to init LDAP", e );
			assertTrue( "Exception", false );
		}

	}

	/**
	 * 
	 * refer to notes at: your LDAP provideer
	 * Searches_against_DSX
	 * 
	 * @throws Exception
	 */
	@Test
	public void validate_generic_login_using_context ()
			throws Exception {


		if ( !isSetupOk() )
			return;
		
		LdapTemplate ldapSpringTemplate = buildContextTemplate();

		try {

			DirContext ctx = null;
			// this works
			// ctx =
			// contextSource.getContext(ldapUserFull,
			// ldapPass);

			AndFilter filter = new AndFilter();
			filter.and( new EqualsFilter( "uid", ldapUser ) )
				.and( new EqualsFilter( "objectclass", "person" ) );

			assertThat(

				ldapSpringTemplate.authenticate(
					genericTree,
					filter.toString(),
					ldapPass ) )

						.as( "generic login" )

						.isTrue();

			logger.info( "validated authentication" );

		} catch (Exception e) {
			logger.error( "Failed to init LDAP", e );
			assertTrue( "Exception", false );
		}

	}

	private LdapTemplate buildContextTemplate ()
			throws Exception {
		LdapTemplate ldapSpringTemplate = new LdapTemplate();
		LdapContextSource contextSource = new LdapContextSource();

		contextSource.setUserDn( ldapUser );
		contextSource.setPassword( ldapPass );
		contextSource.setUrl( ldapUrl );

		// Critical: Spring beans must be initialized, and JavaConfig requires
		// explicity calls
		contextSource.afterPropertiesSet();
		ldapSpringTemplate.setContextSource( contextSource );
		ldapSpringTemplate.afterPropertiesSet();
		return ldapSpringTemplate;
	}

	@Test
	public void validate_generic_login_with_csap_user_context_created ()
			throws Exception {


		if ( !isSetupOk() )
			return;
		
		LdapTemplate ldapTemplate = buildContextTemplate();

		CsapUserContextCallback csapUserContextCallback = new CsapUserContextCallback( ldapTemplate );

		assertThat(
			ldapTemplate
				.authenticate(
					genericTree,
					new EqualsFilter( "uid", ldapUser ).toString(),
					ldapPass,
					csapUserContextCallback ) )

						.as( "generic login with csap user context created" )

						.isTrue();

		CsapUser csapUser = csapUserContextCallback.getCsapUser();
		logger.info( "User from context: {}", csapUser.toString() );
		
		assertThat( csapUser.getFullName() )
			.as( "name from context" )
			.isEqualTo( ldapUser ) ;


	}
	
	@Test
	public void validate_user_login_with_csap_user_context_created ()
			throws Exception {


		if ( !isSetupOk() )
			return;
		
		LdapTemplate ldapTemplate = buildContextTemplate();

		CsapUserContextCallback csapUserContextCallback = new CsapUserContextCallback( ldapTemplate );

	

		// Validate a wrong password
		boolean authenticated = ldapTemplate.authenticate(
			ldapSearchUser,
			new EqualsFilter( "uid", currentUser ).toString(),
			"Wrong Password",
			csapUserContextCallback );

		if ( authenticated ) {
			CsapUser csapUser = csapUserContextCallback.getCsapUser();
			logger.info( "User from context: " + csapUser.toString() );
		}

		//update with actual password to validate
		assertThat( authenticated ).isFalse() ;

	}
}
