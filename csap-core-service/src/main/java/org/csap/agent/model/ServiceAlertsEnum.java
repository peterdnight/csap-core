/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.model;

import static org.csap.agent.model.ServiceBase.USER_STOP;

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.commons.lang3.text.WordUtils;
import org.csap.agent.stats.JmxCommonEnum;
import org.csap.agent.stats.OsProcessEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *
 * @author someDeveloper
 */
public enum ServiceAlertsEnum {

	threads( OsProcessEnum.threadCount.value ), diskSpace( OsProcessEnum.diskUsedInMb.value ),
	diskWriteRate( OsProcessEnum.diskWriteKb.value ),
	memory( OsProcessEnum.rssMemory.value ), sockets( OsProcessEnum.socketCount.value ),
	openFileHandles( OsProcessEnum.fileCount.value ),
	cpu( OsProcessEnum.topCpu.value ), httpConnections( JmxCommonEnum.httpConnections.value );

	static final Logger logger = LoggerFactory.getLogger( ServiceAlertsEnum.class );
	static ObjectMapper jacksonMapper = new ObjectMapper();

	public String value;

	public String maxId () {
		return "max_" + value;
	}

	public static double ALERT_LEVEL = 1.0;
	public static String JAVA_HEARTBEAT = "jmxHeartbeatMs";

	private ServiceAlertsEnum( String value ) {
		this.value = value;
	}

	public String buildMessage ( ServiceInstance serviceDefinition, long collected, long maxAllowed ) {

		StringBuilder alertMessage = new StringBuilder(
			serviceDefinition.getHostName() + ": " + serviceDefinition.getServiceName() + ": " );

		switch (this) {
		case threads:
			alertMessage.append( "OS Threads: "
					+ collected
					+ ", Alert threshold: " + maxAllowed );
			break;

		case diskSpace:
			alertMessage.append( "Disk Space: "
					+ collected
					+ "Mb, Alert threshold: " + maxAllowed + "Mb" );
			break;

		case diskWriteRate:
			alertMessage.append( "Disk Writes: "
					+ collected + "Kb/s, Alert threshold: " + maxAllowed );

			if ( serviceDefinition.getServiceName()
				.equals( Application.AGENT_ID ) ) {
				alertMessage.append( " (log rotation, deployment)" );
			}
			break;

		case memory:
			alertMessage.append( "Memory (RSS): "
					+ collected / 1024
					+ "Mb, Alert threshold: " + maxAllowed / 1024 + "Mb" );
			break;

		case sockets:
			alertMessage.append( "Network Connections: "
					+ collected
					+ " sockets, Alert threshold: " + maxAllowed );
			break;

		case openFileHandles:
			alertMessage.append( "OS File Handles: "
					+ collected
					+ " open, Alert threshold: " + maxAllowed );
			break;

		case cpu:
			alertMessage.append( "Process CPU: "
					+ collected
					+ "%, Alert threshold: " + maxAllowed );
			break;

		case httpConnections:
			alertMessage.append( "Http Connections: "
					+ collected
					+ " Open, Alert threshold: " + maxAllowed );
			break;

		default:
			throw new AssertionError( this.name() );

		}

		return alertMessage.toString();
	}

	/**
	 *
	 * Supports 2 distinct flows: 1) Node Agent 2) Node Manager
	 *
	 * - we are NOT verifying InstanceConfig data, but can use the non-runtime
	 * configuration
	 *
	 * In Node Manager scenario, runtime data is passed in after being loaded
	 * from remote host. For implementation, we always pass in runtime data
	 * versus using local configuration.
	 *
	 * @return
	 */
	static public ArrayList<String> getServiceAlertsAndUpdateCpu (
																	ServiceInstance serviceDefinition,
																	double alertLevel, JsonNode collectedServiceData,
																	LifeCycleSettings lifeCycleMetaData ) {
		ArrayList<String> result = new ArrayList<String>();

		for ( ServiceAlertsEnum alert : ServiceAlertsEnum.values() ) {

			try {
				long maxAllowed = Math.round( alertLevel * getLimitFromHierarchy( serviceDefinition, lifeCycleMetaData, alert ) );
				long collected = -1;
				if ( collectedServiceData.has( alert.value ) ) {
					collected = collectedServiceData.get( alert.value ).asInt( -1 );
				}
				// maxValues.get(attributeName)
				if ( collected > maxAllowed ) {
					String alertMessage = alert.buildMessage( serviceDefinition, collected, maxAllowed );
					result.add( alertMessage.toString() );
				}
			} catch (Exception e) {
				result.add( serviceDefinition.getHostName() + ": " + serviceDefinition.getServiceName() + ": " + alert.value
						+ " Value: " + collectedServiceData.get( alert.value )
						+ " Failed evaluation: " + e.getMessage() );
			}

		}

		if ( !serviceDefinition.isScript()
				&& !serviceDefinition.isOs()
				&& isCollectedServiceInactive( collectedServiceData )
				&& collectedServiceData.has( USER_STOP )
				&& !collectedServiceData.get( USER_STOP ).asBoolean() ) {

			result.add( serviceDefinition.getHostName() + ": " + serviceDefinition.getServiceName()
					+ ": no active OS process found, and no user stop was issued. Probable crash or runaway resource kill: review logs." );
		} else if ( isJavaHeartbeatEnabled( serviceDefinition, lifeCycleMetaData ) ) {
			javaHeartBeatValidation( serviceDefinition, collectedServiceData, lifeCycleMetaData, result );
		}

		logger.debug( "{}   isInactive:{}, result: {}",
			serviceDefinition.getServiceName(), serviceDefinition.isInactive(), result );

		return result;
	}

	static boolean isCollectedServiceInactive ( JsonNode collectedServiceData ) {

		return collectedServiceData.has( "cpuUtil" )
				&& collectedServiceData.get( "cpuUtil" ).asText().equals( ServiceInstance.INACTIVE );

	}

	static public boolean isJavaHeartbeatEnabled (
													ServiceInstance serviceDefinition,
													LifeCycleSettings lifeCycleMetaData ) {

		boolean isJmxHearbeat = lifeCycleMetaData.isJmxHeatbeat( serviceDefinition.getHostName() );

		if ( serviceDefinition.getMonitors() != null
				&& serviceDefinition.getMonitors().has( LifeCycleSettings.JMX_HEARTBEAT ) ) {
			isJmxHearbeat = serviceDefinition.getMonitors()
				.get( LifeCycleSettings.JMX_HEARTBEAT ).asBoolean();
		}

		return isJmxHearbeat;
	}

	static private void javaHeartBeatValidation (
													ServiceInstance serviceDefinition,
													JsonNode collectedServiceData, LifeCycleSettings lifeCycleMetaData,
													ArrayList<String> result ) {

		logger.debug( "\n\n CsAgent on {} collected: {}", serviceDefinition.getHostName(),
			WordUtils.wrap( collectedServiceData.toString(), 100 ) );

		// primary guard - this only applies to java JMX instances
		if ( serviceDefinition.isScript() || serviceDefinition.isOs() || serviceDefinition.isSkipJmxCollection() ) {
			return;
		}

		// if ( collectedServiceData.has( USER_STOP )
		// && collectedServiceData.get( USER_STOP ).asBoolean() ) {
		// return;
		// }

		// if heartbeat checks are enabled
		boolean isSkipCheckIfServiceIsNotRunning = lifeCycleMetaData.isJmxHeatbeatIgnoreStopped( serviceDefinition.getHostName() );
		if ( serviceDefinition.getMonitors() != null
				&& serviceDefinition.getMonitors().has( LifeCycleSettings.JMX_HEARTBEAT_IGNORE_STOPPED ) ) {
			isSkipCheckIfServiceIsNotRunning = serviceDefinition.getMonitors().get( LifeCycleSettings.JMX_HEARTBEAT_IGNORE_STOPPED )
				.asBoolean();
		}

		// updating state of passed object with last collected data
		// it is needed when determining service alerts from remote hosts.
		// is it still needed ?
		serviceDefinition.setCpuUtil( collectedServiceData.get( "cpuUtil" ).asText() );

		logger.debug(
			" {} has hearbeat: {}, isSkipCheckIfServiceIsNotRunning setting: {} , isInactive: {}, metrics: \n{}",
			collectedServiceData.get( "serviceName" ),
			collectedServiceData.get( JAVA_HEARTBEAT ),
			isSkipCheckIfServiceIsNotRunning,
			serviceDefinition.isInactive(),
			serviceDefinition.getPerformanceConfiguration() );

		// if ( serviceDefinition.isInactive() ) {
		if ( isCollectedServiceInactive( collectedServiceData ) ) {
			return;
		}

		// if ( serviceDefinition.getPerformanceConfiguration() != null
		// && serviceDefinition.getPerformanceConfiguration().has(
		// JAVA_HEARTBEAT ) ) {
		if ( serviceDefinition.isApplicationHealthMeter() ) {

			// Support for application specific checks.
			// logger.info(getServiceName() + " contains " +
			// MetricEnum.jmxHeartbeat.value);
			if ( !collectedServiceData.has( JAVA_HEARTBEAT ) ) {

				result.add( serviceDefinition.getHostName() + ": " + serviceDefinition.getServiceName()
						+ ": java heartbeat is enabled but attribute is missing: " + JAVA_HEARTBEAT );
			} else {
				long collectedJmxHeartbeatMs = collectedServiceData.get( JAVA_HEARTBEAT ).asLong( -1L );
				if ( collectedJmxHeartbeatMs <= 0 ) {
					if ( isSkipCheckIfServiceIsNotRunning ) {
						if ( !serviceDefinition.isInactive() ) {
							result.add( getHeartBeatErrorMessage( serviceDefinition ) );
						}
					} else {
						result.add( getHeartBeatErrorMessage( serviceDefinition ) );
					}
				}
			}

		} else {
			// default check: use jvmThreadCount to detect

			logger.debug( "{} Checking hearbeat via thread count", serviceDefinition.getServiceName() );

			if ( collectedServiceData.has( JmxCommonEnum.jvmThreadCount.value ) ) {
				int numThreads = collectedServiceData.get( JmxCommonEnum.jvmThreadCount.value ).asInt();
				if ( numThreads <= 0 ) {

					if ( isSkipCheckIfServiceIsNotRunning ) {
						result.add( serviceDefinition.getHostName() + ": " + serviceDefinition.getServiceName()
								+ ": java heartbeat is enabled but not detected using "
								+ JmxCommonEnum.jvmThreadCount.value );

					} else {
						result.add( serviceDefinition.getHostName() + ": " + serviceDefinition.getServiceName()
								+ ": java heartbeat is enabled but not detected using "
								+ JmxCommonEnum.jvmThreadCount.value );
					}
				}
			} else {
				logger.error( "Did not find default attribute: {}; verify collection data. ",
					JmxCommonEnum.jvmThreadCount.value );
			}

		}
	}

	// Used in app-portal-main.js as well
	public static final String healthStatusToken = " Review health report: ";

	static String getHeartBeatErrorMessage ( ServiceInstance serviceDefinition ) {

		String errorMsg = serviceDefinition.getHostName() + ": " + serviceDefinition.getServiceName()
				+ ": Application Heartbeat failure.";
		if ( serviceDefinition.isHealthStatusConfigured() ) {

			errorMsg += healthStatusToken + serviceDefinition.getUrl() + "/csap/health";
		} else {
			errorMsg += " Review logs.";
		}

		return errorMsg;
	}

	// Service level, Cluster Level, then lifecycle
	static public long getLimitFromHierarchy (
												ServiceInstance serviceDefinition,
												LifeCycleSettings lifeCycleMetaData, ServiceAlertsEnum alert ) {

		// default to the value in lifecycle
		long maxAllowed = lifeCycleMetaData
			.getMonitor( serviceDefinition.getHostName(), alert );

		if ( alert == ServiceAlertsEnum.diskSpace ) {
			return getMaxDiskInMb( serviceDefinition, lifeCycleMetaData );
		}

		// check for local
		if ( serviceDefinition.getMonitors() != null && serviceDefinition.getMonitors().has( alert.maxId() ) ) {

			JsonNode itemJson = serviceDefinition.getMonitors().get( alert.maxId() );
			// maxAllowed = itemJson.asInt();

			String alertSetting = itemJson.asText().toLowerCase();
			logger.debug( "alertSetting: {} , customSettings: {}", alertSetting, itemJson );
			maxAllowed = LifeCycleSettings.convertUnitToKb( alertSetting );

		}

		logger.debug( "{} limit: {}", alert, maxAllowed );

		return maxAllowed;
	}

	static public String getMaxAllowedSummary (
												ServiceInstance serviceDefinition,
												LifeCycleSettings lifeCycleMetaData, ServiceAlertsEnum alert ) {

		String maxString = "" + getLimitFromHierarchy( serviceDefinition, lifeCycleMetaData, alert );

		if ( serviceDefinition.getMonitors() != null && serviceDefinition.getMonitors().has( alert.maxId() ) ) {

			maxString = serviceDefinition.getMonitors().get( alert.maxId() ).asText();

		}

		return maxString;
	}

	static public ObjectNode getAdminUiLimits (
												ServiceInstance serviceDefinition,
												LifeCycleSettings lifeCycleMetaData )
			throws JsonProcessingException {

		ObjectNode limitsNode = jacksonMapper.createObjectNode();
		for ( ServiceAlertsEnum alert : ServiceAlertsEnum.values() ) {

			String maxAllowed = "" + getLimitFromHierarchy( serviceDefinition, lifeCycleMetaData, alert );

			if ( alert == ServiceAlertsEnum.memory ) {
				// special handling to deal with units in UI: must be "g" or "m"
				// maxAllowed = lifeCycleMetaData.getMax_rssMemoryRaw();
				if ( serviceDefinition.getMonitors() != null && serviceDefinition.getMonitors().has( ServiceAlertsEnum.memory.maxId() ) ) {
					maxAllowed = serviceDefinition.getMonitors().get( ServiceAlertsEnum.memory.maxId() ).asText();
				}

				// force into MB; use when no units are specified as limits
				if ( maxAllowed.matches( "^[0-9]+$" ) ) {
					maxAllowed = (getLimitFromHierarchy( serviceDefinition, lifeCycleMetaData, alert ) / 1024) + "m";
				}

			}
			limitsNode.put( alert.value, maxAllowed );

		}
		// resultsJson.put("socketCount", getMaxAllowed(lifeCycleMetaData,
		// "socketCount"));

		// rarely used .. but allows ui warnings
		ObjectNode customJavaSettings = serviceDefinition.getPerformanceConfiguration();
		if ( customJavaSettings != null ) {
			String metricId = null;
			for ( Iterator<String> metricIdIterator = customJavaSettings
				.fieldNames(); metricIdIterator.hasNext(); ) {

				metricId = metricIdIterator.next().trim();
				ObjectNode metricConfigNode = (ObjectNode) customJavaSettings
					.get( metricId );
				if ( metricConfigNode.has( "max" ) ) {
					String maxValue = metricConfigNode
						.path( "max" ).asText();
					try {
						limitsNode.put( metricId, Integer.parseInt( maxValue ) );
					} catch (NumberFormatException e) {
						logger.error( "node :" + metricConfigNode, e );
						;
					}
				}
			}

		}

		// rarely used .. but allows ui warnings
		ObjectNode javaStandardSettings = serviceDefinition.getAttributeAsObject( ServiceAttributes.javaAlertWarnings );
		if ( javaStandardSettings != null ) {
			String metricId = null;
			for ( Iterator<String> metricIdIterator = javaStandardSettings
				.fieldNames(); metricIdIterator.hasNext(); ) {

				metricId = metricIdIterator.next().trim();
				ObjectNode metricConfigNode = (ObjectNode) javaStandardSettings
					.get( metricId );
				if ( metricConfigNode.has( "max" ) ) {
					String maxValue = metricConfigNode
						.path( "max" ).asText();
					try {
						limitsNode.put( metricId, Integer.parseInt( maxValue ) );
					} catch (NumberFormatException e) {
						logger.error( "node :" + metricConfigNode, e );
						;
					}
				}
			}

		}

		return limitsNode;
	}

	static public long getMaxDiskInMb (
										ServiceInstance serviceDefinition,
										LifeCycleSettings lifeCycleMetaData ) {

		long maxAllowed = lifeCycleMetaData
			.getMonitor( serviceDefinition.getHostName(), diskSpace );

		if ( serviceDefinition.getMonitors() != null && serviceDefinition.getMonitors().has( diskSpace.maxId() ) ) {
			// maxAllowed = serviceDefinition.getMonitors().get(
			// diskSpace.maxId() ).asInt();
			String alertSetting = serviceDefinition.getMonitors().get( diskSpace.maxId() ).asText().toLowerCase();
			;
			maxAllowed = LifeCycleSettings.convertUnitToKb( alertSetting );

			// in case user specified limit with m or g - the above switches to
			// kb
			// but we default to MB for disk
			if ( alertSetting.endsWith( "m" ) || alertSetting.endsWith( "g" ) ) {
				maxAllowed = maxAllowed / 1024;
			}
		}

		return maxAllowed;
	}
}
