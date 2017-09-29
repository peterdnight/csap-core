/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.input.http.api;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.csap.CsapMonitor;
import org.csap.agent.CsapCoreService;
import org.csap.agent.CSAP;
import org.csap.agent.linux.OsCommandRunner;
import org.csap.agent.linux.OutputFileMgr;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.ActiveUsers;
import org.csap.agent.model.Application;
import org.csap.agent.model.ServiceAlertsEnum;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.services.OsManager;
import org.csap.agent.services.ServiceCommands;
import org.csap.agent.services.ServiceOsManager;
import org.csap.agent.stats.OsProcessCollector;
import org.csap.agent.stats.OsSharedResourcesCollector;
import org.csap.agent.stats.ServiceCollector;
import org.csap.docs.CsapDoc;
import org.csap.security.SpringAuthCachingFilter;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *
 * @author someDeveloper
 */
@RestController
@CsapMonitor ( prefix = "api.agent" )
@RequestMapping ( CsapCoreService.API_AGENT_URL )
@CsapDoc ( title = "/api/agent/*: apis for querying data collected by management agent." , type = CsapDoc.PUBLIC , notes = {
		"CSAP Performance APis provide access to the runtime data. This includes everything from the state "
				+ "of the host resources (disk/cpu/memory), java (heap, threads), and service custom metrics",
		"For  access to aggregated performance collections - refer to <a class='simple' href='class?clazz=org.csap.agent.input.http.api.Runtime_Application'>Application Apis</a> ",
		"<a class='pushButton' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/csapboot.png' />"
				+ "<img class='csapDocImage' src='CSAP_BASE/images/event.png' />"
				+ "<img class='csapDocImage' src='CSAP_BASE/images/editOverview.png' />",
		"Note: unless otherwise stated - these apis can only be executed on CSAP Admin Service instances. Typically: https://yourApp/admin" } )
public class AgentApi {

	final Logger logger = LoggerFactory.getLogger( this.getClass() );

	ObjectMapper jacksonMapper = new ObjectMapper();

	// Spring Injected
	@Inject
	private Application csapApp;
	private ObjectNode managerError;

	{
		managerError = jacksonMapper.createObjectNode();
		managerError.put( "error", "Only permitted on admin nodes" );

	}

	@CsapDoc ( notes = {
			"Health Check for eman. Alert Level can be used to customize thresholds.",
			CsapDoc.INDENT + "A configured limit of 100, with alertLevel=1.5 will become 150"
	} , produces = "plain/txt" )
	@RequestMapping ( "/emanStatus" )
	public String emanStatus (
								@RequestParam ( value = "alertLevel" , required = false , defaultValue = "1.0" ) double alertLevelForFiltering ) {

		logger.debug( "AlertLevel : {}", alertLevelForFiltering );
		String result = "Failure";
		try {
			// check vm connections
			ObjectNode errorNode = csapApp.buildErrorsForAdminOrAgent( alertLevelForFiltering );

			if ( errorNode.size() == 0 ) {
				result = "Success";
			} else {
				ObjectWriter writer = jacksonMapper.writerWithDefaultPrettyPrinter();
				result = "Failure\n" + writer.writeValueAsString( errorNode );
			}

		} catch (Exception e) {
			logger.error( "Failed: ", e );
		}

		return result;
	}

	@CsapDoc ( notes = "Health of host" )
	@RequestMapping ( "/health" )
	public ObjectNode health ()
			throws Exception {

		ObjectNode healthJson = jacksonMapper.createObjectNode();

		ObjectNode errorNode = csapApp.buildErrorsForAdminOrAgent( ServiceAlertsEnum.ALERT_LEVEL );

		if ( errorNode.size() == 0 ) {
			healthJson.put( "Healthy", true );
		} else {

			healthJson.put( "Healthy", false );
			healthJson.set( "errors", errorNode );
		}

		return healthJson;
	}

	public final static String RUNTIME_URL = "/runtime";

	@CsapDoc ( notes = {
			"Runtime status of host, including cpu, disk, services, etc"
	} , linkTests = "default" )
	@GetMapping ( RUNTIME_URL )
	public JsonNode runtime ()
			throws JsonGenerationException, JsonMappingException, IOException {

		return osManager.getHostRuntime();
	}

	@CsapDoc ( notes = {
			"Get the number of jobs (start, stop, deploy) queued for execution."
	} , linkTests = {
			"Show Jobs"
	} )
	@RequestMapping ( "/service/jobs" )
	public ObjectNode serviceJobStatus ()
			throws Exception {

		return serviceManager.getJobStatus();
	}

	public final static String USERS_URL = "/users/active";

	@CsapDoc ( notes = {
			"Uses active in past 60 minutes"
	} , linkTests = "default" )
	@GetMapping ( USERS_URL )
	public ArrayNode usersActive ()
			throws JsonGenerationException, JsonMappingException, IOException {

		return activeUsers.getActive();
	}

	@Inject
	ActiveUsers activeUsers;

	@CsapDoc ( notes = { "Summary status of host" } , linkTests = "default" )
	@RequestMapping ( { "/status" } )
	public ObjectNode status ()
			throws JsonGenerationException, JsonMappingException,
			IOException {

		ObjectNode healthJson = jacksonMapper.createObjectNode();

		if ( Application.isJvmInManagerMode() ) {
			healthJson.put( "error", "vmHealth is only enabled on CsAgent urls." );
		} else {

			healthJson = csapApp.statusForAdminOrAgent( ServiceAlertsEnum.ALERT_LEVEL );
		}

		return healthJson;
	}

	@CsapDoc ( notes = { "Host disk usage", "* Agent service only" } )
	@GetMapping ( "/diskUsage" )
	public ObjectNode diskUsage ()
			throws JsonGenerationException, JsonMappingException,
			IOException {

		ObjectNode diskInfo = jacksonMapper.createObjectNode();

		if ( Application.isJvmInManagerMode() ) {
			diskInfo.put( "error",
				"Disk Usage is only enabled on CsAgent urls. Use /admin/api/hosts, then /CsAgent/api/diskUsage on host.  CSAP Command Runner UI can be used to run on all VMS at same time." );
		} else {
			diskInfo.set( Application.getHOST_NAME(), osManager.getCachedFileSystemInfo() );
		}

		return diskInfo;
	}

	@Inject
	private OsManager osManager;

	@CsapDoc ( notes = {
			"Gets Host summary information: uptime, os version, df. Optional support for JSONP"
					+ " is provided if callback parameter is included",
			"* Agent service only"
	} , linkTests = {
			"json", "jsonp"
	} , linkGetParams = {
			"a=b",
			"callback=myFunctionCall"
	} , produces = {
			MediaType.APPLICATION_JSON_VALUE, "application/javascript"
	} )
	@GetMapping ( {
			"/hostSummary",
			"/vmSummary"
	} )
	public JsonNode hostSummary (
									@RequestParam ( value = "callback" , required = false , defaultValue = "false" ) String callback )
			throws Exception {

		return osManager.getHostSummary();

	}

	@CsapDoc ( notes = {
			"OS data collect by agent that is shared across all processes",
			"This includes host cpu (mpstat), open files(/proc/sys, lsof), sockets and many others",
			"param collectionInterval:  specifying '0' (or non existing) will return data for the shortest interval",
			"param numberOfDataPoints:  the amount of collected data points to return"
	} , linkTests = {
			"Host data"
	} , linkGetParams = {
			"collectionInterval=30,numberOfDataPoints=1"
	} )
	@GetMapping ( {
			"/collection/osShared/{collectionInterval}/{numberOfDataPoints}"
	} )
	public JsonNode collectionOsShared (
											@PathVariable ( "collectionInterval" ) String collectionInterval,
											@PathVariable ( "numberOfDataPoints" ) int numberOfDataPoints )
			throws Exception {

		return getPerformanceData( "resource", collectionInterval, numberOfDataPoints );

	}

	@CsapDoc ( notes = {
			"Default os data collected for service by agent.",
			"param serviceName:  specifying 'all' will return data for all services.",
			"param collectionInterval:  specifying '0' (or non existing) will return data for the shortest interval",
			"param numberOfDataPoints:  the amount of collected data points to return",
			"This includes top cpu, thread counts, disk usage, etc.",
			"Note: "
	} , linkTests = {
			"CsAgent OS Data",
			"CsAgent_8011 OS Data",
			"All Services OS Data"
	} , linkGetParams = {
			"serviceName=CsAgent,collectionInterval=30,numberOfDataPoints=1",
			"serviceName=CsAgent_8011,collectionInterval=30,numberOfDataPoints=1",
			"serviceName=all,collectionInterval=30,numberOfDataPoints=1"
	} )
	@GetMapping ( {
			"/collection/os/{serviceName}/{collectionInterval}/{numberOfDataPoints}",
			"/collection/os/{serviceName}/{collectionInterval}/{numberOfDataPoints}"
	} )
	public JsonNode collectionOsProcess (
											@PathVariable ( "serviceName" ) String serviceName,
											@PathVariable ( "collectionInterval" ) String collectionInterval,
											@PathVariable ( "numberOfDataPoints" ) int numberOfDataPoints )
			throws Exception {

		return getPerformanceData( "service", collectionInterval, numberOfDataPoints, serviceName );

	}

	@CsapDoc ( notes = {
			"Application data collected for service instance by agent.",
			"param serviceName:  specifying 'all' will return data for all services. port is required.",
			"param collectionInterval:  specifying '0' (or non existing) will return data for the shortest interval",
			"param numberOfDataPoints:  the amount of collected data points to return"
	} , linkTests = {
			"CsAgent Custom Data"
	} , linkGetParams = {
			"serviceName=CsAgent_8011,collectionInterval=30,numberOfDataPoints=1",
	} )
	@GetMapping ( {
			"/collection/application/{serviceName}/{collectionInterval}/{numberOfDataPoints}"
	} )
	public JsonNode collectionApplication (
											@PathVariable ( "serviceName" ) String serviceNamePort,
											@PathVariable ( "collectionInterval" ) String collectionInterval,
											@PathVariable ( "numberOfDataPoints" ) int numberOfDataPoints ) {

		// return appData( serviceName, "app", interval, number );
		return getPerformanceData( "app", collectionInterval, numberOfDataPoints, serviceNamePort );

	}

	@CsapDoc ( notes = {
			"Default java data collected for service by agent. Specifying all will return data for all services.",
			"This includes heap, gc, threads, etc. If tomcat based, http sessions, requests, etc. are also included",
			"param serviceName:  specifying 'all' will return data for all services. port is required.",
			"param collectionInterval:  specifying '0' (or non existing) will return data for the shortest interval",
			"param numberOfDataPoints:  the amount of collected data points to return"
	} , linkTests = {
			"CsAgent Java Data",
			"All Java Data"
	} , linkGetParams = {
			"serviceName=CsAgent_8011,collectionInterval=30,numberOfDataPoints=1",
			"serviceName=all,collectionInterval=30,numberOfDataPoints=1",
	} )
	@GetMapping ( {
			"/collection/java/{serviceName}/{collectionInterval}/{numberOfDataPoints}",
			"/collection/java/{serviceName}/{collectionInterval}/{numberOfDataPoints}"
	} )
	public JsonNode collectionJava (
										@PathVariable ( "serviceName" ) String serviceNamePort,
										@PathVariable ( "collectionInterval" ) String collectionInterval,
										@PathVariable ( "numberOfDataPoints" ) int numberOfDataPoints ) {

		return getPerformanceData( "jmx", collectionInterval, numberOfDataPoints, serviceNamePort );

	}

	public JsonNode getPerformanceData ( String type, String interval, int number, String... services ) {
		ObjectNode metricsJson = jacksonMapper.createObjectNode();

		if ( Application.isJvmInManagerMode() ) {
			metricsJson.put( "error",
				"Metrics api is only available from Node Agent instances. Vm Name: CsAgent" );
		} else {
			switch (type) {
			case "resource":

				OsSharedResourcesCollector vmStatsRunnable = csapApp.getVmSharedCollector( Integer
					.parseInt( interval ) );
				metricsJson = vmStatsRunnable.getCSVdata( false, null, number, 0 );
				break;

			case "service":
				OsProcessCollector svcStats = csapApp.getVmProcessCollector( Integer
					.parseInt( interval ) );
				// metricsJson = svcStats.getAllCSVdata( number, 0 );
				metricsJson = svcStats.getCollection( number, 0, services );
				break;

			case "jmx":
				ServiceCollector serviceCollector = csapApp.getServiceCollector( Integer.parseInt( interval ) );
				metricsJson = serviceCollector.getJavaCollection( number, 0, services );
				break;

			case "app":

				// not working?
				ServiceCollector appCollector = csapApp.getServiceCollector( Integer
					.parseInt( interval ) );
				// metricsJson = appCollector.getAllCSVdata( number, 0 );
				metricsJson = appCollector.getApplicationCollection( number, 0, services );
				break;

			default:
				metricsJson.put( "error", "Unknown metric selection: " + type );
				logger.error( "Unknown metric selection: " + type );
				break;
			}
		}

		return metricsJson;
	}

	public static final String RUN_SCRIPT_URL = "/script";

	@CsapDoc ( notes = { RUN_SCRIPT_URL + ": api for running script",
			"scriptName:  name of script to be run.",
			"scriptUserid: OS user used for script execution",
			"timeoutSeconds: amount of time before script will be aborted/killed",
			"scriptContents: actual script to be run"
	} , linkTests = {
			"Run shell script"
	} , linkPaths = {
			RUN_SCRIPT_URL
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "scriptUserid=ssadmin,timeoutSeconds=30,scriptName=MyTest.sh,scriptContents=#!/bin/bash\nls -l" } )
	@PostMapping ( RUN_SCRIPT_URL )
	public ObjectNode scriptRun (
									@RequestParam String scriptName,
									@RequestParam int timeoutSeconds,
									@RequestParam String scriptUserid,
									@RequestParam String scriptContents,
									@RequestParam ( SpringAuthCachingFilter.USERID ) String apiUserid,
									@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String apiPass,
									HttpServletRequest request )
			throws JsonGenerationException, JsonMappingException, IOException {

		logger.info( "apiUserid: {}, scriptName: {},  scriptUserid: {} ",
			apiUserid, scriptName, scriptUserid );

		String[] hosts = { Application.getHOST_NAME() };
		String fullName = scriptName + LocalDateTime.now().format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm" ) );
		OutputFileMgr outputFm = new OutputFileMgr( csapApp.getScriptDir(), fullName );

		ObjectNode apiResponse = jacksonMapper.createObjectNode();
		apiResponse.put( "scriptOutput", "$STAGING/scripts/xfer_" + scriptName + ".log" );

		ObjectNode runResponse = osManager.executeShellScriptClustered(
			apiUserid,
			timeoutSeconds, scriptContents, scriptUserid,
			hosts,
			scriptName, outputFm );
		outputFm.opCompleted();

		apiResponse.set( "result", runResponse );

		ObjectNode securityResponse = (ObjectNode) request
			.getAttribute( SpringAuthCachingFilter.SEC_RESPONSE_ATTRIBUTE );
		apiResponse.set( "security", securityResponse );

		return apiResponse;

	}

	@Inject
	ServiceCommands serviceCommands;

	public static final String USERID_PASS_PARAMS = "userid=someDeveloper,pass=CHANGEME,";

	public static final String KILL_SERVICES_URL = "/service/kill";

	@CsapDoc ( notes = { KILL_SERVICES_URL + ": api for stoping specified service",
			"param services:  1 or more service port is required.",
			"Parameter: clean - optional - omit or leave blank to not delete files"
	} , linkTests = {
			"ServletSample"
	} , linkPaths = {
			KILL_SERVICES_URL
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "clean=clean,keepLogs=keepLogs,services=ServletSample_8041,auditUserid=blank" } )
	@PostMapping ( KILL_SERVICES_URL )
	public ObjectNode serviceKill (
									@RequestParam ArrayList<String> services,
									@RequestParam ( required = false ) String clean,
									@RequestParam ( required = false ) String keepLogs,
									@RequestParam ( SpringAuthCachingFilter.USERID ) String apiUserid,
									@RequestParam ( defaultValue = "" ) String auditUserid,
									HttpServletRequest request )
			throws JsonGenerationException, JsonMappingException, IOException {

		logger.info( "auditUserid: {},  apiUserid: {}, services: {} clean: {}, keepLogs: {} ",
			auditUserid, apiUserid, services, clean, keepLogs );

		ObjectNode apiResponse = serviceCommands.killRequest( apiUserid, services, clean, keepLogs, auditUserid );

		ObjectNode securityResponse = (ObjectNode) request
			.getAttribute( SpringAuthCachingFilter.SEC_RESPONSE_ATTRIBUTE );
		apiResponse.set( "security", securityResponse );

		return apiResponse;

	}

	public static final String START_SERVICES_URL = "/service/start";

	@CsapDoc ( notes = { START_SERVICES_URL + ": api for starting specified service",
			"parameter:  services:  1 or more service port is required.",
			"optional: deployId -  start will only be issued IF deployment id specified is successful. If not specified start will be issued.",
			"optional: clean  - omit or leave blank to not delete files"
	} , linkTests = {
			"ServletSample"
	} , linkPaths = {
			START_SERVICES_URL
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "commandArguments=blank,runtime=blank,services=ServletSample_8041,auditUserid=blank,startClean=blank,startNoDeploy=blank,hotDeploy=blank,deployId=blank" } )
	@PostMapping ( START_SERVICES_URL )
	public ObjectNode serviceStart (
										@RequestParam ArrayList<String> services,

										@RequestParam ( required = false ) String commandArguments,
										@RequestParam ( required = false ) String runtime,
										@RequestParam ( required = false ) String hotDeploy,
										@RequestParam ( required = false ) String startClean,
										@RequestParam ( required = false ) String noDeploy,
										String deployId,

										@RequestParam ( SpringAuthCachingFilter.USERID ) String apiUserid,
										@RequestParam ( defaultValue = "" ) String auditUserid,
										HttpServletRequest request )
			throws JsonGenerationException, JsonMappingException, IOException {

		logger.info(
			"auditUserid: {},  apiUserid:{} , services: {} javaOpts: {}, runtime: {}, hotDeploy: {}, startClean: {}, startNoDeploy: {} ",
			auditUserid, apiUserid, services, commandArguments, runtime, hotDeploy, startClean, noDeploy );

		return serviceCommands.startRequest( apiUserid, services, commandArguments, runtime, hotDeploy, startClean, noDeploy,
			auditUserid, deployId );

	}

	public static final String DEPLOY_SERVICES_URL = "/service/deploy";

	@CsapDoc ( notes = { DEPLOY_SERVICES_URL + ": api for deploying specified services",
			"param: services - 1 or more service port is required.",
			"optional: mavenDeployArtifact:  the artifact to deploy from maven - if ommited then source build is done."
					+ " specify 'default' to use, the version in definition file",
			"optional: targetScpHosts - after deploying artifact, it will be copied to specified hosts",
			"optional: commandArguments - overide the definition parameters for heap size, etc.",
			"optional: deployId - unique ID to identify results of deploy."
	} , linkTests = {
			"ServletSample"
	} , linkPaths = {
			DEPLOY_SERVICES_URL
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "commandArguments=blank,runtime=blank,services=ServletSample_8041,auditUserid=blank,startClean=blank,startNoDeploy=blank,hotDeploy=blank,"
					+ "scmUserid=someDeveloper,scmBranch=trunk,scmPass=CHANGEME,"
					+ "mavenDeployArtifact=default,deployId=blank" } )
	@PostMapping ( DEPLOY_SERVICES_URL )
	public ObjectNode serviceDeploy (
										@RequestParam ArrayList<String> services,

										// required parameters
										@RequestParam ( defaultValue = "dummy" ) String scmUserid,
										@RequestParam ( defaultValue = "dummy" ) String scmPass,
										@RequestParam ( defaultValue = "dummy" ) String scmBranch,

										String primaryHost,
										String deployId,
										String commandArguments,
										String runtime,
										String targetScpHosts,
										String hotDeploy,
										String mavenDeployArtifact, // if null -
																	// then
																	// source
																	// build
										String scmCommand,
										@RequestParam ( required = false , defaultValue = "true" ) boolean doEncrypt,

										@RequestParam ( SpringAuthCachingFilter.USERID ) String apiUserid,
										@RequestParam ( defaultValue = "" ) String auditUserid,
										HttpServletRequest request )
			throws JsonGenerationException, JsonMappingException, IOException {

		logger.info(
			"auditUserid: {},  apiUserid:{} , primaryHost:{} , services: {} javaOpts: {}, runtime: {}, hotDeploy: {},"
					+ " scmUserid: {}, scmBranch: {}, mavenDeployArtifact: {}, deployId: {} "
					+ "\n targetScpHosts: {} ",
			auditUserid, apiUserid, primaryHost, services, commandArguments, runtime, hotDeploy,
			scmUserid, scmBranch, mavenDeployArtifact, deployId,
			targetScpHosts );
		String sourcePassword = encryptor.encrypt( scmPass ); // immediately
		// encrypt
		// pass

		if ( !doEncrypt ||
				(csapApp.isRunningOnDesktop() && Application.isJvmInManagerMode()) ) {
			sourcePassword = scmPass;
		}

		return serviceCommands.deployRequest(
			apiUserid, primaryHost, deployId,
			services, scmUserid, sourcePassword, scmBranch,
			commandArguments, runtime, hotDeploy,
			mavenDeployArtifact, scmCommand, targetScpHosts, auditUserid );

	}

	@Inject
	private StandardPBEStringEncryptor encryptor;

	// int maxAttempts = 6;
	// while (serviceManager.getStartList().contains( taskId ) && maxAttempts--
	// > 0) {
	// try {
	// Thread.sleep( 5000 );
	// } catch ( InterruptedException e ) {
	// logger.error( "Failed polling results" );
	// }
	// }

	// if ( maxAttempts == 0 ) {
	// resultJson
	// .put( "results",
	// "Request Queued: "
	// + serviceManager.getStartList()
	// + " Verify results in CS-AP start logs, and application logs." );
	// } else {
	// resultJson
	// .put( "results",
	// "Request completed. Verify results in CS-AP start logs, and application
	// logs." );
	//
	// }

	/**
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * LOGS
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 */

	private ObjectNode getAllServices () {
		ObjectNode response = jacksonMapper.createObjectNode();

		response.put( "info", "add service name to url" );
		response.set( "availableServices", jacksonMapper.convertValue(
			csapApp.getAllPackages().getServiceNamesInLifecycle(),
			ArrayNode.class ) );
		return response;
	}

	@CsapDoc ( notes = {
			"List of log files",
			"Note: agent api only."
	} , linkTests = {
			"CsAgent Listing"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "serviceName_port=CsAgent_8011" } )
	@PostMapping ( "/service/log/list" )
	public JsonNode logsForService (
										@RequestParam ( "serviceName_port" ) String serviceName_port,
										@RequestParam ( SpringAuthCachingFilter.USERID ) String userid,
										@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass ) {

		if ( serviceName_port.equals( "{serviceName_port}" ) ) {
			return getAllServices();
		}

		logger.info( "{} listing files on: ", userid, serviceName_port );

		ArrayList<String> names = new ArrayList<String>();

		File working = csapApp.getLogDir( serviceName_port );

		if ( working != null && working.exists() ) {
			File[] logsFiles = working.listFiles();

			for ( File logItem : logsFiles ) {

				if ( logItem.isDirectory() ) {
					File[] subFiles = logItem.listFiles();
					for ( File subFile : subFiles ) {
						if ( subFile.isFile() ) {
							// Hook since tomcat chokes on urlencoded /
							String path = logItem.getName() + "_slash_" + subFile.getName();
							names.add( path );
						}
					}
				} else {
					names.add( logItem.getName() );
				}
			}
		}
		return jacksonMapper.convertValue( names, ArrayNode.class );
	}

	@CsapDoc ( notes = {
			"Download service log file.",
			"Note: agent api only."
	} , linkTests = {
			"CsAgent Warnings"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "serviceName_port=CsAgent_8011,fileName=warnings.log"
	} )
	@RequestMapping ( value = "/service/log/download" , produces = MediaType.APPLICATION_OCTET_STREAM_VALUE )
	public FileSystemResource downloadLogFile (
												@RequestParam ( "serviceName_port" ) String serviceName_port,
												@RequestParam ( "fileName" ) String fileName,
												@RequestParam ( SpringAuthCachingFilter.USERID ) String userid,
												@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass,
												HttpServletResponse response ) {

		logger.info( "{} Downloading {} file: {}", userid, serviceName_port, fileName );

		// Hook since tomcat chokes on urlencoded /
		File logFileRequested = new File( csapApp.getLogDir( serviceName_port ) + "/"
				+ fileName.replaceAll( "_slash_", "/" ) );

		if ( !logFileRequested.exists() ) {
			throw new RuntimeException( "File not found" + logFileRequested.getAbsolutePath() );
		}
		// HttpServletResponse response
		// String mt = new
		// MimetypesFileTypeMap().getContentType(logFileRequested);

		// logger.info("File type: {}" , mt) ;
		response.setHeader( "Content-Disposition", "attachment;filename=" + fileName );
		FileSystemResource theFile = new FileSystemResource( logFileRequested );

		return theFile;
	}

	@CsapDoc ( notes = {
			"Log File output filtered by specified filter, processed using grep command",
			"Note: agent api only"
	} , linkTests = {
			"CsAgent_8011"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "serviceName_port=CsAgent_8011,fileName=warnings.log,filter=Error"
	} )
	@RequestMapping ( value = "/service/log/filter" , produces = MediaType.TEXT_PLAIN_VALUE ) // Support
	public String serviceLogsFiltered (
										@RequestParam ( "serviceName_port" ) String serviceName_port,
										@RequestParam ( "fileName" ) String fileName,
										@RequestParam ( value = "filter" ) String filter,
										@RequestParam ( SpringAuthCachingFilter.USERID ) String userid,
										@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass )
			throws IOException {

		logger.info( "{} Filtering {} file: {} with: {}", userid, serviceName_port, fileName, filter );

		StringBuilder results = new StringBuilder( "Filter: " + filter + "\n\n" );
		// Hook since tomcat chokes on urlencoded /
		File logFileRequested = new File( csapApp.getLogDir( serviceName_port ) + "/"
				+ fileName.replaceAll( "_slash_", "/" ) );

		if ( !logFileRequested.exists() ) {
			throw new RuntimeException( "File not found" + logFileRequested.getAbsolutePath() );
		}

		List<String> parmList = Arrays.asList( "bash", "-c",
			"grep -i '" + filter + "' " + logFileRequested.getAbsolutePath() );
		// osCommandRunner.executeString(parmList);

		OsCommandRunner osCommandRunner = new OsCommandRunner( 10, 3, "Api" );

		results.append( osCommandRunner
			.executeString( parmList, csapApp.getStagingFolder(),
				null, null, 20, 2, null ) );

		logger.debug( "Result: {}", results );

		return results.toString();
	}

	@CsapDoc ( notes = {
			"/service/file/download - download files from the service folder in $PROCESSING",
			"Note: agent api only."
	} , linkTests = {
			"CsAgent start file"
	} , linkPaths = {
			"/service/file/download"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "serviceName_port=CsAgent_8011,fileName=CsAgent_8011_start.log"
	} )
	@RequestMapping ( value = "/service/file/download" , produces = MediaType.APPLICATION_OCTET_STREAM_VALUE )
	public FileSystemResource downloadServiceFile (
													@RequestParam ( "serviceName_port" ) String serviceName_port,
													@RequestParam ( "fileName" ) String fileName,
													@RequestParam ( SpringAuthCachingFilter.USERID ) String userid,
													@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass,
													HttpServletResponse response ) {

		logger.info( "{} Downloading {} file: {}", userid, serviceName_port, fileName );

		// Hook since tomcat chokes on urlencoded /
		File serviceFile = new File( csapApp.getWorkingDirectory( serviceName_port ) + "/"
				+ fileName.replaceAll( "_slash_", "/" ) );

		if ( !serviceFile.exists() ) {
			throw new RuntimeException( "File not found: " + serviceFile.getAbsolutePath() );
		}

		response.setHeader( "Content-Disposition", "attachment;filename=" + fileName );
		FileSystemResource theFile = new FileSystemResource( serviceFile );

		return theFile;
	}

	@Inject
	OsManager osManger;

	public static final String PROGRESS_PREFIX = "xfer_";
	public static final String PLATFORM_UPDATE = "/platformUpdate";

	@CsapDoc ( notes = { PLATFORM_UPDATE + ": upload a .tgz to the file system, with support for extraction, and optional running scripts",
			"extractDir: location to place uploaded archive, ",
			"distFile: multi-part attachment containing a *.tgz archive file (tar gzipped) ",
			"Optional deleteExisting: defaults false. If true - existing file will be removed", } , linkTests = {
					"platform update"
			} , linkPostParams = {
					USERID_PASS_PARAMS
							+ "extractDir=$STAGING/temp,deleteExisting=false,timeoutSeconds=120"
							+ "chownUserid=ssadmin,"
			} , fileParams = {
					"distFile"
			} )

	@RequestMapping ( PLATFORM_UPDATE )
	public ObjectNode platformUpdate (
										@RequestParam ( SpringAuthCachingFilter.USERID ) String userid,
										@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass,

										@RequestParam ( value = "extractDir" , required = true ) String extractToken,
										@RequestParam ( value = "chownUserid" , required = false ) String chownUserid,
										@RequestParam ( value = "timeoutSeconds" , required = false , defaultValue = "120" ) int timeoutSeconds,
										@RequestParam ( value = "deleteExisting" , required = false ) String deleteExisting,
										@RequestParam ( value = "auditUser" , required = false ) String auditUser,
										@RequestParam ( value = "distFile" , required = true ) MultipartFile multiPartFile,
										HttpServletRequest request ) {

		if ( auditUser == null ) {
			auditUser = userid;
		}

		String extractDir = extractToken;
		if ( extractDir.startsWith( Application.FileToken.STAGING.value ) ) {
			// STAGING_TOKEN is used as csap install folder might be different;
			// full paths are not passed when syncing elements between hosts.
			extractDir = extractDir.replaceAll( Application.FileToken.STAGING.value, csapApp.stagingFolderAsString() );
		}

		String desc = multiPartFile.toString();
		if ( multiPartFile != null ) {
			desc = multiPartFile.getOriginalFilename() + " size: " + multiPartFile.getSize();
		}

		logger.info(
			"File System being updated using: {}\t extractToken: {}\t extractDir: {}\n\t chownUserid: {} \t timeoutSeconds: {}\t deleteExisting: {}",
			desc, extractToken, extractDir, chownUserid, timeoutSeconds, deleteExisting );

		csapEventClient.generateEvent(
			CsapEventClient.CSAP_OS_CATEGORY + PLATFORM_UPDATE, auditUser,
			"File System Update", "source: " + desc
					+ "\n extractDir: " + extractDir 
					+ "\n deleteExisting: " + deleteExisting
					+ "\n timeoutSeconds: " + timeoutSeconds 
					+ "\n chownUserid: " + chownUserid );

		ObjectNode jsonObjectResponse = jacksonMapper.createObjectNode();
		jsonObjectResponse.put( "host", Application.getHOST_NAME() );

		ArrayNode coreArray = jsonObjectResponse.putArray( "coreResults" );

		// MUST be the first line otherwise remote parsing will fail in
		// Transfermanager
		StringBuilder plainTextResponse = new StringBuilder( CSAP.AGENT_CONTEXT + "@"
				+ Application.getHOST_NAME() + ":" );

		String servletRemoteHost = request.getRemoteHost();

		String coreResults = "Error";

		try {
			OutputFileMgr outputFm = new OutputFileMgr( csapApp.getScriptDir(), userid + "_platformUpdate" );
			coreResults = osManger.updatePlatformCore( multiPartFile, extractDir, false,
				servletRemoteHost,
				chownUserid,
				auditUser,
				deleteExisting,
				outputFm );
			outputFm.close();
		} catch (IOException e1) {
			logger.error( "Failed updating Platform core", e1 );
			coreArray.add( "**" + CSAP.CONFIG_PARSE_ERROR + " Host "
					+ Application.getHOST_NAME()
					+ " failed updating core: "
					+ e1.getMessage() );

			return jsonObjectResponse;
		}

		coreArray.add( coreResults );

		if ( extractDir.equals( csapApp.getDefinitionFolder()
			.getAbsolutePath() ) ) {
			plainTextResponse.append(
				" Extract to cluster definition detected,  triggering a reload " );
			coreArray.add( "Triggering Reload" );
			logger.info( " Extract to cluster definition detected,  triggering a reload " );
			csapApp.updateCache( true );

		} else if ( extractDir.startsWith( csapApp.getScriptDir().getAbsolutePath() ) ) {

			// Special hook for execution of scripts
			logger.info( "Triggering script execution" );
			String scriptName = multiPartFile.getOriginalFilename();
			if ( scriptName.endsWith( ".zip" ) || scriptName.endsWith( ".tgz" ) ) {
				scriptName = scriptName.substring( 0, scriptName.length() - 4 );
			}

			String scriptResults = "";
			if ( scriptName.endsWith( OsCommandRunner.CANCEL_SUFFIX ) ) {
				scriptResults = "Job Cancel Received: " + scriptName
						+ ". \n ==== Output will be displayed after all VMs have cancelled the command.";
				logger.warn( scriptResults );
			} else {
				// script output is queried from UI
				try {
					OutputFileMgr scriptOutput = new OutputFileMgr( csapApp.getScriptDir(), PROGRESS_PREFIX + scriptName );
					scriptResults = OsCommandRunner.runCancellable(
						timeoutSeconds, chownUserid,
						new File( extractDir, scriptName ),
						scriptOutput );
					scriptOutput.close();
				} catch (IOException e) {
					logger.error( "Failed script run {}", CSAP.getCsapFilteredStackTrace( e ) );
				}

				int maxReturned = 1024 * 500; // max 500k
				if ( scriptResults.length() > maxReturned ) {
					String header = CSAP.CONFIG_PARSE_WARN
							+ " Output was truncated, use click Show Complete Output button above";

					scriptResults = header + scriptResults.substring( scriptResults.length() - maxReturned );
				}
			}

			ArrayNode res = jsonObjectResponse.putArray( "scriptResults" );
			res.add( scriptResults );

		}

		return jsonObjectResponse;

	}

	@Inject
	ServiceOsManager serviceManager;

	@Inject
	CsapEventClient csapEventClient;

	@CsapDoc ( notes = { "/upload/file: upload a file to the file system",
			"Note: agent api only, password should always be encypted using CSAP encoder UI.",
			"Optional Param: otherHosts - defaults to none. If specified file will be synched to other hosts (along with version if specified)",
			"Optional Param: uploadLocation - defaults to $STAGING/temp. $DEPLOY_FOLDER, $PROCESSING and $STAGING variables supported",
			"Optional Param: overwrite - defaults false. If true - existing file will be overwritten", } , linkTests = {
					"Sample Upload"
			} , linkPostParams = {
					USERID_PASS_PARAMS
							+ "uploadLocation=$STAGING/temp,overWrite=false,"
							+ "otherHosts=blank,"
			} , fileParams = {
					"uploadFile"
			} )

	@PostMapping ( value = "/upload/file" )
	public ObjectNode uploadFileToFileSystem (
												@RequestParam ( "uploadFile" ) MultipartFile multiPartFile,
												@RequestParam ( value = "uploadLocation" , required = false , defaultValue = "$DEPLOY_FOLDER" ) String uploadLocation,
												@RequestParam ( value = "overWrite" , required = false , defaultValue = "false" ) boolean overWrite,
												@RequestParam ( value = "otherHosts" , required = false ) String[] otherHosts,
												@RequestParam ( SpringAuthCachingFilter.USERID ) String userid,
												@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass,
												HttpServletRequest request )
			throws IOException {

		ObjectNode resultJson = (ObjectNode) request
			.getAttribute( SpringAuthCachingFilter.SEC_RESPONSE_ATTRIBUTE );

		resultJson.put( "uploadFile", multiPartFile.getOriginalFilename() );
		resultJson.put( "uploadFileSize", multiPartFile.getSize() );
		resultJson.put( "uploadLocation", uploadLocation );
		resultJson.put( "overWrite", overWrite );

		uploadLocation = replaceVariables( uploadLocation );
		String artifactName = multiPartFile.getOriginalFilename();
		csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/fileUpload",
			userid, "API: file upload", "location: " + uploadLocation );

		resultJson.put( "uploadLocationResolved", uploadLocation );

		try {

			if ( multiPartFile.getSize() == 0 || multiPartFile.getOriginalFilename().length() == 0 ) {
				throw new IOException( "Invalid parameters. Verify name and size of attachment" );
			}

			File targetDir = new File( uploadLocation );
			if ( !targetDir.exists() ) {
				targetDir.mkdirs();
			}

			File uploadFile = new File( targetDir, artifactName );

			if ( uploadFile.exists() ) {
				if ( !overWrite ) {
					throw new IOException( "target location contains an existing file of the same name. Rename or set overwrite to true" );
				} else {
					resultJson.put( "warning", "Existing file is being overwritten" );
				}
			}

			multiPartFile.transferTo( uploadFile );

			resultJson.put( "savedTo", uploadFile.getAbsolutePath() );
			resultJson.put( "uploadResult", true );

			if ( otherHosts != null && otherHosts.length > 0 ) {
				List<String> hostsToCopyTo = new ArrayList<String>( Arrays.asList( otherHosts ) );
				String syncResult = serviceManager.syncToOtherHosts(
					userid,
					hostsToCopyTo,
					uploadFile.getAbsolutePath(),
					uploadFile.getParentFile().getAbsolutePath(),
					"ssadmin", userid,
					false, null );
				resultJson.put( "syncResult", syncResult );
			}

			resultJson.put( "success", true );
		} catch (Exception e) {
			resultJson.put( "success", false );
			resultJson.put( "errorMessage", e.getMessage() );

			logger.warn( CSAP.getCsapFilteredStackTrace( e ) );
			logger.debug( "Full exception", e );
		}

		return resultJson;
	}

	private String replaceVariables ( String uploadLocation )
			throws IOException {
		if ( uploadLocation.contains( "$STAGING" ) ) {
			uploadLocation = uploadLocation.replace( "$STAGING", csapApp.getStagingFolder().getCanonicalPath() );
		}
		if ( uploadLocation.contains( "$PROCESSING" ) ) {
			uploadLocation = uploadLocation.replace( "$PROCESSING", csapApp.getProcessingDir().getCanonicalPath() );
		}
		if ( uploadLocation.contains( "$DEPLOY_FOLDER" ) ) {
			uploadLocation = uploadLocation.replace( "$DEPLOY_FOLDER", csapApp.getDeploymentStorageFolder().getCanonicalPath() );
		}
		return uploadLocation;
	}

	@CsapDoc ( notes = { "/upload/service: upload a service artifact to the file system, and start.",
			"Note: agent api only, password should always be encypted using CSAP encoder UI.",
			"Required Param: serviceName - uploadFile will be renamed to be serviceName, location set to $DEPLOY_FOLDER",
			"Required Param: version - if specified,  csap version file will be created in the upload location",
			"Optional Param: serviceStart - defaults true. Set to false to skip service start. Service folder is deleted"
					+ " - use other apis if needed to backup logs or skip working folder clean"
	} , linkTests = {
			"Sample Upload"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "serviceName=ServletSample_8041,version=1.2.3-test,serviceStart=true"
	} , fileParams = {
			"uploadFile"
	} )
	@PostMapping ( value = "/upload/service" )
	public ObjectNode uploadServiceToFileSystem (
													@RequestParam ( "uploadFile" ) MultipartFile multiPartFile,
													@RequestParam ( "serviceName" ) String serviceName_port,
													@RequestParam ( "version" ) String version,
													@RequestParam ( value = "serviceStart" , required = false , defaultValue = "true" ) boolean serviceStart,
													@RequestParam ( SpringAuthCachingFilter.USERID ) String userid,
													@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass,
													HttpServletRequest request )
			throws IOException {

		ObjectNode resultJson = (ObjectNode) request
			.getAttribute( SpringAuthCachingFilter.SEC_RESPONSE_ATTRIBUTE );

		resultJson.put( "uploadFile", multiPartFile.getOriginalFilename() );
		resultJson.put( "uploadFileSize", multiPartFile.getSize() );
		resultJson.put( "version", version );

		try {

			String uploadLocation = replaceVariables( "$DEPLOY_FOLDER" );

			ServiceInstance serviceInstance = findServiceOnCurrentHost( serviceName_port );

			String checkVersion = version.replaceAll( "-", "1" ).replaceAll( ".", "1" );
			if ( !StringUtils.isAlphanumeric( checkVersion ) ) {
				throw new IOException( "Invalid version specified: " + checkVersion );
			}

			uploadLocation = uploadLocation.replace( "$DEPLOY_FOLDER", csapApp.getDeploymentStorageFolder().getCanonicalPath() );

			csapEventClient.generateEvent( CsapEventClient.CSAP_SVC_CATEGORY + "/" + serviceName_port,
				userid, "API: upload, start", "location: " + uploadLocation );

			if ( multiPartFile.getSize() == 0 ) {
				throw new IOException( "Invalid parameters. Verify name and size of attachment" );
			}

			File targetDir = new File( uploadLocation );
			if ( !targetDir.exists() ) {
				targetDir.mkdirs();
			}

			File uploadFile = new File( targetDir, serviceInstance.getDeployFileName() );

			resultJson.put( "destination", uploadFile.getAbsolutePath() );

			multiPartFile.transferTo( uploadFile );

			resultJson.put( "success", true );

			File versionFile = serviceManager.createVersionFile(
				csapApp.getDeployVersionFile( serviceInstance ),
				version, userid, "API Upload" );

			resultJson.put( "versionFileCreated", versionFile.getAbsolutePath() );

			if ( serviceName_port != null && serviceName_port.length() > 0 && serviceStart ) {
				resultJson.put( "serviceStartRequested", true );
				// startServiceAndWaitABit( serviceName_port, userid, resultJson
				// );
				ArrayList<String> services = new ArrayList<>();
				services.add( serviceName_port );
				serviceCommands.startRequest(
					userid, services, null, null, null,
					"clean", null, userid, null );
			} else {
				resultJson.put( "serviceStartRequested", false );
			}

		} catch (Exception e) {
			resultJson.put( "success", false );
			resultJson.put( "errorMessage", e.getMessage() );

			logger.warn( CSAP.getCsapFilteredStackTrace( e ) );
			logger.debug( "Full exception", e );
		}

		return resultJson;
	}

	private ServiceInstance findServiceOnCurrentHost ( String serviceName_port )
			throws IOException {

		ServiceInstance instance = csapApp.getServiceInstanceCurrentHost( serviceName_port );

		if ( instance == null ) {
			throw new IOException( "Requested service not found in model: " + serviceName_port );
		}

		return instance;
	}

}
