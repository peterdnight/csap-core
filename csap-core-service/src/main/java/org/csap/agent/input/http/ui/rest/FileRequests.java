package org.csap.agent.input.http.ui.rest;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.csap.agent.CsapCoreService;
import org.csap.agent.CSAP;
import org.csap.agent.input.http.ui.windows.HostPortal;
import org.csap.agent.linux.OsCommandRunner;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.ActiveUsers;
import org.csap.agent.model.Application;
import org.csap.agent.model.Application.FileToken;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.services.DockerHelper;
import org.csap.docs.CsapDoc;
import org.csap.integations.CsapInformation;
import org.csap.integations.CsapSecurityConfiguration;
import org.csap.security.CsapUser;
import org.csap.security.CustomUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *
 * UI controller for browsing/viewing/editing files
 *
 * @author someDeveloper
 *
 *
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/current/spring-framework-reference/html/mvc.html">
 *      SpringMvc Docs </a>
 *
 * @see SpringContext_agentSvcServlet
 *
 *
 */
@Controller
@RequestMapping ( CsapCoreService.FILE_URL )
@CsapDoc ( title = "File Operations" , notes = {
		"File browser/manager, and associated rest operations. Includes viewing, saving, editing files",
		"<a class='pushButton' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/portals.png' />" } )
public class FileRequests {

	final Logger logger = LoggerFactory.getLogger( FileRequests.class );

	@Inject
	public FileRequests( Application csapApp,
			CsapEventClient csapEventClient ) {

		this.csapApp = csapApp;
		this.csapEventClient = csapEventClient;
	}

	Application csapApp;

	@Autowired ( required = false )
	CsapSecurityConfiguration csapSecurityConfiguration;

	CsapEventClient csapEventClient;

	OsCommandRunner osCommandRunner = new OsCommandRunner( 90, 3, "FileRequests" );

	@RequestMapping ( "propertyEncoder" )
	public String propertyEncoder (	ModelMap modelMap,
									@RequestParam ( value = "path" , required = false , defaultValue = "none" ) String path,
									HttpServletRequest request, HttpSession session )
			throws IOException {

		setCommonAttributes( modelMap, session, "Property Encoder" );
		modelMap.addAttribute( "name", Application.getHOST_NAME() );
		return "misc/property-encoder";
	}

	@Value ( "${user.name:dummy}" )
	private String CSAP_USER = "willBeReplacedOnSpringInit";

	public static final String FILE_MANAGER = "FileManager";

	@RequestMapping ( FILE_MANAGER )
	public String fileManager (
								@RequestParam ( defaultValue = "false" ) boolean docker,
								@RequestParam ( value = "fromFolder" , required = true ) String fromFolder,
								@RequestParam ( value = "showDu" , required = false ) String showDu,
								@RequestParam ( value = CSAP.SERVICE_PORT_PARAM , required = false ) String service_port,
								ModelMap modelMap, HttpServletRequest request, HttpSession session ) {

		csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/FileManager",
			CsapUser.currentUsersID(),
			"Browsing: " + fromFolder,
			null );

		setCommonAttributes( modelMap, session, "File Manager" );

		modelMap.addAttribute( "serviceName", service_port );
		modelMap.addAttribute( "fromFolder", fromFolder );

		// Tool tips
		ObjectNode diskPathsForTips = jacksonMapper.createObjectNode();
		modelMap.addAttribute( "diskPathsForTips", diskPathsForTips );

		diskPathsForTips.put( "fromDisk", pathForTips( fromFolder, service_port ) );
		diskPathsForTips.put( "homeDisk", pathForTips( FileToken.HOME.value, service_port ) );
		diskPathsForTips.put( "stagingDisk", pathForTips( FileToken.STAGING.value, service_port ) );
		diskPathsForTips.put( "installDisk", pathForTips( FileToken.STAGING.value + "/..", service_port ) );
		diskPathsForTips.put( "processingDisk", pathForTips( csapApp.getProcessingDir().getAbsolutePath(), service_port ) );
		if ( service_port != null ) {
			diskPathsForTips.put( "propDisk", pathForTips( FileToken.PROPERTY.value, service_port ) );
			ServiceInstance instance = csapApp.getServiceInstanceCurrentHost( service_port );
			if ( instance != null && instance.isDockerContainer() ) {
				diskPathsForTips.put( "propDisk", "dockerContainer"+ instance.getDockerContainerPath() );
				diskPathsForTips.put( "fromDisk", "dockerContainer" + instance.getDockerContainerPath()) ;
			}
					
		}

		if ( csapApp.isDockerInstalledAndActive() ) {
			String dockerRoot = dockerHelper.buildSummary().get( "rootDirectory" ).asText();
			diskPathsForTips.put( "dockerDisk", dockerRoot );
			if ( isDockerFolder( fromFolder ) ) {
				String dockerTarget = fromFolder.substring( Application.FileToken.DOCKER.value.length() );
				diskPathsForTips.put( "fromDisk", dockerRoot + "~" + dockerTarget );
			}
		}

		return CsapCoreService.FILE_URL + "/file-browser";
	}

	private String pathForTips ( String location, String serviceName ) {
		return csapApp.getRequestedFile( location, serviceName, false ).getAbsolutePath();
	}

	@RequestMapping ( "/browser/{browseId}" )
	public String fileBrowser (
								@PathVariable ( value = "browseId" ) String browseId,
								@RequestParam ( value = "showDu" , required = false ) String showDu,
								ModelMap modelMap, HttpServletRequest request, HttpSession session, PrintWriter writer ) {

		setCommonAttributes( modelMap, session, "File Browser" );
		JsonNode browseSettings = getBrowseSettings( browseId );

		if ( browseSettings.isMissingNode()
				|| !browseSettings.has( "group" ) ) {
			// logger.info( "settingsNode: {}", settingsNode );
			writer.println( "requested browse group not found: " + browseId );
			writer.println( "Contact administrator" );
			return null;
		}

		if ( Application.isJvmInManagerMode() ) {
			// csapApp.getRootModel().getAllPackagesModel().getServiceInstances(
			// serviceName )

			String cluster = browseSettings.get( "cluster" ).asText().replace( "-", "" );
			ArrayList<String> clusterHosts = csapApp.getActiveModel().getAllPackagesModel()
				.getLcGroupVerToHostMap().get( cluster );

			logger.debug( "specified: {}, Keys: {}", cluster, csapApp.getActiveModel().getAllPackagesModel()
				.getLcGroupVerToHostMap().keySet() );

			if ( clusterHosts == null || clusterHosts.size() == 0 ) {
				writer.println( "Incorrect browser configuration - very settings: " + browseSettings.get( "cluster" ).asText() );
				return null;
			}
			return "redirect:" + csapApp.getAgentUrl( clusterHosts.get( 0 ), "/file/browser/" + browseId, false );
		}

		csapSecurityConfiguration.addRoleIfUserHasAccess( session, browseSettings.get( "group" ).asText() );
		if ( !hasBrowseAccess( session, browseId ) ) {
			logger.info( "Permission denied for accessing {}, Confirm: {} is a member of: {}",
				browseId, csapSecurityConfiguration.getUserIdFromContext(),
				browseSettings.get( "group" ).asText() );
			return "csap/security/accessError";
		}

		modelMap.addAttribute( "serviceName", null );
		modelMap.addAttribute( "browseId", browseId );
		modelMap.addAttribute( "browseGroup", getBrowseSettings( browseId ).get( "group" ).asText() );

		modelMap.addAttribute( "fromFolder", Application.FileToken.ROOT.value );

		return CsapCoreService.FILE_URL + "/file-browser";
	}

	private boolean hasBrowseAccess ( HttpSession session, String browseId ) {

		JsonNode browseSettings = getBrowseSettings( browseId );

		if ( browseSettings.isMissingNode()
				|| !browseSettings.has( "group" ) ) {
			return false;
		}

		logger.info( "Checking access: {}", browseSettings );

		return csapSecurityConfiguration.hasCustomRole( session, browseSettings.get( "group" ).asText() );
	}

	private JsonNode getBrowseSettings ( String browseId ) {
		JsonNode groupFileNode = (JsonNode) csapApp.lifeCycleSettings().getFileBrowserConfig()
			.at( "/" + browseId );
		return groupFileNode;
	}

	@Autowired
	CsapInformation csapInformation;

	private void setCommonAttributes ( ModelMap modelMap, HttpSession session, String windowName ) {

		modelMap.addAttribute( "host", Application.getHOST_NAME() );

		modelMap.addAttribute( "dateTime",
			LocalDateTime.now().format( DateTimeFormatter.ofPattern( "HH:mm:ss,   MMMM d  uuuu " ) ) );

		modelMap.addAttribute( "userid", CsapUser.currentUsersID() );

		modelMap.addAttribute( "deskTop", Application.isRunningOnDesktop()
				&& !Application.isJvmInManagerMode() );

		modelMap.addAttribute( "adminRole", false );
		if ( csapSecurityConfiguration.getAndStoreUserRoles( session )
			.contains( CsapSecurityConfiguration.ADMIN_ROLE ) ) {
			modelMap.addAttribute( "adminRole", true );
		}

		modelMap.addAttribute( "infraRole", false );
		if ( csapSecurityConfiguration.getAndStoreUserRoles( session )
			.contains( CsapSecurityConfiguration.INFRA_ROLE ) ) {
			modelMap.addAttribute( "infraRole", true );
		}

		if ( session.getAttribute( CsapCoreService.FILE_URL + windowName ) == null ) {
			session.setAttribute( CsapCoreService.FILE_URL + windowName, "AccessLogged" );
			// auditRecord("metricsBrowser", "UI Launched");
			csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/accessed", CsapUser.currentUsersID(),
				"User interface: " + windowName, "" );
			activeUsers.updateUserAccessAndReturnAllActive( CsapUser.currentUsersID(), false );
		}

		modelMap.addAttribute( "user", CSAP_USER );
		modelMap.addAttribute( "csapApp", csapApp );

		modelMap.addAttribute( csapApp.lifeCycleSettings() );
		modelMap.addAttribute( "analyticsUrl", csapApp.lifeCycleSettings().getAnalyticsUiUrl() );
		modelMap.addAttribute( "eventApiUrl", csapApp.lifeCycleSettings().getEventApiUrl() );

		modelMap.addAttribute( "eventApiUrl", csapApp.lifeCycleSettings().getEventApiUrl() );

		modelMap.addAttribute( "eventMetricsUrl",
			csapApp.lifeCycleSettings().getEventMetricsUrl() );
		modelMap.addAttribute( "eventUser", csapApp.lifeCycleSettings().getEventDataUser() );
		modelMap.addAttribute( "life", Application.getCurrentLifeCycle() );

	}

	@Inject
	ActiveUsers activeUsers;

	@RequestMapping ( "/FileMonitor" )
	public String fileMonitor (
								@RequestParam ( value = "fromFolder" , required = false ) String fromFolder,
								@RequestParam ( value = "fileName" , required = false ) String fileName,
								@RequestParam ( value = "showDu" , required = false ) String showDu,
								@RequestParam ( value = CSAP.SERVICE_PORT_PARAM , required = false ) String serviceName,
								ModelMap modelMap, HttpServletRequest request, HttpSession session ) {

		setCommonAttributes( modelMap, session, "FileMonitor" );
		modelMap.addAttribute( "serviceName", serviceName );
		modelMap.addAttribute( "fromFolder", fromFolder );

		String shortName = "tail";
		if ( fileName != null ) {
			shortName = (new File( fileName )).getName();
		} else if ( serviceName != null ) {
			shortName = "logs " + serviceName;
		}
		modelMap.addAttribute( "shortName", shortName );

		String initialLogFileToShow = "";
		ArrayList<String> logFileNames = new ArrayList<String>();

		if ( fileName != null ) {
			// Use case: Show files in folder selected From file Browser
			// file requested will be inserted at top of list

			logFileNames.add( fileName );
			// logger.info( "file: {} , shortened: {}" , fileName ,
			// getShortNameFromCsapFilePath( fileName ) );
			// modelMap.addAttribute( "initialLogFileToShow",
			// getShortNameFromCsapFilePath( fileName ) );
			initialLogFileToShow = getShortNameFromCsapFilePath( fileName );

			File targetFile = csapApp.getRequestedFile( fileName, serviceName, false );

			if ( targetFile.getParentFile().exists() ) {
				// populate drop down with files in same folder; convenience for
				// browsing.
				File[] fileArray = targetFile.getParentFile().listFiles();

				for ( File itemInLogFolder : fileArray ) {
					if ( itemInLogFolder.isFile()
							&& !(itemInLogFolder.equals( targetFile )) ) {
						// logFileNames.add(prefix + itemInLogFolder.getName());

						// use full path for passing to other UIs
						try {
							logFileNames.add( Application.FileToken.ROOT.value + itemInLogFolder.getCanonicalPath() );
						} catch (IOException e) {
							logger.error( "Reverting to absolute path", e );
							logFileNames.add( Application.FileToken.ROOT.value + itemInLogFolder.getAbsolutePath() );
						}
					}
				}
			}
		} else {
			// Use Case: Show log files for requested services
			// -- scenario 1 - it is a tomcat jvm, so use catalina.out if
			// available
			// -- scenario 2 - it is a wrapper, use first file found in first
			// directory that is NOT a compressed file

			ServiceInstance serviceInstance = csapApp.getServiceInstanceCurrentHost(
				serviceName );
			File logDir = csapApp.getLogDir( serviceName );
			
			logger.debug( "default log file: {}, folder: {}", csapApp.getDefaultLogFileName( serviceName ), logDir );

			if ( serviceInstance.isDockerContainer() ) {
				logFileNames.add( Application.FileToken.DOCKER.value + serviceInstance.getDockerContainerPath() );
				initialLogFileToShow = Application.FileToken.DOCKER.value + serviceInstance.getDockerContainerPath();

			} else if ( logDir.exists() ) {

				File[] logsFiles = logDir.listFiles();

				int fileCountInLogFolder = 0;
				// int indexOfFileInListToDisplay = 0;

				ArrayList<String> logFileNamesInSubDir = new ArrayList<String>();
				String firstFileInFirstDir = "";

				String currentLogFile = "firstInFolder";
				for ( File itemInLogFolder : logsFiles ) {

					if ( itemInLogFolder.isFile() ) {
						// default to the first file found
						// look for a match

						if ( !itemInLogFolder.getName()
							.matches( serviceInstance.getLogRegEx() ) ) {
							// service definitions allow for optional selectors
							// for determing log files
							// some are .txt, some .log, etc.
							continue;
						}
						
						logger.debug( "Item in log folder: {}, default: {}", itemInLogFolder.getName(), csapApp.getDefaultLogFileName( serviceName ) );
						// use full path for passing to other UIs
						String currentItemPath = itemInLogFolder.getAbsolutePath() ;
						try {
							currentItemPath = itemInLogFolder.getCanonicalPath() ;
						} catch (IOException e) {
							logger.error( "Reverting to absolute path: {}", CSAP.getCsapFilteredStackTrace( e ) );
						}
						logFileNames.add( Application.FileToken.ROOT.value + currentItemPath );
						if ( fileCountInLogFolder == 0
								|| currentLogFile.endsWith( "gz" )
								|| itemInLogFolder.getName()
									.equals(
										csapApp.getDefaultLogFileName( serviceName ) ) ) {
							// indexOfFileInListToDisplay =
							// fileCountInLogFolder;
							// logger.info( "itemInLogFolder: {}",
							// itemInLogFolder.getName() );
							initialLogFileToShow = getShortNameFromCsapFilePath( Application.FileToken.ROOT.value + currentItemPath );
							currentLogFile = itemInLogFolder.getName();
						}
						fileCountInLogFolder++;

						logger.info( "Item in log folder: {}, default: {}, initialLogFileToShow: {}", 
							itemInLogFolder.getName(), csapApp.getDefaultLogFileName( serviceName ), initialLogFileToShow );
					} else if ( itemInLogFolder.isDirectory() ) {
						firstFileInFirstDir = findLogFiles( serviceInstance, logFileNamesInSubDir, firstFileInFirstDir,
							itemInLogFolder );
					}
				}

				if ( logFileNamesInSubDir.size() != 0 ) {
					logFileNames.addAll( logFileNamesInSubDir );
				}

				// not used? should be deleted if no probs
				// if (fileCountInLogFolder == 0)
				// foundFile = firstFileInFirstDir;
			}
			if ( logFileNames.size() == 0 ) {
				logger.error( "Failed to find any matching log files: " + logDir.getAbsolutePath()
						+ " \n Processing: " + csapApp.getProcessingDir().getAbsolutePath() );
			}
		}

		if ( logFileNames.size() == 0 ) {
			logger.error( "Failed to find any matching log files" );
		} else {

			HashMap<String, String> logFileMap = new HashMap<String, String>();
			for ( String logFileName : logFileNames ) {

				// skip past id
				String endName = getShortNameFromCsapFilePath( logFileName );
				logFileMap.put( endName, logFileName );
			}
			modelMap.addAttribute( "logFileMap", logFileMap );
			// This is used to select file in UI
			modelMap.addAttribute( "initialLogFileToShow", initialLogFileToShow );
		}
		return CsapCoreService.FILE_URL + "/file-monitor";
	}

	private String findLogFiles (	ServiceInstance instance, ArrayList<String> logFileNamesInSubDir,
									String firstFileInFirstDir, File itemInLogFolder ) {
		// Some services have multiple subfolders in log
		// directory.
		// One directory down will also be scanned for files.

		File[] subFiles = itemInLogFolder.listFiles();

		if ( subFiles == null ) {
			ArrayNode fileListing = buildListingUsingRoot( itemInLogFolder, new String[ 0 ], "notUsed" );
			Iterator<JsonNode> fileIterator = fileListing.iterator();

			while (fileIterator.hasNext()) {
				JsonNode file = fileIterator.next();
				if ( !file.has( "folder" ) ) {
					String name = file.get( "name" ).asText();
					if ( !name.matches(
						instance.getLogRegEx() ) ) {
						continue;
					}
					String path = itemInLogFolder.getName() + "/" + name;
					if ( firstFileInFirstDir.length() == 0 ) {
						firstFileInFirstDir = path;
					}
					// logFileNamesInSubDir.add(path);
					// use full path for passing to other UIs
					try {
						logFileNamesInSubDir.add( Application.FileToken.ROOT.value
								+ itemInLogFolder.getCanonicalPath() + "/" + name );
					} catch (IOException e) {
						logger.error( "Reverting to absolute path", e );
						logFileNamesInSubDir.add( Application.FileToken.ROOT.value
								+ itemInLogFolder.getAbsolutePath() + "/" + name );
					}
				}
			}
			;
		} else {
			for ( File subFile : subFiles ) {
				if ( subFile.isFile() ) {
					if ( !subFile.getName().matches(
						instance.getLogRegEx() ) ) {
						continue;
					}
					String path = itemInLogFolder.getName() + "/" + subFile.getName();
					if ( firstFileInFirstDir.length() == 0 ) {
						firstFileInFirstDir = path;
					}
					// logFileNamesInSubDir.add(path);
					// use full path for passing to other UIs
					try {
						logFileNamesInSubDir.add( Application.FileToken.ROOT.value + subFile.getCanonicalPath() );
					} catch (IOException e) {
						logger.error( "Reverting to absolute path", e );
						logFileNamesInSubDir.add( Application.FileToken.ROOT.value + subFile.getAbsolutePath() );
					}
				}
			}
		}
		return firstFileInFirstDir;
	}

	private String getShortNameFromCsapFilePath ( String logFileName ) {
		String shortName = logFileName.substring( logFileName.lastIndexOf( "__" ) + 2 );
		String endName = shortName;

		// hook to shorten name
		if ( StringUtils.countMatches( shortName, "/" ) > 2 ) {
			endName = shortName.substring( 0, shortName.lastIndexOf( "/" ) );
			endName = shortName.substring( endName.lastIndexOf( "/" ) + 1 );
		}
		// windows
		if ( StringUtils.countMatches( shortName, "\\" ) > 2 ) {
			endName = shortName.substring( 0, shortName.lastIndexOf( "\\" ) );
			endName = shortName.substring( endName.lastIndexOf( "\\" ) + 1 );
		}
		return endName;
	}

	private ObjectMapper jacksonMapper = new ObjectMapper();

	@RequestMapping ( "/getFilesJson" )
	public void getFilesJson (
								@RequestParam ( value = "browseId" , required = true ) String browseId,
								@RequestParam ( value = "fromFolder" , required = true ) String fromFolder,
								@RequestParam ( value = "showDu" , required = false ) String showDu,
								@RequestParam ( defaultValue = "false" ) boolean useRoot,
								@RequestParam ( value = CSAP.SERVICE_PORT_PARAM , required = false ) String service_port,
								HttpSession session,
								HttpServletRequest request, HttpServletResponse response )
			throws IOException {

		logger.debug( "fromFolder: {}, service: {}, showDu: {}, browseId: {}, useRoot: {}",
			fromFolder, service_port, showDu, browseId, useRoot );

		response.setHeader( "Cache-Control", "no-cache" );
		response.setContentType( MediaType.APPLICATION_JSON_VALUE );

		File targetFile = csapApp.getRequestedFile( fromFolder, service_port, false );

		if ( browseId.length() > 0 ) {
			// browse access requires explicit membership

			String browseFolder = getBrowseSettings( browseId ).get( "folder" ).asText();
			targetFile = new File( browseFolder,
				fromFolder.substring( browseId.length() ) );
			if ( !hasBrowseAccess( session, browseId )
					|| (!Application.isRunningOnDesktop() && !targetFile.getCanonicalPath().startsWith( browseFolder )) ) {

				accessViolation( response, targetFile, browseFolder );
				return;

			}
		} else {
			// general access requires admin
			if ( !csapSecurityConfiguration.getAndStoreUserRoles( session )
				.contains( CsapSecurityConfiguration.ADMIN_ROLE ) ) {
				accessViolation( response, targetFile, "/" );
				return;
			}
		}

		logger.debug( "targetFile: {}", targetFile.getAbsolutePath() );

		String[] duLines = new String[ 0 ];
		if ( showDu != null ) {
			duLines = runDiskUsage( targetFile );
		}

		ArrayNode fileListing;
		
		ServiceInstance instance = csapApp.getServiceInstanceCurrentHost( service_port );

		if ( fromFolder.startsWith( Application.FileToken.DOCKER.value ) ) {

			fileListing = buildListingUsingDocker(
				fromFolder.substring( Application.FileToken.DOCKER.value.length() ),
				duLines,
				fromFolder );

		} else if ( instance != null 
				&& instance.isDockerContainer()
				&& (
						fromFolder.equals( Application.FileToken.PROCESSING.value + "/" + service_port + "/" )
						|| fromFolder.equals( Application.FileToken.PROPERTY.value + "/" )
					)
				) {

			// handle file browser of docker services properties and app folder
			fileListing = buildListingUsingDocker(
				instance.getDockerContainerPath() + "/",
				duLines,
				Application.FileToken.DOCKER.value + instance.getDockerContainerPath() + "/");

		} else if ( !targetFile.exists() || !targetFile.isDirectory() || useRoot ) {

			fileListing = buildListingUsingRoot( targetFile, duLines, fromFolder );

		} else {

			// File[] filesInFolder = targetFile.listFiles();
			List<File> files = null;
			try (Stream<Path> pathStream = Files.list( targetFile.toPath() )) {
				files = pathStream
					.map( Path::toFile )
					.collect( Collectors.toList() );
				
			} catch (Exception e) {
				logger.info( "Failed to get listing for {} reason\n {} ", CSAP.getCsapFilteredStackTrace( e ), targetFile );
			}

			if ( files == null || files.size() == 0 ) {
				fileListing = buildListingUsingRoot( targetFile, duLines, fromFolder );

			} else {
				fileListing = buildListingUsingJava( fromFolder, duLines, files );
			}
		}
		// response.getWriter().println("</fromFolder>");

		// fileResponseJson folderJsonArray
		response.getWriter().println(
			jacksonMapper.writeValueAsString( fileListing ) );

	}

	private String[] runDiskUsage ( File targetFile ) {

		String[] lines = {
				"#!/bin/bash",
				"timeout 60 du -sm "
						+ targetFile.getAbsolutePath()
						+ "/* | awk '{print $1 \" \"  $2}'",
				"" };

		String[] scriptOutputLines = runScriptAndUseRootIfPermitted( "du", lines );
		if ( Application.isRunningOnDesktop() ) {
			logger.warn( "Loading test data" );
			String scriptOutput = Application.getContents(
				new File( getClass().getResource( "/linux/lsAsRootDu.txt" ).getFile() ) );
			scriptOutputLines = scriptOutput.split( "\n" );
		}

		// duLines = duResult.split( System.getProperty( "line.separator" ) );
		return scriptOutputLines;
	}

	private String[] runScriptAndUseRootIfPermitted ( String prefix, String[] lines ) {

		String scriptOutput = "Failed to run";
		try {
			scriptOutput = osCommandRunner.runUsingRootUser( prefix, lines );
		} catch (IOException e) {
			logger.info( "Failed to update: {} ", CSAP.getCsapFilteredStackTrace( e ) );
			scriptOutput += ", reason: " + e.getMessage() + " type: " + e.getClass().getName();
		}
		if ( Application.isRunningOnDesktop() ) {
			logger.warn( "Loading test data" );
			scriptOutput = Application.getContents(
				new File( getClass().getResource( "/linux/lsAsRootDu.txt" ).getFile() ) );
		}

		logger.debug( "prefix: {} , output: \n {}", scriptOutput );

		String[] scriptOutputLines = scriptOutput.split( "\n" );

		return scriptOutputLines;
	}

	@Inject
	DockerHelper dockerHelper;

	private ArrayNode buildListingUsingDocker ( String targetFolder, String[] duLines, String fromFolder ) {

		logger.info( "targetFolder: {} ", targetFolder );

		ArrayNode fileListing;
		if ( targetFolder.equals( "/" ) ) {
			ArrayNode containerListing = jacksonMapper.createArrayNode();
			dockerHelper.containerNames( false ).forEach( fullName -> {
				ObjectNode itemJson = containerListing.addObject();
				String name = fullName.substring( 1 ); // strip off leading
														// slash added by ui
				itemJson.put( "folder", true );
				itemJson.put( "lazy", true );
				itemJson.put( "name", name );
				itemJson.put( "location", fromFolder + name );
				// itemJson.put("data", dataNode) ;
				itemJson.put( "title", name );
			} );
			fileListing = containerListing;
		} else {
			// do docker ls & feed to OS listing

			String[] dockerContainerAndPath = splitDockerTarget( targetFolder );
			String lsOutputLines = dockerHelper.listFiles(
				dockerContainerAndPath[0],
				dockerContainerAndPath[1] );

			fileListing = buildListingUsingOs( fromFolder, lsOutputLines.split( "\n" ), duLines );
		}

		return fileListing;
	}

	private String[] splitDockerTarget ( String targetFolder ) {

		int secondSlashIndex = targetFolder.substring( 1 ).indexOf( "/" );

		if ( secondSlashIndex == -1 ) {
			return new String[] { targetFolder, "" };
		}
		String containerName = targetFolder.substring( 0, secondSlashIndex + 1 );
		String path = targetFolder.substring( containerName.length() );

		logger.info( "containerName: {} , path: {} ", containerName, path );
		return new String[] { containerName, path };
	}

	private ArrayNode buildListingUsingRoot ( File targetFolder, String[] duLines, String fromFolder ) {

		String[] lines = {
				"#!/bin/bash",
				"ls -al " + targetFolder.getAbsolutePath(),
				"" };

		String[] lsOutputLines = runScriptAndUseRootIfPermitted( "ls", lines );

		if ( Application.isRunningOnDesktop() ) {
			logger.warn( "Loading test data" );
			String scriptOutput = Application.getContents(
				new File( getClass().getResource( "/linux/lsAsRoot.txt" ).getFile() ) );
			lsOutputLines = scriptOutput.split( "\n" );
		}

		ArrayNode fileListing = buildListingUsingOs( fromFolder, lsOutputLines, duLines );

		return fileListing;

	}

	private ArrayNode buildListingUsingOs (	String fromFolder,
											String[] lsOutputLines,
											String[] duLines ) {

		ArrayNode fileListing = jacksonMapper.createArrayNode();
		for ( String line : lsOutputLines ) {
			String[] words = line.replaceAll( "\\s+", " " ).split( " " );
			logger.debug( "line: {} words: {} ", line, words.length );
			if ( words.length >= 9 && words[0].length() >= 11 ) {
				ObjectNode itemJson = fileListing.addObject();
				String currentItemName = words[8];
				itemJson.put( "name", currentItemName );

				String fsize = words[4] + " b, ";
				long fsizeNumeric = 0;
				try {
					Long fileSize = Long.parseLong( words[4] );
					fsizeNumeric = fileSize.longValue();
					if ( fileSize > 1000 )
						fsize = fsizeNumeric / 1000 + "kb, ";
				} catch (NumberFormatException e) {
					logger.info( "Unable to parse date {} for {} ",
						CSAP.getCsapFilteredStackTrace( e ) );
				}

				if ( words[0].contains( "d" ) ) {
					itemJson.put( "folder", true );
					itemJson.put( "lazy", true );
					for ( int i = 0; i < duLines.length; i++ ) {
						//
						// logger.debug("Checking " + duLines[i] +
						// " contains "
						// + itemInFolder.getAbsolutePath());
						if ( duLines[i].endsWith( "/"
								+ currentItemName ) ) {
							fsize = duLines[i].substring( 0,
								duLines[i].indexOf( " " ) )
									+ "Mb, ";

							try {
								fsizeNumeric = Long.parseLong( duLines[i]
									.substring( 0,
										duLines[i].indexOf( " " ) ) );
								fsizeNumeric = fsizeNumeric * 1000 * 1000;
								// gets to bytes
							} catch (Exception e) {
								logger.error( "Failed to parse to long" + fsize );
							}

							duLines[i] = ""; // optimize by shortening since
							// only 1 match
							break;
						}
					}
				}

				itemJson.put( "restricted", true );
				itemJson.put( "filter", false );
				itemJson.put( "location",
					fromFolder + words[8] );
				// itemJson.put("data", dataNode) ;
				itemJson.put( "title", words[8] );
				itemJson.put(
					"meta",
					"~"
							+ fsize
							+ words[5] + " "
							+ words[6] + " "
							+ words[7] + ","
							+ words[0] + ","
							+ words[1] + ","
							+ words[2] + ","
							+ words[3] );

				itemJson.put( "size", fsizeNumeric );
				itemJson.put( "target", fromFolder + words[8]
						+ "/" );
			}
		}

		if ( fileListing.size() == 0 ) {
			fileListing = jacksonMapper.createArrayNode();
			ObjectNode itemJson = fileListing.addObject();
			itemJson.put( "title", "Unable to get Listing" );
		}
		return fileListing;
	}

	private ArrayNode buildListingUsingJava ( String fromFolder, String[] duLines, List<File> filesInFolder ) {

		ArrayNode fileListing = jacksonMapper.createArrayNode();
		for ( File itemInFolder : filesInFolder ) {

			String fsize = itemInFolder.length() + " b, ";
			if ( itemInFolder.length() > 1000 ) {
				fsize = itemInFolder.length() / 1000 + "kb, ";
			}
			Long fsizeNumeric = itemInFolder.length();

			ObjectNode itemJson = fileListing.addObject();
			// ObjectNode dataNode =jacksonMapper.createObjectNode() ;

			if ( itemInFolder.isDirectory() ) {
				itemJson.put( "folder", true );
				itemJson.put( "lazy", true );
				for ( int i = 0; i < duLines.length; i++ ) {
					//
					// logger.debug("Checking " + duLines[i] +
					// " contains "
					// + itemInFolder.getAbsolutePath());
					if ( duLines[i].endsWith( "/"
							+ itemInFolder.getName() ) ) {
						fsize = duLines[i].substring( 0,
							duLines[i].indexOf( " " ) )
								+ "Mb, ";

						try {
							fsizeNumeric = Long.parseLong( duLines[i]
								.substring( 0,
									duLines[i].indexOf( " " ) ) );
							fsizeNumeric = fsizeNumeric * 1000 * 1000;
							// gets to bytes
						} catch (Exception e) {
							logger.error( "Failed to parse to long" + fsize );
						}

						duLines[i] = ""; // optimize by shortening since
						// only 1 match
						break;
					}
				}
			}
			boolean filtered = false;
			if ( itemInFolder.getName().equals( ".ssh" ) ) {
				filtered = true;
			}

			itemJson.put( "name", itemInFolder.getName() );
			itemJson.put( "filter", filtered );
			itemJson.put( "location",
				fromFolder + itemInFolder.getName() );
			// itemJson.put("data", dataNode) ;
			itemJson.put( "title", itemInFolder.getName() );
			itemJson.put(
				"meta",
				"~"
						+ fsize
						+ fileDateOutput.format( new Date( itemInFolder
							.lastModified() ) ) );
			itemJson.put( "size", fsizeNumeric.longValue() );
			itemJson.put( "target", fromFolder + itemInFolder.getName()
					+ "/" );

		}

		return fileListing;
	}

	private void accessViolation ( HttpServletResponse response, File targetFile, String browseFolder )
			throws IOException, JsonProcessingException {

		logger.debug( "Verify access: {} by {}", browseFolder, targetFile.getCanonicalPath() );
		ArrayNode childArray = jacksonMapper.createArrayNode();
		ObjectNode itemJson = childArray.addObject();
		itemJson.put( "name", "permission denied" );
		itemJson.put( "title", "permission denied" );

		response.getWriter().println(
			jacksonMapper.writeValueAsString( childArray ) );
	}

	private ThreadSafeSimpleDateFormat fileDateOutput = new ThreadSafeSimpleDateFormat(
		"MMM-d-yyyy H:mm:ss" );

	public class ThreadSafeSimpleDateFormat {

		private DateFormat df;

		public ThreadSafeSimpleDateFormat( String format ) {
			this.df = new SimpleDateFormat( format );
		}

		public synchronized String format ( Date date ) {
			return df.format( date );
		}

		public synchronized Date parse ( String string )
				throws ParseException {
			return df.parse( string );
		}
	}

	// private static final ThreadLocal<DateFormat> df = new
	// ThreadLocal<DateFormat>() {
	// @Override
	// protected DateFormat initialValue() {
	// return new SimpleDateFormat("yyyyMMM.d H:mm:ss");
	// }
	// };
	public static int BYTE_DOWNLOAD_CHUNK = 1024 * 10;

	@RequestMapping ( "/downloadFile/{fileName:.+}" )
	public void downloadFile (
								@PathVariable ( value = "fileName" ) String doNotUseThisAsItIsNotUrlEncoded,
								@RequestParam ( value = "browseId" , required = false , defaultValue = "" ) String browseId,
								@RequestParam ( defaultValue = "false" ) boolean forceText,
								@RequestParam ( value = "fromFolder" , required = true ) String fromFolder,
								@RequestParam ( value = "isBinary" , required = false , defaultValue = "false" ) boolean isBinary,
								@RequestParam ( value = CSAP.SERVICE_PORT_PARAM , required = false ) String svcName,
								@RequestParam ( value = "isLogFile" , required = false , defaultValue = "false" ) boolean isLogFile,
								HttpServletRequest request, HttpServletResponse response, HttpSession session )
			throws IOException {


		logger.info( "{} downloading service: {}, browseId: {} , fromFolder: {}",
			CsapUser.currentUsersID(), svcName, browseId, fromFolder );

		File targetFile = csapApp.getRequestedFile( fromFolder, svcName, isLogFile );

		// Restricted browse support
		if ( browseId != null && browseId.length() > 0 ) {
			// browse access requires explicit membership

			String browseFolder = getBrowseSettings( browseId ).get( "folder" ).asText();
			targetFile = new File( browseFolder,
				fromFolder.substring( browseId.length() ) );
			if ( !hasBrowseAccess( session, browseId ) || !targetFile.getCanonicalPath().startsWith( browseFolder ) ) {
				if ( !Application.isRunningOnDesktop() ) {
					accessViolation( response, targetFile, browseFolder );
					return;
				} else {
					logger.info( "Skipping access checks on desktop" );
				}

			}

			if ( targetFile == null || !targetFile.exists() ) {
				logger.warn( "Request file system does not exist: {}.  Check if {}  is bypassing security: ",
					targetFile.getCanonicalPath(), CsapUser.currentUsersID() );
				response.getWriter().println( "Invalid path " + fromFolder );
				return;
			}
		} else {

			if ( targetFile == null || !targetFile.exists() ) {

				if ( !csapSecurityConfiguration
					.getAndStoreUserRoles( session )
					.contains( CsapSecurityConfiguration.INFRA_ROLE ) ) {
					logger.warn( "Requested file does not exist: {}.  Check if {}  is bypassing security: ",
						targetFile.getCanonicalPath(), CsapUser.currentUsersID() );
					response.getWriter().println( "Invalid path " + fromFolder );
					return;
				} else {
					logger.info( "Requested file not readable by csap: {}. Attempt to access with restricted permissions",
						targetFile.getAbsolutePath() );
				}
			}

			// if it is not an admin - only allow viewing of files in processing
			// folder
			if ( !csapSecurityConfiguration.getAndStoreUserRoles( session )
				.contains( CsapSecurityConfiguration.ADMIN_ROLE ) ) {
				// run secondary check
				if ( !targetFile.getCanonicalPath().startsWith(
					csapApp.getProcessingDir().getCanonicalPath() ) ) {
					logger.warn( "Attempt to access file system: {}. Only {} is permitted. Check if {}  is bypassing security: ",
						targetFile.getCanonicalPath(), csapApp.getProcessingDir().getCanonicalPath(), CsapUser.currentUsersID() );
					response.getWriter().println( "*** Content protected: can be accessed by admins " + fromFolder );
					return;
				}
			}
			// Only allow infra admin to view security files.
			if ( !csapSecurityConfiguration.getAndStoreUserRoles( session )
				.contains( CsapSecurityConfiguration.INFRA_ROLE ) ) {
				// run secondary check
				if ( isInfraOnlyFile( targetFile ) ) {
					logger.warn( "Attempt to access security file: {}. Check if {}  is bypassing security.", fromFolder,
						CsapUser.currentUsersID() );
					response.getWriter().println( "*** Content masked: can be accessed by infra admins " + fromFolder );
					return;
				}
			}

		}

		String contentType = MediaType.TEXT_PLAIN_VALUE;

		if ( forceText ) {
			contentType = MediaType.TEXT_PLAIN_VALUE;
		} else if ( targetFile.getName().endsWith( ".html" ) ) {
			contentType = "text/html";
		} else if ( targetFile.getName().endsWith( ".xml" ) || targetFile.getName().endsWith( ".jmx" ) ) {
			contentType = MediaType.APPLICATION_XML_VALUE;
		} else if ( targetFile.getName().endsWith( ".json" ) ) {
			contentType = MediaType.APPLICATION_JSON_VALUE;
		} else if ( targetFile.getName().endsWith( ".gif" ) ) {
			contentType = MediaType.IMAGE_GIF_VALUE;
		} else if ( targetFile.getName().endsWith( ".png" ) ) {
			contentType = MediaType.IMAGE_PNG_VALUE;
		} else if ( targetFile.getName().endsWith( ".jpg" ) ) {
			contentType = MediaType.IMAGE_JPEG_VALUE;
		} else if ( targetFile.getName().endsWith( ".gz" ) || targetFile.getName().endsWith( ".zip" ) ) {
			isBinary = true;
		}

		// User is downloading to their desktop
		if ( isBinary ) {
			contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
			response.setContentLength( (int) targetFile.length() );
			response.setHeader( "Content-disposition", "attachment; filename=\""
					+ targetFile.getName() + "\"" );
		}

		response.setContentType( contentType );
		response.setHeader( "Cache-Control", "no-cache" );

		logger.debug( "file: {}", targetFile.getAbsolutePath() );

		csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/file/download",
			CsapUser.currentUsersID(),
			getFileNameForEvents( targetFile, 100 ),
			targetFile.getAbsolutePath() );

		if ( forceText && targetFile.length() > MAX_EDIT_SIZE ) {
			String contents = "Error: selected file has size " + targetFile.length() / 1024
					+ " kb; it exceeds the max allowed of: " + MAX_EDIT_SIZE / 1024
					+ "kb\n Use view or download to access on your desktop;  optionally CSAP upload can be used to update.";

			response.getOutputStream().print( contents );
		} else {
			if ( fromFolder.startsWith( Application.FileToken.DOCKER.value ) ) {
				String dockerTarget = fromFolder.substring( Application.FileToken.DOCKER.value.length() );
				String[] dockerContainerAndPath = splitDockerTarget( dockerTarget );

				long maxSizeForDocker = MAX_EDIT_SIZE;// MAX_EDIT_SIZE ;
				if ( !forceText ) {
					maxSizeForDocker = 500 * maxSizeForDocker; // still limit to
																// avoid heavy
																// reads
				}

				dockerHelper.writeContainerFileToHttpResponse(
					isBinary,
					dockerContainerAndPath[0],
					dockerContainerAndPath[1],
					response,
					maxSizeForDocker,
					CHUNK_SIZE_PER_REQUEST );

				// File tempFile = createCsapUserReadableFile( targetFile );
				// if ( forceText && tempFile.length() > MAX_EDIT_SIZE ) {
				// String contents = "Error: selected file has size " +
				// tempFile.length() / 1024
				// + " kb; it exceeds the max allowed of: " + MAX_EDIT_SIZE /
				// 1024
				// + "kb\n Use view or download to access on your desktop;
				// optionally CSAP upload can be used to update.";
				//
				// response.getOutputStream().print( contents );
				// } else {
				// writeFileToOutputStream( response, tempFile );
				// }
				// tempFile.delete();

			} else {
				if ( !targetFile.canRead() ) {
					addUserReadPermissions( targetFile );
				}
				writeFileToOutputStream( response, targetFile );
			}
		}

	}

	// private File createCsapUserReadableFile ( File targetFile ) {
	// File tempFileWithReadPermissions = new File( csapApp.getStagingDir(),
	// "/temp/" + System.currentTimeMillis() );
	// try {
	// List<String> lines = Arrays.asList(
	// "#!/bin/bash",
	// " cp " + targetFile.getAbsolutePath() + " " +
	// tempFileWithReadPermissions.getAbsolutePath(),
	// " chown " + csapApp.getAgentRunUser() + " " +
	// tempFileWithReadPermissions.getAbsolutePath(),
	// "" );
	// String[] lsOutputLines = runScriptAndUseRootIfPermitted( "cpTemp", lines
	// );
	//
	// logger.debug( "output for temporary read", Arrays.asList( lsOutputLines )
	// );
	//
	// } catch (Exception ex) {
	// logger.error( "Failed reading file {}, {}",
	// targetFile.getAbsolutePath(), Application.getCsapFilteredStackTrace( ex,
	// "csap" ) );
	// }
	//
	// return tempFileWithReadPermissions;
	// }

	private void writeFileToOutputStream ( HttpServletResponse response, File targetFile )
			throws IOException {
		try (DataInputStream in = new DataInputStream( new FileInputStream(
			targetFile.getAbsolutePath() ) );
				ServletOutputStream servletOutputStream = response.getOutputStream();) {

			byte[] bbuf = new byte[ BYTE_DOWNLOAD_CHUNK ];

			int numBytesRead;
			long startingMax = targetFile.length();
			long totalBytesRead = 0L; // hook for files that are being updated

			while ((in != null) && ((numBytesRead = in.read( bbuf )) != -1)
					&& (startingMax > totalBytesRead)) {

				totalBytesRead += numBytesRead;
				servletOutputStream.write( bbuf, 0, numBytesRead );
				servletOutputStream.flush();
			}

		} catch (Exception e) {
			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.error( "Failed accessing file: {}", reason );
			response.getWriter().println(
				"Failed accessing file: " + targetFile + "\n Validate OS file system permissions" );
		}
	}

	final static long MAX_EDIT_SIZE = CSAP.MB_FROM_BYTES * 1;

	public final static String EDIT_URL = "/editFile";
	public final static String SAVE_URL = "/saveChanges";

	private final boolean isDockerFolder ( String fromFolder ) {
		return fromFolder.startsWith( Application.FileToken.DOCKER.value );
	}

	@RequestMapping ( { EDIT_URL, SAVE_URL } )
	public String editFile (
								ModelMap modelMap,
								@RequestParam ( value = "fromFolder" , required = false ) String fromFolder,
								@RequestParam ( value = "chownUserid" , required = false ) String chownUserid,
								@RequestParam ( value = CSAP.SERVICE_PORT_PARAM , required = false ) String svcNamePort,
								@RequestParam ( value = "contents" , required = false , defaultValue = "" ) String contents,
								HttpSession session )
			throws IOException {

		setCommonAttributes( modelMap, session, "File Edit" );
		modelMap.addAttribute( "serviceName", svcNamePort );
		modelMap.addAttribute( "fromFolder", fromFolder );
		modelMap.addAttribute( "osUsers", HostPortal.buildOsUsersList( CSAP_USER ) );

		if ( svcNamePort != null && svcNamePort.equals( "null" ) ) {
			svcNamePort = null;
		}

		File targetFile = csapApp.getRequestedFile( fromFolder, svcNamePort, false );

		modelMap.addAttribute( "targetFile", targetFile );

		if ( isDockerFolder( fromFolder ) ) {
			String dockerTarget = fromFolder.substring( Application.FileToken.DOCKER.value.length() );
			String[] dockerContainerAndPath = splitDockerTarget( dockerTarget );

			contents = dockerHelper.writeContainerFileToString(
				dockerContainerAndPath[0],
				dockerContainerAndPath[1],
				MAX_EDIT_SIZE,
				CHUNK_SIZE_PER_REQUEST ).toString();

		} else if ( targetFile == null || !targetFile.exists() ) {
			logger.warn( "Request file system does not exist: {}.  Check if {}  is bypassing security: ",
				targetFile.getCanonicalPath(), CsapUser.currentUsersID() );
			contents = "Invalid path: " + fromFolder;
		} else {

			logger.info( "targetFile: {}, length: {},  contents length: {}", targetFile, targetFile.length(),
				contents.length() );

			// Only allow infra admin to view security files.
			if ( !csapSecurityConfiguration
				.getAndStoreUserRoles( session )
				.contains( CsapSecurityConfiguration.INFRA_ROLE )
					&& (isInfraOnlyFile( targetFile )) ) {
				logger.warn( "Attempt to access security file: {}. Check if {}  is bypassing security",
					fromFolder, CsapUser.currentUsersID() );
				contents = "*** Content masked: can be accessed by infra admins: " + fromFolder;

			} else if ( contents.length() == 0 ) {

				if ( targetFile.length() > MAX_EDIT_SIZE ) {
					contents = "Error: selected file has size " + targetFile.length() / 1024
							+ " kb; it exceeds the max allowed of: " + MAX_EDIT_SIZE / 1024
							+ "kb\n CSAP download can be used to edit or view on desktop, then CSAP upload can be used.";
				} else {
					if ( !targetFile.canRead() ) {
						addUserReadPermissions( targetFile );
						modelMap.addAttribute( "rootFile", "found" );
					}
					contents = FileUtils.readFileToString( targetFile );
					// } else {
					// File tempFile = createCsapUserReadableFile( targetFile );
					// contents = FileUtils.readFileToString( tempFile );
					// tempFile.delete();
					// modelMap.addAttribute( "rootFile", "found" );
					// }
				}

				csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/file/edit",
					CsapUser.currentUsersID(),
					getFileNameForEvents( targetFile, 100 ),
					targetFile.getAbsolutePath() );
			} else {
				saveUpdatedFile( modelMap, chownUserid, svcNamePort, contents, targetFile );
			}
		}

		modelMap.addAttribute( "contents", contents );

		return CsapCoreService.FILE_URL + "/file-edit";
	}

	public static final String CSAP_SECURITYPROPERTIES = "csapSecurity.properties";
	public static final String CSAPTOKEN = "csap.token";

	private boolean isInfraOnlyFile ( File targetFile ) {

		String filePath = Application.filePathAllOs( targetFile );
		if ( filePath.endsWith( CSAPTOKEN ) ) {
			return true;
		}
		if ( filePath.endsWith( CSAP_SECURITYPROPERTIES ) ) {
			return true;
		}
		if ( filePath.endsWith( ".yml" ) ) {
			String processing = Application.filePathAllOs( csapApp.getProcessingDir() );
			if ( filePath.matches( processing + ".*admin.*application.*yml" ) ) {
				return true;
			}
			if ( filePath.matches( processing + ".*CsAgent.*application.*yml" ) ) {
				return true;
			}
		}
		if ( csapApp.isRunningOnDesktop() && filePath.endsWith( ".sh" ) ) {
			String processing = Application.filePathAllOs( csapApp.getProcessingDir() );
			Pattern p = Pattern.compile( processing + ".*pTemp.*open.*sh" );
			Matcher m = p.matcher( filePath );
			logger.info( " Checking pattern {} for file in {}", p.toString(), filePath );
			if ( m.matches() ) {
				return true;
			}
		}

		return false;
	}

	private void saveUpdatedFile (	ModelMap modelMap, String chownUserid, String svcNamePort, String contents,
									File targetFile )
			throws IOException {
		// Updated file provided

		ArrayNode resultLinesJson = jacksonMapper.createArrayNode();
		resultLinesJson.add( "Updating File: " + targetFile.getAbsolutePath() );

		csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/edit/save",
			CsapUser.currentUsersID(),
			getFileNameForEvents( targetFile, 100 ),
			targetFile.getAbsolutePath() );

		if ( svcNamePort != null ) {
			String desc = csapApp.getServiceInstanceCurrentHost( svcNamePort ).getServiceName() + "/edit";

			csapEventClient.generateEvent( CsapEventClient.CSAP_SVC_CATEGORY + "/" + desc, CsapUser.currentUsersID(),
				getFileNameForEvents( targetFile, 100 ),
				targetFile.getAbsolutePath() );
		} else if ( Application.isRunningOnDesktop() ) {
			File backupFile = new File( targetFile.getAbsolutePath() + "."
					+ CsapUser.currentUsersID() + "."
					+ System.currentTimeMillis() );
			if ( !backupFile.exists() ) {
				targetFile.renameTo( backupFile );
			}
			try (FileWriter fstream = new FileWriter( targetFile );
					BufferedWriter out = new BufferedWriter( fstream );) {
				// Create file

				out.write( contents );
			}
		}

		File tempFolder = Application.getStagingFile( "/temp/" ) ; 

		if ( !tempFolder.exists() ) {
			tempFolder.mkdirs();
		}

		File tempLocation = new File( tempFolder, "_" + targetFile.getName() );
		try (FileWriter fstream = new FileWriter( tempLocation );
				BufferedWriter out = new BufferedWriter( fstream );) {
			// Create file

			out.write( contents );

		}
		List<String> parmList = new ArrayList<String>();

		File scriptPath = Application.getStagingFile( "/bin/editAsRoot.sh"  ) ; 
		if ( Application.isRunningAsRoot() ) {
			parmList.add( "/usr/bin/sudo" );
			parmList.add( scriptPath.getAbsolutePath() );
			parmList.add( tempLocation.getAbsolutePath() );
			parmList.add( targetFile.getAbsolutePath() );
			parmList.add( chownUserid );
			parmList.add( CsapUser.currentUsersID() );
		} else {
			parmList.add( "bash" );
			parmList.add( "-c" );

			parmList.add( scriptPath.getAbsolutePath()
					+ " " + tempLocation.getAbsolutePath()
					+ " " + targetFile.getAbsolutePath()
					+ " " + chownUserid
					+ " " + CsapUser.currentUsersID() );

		}

		String[] lines = osCommandRunner.executeString( null, parmList ).split(
			System.getProperty( "line.separator" ) );

		for ( String line : lines ) {
			if ( line.indexOf( OsCommandRunner.HEADER_TOKEN ) == -1 ) {
				resultLinesJson.add( line );
			}
		}

		logger.debug( "result: {}", resultLinesJson );

		// Try with resources closes file, so we can then issue bash
		// commands on it.
		// hook for editing scripts. convert pasted chars if needed and
		// chmod file
		// List<String> parmList = Arrays.asList("bash", "-c", "dos2unix "
		// + targetFile.getAbsolutePath() + "; chmod 755 "
		// + targetFile.getAbsolutePath());
		//
		// String result = osCommandRunner.executeString(parmList,
		// csapApp.getProcessingDir());
		// logger.info("File upload permisions change result: \n" + result);
		modelMap.addAttribute( "result", resultLinesJson );
	}

	public final static String LAST_LINE_SESSION = "lastLineInSession";

	public final static String LOG_FILE_OFFSET_PARAM = "logFileOffset";
	public final static String LOG_SELECT_PARAM = "logSelect";
	public final static String PROP_SELECT_PARAM = "propSelect";

	final static String EOL = System.getProperty( "line.separator" );

	final static int EOL_SIZE_BYTES = EOL.length();

	// final static int DEFAULT_TAIL = 1024 * 50;
	public final static int CHUNK_SIZE_PER_REQUEST = 1024 * 100;// 500k/time:
	// Some browsers will choke on large chunks.

	public final static int NUM_BYTES_TO_READ = 1024;// 5k/time
	public final static String PROGRESS_TOKEN = "*Progress:";
	public final static String OFFSET_TOKEN = "*Offset:";

	/**
	 *
	 * Key Use Cases: 1) Used to tail log files on FileMonitor UI 2) Used on
	 * MANY ui s to tail results of commands while they are running. This
	 * provides feedback to users
	 *
	 * @param fromFolder
	 * @param bufferSize
	 * @param hostNameArray
	 * @param svcName
	 * @param isLogFile
	 * @param offsetLong
	 * @param request
	 * @param response
	 * @throws IOException
	 */
	@RequestMapping ( value = { "/getFileChanges" } , produces = MediaType.APPLICATION_JSON_VALUE )
	@ResponseBody
	public ObjectNode getFileChanges (
										@RequestParam ( value = "fromFolder" , required = false ) String fromFolder,
										@RequestParam ( defaultValue = "0" ) int dockerLineCount,
										@RequestParam ( defaultValue = "0" ) int dockerSince,
										@RequestParam ( value = "bufferSize" , required = true ) long bufferSize,
										@RequestParam ( value = CSAP.HOST_PARAM , required = false ) String hostName,
										@RequestParam ( value = CSAP.SERVICE_PORT_PARAM , required = false ) String svcName,
										@RequestParam ( value = "isLogFile" , required = false , defaultValue = "false" ) boolean isLogFile,
										@RequestParam ( value = LOG_FILE_OFFSET_PARAM , required = false , defaultValue = "-1" ) long offsetLong,
										HttpServletRequest request, HttpSession session )
			throws IOException {

		if ( hostName == null ) {
			hostName = Application.getHOST_NAME();
		}

		File targetFile = csapApp.getRequestedFile( fromFolder, svcName, isLogFile );
		if ( session.getAttribute( CsapCoreService.FILE_URL + targetFile.getName() ) == null ) {
			session.setAttribute( CsapCoreService.FILE_URL + targetFile.getName(), "AccessLogged" );
			// auditRecord("metricsBrowser", "UI Launched");
			csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/file/tail", CsapUser.currentUsersID(),
				"User interface: " + targetFile.getAbsolutePath(), "" );
			activeUsers.updateUserAccessAndReturnAllActive( CsapUser.currentUsersID(), false );
		}

		// varying security levels based on files being accessed
		// - service logs can be views by view users
		// - anything else: requires admin and/or infra
		if ( !csapSecurityConfiguration.getAndStoreUserRoles( session )
			.contains( CsapSecurityConfiguration.ADMIN_ROLE ) ) {
			// run secondary check
			if ( !targetFile.getCanonicalPath().startsWith(
				csapApp.getProcessingDir().getCanonicalPath() ) ) {
				logger.warn( "Attempt to access file system: {}. Only {} is permitted. Check if {}  is bypassing security: ",
					targetFile.getCanonicalPath(), csapApp.getProcessingDir().getCanonicalPath(), CsapUser.currentUsersID() );
				ObjectNode errorResponse = jacksonMapper.createObjectNode();
				errorResponse
					.put( "error", "*** Content protected: can be accessed by admins " + fromFolder );
				return errorResponse;
			}
		}
		// Only allow infra admin to view security files.
		if ( !csapSecurityConfiguration.getAndStoreUserRoles( session )
			.contains( CsapSecurityConfiguration.INFRA_ROLE ) ) {
			// run secondary check
			if ( isInfraOnlyFile( targetFile ) ) {
				logger.warn( "Attempt to access security file: {}. Check if {}  is bypassing security.", fromFolder, CsapUser.currentUsersID() );
				ObjectNode errorResponse = jacksonMapper.createObjectNode();
				errorResponse
					.put( "error", "*** Content masked: can be accessed by infra admins " + fromFolder );
				return errorResponse;
			}
		}

		// generate audit records as needed
		@SuppressWarnings ( "unchecked" )
		ArrayList<String> fileList = (ArrayList<String>) request.getSession()
			.getAttribute( "FileAcess" );

		if ( CSAP.HOST_PARAM == null
				&& (fileList == null || !fileList.contains( targetFile.getAbsolutePath() )) ) {

			if ( fileList == null ) {
				fileList = new ArrayList<String>();
				request.getSession().setAttribute( "FileAcess", fileList );
			}
			fileList.add( targetFile.getAbsolutePath() );

			csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/file/tail",
				CsapUser.currentUsersID(),
				getFileNameForEvents( targetFile, 100 ),
				targetFile.getAbsolutePath() );
		}

		if ( fromFolder.startsWith( Application.FileToken.DOCKER.value ) ) {
			return tailUsingDocker( fromFolder, dockerLineCount, dockerSince );

			// } else if ( rootLineCount > 0
			// && csapSecurityConfiguration.getAndStoreUserRoles( session
			// ).contains( CsapSecurityConfiguration.ADMIN_ROLE ) ) {
			// // ObjectNode errorResponse = jacksonMapper.createObjectNode();
			// // errorResponse
			// // .put( "error", "*** Running root level script to access " +
			// // targetFile.getAbsolutePath() );
			// // return errorResponse;
			// return tailUsingRoot( targetFile, rootLineCount );

		} else {
			if ( targetFile == null || !targetFile.isFile() || !targetFile.canRead() ) {
				addUserReadPermissions( targetFile );
			}

			return readFileChanges( bufferSize, offsetLong, targetFile );
		}

	}

	private ObjectNode tailUsingDocker ( String fromFolder, int numberOfLines, int dockerSince ) {
		ObjectNode fileChangesJson = jacksonMapper.createObjectNode();

		if ( numberOfLines == 0 ) {
			numberOfLines = 50;
		}
		fileChangesJson.put( "source", "docker" );
		ArrayNode contentsJson = fileChangesJson.putArray( "contents" );

		String dockerTarget = fromFolder.substring( Application.FileToken.DOCKER.value.length() );
		String[] dockerContainerAndPath = splitDockerTarget( dockerTarget );

		if ( dockerContainerAndPath[1] == null || dockerContainerAndPath[1].trim().length() == 0 ) {

			// show container logs
			ObjectNode tailResult = dockerHelper
				.containerTail( null, dockerContainerAndPath[0], numberOfLines, dockerSince );

			fileChangesJson.put( "since", tailResult.get( "since" ).asInt() );

			String[] tailLines = tailResult.get( "plainText" ).asText().split( "\n" );
			for ( String line : tailLines )
				contentsJson.add( StringEscapeUtils.escapeHtml4( line ) + "\n" );
		} else {

			// tail on docker file
			String[] tailLines = dockerHelper
				.tailFile(
					dockerContainerAndPath[0], dockerContainerAndPath[1], numberOfLines )
				.split( "\n" );

			for ( String line : tailLines )
				contentsJson.add( StringEscapeUtils.escapeHtml4( line ) + "\n" );
		}
		return fileChangesJson;
	}

	// private ObjectNode tailUsingRoot ( File targetFile, int numberOfLines ) {
	//
	// ObjectNode fileChangesJson = jacksonMapper.createObjectNode();
	//
	// String[] lines = {
	// "#!/bin/bash",
	// "tail -" + numberOfLines + " " + targetFile.getAbsolutePath(),
	// "" };
	//
	// String[] tailLines = runScriptAndUseRootIfPermitted( "tail", lines );
	//
	// fileChangesJson.put( "source", "os" );
	//
	// // getTail(targetFile, offsetLong, dirBuf, response);
	// ArrayNode contentsJson = fileChangesJson.putArray( "contents" );
	//
	// for ( String line : tailLines )
	// contentsJson.add( StringEscapeUtils.escapeHtml4( line ) + "\n" );
	//
	// return fileChangesJson;
	// }

	@Autowired ( required = false )
	CsapSecurityConfiguration securityConfig;

	private void addUserReadPermissions ( File targetFile ) {

		String[] lines = {
				"#!/bin/bash",
				"setfacl -m u:" + csapApp.getAgentRunUser() + ":r " + targetFile.getAbsolutePath(),
				"" };

		String[] result = runScriptAndUseRootIfPermitted( "permissions", lines );

		StringBuilder results = new StringBuilder( "Updating access using setfacl: " + targetFile.getAbsolutePath() );

		results.append( "\nCommand: " + Arrays.asList( lines ) + "\n Result:" + Arrays.asList( result ) );

		File parentFolder = targetFile.getParentFile();

		logger.debug( "parentFolderL {}, exists: {}, canExecute: {}, read: {}",
			parentFolder.getAbsolutePath(), parentFolder.exists(), parentFolder.canExecute(), parentFolder.canRead() );
		int maxDepth = 8;
		while ((maxDepth-- > 0)
				&& (parentFolder != null)
				&& (!parentFolder.canExecute() || !parentFolder.canRead())) {

			String[] parentLines = {
					"#!/bin/bash",
					"setfacl -m u:" + csapApp.getAgentRunUser() + ":rx " + parentFolder.getAbsolutePath(),
					"" };

			String[] parentResult = runScriptAndUseRootIfPermitted( "permissions", parentLines );

			results.append( "\nCommand: " + Arrays.asList( parentLines ) + "\n Result:" + Arrays.asList( parentResult ) );
			parentFolder = parentFolder.getParentFile();
		}

		csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/file/permissions",
			securityConfig.getUserIdFromContext(),
			"Adding read permissions: " + getFileNameForEvents( targetFile, 50 ),
			results.toString() );

		logger.debug( "Result: {}", results );

		return;
	}

	private String getFileNameForEvents ( File targetFile, int size ) {
		String name = targetFile.getAbsolutePath();
		String finalName = targetFile.getAbsolutePath();
		if ( name.length() > size && name.length() >= 50 ) {
			finalName = name.substring( 0, 29 );
			finalName += "_TRIMMED_" + name.substring( name.length() - (size - 31) );
		}
		return finalName;
	}

	public ObjectNode readFileChanges ( long bufferSize, long offsetLong, File targetFile )
			throws IOException, FileNotFoundException {
		ObjectNode fileChangesJson = jacksonMapper.createObjectNode();

		fileChangesJson.put( "source", "java" );
		// getTail(targetFile, offsetLong, dirBuf, response);
		ArrayNode contentsJson = fileChangesJson.putArray( "contents" );

		// || targetFile.getAbsolutePath().contains( "banner" )
		if ( targetFile == null || !targetFile.isFile() || !targetFile.canRead() ) {
			// UI is handling...
			logger.debug( "File not accessible: " + targetFile.getAbsolutePath() );
			fileChangesJson
				.put( "error",
					"Warning: File does not exist or permission to read is denied. Try the root tail option or select another file\n" );

			return fileChangesJson;
		}

		// try with resource
		try (RandomAccessFile randomAccessFile = new RandomAccessFile(
			targetFile, "r" );) {

			long fileLengthInBytes = randomAccessFile.length();

			Long lastPosition = offsetLong;
			if ( lastPosition.longValue() == -1 ) { // -1 means default tail
				// size,
				// -2
				// is show whole file
				if ( fileLengthInBytes < bufferSize ) {
					lastPosition = new Long( 0 ); // start from the start of the
					// file
				} else {
					lastPosition = new Long( fileLengthInBytes - bufferSize );
				}
			} else if ( lastPosition.longValue() == -2 ) { // show whole file
				// without
				// chunking
				lastPosition = new Long( 0 );
			}

			// file may have been rolled by agent - emptying it , compressing
			// contents.
			if ( lastPosition.longValue() > fileLengthInBytes ) {
				contentsJson.add( "FILE ROLLED\n" );
				if ( fileLengthInBytes < bufferSize ) {
					lastPosition = new Long( 0 ); // start from the start of the
					// file
				} else {
					lastPosition = new Long( fileLengthInBytes - bufferSize );
				}
			}
			// Progress info is displayed on 1st line
			if ( offsetLong != -2 ) {
				fileChangesJson.put( "lastPosition", lastPosition.longValue() );
			}

			if ( offsetLong != -2 ) {
				fileChangesJson.put( "fileLength", randomAccessFile.length() );
			}

			// printWriter.print(PROGRESS_TOKEN + " " + lastPosition.longValue()
			// / 1024 + " of " + randomAccessFile.length() / 1024 + " Kb\n");
			// log.info("fileName: " + fileName + " Raf length of file:" +
			// currLength + " Offset:" + lastPosition) ;
			long currPosition = lastPosition.longValue();
			byte[] bufferAsByteArray = new byte[ NUM_BYTES_TO_READ ];
			int numBytes = NUM_BYTES_TO_READ;

			// as the files roll
			String stringReadIn = null;
			randomAccessFile.seek( currPosition ); // this goes to the byte
			// before
			int numBytesSent = 0;

			while (currPosition < fileLengthInBytes) {

				if ( (fileLengthInBytes - currPosition) < NUM_BYTES_TO_READ ) {
					long numBytesLong = fileLengthInBytes - currPosition;
					numBytes = (new Long( numBytesLong )).intValue();
				}

				randomAccessFile.read( bufferAsByteArray, 0, numBytes );

				stringReadIn = new String( bufferAsByteArray, 0, numBytes );
				// System.out.print(" ---- read in" + stringReadIn +
				// " at offset: "
				// + currPosition) ;

				currPosition += numBytes;
				// we stream in the data, to keep server side as lean as
				// possible
				contentsJson.add( StringEscapeUtils.escapeHtml4( stringReadIn ) );
				// sbuf.append(stringReadIn);

				numBytesSent += numBytes; // send on
				if ( numBytesSent > CHUNK_SIZE_PER_REQUEST - 1 ) {
					break; // only send back limited at a time for
				} // responsiveness
			}

			long newOffset = lastPosition.longValue() + numBytesSent;
			// printWriter.print("\n" + OFFSET_TOKEN + " " + newOffset +
			// " Total: "
			// + currLength);

			long numChunks = (fileLengthInBytes / CHUNK_SIZE_PER_REQUEST) + 1;

			fileChangesJson.put( "numChunks", numChunks );
			fileChangesJson.put( "newOffset", newOffset );
			fileChangesJson.put( "currLength", fileLengthInBytes );
		}
		return fileChangesJson;
	}

}
