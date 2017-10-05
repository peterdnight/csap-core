package org.csap.agent;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.csap.CsapMicroService;
import org.csap.agent.input.http.ui.rest.ExplorerRequests;
import org.csap.agent.input.http.ui.rest.FileRequests;
import org.csap.agent.input.http.ui.rest.HostRequests;
import org.csap.agent.input.http.ui.rest.ServiceRequests;
import org.csap.agent.model.Application;
import org.csap.agent.model.CsAgentTimer;
import org.csap.alerts.AlertSettings;
import org.csap.helpers.CsapRestTemplateFactory;
import org.csap.integations.CsapInformation;
import org.csap.integations.CsapPerformance;
import org.csap.integations.CsapRolesEnum;
import org.csap.integations.CsapSecurityConfiguration;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.resource.VersionResourceResolver;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.DockerCmdExecFactory;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;

// Consider use of: @CsapMicroService or @SpringBootApplication 
@CsapMicroService
@ConfigurationProperties ( prefix = "my-service-configuration" )
@Aspect
public class CsapCoreService extends WebMvcConfigurerAdapter {

	final static Logger logger = LoggerFactory.getLogger( CsapCoreService.class );
	// Security
	// must be synced with ehcache.xml
	public final static String TIMEOUT_CACHE_60s = "CacheWith60SecondEviction";
	public final static String TIMEOUT_CACHE_30s = "CacheWith60SecondEviction";

	// URLs
	public final static String BASE_URL = "/";
	public final static String TEST_URL = BASE_URL + "test";
	public final static String METER_URL = BASE_URL + "MeterActivity";
	public final static String MAINSERVICES_URL = BASE_URL + "services";
	public final static String MAINHOSTS_URL = BASE_URL + "hosts";
	public final static String CLUSTERBROWSER_URL = BASE_URL + "clusterDialog";
	public final static String ADMIN_URL = BASE_URL + "admin";
	public final static String EDIT_URL = BASE_URL + "edit";
	public final static String SUMMARY_URL = BASE_URL + "showSummary";
	public final static String SCREEN_URL = BASE_URL + "viewScreencast";

	public final static String DEFINITION_URL = BASE_URL + "definition";
	public final static String ENCODE_URL = "/properties/encode";
	public final static String ENCODE_FULL_URL = DEFINITION_URL + ENCODE_URL;
	public final static String NOTIFY_URL = "/notify";
	public final static String NOTIFY_FULL_URL = DEFINITION_URL + NOTIFY_URL;
	public final static String DECODE_URL = "/properties/decode";

	public final static String SERVICE_URL = BASE_URL + "service";
	public final static String FILE_URL = BASE_URL + "file";
	public final static String FILE_MANAGER_URL = FILE_URL + "/" + FileRequests.FILE_MANAGER;
	public final static String OS_URL = BASE_URL + "os";
	public final static String API_URL = BASE_URL + "api";
	public final static String API_AGENT_URL = API_URL + "/agent";
	public final static String API_APPLICATION_URL = API_URL + "/application";
	public final static String JSP_VIEW = "/view/";

	public static void main ( String[] args ) {

		SpringApplication.run( CsapCoreService.class, args );
		
		// CsapCommonConfiguration wires in ldap, perf monitors, ...
	}
	

	// configure @Scheduled thread pool
	@Bean
	public TaskScheduler taskScheduler () {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setThreadNamePrefix( CsapCoreService.class.getSimpleName() + "Scheduler" );
		scheduler.setPoolSize( 2 );
		return scheduler;
	}

	final public static String HEALTH_EXECUTOR = "CsapHealthExecutor";

	@Bean ( HEALTH_EXECUTOR )
	public TaskExecutor taskExecutor () {
		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		taskExecutor.setThreadNamePrefix( CsapCoreService.class.getSimpleName() + "@Async" );
		taskExecutor.setMaxPoolSize( 5 );
		taskExecutor.setQueueCapacity( 300 );
		taskExecutor.afterPropertiesSet();
		return taskExecutor;
	}
	

	private int ONE_YEAR_SECONDS = 60 * 60 * 24 * 365;

	@Autowired
	CsapInformation csapInformation;

	// https://spring.io/blog/2014/07/24/spring-framework-4-1-handling-static-web-resources
	// http://www.mscharhag.com/spring/resource-versioning-with-spring-mvc
	@Override
	public void addResourceHandlers ( ResourceHandlerRegistry registry ) {

//		if ( Application.isRunningOnDesktop() ) {   // NOT initialized prior to start
		if ( System.getenv( "STAGING" ) == null ) {
			logger.warn( "\n\n\n Desktop detected: Caching DISABLED \n\n\n" );
			return; // when disabled in yaml
			// ONE_YEAR_SECONDS = 0;
			// return;
		} else {
			logger.info( "Web caching enabled" );
		}

		// String version = csapInformation.getVersion(); // this is fixed
		// version from definition
		// // find actual version? or use snap?
		// if ( version.toLowerCase().contains( "snapshot" ) ) {
		// version = "snap" + System.currentTimeMillis();
		// }
		String version = "start" + System.currentTimeMillis();
		VersionResourceResolver versionResolver = new VersionResourceResolver()
			.addFixedVersionStrategy( version,
				"/**/modules/**/*.js" ) // requriesjs uses relative paths
			.addContentVersionStrategy( "/**" );

		// A Handler With Versioning - note images in css files need to be
		// resolved.
		registry.addResourceHandler( "/**/*.js", "/**/*.css", "/**/*.png", "/**/*.gif", "/**/*.jpg" )
			.addResourceLocations( "classpath:/static/", "classpath:/public/" )
			.setCachePeriod( ONE_YEAR_SECONDS )
			.resourceChain( true )
			.addResolver( versionResolver );

	}

	@Bean
	@ConditionalOnProperty ( "csap.security.enabled" )
	public CsapSecurityConfiguration.CustomHttpSecurity mySecurityPolicy () {

		// spring.resources.cache-period should almost always be set as well
		// @formatter:off
		CsapSecurityConfiguration.CustomHttpSecurity mySecurity =  (httpSecurity -> {

			httpSecurity

			// CSRF adds complexity - refer to
			// https://docs.spring.io/spring-security/site/docs/current/reference/htmlsingle/#csrf
			// csap.security.csrf also needs to be enabled or this will be ignored
			.csrf()
				.requireCsrfProtectionMatcher( 
					CsapSecurityConfiguration.buildRequestMatcher( 
						"/login*",
						FILE_URL + FileRequests.SAVE_URL,
						OS_URL + HostRequests.EXECUTE_URL
					) 
				)
				.and()
				
			.authorizeRequests()
				// Public assets for UI
				// Spring boot allows: "/webjars/**", "/js/**", "/css/**", "/images/**"
				.antMatchers(  "/noAuth/**"  )
					.permitAll()
				
				//
				// Api access is protected at application level if needed
				.antMatchers( "/api/**" )
					.permitAll() // Disable security on public assets
					
					
				
				//
				// Service Deployment
				//
				.antMatchers( SERVICE_URL + ServiceRequests.REBUILD_URL )
					.access( CsapSecurityConfiguration.hasAnyRole( CsapRolesEnum.build ) )
					
				//
				// admin actions
				//
				.antMatchers( SERVICE_URL + "/reImage", 
						SERVICE_URL + ServiceRequests.START_URL, 
						SERVICE_URL + "/stopServer",
						SERVICE_URL + ServiceRequests.KILL_URL,
						SERVICE_URL + ServiceRequests.GENERATE_APACHE_MAPPINGS,
						SERVICE_URL + "/httpd", 
						SERVICE_URL + "/modjk", 
						SERVICE_URL + "/status",
						SERVICE_URL + "/uploadArtifact", 
						SERVICE_URL + "/jmeter" )
					.access( CsapSecurityConfiguration.hasAnyRole( CsapRolesEnum.admin ) )
					
				//
				//  Definition Management
				//
				.antMatchers( HttpMethod.GET, DEFINITION_URL + "/**" )
					.authenticated()
				

				//
				//  Explorer operations
				.antMatchers( HttpMethod.POST, ExplorerRequests.EXPLORER_URL + "/**" )
					.access( CsapSecurityConfiguration.hasAnyRole( CsapRolesEnum.admin )
							+ " OR "
							+ CsapSecurityConfiguration.hasAnyRole( CsapRolesEnum.infra ) )
				//
				//  encoding properties
				.antMatchers( HttpMethod.POST, ENCODE_FULL_URL )
					.access( CsapSecurityConfiguration.hasAnyRole( CsapRolesEnum.admin )
							+ " OR "
							+ CsapSecurityConfiguration.hasAnyRole( CsapRolesEnum.infra ) )
					
				//
				//  notify when non admin
				.antMatchers( HttpMethod.POST, NOTIFY_FULL_URL )
					.access( CsapSecurityConfiguration.hasAnyRole( CsapRolesEnum.admin ))
								
				//
				// Updating Application Definition
				.antMatchers( HttpMethod.POST, DEFINITION_URL + "/**" )
					.access( CsapSecurityConfiguration.hasAnyRole( CsapRolesEnum.infra ) )
					
				//
				// Agent operations protected at app level using host membership
				//
				.antMatchers( 
					CsapCoreService.SERVICE_URL + "/query/**",
					OS_URL + HostRequests.AGENT_INFO_URL,
					OS_URL + HostRequests.HOST_INFO_URL,
					OS_URL + "/getConfigZip",
					OS_URL + HostRequests.UPDATE_PLATFORM  // pending removal
					)
					.permitAll() // Disable security on public assets
					
				// 
				//
				// VM actions OS_URL + "/command", EDIT_URL + "/**",
				.antMatchers( 
					FILE_MANAGER_URL, 
					FILE_URL + FileRequests.EDIT_URL, 
					FILE_URL + FileRequests.SAVE_URL,
					OS_URL + "/syncFiles", 
					OS_URL + "/hostAdmin", 
					OS_URL + "/delete",
					OS_URL + "/checkFsThroughput", 
					OS_URL + "/killPid", 
					OS_URL + HostRequests.EXECUTE_URL )
					.access( CsapSecurityConfiguration.hasAnyRole( CsapRolesEnum.admin ) )
					
				// anything else
				.anyRequest()
					.access( CsapSecurityConfiguration.hasAnyRole( CsapRolesEnum.view )
							+ " OR "
							+ CsapSecurityConfiguration.hasAnyRole( CsapRolesEnum.admin ) );

		});

		// @formatter:on
		return mySecurity;

	}

	@Bean
	public CsapRestTemplateFactory csapRestFactory () {

		return new CsapRestTemplateFactory();
	}

	// UI sessions are associated with these calls
	@Bean ( name = "adminConnection" )
	public RestTemplate getAdminConnection ( CsapRestTemplateFactory factory ) {
		// 1 1 for testing, 20, 20
		return factory.buildDefaultTemplate( "Admin", isDisableSslValidation(), 10, 10, 2, 4, 300 );
	}

	// UI sessions are associated with these calls
	@Bean ( name = "analyticsRest" )
	public RestTemplate getCsapAnalyticsService ( CsapRestTemplateFactory factory ) {
		// 1 1 for testing, 20, 20
		return factory.buildDefaultTemplate( "Analytics", isDisableSslValidation(), 10, 10, 5, 60, 300 );
	}

	// These are background UI initiated
	@Bean ( name = "trendRestTemplate" )
	public RestTemplate getTrendAnalyticsService ( CsapRestTemplateFactory factory ) {
		return factory.buildDefaultTemplate( "Trending", isDisableSslValidation(), 10, 10, 5, 60, 300 );
	}

	private boolean disableSslValidation = false;

	public void setDisableSslValidation ( boolean disableSslValidation ) {
		this.disableSslValidation = disableSslValidation;
	}

	public boolean isDisableSslValidation () {
		return disableSslValidation;
	}

	public static String getStaging () {
		return staging;
	}

	public void setStaging ( String staging ) {
		this.staging = staging;
	}

	public static String getProcessing () {
		return processing;
	}

	public void setProcessing ( String processing ) {
		this.processing = processing;
	}

	private static String staging="" ;
	private static String processing="" ;
	private static String definitionFolder="";

	public static String getDefinitionFolder () {
		return definitionFolder;
	}

	public static void setDefinitionFolder ( String definitionFolder ) {
		CsapCoreService.definitionFolder = definitionFolder;
	}

	//
	@Bean ( name = "csapEventsService" )
	public RestTemplate csapEventsService ( CsapRestTemplateFactory factory ) {

		RestTemplate restTemplate = factory.buildDefaultTemplate( "csapEvents", false, 10, 10, 5, 5, 300 );

		restTemplate.getMessageConverters().clear();
		restTemplate.getMessageConverters().add( new FormHttpMessageConverter() );
		restTemplate.getMessageConverters().add( new StringHttpMessageConverter() );
		restTemplate.getMessageConverters().add( new MappingJackson2HttpMessageConverter() );

		return restTemplate;
	}

	//
	@Bean ( name = "apiTester" )
	public RestTemplate getApiTemplate ( CsapRestTemplateFactory factory ) {
		return factory.buildDefaultTemplate( "ApiTester", 1, 1, 10, 10, 10 );
	}

	// Summary of all public events in class
	@Pointcut ( "execution(public * org.csap.agent.input.http.ui.rest.ServiceRequests.*(..))" )
	private void servicePC () {
	}

	;

	@Around ( "servicePC()" )
	public Object serviceAdvice ( ProceedingJoinPoint pjp )
			throws Throwable {

		Object obj = executeSimon( pjp, "aop.summary.ServiceRequests" );

		return obj;
	}

	// Summary of all public events in class
	@Pointcut ( "execution(public * org.csap.agent.input.http.ui.rest.HostRequests.*(..))" )
	private void hostPC () {
	}

	;

	@Around ( "hostPC()" )
	public Object hostAdvice ( ProceedingJoinPoint pjp )
			throws Throwable {

		Object obj = executeSimon( pjp, "aop.summary.HostRequests" );

		return obj;
	}

	private Object executeSimon ( ProceedingJoinPoint pjp, String desc )
			throws Throwable {

		String timerId = desc;

		Split split = SimonManager.getStopwatch( timerId ).start();
		Object obj = pjp.proceed();
		split.stop();
		return obj;

	}

	@Pointcut ( "within(org.csap.agent.model.Application)" )
	private void csapModelPC () {
	}

	;

	@Around ( "csapModelPC()" )
	public Object modelAdvice ( ProceedingJoinPoint pjp )
			throws Throwable {

		Object obj = CsapPerformance.executeSimon( pjp, "model." );

		return obj;
	}

	@Pointcut ( "within(org.csap.agent.linux..*)" )
	private void linuxPC () {
	}

	
	 @Pointcut ( "within(org.csap.agent.linux.ServiceJobRunner)" )
	 	private void isServiceJobRunner () {
	 }
	 
	@Around ( "linuxPC() && !isServiceJobRunner()" )
	public Object linuxAdvice ( ProceedingJoinPoint pjp )
			throws Throwable {

		// logger.info( "excluding: {} ", pjp.getTarget().getClass().getName() );
		Object obj = CsapPerformance.executeSimon( pjp, "linux." );

		return obj;
	}

	// @Pointcut ( "within(org.csap.agent.init..*)" )
	// private void initPC () {
	// }
	//
	// ;

	// @Pointcut ( "within(@org.springframework.stereotype.Controller *)" )
	// private void mvcPC () {
	// }
	//
	// ;
	//
	// @Around ( "mvcPC()" )
	// public Object mvcAdvice ( ProceedingJoinPoint pjp )
	// throws Throwable {
	//
	// Object obj = CsapPerformance.executeSimon( pjp, "aop.controller." );
	//
	// return obj;
	// }

	@Pointcut ( "within(@org.springframework.stereotype.Controller *)" )
	private void mvcPC () {
	}

	;

	@Around ( "mvcPC()" )
	public Object mvcAdvice ( ProceedingJoinPoint pjp )
			throws Throwable {

		Object obj = CsapPerformance.executeSimon( pjp, "aop.controller." );

		return obj;
	}

	// ref.
	// http://guptavikas.wordpress.com/2010/04/15/aspectj-pointcut-expressions/
	// @Around ( "within(org.csap.agent.input..*) && !linuxPC() &&
	// !csapModelPC() && !mvcPC() && !initPC()" )
	// public Object inputSimonAdvice ( ProceedingJoinPoint pjp )
	// throws Throwable {
	//
	// Object obj = CsapPerformance.executeSimon( pjp, "java.input." );
	// return obj;
	// }
	// && !mvcPC() && !initPC()

	@Around ( "within(org.csap.agent.services..*)  && !linuxPC()  && !csapModelPC() " )
	public Object servicesSimonAdvice ( ProceedingJoinPoint pjp )
			throws Throwable {

		Object obj = CsapPerformance.executeSimon( pjp, "java.services." );
		return obj;
	}

	@Around ( value = "@annotation(annotation)" )
	public Object csAgentTimerAdvice ( ProceedingJoinPoint pjp, final CsAgentTimer annotation )
			throws Throwable {

		Object obj = CsapPerformance.executeSimon( pjp, "java.CsAgentTimer." );
		return obj;
	}

	// Use for admin Health summary dashboard
	public AlertSettings getAlerts () {
		return alerts;
	}

	public void setAlerts ( AlertSettings alerts ) {
		this.alerts = alerts;
	}

	private AlertSettings alerts = new AlertSettings();

	private DockerSettings docker = new DockerSettings();

	public DockerSettings getDocker () {
		return docker;
	}

	public void setDocker ( DockerSettings docker ) {
		this.docker = docker;
	}

	// https://github.com/spotify/docker-client/blob/master/docs/user_manual.md#creating-a-docker-client
	@Bean
	public DockerClient getDockerClient () {
		logger.info( "Creating pooled docker: {} ", docker.toString() );

		DockerClient client = null;
		try {
			DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
				.withDockerHost( docker.getUrl() )
				.build();

			DockerCmdExecFactory dockerCmdExecFactory = new JerseyDockerCmdExecFactory()
				.withReadTimeout( docker.getReadTimeoutSeconds() *1000 )
				.withConnectTimeout( docker.getConnectionTimeoutSeconds() *1000  )
				.withMaxTotalConnections( docker.getConnectionPool() )
				.withMaxPerRouteConnections( 3 );
			
//			DockerCmdExecFactory dockerCmdExecFactory = new NettyDockerCmdExecFactory()
//					.withConnectTimeout( docker.getReadTimeoutSeconds() *1000 ) ;

			client = DockerClientBuilder
				.getInstance( config )
				.withDockerCmdExecFactory( dockerCmdExecFactory )
				.build();

			// client = DefaultDockerClient.builder()
			// .uri( docker.getUrl() )
			// .connectionPoolSize( docker.getConnectionPool() )
			// .build();
		} catch (Throwable t) {
			logger.warn( "Failed connecting to docker: {}", CSAP.getCsapFilteredStackTrace( t ) );
		}

		return client;
	}

	public class DockerSettings {
		private String url;
		
		private String templateRepository="docker.io" ;
		public String getTemplateRepository () {
			return templateRepository;
		}

		public void setTemplateRepository ( String templateRepository ) {
			this.templateRepository = templateRepository;
		}

		private int connectionPool = 1;
		public int getReadTimeoutSeconds () {
			return readTimeoutSeconds;
		}

		public void setReadTimeoutSeconds ( int readTimeoutSeconds ) {
			this.readTimeoutSeconds = readTimeoutSeconds;
		}

		public int getConnectionTimeoutSeconds () {
			return connectionTimeoutSeconds;
		}

		public void setConnectionTimeoutSeconds ( int connectionTimeoutSeconds ) {
			this.connectionTimeoutSeconds = connectionTimeoutSeconds;
		}

		private int readTimeoutSeconds =10;
		private int connectionTimeoutSeconds =10;

		public String getUrl () {
			return url;
		}

		public void setUrl ( String url ) {
			this.url = url;
		}

		public int getConnectionPool () {
			return connectionPool;
		}

		public void setConnectionPool ( int connectionPool ) {
			this.connectionPool = connectionPool;
		}

		@Override
		public String toString () {
			return "DockerSettings [url=" + url + ", templateRepository=" + templateRepository + ", connectionPool=" + connectionPool
					+ ", readTimeoutSeconds=" + readTimeoutSeconds + ", connectionTimeoutSeconds=" + connectionTimeoutSeconds + "]";
		}

        
	}

}
