package org.csap.agent.stats;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.management.ManagementFactory;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.csap.agent.CSAP;
import org.csap.agent.model.Application;
import org.csap.agent.model.ServiceAlertsEnum;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.services.OsManager;
import org.csap.agent.stats.ServiceMeter.JavaCollectionType;
import org.csap.alerts.MonitorMbean.Report;
import org.javasimon.Counter;
import org.javasimon.CounterSample;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.javasimon.Stopwatch;
import org.javasimon.StopwatchSample;
import org.javasimon.jmx.SimonManagerMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jmx.remote.util.EnvHelp;
import com.sun.management.OperatingSystemMXBean;

@SuppressWarnings ( "restriction" )
public class ServiceCollector extends HostCollector implements Runnable {

	final Logger logger = LoggerFactory.getLogger( ServiceCollector.class );

	// String metricsArray[] = { "cpuPercent", "heapUsed", "heapMax",
	// "httpConnections", "ajpConnections" };
	private long maxCollectionAllowedInMs = 2000;

	private JavaCommonCollector javaCommonCollector = new JavaCommonCollector();
	private HttpCollector httpCollector;

	public ServiceCollector( Application csapApplication, OsManager osManager,
			int intervalSeconds, boolean publishSummary ) {

		super( csapApplication, osManager, intervalSeconds, publishSummary );

		httpCollector = new HttpCollector( csapApplication, this );

		if ( Application.isRunningOnDesktop() && !csapApplication.isJunit() ) {
			System.err.println( "\n ============= DESKTOP detected - setting logs to ERROR " );
			Configurator.setLevel( ServiceCollector.class.getName(), Level.ERROR );
		}

		timeStampArray_m = jacksonMapper.createArrayNode();
		totalCpuArray = jacksonMapper.createArrayNode();

		setMaxCollectionAllowedInMs( csapApplication
			.lifeCycleSettings()
			.getMaxJmxCollectionMs() );

		scheduleCollection( this );
	}

	//
	// final OperatingSystemMXBean osStats = ManagementFactory
	// .getOperatingSystemMXBean();
	// Sun mbean has more features then found in jdk
	final OperatingSystemMXBean osStats = ManagementFactory
		.getPlatformMXBean( OperatingSystemMXBean.class );

	ArrayList<Long> timestamps = new ArrayList<Long>();

	private Map<String, ObjectNode> jmxResultsCache = new HashMap<String, ObjectNode>();
	private Map<String, String> serviceRemoteHost = new HashMap<String, String>();

	public Map<String, ObjectNode> getJmxResultsCache () {
		return jmxResultsCache;
	}

	public void setJmxResultsCache ( Map<String, ObjectNode> cache_serviceToObjectNode ) {
		this.jmxResultsCache = cache_serviceToObjectNode;
	}

	Map<String, ObjectNode> customResultsCache = new HashMap<String, ObjectNode>();

	public Map<String, ObjectNode> getCustomResultsCache () {
		return customResultsCache;
	}

	ArrayNode timeStampArray_m;
	ArrayNode totalCpuArray;

	ObjectMapper jacksonMapper = new ObjectMapper();

	// Hook to avoid flooding buffer
	boolean isShowWarnings = true;

	long lastMessagesDisplayed = 0;
	static long DAY_IN_MS = 24 * 60 * 60 * 1000;

	@Override
	public void run () {

		// initCollectorThread(); Switching to workers

		try {

			// logger.warn( "\n\n *********************** Collecting Service
			// Data *********************\n\n" );
			logger.debug( "Collecting Service Data" );

			if ( logger.isDebugEnabled()
					|| (isPublishSummaryAndPerformHeartBeat() && System.currentTimeMillis() - lastMessagesDisplayed > DAY_IN_MS) ) {
				// only shown one collection per day
				logger.info(
					"\n\n ****************** Collection Failures will be displayed, and should be corrected by operator\n\n" );
				lastMessagesDisplayed = System.currentTimeMillis();
				isShowWarnings = true;
			} else {
				isShowWarnings = false;
			}

			updateTimeStampAndCpu();

			performJmxCollection();

			httpCollector.collect();

			cleanUpServiceCache();

			// Use fixed iterations??
			peformUploadIfNeeded();

		} catch (Exception e) {
			logger.error( "Exception in processing", e );
			try {
				// add a lag. This should never happen, but if it does we
				// will not hammer on resources.
				Thread.sleep( 5000 );
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

		logger.debug( "ServiceMetricsRunnable thread exiting: " + collectionIntervalSeconds );
	}

	private void updateTimeStampAndCpu () {
		ObjectNode hostRuntimeNode = (ObjectNode) osManager
			.getServiceMetrics( true );
		int totalCpuFromSunMbean = -1;
		
		// logger.info( "csapApplication.isBootstrapComplete {}", csapApplication.isBootstrapComplete() );

		if ( hostRuntimeNode == null || !hostRuntimeNode.has( "ps" ) || !csapApplication.isBootstrapComplete() ) {
			logger.warn( "Failed to get valid data " );
			totalCpuArray.insert( 0, -1 );
		} else {

			timeStampArray_m.insert( 0,
				Long.toString( System.currentTimeMillis() ) );
			try {
				// int mpTotal = (hostRuntimeNode.get("mp").get("all")
				// .get("puser").asInt() + hostRuntimeNode.get("mp")
				// .get("all").get("psys").asInt())
				// * osStats.getAvailableProcessors();

				totalCpuFromSunMbean = ((int) (osStats.getSystemCpuLoad() * 100));
				// logger.debug("Parsing: " +
				// hostRuntimeNode.get("mp").get("all").get("puser") );
				totalCpuArray.insert( 0, totalCpuFromSunMbean );
			} catch (Exception e) {
				totalCpuArray.insert( 0, -1 );
			}
		}

		if ( timeStampArray_m.size() > getInMemoryCacheSize() ) {
			timeStampArray_m.remove( timeStampArray_m.size() - 1 );
			totalCpuArray.remove( timeStampArray_m.size() - 1 );
		}
	}

	public void testHttpCollection ( long maxConnectionInMs ) {
		// set timeoutout on desktop

		setMaxCollectionAllowedInMs( maxConnectionInMs );
		logger.info( "\n\n ==================== Test =======================\n\n" );

		updateTimeStampAndCpu();
		httpCollector.collect();
		updateTimeStampAndCpu();
		httpCollector.collect();
	}

	public int getTestNumRetries () {
		return testNumRetries;
	}

	private int testNumRetries = 999;
	private long testServiceTimeout = -1;
	private String testServiceTimeoutName = null;

	public String getTestServiceTimeoutName () {
		return testServiceTimeoutName;
	}

	public long getTestServiceTimeout () {
		return testServiceTimeout;
	}

	/**
	 * Junit testing only
	 *
	 * @param testServiceTimeout
	 * @param serviceName
	 * @param numRetries
	 */
	public void setTestServiceTimeout ( long testServiceTimeout, String serviceName, int numRetries ) {
		this.testServiceTimeout = testServiceTimeout;
		this.testServiceTimeoutName = serviceName;
		this.testNumRetries = numRetries;
	}

	// private static String TEST_HOST="csapdb-dev01" ;
	private static String TEST_HOST = "csap-dev01";

	public void testJmxCollection ( String testHost ) {

		TEST_HOST = testHost;
		logger.info( "\n\n ==================== Test using host: {} =======================", TEST_HOST );
		updateTimeStampAndCpu();
		performJmxCollection();
		updateTimeStampAndCpu();
		performJmxCollection();
	}

	public void testJmxCollection ( ServiceInstance serviceInstance, String testHost ) {

		setTestHeartBeat( true );
		MAX_TIME_TO_COLLECT_INTERVAL_MS = 30 * 1000;
		TEST_HOST = testHost;
		logger.info( "\n\n ==================== Testing: {} on  host: {}, serviceMeters: {}  =======================\n\n\n",
			serviceInstance.getServiceName_Port(), TEST_HOST, serviceInstance.hasServiceMeters() );
		updateTimeStampAndCpu();
		// performJmxCollection();
		collectJmxDataForService( serviceInstance );
		updateTimeStampAndCpu();
		collectJmxDataForService( serviceInstance );
	}

	// this is total time for all services on VM being collected.
	// Typical collection only take ~30ms per services ; retries are reserved in
	// case a major gc occurs
	private int MAX_TIME_TO_COLLECT_INTERVAL_MS = 10 * 1000;

	private int numberOfCollectionAttempts = 0;

	public int getNumberOfCollectionAttempts () {
		return numberOfCollectionAttempts;
	}

	long collectionStart = 0;

	private void performJmxCollection () {

		collectionStart = System.currentTimeMillis();

		// Java GC can very occasionally prevent collection
		// retries are limited to avoid impacting subsequent collections
		numberOfCollectionAttempts = 0;
		Split jmxTimer = SimonManager.getStopwatch( "collector.jmx" ).start();

		Stream<ServiceInstance> serviceToCollectStream = csapApplication
			.getActiveModel()
			.getServicesOnHost( Application.getHOST_NAME() );

		numberOfCollectionAttempts = 0;

		Counter jmxConnectionRetry = SimonManager.getCounter( "collector.jmx.connection.retry" );
		while (true) {
			numberOfCollectionAttempts++; // Strictly for tracking

			List<ServiceInstance> failedToCollectInstances = serviceToCollectStream
				.filter( ServiceInstance::isPerformJmxCollection )
				.filter( this::collectJmxDataForService )
				.collect( Collectors.toList() );

			if ( failedToCollectInstances.size() > 0 ) {
				jmxConnectionRetry.increase();
				serviceToCollectStream = failedToCollectInstances.stream();
				try {

					logger.info(
						"*jmxCollectionFailure item count: {} ,  attempts taken: {}, sleeping for 1s and trying again.",
						failedToCollectInstances.size(), numberOfCollectionAttempts );
					Thread.sleep( 1000 );
				} catch (InterruptedException e) {
					logger.debug( "jmxRetry faile", e );
				}
			} else {
				break;
			}
		}

		jmxTimer.stop();
	}

	/**
	 * GC will cause connection failures; retry until
	 * MAX_TIME_TO_COLLECT_INTERVAL_MS
	 *
	 * @param serviceInstance
	 * @return
	 */
	private boolean collectJmxDataForService ( ServiceInstance serviceInstance ) {
		boolean isIgnoreCollectionFailure = true;
		if ( System.currentTimeMillis() - collectionStart > MAX_TIME_TO_COLLECT_INTERVAL_MS ) {
			isIgnoreCollectionFailure = false;
			logger.warn( "Final collection attempt due to collection time is longer then {} seconds",
				MAX_TIME_TO_COLLECT_INTERVAL_MS / 1000 );
		}
		return !executeJmxCollection( serviceInstance, isIgnoreCollectionFailure );
	}

	private HashMap<String, ServiceCollectionResults> lastCollectedResults = new HashMap<String, ServiceCollectionResults>();

	public HashMap<String, ServiceCollectionResults> getLastCollectedResults () {
		return lastCollectedResults;
	}

	private boolean executeJmxCollection ( ServiceInstance serviceInstance, boolean isIgnoreCollectionFailure ) {

		String serviceNamePort = serviceInstance.getServiceName_Port();

		logger.debug( "{} Getting JMX Metrics, isIgnoreCollectionFailure: {}, custom metrics is: {}",
			serviceNamePort, isIgnoreCollectionFailure, serviceInstance.getServiceMeters() );

		Stopwatch jmxTotalStopWatch = SimonManager
			.getStopwatch( "collector.jmx."
					+ serviceInstance.getServiceName() );

		Split jmxTotalSpit = jmxTotalStopWatch.start();

		Stopwatch serviceConnectionStopWatch = SimonManager
			.getStopwatch( "collector.jmx." +
					serviceInstance.getServiceName() + ".connect" );

		Split connectSplit = null;

		// default value is 0 - gets updated if service is online
		ServiceCollectionResults applicationResults = new ServiceCollectionResults( serviceInstance, getInMemoryCacheSize() );

		serviceInstance
			.getServiceMeters()
			.stream()
			.forEach( serviceMeter -> {
				applicationResults.addCustomResultLong( serviceMeter.getCollectionId(), 0l );
			} );

		// Only connect to active instances
		if ( !serviceInstance.isRunning()
				&& !serviceInstance.isRemoteCollection() ) {

			logger.debug( "{} is inactive, skipping collection", serviceNamePort );

		} else {

			logger.debug( "{} is active, starting JMX collection ", serviceNamePort );

			// String opHost = Application.getHOST_NAME();
			String jmxHost = "localhost";
			String jmxPort = serviceInstance.getJmxPort();
			if ( Application.isRunningOnDesktop() ) {
				jmxHost = TEST_HOST;

				if ( isShowWarnings ) {
					logger.warn( "JmxConnection using " + jmxHost + " For desktop testing" );
				}
			}

			if ( serviceInstance.isRemoteCollection() ) {
				jmxHost = serviceInstance.getCollectHost();
				jmxPort = serviceInstance.getCollectPort();
				if ( !serviceRemoteHost.containsKey( serviceNamePort ) ) {
					serviceRemoteHost.put( serviceNamePort, jmxHost );
				}
			}

			long maxJmxCollectionMs = csapApplication
				.lifeCycleSettings()
				.getMaxJmxCollectionMs();

			if ( getTestServiceTimeout() > 0
					&& serviceInstance.getServiceName_Port().equals( getTestServiceTimeoutName() )
					&& getNumberOfCollectionAttempts() < getTestNumRetries() ) {
				maxJmxCollectionMs = getTestServiceTimeout();
			}
			// String serviceUrl = "service:jmx:rmi://" + opHost
			// + "/jndi/rmi://" + opHost + ":" + opPort + "/jmxrmi";
			String serviceUrl = "service:jmx:rmi:///jndi/rmi://" + jmxHost + ":" + jmxPort + "/jmxrmi";
			JMXConnector connector = null;
			try {
				// REF. http://wiki.apache.org/tomcat/FAQ/Monitoring

				MBeanServerConnection mbeanConn = null;

				// keepRunning is set to false in junits
				if ( serviceInstance
					.getServiceName()
					.equals( "CsAgent" )
						&& isKeepRunning() ) {
					// remote connections to self not possible. So
					// we
					// grab from tomcat
					mbeanConn = (MBeanServer) ManagementFactory
						.getPlatformMBeanServer();
				} else {

					connectSplit = serviceConnectionStopWatch.start();
					// connector = JMXConnectorFactory.connect(new
					// JMXServiceURL(serviceUrl));

					// Add a max of 2 retries to handle GC collections
					try {

						connector = connectWithTimeout( new JMXServiceURL(
							serviceUrl ), maxJmxCollectionMs, TimeUnit.MILLISECONDS );
						connectSplit.stop();
					} catch (Exception connectException) {
						connectSplit.stop();

						// increase counter
						SimonManager.getCounter( "collector.jmx." +
								serviceInstance.getServiceName() + ".connect" + ".failures" )
							.increase();
						
						logger.warn( "\n *** Failed connecting to: {} using: {}, due to: {}",
							serviceInstance.getServiceName(),
							serviceUrl,
							connectException.getClass().getName() );
						logger.debug( "Reason: {}", CSAP.getCsapFilteredStackTrace( connectException ) );
						if ( isIgnoreCollectionFailure ) {
							return false; // try agains will occur
						} else {
							// too many attempts will cause timers to overlap.
							throw connectException;
						}
					}

					mbeanConn = connector.getMBeanServerConnection();

					logger.debug( "{}  ***JMX Connection time: {} ", serviceNamePort, connectSplit.toString() );
				}

				// NOTE: We use a single connection to collect first the
				// JMX
				// info, then optionally any custom attributes
				javaCommonCollector.collect( mbeanConn, applicationResults );

				if ( serviceInstance.hasServiceMeters() ) {
					executeCustomJmxCollection( mbeanConn, applicationResults );
				}
				if ( serviceInstance.isHealthReportConfigured() ) {
					executeHealthReportCollection( mbeanConn, applicationResults );
				}

				logger.debug( "\n ******* App Collection Complete: {} \n{}\n", serviceInstance.getServiceName_Port(),
					WordUtils.wrap( applicationResults.toString(), 200 ) );

			} catch (Exception e) {

				SimonManager.getCounter( "collector.jmx.failures" ).increase();
				SimonManager.getCounter( "collector.jmx." + serviceInstance.getServiceName() + ".failures" ).increase();
				SimonManager.getCounter( "collector.jmx." + serviceInstance.getServiceName() + ".failures" ).increase();
				logger.debug( "{} Failed to get jmx data for service", serviceNamePort, e );

				if ( isShowWarnings ) {

					String reason = e.getMessage();
					if ( reason != null && reason.length() > 60 ) {
						reason = e
							.getClass()
							.getName();
					}

					logger.warn( "\n **** Failed to collect {} for service {}\n Reason: {}, Cause: {}",
						"commmon attributes", serviceNamePort, reason, e.getCause() );
				}

				// resultNode.put("error", "Failed to invoke JMX"
				// + Application.getCustomStackTrace(e));
			} finally {
				if ( connectSplit != null ) {
					connectSplit.stop(); // in case not caught above
				}
				try {
					if ( connector != null ) {
						logger.debug( "{} Closing JMX connection id: {}", serviceInstance.getServiceName_Port(),
							connector.getConnectionId() );
						connector.close();
						logger.debug( "{} --Closed JMX connection", serviceInstance.getServiceName_Port() );
					}

				} catch (Exception e) {

					logger.debug( "Failed closing connection: {} due to: {}", serviceNamePort,
						CSAP.getCsapFilteredStackTrace( e ) );

					if ( !e
						.getMessage()
						.contains( "Not connected" ) ) {
						logger.error(
							"Failed closing connection for: " + serviceNamePort + " reason: " + e.getMessage() );
					}
				}
			}
		}

		applicationResults.updateJmxResultCache( getJmxResultsCache() );
		applicationResults.updateCustomResultCache( getCustomResultsCache() );

		if ( isPublishSummaryAndPerformHeartBeat() || isTestHeartBeat() ) {

			// update instance settings for availability checks
			serviceInstance.setNumTomcatConns( applicationResults.getHttpConn() );
			serviceInstance.setJvmThreadCount( applicationResults.getJvmThreadCount() );
			if ( serviceInstance.isApplicationHealthMeter() ) {
				serviceInstance.setJmxHeartbeatMs( applicationResults.getCustomResult( ServiceAlertsEnum.JAVA_HEARTBEAT ) );
			}

			lastCollectedResults.put( serviceInstance.getServiceName_Port(), applicationResults );
		}
		jmxTotalSpit.stop();

		return true; // results were recorded.
	}

	private final static int MAX_HEALTH_REPORT_SIZE = 10000;

	private void executeHealthReportCollection (
													MBeanServerConnection mbeanConn,
													ServiceCollectionResults collectionResults )
			throws Exception {

		try {
			String collectedResponse = (String) mbeanConn.getAttribute(
				new ObjectName( collectionResults.getServiceInstance().getHealthReportMbean() ),
				collectionResults.getServiceInstance().getHealthReportAttribute() );

			logger.debug( "healthReport response: {}", collectedResponse );

			ObjectNode healthReport;
			if ( collectedResponse.length() < MAX_HEALTH_REPORT_SIZE ) {
				healthReport = (ObjectNode) jacksonMapper.readTree( collectedResponse );
			} else {
				healthReport = jacksonMapper.createObjectNode();
				healthReport.put( Report.healthy.json, false );
				healthReport.put( "error", "Collected Report exceeds max length: " + MAX_HEALTH_REPORT_SIZE );
			}
			collectionResults.getServiceInstance().setHealthReportCollected( healthReport );

		} catch (Exception e) {
			logger.debug( "Failed to get complete health report: {}", CSAP.getCsapFilteredStackTrace( e ) );
		}

	}

	private ObjectNode deltaLastCollected = jacksonMapper.createObjectNode();

	/**
	 *
	 * Each JVM can optionally specify additional attributes to collect
	 *
	 * @param instance
	 * @param serviceNamePort
	 * @param collectionResults
	 * @param mbeanConn
	 */
	private void executeCustomJmxCollection (	MBeanServerConnection mbeanConn,
												ServiceCollectionResults collectionResults ) {

		// System.err.println( "\n\n xxx logging issues\n\n " );
		logger.debug( "\n\n ============================ Getting JMX Custom Metrics for {} \n\n",
			collectionResults.getServiceInstance().getServiceName_Port() );

		// ObjectNode performanceConfiguration =
		// collectionResults.getInstanceConfig().getPerformanceConfiguration();

		final SimonManagerMXBean simonMgrMxBean = getSimonProxyBean( mbeanConn, collectionResults );

		// String metricId = null;
		// for ( Iterator<String> metricIdIterator = performanceConfiguration
		// .fieldNames(); metricIdIterator.hasNext(); ) {

		collectionResults.getServiceInstance().getServiceMeters().forEach( serviceMeter -> {

			Object attributeCollected = 0;
			Split jmxAttributeTimer = Split.start();

			boolean isCollectionSuccesful = false;
			try {

				logger.debug( "serviceMeter: {}", serviceMeter );

				if ( serviceMeter.getMeterType().isMbean() ) {

					attributeCollected = collectCustomMbean( serviceMeter,
						collectionResults, mbeanConn );

				} else if ( serviceMeter.getMeterType().isSimon() ) {

					attributeCollected = collectCustomSimon( serviceMeter, simonMgrMxBean, mbeanConn, collectionResults );

				} else {
					throw new Exception( "Unknown metric type" );
				}

				isCollectionSuccesful = true;
			} catch (Throwable e) {
				if ( !serviceMeter.isIgnoreErrors() ) {
					// SLA will monitor counts
					SimonManager.getCounter( "collector.jmx.custom.failures" ).increase();
					SimonManager.getCounter( "collector.jmx."
							+ collectionResults.getServiceInstance().getServiceName() + ".custom.failures" )
						.increase();
					SimonManager
						.getCounter( "collector.jmx." +
								collectionResults.getServiceInstance().getServiceName() + "." + serviceMeter.getCollectionId() +
								".custom.failures" )
						.increase();
					if ( isShowWarnings ) {
						String reason = e.getMessage();
						if ( reason != null && reason.length() > 60 ) {
							reason = e
								.getClass()
								.getName();
						}
						logger.warn( "\n **** Failed to collect {} for service {}\n Reason: {}, Cause: {}",
							serviceMeter.getCollectionId(), collectionResults.getServiceInstance().getServiceName_Port(), reason,
							e.getCause() );
					}

				}

				logger.debug( "{} Failed getting custom metrics for: {}, reason: {}",
					collectionResults.getServiceInstance().getServiceName_Port(),
					serviceMeter.getCollectionId(),
					CSAP.getCsapFilteredStackTrace( e ) );

			} finally {
				long resultLong = -1l;

				if ( attributeCollected instanceof Long ) {
					resultLong = (Long) attributeCollected;

				} else if ( attributeCollected instanceof Integer ) {
					resultLong = (Integer) attributeCollected;

				} else if ( attributeCollected instanceof Double ) {
					Double d = (Double) attributeCollected;
					d = d * serviceMeter.getMultiplyBy();
					if ( serviceMeter.getCollectionId().equals( "SystemCpuLoad" )
							|| serviceMeter.getCollectionId().equals( "ProcessCpuLoad" ) ) {
						logger.debug( "Adding multiple by for cpu values: {}", serviceMeter.getCollectionId() );
						d = d * 100;
					} else if ( d < 1 ) {
						logger.debug( "{}: Multiplying {} by 1000 to store. Add divideBy 1000",
							collectionResults.getServiceInstance().getServiceName_Port(),
							serviceMeter.getCollectionId() );
						d = d * 1000;
					}

					resultLong = Math.round( d );

				} else if ( attributeCollected instanceof Boolean ) {

					logger.debug( "Got a boolean result" );
					Boolean b = (Boolean) attributeCollected;

					if ( b ) {
						resultLong = 1;
					} else {
						resultLong = 0;
					}
				}

				logger.debug( "{} metric: {} , jmxResultObject: {} , resultLong: {}",
					collectionResults.getServiceInstance().getServiceName(),
					serviceMeter.getCollectionId(), attributeCollected, resultLong );

				if ( serviceMeter.getCollectionId().equalsIgnoreCase( ServiceAlertsEnum.JAVA_HEARTBEAT ) ) {
					// for hearbeats, store the time IF it has passed
					if ( resultLong == 1 ) {
						resultLong = jmxAttributeTimer
							.stop()
							.runningFor()
								/ 1000000;

						// some apps return very quickly due to not actually
						// implementing. return 1 if that happens
						if ( resultLong == 0 ) {
							resultLong = 1; // minimum of 1 to indicate success
						} // for checks.
					}
					collectionResults
						.getServiceInstance()
						.setJmxHeartbeatMs( resultLong );
				}

				if ( !(attributeCollected instanceof Double) ) {
					resultLong = resultLong * serviceMeter.getMultiplyBy();
				}

				resultLong = Math.round( resultLong / serviceMeter.getDivideBy( getCollectionIntervalSeconds() ) );

				// simon delta is handled in simon collection
				if ( serviceMeter.isDelta() && !serviceMeter.getMeterType().isSimon() ) {
					long last = resultLong;
					String key = collectionResults.getServiceInstance().getServiceName_Port() + serviceMeter.getCollectionId();
					if ( deltaLastCollected.has( key ) && isCollectionSuccesful ) {
						resultLong = resultLong - deltaLastCollected.get( key ).asLong();
						if ( resultLong < 0 ) {
							resultLong = 0;
						}
					} else {
						resultLong = 0;
					}
					// Only update the delta when collection is successful;
					// otherwise leave last collected in place
					if ( isCollectionSuccesful ) {
						deltaLastCollected.put( key, last );
					}
				}

				logger.debug( "\n\n{} ====> metricId: {}, resultLong: {} \n\n",
					collectionResults.getServiceInstance().getServiceName(), serviceMeter.getCollectionId(), resultLong );
				collectionResults.addCustomResultLong( serviceMeter.getCollectionId(), resultLong );

			}
		} );
	}

	private SimonManagerMXBean getSimonProxyBean (
													MBeanServerConnection mbeanConn,
													ServiceCollectionResults collectionResults ) {

		SimonManagerMXBean simonMgrMxBean = null;

		ServiceInstance service = collectionResults.getServiceInstance();
		if ( service.getSimonMbean().length() > 0 ) {

			try {
				simonMgrMxBean = JMX.newMXBeanProxy(
					mbeanConn, new ObjectName(
						collectionResults.getServiceInstance().getSimonMbean() ),
					SimonManagerMXBean.class );
			} catch (Exception e) {
				logger.warn( "Failed to get simon proxy", CSAP.getCsapFilteredStackTrace( e ) );
			}

		}

		logger.debug( "{} type: {} Simon mbean name: {} ",
			service.getServiceName(), service.getServerType(), service.getSimonMbean() );

		return simonMgrMxBean;
	}

	public Object collectCustomSimon (
										ServiceMeter serviceMeter,
										SimonManagerMXBean simonMgrMxBean,
										MBeanServerConnection mbeanConn,
										ServiceCollectionResults collectionResults )
			throws Exception {

		Object jmxResultObject = 0;

		// named collections are required so that same metric can be collected
		// for different attributes
		String simonNamedCollection = Application.getHOST_NAME() + "_agent_" + collectionIntervalSeconds + serviceMeter.getCollectionId();
		// String simonNamedCollection = "CsAgent_" + intervalSeconds +
		// metricId;
		if ( !isKeepRunning() ) {
			// junit hook to avoid impact remote envs. This ensure we get a
			// total count each time
			simonNamedCollection += System.currentTimeMillis();
		}
		logger.debug( "Simon Named Collection: {}", simonNamedCollection );

		// CounterSample simonCounter = simonMgrMxBean
		// .getCounterSampleAndReset(simonId);
		String simonType = simonMgrMxBean.getType( serviceMeter.getSimonId() );
		logger.debug( "Simon field name: {}, id: {}, type: {}", serviceMeter.getMeterType().getFieldName(), serviceMeter.getSimonId(),
			simonType );
		if ( simonType == null ) {
			// Simon counter is not initialized yet. Probably not a
			// problem
			logger.debug( " Could not access simon mbean for {}, skipping: {}",
				collectionResults.getServiceInstance().getServiceName_Port(), serviceMeter.getSimonId() );

		} else if ( simonType.equalsIgnoreCase( "COUNTER" ) ) {

			CounterSample simonCounter = simonMgrMxBean
				.getIncrementCounterSample( serviceMeter.getSimonId(), simonNamedCollection );
			if ( !serviceMeter.isDelta() ) {
				simonCounter = simonMgrMxBean.getCounterSample( serviceMeter.getSimonId() );
			}
			if ( simonCounter != null ) {
				jmxResultObject = simonCounter.getCounter();
			}

		} else if ( simonType.equalsIgnoreCase( "STOPWATCH" ) ) {

			StopwatchSample simonStop = simonMgrMxBean
				.getIncrementStopwatchSample( serviceMeter.getSimonId(), simonNamedCollection );

			if ( !serviceMeter.isDelta() ) {
				simonStop = simonMgrMxBean.getStopwatchSample( serviceMeter.getSimonId() );
			}
			if ( simonStop != null ) {
				jmxResultObject = simonStop.getCounter();
				if ( serviceMeter.getMeterType() == JavaCollectionType.simonMedianTime ) {
					jmxResultObject = Math.round( simonStop.getMean() );
				} else if ( serviceMeter.getMeterType() == JavaCollectionType.simonMaxTime ) {
					jmxResultObject = simonStop.getMax();
				}
			}
		}
		logger.debug( "{} - {} \t Simon field name: {}, intervalSeconds: {}, simonId: {}, jmxResultObject: {}",
			collectionResults.getServiceInstance().getServiceName_Port(), serviceMeter.getCollectionId(),
			serviceMeter.getMeterType().getFieldName(),
			collectionIntervalSeconds, serviceMeter.getSimonId(),
			jmxResultObject );

		return jmxResultObject;
	}

	private Object collectCustomMbean (
										ServiceMeter serviceMeter,
										ServiceCollectionResults jmxResults,
										MBeanServerConnection mbeanConn )
			throws Exception {

		Object jmxResultObject = 0;
		String mbeanNameCustom = serviceMeter.getMbeanName();

		if ( mbeanNameCustom.contains( "__CONTEXT__" ) ) {
			// Some servlet metrics require version string in name
			// logger.info("****** version: " +
			// jmxResults.getInstanceConfig().getMavenVersion());
			String version = jmxResults
				.getServiceInstance()
				.getMavenVersion();

			if ( jmxResults
				.getServiceInstance()
				.isScmDeployed() ) {
				version = jmxResults
					.getServiceInstance()
					.getScmVersion();
				version = version.split( " " )[0]; // first word of
				// scm
				// scmVersion=3.5.6-SNAPSHOT
				// Source build
				// by ...
			}
			// WARNING: version must be updated when testing.
			String serviceContext = "//localhost/" + jmxResults
				.getServiceInstance()
				.getContext();
			if ( !jmxResults.getServiceInstance().isSpringBoot() ) {
				serviceContext += "##" + version;
			}
			mbeanNameCustom = mbeanNameCustom.replaceAll( "__CONTEXT__", serviceContext );
			logger.debug( "Using custom name: {} ", mbeanNameCustom );
		}

		String mbeanAttributeName = serviceMeter.getMbeanAttribute();
		if ( mbeanAttributeName.equals( "SystemCpuLoad" ) ) {

			// Reuse already collected values (load is stateful)
			jmxResultObject = new Long( totalCpuArray.get( 0 ).asInt() );

		} else if ( mbeanAttributeName.equals( "ProcessCpuLoad" ) ) {
			// Reuse already collected values
			jmxResultObject = new Long( jmxResults.getCpuPercent() );

		} else if ( serviceMeter.getCollectionId().equalsIgnoreCase( ServiceAlertsEnum.JAVA_HEARTBEAT )
				&& !isPublishSummaryAndPerformHeartBeat() && !isTestHeartBeat() ) {
			// special case to avoid double heartbeats
			// reUse collected value from earlier interval.
			jmxResultObject = new Long( jmxResults.getServiceInstance().getJmxHeartbeatMs() );

		} else {

			logger.debug( "Collecting mbean: {}, attribute: {}", mbeanNameCustom, mbeanAttributeName );
			jmxResultObject = mbeanConn.getAttribute( new ObjectName( mbeanNameCustom ),
				mbeanAttributeName );
		}
		logger.debug( "Result for {} is: {}", mbeanAttributeName, jmxResultObject );
		return jmxResultObject;
	}

	/**
	 * Helper method to remove services when ever instances are removed from
	 * manager
	 */
	private void cleanUpServiceCache () {

		Iterator<String> keyIter = jmxResultsCache
			.keySet()
			.iterator();
		while (keyIter.hasNext()) {
			String serviceName_port = keyIter.next();

			if ( csapApplication.getServiceInstanceCurrentHost( serviceName_port ) == null ) {

				logger.warn( "Removing service from monitor list: " + serviceName_port
						+ " , assumed due to definition update." );
				keyIter.remove();
			}
		}

		Iterator<String> keyCustomIter = customResultsCache
			.keySet()
			.iterator();
		while (keyCustomIter.hasNext()) {
			String serviceName_port = keyCustomIter.next();

			if ( csapApplication.getServiceInstanceCurrentHost( serviceName_port ) == null ) {

				logger.warn( "Removing: {} from monitor list - assumed due to definition update.", serviceName_port );
				keyCustomIter.remove();
			}
		}

	}

	public final static String JMX_METRICS_EVENT = METRICS_EVENT + "/jmx/standard/";
	public final static String APPLICATION_METRICS_EVENT = METRICS_EVENT + "/jmx/custom/";

	// need to refactor to app/custom but this involves db
	public String uploadMetrics ( int iterationsBetweenUploads ) {

		String result = "PASSED";

		try {

			// this is one upload
			result = uploadJmxCollection( iterationsBetweenUploads );

			// this is one per service with custom metrics
			result = uploadApplicationCollection( iterationsBetweenUploads );

		} catch (Exception e) {
			logger.error( "Failed to connect", e );
			result = "Failed, Exception:"
					+ CSAP.getCsapFilteredStackTrace( e );
		}

		return result;

	}

	private String uploadApplicationCollection ( int iterationsBetweenAuditUploads )
			throws JsonProcessingException {
		// Now do custom metrics - each service uploaded independently

		_lastCustomServiceSummary = jacksonMapper.createObjectNode();

		for ( String serviceName_port : customResultsCache.keySet() ) {

			String[] idsForCustomServices = { serviceName_port };

			ObjectNode customSamplesToUpload = getCSVdata( true,
				idsForCustomServices, iterationsBetweenAuditUploads, 0, "jmxCustom" );

			String customBaseCategory = APPLICATION_METRICS_EVENT + serviceName_port + "/" + collectionIntervalSeconds + "/";

			// new Event publisher - it checks if publish is enabled. If
			// services have been updated, then attributes are uploaded again
			if ( customCacheJson.has( serviceName_port ) && !customCacheJson
				.get( serviceName_port )
				.has( "published" ) ) {
				ObjectNode itemInCache = (ObjectNode) customCacheJson.get( serviceName_port );
				itemInCache.put( "published", "true" );
				// full upload. We could make call to event service to see if
				// they match...for now we do on restarts
				csapApplication
					.getEventClient()
					.publishEvent( customBaseCategory + "attributes", "Modified", null,
						itemInCache.get( "attributes" ) );
			}
			ServiceInstance serviceInstance = csapApplication
				.getServiceInstanceCurrentHost( serviceName_port );

			if ( serviceInstance == null ) {
				logger.warn( "Service not found in cluster, assuming deleted and removing from cutom collection keys: "
						+ serviceName_port );
				// jmxCustomResultsCache.remove(serviceName_port) ;
				continue;
			}
			ObjectNode correlationAttributes = jacksonMapper.createObjectNode();
			correlationAttributes.put( "id", "jmx" + serviceInstance.getServiceName() + "_" + collectionIntervalSeconds );
			correlationAttributes.put( "hostName", Application.getHOST_NAME() );

			// Send normalized data
			customSamplesToUpload.set( "attributes", correlationAttributes );

			// new Event publisher - it checks if publish is enabled
			csapApplication
				.getEventClient()
				.publishEvent( customBaseCategory + "data",
					"Upload", null, customSamplesToUpload );

		}

		logger.debug( "Adding summary: ", _lastCustomServiceSummary );

		addApplicationSummary( _lastCustomServiceSummary, true );

		publishSummaryReport( "jmxCustom", true );
		return "PASSED";
	}

	private String uploadJmxCollection ( int iterationsBetweenAuditUploads )
			throws JsonProcessingException {

		// MultiValueMap<String, String> map = new
		// LinkedMultiValueMap<String, String>();
		ObjectNode jmxSamplesToUploadNode = getCSVdata( true,
			jmxResultsCache
				.keySet()
				.toArray( new String[ 0 ] ),
			iterationsBetweenAuditUploads, 0 );

		// new Event publisher - it checks if publish is enabled. If services
		// have been updated, then attributes are uploaded again
		if ( isCacheNeedsPublishing ) {
			// full upload. We could make call to event service to see if they
			// match...for now we do on restarts
			csapApplication
				.getEventClient()
				.publishEvent( JMX_METRICS_EVENT + collectionIntervalSeconds + "/attributes", "Modified", null,
					attributeCacheJson );
			isCacheNeedsPublishing = false;
		}

		if ( jmxCorrelationAttributes == null ) {
			jmxCorrelationAttributes = jacksonMapper.createObjectNode();
			jmxCorrelationAttributes.put( "id", "jmx_" + collectionIntervalSeconds );
			jmxCorrelationAttributes.put( "hostName", Application.getHOST_NAME() );
		}
		// Send normalized data
		jmxSamplesToUploadNode.set( "attributes", jmxCorrelationAttributes );
		csapApplication
			.getEventClient()
			.publishEvent( JMX_METRICS_EVENT + collectionIntervalSeconds + "/data",
				"Upload", null, jmxSamplesToUploadNode );

		publishSummaryReport( "jmx" );

		return "PASSED";
	}

	private ObjectNode jmxCorrelationAttributes = null;

	final static int DEFAULT_SERVICES = 4;

	public ObjectNode getJavaCollection ( int requestedSampleSize, int skipFirstItems, String... services ) {

		if ( services[0].toLowerCase().equals( ALL_SERVICES ) ) {
			services = jmxResultsCache.keySet().toArray( new String[ 0 ] );
		}
		return getCSVdata( false, services, requestedSampleSize, skipFirstItems );
	}

	public ObjectNode getApplicationCollection ( int requestedSampleSize, int skipFirstItems, String... services ) {

		return getCSVdata( false,
			services,
			requestedSampleSize, 0, "isCustom" );
	}

	private ObjectNode _lastCustomServiceSummary = jacksonMapper.createObjectNode();

	public ObjectNode getCSVdata (	boolean isUpdateSummary, String[] serviceNameArray,
									int requestedSampleSize, int skipFirstItems, String... customArgs ) {

		logger
			.debug( " serviceNameArray: {} , customArgs: {}, intervalSeconds: {}, jmxCacheResults: {}, timeStamps: {} ",
				Arrays.toString( serviceNameArray ), Arrays.toString( customArgs ), collectionIntervalSeconds,
				jmxResultsCache
					.size(),
				timeStampArray_m.size() );

		boolean isCustom = customArgs.length > 0;

		ObjectNode rootNode = jacksonMapper.createObjectNode();

		List<String> servicesFilter = new ArrayList<String>();
		if ( serviceNameArray == null ) {
			serviceNameArray = new String[ DEFAULT_SERVICES ];
			int i = 0;
			for ( String serviceName : jmxResultsCache.keySet() ) {
				servicesFilter.add( serviceName );
				if ( ++i >= DEFAULT_SERVICES ) {
					break;
				}
			}
		} else {
			servicesFilter = Arrays.asList( serviceNameArray );
			if ( isCustom ) {
				if ( !customResultsCache.keySet().contains( servicesFilter.get( 0 ) ) ) {
					rootNode.put( "error", "Did find data for service: " + servicesFilter.get( 0 ) );
					return rootNode;
				}

			} else {
				if ( !jmxResultsCache.keySet().contains( servicesFilter.get( 0 ) ) ) {
					rootNode.put( "error", "Did find data for service: " + servicesFilter.get( 0 ) );
					return rootNode;
				}
			}
		}

		logger.debug( "requestedSampleSize: {}, skipFirstItems: {}", requestedSampleSize, skipFirstItems );

		if ( !isCustom ) {
			rootNode.set( "attributes", generateJmxAttributeJson( servicesFilter, requestedSampleSize,
				skipFirstItems ) );
		} else {
			rootNode.set( "attributes", generateCustomAttributeJson( servicesFilter, requestedSampleSize,
				skipFirstItems ) );
		}

		ObjectNode dataNode = rootNode.putObject( "data" );
		ArrayNode tsArray = dataNode.putArray( "timeStamp" );
		ArrayNode totalArray = dataNode.putArray( "totalCpu" );

		int numSamples = 0;
		if ( requestedSampleSize == -1 ) {
			tsArray.addAll( timeStampArray_m );
			totalArray.addAll( totalCpuArray );

		} else {

			for ( int i = 0; i < requestedSampleSize
					&& i < timeStampArray_m.size(); i++ ) {
				tsArray.add( timeStampArray_m.get( i ) );
				totalArray.add( totalCpuArray.get( i ) );
				numSamples++;
			}

		}

		logger.debug( "Number of timestamps: {} , tsArray: {}", timeStampArray_m.size(), tsArray.size() );

		if ( isCustom ) {
			buildApplicationReports( isUpdateSummary, requestedSampleSize, servicesFilter, dataNode, numSamples );

		} else {

			buildJmxReports( isUpdateSummary, requestedSampleSize, servicesFilter, dataNode, numSamples );
		}

		return rootNode;
	}

	public void buildJmxReports (	boolean isUpdateSummary, int requestedSampleSize, List<String> servicesFilter,
									ObjectNode dataNode, int numSamples ) {
		ObjectNode currSummary = jacksonMapper.createObjectNode();
		// standard JMX collections
		for ( String serviceName : servicesFilter ) {
			ObjectNode serviceCacheNode = jmxResultsCache
				.get( serviceName );
			if ( serviceCacheNode != null ) {

				String sumName = serviceName;
				int index = sumName.indexOf( "_" );
				if ( index != -1 ) {
					sumName = sumName.substring( 0, index );
				}
				ObjectNode serviceSummaryJson = currSummary.putObject( sumName );
				serviceSummaryJson.put( "numberOfSamples", numSamples );
				for ( JmxCommonEnum jmxMetric : JmxCommonEnum.values() ) {

					String metricFullName = jmxMetric.value + "_"
							+ serviceName;
					ArrayNode cacheArray = (ArrayNode) serviceCacheNode
						.get( metricFullName );

					if ( cacheArray == null ) {
						continue;
					}

					ArrayNode metricArray = dataNode
						.putArray( metricFullName );
					if ( requestedSampleSize == -1 ) {
						metricArray.addAll( cacheArray );
					} else {
						long metricTotal = 0;
						for ( int i = 0; i < requestedSampleSize
								&& i < cacheArray.size(); i++ ) {
							metricArray.add( cacheArray.get( i ) );
							metricTotal += cacheArray
								.get( i )
								.asLong();
						}
						serviceSummaryJson.put( jmxMetric.value, metricTotal );
					}
				}
			}
		}

		addSummary( currSummary, isUpdateSummary );
	}

	public void buildApplicationReports (	boolean isUpdateSummary, int requestedSampleSize, List<String> servicesFilter,
											ObjectNode dataNode,
											int numSamples ) {
		// custom Collections
		String serviceName = servicesFilter.get( 0 );
		ObjectNode serviceCacheNode = customResultsCache
			.get( serviceName );

		String sumName = serviceName;
		int index = sumName.indexOf( "_" );
		if ( index != -1 ) {
			sumName = sumName.substring( 0, index );
		}

		//
		ObjectNode serviceSummaryJson = jacksonMapper.createObjectNode();
		if ( isUpdateSummary ) {
			// Done on a single thread
			serviceSummaryJson = _lastCustomServiceSummary.putObject( sumName );
		}

		serviceSummaryJson.put( "numberOfSamples", numSamples );

		if ( serviceCacheNode == null ) {

			logger.debug( "Warning: serviceCacheNode is null" );

		} else {

			// logger.info("**** serviceName " + serviceName);
			for ( Iterator<String> metricIdIterator = serviceCacheNode
				.fieldNames(); metricIdIterator.hasNext(); ) {

				String metricId = metricIdIterator
					.next()
					.trim();

				logger.debug( "metricId: {}", metricId );

				ArrayNode metricCacheArray = (ArrayNode) serviceCacheNode
					.get( metricId );

				ArrayNode metricResultsArray = dataNode.putArray( metricId );

				//
				if ( requestedSampleSize == -1 ) {
					metricResultsArray.addAll( metricCacheArray );
				} else {

					// summary reports as ints? maybe switch to doubles
					//
					// logger.info( "metricCacheArray size: " +
					// metricCacheArray.size() ) ;
					long metricTotal = 0;
					for ( int i = 0; i < requestedSampleSize
							&& i < metricCacheArray.size(); i++ ) {

						metricResultsArray.add( metricCacheArray.get( i ) );
						long current = metricCacheArray
							.get( i )
							.asLong();
						metricTotal += current;
					}
					serviceSummaryJson.put( metricId, metricTotal );
					logger.debug( "{}  total: {} ", metricId, metricTotal );
					// logger.info("Type: " +
					// serviceSummaryJson.get(metricId).getNodeType() ) ;
				}

			}
		}
	}

	// only cache for all services.
	private ObjectNode attributeCacheJson = null;
	private int serviceCacheHash = 0;
	private boolean isCacheNeedsPublishing = true;

	private ObjectNode generateJmxAttributeJson (	List<String> servicesFilter,
													int requestedSampleSize, int skipFirstItems ) {

		int requestHashValue = servicesFilter
			.toString()
			.hashCode();

		boolean isFullServicesRequest = servicesFilter.size() == getJmxResultsCache()
			.keySet()
			.size();
		if ( attributeCacheJson != null && isFullServicesRequest ) {
			if ( requestHashValue == serviceCacheHash ) {

				logger.debug( "Using Cached attributes" );
				return attributeCacheJson;
			}
		}

		ObjectNode attributeJson = jacksonMapper.createObjectNode();

		attributeJson.put( "id", "jmx_" + collectionIntervalSeconds );
		attributeJson.put( "metricName", "JMX Resources" );
		attributeJson.put( "description", "Contains service metrics" );
		attributeJson.put( "timezone", TIME_ZONE_OFFSET );
		attributeJson.put( "hostName", Application.getHOST_NAME() );
		attributeJson.put( "sampleInterval", collectionIntervalSeconds );
		attributeJson.put( "samplesRequested", requestedSampleSize );
		attributeJson.put( "samplesOffset", skipFirstItems );
		attributeJson.put( "currentTimeMillis", System.currentTimeMillis() );
		attributeJson.put( "cpuCount", osStats.getAvailableProcessors() );

		ObjectNode remoteCollect = attributeJson
			.putObject( "remoteCollect" );
		for ( String serviceName : serviceRemoteHost.keySet() ) {
			remoteCollect.put( serviceName, serviceRemoteHost.get( serviceName ) );
		}

		ArrayNode servicesAvailArray = attributeJson
			.putArray( "servicesAvailable" );
		for ( String serviceName : jmxResultsCache.keySet() ) {
			servicesAvailArray.add( serviceName );
		}
		ArrayNode servicesReqArray = attributeJson
			.putArray( "servicesRequested" );

		for ( String serviceName : servicesFilter ) {
			servicesReqArray.add( serviceName );
		}

		ObjectNode graphsObject = attributeJson.putObject( "graphs" );
		attributeJson.set( "titles", JmxCommonEnum.graphLabels() );

		for ( JmxCommonEnum metric : JmxCommonEnum.values() ) {

			String metricNameInAttributes = metric.value;

			ObjectNode resourceGraph = graphsObject.putObject( metricNameInAttributes );

			for ( String serviceName_port : servicesFilter ) {
				String metricFullName = metric.value + "_" + serviceName_port;

				// filter tomcat params from non tomcat services.
				// ServiceInstance testInstance =
				// getCsapApplication().getServiceInstanceAnyPackage(
				// serviceName );
				ServiceInstance testInstance = getCsapApplication().getServiceInstanceCurrentHost( serviceName_port );
				if ( metric.isTomcatOnly() ) {
					if ( testInstance.isTomcatJarsPresent() ) {
						resourceGraph.put( metricFullName, serviceName_port );
					}
				} else {
					resourceGraph.put( metricFullName, serviceName_port );
				}
			}

			if ( !resourceGraph
				.fieldNames()
				.hasNext() ) {
				// some jmx attributes are for tomcat only; filter them if empty
				graphsObject.remove( metricNameInAttributes );
			}

			// Added at the bottom so colors match across graphs
			if ( metric == JmxCommonEnum.cpuPercent ) {
				resourceGraph.put( "totalCpu", "VM Total" );
			}
		}
		if ( isFullServicesRequest ) {
			attributeCacheJson = attributeJson;
			serviceCacheHash = requestHashValue;
			isCacheNeedsPublishing = true;

			logger.debug( "Updated attributes cache \n {}", attributeCacheJson );

		}
		return attributeJson;
	}

	// only cache for all services. sevice -> service.hash, service.publish,
	// service.attributes
	private ObjectNode customCacheJson = jacksonMapper.createObjectNode();

	private ObjectNode generateCustomAttributeJson (	List<String> servicesFilter,
														int requestedSampleSize, int skipFirstItems ) {

		String svcName_port = "";

		svcName_port = servicesFilter.get( 0 );
		ServiceInstance serviceInstance = csapApplication
			.getServiceInstanceCurrentHost( svcName_port );
		ObjectNode serviceCacheNode = customResultsCache
			.get( svcName_port );

		if ( serviceCacheNode == null ) {
			return null;
		}

		StringBuilder allMetrics = new StringBuilder( "" );
		for ( Iterator<String> metricIdIterator = serviceCacheNode
			.fieldNames(); metricIdIterator.hasNext(); ) {
			allMetrics.append( metricIdIterator.next() );
		}
		int requestHashValue = allMetrics
			.toString()
			.hashCode();

		if ( customCacheJson.has( svcName_port ) ) {
			ObjectNode cachedItem = (ObjectNode) customCacheJson.get( svcName_port );

			if ( requestHashValue == cachedItem
				.get( "hash" )
				.asInt() ) {

				logger.debug( "Using Cached attributes" );
				return (ObjectNode) cachedItem.get( "attributes" );
			}
		}

		ObjectNode updatedAttributesJson = jacksonMapper.createObjectNode();

		updatedAttributesJson.put( "id", "jmx" + serviceInstance.getServiceName()
				+ "_" + collectionIntervalSeconds );
		updatedAttributesJson.put( "metricName", "JMX Custom Resources" );
		updatedAttributesJson.put( "description", "Contains service metrics" );
		updatedAttributesJson.put( "timezone", TIME_ZONE_OFFSET );
		updatedAttributesJson.put( "hostName", Application.getHOST_NAME() );
		updatedAttributesJson.put( "sampleInterval", collectionIntervalSeconds );
		updatedAttributesJson.put( "samplesRequested", requestedSampleSize );
		updatedAttributesJson.put( "samplesOffset", skipFirstItems );
		updatedAttributesJson.put( "currentTimeMillis", System.currentTimeMillis() );
		updatedAttributesJson.put( "cpuCount", osStats.getAvailableProcessors() );

		ObjectNode remoteCollect = updatedAttributesJson
			.putObject( "remoteCollect" );

		if ( serviceRemoteHost.containsKey( svcName_port ) ) {
			remoteCollect.put( "default", serviceRemoteHost.get( svcName_port ) );
		}

		ArrayNode servicesAvailArray = updatedAttributesJson
			.putArray( "servicesAvailable" );
		// for (String serviceName : jmxResultsCache.keySet()) {
		for ( String serviceName : customResultsCache.keySet() ) {
			servicesAvailArray.add( serviceName );
		}
		ArrayNode servicesReqArray = updatedAttributesJson
			.putArray( "servicesRequested" );

		for ( String serviceName : servicesFilter ) {
			servicesReqArray.add( serviceName );
		}

		ObjectNode graphsObject = updatedAttributesJson.putObject( "graphs" );
		updatedAttributesJson.set( "titles", serviceInstance.getServiceMeterTitles() );

		serviceInstance.getServiceMeters().forEach( meter -> {

			String uiLegend = meter.toSummary();
			uiLegend.replace( '"', '/' );

			ObjectNode resourceGraph = graphsObject.putObject( meter.getCollectionId() );
			resourceGraph.put( meter.getCollectionId(), uiLegend );
		} );

		ObjectNode updateCachItem = jacksonMapper.createObjectNode();
		updateCachItem.set( "attributes", updatedAttributesJson );
		updateCachItem.put( "hash", requestHashValue );
		customCacheJson.set( svcName_port, updateCachItem );

		return updatedAttributesJson;
	}

	private BasicThreadFactory jmxConnectionMonitorFactory = new BasicThreadFactory.Builder()
		.namingPattern( "CsapJmxMonitor-%d" )
		.daemon( true )
		.priority( Thread.NORM_PRIORITY )
		.build();

	ScheduledExecutorService jmxConnectionMonitorExecutor = Executors.newScheduledThreadPool( 1,
		jmxConnectionMonitorFactory );

	/**
	 * Hack needed in order to get JMX timeouts
	 * http://weblogs.java.net/blog/emcmanus
	 * /archive/2007/05/making_a_jmx_co.html
	 *
	 * Total timeout = 2 x params. First to connect, then for calls
	 *
	 * @param url
	 * @param requestTimeout
	 * @param requestUnit
	 * @return
	 * @throws IOException
	 */
	public JMXConnector connectWithTimeout (	final JMXServiceURL url,
												long requestTimeout, TimeUnit requestUnit )
			throws IOException {

		logger.debug( "xxx Attempting connection to: {} with timeout : {} ms", url, requestTimeout );

		Future<Object> connectionResults = jmxConnectionMonitorExecutor.submit( new Callable<Object>() {
			public Object call () {
				try {
					Map<String, Object> environment = new HashMap<String, Object>();
					environment.put( EnvHelp.CLIENT_CONNECTION_CHECK_PERIOD, new Long( 0 ) );

					JMXConnector connector = JMXConnectorFactory.connect( url, environment );
					return connector;
				} catch (Throwable t) {
					return t;
				}
			}
		} );

		Object result;
		try {
			result = connectionResults.get( requestTimeout, requestUnit );
		} catch (Exception e) {
			throw initCause( new InterruptedIOException( e.getMessage() ), e );
		} finally {
			// jmxConnectionMonitorExecutor.shutdown();
			connectionResults.cancel( true );
		}
		if ( result == null ) {
			throw new SocketTimeoutException( "xxx Connect timed out: " + url );
		}

		if ( result instanceof JMXConnector ) {
			final JMXConnector connector = (JMXConnector) result;
			@SuppressWarnings ( "unused" )
			Future<Object> closeResults = jmxConnectionMonitorExecutor.schedule( (new Callable<Object>() {
				public Object call () {
					Object result = "";
					try {

						logger.debug( "xxx Closing connection: " + url );

						connector.close();
					} catch (Throwable t) {
						logger.error( "xxx Failed to close connection", t );
					}
					return result;
				}
			}), requestTimeout, requestUnit );

			return (JMXConnector) result;
		}

		try {
			throw (Throwable) result;
		} catch (IOException e) {
			throw e;
		} catch (RuntimeException e) {
			throw e;
		} catch (Error e) {
			throw e;
		} catch (Throwable e) {
			// In principle this can't happen but we wrap it anyway
			throw new IOException( e.toString(), e );
		}
	}

	private static <T extends Throwable> T initCause (	T wrapper,
														Throwable wrapped ) {
		wrapper.initCause( wrapped );
		return wrapper;
	}

	private boolean testHeartBeat = false;

	public boolean isTestHeartBeat () {
		return testHeartBeat;
	}

	public void setTestHeartBeat ( boolean testHeartBeat ) {
		this.testHeartBeat = testHeartBeat;
	}

	public long getMaxCollectionAllowedInMs () {
		return maxCollectionAllowedInMs;
	}

	public void setMaxCollectionAllowedInMs ( long maxCollectionAllowedInMs ) {
		this.maxCollectionAllowedInMs = maxCollectionAllowedInMs;
	}

}
