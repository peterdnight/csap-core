/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.input.http.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.csap.CsapMonitor;
import org.csap.agent.CsapCoreService;
import org.csap.agent.input.http.ui.rest.DefinitionRequests;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.ActiveUsers;
import org.csap.agent.model.Application;
import org.csap.agent.model.ReleasePackage;
import org.csap.agent.model.ServiceAlertsEnum;
import org.csap.agent.services.ServiceCommands;
import org.csap.agent.services.ServiceOsManager;
import org.csap.docs.CsapDoc;
import org.csap.integations.CsapSecurityConfiguration;
import org.csap.security.CsapUser;
import org.csap.security.SpringAuthCachingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *
 * @author someDeveloper
 */
@RestController
@CsapMonitor ( prefix = "api.application" )
@RequestMapping ( { CsapCoreService.API_URL + "/application" } )
@CsapDoc ( title = "/api/application/*: apis for querying data aggregated across all hosts." , type = CsapDoc.PUBLIC , notes = {
		"CSAP Application Performance APis provide access to the runtime data aggregated across all hosts.",
		"For direct access to CSAP performance collections - refer to <a class='simple' href='class?clazz=org.csap.agent.input.http.api.Runtime_Host'>Host Apis</a> ",
		"<a class='pushButton' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/csapboot.png' />"
				+ "<img class='csapDocImage' src='CSAP_BASE/images/event.png' />"
				+ "<img class='csapDocImage' src='CSAP_BASE/images/editOverview.png' />",
		"Note: unless otherwise stated - these apis can only be executed on CSAP Admin Service instances. Typically: https://yourApp/admin" } )
public class ApplicationApi {

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
			"Validates the application definition, returning any errors or warnings. ",
			"Optionally specify useCurrent for the definition and release package to find any warning in the ",
			"currently loaded definition."
	} , linkTests = {
			"definition validate"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "definition=useCurrent,releasePackage=useCurrent"
	} )
	@PostMapping ( value = "/definition/validate" )
	public ObjectNode definitionValidate (
											@RequestParam ( "definition" ) String definition,
											@RequestParam ( "releasePackage" ) String releasePackage,
											@RequestParam ( SpringAuthCachingFilter.USERID ) String userid,
											@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass,
											HttpServletResponse response,
											HttpServletRequest request )
			throws Exception {

		logger.info( "validate by user: {}", userid );

		if ( definition.equals( "useCurrent" ) ) {
			definition = csapApp.getRootModel().getJsonModelDefinition().toString();
		}
		if ( releasePackage.equals( "useCurrent" ) ) {
			releasePackage = csapApp.getRootModel().getReleasePackageName();
		}

		logger.debug( "{} model contains: {}", releasePackage, definition );

		return csapApp.checkDefinitionForParsingIssues( definition, releasePackage, request.getPathInfo() );
	}

	@Autowired ( required = false )
	DefinitionRequests defRequests;

	@CsapDoc ( notes = {
			"Reloads the Application definition from source control system",
			"Note: if run on agent - only agent will reload. If run on Admin - all hosts will be reloaded"
	} , linkTests = {
			"def reload"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "branch=trunk"
	} )
	@RequestMapping ( value = "/definition/reload" , produces = MediaType.TEXT_PLAIN_VALUE )
	public String definitionReload (
										@RequestParam ( value = "branch" ) String branch,
										@RequestParam ( SpringAuthCachingFilter.USERID ) String userid,
										@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass,
										HttpServletResponse response )
			throws Exception {

		logger.info( "Reloading by user: {}", userid );

		String results = defRequests.reloadApplication( userid, inputPass, branch, "HostCommand" );

		return results;
	}

	@CsapDoc ( notes = "Summary of Application health" )
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

	private ObjectNode getAllServices () {
		ObjectNode response = jacksonMapper.createObjectNode();

		response.put( "info", "add service name to url" );
		response.set( "availableServices", jacksonMapper.convertValue(
			csapApp.getActiveModel().getAllPackagesModel().getServiceNamesInLifecycle(),
			ArrayNode.class ) );
		return response;
	}

	private ObjectNode getAllPackages ( String specifiedPackage ) {
		ObjectNode response = jacksonMapper.createObjectNode();

		response.put( "info", "invalid releasePackage: " + specifiedPackage );
		response.set( "releasePackages", jacksonMapper.convertValue(
			csapApp.getPackageNames(),
			ArrayNode.class ) );
		return response;
	}

	@CsapDoc ( notes = { "get the last collected health report for services support health report api",
			"Optional Parameter: releasePackage may be specified to filter results. Default is all packages" } )
	@RequestMapping ( value = "/health/reports" )
	public JsonNode serviceHealthReports (
											@RequestParam ( value = "releasePackage" , required = false , defaultValue = Application.ALL_PACKAGES ) String releasePackage )
			throws Exception {

		if ( !Application.isJvmInManagerMode() ) {
			return managerError;
		}

		return csapApp.getHostStatusManager().getAllAlerts();
	}

	@CsapDoc ( notes = { "the latest runtime collection from agents",
			"Optional Parameter: releasePackage may be specified to filter results. Default is all packages"
	} , linkTests = {
			"All hosts",
			"releasePackage Filter"
	} , linkGetParams = {
			"no=filter", "releasePackage=changeMe"
	} )
	@RequestMapping ( value = "/collection" )
	public JsonNode collection (
									@RequestParam ( value = "releasePackage" , required = false , defaultValue = Application.ALL_PACKAGES ) String releasePackage )
			throws Exception {

		if ( !Application.isJvmInManagerMode() ) {
			return managerError;
		}

		if ( !csapApp.getPackageNames().contains( releasePackage )
				&& !releasePackage.equals( Application.ALL_PACKAGES ) ) {
			return getAllPackages( releasePackage );
		}

		ReleasePackage model = csapApp.getModel( releasePackage );

		Map<String, ObjectNode> runtimeSummary = csapApp.getHostStatusManager().hostsRuntime( model.getHostsCurrentLc() );

		return jacksonMapper.convertValue( runtimeSummary, ObjectNode.class );
	}

	@CsapDoc ( notes = { "the latest runtime collection from agents, filted by host attributes",
			"Optional Parameter: releasePackage may be specified to filter results. Default is all packages"
	} , linkTests = {
			"All hosts",
			"releasePackage Filter"
	} , linkGetParams = {
			"no=filter", "releasePackage=changeMe"
	} )
	@RequestMapping ( value = "/collection/hosts" )
	public JsonNode collectionHosts (
										@RequestParam ( value = "releasePackage" , required = false , defaultValue = Application.ALL_PACKAGES ) String releasePackage )
			throws Exception {

		if ( !Application.isJvmInManagerMode() ) {
			return managerError;
		}

		if ( !csapApp.getPackageNames().contains( releasePackage )
				&& !releasePackage.equals( Application.ALL_PACKAGES ) ) {
			return getAllPackages( releasePackage );
		}

		ReleasePackage model = csapApp.getModel( releasePackage );

		Map<String, ObjectNode> runtimeSummary = csapApp.getHostStatusManager().hostsInfo( model.getHostsCurrentLc() );

		return jacksonMapper.convertValue( runtimeSummary, ObjectNode.class );
	}

	@CsapDoc ( notes = { "the latest runtime collection from agents, filted by service attributes",
			"Optional Parameter: releasePackage may be specified to filter results. Default is all packages"
	} , linkTests = {
			"All hosts",
			"releasePackage Filter"
	} , linkGetParams = {
			"no=filter", "releasePackage=changeMe"
	} )
	@RequestMapping ( value = "/collection/hosts/services" )
	public JsonNode collectionHostsServices (
												@RequestParam ( value = "releasePackage" , required = false , defaultValue = Application.ALL_PACKAGES ) String releasePackage )
			throws Exception {

		if ( !Application.isJvmInManagerMode() ) {
			return managerError;
		}

		if ( !csapApp.getPackageNames().contains( releasePackage )
				&& !releasePackage.equals( Application.ALL_PACKAGES ) ) {
			return getAllPackages( releasePackage );
		}

		ReleasePackage model = csapApp.getModel( releasePackage );

		Map<String, ObjectNode> runtimeSummary = csapApp.getHostStatusManager().hostsServices( model.getHostsCurrentLc(), null, null );

		return jacksonMapper.convertValue( runtimeSummary, ObjectNode.class );
	}

	@CsapDoc ( notes = { "the latest service related data from agents, filtered by serviceName",
			"param: serviceNameFilter path parameter matches against serviceName_port. Note: wildCards are supported eg. CsAgent.*",
			"Optional: releasePackage may be specified to filter results. Default is all packages"
	} , linkTests = {
			"All Packages, filtered by CsAgent",
			"releasePackage Filter"
	} , linkGetParams = {
			"serviceNameFilter=CsAgent_8011", "releasePackage=changeMe,serviceNameFilter=CsAgent_8011"
	} )
	@RequestMapping ( value = "/collection/hosts/services/{serviceNameFilter}" )
	public JsonNode collectionHostsServicesFiltered (
														@PathVariable ( "serviceNameFilter" ) String serviceNameFilter,
														@RequestParam ( value = "releasePackage" , required = false , defaultValue = Application.ALL_PACKAGES ) String releasePackage )
			throws Exception {

		if ( !Application.isJvmInManagerMode() ) {
			return managerError;
		}

		if ( !csapApp.getPackageNames().contains( releasePackage )
				&& !releasePackage.equals( Application.ALL_PACKAGES ) ) {
			return getAllPackages( releasePackage );
		}

		ReleasePackage model = csapApp.getModel( releasePackage );

		Map<String, ObjectNode> runtimeSummary = csapApp.getHostStatusManager().hostsServices( model.getHostsCurrentLc(), serviceNameFilter,
			null );

		return jacksonMapper.convertValue( runtimeSummary, ObjectNode.class );
	}

	@CsapDoc ( notes = { "the latest service related data from agents, filtered by serviceName and attribute",
			"param: serviceNameFilter path parameter matches against serviceName_port. Note: wildCards are supported eg. CsAgent.*",
			"param: attributeName path parameter will only return specified attribute",
			"Optional: releasePackage may be specified to filter results. Default is all packages"
	} , linkTests = {
			"All Packages, filtered by CsAgent threadCount",
			"releasePackage Filter"
	} , linkGetParams = {
			"serviceNameFilter=CsAgent_8011,attributeName=threadCount",
			"releasePackage=changeMe,serviceNameFilter=CsAgent_8011,attributeName=threadCount"
	} )
	@RequestMapping ( value = "/collection/hosts/services/{serviceNameFilter}/{attributeName}" )
	public JsonNode collectionHostsServicesAttributeFiltered (
																@PathVariable ( "serviceNameFilter" ) String serviceNameFilter,
																@PathVariable ( "attributeName" ) String attributeName,
																@RequestParam ( value = "releasePackage" , required = false , defaultValue = Application.ALL_PACKAGES ) String releasePackage )
			throws Exception {

		if ( !Application.isJvmInManagerMode() ) {
			return managerError;
		}

		if ( !csapApp.getPackageNames().contains( releasePackage )
				&& !releasePackage.equals( Application.ALL_PACKAGES ) ) {
			return getAllPackages( releasePackage );
		}

		ReleasePackage model = csapApp.getModel( releasePackage );

		Map<String, ObjectNode> runtimeSummary = csapApp.getHostStatusManager().hostsServices( model.getHostsCurrentLc(), serviceNameFilter,
			attributeName );

		return jacksonMapper.convertValue( runtimeSummary, ObjectNode.class );
	}

	@CsapDoc ( notes = { "the latest cpu related data from agents",
			"Optional Parameter: releasePackage may be specified to filter results. Default is all packages"
	} , linkTests = {
			"All hosts",
			"releasePackage Filter"
	} , linkGetParams = {
			"no=filter", "releasePackage=changeMe"
	} )
	@RequestMapping ( value = "/collection/hosts/cpu" )
	public JsonNode collectionHostsCpu (
											@RequestParam ( value = "releasePackage" , required = false , defaultValue = Application.ALL_PACKAGES ) String releasePackage )
			throws Exception {

		if ( !Application.isJvmInManagerMode() ) {
			return managerError;
		}

		if ( !csapApp.getPackageNames().contains( releasePackage )
				&& !releasePackage.equals( Application.ALL_PACKAGES ) ) {
			return getAllPackages( releasePackage );
		}

		ReleasePackage model = csapApp.getModel( releasePackage );

		Map<String, ObjectNode> runtimeSummary = csapApp.getHostStatusManager().hostsCpuInfo( model.getHostsCurrentLc() );

		return jacksonMapper.convertValue( runtimeSummary, ObjectNode.class );
	}

	@CsapDoc ( notes = { "get the last collected values for Application Summary Statistcs",
			"Optional Parameter: releasePackage may be specified to filter results. Default is all packages" } , linkTests = { "All hosts",
					"releasePackage Filter" } , linkGetParams = { "no=filter", "releasePackage=changeMe" } )
	@RequestMapping ( value = "/realTimeMeters" )
	public JsonNode realTimeMeters (
										@RequestParam ( value = "releasePackage" , required = false , defaultValue = Application.ALL_PACKAGES ) String releasePackage )
			throws Exception {

		if ( !Application.isJvmInManagerMode() ) {
			return managerError;
		}

		if ( !csapApp.getPackageNames().contains( releasePackage )
				&& !releasePackage.equals( Application.ALL_PACKAGES ) ) {
			return getAllPackages( releasePackage );
		}

		ArrayNode metersJson = jacksonMapper.createArrayNode();

		metersJson = csapApp.lifeCycleSettings().getRealTimeMeters().deepCopy();
		ReleasePackage model = csapApp.getModel( releasePackage );

		csapApp.getHostStatusManager().updateRealTimeMeters( metersJson, model.getHostsCurrentLc(),
			null );

		return metersJson;
	}

	@CsapDoc ( notes = "Retrieves the url with the lowest cpu load, but is in service" , linkTests = { "CsAgent",
			"list" } , linkGetParams = {
					"serviceName=CsAgent" } )
	@RequestMapping ( "/service/url/lowestLoad/{serviceName}" )
	public JsonNode serviceUrlWithLowLoad (
											@PathVariable ( "serviceName" ) String serviceName ) {

		if ( !Application.isJvmInManagerMode() ) {
			return managerError;
		}

		if ( serviceName.equals( "{serviceName}" ) ) {
			return getAllServices();
		}

		return urlByLowestResource( serviceName, "/hostStats/cpuLoad" );
	}

	@CsapDoc ( notes = "Retrieves the url with the lowest cpu usage, but is in service" , linkTests = { "CsAgent",
			"list" } , linkGetParams = {
					"serviceName=CsAgent" } )
	@RequestMapping ( value = "/service/url/lowestCpu/{serviceName}" )
	public JsonNode serviceUrlWithLowCpu (
											@PathVariable ( "serviceName" ) String serviceName ) {

		if ( !Application.isJvmInManagerMode() ) {
			return managerError;
		}

		if ( serviceName.equals( "{serviceName}" ) ) {
			return getAllServices();
		}
		return urlByLowestResource( serviceName, "/hostStats/cpu" );
	}
	//
	// @GET
	// @Path("/service/url/low/{serviceName}")
	// @Produces(MediaType.APPLICATION_JSON)

	@CsapDoc ( notes = { "For specified service, retrieves urls for active instances",
			"Lowest CPU load, and lowest CPU activity urls will also be noted " } , linkTests = { "CsAgent",
					"list" } , linkGetParams = { "serviceName=CsAgent" } )
	@RequestMapping ( value = "/service/url/low/{serviceName}" )
	public JsonNode serviceUrlsWithLowItems (
												@PathVariable ( "serviceName" ) String serviceName ) {

		if ( !Application.isJvmInManagerMode() ) {
			return managerError;
		}

		if ( serviceName.equals( "{serviceName}" ) ) {
			return getAllServices();
		}
		ObjectNode urlNode = jacksonMapper.createObjectNode();
		urlNode.put( "lowCpu", urlByLowestResource( serviceName, "/hostStats/cpu" ).at( "/url" ).asText() );
		urlNode.put( "lowLoad", urlByLowestResource( serviceName, "/hostStats/cpuLoad" ).at( "/url" ).asText() );
		urlNode.set( "active", serviceUrlsFilteredByActiveState( serviceName, Application.ALL_PACKAGES ) );

		return urlNode;
	}

	@CsapDoc ( notes = { "get service urls for the specified service that are marked as running.",
			"Optional Parameter: releasePackage may be specified to filter results. Default is all packages" } , linkTests = { "CsAgent",
					"CsAgent with releasePackage",
					"list services" } , linkGetParams = { "serviceName=CsAgent", "serviceName=CsAgent,releasePackage=changeMe" } )
	@RequestMapping ( value = "/service/urls/active/{serviceName}" )
	public JsonNode serviceUrlsFilteredByActiveState (
														@PathVariable ( "serviceName" ) String serviceName,
														@RequestParam ( value = "releasePackage" , required = false , defaultValue = Application.ALL_PACKAGES ) String releasePackage ) {

		if ( !Application.isJvmInManagerMode() ) {
			return managerError;
		}

		if ( serviceName.equals( "{serviceName}" ) ) {
			return getAllServices();
		}

		if ( !csapApp.getPackageNames().contains( releasePackage )
				&& !releasePackage.equals( Application.ALL_PACKAGES ) ) {
			return getAllPackages( releasePackage );
		}

		List<String> urls = csapApp
			.getActiveServiceInstances( releasePackage, serviceName )
			.map( serviceInstance -> serviceInstance.getUrl() )
			.collect( Collectors.toList() );

		return jacksonMapper.convertValue( urls, ArrayNode.class );
	}

	//
	private ObjectNode urlByLowestResource ( String serviceName, String attributePath ) {

		List<String> hosts = csapApp
			.getServiceInstances( Application.ALL_PACKAGES, serviceName )
			.filter( csapApp::filterInactiveHostsUsingStatusManager )
			.map( serviceInstance -> serviceInstance.getHostName() )
			.collect( Collectors.toList() );

		if ( hosts.size() == 0 ) {

			logger.debug( "No inservice hosts for service found: {}", serviceName );
			throw new RuntimeException( "No inservice hosts for service found: " + serviceName );
		}

		logger.debug( "{} with service running", hosts );

		String lowHost = csapApp.findHostWithLowestAttribute( hosts, attributePath );

		String lowUrl = csapApp
			.getServiceInstances( Application.ALL_PACKAGES, serviceName )
			.filter( serviceInstance -> serviceInstance.getHostName().equals( lowHost ) )
			.findFirst()
			.map( serviceInstance -> serviceInstance.getUrl() )
			.get();

		ObjectNode response = jacksonMapper.createObjectNode();
		response.put( "selector", attributePath );
		response.put( "url", lowUrl );

		return response;
	}

	@Autowired ( required = false )
	LdapTemplate ldapTemplate;

	@Autowired ( required = false )
	CsapSecurityConfiguration springGlobalContext;

	@SuppressWarnings ( "unchecked" )
	@CsapDoc ( notes = "Get identity attributes for userid. Optional request parameters: full (shows more data), and callback (for JSONP)" , linkTests = {
			"someDeveloper", "someDeveloper:full", "someDeveloper:jsonp" } , linkGetParams = { "userid=someDeveloper", "userid=someDeveloper,full=full",
					"userid=someDeveloper,callback=myTestFunction" } , produces = { MediaType.APPLICATION_JSON_VALUE,
							"application/javascript" } )
	@RequestMapping ( "/userInfo/{userid}" )
	public JsonNode userInfo (
								@PathVariable ( "userid" ) String userid,
								@RequestParam ( value = "callback" , required = false , defaultValue = "false" ) String callback,
								@RequestParam ( value = "full" , required = false , defaultValue = "false" ) String full )
			throws Exception {

		ObjectNode userInfo = jacksonMapper.createObjectNode();
		userInfo.put( "error", "Did not find user" );

		try {
			// CsapUser csapUser = (CsapUser) ldapTemplate.lookup(dn, new
			// CsapUser() );
			long start = System.currentTimeMillis();

			// Attribute filter cuts call time in half, and data size by 90%.
			// Default is to use the filter
			CsapUser csapUser;
			if ( full.equals( "false" ) ) {
				logger.debug( "Doing  CsapUser.PRIMARY_ATTRIBUTES  retrieval" );
				try {
					csapUser = (CsapUser) ldapTemplate.lookup(
						springGlobalContext.getRealUserDn( userid ), CsapUser.PRIMARY_ATTRIBUTES,
						new CsapUser() );
				} catch (NameNotFoundException e) {
					// Try generic tree as well
					csapUser = (CsapUser) ldapTemplate.lookup(
						springGlobalContext.getGenericUserDn( userid ),
						CsapUser.PRIMARY_ATTRIBUTES,
						new CsapUser() );
				}
				userInfo = jacksonMapper.convertValue( csapUser, ObjectNode.class );
			} else {
				logger.debug( "Doing a full attribute retrieval" );
				try {
					csapUser = (CsapUser) ldapTemplate.lookup(
						springGlobalContext.getRealUserDn( userid ),
						new CsapUser() );
				} catch (NameNotFoundException e) {
					logger.debug( "Looking on the generic tree" );
					csapUser = (CsapUser) ldapTemplate.lookup(
						springGlobalContext.getGenericUserDn( userid ),
						new CsapUser() );
				}

				userInfo = jacksonMapper.convertValue( csapUser, ObjectNode.class );
			}

			long len = System.currentTimeMillis() - start;
			logger.debug( "Person: time {} csapUser: \n\t{}", len, csapUser.toString() );

		} catch (Exception e) {
			userInfo.put( "error", "Did not find user" + e.getMessage() );
			logger.error( "Failed LDAP", e );
		}

		// Support for JSONP calls
		return userInfo;
	}

	@CsapDoc ( notes = "Get the full names for specified array of userids, with optional support for JSONP" , linkTests = { "users",
			"users:jsonp" } , linkGetParams = { "userid=someDeveloper,userid=dtandon,userid=paranant,userid=nonExist",
					"userid=someDeveloper,userid=dtandon,callback=myFunctionCall" } , produces = { MediaType.APPLICATION_JSON_VALUE,
							"application/javascript" } )
	@RequestMapping ( "/userNames" )
	public JsonNode userNames (
								@RequestParam ( value = "userid" , required = true ) List<String> userids,
								@RequestParam ( value = "callback" , required = false , defaultValue = "false" ) String callback )
			throws Exception {

		ObjectNode resultNode = jacksonMapper.createObjectNode();
		userids.forEach( userid -> {
			CsapUser csapUser;
			try {
				csapUser = (CsapUser) ldapTemplate.lookup(
					springGlobalContext.getRealUserDn( userid ),
					CsapUser.PRIMARY_ATTRIBUTES,
					new CsapUser() );

				resultNode.put( userid, csapUser.getFullName() );
			} catch (Exception e) {
				resultNode.put( userid, "Failed to retrieve from directory" );
			}
		} );

		logger.debug( "List of resultNode: {}", resultNode.toString() );

		return resultNode;
	}

	public static final String USERID_PASS_PARAMS = "userid=someDeveloper,pass=blank,";

	@Inject
	CsapEventClient csapEventClient;

	@Inject
	ServiceCommands serviceCommands;

	// look for all hosts with matching cluster name
	private List<String> findHosts ( String cluster ) {

		String filteredCluster = cluster.replaceAll( "-", "" );

		List<String> aggregateList = csapApp
			.getReleasePackageStream()
			.filter( model -> model.getLcGroupVerToHostMap().get( filteredCluster ) != null )
			.flatMap( model -> model.getLcGroupVerToHostMap().get( filteredCluster ).stream() )
			.collect( Collectors.toList() );

		// logger.info("aggregateList: " + aggregateList);
		return aggregateList;
	}

	@CsapDoc ( notes = {
			"Get the number of jobs (start, stop, deploy) queued for execution."
	} , linkTests = {
			"Show Jobs"
	} )
	@RequestMapping ( "/service/jobs" )
	public ObjectNode serviceJobStatus ()
			throws Exception {

		ObjectNode resultNode = jacksonMapper.createObjectNode();

		logger.debug( "List of resultNode: {}", resultNode.toString() );
		resultNode.put( "tasksRemaining", csapApp.getHostStatusManager().totalOpsQueued() );

		return resultNode;
	}

	@CsapDoc ( notes = { "/service/kill/{serviceName_port}: api for stopping specified service",
			"Note: Password may optionally be encypted. ",
			"Parameter: cluster - the set of hosts to stop the service on ",
			"Parameter: clean - optional - omit or leave blank to not delete files",
			"Parameter: keepLogs - optional - omit or leave blank to not delete files"
	} , linkTests = {
			"ServletSample"
	} , linkPaths = {
			"/service/kill/ServletSample_8041"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "cluster=TestJavaServices-1,clean=clean,keepLogs=keepLogs" } )
	@RequestMapping ( "/service/kill/{serviceName}" )
	public ObjectNode serviceKill (
									@PathVariable ( "serviceName" ) String serviceName_port,
									@RequestParam ( defaultValue = "" ) String cluster,
									@RequestParam ( defaultValue = "" ) String clean,
									@RequestParam ( defaultValue = "" ) String keepLogs,
									@RequestParam ( SpringAuthCachingFilter.USERID ) String userid,
									@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass,
									HttpServletRequest request )
			throws JsonGenerationException, JsonMappingException, IOException {

		csapEventClient.generateEvent( CsapEventClient.CSAP_SVC_CATEGORY + "/" + serviceName_port,
			userid, "API: stop", "Cluster: " + cluster + " clean:" + clean );
		// logger.info("serviceName" + serviceName + " authHeader: " +
		// authHeader +
		// "");

		ObjectNode security = (ObjectNode) request
			.getAttribute( SpringAuthCachingFilter.SEC_RESPONSE_ATTRIBUTE );

		ObjectNode resultJson = jacksonMapper.createObjectNode();
		if ( cluster.length() == 0 ) {
			resultJson
				.put( "error",
					"missing cluster parameter" );

		} else {

			List<String> hosts = findHosts( Application.getCurrentLifeCycle() + cluster );

			if ( hosts.size() > 0 ) {
				ArrayList<String> services = new ArrayList<>();
				services.add( serviceName_port );
				resultJson = serviceCommands.killRemoteRequests(
					userid,
					services, hosts, clean, keepLogs,
					userid, inputPass );

			} else {

				resultJson
					.put( "error",
						"cluster did not resolve to any hosts: " + cluster );

				resultJson
					.set( "available", jacksonMapper.convertValue(
						csapApp.getClustersForActiveLifecycle(),
						ObjectNode.class ) );
			}
		}
		resultJson.set( "security", security );

		return resultJson;

	}

	@CsapDoc ( notes = { "/service/start/{serviceName_port}: api for starting specified service",
			"Note: Password may optionally be encypted. ",
			"Parameter: cluster - the set of hosts to start the service on ",
			"Optional: clean - omit or leave blank to not delete files",
			"Optional: keepLogs -  omit or leave blank to not delete files",
			"Optional: deployId - start will only be issued IF deployment id specified was successful."
	} , linkTests = {
			"ServletSample"
	} , linkPaths = {
			"/service/start/ServletSample_8041"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "cluster=TestJavaServices-1,commandArguments=blank,runtime=blank,hotDeploy=blank,startClean=blank,startNoDeploy=blank,deployId=blank" } )
	@RequestMapping ( "/service/start/{serviceName}" )
	public ObjectNode serviceStart (
										@PathVariable ( "serviceName" ) String serviceName_port,
										@RequestParam ( defaultValue = "" ) String cluster,

										@RequestParam ( required = false ) String commandArguments,
										@RequestParam ( required = false ) String runtime,
										@RequestParam ( required = false ) String hotDeploy,
										@RequestParam ( required = false ) String startClean,
										@RequestParam ( required = false ) String noDeploy,
										String deployId,

										@RequestParam ( SpringAuthCachingFilter.USERID ) String apiUserid,
										@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String apiPass,
										HttpServletRequest request )
			throws JsonGenerationException, JsonMappingException, IOException {

		csapEventClient.generateEvent( CsapEventClient.CSAP_SVC_CATEGORY + "/" + serviceName_port,
			apiUserid, "API: start", "Cluster: " + cluster + " commandArguments:" + commandArguments
					+ " runtime: " + runtime + " hotDeploy: " + hotDeploy
					+ " startClean: " + startClean + " noDeploy:" + noDeploy );

		ObjectNode resultJson = jacksonMapper.createObjectNode();
		if ( cluster.length() == 0 ) {
			resultJson
				.put( "error",
					"missing cluster parameter" );

		} else {

			List<String> hosts = findHosts( Application.getCurrentLifeCycle() + cluster );

			if ( hosts.size() > 0 ) {
				ArrayList<String> services = new ArrayList<>();
				services.add( serviceName_port );
				resultJson = serviceCommands.startRemoteRequests(
					apiUserid, services, hosts,
					commandArguments, runtime,
					hotDeploy, startClean, noDeploy,
					apiUserid, apiPass, null );

			} else {

				resultJson
					.put( "error",
						"cluster did not resolve to any hosts: " + cluster );

				resultJson
					.set( "available", jacksonMapper.convertValue(
						csapApp.getClustersForActiveLifecycle(),
						ObjectNode.class ) );
			}
		}

		ObjectNode security = (ObjectNode) request
			.getAttribute( SpringAuthCachingFilter.SEC_RESPONSE_ATTRIBUTE );
		resultJson.set( "security", security );

		return resultJson;

	}

	@CsapDoc ( notes = { "/service/deploy/{serviceName_port}: api for starting specified service",
			"Parameter: cluster - the set of hosts to deploy the service on ",
			"Optional: mavenId - the name of the artifact to build eg. org.csap:BootEnterprise:1.0.27:jar . 'default' will use version from application definition. ",
			"Optional: performStart - defaults to true. Setting to false will skip the stop and start. ",
			"Notes: ensure artifact is in repository prior to initiating."
					+ " stop/clean should be run prior to deploy to remove files from previous deployment."
					+ " Depending on the size of the artifact being deployed, time will vary from 30s to several minutes. "
					+ " Clustered deployments will be much faster then deploying host by host",
			"Note: Password may optionally be encypted. "
	} , linkTests = {
			"ServletSample"
	} , linkPaths = {
			"/service/deploy/ServletSample_8041"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "performStart=true,mavenId=default,cluster=TestJavaServices-1" } )
	@RequestMapping ( "/service/deploy/{serviceName}" )
	public ObjectNode serviceDeploy (
										@PathVariable ( "serviceName" ) String serviceName_port,
										@RequestParam ( defaultValue = "" ) String cluster,

										@RequestParam ( defaultValue = ServiceOsManager.MAVEN_DEFAULT_BUILD ) String mavenId,
										@RequestParam ( defaultValue = "true" ) boolean performStart,

										@RequestParam ( SpringAuthCachingFilter.USERID ) String apiUserid,
										@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String apiPass,
										HttpServletRequest request )
			throws JsonGenerationException, JsonMappingException, IOException {

		csapEventClient.generateEvent( CsapEventClient.CSAP_SVC_CATEGORY + "/" + serviceName_port,
			apiUserid, "API: deploy", "Cluster: " + cluster + " performStart" + performStart + " Maven: " + mavenId );

		ObjectNode resultJson = jacksonMapper.createObjectNode();
		if ( cluster.length() == 0 ) {
			resultJson
				.put( "error",
					"missing cluster parameter" );

		} else {

			List<String> hosts = findHosts( Application.getCurrentLifeCycle() + cluster );

			if ( hosts.size() > 0 ) {
				ArrayList<String> services = new ArrayList<>();
				services.add( serviceName_port );
				String deployId = "ms" + System.currentTimeMillis();
				resultJson = serviceCommands.deployRemoteRequests(
					deployId,
					apiUserid, services, hosts,
					apiUserid, "notUsedByApi", "notUsedByApi",
					null, null, null, mavenId, null,
					null, apiUserid, apiPass );

				resultJson.put( "deployId", deployId );

				if ( performStart ) {
					JsonNode startResults = serviceCommands.startRemoteRequests(
						apiUserid, services, hosts,
						null, null,
						null, null, null,
						apiUserid, apiPass, deployId );

					logger.debug( "start results: {} ", startResults );
					resultJson.set( "startResults", startResults );
				}

			} else {

				resultJson
					.put( "error",
						"cluster did not resolve to any hosts: " + cluster );

				resultJson
					.set( "available", jacksonMapper.convertValue(
						csapApp.getClustersForActiveLifecycle(),
						ObjectNode.class ) );
			}
		}

		ObjectNode security = (ObjectNode) request
			.getAttribute( SpringAuthCachingFilter.SEC_RESPONSE_ATTRIBUTE );
		resultJson.set( "security", security );

		return resultJson;

	}

	@Inject
	ActiveUsers activeUsers;

	@CsapDoc ( notes = {
			"Uses active in past 60 minutes"
	} , linkTests = "default" )
	@GetMapping ( AgentApi.USERS_URL )
	public ArrayNode usersActive ()
			throws JsonGenerationException, JsonMappingException, IOException {

		return activeUsers.allAdminUsers();
	}
}
