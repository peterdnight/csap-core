/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.model;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.csap.agent.CSAP;

import org.csap.agent.model.Application.FileToken;
import org.csap.agent.services.DockerJson;
import org.csap.agent.stats.ServiceMeter;
import org.csap.integations.CsapPerformance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *
 * @author someDeveloper
 */
public class ServiceBaseParser extends ServiceBase {

	ObjectMapper jacksonMapper = new ObjectMapper();

	String packageName = "";

	static final Logger logger = LoggerFactory.getLogger( ServiceBaseParser.class );

	// jvisualvm launches, etc
	public final static String ERRORS = "errors";
	public final static String NOT_FOUND = "notFound";
	private final static String NO_JMX_RMI = "noJmxRmiAvailable";

	private Map<ServiceAttributes, JsonNode> definitionAttributes = new HashMap<>();

	public String getAttribute ( ServiceAttributes attribute ) {

		if ( definitionAttributes.containsKey( attribute ) ) {
			return definitionAttributes.get( attribute ).asText( "Not Found" ).trim();
		}
		return NOT_FOUND;
	}

	public String replaceParserVariables ( String input ) {

		if ( input == null ) {
			return null;
		}
		// logger.info( "input: {}", input );

		String result = replaceVariablesAndTrim( input );
		result = result
			.trim()
			.replaceAll(
				Matcher.quoteReplacement( CSAP.SERVICE_PARAMETERS ),
				Matcher.quoteReplacement( getParameters () ) );
		
		return result;
	}

	public String getParameters () {
		// logger.info( "raw: {}", definitionAttributes.get(
		// ServiceAttributes.parameters ) );
		if ( definitionAttributes.containsKey( ServiceAttributes.parameters ) ) {
			return definitionAttributes.get( ServiceAttributes.parameters ).asText( "Not Found" ).trim() ;
		}
		return "";
	}

	public int getAttributeAsNumber ( ServiceAttributes attribute ) {

		if ( definitionAttributes.containsKey( attribute ) ) {
			return definitionAttributes.get( attribute ).asInt( -1 );
		}
		return -1;
	}

	public ObjectNode getAttributeAsObject ( ServiceAttributes attribute ) {

		return (ObjectNode) definitionAttributes.get( attribute );
	}

	public JsonNode getAttributeAsJson ( ServiceAttributes attribute ) {

		return definitionAttributes.get( attribute );
	}

	public String getJavaVersion () {
		String params = getParameters();

		if ( params.contains( "csapJava8" ) ) {
			return "8";
		}
		if ( params.contains( "csapJava7" ) ) {
			return "7";
		}

		return "default";
	}

	public boolean isJmxRmi () {

		String params = getParameters();

		// hook for disabling rmi,
		// https://github.com/csap-platform/csap-core/wiki#updateRefTomcat+Advanced+Configuration
		if ( params.contains( "DnoJmxFirewall" )
				|| isSpringBoot()
				|| getMetaData().contains( NO_JMX_RMI ) ) {
			return false;
		}

		return true;
	}

	public ObjectNode getLifeEnvironmentVariables () {

		ObjectNode vars = getAttributeAsObject( ServiceAttributes.environmentVariables );

		if ( vars != null ) {
			JsonNode lifeJson = vars.at( "/lifecycle/" + Application.getCurrentLifeCycle() );
			if ( !lifeJson.isMissingNode() && lifeJson.isObject() ) {
				return (ObjectNode) lifeJson;
			}
		}
		return jacksonMapper.createObjectNode();
	}

	public final static String SCRIPTS_NEVER_MATCH = "scriptsNeverMatch";

	/**
	 * Used to match ps output to determine process state
	 *
	 * @return
	 */
	@JsonIgnore
	public String getProcessFilter () {

		if ( isScript() || isRemoteCollection() ) {
			return SCRIPTS_NEVER_MATCH;
		}

		String processFilter = getAttribute( ServiceAttributes.processFilter );

		if ( processFilter.equals( NOT_FOUND ) ) {
			if ( isWrapper() ) {
				processFilter = ".*" + getServiceName() + "_" + getPort() + ".*";
			} else {
				processFilter = ".*java.*csapProcessId=" + getServiceName() + "_" + getPort() + ".*";
			}
			// } else if ( isSpringBoot() ) {
			// return ".*java.*" + getServiceName() + "_" + getPort() +
			// ".*--server.port=" + getPort();
			// } else {
			// return ".*catalina.base=.*" + getServiceName() + "_" + getPort()
			// + ".*";
			// }
		}

		logger.debug( "Using: {} ", processFilter );

		return processFilter;
	}

	public static Stream<String> getJsonAttributeStream ( ObjectNode jsonTree ) {

		// handle empty lists
		if ( jsonTree == null ) {
			return (new ArrayList<String>()).stream();
		}

		Iterable<String> jsonIterable = () -> jsonTree.fieldNames();
		return StreamSupport.stream( jsonIterable.spliterator(), false );
	}

	public Stream<String> environmentVariableNames () {

		Stream<String> namesOnlys = getJsonAttributeStream( getAttributeAsObject( ServiceAttributes.environmentVariables ) )
			.filter( name -> {
				return !name.equals( "lifecycle" );
			} );

		return namesOnlys;
	}

	public Stream<String> environmentLifeVariableNames () {
		// return getJsonAttributeStream( getAttributeAsObject(
		// ServiceAttributes.environmentVariables ) );
		ObjectNode lifeVars = getLifeEnvironmentVariables();

		return getJsonAttributeStream( lifeVars );
	}

	public JsonNode getFiles () {
		return getAttributeAsJson( ServiceAttributes.files );
	}

	public ObjectNode getPerformanceConfiguration () {
		return getAttributeAsObject( ServiceAttributes.performanceApplication );
	}

	public boolean isHealthReportConfigured () {

		ObjectNode healthDef = getAttributeAsObject( ServiceAttributes.health );

		if ( healthDef != null && healthDef.has( "reportMbean" ) && healthDef.has( "reportAttribute" ) ) {
			return true;
		}

		return false;
	}

	public String getHealthReportMbean () {

		if ( isHealthReportConfigured() ) {
			return getAttributeAsObject( ServiceAttributes.health ).get( "reportMbean" ).asText();
		}
		return null;
	}

	public String getHealthReportAttribute () {

		if ( isHealthReportConfigured() ) {
			return getAttributeAsObject( ServiceAttributes.health ).get( "reportAttribute" ).asText();
		}
		return null;
	}

	public boolean isHealthStatusConfigured () {

		ObjectNode healthDef = getAttributeAsObject( ServiceAttributes.health );

		if ( healthDef != null && healthDef.has( "statusMbean" ) && healthDef.has( "statusAttribute" ) ) {
			return true;
		}

		return false;
	}

	public String getHealthStatusMbean () {

		if ( isHealthReportConfigured() ) {
			return getAttributeAsObject( ServiceAttributes.health ).get( "statusMbean" ).asText();
		}
		return null;
	}

	public String getHealthStatusAttribute () {

		if ( isHealthReportConfigured() ) {
			return getAttributeAsObject( ServiceAttributes.health ).get( "statusAttribute" ).asText();
		}
		return null;
	}

	public Stream<String> performanceAttributeNames () {
		return getJsonAttributeStream( getPerformanceConfiguration() );
	}

	public boolean hasJobs () {
		if ( serviceJobs.isEmpty() )
			return false;

		return true;
	}

	private List<LogRotation> logsToRotate = new ArrayList<LogRotation>();

	public List<LogRotation> getLogsToRotate () {
		return logsToRotate;
	}

	@JsonIgnoreProperties ( ignoreUnknown = true )
	public static class LogRotation {
		public String getPath () {
			return path;
		}

		public void setPath ( String path ) {
			this.path = path;
		}

		public String getLifecycles () {
			return lifecycles;
		}

		public void setLifecycles ( String lifecycle ) {
			this.lifecycles = lifecycle;
		}

		public String getSettings () {
			return settings;
		}

		public void setSettings ( String settings ) {
			this.settings = settings;
		}

		String path;
		String lifecycles = "all";
		String settings;

		@Override
		public String toString () {
			return "LogRotation [path=" + path + ", lifecycle=" + lifecycles + ", settings=" + settings + "]";
		}

		public boolean isActive () {

			if ( lifecycles.equalsIgnoreCase( "all" ) ||
					lifecycles.equalsIgnoreCase( Application.getCurrentLifeCycle() ) ) {
				return true;
			}

			List<String> selectedLifes = Arrays.asList( getLifecycles().split( "," ) );

			logger.info( "Application.getCurrentLifeCycle: {}", Application.getCurrentLifeCycle() );
			if ( selectedLifes.contains( Application.getCurrentLifeCycle() ) )
				return true;

			return false;
		}

	}

	private List<ServiceJob> serviceJobs = new ArrayList<ServiceJob>();

	@JsonIgnoreProperties ( ignoreUnknown = true )
	public static class ServiceJob {
		String script = "";

		String description = "";

		public void setDescription ( String description ) {
			this.description = description;
		}

		String frequency = "daily";
		String hour = "01";

		boolean pruneEmptyFolders = false;

		public boolean isPruneEmptyFolders () {
			return pruneEmptyFolders;
		}

		public void setPruneEmptyFolders ( boolean pruneEmptyFolders ) {
			this.pruneEmptyFolders = pruneEmptyFolders;
		}

		// max depth for disk clean folders
		int maxDepth = 3;

		public int getMaxDepth () {
			return maxDepth;
		}

		public void setMaxDepth ( int maxDepth ) {
			this.maxDepth = maxDepth;
		}

		public boolean isDiskCleanJob () {
			return isDiskCleanJob;
		}

		public boolean isMatchingJob ( String key ) {
			// logger.info( "Comparing key: {} to {} ", key, getDescription() );
			return description.equals( key );
		}

		public void setDiskCleanJob ( boolean isDiskCleanJob ) {
			this.isDiskCleanJob = isDiskCleanJob;
		}

		public String getPath () {
			return path;
		}

		public int getOlderThenDays () {
			return olderThenDays;
		}

		String path = "";

		public void setPath ( String path ) {
			this.path = path;
		}

		int olderThenDays = -1;
		boolean isDiskCleanJob = false;

		public void setScript ( String script ) {
			this.script = script;
		}

		public String getHour () {
			return hour;
		}

		public String getScript () {
			return script;
		}

		public String getDescription () {
			return description;
		}

		public String getFrequency () {
			return frequency;
		}

		@Override
		public String toString () {
			return "ServiceJob [script=" + script + ", description=" + description + ", frequency=" + frequency + ", hour=" + hour + "]";
		}

		// support hourly or daily at given hour
		public boolean isTimeToRun () {
			if ( getFrequency().trim().equals( "hourly" ) ) {
				return true;
			} else if ( getFrequency().trim().equals( "daily" ) ) {
				LocalTime currentTime = LocalTime.now(); // current time
				String currentHour = currentTime.format( DateTimeFormatter.ofPattern( "HH" ) );
				if ( currentHour.matches( getHour() ) ) {
					return true;
				}
			}
			return false;
		}

	}

	/**
	 * @return the jobs
	 */
	public List<ServiceJob> getJobs () {
		return serviceJobs;
	}

	public ObjectNode getMonitors () {
		return getAttributeAsObject( ServiceAttributes.osAlertLimits );
	}

	public ObjectNode getDockerSettings () {
		return getAttributeAsObject( ServiceAttributes.dockerSettings );
	}

	public boolean isRunningAsRoot () {

		if ( isRunInDockerContainer() ) {
			if ( getDockerSettings() != null && getDockerSettings().has( DockerJson.runUser.key ) ) {
				String user = getDockerSettings().get( DockerJson.runUser.key ).asText();

				if ( user.length() == 0 || user.startsWith( "0" ) ) {
					return true;
				}
			}
		}
		return false;
	}

	public String getDockerContainerName () {

		if ( getDockerSettings() != null && getDockerSettings().has( DockerJson.containerName.key ) ) {
			return getDockerSettings().get( DockerJson.containerName.key ).asText();
		}

		return "notFound";
	}

	public String getDockerVersionCommand () {

		if ( getDockerSettings() != null && getDockerSettings().has( DockerJson.versionCommand.key ) ) {
			return getDockerSettings().get( DockerJson.versionCommand.key ).asText();
		}

		return null;
	}

	public String getDockerImageName () {

		if ( getDockerSettings() != null && getDockerSettings().has( DockerJson.imageName.key ) ) {
			return getDockerSettings().get( DockerJson.imageName.key ).asText();
		}

		return "notFound";
	}

	public String getDockerContainerPath () {
		return "/" + replaceVariablesAndTrim( getDockerContainerName() );
	}

	public boolean isRunInDockerContainer () {
		// && isSpringBoot()
		if ( getDockerSettings() != null && isUseDockerJavaContainer() ) {
			return true;
		}
		return false;
	}

	/**
	 * Parsing
	 */
	/**
	 *
	 * resultsBuffer set to null will ignore errors
	 *
	 * @param definitionNode
	 * @param resultsBuffer
	 */
	public void parseDefinition (	String packageName,
									JsonNode definitionNode,
									StringBuffer resultsBuffer ) {

		this.packageName = packageName;
		try {

			ServiceAttributes
				.stream()
				.forEach(
					serviceAttribute -> configureSeviceAttribute(
						serviceAttribute,
						definitionNode,
						resultsBuffer ) );

		} catch (Exception e) {
			logger.error( "{} parsing service {} ",
				getServiceName(),
				CSAP.getCsapFilteredStackTrace( e ) );

			resultsBuffer.append( CSAP.CONFIG_PARSE_ERROR
					+ getErrorHeader()
					+ " could not be parsed." );
		}

	}

	private void configureSeviceAttribute (
											ServiceAttributes attribute,
											JsonNode definitionNode,
											StringBuffer resultsBuffer ) {

		String attributeText = attribute.value;

		if ( definitionNode.has( attributeText ) ) {

			attributeLoad( attribute, definitionNode, resultsBuffer );

		} else {

			attributeMissingMessage( attribute, resultsBuffer );

		}

	}

	private void attributeLoad ( ServiceAttributes attribute, JsonNode definitionNode, StringBuffer resultsBuffer ) {

		String attributeText = attribute.value;
		definitionAttributes.put( attribute, definitionNode.get( attributeText ) );

		switch (attribute) {

		/**
		 * migrated to attributeMap
		 */
		case eolParameters:
			definitionAttributes.put( ServiceAttributes.parameters, definitionNode.get( attributeText ) );
			break;

		case eolEnv:
			definitionAttributes.put( ServiceAttributes.environmentVariables, definitionNode.get( attributeText ) );
			updateServiceParseResults( resultsBuffer, CSAP.CONFIG_PARSE_WARN,
				getErrorHeader() + " has EOL attribute: " + attribute.value
						+ " rename it: " + ServiceAttributes.environmentVariables.value );
			break;

		case environmentVariables:
		case parameters:
		case processFilter:
		case webServerTomcat:
		case webServerReWrite:
		case files:
		case osAlertLimits:
		case dockerSettings:
		case health:
		case notifications:
		case javaAlertWarnings:
			break;

		case scheduledJobs:
			// (ArrayNode) definitionAttributes.get( ServiceAttributes.jobs )
			try {
				buildJobs();
			} catch (Exception e) {
				// Add Warnings
				logger.error( "{} Failed parsing: {}", getServiceName(), CSAP.getCsapFilteredStackTrace( e ) );
				updateServiceParseResults( resultsBuffer, CSAP.CONFIG_PARSE_WARN,
					getErrorHeader() + " not able to parse: " + attribute.value );
			}
			break;

		case performanceApplication:
			buildServiceMeters( resultsBuffer );
			break;

		case useDockerJavaContainer:
			setUseDockerJavaContainer( definitionNode.path( attributeText ).asBoolean( false ) );
			break;
		/**
		 * Using explicit member
		 */
		case serviceType:
			configureServiceType( definitionNode, attributeText, resultsBuffer );
			break;

		case startOrder:
			setAutoStart( definitionNode.path( attributeText ).asText() );
			break;

		case folderToMonitor:
			setDisk( definitionNode.path( attributeText ).asText() );
			break;

		case description:
			setDescription( definitionNode.path( attributeText ).asText() );
			break;

		case serviceUrl:
			setUrl( definitionNode.path( attributeText ).asText() );
			break;

		case documentation:
			setDocUrl( definitionNode.path( attributeText ).asText() );
			break;

		case osProcessPriority:
			setOsProcessPriority( definitionNode.path( attributeText ).asText() );
			break;

		case logFolder:
			setLogDirectory( definitionNode.path( attributeText ).asText() );
			break;

		case logDefaultFile:
			setDefaultLogToShow( definitionNode.path( attributeText ).asText() );
			break;

		case logFilter:
			setLogRegEx( definitionNode.path( attributeText ).asText() );
			break;

		case propertyFolder:
			setPropDirectory( definitionNode.path( attributeText ).asText() );
			break;

		case libraryFolder:
			setLibDirectory( definitionNode.path( attributeText ).asText() );
			break;

		case metaData:
			setMetaData( definitionNode.path( attributeText ).asText() );
			break;

		case remoteCollections:
			break;

		case deployFromSource:
			configureDeployFromSource( definitionNode, attributeText );
			break;

		case deployFromRepository:
			configureDeployFromRepo( definitionNode, attributeText, resultsBuffer );
			break;

		case deployTimeMinutes:
			setDeployTimeOutMinutes( definitionNode.path( attributeText ).asText() );
			break;

		case javaJmxPort:
			setJmxPort( definitionNode.path( attributeText ).asText() );
			break;

		case simonMbean:
			setSimonMbean( definitionNode.path( attributeText ).asText() );
			break;

		case servletContext:
			setContext( definitionNode.path( attributeText ).asText() );
			break;

		case servletThreads:
			setServletThreadCount( definitionNode.path( attributeText ).asText() );
			break;

		case servletAccept:
			setServletAccept( definitionNode.path( attributeText ).asText() );
			break;

		case servletMaxConnections:
			setServletMaxConnections( definitionNode.path( attributeText ).asText() );
			break;

		case servletTimeoutMs:
			setServletTimeoutMs( definitionNode.path( attributeText ).asText() );
			break;

		case cookieName:
			setCookieName( definitionNode.path( attributeText ).asText() );
			break;

		case cookiePath:
			setCookiePath( definitionNode.path( attributeText ).asText() );
			break;

		case cookieDomain:
			setCookieDomain( definitionNode.path( attributeText ).asText() );
			break;

		case httpCompression:
			setCompression( definitionNode.path( attributeText ).asText() );
			break;

		case httpCompressTypes:
			setCompressableMimeType( definitionNode.path( attributeText ).asText() );
			break;

		default:
			updateServiceParseResults( resultsBuffer, CSAP.CONFIG_PARSE_WARN,
				getErrorHeader() + "Unexpected attribute: " + attribute );
			break;
		}
	}

	public ObjectNode getJobsDefinition ()
			throws Exception {
		return getAttributeAsObject( ServiceAttributes.scheduledJobs );
	}

	private void buildJobs () {
		ObjectNode jobs = getAttributeAsObject( ServiceAttributes.scheduledJobs );

		if ( jobs.has( "scripts" ) ) {
			jobs.get( "scripts" ).forEach( scripts -> {
				try {
					logger.debug( "Job: {}", scripts );
					ServiceJob serviceJob = jacksonMapper.treeToValue( scripts, ServiceJob.class );
					serviceJob.setScript( replaceVariablesAndTrim( serviceJob.getScript() ) );
					if ( serviceJob.getDescription().isEmpty() ) {
						serviceJob.setDescription( "Service Script: " + serviceJob.getScript() );
					}
					logger.debug( "{} loaded job: {}",
						getServiceName_Port(), serviceJob );
					serviceJobs.add( serviceJob );
				} catch (Exception e) {
					logger.error( "{} Failed parsing jobs: {}",
						getServiceName_Port(),
						CSAP.getCsapFilteredStackTrace( e ) );
				}

			} );
		}
		if ( jobs.has( "diskCleanUp" ) ) {
			jobs.get( "diskCleanUp" ).forEach( diskCleanUp -> {
				try {
					logger.debug( "Job: {}", diskCleanUp );
					ServiceJob serviceJob = jacksonMapper.treeToValue( diskCleanUp, ServiceJob.class );
					if ( !serviceJob.getPath().isEmpty() && serviceJob.getOlderThenDays() > 0 ) {
						serviceJob.setPath( replaceVariablesAndTrim( serviceJob.getPath() ) );
						if ( serviceJob.getDescription().isEmpty() ) {
							serviceJob.setDescription( "Disk CleanUp: " + serviceJob.getPath() );
						}
						serviceJob.setDiskCleanJob( true );
						logger.debug( "{} loaded job: {}",
							getServiceName_Port(), serviceJob );
						serviceJobs.add( serviceJob );
					}
				} catch (Exception e) {
					logger.error( "{} Failed parsing jobs: {}",
						getServiceName_Port(),
						CSAP.getCsapFilteredStackTrace( e ) );
				}

			} );
		}
		if ( jobs.has( "logRotation" ) ) {
			jobs.get( "logRotation" ).forEach( logRotationConfig -> {
				try {
					logger.debug( "Job: {}", logRotationConfig );
					LogRotation logRotation = jacksonMapper.treeToValue( logRotationConfig, LogRotation.class );
					String logPath = replaceVariablesAndTrim( logRotation.getPath() );
					logPath = logPath.trim().replaceAll( "\\$logFolder", getLogWorkingDirectory().getAbsolutePath() );
					logRotation.setPath( logPath );
					logger.debug( "{} loaded logRotation: {}",
						getServiceName_Port(), logRotation );
					logsToRotate.add( logRotation );
				} catch (Exception e) {
					logger.error( "{} Failed parsing jobs: {}",
						getServiceName_Port(),
						CSAP.getCsapFilteredStackTrace( e ) );
				}

			} );
		}
	}

	public File getLogWorkingDirectory () {
		File logDir = new File( getWorkingDirectory(), "/" + getLogDirectory() );

		if ( getLogDirectory()
			.startsWith( "/" ) ) {
			logDir = new File( getLogDirectory() );
		}

		logger.debug( "{} log directory: {}", getServiceName_Port(), logDir.getAbsolutePath() );

		if ( isDockerContainer() ) {
			logDir = new File( FileToken.DOCKER.value, getDockerContainerPath() );
		} else if ( Application.isRunningOnDesktop() ) {
			logger.debug( "Stubbing logs on desktop" );
			logDir = new File( "logs" );
		}
		return logDir;
	}

	private File getWorkingDirectory () {

		if ( Application.isRunningOnDesktop() ) {
			return new File( Application.getPROCESSING() );
		}

		return new File( Application.getPROCESSING(), "/" + getServiceName_Port() );

	}

	private void attributeMissingMessage ( ServiceAttributes attribute, StringBuffer resultsBuffer ) {
		switch (attribute) {

		case serviceUrl:
		case startOrder:
		case folderToMonitor:
		case osProcessPriority:
		case environmentVariables:
		case description:
		case documentation:
		case eolEnv:
		case parameters:
		case eolParameters:
		case osAlertLimits:
		case dockerSettings:
		case useDockerJavaContainer:
		case health:
		case notifications:
		case performanceApplication:
		case javaAlertWarnings:
		case javaJmxPort:
		case simonMbean:
		case scheduledJobs:
		case deployTimeMinutes:
		case logFolder:
		case logDefaultFile:
		case logFilter:
		case propertyFolder:
		case remoteCollections:
		case libraryFolder:
		case servletContext:
		case servletThreads:
		case servletAccept:
		case servletMaxConnections:
		case servletTimeoutMs:
		case cookieName:
		case cookiePath:
		case cookieDomain:
		case httpCompression:
		case httpCompressTypes:
		case webServerTomcat:
		case webServerReWrite:
		case files:
		case metaData:
			// optional params do not need to be present
			break;

		case serviceType:
			updateServiceParseResults(
				resultsBuffer,
				CSAP.CONFIG_PARSE_ERROR,
				getErrorHeader()
						+ " Missing required attribute: " + attribute.value + " decription: " + attribute );

			break;

		case processFilter:
			if ( isWrapper() && !isScript() ) {

				updateServiceParseResults(
					resultsBuffer,
					CSAP.CONFIG_PARSE_WARN,
					getErrorHeader()
							+ " Missing Attribute: " + attribute + " It is strongly recommended to set" );
			}
			break;

		case deployFromRepository:
		case deployFromSource:
			// allow optional deploy options
			// if ( !isDockerContainer() && !isOs() && !isRemoteCollection() ) {
			//
			// updateServiceParseResults(
			// resultsBuffer,
			// CONFIG_PARSE_WARN,
			// getErrorHeader()
			// + " Missing attribute: " + attribute.value + " It is strongly
			// recommended to set" );
			// }
			break;

		// only output warning messages for mandatory attributes
		default:
			updateServiceParseResults(
				resultsBuffer,
				CSAP.CONFIG_PARSE_WARN,
				getErrorHeader()
						+ "Missing Attribute: " + attribute.value + " description: " + attribute );
		}
	}

	public String getErrorHeader () {
		return " Service: " + getServiceName() + "(" + packageName + ") - ";
	}

	public static void updateServiceParseResults ( StringBuffer resultsBuffer, String messageType, String messageDescription ) {
		if ( resultsBuffer != null
				&& resultsBuffer.indexOf( messageDescription ) == -1 ) {
			resultsBuffer.append( "\n" );
			resultsBuffer.append( messageType );
			resultsBuffer.append( messageDescription );
			resultsBuffer.append( "\n" );
		}
	}

	private boolean applicationHealthMeter = false;
	private ObjectNode httpMeterCollectionConfig = null;

	public boolean hasHttpCollection () {
		return httpMeterCollectionConfig != null;
	}

	private ObjectNode serviceMeterTitles = jacksonMapper.createObjectNode();
	private List<ServiceMeter> serviceMeters = new ArrayList<>();

	public List<ServiceMeter> getServiceMeters () {
		return serviceMeters;
	}

	public boolean hasServiceMeters () {
		return serviceMeters.size() > 0;
	}

	public boolean hasMeter ( String id ) {

		Optional<ServiceMeter> theMeter = getServiceMeters().stream().filter( meter -> meter.getCollectionId().equals( id ) ).findFirst();

		if ( theMeter.isPresent() ) {
			return true;
		}

		return false;
	}

	// public ServiceMeter getServiceMeter( String id) {
	//
	// Optional<ServiceMeter> theMeter = getServiceMeters().stream().filter(
	// meter -> meter.getCollectionId().equals( id ) ).findFirst() ;
	//
	// if ( theMeter.isPresent() ) {
	// return theMeter.get() ;
	// }
	//
	// return null;
	// }
	private void buildServiceMeters ( StringBuffer resultsBuf ) {

		ObjectNode serviceMetersDefinition = getPerformanceConfiguration();

		if ( serviceMetersDefinition.has( "config" ) && serviceMetersDefinition.get( "config" ).isObject() ) {
			// http collection
			boolean httpCollection = true;
			if ( !serviceMetersDefinition.get( "config" ).has( "httpCollectionUrl" ) ) {
				httpCollection = false;
				updateServiceParseResults( resultsBuf, CSAP.CONFIG_PARSE_WARN,
					getErrorHeader()
							+ "Invalid http configuration: Missing attribute: httpCollectionUrl" );
			}

			if ( !serviceMetersDefinition.get( "config" ).has( "patternMatch" ) ) {

				httpCollection = false;
				updateServiceParseResults( resultsBuf, CSAP.CONFIG_PARSE_WARN,
					getErrorHeader()
							+ "Invalid http configuration: Missing attribute: patternMatch" );

			}
			if ( httpCollection ) {
				httpMeterCollectionConfig = (ObjectNode) serviceMetersDefinition.get( "config" );
			}
		}

		performanceAttributeNames()
			.filter( name -> isAlphaNumeric( resultsBuf, serviceMetersDefinition, name ) )
			.filter( name -> !"config".equals( name ) )
			.map( metricId -> buildServiceMeter( metricId, serviceMetersDefinition, resultsBuf ) )
			.filter( serviceMeter -> serviceMeter != null )
			.forEach( serviceMeter -> {
				serviceMeters.add( serviceMeter );
				if ( serviceMeter.getCollectionId().equals( ServiceAlertsEnum.JAVA_HEARTBEAT ) ) {
					setApplicationHealthMeter( true );
				}
			} );

		// Add in health status if it is configured - overwriting existing.
		if ( isHealthStatusConfigured() ) {
			ServiceMeter healthMeter = new ServiceMeter( ServiceAlertsEnum.JAVA_HEARTBEAT, "HeartBeat Response (ms)",
				getHealthStatusMbean(), getHealthStatusAttribute() );
			serviceMeters.add( healthMeter );
			setApplicationHealthMeter( true );
		}

		//
		serviceMeters.forEach( meter -> {
			serviceMeterTitles.put( meter.getCollectionId(), meter.getTitle() );
		} );

	}

	private boolean isAlphaNumeric ( StringBuffer resultsBuf, ObjectNode serviceMetersDefinition, String name ) {
		if ( !StringUtils.isAlphanumeric( name ) ) {

			ObjectNode metricSettings = (ObjectNode) serviceMetersDefinition.get( name );
			metricSettings.put( "errors", true );

			updateServiceParseResults( resultsBuf, CSAP.CONFIG_PARSE_WARN,
				getErrorHeader()
						+ "Invalid attribute name: " + name
						+ ", must be alphaNumeric only. Removing" );

			return false;

		}
		return true;
	}

	private ServiceMeter buildServiceMeter ( String metricId, ObjectNode serviceMetersDefinition, StringBuffer resultsBuf ) {
		ServiceMeter serviceMeter = null;

		JsonNode metricSettings = serviceMetersDefinition.get( metricId );

		if ( serviceMetersDefinition.has( "config" ) ) {
			// http checks
			if ( !metricSettings.has( "attribute" ) ) {

				updateServiceParseResults( resultsBuf, CSAP.CONFIG_PARSE_WARN,
					getErrorHeader()
							+ metricId + " is missing attribute field (http collection)" );
			} else {
				// ServiceMeter
				serviceMeter = new ServiceMeter( metricId, (ObjectNode) metricSettings );
			}
		} else {
			// JMX checks
			if ( metricSettings.has( "mbean" ) && !metricSettings.has( "attribute" ) ) {

				updateServiceParseResults( resultsBuf, CSAP.CONFIG_PARSE_WARN,
					getErrorHeader()
							+ metricId + " is missing attribute field (java collection)" );

			} else if ( !metricSettings.has( "mbean" ) && !metricSettings.has( "simonMedianTime" )
					&& !metricSettings.has( "simonCounter" ) && !metricSettings.has( "simonMaxTime" )
					&& !metricSettings.has( "simonMinTime" ) ) {

				updateServiceParseResults( resultsBuf, CSAP.CONFIG_PARSE_WARN,
					getErrorHeader()
							+ metricId
							+ " Missing or invalid collection type: " + metricSettings
							+ " Expected: mbean, simonCounter,simonMedianTime,simonMinTime, simonMaxTime" );
			} else if ( metricSettings.isObject() ) {
				// ServiceMeter
				serviceMeter = new ServiceMeter( metricId, (ObjectNode) metricSettings );

			}

		}
		if ( metricSettings.has( "divideBy" ) ) {
			if ( metricSettings.get( "divideBy" ).asText().equalsIgnoreCase( "interval" ) ) {
				// interval will be used
			} else {
				double d = metricSettings.get( "divideBy" ).asDouble();
				if ( d == 0 ) {

					updateServiceParseResults( resultsBuf, CSAP.CONFIG_PARSE_WARN,
						getErrorHeader()
								+ metricId
								+ " Invalid divideBy attribute: " + metricSettings
								+ " Resolving to 0" );
				}
			}
		}

		if ( serviceMeter != null && serviceMeter.getSimonId() != null ) {
			// legacy support at cisco
			String mbeanNameCustom = "com.cisco:application=csap,name=SimonManager";

			if ( isSpringBoot() || isDockerContainer() ) {
				mbeanNameCustom = CsapPerformance.SIMON_MBEAN;
			}
			setSimonMbean( mbeanNameCustom );
		}

		if ( serviceMeter != null && serviceMeter.getMbeanName() != null ) {
			serviceMeter.setMbeanName( replaceParserVariables( serviceMeter.getMbeanName() ) );
		}

		return serviceMeter;
	}

	public void configureServiceType ( JsonNode definitionNode, String attributeText, StringBuffer resultsBuffer ) {
		setServerType( definitionNode.path( attributeText ).asText() );

		if ( !isDockerContainer() && !isSpringBoot() && !isTomcatJarsPresent()
				&& !isWrapper() && !isOs() ) {

			updateServiceParseResults(
				resultsBuffer, CSAP.CONFIG_PARSE_WARN,
				getErrorHeader() + "Unexpected: " + attributeText + " found: "
						+ getServerType() );

		}
	}

	public boolean isRemoteCollection () {

		// logger.info("{} , {}", getServiceName() , getAttributeAsJson(
		// ServiceAttributesEnum.remoteCollections )) ;
		if ( getAttributeAsJson( ServiceAttributes.remoteCollections ) != null ) {
			return true;
		}
		return false;
	}

	public boolean configureRemoteCollection ( int collectHostIndex, StringBuffer resultsBuf ) {

		JsonNode remoteCollectionsDefinition = getAttributeAsJson( ServiceAttributes.remoteCollections );

		JsonNode remoteDefinition = remoteCollectionsDefinition.get( collectHostIndex );

		if ( !remoteDefinition.isObject() || !remoteDefinition.has( "host" ) || !remoteDefinition.has( "port" ) ) {
			logger.error( "Invalid configuration: {}", remoteDefinition.toString() );
			updateServiceParseResults(
				resultsBuf, CSAP.CONFIG_PARSE_WARN,
				getErrorHeader()
						+ "Invalid format for " + ServiceAttributes.remoteCollections
						+ " expected: host and port, found: " + remoteCollectionsDefinition.toString() );
			return false;
		}

		setCollectHost( remoteDefinition.get( "host" ).asText() );
		setCollectPort( remoteDefinition.get( "port" ).asText() );
		return true;

	}

	public void configureDeployFromRepo ( JsonNode definitionNode, String attributeText, StringBuffer resultsBuf ) {
		JsonNode mavenNode = definitionNode.get( attributeText );
		if ( mavenNode.has( "dependency" ) ) {
			setMavenId( mavenNode
				.path( "dependency" )
				.asText() );
			if ( getMavenId().split( ":" ).length != 4 ) {
				updateServiceParseResults(
					resultsBuf, CSAP.CONFIG_PARSE_WARN,
					getErrorHeader()
							+ "Invalid format for " + attributeText
							+ " expected: group:artifact:version:type, found: " + getMavenId() );
			}

		}

		if ( mavenNode.has( "repo" ) ) {
			setMavenRepo( mavenNode
				.path( "repo" )
				.asText() );
		}

		if ( mavenNode.has( "secondary" ) ) {
			setMavenSecondary( mavenNode.path( "secondary" ).asText() );
		}
		if ( mavenNode.has( "enableReleaseFile" ) ) {
			setAllowReleaseFileToOverride( mavenNode.path( "enableReleaseFile" ).asBoolean( true ) );
		}
	}

	public void configureDeployFromSource ( JsonNode definitionNode, String attributeText ) {
		JsonNode sourceNode = definitionNode.get( attributeText );
		if ( sourceNode.has( "path" ) ) {
			setScmLocation( sourceNode.path( "path" ).asText() );
		}

		if ( sourceNode.has( "branch" ) ) {
			setDefaultBranch( sourceNode.path( "branch" ).asText() );
		}

		if ( sourceNode.has( "scm" ) ) {
			setScm( sourceNode.path( "scm" ).asText() );
		}

		if ( sourceNode.has( "buildLocation" ) ) {
			setScmBuildLocation( sourceNode.path( "buildLocation" ).asText() );
		}
	}

	public ObjectNode getServiceMeterTitles () {
		return serviceMeterTitles;
	}

	public void setServiceMeterTitles ( ObjectNode serviceMeterTitles ) {
		this.serviceMeterTitles = serviceMeterTitles;
	}

	public ObjectNode getHttpMeterCollectionConfig () {
		return httpMeterCollectionConfig;
	}

	public void setHttpMeterCollectionConfig ( ObjectNode httpConfig ) {
		this.httpMeterCollectionConfig = httpConfig;
	}

	public boolean isApplicationHealthMeter () {
		return applicationHealthMeter;
	}

	public void setApplicationHealthMeter ( boolean applicationHealthMeter ) {
		this.applicationHealthMeter = applicationHealthMeter;
	}
}
