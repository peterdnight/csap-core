package org.csap.agent.input.http.ui.rest;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.csap.agent.CsapCoreService;
import org.csap.agent.CSAP;
import org.csap.agent.input.http.api.AgentApi;
import org.csap.agent.linux.OsCommandRunner;
import org.csap.agent.linux.OutputFileMgr;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.ActiveUsers;
import org.csap.agent.model.Application;
import org.csap.agent.model.LifeCycleSettings;
import org.csap.agent.model.ReleasePackage;
import org.csap.agent.model.ServiceAlertsEnum;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.services.HostKeys;
import org.csap.agent.services.OsManager;
import org.csap.agent.services.ServiceCommands;
import org.csap.agent.services.ServiceOsManager;
import org.csap.agent.stats.MetricCategory;
import org.csap.docs.CsapDoc;
import org.csap.integations.CsapSecurityConfiguration;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.javasimon.SimonManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *
 * ServiceRequests is a container for MVC actions primarily targetting the main
 * UI for services.
 *
 * @author someDeveloper
 *
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/current/spring-framework-reference/html/mvc.html">
 *      SpringMvc Docs </a>
 *
 */
@RestController
@RequestMapping ( CsapCoreService.SERVICE_URL )
@CsapDoc ( title = "Service Operations" , notes = {
		"Update, Reload and similar operations to manage the running application",
		"<a class='pushButton' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/portals.png' />"
} )
public class ServiceRequests {

	final static Logger logger = LoggerFactory.getLogger( ServiceRequests.class );

	public static final String SSO_PARAM_FOR_BATCH = "ssoCookie";

	@Inject
	public ServiceRequests(
			Application csapApp,
			ServiceOsManager serviceOsManager,
			OsManager osManager,
			CsapEventClient csapEventClient,
			AgentApi performanceData,
			ServiceCommands serviceCommands ) {

		this.serviceOsManager = serviceOsManager;
		this.csapApp = csapApp;
		this.osManager = osManager;
		this.csapEventClient = csapEventClient;
		this.performanceApi = performanceData;
		this.serviceCommands = serviceCommands;
	}

	Application csapApp;
	AgentApi performanceApi;

	@Inject
	ServiceCommands serviceCommands;

	@Autowired ( required = false )
	CsapSecurityConfiguration securityConfig;

	CsapEventClient csapEventClient;
	ServiceOsManager serviceOsManager;
	OsManager osManager;

	OsCommandRunner osCommandRunner = new OsCommandRunner( 60, 2, "ServiceRequests" );

	ObjectMapper jacksonMapper = new ObjectMapper();

	public ServiceRequests( ) {

		jacksonMapper.getFactory().enable( JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS );
	}

	public static boolean isRoleInGroup ( List<String> roles, String role ) {

		for ( String curRole : roles ) {
			if ( curRole.equalsIgnoreCase( role ) ) {
				return true;
			}
		}
		return false;
	}

	// public final static String LOG_FILE = "caSvcRollingLogs.txt";
	static public String START_OP = "_start";
	static public String DEPLOY_OP = "_deploy";
	static public String KILL_OP = "_kill";
	
	static public String MAVEN_VERIFY_FILE = "admin-run-load-test.sh";
	static public String JMX_AUTH_FILE = "admin-reset-jmx-auth.sh";
	static public String PURGE_FILE = "admin-clean-deploy.sh";

	public final static String SKIP_HEADERS = "skipHeaders";

	@Inject
	@Qualifier ( "csapEventsService" )
	private RestTemplate csapEventsService;

	@Cacheable ( value = CsapCoreService.TIMEOUT_CACHE_60s , key = "{'eventCount-' + #releasePackage }" )
	@RequestMapping ( value = "/activityCount" )
	public ObjectNode eventCount (
									@RequestParam ( value = CSAP.PACKAGE_PARAM , required = false ) String releasePackage ) {
		ObjectNode resultsJson = jacksonMapper.createObjectNode();
		resultsJson.put( "count", "-1" );

		String restUrl = csapApp.lifeCycleSettings().getEventUiCountUrl();

		if ( releasePackage != null ) {
			restUrl += "&project=" + releasePackage;
		}
		logger.debug( "restUrl: {}", restUrl );

		if ( !restUrl.startsWith( "http" ) ) {

			logger.debug( "====> \n\n Cache Reuse or disabled" );

			resultsJson.put( "message", "cache disabled: " + restUrl );
		}
		if ( ! csapApp.lifeCycleSettings().isEventPublishEnabled() ) {
			logger.info( "Stubbing out data for trends - add csap events services" );
			resultsJson.put( "count", "disabled" );
			resultsJson.put( "message", "csap-event-service disabled - using stub data" );
			return resultsJson ;
		}

		try {
			
			ObjectNode restResponse = csapEventsService.getForObject( restUrl, ObjectNode.class );

			resultsJson.put( "url", restUrl );
			if ( restResponse != null ) {
				resultsJson.set( "count", restResponse.get( "count" ) );
			} else {
				resultsJson.put( "message", "Got a null response from url: " + restUrl );
			}
		} catch (Exception e) {
			logger.error( "Failed getting activity count from url: {}, reason: {}", restUrl, CSAP.getCsapFilteredStackTrace( e ) );
			resultsJson.put( "url", restUrl );
			resultsJson.put( "message", "Error during Access: " + e.getMessage() );
		}

		return resultsJson;
	}

	@RequestMapping ( value = "/httpd" , produces = MediaType.TEXT_HTML_VALUE )
	@ResponseBody
	public String getHttpdStatus ( HttpServletRequest request )
			throws IOException {

		String statusUrl = "http://localhost:8080/server-status?" + request.getQueryString();

		if ( Application.isRunningOnDesktop() ) {
			statusUrl = "http://csap-dev02:8080/server-status?" + request.getQueryString();
		}

		csapEventClient.generateEvent( "httpd", securityConfig.getUserIdFromContext(),
			"apache httpd status", "privleged information via: " + statusUrl );

		String restResponse = analyticsTemplate.getForObject( statusUrl, String.class );

		return restResponse;
	}

	@RequestMapping ( value = { "/modjk", "/status" } , produces = MediaType.TEXT_HTML_VALUE )
	@ResponseBody
	public String getModjkStatus ( HttpServletRequest request )
			throws IOException {

		logger.debug( "queryString: {} ", request.getQueryString() );
		String statusUrl = "http://localhost:8080/status?" + request.getQueryString();

		if ( Application.isRunningOnDesktop() ) {
			statusUrl = "http://csap-dev02:8080/status?" + request.getQueryString();
		}

		csapEventClient.generateEvent( "httpd", securityConfig.getUserIdFromContext(),
			"apache modjk status", "privleged information via: " + statusUrl );

		String restResponse = analyticsTemplate.getForObject( statusUrl, String.class );
		// restResponse.replaceAll("/status", "./status")
		// response.getWriter().print( restResponse );
		return restResponse.replaceAll( "/status", "./status" );
	}

	@RequestMapping ( value = "/getLatestServiceStats" )
	public ObjectNode getLatestServiceStats (
												@RequestParam ( value = CSAP.HOST_PARAM ) String[] hostNameArray,
												@RequestParam ( value = CSAP.SERVICE_PORT_PARAM ) String serviceName ) {

		return csapApp.getServiceCollection( hostNameArray, serviceName );
	}

	static public final String LATEST_APP_STATS_URL = "/query/getLatestAppStats";

	@RequestMapping ( { LATEST_APP_STATS_URL } )
	public JsonNode getLatestAppStats (
										@RequestParam ( value = CSAP.HOST_PARAM , required = false ) ArrayList<String> hosts,
										@RequestParam ( value = CSAP.SERVICE_PORT_PARAM ) String serviceName,
										String type,
										String interval,
										int number,
										HttpServletRequest request ) {

		ObjectNode resultsJson = jacksonMapper.createObjectNode();
		if ( Application.isJvmInManagerMode() ) {
			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>();

			String url = CsapCoreService.SERVICE_URL + LATEST_APP_STATS_URL;

			urlVariables.add( CSAP.SERVICE_PORT_PARAM, serviceName );
			urlVariables.add( "type", type );
			urlVariables.add( "interval", interval );
			urlVariables.add( "number", Integer.toString( number ) );

			if ( hosts != null ) {
				resultsJson = serviceOsManager.remoteAgentsStateless( hosts, url, urlVariables );

			} else {
				resultsJson.put( CSAP.CONFIG_PARSE_ERROR, " - Failed to find hostName: "
						+ hosts );
			}

			return resultsJson;

		}

		if ( type.equals( "app" ) ) {
			return performanceApi.collectionApplication( serviceName, interval, number );
		} else {
			return performanceApi.collectionJava( serviceName, interval, number );
		}
	}

	@Cacheable ( value = CsapCoreService.TIMEOUT_CACHE_60s , key = "{'realTimeMeters-' + #projectName + #detailMeters  }" )
	@RequestMapping ( value = "/realTimeMeters" )
	public ArrayNode getRealTimeMeters (
											@RequestParam ( value = "project" ) String projectName,
											@RequestParam ( value = "meterId" , required = false , defaultValue = "" ) ArrayList<String> detailMeters ) {

		ArrayNode results = csapApp.lifeCycleSettings().getRealTimeMeters().deepCopy();
		if ( Application.isJvmInManagerMode() ) {

			logger.debug( "Updating Manager meters" );

			ReleasePackage model = csapApp.getModel( projectName );
			if ( model == null ) {
				model = csapApp.getActiveModel().getAllPackagesModel();
			}

			csapApp.getHostStatusManager().updateRealTimeMeters( results, model.getHostsCurrentLc(),
				detailMeters );

		} else {
			for ( JsonNode item : results ) {
				ObjectNode meterJson = (ObjectNode) item;

				String id = meterJson.get( "id" ).asText();
				String[] jsonPath = id.split( Pattern.quote( "." ) );
				String collector = jsonPath[0];
				String attribute = jsonPath[1];
				// logger.info("collector: " + collector + " attribute: " +
				// attribute);
				// vm. process. jmxCommon. jmxCustom.Service.var

				ObjectNode latestCollection = null;
				ObjectNode collectedJson = null;
				try {
					latestCollection = (ObjectNode) osManager.getLastCollectedValsConfigured();
					collectedJson = (ObjectNode) latestCollection.get( collector );
					if ( collector.equals( "jmxCustom" ) ) {
						String serviceName = jsonPath[1];
						attribute = jsonPath[2];
						if ( !collectedJson.has( serviceName ) ) {
							continue;
						}
						collectedJson = (ObjectNode) collectedJson.get( serviceName );
					}
					if ( !collectedJson.has( attribute ) ) {
						continue;
					}
					// logger.info(" collectedJson : " + collectedJson); ;
					meterJson.put( "value", collectedJson.get( attribute ).asInt() );
					meterJson.put( "vmCount", 1 );

					// to get values
					if ( detailMeters == null || detailMeters.contains( id ) ) {
						ArrayNode hosts = meterJson.putArray( "hostNames" );
						hosts.add( Application.getHOST_NAME() );
						ArrayNode hostValues = meterJson.putArray( "hostValues" );
						hostValues.add( collectedJson.get( attribute ).asInt() );
					}

				} catch (Exception e) {
					logger.error( "Failed runtime: " + collectedJson + " latestCollection: " + latestCollection, e );
					meterJson.put( "value", 0 );
				}

			}
		}

		return results;
	}

	@Inject
	@Qualifier ( "analyticsRest" )
	private RestTemplate analyticsTemplate;
	// #path1.concat('peter')

	@Cacheable ( value = CsapCoreService.TIMEOUT_CACHE_60s , key = "{'latestEvent-' + #apiName + #appId + #life + #category }" )
	@RequestMapping ( value = "/eventApi/{apiName:.+}" )
	public ObjectNode latestEvent (
									@PathVariable ( value = "apiName" ) String apiName,
									@RequestParam ( value = "appId" , required = true ) String appId,
									@RequestParam ( value = "life" , required = true ) String life,
									@RequestParam ( value = "category" , required = false ) String category,
									HttpServletRequest request )
			throws Exception {

		ObjectNode resultsJson = jacksonMapper.createObjectNode();
		String restUrl = csapApp.lifeCycleSettings().getEventApiUrl() + "/" + apiName
				+ "?life=" + life
				+ "&appId=" + appId
				+ "&category=" + category;

		logger.debug( "getting report from: {} ", restUrl );

		try {

			ObjectNode restResponse = analyticsTemplate.getForObject( restUrl, ObjectNode.class );

			resultsJson = restResponse;
			if ( resultsJson != null ) {
				resultsJson.put( "source", restUrl );

				resultsJson.put( "updated", shortFormatter.format( new Date() ) );

			}
		} catch (Exception e) {
			logger.error( "Failed getting report from url: {}, Reason: ", restUrl, e.getMessage() );
			logger.debug( "Stack Trace ", e );
			resultsJson.put( "url", restUrl );
			resultsJson.put( "message", "Error during Access: " + e.getMessage() );
		}

		return resultsJson;

	}

	@Inject
	GraphCache graphsCache;

	/**
	 * loads historical data in context; wraps remote calls so that ssl & non
	 * ssl applications can still get data
	 *
	 * @param callback
	 * @param host
	 * @param graph
	 * @param appId
	 * @param life
	 * @param numberOfDays
	 * @param dateOffSet
	 * @param serviceName
	 * @param padLatest
	 * @param searchFromBegining
	 * @param response
	 * @throws Exception
	 */
	@RequestMapping ( value = "/metricsApi/{host}/{graph}" , produces = { "application/javascript" } )
	public void metricsApi (
								@RequestParam ( value = "callback" , defaultValue = "false" ) String callback,
								@PathVariable ( value = "host" ) String host,
								@PathVariable ( value = "graph" ) String graph,
								@RequestParam ( value = "appId" , required = true ) String appId,
								@RequestParam ( value = "life" , required = true ) String life,
								@RequestParam ( value = "numberOfDays" , required = false , defaultValue = "1" ) int numberOfDays,
								@RequestParam ( value = "dateOffSet" , required = false , defaultValue = "0" ) int dateOffSet,
								@RequestParam ( value = CSAP.SERVICE_PORT_PARAM , required = false ) String serviceName,
								@RequestParam ( value = "padLatest" , required = false ) String padLatest,
								@RequestParam ( value = "searchFromBegining" , required = false ) String searchFromBegining,
								HttpServletResponse response )
			throws Exception {

		String restResponse = "{failed}";
		// ObjectNode resultsJson = jacksonMapper.createObjectNode();
		String restUrl = csapApp.lifeCycleSettings().getEventMetricsUrl() + host + "/" + graph
				+ "?life=" + life
				+ "&appId=" + appId
				+ "&numberOfDays=" + numberOfDays
				+ "&dateOffSet=" + dateOffSet
				+ "&serviceName=" + serviceName
				+ "&padLatest=" + padLatest
				+ "&searchFromBegining=" + searchFromBegining;

		if ( callback.equals( "false" ) ) {
			response.setContentType( MediaType.APPLICATION_JSON_VALUE );
		} else {
			response.setContentType( "application/javascript" );
		}

		PrintWriter writer = response.getWriter();
		if ( !callback.equals( "false" ) ) {
			writer.print( callback + "(" );
		}

		String cachedResponse = graphsCache.getGraphData( restUrl );
		if ( cachedResponse.length() < 100 && cachedResponse.contains( "Error getting data" ) ) {
			ObjectNode resultsJson = jacksonMapper.createObjectNode();
			resultsJson.put( "error", "Invalid Response from " + restUrl );
			resultsJson.put( "host", host );
			cachedResponse = resultsJson.toString();
		}
		writer.print( cachedResponse );

		if ( !callback.equals( "false" ) ) {
			writer.print( ")" );
		}
		return;

	}

	DateFormat shortFormatter = new SimpleDateFormat( "HH:mm:ss MMM-dd" );
	// #path1.concat('peter')
	@Cacheable ( CsapCoreService.TIMEOUT_CACHE_60s )
	@RequestMapping ( value = "/report" )
	public ObjectNode analyticsCompareReport (	@RequestParam ( value = "report" , required = false ) String report,
												@RequestParam ( value = "appId" , required = true ) String appId,
												@RequestParam ( value = "project" , required = true ) String project,
												@RequestParam ( value = CSAP.SERVICE_PORT_PARAM , required = false ) String serviceName,
												@RequestParam ( value = "host" , required = false ) String host,
												@RequestParam ( value = "life" , required = false ) String life,
												@RequestParam ( value = "numDays" , required = false , defaultValue = "1" ) int numDays,
												@RequestParam ( value = "dateOffSet" , required = false , defaultValue = "0" ) int dateOffSet,
												@RequestParam ( value = "trending" , required = false , defaultValue = "0" ) int trending,
												@RequestParam ( value = "trendDivide" , required = false , defaultValue = "0" ) String trendDivide,
												@RequestParam ( value = "allVmTotal" , required = false , defaultValue = "" ) String allVmTotal,
												@RequestParam ( value = "metricsId" , required = false , defaultValue = "topCpu" ) String metricsId,
												@RequestParam ( value = "resource" , required = false , defaultValue = "resource_30" ) String resource )
			throws Exception {

		ObjectNode resultsJson = jacksonMapper.createObjectNode();

		// Support for converting to Analytics
		String analyticsReportType = report;
		if ( report.equals( "app" ) ) {
			analyticsReportType = "jmxCustom";
		}
		if ( report.equals( "java" ) ) {
			analyticsReportType = "jmx";
		}

		String restUrl = csapApp.lifeCycleSettings().getReportUrl() + analyticsReportType
				+ "?life=" + life
				+ "&appId=" + appId
				+ "&numDays=" + numDays
				+ "&dateOffSet=" + dateOffSet;

		if ( !project.contains( CSAP.ALL_PACKAGES ) ) {
			restUrl += "&project=" + project;
		}

		logger.debug( "getting report from: {} ", restUrl );

		if ( trending != 0 ) {
			restUrl += "&trending=true&metricsId=" + metricsId;
			if ( trendDivide.length() > 1 ) {
				String[] divides = trendDivide.split( "," );
				for ( String div : divides ) {
					restUrl += "&divideBy=" + div;
				}
			}
			if ( allVmTotal.length() > 0 ) {
				restUrl += "&allVmTotal=" + allVmTotal;
			}
		}

		if ( serviceName != null ) {
			restUrl += "&serviceName=" + serviceName;
		}
		if ( host != null ) {
			restUrl += "&host=" + host;
		}

		try {
			
			ObjectNode restResponse ;

			if ( !csapApp.lifeCycleSettings().isEventPublishEnabled() ) {
				ClassPathResource reportStub = new ClassPathResource( "events/report-" + report + ".json" );
				logger.info( "Stubbing out report data using: {},  add csap events services", reportStub );
				restResponse = (ObjectNode) jacksonMapper.readTree( reportStub.getFile() );

				resultsJson.put( "message", "csap-event-service disabled - using stub data" );

			} else {
				restResponse = analyticsTemplate.getForObject( restUrl, ObjectNode.class );
			}
			

			resultsJson = restResponse;
			if ( resultsJson != null ) {
				resultsJson.put( "source", restUrl );

				resultsJson.put( "updated", shortFormatter.format( new Date() ) );

			}
		} catch (Exception e) {
			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.error( "Failed getting report from url: {}, Reason: {}", restUrl, reason );
			logger.debug( "Stack Trace {}", reason);
			resultsJson.put( "url", restUrl );
			resultsJson.put( "message", "Error during Access: " + reason );
		}

		return resultsJson;
	}

	@Autowired
	TrendCache trendCache;

	@Autowired
	TrendCacheManager trendCacheManager;

	@RequestMapping ( value = "/trend" )
	@CsapDoc ( notes = "Get last cached data, and trigger a refresh if needed" , linkTests = { "Vm Threads",
			"NullException" } , linkGetParams = {
					"report=vm,appId=csapssp.gen,metricsId=threadsTotal,project='SNTC and PSS',life=dev,trending=1",
					"report=testNull,appId=csapssp.gen,project='SNTC and PSS',life=dev" } )
	public ObjectNode analyticsTrendReport (	@RequestParam ( value = "report" , required = false ) String report,
												@RequestParam ( value = "appId" , required = true ) String appId,
												@RequestParam ( value = "project" , required = true ) String project,
												@RequestParam ( value = CSAP.SERVICE_PORT_PARAM , required = false ) String serviceName,
												@RequestParam ( value = "host" , required = false ) String host,
												@RequestParam ( value = "life" , required = false ) String life,
												@RequestParam ( value = "numDays" , required = false , defaultValue = "1" ) int numDays,
												@RequestParam ( value = "dateOffSet" , required = false , defaultValue = "0" ) int dateOffSet,
												@RequestParam ( value = "trending" , required = false , defaultValue = "0" ) int trending,
												@RequestParam ( value = "trendDivide" , required = false , defaultValue = "0" ) String trendDivide,
												@RequestParam ( value = "allVmTotal" , required = false , defaultValue = "" ) String allVmTotal,
												@RequestParam ( value = "metricsId" , required = false , defaultValue = "topCpu" ) String metricsId,
												@RequestParam ( value = "resource" , required = false , defaultValue = "resource_30" ) String resource )
			throws Exception {

		String restUrl = csapApp.lifeCycleSettings().getReportUrl() + report
				+ "?life=" + life
				+ "&appId=" + appId
				+ "&dateOffSet=" + dateOffSet;

		if ( !project.contains( CSAP.ALL_PACKAGES ) ) {
			restUrl += "&project=" + project;
		}
		// logger.warn( "SLEEPING 10000 for testing" );
		// Thread.sleep( 10000 );
		if ( trending != 0 ) {
			restUrl += "&trending=true&metricsId=" + metricsId;
			if ( trendDivide.length() > 1 ) {
				String[] divides = trendDivide.split( "," );
				for ( String div : divides ) {
					restUrl += "&divideBy=" + div;
				}
			}
			if ( allVmTotal.length() > 0 ) {
				restUrl += "&allVmTotal=" + allVmTotal;
			}
		}

		if ( serviceName != null && !MetricCategory.isAllServices( serviceName ) ) {
			restUrl += "&serviceName=" + serviceName;
		}
		if ( host != null ) {
			restUrl += "&host=" + host;
		}

		ObjectNode resultsJson = trendCache.get( restUrl + "&numDays=" + numDays );

		String timerName = project + "." + report + ".days.";

		if ( trendCacheManager.isRefreshNeeded( resultsJson, numDays ) ) {
			logger.debug( "Updating cache on background thread to not impact UI" );
			trendCacheManager.updateInBackground( restUrl + "&numDays=" + numDays, numDays, timerName + numDays );
			// if ( numDays == 14 || numDays == 16 ) {
			//
			// if ( trendCacheManager.isInitialLoad( resultsJson ) ) {
			// // if we have results for 2weeks - and no other results - lets
			// lazy load for enhanced UI
			// logger.info( "{} - Refreshing {}", project, report );
			// for ( int reportDay : reportDays ) {
			// trendCacheManager.updateInBackground( restUrl + "&numDays=" +
			// reportDay,
			// reportDay, timerName + reportDay );
			// }
			// }
			//
			// }

		}

		return resultsJson;
	}

	// 365
	private int[] reportDays = new int[] { 21, 28, 56, 112, 182, 365 };

	@RequestMapping ( value = "/batchStart" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode startBatch (
									@RequestParam ( CSAP.SERVICE_PORT_PARAM ) ArrayList<String> services,
									@RequestParam ( CSAP.HOST_PARAM ) ArrayList<String> hosts,
									HttpServletRequest request ) {

		String uiUser = securityConfig.getUserIdFromContext();
		logger.info( "User: {}, services: {}, hosts:{}",
			uiUser, services, hosts );

		ObjectNode resultsJson = jacksonMapper.createObjectNode();

		List<String> requestedNames = services;

		createBatchThreadPool();
		resultsJson.put( "result", "Batch start has been scheduled." );
		resultsJson.put( "hosts", hosts.size() );
		resultsJson.put( "services", services.size() );
		resultsJson.put( "parallelRequests",
			csapApp.lifeCycleSettings().getNumberWorkerThreads() );

		ObjectNode hostMap = resultsJson.putObject( "hostInfo" );

		int operationCount = 0;
		int jobCount = 0;
		for ( String host : hosts ) {
			ObjectNode hostInfo = hostMap.putObject( host );
			String[] hostBatch = { host };

			List<String> batchServices = csapApp
				.getServicesOnTargetHost( host )
				.stream()
				.filter( instance -> requestedNames.contains( instance.getServiceName() ) )
				.map( ServiceInstance::getServiceName_Port )
				.collect( Collectors.toList() );

			operationCount += batchServices.size();

			if ( batchServices.size() == 0 ) {
				hostInfo.put( "info", "Skipping - no services on host" );
				continue;
			} else {
				if ( batchServices.size() != requestedNames.size() ) {
					hostInfo.put( "info",
						"Scheduling services (host filtered)" );
				} else {
					hostInfo.put( "info", "Scheduling services" );
				}
				ArrayNode servicesNode = hostInfo.putArray( "services" );
				for ( String svc : batchServices ) {
					servicesNode.add( svc );
				}
			}

			jobCount++;
			Runnable startServicesJob = () -> {
				logger.debug( "Batch Start: {}", host );
				try {
					ObjectNode results;
					if ( Application.isJvmInManagerMode() ) {

						results = serviceCommands.startRemoteRequests(
							uiUser,
							batchServices, hosts,
							null, null,
							null, null, null,
							csapApp.lifeCycleSettings().getAgentUser(),
							csapApp.lifeCycleSettings().getAgentPass(),
							null );
					} else {
						results = serviceCommands.startRequest(
							uiUser, batchServices,
							null, null, null, null, null, null, null );
					}

					Thread.sleep( 1000 * batchRandom.nextInt( 5 ) );
					logger.debug( "{} Completed ", host );
					logger.info( "Results: \n {}",
						jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( results ) );
				} catch (Throwable t) {
					logger.error( "{} start failed: \n", host, t );
				}
			};
			batchExecutor.submit( startServicesJob );
		}

		resultsJson.put( "jobsOperations", operationCount );
		resultsJson.put( "jobsCount", jobCount );
		resultsJson.put( "jobsRemaining", jobCount );

		return resultsJson;
	}

	@RequestMapping ( value = "/batchDeploy" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode deployBatch (
									@RequestParam ( value = CSAP.PACKAGE_PARAM , required = true ) String releasePackage,
									@RequestParam ( CSAP.SERVICE_PORT_PARAM ) ArrayList<String> services,
									@RequestParam ( CSAP.HOST_PARAM ) ArrayList<String> hosts,
									HttpServletRequest request ) {

		String uiUser = securityConfig.getUserIdFromContext();
		logger.info( "User: {}, services: {}, hosts:{}",
			uiUser, services, hosts );

		ObjectNode resultsJson = jacksonMapper.createObjectNode();

		createBatchThreadPool();
		resultsJson.put( "result", "Batch start has been scheduled." );
		resultsJson.put( "hosts", hosts.size() );
		resultsJson.put( "services", services.size() );
		resultsJson.put( "parallelRequests",
			csapApp.lifeCycleSettings().getNumberWorkerThreads() );

		ObjectNode hostInfo = resultsJson.putObject( "hostInfo" );

		int operationsCount = 0;
		int jobCount = 0;

		HashSet<String> servicePortSet = new HashSet<>();
		for ( String serviceName : services ) {
			List<ServiceInstance> instances = csapApp.getModel( releasePackage )
				.getAllPackagesModel()
				.serviceInstancesInCurrentLifeByName()
				.get( serviceName );

			List<String> batchServices = instances
				.stream()
				.map( ServiceInstance::getServiceName_Port )
				.distinct()
				.collect( Collectors.toList() );

			// String serviceNamePort = instances.get( 0
			// ).getServiceName_Port();

			List<String> batchHosts = instances.stream()
				.filter( instance -> hosts.contains( instance.getHostName() ) )
				.map( instance -> instance.getHostName() )
				.collect( Collectors.toList() );

			operationsCount += batchHosts.size();

			if ( batchHosts.size() == 0 ) {
				ObjectNode hostNode = (ObjectNode) hostInfo.putObject( serviceName );
				hostNode.put( "info", "No Services found that match: " + batchServices );
				logger.debug( "Skipping {} - no services on any selected hosts", batchServices );
				continue;
			} else {
				for ( String host : batchHosts ) {
					ObjectNode hostNode = (ObjectNode) hostInfo.get( host );
					if ( hostNode == null ) {
						hostNode = hostInfo.putObject( host );
						hostNode.put( "info", "Deploying Services" );
					}
					ArrayNode hostServiceNode = (ArrayNode) hostNode.get( "services" );
					if ( hostServiceNode == null ) {
						hostServiceNode = hostNode.putArray( "services" );
					}
					batchServices.stream().forEach( hostServiceNode::add );
				}
			}

			jobCount++;
			Runnable deployServiceJobs = () -> {
				logger.debug( "Batch Start: {}", batchServices );
				try {

					ObjectNode results = serviceCommands.deployRemoteRequests(
						"ms" + System.currentTimeMillis(),
						uiUser,
						batchServices, batchHosts,
						"dummy", "dummy", "dummy",
						null, null, null,
						ServiceOsManager.MAVEN_DEFAULT_BUILD, null,
						null,
						csapApp.lifeCycleSettings().getAgentUser(),
						csapApp.lifeCycleSettings().getAgentPass() );
					// ObjectNode results = rebuildServer( firstHost,
					// "dummy", "dummy", "dummy", serviceNamePort,
					// null, null, scpHosts, null,
					// ServiceOsManager.MAVEN_DEFAULT_BUILD, true, null, user,
					// null );
					// stagger the jobs to distribute compute a bit
					Thread.sleep( 1000 * batchRandom.nextInt( 5 ) );
					logger.debug( "{} Completed ", batchServices );
					logger.debug( "Results: \n {}",
						jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( results ) );
				} catch (Throwable e) {
					logger.error( "{} deploy failed", batchServices, e );
				}
			};
			batchExecutor.submit( deployServiceJobs );
		}

		resultsJson.put( "jobsOperations", operationsCount );
		resultsJson.put( "jobsCount", jobCount );
		resultsJson.put( "jobsRemaining", jobCount );

		return resultsJson;
	}

	@Inject
	FileRequests fileRequests;
	public final static String DEPLOY_PROGRESS_URL = "/query/deployProgress";

	@RequestMapping ( value = { DEPLOY_PROGRESS_URL } , produces = MediaType.APPLICATION_JSON_VALUE )
	@ResponseBody
	public JsonNode deployProgress (
										@RequestParam ( CSAP.HOST_PARAM ) String hostName,
										@RequestParam ( CSAP.SERVICE_PORT_PARAM ) String serviceName_port,
										@RequestParam ( FileRequests.LOG_FILE_OFFSET_PARAM ) long offsetLong )
			throws IOException {

		String fromFolder = "//" + serviceName_port + "_deploy.log";
		JsonNode progress = null;

		long bufferSize = 100 * 1024;
		if ( Application.isJvmInManagerMode() ) {
			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>();

			urlVariables.set( CSAP.SERVICE_PORT_PARAM, serviceName_port );
			urlVariables.set( FileRequests.LOG_FILE_OFFSET_PARAM, Long.toString( offsetLong ) );
			String url = CsapCoreService.SERVICE_URL + DEPLOY_PROGRESS_URL;
			List<String> hosts = new ArrayList<>();
			hosts.add( hostName );
			progress = serviceOsManager
				.remoteAgentsStateless( hosts, url, urlVariables )
				.get( hostName );

		} else {
			File targetFile = csapApp.getRequestedFile( fromFolder, serviceName_port, false );
			logger.debug( "Getting progress from: {}", targetFile.getAbsolutePath() );
			progress = fileRequests.readFileChanges( bufferSize, offsetLong, targetFile );
		}

		return progress;
	}

	@Inject
	private StandardPBEStringEncryptor encryptor;

	public static final String REBUILD_URL = "/rebuildServer";

	@RequestMapping ( { REBUILD_URL } )
	public ObjectNode deployService (
										@RequestParam ( CSAP.SERVICE_PORT_PARAM ) ArrayList<String> services,
										@RequestParam ( CSAP.HOST_PARAM ) ArrayList<String> primaryHost,

										// required parameters
										String scmUserid,
										String scmPass,
										String scmBranch,

										@RequestParam ( required = false ) String commandArguments,
										@RequestParam ( required = false ) String runtime,
										@RequestParam ( required = false ) ArrayList<String> targetScpHosts,
										@RequestParam ( required = false ) String hotDeploy,
										@RequestParam ( required = false ) String mavenDeployArtifact,
										@RequestParam ( required = false , defaultValue = "true" ) boolean doEncrypt,
										@RequestParam ( required = false ) String scmCommand,
										HttpServletRequest request )
			throws IOException {

		String uiUser = securityConfig.getUserIdFromContext();

		logger.info(
			"User: {}, services: {}, mavenDeployArtifact:{},  primaryHost: {}, transferHosts: {} commandArguments: {}, runtime: {}, hotDeploy: {}, scmUserid: {}, scmBranch: {} ",
			uiUser, services, mavenDeployArtifact, primaryHost, targetScpHosts, commandArguments, runtime, hotDeploy, scmUserid,
			scmBranch );

		ObjectNode resultsJson;
		String sourcePassword = encryptor.encrypt( scmPass ); // immediately
		// encrypt
		// pass

		if ( !doEncrypt ||
				(Application.isRunningOnDesktop() && Application.isJvmInManagerMode()) ) {
			sourcePassword = scmPass;
		}

		if ( Application.isJvmInManagerMode() ) {

			List<String> allHostsPrimaryFirst = new ArrayList<>();
			allHostsPrimaryFirst.addAll( primaryHost );
			if ( targetScpHosts != null ) {
				allHostsPrimaryFirst.addAll( targetScpHosts );
			}
			resultsJson = serviceCommands.deployRemoteRequests(
				"ms" + System.currentTimeMillis(),
				uiUser, services, allHostsPrimaryFirst,
				scmUserid, scmPass, scmBranch,
				commandArguments, runtime, hotDeploy,
				mavenDeployArtifact, scmCommand,
				null,
				csapApp.lifeCycleSettings().getAgentUser(),
				csapApp.lifeCycleSettings().getAgentPass() );

		} else {
			resultsJson = serviceCommands.deployRequest(
				uiUser, null,
				"ms" + System.currentTimeMillis(),
				services,
				scmUserid, sourcePassword, scmBranch,
				commandArguments, runtime,
				hotDeploy, mavenDeployArtifact, scmCommand,
				null,
				null );
		}

		return resultsJson;

	}

	public static final String START_URL = "/startServer";

	@RequestMapping ( START_URL )
	public ObjectNode startServer (
									@RequestParam ( CSAP.SERVICE_PORT_PARAM ) ArrayList<String> services,
									@RequestParam ( CSAP.HOST_PARAM ) ArrayList<String> hosts,
									@RequestParam ( required = false ) String commandArguments,
									@RequestParam ( required = false ) String runtime,
									@RequestParam ( required = false ) String hotDeploy,
									@RequestParam ( required = false ) String startClean,
									@RequestParam ( required = false ) String noDeploy,
									HttpServletRequest request )
			throws IOException {

		String uiUser = securityConfig.getUserIdFromContext();

		logger.info(
			"User: {},  hosts: {}, services: {} commandArguments: {}, runtime: {}, hotDeploy: {}, startClean: {}, noDeploy: {} ",
			uiUser, hosts, services, commandArguments, runtime, hotDeploy, startClean, noDeploy );

		ObjectNode resultsJson;

		if ( Application.isJvmInManagerMode() ) {

			resultsJson = serviceCommands.startRemoteRequests(
				uiUser, services, hosts,
				commandArguments, runtime,
				hotDeploy, startClean, noDeploy,
				csapApp.lifeCycleSettings().getAgentUser(),
				csapApp.lifeCycleSettings().getAgentPass(),
				null );

		} else {
			resultsJson = serviceCommands.startRequest(
				uiUser, services,
				commandArguments, runtime,
				hotDeploy, startClean, noDeploy,
				null,
				null );
		}

		return resultsJson;

	}

	@RequestMapping ( value = "/stopServer" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode stopServer (
									@RequestParam ( value = CSAP.HOST_PARAM , required = false ) ArrayList<String> hosts,
									@RequestParam ( CSAP.SERVICE_PORT_PARAM ) String[] svcNameArray,
									HttpServletRequest request ) {

		logger.info( "User: " + securityConfig.getUserIdFromContext() + " hostName : "
				+ hosts + " Services: " + Arrays.toString( svcNameArray ) );

		ObjectNode resultsJson = jacksonMapper.createObjectNode();

		if ( Application.isJvmInManagerMode() ) {

			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>();
			for ( String service : svcNameArray ) {
				urlVariables.add( CSAP.SERVICE_PORT_PARAM, service );
			}

			String url = CsapCoreService.SERVICE_URL + "/stopServer";

			if ( hosts != null ) {
				resultsJson = serviceOsManager.remoteAgentsUsingUserCredentials( hosts, url, urlVariables, request );
			} else {
				resultsJson.put( CSAP.CONFIG_PARSE_ERROR, " - Failed to find hostName: " + hosts );
			}
			return resultsJson;

		}
		for ( int i = 0; i < svcNameArray.length; i++ ) {

			String svcName = svcNameArray[i];
			logger.info( "service : " + svcName );

			ArrayList<String> params = new ArrayList<String>();

			// Runs a blocking request 
			resultsJson.put( svcName,
				serviceOsManager.runScript( 
					securityConfig.getUserIdFromContext(), 
					ServiceOsManager.STOP_FILE, 
					svcName,
					params,
					null, null ) );

		}

		logger.info( "Completed" );
		return resultsJson;

	}

	@RequestMapping ( value = "/reImage" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode reImage (
								@RequestParam ( value = CSAP.HOST_PARAM , required = false ) ArrayList<String> hosts,
								@RequestParam ( value = CSAP.SERVICE_PORT_PARAM , required = false ) String[] svcNameArray,
								HttpServletRequest request )
			throws IOException {

		logger.info( "\n\t hosts: {}, \n\t services: {}", hosts, Arrays.toString( svcNameArray ) );

		ObjectNode resultsJson = jacksonMapper.createObjectNode();

		if ( Application.isJvmInManagerMode() ) {

			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>();
			if ( svcNameArray != null && svcNameArray.length > 0 ) {
				for ( String service : svcNameArray ) {
					if ( service.trim().length() != 0 ) {
						urlVariables.add( CSAP.SERVICE_PORT_PARAM, service );
					}
				}
			}
			String url = CsapCoreService.SERVICE_URL + "/reImage";

			if ( hosts != null ) {
				resultsJson = serviceOsManager.remoteAgentsUsingUserCredentials( hosts, url, urlVariables, request );
			} else {
				resultsJson.put( CSAP.CONFIG_PARSE_ERROR, " - Failed to find hostName: " + hosts );
			}
			return resultsJson;

		}

		// Use file to trigger reImage process
		serviceOsManager.getReImageFile().createNewFile();

		// pkill any middleware instances specified
		if ( svcNameArray != null && svcNameArray.length > 0 ) {
			for ( ServiceInstance instance : csapApp.getServicesOnHost() ) {
				if ( instance.getUser() != null && !instance.getUser().equals( csapApp.getAgentRunUser() )
						&& Application.isRunningAsRoot() ) {

					for ( String sevicePort : svcNameArray ) {

						if ( sevicePort.equals( instance.getServiceName_Port() ) ) {
							logger.info( "pkill on user: " + instance.getUser() );

							List<String> parmList = Arrays.asList( "bash", "-c",
								"sudo /usr/bin/pkill -9 -u " + instance.getUser() );
							// osCommandRunner.executeString(parmList);
							resultsJson.put( "pkill", osCommandRunner
								.executeString( parmList, csapApp.getStagingFolder(),
									null, null, 600, 1, null ) );
							break;
						}

					}
				}
			}
		}

		// Now clean up the deploy artifacts, copying back in CsAgent
		List<String> parmList = Arrays.asList(
			"bash",
			"-c",
			"mv CsAgent* .. ; "
					+ "rm -rf * ; "
					+ "mv ../CsAgent* . ;" );

		sendCsapEvent( "Host ReImage", parmList.toString() );
		// osCommandRunner.executeString(parmList);
		osCommandRunner.executeString( parmList,
			csapApp.getDeploymentStorageFolder(), null, null, 600, 1, null );

		String svcName = csapApp.AGENT_NAME_PORT ;
		ArrayList<String> params = new ArrayList<String>();

		params.add( "-cleanType" );
		params.add( "super" );

		resultsJson.put( svcName,
			serviceOsManager.runScript( securityConfig.getUserIdFromContext(), ServiceOsManager.KILL_FILE, svcName,
				params,
				null,
				null ) );

		return resultsJson;

	}

	@RequestMapping ( value = "/batchKill" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode killBatch (
									@RequestParam ( CSAP.SERVICE_PORT_PARAM ) ArrayList<String> services,
									@RequestParam ( CSAP.HOST_PARAM ) ArrayList<String> hosts,
									@RequestParam ( defaultValue = "" ) String clean,
									HttpServletRequest request ) {

		String uiUser = securityConfig.getUserIdFromContext();
		logger.info( "User: {}, services: {}, hosts:{}",
			uiUser, services, hosts );

		ObjectNode resultsJson = jacksonMapper.createObjectNode();

		List<String> requestedNames = services;

		createBatchThreadPool();
		resultsJson.put( "result", "Batch kill has been scheduled." );
		resultsJson.put( "hosts", hosts.size() );
		resultsJson.put( "services", services.size() );
		resultsJson.put( "parallelRequests",
			csapApp.lifeCycleSettings().getNumberWorkerThreads() );

		ObjectNode hostMap = resultsJson.putObject( "hostInfo" );
		int operationCount = 0;
		int jobCount = 0;
		for ( String host : hosts ) {

			ObjectNode hostInfo = hostMap.putObject( host );
			ArrayList<String> batchHost = new ArrayList<>();
			batchHost.add( host );

			List<String> batchServices = csapApp
				.getServicesOnTargetHost( host )
				.stream()
				.filter( instance -> requestedNames.contains( instance.getServiceName() ) )
				.map( ServiceInstance::getServiceName_Port )
				.collect( Collectors.toList() );

			operationCount += batchServices.size();
			if ( batchServices.size() == 0 ) {
				hostInfo.put( "info", "Skipping - no services on host" );
				continue;
			} else {
				if ( batchServices.size() != requestedNames.size() ) {
					hostInfo.put( "info",
						"Scheduling services (host filtered)" );
				} else {
					hostInfo.put( "info", "Scheduling services" );
				}
				ArrayNode responseServices = hostInfo.putArray( "services" );
				for ( String svc : batchServices ) {
					responseServices.add( svc );
				}
			}

			jobCount++;
			Runnable killTask = () -> {
				logger.debug( "Batch Delete: {}", host );
				try {
					ObjectNode clusterResponse;
					if ( Application.isJvmInManagerMode() ) {
						clusterResponse = serviceCommands.killRemoteRequests(
							uiUser,
							batchServices, batchHost, clean, "keepLogs",
							csapApp.lifeCycleSettings().getAgentUser(),
							csapApp.lifeCycleSettings().getAgentPass() );
					} else {
						clusterResponse = serviceCommands.killRequest(
							uiUser, batchServices, clean, "keepLogs", null );
					}

					logger.debug( "{} Completed ", host );
					logger.debug( "Results: \n {}",
						jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( clusterResponse ) );
				} catch (Throwable t) {
					logger.error( "{} kill failed: \n", host, t );
				}
			};
			batchExecutor.submit( killTask );
		}

		resultsJson.put( "jobsOperations", operationCount );
		resultsJson.put( "jobsCount", jobCount );
		resultsJson.put( "jobsRemaining", jobCount );

		return resultsJson;
	}

	BasicThreadFactory batchFactory = new BasicThreadFactory.Builder()
		.namingPattern( "CsapBatchThread-%d" )
		.daemon( true )
		.priority( Thread.MAX_PRIORITY )
		.build();

	final BlockingQueue<Runnable> batchQueue = new ArrayBlockingQueue<>( 1000 );
	private ExecutorService batchExecutor = null;

	private void createBatchThreadPool () {
		if ( batchExecutor == null ) {
			logger.info( "Creating batch thread pool" );
			//
			// batchExecutor = Executors.newFixedThreadPool(
			// csapApp.getCurrentLifecycleMetaData().getNumberWorkerThreads(),
			// batchFactory );
			int numThreads = csapApp.lifeCycleSettings().getNumberWorkerThreads();
			// int numThreads = 1 ;
			batchExecutor = new ThreadPoolExecutor( numThreads, numThreads,
				0L, TimeUnit.MILLISECONDS,
				batchQueue, batchFactory );
			// batchExecutor = Executors.newFixedThreadPool(
			// 1, batchFactory );
		}
	}

	@RequestMapping ( value = "/batchJobs" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode batchJobs () {

		// logger.info("got request");
		ObjectNode jobsNode = jacksonMapper.createObjectNode();

		jobsNode.put( "jobsRemaining", batchQueue.size() );

		if ( Application.isJvmInManagerMode() ) {
			jobsNode.put( "tasksRemaining", csapApp.getHostStatusManager().totalOpsQueued() );
		} else {
			jobsNode.put( "tasksRemaining", serviceOsManager.getOpsQueued() );
		}

		return jobsNode;
	}

	public static final String KILL_URL = "/killServer";

	@RequestMapping ( KILL_URL )
	public ObjectNode killServer (
									@RequestParam ( CSAP.SERVICE_PORT_PARAM ) ArrayList<String> services,
									@RequestParam ( CSAP.HOST_PARAM ) ArrayList<String> hosts,
									@RequestParam ( required = false ) String clean,
									@RequestParam ( required = false ) String keepLogs,
									HttpServletRequest request )
			throws IOException {

		String uiUser = securityConfig.getUserIdFromContext();

		logger.info( "User: {},  hosts: {}, services: {} clean: {}, keepLogs: {} ",
			uiUser, hosts, services, clean, keepLogs );

		ObjectNode resultsJson;

		if ( Application.isJvmInManagerMode() ) {

			resultsJson = serviceCommands.killRemoteRequests(
				uiUser,
				services, hosts,
				clean, keepLogs,
				csapApp.lifeCycleSettings().getAgentUser(),
				csapApp.lifeCycleSettings().getAgentPass() );

		} else {
			resultsJson = serviceCommands.killRequest( uiUser, services, clean, keepLogs, null );
		}

		return resultsJson;

	}

	@RequestMapping ( value = "/runServiceJob" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode runServiceJob (
										@RequestParam ( value = CSAP.HOST_PARAM , required = false ) ArrayList<String> hosts,
										@RequestParam ( CSAP.SERVICE_PORT_PARAM ) String[] svcNameArray,
										@RequestParam ( "jobToRun" ) String jobToRun,
										HttpServletRequest request )
			throws Exception {

		logger.info( "User: {}, Service(s): {}, Host(s): {}",
			getUser( null ), Arrays.toString( svcNameArray ), hosts );

		ObjectNode resultsJson = jacksonMapper.createObjectNode();

		if ( Application.isJvmInManagerMode() ) {

			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>();
			for ( String service : svcNameArray ) {
				urlVariables.add( CSAP.SERVICE_PORT_PARAM, service );
				urlVariables.add( "jobToRun", jobToRun );
			}

			String url = CsapCoreService.SERVICE_URL + "/runServiceJob";

			if ( hosts != null ) {
				resultsJson = serviceOsManager.remoteAgentsUsingUserCredentials( hosts, url, urlVariables, request );

			} else {
				resultsJson.put( CSAP.CONFIG_PARSE_ERROR, " - Failed to find hostName: " + hosts );
			}
			return resultsJson;

		}

		for ( int i = 0; i < svcNameArray.length; i++ ) {
			String serviceName_port = svcNameArray[i];
			ServiceInstance serviceInstance = csapApp.getServiceInstanceCurrentHost( serviceName_port );

			String jobResults = osManager.getJobRunner()
				.runJobUsingDescription(
					serviceInstance,
					jobToRun );

			resultsJson.put( serviceName_port, jobResults );

			csapEventClient.generateEvent(
				serviceInstance.getServiceName(),
				securityConfig.getUserIdFromContext(),
				"Job: " + jobToRun, jobResults.toString() );

		}

		return resultsJson;

	}

	private String getUser ( String ssoCookie ) {
		String user = "batch";
		if ( ssoCookie == null ) {
			user = securityConfig.getUserIdFromContext();
		}
		return user;
	}

	/**
	 *
	 *
	 * trigger a mvn verify in the background via nohup. Should be configured to
	 * run jmeter via pom.xml plgun
	 *
	 * @param hostNameArray
	 * @param svcNameArray
	 * @param requireXml
	 * @param request
	 * @param response
	 * @throws IOException
	 */
	@RequestMapping ( value = "/jmeter" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode jmeter (
								@RequestParam ( value = CSAP.HOST_PARAM , required = false ) ArrayList<String> hosts,
								@RequestParam ( CSAP.SERVICE_PORT_PARAM ) String[] svcNameArray,
								HttpServletRequest request ) {

		logger.info( "hostName : " + hosts + " svcName count: "
				+ svcNameArray.length );

		ObjectNode resultsJson = jacksonMapper.createObjectNode();

		if ( Application.isJvmInManagerMode() ) {

			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>();
			for ( String service : svcNameArray ) {
				urlVariables.add( CSAP.SERVICE_PORT_PARAM, service );
			}

			String url = CsapCoreService.SERVICE_URL + "/jmeter";

			if ( hosts != null ) {
				resultsJson = serviceOsManager.remoteAgentsUsingUserCredentials( hosts, url, urlVariables, request );
			} else {
				resultsJson.put( CSAP.CONFIG_PARSE_ERROR, " - Failed to find hostName: " + hosts );
			}
			return resultsJson;
		}
		for ( int i = 0; i < svcNameArray.length; i++ ) {

			String svcName = svcNameArray[i];
			ArrayList<String> params = new ArrayList<String>();

			// check for host
			resultsJson.put( svcName,
				serviceOsManager.runScript( securityConfig.getUserIdFromContext(), MAVEN_VERIFY_FILE,
					svcName, params, null, null ) );
		}

		return resultsJson;

	}

	@RequestMapping ( value = "/purgeDeployCache" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode purgeDeployCaches (

											@RequestParam ( CSAP.SERVICE_PORT_PARAM ) ArrayList<String> services,
											@RequestParam ( CSAP.HOST_PARAM ) ArrayList<String> hosts,
											@RequestParam ( value = "global" , required = false ) String global,
											HttpServletRequest request ) {

		String uiUser = securityConfig.getUserIdFromContext();
		logger.info( "User: {}, services: {}, hosts:{}, global: {}",
			uiUser, services, hosts, global );

		ObjectNode resultsJson = jacksonMapper.createObjectNode();

		if ( Application.isJvmInManagerMode() ) {

			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>();
			urlVariables.put( CSAP.SERVICE_PORT_PARAM, services );
			// }
			urlVariables.add( "global", global );

			String url = CsapCoreService.SERVICE_URL + "/purgeDeployCache";

			resultsJson = serviceOsManager.remoteAgentsUsingUserCredentials(
				hosts, url, urlVariables,
				request );

			return resultsJson;

		}
		for ( String service : services ) {

			ArrayList<String> params = new ArrayList<String>();

			if ( global != null ) {
				// triggers complete empty of the maven and build
				// folders
				params.add( "-serviceName" );
				params.add( "GLOBAL" ); // hardcoded in purge script
			}
			resultsJson.put( service,
				serviceOsManager.runScript(
					uiUser,
					PURGE_FILE, service,
					params, null, null ) );
		}

		return resultsJson;

	}

	private Random batchRandom = new Random();

	@RequestMapping ( value = "/getServicesInLifeCycleDetail" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode getServicesInLifeCycleDetail ( HttpSession session )
			throws Exception {

		ObjectNode servicesDetailJson = jacksonMapper.createObjectNode();

		if ( !Application.isJvmInManagerMode() ) {

			ObjectNode serviceToRuntimeNode = csapApp.getHostLoadCpuAndMore();

			servicesDetailJson.set( HostKeys.hostStats.jsonId, serviceToRuntimeNode );

			ArrayNode servicesNode = servicesDetailJson.putArray( "services" );

			for ( ServiceInstance instance : csapApp.getServicesOnHost() ) {
				servicesNode.add( instance.getRuntime() );
				// rootNode.put(serviceName,
				// instance.getRuntime());
			}

		} else {
			ArrayList<String> lifeCycleHostList = csapApp.getLifeCycleToHostMap()
				.get( Application.getCurrentLifeCycle() );

			for ( String host : lifeCycleHostList ) {
				if ( csapApp.getHostStatusManager().getResponseFromHost( host ) != null ) {

					servicesDetailJson.set( host,
						csapApp.getHostStatusManager().getResponseFromHost( host ) );

				}
			}
		}

		isSessionExpired( session );

		return servicesDetailJson;

	}

	@RequestMapping ( value = "/getServicesInLifeCycleSummary" , produces = MediaType.APPLICATION_JSON_VALUE )

	@CsapDoc ( notes = "Summary information for lifecycle, including services, hosts, scoreCard, errors " , linkTests = { "default package",
			"releasePackageExample" } , linkGetParams = { "blocking=true",
					"releasePackage=someReleasePackge,blocking=true" } , produces = { MediaType.APPLICATION_JSON_VALUE } )
	public ObjectNode getServicesInLifeCycleSummary (
														@RequestParam ( value = "blocking" , required = false ) boolean blocking,
														@RequestParam ( value = CSAP.PACKAGE_PARAM , required = false ) String releasePackage,
														@RequestParam ( value = "cluster" , required = false ) String cluster,
														HttpSession session )
			throws IOException {

		SimonManager.getCounter( "users.getSummary" ).increase();

		String clusterFilter = Application.getCurrentLifeCycle();
		if ( cluster != null ) {
			clusterFilter = cluster;
		}

		logger.debug( "cluster: {}, clusterFilter: {}, blocking: {}, releasePackage: {}, ",
			cluster, clusterFilter, blocking, releasePackage );

		if ( releasePackage == null ) {
			releasePackage = csapApp.getActiveModelName();
		}
		// ArrayList<String> lifeCycleHostList = csapApp
		// .getLifeCycleToHostMap().get(clusterFilter);

		ReleasePackage activeModel = csapApp.getModel( releasePackage );

		ObjectNode summaryJson = jacksonMapper.createObjectNode();

		if ( activeModel == null ) {
			summaryJson.put( "errors", "Model was null, verify parameters,  releasePackage: "
					+ releasePackage );

			return summaryJson;
		}

		ArrayList<String> lifeCycleHostList = activeModel.getLifeCycleToHostMap()
			.get( clusterFilter );

		ArrayNode newsJsonArray = summaryJson.putArray( "news" );
		newsJsonArray.addAll( csapApp.lifeCycleSettings().getNewsJsonArray() );

		ArrayList<String> errorList = new ArrayList<String>();
		if ( lifeCycleHostList == null ) {

			clusterFilter = Application.getCurrentLifeCycle();

			errorList.add( "Cluster filter specified in definition not found: " + cluster
					+ " Reverting to default: " + clusterFilter );
			lifeCycleHostList = activeModel.getLifeCycleToHostMap().get( clusterFilter );
			// lifeCycleHostList =
			// csapApp.getLifeCycleToHostMap().get(
			// clusterFilter);
		}

		logger.debug( "lifeCycleHost map: {}", lifeCycleHostList );

		ObjectNode servicesActiveJson = summaryJson.putObject( "servicesActive" );
		TreeMap<String, Integer> serviceTotalCountMap = new TreeMap<String, Integer>();
		TreeMap<String, String> serviceTypeMap = new TreeMap<String, String>();

		ObjectNode hostMapNode = summaryJson.putObject( "hostStatus" );

		String lastOp = csapApp.getLastOpMessage();
		long lastOpMills = csapApp.getLastOpMillis();

		if ( !Application.isJvmInManagerMode() ) {

			// packages are NOT displayed on node agent
			// summaryJson.put("packageCount",
			// csapApp.getGlobalModel().getReleaseModelMap()
			// .size());
			getServicesSummaryNodeAgent( summaryJson, blocking, clusterFilter, servicesActiveJson,
				serviceTotalCountMap, serviceTypeMap, hostMapNode, errorList );

			summaryJson.set( "users",
				activeUsers.updateUserAccessAndReturnAllActive( securityConfig.getUserIdFromContext(), true ) );
		} else {

			summaryJson.put( "packageCount", csapApp.getRootModel().getReleaseModelCount() );
			lastOp = getServiceSummaryDeployManager( blocking, releasePackage, clusterFilter,
				lifeCycleHostList, summaryJson, servicesActiveJson, serviceTotalCountMap,
				serviceTypeMap, hostMapNode, lastOp, lastOpMills, errorList );

			// First updated the current userid to current host
			activeUsers.updateUserAccessAndReturnAllActive( securityConfig.getUserIdFromContext(), true );

			// Now use the aggregated results to build response
			summaryJson.set( "users", activeUsers.allAdminUsers() );
		}
		Format tsFormater = new SimpleDateFormat( "HH:mm:ss" );
		summaryJson.put( "timeStamp", tsFormater.format( new Date() ) );

		summaryJson.put( "lastOp", lastOp );

		// summaryJson.put("news",
		// jacksonMapper.valueToTree(newsList));
		summaryJson.set( "errors", jacksonMapper.valueToTree( errorList ) );

		summaryJson.set( "servicesTotal", jacksonMapper.valueToTree( serviceTotalCountMap ) );

		summaryJson.set( "servicesType", jacksonMapper.valueToTree( serviceTypeMap ) );

		ArrayList<String> clustersInCurrentLifeCycle = csapApp.getClustersInLifecycle( releasePackage );

		ObjectNode lbNode = summaryJson.putObject( "lbUrls" );
		for ( String lc : csapApp.getLifeToMetaDataMap().keySet() ) {
			LifeCycleSettings lifeMeta = csapApp.getLifeToMetaDataMap().get( lc );
			lbNode.put( lc, lifeMeta.getLbUrl() );
		}

		summaryJson.put( "currentlc", Application.getCurrentLifeCycle() );
		summaryJson.set( "clusters", jacksonMapper.valueToTree( clustersInCurrentLifeCycle ) );

		ObjectNode scoreCardJson = summaryJson.putObject( "scoreCard" );
		scoreCardJson.set( "platform", csapApp.getPlatformScore( blocking ) );
		scoreCardJson.set( "app", csapApp.getApplicationScore() );

		isSessionExpired( session );

		return summaryJson;

	}

	@Inject
	ActiveUsers activeUsers;

	public String getServiceSummaryDeployManager (	boolean blocking, String releasePackage,
													String clusterFilter, ArrayList<String> lifeCycleHostList, JsonNode summaryJson,
													ObjectNode serviceActiveJson, TreeMap<String, Integer> serviceTotalCountMap,
													TreeMap<String, String> serviceTypeMap, ObjectNode hostMapNode, String lastOp,
													long lastOpMills, ArrayList<String> errorList )
			throws IOException, JsonParseException,
			JsonMappingException {

		int totalHosts = lifeCycleHostList.size();
		int totalServices = 0;
		int totalServicesActive = 0;
		int totalHostsActive = 0;
		//
		// Deploy manager case

		if ( blocking ) {
			csapApp.getHostStatusManager().refreshAndWaitForComplete( null );
		}

		// First build totals map
		totalServices = generateTotalsMap( releasePackage, clusterFilter, serviceActiveJson,
			serviceTotalCountMap, serviceTypeMap, totalServices );

		StringBuilder clusterSync = new StringBuilder();

		// CapabilityDataModel activeModel =
		// csapApp.getModel(releasePackage);
		for ( String host : lifeCycleHostList ) {
			ObjectNode hostStatusNode = hostMapNode.putObject( host );
			int hostServiceActive = 0;
			int hostServiceTotal = 0;

			ObjectNode errorNode = csapApp.getAlertsOnRemoteAgent( ServiceAlertsEnum.ALERT_LEVEL, host );

			ArrayNode errors = (ArrayNode) errorNode.get( "errors" );
			if ( errors.size() > 0 ) {

				for ( JsonNode errorText : errors ) {
					errorList.add( errorText.asText() );
				}
			}

			ObjectNode vmServicesAndResources = csapApp.getHostStatusManager()
				.getHostAsJson( host );
			if ( vmServicesAndResources != null ) {
				// logger.info("******************* json for host " + host +
				// ":\n"
				// + vmServicesAndResources);

				logger.debug( "{} json: ", host, vmServicesAndResources );

				JsonNode hostStats = vmServicesAndResources.path( HostKeys.hostStats.jsonId );
				if ( hostStats != null ) {

					if ( hostStats.has( "lastOpMillis" ) ) {
						long hostOpMillis = hostStats.path( "lastOpMillis" ).longValue();
						if ( hostOpMillis > lastOpMills ) {
							lastOpMills = hostOpMillis;
							lastOp = host + ":" + hostStats.path( "lastOp" ).textValue();
						}
					}
					try {

						hostStatusNode.put( "cpuCount",
							Integer.parseInt( hostStats.path( "cpuCount" ).asText() ) );
						double newKB = Math.round( Double.parseDouble( hostStats.path( "cpuLoad" )
							.asText() ) * 10.0 ) / 10.0;
						hostStatusNode.put( "cpuLoad", newKB );

						hostStatusNode.set( "vmLoggedIn", hostStats.path( "vmLoggedIn" ) );

					} catch (Exception e) {
						logger.debug( "Failed parsing {}", hostStats.path( "cpuLoad" ).asText(), e );
					}

					hostStatusNode.put( "du", hostStats.path( "du" ).longValue() );

				} else {
					errorList.add( host + " - Agent response does not contain hostStats." );
				}

				JsonNode serviceNode = vmServicesAndResources.path( "services" );

				for ( Iterator<String> serviceInstanceNameIter = serviceNode.fieldNames(); serviceInstanceNameIter
					.hasNext(); ) {
					String serviceInstanceName = serviceInstanceNameIter.next().trim();

					if ( serviceInstanceName.contains( CSAP.AGENT_CONTEXT ) ) {
						totalHostsActive++;
					}

					ServiceInstance serviceWithRuntimeStats = jacksonMapper.readValue( serviceNode.get( serviceInstanceName ).traverse(),
						ServiceInstance.class );

					// logger.debug(serviceInstance);
					// Grab the matching instance from cluster
					// definintion to check lifecycle
					ServiceInstance serviceWithConfiguration = csapApp.getServiceInstance(
						serviceInstanceName, host, releasePackage );

					// Scripts are ignored from summarys
					if ( serviceWithConfiguration != null && !serviceWithConfiguration.isScript()
							&& serviceWithConfiguration.getLifecycle().startsWith( clusterFilter ) ) {
						hostServiceTotal++;
					}

					if ( serviceActiveJson.get( serviceWithRuntimeStats.getServiceName() ) == null ) {
						clusterSync.append( " " + serviceWithRuntimeStats.getServiceName() );
						continue;
					}

					if ( serviceWithConfiguration != null
							&& serviceWithConfiguration.getLifecycle().startsWith( clusterFilter ) ) {
						logger.debug( "{} remoteCollect: {}",
							serviceWithConfiguration.getServiceName(), serviceWithConfiguration.isRemoteCollection() );

						if ( serviceWithConfiguration.isRemoteCollection() ) {

							if ( serviceWithConfiguration.isTomcatJarsPresent() && serviceWithRuntimeStats.getJvmThreadCount() > 0 ) {
								totalServicesActive++;
								hostServiceActive++;
								int active = serviceActiveJson.get( serviceWithRuntimeStats.getServiceName() )
									.asInt() + 1;
								serviceActiveJson.put( serviceWithRuntimeStats.getServiceName(), active );
								logger.debug( "{} threads: {}",
									serviceWithRuntimeStats.getServiceName(), serviceWithRuntimeStats.getJvmThreadCount() );
							}

						} else if ( !serviceWithRuntimeStats.getCpuUtil().equals( ServiceInstance.INACTIVE ) ) {
							totalServicesActive++;
							hostServiceActive++;
							int active = serviceActiveJson.get( serviceWithRuntimeStats.getServiceName() )
								.asInt() + 1;
							serviceActiveJson.put( serviceWithRuntimeStats.getServiceName(), active );
						}
					}

					// logger.info( serviceInstance) ;
				}
			}

			hostStatusNode.put( "serviceTotal", new Integer( hostServiceTotal ) );
			hostStatusNode.put( "serviceActive", new Integer( hostServiceActive ) );
		}
		if ( clusterSync.length() > 0
				&& clusterFilter.equals( Application.getCurrentLifeCycle() ) ) {
			String error = "Found one or more services not in the cluster definition in localhost. Need to resync cluster ASAP: "
					+ clusterSync;
			logger.error( error );
			errorList.add( error );
		}

		((ObjectNode) summaryJson).put( "totalHostsActive", totalHostsActive );

		((ObjectNode) summaryJson).put( "totalServices", totalServices );

		((ObjectNode) summaryJson).put( "totalServicesActive", totalServicesActive );
		((ObjectNode) summaryJson).put( "totalHosts", totalHosts );

		logger.debug( "Completed request, services: {}, active: {}", totalServices, totalServicesActive );

		return lastOp;
	}

	public int generateTotalsMap (	String releaseFilter, String clusterFilter,
									ObjectNode serviceActiveJson, TreeMap<String, Integer> serviceTotalCountMap,
									TreeMap<String, String> serviceTypeMap, int totalServices ) {

		TreeMap<String, List<ServiceInstance>> serviceToInstanceList = csapApp
			.getModel( releaseFilter ).serviceInstancesInCurrentLifeByName();

		for ( String serviceName : serviceToInstanceList.keySet() ) {

			if ( serviceToInstanceList.get( serviceName ) != null
					&& serviceToInstanceList.get( serviceName ).size() > 0 ) {

				ServiceInstance firstInstance = serviceToInstanceList.get( serviceName ).get( 0 );

				serviceTypeMap.put( serviceName, firstInstance.getServerUiIconType() );

			} else {
				// Skip services not in current lifecycle
				continue;
			}

			if ( clusterFilter.equals( Application.getCurrentLifeCycle() ) ) {
				try {
					serviceActiveJson.put( serviceName, 0 );

					logger.debug( "service: {}, instances: {}", serviceName, serviceToInstanceList.get( serviceName ) );

					if ( serviceToInstanceList.get( serviceName ) != null ) {
						serviceTotalCountMap.put( serviceName, serviceToInstanceList
							.get( serviceName ).size() );

						ServiceInstance instance;

						instance = serviceToInstanceList.get( serviceName ).get( 0 );

						// ignore scripts
						if ( instance != null && !instance.isScript() ) {
							totalServices += serviceToInstanceList.get( serviceName ).size();
						}

					} else {
						logger.warn( "serviceName: " + serviceName
								+ " has a NULL map and is being excluded from totals" );
					}
				} catch (Exception e) {
					logger.error( "Failed to get total for service: " + serviceName, e );
				}

			} else {
				// a filter was applied, so we need to iterate over all
				// services.
				int matchesFound = 0;
				for ( ServiceInstance filterInstance : serviceToInstanceList.get( serviceName ) ) {

					if ( filterInstance.isScript() ) {
						continue;
					}

					if ( filterInstance.getLifecycle().startsWith( clusterFilter ) ) {
						matchesFound++;
					}
				}
				if ( matchesFound > 0 ) {
					serviceActiveJson.put( serviceName, 0 );
					serviceTotalCountMap.put( serviceName, matchesFound );
					totalServices += matchesFound;
				}
			}

		}
		return totalServices;
	}

	public void getServicesSummaryNodeAgent (	ObjectNode summaryJson, boolean blocking,
												String clusterFilter, ObjectNode serviceActiveJson,
												TreeMap<String, Integer> serviceTotalCountMap, TreeMap<String, String> serviceTypeMap,
												ObjectNode hostMapNode, ArrayList<String> errorList )
			throws IOException,
			JsonParseException, JsonMappingException {

		logger.debug( "clusterFilter: {}", clusterFilter );

		int totalServicesActive = 0;
		if ( blocking ) {
			csapApp.updateCache( true );
		} else {
			csapApp.updateCache( false );
		}

		osManager.checkForProcessStatusUpdate();

		errorList
			.add( "Running in single node mode - switch to deployment manager for all lifecycle hosts: "
					+ "<a class= \"simple\" href=\""
					+ csapApp.lifeCycleSettings().getLbUrl()
					+ "/admin/services\">Launch Deployment Manager</a>" );
		//
		// csapApp.getErrorsOnVm(Application.getHOST_NAME(),
		// errorList,
		// (ObjectNode) osManager.getVmRuntime());;
		ObjectNode errorNode = csapApp.getAlertsOnLocalAgent( ServiceAlertsEnum.ALERT_LEVEL );
		ArrayNode errors = (ArrayNode) errorNode.get( "errors" );
		if ( errors.size() > 0 ) {

			for ( JsonNode errorText : errors ) {
				errorList.add( errorText.asText() );
			}
		}

		for ( ServiceInstance instance : csapApp.getServicesOnHost() ) {
			String serviceName = instance.getServiceName();

			if ( !instance.getLifecycle().startsWith( clusterFilter ) ) {
				continue;
			}

			int start = 0;
			if ( serviceTotalCountMap.containsKey( serviceName ) ) {
				start = serviceTotalCountMap.get( serviceName );
			} else {
				serviceTypeMap.put( serviceName, instance.getServerUiIconType() );
				serviceActiveJson.put( serviceName, 0 );
			}

			serviceTotalCountMap.put( serviceName, 1 + start );

			if ( instance.isScript() ) {
				// do nothing for scripts
			} else if ( instance.isRemoteCollection() ) {

				if ( instance.isTomcatJarsPresent() && instance.getJvmThreadCount() > 0 ) {
					start = serviceActiveJson.get( serviceName ).asInt();
					totalServicesActive++;
					serviceActiveJson.put( serviceName, 1 + start );
				}

			} else {
				if ( !instance.getCpuUtil().equals( ServiceInstance.INACTIVE ) ) {
					start = serviceActiveJson.get( serviceName ).asInt();
					totalServicesActive++;
					serviceActiveJson.put( serviceName, 1 + start );
				}
			}

		}
		ObjectNode hostStatusNode = hostMapNode.putObject( Application.getHOST_NAME() );
		hostStatusNode.put( "serviceTotal", csapApp.getServicesOnHost().size() );
		hostStatusNode.put( "serviceActive", totalServicesActive );
		ObjectNode serviceToRuntimeNode = csapApp.getHostLoadCpuAndMore();

		hostStatusNode.set( "vmLoggedIn", serviceToRuntimeNode.path( "vmLoggedIn" ) );

		hostStatusNode.put( "cpuCount",
			Integer.parseInt( serviceToRuntimeNode.path( "cpuCount" ).asText() ) );
		double newKB = Math
			.round( Double.parseDouble( serviceToRuntimeNode.path( "cpuLoad" ).asText() ) * 10.0 ) / 10.0;
		hostStatusNode.put( "cpuLoad", newKB );

		hostStatusNode.put( "du", serviceToRuntimeNode.path( "du" ).longValue() );

		int totalServices = csapApp.getServicesOnHost().size();
		int totalHosts = 1;

		summaryJson.put( "totalHostsActive", totalServicesActive );

		summaryJson.put( "totalServices", totalServices );

		summaryJson.put( "totalServicesActive", totalServicesActive );

		summaryJson.put( "totalHosts", totalHosts );

		return;
	}

	private String SESSION_EXPIRED = "SessionExpired";

	public void isSessionExpired ( HttpSession session ) {
		// Hook for checking for expired SSO cookie.
		if ( session.getAttribute( "renew" ) == null ) {
			session.setAttribute( "renew", System.currentTimeMillis() );
			// logger.info("session.getAttribute(ServiceRequests.PROGRESS_BUFF"
			// + session.getAttribute(ServiceRequests.PROGRESS_BUFF) ) ;
			if ( session.getAttribute( SESSION_EXPIRED ) == null ) {
				session.setAttribute( SESSION_EXPIRED, new StringBuffer() );
			}
		}
		// logger.debug("\n\n ******** session.getAttribute(renew)" +
		// session.getAttribute("renew") + " current: " +
		// System.currentTimeMillis() ) ;
		// hook for expiring sessions. We force SSO validation every hour,
		// but never interupting t
		if ( ((StringBuffer) session.getAttribute( SESSION_EXPIRED )).length() == 0
				&& System.currentTimeMillis() - ((long) session.getAttribute( "renew" )) > 60 * 60 * 1000 ) {
			// 60*60*1000
			// logger.warn("\n\n **************** Forcing session renew
			// *****************")
			// ;
			session.invalidate();
		}
	}

	@RequestMapping ( value = "/getServiceInstances" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode getServiceInstances (
											HttpSession session,
											@RequestParam ( value = "blocking" , required = false , defaultValue = "false" ) boolean blocking,
											@RequestParam ( value = CSAP.PACKAGE_PARAM , required = false ) String releasePackage,
											@RequestParam ( value = CSAP.HOST_PARAM , required = false ) String hostName,
											@RequestParam ( value = CSAP.SERVICE_PORT_PARAM , required = false ) String serviceName_NO_PORT ) {

		logger.debug( "{}:  service: {}, releasePackage: {}, blocking: {}", hostName, serviceName_NO_PORT, releasePackage, blocking );

		if ( releasePackage == null ) {
			releasePackage = csapApp.getActiveModelName();
		}

		SimonManager.getCounter( "users.getInstances" ).increase();

		if ( blocking && Application.isJvmInManagerMode() ) {
			logger.debug( "Blocking request" );
			csapApp.getHostStatusManager().refreshAndWaitForComplete( null );
		}

		ArrayList<JsonNode> serviceStatusArray = new ArrayList<JsonNode>();

		List<ServiceInstance> instanceList;
		if ( hostName != null ) {
			// Hosts Dashboard retrieves services running on specified host
			instanceList = csapApp.getModel( releasePackage )
				.getHostToConfigMap()
				.get( hostName );
		} else {
			// Services Dashboard retrieves service instances on different host
			// instanceList =
			// userSelectedModel.serviceInstancesInCurrentLifeByName().get(
			// serviceName_NO_PORT );
			instanceList = csapApp.serviceInstancesByName(
				releasePackage,
				serviceName_NO_PORT );
		}

		for ( ServiceInstance serviceInstance : instanceList ) {

			// Possible removal as it is not needed?
			if ( !Application.isJvmInManagerMode()
					&& !serviceInstance.getHostName().equals( Application.getHOST_NAME() ) ) {
				continue;
			}
			serviceStatusArray.add( buildServiceInstanceStatus( serviceInstance ) );
		}

		ObjectNode rootNode = jacksonMapper.createObjectNode();
		Format tsFormater = new SimpleDateFormat( "HH.mm.ss" );

		rootNode.put( "cpuTimestamp", tsFormater.format( new Date() ) );

		rootNode.set( "serviceStatus", jacksonMapper.valueToTree( serviceStatusArray ) );

		isSessionExpired( session );

		return rootNode;

	}

	private ObjectNode buildServiceInstanceStatus ( ServiceInstance serviceInstance ) {
		ObjectNode instanceRuntime = jacksonMapper.createObjectNode();
		ObjectNode hostRuntime = null;
		if ( !Application.isJvmInManagerMode() ) {
			instanceRuntime = serviceInstance.getRuntime();
			hostRuntime = csapApp.getHostLoadCpuAndMore();
		} else {
			instanceRuntime = csapApp
				.getHostStatusManager()
				.getServiceRuntime(
					serviceInstance.getHostName(),
					serviceInstance.getServiceName_Port() );

			if ( instanceRuntime == null ) {
				// do not have runtime results, but we can makes a dummy
				// node
				instanceRuntime = jacksonMapper.createObjectNode();
			}

			hostRuntime = null;

			// Host might not have a valid response
			if ( csapApp.getHostStatusManager().getHostAsJson(
				serviceInstance.getHostName() ) != null ) {
				hostRuntime = (ObjectNode) csapApp.getHostStatusManager()
					.getHostAsJson( serviceInstance.getHostName() ).path( HostKeys.hostStats.jsonId );
			}
		}
		instanceRuntime.put( "host", serviceInstance.getHostName() );
		instanceRuntime.put( CSAP.SERVICE_PORT_PARAM, serviceInstance.getServiceName() );
		instanceRuntime.put( "mavenId", serviceInstance.getMavenId() );
		instanceRuntime.put( "context", serviceInstance.getContext() );
		instanceRuntime.put( "launchUrl", serviceInstance.getUrl() );
		instanceRuntime.put( "jmx", serviceInstance.getJmxPort() );
		instanceRuntime.put( "jmxrmi", serviceInstance.isJmxRmi() );
		if ( serviceInstance.isRemoteCollection() ) {
			instanceRuntime.put( "jmx", serviceInstance.getCollectHost() + ":" + serviceInstance.getCollectPort() );
		}
		instanceRuntime.put( "autoStart", serviceInstance.getRawAutoStart() );
		instanceRuntime.put( "port", serviceInstance.getPort() );
		instanceRuntime.put( "lc", serviceInstance.getLifecycle() );
		if ( hostRuntime != null ) {
			instanceRuntime.put( "cpuCount", hostRuntime.get( "cpuCount" ).textValue() );
			instanceRuntime.put( "cpuLoad", hostRuntime.get( "cpuLoad" ).textValue() );
			try {
				double newKB = Math.round( Double.parseDouble( hostRuntime.get( "cpuLoad" )
					.textValue() ) * 10.0 ) / 10.0;
				instanceRuntime.put( "cpuLoad", Double.toString( newKB ) );
			} catch (Exception e) {
				logger.debug( "Failed parsing: {}", hostRuntime.get( "cpuLoad" ).textValue(),
					e );
			}
			instanceRuntime.put( "motd", hostRuntime.get( "motd" ).textValue() );
			instanceRuntime.put( "users", hostRuntime.get( "users" ).textValue() );
			instanceRuntime.put( "lastOp", hostRuntime.get( "lastOp" ).textValue() );

			if ( hostRuntime.has( "du" ) ) {
				instanceRuntime.put( "du", hostRuntime.get( "du" ).asInt() );
			}
		}

		return instanceRuntime;
	}

	@RequestMapping ( value = "/uploadArtifact" , produces = MediaType.TEXT_PLAIN_VALUE )
	public String uploadArtifact (
									@RequestParam ( value = "distFile" , required = false ) MultipartFile multiPartFile,
									@RequestParam ( value = CSAP.SERVICE_PORT_PARAM , required = false ) String serviceNamePort,
									@RequestParam ( value = CSAP.HOST_PARAM , required = true ) String[] hostNameArray,
									HttpServletRequest request )
			throws IOException {

		logger.debug( "{}: service: {}, multiPartFile: ", Arrays.toString( hostNameArray ), serviceNamePort,
			multiPartFile );

		if ( multiPartFile == null
				|| serviceNamePort == null
				|| (serviceNamePort.indexOf( "_" ) == -1) ) {

			return "\n== " + CSAP.CONFIG_PARSE_ERROR
					+ " Missing Param: multiPartFile" + multiPartFile + " serviceName"
					+ serviceNamePort;
		}

		ServiceInstance serviceInstance = csapApp.getServiceInstanceAnyPackage( serviceNamePort );

		StringBuilder results = new StringBuilder( "Received upload for jvm: " + serviceNamePort );

		if ( multiPartFile != null && multiPartFile.getSize() != 0 ) {

			File deployFolder = csapApp.getDeploymentStorageFolder();
			if ( !deployFolder.exists() ) {
				deployFolder.mkdirs();
			}

			File deployFile = new File( deployFolder.getCanonicalPath(), serviceInstance.getDeployFileName() );

			results.append( ", uploaded file name: " + multiPartFile.getOriginalFilename() );
			results.append( " Size: " + multiPartFile.getSize() );

			try {
				multiPartFile.transferTo( deployFile );
				results.append( "\n- File saved to: " + deployFile.getAbsolutePath() );
			} catch (Exception e) {
				results.append( "\n== " + CSAP.CONFIG_PARSE_ERROR + " Host "
						+ Application.getHOST_NAME() + ":" + e.getMessage() );
			}

			File versionFile = serviceOsManager.createVersionFile(
				csapApp.getDeployVersionFile( serviceInstance ),
				null,
				securityConfig.getUserIdFromContext(),
				"User Upload" );

			OutputFileMgr outputFm = new OutputFileMgr( csapApp.getProcessingDir(),
				"/" + serviceNamePort + DEPLOY_OP );

			List<String> hostsToCopyTo = new ArrayList<String>( Arrays.asList( hostNameArray ) );
			results.append( "\n" );
			results.append(
				serviceOsManager.syncToOtherHosts(
					securityConfig.getUserIdFromContext(),
					hostsToCopyTo,
					deployFile.getAbsolutePath(),
					Application.CSAP_PACKAGES_TOKEN,
					csapApp.getAgentRunUser(),
					securityConfig.getUserIdFromContext(),
					false, outputFm.getBufferedWriter() ) );

			results.append( "\n" );
			results.append(
				serviceOsManager.syncToOtherHosts(
					securityConfig.getUserIdFromContext(),
					hostsToCopyTo,
					versionFile.getAbsolutePath(),
					Application.CSAP_PACKAGES_TOKEN,
					csapApp.getAgentRunUser(),
					securityConfig.getUserIdFromContext(), false,
					outputFm.getBufferedWriter() ) );

			csapEventClient.generateEvent(
				serviceInstance.getServiceName(), securityConfig.getUserIdFromContext(),
				"Uploaded deployment", results.toString() );

			outputFm.opCompleted();

		} else {
			logger.error( "Empty File received" );

			if ( multiPartFile != null ) {
				results.append( "<br>ERROR: File  received was size 0" );
			}
		}

		if ( Application.isRunningOnDesktop() ) {
			logger.warn( "Sleeping on desktop to similate remote connection speed" );
			try {
				Thread.sleep( 10000 );
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		results.append( "Completed upload. Click Start to use the new file." );
		return results.toString();
	}

	@RequestMapping ( "/getProfilerLaunchFile" )
	public void getProfileLaunchFile (
										@RequestParam ( "jmxPorts" ) String jmxHostPortsSpaceDelimArray,
										@RequestParam ( value = "jvisualvm" , required = false ) String jvisualvm,
										@RequestParam ( value = CSAP.SERVICE_PORT_PARAM , required = false , defaultValue = "no" ) String serviceName_port,
										HttpServletRequest request, HttpServletResponse response ) {

		String[] jmxHostPortArray = jmxHostPortsSpaceDelimArray.trim().split( " " );

		logger.debug( "{} : jmxHostPortsSpaceDelimArray: {}, length: {}, jvisualvm: {}",
			serviceName_port, jmxHostPortsSpaceDelimArray, jmxHostPortArray.length, jvisualvm );

		// boolean isJmxUsingRmi = true;
		if ( !serviceName_port.equals( "no" ) ) {
			ServiceInstance serviceDefinition = csapApp
				.getServiceInstanceAnyPackage( serviceName_port );

			if ( serviceDefinition == null ) {
				// isJmxUsingRmi = serviceDefinition.isJmxRmi();
			} else {
				logger.warn( "Did not find a service instance for: " + serviceName_port );
			}
		}

		try {

			csapEventClient.generateEvent( Application.AGENT_NAME_PORT, securityConfig.getUserIdFromContext(),
				" Java Profiler Launch: " + Application.getHOST_NAME(), "no details" );

			StringBuilder batContents = new StringBuilder();
			batContents.append( "echo %JAVA_HOME% \r\n" );
			batContents
				.append( "echo Optional Download topthreads plugin from http://lsd.luminis.nl/top-threads-plugin-for-jconsole \r\n" );
			batContents.append( "echo Tomcat Firewalled ports connection \r\n" );
			if ( jvisualvm != null ) {
				batContents.append( "start jvisualvm --openjmx  " );
			} else {
				batContents.append( "start jmc --openjmx   " );
				// batContents.append("start jconsole -pluginpath
				// /java/topthreads.jar ");
			}
			for ( int i = 0; i < jmxHostPortArray.length; i++ ) {
				// from csap-eng01:8026 to
				// service:jmx:rmi://csap-eng01:8028/jndi/rmi://csap-eng01:8027/jmxrmi
				String jndiDest = jmxHostPortArray[i]
					.substring( 0, jmxHostPortArray[i].length() - 1 ) + "7";
				String firewallDest = jmxHostPortArray[i].substring( 0,
					jmxHostPortArray[i].length() - 1 ) + "8";

				// if ( isJmxUsingRmi ) {
				// batContents.append( "service:jmx:rmi://" + firewallDest );
				// batContents.append( "/jndi/rmi://" + jndiDest + "/jmxrmi " );
				// } else {
				batContents.append( jmxHostPortArray[i] );
				// }

				if ( jvisualvm != null ) {
					// jvisualvm only can launch with 1 url. put the remaining
					// on the comment
					batContents.append( "\r\n REM " );
				}
			}
			batContents.append( "\r\n echo Direct connect port connection\r\n" );
			batContents.append( "REM start jconsole -pluginpath /java/topthreads.jar " );

			for ( int i = 0; i < jmxHostPortArray.length; i++ ) {
				batContents.append( jmxHostPortArray[i] );
				batContents.append( " " );
			}

			// irdev1:8016" ;
			response.setContentType( "application/bat" );
			response.setContentLength( (int) batContents.length() );
			response.setHeader( "Content-Disposition", "attachment; filename=\"" + "profile_"
					+ System.currentTimeMillis() + ".bat" + "\"" );
			response.getWriter().println( batContents );
		} catch (Throwable e) {
			logger.error( "Failed to getperfdata", e );
		}
	}

	BasicThreadFactory resetTokensFactory = new BasicThreadFactory.Builder()
		.namingPattern( "CsapResetTokens-%d" )
		.daemon( true )
		.priority( Thread.NORM_PRIORITY )
		.build();
	ScheduledExecutorService resetTokensExecutor = Executors.newScheduledThreadPool( 1, resetTokensFactory );

	@RequestMapping ( value = "/resetJmxAuth" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode resetJmxAuth (
										@RequestParam ( value = CSAP.HOST_PARAM , required = false ) ArrayList<String> hosts,
										@RequestParam ( CSAP.SERVICE_PORT_PARAM ) String[] svcNameArray,
										HttpServletRequest request )
			throws IOException {

		logger.debug( "hostName : " + hosts + " svcName count: "
				+ svcNameArray.length );

		ObjectNode resultsJson = jacksonMapper.createObjectNode();

		csapEventClient.generateEvent( Application.AGENT_NAME_PORT, securityConfig.getUserIdFromContext(),
			"JMX Access", "no details" );

		StringBuilder userMessage = new StringBuilder( "JMX Auth Token Reset for userid: "
				+ securityConfig.getUserIdFromContext() + " services:" );
		if ( Application.isJvmInManagerMode() ) {

			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>();
			for ( String service : svcNameArray ) {
				urlVariables.add( CSAP.SERVICE_PORT_PARAM, service );
			}

			String url = CsapCoreService.SERVICE_URL + "/resetJmxAuth";

			if ( hosts != null ) {
				resultsJson = serviceOsManager.remoteAgentsUsingUserCredentials( hosts, url, urlVariables, request );
			} else {
				resultsJson.put( CSAP.CONFIG_PARSE_ERROR, " - Failed to find hostName: " + hosts );
			}
			return resultsJson;

		}
		for ( int i = 0; i < svcNameArray.length; i++ ) {

			String svcName = svcNameArray[i];

			ArrayList<String> params = new ArrayList<String>();
			params.add( "-jmxPassword" );
			params.add( securityConfig.getUserIdFromContext() );

			resultsJson.put( svcName,
				serviceOsManager.runScript( securityConfig.getUserIdFromContext(), JMX_AUTH_FILE,
					svcName, params, null, null ) );

			logger.debug( "Results: {}", resultsJson );

			userMessage.append( " " + svcName );
		}

		resultsJson.put( "results", userMessage.toString() );

		resetTokensExecutor.schedule( new ResetToken( svcNameArray ), 120, TimeUnit.SECONDS );

		return resultsJson;

	}

	/**
	 * Helper class to disable user access after a couple of minutes. This keep
	 * JMX access restricted, typically only in protected nets
	 *
	 * @author someDeveloper
	 *
	 */
	private class ResetToken implements Runnable {

		private String[] svcNameArray;

		public ResetToken( String[] svcNameArray ) {
			this.svcNameArray = svcNameArray;
		}

		public void run () {
			try {

				logger.debug( "Resetting tokens on services count: {}", svcNameArray.length );

				for ( int i = 0; i < svcNameArray.length; i++ ) {

					String svcName = svcNameArray[i];

					// check for host
					//
					// logger.debug("Resetting tokens on item: " +
					// svcNameArray[i]) ;
					String stagingPath = ".";
					if ( System.getenv( "STAGING" ) != null ) {
						stagingPath = System.getenv( "STAGING" );
					}
					File workingDir = new File( stagingPath );
					File scriptPath = new File( stagingPath + "/bin/" + JMX_AUTH_FILE );

					List<String> parmList = new ArrayList<String>();
					parmList.add( "bash" );
					// parmList.add("-c") ;
					parmList.add( scriptPath.getAbsolutePath() );

					parmList.add( "-jmxPassword" );
					parmList.add( "wipeit" );
					ServiceInstance instanceConfig = csapApp.getServiceInstanceCurrentHost( svcName );

					parmList.add( "-jmxAuth" );
					parmList.add( csapApp.getJmxAuth() );
					parmList.add( "-serviceName" );
					parmList.add( instanceConfig.getServiceName() );
					parmList.add( "-port" );
					parmList.add( instanceConfig.getPort() );

					String result = osCommandRunner.executeString( parmList, workingDir, null, null,
						null );

					// String result =
					// serviceApi.runScript(securityConfig.getUserIdFromContext(),JMX_AUTH_FILE,
					// svcName, params,
					// null, null);
					logger.debug( "Results: ", result );
					// logger.info(result);
				}
			} catch (Exception e) {
				logger.error( "Failed to reset tokens", e );
			}

		}
	}

	final public static String GENERATE_APACHE_MAPPINGS = "/genHttpdWorkers";

	@RequestMapping ( value = GENERATE_APACHE_MAPPINGS , produces = MediaType.TEXT_PLAIN_VALUE )
	public String genHttpdWorkers ( HttpServletRequest request, HttpServletResponse response ) {

		logger.debug( " Entering" );
		StringBuilder results = new StringBuilder();
		results.append( "\n\n ========== Restart httpd process to pick up update ========\n\n" );
		results.append( csapApp.buildHttpdConfiguration() );
		results.append( "\n\n ========== Restart httpd process to pick up update ========\n\n" );

		sendCsapEvent( "Httpd config updated", "" );
		return results.toString();
	}

	@RequestMapping ( value = "/getInstances" , produces = MediaType.TEXT_PLAIN_VALUE )
	public void getInstances (
								@RequestParam ( value = "simpleFormater" , required = false ) String format,
								HttpServletRequest request, HttpServletResponse response ) {

		logger.debug( " simpleFormater: {}", format );

		// updates only if necessary
		csapApp.updateCache( false );
		try {
			response.setContentType( "text/plain" );
			response.getWriter().print( "\n\n ========== showInstances.sh -list ========\n\n" );

			response.getWriter().print( csapApp.toString() );

			response.getWriter().print( "\n\n ========== ========\n\n" );

		} catch (Exception e) {
			logger.error( "Failed to rebuild", e );
		}
	}

	@RequestMapping ( value = "/undeploy" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode undeploy (
									@RequestParam ( value = CSAP.HOST_PARAM , required = false ) ArrayList<String> hosts,
									@RequestParam ( CSAP.SERVICE_PORT_PARAM ) String serviceName,
									@RequestParam ( "warSelect" ) String warSelect,
									HttpServletRequest request, HttpServletResponse response ) {

		logger.info( "User: " + securityConfig.getUserIdFromContext() + " hostName : "
				+ hosts + " Services: " + serviceName
				+ " warSelect: " + warSelect );

		ObjectNode resultsJson = jacksonMapper.createObjectNode();

		if ( serviceName.contains( CSAP.AGENT_CONTEXT ) ) {
			resultsJson.put( serviceName, "Agent does not support undeploys" );
			return resultsJson;
		}

		if ( Application.isJvmInManagerMode() ) {

			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>();
			urlVariables.add( CSAP.SERVICE_PORT_PARAM, serviceName );

			urlVariables.add( "warSelect", warSelect );

			String url = CsapCoreService.SERVICE_URL + "/undeploy";

			if ( hosts != null ) {
				resultsJson = serviceOsManager.remoteAgentsUsingUserCredentials( hosts, url, urlVariables, request );
			} else {
				resultsJson.put( CSAP.CONFIG_PARSE_ERROR, " - Failed to find hostName: " + hosts );
			}
			return resultsJson;

		}

		File targetFile = new File( csapApp.getProcessingDir(), serviceName
				+ "/webapps/" + warSelect );

		if ( targetFile.exists() ) {
			List<String> parmList = new ArrayList<String>();
			Collections
				.addAll( parmList, "bash", "-c", "rm -rf " + targetFile.getAbsolutePath() );

			File workingDir2 = new File( csapApp.getStagingFolder()
				.getAbsolutePath() );
			osCommandRunner.executeString( parmList, workingDir2,
				null, null, 60, 1, null );
			// response.getWriter().print(sourceControlManager.executeShell(parmList,
			// null));

			csapEventClient.generateEvent( Application.AGENT_NAME_PORT,
				securityConfig.getUserIdFromContext(),
				" Undeploying: " + targetFile.getAbsolutePath()
						+ Application.getHOST_NAME(),
				warSelect );
			resultsJson.put( serviceName, "removed: " + targetFile.getAbsolutePath() );
		} else {
			resultsJson.put( serviceName,
				"ERROR: Did not find deployment file: " + targetFile.getAbsolutePath() );
		}

		return resultsJson;

	}

	@RequestMapping ( value = "/updateMotd" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode updateMotd (	@RequestParam ( "motd" ) String motdMessage,
									@RequestParam ( value = CSAP.HOST_PARAM , required = false ) ArrayList<String> hosts,
									HttpServletRequest request ) {

		logger.debug( " motdMessage: {}", motdMessage );

		ObjectNode resultsJson = jacksonMapper.createObjectNode();

		if ( Application.isJvmInManagerMode() ) {

			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>();
			urlVariables.add( "motd", motdMessage );

			String url = CsapCoreService.SERVICE_URL + "/updateMotd";

			if ( hosts != null ) {
				resultsJson = serviceOsManager.remoteAgentsUsingUserCredentials( hosts, url, urlVariables, request );
			} else {
				resultsJson.put( CSAP.CONFIG_PARSE_ERROR, " - Failed to find hostName: " + hosts );
			}
			return resultsJson;

		}

		csapApp.setMotdMessage( securityConfig.getUserIdFromContext() + ": " + motdMessage );
		csapEventClient.generateEvent( Application.AGENT_NAME_PORT, securityConfig.getUserIdFromContext(),
			" updated motd on Host: " + Application.getHOST_NAME(), motdMessage );

		resultsJson.put( "results", " updated motd on Host: " + Application.getHOST_NAME() );

		return resultsJson;
	}

	private void sendCsapEvent ( String commandDesc, String details ) {

		csapEventClient.generateEvent( Application.AGENT_NAME_PORT, securityConfig.getUserIdFromContext(),
			commandDesc, details );
	}

}
