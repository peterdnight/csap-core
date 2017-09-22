package org.csap.agent.linux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.csap.agent.CSAP;
import org.csap.agent.input.http.ui.rest.ServiceRequests;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.Application;
import org.csap.agent.model.LifeCycleSettings;
import org.csap.agent.model.ServiceAlertsEnum;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.services.HostKeys;
import org.csap.agent.services.ServiceOsManager;
import org.csap.agent.stats.MetricsPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class HostAlertProcessor {

	ObjectMapper jacksonMapper = new ObjectMapper();
	final Logger logger = LoggerFactory.getLogger( this.getClass() );

	CsapEventClient csapEventClient;
	ServiceOsManager serviceManager;
	Application csapApp;

	public HostAlertProcessor( Application csapApp, CsapEventClient csapEventClient, ServiceOsManager serviceManager ) {
		this.csapApp = csapApp;
		this.csapEventClient = csapEventClient;
		this.serviceManager = serviceManager;
	}

	/**
	 *
	 * Add errors to errorList.
	 *
	 * @param hostName
	 * @param errorList
	 * @param responseFromHostStatusJson
	 */
	public ObjectNode alertsBuilder ( double alertLevel, String hostName, ObjectNode responseFromHostStatusJson ) {

		ObjectNode alertMessages = jacksonMapper.createObjectNode();
		ArrayNode errorArray = alertMessages.putArray( Application.VALIDATION_ERRORS );

		if ( responseFromHostStatusJson.has( "error" ) ) {
			errorArray.add( hostName + ": " + responseFromHostStatusJson
				.path( "error" )
				.textValue() );
			// return result;
			return alertMessages;
		}

		ObjectNode hostStatsNode = (ObjectNode) responseFromHostStatusJson.path( HostKeys.hostStats.jsonId );
		if ( hostStatsNode == null ) {
			errorArray.add( hostName + ": " + "Host response missing attribute: hostStats" );
			// return result;
			return alertMessages;
		}

		alertsForHostCpu( hostStatsNode, hostName, alertLevel, errorArray, alertMessages );

		alertsForHostMemory( hostStatsNode, hostName, errorArray, alertMessages );

		alertsForHostDisk( hostStatsNode, hostName, alertLevel, errorArray, alertMessages );

		ObjectNode serviceHealthCollected = (ObjectNode) responseFromHostStatusJson.path( "services" );
		if ( serviceHealthCollected == null ) {
			errorArray.add( hostName + ": " + "Host response missing attribute: services" );
			// return result;
			return alertMessages;
		}

		alertsForServices( hostName, serviceHealthCollected, errorArray, alertLevel, alertMessages );

		return alertMessages;
	}

	private void alertsForServices (	String hostName, ObjectNode serviceHealthCollected,
										ArrayNode errorArray, double alertLevel,
										ObjectNode errorsFoundJson ) {

		StringBuilder serviceMessages = new StringBuilder();
		ArrayList<ServiceInstance> instancesOnHost = csapApp.getAllPackages()
			.getHostToConfigMap()
			.get( hostName );

		for ( ServiceInstance instance : instancesOnHost ) {
			if ( !serviceHealthCollected.has( instance.getServiceName_Port() ) ) {
				String message = hostName + ": " + instance.getServiceName_Port()
						+ " No status found";
				serviceMessages.append( message + "\n" );
				errorArray.add( message );
				continue;
			}
			// errorArray.addAll(
			try {

				List<String> serviceAlerts = ServiceAlertsEnum.getServiceAlertsAndUpdateCpu(
					instance, alertLevel,
					serviceHealthCollected.get( instance.getServiceName_Port() ),
					csapApp.lifeCycleSettings() );

				for ( String alertDescription : serviceAlerts ) {
					errorArray.add( alertDescription );
					serviceMessages.append( alertDescription + "\n" );
				}
			} catch (Exception e) {
				logger.error( "Failed parsing messages", e );
			}
		}
		if ( serviceMessages.length() > 0 ) {
			addNagiosStateMessage( errorsFoundJson, "processes", MetricsPublisher.NAGIOS_WARN, serviceMessages.toString() );
		}

	}

	private void alertsForHostDisk (	ObjectNode hostStatsNode, String hostName, double alertLevel, ArrayNode errorArray,
										ObjectNode errorsFoundJson )
			throws NumberFormatException {
		StringBuilder diskMessages = new StringBuilder();
		if ( hostStatsNode.has( "df" ) ) {
			ObjectNode dfJson = (ObjectNode) hostStatsNode.path( "df" );
			Iterator<String> keyIter = dfJson.fieldNames();
			while (keyIter.hasNext()) {
				String mount = keyIter.next();
				String perCentString = dfJson
					.path( mount )
					.asText();
				String perCent = perCentString.substring( 0, perCentString.length() - 1 );
				int perCentInt = Integer.parseInt( perCent );
				int diskMax = (int) Math.round( csapApp.lifeCycleSettings()
					.getMaxDiskPercent( hostName ) * alertLevel );
				if ( (!csapApp.lifeCycleSettings()
					.isIgnoreDisk( mount )) && perCentInt > diskMax ) {
					// states.put("disk", MetricsPublisher.NAGIOS_WARN) ;
					String message = hostName + ": " + " Disk usage on " + mount + " is: "
							+ perCentString + ", max: " + diskMax;
					errorArray.add( message );
					diskMessages.append( message + "\n" );
				}
			}

		} else {
			errorArray.add( hostName + ": " + "Host response missing attribute: hostStats.df" );
		}

		if ( hostStatsNode.has( "readOnlyFS" ) ) {
			ArrayNode readOnlyFS = (ArrayNode) hostStatsNode.path( "readOnlyFS" );
			for ( JsonNode item : readOnlyFS ) {
				String message = hostName + ": Read Only Filesystem: " + item.asText();
				errorArray.add( message );
				diskMessages.append( message + "\n" );
			}
		} else {
			errorArray
				.add( hostName + ": " + "Host response missing attribute: hostStats.readOnlyFS" );
		}

		if ( diskMessages.length() > 0 ) {
			addNagiosStateMessage( errorsFoundJson, "disk", MetricsPublisher.NAGIOS_WARN, diskMessages.toString() );
		}
	}

	private void alertsForHostMemory (	ObjectNode hostStatsNode, String hostName, ArrayNode errorArray,
										ObjectNode errorsFoundJson ) {
		if ( hostStatsNode.has( "memoryAggregateFreeMb" ) ) {
			int minFree = csapApp.lifeCycleSettings()
				.getMinFreeMemoryMb( hostName );
			int freeMem = hostStatsNode
				.path( "memoryAggregateFreeMb" )
				.asInt();

			logger.debug( "freeMem: {} , minFree: {}", freeMem, minFree );

			if ( freeMem < minFree ) {
				String message = hostName + ": " + " available memory " + freeMem
						+ " < min configured: " + minFree;
				errorArray.add( message );

				addNagiosStateMessage( errorsFoundJson, "memory", MetricsPublisher.NAGIOS_WARN, message );
			}
		} else {
			errorArray.add( hostName + ": "
					+ "Host response missing attribute: hostStats.memoryAggregateFreeMb" );
		}
	}

	private void alertsForHostCpu (	ObjectNode hostStatusResponse, String hostName, double alertLevel, ArrayNode errorArray,
									ObjectNode errorsFoundJson ) {
		int hostLoad = ((Double) hostStatusResponse
			.path( "cpuLoad" )
			.asDouble()).intValue();
		int maxLoad = (int) Math.round( csapApp.lifeCycleSettings()
			.getMaxHostCpuLoad( hostName ) * alertLevel );
		if ( hostLoad >= maxLoad ) {

			String message = hostName + ": " + "current load " + hostLoad + " >= max configured: "
					+ maxLoad;
			errorArray.add( message );
			addNagiosStateMessage( errorsFoundJson, "cpuLoad", MetricsPublisher.NAGIOS_WARN, message );
		}

		int hostCpu = ((Double) hostStatusResponse
			.path( "cpu" )
			.asDouble()).intValue();
		int maxCpu = (int) Math.round( csapApp.lifeCycleSettings()
			.getMaxHostCpu( hostName ) * alertLevel );
		if ( hostCpu >= maxCpu ) {

			String message = hostName + ": " + "current mpstat cpu " + hostCpu + " >= max configured: "
					+ maxCpu;
			errorArray.add( message );
			addNagiosStateMessage( errorsFoundJson, "hostCpu", MetricsPublisher.NAGIOS_WARN, message );
		}

		int hostCpuIoWait = ((Double) hostStatusResponse
			.path( "cpuIoWait" )
			.asDouble()).intValue();
		int maxCpuIoWait = (int) Math.round( csapApp.lifeCycleSettings()
			.getMaxHostCpuIoWait( hostName ) * alertLevel );
		if ( hostCpuIoWait >= maxCpuIoWait ) {

			String message = hostName + ": " + "current mpstat cpu IoWait " + hostCpuIoWait + " >= max configured: "
					+ maxCpuIoWait;
			errorArray.add( message );
			addNagiosStateMessage( errorsFoundJson, "hostCpuIoWait", MetricsPublisher.NAGIOS_WARN, message );
		}
	}

	/**
	 *
	 * Only agents use state
	 *
	 * @param stateNode
	 * @param State
	 * @param message
	 */
	private void addNagiosStateMessage ( ObjectNode errorNode, String monitor, String state, String message ) {

		ObjectNode stateNode = (ObjectNode) errorNode.get( "states" );
		if ( stateNode != null ) {

			ObjectNode item = stateNode.putObject( monitor );
			item.put( "status", state );
			item.put( "message", message );
		}

	}

	static final List<String> csapAdminServices = Arrays.asList( "CsAgent", "admin", "httpd" );


	@Scheduled ( initialDelay = 60 * CSAP.ONE_SECOND_MS , fixedDelay = 60 * CSAP.ONE_SECOND_MS )
	public void killRunaways () {

		if ( Application.isJvmInManagerMode() ) {
			return;
		}

		logger.debug( "Checking services on host to see if we need to be killed" );
		csapApp
			.getActiveModel()
			.getServicesOnHost( Application.getHOST_NAME() )
			.filter( serviceInstance -> !csapAdminServices.contains( serviceInstance.getServiceName() ) )
			.filter( serviceInstance -> serviceInstance.getDisk() == null )
			.filter( serviceInstance -> !serviceInstance.isInactive() )
			.filter( serviceInstance -> isServiceResourceRunawayAndTimeToKill( serviceInstance ) )
			.forEach( this::killService );

	}

	private boolean isServiceResourceRunawayAndTimeToKill ( ServiceInstance serviceDefinition ) {

		long maxAllowedDisk = ServiceAlertsEnum.getMaxDiskInMb( serviceDefinition, csapApp.lifeCycleSettings() );
		double resourceThresholdMultiplier = csapApp.lifeCycleSettings().getAutoStopServiceThreshold( serviceDefinition.getHostName() );

		long diskKillThreshold = Math.round( maxAllowedDisk * resourceThresholdMultiplier );
		int currentDisk = 0;

		try {
			currentDisk = serviceDefinition.getDiskUsageInMb() ;

			logger.debug( "{} : currentDisk: {}, threshold: {}, maxAllowed: {}, maxThresh: {}",
				serviceDefinition.getServiceName(), currentDisk, resourceThresholdMultiplier, maxAllowedDisk, diskKillThreshold );

			if ( currentDisk > diskKillThreshold ) {
				return true;
			}

			for ( ServiceAlertsEnum alert : ServiceAlertsEnum.values() ) {

				int lastCollectedValue = 0;
				switch (alert) {
				case threads:
					lastCollectedValue = serviceDefinition.getThreadCount();
					break;
					
				case sockets:
					lastCollectedValue = serviceDefinition.getSocketCount();
					break;
					
				case openFileHandles:
					lastCollectedValue = serviceDefinition.getFileCount();
					break;

				default:
					continue;
				}

				long maxAllowed = ServiceAlertsEnum.getLimitFromHierarchy( serviceDefinition, csapApp.lifeCycleSettings(), alert );
				long killThreshold = Math.round( maxAllowed * resourceThresholdMultiplier );

				logger.debug( "{} : Item: {} lastCollectedValue: {}, threshold: {}, maxAllowed: {}, maxThresh: {}",
					serviceDefinition.getServiceName(), alert, lastCollectedValue, resourceThresholdMultiplier, maxAllowed, killThreshold );

				if ( lastCollectedValue > killThreshold ) {
					return true;
				}
			}

		} catch (NumberFormatException e) {
			logger.warn( serviceDefinition.getServiceName() + " - Failed to parse disk: " + serviceDefinition.getDisk() );
		}

		return false;
	}

	private void killService ( ServiceInstance serviceInstance ) {
		logger.warn( "Killing service as Service limits exceeded for "
				+ serviceInstance.getServiceName_Port() );

		ArrayList<String> params = new ArrayList<String>();

		// trigger a system event as well.
		csapEventClient.publishEvent( CsapEventClient.CSAP_SYSTEM_CATEGORY + "/runaway/kill/"
				+ serviceInstance.getServiceName(),
			"Service limits exceeded", null,
			serviceInstance.getRuntime() );

		// log a user event so it is found easier
		csapEventClient.publishEvent( CsapEventClient.CSAP_SVC_CATEGORY + "/"
				+ serviceInstance.getServiceName(),
			"Service limits exceeded, Service will be killed by system",
			null,
			serviceInstance.getRuntime() );

		serviceInstance.setAutoKillInProgress( true );
		serviceManager.runScript( Application.SYS_USER, ServiceOsManager.KILL_FILE,
			serviceInstance.getServiceName_Port(), params,
			null,
			null );
	}

}
