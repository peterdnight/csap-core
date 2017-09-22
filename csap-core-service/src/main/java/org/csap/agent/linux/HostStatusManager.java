package org.csap.agent.linux;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.csap.agent.CsapCoreService;
import org.csap.agent.CSAP;
import org.csap.agent.input.http.api.AgentApi;
import org.csap.agent.input.http.ui.rest.HostRequests;
import org.csap.agent.model.Application;
import org.csap.agent.model.ServiceBaseParser;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.services.HostKeys;
import org.csap.alerts.AlertInstance;
import org.csap.alerts.AlertInstance.AlertItem;
import org.csap.alerts.MonitorMbean.Report;
import org.csap.helpers.CsapRestTemplateFactory;
import org.csap.helpers.CsapSimpleCache;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.javasimon.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *
 * This is used only when jvm is started with -DmgrUi=mgrUi is specified.
 *
 * It fronts all the agent instances, acting as an aggregating proxy for getting
 * os status, etc.
 *
 * This allows a single http request to front all agents on many hosts.
 *
 *
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/3.2.0.RELEASE/spring-framework-reference/html/remoting.html#rest-resttemplate">
 *      Spring REST</a>
 *
 * @author someDeveloper
 *
 */
public class HostStatusManager implements Runnable {

	final Logger logger = LoggerFactory.getLogger( HostStatusManager.class );

	ObjectMapper jacksonMapper = new ObjectMapper();

	BasicThreadFactory schedFactory = new BasicThreadFactory.Builder()
		.namingPattern( "CsapHostJobsScheduler-%d" )
		.daemon( true )
		.priority( Thread.NORM_PRIORITY )
		.build();

	ScheduledExecutorService scheduleGetHostStatusJobs = Executors.newScheduledThreadPool( 1, schedFactory );

	// ScheduledExecutorService scheduleGetHostStatusJobs = Executors
	// .newScheduledThreadPool(1);
	ScheduledFuture<?> refreshHostStatusHandle = null;

	private int connectionTimeoutSeconds = 2;

	boolean isTest = false;
	private File testHostResponseFile;

	/**
	 * For testing only
	 *
	 * @param testHostResponse
	 */
	public HostStatusManager( File testHostResponseFile ) {
		logger.warn( "\n ************** Running in Stub Mode *************** \n" );
		this.isTest = true;
		this.testHostResponseFile = testHostResponseFile;
		// initRestTemplate( 1 );
	}

	Application csapApp = null;

	public HostStatusManager(
			Application csapApplication,
			int numberOfThreads,
			ArrayList<String> hostsToQuery ) {

		this.csapApp = csapApplication;

		csapApp.loadCacheFromDisk( getAlertHistory(), this.getClass().getSimpleName() );

		throttleTimer = CsapSimpleCache.builder(
			csapApplication.getCsapCoreService().getAlerts().getThrottle().getFrequency(),
			CsapSimpleCache.parseTimeUnit(
				csapApplication.getCsapCoreService().getAlerts().getThrottle().getTimeUnit(),
				TimeUnit.HOURS ),
			HostStatusManager.class,
			"Global Alert Throttle" );

		logger.warn( "Constructed with thread count: {}, connectionTimeout: {} Host Count: {}, \n Hosts: {}, \n Alert: {}",
			numberOfThreads, this.connectionTimeoutSeconds, hostsToQuery.size(), hostsToQuery,
			csapApplication.getCsapCoreService().getAlerts() );

		BasicThreadFactory statusFactory = new BasicThreadFactory.Builder()
			.namingPattern( "CsapHostStatus-%d" )
			.daemon( true )
			.priority( Thread.NORM_PRIORITY )
			.build();

		hostStatusRequestPool = Executors.newFixedThreadPool( numberOfThreads,
			statusFactory );

		hostStatusThreadManager = new ExecutorCompletionService<AgentStatus>(
			hostStatusRequestPool );
		hostList = new CopyOnWriteArrayList<String>( hostsToQuery );

		restartHostRefreshTimer( 3 );

	}

	public void restartHostRefreshTimer ( int initialDelaySeconds ) {
		refreshHostStatusHandle = scheduleGetHostStatusJobs
			.scheduleWithFixedDelay( this, initialDelaySeconds, csapApp.getHostRefreshIntervalSeconds(), TimeUnit.SECONDS );
	}

	/**
	 * Kill off the spawned threads - triggered from ServiceRequests
	 */
	public void shutdown () {

		System.out.println( "\n ****  Shutting down jobs in " + HostStatusManager.class.getName() );

		// restFactory.shutdown();

		if ( refreshHostStatusHandle != null ) {
			scheduleGetHostStatusJobs.shutdownNow();
		}

		if ( hostStatusRequestPool != null ) {
			hostStatusRequestPool.shutdownNow();
		}
		csapApp.flushCacheToDisk( getAllAlerts(), this.getClass().getSimpleName() );
	}

	private CopyOnWriteArrayList<String> hostList;

	// private ConcurrentSkipListMap<String, String> hostResponseMap = new
	// ConcurrentSkipListMap<String, String>();
	private ConcurrentSkipListMap<String, ObjectNode> hostJsonMap = new ConcurrentSkipListMap<String, ObjectNode>();

	public ObjectNode getResponseFromHost ( String hostName ) {

		ObjectNode hostResponse = hostJsonMap.get( hostName );
		if ( isTest ) {
			try {
				logger.warn( "Loading test data from : " + testHostResponseFile.getAbsolutePath() );
				hostResponse = jacksonMapper.readValue( testHostResponseFile, ObjectNode.class );
			} catch (Exception e) {
				logger.error( "Failed loading test data" );
			}
		}

		return hostResponse;
	}

	public int totalOpsQueued () {
		// TODO Auto-generated method stub

		int totalOpsQueued = hostJsonMap
			.values()
			.stream()
			.mapToInt( hostRuntime -> {

				if ( hostRuntime != null && hostRuntime.has( "serviceOpsQueue" ) ) {
					return hostRuntime.get( "serviceOpsQueue" ).asInt( 0 );
				}

				return 0;
			} )
			.sum();

		return totalOpsQueued;
	}

	public String getHostWithLowestAttribute ( List<String> hostNames, String attributeName ) {
		String result = hostNames.get( 0 );

		//
		// hostRuntimeInJsonMap
		// .values()
		// .forEach(System.out::println);
		Comparator<Entry<String, ObjectNode>> compareAttribute = ( host1Entry, host2Entry ) -> {

			ObjectNode vm1Json = host1Entry.getValue();
			ObjectNode vm2Json = host2Entry.getValue();

			logger.debug(
				"Entry1: {}, attributeName: {}, value: {}, Entry2: {}, attributeName: {}, value: {}",
				host1Entry.getKey(), attributeName, vm1Json.at( attributeName ).asDouble(),
				host2Entry.getKey(), attributeName, vm2Json.at( attributeName ).asDouble() );

			return Double.compare( vm1Json.at( attributeName ).asDouble(),
				vm2Json.at( attributeName ).asDouble() );

			// return Integer.compare( entry1.getValue().length(),
			// entry2.getValue().length() );
		};

		Optional<Entry<String, ObjectNode>> hostWithLowest = hostJsonMap
			.entrySet()
			.stream()
			.filter( hostEntry -> hostNames.contains( hostEntry.getKey() ) )
			.min( compareAttribute );

		if ( hostWithLowest.isPresent() ) {
			result = hostWithLowest.get().getKey();
		}

		return result;
	}

	public Map<String, ObjectNode> hostsInfo ( List<String> hosts ) {

		return hosts.stream()
			.map( host -> {
				ObjectNode hostRuntime = hostJsonMap.get( host );
				// logger.info(hostRuntime.toString()) ;
				ObjectNode hostConfiguration = jacksonMapper.createObjectNode();
				hostConfiguration.put( "collectedHost", host );
				if ( hostRuntime == null ||
						(hostRuntime != null && hostRuntime.has( "error" )) ) {
					hostConfiguration.put( "error", "Collection failed" );
				} else {
					hostConfiguration.put( "timeStamp", hostRuntime.get( "timeStamp" ).asText() );
					JsonNode hostStats = hostRuntime.get( "hostStats" );
					hostConfiguration.set( "hostStats", hostStats );
				}
				return hostConfiguration;
			} )
			.collect( Collectors.toMap(
				hostStatus -> hostStatus.get( "collectedHost" ).asText(),
				hostStatus -> hostStatus ) );
	}

	public Map<String, ObjectNode> hostsServices ( List<String> hosts, String serviceFilter, String attributeName ) {

		return hosts.stream()
			.map( host -> {
				ObjectNode hostRuntime = hostJsonMap.get( host );
				// logger.info(hostRuntime.toString()) ;
				ObjectNode hostFilteredRuntime = jacksonMapper.createObjectNode();
				hostFilteredRuntime.put( "collectedHost", host );
				if ( hostRuntime == null ||
						(hostRuntime != null && hostRuntime.has( "error" )) ) {
					hostFilteredRuntime.put( "error", "Collection failed" );
				} else {
					hostFilteredRuntime.put( "timeStamp", hostRuntime.get( "timeStamp" ).asText() );
					JsonNode servicesCollected = hostRuntime.get( "services" );
					if ( serviceFilter == null ) {
						hostFilteredRuntime.set( "services", servicesCollected );
					} else {
						ObjectNode servicesFiltered = jacksonMapper.createObjectNode();
						hostFilteredRuntime.set( "services", servicesFiltered );
						servicesCollected.fieldNames().forEachRemaining( servicePortName -> {
							if ( servicePortName.matches( serviceFilter ) ) {
								if ( attributeName == null ) {
									servicesFiltered.set( servicePortName, servicesCollected.get( servicePortName ) );
								} else {
									ObjectNode attributeFiltered = jacksonMapper.createObjectNode();
									JsonNode allAttributes = servicesCollected.get( servicePortName );
									if ( allAttributes.has( attributeName ) ) {
										attributeFiltered.set( attributeName, allAttributes.get( attributeName ) );
									}
									servicesFiltered.set( servicePortName, attributeFiltered );
								}
							}
						} );
					}
				}
				return hostFilteredRuntime;
			} )
			.collect( Collectors.toMap(
				hostStatus -> hostStatus.get( "collectedHost" ).asText(),
				hostStatus -> hostStatus ) );
	}

	public Map<String, ObjectNode> hostsCpuInfo ( List<String> hosts ) {

		return hosts.stream()
			.map( host -> {
				ObjectNode hostRuntime = hostJsonMap.get( host );
				// logger.info(hostRuntime.toString()) ;
				ObjectNode hostConfiguration = jacksonMapper.createObjectNode();
				hostConfiguration.put( "collectedHost", host );
				if ( hostRuntime == null ||
						(hostRuntime != null && hostRuntime.has( "error" )) ) {
					hostConfiguration.put( "error", "Collection failed" );
				} else {
					hostConfiguration.put( "timeStamp", hostRuntime.get( "timeStamp" ).asText() );
					JsonNode hostStats = hostRuntime.get( "hostStats" );
					hostConfiguration.put( "cpuLoad", hostStats.get( "cpuLoad" ).asText() );
					hostConfiguration.put( "cpuCount", hostStats.get( "cpuCount" ).asText() );
					hostConfiguration.put( "cpu", hostStats.get( "cpu" ).asText() );
					hostConfiguration.put( "cpuIoWait", hostStats.get( "cpuIoWait" ).asText() );
				}
				return hostConfiguration;
			} )
			.collect( Collectors.toMap(
				hostStatus -> hostStatus.get( "collectedHost" ).asText(),
				hostStatus -> hostStatus ) );
	}

	public Map<String, ObjectNode> hostsRuntime ( List<String> hosts ) {

		return hosts.stream()
			.map( host -> {
				ObjectNode hostRuntime = hostJsonMap.get( host );
				if ( hostRuntime == null ) {
					hostRuntime = jacksonMapper.createObjectNode();
					hostRuntime.put( "error", "No response found" );
				}
				hostRuntime.put( "collectedHost", host );
				return hostRuntime;
			} )
			.collect( Collectors.toMap(
				hostStatus -> hostStatus.get( "collectedHost" ).asText(),
				hostStatus -> hostStatus ) );
	}

	// public ObjectNode hostsRuntime() {
	// List<ObjectNode> hostJsons = hosts.stream()
	// .map( host -> getHostAsJson( host ) )
	// .filter( java.util.Objects::nonNull )
	// .filter( hostJson -> hostJson.has( HostKeys.lastCollected.jsonId ) )
	// .filter( hostJson -> !hostJson.get( HostKeys.lastCollected.jsonId ).has(
	// "warning" ) )
	// .map( hostJson -> (ObjectNode) hostJson.get(
	// HostKeys.lastCollected.jsonId ) )
	// .collect( Collectors.toList() );
	//
	// return last ;
	// }

	/**
	 * realTime meters csapApp.getCurrentLifecycleMetaData().getRealTimeMeters()
	 *
	 * * if detailMeters == null , then all details will be used.
	 *
	 * @param attribute
	 * @return
	 */
	public void updateRealTimeMeters ( ArrayNode realTimeMeters, List<String> hosts, List<String> detailMeters ) {

		if ( logger.isDebugEnabled() ) { // isDebugEnabled isInfoEnabled

			List<ObjectNode> hostJsons = hosts.stream()
				.map( host -> getHostAsJson( host ) )
				.filter( java.util.Objects::nonNull )
				.filter( hostJson -> hostJson.has( HostKeys.lastCollected.jsonId ) )
				.filter( hostJson -> !hostJson.get( HostKeys.lastCollected.jsonId ).has( "warning" ) )
				.map( hostJson -> (ObjectNode) hostJson.get( HostKeys.lastCollected.jsonId ) )
				.collect( Collectors.toList() );

			logger.debug( "lastCollected: {}", hostJsons );

		}

		for ( JsonNode item : realTimeMeters ) {
			ObjectNode meterJson = (ObjectNode) item;
			String id = meterJson.get( "id" ).asText();
			String[] jsonPath = id.split( Pattern.quote( "." ) );
			String collector = jsonPath[0];
			String attribute = jsonPath[1];
			double hostTotal = 0;
			meterJson.put( "vmCount", 0 );

			try {

				if ( !collector.equals( "jmxCustom" ) ) {
					hostTotal = hosts
						.stream()
						.map( host -> getHostAsJson( host ) )
						.filter( java.util.Objects::nonNull )
						.filter( hostJson -> hostJson.has( HostKeys.lastCollected.jsonId ) )
						.filter( hostJson -> hostJson.get( HostKeys.lastCollected.jsonId ).has( collector ) )
						.map( hostJson -> (ObjectNode) hostJson.get( HostKeys.lastCollected.jsonId ).get( collector ) )
						.filter( collectionJson -> collectionJson.has( attribute ) )
						.mapToInt(
							collectionJson -> getCollectedMetric( meterJson, attribute, collector,
								collectionJson ) )
						.sum();
					if ( detailMeters == null || detailMeters.contains( id ) ) {
						addMetricHostValues( hosts, meterJson, collector, attribute, null, null );
					}
				} else {
					// jmx custom / application metrics have nested data.
					String serviceName = jsonPath[1];
					final String serviceAttribute = jsonPath[2];
					hostTotal = hosts
						.stream()
						.map( host -> getHostAsJson( host ) )
						.filter( java.util.Objects::nonNull )
						.filter( hostJson -> hostJson.has( HostKeys.lastCollected.jsonId ) )
						.filter( hostJson -> hostJson.get( HostKeys.lastCollected.jsonId ).has( collector ) )
						.map( hostJson -> (ObjectNode) hostJson.get( HostKeys.lastCollected.jsonId ).get( collector ) )
						.filter( collectionJson -> collectionJson.has( attribute ) )
						.mapToInt(
							collectionJson -> getCollectedMetric( meterJson, serviceAttribute, collector,
								(ObjectNode) collectionJson.get( serviceName ) ) )
						.sum();

					if ( detailMeters == null || detailMeters.contains( id ) ) {

						addMetricHostValues( hosts, meterJson, collector, attribute, serviceAttribute, serviceName );

					}
				}

				if ( meterJson.get( "vmCount" ).asInt() > 0 && meterJson.has( "average" )
						&& meterJson.get( "average" ).asBoolean() ) {
					meterJson.put( "collectedTotal", hostTotal );
					hostTotal = hostTotal / meterJson.get( "vmCount" ).asInt();
				}
				meterJson.put( "value", hostTotal );

				logger.debug( "collector: {}, attribute: {}, total: {}", collector, attribute, hostTotal );

			} catch (Exception e) {
				logger.warn( "Exception while process ing realtime meters" + meterJson, e );
			}

		}

	}

	public void addMetricHostValues (	List<String> hosts, ObjectNode meterJson, String collector, String attribute,
										String serviceAttribute, String serviceName ) {
		final String detailAttribute;

		if ( serviceAttribute != null ) {
			detailAttribute = serviceAttribute;
		} else {
			detailAttribute = attribute;
		}

		for ( String detailHost : hosts ) {
			List<String> detailHosts = new ArrayList<String>();
			detailHosts.add( detailHost );
			detailHosts
				.stream()
				.map( host -> getHostAsJson( host ) )
				.filter( java.util.Objects::nonNull )
				.filter( hostJson -> hostJson.has( HostKeys.lastCollected.jsonId ) )
				.filter( hostJson -> hostJson.get( HostKeys.lastCollected.jsonId ).has( collector ) )
				.map( hostJson -> (ObjectNode) hostJson.get( HostKeys.lastCollected.jsonId ).get( collector ) )
				.filter( collectionJson -> collectionJson.has( attribute ) )
				.forEach(
					collectionJson -> addDetail( meterJson, detailHost, detailAttribute, collectionJson,
						serviceName ) );

		}
	}

	private void addDetail (	ObjectNode meterJson, String detailHost, String attribute, ObjectNode collectionJson,
								String serviceName ) {

		logger.debug( "detailHost: {}, attribute: {}, collectionJson: {}", detailHost, attribute, collectionJson );

		if ( serviceName != null ) {
			collectionJson = (ObjectNode) collectionJson.get( serviceName );
		}

		if ( !meterJson.has( "hostNames" ) ) {
			meterJson.putArray( "hostNames" );
			meterJson.putArray( "hostValues" );
		}

		ArrayNode hostValues = (ArrayNode) meterJson.get( "hostValues" );
		ArrayNode hostNames = (ArrayNode) meterJson.get( "hostNames" );

		hostNames.add( detailHost );
		hostValues.add( collectionJson.get( attribute ) );

	}

	public int getCollectedMetric ( ObjectNode meterJson, String attribute, String collector, ObjectNode lastCollected ) {

		if ( lastCollected == null || !lastCollected.has( attribute ) ) {
			logger.info( " Null attribute: " + attribute + "\n collectionJson: " + lastCollected );
			return 0;
		}
		int collected = lastCollected.get( attribute ).asInt();
		meterJson.put( "vmCount", meterJson.get( "vmCount" ).asInt() + 1 );

		return collected;
	}

	public ObjectNode getHostAsJson ( String hostName ) {

		if ( isTest ) {
			try {
				logger.warn( "Loading test data from : " + testHostResponseFile.getAbsolutePath() );
				return (ObjectNode) jacksonMapper.readTree( testHostResponseFile );
			} catch (Exception e) {
				logger.error( "Failed loading test data" );
			}
		}

		if ( hostJsonMap.containsKey( hostName ) ) {

			try {
				ObjectNode hostStatus = hostJsonMap.get( hostName );

				if ( hostStatus.has( "error" ) ) {
					return null;
				}

				return hostStatus;
			} catch (Exception e) {
				logger.error( "Failed reading", e );
			}
		}

		return null;

	}

	public ObjectNode getServiceRuntime ( String hostName, String serviceName_port ) {

		if ( hostJsonMap.containsKey( hostName ) ) {

			try {
				ObjectNode hostRuntime = hostJsonMap.get( hostName );

				if ( hostRuntime.has( "error" ) ) {
					return null;
				} else {
					ObjectNode serviceNode = (ObjectNode) hostRuntime
						.path( HostKeys.services.jsonId );
					if ( serviceNode == null || !serviceNode.has( serviceName_port ) ) {
						return null;
					}
					return (ObjectNode) serviceNode.path( serviceName_port );
				}
			} catch (Exception e) {
				logger.error( "Failed reading", e );
			}
		}

		return null;
	}

	public void wipeList () {
		logger.warn( "Received clear request" );
		hostList.clear();

	}

	ExecutorService hostStatusRequestPool;
	ExecutorCompletionService<AgentStatus> hostStatusThreadManager;

	public void run () {

		logger.debug( "Checking for hosts to Query" );
		try {
			update_hosts_runtime_lock.lock();
			executeQueriesInParallel( null );
		} catch (Exception e) {
			logger.warn( "Scheduled refresh interruption: " + e.getMessage(), e );
		} finally {

			try {
				update_hosts_runtime_lock.unlock();
			} catch (Exception e) {
				logger.warn( "Failed to release lock: " + e.getMessage() );
			}
		}

	}

	private class AgentStatus {

		public AgentStatus( String host, String jsonResponse ) {
			this.host = host;
			this.hostRuntimeJson = jsonResponse;
		}

		public String getHost () {
			return host;
		}

		String host;

		String hostRuntimeJson;

		public String getHostRuntimeJson () {
			return hostRuntimeJson;
		}

	}

	private class AgentStatusCallable implements Callable<AgentStatus> {

		private String host;
		private boolean resetCache;

		public AgentStatusCallable( String host, boolean resetCache ) {
			this.host = host;
			this.resetCache = resetCache;

		}

		@Override
		public AgentStatus call () {

			// Thread.currentThread().setName("HostQuery_" + host);
			// if ( !Thread.currentThread().getName().contains("hostQuery") )
			// Thread.currentThread().setName("hostQuery_" +
			// Thread.currentThread().getName());
			Split transferTimer = SimonManager.getStopwatch( "agent.remote.status" ).start();
			String jsonResponse = "{\"error\": \"Reason: Initialization in Progress: "
					+ host + "\"}";
			// always use pooled connection
			RestTemplate pooledRest =csapApp.getAgentPooledConnection( 1, 1 ) ;
			jsonResponse = queryAgentStatus( pooledRest );
			transferTimer.stop();

			SimonManager.getStopwatch( "agent.remote.status." + host ).addSplit( transferTimer );
			SimonManager.getStopwatch( "java.HostStatusManager.getHostRuntimeJson" ).addSplit( transferTimer );

			logger.debug( "{} pool: {} \n response: {}", host, pooledRest, jsonResponse );
			return new AgentStatus( host, jsonResponse );

		}

		/**
		 *
		 * method to perform rest call to agent to retrieve process state
		 *
		 * @see HostRequests#getManagerJson(javax.servlet.http.HttpServletRequest,
		 *      javax.servlet.http.HttpServletResponse)
		 * @param restTemplate
		 * @return
		 */
		private String queryAgentStatus ( RestTemplate restTemplate ) {
			String jsonResponse;

			String statusUrl = csapApp.getAgentUrl( host, CsapCoreService.API_AGENT_URL + AgentApi.RUNTIME_URL + "?", true );

			if ( resetCache ) {
				statusUrl += "resetCache=true&";
				// activeUsersCache.setLastRefreshMs( 0 );
			}

			try {

				Map<String, String> vars = new HashMap<String, String>();

				logger.debug( "Querying host url: {}", statusUrl );

				String restResult = restTemplate.getForObject( statusUrl, String.class, vars );

				// logger.info( "{} \n {} ", statusUrl , restResult);
				if ( restResult.indexOf( "cpuUtil" ) == -1 ) {
					logger.error( "Error: Invalid response from host query: " + statusUrl
							+ "\n==============>\n" + restResult );

					jsonResponse = "{\"error\": \"Failed to parse response from host: "
							+ host + "\"}";
				} else {
					jsonResponse = restResult;
					// if ( host.contains("db")) {
					// logger.info(restResult) ;
					// }
				}

			} catch (Exception e) {
				// String message = "{\"error\": \"Invalid response from host: "
				// + host + " Message:"
				// + e.getMessage().replaceAll("\"", "") + "\"}" ;
				jsonResponse = "{\"error\": \"Connection Failure - verify agent is running and accessible on: "
						+ host + "\"}";
				logger.debug( "{} has an invalid response: {}", statusUrl, CSAP.getCsapFilteredStackTrace( e ) );
			}
			return jsonResponse;
		}

	}

	private ReentrantLock update_hosts_runtime_lock = new ReentrantLock();

	/**
	 * Invoked in response to UI by a user. If a full refresh is issued, restart
	 * the scheduled event.
	 *
	 * Working with timers can incredibly nuanced due to race conditions.
	 * Test/test/test.
	 *
	 * @param hostNameArray
	 */
	public void refreshAndWaitForComplete ( List<String> hosts ) {

		boolean gotLock = false;
		try {
			gotLock = update_hosts_runtime_lock.tryLock( 5, TimeUnit.SECONDS );
			if ( gotLock ) {
				List<String> hostToUpdate;
				if ( hosts == null ) {
					hostToUpdate = hostList;

					logger.debug( "Cancelling scheduler" );
					if ( refreshHostStatusHandle != null && !refreshHostStatusHandle.isDone() ) {
						refreshHostStatusHandle.cancel( true );
					}
				} else {
					hostToUpdate = hosts;
				}
				executeQueriesInParallel( hostToUpdate );
			} else {
				logger.warn( "Status refresh requested did NOT get lock in time interval, skipping request." );
				;
			}
		} catch (Exception e) {

			logger.warn( "UI triggered refresh interruption, {}", CSAP.getCsapFilteredStackTrace( e ) );
			
		} finally {

			if ( gotLock ) {
				if ( hosts == null ) {

					logger.debug( "Restarting scheduler" );
					// We just got results, so do a full interval

					if ( !isTest )
						restartHostRefreshTimer( csapApp.getHostRefreshIntervalSeconds() );
				}
				update_hosts_runtime_lock.unlock();
			}
		}
	}

	private void executeQueriesInParallel ( List<String> hostToUpdate ) {

		logger.debug( "Lock Requests: {}", update_hosts_runtime_lock.getQueueLength() );

		if ( isTest ) {
			logger.warn( "Running in test mode, status will be loaded from disk {}", hostToUpdate );
			return;
		}

		logger.debug( "Received a blocking refresh request, cancel existing job, then refresh on hosts: {}",
			hostToUpdate );

		try {

			List<Future<AgentStatus>> futureResultsList = new ArrayList<>();
			for ( String host : hostList ) {
				Future<AgentStatus> futureResult = hostStatusThreadManager.submit( new AgentStatusCallable( host, false ) );
				futureResultsList.add( futureResult );
			}
			for ( int i = 0; i < futureResultsList.size(); i++ ) {
				try {
					Future<AgentStatus> finishedJob = hostStatusThreadManager
						.take();
					// Future<QueryResult> finishedJob = hostStatusThreadManager
					// .poll(10, TimeUnit.SECONDS);

					if ( finishedJob != null ) {

						ObjectNode hostObject = (ObjectNode) jacksonMapper.readTree( finishedJob.get().getHostRuntimeJson() );
						hostJsonMap.put(
							finishedJob.get().getHost(), hostObject );

						checkForUpdatedHealthReport( finishedJob.get().getHost(), hostObject );
					} else {
						logger.error( "Got a Null result" );
						break;
					}
				} catch (Exception e) {
					logger.error( "Got an exception while processing task results {}", 
						CSAP.getCsapFilteredStackTrace( e ) );
				}
			}

			// cleanUp in case anything hangs.
			for ( Future<AgentStatus> f : futureResultsList ) {
				if ( f.cancel( true ) ) {
					logger.warn( "Task was cancelled: " + f.toString() );
				}
			}

		} catch (Exception e) {
			logger.error( "Failed waiting", e );
		}

		logger.debug( "blocking update completed" );

	}

	public ArrayNode getAllAlerts () {
		ArrayNode all = jacksonMapper.createArrayNode();
		all.addAll( getAlertHistory() );
		all.addAll( getAlertsThrottled() );

		return all;
	}

	private ArrayNode getAlertHistory () {
		return alertHistory;
	}

	private ArrayNode alertHistory = jacksonMapper.createArrayNode();
	private ArrayNode alertsThrottled = jacksonMapper.createArrayNode();

	private CsapSimpleCache throttleTimer;

	private ConcurrentSkipListMap<String, String> hostServiceReportTimes = new ConcurrentSkipListMap<String, String>();

	synchronized void checkForUpdatedHealthReport ( String hostName, ObjectNode hostStatus ) {

		if ( hostStatus.has( HostKeys.services.jsonId ) ) {

			ObjectNode services = (ObjectNode) hostStatus.get( HostKeys.services.jsonId );

			ServiceBaseParser.getJsonAttributeStream( services )

				.map( serviceName -> services.get( serviceName ) )

				.filter( serviceStatus -> serviceStatus.has( HostKeys.healthReportCollected.jsonId ) )
				.filter(
					serviceStatus -> {

						// return true only if healthReport is present and
						// contains failed items
						JsonNode healthReport = serviceStatus.get( HostKeys.healthReportCollected.jsonId );

						if ( healthReport.has( Report.healthy.json ) ) {
							return !healthReport.get( Report.healthy.json ).asBoolean();
						}
						return false;
					} )

				.forEach( serviceWithFailedReport -> {
					try {
						ServiceInstance runtimeInstance = jacksonMapper.readValue( serviceWithFailedReport.traverse(),
							ServiceInstance.class );
						runtimeInstance.setHostName( hostName );

						String lastUpdatedTime = runtimeInstance.getHealthReportCollected().get( Report.lastCollected.json ).asText();

						String lastUpdatedKey = hostName + runtimeInstance.getServiceName_Port();

						logger.debug( "lastUpdatedKey: {}, lastUpdatedTime: {}", lastUpdatedKey, lastUpdatedTime );

						if ( hostServiceReportTimes.containsKey( lastUpdatedKey ) &&
								hostServiceReportTimes.get( lastUpdatedKey ).equals( lastUpdatedTime ) ) {

							logger.debug( "skipping as items already added: {},\n {}",
								runtimeInstance.getServiceName(),
								runtimeInstance.getHealthReportCollected() );
						} else {

							logger.debug( "Adding health alerts for {},\n {}",
								runtimeInstance.getServiceName(),
								runtimeInstance.getHealthReportCollected() );

							hostServiceReportTimes.put( lastUpdatedKey, lastUpdatedTime );

							String healthUrl = "notFound";
							try {
								ServiceInstance serviceInstance = csapApp.getServiceInstanceAnyPackage(
									runtimeInstance.getServiceName_Port(), runtimeInstance.getHostName() );
								healthUrl = serviceInstance.getUrl() + "/csap/health";
							} catch (Exception e) {
								logger.error( "Did not find service: {}",
									runtimeInstance.getServiceName_Port(),
									CSAP.getCsapFilteredStackTrace( e ) );
							}

							addUpdatedServiceAlerts( runtimeInstance, lastUpdatedTime, healthUrl );
						}

					} catch (Exception e) {
						logger.warn( "Ignoring exception: " + e.getClass().getName(), e );
					}
				} );
		}

		logger.debug( "history size: {} throttle size: {} ", getAlertHistory().size(), getAlertsThrottled().size() );

		while (getAlertHistory().size() > csapApp.getCsapCoreService().getAlerts().getRememberCount()) {
			getAlertHistory().remove( 0 );
		}

		if ( getAlertsThrottled().size() > (csapApp.getCsapCoreService().getAlerts().getRememberCount() * .1) ) {
			logger.error( "Excessive alerts happening each hour: {}, allowed is 10% of backlog: {}",
				getAlertsThrottled().size(), csapApp.getCsapCoreService().getAlerts().getRememberCount() );
		}
		while (getAlertsThrottled().size() > (csapApp.getCsapCoreService().getAlerts().getRememberCount() * .1)) {
			getAlertsThrottled().remove( 0 );
		}

	}

	// {"collectionCount":61,"lastCollected":"13:31:51 , Jan
	// 6","isHealthy":false,
	// "undefined":[],"pendingFirstInterval":[],"limitsExceeded":[{"id":"health.exceptions"
	// ,"type":"Occurences -
	// Max","collected":5,"limit":0,"description":"
	// Collected: 5, Limit: 0"}]}

	private void addUpdatedServiceAlerts ( ServiceInstance runtimeInstance, String lastUpdated, String healthUrl )
			throws IOException, JsonParseException, JsonMappingException {

		ObjectNode healthReport = runtimeInstance.getHealthReportCollected();

		// increment counters and dates - or add
		ArrayList<ObjectNode> activeAlerts = jacksonMapper.readValue(
			healthReport.get( Report.limitsExceeded.json ).traverse(),
			new TypeReference<ArrayList<ObjectNode>>() {
			} );

		long now = System.currentTimeMillis();
		activeAlerts.forEach( item -> {
			item.put( AlertItem.host.json, runtimeInstance.getHostName() );
			item.put( AlertItem.service.json, runtimeInstance.getServiceName() );
			item.put( "port", runtimeInstance.getPort() );
			item.put( AlertInstance.AlertItem.count.json, 1 );
			item.put( AlertInstance.AlertItem.formatedTime.json, lastUpdated );
			item.put( AlertInstance.AlertItem.timestamp.json, now );
			item.put( "healthUrl", healthUrl );

		} );

		activeAlerts.forEach( activeAlert -> {
			int matchCount = 0;
			int lastMatchIndex = 0;
			int index = 0;
			for ( JsonNode throttledEvent : getAlertsThrottled() ) {
				// handles uniqueness of host and service
				if ( AlertInstance.AlertItem.isSameId( activeAlert, throttledEvent ) ) {
					matchCount++;
					lastMatchIndex = index;
				}
				index++;
			}
			if ( matchCount >= csapApp.getCsapCoreService().getAlerts().getThrottle().getCount() ) {
				// update the count
				int oldCount = getAlertsThrottled()
					.get( lastMatchIndex )
					.get( AlertInstance.AlertItem.count.json )
					.asInt();
				activeAlert.put( AlertInstance.AlertItem.count.json, 1 + oldCount );

				// remove the oldest
				getAlertsThrottled().remove( lastMatchIndex );

			}
			// add the newest
			getAlertsThrottled().add( activeAlert );

		} );

		if ( getThrottleTimer().isExpired() ) {
			// Always add in memory browsing
			getAlertHistory().addAll( getAlertsThrottled() );
			getThrottleTimer().reset();
			getAlertsThrottled().removeAll();
		}
	}

	public ArrayNode getAlertsThrottled () {
		return alertsThrottled;
	}

	public void setAlertsThrottled ( ArrayNode alertsThrottled ) {
		this.alertsThrottled = alertsThrottled;
	}

	public CsapSimpleCache getThrottleTimer () {
		return throttleTimer;
	}

	public void setThrottleTimer ( CsapSimpleCache throttleTimer ) {
		this.throttleTimer = throttleTimer;
	}

}
