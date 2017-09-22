package org.csap.agent.model;

import java.io.IOException;
import java.net.URI;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.text.WordUtils;
import org.csap.agent.CSAP;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.ServiceBaseParser.LogRotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriTemplate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *
 * Metadata associated with lifecycles
 *
 *
 * @author someDeveloper
 *
 */
public class LifeCycleSettings {

	final static Logger logger = LoggerFactory.getLogger( LifeCycleSettings.class );

	private String helpUrBase = "https://github.com/csap-platform/csap-core/wiki#";

	private int limitSamples = 5; // determines number of items for rolling
									// average

	public LifeCycleSettings( ) {
		newsJsonArray = jacksonMapper.createArrayNode();
		emailJsonArray = jacksonMapper.createArrayNode();

		// default values can be overwritten per cluster, per host, or per
		// service
		serviceLimits.put( ServiceAlertsEnum.diskSpace, 100l );
		serviceLimits.put( ServiceAlertsEnum.diskWriteRate, 10l );
		serviceLimits.put( ServiceAlertsEnum.openFileHandles, 400l );
		serviceLimits.put( ServiceAlertsEnum.httpConnections, 40l );
		serviceLimits.put( ServiceAlertsEnum.memory, convertUnitToKb( "765m" ) );
		serviceLimits.put( ServiceAlertsEnum.sockets, 30l );
		serviceLimits.put( ServiceAlertsEnum.threads, 100l );
		serviceLimits.put( ServiceAlertsEnum.cpu, 200l );

		eolJarPatterns = jacksonMapper.createArrayNode();
		eolJarPatterns.add( "commons-dbcp-1.*.jar" );
		eolJarPatterns.add( "hibernate-core-4.*.jar" );
		eolJarPatterns.add( "hibernate-core-3.*.jar" );
		eolJarPatterns.add( "spring-boot-1.3.*.jar" );
		eolJarPatterns.add( "org.springframework.*-3.*.jar" ); // osgi spring
		eolJarPatterns.add( "spring-core-3.*.jar" );
		eolJarPatterns.add( "spring-security.*-3.*.jar" );
		eolJarPatterns.add( "log4j-1.*jar" );
		// eolJarPatterns.add("EasyCriteria-3.*.jar") ; // testing only
	}

	private ArrayNode eolJarPatterns;

	private ObjectNode settingsDefinition = null;

	public ObjectNode getSettingsDefinition () {
		return settingsDefinition;
	}

	public ObjectNode getReferences () {
		return (ObjectNode) settingsDefinition.get( "references" );
	}

	public void setSettingsDefinition ( ObjectNode settingsDefinition ) {
		this.settingsDefinition = settingsDefinition;
	}

	Application csapApplication;

	public void loadSettings (	ObjectNode inputNode,
								StringBuffer resultsBuf, String platformLifeCycle, Application manager )
			throws IOException {
		// Check if a default version to view for the console is set

		this.csapApplication = manager;

		try {
			JsonNode configNode = inputNode.path( DefinitionParser.PARSER_CLUSTER_DEFN ).path(
				platformLifeCycle );

			// backwards compatability
			if ( configNode.has( "settings" ) ) {
				configNode = configNode.path( "settings" );
			}

			settingsDefinition = (ObjectNode) configNode;

			logger.info( "Parsing Settings for lifeCycle: {} ",
				platformLifeCycle );

			logger.debug( "Parsing Settings for lifeCycle: {}, input: {}",
				platformLifeCycle, configNode );

			processCoreSettings( configNode );

			processCsapData( configNode );

			processMonitors( configNode );

			processMetricsCollection( configNode );
		} catch (IOException e) {
			logger.error( "Failed parsing: ", e );
			throw e;
		} catch (Exception e) {
			logger.error( "Failed parsing: {}", CSAP.getCsapFilteredStackTrace( e ) );
			throw new IOException( "Error while parsing Settings : " + e.getMessage() );
		}

		logger.info( "Completed parsing, Events will be posted to: {}", getEventUrl() );
		logger.debug( "Parsed lifecycle settings for {}, found: \n{}", platformLifeCycle, this.toString() );
	}

	private ArrayNode loadBalanceVmFilter = null;

	public boolean isLoadBalanceVmFilter ( String hostName ) {
		if ( loadBalanceVmFilter == null ) {
			return false;
		}

		try {
			for ( JsonNode hostJson : loadBalanceVmFilter ) {
				if ( hostJson.asText().equals( hostName ) ) {
					return true;
				}
			}
		} catch (Exception e) {
			logger.error( "Failed parsing " + loadBalanceVmFilter, e );
		}
		return false;
	}

	private void processCoreSettings ( JsonNode configNode )
			throws IOException {

		if ( configNode.has( "defaultUiDisplayVersion" ) ) {
			this.defaultUiDisplayVersion = configNode.path(
				"defaultUiDisplayVersion" ).textValue();
		}

		if ( configNode.has( "mavenCommand" ) ) {
			this.mavenCommand = configNode.path(
				"mavenCommand" ).textValue();
		}

		if ( configNode.has( "defaultUiDisplayCluster" ) ) {
			defaultUiDisplayCluster = configNode
				.path( "defaultUiDisplayCluster" ).textValue();
		}

		if ( configNode.has( "autoRestartHttpdOnClusterReload" ) ) {
			if ( configNode.path( "autoRestartHttpdOnClusterReload" )
				.textValue().startsWith( "n" )
					|| configNode.path( "autoRestartHttpdOnClusterReload" )
						.textValue().startsWith( "f" ) ) {
				autoRestartHttpdOnClusterReload = false;
			}
		}

		if ( configNode.has( "portRange" ) ) {
			if ( configNode.path( "portRange" ).has( "start" ) ) {
				setPortStart( configNode.path( "portRange" ).path( "start" ).asInt() );
			}

			if ( configNode.path( "portRange" ).has( "end" ) ) {
				setPortEnd( configNode.path( "portRange" ).path( "end" ).asInt() );
			}
		}

		if ( configNode.has( "secureUrl" ) ) {
			secureUrl = configNode.path( "secureUrl" ).asText();
		}

		if ( configNode.has( "lbUrl" ) ) {
			lbUrl = trimSpacesAndVariables( configNode.path( "lbUrl" ).asText() );
		}
		if ( configNode.has( "monitoringUrl" ) ) {
			monitoringUrl = trimSpacesAndVariables( configNode.path( "monitoringUrl" ).asText() );
		}

		processAgentSettings( configNode );

		if ( configNode.has( "newsItems" ) ) {
			newsJsonArray = (ArrayNode) configNode.get( "newsItems" );
		} else {
			newsJsonArray
				.add( "No news today - Use Capability Editor to publish." );
		}

		if ( configNode.has( "loadBalanceVmFilter" ) ) {
			loadBalanceVmFilter = (ArrayNode) configNode
				.get( "loadBalanceVmFilter" );
		}

		if ( configNode.has( "operatorNotifications" ) ) {
			emailJsonArray = (ArrayNode) configNode
				.get( "operatorNotifications" );
		} else {
			emailJsonArray.add( "csapsupport@yourcompany.com" );
		}

		if ( configNode.has( "launchUrls" ) ) {
			// defaultLifeCycleToVersionViewMap.put(platformLifeCycle,
			// configNode
			// .path(PARSER_CLUSTER_DEFN).path(platformLifeCycle).path("launchUrls").getTextValue())
			// ;
			JsonNode launchNode = configNode.path( "launchUrls" );

			logger.debug( "launchNode is: {} ", launchNode.textValue() );

			for ( Iterator<String> targetIter = launchNode.fieldNames(); targetIter
				.hasNext(); ) {

				String target = targetIter.next().trim();

				labelToServiceUrlLaunchMap.put( target,
					trimSpacesAndVariables( launchNode.path( target ).textValue() ) );
			}

		}
		if ( labelToServiceUrlLaunchMap.size() == 0 ) {
			// default Settings
			labelToServiceUrlLaunchMap.put( "1- http(Tomcat Embed)", "default" );
			labelToServiceUrlLaunchMap.put( "2- ajp(LB)", getLbUrl() );
		}
	}

	private void processMetricsCollection ( JsonNode configNode ) {

		logger.debug( "Entered" );

		if ( configNode.has( "metricsPublication" ) ) {

			if ( configNode.path( "metricsPublication" ).isArray() ) {

				setMetricsPublicationNode( (ArrayNode) configNode.path( "metricsPublication" ) );
				if ( getMetricsPublicationNode().size() > 0 ) {
					setMetricsPublication( true );
				}
			}

		}

		if ( configNode.has( "useCsapMetrics" ) ) {
			setMetricsUrl( trimSpacesAndVariables( configNode.path( "useCsapMetrics" ).asText() ) );
		}

		if ( configNode.has( "metricsCollectionInSeconds" ) ) {
			// defaultLifeCycleToVersionViewMap.put(platformLifeCycle,
			// configNode
			// .path(PARSER_CLUSTER_DEFN).path(platformLifeCycle).path("launchUrls").getTextValue())
			// ;
			JsonNode collectionAndLandingSettings = configNode
				.path( "metricsCollectionInSeconds" );

			if ( collectionAndLandingSettings.has( "uploadIntervalsInHours" ) ) {
				uploadIntervalsInHoursJson = (ObjectNode) collectionAndLandingSettings.get( "uploadIntervalsInHours" );
			} else {
				uploadIntervalsInHoursJson = null;
			}
			if ( collectionAndLandingSettings.has( "realTimeMeters" ) ) {
				realTimeMetersJson = (ArrayNode) collectionAndLandingSettings.get( "realTimeMeters" );
			} else {
				realTimeMetersJson = jacksonMapper.createArrayNode();
			}
			if ( collectionAndLandingSettings.has( "trending" ) ) {
				trendingJson = (ArrayNode) collectionAndLandingSettings.get( "trending" );
			} else {
				trendingJson = jacksonMapper.createArrayNode();
			}

			if ( collectionAndLandingSettings.has( "processDumps" ) ) {
				JsonNode jsonNode = collectionAndLandingSettings.path( "processDumps" );
				psDumpInterval = jsonNode.path( "resouceInterval" )
					.asInt();
				psDumpCount = jsonNode.path( "maxInInterval" ).asInt();
				psDumpLowMemoryInMb = jsonNode.path( "lowMemoryInMb" )
					.asInt();

			}

			if ( collectionAndLandingSettings.has( "inMemoryCacheSize" ) ) {
				JsonNode jsonNode = collectionAndLandingSettings.path( "inMemoryCacheSize" );
				setInMemoryCacheSize( jsonNode.asInt() );
				logger.warn( "Updated in memory cache size: {} ", getInMemoryCacheSize() );
			}

			logger.debug( "metricsNodes is: ", collectionAndLandingSettings.textValue() );

			collectionAndLandingSettings
				.fieldNames()
				.forEachRemaining( fieldName -> {

					if ( !fieldName.equals( Application.COLLECTION_RESOURCE ) &&
							!fieldName.equals( Application.COLLECTION_SERVICE ) &&
							!fieldName.equals( Application.COLLECTION_JMX ) ) {
						return;
					}

					if ( collectionAndLandingSettings.path( fieldName ).isArray() ) {

						for ( JsonNode jsonNode : collectionAndLandingSettings.path( fieldName ) ) {

							if ( jsonNode.isObject() ) {
								metricToSecondsMap.add( fieldName,
									jsonNode.path( "interval" ).asInt() );

							} else if ( jsonNode.asInt() != 0 ) {
								metricToSecondsMap.add( fieldName, jsonNode.asInt() );

							} else {

								logger.warn( "Metrics configuration ignored, only non-0 will be used: "
										+ jsonNode.toString() + " when reading: "
										+ fieldName );
							}
						}

					} else {
						logger.warn( "Found unexpected item in metricsCollectionInSeconds: "
								+ fieldName );
					}
				} );

		}
		// if ( metricToSecondsMap.size() == 0 ) {
		// // default Settings
		// metricToSecondsMap.add( "resource", 300 );
		// }

		logger.debug( "metricToSecondsMap: {}", metricToSecondsMap );
	}

	private ArrayNode trendingJson = null;

	public ArrayNode getTrendingConfig () {
		return trendingJson;
	}

	private ArrayNode realTimeMetersJson = null;

	public ArrayNode getRealTimeMeters () {
		return realTimeMetersJson;
	}

	public String getRealTimeMetersForView () {
		try {
			return jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( getRealTimeMeters() );
		} catch (JsonProcessingException e) {
			return "Failed to Parse JSON";
		}
	}

	private ObjectNode uploadIntervalsInHoursJson = null;

	public ObjectNode getUploadIntervalsInHoursJson () {
		return uploadIntervalsInHoursJson;
	}

	public void setUploadIntervalsInHoursJson ( ObjectNode uploadIntervalsInHoursJson ) {
		this.uploadIntervalsInHoursJson = uploadIntervalsInHoursJson;
	}

	@JsonIgnoreProperties ( ignoreUnknown = true )
	public static class InfraTests {
		public int getCpuIntervalMinutes () {
			return cpuIntervalMinutes;
		}

		public int getCpuLoopsMillions () {
			return cpuLoopsMillions;
		}

		public int getDiskIntervalMinutes () {
			return diskIntervalMinutes;
		}

		public int getDiskWriteMb () {
			return diskWriteMb;
		}

		int cpuIntervalMinutes = 60;
		int cpuLoopsMillions = 1;
		int diskIntervalMinutes = 60;
		int diskWriteMb = 100;

		@Override
		public String toString () {
			return "InfraTests [cpuIntervalMinutes=" + cpuIntervalMinutes + ", cpuLoopsMillions=" + cpuLoopsMillions
					+ ", diskIntervalMinutes=" + diskIntervalMinutes + ", diskWriteMb=" + diskWriteMb + "]";
		}

	}

	private InfraTests infraTests = new InfraTests();

	public InfraTests getInfraTests () {
		return infraTests;
	}

	private void processAgentSettings ( JsonNode configNode )
			throws IOException {

		if ( configNode.has( "numberWorkerThreads" ) ) {
			numberWorkerThreads = configNode.path( "numberWorkerThreads" )
				.asInt();
			if ( numberWorkerThreads <= 0 ) {
				throw new IOException( "Invalid worker thread count: " + configNode.path( "numberWorkerThreads" ) );
			}
			logger.warn( "EOL location of numberWorkerThreads - move to lifecycle/agent" );
		}

		if ( configNode.has( "agent" ) ) {

			JsonNode agentSettings = configNode.path( "agent" );

			if ( agentSettings.has( "infraTests" ) ) {
				try {
					infraTests = jacksonMapper.treeToValue( agentSettings.path( "infraTests" ), InfraTests.class );

				} catch (Exception e) {
					logger.warn( "Failed parsing infraTests, {}", CSAP.getCsapFilteredStackTrace( e ) );
				}
			}

			if ( agentSettings.has( "numberWorkerThreads" ) ) {
				numberWorkerThreads = agentSettings.path( "numberWorkerThreads" ).asInt();
				if ( numberWorkerThreads <= 0 ) {
					throw new IOException( "Invalid worker thread count: " + agentSettings.path( "numberWorkerThreads" ) );
				}
			}
			if ( agentSettings.has( "logRotationMinutes" ) ) {
				logRotationMinutes = agentSettings.path( "logRotationMinutes" ).asLong();
				if ( logRotationMinutes <= 0 ) {
					throw new IOException( "Invalid logRotationMinutes: " + agentSettings.path( "logRotationMinutes" ) );
				}
			}

			if ( agentSettings.has( "adminToAgentTimeoutInMs" ) ) {
				adminToAgentTimeout = agentSettings
					.path( "adminToAgentTimeoutInMs" ).asInt();

				if ( adminToAgentTimeout <= 1000 ) {
					throw new IOException(
						"Invalid agent timeout, must be at least 1000: " + agentSettings.path( "adminToAgentTimeoutInMs" ) );
				}
			}

			if ( agentSettings.has( "apiLocal" ) ) {
				setAgentLocalAuth( agentSettings.path( "apiLocal" ).asBoolean( true ) );
			}

			if ( agentSettings.has( "apiUser" ) ) {
				setAgentUser( trimSpacesAndVariables( agentSettings.path( "apiUser" ).textValue() ) );
			}

			if ( agentSettings.has( "apiPass" ) ) {
				setAgentPass( trimSpacesAndVariables( agentSettings.path( "apiPass" ).textValue() ) );
			}

			if ( agentSettings.has( "iostatFilter" ) ) {
				setIostatDeviceFilter( trimSpacesAndVariables( agentSettings.path( "iostatFilter" ).textValue() ) );
			}

			if ( agentSettings.has( "eolJarPatterns" )
					&& agentSettings.get( "eolJarPatterns" ).isArray()
					&& agentSettings.get( "eolJarPatterns" ).size() > 1 ) {
				eolJarPatterns = (ArrayNode) agentSettings.get( "eolJarPatterns" );
			}

			if ( agentSettings.has( "duIntervalMins" ) ) {
				setDuIntervalMins( agentSettings.path( "duIntervalMins" ).asInt() );
			}

			if ( agentSettings.has( "lsofIntervalMins" ) ) {
				setLsofIntervalMins( agentSettings.path( "lsofIntervalMins" ).asInt() );
			}

			if ( agentSettings.has( "maxJmxCollectionMs" ) ) {
				maxJmxCollectionMs = agentSettings.path( "maxJmxCollectionMs" ).asLong( 2000 );
			}

		}
	}

	public String summarySettings () {
		StringBuilder settings = new StringBuilder();
		settings.append( "\n\t Lifecycle Settings" );
		settings.append( CSAP.pad( "\n\t\t Agent Workers:" ) + getNumberWorkerThreads() );
		settings.append( CSAP.pad( "\n\t\t Agent Timeout:" ) + getAdminToAgentTimeoutSeconds() + " seconds" );
		settings.append( CSAP.pad( "\n\t\t iostat filter:" ) + getIostatDeviceFilter() );
		settings.append( "\n" );
		return settings.toString();
	}

	private void processCsapData ( JsonNode configNode ) {
		if ( configNode.has( "csapData" ) ) {

			JsonNode csapData = configNode.path( "csapData" );

			if ( csapData.has( "user" ) ) {
				setEventUser( 
					resolveVariables( "user",
						csapData.path( "user" ).asText() ) );
			}

			if ( csapData.has( "pass" ) ) {
				setEventPass( 
					resolveVariables( "pass",
						csapData.path( "pass" ).asText() ) );
			}

			if ( csapData.has( "eventUrl" ) ) {
				setEventUrl( 
					resolveVariables( "eventUrl",
						csapData.path( "eventUrl" ).asText() ) );
			}

			if ( csapData.has( "eventMetricsUrl" ) ) {
				setEventMetricsUrl( 
					resolveVariables( "eventMetricsUrl",
						csapData.path( "eventMetricsUrl" ).asText() ) );
			}

			if ( csapData.has( "eventApiUrl" ) ) {
				eventApiUrl = resolveVariables( "eventApiUrl", csapData.path( "eventApiUrl" ).asText() );
			}

			if ( csapData.has( "historyUiUrl" ) ) {
				setHistoryUiUrl(
					resolveVariables( "historyUiUrl",
						csapData.path( "historyUiUrl" ).asText() ) );
			}

			if ( csapData.has( "analyticsUiUrl" ) ) {
				setAnalyticsUiUrl( 
					resolveVariables( "analyticsUiUrl",
						csapData.path( "analyticsUiUrl" ).asText() ) );
			}

		}
	}

	private String resolveVariables ( String keyName, String input ) {

		String result = trimSpacesAndVariables( input );
		if ( result.startsWith( "$" ) ) {
			result = csapApplication
				.getCompanyConfiguration(
					"test.variables." + input.substring( 1 ),
					input );
		}

		logger.debug( "input: {}, result: {}", input, result );
		return result;

	}

	private void processMonitors ( JsonNode configNode ) {
		if ( configNode.has( "monitorDefaults" ) ) {
			JsonNode defaultsNode = configNode.path( "monitorDefaults" );

			if ( defaultsNode.has( "autoStopServiceThreshold" ) ) {
				defaultAutoStopServiceThreshold = defaultsNode.path( "autoStopServiceThreshold" ).asDouble();
			}

			if ( defaultsNode.has( "maxDiskPercent" ) ) {
				defaultMaxDiskPercent = defaultsNode.path( "maxDiskPercent" ).asInt();
			}

			if ( defaultsNode.has( "limitSamples" ) ) {
				limitSamples = defaultsNode.path( "limitSamples" ).asInt();
			}

			if ( defaultsNode.has( "maxDiskPercentIgnorePatterns" ) ) {
				maxDiskPercentIgnorePatterns = defaultsNode.path( "maxDiskPercentIgnorePatterns" ).asText()
					.split( "," );
			}

			// Mpstat max limits
			if ( defaultsNode.has( MAX_HOST_CPU ) ) {
				defaultMaxHostCpu = defaultsNode.path( MAX_HOST_CPU )
					.asInt();
			}

			if ( defaultsNode.has( MAX_HOST_CPU_LOAD ) ) {
				defaultMaxHostCpuLoad = defaultsNode.path( MAX_HOST_CPU_LOAD )
					.asInt();
			}

			if ( defaultsNode.has( MAX_HOST_CPU_IO_WAIT ) ) {
				defaultMaxHostCpuIoWait = defaultsNode.path( MAX_HOST_CPU_IO_WAIT )
					.asInt();
			}

			if ( defaultsNode.has( MIN_FREE_MEMORY_MB ) ) {
				defaultMinFreeMemoryMb = defaultsNode.path( MIN_FREE_MEMORY_MB ).asInt();
			}

			for ( ServiceAlertsEnum serviceLimit : ServiceAlertsEnum.values() ) {
				if ( defaultsNode.has( serviceLimit.maxId() ) ) {
					serviceLimits.put( serviceLimit, defaultsNode.path( serviceLimit.maxId() ).asLong() );

					if ( serviceLimit == ServiceAlertsEnum.memory ) {
						serviceLimits.put( ServiceAlertsEnum.memory,
							convertUnitToKb(
								defaultsNode.path( ServiceAlertsEnum.memory.maxId() ).asText() ) );
					}
				}

			}

			if ( defaultsNode.has( JMX_HEARTBEAT ) ) {
				jmxHeartBeat = defaultsNode.path( JMX_HEARTBEAT ).asBoolean();
			}

			if ( defaultsNode.has( JMX_HEARTBEAT_IGNORE_STOPPED ) ) {
				jmxHeartBeatIgnoreStopped = defaultsNode.path( JMX_HEARTBEAT_IGNORE_STOPPED ).asBoolean();
			}

		}
	}

	private int adminToAgentTimeout = 5000;

	ObjectMapper jacksonMapper = new ObjectMapper();

	// DEFAULT: 24 hour upload intervals
	// - this heavily reduces number of documents aggregated when performing
	// long term trends
	final private static int PUBLICATION_INTERVAL_SECONDS = 24 * 60 * 60;

	public int getMetricsUploadSeconds ( int collectionIntervalSeconds ) {

		int numSeconds = PUBLICATION_INTERVAL_SECONDS;
		if ( uploadIntervalsInHoursJson == null ) {
			if ( collectionIntervalSeconds <= 60 ) {
				numSeconds = 30 * 60; // 30 minutes
			}
		} else if ( uploadIntervalsInHoursJson.has( collectionIntervalSeconds + "seconds" ) ) {
			numSeconds = (int) (uploadIntervalsInHoursJson.get( collectionIntervalSeconds + "seconds" ).asDouble() * 60
					* 60);
		}

		return numSeconds;
	}

	private String eventUrl = "no"; // Disabled by default
	private String eventMetricsUrl = "/need/to/set/eventMetricsUrl";

	public String getEventDataUser () {
		return eventUser;
	}

	public void setEventUser ( String eventUser ) {
		this.eventUser = eventUser;
	}

	public String getEventDataPass () {
		return csapApplication.decode( eventPass, "CSAP Events password" );
	}

	public void setEventPass ( String eventPass ) {
		this.eventPass = eventPass;
	}

	private String eventUser = "XXXXX.gen";
	private String eventPass = "requiredInCluster";

	private String agentUser = "agentUser";
	private String agentPass = null;
	private boolean agentLocalAuth = true;
	private String iostatDeviceFilter = "^sd.*";

	public String getIostatDeviceFilter () {
		return iostatDeviceFilter;
	}

	public void setIostatDeviceFilter ( String iostatDeviceFilter ) {
		this.iostatDeviceFilter = iostatDeviceFilter;
	}

	//
	private String eventApiUrl = "/need/to/set/eventApiUrl";

	private String secureUrl = null;

	public String getSecureUrl () {
		return secureUrl;
	}

	public void setSecureUrl ( String secureUrl ) {
		this.secureUrl = secureUrl;
	}

	Map<String, String> browseDisks = null;

	public Map<String, String> getBrowseDisks () {

		if ( browseDisks == null ) {
			browseDisks = new HashMap<String, String>();
			JsonNode groupFileNode = getFileBrowserConfig();
			groupFileNode.fieldNames().forEachRemaining( name -> {
				logger.debug( "got name: {}", name );
				browseDisks.put( name, "file/browser/" + name );
			} );
		}

		return browseDisks;

	}

	public JsonNode getFileBrowserConfig () {
		JsonNode groupFileNode = getSettingsDefinition().at( "/file-browser" );
		return groupFileNode;
	}

	private boolean autoRestartHttpdOnClusterReload = true;
	private String analyticsUiUrl = "/need/to/set/analyticsUiUrl";

	private String historyUiUrl = "/need/to/set/historyUiUrl";
	private boolean csapAuditEnabled = false;
	private boolean eventPublishEnabled = false;
	private boolean csapMetricsUploadEnabled = false;

	private String mavenCommand = "-B -Dmaven.test.skip=true clean package";

	public String getMavenCommand () {
		return mavenCommand;
	}

	public void setMavenCommand ( String mavenCommand ) {
		this.mavenCommand = mavenCommand;
	}

	public int getPortStart () {
		return portStart;
	}

	public void setPortStart ( int portStart ) {
		this.portStart = portStart;
	}

	public int getPortEnd () {
		return portEnd;
	}

	public void setPortEnd ( int portEnd ) {
		this.portEnd = portEnd;
	}

	private int portStart = 8200;
	private int portEnd = 9000;

	private int duIntervalMins = 5;

	public int getDuIntervalMins () {
		return duIntervalMins;
	}

	public void setDuIntervalMins ( int duIntervalMins ) {
		this.duIntervalMins = duIntervalMins;
	}

	private int lsofIntervalMins = 1;

	public int getLsofIntervalMins () {
		return lsofIntervalMins;
	}

	public void setLsofIntervalMins ( int lsofIntervalMins ) {
		this.lsofIntervalMins = lsofIntervalMins;
	}

	public boolean isLsofEnabled () {
		return lsofIntervalMins > 0;
	}

	private String defaultUiDisplayCluster = "all";
	private String defaultUiDisplayVersion = "all";
	private TreeMap<String, String> labelToServiceUrlLaunchMap = new TreeMap<String, String>();

	private String lbUrl = "http://needToAddYourLbToClusterFile";

	private String monitoringUrl = "none";

	private String metricsUrl = "no";

	private MultiValueMap<String, Integer> metricToSecondsMap = new LinkedMultiValueMap<String, Integer>();

	private ArrayNode newsJsonArray = null;

	private ArrayNode emailJsonArray = null;

	public ArrayNode getEmailJsonArray () {
		return emailJsonArray;
	}

	public ArrayNode getNewsJsonArray () {
		return newsJsonArray;
	}

	public int getPsDumpCount () {
		return psDumpCount;
	}

	public int getPsDumpLowMemoryInMb () {
		return psDumpLowMemoryInMb;
	}

	private int inMemoryCacheSize = 600;

	public int getInMemoryCacheSize () {
		return inMemoryCacheSize;
	}

	public void setInMemoryCacheSize ( int inMemoryCacheSize ) {
		this.inMemoryCacheSize = inMemoryCacheSize;
	}

	private int psDumpInterval = -1;

	public int getPsDumpInterval () {
		return psDumpInterval;
	}

	private double defaultAutoStopServiceThreshold = 2.0;

	public double getAutoStopServiceThreshold ( String host ) {

		double result = defaultAutoStopServiceThreshold;

		if ( monitorMap.containsKey( host )
				&& monitorMap.get( host ).has( "autoStopServiceThreshold" ) ) {
			result = monitorMap.get( host ).path( "autoStopServiceThreshold" ).asDouble();
		}

		// protect agains typos
		if ( result < 0.5 ) {
			logger.warn( "autoStopServiceThreshold probably incorrect. Using hardcoded lower boundary: " + result );
			result = 0.5;
		}

		return result;
	}

	private int defaultMinFreeMemoryMb = 500;
	private int defaultMaxDiskPercent = 90;

	// Mpstat monitors
	private int defaultMaxHostCpuLoad = 4;
	private int defaultMaxHostCpu = 80;
	private int defaultMaxHostCpuIoWait = 10;

	private Map<String, JsonNode> monitorMap = new HashMap<String, JsonNode>();

	public void addHostMonitor ( String hostName, JsonNode vals ) {
		monitorMap.put( hostName, vals );

	}

	public int getMaxDiskPercent ( String host ) {

		if ( monitorMap.containsKey( host )
				&& monitorMap.get( host ).has( "maxDiskPercent" ) ) {
			return monitorMap.get( host ).path( "maxDiskPercent" ).asInt();
		}

		return defaultMaxDiskPercent;
	}

	private String[] maxDiskPercentIgnorePatterns = new String[ 0 ];

	public boolean isIgnoreDisk ( String mountPoint ) {

		for ( String ignorePattern : maxDiskPercentIgnorePatterns ) {
			// logger.info("ignorePattern: " + ignorePattern + " mount: " +
			// mountPoint);
			if ( mountPoint.matches( ignorePattern ) ) {
				return true;
			}
		}
		return false;
	}

	public int getMaxHostCpuLoad ( String host ) {

		if ( monitorMap.containsKey( host ) && monitorMap.get( host ).has( MAX_HOST_CPU_LOAD ) ) {

			return monitorMap.get( host ).path( MAX_HOST_CPU_LOAD ).asInt();
		}

		return defaultMaxHostCpuLoad;
	}

	public static final String MAX_HOST_CPU_LOAD = "maxHostCpuLoad";

	public int getMaxHostCpu ( String host ) {

		if ( monitorMap.containsKey( host ) && monitorMap.get( host ).has( MAX_HOST_CPU ) ) {
			return monitorMap.get( host ).path( MAX_HOST_CPU ).asInt();
		}
		return defaultMaxHostCpu;
	}

	public static final String MAX_HOST_CPU = "maxHostCpu";

	public int getMaxHostCpuIoWait ( String host ) {

		if ( monitorMap.containsKey( host ) && monitorMap.get( host ).has( MAX_HOST_CPU_IO_WAIT ) ) {
			return monitorMap.get( host ).path( MAX_HOST_CPU_IO_WAIT ).asInt();
		}

		return defaultMaxHostCpuIoWait;

	}

	public static final String MAX_HOST_CPU_IO_WAIT = "maxHostCpuIoWait";

	public int getMinFreeMemoryMb ( String host ) {

		if ( monitorMap.containsKey( host ) && monitorMap.get( host ).has( MIN_FREE_MEMORY_MB ) ) {
			return monitorMap.get( host ).path( MIN_FREE_MEMORY_MB ).asInt();
		}

		return defaultMinFreeMemoryMb;
	}

	public static final String MIN_FREE_MEMORY_MB = "minFreeMemoryMb";

	public String getMonitors () {
		StringBuilder sb = new StringBuilder();

		sb.append( "defaultMaxDiskPercent: " + defaultMaxDiskPercent
				+ " defaultMaxHostCpuLoad:" + defaultMaxHostCpuLoad
				+ " defaultMaxHostCpu:" + defaultMaxHostCpu
				+ " defaultMaxHostCpuIoWait:" + defaultMaxHostCpuIoWait
				+ " defaultMinFreeMemoryMb:" + defaultMinFreeMemoryMb );

		for ( String host : monitorMap.keySet() ) {
			try {
				sb.append( "<br><br>"
						+ host
						+ ": jmxHeartBeat: "
						+ isJmxHeatbeat( host )
						+ "<br>"
						+ jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( monitorMap.get( host ) ) );
			} catch (Exception e) {
				logger.error( "Failed finding host defaults", e );
			}
		}

		return sb.toString();
	}

	// Monitor { "threadCount", "fileCount", "rssMemory", "diskUtil",
	// "numTomcatConns", "topCpu" };
	/**
	 *
	 * We default to the lifecycle default, and allow both cluster and service
	 * to override
	 *
	 * @param host
	 * @param attribute
	 * @return
	 */
	public long getMonitor ( String host, ServiceAlertsEnum alert ) {

		// first check if value is in cluster defaults
		if ( monitorMap.containsKey( host ) && monitorMap.get( host ).has( alert.maxId() ) ) {
			return monitorMap.get( host ).path( alert.maxId() ).asInt();
		}

		// if not found above - use the default (either hardcoded, or overridden
		// by lifecycle
		try {
			return serviceLimits.get( alert );
		} catch (Exception e) {
			logger.error( "Failed to find value", e );
		}

		return 1;
	}

	public static long convertUnitToKb ( String alertSetting ) {

		String alertSettingLowerCase = alertSetting.toLowerCase();
		// NumberFormat.getInstance().parse(itemJson.asText()).intValue(),
		long result = -1;
		try {
			result = NumberFormat.getInstance().parse( alertSetting ).longValue();
		} catch (ParseException e) {
			logger.error( "Unexpected limit: \"{}\" . Verify in application definition ", alertSetting );
			return result;
		}

		if ( alertSettingLowerCase.contains( "g" ) ) {
			result = result * 1024 * 1024;
		} else if ( alertSettingLowerCase.contains( "m" ) ) {
			result = result * 1024;
		}

		logger.debug( "alertSetting: {} , Result: {}", alertSetting, result );

		return result;
	}

	// Cluster wide defaults
	private Map<ServiceAlertsEnum, Long> serviceLimits = new EnumMap<ServiceAlertsEnum, Long>( ServiceAlertsEnum.class );

	public static String JMX_HEARTBEAT = "jvm_jmxHeartbeat";
	private boolean jmxHeartBeat = false;

	/**
	 * We default to the lifecycle default, and allow both cluster and service
	 * to override
	 *
	 * @param host
	 * @return
	 */
	public boolean isJmxHeatbeat ( String host ) {

		if ( monitorMap.containsKey( host ) && monitorMap.get( host ).has( JMX_HEARTBEAT ) ) {
			return monitorMap.get( host ).path( JMX_HEARTBEAT ).asBoolean();
		}

		return jmxHeartBeat;
	}

	public static String JMX_HEARTBEAT_IGNORE_STOPPED = "jvm_jmxHeartbeatIgnoreStopped";
	private boolean jmxHeartBeatIgnoreStopped = false;

	public boolean isJmxHeatbeatIgnoreStopped ( String host ) {

		if ( monitorMap.containsKey( host ) && monitorMap.get( host ).has( JMX_HEARTBEAT_IGNORE_STOPPED ) ) {
			return monitorMap.get( host ).path( JMX_HEARTBEAT_IGNORE_STOPPED ).asBoolean();
		}

		return jmxHeartBeatIgnoreStopped;
	}

	private int psDumpCount = 0;
	private int psDumpLowMemoryInMb = 1000;

	private int numberWorkerThreads = 5;

	boolean metricsPublication = false;

	public boolean isMetricsPublication () {
		return metricsPublication;
	}

	public void setMetricsPublication ( boolean metricsPublication ) {
		this.metricsPublication = metricsPublication;
	}

	ArrayNode metricsPublicationNode = null;

	public ArrayNode getMetricsPublicationNode () {
		return metricsPublicationNode;
	}

	public void setMetricsPublicationNode ( ArrayNode metricsPublicationNode ) {
		this.metricsPublicationNode = metricsPublicationNode;
	}

	private String trimSpacesAndVariables ( String input ) {

		if ( input == null ) {
			return null;
		}
		return input.trim().replaceAll( "\\$host",
			Application.getHOST_NAME() );

	}

	public int getAdminToAgentTimeoutMs () {
		return adminToAgentTimeout;
	}

	public int getAdminToAgentTimeoutSeconds () {
		return adminToAgentTimeout / 1000;
	}

	public String getEventUrl () {
		if ( !eventUrl.startsWith( "http" ) ) {
			return getLbUrl() + eventUrl;
		}
		return eventUrl;
	}

	private String resolvedHistoryUrl = null;

	public String getHistoryUiUrl () {
		if ( resolvedHistoryUrl == null ) {
			resolvedHistoryUrl = buildUrl( historyUiUrl, CsapEventClient.CSAP_UI_CATEGORY + "/*" );
		}
		return resolvedHistoryUrl;
	}

	private String resolvedHistoryHostUrl = null;

	public String getHistoryHostUrl () {
		if ( resolvedHistoryHostUrl == null ) {
			resolvedHistoryHostUrl = buildUrl( historyUiUrl, "/csap/*" );
			resolvedHistoryHostUrl += "&hostName=" + Application.getHOST_NAME();
		}
		return resolvedHistoryHostUrl;
	}

	public String getEventMetricsUrl () {
		if ( !eventMetricsUrl.startsWith( "http" ) ) {
			return getLbUrl() + eventMetricsUrl;
		}
		return eventMetricsUrl;
	}

	public String getReportUrl () {
		return getEventMetricsUrl() + "../report/";
	}

	public void setEventMetricsUrl ( String eventMetricsUrl ) {
		this.eventMetricsUrl = eventMetricsUrl;
	}

	private String resolvedUiCountUrl = null;

	public String getEventUiCountUrl () {
		if ( resolvedUiCountUrl == null ) {
			resolvedUiCountUrl = buildUrl( eventApiUrl + "/count?appId={appId}&life={life}&category={category}&",
				CsapEventClient.CSAP_UI_CATEGORY );
		}
		return resolvedUiCountUrl;
	}

	private String resolvedHostCountUrl = null;

	public String getEventHostCountUrl () {
		if ( resolvedHostCountUrl == null ) {
			resolvedHostCountUrl = buildUrl( eventApiUrl + "/count?appId={appId}&life={life}&host={host}&",
				CsapEventClient.CSAP_CATEGORY );
		}
		return resolvedHostCountUrl;
	}

	public String getEventApiUrl () {
		return eventApiUrl;
	}

	public String getToolsServer () {

		if ( eventApiUrl.length() > 10 && eventApiUrl.indexOf( "/", 10 ) != -1 )
			return eventApiUrl.substring( 0, eventApiUrl.indexOf( "/", 10 ) );

		return eventApiUrl;
	}

	private String resolvedAnalyticsUrl = null;

	public String getAnalyticsUiUrl () {
		if ( resolvedAnalyticsUrl == null ) {
			resolvedAnalyticsUrl = buildUrl( analyticsUiUrl, "dummy" );
		}
		return resolvedAnalyticsUrl;
	}

	private String buildUrl ( String url, String category ) {

		if ( !url.startsWith( "http" ) ) {
			url = getLbUrl() + url;
		}
		String resolvedUrl = url;
		Map<String, String> urlVariables = new HashMap<String, String>();
		if ( url.contains( "{appId}" ) ) {
			urlVariables.put( "appId", getEventDataUser() );
		}

		if ( url.contains( "{life}" ) ) {
			urlVariables.put( "life", Application.getCurrentLifeCycle() );
		}

		if ( url.contains( "{host}" ) ) {
			urlVariables.put( "host", Application.getHOST_NAME() );
		}

		if ( url.contains( "{category}" ) ) {
			urlVariables.put( "category", category );
		}

		if ( url.contains( "{project}" ) ) {
			urlVariables.put( "project", csapApplication.getActiveModel().getReleasePackageName() );
		}

		try {
			URI expanded = new UriTemplate( url ).expand( urlVariables );
			resolvedUrl = expanded.toURL().toString();
			logger.debug( "Url: {}, Resolved: {}", url, resolvedUrl );

		} catch (Exception e) {
			logger.warn( "Failed to build url: " + url, e );
		}
		return resolvedUrl;
	}

	public void setAnalyticsUiUrl ( String analyticsUiUrl ) {
		this.analyticsUiUrl = analyticsUiUrl;
	}

	public String getDefaultUiDisplayCluster () {
		return defaultUiDisplayCluster;
	}

	public String getDefaultUiDisplayVersion () {
		return defaultUiDisplayVersion;
	}

	public TreeMap<String, String> getLabelToServiceUrlLaunchMap () {
		return labelToServiceUrlLaunchMap;
	}

	public String getMonitoringUrl () {
		return monitoringUrl;
	}

	public String getLbUrl () {
		return lbUrl;
	}

	public String getLbServer () {
		// eg. strip off http:// from
		int lastSlash = lbUrl.lastIndexOf( "/" ) + 1;

		logger.debug( "lastSlash: {}, in {}", lastSlash, lbUrl );
		if ( lbUrl.length() > lastSlash ) {
			return lbUrl.substring( lastSlash );
		}

		return lbUrl;
	}

	public String getMetricsUrl () {
		if ( !metricsUrl.startsWith( "http" ) ) {
			return getLbUrl() + metricsUrl;
		}
		return metricsUrl;
	}

	public MultiValueMap<String, Integer> getMetricToSecondsMap () {
		return metricToSecondsMap;
	}

	public int getNumberWorkerThreads () {
		return numberWorkerThreads;
	}

	public boolean isAutoRestartHttpdOnClusterReload () {
		return autoRestartHttpdOnClusterReload;
	}

	public boolean isCsapAuditEnabled () {
		return csapAuditEnabled;
	}

	public boolean isEventPublishEnabled () {

		if ( getEventDataUser().contains( "XXXXX" ) ) {
			return false;
		}
		return eventPublishEnabled;
	}

	public boolean isCsapMetricsUploadEnabled () {
		return csapMetricsUploadEnabled;
	}

	public void setAdminToAgentTimeout ( int adminToAgentTimeout ) {
		this.adminToAgentTimeout = adminToAgentTimeout;
	}

	public void setEventUrl ( String eventUrl ) {
		if ( !eventUrl.equalsIgnoreCase( "no" ) && !eventUrl.equalsIgnoreCase( "disabled" ) ) {
			eventPublishEnabled = true;
		}
		this.eventUrl = eventUrl;
	}

	public void setAutoRestartHttpdOnClusterReload (
														boolean autoRestartHttpdOnClusterReload ) {
		this.autoRestartHttpdOnClusterReload = autoRestartHttpdOnClusterReload;
	}

	public void setHistoryUiUrl ( String consoleHistoryUi ) {
		this.historyUiUrl = consoleHistoryUi;
	}

	public void setDefaultUiDisplayCluster ( String defaultUiDisplayCluster ) {
		this.defaultUiDisplayCluster = defaultUiDisplayCluster;
	}

	public void setDefaultUiDisplayVersion ( String defaultUiDisplayVersion ) {
		this.defaultUiDisplayVersion = defaultUiDisplayVersion;
	}

	public void setLabelToServiceUrlLaunchMap (
												TreeMap<String, String> labelToServiceUrlLaunchMap ) {
		this.labelToServiceUrlLaunchMap = labelToServiceUrlLaunchMap;
	}

	public void setLbUrl ( String lbUrl ) {
		this.lbUrl = lbUrl;
	}

	public void setMetricsUrl ( String metricsUrl ) {
		if ( !metricsUrl.equalsIgnoreCase( "no" ) ) {
			csapMetricsUploadEnabled = true;
		}

		this.metricsUrl = metricsUrl;
	}

	public void setNumberWorkerThreads ( int numberWorkerThreads ) {
		this.numberWorkerThreads = numberWorkerThreads;
	}

	// once a connection is created - collections is very very quick for local
	// host calls. < 200ms for 20 services with many custom attributes
	private long maxJmxCollectionMs = 1000;

	public long getMaxJmxCollectionMs () {
		// TODO Auto-generated method stub
		return maxJmxCollectionMs;
	}

	@Override
	public String toString () {
		return WordUtils.wrap( "LifeCycleMetaData [loadBalanceVmFilter=" + loadBalanceVmFilter + ", trendingJson="
				+ trendingJson
				+ ", realTimeMetersJson=" + realTimeMetersJson + ", uploadIntervalsInHoursJson="
				+ uploadIntervalsInHoursJson + ", adminToAgentTimeout=" + adminToAgentTimeout +
				", eventUrl=" + eventUrl + ", eventMetricsUrl=" + eventMetricsUrl + ", eventUser="
				+ eventUser + ", eventPass=" + eventPass + ", eventApiUrl=" + eventApiUrl + ", secureUrl=" + secureUrl
				+ ", autoRestartHttpdOnClusterReload=" + autoRestartHttpdOnClusterReload + ", analyticsUiUrl="
				+ analyticsUiUrl + ", historyUiUrl=" + historyUiUrl + ", csapAuditEnabled=" + csapAuditEnabled
				+ ", eventPublishEnabled=" + eventPublishEnabled + ", csapMetricsUploadEnabled="
				+ csapMetricsUploadEnabled + ", mavenCommand=" + mavenCommand + ", portStart=" + portStart
				+ ", portEnd=" + portEnd + ", duIntervalMins=" + duIntervalMins + ", lsofIntervalMins="
				+ lsofIntervalMins + ", defaultUiDisplayCluster=" + defaultUiDisplayCluster
				+ ", defaultUiDisplayVersion=" + defaultUiDisplayVersion + ", labelToServiceUrlLaunchMap="
				+ labelToServiceUrlLaunchMap + ", lbUrl=" + lbUrl + ", monitoringUrl=" + monitoringUrl
				+ ", metricsUrl=" + metricsUrl + ", metricToSecondsMap=" + metricToSecondsMap + ", newsJsonArray="
				+ newsJsonArray + ", emailJsonArray=" + emailJsonArray + ", psDumpInterval=" + psDumpInterval
				+ ", defaultAutoStopServiceThreshold=" + defaultAutoStopServiceThreshold + ", defaultMinFreeMemoryMb="
				+ defaultMinFreeMemoryMb + ", defaultMaxDiskPercent=" + defaultMaxDiskPercent
				+ ", defaultMaxHostCpuLoad=" + defaultMaxHostCpuLoad + ", defaultMaxHostCpu=" + defaultMaxHostCpu
				+ ", defaultMaxHostCpuIoWait=" + defaultMaxHostCpuIoWait + ", monitorMap=" + monitorMap
				+ ", maxDiskPercentIgnorePatterns=" + Arrays.toString( maxDiskPercentIgnorePatterns )
				+ ", jmxHeartBeat="
				+ jmxHeartBeat + ", jmxHeartBeatIgnoreStopped=" + jmxHeartBeatIgnoreStopped + ", psDumpCount="
				+ psDumpCount + ", psDumpLowMemoryInMb=" + psDumpLowMemoryInMb + ", numberWorkerThreads="
				+ numberWorkerThreads + ", metricsPublication="
				+ metricsPublication + ", metricsPublicationNode=" + metricsPublicationNode + ", resolvedHistoryUrl="
				+ resolvedHistoryUrl + ", resolvedHistoryHostUrl=" + resolvedHistoryHostUrl + ", resolvedUiCountUrl="
				+ resolvedUiCountUrl + ", resolvedHostCountUrl=" + resolvedHostCountUrl + ", resolvedAnalyticsUrl="
				+ resolvedAnalyticsUrl + ", maxJmxCollectionMs=" + maxJmxCollectionMs + "]",
			50 );
	}

	/**
	 * @return the helpUrBase
	 */
	public String getHelpUrBase () {
		return helpUrBase;
	}

	public String getUserLookupUrl ( String user ) {
		return helpUrBase + user;
	}

	/**
	 * @param helpUrBase
	 *            the helpUrBase to set
	 */
	public void setHelpUrBase ( String helpUrBase ) {
		this.helpUrBase = helpUrBase;
	}

	/**
	 * @return the limitSamples
	 */
	public int getLimitSamples () {
		return limitSamples;
	}

	/**
	 * @param limitSamples
	 *            the limitSamples to set
	 */
	public void setLimitSamples ( int limitSamples ) {
		this.limitSamples = limitSamples;
	}

	/**
	 * @return the eolJarPatters
	 */
	public ArrayNode getEolJarPatterns () {
		return eolJarPatterns;
	}

	/**
	 * @param eolJarPatterns
	 *            the eolJarPatters to set
	 */
	public void setEolJarPatterns ( ArrayNode eolJarPatterns ) {
		this.eolJarPatterns = eolJarPatterns;
	}

	public String getAgentUser () {
		return agentUser;
	}

	public void setAgentUser ( String agentUser ) {
		this.agentUser = agentUser;
	}

	public String getAgentPass () {
		return agentPass;
	}

	public void setAgentPass ( String agentPass ) {
		this.agentPass = agentPass;
	}

	public boolean isAgentLocalAuth () {
		return agentLocalAuth;
	}

	public void setAgentLocalAuth ( boolean agentUseLdap ) {
		this.agentLocalAuth = agentUseLdap;
	}

	private long logRotationMinutes = 60;

	public long getLogRotationMinutes () {
		// TODO Auto-generated method stub
		return logRotationMinutes;
	}

}
