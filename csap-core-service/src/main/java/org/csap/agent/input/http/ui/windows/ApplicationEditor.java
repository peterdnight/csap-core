/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.input.http.ui.windows;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.csap.agent.CsapCoreService;
import org.csap.agent.model.Application;
import org.csap.agent.model.ClusterPartition;
import org.csap.agent.model.DefinitionParser;
import org.csap.agent.model.LifeCycleSettings;
import org.csap.agent.model.ReleasePackage;
import org.csap.agent.model.ServiceAlertsEnum;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.stats.JmxCommonEnum;
import org.csap.agent.stats.OsProcessEnum;
import org.csap.agent.stats.OsSharedEnum;
import org.csap.docs.CsapDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 *
 * @author someDeveloper
 */
@Controller
@RequestMapping(value = CsapCoreService.EDIT_URL)
@CsapDoc(title = "CSAP Application Editor", notes = {
	"CSAP Application Editor provides ability to update/modify application definition, including "
	+ "viewing/reviewing current configuration, changing service parameters, and more",
	"<a class='pushButton' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
	"<img class='csapDocImage' src='CSAP_BASE/images/csapboot.png' />"
	+ "<img class='csapDocImage' src='CSAP_BASE/images/portals.png' />"})
public class ApplicationEditor {

	final Logger logger = LoggerFactory.getLogger( getClass() );

	@Autowired
	CorePortals corePortals;

	@Autowired
	Application csapApp;

	//
	// Editor Related functions
	//
	@RequestMapping("/application")
	public String applicationEditor(
			@RequestParam(value = "releasePackage", required = false) String releasePackage,
			ModelMap modelMap, HttpServletRequest request,
			HttpSession session) throws IOException {

		logger.debug( " Entered" );

		corePortals.setCommonAttributes( modelMap, session );

		File definitionSource = new File( csapApp.getSourceLocation() );
		modelMap.addAttribute( "definitionName", definitionSource.getName() );

		corePortals.addSelectedReleasePackage( modelMap, releasePackage );
		if ( modelMap.get( "selectedRelease" ).equals( Application.ALL_PACKAGES ) ) {
			modelMap.addAttribute( "selectedRelease", csapApp.getActiveModelName() );
		}

		if ( !Application.getCurrentLifeCycle().equals( "dev" )
				&& (csapApp.getLifecycleList().contains( "dev" )) ) {
			modelMap.addAttribute( "showCheckinWarning", "showCheckinWarning" );
		}

		return "/editor/_main";
	}

	ObjectMapper jacksonMapper = new ObjectMapper();

	@RequestMapping("/clusterDialog")
	public String clusterDialog(
			@RequestParam(value = "clusterName", required = false) String clusterName,
			@RequestParam(value = "lifeToEdit", required = false) String lifeToEdit,
			@RequestParam(value = "releasePackage", required = false) String releasePackage,
			ModelMap modelMap, HttpServletRequest request,
			HttpSession session) throws IOException {

		if ( clusterName == null ) {
			lifeToEdit = Application.getCurrentLifeCycle();
		}

		if ( lifeToEdit == null ) {
			lifeToEdit = Application.getCurrentLifeCycle();
		}

		modelMap.addAttribute( "lifeToEdit", lifeToEdit );
		modelMap.addAttribute( "clusterEntries", ClusterPartition.clusterEntries() );

		ReleasePackage requestedModel = null;
		if ( releasePackage == null ) {
			requestedModel = csapApp.getActiveModel();
		} else {
			requestedModel = csapApp.getModel( releasePackage );
		}

		modelMap.addAttribute( requestedModel );

		logger.info( "clusterName: {}, lifeToEdit: {}, releasePackage: {}",
				clusterName, lifeToEdit, requestedModel.getReleasePackageName() );

		ObjectNode lifecycleJson = (ObjectNode) requestedModel.getJsonModelDefinition()
				.at( DefinitionParser.buildLifePtr( lifeToEdit ) );

		ArrayList<String> clusterNames = new ArrayList<>();
		lifecycleJson.fieldNames().forEachRemaining( name -> {
			if ( !name.equals( "settings" ) ) {
				clusterNames.add( name );
			}
		} );
		// modelMap.addAttribute( "clusterNames",
		// requestedModel.getLifeCycleToGroupMap().get( lifeToEdit ) );
		modelMap.addAttribute( "clusterNames", clusterNames );

		// modelMap.addAttribute( "servicesInPackage", corePortals.getServices(
		// requestedModel ) );
		try {
			addJeeSeviceAttributes( modelMap, requestedModel, lifeToEdit );
			modelMap.addAttribute( "osServices", corePortals.osServices( requestedModel ) );
			modelMap.addAttribute( "hosts", requestedModel.getLifeCycleToHostMap().get( lifeToEdit ) );

		} catch ( Exception e ) {
			logger.error( "Failed configuring dialog", e );
		}

		corePortals.setCommonAttributes( modelMap, session );

		return "/editor/dialog-cluster";
	}

	private void addJeeSeviceAttributes(ModelMap modelMap, ReleasePackage appPackage, String lifeToEdit) {

		JsonNode lifeJson = appPackage.getJsonModelDefinition().at( DefinitionParser.buildLifePtr( lifeToEdit ) );
		List<JsonNode> javaServiceNodes = lifeJson.findValues( DefinitionParser.CLUSTER_JAVA_SERVICES );

		Map<String, String> jeePortMap = corePortals.jeeServices( appPackage ).stream()
				//	.filter( serviceName -> !serviceName.equals( Application.AGENT_ID ) )
				.collect( Collectors.toMap(
						serviceName -> serviceName,
						serviceName -> getHttpPortFromCurrentClusters( serviceName, javaServiceNodes ) ) );

		AtomicInteger startPort = new AtomicInteger( csapApp.lifeCycleSettings().getPortStart() + 10 );

		jeePortMap.entrySet().stream()
				.filter( jeeport -> jeeport.getValue().length() == 0 ) // we set unknown services to ""
				.forEach( entry -> {
					String nextAvailable = getNextAvailableHttpPort( javaServiceNodes, startPort );
					entry.setValue( nextAvailable );
					// we need new values.
				} );

		modelMap.addAttribute( "jeeServices", jeePortMap );

		// Get the next 20 free ports for manual assignment
		List<String> jeeFreePorts = IntStream
				.iterate( startPort.get(), i -> i + 1 ).limit( 20 )
				.mapToObj( portNumber -> {
					return getNextAvailableHttpPort( javaServiceNodes, startPort );
				} )
				.collect( Collectors.toList() );

		modelMap.addAttribute( "jeeFreePorts", jeeFreePorts );

	}

	private String getHttpPortFromCurrentClusters(String serviceName, List<JsonNode> javaServiceNodes) {

		String portAssigned = javaServiceNodes.stream()
				.filter( javaNode -> javaNode.has( serviceName ) )
				.map( javaNode -> javaNode.get( serviceName ) )
				.filter( JsonNode::isArray )
				.map( arrayNode -> arrayNode.get( 0 ).asText() )
				.findFirst()
				.orElse( "" );

		return portAssigned;
	}

	private String getNextAvailableHttpPort(List<JsonNode> javaServiceNodes, AtomicInteger startPort) {

		logger.debug( " Starting at: {}", startPort.get() );
		int pstart = startPort.get() + 10;

		String nextAvailable = IntStream
				.iterate( pstart, i -> i + 10 ).limit( 500 )
				.mapToObj( portNum -> {
					logger.debug( " Setting at: {}", portNum );
					startPort.set( portNum );
					String testPort = Integer.toString( portNum );
					testPort = testPort.substring( 0, testPort.length() - 1 ) + "x";
					return testPort;
				} )
				.filter( portAsString -> isPortAvailable( portAsString, javaServiceNodes ) )
				.findFirst()
				.orElse( "000x" );

		return nextAvailable;
	}

	public boolean isPortAvailable(String testPort, List<JsonNode> javaServiceNodes) {

		Optional<JsonNode> matchingNodes = javaServiceNodes.stream()
				.filter( JsonNode::isObject )
				.filter( isPortInCluster( testPort ) )
				.findFirst();

		return !matchingNodes.isPresent();
	}

	private Predicate<? super JsonNode> isPortInCluster(String testPort) {
		return javaNode -> {
			logger.debug( "Checking: port: {} in {}", testPort, javaNode );
			Iterator<JsonNode> portIterator = javaNode.elements();
			boolean isPortFound = false;
			while (portIterator.hasNext() && !isPortFound) {
				ArrayNode portArray = (ArrayNode) portIterator.next();
				for ( int i = 0; i < portArray.size(); i++ ) {
					if ( portArray.get( i ).asText().equals( testPort ) ) {
						isPortFound = true;
						break;
					}
				}
			}
			return isPortFound;
		};
	}

	@RequestMapping("/serviceDialog")
	public String serviceDialog(
			@RequestParam("serviceName") String serviceName,
			@RequestParam("hostName") String hostName,
			ModelMap modelMap, HttpServletRequest request,
			@RequestParam(value = "newService", required = false) String newService,
			@RequestParam(value = "releasePackage", required = false) String releasePackage,
			HttpSession session) throws IOException {

		logger.debug( "service: {}", serviceName );

		corePortals.setCommonAttributes( modelMap, session );

		ServiceInstance serviceDefinition = csapApp.findFirstServiceInstanceInLifecycle( serviceName );

		if ( serviceDefinition == null ) {
			modelMap.addAttribute( "unused", "unused" );
		}

		modelMap.addAttribute( "tomcatServers", ServiceInstance.getServertypes() );

		Map<String, String> limits = Arrays.stream( ServiceAlertsEnum.values() )
				.collect( Collectors.toMap(
						alert -> alert.value,
						alert -> {
							if ( serviceDefinition == null ) {
								return "-";
							}
							return ServiceAlertsEnum.getMaxAllowedSummary(
									serviceDefinition,
									csapApp.lifeCycleSettings(),
									alert );
						} ) );

		if ( serviceDefinition != null ) {
			limits.put( LifeCycleSettings.JMX_HEARTBEAT,
					Boolean.toString( ServiceAlertsEnum.isJavaHeartbeatEnabled( serviceDefinition, csapApp.lifeCycleSettings() ) )
			);
		}

//		limits.put( LifeCycleSettings.JMX_HEARTBEAT, "cluster default" );
//		if ( serviceDefinition != null && serviceDefinition.getMonitors() != null
//				&& serviceDefinition.getMonitors().has( LifeCycleSettings.JMX_HEARTBEAT ) ) {
//			limits.put( LifeCycleSettings.JMX_HEARTBEAT,
//					serviceDefinition.getMonitors().get( LifeCycleSettings.JMX_HEARTBEAT ).asText() );
//		}
		modelMap.addAttribute( "limits", limits );

		ReleasePackage serviceModel = null;
		if ( releasePackage == null ) {
			serviceModel = csapApp.getModel( hostName, serviceName );
		} else {
			serviceModel = csapApp.getModel( releasePackage );
		}

		modelMap.addAttribute( "servicesInPackage", corePortals.getServices( serviceModel ) );
		modelMap.addAttribute( "servicePackage", serviceModel.getReleasePackageName() );

		return "/editor/dialog-service";
	}

	@RequestMapping("/settingsDialog")
	public String settingsDialog(
			@RequestParam(value = "lifeToEdit", required = false) String lifeToEdit,
			@RequestParam(value = "releasePackage", required = false) String releasePackage,
			ModelMap modelMap, HttpServletRequest request,
			HttpSession session) throws IOException {

		logger.info( "lifeToEdit: {}", lifeToEdit );

		if ( lifeToEdit == null ) {
			lifeToEdit = Application.getCurrentLifeCycle();
		}

		modelMap.addAttribute( "lifeToEdit", lifeToEdit );

		if ( releasePackage == null ) {
			releasePackage = csapApp.getActiveModelName();
		}

		ReleasePackage model = csapApp.getModel( releasePackage );
		modelMap.addAttribute( model );
		corePortals.setCommonAttributes( modelMap, session );

		return "/editor/dialog-settings";
	}
	

	@RequestMapping(value = "/lifecycle", method = RequestMethod.GET)
	public String lifecycle(
			@RequestParam(value = "lifeToEdit", required = false) String lifeToEdit,
			@RequestParam(value = "releasePackage", required = false) String releasePackage,
			ModelMap modelMap, HttpServletRequest request,
			HttpSession session) throws IOException {

		logger.debug( "lifeToEdit: {}", lifeToEdit );

		if ( lifeToEdit == null ) {
			lifeToEdit = Application.getCurrentLifeCycle();
		}

		corePortals.setCommonAttributes( modelMap, session );

		modelMap.addAttribute( "lifeToEdit", lifeToEdit );

		if ( releasePackage == null ) {
			releasePackage = csapApp.getActiveModelName();
		}

		ReleasePackage model = csapApp.getModel( releasePackage );
		modelMap.addAttribute( model );

		JsonNode lifecycleJsonTest = model.getJsonModelDefinition()
				.at( DefinitionParser.buildLifePtr( lifeToEdit ) );

		ArrayList<String> packageLifes = new ArrayList<>();
		model.getJsonModelDefinition()
				.at( DefinitionParser.buildClusterPtr() )
				.fieldNames()
				.forEachRemaining( lifeName -> packageLifes.add( lifeName ) );

		modelMap.addAttribute( "packageLifes", packageLifes );

		if ( lifecycleJsonTest.isMissingNode() ) {
			ArrayList<String> clusterNames = new ArrayList<>();
			modelMap.addAttribute( "clusterNames", clusterNames );
			return "/editor/tab-lifecycle";
		}

		ObjectNode lifecycleJson = (ObjectNode) lifecycleJsonTest;

		ArrayList<String> unusedServices = corePortals.getServices( model );
		ArrayList<String> clusterNames = new ArrayList<>();
		HashMap<String, String> clusterDescriptionMap = new HashMap<>();
		HashMap<String, String> clusterNotesMap = new HashMap<>();
		HashMap<String, String> clusterDisplayMap = new HashMap<>();
		HashMap<String, ArrayList<String>> hostsMap = new HashMap<>();
		HashMap<String, ArrayList<String>> servicesMap = new HashMap<>();
		lifecycleJson.fieldNames().forEachRemaining( clusterName -> {
			if ( !clusterName.equals( "settings" ) ) {
				clusterNames.add( clusterName );
				ObjectNode clusterJson = (ObjectNode) lifecycleJson.get( clusterName );

				if ( clusterJson.has( "display" ) ) {
					clusterDisplayMap.put( clusterName, clusterJson.get( "display" ).asText() );
				} else {
					clusterDisplayMap.put( clusterName, "normal" );
				}

				if ( clusterJson.has( "notes" ) ) {
					clusterNotesMap.put( clusterName, clusterJson.get( "notes" ).asText() );
				} else {
					clusterNotesMap.put( clusterName, "" );
				}
				ClusterPartition clusterPartition = ClusterPartition.getPartitionType( clusterJson ) ;
				String description = clusterPartition.getDescription() ;
				if ( clusterJson.has( "version" ) ) description += " *** EOL ***" ;
				clusterDescriptionMap.put( 
					clusterName, 
					description
				) ;

				ArrayList<String> hostList = new ArrayList<>();
				clusterJson.findValues( "hosts" ).forEach( hostNodeJson -> {
					ArrayNode hostsNode = (ArrayNode) hostNodeJson;
					hostsNode.forEach( itemJson -> hostList.add( itemJson.asText() ) );
				} );
				Collections.sort( hostList, String.CASE_INSENSITIVE_ORDER );
				hostsMap.put( clusterName, hostList );

				ArrayList<String> serviceList = new ArrayList<>();
				JsonNode osNode = clusterJson.get( "osProcessesList" );
				if ( osNode != null ) {
					osNode.forEach( itemJson -> serviceList.add( itemJson.asText() ) );
				}

				JsonNode jvmsNode = clusterJson.get( "jvmPorts" );

				if ( jvmsNode != null ) {
					jvmsNode.fieldNames().forEachRemaining( jvmName -> serviceList.add( jvmName ) );
				}
				Collections.sort( serviceList, String.CASE_INSENSITIVE_ORDER );

				serviceList.forEach( service -> {
					if ( unusedServices.contains( service ) ) {
						unusedServices.remove( service );
					}
				} );

				servicesMap.put( clusterName, serviceList );
			}
		} );

		Collections.sort( unusedServices, String.CASE_INSENSITIVE_ORDER );
		modelMap.addAttribute( "unusedServices", unusedServices );

		Collections.sort( clusterNames, String.CASE_INSENSITIVE_ORDER );
		modelMap.addAttribute( "clusterNames", clusterNames );
		modelMap.addAttribute( "clusterDisplayMap", clusterDisplayMap );
		modelMap.addAttribute( "clusterNotesMap", clusterNotesMap );
		modelMap.addAttribute( "clusterDescriptionMap", clusterDescriptionMap );
		modelMap.addAttribute( "hostsMap", hostsMap );
		modelMap.addAttribute( "servicesMap", servicesMap );

		return "/editor/tab-lifecycle";
	}

	@RequestMapping(CsapCoreService.SUMMARY_URL)
	public String applicationSummaryReport(
			@RequestParam(value = "emptyCache", required = false) String emptyCache,
			@RequestParam(value = "releasePackage", required = false) String releasePackage,
			ModelMap modelMap, HttpSession session) {

		if ( logger.isDebugEnabled() ) {
			logger.debug( "Updating Cache to reload cluster if it has changed" );
		}
		if ( releasePackage == null ) {
			releasePackage = csapApp.getActiveModelName();
		}
		ReleasePackage model = csapApp.getModel( releasePackage );
		modelMap.addAttribute( model );

		csapApp.updateCache( false );

		corePortals.setCommonAttributes( modelMap, session );

		return "editor/summary-body";
	}

}
