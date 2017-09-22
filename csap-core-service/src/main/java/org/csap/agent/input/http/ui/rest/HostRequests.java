package org.csap.agent.input.http.ui.rest;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.FileUtils;
import org.csap.agent.CsapCoreService;
import org.csap.agent.CSAP;
import org.csap.agent.input.http.api.AgentApi;
import org.csap.agent.linux.OsCommandRunner;
import org.csap.agent.linux.OutputFileMgr;
import org.csap.agent.linux.TransferManager;
import org.csap.agent.linux.ZipUtility;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.Application;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.services.OsManager;
import org.csap.agent.services.ServiceOsManager;
import org.csap.agent.stats.HostCollector;
import org.csap.agent.stats.OsSharedResourcesCollector;
import org.csap.docs.CsapDoc;
import org.csap.integations.CsapSecurityConfiguration;
import org.csap.security.CsapUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ui.ModelMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *
 * HostRequests is a container for MVC actions targeting OS metric commands.
 * Note that it has more permissive timeouts for command execution to allow for
 * long running operations
 *
 * @author someDeveloper
 *
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/current/spring-framework-reference/html/mvc.html">
 *      SpringMvc Docs </a>
 *
 * @see SpringContext_agentSvcServlet
 *
 */
@RestController
@RequestMapping ( CsapCoreService.OS_URL )
@CsapDoc ( title = "Host Operations" , notes = { "Comprehensive set of OS commands, including ps, top, file transfer, and many others",
		"<a class='pushButton' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/portals.png' />" } )
public class HostRequests {

	final Logger logger = LoggerFactory.getLogger( this.getClass() );

	@Inject
	public HostRequests( Application csapApp, OsManager osManager,
			CsapEventClient csapEventClient ) {

		this.csapApp = csapApp;
		this.osManager = osManager;
		this.csapEventClient = csapEventClient;
	}

	Application csapApp;
	OsManager osManager;

	@Autowired ( required = false )
	CsapSecurityConfiguration securityConfig;

	CsapEventClient csapEventClient;

	OsCommandRunner osCommandRunner = new OsCommandRunner( 60, 1, "HostRequests" );

	private static final ClassPathResource searchTemplateLocation = new ClassPathResource( "searchTemplate.txt" );
	private static final String searchTemplate;

	static {
		try {
			// load template file and replace EOL characters "\\r|\\n"
			searchTemplate = FileUtils.readFileToString( searchTemplateLocation.getFile() )
				.replaceAll(
					"\\r\\n|\\r|\\n", System.getProperty( "line.separator" ) );
		} catch (IOException e) {
			throw new RuntimeException( "Could not init class.", e );
		}
	}

	// REST APIS below
	@RequestMapping ( value = "/settingsUpdate" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode settingsUpdate (
										@RequestParam ( value = "testUser" , required = false ) String testUser,
										@RequestParam ( value = "new" , required = false ) String preferencesString )
			throws IOException {

		ObjectNode resultsJson = jacksonMapper.createObjectNode();
		String user = securityConfig.getUserIdFromContext();

		ObjectNode preferences;
		if ( testUser != null ) {
			preferences = jacksonMapper.createObjectNode();
			preferences.put( "test", "test insert by " + user );
			user = testUser;
		} else {
			preferences = (ObjectNode) jacksonMapper.readTree( preferencesString );
		}

		csapEventClient.publishEvent( CsapEventClient.CSAP_USER_SETTINGS_CATEGORY + user,
			"User Preferences", null,
			preferences );

		resultsJson.put( "user", user );
		resultsJson.set( "preferences", preferences );
		return resultsJson;
	}

	@Inject
	@Qualifier ( "csapEventsService" )
	private RestTemplate csapEventsService;

	@RequestMapping ( value = "/settingsGet" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode settingsGet (
									@RequestParam ( value = "testUser" , required = false ) String testUser ) {
		ObjectNode resultsJson = jacksonMapper.createObjectNode();

		if ( !csapApp.lifeCycleSettings().isEventPublishEnabled() ) {
			logger.info( "Skipping user settings as remote access is disabled by lifeCycleSettings().isEventPublishEnabled() " );
			return null;
		}

		String user = securityConfig.getUserIdFromContext();

		if ( testUser != null ) {
			user = testUser;
		}
		resultsJson.put( "userid", user );

		String restUrl = csapApp.lifeCycleSettings()
			.getEventApiUrl() + "/latest?keepMostRecent=5&category="
				+ CsapEventClient.CSAP_USER_SETTINGS_CATEGORY + user;

		resultsJson.put( "url", restUrl );

		if ( !restUrl.startsWith( "http" ) ) {

			logger.debug( "====> \n\n Cache Reuse or disabled" );

			resultsJson.put( "message", "cache disabled: " + restUrl );
		} else {

			try {

				ObjectNode restResponse = csapEventsService.getForObject( restUrl, ObjectNode.class );

				if ( restResponse != null ) {
					resultsJson.set( "response", restResponse.get( "data" ) );
				} else {
					resultsJson.put( "message", "Got a null response from url: " + restUrl );
				}
			} catch (Exception e) {
				logger.error( "Failed getting user settings from url: " + restUrl, e );
				resultsJson.put( "url", restUrl );
				resultsJson.put( "message", "Error during Access: " + e.getMessage() );
			}
		}

		return resultsJson;
	}

	@RequestMapping ( value = "/logSearch" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode logSearch (
									ModelMap modelMap,
									@RequestParam ( "searchTarget" ) String searchTarget,
									@RequestParam ( "serviceName" ) String serviceName,
									@RequestParam ( "fromFolder" ) String fromFolder,
									@RequestParam ( "maxMatches" ) int maxMatches,
									@RequestParam ( "linesBefore" ) int linesBefore,
									@RequestParam ( "linesAfter" ) int linesAfter,
									@RequestParam ( "tailLines" ) int tailLines,
									@RequestParam ( value = "ignoreCase" , required = false , defaultValue = "false" ) boolean ignoreCase,
									@RequestParam ( value = "delim" , required = false , defaultValue = "false" ) boolean delim,
									@RequestParam ( value = "reverseOrder" , required = false , defaultValue = "false" ) boolean reverseOrder,
									@RequestParam ( value = "zipSearch" , required = false , defaultValue = "false" ) boolean zipSearch,
									@RequestParam ( value = "cancel" , required = false , defaultValue = "none" ) String cancelFlag,
									@RequestParam ( value = "jobId" , required = true ) String jobId,
									@RequestParam ( "hosts" ) String[] hosts,
									@RequestParam ( value = "scriptName" , required = true ) String scriptName,
									@RequestParam ( value = "timeoutSeconds" , required = false , defaultValue = "30" ) int timeoutSeconds )
			throws IOException {

		if ( fromFolder.startsWith( "logs" ) ) {
			fromFolder = fromFolder.substring( 5 );
		}

		File targetFile = csapApp.getRequestedFile( fromFolder, serviceName, true );

		logger.debug( "Target: {},  processing absolutePath: {}", targetFile.getCanonicalPath(),
			csapApp.getProcessingDir()
				.getCanonicalPath() );

		if ( searchTarget.length() == 0 || searchTarget.indexOf( ';' ) != -1 ) {
			ObjectNode resultsNode = jacksonMapper.createObjectNode();
			resultsNode.put( "error", "Add some text to search for. It is empty or contains invalid characters." );
			return resultsNode;
		}

		if ( targetFile == null
				|| targetFile.getAbsolutePath()
					.contains( ";" )
				|| !targetFile.getParentFile()
					.exists()
				|| !targetFile.getParentFile()
					.getCanonicalPath()
					.startsWith( csapApp.getLogDir( serviceName )
						.getCanonicalPath() ) ) {
			ObjectNode resultsNode = jacksonMapper.createObjectNode();
			resultsNode.put( "error", "Invalid path " + fromFolder );
			logger.warn( "Target: {},  processing absolutePath: {}", targetFile.getCanonicalPath(),
				csapApp.getProcessingDir()
					.getCanonicalPath() );
			;
			return resultsNode;
		}

		String maxCommand = "";
		if ( maxMatches != 0 ) {
			maxCommand = "-m " + maxMatches;
		}

		String ignoreCommand = "";
		if ( ignoreCase ) {
			ignoreCommand = "-i";
		}

		String delimCommand = "";
		if ( delim ) {
			delimCommand = "--group-separator=__CSAPDELIM__";
		}

		String tailCommand = "";
		if ( tailLines != 0 ) {
			tailCommand = Integer.toString( tailLines );
		}

		String linesBeforeFinal = Integer.toString( linesBefore );
		String linesAfterFinal = Integer.toString( linesAfter );
		if ( reverseOrder ) {
			// File is being reversed, so reverse the search
			linesBeforeFinal = Integer.toString( linesAfter );
			linesAfterFinal = Integer.toString( linesBefore );
		}

		String contents = searchTemplate
			.replaceAll( "__searchTarget__", searchTarget )
			.replaceAll( "__searchLocation__", targetFile.getAbsolutePath() )
			.replaceAll( "__maxMatches__", maxCommand )
			.replaceAll( "__linesBefore__", linesBeforeFinal )
			.replaceAll( "__linesAfter__", linesAfterFinal )
			.replaceAll( "__ignoreCase__", ignoreCommand )
			.replaceAll( "__tailLines__", tailCommand )
			.replaceAll( "__delim__", delimCommand )
			.replaceAll( "__reverseOrder__", Boolean.toString( reverseOrder ) )
			.replaceAll( "__zipSearch__", Boolean.toString( zipSearch ) );

		if ( !delim ) {
			contents = contents.replaceAll( "__delim__", "" );
		}

		scriptName += "_" + jobId;
		if ( OsCommandRunner.CANCEL_SUFFIX.endsWith( cancelFlag ) ) {
			scriptName += OsCommandRunner.CANCEL_SUFFIX;
		}

		logger.debug( "search Script: {}", contents );

		OutputFileMgr outputFm = new OutputFileMgr( csapApp.getScriptDir(), scriptName );
		ObjectNode resultsNode = osManager.executeShellScriptClustered(
			securityConfig.getUserIdFromContext(),
			timeoutSeconds, contents, csapApp.getAgentRunUser(),
			hosts,
			scriptName, outputFm );
		outputFm.opCompleted();

		if ( Application.isRunningOnDesktop() ) {
			logger.info( "Dummy results for desktop, using script  contents:\n {}", contents );
			ObjectNode transferResults = (ObjectNode) resultsNode.at( "/otherHosts/0/transferResults" );
			ArrayNode scriptResults = transferResults.putArray( "scriptResults" );
			File desktopResults = (new ClassPathResource( "/linux/searchResults.txt" )).getFile();
			scriptResults.add( FileUtils.readFileToString( desktopResults ) );
		}

		return resultsNode;
	}

	public final static String EXECUTE_URL = "/executeScript";

	/**
	 *
	 * If scriptName ends with .cancel, job with same name will be cancelled.
	 *
	 * @param modelMap
	 * @param contents
	 * @param chownUserid
	 * @param clusterName
	 * @param scriptName
	 * @param timeoutSeconds
	 * @param request
	 * @param response
	 * @throws IOException
	 */
	@RequestMapping ( value = EXECUTE_URL , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode executeScript (
										@RequestParam ( "contents" ) String contents,
										@RequestParam ( value = "chownUserid" , required = false , defaultValue = "dummy" ) String chownUserid,
										@RequestParam ( value = "cancel" , required = false , defaultValue = "none" ) String cancelFlag,
										@RequestParam ( value = "jobId" , required = true ) String jobId,
										@RequestParam ( "hosts" ) String[] hosts,
										@RequestParam ( value = "scriptName" , required = true ) String scriptName,
										@RequestParam ( value = "timeoutSeconds" , required = false , defaultValue = "120" ) int timeoutSeconds )
			throws IOException {

		scriptName += "_" + jobId;
		if ( OsCommandRunner.CANCEL_SUFFIX.endsWith( cancelFlag ) ) {
			scriptName += OsCommandRunner.CANCEL_SUFFIX;
		}

		OutputFileMgr outputFm = new OutputFileMgr( csapApp.getScriptDir(), scriptName );
		ObjectNode resultsNode = osManager.executeShellScriptClustered(
			securityConfig.getUserIdFromContext(),
			timeoutSeconds, contents, chownUserid,
			hosts,
			scriptName, outputFm );
		outputFm.opCompleted();

		return resultsNode;
	}

	@RequestMapping ( value = "/killScript" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode killScript (
									ModelMap modelMap,
									@RequestParam ( "contents" ) String contents,
									@RequestParam ( value = "chownUserid" , required = false , defaultValue = "dummy" ) String chownUserid,
									@RequestParam ( "hosts" ) String[] hosts,
									@RequestParam ( value = "scriptName" , required = true ) String scriptName,
									@RequestParam ( value = "timeoutSeconds" , required = false , defaultValue = "120" ) int timeoutSeconds )
			throws IOException {

		OutputFileMgr outputFm = new OutputFileMgr( csapApp.getScriptDir(), scriptName );
		ObjectNode resultsNode = osManager.executeShellScriptClustered(
			securityConfig.getUserIdFromContext(),
			timeoutSeconds, contents, chownUserid,
			hosts,
			scriptName, outputFm );
		outputFm.opCompleted();

		return resultsNode;
	}

	@RequestMapping ( "/getConfigZip" )
	public void getConfigZip (
								@RequestParam ( value = "path" , required = false , defaultValue = "" ) String path,
								HttpServletRequest request, HttpSession session,
								HttpServletResponse response )
			throws IOException {

		File source = new File( csapApp.getDefinitionFolder(), path );
		File workingFolder = Application.getStagingFile( "/temp/" );

		if ( !workingFolder.exists() ) {
			workingFolder.mkdirs();
		}

		File fileName = new File( workingFolder, source.getName() + ".zip" );
		File zipLocation = new File( workingFolder, fileName.getName() );

		logger.info( "Zipping conf " + source.getAbsolutePath() + " zipLocation"
				+ zipLocation.getAbsolutePath() );
		ZipUtility.zipDirectory( source, zipLocation );

		// response.setContentType("application/octet-stream");
		response.setContentType( MediaType.APPLICATION_OCTET_STREAM_VALUE );
		response.setContentLength( (int) zipLocation.length() );
		response.setHeader( "Content-Disposition", "attachment; filename=\"" + zipLocation.getName()
				+ "\"" );

		try (DataInputStream in = new DataInputStream( new FileInputStream(
			zipLocation.getAbsolutePath() ) );
				ServletOutputStream op = response.getOutputStream();) {

			byte[] bbuf = new byte[ 3000 ];

			int numBytesRead;
			long startingMax = zipLocation.length();
			long totalBytesRead = 0L; // hook for files that are being updated

			while ((in != null) && ((numBytesRead = in.read( bbuf )) != -1)
					&& (startingMax > totalBytesRead)) {

				totalBytesRead += numBytesRead;
				op.write( bbuf, 0, numBytesRead );
			}

		} catch (FileNotFoundException e) {
			logger.error( "File not found", e );
			response.getWriter()
				.println( "Did not find file: " + zipLocation );
		}

		return;
	}

	@RequestMapping ( value = "/showRoles" , produces = MediaType.TEXT_PLAIN_VALUE )
	public String showRoles ( HttpSession session ) {

		csapApp.clearResourceStats();

		StringBuilder results = new StringBuilder();

		results.append( "\n\n Admin Group is: " + securityConfig.getAdminGroup() );
		results.append( "\n\n Cvs Group is: " + securityConfig.getBuildGroup() );
		results.append( "\n\n View Group is: " + securityConfig.getViewGroup() );
		for ( String role : securityConfig.getAndStoreUserRoles( session ) ) {
			results.append( "\n\n Role: " + role );
		}

		return results.toString();
	}
	
	@Autowired(required=false)
	private LdapTemplate csapLdap ;

	@SuppressWarnings ( "unchecked" )
	@RequestMapping ( value = "/testLdap" , produces = MediaType.TEXT_PLAIN_VALUE )
	public String testLdap (
								@RequestParam ( value = "numAttempts" , required = false , defaultValue = "5" ) int numAttempts,
								String ldapPassword,
								String ldapUser,
								String ldapUrl )
			throws Exception {

		int maxAttempts = numAttempts;
		int numFailures = 0;

		
		//
		if ( ldapUrl == null ) {
			ldapUrl = securityConfig.getUrl();
		}
		if ( ldapUser == null ) {
			ldapUser = securityConfig.getLdapSearchUser().toString();
		}

		StringBuffer resultsBuffer = new StringBuffer( "\n\n http request parameters can be updated as needed:" );
		resultsBuffer.append( "\n\t ldapUrl: " + ldapUrl );
		resultsBuffer.append( "\n\t ldapUser: " + ldapUser );
		resultsBuffer.append( "\n\t ldapPassword: " + ldapPassword );
		resultsBuffer.append( "\n\t numAttempts: " + numAttempts );

		boolean addedEntry = false;
		for ( int i = 0; i < maxAttempts; i++ ) {

			logger.debug( "\n\n *****************   Attempt: {}", i );

			LdapTemplate testLdapTemplate = new LdapTemplate();
			if ( ldapPassword != null) {
				LdapContextSource contextSource = new LdapContextSource();

				contextSource.setUserDn( ldapUser );
				contextSource.setPassword( ldapPassword );
				contextSource.setUrl( ldapUrl );

				//
				contextSource.afterPropertiesSet();
				testLdapTemplate.setContextSource( contextSource );
				testLdapTemplate.afterPropertiesSet();

				logger.debug( "\n\n Ldap connection: {}", contextSource.getBaseLdapPathAsString() );
			} else {
				if ( !addedEntry ) {
					resultsBuffer.append( "\n\n WARNING: ldapPassword not set - cached connection will be used. To validate connections add: &ldapPassword=CHANGE_ME") ;
				}
				testLdapTemplate = csapLdap ;
			}



			String dn = "uid=" + securityConfig.getUserIdFromContext() + "," + securityConfig.getSearchUser();

			CsapUser csapUser;
			try {
				csapUser = (CsapUser) testLdapTemplate.lookup( dn, new CsapUser() );
				logger.debug( "CsapUser Raw Attributes: \n\t {}", csapUser.getAttibutesJson() );

				csapUser = (CsapUser) testLdapTemplate.lookup( dn, CsapUser.PRIMARY_ATTRIBUTES, new CsapUser() );
				logger.debug( "CsapUser.PRIMARY_ATTRIBUTES  filter: \n\t {}", csapUser.getAttibutesJson() );

				if ( !addedEntry ) {
					resultsBuffer.append( "\n\n First successful output shown: "
							+ jacksonMapper.writerWithDefaultPrettyPrinter()
								.writeValueAsString(
									csapUser.getAttibutesJson() ) );
					addedEntry = true;
				}

			} catch (Exception e) {
				resultsBuffer.append( "\n\n failure reason: " + CSAP.getCsapFilteredStackTrace( e ) ) ;
				logger.error( "Failed LDAP: {}", CSAP.getCsapFilteredStackTrace( e ) );
				numFailures++;
			}

		}

		resultsBuffer.insert( 0, "numAttempts: " + numAttempts + "        numFailures: " + numFailures );
		logger.debug( resultsBuffer.toString() );

		return resultsBuffer.toString();

	}

	@RequestMapping ( "/getDf" )
	public void getDf ( HttpServletRequest request, HttpServletResponse response )
			throws IOException {

		logger.debug( " Entered" );

		response.setContentType( MediaType.APPLICATION_JSON_VALUE );
		response.getWriter()
			.println( osManager.getCachedFileSystemInfo() );

	}

	@RequestMapping ( "/getVmStats" )
	public void getVmStats ( HttpServletRequest request, HttpServletResponse response )
			throws IOException {

		logger.debug( " Entered" );

		response.setContentType( MediaType.APPLICATION_JSON_VALUE );
		response.getWriter()
			.println( osManager.getHostResourceSummary() );

	}

	final OperatingSystemMXBean osStats = ManagementFactory.getOperatingSystemMXBean();

	@RequestMapping ( "/getLoad" )
	public ObjectNode getLoad ( HttpServletRequest request, HttpServletResponse response ) {

		logger.debug( " Entered" );

		return csapApp.getHostLoadCpuAndMore();

	}

	@RequestMapping ( value = "/lastCollected" )
	public ObjectNode getLastCollected () {

		return osManager.getLastCollectedVals();
	}

	/**
	 *
	 * Special hooks to return VM status from CsAgent to admin instances On
	 * admin VMs, CsAgent is updated with active users so that it can be shared
	 * across clustered admin instances
	 *
	 * @param resetCache
	 * @param request
	 * @param response
	 */
	static final public String AGENT_INFO_URL = "/getManagerJson";

	@RequestMapping ( value = AGENT_INFO_URL , produces = MediaType.APPLICATION_JSON_VALUE )
	@Deprecated
	public JsonNode getManagerJson ()
			throws Exception {

		logger.warn( "Deprecated API" );

		return osManager.getHostRuntime();

	}

	String memResultsCache = "";
	long lastMemTimeStamp = 0;

	@RequestMapping ( value = "/getHosts" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode getHosts (
									@RequestParam ( value = "clusterName" , required = true ) String clusterName ) {

		logger.debug( "clusterName: {} ", clusterName );

		ObjectNode responseObject = jacksonMapper.createObjectNode();
		responseObject.put( "cluster", clusterName );

		responseObject.putArray( "hosts" );

		responseObject.putPOJO( "hosts", csapApp.getMutableHostsInActivePackage( clusterName ) );

		return responseObject;
	}

	@RequestMapping ( value = "/getMem" , produces = MediaType.APPLICATION_JSON_VALUE )
	public JsonNode getMem ()
			throws IOException {
		return osManager.getMemoryStatistics();
	}

	@RequestMapping ( value = "/diskActivity" , produces = MediaType.APPLICATION_JSON_VALUE )
	public JsonNode diskActivity ()
			throws IOException {
		return osManager.diskActivity();
	}

	/**
	 * du can be relatively expensive - we will cache almost always timers will
	 * be reset in case of operation
	 */
	@RequestMapping ( "/getCpu" )
	public ObjectNode getCpu (
								@RequestParam ( value = "filter" , defaultValue = "yes" , required = false ) String compressedFormat,
								HttpServletResponse response )
			throws IOException {

		logger.debug( "compressedFormat: {}", compressedFormat );

		response.setContentType( MediaType.APPLICATION_JSON_VALUE );

		ObjectNode serviceMetricsJson = osManager.getServiceMetrics( compressedFormat.equals( "yes" ) );

		Format tsFormater = new SimpleDateFormat( "HH.mm.ss" );
		// JsonNode rootNode = jacksonMapper.valueToTree(commandMap);
		((ObjectNode) serviceMetricsJson).put( "timestamp", tsFormater.format( new Date() ) );

		return serviceMetricsJson;

	}

	ObjectMapper jacksonMapper = new ObjectMapper();

	@RequestMapping ( "/getParams" )
	public void getParams (	@RequestParam ( value = "pid" , required = true ) String pid,
							HttpServletRequest request, HttpServletResponse response ) {

		logger.debug( " pid: {}", pid );

		try {
			response.setContentType( "text/html" );
			response.getWriter()
				.print( "\n\n =======================\n\n" );
			String psResult = null;

			// Same host as login, so get the processes
			List<String> parmList = Arrays.asList( "bash", "-c", "ps -o pcpu,pid,args -p " + pid );
			psResult = osCommandRunner.executeString( null, parmList );

			logger.debug( "psResult: {}", psResult );

			response.getWriter()
				.println( psResult );

			response.getWriter()
				.print( "\n\n ====================\n\n" );

		} catch (Exception e) {
			logger.error( "Failed to rebuild", e );
		}
	}

	@RequestMapping ( "/checkFsThroughput" )
	public void checkFsThroughput (
									@RequestParam ( value = "numGb" , required = false , defaultValue = "1" ) int numGb,
									@RequestParam ( value = "targetFs" , required = false , defaultValue = "" ) String targetFs,
									HttpServletRequest request, HttpServletResponse response ) {

		if ( targetFs.length() == 0 ) {
			targetFs = csapApp.getAgentRunHome();
		}

		logger.debug( " numGb: {} , targetFs: {} ", numGb, targetFs );

		try {
			response.setContentType( "text/plain" );
			response.getWriter()
				.print(
					"\n\n ========== checkFsThroughput Begin: " + numGb
							+ "Gb will be written then deleted" );

			response.getWriter()
				.print(
					"\n\n ==Expected throughput can vary, but typically this is 200-400Mbs. Contact vif-ops for abnormal results.\n\n" );

			response.flushBuffer();

			// Same host as login, so get the processes
			if ( numGb != -1 ) {
				runFsScript( numGb, targetFs, response );

			} else {

				runFsScript( 1, targetFs, response );

				runFsScript( 2, targetFs, response );

				runFsScript( 4, targetFs, response );

				runFsScript( 8, targetFs, response );

				runFsScript( 16, targetFs, response );

			}

			response.getWriter()
				.print( "\n\n ========== checkFsThroughput End ========\n\n" );

		} catch (Exception e) {
			logger.error( "Failed to rebuild", e );
		}
	}

	private void runFsScript ( int numGb, String targetFs, HttpServletResponse response )
			throws IOException {

		String psResult;
		response.getWriter()
			.println(
				"==  targetFs: " + targetFs + " Size of test file (Gb) :" + numGb );
		response.getWriter()
			.flush();
		List<String> parmList = Arrays.asList( "bash", "-c", "checkFsThroughput.sh " + numGb + " "
				+ targetFs );
		psResult = osCommandRunner
			.executeString( parmList, new File( "." ), null, null, 600, 10, null );

		auditRecord( "checkFsThroughput", psResult );

		logger.debug( "psResult: {} ", psResult );
		response.getWriter()
			.println( psResult );

		response.getWriter()
			.flush();
	}

	@RequestMapping ( "/checkVmThroughput" )
	public void checkVmThroughput (
									@RequestParam ( value = "numIterations" , required = false , defaultValue = "1000000" ) long numIterations,
									HttpServletRequest request, HttpServletResponse response ) {

		logger.debug( " numIterations: {}", numIterations );

		try {
			response.setContentType( "text/plain" );
			response.getWriter()
				.print(
					"\n\n ========== checkVmThroughput Begin: " + numIterations
							+ " iterations will be done" );

			response.getWriter()
				.print(
					"\n\n == Expected throughput can vary, but typically 1000000 will take about 10 seconds. Contact vif-ops for abnormal results.\n\n" );

			response.flushBuffer();

			List<String> parmList = Arrays.asList( "bash", "-c", "checkVmThroughput.sh "
					+ numIterations );
			String result = osCommandRunner.executeString( parmList, new File( "." ), null, null, 600,
				10, null );
			auditRecord( "checkVmThroughput", result );

			logger.debug( "psResult: {}", result );
			response.getWriter()
				.println( result );

			response.getWriter()
				.flush();

			response.getWriter()
				.print( "\n\n ========== checkVmThroughput End ========\n\n" );

		} catch (Exception e) {
			logger.error( "Failed to rebuild", e );
		}
	}

	@RequestMapping ( "/checkLimits" )
	public void checkLimits ( HttpServletRequest request, HttpServletResponse response ) {

		logger.debug( " Entered" );

		try {
			response.setContentType( "text/plain" );
			response.getWriter()
				.print( "\n\n ========== checkLimits Begin ========\n\n" );

			// Same host as login, so get the processes
			List<String> parmList = Arrays.asList( "bash", "-c", "checkLimits.sh " );
			String psResult = osCommandRunner.executeString( parmList, new File( "." ), null, null,
				600, 10, null );

			logger.debug( "psResult: {}", psResult );

			response.getWriter()
				.println( psResult );
			response.getWriter()
				.print( "\n\n ========== checkLimits End ========\n\n" );

		} catch (Exception e) {
			logger.error( "Failed to rebuild", e );
		}
	}

	@RequestMapping ( "/getPsTree" )
	public void getPsTree ( HttpServletRequest request, HttpServletResponse response ) {

		logger.debug( " Entered" );

		try {
			response.setContentType( "text/plain" );
			ServiceInstance s = csapApp.findServiceByNameOnCurrentHost( CSAP.AGENT_CONTEXT );
			response.getWriter()
				.print( osManager.getProcessTree( s.getPid().get( 0 ), "CsAgent" ) );

		} catch (Exception e) {
			logger.error( "Failed to rebuild", e );
		}
	}

	@RequestMapping ( "/updatePriority" )
	public void updatePriority (
									@RequestParam ( value = "pid" ) String pid,
									@RequestParam ( value = "priority" ) String priority,
									HttpServletRequest request, HttpServletResponse response ) {

		logger.debug( "Entered" );

		response.setContentType( MediaType.APPLICATION_JSON_VALUE );
		String output = "\n Renice not support on non-root installs";

		if ( Application.isRunningAsRoot() ) {

			File scriptPath = Application.getStagingFile( "/bin/rootRenice.sh" );
			List<String> parmList = new ArrayList<String>();
			// parmList.add("bash");
			parmList.add( "/usr/bin/sudo" );
			// parmList.add("-c") ;
			parmList.add( scriptPath.getAbsolutePath() );
			parmList.add( "UiRequest" );
			parmList.add( priority );
			parmList.add( pid );

			auditRecord( "updatePriority", "pid: " + pid + " os priority modified to: " + priority );

			output = "\n" + osCommandRunner.executeString( null, parmList );
		}

		logger.debug( "results: {} ", output );

		ObjectNode resultsNode = jacksonMapper.createObjectNode();
		resultsNode.put( "results", output );

		osManager.resetAllCaches();
		try {

			response.getWriter()
				.println( jacksonMapper.writeValueAsString( resultsNode ) );
			// response.getWriter().print(processesFound);
		} catch (Exception e) {

			logger.error( "Failed to write output", e );
		}

	}

	public final static String HOST_INFO_URL = "/hostOverview";

	@Autowired
	ServiceOsManager serviceOsManager;

	@RequestMapping ( value = HOST_INFO_URL , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode getHostOverview (
										@RequestParam ( value = CSAP.HOST_PARAM , required = false ) String host,
										HttpServletRequest request )
			throws IOException {

		if ( Application.isJvmInManagerMode() ) {

			MultiValueMap<String, String> urlVariables = new LinkedMultiValueMap<String, String>();

			if ( host != null ) {
				String url = csapApp.getAgentUrl( host, CsapCoreService.API_AGENT_URL + "/hostSummary", true );
				return csapEventsService.getForObject( url, ObjectNode.class );
			} else {
				ObjectNode error = jacksonMapper.createObjectNode();
				return error.put( "Error",
					CSAP.CONFIG_PARSE_ERROR + " - Failed to find hosts parameter" );
			}

		}

		return osManager.getHostSummary();

	}

	@RequestMapping ( "/getLsof" )
	public void getLsof (	@RequestParam ( value = "pid" , required = true ) String pidCommaSeparated,
							HttpServletRequest request, HttpServletResponse response ) {

		logger.debug( " pid: {}", pidCommaSeparated );

		try {
			response.setContentType( "text/plain" );
			response.getWriter()
				.print(
					"\n\n ========== lsof for pids: " + pidCommaSeparated + "========\n\n" );
			String commandResult = null;

			String[] pidArray = pidCommaSeparated.split( "," );
			for ( String pid : pidArray ) {
				response.getWriter()
					.print( "\n\n ========== lsof for pids: " + pid + "========\n\n" );
				// Same host as login, so get the processes
				List<String> parmList = Arrays.asList( "bash", "-c", "/usr/sbin/lsof -p " + pid
						+ " | wc -l" );

				auditRecord( "getLsof", parmList.toString() );

				commandResult = osCommandRunner.executeString( parmList, new File( "." ) );

				logger.debug( "result: {}", commandResult );

				response.getWriter()
					.println( commandResult );

				parmList = Arrays.asList( "bash", "-c", "/usr/sbin/lsof -p " + pid );
				commandResult = osCommandRunner.executeString( parmList, new File( "." ) );

				logger.debug( "result: {}", commandResult );

				response.getWriter()
					.println( commandResult );

				response.getWriter()
					.print( "\n\n ========== ========\n\n" );
			}
		} catch (Exception e) {
			logger.error( "Failed to rebuild", e );
		}
	}

	@Autowired ( required = false )
	CsapSecurityConfiguration csapSecurityConfiguration;

	@RequestMapping ( "/getTop" )
	public void getTop (
							@RequestParam ( defaultValue = "" ) String filter,
							HttpServletRequest request, HttpServletResponse response, HttpSession session ) {

		logger.info( " params: {} ", filter );

		auditRecord( "top", filter );

		try {
			response.setContentType( "text/plain" );

			// if ( isAdmin)
			if ( filter.length() > 0 ) {
				if ( !csapSecurityConfiguration.getAndStoreUserRoles( session )
					.contains( CsapSecurityConfiguration.ADMIN_ROLE ) ) {
					filter = "|grep " + csapApp.getAgentRunUser();
					response.getWriter().println( "*Note your role does not permit modifying parameters, using default: " + filter );
				} else {
					filter = "|grep " + filter;
				}
			}

			response.getWriter()
				.print( "\n\n ========== top ========\n\n" );
			String psResult = null;

			// Same host as login, so get the processes
			List<String> parmList = Arrays.asList( "bash", "-c",
				"export COLUMNS=500; top -b -d 2 -n 1 -c " + filter );
			psResult = osCommandRunner.executeString( null, parmList );

			logger.debug( "result: {}", psResult );

			response.getWriter()
				.println( psResult );

			response.getWriter()
				.print( "\n\n ========== ========\n\n" );

		} catch (Exception e) {
			logger.error( "Failed to rebuild", e );
		}
	}

	@RequestMapping ( "/journal" )
	public void journal (
							@RequestParam ( defaultValue = "100" ) String numberOfLines,
							@RequestParam ( defaultValue = "false" ) boolean reverse,
							@RequestParam ( defaultValue = "false" ) boolean json,
							HttpServletRequest request, HttpServletResponse response, HttpSession session ) {

		logger.info( " params: {} ", numberOfLines );

		auditRecord( "top", numberOfLines );

		try {
			if ( json ) {
				response.setContentType( MediaType.APPLICATION_JSON_VALUE );
			} else {
				response.setContentType( "text/plain" );
			}

			// if ( isAdmin)
			if ( !csapSecurityConfiguration.getAndStoreUserRoles( session )
				.contains( CsapSecurityConfiguration.ADMIN_ROLE ) ) {
				response.getWriter().println( "*Permission denied: only admins may access journal entries" );
				return;

			}

			String reverseParam = "";
			if ( reverse )
				reverseParam = " -r ";

			String jsonParam = "";
			if ( json )
				jsonParam = " -o json ";

			if ( !json ) {
				response.getWriter()
					.print( "\n\n ========== journalctl ========\n\n" );
			}

			String[] lines = {
					"#!/bin/bash",
					"journalctl --no-pager -n " + numberOfLines + reverseParam + jsonParam,
					"" };

			String scriptOutput = "Failed to run";
			try {
				scriptOutput = osCommandRunner.runUsingRootUser( "journal", lines );
			} catch (IOException e) {
				logger.info( "Failed to update: {} ", CSAP.getCsapFilteredStackTrace( e ) );
				scriptOutput += ", reason: " + e.getMessage() + " type: " + e.getClass().getName();
			}

			if ( json ) {
				response.getWriter().print( "{ \"results\": [" );
				scriptOutput = scriptOutput.substring( scriptOutput.indexOf( "{" ) );
				String[] jsonLines = scriptOutput.split( System.getProperty( "line.separator" ) );

				for ( int i = 0; i < jsonLines.length; i++ ) {

					response.getWriter().print( jsonLines[i] );
					if ( i != jsonLines.length - 1 ) {
						response.getWriter().println( "," );
					}
				}

				response.getWriter().print( "]}" );

			} else {
				response.getWriter().print( scriptOutput );
			}

			if ( !json ) {
				response.getWriter()
					.print( "\n\n ========== ========\n\n" );
			}

		} catch (Exception e) {
			logger.error( "Failed to rebuild", e );
		}
	}

	@RequestMapping ( "/invalidateSession" )
	public void invalidateSession ( HttpServletRequest request, HttpServletResponse response ) {

		logger.debug( "Entered" );

		try {
			response.setContentType( "text/plain" );
			response.getWriter()
				.print(
					"\n\n ========== request.getSession().invalidate() ========\n\n" );
			// request.getSession().invalidate() ;

			request.getSession()
				.invalidate();

		} catch (Exception e) {
			logger.error( "Failed to rebuild", e );
		}
	}

	@RequestMapping ( "/getMemInfo" )
	public void getMemInfo ( HttpServletRequest request, HttpServletResponse response ) {

		logger.debug( "Entered" );

		try {
			response.getWriter()
				.print( "\n\n ========== memInfo ========\n\n" );
			response.getWriter()
				.print( "\n\n ===ref. http://linux-kb.blogspot.com/2009/09/free-memory-in-linux-explained.html \n\n" );

			String psResult = null;

			// Same host as login, so get the processes
			List<String> parmList = Arrays.asList( "bash", "-c", "cat /proc/meminfo" );
			psResult = osCommandRunner.executeString( null, parmList );

			logger.debug( "result: {}", psResult );

			response.getWriter()
				.println( psResult );

			response.getWriter()
				.print( "\n\n ========== ========\n\n" );

		} catch (Exception e) {
			logger.error( "Failed to rebuild", e );
		}
	}

	@RequestMapping ( "/getMemFree" )
	public void getMemFree ( HttpServletRequest request, HttpServletResponse response ) {

		logger.debug( "Entered" );

		try {
			response.getWriter()
				.print( "\n\n ========== memInfo ========\n\n" );
			response.getWriter()
				.print( "\n\n ===ref. http://linux-kb.blogspot.com/2009/09/free-memory-in-linux-explained.html \n\n" );

			String psResult = null;

			// Same host as login, so get the processes
			List<String> parmList = Arrays.asList( "bash", "-c", "free -m" );
			psResult = osCommandRunner.executeString( null, parmList );

			logger.debug( "result: {}", psResult );

			response.getWriter()
				.println( psResult );

			response.getWriter()
				.print( "\n\n ========== ========\n\n" );

		} catch (Exception e) {
			logger.error( "Failed to rebuild", e );
		}
	}

	@RequestMapping ( "/getCpuInfo" )
	public void getCpuInfo ( HttpServletRequest request, HttpServletResponse response )
			throws IOException {

		logger.debug( " running getCpuInfo:" );

		response.setContentType( "text/plain" );
		response.getWriter()
			.print( "\n\n ========== cpuInfo ========\n\n" );
		String psResult = null;

		// Same host as login, so get the processes
		List<String> parmList = Arrays.asList( "bash", "-c", "cat /proc/cpuinfo" );
		psResult = osCommandRunner.executeString( null, parmList );

		logger.debug( "result: {}", psResult );

		response.getWriter()
			.println( psResult );

		response.getWriter()
			.print( "\n\n ========== ========\n\n" );

	}

	@RequestMapping ( "/getVmStat" )
	public void getVmStat ( HttpServletRequest request, HttpServletResponse response )
			throws IOException {

		logger.debug( " running getVmStat:" );

		response.setContentType( "text/plain" );
		response.getWriter()
			.print( "\n\n ========== vmstat ========\n\n" );
		String vmResult = null;
		// Same host as login, so get the processes
		List<String> parmList = Arrays.asList( "bash", "-c", "vmstat" );
		vmResult = osCommandRunner.executeString( null, parmList );

		logger.debug( "result: {}", vmResult );

		response.getWriter()
			.println( vmResult );

		response.getWriter()
			.print( "\n\n ========== ========\n\n" );

	}

	@RequestMapping ( "/getPsThreads" )
	public void getPsThreads (	@RequestParam ( value = "pid" , required = true ) String pid,
								HttpServletRequest request, HttpServletResponse response )
			throws IOException {

		logger.debug( " pid: {}", pid );

		response.setContentType( "text/plain" );
		response.getWriter()
			.print( "\n\n ========== lsof ========\n\n" );
		String psResult = null;

		// Same host as login, so get the processes
		List<String> parmList = Arrays.asList( "bash", "-c", "ps -Lo pcpu,pid,tid,state,nlwp -p "
				+ pid.replaceAll( ",", " -e " ) );

		auditRecord( "psThreads", parmList.toString() );

		psResult = osCommandRunner.executeString( null, parmList );

		logger.debug( "result: {}", psResult );

		response.getWriter()
			.println( psResult );

		response.getWriter()
			.print( "\n\n ========== ========\n\n" );

	}

	@RequestMapping ( "/showProcesses" )
	public void showProcesses (
								@RequestParam ( value = "sortByNice" , required = false , defaultValue = "false" ) boolean sortByNice,
								@RequestParam ( value = "csapFilter" , required = false , defaultValue = "false" ) boolean csapFilter,
								HttpServletRequest request, HttpServletResponse response )
			throws IOException {

		response.setContentType( "text/plain" );

		response.getWriter()
			.println( osManager.performMemoryProcessList( sortByNice, csapFilter, false ) );

	}

	@RequestMapping ( "/killPid" )
	public void killPid (	@RequestParam ( value = "pid" , required = true ) String pid,
							HttpServletRequest request, HttpServletResponse response )
			throws IOException {

		logger.debug( " pid: {}", pid );

		response.setContentType( "text/plain" );
		response.getWriter()
			.print( "\n\n =======================\n\n" );

		auditRecord( "kill", pid );

		String[] lines = {
				"#!/bin/bash",
				"kill -9 " + pid.replaceAll( ",", " " ),
				"" };

		String scriptOutput = "Failed to run";
		try {
			scriptOutput = osCommandRunner.runUsingRootUser( "kill", lines );
		} catch (IOException e) {
			logger.info( "Failed to update: {} ", CSAP.getCsapFilteredStackTrace( e ) );
			scriptOutput += ", reason: " + e.getMessage() + " type: " + e.getClass().getName();
		}

		response.getWriter().println( scriptOutput );

		response.getWriter()
			.print( "\n\n ====================\n\n" );

	}

	@RequestMapping ( "/testOracle" )
	public void testOci (	@RequestParam ( value = "url" , required = true ) String url,
							@RequestParam ( value = "query" , required = true ) String query,
							@RequestParam ( value = "user" , required = true ) String user,
							@RequestParam ( value = "pass" , required = true ) String pass, HttpServletRequest request,
							HttpServletResponse response )
			throws IOException {

		logger.debug( "user: {},  url: {} , query: {}", user, url, query );

		StringBuilder resultsBuff = new StringBuilder( "\n\nTesting connection: " );

		try (Connection jdbcConnection = DriverManager.getConnection( url, user, pass );
				ResultSet rs = jdbcConnection.createStatement()
					.executeQuery( query );) {
			// Class.forName("oracle.jdbc.driver.OracleDriver");

			// resultsBuff.append(jdbcConnection.createStatement().executeQuery("select
			// count(*) from job_schedule").getString(1))
			// ;
			while (rs.next()) {
				resultsBuff.append( rs.getString( 1 ) );
			}

		} catch (SQLException e) {
			// resultsBuff.append( "Got an SQL Exception" +
			// Application.getCustomStackTrace( e ) );
			resultsBuff.append( "Got an SQL Exception" + CSAP.getCsapFilteredStackTrace( e ) );
		}

		response.setContentType( "text/plain" );
		response.getWriter()
			.print(
				"\n\n ========== Results from: " + " url:" + url + " query" + query + "\n\n" );

		response.getWriter()
			.println( resultsBuff );

		response.getWriter()
			.println( "\n===================\n\n" );

	}

	@RequestMapping ( "/metricIntervals/{type}" )
	public void getMetricsIntervals (
										@PathVariable ( value = "type" ) String reqType,
										HttpServletRequest request, HttpServletResponse response )
			throws IOException {

		response.setContentType( MediaType.APPLICATION_JSON_VALUE );

		ArrayNode samplesArray = jacksonMapper.createArrayNode();

		String type = reqType;
		if ( reqType.startsWith( "jmx" ) ) {
			type = "jmx";
		}

		for ( Integer sampleInterval : csapApp.lifeCycleSettings()
			.getMetricToSecondsMap()
			.get( type ) ) {
			samplesArray.add( sampleInterval );
		}

		response.getWriter()
			.println( jacksonMapper.writeValueAsString( samplesArray ) );
	}

	@RequestMapping ( value = "/metricsData" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode getMetricsData (
										@RequestParam ( value = CSAP.HOST_PARAM , required = false ) String[] hostNameArray,
										@RequestParam ( value = "metricChoice" , required = false , defaultValue = "resource" ) String requestedMetricChoice,
										@RequestParam ( value = "id" , required = false , defaultValue = "resource" ) String historicalId,
										@RequestParam ( value = "numSamples" , required = false , defaultValue = "5" ) int numSamples,
										@RequestParam ( value = "skipFirstItems" , required = false , defaultValue = "0" ) int skipFirstItems,
										@RequestParam ( value = "serviceName" , required = false ) String[] svcNameArray,
										@RequestParam ( value = "resourceTimer" , required = false , defaultValue = "-1" ) Integer resourceTimer,
										@RequestParam ( value = "numberOfDays" , required = false , defaultValue = "-1" ) int numberOfDays,
										@RequestParam ( value = "dayOffset" , required = false , defaultValue = "1" ) int dayOffset,
										@RequestParam ( value = "isLastDay" , required = false , defaultValue = "false" ) boolean isLastDay,
										HttpServletRequest request )
			throws IOException {

		Integer secondsBetweenCollections = resourceTimer;

		String metricChoice = requestedMetricChoice;
		if ( requestedMetricChoice.startsWith( "jmx" ) && requestedMetricChoice.length() > 3 ) {
			metricChoice = "jmx";
		}

		logger.debug(
			"   requestedMetricChoice: {}, metricChoice: {}, resourceTimer: {}, \n\t numberOfDays: {} , numSamples: {}, dayOffset: {}",
			requestedMetricChoice, metricChoice, resourceTimer, numberOfDays, numSamples, dayOffset );

		// -1 indicates usage of the largest interval in the definition file
		// Real time graphs: numberOfDays== -1 , default to the shortest
		// collection interval
		if ( resourceTimer == -1 ) {
			switch (metricChoice) {
			case "resource":
				secondsBetweenCollections = csapApp.getLastVmStatsKey();
				if ( numberOfDays == -1 ) {
					secondsBetweenCollections = csapApp.getFirstVmStatsKey();
				}
				break;

			case "service":
				secondsBetweenCollections = csapApp.getLastSvcStatsKey();
				if ( numberOfDays == -1 ) {
					secondsBetweenCollections = csapApp.getFirstSvcStatsKey();
				}
				break;

			case "jmx":
				secondsBetweenCollections = csapApp.getLastJmxStatsKey();
				if ( numberOfDays == -1 ) {
					secondsBetweenCollections = csapApp.getFirstJmxStatsKey();
				}
				break;

			default:
				logger.error( "Unknown metric selection: " + metricChoice );
				break;
			}
		}
		ObjectNode statsMap = null;

		// We do not got remote if we are getting historical, or if local
		if ( Application.isJvmInManagerMode()
				&& !(Application.isRunningOnDesktop() && hostNameArray[0].equals( "localhost" )) ) {

			logger.warn( "DEPRECATED - use analytics API" );
			ObjectNode err = jacksonMapper.createObjectNode();
			err.put( "error", "Use Analytics apis" );
			return err;
			// // Used in Metrics dashboard
			// String ssoCookieStringForHeader =
			// HostRequests.getSSO_COOKIE_NAME() + "="
			// + WebUtils.getCookie( request, HostRequests.getSSO_COOKIE_NAME()
			// )
			// .getValue();
			//
			// return getMetricsDataRemote( ssoCookieStringForHeader,
			// hostNameArray, requestedMetricChoice, numSamples,
			// skipFirstItems,
			// svcNameArray, numberOfDays, dayOffset, isLastDay,
			// secondsBetweenCollections );

		} else {
			// Used in VM dashboard
			HostCollector hostCollector = null;

			switch (metricChoice) {
			case "resource":
				hostCollector = csapApp.getVmSharedCollector( resourceTimer );
				break;

			case "service":
				hostCollector = csapApp.getVmProcessCollector( resourceTimer );
				break;

			case "jmx":
				hostCollector = csapApp.getServiceCollector( resourceTimer );
				break;

			default:
				logger.error( "Unknown metric selection: " + metricChoice );
				break;
			}

			// logger.error("resourceTimer: " + resourceTimer) ;
			// ServiceMetricsRunnable statsRun = csapApp
			// .getSvcStatsRunnable(resourceTimer);
			if ( hostCollector == null ) {
				// hook for desktop testing
				logger.warn( "Should never happen, maybe wrong key was passed: " + resourceTimer );
				// metricsCollector = new ServiceMetricsRunnable(
				// csapApp, osManager, 30);

			} else if ( metricChoice.equals( requestedMetricChoice ) ) {
				statsMap = hostCollector
					.getCSVdata( false, svcNameArray, numSamples, skipFirstItems );
			} else {
				if ( svcNameArray != null && svcNameArray.length == 1 && !svcNameArray[0].contains( "_" ) ) {
					logger.info( "Updating svcNameArray with port number" );
					svcNameArray[0] = csapApp.getActiveModel()
						.serviceInstancesInCurrentLifeByName()
						.get( svcNameArray[0] )
						.get( 0 )
						.getServiceName_Port();

				}
				statsMap = hostCollector
					.getCSVdata( false, svcNameArray, numSamples, skipFirstItems, "customJmx" );
			}
		}

		if ( statsMap != null && statsMap.has( "attributes" ) && statsMap.get( "attributes" ).isObject() ) {

			ObjectNode attributesNode = ((ObjectNode) statsMap.path( "attributes" ));
			attributesNode.put( "sampleInterval", secondsBetweenCollections.intValue() );

			ArrayNode samplesArray = attributesNode.putArray( "samplesAvailable" );

			for ( Integer sampleInterval : csapApp.lifeCycleSettings()
				.getMetricToSecondsMap()
				.get( metricChoice ) ) {
				samplesArray.add( sampleInterval );
			}
		} else {
			statsMap = jacksonMapper.createObjectNode();
			statsMap.put( "errors",
				"{ No data found. Try again in a 2 minutes. If this fails repeatedly, contact operations. }" );
		}

		// Prune any data
		// pruneHistoricalFeedsIfNeeded(svcNameArray, numberOfDays, statsMap);
		return statsMap;
	}

	@RequestMapping ( "/testResourceMetricsUpload" )
	public void testResourceUpload (
										@RequestParam ( value = "numSamples" , required = false , defaultValue = "5" ) int numSamples,
										HttpServletRequest request, HttpServletResponse response )
			throws IOException {
		response.setContentType( "text/plain" );

		OsSharedResourcesCollector statsRun = csapApp.getVmSharedCollector( -1 );

		response.getWriter()
			.print( statsRun.uploadMetricsNow() );

	}

	@RequestMapping ( "/testDummyMetricsUpload" )
	public void testMetricsUpload (
									@RequestParam ( value = "numSamples" , required = false , defaultValue = "5" ) int numSamples,
									@RequestParam ( value = "numGraphs" , required = false , defaultValue = "5" ) int numGraphs,
									@RequestParam ( value = "numServices" , required = false , defaultValue = "5" ) int numServices,
									HttpServletRequest request, HttpServletResponse response )
			throws IOException {
		// response.setContentType("text/plain");
		response.setContentType( MediaType.APPLICATION_JSON_VALUE );

		ObjectNode metricsNode = jacksonMapper.createObjectNode();
		addDummyMetricAttributes( numSamples, 0, numServices, numGraphs, metricsNode );

		addDummyMetricData( numSamples, numServices, numGraphs, metricsNode );

		String result = "notSent";
		try {
			String restUrl = csapApp.lifeCycleSettings()
				.getMetricsUrl() + "/"
					+ Application.getHOST_NAME();
			SimpleClientHttpRequestFactory simpleClientRequestFactory = new SimpleClientHttpRequestFactory();
			simpleClientRequestFactory.setReadTimeout( 5000 );
			simpleClientRequestFactory.setConnectTimeout( 5000 );

			RestTemplate rest = new RestTemplate( simpleClientRequestFactory );

			String jsonDoc = jacksonMapper.writeValueAsString( metricsNode );

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType( MediaType.APPLICATION_JSON );
			HttpEntity<String> springEntity = new HttpEntity<String>( jsonDoc, headers );

			result = rest.postForObject( restUrl, springEntity, String.class );

			logger.info( "Uploaded Metrics, numSamples: " + numSamples + " numGraphs:" + numGraphs
					+ " numServices:" + numServices + "Response: \n" + result );

		} catch (Exception e) {
			logger.error( "Failed upload", e );
			result = "Failed, Exception:" + CSAP.getCsapFilteredStackTrace( e );
		}

		// response.getWriter().print(statsRun.uploadMetrics(numSamples, 1));
		response.getWriter()
			.print( jacksonMapper.writeValueAsString( metricsNode ) );
	}

	private int futureOffset = 0;

	private void addDummyMetricData (	int numSamples, int numServices, int numGraphs,
										ObjectNode metricsNode ) {

		futureOffset = futureOffset + numSamples; // for repeated calls, push
		// the offset so timestamps
		// are not duplicated ;

		ObjectNode dataNode = metricsNode.putObject( "data" );

		ArrayNode timeStampNode = dataNode.putArray( "timeStamp" );

		long currMs = System.currentTimeMillis() + (futureOffset * 1000);

		for ( int i = 0; i < numSamples; i++ ) {
			// java script needs specialty classes to deal with longs. Must pass
			// in as string
			timeStampNode.add( Long.toString( currMs + i * 1000 ) );
		}

		for ( int i = 0; i < numGraphs; i++ ) {
			String graphName = "DummyGraph" + i;

			for ( int j = 0; j < numServices; j++ ) {

				ArrayNode serviceNode = dataNode.putArray( graphName + "Service" + j );

				for ( int k = 0; k < numSamples; k++ ) {
					serviceNode.add( k );
				}
			}
		}

	}

	private void addDummyMetricAttributes (	int requestedSampleSize, int skipFirstItems,
											int numServices, int numGraphs, ObjectNode rootNode ) {
		ObjectNode descNode = rootNode.putObject( "attributes" );

		descNode.put( "id", "dummy_99" );
		descNode.put( "metricName", "System Resource" );
		descNode.put( "description", "Contains usr,sys,io, and load level metrics" );
		descNode.put( "hostName", Application.getHOST_NAME() );
		descNode.put( "sampleInterval", 99 );
		descNode.put( "samplesRequested", requestedSampleSize );
		descNode.put( "samplesOffset", skipFirstItems );
		descNode.put( "currentTimeMillis", System.currentTimeMillis() );
		descNode.put( "cpuCount", osStats.getAvailableProcessors() );

		ObjectNode graphsArray = descNode.putObject( "graphs" );

		for ( int i = 0; i < numGraphs; i++ ) {
			String graphName = "DummyGraph" + i;
			ObjectNode resourceGraph = graphsArray.putObject( graphName );
			for ( int j = 0; j < numServices; j++ ) {
				resourceGraph.put( graphName + "Service" + j, "Service " + j );
			}
		}
	}

	private void auditRecord ( String commandDesc, String details ) {

		csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/" + commandDesc, CsapUser.currentUsersID(),
			"Os Command", details );
	}

	private static String SSO_COOKIE_NAME;

	public static String getSSO_COOKIE_NAME () {
		return SSO_COOKIE_NAME;
	}

	@Autowired ( required = false )
	@Value ( "${security.cookie.name:needsInitSso}" )
	public void setSsoCookie ( String cookieName ) {
		HostRequests.SSO_COOKIE_NAME = cookieName;
	}

	@RequestMapping ( "/delete" )
	public void delete (	ModelMap modelMap,
							@RequestParam ( "location" ) String location,
							@RequestParam ( "hosts" ) String[] hosts,
							@RequestParam ( value = "runAsRoot" , required = false ) boolean runAsRoot,
							@RequestParam ( value = "timeoutSeconds" , required = false , defaultValue = "120" ) int timeoutSeconds,
							HttpServletRequest request, HttpServletResponse response )
			throws IOException {

		csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/delete",
			securityConfig.getUserIdFromContext(), location, "" );

		response.setHeader( "Cache-Control", "no-cache" );
		response.setContentType( MediaType.APPLICATION_JSON_VALUE );

		OutputFileMgr outputFm = new OutputFileMgr( csapApp.getScriptDir(),
			securityConfig.getUserIdFromContext() + "_delete" );

		File deleteTarget = new File( location );

		// String htmlResult = "";
		ObjectNode resultsNode = jacksonMapper.createObjectNode();

		resultsNode.put( "scriptHost", Application.getHOST_NAME() );
		ArrayNode hostNode = resultsNode.putArray( "scriptOutput" );

		if ( deleteTarget.exists() ) {
			SimpleDateFormat df = new SimpleDateFormat( "MMM-d-HH-mm-ss" );

			String user = csapApp.getAgentRunUser();

			if ( runAsRoot ) {
				user = "root";
			}
			resultsNode = osManager.executeShellScriptClustered(
				securityConfig.getUserIdFromContext(),
				timeoutSeconds, "rm -rvf " + deleteTarget.getAbsolutePath(), user,
				hosts,
				"delete-" + df.format( new Date() ), outputFm );

		} else {
			hostNode.add( "Path does not exist:" + deleteTarget.getAbsolutePath() );
		}
		outputFm.opCompleted();
		response.getWriter()
			.println( resultsNode );
		return;
	}

	@RequestMapping ( "/syncFiles" )
	public void syncFiles (	ModelMap modelMap, @RequestParam ( "location" ) String locationToZip,
							@RequestParam ( "hosts" ) String[] hosts,
							@RequestParam ( value = "extractDir" , required = true ) String extractDir,
							@RequestParam ( value = "chownUserid" , required = false ) String chownUserid,
							@RequestParam ( value = "deleteExisting" , required = false ) String deleteExisting,
							@RequestParam ( value = "timeoutSeconds" , required = false , defaultValue = "120" ) int timeoutSeconds,
							HttpServletRequest request, HttpServletResponse response )
			throws IOException {

		response.setHeader( "Cache-Control", "no-cache" );
		response.setContentType( MediaType.APPLICATION_JSON_VALUE );

		ObjectNode resultsNode = jacksonMapper.createObjectNode();
		List hostList = new ArrayList<String>( Arrays.asList( hosts ) );

		if ( hostList.size() < 2 ) {
			resultsNode.put( "Failure", "Add at least one additional host" );
			response.getWriter()
				.println( resultsNode );
			return;
		}

		csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/sync",
			securityConfig.getUserIdFromContext(), locationToZip,
			"Syncing: " + locationToZip + " extractDir: " + extractDir + " Hosts: "
					+ hostList );

		resultsNode.put( "scriptHost", Application.getHOST_NAME() );
		ArrayNode hostNode = resultsNode.putArray( "scriptOutput" );

		File scriptDir = csapApp.getScriptDir();
		if ( !scriptDir.exists() ) {
			logger.info( "Making: " + scriptDir.getAbsolutePath() );
			scriptDir.mkdirs();
		}
		OutputFileMgr outputFm = new OutputFileMgr( csapApp.getScriptDir(),
			securityConfig.getUserIdFromContext() + "_sync" );

		File zipLocation = new File( locationToZip );
		hostNode.add( "\n Source Location: " + zipLocation.getAbsolutePath() );

		// List<String> hostList = csapApp.getMutableHostsInActivePackage(
		// clusterName );
		if ( hostList != null && hostList.contains( Application.getHOST_NAME() ) ) {

			logger.debug( "Removing : {}", Application.getHOST_NAME() );
			// always remove current host
			hostList.remove( Application.getHOST_NAME() );
		}

		resultsNode.set(
			"otherHosts",
			osManager.zipAndTransfer(
				securityConfig.getUserIdFromContext(),
				timeoutSeconds, hostList,
				locationToZip, extractDir, chownUserid,
				outputFm, deleteExisting ) );

		outputFm.opCompleted();

		response.getWriter()
			.println( resultsNode );

		return;
	}

	@RequestMapping ( "/uploadToFsValidate" )
	public ObjectNode uploadToFsValidate (
											@RequestParam String uploadFilePath,
											@RequestParam String extractDir,
											@RequestParam ( value = "skipExtract" , required = false ) boolean skipExtract,
											@RequestParam ( value = "overwriteTarget" , required = false , defaultValue = "false" ) boolean overwriteTarget,
											@RequestParam ( value = CSAP.SERVICE_PORT_PARAM , required = false ) String svcName )
			throws IOException {
		// Default is to place in processing folder. Push uploaded files
		// with absolute paths into root. Handle windows as well
		if ( extractDir.startsWith( "/" ) || extractDir.contains( ":" ) ) {
			extractDir = "__root__" + extractDir;
		}

		String fileName = new File( uploadFilePath ).getName();
		// Handle scenario where files are copied from a VM with alternate
		// csap install folder
		File targetExtractionDirectory = csapApp.getRequestedFile( extractDir, svcName, false );

		File extractFullTarget = new File( targetExtractionDirectory, fileName );

		String startTime = LocalDateTime.now().format( DateTimeFormatter.ofPattern( "HH:mm:ss MMM d" ) );

		ObjectNode results = jacksonMapper.createObjectNode();
		OutputFileMgr outputFileManager = new OutputFileMgr( csapApp.getScriptDir(), securityConfig.getUserIdFromContext() + "_upload" );
		outputFileManager.close();

		File progressFile = outputFileManager.getOutputFile();
		List<String> lines = Arrays.asList(
			"\n\n *** starting upload at: " + startTime,
			"*** target location: " + extractFullTarget.getAbsolutePath(),
			"*** time to complete will vary based on network speed and size of file" );

		Files.write( progressFile.toPath(), lines, Charset.forName( "UTF-8" ) );
		results.put( "progressFile", progressFile.getCanonicalPath() );
		// results.put( "error", "Need to Implment" ) ;
		if ( Application.isRunningOnDesktop() ) {

			// windows will not allow shell scripts - so
			if ( skipExtract ) {
				extractFullTarget = new File( targetExtractionDirectory, fileName
						+ ".windebug" );

				// extractFullTarget = new File( "/aTemp/peter.windebug" );
				targetExtractionDirectory = extractFullTarget;
			} else {
				extractFullTarget = new File( targetExtractionDirectory, "windebug" );
			}
		}

		results.put( "targetExtractionDirectory ", targetExtractionDirectory.getAbsolutePath() );
		results.put( "extractFullTarget ", extractFullTarget.getAbsolutePath() );
		if ( (targetExtractionDirectory.exists() && targetExtractionDirectory.isFile() && !overwriteTarget)
				|| (extractFullTarget.exists() && extractFullTarget.isFile() && !overwriteTarget) ) {
			results.put( "error", "\n\nSpecified destination already exists:\n"
					+ extractFullTarget.getAbsolutePath()
					+ "\n\n ===> UseOverwrite checkbox  to proceed\n" );

		}

		return results;

	}

	@RequestMapping ( "/uploadToFs" )
	public ObjectNode uploadToFs (	ModelMap modelMap,
									@RequestParam ( "distFile" ) MultipartFile multiPartFile,
									@RequestParam ( "hosts" ) String[] hosts,
									@RequestParam ( "extractDir" ) String extractTargetToken,
									@RequestParam ( "chownUserid" ) String chownUserid,
									@RequestParam ( value = "skipExtract" , required = false ) boolean skipExtract,
									@RequestParam ( value = "deleteExisting" , required = false ) String deleteExisting,
									@RequestParam ( value = "timeoutSeconds" , required = false , defaultValue = "120" ) int timeoutSeconds,
									@RequestParam ( value = CSAP.SERVICE_PORT_PARAM , required = false ) String svcName,
									@RequestParam ( value = "overwriteTarget" , required = false , defaultValue = "false" ) boolean overwriteTarget,
									HttpServletRequest request )
			throws IOException {

		List<String> hostList = new ArrayList<>( Arrays.asList( hosts ) );

		logger.info( "svcName: {}, extractTargetToken: {}, chownUserid: {}, hostList: {},  skipExtract: {}, overwriteTarget: {} ",
			svcName, extractTargetToken, chownUserid, hostList, skipExtract, overwriteTarget );

		ObjectNode results = jacksonMapper.createObjectNode();

		OutputFileMgr outputFileManager = new OutputFileMgr( csapApp.getScriptDir(), securityConfig.getUserIdFromContext() + "_upload" );

		results.put( "scriptHost", Application.getHOST_NAME() );
		ArrayNode scriptOutputArray = results.putArray( "scriptOutput" );

		results.putArray( "otherHosts" );
		// results. ( "otherHosts", jacksonMapper.createArrayNode() );

		String remoteServerName = request.getRemoteHost();

		if ( multiPartFile != null && multiPartFile.getSize() != 0 ) {

			// Default is to place in processing folder. Push uploaded files
			// with absolute paths into root. Handle windows as well
			if ( extractTargetToken.startsWith( "/" ) || extractTargetToken.contains( ":" ) ) {
				extractTargetToken = "__root__" + extractTargetToken;
			}

			// Handle scenario where files are copied from a VM with alternate
			// csap install folder
			File targetExtractionDirectory = csapApp.getRequestedFile( extractTargetToken, svcName, false );

			File extractFullTarget = new File( targetExtractionDirectory, multiPartFile.getOriginalFilename() );

			if ( Application.isRunningOnDesktop() ) {

				// windows will not allow shell scripts - so
				if ( skipExtract ) {
					extractFullTarget = new File( targetExtractionDirectory, multiPartFile.getOriginalFilename()
							+ ".windebug" );

					// extractFullTarget = new File( "/aTemp/peter.windebug" );
					targetExtractionDirectory = extractFullTarget;
				} else {
					extractFullTarget = new File( targetExtractionDirectory, "windebug" );
				}
			}

			logger.debug( "File Upload target: {}", extractFullTarget.getAbsolutePath() );

			if ( (targetExtractionDirectory.exists() && targetExtractionDirectory.isFile() && !overwriteTarget)
					|| (extractFullTarget.exists() && extractFullTarget.isFile() && !overwriteTarget) ) {
				scriptOutputArray.add( "\n\n Specified destination already exists:\n"
						+ extractFullTarget.getAbsolutePath()
						+ "\n\n ===> UseOverwrite checkbox  to proceed\n" );

			} else {

				String platformUpdateResults = osManager.updatePlatformCore(
					multiPartFile, targetExtractionDirectory.getAbsolutePath(),
					skipExtract, remoteServerName, chownUserid,
					securityConfig.getUserIdFromContext(), deleteExisting, outputFileManager );

				scriptOutputArray.add( platformUpdateResults );

				if ( hostList != null ) {
					hostList.remove( Application.getHOST_NAME() );
					if ( hostList.size() != 0 ) {
						if ( skipExtract ) {
							TransferManager transferManager = new TransferManager( csapApp, timeoutSeconds,
								outputFileManager.getBufferedWriter() );

							if ( extractFullTarget.exists() ) {
								transferManager
									.httpCopyViaCsAgent(
										securityConfig.getUserIdFromContext(),
										extractFullTarget, targetExtractionDirectory.getAbsolutePath(),
										hostList,
										chownUserid );
								results.replace( "otherHosts",
									transferManager.waitForCompleteJson() );
								// result.append(transferManager.waitForComplete(
								// "<pre class=\"result\">", "</pre>"));
							} else {
								scriptOutputArray.add( "\n ===> Did not find file to transfer: "
										+ extractFullTarget.getAbsolutePath() );
							}
						} else {
							scriptOutputArray
								.add( "\n ===> Requested sync to other hosts, and extract. Only either option may be used." );
						}
					}
				} else {
					// result.append("\n == no sync hosts specified");
				}
			}

		} else {
			results.put( "error", "Unable to process request: multiPartFile: " + multiPartFile );
			logger.error( "Unable to process request due to null file" );
		}

		// hook for displaying results
		ArrayNode otherHosts = (ArrayNode) results.get( "otherHosts" );
		ObjectNode hostResponse = otherHosts.addObject();
		hostResponse.put( "host", Application.getHOST_NAME() );

		logger.info( "completed upload" );
		outputFileManager.opCompleted();

		return results;
	}

	@Inject
	AgentApi agentApi;
	final public static String UPDATE_PLATFORM = "/updatePlatformLocal";

	@Deprecated
	@RequestMapping ( value = UPDATE_PLATFORM , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode updatePlatformLocal (
											@RequestParam ( value = "extractDir" , required = true ) String extractToken,
											@RequestParam ( value = "chownUserid" , required = false ) String chownUserid,
											@RequestParam ( value = "timeoutSeconds" , required = false , defaultValue = "120" ) int timeoutSeconds,
											@RequestParam ( value = "deleteExisting" , required = false ) String deleteExisting,
											@RequestParam ( value = "auditUser" , required = false ) String auditUser,
											@RequestParam ( value = "distFile" , required = true ) MultipartFile multiPartFile,
											HttpServletRequest request ) {

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

		ObjectNode jsonObjectResponse = jacksonMapper.createObjectNode();
		jsonObjectResponse.put( "host", Application.getHOST_NAME() );

		ArrayNode coreArray = jsonObjectResponse.putArray( "coreResults" );

		// MUST be the first line otherwise remote parsing will fail in
		// Transfermanager
		StringBuilder plainTextResponse = new StringBuilder( CSAP.AGENT_CONTEXT + "@"
				+ Application.getHOST_NAME() + ":" );

		String servletRemoteHost = request.getRemoteHost();

		if ( !csapApp.isHostAuthenticatedMember( servletRemoteHost ) ) {

			logger.error( "Security WARNING: bypass auth from {}", servletRemoteHost );
			plainTextResponse.append( "**" + CSAP.CONFIG_PARSE_ERROR + " Host "
					+ Application.getHOST_NAME()
					+ " received request from a host not in its cluster lifecycle: "
					+ servletRemoteHost );
			coreArray.add( "**" + CSAP.CONFIG_PARSE_ERROR + " Host "
					+ Application.getHOST_NAME()
					+ " received request from a host not in its cluster lifecycle: "
					+ servletRemoteHost );

			if ( !Application.isRunningOnDesktop() ) {
				return jsonObjectResponse;
			}
		} else {
			jsonObjectResponse = agentApi.platformUpdate( auditUser, "Dummy", extractToken, chownUserid, timeoutSeconds, deleteExisting,
				auditUser, multiPartFile, request );
		}

		return jsonObjectResponse;
	}

}
