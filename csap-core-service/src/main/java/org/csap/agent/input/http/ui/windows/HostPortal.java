package org.csap.agent.input.http.ui.windows;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;

import javax.inject.Inject;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.csap.agent.CsapCoreService;
import org.csap.agent.CSAP;
import org.csap.agent.input.http.ui.rest.ExplorerRequests;
import org.csap.agent.input.http.ui.rest.ServiceRequests;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.ActiveUsers;
import org.csap.agent.model.Application;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.services.OsManager;
import org.csap.agent.stats.JmxCommonEnum;
import org.csap.agent.stats.OsProcessEnum;
import org.csap.agent.stats.OsSharedEnum;
import org.csap.docs.CsapDoc;
import org.csap.integations.CsapInformation;
import org.csap.integations.CsapSecurityConfiguration;
import org.csap.security.CsapUser;
import org.csap.security.CustomUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Controller
@RequestMapping(CsapCoreService.OS_URL)
@CsapDoc(title = "Host and Perfomance Portals; including script execution",
		notes = {"Update, Reload and similar operations to manage the running application",
			"<a class='pushButton' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
			"<img class='csapDocImage' src='CSAP_BASE/images/csapboot.png' />"
			+ "<img class='csapDocImage' src='CSAP_BASE/images/portals.png' />"})
public class HostPortal {

	final Logger logger = LoggerFactory.getLogger( CorePortals.class );

	private static final String _METRICS = "_METRICS_";

	@Inject
	public HostPortal(Application csapApp, OsManager osManager, CsapEventClient csapEventClient) {

		this.csapApp = csapApp;
		this.osManager = osManager;
		this.csapEventClient = csapEventClient;
	}

	Application csapApp;
	OsManager osManager;

	@Autowired(required = false)
	CsapSecurityConfiguration csapSecurityConfiguration;

	CsapEventClient csapEventClient;
	// Circular dependencies arise with constructor injection - b
	@Inject
	ServiceRequests serviceController;

	ObjectMapper jacksonMapper = new ObjectMapper();

	@Value("${user.name:dummy}")
	private String CSAP_USER = "willBeReplacedOnSpringInit";

	final static String COMMAND_URL = "command";
	public final static String COMMAND_SCREEN_URL = CsapCoreService.OS_URL + "/" + COMMAND_URL;

	@RequestMapping(COMMAND_URL)
	public ModelAndView osCommandExecutor(
			ModelMap modelMap, HttpSession session,
			@RequestParam(value = "fromFolder", required = false) String fromFolder,
			@RequestParam(value = "location", required = false) String location,
			@RequestParam(value = "extractDir", required = false) String extractDir,
			@RequestParam(value = "searchText", required = false, defaultValue = "") String searchText,
			@RequestParam(value = "pid", required = false, defaultValue = "") String pid,
			@RequestParam(value = "template", required = false, defaultValue = "") String template,
			@RequestParam(value = "serviceName", required = false, defaultValue = "") String serviceName_port,
			@RequestParam(value = "", required = false, defaultValue = "") String contents,
			@RequestParam("command") String command,
			@RequestParam(value = CSAP.SERVICE_PORT_PARAM, required = false) String svcName)
			throws IOException {

		if ( fromFolder == null ) {
			fromFolder = Application.FileToken.HOME.value;
		}

		// logger.info("Gpt here");
		setCommonAttributes( modelMap, session, "OS Commands" );

		File targetFile = csapApp.getRequestedFile( fromFolder, svcName, false );

		String defaultLocation = targetFile.getAbsolutePath();

		// modelMap.addAttribute("location", targetFile.getCanonicalPath());
		modelMap.addAttribute( "location", targetFile.toURI().getPath() );
		modelMap.addAttribute( "command", command );
		String title = "";
		switch ( command ) {
			case "logSearch":
				title = Application.getHOST_NAME() + " Search";
				break;

			default:
				title = Application.getHOST_NAME() + " " + command;
				break;

		}
		modelMap.addAttribute( "title", title );
		modelMap.addAttribute( "csapUser", CSAP_USER );

		modelMap.addAttribute( "clusterHostsMap",
				jacksonMapper.convertValue( csapApp.getClustersForActiveLifecycle(), ObjectNode.class ) );

		modelMap.addAttribute( "allHosts",
				jacksonMapper.convertValue( csapApp.getAllHostsInAllPackagesInCurrentLifecycle(), ArrayNode.class ) );

		if ( !serviceName_port.equals( "null" ) && serviceName_port.length() > 0 ) {

			try {
				ServiceInstance serviceOnHost = csapApp.getServiceInstanceCurrentHost( svcName );
				logger.info( "serviceOnHost: {}", svcName  );
				modelMap.addAttribute( "serviceHosts",
						jacksonMapper.convertValue(
								csapApp.getActiveModel().findHostsForService( serviceOnHost.getServiceName() ),
								ArrayNode.class ) );
			} catch ( Exception e ) {
				logger.warn( "Failed to find service hosts {}", CSAP.getCsapFilteredStackTrace( e ) );
			}
		}

		File scriptFolder = new File( csapApp.getDefinitionFolder(), "scripts" );

		String[] scriptNames = scriptFolder.list( 
				( File dir, String name ) -> 
						name.toLowerCase().endsWith( ".sh" ) 
								|| name.toLowerCase().endsWith( ".ksh" ) );

		if ( scriptNames == null ) {
			scriptNames = new String[0];
		}
		modelMap.addAttribute( "projectScripts", scriptNames );

		if ( command.equals( "script" )
				&& (targetFile.getName().endsWith( ".sh" ) || targetFile.getName().endsWith( ".ksh" )) ) {
			logger.info( "loading: {}", targetFile.getAbsolutePath() );
			contents = FileUtils.readFileToString( targetFile );
		}
		modelMap.addAttribute( "contents", contents );

		File[] searchFiles = new File[0];
		String searchFolder = fromFolder;
		if ( command.equals( "logSearch" ) ) {
			//  file listing for ui selection

			File searchDir = csapApp.getLogDir( svcName );
			if ( searchDir.isDirectory() ) {
				searchFiles = searchDir.listFiles();
			}
			Arrays.sort( searchFiles );
			if ( searchFolder != null && searchFolder.equals( "defaultLog" ) ) {
				searchFolder = csapApp.getDefaultLogFile( svcName );
				// Arrays.sort(searchFiles, comparingLong(File::lastModified));
			}
		}

		modelMap.addAttribute( "searchFolder", searchFolder );
		modelMap.addAttribute( "searchFiles", searchFiles );
		if ( searchText != null ) {

			String searchTextJava = searchText.replaceAll( "\r\n.*", " " );
			searchTextJava = searchTextJava.replaceAll( "\n.*", " " );
			searchText = searchTextJava;
		}
		modelMap.addAttribute( "searchText", searchText );

		modelMap.addAttribute( "pid", pid );

		modelMap.addAttribute( "serviceName", serviceName_port );

		modelMap.addAttribute( "template", template );

		// logger.info( " location: {}", location );
		if ( location != null ) {
			defaultLocation = location;
			if ( Application.isRunningOnDesktop() ) {
				defaultLocation.replaceAll( StringEscapeUtils.escapeJava( "\\" ),
						StringEscapeUtils.escapeJava( "\\\\" ) );
			}
		}
		if ( extractDir != null ) {
			defaultLocation = extractDir;
		}

		modelMap.addAttribute( "defaultLocation", defaultLocation );

		modelMap.addAttribute( "osUsers", buildOsUsersList( CSAP_USER ) );

		//ArrayList<String> clusters = new ArrayList<String>();
//		for ( String clusterInLc : csapApp.getLifecycleList() ) {
//
//			if ( !clusterInLc.startsWith( Application.getCurrentLifeCycle() ) ) {
//				continue;
//			}
//
//			ArrayList<String> lifeCycleHostList = csapApp.getLifeCycleToHostMap().get( clusterInLc );
//
//			// System.out.println("clusterInLc: " + clusterInLc) ;
//			if ( lifeCycleHostList == null ) {
//				continue;
//			}
//
//			clusters.add( clusterInLc );
//
//		}
		modelMap.addAttribute( "clusters", csapApp.getClustersForActivePackage().keySet() );

		return new ModelAndView( CsapCoreService.OS_URL + "/command-body" );
	}

	public static ArrayList<String> buildOsUsersList(String csapUser) {
		ArrayList<String> osUsers = new ArrayList<String>();

		if ( Application.isRunningOnDesktop() ) {

			osUsers.add( "ssadmin" );
			osUsers.add( "root" );
			//osUsers.add( System.getProperty( "user.name" ) );
		} else if ( Application.isRunningAsRoot() ) {

			osUsers.add( "root" );
			File homeDir = new File( "/home" );

			if ( homeDir.exists() && homeDir.isDirectory() ) {
				for ( File userHome : homeDir.listFiles() ) {

					osUsers.add( userHome.getName() );
				}
			}
		} else {
			osUsers.add( csapUser );
		}
		return osUsers;
	}

	@RequestMapping("performance")
	public ModelAndView performancePortal(ModelMap modelMap, HttpSession session,
			@RequestParam(value = "serviceName", required = false) String serviceNamePort,
			@RequestParam(value = "project", required = false) String project,
			@RequestParam(value = "life", required = false) String life,
			@RequestParam(value = CSAP.HOST_PARAM, required = false) String hostName)
			throws IOException {

		setCommonAttributes( modelMap, session, "Analytics Portal" );

		modelMap.put( "project", project );
		modelMap.put( "life", life );

		modelMap.addAttribute( "metricLabels", buildMetricLabels()  );

		return new ModelAndView( "performance/perf-main" );
	}

	private ObjectNode buildMetricLabels() {
		ObjectNode labelMapping = JmxCommonEnum.graphLabels();
		labelMapping.setAll( OsSharedEnum.graphLabels() );
		labelMapping.setAll( OsProcessEnum.graphLabels() );
		return labelMapping;
	}

	@RequestMapping("shared")
	public ModelAndView vmScreen(ModelMap modelMap, HttpSession session)
			throws IOException {

		modelMap.addAttribute( "name", "Host Shared Resource Graphs" );

		setCommonAttributes( modelMap, session, "Host Graphs" );

		return new ModelAndView( "/graphs/host" );
	}

	@RequestMapping("process")
	public ModelAndView processScreen(ModelMap modelMap, HttpSession session)
			throws IOException {

		modelMap.addAttribute( "name", "OS Process Graphs" );
		setCommonAttributes( modelMap, session, "OS Process Graphs" );

		return new ModelAndView( "/graphs/process" );
	}

	@RequestMapping(value = {"java", "jmx"})
	public ModelAndView jmxScreen(
			@RequestParam(value = "service", required = false, defaultValue = "none") String serviceName,
			ModelMap modelMap, HttpSession session)
			throws IOException {

		modelMap.addAttribute( "name", "Java Graphs" );
		// string off port if it is specified
		if ( serviceName.indexOf( "_" ) != -1 ) {
			serviceName = serviceName.substring( 0, serviceName.indexOf( "_" ) );
		}
		modelMap.addAttribute( "csapPageLabel", " Java Performance for top services" );
		if ( !serviceName.equals( "none" ) ) {
			modelMap.addAttribute( "csapPageLabel", "Java Performance for service: " + serviceName );
		}
		setCommonAttributes( modelMap, session, "Java Graphs" );

		return new ModelAndView( "/graphs/java" );
	}

	@RequestMapping("application")
	public ModelAndView application(
			@RequestParam(value = "service") String serviceName,
			ModelMap modelMap, HttpSession session)
			throws IOException {

		modelMap.addAttribute( "name", "Application Graphs" );

		// string off port if it is specified
		if ( serviceName.indexOf( "_" ) != -1 ) {
			serviceName = serviceName.substring( 0, serviceName.indexOf( "_" ) );
		}

		modelMap.addAttribute( "csapPageLabel", "Application Performance for service: " + serviceName );
		modelMap.addAttribute( "serviceName", serviceName );
		setCommonAttributes( modelMap, session, "Application Graphs" );

		return new ModelAndView( "/graphs/application" );
	}

	
	@RequestMapping("HostDashboard")
	public ModelAndView hostDashboard(
			@RequestParam(value = "emptyCache", required = false) String emptyCache,
			@RequestParam(value = "clearStats", required = false) String clearStats,
			ModelMap modelMap, HttpSession session) {

		setCommonAttributes( modelMap, session, "Host Dashboard" );
		
		if ( emptyCache != null ) {
			osManager.resetAllCaches();
		}

		if ( clearStats != null ) {
			csapApp.clearResourceStats();
		}

		modelMap.addAttribute( "explorerUrl", ExplorerRequests.EXPLORER_URL ) ;
		modelMap.addAttribute( "dockerUrl", agentConfig.getDocker().getUrl() ) ;
		modelMap.addAttribute( "dockerRepository", agentConfig.getDocker().getTemplateRepository() ) ;
		return new ModelAndView( "/os/dashboard" );
	}
	
	@RequestMapping("systemctl")
	public ModelAndView systemctl(
			ModelMap modelMap, HttpSession session) {

		setCommonAttributes( modelMap, session, "systemctl dashboard" );
		modelMap.addAttribute( "csapPageLabel", " OS systemctl status" );
		logger.debug( " Entered" );
 

		String commandOutput = osManager.systemStatus() ;
//		
		StringBuilder html = new StringBuilder() ;
		Arrays.stream( commandOutput.split( "\n" ) )
			.forEach( line -> {
				if ( line.endsWith( ".service" )) {

					html.append( "<span class='service'>" ) ;
					html.append( line ) ;
					html.append( "</span>" ) ;
				} else {
					html.append( line ) ;
				}
				html.append( "<br/>" ) ;
			});
		

		modelMap.addAttribute( "commandOutput", html ) ;
		
//		return html.toString() ;
		return new ModelAndView( "/os/systemCtl" );
	}
	
	@Autowired
	CsapCoreService agentConfig ;

	@Autowired
	CsapInformation csapInformation;

	private void setCommonAttributes(ModelMap modelMap, HttpSession session, String windowName) {


		modelMap.addAttribute( "toolsServer", csapApp.lifeCycleSettings().getToolsServer() );
		modelMap.addAttribute( "host", Application.getHOST_NAME() );
		modelMap.addAttribute( "version", csapInformation.getVersion() );

		modelMap.addAttribute( "dateTime",
				LocalDateTime.now().format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) );

		modelMap.addAttribute( "userid", CsapUser.currentUsersID() );

		modelMap.addAttribute( "agentHostUrlPattern", csapApp.getAgentHostUrlPattern( false ) );
		
		modelMap.addAttribute( "deskTop", Application.isRunningOnDesktop()
				&& !Application.isJvmInManagerMode() );

		modelMap.addAttribute( "adminRole", false );
		if ( csapSecurityConfiguration.getAndStoreUserRoles( session )
				.contains( csapSecurityConfiguration.ADMIN_ROLE ) ) {
			modelMap.addAttribute( "adminRole", true );
		}

		modelMap.addAttribute( "adminGroup", csapSecurityConfiguration.getAdminGroup() );

		session.setAttribute( CSAP.ROLES, csapSecurityConfiguration.getAndStoreUserRoles( session ) );

		if ( session.getAttribute( _METRICS + windowName ) == null ) {
			session.setAttribute( _METRICS + windowName, "AccessLogged" );
			// auditRecord("metricsBrowser", "UI Launched");
			csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/accessed", CsapUser.currentUsersID(),
					"User interface: " + windowName, "" );
			activeUsers.updateUserAccessAndReturnAllActive( CsapUser.currentUsersID(), false ) ;
		}
		modelMap.addAttribute( "csapApp", csapApp );

		modelMap.addAttribute( csapApp.lifeCycleSettings() );
		modelMap.addAttribute( "analyticsUrl", csapApp.lifeCycleSettings().getAnalyticsUiUrl() );
		modelMap.addAttribute( "eventApiUrl", csapApp.lifeCycleSettings().getEventApiUrl() );

		modelMap.addAttribute( "eventApiUrl", csapApp.lifeCycleSettings().getEventApiUrl() );

		modelMap.addAttribute( "eventMetricsUrl",
				csapApp.lifeCycleSettings().getEventMetricsUrl() );
		modelMap.addAttribute( "eventUser", csapApp.lifeCycleSettings().getEventDataUser() );
		modelMap.addAttribute( "life", Application.getCurrentLifeCycle() );

		modelMap.addAttribute( "agentUser", csapApp.getAgentRunUser());
	}
	

	@Inject 
	ActiveUsers activeUsers;

}
