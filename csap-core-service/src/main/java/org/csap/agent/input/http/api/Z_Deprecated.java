package org.csap.agent.input.http.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.csap.CsapMonitor;
import org.csap.agent.CsapCoreService;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.Application;
import org.csap.agent.model.ReleasePackage;
import org.csap.agent.services.ServiceOsManager;
import org.csap.agent.stats.HostCollector;
import org.csap.docs.CsapDoc;
import org.csap.security.SpringAuthCachingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
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

@RestController
@CsapMonitor ( prefix = "api.deprecated" )
@RequestMapping ( CsapCoreService.API_URL )
@CsapDoc ( title = "Deprecated APIS" , type = CsapDoc.PUBLIC , notes = {
		"APIs that are deprecated and will be removed in the next major release"
} )
public class Z_Deprecated {

	final Logger logger = LoggerFactory.getLogger( this.getClass() );

	ObjectMapper jacksonMapper = new ObjectMapper();

	@Inject
	private Application csapApp;

	@CsapDoc ( notes = "refer to: use /collect/type methods" , linkTests = { "CsAgent JMX",
			"CsAgent App" } , linkGetParams = { "serviceName=CsAgent_8011,type=jmx,interval=30,number=1",
					"serviceName=CsAgent_8011,type=app,interval=30,number=1", } )
	@GetMapping ( {
			"/appData/{serviceName}/{type}/{interval}/{number}"
	} )
	@Deprecated
	public JsonNode appData (
								@PathVariable ( "serviceName" ) String serviceName,
								@PathVariable ( "type" ) String type,
								@PathVariable ( "interval" ) String interval,
								@PathVariable ( "number" ) int number ) {

		logger.debug( "DeprecatedApi - use /api/agent/collection/*" );

		return agentApi.getPerformanceData( type, interval, number, HostCollector.ALL_SERVICES );
	}

	@CsapDoc ( notes = {
			"refer to: /collect methods. Collected performance data: resources consumed and workload produced."
	} , linkTests = {
			"resource 1", "resource all",
			"service 1", "jmx 1"
	} , linkGetParams = { "type=resource,interval=30,number=1", "type=resource,interval=30,number=-1",
			"type=service,interval=30,number=1", "type=jmx,interval=30,number=1", } )
	@Deprecated
	@GetMapping ( "/metric/{type}/{interval}/{number}" )
	public JsonNode performanceData (
										@PathVariable ( "type" ) String type,
										@PathVariable ( "interval" ) String interval,
										@PathVariable ( "number" ) int number )
			throws Exception {

		logger.debug( "DeprecatedApi - use /api/agent/collection/*" );
		return agentApi.getPerformanceData( type, interval, number, HostCollector.ALL_SERVICES );
	}

	@Inject
	AgentApi agentApi;

	@CsapDoc ( notes = {
			"refer to: /api/agent/emanStatus"
	} , produces = "plain/txt" )
	@RequestMapping ( "/emanStatus" )
	@Deprecated
	public String emanStatus (
								@RequestParam ( value = "alertLevel" , required = false , defaultValue = "1.0" ) double alertLevelForFiltering ) {

		logger.debug( "refer to:  /api/agent/emanStatus" );
		return agentApi.emanStatus( alertLevelForFiltering );

	}

	@CsapDoc ( notes = { "Health Check for Http Monitoring via Eman, hyperic, nagios, and others.",
			"* Agent service only" } , linkTests = "default" )
	@RequestMapping ( value = { "/hostStatus", "/vmStatus" } )
	@Deprecated
	public JsonNode hostStatus ()
			throws JsonGenerationException, JsonMappingException, IOException {

		return agentApi.runtime();
	}

	@CsapDoc ( notes = "refer to:  /api/agent/CapabilityHealth " )
	@RequestMapping ( "/CapabilityHealth" )
	@Deprecated
	public ObjectNode applicationHealth ()
			throws Exception {

		logger.debug( "refer to:  /api/agent/health" );
		return agentApi.health();
	}

	@CsapDoc ( notes = "refer to:  /agent/health methods" )
	@RequestMapping ( { "/hostHealth", "/vmHealth" } )
	@Deprecated
	public ObjectNode hostHealth ()
			throws JsonGenerationException, JsonMappingException,
			IOException {

		logger.debug( "DeprecatedApi - use /api/agent/status" );
		return agentApi.status();
	}

	@CsapDoc ( notes = { "get the last collected values for Application Summary Statistcs",
			"Optional Parameter: releasePackage may be specified to filter results. Default is all packages" } , linkTests = { "All hosts",
					"releasePackage Filter" } , linkGetParams = { "no=filter", "releasePackage=changeMe" } )
	@RequestMapping ( value = "/realTimeMeters" )
	@Deprecated
	public JsonNode realTimeMeters (
										@RequestParam ( value = "releasePackage" , required = false , defaultValue = Application.ALL_PACKAGES ) String releasePackage )
			throws Exception {

		return applicationApi.realTimeMeters( releasePackage );
	}

	@Inject
	ApplicationApi appRuntime;

	@CsapDoc ( notes = { "refer to: /api/application"
	} , linkTests = { "CsAgent",
			"CsAgent with releasePackage",
			"list services" } , linkGetParams = { "serviceName=CsAgent", "serviceName=CsAgent,releasePackage=changeMe" } )
	@RequestMapping ( value = "/service/urls/active/{serviceName}" )
	@Deprecated
	public JsonNode serviceUrlsFilteredByActiveState (
														@PathVariable ( "serviceName" ) String serviceName,
														@RequestParam ( value = "releasePackage" , required = false , defaultValue = Application.ALL_PACKAGES ) String releasePackage ) {

		logger.debug( "DeprecatedApi - refer to /api/application/service/*" );
		return appRuntime.serviceUrlsFilteredByActiveState( serviceName, releasePackage );
	}
	
	@CsapDoc(notes = "refer to: /model/service/urls/")
	@RequestMapping(value = "/service/urls/all/{serviceName}")
	@Deprecated
	public JsonNode serviceUrlsForAllInstances(
			@PathVariable("serviceName") String serviceName) {
		return csapModel.serviceUrlsForAllInstances( serviceName );
	}

	@Inject
	ModelApi csapModel;

	@CsapDoc ( notes = {
			"refer to: /model/services/currentLifecycle/name"
	} , linkTests = {
			"CsAgent",
			"List"
	} , linkGetParams = { "serviceName=CsAgent" } )
	@RequestMapping ( value = { "/services/currentLifecycle/name/{serviceName}", "/services/currentLifecycle/jvm/{serviceName}" } )
	@Deprecated
	public JsonNode serviceDefinitionsInLifecycleFilteredByName (
																	@PathVariable ( "serviceName" ) String serviceName ) {

		logger.debug( "DeprecatedApi - refer to /api/model" );
		return csapModel.serviceDefinitionsInLifecycleFilteredByName( serviceName );
	}

	public static final String USERID_PASS_PARAMS = "userid=someDeveloper,pass=blank,";

	@Inject
	ServiceOsManager serviceManager;

	@Inject
	CsapEventClient csapEventClient;

	@Inject
	ModelApi modelApi;

	
	@CsapDoc(notes = "refer to /api/application",
			linkTests = {"def reload"},
			linkPostParams = {"branch=trunk,userid=someDeveloper,pass=changeMe"})
	@RequestMapping(  value = "/definition/reload", produces = MediaType.TEXT_PLAIN_VALUE)
	@Deprecated
	public String definitionReload(
			@RequestParam(value="branch") String branch,
			@RequestParam(SpringAuthCachingFilter.USERID) String userid,
			@RequestParam(SpringAuthCachingFilter.PASSWORD) String inputPass,
			HttpServletResponse response) throws Exception {

		return applicationApi.definitionReload( branch, userid, inputPass, response );
	}
	
	
	
	@CsapDoc ( notes = {
			"refer to /model/services/byName"
	} , linkPaths = {
			"/services/jvm/CsAgent",
			"/services/byName/CsAgent"
	} )
	@RequestMapping ( value = { "/services/jvm/{serviceName:.+}", "/services/byName/{serviceName:.+}", } )
	@Deprecated
	public JsonNode serviceDefinitionsFilterdByName (
														@PathVariable ( "serviceName" ) String serviceName ) {

		return modelApi.serviceDefinitionsFilteredByName( serviceName );
	}

	@Inject
	ApplicationApi applicationApi;

	@CsapDoc ( notes = {
			"refer to /api/appication/service"
	} , linkTests = {
			"ServletSample"
	} , linkPaths = {
			"/serviceDeploy/ServletSample_8041"
	} , linkPostParams = {
			USERID_PASS_PARAMS
					+ "performStart=true,mavenId=com.your.group:Servlet3Sample:1.0.3:war,cluster=changeMe"
	} )
	@PostMapping ( "/serviceDeploy/{serviceName}" )
	@Deprecated
	public JsonNode serviceDeploy (
									@PathVariable ( "serviceName" ) String serviceName_port,
									@RequestParam ( "mavenId" ) String mavenId,
									@RequestParam ( value = "cluster" , defaultValue = "" ) String cluster,
									@RequestParam ( value = "performStart" , defaultValue = "true" ) boolean performStart,
									@RequestParam ( SpringAuthCachingFilter.USERID ) String userid,
									@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass,
									HttpServletRequest request )
			throws JsonGenerationException, JsonMappingException, IOException {

		logger.debug( "DeprecatedApi - use /api/application/serviceDeploy" );

		return applicationApi.serviceDeploy( serviceName_port, stripOffLifecycle(cluster), mavenId, performStart, userid, inputPass, request );

	}
	
	public String stripOffLifecycle( String cluster ) {
		
		if ( cluster.startsWith( Application.getCurrentLifeCycle() + "-" ) ) {
			return cluster.substring( Application.getCurrentLifeCycle().length() + 1) ;
		}
		
		return cluster ;
	}

	@CsapDoc ( notes = {
			"refer to /api/appication/service"
	} , linkTests = {
			"ServletSample"
	} , linkPaths = {
			"/serviceStart/ServletSample_8041" } , linkPostParams = { "userid=someDeveloper,pass=changeMe,cluster=changeMe" } )
	@RequestMapping ( "/serviceStart/{serviceName}" )
	@Deprecated
	public JsonNode serviceStart (
									@PathVariable ( "serviceName" ) String serviceName_port,
									@RequestParam ( value = "cluster" , defaultValue = "" ) String cluster,
									@RequestParam ( SpringAuthCachingFilter.USERID ) String userid,
									@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass,
									HttpServletRequest request )
			throws Exception {

		logger.debug( "DeprecatedApi - use /api/application/service/start" );
		
		if ( Application.isJvmInManagerMode() ) {
			return applicationApi.serviceStart( serviceName_port, stripOffLifecycle(cluster), null, null, null, null, null, null, userid, inputPass, request );
		} else {
			ArrayList<String> services = new ArrayList<>() ;
			services.add( serviceName_port ) ;
			return agentApi.serviceStart( services, null, null, null, null, null, null, userid, inputPass, request );
		}

	}

	@CsapDoc ( notes = {
			"refer to /api/appication/service"
	} , linkTests = {
			"ServletSample" } , linkPaths = { "/serviceStop/ServletSample_8041" } , linkPostParams = {
					"userid=someDeveloper,pass=changeMe,cluster=changeMe,clean=clean" } )
	@RequestMapping ( "/serviceStop/{serviceName}" )
	@Deprecated
	public JsonNode serviceStop (
									@PathVariable ( "serviceName" ) String serviceName_port,
									@RequestParam ( value = "cluster" , defaultValue = "" ) String cluster,
									@RequestParam ( value = "clean" , defaultValue = "" ) String clean,
									@RequestParam ( SpringAuthCachingFilter.USERID ) String userid,
									@RequestParam ( SpringAuthCachingFilter.PASSWORD ) String inputPass,
									HttpServletRequest request )
			throws JsonGenerationException, JsonMappingException, IOException {

		logger.debug( "DeprecatedApi - use /api/application/service/kill" );
		if ( Application.isJvmInManagerMode() ) {
			return applicationApi.serviceKill( serviceName_port, stripOffLifecycle(cluster), clean, "keepLogs", userid, inputPass, request );
		} else {
			ArrayList<String> services = new ArrayList<>() ;
			services.add( serviceName_port ) ;
			return agentApi.serviceKill( services, clean, null, userid, userid, request );
		}
	}

}
