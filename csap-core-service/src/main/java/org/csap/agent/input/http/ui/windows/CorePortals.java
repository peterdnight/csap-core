package org.csap.agent.input.http.ui.windows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.csap.agent.CsapCoreService;
import org.csap.agent.CSAP;
import org.csap.agent.input.http.ui.rest.HostRequests;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.Application;
import org.csap.agent.model.DefinitionParser;
import org.csap.agent.model.LifeCycleSettings;
import org.csap.agent.model.ReleasePackage;
import org.csap.agent.model.ServiceAlertsEnum;
import org.csap.agent.model.ServiceAttributes;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.stats.JmxCommonEnum;
import org.csap.alerts.AlertSettings;
import org.csap.docs.CsapDoc;
import org.csap.integations.CsapInformation;
import org.csap.integations.CsapSecurityConfiguration;
import org.csap.security.CustomUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;
import static java.util.Comparator.comparing;
import org.csap.security.CsapUser;

@Controller
@RequestMapping(value = CsapCoreService.BASE_URL)
@CsapDoc(title = "CSAP Application Portal", notes = {
		"CSAP Application Portal provides core application management capabilities, including "
				+ "starting/stoping/deploying services, viewing log files, and much more.",
		"<a class='pushButton' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/csapboot.png' />"
				+ "<img class='csapDocImage' src='CSAP_BASE/images/portals.png' />" })
public class CorePortals {

	final Logger logger = LoggerFactory.getLogger( getClass() );

	@Inject
	public CorePortals( Application csapApp, HostRequests hostController,
			CsapInformation globalContext,
			CsapEventClient csapEventClient ) {
		this.application = csapApp;
		this.csapInformation = globalContext;
		this.csapEventClient = csapEventClient;
	}

	Application application;
	CsapInformation csapInformation;

	@Autowired(required = false)
	CsapSecurityConfiguration csapSecurityConfiguration;

	CsapEventClient csapEventClient;

	@GetMapping
	@CsapDoc(notes = { "Default page load for Application. It redirects to the services portal" })
	public String applicationDefault_loadServicesPortal ( Model springViewModel ) {
		return "redirect:/services";
	}
	
//	@GetMapping("/error")
//	@CsapDoc(notes = { "Default page load for Application. It redirects to the services portal" })
//	public String errorPage ( Model springViewModel, HttpServletRequest request ) {
//		logger.warn( "error on: {} ", request.getRequestURI() );
//		return "redirect:/test";
//	}

	@RequestMapping(CsapCoreService.TEST_URL)
	public String integrationTestsPage ( Model springViewModel ) {

		String pageName="agent@" + Application.getHOST_NAME() ;
		if ( Application.isJvmInManagerMode() ) {
			pageName="admin@" + Application.getHOST_NAME() ;
		}
		springViewModel.addAttribute( "pageName", pageName );
		springViewModel.addAttribute( "host", Application.getHOST_NAME() );

		return "IntegrationTests";
	}

	//
	// Initial login page redirects to services ui
	//
	// @RequestMapping(method = RequestMethod.GET)
	// public String get(HttpServletRequest request) {
	// // return "launch";
	// logger.info( "redirect: " + request.getRequestURI() );
	// return "redirect:/services";
	// }
	//
	// Test UI for function testing apis
	//

	@RequestMapping("health")
	@CsapDoc(notes = { "The real time meters dashboard enables teams to quickly view the last collected operational "
			+ "metrics for each host. This enables teams to quickly identify if one or more service instances "
			+ "has encounterd a performance issue or functional escape." })
	public String health ( ModelMap modelMap, HttpSession session ) {

		if ( logger.isDebugEnabled() ) {
			logger.debug( " Entered" );
		}
		AlertSettings alertSettings = application.getCsapCoreService().getAlerts();
		HashMap<String, String> settings = new HashMap<>();
		settings.put( "Health Report Interval", alertSettings.getReport().getIntervalSeconds() + " seconds" );
		settings.put( "Maximum items to store", alertSettings.getRememberCount() + "" );
		settings.put( "Email Notifications", alertSettings.getNotify().toString() );
		settings.put( "Alert Throttles", alertSettings.getThrottle().toString() );

		modelMap.addAttribute( "csapPageLabel", "Service Health Reports" );
		modelMap.addAttribute( "settings", settings );

		HashMap<String, List<ServiceInstance>> healthUrlsByService = new HashMap<>();

		application.getAllPackages().getServiceNameStream().forEach( serviceName -> {

			List<ServiceInstance> healthUrls = application
				.getAllPackages()
				.getServiceInstances( serviceName )
				.filter( ServiceInstance::isHealthReportConfigured )
				.collect( Collectors.toList() );
			if ( healthUrls.size() > 0 )
				healthUrlsByService.put( serviceName, healthUrls );

		} );

		modelMap.addAttribute( "healthUrlsByService", healthUrlsByService );

		setCommonAttributes( modelMap, session );

		return "health/alertsPortal";
	}

	@CsapDoc(notes = "Health data showing alerts. Default hours is 4 - and testing is 0", baseUrl = "/csap")
	@GetMapping(value = "/health/alerts", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ObjectNode report (
								@RequestParam(value = "hours", required = false, defaultValue = "4") int hours,
								@RequestParam(value = "testCount", required = false, defaultValue = "0") int testCount ) {
		ObjectNode results = jacksonMapper.createObjectNode();

		ArrayNode alertsTriggered;

		if ( !Application.isJvmInManagerMode() )  {
			alertsTriggered = jacksonMapper.createArrayNode();
			ObjectNode t = alertsTriggered.addObject();
			t.put( "ts", System.currentTimeMillis() );

			// String foundTime = LocalDateTime.now().format(
			// DateTimeFormatter.ofPattern( "HH:mm:ss , MMM d" ) ) ;
			t.put( "id", "Agent mode"  );
			t.put( "type", "."  );
			t.put( "time", ".");
			t.put( "host", "." );
			t.put( "service", "."  );
			t.put( "description", "Switch to admin to view aggregated"  );
		} else  if ( testCount == 0 ) {
			alertsTriggered = application.getHostStatusManager().getAllAlerts();
		} else {
			results.put( "testCount", testCount );
			alertsTriggered = jacksonMapper.createArrayNode();
			Random rg = new Random();

			for ( int i = 0; i < testCount; i++ ) {
				ObjectNode t = alertsTriggered.addObject();
				long now = System.currentTimeMillis();
				long itemTimeGenerated = now - rg.nextInt( (int) TimeUnit.DAYS.toMillis( 1 ) );
				t.put( "ts", itemTimeGenerated );

				// String foundTime = LocalDateTime.now().format(
				// DateTimeFormatter.ofPattern( "HH:mm:ss , MMM d" ) ) ;
				t.put( "id", "test.simon." + rg.nextInt( 20 ) );
				t.put( "type", "type" + rg.nextInt( 20 ) );
				t.put( "time", getFormatedTime( itemTimeGenerated ) );
				t.put( "host", "testHost" + rg.nextInt( 10 ) );
				t.put( "service", "testService" + rg.nextInt( 10 ) );
				t.put( "description", "description" + rg.nextInt( 20 ) );
			}
		}
		ArrayNode filteredByHoursShow = alertsTriggered ;
		if ( hours > 0 ) {

			ArrayNode filteredByHours = jacksonMapper.createArrayNode();

			long now = System.currentTimeMillis();
			alertsTriggered.forEach( item -> {
				if ( item.has( "ts" ) ) {
					long itemTime = item.get( "ts" ).asLong();

					if ( (now - itemTime) < TimeUnit.HOURS.toMillis( hours ) ) {
						filteredByHours.add( item );
					}

				}
			} );
			
			filteredByHoursShow=filteredByHours;

		} 

		results.put( "storeTotal", alertsTriggered.size() );
		results.put( "filterTotal", filteredByHoursShow.size() );
		results.set( "triggered", filteredByHoursShow );

		return results;
	}

	SimpleDateFormat timeDayFormat = new SimpleDateFormat( "HH:mm:ss , MMM d" );

	private String getFormatedTime ( long tstamp ) {

		Date d = new Date( tstamp );
		return timeDayFormat.format( d );
	}

	@RequestMapping(CsapCoreService.METER_URL)
	@CsapDoc(notes = { "The real time meters dashboard enables teams to quickly view the last collected operational "
			+ "metrics for each host. This enables teams to quickly identify if one or more service instances "
			+ "has encounterd a performance issue or functional escape." })
	public String realTimeMetersDashboard ( ModelMap modelMap, HttpSession session ) {

		if ( logger.isDebugEnabled() ) {
			logger.debug( " Entered" );
		}

		setCommonAttributes( modelMap, session );

		return "deployment/real-time";
	}

	//
	// Top level browsers for services
	//
	@RequestMapping(CsapCoreService.MAINSERVICES_URL)
	public String applicationPortal (	ModelMap modelMap, HttpServletRequest request, HttpSession session,
										@RequestParam(value = CSAP.PACKAGE_PARAM, required = false) String releasePackage,
										@CookieValue(value = "JSESSIONID", required = false) String cookie ) {

		if ( logger.isDebugEnabled() ) {
			logger.debug( " Entered" );
		}

		if ( Application.isJvmInManagerMode() && request.getServerPort() == 443
				&& cookie == null ) {
			// Hook for switching admin into https mode this is only needed when
			// admin is
			// running in secure mode. Jsessionids will NOT be set in http,
			// therefore we
			// redirect to the url.
			// logger.debug("Redirecting to https") ;

			// This might look like a infinite loop, but when secure mode is
			// configured the requestUrl will
			// have the https in front as it is configured in server.xml, it
			// does NOT come from the request
			// response.sendRedirect(request.getRequestURL().toString());
			return "redirect:" + request.getRequestURL()
				.toString();
		}

		setCommonAttributes( modelMap, session );

		if ( !request.getServerName().contains( "." ) ) {
			String fqdn = "LB Url not found in lifecycle settings";
			try {
				String lb = application.lifeCycleSettings().getLbServer();
				String dn = request.getServerName()
						+ lb.substring( lb.indexOf( "." ) );
				fqdn = request.getRequestURL().toString().replace( request.getServerName(), dn );
			} catch (Exception e) {
				logger.warn( "Failed to find LB settings" );
			}

			modelMap.addAttribute( "domainUrl", fqdn );
		}

		addSelectedReleasePackage( modelMap, releasePackage );

		if ( releasePackage == null ) {
			//
			if ( Application.isJvmInManagerMode()
					&& application.getRootModel()
						.getReleaseModelCount() > 1 ) {
				return "/deployment/package-select";
			}

			releasePackage = application.getActiveModelName();
		}

		return "/deployment/service-body";
	}

	public void addSelectedReleasePackage ( ModelMap modelMap, String releasePackage ) {
		modelMap.addAttribute( "selectedRelease", application.getActiveModelName() );
		if ( releasePackage != null ) {
			modelMap.addAttribute( "selectedRelease", releasePackage );
		}
	}

	@RequestMapping(CsapCoreService.MAINHOSTS_URL)
	public String applicationHostsPortal (	ModelMap modelMap,
											@RequestParam(value = CSAP.PACKAGE_PARAM, required = false) String releasePackage,
											HttpSession session ) {

		if ( logger.isDebugEnabled() ) {
			logger.debug( " Entered" );
		}

		setCommonAttributes( modelMap, session );
		addSelectedReleasePackage( modelMap, releasePackage );

		return "/deployment/service-host";
	}

	@RequestMapping(CsapCoreService.CLUSTERBROWSER_URL)
	public String applicationClusterOperationsDashboard (
															@RequestParam(value = CSAP.PACKAGE_PARAM, required = false) String releasePackage,
															ModelMap modelMap, HttpSession session ) {

		if ( logger.isDebugEnabled() ) {
			logger.debug( " Entered" );
		}
		if ( releasePackage == null ) {
			releasePackage = application.getActiveModelName();
		}
		ReleasePackage model = application.getModel( releasePackage );
		modelMap.addAttribute( model );

		setCommonAttributes( modelMap, session );

		return "/deployment/service-dialog-cluster";
	}

	@RequestMapping("/batchDialog")
	public String applicationBatchDeploymentDashboard (
														@RequestParam(value = CSAP.PACKAGE_PARAM, required = false) String releasePackage,
														ModelMap modelMap, HttpSession session ) {

		if ( logger.isDebugEnabled() ) {
			logger.debug( " Entered" );
		}

		setCommonAttributes( modelMap, session );

		addSelectedReleasePackage( modelMap, releasePackage );

		ReleasePackage model = application.getModel( (String) modelMap.get( "selectedRelease" ) );

		Map<String, String> servicesToType = model
			.serviceInstancesInCurrentLifeByName().values()
			.stream()
			.filter( listOfInstances -> listOfInstances.size() > 0 )
			.collect( Collectors.toMap(
				listOfInstances -> listOfInstances.get( 0 ).getServiceName(),
				listOfInstances -> listOfInstances.get( 0 ).getServerClass(),
				( a, b ) -> a, // merge function should never be used
				TreeMap::new ) ); // want them sorted

		modelMap.addAttribute( "serviceNames", servicesToType );

		Collections.sort( model.getHostsCurrentLc() );
		modelMap.addAttribute( "hostNames", model.getHostsCurrentLc() );

		if ( !Application.isJvmInManagerMode() ) {

			servicesToType = application
				.getServicesOnHost()
				.stream()
				.collect( Collectors.toMap(
					ServiceInstance::getServiceName,
					ServiceInstance::getServerClass,
					( a, b ) -> a, // merge function should never be used
					TreeMap::new ) ); // want them sorted

			modelMap.addAttribute( "serviceNames", servicesToType );
			modelMap.addAttribute( "hostNames", Application.getHOST_NAME() );
		}

		modelMap.addAttribute( "clusters", model.getClustersToHostMap() );

		try {
			modelMap.addAttribute( "clusterHostJson", jacksonMapper.writeValueAsString( model.getClustersToHostMap() ) );
		} catch (JsonProcessingException e) {
			logger.error( "Failed to generate host mapping", e );
		}
		try {
			modelMap.addAttribute( "clusterServiceJson", jacksonMapper.writeValueAsString( model.getClustersToServicesMap() ) );
		} catch (JsonProcessingException e) {
			logger.error( "Failed to generate host mapping", e );
		}

		return "/deployment/service-dialog-batch";
	}

	@RequestMapping("/find-service/{releasePackage}/{serviceName}")
	public ModelAndView servicePortalFind (
											@PathVariable String serviceName,
											@PathVariable String releasePackage ) {

		ModelAndView mav = new ModelAndView();
		mav.setView( new RedirectView( CsapCoreService.ADMIN_URL, true, false, true ) );

		logger.info( "Redirecting based on package {}  and service {}", releasePackage, serviceName );

		mav.getModel().put( CSAP.PACKAGE_PARAM, releasePackage );

		// use the first instance to determine the default admin service
		ServiceInstance instance = application
			.serviceInstancesByName( releasePackage, serviceName )
			.stream()
			.findFirst()
			.get();

		mav.getModel().put( CSAP.SERVICE_PORT_PARAM, instance.getServiceName_Port() );
		mav.getModel().put( CSAP.HOST_PARAM, instance.getHostName() );

		return mav;
	}

	//
	// Service managment - start/stop/deploy
	//
	@RequestMapping(CsapCoreService.ADMIN_URL)
	public String servicePortal (
									ModelMap modelMap, HttpSession session,
									HttpServletResponse response,
									@RequestParam(CSAP.SERVICE_PORT_PARAM) String serviceNamePort,
									@RequestParam(CSAP.PACKAGE_PARAM) String releasePackage,
									@RequestParam(CSAP.HOST_PARAM) String hostName )
			throws IOException {

		if ( logger.isDebugEnabled() ) {
			logger.debug( " Entered" );
		}

		setCommonAttributes( modelMap, session );

		ServiceInstance requestedInstance = application.getServiceInstance( serviceNamePort, hostName,
			releasePackage );

		// if this is the main page request, or someone has bookmarked an old
		// reference - display the main page
		if ( requestedInstance == null ) {
			return "/deployment/service-body";
		}

		logger.debug( " cluster: {}", requestedInstance.getCluster() );
		ArrayNode servicesOnHost = jacksonMapper.createArrayNode();
		application.getServicesOnTargetHost( hostName )
			.stream()
			.filter( serviceInstance -> serviceInstance.getCluster().equals( requestedInstance.getCluster() ) )
			.filter( serviceInstance -> !serviceInstance.isScript() && !serviceInstance.isOs() )
			.sorted( comparing( ServiceInstance::getAutoStart ) )
			.map( serviceInstance -> serviceInstance.getServiceName_Port() )
			.filter( name -> !name.contains( Application.AGENT_ID ) )
			.forEach( servicesOnHost::add );

		if ( requestedInstance.isUseDockerJavaContainer() || requestedInstance.isDockerContainer()  ) {
			modelMap.addAttribute( "useDockerContainer", servicesOnHost );
		}
		modelMap.addAttribute( "servicesOnHost", servicesOnHost );
		modelMap.addAttribute( "collectHost", requestedInstance.getCollectHost() );
		// logger.info( "servicesOnHost: {}", servicesOnHost.toString() );

		modelMap.addAttribute( "serviceInstance", requestedInstance );
		modelMap.addAttribute( "serviceParameters", requestedInstance.getParameters() );

		
		ObjectNode jvms = jacksonMapper.createObjectNode();
		ArrayNode jvmsAvailable = jvms.putArray( "available" );

		if ( System.getenv( "JAVA7_HOME" ) != null || Application.isRunningOnDesktop() ) {
			jvmsAvailable.add( "Java 7" );
		}
		if ( System.getenv( "JAVA8_HOME" ) != null || Application.isRunningOnDesktop() ) {
			jvmsAvailable.add( "Java 8" );
		}
		if ( System.getenv( "JAVA9_HOME" ) != null ) {
			jvmsAvailable.add( "Java 9" );
		}

		String javaVersion = requestedInstance.getJavaVersion();

		if ( javaVersion.equals( "default" ) ) {

			javaVersion = "7";
			// csapApp.getServiceInstanceCurrentHost("jdk")

			logger.debug( "Getting data for: {}", hostName );
			Optional<ServiceInstance> jdkInstance = application.getModel( releasePackage )
				.getServicesOnHost( hostName )
				.filter( serviceInstance -> serviceInstance.getServiceName()
					.matches( "jdk" ) )
				.findFirst();

			if ( jdkInstance.isPresent() && jdkInstance.get()
				.getMavenVersion()
				.startsWith( "8" ) ) {
				javaVersion = "8";
			}
		}

		jvms.put( "serviceSelected", "Java " + javaVersion );

		modelMap.addAttribute( "csapJava", jvms );
		modelMap.addAttribute( "jmxLabels", JmxCommonEnum.graphLabels() );

		addServiceRuntimeDescriptions( requestedInstance, modelMap );

		// modelMap.addAttribute( "maxDisk",
		// ServiceAlertsEnum.getMaxDisk( requestedInstance,
		// application.lifeCycleSettings() ) );

		modelMap.addAttribute( "serviceLimits",
			ServiceAlertsEnum.getAdminUiLimits( requestedInstance,
				application.lifeCycleSettings() ) );
		
		modelMap.addAttribute( "serviceDocker", requestedInstance.getDockerSettings() );

		try {
			modelMap.addAttribute( "graphReleasePackage",
				application.getModelForHost( hostName ).getReleasePackageName() );
		} catch (Exception e) {
			logger.error( "Failed autoset package", e );
			modelMap.addAttribute( "graphReleasePackage", releasePackage );
		}

		return "/deployment/admin-body";
	}

	private void addServiceRuntimeDescriptions ( ServiceInstance serviceInstance, ModelMap modelMap ) {
		String serverImage = "images/32x32/cssp.gif";
		String serverTitle = "CSSP 2.x";

		if ( serviceInstance != null ) {

			switch (serviceInstance.getServerType()) {
			case "cssp-1.x":
				serverImage = "images/cssp1.png";
				serverTitle = "CSSP 1.x - Spring Tomcat";
				break;
			case "cssp-2.x":
				serverImage = "images/cssp2.png";
				serverTitle = "CSSP 2.x - Spring Tomcat";
				break;
			case "cssp-3.x":
				serverImage = "images/cssp3.png";
				serverTitle = "CSSP 3.x - Spring Tomcat";
				break;
			case "tomcat7.x":
				serverImage = "images/tomcat7.png";
				serverTitle = "Tomcat";
				break;
			case "tomcat8.x":
				serverImage = "images/tomcat8.png";
				serverTitle = "Tomcat";
				break;
			case "wrapper":
				serverImage = "images/32x32/generic.png";
				serverTitle = "CS-AP Wrapper Project";
				break;
			case "SpringBoot":
				serverImage = "images/boot.png";
				serverTitle = "Spring Boot Project";
				break;
			default:
				serverImage = "images/32x32/computer.png";
				break;
			}
		}

		String subType = "/images/16x16/sysMon.png";
		String subTitle = "Service is a Linux Process";
		if ( serviceInstance.isDataStore() ) {
			subType = "/images/database.png";
			subTitle = "Service is a Datastore, refer to service help prior to performing operation procedures";
		}
		if ( serviceInstance.isJms() ) {
			subType = "/images/32x32/jms.gif";
			subTitle = "Service is a JMS provider,  refer to service help prior to performing operation procedures";
		}

		if ( serviceInstance.getServiceName()
			.toLowerCase()
			.contains( "oracle" ) ) {
			subType = "/images/32x32/oracle.png";
			subTitle = "Service is a Datastore,  refer to service help prior to performing operation procedures";
		}

		if ( serviceInstance.isScript() ) {
			subType = "/images/32x32/accessories-text-editor.png";
			subTitle = "Service is a script,  refer to service help prior to performing operation procedures";
		}

		modelMap.put( "subType", subType );
		modelMap.put( "serverImage", serverImage );
		modelMap.put( "serverTitle", serverTitle );
		modelMap.put( "subTitle", subTitle );

	}

	ObjectMapper jacksonMapper = new ObjectMapper();

	@Inject
	@Qualifier("csapEventsService")
	private RestTemplate csapEventsService;
	

	@RequestMapping(CsapCoreService.SCREEN_URL)
	public String csapScreenCastViewer (
											ModelMap modelMap,
											@RequestParam("item") String item,
											@RequestParam(value = "wiki", required = false, defaultValue = "https://github.com/csap-platform/csap-core/wiki") String wiki,
											HttpServletRequest request, HttpSession session )
			throws IOException {

		String user = CsapUser.currentUsersID();


		String mp4 = application.lifeCycleSettings().getToolsServer() + "/screencasts/" + item + ".mp4";
		String ogg = application.lifeCycleSettings().getToolsServer() + "/screencasts/" + item + ".ogg";

		csapEventClient.generateEvent( CsapEventClient.CSAP_UI_CATEGORY + "/screencast", user, mp4, wiki );

		if ( !wiki.startsWith( "http" ) ) {
			wiki = "https://github.com/csap-platform/csap-core/wiki#updateRef" + wiki;
		}

		modelMap.addAttribute( "mp4", mp4 );
		modelMap.addAttribute( "ogg", ogg );
		modelMap.addAttribute( "wiki", wiki );

		return "misc/screencast";
	}

	public void setCommonAttributes ( ModelMap modelMap, HttpSession session ) {

		session.setAttribute( CSAP.ROLES, csapSecurityConfiguration.getAndStoreUserRoles( session ) );

		if ( csapSecurityConfiguration.getAndStoreUserRoles( session )
			.contains( CsapSecurityConfiguration.ADMIN_ROLE ) ) {
			modelMap.addAttribute( "adminRole", CsapSecurityConfiguration.ADMIN_ROLE );
		}

		if ( csapSecurityConfiguration.getAndStoreUserRoles( session )
			.contains( CsapSecurityConfiguration.BUILD_ROLE ) ) {
			modelMap.addAttribute( "scmRole", CsapSecurityConfiguration.BUILD_ROLE );
		}

		if ( csapSecurityConfiguration.getAndStoreUserRoles( session )
			.contains( CsapSecurityConfiguration.INFRA_ROLE ) ) {
			modelMap.addAttribute( "infraRole", CsapSecurityConfiguration.INFRA_ROLE );
		}

		modelMap.addAttribute( "csapApp", application );
		modelMap.addAttribute( "applicationBranch", application.getSourceBranch() );
		modelMap.addAttribute( "rootPackageName", application.getRootModel().getReleasePackageName() );

		modelMap.addAttribute( "agentHostUrlPattern", application.getAgentHostUrlPattern( false ) );

		modelMap.addAttribute( "host", Application.getHOST_NAME() );
		modelMap.addAttribute( "runtimes", ServiceInstance.getServertypes() );

		try {
			modelMap.addAttribute( "userid", CsapUser.currentUsersID() );
		} catch (Exception e) {
			logger.error( "Failed to get security principle", e );
			modelMap.addAttribute( "userid", "UnknownUserid" );
		}

		LifeCycleSettings lifeMetaData = application.lifeCycleSettings();
		modelMap.addAttribute( lifeMetaData );

		String defaultLc = Application.getCurrentLifeCycle();
		if ( !lifeMetaData.getDefaultUiDisplayCluster()
			.equalsIgnoreCase( "all" )
				&& !lifeMetaData.getDefaultUiDisplayVersion()
					.equalsIgnoreCase( "all" ) ) {
			defaultLc += "-" + lifeMetaData.getDefaultUiDisplayCluster()
					+ "-" + lifeMetaData.getDefaultUiDisplayVersion();
		}

		for ( String role : csapSecurityConfiguration.getAndStoreUserRoles( session ) ) {

			if ( role.equals( CsapSecurityConfiguration.ADMIN_ROLE ) ) {
				modelMap.addAttribute( "admin", "admin" );
			}
		}

		modelMap.addAttribute( "name", application.getName() );
		modelMap.addAttribute( "packageNames", application.getPackageNames() );
		modelMap.addAttribute( "packageMap", application.getPackageNameToFileMap() );

		ArrayNode testUrls = jacksonMapper.createArrayNode();
		for ( String url : application.getRootModel().getHttpdTestUrls() ) {
			testUrls.add( url );
		}

		modelMap.addAttribute( "testUrls", testUrls );
		logger.debug( "testUrls: {} ", testUrls.toString() );

		logger.debug( "Root Model: {} ", application.getRootModel() );

		modelMap.addAttribute( "lifecycle", defaultLc );
		modelMap.addAttribute( "version", csapInformation.getVersion() );

		modelMap.addAttribute( "dateTime",
			LocalDateTime.now().format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMM d  uuuu " ) ) );
		modelMap.addAttribute( "adminGroup", csapSecurityConfiguration.getAdminGroup() );
		modelMap.addAttribute( "buildGroup", csapSecurityConfiguration.getBuildGroup() );
		modelMap.addAttribute( "analyticsUrl", application.lifeCycleSettings()
			.getAnalyticsUiUrl()
				+ "?life=" + Application.getCurrentLifeCycle() );
		// 
		modelMap.addAttribute( "prodDataUrl", application.lifeCycleSettings()
			.getAnalyticsUiUrl()
				+ "?life=prod&report=service/detail&appId=" + application.lifeCycleSettings()
					.getEventDataUser()
				+ "&service=" );

		modelMap.addAttribute( "eventUser", application.lifeCycleSettings().getEventDataUser() );
		modelMap.addAttribute( "eventApiUrl", application.lifeCycleSettings()
			.getEventApiUrl() );

	}

	public ArrayList<String> getServices ( ReleasePackage serviceModel ) {
		ArrayList<String> serviceNames = jeeServices( serviceModel );
		serviceNames.addAll( osServices( serviceModel ) );

		Collections.sort( serviceNames, String.CASE_INSENSITIVE_ORDER );

		return serviceNames;
	}

	public ArrayList<String> osServices ( ReleasePackage serviceModel ) {

		ArrayList<String> serviceNames = new ArrayList<>();
		JsonNode osNodes = serviceModel.getJsonModelDefinition()
			.at( "/" + DefinitionParser.OS_PROCESSES );
		osNodes.fieldNames().forEachRemaining( name -> {
			serviceNames.add( name );
		} );

		return serviceNames;
	}

	public ArrayList<String> jeeServices ( ReleasePackage serviceModel ) {
		ArrayList<String> serviceNames = new ArrayList<>();
		JsonNode jvmNodes = serviceModel.getJsonModelDefinition()
			.at( "/" + DefinitionParser.PARSER_JVMS );
		jvmNodes.fieldNames().forEachRemaining( name -> {
			serviceNames.add( name );
		} );
		return serviceNames;
	}

}
