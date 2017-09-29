package org.csap.agent.input.http.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.csap.CsapMonitor;
import org.csap.agent.CsapCoreService;
import org.csap.agent.model.Application;
import org.csap.agent.model.ReleasePackage;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.stats.NagiosIntegration;
import org.csap.docs.CsapDoc;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *
 * CSAP APIs
 *
 * @author someDeveloper
 *
 */
@RestController
@CsapMonitor ( prefix = "api.model" )
@RequestMapping ( {
		CsapCoreService.API_URL + ModelApi.MODEL_CONTEXT
} )
@CsapDoc ( title = "/api/model/*: apis for querying CSAP definition (Application.json)" , type = CsapDoc.PUBLIC , notes = {
		"CSAP Application Apis provide access to the application definition and instances",
		"<a class='pushButton' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/csapboot.png' />"
				+ "<img class='csapDocImage' src='CSAP_BASE/images/event.png' />"
				+ "<img class='csapDocImage' src='CSAP_BASE/images/editOverview.png' />" } )
public class ModelApi {

	final Logger logger = LoggerFactory.getLogger( this.getClass() );
	
	public final static String MODEL_CONTEXT = "/model" ;

	ObjectMapper jacksonMapper = new ObjectMapper();

	// Spring Injected
	@Inject
	private Application application;

	@CsapDoc ( notes = "Summary information for application. On Admin nodes, it will include packages, clusters, trending configuration and more." )
	@RequestMapping ( "/summary" )
	public ObjectNode applicationSummary ()
			throws JsonGenerationException, JsonMappingException,
			IOException {

		return application.getCapabilitySummary( true );
	}

	@CsapDoc ( notes = {
			"Gets the application definition for specified release package ",
			"Optional: releasePackage - if not specified, Application.json (root package) will be returned",
			"Optional: path - json path in definition. If not specified - the entire definition will be returned"
	} , linkTests = {
			"Application.json",
			"CsAgent definition",
			"Release Package",
	} , linkGetParams = {
			"params=none", "path='/jvms/CsAgent'", "releasePackage=changeMe"
	} )
	@RequestMapping ( "/application" )
	public JsonNode applicationDefinition (
											String releasePackage,
											String path ) {

		if ( releasePackage == null ) {
			releasePackage = application.getRootModel().getReleasePackageName();
		}
		ReleasePackage model = application.getModel( releasePackage );

		if ( model == null ) {
			ObjectNode managerError = jacksonMapper.createObjectNode();
			managerError.put( "error", "Unrecognized package name: " + releasePackage );
			managerError.set( "available", packagesWithCluster() );
			return managerError;
		}
		JsonNode results = model.getJsonModelDefinition();

		if ( path != null ) {
			results = results.at( path );
		}

		return results;
	}

	// @CsapDoc ( notes = "Get the release package definition stored at
	// $STAGING/{packageName}" , linkTests = {
	// "ChangeMe" } , linkGetParams = { "packageName=ChangeMe" } )
	// @RequestMapping ( "/package/{packageName}" )
	// public JsonNode applicationPackageDefinition (
	// @PathVariable ( "packageName" ) String packageName ) {
	//
	// if ( application.getModel( packageName ) != null ) {
	// return application.getModel( packageName ).getJsonModelDefinition();
	// } else {
	// ObjectNode error = jacksonMapper.createObjectNode();
	// error.put( "error", "packageName not found" );
	// error.set( "packages", jacksonMapper.convertValue(
	// application.getPackageNames(), ArrayNode.class ) );
	// return error;
	// }
	//
	// }
	@CsapDoc ( notes = "Gets the last load time of the cluster. Usefull for binding to application changes" )
	@RequestMapping ( "/application/latestReload" )
	public JsonNode applicationLatestReloadInfo () {
		return application.latestReloadInfo();
	}

	@CsapDoc ( notes = "Gets the application lifecycle, based on how the current host is configured in the application definition" )
	@RequestMapping ( "/host/lifecycle" )
	public JsonNode hostLifecycle () {
		return jacksonMapper.createObjectNode().put( "lifecycle", Application.getCurrentLifeCycle() );
	}

	@CsapDoc ( notes = "Gets all hosts in lifecycle" )
	@RequestMapping ( "/hosts" )
	public JsonNode hostsInLifecycle () {
		logger.debug( "csapApp lifecycle is {}", Application.getCurrentLifeCycle() );

		ReleasePackage allModel = application.getRootModel().getAllPackagesModel();

		return jacksonMapper.convertValue( allModel.getLifeCycleToHostInfoMap()
			.get( Application.getCurrentLifeCycle() ), ArrayNode.class );
	}

	@CsapDoc ( notes = "Gets  hosts for specified {cluster} in current lifecycle. eg. webServer-1 ." )
	@RequestMapping ( "/hosts/{cluster}" )
	public JsonNode hostsForCluster (
										@PathVariable ( "cluster" ) String cluster ) {

		return hostsForClusterInPackage( Application.ALL_PACKAGES, cluster );

	}

	@CsapDoc ( notes = "Gets hosts for specified {releasePackage}{cluster} in current lifecycle."
			+ "eg. webServer-1 (current lc), or dev-webServer-1, stage-webServer-1, etc."
			+ "For other lifecycles, prod-webServer-1, etc. To select all packages, pass\"{releasePackage}\"" )
	@RequestMapping ( "/hosts/{releasePackage}/{cluster}" )
	public JsonNode hostsForClusterInPackage (
												@PathVariable ( "releasePackage" ) String releasePackage,
												@PathVariable ( "cluster" ) String cluster ) {
		logger.debug( "csapApp lifecycle is {}", Application.getCurrentLifeCycle() );

		if ( releasePackage.equals( "{releasePackage}" ) ) {
			releasePackage = Application.ALL_PACKAGES;
		}
		ReleasePackage requestedModel = application.getModel( releasePackage );

		ObjectNode hostNode = jacksonMapper.createObjectNode();

		logger.debug( "keys: {}", requestedModel.getLcGroupVerToHostMap().keySet().toString() );

		String shortName = cluster.replaceAll( "-", "" );

		if ( shortName.equals( "{cluster}" ) ) {
			shortName = requestedModel.getLcGroupVerToHostMap().firstKey();
		}
		ArrayList<String> hosts = requestedModel.getLcGroupVerToHostMap().get( shortName );

		if ( hosts == null && !shortName.startsWith( Application.getCurrentLifeCycle() ) ) {
			shortName = Application.getCurrentLifeCycle() + shortName;
			hosts = requestedModel.getLcGroupVerToHostMap().get( shortName );
		}
		hostNode.put( "releasePackage", releasePackage );
		hostNode.put( "cluster", shortName );
		hostNode.putPOJO( "hosts", hosts );

		return hostNode;
	}

	@CsapDoc ( notes = "Gets hosts organized by cluster" )
	@RequestMapping ( "/clusters" )
	public JsonNode clusters () {
		return jacksonMapper.convertValue(
			application.getClustersForActiveLifecycle(),
			ObjectNode.class );
	}

	@CsapDoc ( notes = "Gets packages, by cluster and hosts" )
	@RequestMapping ( "/packages" )
	public ArrayNode packagesWithCluster () {

		List<ObjectNode> nodeList = application
			.getReleasePackageStream()
			.map( model -> {
				ObjectNode packageJson = jacksonMapper.createObjectNode();
				packageJson.put( "packageName", model.getReleasePackageName() );
				packageJson.put( "packageFile", model.getReleasePackageFileName() );
				packageJson.set( "clusters", application.getClusters( model ) );
				return packageJson;
			} )
			.collect( Collectors.toList() );

		return jacksonMapper.convertValue( nodeList, ArrayNode.class );
	}

	@CsapDoc ( notes = {
			"Gets the artifacts configured in application definition",
			"Optional: releasePackage - if not specified, all artifacts in all packages will be returned"
	} , linkTests = {
			"All Packages",
			"changeMe package",
	} , linkGetParams = {
			"params=none", "releasePackage=changeMe"
	} )
	@GetMapping ( value={"/artifacts", "/mavenArtifacts"} )
	public JsonNode artifacts ( String releasePackage ) {
		
		if ( releasePackage == null ) {
			releasePackage = application.getAllPackages().getReleasePackageName();
		}
		
		if ( application.getModel( releasePackage ) == null ) {
			ObjectNode managerError = jacksonMapper.createObjectNode();
			managerError.put( "error", "Unrecognized package name: " + releasePackage );
			managerError.set( "available", packagesWithCluster() );
			return managerError;
		}
		

		List<String> mavenIds = application.getModel( releasePackage )
			.getServiceConfigStreamInCurrentLC()
			.flatMap( serviceInstancesEntry -> serviceInstancesEntry.getValue().stream() )
			.map( serviceInstance -> serviceInstance.getMavenId() )
			.distinct()
			.filter( version -> version.length() != 0 )
			.collect( Collectors.toList() );

		return jacksonMapper.convertValue( mavenIds, ArrayNode.class );

	}

	@CsapDoc ( notes = "All hosts in all lifecycles, organized by lifecycle" )
	@RequestMapping ( "/hosts/allLifeCycles" )
	public JsonNode hostAllLifecycles () {

		ReleasePackage allModel = application.getRootModel().getAllPackagesModel();

		return jacksonMapper.convertValue( allModel.getLifeCycleToHostInfoMap(), ObjectNode.class );
	}

	@CsapDoc ( notes = "Nagios definition" )
	@RequestMapping ( value = "/nagios/definition" , produces = MediaType.TEXT_PLAIN_VALUE )
	public String nagiosDefinition ()
			throws IOException {

		Split mapTimer = SimonManager.getStopwatch( this.getClass().getSimpleName() + ".nagiosDefinition" ).start();

		String nagiosDefinition = NagiosIntegration.getNagiosDefinition( application.getReleasePackageStream() );

		mapTimer.stop();

		return nagiosDefinition;
	}

	@CsapDoc ( notes = "Get all services on host" )
	@RequestMapping ( value = "/services" )
	public ArrayNode services () {

		List<ServiceInstance> filterInstances = application.getActiveModel()
			.getServicesOnHost( Application.getHOST_NAME() )
			.collect( Collectors.toList() );

		return jacksonMapper.convertValue( filterInstances, ArrayNode.class );
	}

	@CsapDoc ( notes = "Get all services on host" )
	@RequestMapping ( value = "/supportEmail" )
	public ArrayNode supportEmail () {
		logger.debug( "Entered" );

		return application
			.lifeCycleSettings().getEmailJsonArray();
	}

	// @CsapDoc ( notes = "Metrics configuration for application" )
	// @RequestMapping ( value = "/metricsConfig" )
	// public ObjectNode metricsConfiguration () {
	// logger.debug( "Entered" );
	//
	// ObjectNode configNode = jacksonMapper.createObjectNode();
	//
	// configNode.set( "email", application.lifeCycleSettings()
	// .getEmailJsonArray() );
	//
	// configNode.put( "lbUrl", application.lifeCycleSettings().getLbUrl() );
	//
	// ObjectNode creds = configNode.putObject( "credentials" );
	// creds.put( "user", application.lifeCycleSettings().getEventUser() );
	// // creds.put( "pass",
	// // csapApp.getCurrentLifecycleMetaData().getEventPass() );
	// creds.put( "pass", "***MASKED***" );
	//
	// return configNode;
	// }
	@CsapDoc ( notes = "get service ids for services on hosts" )
	@RequestMapping ( value = "/service/ids" )
	public JsonNode serviceIds () {

		Map<String, List<String>> serviceMappings = application.getActiveModel()
			.getServicesOnHost( Application.getHOST_NAME() )
			.collect( Collectors.groupingBy( ServiceInstance::getServiceName,
				Collectors.mapping( ServiceInstance::getServiceName_Port,
					Collectors.toList() ) ) );

		return jacksonMapper.convertValue( serviceMappings, ObjectNode.class );

	}

	@CsapDoc ( notes = "get service url for services on current host" )
	@RequestMapping ( value = "/service/urls" )
	public ObjectNode serviceUrls () {

		Map<String, List<String>> serviceMappings = application.getActiveModel()
			.getServicesOnHost( Application.getHOST_NAME() )
			.collect( Collectors.groupingBy( ServiceInstance::getServiceName,
				Collectors.mapping( ServiceInstance::getUrl,
					Collectors.toList() ) ) );

		return jacksonMapper.convertValue( serviceMappings, ObjectNode.class );
	}

	@CsapDoc ( notes = "get service url for services on current host" )
	@RequestMapping ( value = "/service/http/ports" )
	public ObjectNode serviceHttpPorts () {

		Map<String, List<String>> serviceMappings = application.getActiveModel()
			.getServicesOnHost( Application.getHOST_NAME() )
			.collect( Collectors.groupingBy( ServiceInstance::getServiceName,
				Collectors.mapping( ServiceInstance::getPort,
					Collectors.toList() ) ) );

		return jacksonMapper.convertValue( serviceMappings, ObjectNode.class );
	}

	@CsapDoc ( notes = "get service url for services on current host" )
	@RequestMapping ( value = "/service/jmx/ports" )
	public ObjectNode serviceJmxPorts () {

		Map<String, List<String>> serviceMappings = application.getActiveModel()
			.getServicesOnHost( Application.getHOST_NAME() )
			.collect( Collectors.groupingBy( ServiceInstance::getServiceName,
				Collectors.mapping( ServiceInstance::getJmxPort,
					Collectors.toList() ) ) );

		return jacksonMapper.convertValue( serviceMappings, ObjectNode.class );
	}

	@CsapDoc ( notes = "get allservice urls for the specified service. Only valid on admins" )
	@RequestMapping ( value = "/service/urls/all/{serviceName}" )
	public JsonNode serviceUrlsForAllInstances (
													@PathVariable ( "serviceName" ) String serviceName ) {

		if ( !Application.isJvmInManagerMode() ) {
			return managerError;
		}

		if ( serviceName.equals( "{serviceName}" ) ) {
			return getAllServices();
		}

		List<String> urls = application
			.getServiceInstances( Application.ALL_PACKAGES, serviceName )
			.map( serviceInstance -> serviceInstance.getUrl() )
			.collect( Collectors.toList() );

		return jacksonMapper.convertValue( urls, ArrayNode.class );
	}

	private ObjectNode managerError;

	{
		managerError = jacksonMapper.createObjectNode();
		managerError.put( "error", "Only permitted on admin nodes" );

	}

	private ObjectNode getAllServices () {
		ObjectNode response = jacksonMapper.createObjectNode();

		response.put( "info", "add service name to url" );
		response.set( "availableServices", jacksonMapper.convertValue(
			application.getActiveModel().getAllPackagesModel().getServiceNamesInLifecycle(),
			ArrayNode.class ) );
		return response;
	}

	@CsapDoc ( notes = {
			"Service Definitions on host that match name specified regular expression filters", } , linkTests = {
					"CsAgent",
					"List"
			} , linkGetParams = { "serviceName=CsAgent" } )
	@RequestMapping ( "/services/byName/{serviceName:.+}" )
	public JsonNode serviceDefinitionsFilteredByName (
														@PathVariable ( "serviceName" ) String serviceName ) {

		if ( serviceName.equals( "{serviceName}" ) ) {
			return getAllServices();
		}

		List<ServiceInstance> filterInstances = application.getActiveModel()
			.getServicesOnHost( Application.getHOST_NAME() )
			.filter( serviceInstance -> serviceInstance.getServiceName().matches( serviceName ) )
			.collect( Collectors.toList() );

		return jacksonMapper.convertValue( filterInstances, ArrayNode.class );
	}

	@CsapDoc ( notes = {
			"Service Definitions on host that match regular expression context specified regular expression filters"
	} , linkTests = {
			"CsAgent",
			"List"
	} , linkGetParams = { "contextName=CsAgent" } )

	@RequestMapping ( "/services/byContext/{contextName:.+}" )
	public JsonNode serviceDefinitionsFilterdByContext (
															@PathVariable ( "contextName" ) String contextName ) {

		if ( contextName.equals( "{contextName}" ) ) {
			return getAllServices();
		}

		List<ServiceInstance> filterInstances = application.getActiveModel()
			.getServicesOnHost( Application.getHOST_NAME() )
			.filter( serviceInstance -> serviceInstance.getContext().matches( contextName ) )
			.collect( Collectors.toList() );

		return jacksonMapper.convertValue( filterInstances, ArrayNode.class );
	}

	@CsapDoc ( notes = "Gets all service definitions grouped by host" )
	@RequestMapping ( value = "/services/currentLifecycle" )
	public JsonNode serviceDefinitionsInLifecycleGroupedByHost () {
		ReleasePackage allModel = application.getRootModel().getAllPackagesModel();

		TreeMap<String, ArrayList<ServiceInstance>> hostToConfigMap = new TreeMap<String, ArrayList<ServiceInstance>>();

		allModel.getHostsCurrentLc()
			.forEach( host -> hostToConfigMap.put( host, allModel.getHostToConfigMap().get( host ) ) );

		return jacksonMapper.convertValue( hostToConfigMap, ObjectNode.class );
	}

	@CsapDoc ( notes = "Gets ports used by services" )
	@RequestMapping ( value = "/services/currentLifecycle/ports" )
	public JsonNode portsUsedByServices () {
		ReleasePackage allModel = application.getRootModel().getAllPackagesModel();

		TreeMap<String, ArrayList<Integer>> hostToConfigMap = new TreeMap<String, ArrayList<Integer>>();

		allModel.getHostsCurrentLc()
			.forEach( host -> {

				ArrayList<Integer> portList = new ArrayList<Integer>();
				allModel.getHostToConfigMap().get( host ).forEach( serviceInstance -> {
					try {
						int httpPort = Integer.parseInt( serviceInstance.getPort() );
						if ( httpPort > 0 ) {
							portList.add( httpPort );
						}
						int jmxPort = Integer.parseInt( serviceInstance.getJmxPort() );
						if ( jmxPort > 0 ) {
							portList.add( jmxPort );
						}
					} catch (NumberFormatException e) {
						logger.debug( "Failed to parse port for service: {}", serviceInstance.getServiceName(), e );
					}
				} );
				hostToConfigMap.put( host, portList );
			} );

		return jacksonMapper.convertValue( hostToConfigMap, ObjectNode.class );
	}

	@CsapDoc ( notes = "Service Definitions on all hosts that match name specified regular expression filters" , linkTests = { "CsAgent",
			"List" } , linkGetParams = { "serviceName=CsAgent" } )
	@RequestMapping ( value = { "/services/currentLifecycle/name/{serviceName}", "/services/currentLifecycle/jvm/{serviceName}" } )
	public JsonNode serviceDefinitionsInLifecycleFilteredByName (
																	@PathVariable ( "serviceName" ) String serviceName ) {

		if ( serviceName.equals( "{serviceName}" ) ) {
			return getAllServices();
		}

		return jacksonMapper.convertValue( application.getLifeCycleServicesByMatch( serviceName ), ArrayNode.class );
	}

	@CsapDoc ( notes = "Service Definitions on all hosts that matches regular expression context specified regular expression filters" , linkTests = {
			"CsAgent", "List" } , linkGetParams = { "serviceContext=CsAgent" } )
	@RequestMapping ( "/services/currentLifecycle/context/{serviceContext}" )
	public JsonNode serviceDefinitionsInLifecycleFilteredByContext (
																		@PathVariable ( "serviceContext" ) String serviceContext ) {

		logger.info( "got filter: {}", serviceContext );
		if ( serviceContext.equals( "{serviceContext}" ) ) {
			return getAllServices();
		}

		ArrayList<ServiceInstance> filterInstances = new ArrayList<ServiceInstance>();

		for ( String svcName : application.getSvcToConfigMap().keySet() ) {

			if ( !application.getSvcToConfigMap().get( svcName ).get( 0 ).getContext()
				.matches( serviceContext ) ) {
				continue;
			}

			for ( ServiceInstance instance : application.getSvcToConfigMap().get( svcName ) ) {

				if ( instance.getLifecycle().startsWith( Application.getCurrentLifeCycle() ) ) {
					filterInstances.add( instance );
				}
			}
		}

		return jacksonMapper.convertValue( filterInstances, ArrayNode.class );
	}

	//
	// @POST
	// @Path("/testExportTarget")
	// @Produces(MediaType.TEXT_PLAIN)
	// public String testExportTarget(@FormParam("CapabilityName") String
	// capabilityName,
	// @FormParam("Credential") String cred,
	// @FormParam("ClusterTimeStamp") String clusterTimeStamp,
	// @FormParam("jkmounts") String jkmounts, @FormParam("workers") String
	// workers) {
	//
	// // jamon monitors track both counts and timings - optional
	// String result = "Received definition from CS_AP instance: " +
	// capabilityName
	// + " TimeStamped:" + clusterTimeStamp + " credential:" + cred;
	//
	// logger.debug(result);
	//
	// return result;
	// }
	//
}
