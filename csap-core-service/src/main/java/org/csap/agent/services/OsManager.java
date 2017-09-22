package org.csap.agent.services;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.csap.agent.CSAP;
import org.csap.agent.linux.InfrastructureRunner;
import org.csap.agent.linux.LogRollerRunnable;
import org.csap.agent.linux.OsCommandRunner;
import org.csap.agent.linux.OutputFileMgr;
import org.csap.agent.linux.ServiceJobRunner;
import org.csap.agent.linux.ServiceResourceRunnable;
import org.csap.agent.linux.TopRunnable;
import org.csap.agent.linux.TransferManager;
import org.csap.agent.linux.ZipUtility;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.Application;
import org.csap.agent.model.CsAgentTimer;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.stats.MetricCategory;
import org.csap.agent.stats.OsProcessCollector;
import org.csap.agent.stats.OsProcessEnum;
import org.csap.agent.stats.OsSharedResourcesCollector;
import org.csap.agent.stats.ServiceCollector;
import org.csap.helpers.CsapSimpleCache;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.javasimon.Stopwatch;
import org.javasimon.aop.Monitored;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class OsManager {

	public static final String SWAP = "swap";
	public static final String RAM = "ram";
	public static final String BUFFER = "buffer";

	final Logger logger = LoggerFactory.getLogger( this.getClass() );

	private ObjectMapper jacksonMapper = new ObjectMapper();
	LogRollerRunnable logRoller = null;
	ServiceJobRunner jobRunner = null;
	InfrastructureRunner infraRunner = null;

	public InfrastructureRunner getInfraRunner () {
		return infraRunner;
	}

	public ServiceJobRunner getJobRunner () {
		return jobRunner;
	}

	public LogRollerRunnable getLogRoller () {
		return logRoller;
	}
	
	public OsManager( ) {

		// logger.info("============== OSMANAGER");
	}
	
	public void setTestApp( Application app) {
		csapApp = app ;
	}

	public void startSharedHostResourceCollectors () {

		StringBuilder startInfo = new StringBuilder( "\n ============== Starting resource collection =================" );

		int topSeconds = getTopIntervalSeconds();
		topStatsRunnable = new TopRunnable( topSeconds );
		startInfo.append( "\n\t - cpu(top) collection every: " + topSeconds + " seconds" );

		logRoller = new LogRollerRunnable( csapApp );
		startInfo.append( "\n\t - logrotate execution every " + csapApp.lifeCycleSettings().getLogRotationMinutes() + " minutes" );

		jobRunner = new ServiceJobRunner( csapApp );
		startInfo.append( "\n\t - job runner(bash) reviewed every 60 minutes" );

		infraRunner = new InfrastructureRunner( csapApp );

		//
		int duInterval = csapApp.lifeCycleSettings()
			.getDuIntervalMins();

		if ( duInterval > 0 ) {

			startInfo.append( "\n\t - Disk Usage (du) collection: "
					+ duInterval + " minutes" );
			intenseOsCommandExecutor.scheduleWithFixedDelay(
				() -> collectDiskUsage(), 1, duInterval, TimeUnit.MINUTES );

		} else {
			startInfo.append( "\n\t - Disk Usage (linux du) capture is disabled" );
		}

		if ( csapApp.lifeCycleSettings().isLsofEnabled() ) {

			int lsofInterval = csapApp.lifeCycleSettings()
				.getLsofIntervalMins();

			linuxResourceRunnable = new ServiceResourceRunnable( csapApp, osCommandRunner );
			intenseOsCommandExecutor.scheduleWithFixedDelay(
				linuxResourceRunnable, 1, lsofInterval, TimeUnit.MINUTES );

			startInfo.append( "\n\t - Service sockets(ss), io(pidstat), and files(/proc) collection: "
					+ lsofInterval + " minutes" );

			intenseOsCommandExecutor.scheduleWithFixedDelay(
				() -> collectHostSocketsThreadsFiles(), 1, lsofInterval, TimeUnit.MINUTES );

			startInfo.append( "\n\t - Host Summary:  sockets(ss), threads(ps), and files(/proc and lsof) collection: "
					+ lsofInterval + " minutes" );

		} else {
			startInfo.append( "\n\t -  Sockets, IO, And file capture is disabled" );
		}

		logger.info( startInfo.toString() );
	}

	public void shutDown () {

		if ( topStatsRunnable != null ) {
			topStatsRunnable.shutdown();
		}

		if ( linuxResourceRunnable != null ) {
			linuxResourceRunnable.shutDown();
		}
		// if (hostStatusManager != null)
		// hostStatusManager.stop();
		if ( logRoller != null ) {
			logRoller.shutdown();
		}

		intenseOsCommandExecutor.shutdownNow();
	}

	private Integer getTopIntervalSeconds () {

		// Default is to poll every 1/2 of the service collection interval
		if ( csapApp.lifeCycleSettings()
			.getMetricToSecondsMap()
			.size() == 0
				|| !csapApp.lifeCycleSettings()
					.getMetricToSecondsMap()
					.containsKey( "service" )
				|| csapApp.lifeCycleSettings()
					.getMetricToSecondsMap()
					.get( "service" )
					.size() == 0 ) {
			return 30;
		}

		return csapApp.lifeCycleSettings()
			.getMetricToSecondsMap()
			.get( "service" )
			.get( 0 )
				/ 2;
	}

	BasicThreadFactory openFilesThreadFactory = new BasicThreadFactory.Builder()
		.namingPattern( "CsapOsManager-%d" )
		.daemon( true )
		.priority( Thread.NORM_PRIORITY )
		.build();

	// both du and lsof get invoked here. Note we do this to avoid overwhelming
	// the OS with concurrent commands
	private ScheduledExecutorService intenseOsCommandExecutor = Executors
		.newScheduledThreadPool( 3, openFilesThreadFactory );

	// Spring Injected
	@Autowired
	private Application csapApp;
	OsCommandRunner osCommandRunner = new OsCommandRunner( 120, 3, "OsMgr" );

	public void resetAllCaches () {

		logger.debug( "All caches reset" );

		if ( diskStatisticsCache != null )
			diskStatisticsCache.setLastRefreshMs( 0 );
		if ( processStatisticsCache != null )
			processStatisticsCache.setLastRefreshMs( 0 );
		if ( memoryStatisticsCache != null )
			memoryStatisticsCache.setLastRefreshMs( 0 );

		// du is long running - so it is scheduled in background
		try {
			scheduleDiskUsageCollection();
		} catch (Exception e) {
			logger.error( "Failed to schedule du", e );
		}

	}

	TopRunnable topStatsRunnable = null; // use a lazy load so that thread
	// priority

	public int getVmTotal () {

		Float f = new Float( -1 );
		try {
			String[] pids = { TopRunnable.VM_TOTAL };
			f = Float.parseFloat( topStatsRunnable.getCpuForPid( Arrays.asList( pids ) ) );
		} catch (Exception e) {
			logger.error( "Failed to parse", e );
		}
		return f.intValue();
	}

	public int numberOfProcesses () {
		try {
			return lastProcessStatsCollected().split( LINE_SEPARATOR ).length;
		} catch (Exception e) {

		}

		return -1;
	}

	public ArrayNode processStatus () {
		ArrayNode processArray = jacksonMapper.createArrayNode();
		String[] pslines = lastProcessStatsCollected().split( LINE_SEPARATOR );
		for ( int i = 0; i < pslines.length; i++ ) {
			String curline = pslines[i].trim();
			String[] cols = curline.split( " " );
			if ( cols.length < 7 ) {
				logger.debug( "Skipping line: {}", curline );
				continue;
			}
			String cpu = cols[0];
			String rssMemory = cols[1];
			String vMemory = cols[1];
			String threads = cols[3];
			String user = cols[4];
			String pid = cols[5];
			try {
				Integer.parseInt( pid );
			} catch (NumberFormatException e) {

				logger.info( "Skipping line: {}", curline );
				continue;
			}
			String priority = cols[6];

			ObjectNode processNode = processArray.addObject();

			processNode.put( "pid", pid );
			processNode.put( "command",
				curline.substring( curline.indexOf( pid ) ) );
			processNode.put( "user", user );
			processNode.put( "cpuUtil", cpu );
			processNode.put( "currentProcessPriority", priority );
			processNode.put( "topCpu",
				topStatsRunnable.getCpuForPid( Arrays.asList( pid ) ) );
			processNode.put( "threadCount", threads );
			processNode.put( "rssMemory", rssMemory );
			processNode.put( "virtualMemory", vMemory );
			processNode.put( "fileCount", "" );
			processNode.put( "socketCount", "" );
			processNode.put( "runHeap", "" );
		}

		return processArray;
	}

	/**
	 * Synchronized as multiple metrics collections will try to hit this
	 * frequently. This avoids costly ps calls
	 */
	public ObjectNode getServiceMetrics ( boolean isCsapDefinitionProcessesOnly ) {

		ObjectNode servicesJson = jacksonMapper.createObjectNode();

		logger.debug( "Cache Refresh " );

		checkForProcessStatusUpdate();

		logger.debug( "Cache Refresh: psResult: ", lastProcessStatsCollected() );

		ObjectNode psNode = servicesJson.putObject( "ps" );

		if ( isCsapDefinitionProcessesOnly ) {

			// Showing standard process list - only processes in definition file
			ArrayList<ServiceInstance> svcList = csapApp
				.getServicesOnHost();
			for ( ServiceInstance instance : svcList ) {

				// logger.info("****************************** Updated top
				// added")
				// ;
				instance.setTopCpu( topStatsRunnable.getCpuForPid( instance
					.getPid() ) );

				String id = instance.getPerformanceId();
				JsonNode serviceNode = psNode.set( id,
					instance.getRuntime() );
			}

		} else {

			// Show every process found on OS, including OS processes.
			String[] pslines = lastProcessStatsCollected().split( LINE_SEPARATOR );
			for ( int i = 0; i < pslines.length; i++ ) {
				String curline = pslines[i].trim();
				String[] cols = curline.split( " " );
				if ( cols.length < 7 ) {
					logger.debug( "Skipping line: {}", curline );
					continue;
				}
				String cpu = cols[0];
				String rssMemory = cols[1];
				String vMemory = cols[1];
				String threads = cols[3];
				String pid = cols[5];
				try {
					Integer.parseInt( pid );
				} catch (NumberFormatException e) {

					logger.info( "Skipping line: {}", curline );
					continue;
				}
				String priority = cols[6];

				ObjectNode processNode = psNode.putObject( pid );

				processNode.put( "serviceName", "Pid: " + pid );
				processNode.put( "servletThreadCount", "" );
				processNode.put( "diskUtil",
					curline.substring( curline.indexOf( pid ) ) );
				processNode.put( "cpuUtil", cpu );
				processNode.put( "currentProcessPriority", priority );
				processNode.put( "topCpu",
					topStatsRunnable.getCpuForPid( Arrays.asList( pid ) ) );
				processNode.put( "threadCount", threads );
				processNode.put( "pid", pid );
				processNode.put( "rssMemory", rssMemory );
				processNode.put( "virtualMemory", vMemory );
				processNode.put( "fileCount", "" );
				processNode.put( "socketCount", "" );
				processNode.put( "runHeap", "" );
			}
		}

		servicesJson.set( "mp", getMpStateFromCache() );

		return servicesJson;
	}

	private CsapSimpleCache cpuStatisticsCache = null;
	private ReentrantLock mpStatusLock = new ReentrantLock();

	private ObjectNode getMpStateFromCache () {

		if ( cpuStatisticsCache == null ) {
			cpuStatisticsCache = CsapSimpleCache.builder(
				9,
				TimeUnit.SECONDS,
				OsManager.class,
				"CPU Statistics" );
		}

		if ( !cpuStatisticsCache.isExpired() ) {

			logger.debug( "\n\n***** ReUsing  cpuStatisticsCache   *******\n\n" );

		} else if ( mpStatusLock.tryLock() ) {

			logger.debug( "\n\n***** REFRESHING   cpuStatisticsCache   *******\n\n" );
			Split generalSplit = SimonManager.getStopwatch( "java.OsManager.getVmMpResults.refresh" ).start();
			try {

				cpuStatisticsCache.reset( updateMpCache() );

			} catch (Exception e) {
				logger.info( "Failed refreshing runtime", e );

			} finally {
				mpStatusLock.unlock();
			}
			generalSplit.stop();
		}

		return (ObjectNode) cpuStatisticsCache.getCachedObject();

	}

	private ObjectNode updateMpCache () {
		ObjectNode mpNode = jacksonMapper.createObjectNode();
		String mpResult = "";
		List<String> parmList = Arrays.asList( "bash", "-c",
			"mpstat -P ALL  2 1| grep -i average | sed 's/  */ /g'" );
		mpResult = osCommandRunner.executeString( null, parmList );

		logger.debug( "mpResult: {}", mpResult );

		if ( Application.isRunningOnDesktop() ) {
			updateCachesWithTestData();

			mpResult = Application.getContents( new File( getClass()
				.getResource( "/linux/mpResults.txt" ).getFile() ) );

		}

		// Skip past the header
		mpResult = mpResult.substring( mpResult.indexOf( "Average" ) );

		String[] mpLines = mpResult.split( LINE_SEPARATOR );

		for ( int i = 0; i < mpLines.length; i++ ) {

			String curline = mpLines[i].trim();
			String[] cols = curline.split( " " );
			if ( cols.length < 11 || cols[1].equalsIgnoreCase( "cpu" )
					|| cols[0].startsWith( "_" ) ) {
				logger.debug( "Skipping line: {}", curline );
				continue;
			}

			String name = cols[1];

			ObjectNode cpuNode = mpNode.putObject( name );

			cpuNode.put( "time", cols[0] + cols[1] );
			if ( !name.equals( "all" ) ) {
				name = "CPU -" + name;
			}
			cpuNode.put( "cpu", name );
			cpuNode.put( "puser", cols[2] );
			cpuNode.put( "pnice", cols[3] );
			cpuNode.put( "psys", cols[4] );
			cpuNode.put( "pio", cols[5] );
			cpuNode.put( "pirq", cols[6] );
			cpuNode.put( "psoft", cols[7] );
			cpuNode.put( "psteal", cols[8] );
			cpuNode.put( "pidle", cols[9] );
			cpuNode.put( "intr", cols[10] );
		}
		return mpNode;

	}

	ServiceResourceRunnable linuxResourceRunnable = null;

	private void updateCachesWithTestData () {

		try {
			File psResults = new File( getClass()
				.getResource( "/linux/psResults.txt" ).getFile() );

			// Used for parsing
			LINE_SEPARATOR = "\n";

			if ( csapApp.isDisplayOnDesktop() ) {
				logger.warn( "Desktop Testing - using stub output for linux {}", psResults.getAbsolutePath() );
			}
			processStatisticsCache.reset( FileUtils.readFileToString( psResults ) );

			diskUsageForServicesCache = FileUtils.readFileToString( new File(
				getClass().getResource( "/linux/duResults.txt" ).getFile() ) );

			diskUsageForServicesCache += FileUtils.readFileToString( new File(
				getClass().getResource( "/linux/dfShort.txt" ).getFile() ) );

			diskUsageForServicesCache += diskUsageForDockerContainers();

		} catch (Exception e) {
			logger.error( "Failed to load test data", e );
		}
	}

	// public void goActive() {
	// if (topStatsRunnable == null) {
	// topStatsRunnable = new TopRunnable();
	// // triggers the thread to go active
	// topStatsRunnable.getCpuForPid(Arrays.asList("dummy"));
	// }
	// }
	ArrayNode readOnlyFsResultsCache = null;
	long readOnlyFsTimeStamp = 0;

	public ArrayNode getReadOnlyFs () {

		logger.debug( "Getting getReadOnlyFs " );

		// Use cache
		if ( System.currentTimeMillis() - readOnlyFsTimeStamp < 1000 * 60 ) {

			logger.debug( "\n\n***** ReUsing  readOnlyFsResultsCache  *******\n\n" );

			return readOnlyFsResultsCache;
		}

		// Lets refresh cache
		logger.debug( "\n\n***** Refreshing readOnlyFsResultsCache   *******\n\n" );
		readOnlyFsTimeStamp = System.currentTimeMillis();

		try {

			// Test for read only FS
			// awk '$4~/(^|,)ro($|,)/' /proc/mounts | grep home | wc -l
			// ps -e --no-heading --sort -pcpu -o
			// pid,nlwp,pcpu,rss,ruser,args | sed 's/ */,/g'
			List<String> parmList = Arrays.asList( "bash", "-c",
				"awk '$4~/(^|,)ro($|,)/' /proc/mounts | grep home " );
			String roResult = osCommandRunner.executeString( parmList, new File(
				"." ) );

			logger.debug( "roResult: {}", roResult );

			if ( Application.isRunningOnDesktop() ) {

				if ( csapApp.isDisplayOnDesktop() )
					logger.warn( "Application.isRunningOnDesktop() - load test files from eclipse" );

				roResult = Application.getContents( new File( getClass()
					.getResource( "/linux/roResults.txt" ).getFile() ) );

			}

			ArrayNode readOnlyResults = jacksonMapper.createArrayNode();

			String[] roLines = roResult.split( System
				.getProperty( "line.separator" ) );
			for ( int i = 0; i < roLines.length; i++ ) {
				if ( roLines[i].trim().length() > 0
						&& !roLines[i].contains( OsCommandRunner.HEADER_TOKEN ) ) {
					readOnlyResults.add( roLines[i].trim() );
				}

			}
			readOnlyFsResultsCache = readOnlyResults;
		} catch (Exception e) {

			logger.error( "Failed to write output", e );
		}

		return readOnlyFsResultsCache;

	}

	@Inject
	CsapEventClient csapEventClient;

	ArrayNode whoResultsCache = jacksonMapper.createArrayNode();
	long lastWhoTimeStamp = 0;

	public ArrayNode getVmLoggedIn () {

		logger.debug( "Entered " );

		// Use cache
		if ( System.currentTimeMillis() - lastWhoTimeStamp < 1000 * 60 ) {

			logger.debug( "\n\n***** ReUsing  who cache   *******\n\n" );

			return whoResultsCache;
		}

		logger.debug( "\n\n***** Refreshing who cache   *******\n\n" );
		lastWhoTimeStamp = System.currentTimeMillis();

		try {

			// ps -e --no-heading --sort -pcpu -o
			// pid,nlwp,pcpu,rss,ruser,args | sed 's/ */,/g'
			List<String> parmList = Arrays.asList( "bash", "-c",
				"who |sed 's/  */ /g'"
						+ "" );
			String whoResult = osCommandRunner.executeString( parmList, new File(
				"." ) );

			logger.debug( "whoResult: {}", whoResult );

			if ( Application.isRunningOnDesktop() ) {

				if ( csapApp.isDisplayOnDesktop() ) {
					logger.warn( "Application.isRunningOnDesktop() - adding dummy login data" );
				}
				whoResult = "ssadmin  pts/0        2014-04-16 07:58 (rtp-someDeveloper-8811.yourcompany.com)"
						+ System.getProperty( "line.separator" )
						+ "ssadmin pts/34 2014-02-07 06:51";

			}

			ArrayNode whoResults = jacksonMapper.createArrayNode();

			String[] whoLines = whoResult.split( System
				.getProperty( "line.separator" ) );
			for ( int i = 0; i < whoLines.length; i++ ) {
				String curline = whoLines[i].trim();

				String[] cols = curline.split( " " );
				// some gen 2 systems have a lot of non-external connections.
				// col 5 will contain host if it is external. So we ignore the
				// others
				// To focus on external traffic only
				if ( curline.length() == 0 || curline.contains( OsCommandRunner.HEADER_TOKEN )
						|| cols.length == 4 ) {
					continue;
				}

				whoResults.add( curline );

			}

			if ( !whoResultsCache.toString().equals( whoResults.toString() ) ) {

				if ( whoResults.size() == 0 ) {
					csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/who",
						Application.SYS_USER,
						"Host Session(s) Cleared", "Connections are no longer active:\n"
								+ jacksonMapper.writerWithDefaultPrettyPrinter()
									.writeValueAsString( whoResultsCache ) );
				} else {
					csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/who",
						Application.SYS_USER,
						"Host Session(s) Changed", "Updated output of linux \"who\": \n"
								+ jacksonMapper.writerWithDefaultPrettyPrinter()
									.writeValueAsString( whoResults ) );
				}
			}
			whoResultsCache = whoResults;
		} catch (Exception e) {

			logger.error( "Failed to write output", e );
		}

		return whoResultsCache;
	}

	volatile ObjectNode hostResourceSummary = null;

	public ObjectNode getHostResourceSummary () {
		return hostResourceSummary;
	}

	public int getHostSummaryItem ( String fieldName ) {
		if ( hostResourceSummary == null ) {
			collectHostSocketsThreadsFiles();
		}

		if ( hostResourceSummary.has( fieldName ) ) {
			return hostResourceSummary.get( fieldName ).asInt( 0 );
		}
		return 0;
	}

	private final static String SOCKETS_THREADS_FILES_SCRIPT = "bin/collectHostSocketsThreadsFiles.sh";

	private OsCommandRunner hostRootCommands = new OsCommandRunner( 30, 1, "OsManager" );

	private void collectHostSocketsThreadsFiles () {
		// logger.info( "Call path: {}",
		// Application.getCsapFilteredStackTrace( new Exception( "calltree"
		// ), "csap" ) );
		logger.debug( "refreshing host stats" );
		String statsResult = null;
		try {

			// running as root to get access to all files on host.
			statsResult = osCommandRunner.runUsingRootUser( Application.getStagingFile( SOCKETS_THREADS_FILES_SCRIPT ), null );

			logger.debug( "statsResult: {}", statsResult );

			if ( Application.isRunningOnDesktop() ) {

				if ( csapApp.isDisplayOnDesktop() )
					logger.warn( "Application.isRunningOnDesktop() - load test files from eclipse" );
				statsResult = Application.getContents( new File(
					getClass().getResource( "/linux/vmStatsRoot.txt" ).getFile() ) );
				// statsResult = "openFiles: 2848 totalThreads: 900 csapThreads
				// 747 totalFileDescriptors: 799 csapFileDescriptors: 347
				// networkConns: 86 networkWait: 1";

			}

			statsResult = statsResult.substring( statsResult.indexOf( "openFiles:" ) );
			logger.debug( "trimmed results: {} ", statsResult );
			String now = LocalDateTime.now().format( DateTimeFormatter.ofPattern( "HH:mm:ss" ) );

			ObjectNode updatedHostSummary = jacksonMapper.createObjectNode();
			updatedHostSummary.put( "refreshed", now );

			String[] cols = statsResult.split( " " );

			if ( cols.length == 14 ) {
				updatedHostSummary.put( "openFiles", cols[1] );
				updatedHostSummary.put( "totalThreads", cols[3] );
				updatedHostSummary.put( "csapThreads", cols[5] );
				updatedHostSummary.put( "totalFileDescriptors", cols[7] );
				updatedHostSummary.put( "csapFileDescriptors", cols[9] );
				updatedHostSummary.put( "networkConns", cols[11] );
				updatedHostSummary.put( "networkWait", StringUtils.strip( cols[13], "\n" ) );
			} else {
				updatedHostSummary.put( "error", statsResult );
			}

			hostResourceSummary = updatedHostSummary;
		} catch (Exception e) {

			logger.error( "Failed to process output from {}: {}\n {}",
				SOCKETS_THREADS_FILES_SCRIPT, CSAP.getCsapFilteredStackTrace( e ), statsResult );
		}

	}

	private int diskCount = 0;

	public int getDiskCount () {
		return diskCount;
	}

	// ObjectNode dfResultsCache = null;
	// long lastDfTimeStamp = 0;
	private CsapSimpleCache diskStatisticsCache = null;

	public ObjectNode getCachedFileSystemInfo () {

		logger.debug( "Entered " );

		if ( diskStatisticsCache == null ) {
			diskStatisticsCache = CsapSimpleCache.builder(
				30,
				TimeUnit.SECONDS,
				OsManager.class,
				"Disk Statistics" );
		}

		// Use cache
		if ( !diskStatisticsCache.isExpired() ) {

			logger.debug( "\n\n***** ReUsing  DF cache   *******\n\n" );

			return (ObjectNode) diskStatisticsCache.getCachedObject();
		}

		// Lets refresh cache
		logger.debug( "\n\n***** Refreshing df cache   *******\n\n" );

		try {

			// ps -e --no-heading --sort -pcpu -o
			// pid,nlwp,pcpu,rss,ruser,args | sed 's/ */,/g'
			List<String> parmList = Arrays.asList( "bash", "-c",
				"df -PTh  | sed 's/  */ /g'" );
			String dfResult = osCommandRunner.executeString( parmList, new File(
				"." ) );

			logger.debug( "dfResult: {}", dfResult );

			if ( Application.isRunningOnDesktop() ) {

				if ( csapApp.isDisplayOnDesktop() )
					logger.warn( "Application.isRunningOnDesktop() - load test files from eclipse" );

				dfResult = Application.getContents( new File( getClass()
					.getResource( "/linux/dfResults.txt" ).getFile() ) );

			}

			ObjectNode svcToStatMap = jacksonMapper.createObjectNode();

			String[] dfLines = dfResult.split( System
				.getProperty( "line.separator" ) );
			int lastCount = 0;
			for ( int i = 0; i < dfLines.length; i++ ) {
				String curline = dfLines[i].trim();
				String[] cols = curline.split( " " );

				if ( cols.length < 7 || !cols[6].startsWith( "/" ) ) {
					logger.debug( "Skipping line: {}", curline );
					continue;
				}
				ObjectNode fsNode = svcToStatMap.putObject( cols[6] );

				fsNode.put( "dev", cols[0] );
				fsNode.put( "type", cols[1] );
				fsNode.put( "sized", cols[2] );
				fsNode.put( "used", cols[3] );
				fsNode.put( "avail", cols[4] );
				fsNode.put( "usedp", cols[5] );
				fsNode.put( "mount", cols[6] );
				lastCount++;
			}
			diskCount = lastCount;

			diskStatisticsCache.reset( svcToStatMap );
		} catch (Exception e) {

			logger.error( "Failed to write output", e );
		}

		return (ObjectNode) diskStatisticsCache.getCachedObject();

	}

	long lastSummaryTimeStamp = 0;
	ObjectNode summaryCacheNode;

	public ObjectNode getHostSummary () {
		// Use cache
		if ( System.currentTimeMillis() - lastSummaryTimeStamp < 1000 * 90 ) {

			logger.debug( "\n\n***** ReUsing  Summary cache   *******\n\n" );

			return summaryCacheNode;
		}

		ObjectNode summaryNode = jacksonMapper.createObjectNode();
		//
		List<String> parmList = Arrays.asList( "bash", "-c", "cat /etc/redhat-release" );
		String commandResult = osCommandRunner.executeString( parmList, new File( "." ), null, null,
			600, 10, null );

		logger.debug( "redhat release commandResult: {} ", commandResult );

		if ( commandResult.indexOf( OsCommandRunner.HEADER_TOKEN ) != -1
				&& commandResult.length() > (commandResult.indexOf( OsCommandRunner.HEADER_TOKEN ) + 5) ) {
			commandResult = commandResult.substring( commandResult.indexOf( OsCommandRunner.HEADER_TOKEN ) + 5 );
		}
		// if (psResult.contains(LINE_SEPARATOR))
		// psResult = psResult.substring(
		// psResult.indexOf(LINE_SEPARATOR +1 ));
		summaryNode.put( "redhat", commandResult );

		// w provides uptime and logged in users
		// parmList = Arrays.asList("bash", "-c", "w");
		parmList = Arrays.asList( "bash", "-c", "uptime" );
		commandResult = osCommandRunner.executeString( parmList, new File( "." ), null, null, 600, 10,
			null );

		logger.debug( "uptime commandResult: {} ", commandResult );

		if ( commandResult.indexOf( OsCommandRunner.HEADER_TOKEN ) != -1
				&& commandResult.length() > (commandResult.indexOf( OsCommandRunner.HEADER_TOKEN ) + 5) ) {
			commandResult = commandResult.substring( commandResult.indexOf( OsCommandRunner.HEADER_TOKEN ) + 5 );
		}
		summaryNode.put( "uptime", commandResult );

		//
		parmList = Arrays.asList( "bash", "-c", "uname -sr" );
		commandResult = osCommandRunner.executeString( parmList, new File( "." ), null, null, 600, 10,
			null );

		logger.debug( "uname commandResult: {} ", commandResult );

		if ( commandResult.indexOf( OsCommandRunner.HEADER_TOKEN ) != -1
				&& commandResult.length() > (commandResult.indexOf( OsCommandRunner.HEADER_TOKEN ) + 5) ) {
			commandResult = commandResult.substring( commandResult.indexOf( OsCommandRunner.HEADER_TOKEN ) + 5 );
		}
		summaryNode.put( "uname", commandResult );

		//
		parmList = Arrays.asList( "bash", "-c",
			"df -Ph | awk '{ print $2,$3,$4,$5,$6}' | column -t" );
		commandResult = osCommandRunner.executeString( parmList, new File( "." ), null, null, 600, 10,
			null );

		logger.debug( "df commandResult: {} ", commandResult );

		if ( commandResult.indexOf( OsCommandRunner.HEADER_TOKEN ) != -1
				&& commandResult.length() > (commandResult.indexOf( OsCommandRunner.HEADER_TOKEN ) + 5) ) {
			commandResult = commandResult.substring( commandResult.indexOf( OsCommandRunner.HEADER_TOKEN ) + 5 );
		}

		summaryNode.put( "df", commandResult );

		summaryCacheNode = summaryNode;
		lastSummaryTimeStamp = System.currentTimeMillis();
		return summaryNode;
	}

	/**
	 *
	 * 5 second cache is used, primarily to protect system churn
	 *
	 * @return
	 * @throws IOException
	 * @throws JsonParseException
	 * @throws JsonMappingException
	 */
	public JsonNode getHostRuntime ()
			throws IOException, JsonParseException,
			JsonMappingException {

		Split oldTimer = SimonManager.getStopwatch( "http.CsAgent.os.getManagerJson.GET" ).start();
		Split timer = SimonManager.getStopwatch( "AgentStatus" ).start();
		checkForProcessStatusUpdate();

		ObjectNode hostRuntime = jacksonMapper.createObjectNode();

		// add time stamp
		Format tsFormater = new SimpleDateFormat( "HH:mm:ss" );
		hostRuntime.put( "timeStamp", tsFormater.format( new Date() ) );
		hostRuntime.put( "serviceOpsQueue", serviceManager.getOpsQueued() );

		ObjectNode hotMetricJson = csapApp.getHostLoadCpuAndMore();

		try {
			hotMetricJson.put( "du", getCachedStagingDiskUsage() );

			hotMetricJson.set( "vmLoggedIn", getVmLoggedIn() );

			ObjectNode dfNode = getCachedFileSystemInfo();
			ObjectNode dfFilterNode = jacksonMapper.createObjectNode();

			if ( dfNode != null ) {
				for ( JsonNode node : dfNode ) {
					dfFilterNode.put( node.path( "mount" ).asText(), node
						.path( "usedp" ).asText() );
				}
				hotMetricJson.set( "df", dfFilterNode );
			}

			if ( getReadOnlyFs() != null ) {
				hotMetricJson.set( "readOnlyFS", getReadOnlyFs() );
			}

			if ( getMemoryAvailbleLessCache() < 0 ) {
				logger.error( "Get mem is invalid: " + getMemoryStatistics() );
				hotMetricJson.put( "memoryAggregateFreeMb", -1 );
			} else {
				hotMetricJson.put( "memoryAggregateFreeMb",
					getMemoryAvailbleLessCache() );
			}
		} catch (Exception e) {
			logger.warn( "Failed to get runtime time info", e );
		}

		hostRuntime.set( HostKeys.hostStats.jsonId, hotMetricJson );

		final int numSamplesToTake = csapApp.lifeCycleSettings().getLimitSamples();
		ObjectNode entriesToAverage = null;

		if ( !Application.isJvmInManagerMode() ) {
			entriesToAverage = csapApp.getVmProcessCollector( -1 ).getCollection( numSamplesToTake, 0, OsProcessCollector.ALL_SERVICES );
		}

		// note that admin api also uses this method - but average of data
		// cannot be used.
		final ObjectNode entriesForLambda = entriesToAverage;

		ObjectNode servicesJson = hostRuntime.putObject( HostKeys.services.jsonId );
		csapApp.getActiveModel()
			.getServicesOnHost( Application.getHOST_NAME() )
			.forEach( serviceInstance -> {
				String serviceId = serviceInstance.getServiceName_Port();
				ObjectNode latestServiceStats = serviceInstance.getRuntime();

				logger.debug( "Before: {}, stats: {}", serviceId,
					latestServiceStats.toString() );

				if ( entriesForLambda != null && (numSamplesToTake > 1) ) {
					logger.debug( "Using rolling averages for service meterics." );
					for ( OsProcessEnum os : OsProcessEnum.values() ) {

						if ( os == OsProcessEnum.diskUsedInMb ) {
							// Disk can get large very quickly. Always report
							// the last collected
							continue;
						}

						String ptr = "/data/" + os.value + "_" + serviceInstance.getPerformanceId();
						JsonNode serviceData = entriesForLambda.at( ptr );
						if ( serviceData.isArray() ) {

							long total = 0;
							for ( int i = 0; i < serviceData.size(); i++ ) {
								total += serviceData.get( i ).asLong();
							}
							long average = (total / serviceData.size());
							logger.debug( "{} Total: {} , Average: {}", os.value, total, average );
							latestServiceStats.put( os.value, average );
						}
					}
				}
				logger.debug( "After: {}, stats: {}", serviceId,
					latestServiceStats.toString() );

				servicesJson.set( serviceId,
					latestServiceStats );

			} );

		hostRuntime.set( HostKeys.lastCollected.jsonId, getLastCollectedValsConfigured() );

		timer.stop();
		oldTimer.stop();

		return hostRuntime;

	}

	public String getLastCollected ( ServiceInstance service, String searchKey ) {
		long result = 0;

		try {
			ServiceCollector applicationCollector = csapApp.getServiceCollector( csapApp
				.getFirstJmxStatsKey() );

			String[] serviceArray = { service.getServiceName_Port() };
			ObjectNode serviceData = applicationCollector.getCSVdata( false, serviceArray, 1, 0, "custom" );
			logger.debug( "Collected: {}", serviceData );

			result = serviceData.get( "data" ).get( searchKey ).get( 0 ).asLong();
		} catch (Exception e) {
			logger.warn( "{} Did not find: {}, \n {}",
				service.getServiceName_Port(), searchKey, CSAP.getCsapFilteredStackTrace( e ) );
		}

		return Long.toString( result );
	}

	// For all services.
	public ObjectNode getLastCollectedVals () {
		ObjectNode fullCollectionJson = jacksonMapper.createObjectNode();
		if ( !Application.isJvmInManagerMode() ) {
			OsSharedResourcesCollector vmCollector = csapApp.getVmSharedCollector( csapApp
				.getFirstVmStatsKey() );
			fullCollectionJson.set( "vm", vmCollector.getCSVdata( false, null, 1, 0 ).get( "data" ) );

			ArrayList<ServiceInstance> svcList = csapApp.getServicesOnHost();
			String[] serviceNames = new String[ svcList.size() ];
			int i = 0;
			for ( ServiceInstance instance : csapApp.getServicesOnHost() ) {
				serviceNames[i++] = instance.getServiceName();
			}

			OsProcessCollector serviceCollector = csapApp.getVmProcessCollector( csapApp
				.getFirstSvcStatsKey() );
			// fullCollectionJson.set( "process", serviceCollector.getCSVdata(
			// false, serviceNames, 1, 0 ).get( "data" ) );
			// this will grab all entries stored
			fullCollectionJson.set( "process", serviceCollector.getCSVdata( false, null, 1, 0 ).get( "data" ) );

			ServiceCollector applicationCollector = csapApp.getServiceCollector( csapApp
				.getFirstJmxStatsKey() );

			String[] javaJmxServices = csapApp.getActiveModel()
				.getServicesOnHost( Application.getHOST_NAME() )
				.filter( (ServiceInstance::isPerformJmxCollection) )
				.map( ServiceInstance::getServiceName_Port )
				.collect( Collectors.toList() )
				.stream().toArray( String[]::new );

			ObjectNode lastJmx = applicationCollector.getCSVdata( false, javaJmxServices, 1, 0 );
			if ( lastJmx.has( "data" ) ) {
				fullCollectionJson.set( "jmxCommon", lastJmx.get( "data" ) );
			} else {
				logger.warn( "JMX  collection does not contain data: {}. \n\t Services: {}",
					lastJmx.toString(), Arrays.asList( javaJmxServices ) );
				fullCollectionJson.set( "jmxCommon", lastJmx );
			}

			ObjectNode jmxCustom = fullCollectionJson.putObject( "jmxCustom" );

			csapApp.getActiveModel()
				.getServicesOnHost( Application.getHOST_NAME() )
				.filter( ServiceInstance::hasServiceMeters )
				.forEach( serviceInstance -> {
					String[] serviceArray = { serviceInstance.getServiceName_Port() };
					jmxCustom.set( serviceInstance.getServiceName(),
						applicationCollector.getCSVdata( false, serviceArray, 1, 0, "custom" )
							.get( "data" ) );
				} );

		} else {
			fullCollectionJson.put( "warning", "VM is in manager mode" );
		}
		return fullCollectionJson;
	}

	/**
	 * Gets the values for the real time meters defined in Application.json
	 *
	 * @return
	 */
	public ObjectNode getLastCollectedValsConfigured () {

		ObjectNode configCollection = jacksonMapper.createObjectNode();
		if ( !Application.isJvmInManagerMode() ) {
			ObjectNode fullCollectionJson = getLastCollectedVals();

			ObjectNode vmCollection = configCollection.putObject( "vm" );
			ObjectNode processCollection = configCollection.putObject( "process" );
			ObjectNode jmxCollection = configCollection.putObject( "jmxCommon" );
			ObjectNode jmxCustomCollection = configCollection.putObject( "jmxCustom" );
			ArrayNode realTimeMetersJson = csapApp.lifeCycleSettings().getRealTimeMeters();

			for ( JsonNode realTimeMeterDefn : realTimeMetersJson ) {

				MetricCategory performanceCategory = MetricCategory.parse( realTimeMeterDefn );
				String serviceName = performanceCategory.serviceName( realTimeMeterDefn );
				try {
					String id = realTimeMeterDefn.get( "id" ).asText();
					String[] idComponents = id.split( Pattern.quote( "." ) );
					String category = idComponents[0];
					String attribute = idComponents[1];
					logger.debug( "collector: {}, attribute: {} ", category, attribute );
					// vm. process. jmxCommon. jmxCustom.Service.var

					switch (performanceCategory) {

					case osShared:
						if ( attribute.equals( "cpu" ) ) {
							int totalCpu = fullCollectionJson.get( category ).get( "usrCpu" ).get( 0 ).asInt( 0 )
									+ fullCollectionJson.get( category ).get( "sysCpu" ).get( 0 ).asInt( 0 );
							vmCollection.put( "cpu", totalCpu );
						} else if ( attribute.equals( "coresActive" ) ) {
							int totalCpu = fullCollectionJson.get( category ).get( "usrCpu" ).get( 0 ).asInt( 0 )
									+ fullCollectionJson.get( category ).get( "sysCpu" ).get( 0 ).asInt( 0 );
							double coresActive = Math.round( totalCpu * csapApp.getCpuCountAsInt() ) / 100;
							vmCollection.put( "coresActive", coresActive );
						} else {
							vmCollection.put( attribute, fullCollectionJson.get( category ).get( attribute ).get( 0 )
								.asInt( 0 ) );
						}
						break;

					case osProcess:
						String csapId[] = attribute.split( "_" );
						String osStat = csapId[0];
						addConfiguredOsProcessMeters( serviceName, osStat, fullCollectionJson, category, attribute, processCollection );
						break;

					case java:
						String javaId[] = attribute.split( "_" );
						String javaStat = javaId[0];
						addConfiguredJavaMeters( serviceName, javaStat, attribute, fullCollectionJson, category, jmxCollection );

						break;

					case application:
						attribute = idComponents[2];
						if ( !fullCollectionJson.get( category ).has( serviceName ) ) {
							continue;
						}
						if ( !fullCollectionJson.get( category ).get( serviceName ).has( attribute ) ) {
							continue;
						}

						if ( !jmxCustomCollection.has( serviceName ) ) {
							jmxCustomCollection.putObject( serviceName );
						}
						ObjectNode serviceJmx = (ObjectNode) jmxCustomCollection.get( serviceName );

						serviceJmx.put( attribute,
							fullCollectionJson.get( category ).get( serviceName ).get( attribute )
								.get( 0 ).asInt( 0 ) );
						break;

					default:
						logger.warn( "Unexpected category type", category );

					}

				} catch (Exception e) {
					logger.error( "Failed parsing: {}, \n {}",
						realTimeMeterDefn,
						CSAP.getCsapFilteredStackTrace( e ) );
				}
			}

		} else {
			configCollection.put( "warning", "VM is in manager mode" );
		}

		return configCollection;
	}

	private void addConfiguredJavaMeters (
											String javaServiceName, String javaStat, String attribute,
											ObjectNode fullCollectionJson, String category, ObjectNode jmxCollection ) {

		final String serviceFilter;
		if ( MetricCategory.isAllServices( javaServiceName ) ) {
			serviceFilter = ".*";
		} else {
			serviceFilter = javaServiceName;
		}
		if ( MetricCategory.isAllServices( javaServiceName ) ) {
			Iterable<Map.Entry<String, JsonNode>> iterable = () -> fullCollectionJson.get( category ).fields();
			int allInstanceTotal = StreamSupport.stream( iterable.spliterator(), false )
				.filter( osEntry -> osEntry.getKey().startsWith( javaStat ) )
				.mapToInt( osEntry -> osEntry.getValue().get( 0 ).asInt( 0 ) )
				.sum();
			logger.debug( "Total for {} is {}", javaServiceName, allInstanceTotal );
			jmxCollection.put( attribute, allInstanceTotal );
		} else {
			String[] isFoundAMatch = { null };
			int allInstanceTotal = csapApp.getServicesOnHost().stream()
				.filter( ServiceInstance::isJavaCollectionEnabled )
				.filter( serviceinstance -> serviceinstance.getServiceName().matches( javaServiceName ) )
				.mapToInt( serviceinstance -> {
					String javaIdWithPort = javaStat + "_" + serviceinstance.getServiceName() + "_" + serviceinstance.getPort();

					if ( !fullCollectionJson.get( category ).has( javaIdWithPort ) ) {
						// logger.warn( "Did not find attribute: {}", attribute
						// );
						return 0;
					}
					isFoundAMatch[0] = "";
					int lastCollectedForPort = fullCollectionJson.get( category ).get( javaIdWithPort ).get( 0 ).asInt( 0 );
					return lastCollectedForPort;
				} )
				.sum();

			if ( isFoundAMatch[0] != null ) {
				jmxCollection.put( attribute, allInstanceTotal );
			} else {
				logger.debug( "Did not find a match for {} on host", attribute );
			}
		}

	}

	private void addConfiguredOsProcessMeters (	String csapServiceName, String osStat, ObjectNode fullCollectionJson, String category,
												String attribute, ObjectNode processCollection ) {
		if ( fullCollectionJson.get( category ).has( attribute ) ) {
			// typical
			processCollection.put( attribute,
				fullCollectionJson.get( category ).get( attribute ).get( 0 ).asInt( 0 ) );
		} else {

			if ( MetricCategory.isAllServices( csapServiceName ) ) {
				Iterable<Map.Entry<String, JsonNode>> iterable = () -> fullCollectionJson.get( category ).fields();
				int allInstanceTotal = StreamSupport.stream( iterable.spliterator(), false )
					.filter( osEntry -> osEntry.getKey().startsWith( osStat ) )
					.mapToInt( osEntry -> osEntry.getValue().get( 0 ).asInt( 0 ) )
					.sum();
				logger.debug( "Total for {} is {}", csapServiceName, allInstanceTotal );
				processCollection.put( attribute, allInstanceTotal );
			} else {
				// handle same service on multiple ports...
				String[] isFoundAMatch = { null };
				int allInstanceTotal = csapApp.getServicesOnHost().stream()
					.filter( serviceinstance -> serviceinstance.getServiceName().matches( csapServiceName ) )
					.mapToInt( serviceinstance -> {
						String serviceAndPort = osStat + "_" + serviceinstance.getServiceName() + "_" + serviceinstance.getPort();

						// logger.info("Checking for: {}", serviceAndPort);
						if ( !fullCollectionJson.get( category ).has( serviceAndPort ) ) {
							// logger.warn( "Did not find attribute: {}",
							// attribute );
							return 0;
						}
						isFoundAMatch[0] = "";
						int lastCollectedForPort = fullCollectionJson.get( category ).get( serviceAndPort ).get( 0 ).asInt( 0 );
						return lastCollectedForPort;
					} )
					.sum();
				if ( isFoundAMatch[0] != null ) {
					processCollection.put( attribute, allInstanceTotal );
				} else {
					logger.debug( "Did not find a match for {}", attribute );
				}
			}

		}
	}

	@Inject
	ServiceOsManager serviceManager;

	public ServiceOsManager getServiceManager () {
		return serviceManager;
	}

	/**
	 * Simple Wrapper so that we can block inside the method, and capture AOP
	 * timings
	 *
	 * @param psResult
	 * @param duResult
	 */
	private void handleProcessList ( String psResult, String duResult ) {

		// logger.info("Got here ========== ");
		Stopwatch stopwatch = SimonManager
			.getStopwatch( "java.OsManager.getProcessStats.parse" );

		Split split = stopwatch.start();
		synchronized (this) {
			handleProcessListSync( psResult, duResult );
		}
		split.stop();
	}

	private static String LINE_SEPARATOR = System.getProperty( "line.separator" );

	/**
	 *
	 * This stateful processsing, hence the need to synchronize 1. UI threads
	 * are polling for Updates 2. Metrics threads are polling to record results.
	 *
	 * @param psResult
	 * @param duResult
	 */
	private void handleProcessListSync ( String psResult, String duResult ) {
		// TODO Auto-generated method stub

		logger.debug( "Add org.csap.serviceDebug to debug in log4j.yml and add $PROCESSING/serviceName_port.debug to enable trace" );
		ArrayList<ServiceInstance> modelServiceInstances = csapApp.getServicesOnHost();

		String[] psLines = psResult.split( LINE_SEPARATOR );
		String[] duLines = duResult.split( LINE_SEPARATOR );
		if ( logger.isDebugEnabled() ) {
			logger.debug( "Number of lines: {}", psLines.length );
		}
		// Multiple threads are checking status. So first we capture entire
		// state, then update the live instances.
		HashMap<String, ServiceInstance> updatedInstanceDataMap = new HashMap<String, ServiceInstance>();
		for ( ServiceInstance serviceInstanceActiveModel : modelServiceInstances ) {

			// configure defaults for all services - could this hook the UI
			ServiceInstance instanceWithUpdatedRuntime = new ServiceInstance();

			instanceWithUpdatedRuntime.setServiceName( serviceInstanceActiveModel.getServiceName() );
			// create a new instance - update all fields - then move it to
			// active map

			updatedInstanceDataMap.put( serviceInstanceActiveModel.getServiceName_Port(),
				instanceWithUpdatedRuntime );

			instanceWithUpdatedRuntime.setCpuUtil( ServiceInstance.INACTIVE );
			instanceWithUpdatedRuntime.setThreadCount( 0 );
			instanceWithUpdatedRuntime.setRssMemory( 0 );
			instanceWithUpdatedRuntime.setVirtualMemory( 0 );
			instanceWithUpdatedRuntime.setDiskUsageInMb( "0" );

			if ( diskUsageForServicesCache.length() == 0 ) {
				instanceWithUpdatedRuntime.setDiskUsageInMb( "-1" );
			}
			instanceWithUpdatedRuntime.setRunHeap( "" );

			// defer less specific search until pass 2
			logger.debug( "Looking for: {}", serviceInstanceActiveModel.getServiceName() );

			int matchesFound = 0;
			if ( csapApp.isServiceDebug( serviceInstanceActiveModel ) ) {
				logger.info( "Looking for service: {} , psOutput:\n{} \nlines Remaining:\n {}",
					serviceInstanceActiveModel.getServiceName(), psResult, Arrays.asList( psLines ) );
			}
			for ( int psIndex = 0; psIndex < psLines.length; psIndex++ ) {
				String currLine = psLines[psIndex].trim();

				if ( checkPsLineForServiceMatch( psLines, serviceInstanceActiveModel,
					instanceWithUpdatedRuntime, psIndex, currLine ) ) {
					matchesFound++;
				}
			}
			// if ( csapApp.isServiceDebug( liveInstanceConfig ) ) {
			// logger.info( "Looking for match: {} in {}",
			// liveInstanceConfig.getServiceName(), currLine );
			// }
			logger.debug( "{} Matches Found: {}", serviceInstanceActiveModel.getServiceName(), matchesFound );

			// Traverse Du Lines
			updateServiceWithDiskUsage( duLines,
				serviceInstanceActiveModel,
				instanceWithUpdatedRuntime );
		}

		// Now we have end to end state - update the source of truth
		for ( ServiceInstance activeServiceInstance : modelServiceInstances ) {

			ServiceInstance resultInstance = updatedInstanceDataMap.get( activeServiceInstance
				.getServiceName_Port() );

			logger.debug( "updating service: {} in model with OS data:\n {}", activeServiceInstance.getServiceName_Port(), resultInstance );
			activeServiceInstance.setCpuUtil( resultInstance.getCpuUtil() );
			activeServiceInstance.setThreadCount( resultInstance.getThreadCount() );
			activeServiceInstance.setRssMemory( resultInstance.getRssMemory() );
			activeServiceInstance.setVirtualMemory( resultInstance.getVirtualMemory() );
			activeServiceInstance.setDiskUsageInMb( resultInstance.getDiskUsageInMb() );
			activeServiceInstance.setPid( resultInstance.getPid() );
			activeServiceInstance.setRunHeap( resultInstance.getRunHeap() );
			activeServiceInstance.setCurrentProcessPriority( resultInstance
				.getCurrentProcessPriority() );
			

			logger.debug( "active data: {}", activeServiceInstance );

			// special case for manual stops
			File serviceUserStopFile = new File( csapApp.getProcessingDir(), activeServiceInstance.getStoppedFileName() );
			File serviceFolder = csapApp.getWorkingDirectory( activeServiceInstance.getServiceName_Port() );

			File serviceStartLog = new File( serviceFolder.getAbsolutePath() + "_start.log" );

			if ( !activeServiceInstance.isRunning()
					&& (serviceFolder.exists() || serviceStartLog.exists())
					&& !serviceUserStopFile.exists() ) {
				activeServiceInstance.setUserStopped( false );

			} else {
				activeServiceInstance.setUserStopped( true );

			}

			logger.debug( "serviceUserStopFile: {}, userStopped: {}, serviceStartLog: {}, exists: {}",
				serviceUserStopFile.getAbsolutePath(), activeServiceInstance.isUserStopped(),
				serviceStartLog.getAbsolutePath(), serviceStartLog.exists() );

		}

	}

	/**
	 *
	 * Note - the format in this method is determined by
	 *
	 * @param psLines
	 * @param liveInstanceConfig
	 * @param instanceWithUpdatedRuntime
	 * @param psIndex
	 * @param currLine
	 * @return
	 *
	 * @see performOsProcessList
	 *
	 */
	@Monitored
	public boolean checkPsLineForServiceMatch (
												String[] psLines,
												ServiceInstance liveInstanceConfig,
												ServiceInstance instanceWithUpdatedRuntime,
												int psIndex, String currLine ) {

		logger.debug( "Search for {} using {} in { }",
			liveInstanceConfig.getServiceName(), liveInstanceConfig.getProcessFilter(), currLine );

		if ( currLine.length() > 0 ) {
			if ( !currLine.matches( liveInstanceConfig.getProcessFilter() ) ) {

				logger.debug( "\t NO Match for {} using {} in {}",
					liveInstanceConfig.getServiceName(), liveInstanceConfig.getProcessFilter(), currLine );

			} else {
				logger.debug( "\t MATCH for {} using {} in {}",
					liveInstanceConfig.getServiceName(), liveInstanceConfig.getProcessFilter(), currLine );

				// special hook to work with scripts. Ignore process
				// matches that are tagged
				if ( currLine.matches( ".*isCsapScript.*" )
						&& !liveInstanceConfig.isScript() ) {
					return false;
				}

				// first column is cpu usage
				// MULTIPLE matches possible. eg Oracle spawns many
				// processes
				// input is generated in performOsProcessList:
				// ps -e --no-heading --sort -rss -o
				// pcpu,rss,nlwp,ruser,pid,args
				String[] cols = currLine.trim().split( " " );
				// some services have multiple pids add together
				logger.debug( "{} adding cpu usage: {}", liveInstanceConfig.getServiceName(), cols[0]  );
				instanceWithUpdatedRuntime.addCpuUtil( cols[0] );
				instanceWithUpdatedRuntime.addRssMemory( cols[1] );
				instanceWithUpdatedRuntime.addVirtualMemory( cols[2] );
				instanceWithUpdatedRuntime.addThreadCount( cols[3] );
				instanceWithUpdatedRuntime.addPid( cols[5] );
				instanceWithUpdatedRuntime.setCurrentProcessPriority( cols[6] );
				int heapStart = currLine.indexOf( "-Xmx" );
				int heapEnd = currLine.indexOf( " ", heapStart + 1 );
				// logger.info("currLine: " + currLine + " heapCheck:" +
				// heapStart + " heapEnd: " + heapEnd) ;
				if ( heapStart != -1 && heapEnd != -1 ) {
					instanceWithUpdatedRuntime.addRunHeap( currLine.substring(
						heapStart, heapEnd ) );
				}
				if ( csapApp.isServiceDebug( liveInstanceConfig ) ) {
					logger.info( "{} Clearing line: {}", liveInstanceConfig.getServiceName(), psLines[psIndex] );
				}

				// This can short circuit the matches if multiple conflicting
				// matchers are used.
				psLines[psIndex] = "";

				logger.trace( "{} Updated Instance:\n {} ", liveInstanceConfig.getServiceName(),
					instanceWithUpdatedRuntime );
				return true;
			}
		}

		return false;
	}

	private void updateServiceWithDiskUsage (	String[] du_and_df_output,
												ServiceInstance liveInstanceConfig,
												ServiceInstance resultInstance ) {

		logger.debug( "du_and_df_output: {} ", Arrays.asList( du_and_df_output ) );
		for ( String serviceMatch : liveInstanceConfig.getDiskUsageMatcher() ) {
			for ( String diskLine : du_and_df_output ) {

				logger.debug( "diskSearch: {}, curLine: {}", serviceMatch, diskLine );

				if ( diskLine.contains( serviceMatch ) ) {
					// logger.info("Match found") ;
					String[] duFields = diskLine.split( " " );

					String diskUsedField = duFields[0];
					if ( diskUsedField.contains( "/" ) ) {
						// df output 815M/7942M /run 11% tmpfs
						diskUsedField = diskUsedField.split( "/" )[0];
					}
					resultInstance.setDiskUsageInMb( diskUsedField );

					logger.debug( "{} Matched, diskSearch: {}, in  line: {}",
						liveInstanceConfig.getServiceName(), serviceMatch, diskLine );

					break;

				}

			}
		}
	}

	// triggered after deployment activities
	public void scheduleDiskUsageCollection () {
		// rawDuAndDfLinuxOutput = "";
		if ( !Application.isJvmInManagerMode() ) {

			if ( !intenseOsCommandExecutor.isShutdown() ) {
				intenseOsCommandExecutor.execute( () -> collectDiskUsage() );
			} else {
				logger.info( "Skipping due to intenseOsCommandExecutor is not running, assuming shutdown in progress" );
			}
		}
	}

	private volatile String diskUsageForServicesCache = "";

	@Inject
	DockerHelper dockerHelper;

	private static final String[] diskUsageScriptTemplate = {
			"#!/bin/bash",
			"du -sm _PATHS_  2>&1 | grep -iv '^du:' | awk '{print $1 \" \"  $2}' ",
			"" };

	private static final String[] diskFileSystemScript = {
			"#!/bin/bash",
			"df -PT -BM  | sed 's/  */ /g' |  awk '{print $4 \"/\" $3 \" \" $7 \" \" $6 \" \" $1}'",
			"" };

	/**
	 * 
	 * ==== Disk is collected for both services and core file systems
	 * 
	 * 
	 */
	@CsAgentTimer
	private void collectDiskUsage () {

		Stopwatch stopwatch = SimonManager
			.getStopwatch( "java.OsManager.collectDiskUsage" );
		Split split = stopwatch.start();

		logger.debug( "\n\n***** Refreshing DU cache   *******\n\n" );

		StringBuilder diskCollection;
		try {
			diskCollection = new StringBuilder( "\n" );

			String[] diskUsageScript = diskUsageScriptTemplate.clone();

			// Step 1 - collect disk usage under csap processing. Some files may
			// be privelged - use root if available
			updateDuScriptWithServicePaths( diskUsageScript );
			diskCollection.append( osCommandRunner.runUsingRootUser( "diskUsage", diskUsageScript ) );

			// Step 2 - collect disk usage use df output, services can specify
			// device
			diskCollection.append( osCommandRunner.runUsingDefaultUser( "diskFileUsage", diskFileSystemScript ) );

			// Step 3 - disk usage from docker
			diskCollection.append( diskUsageForDockerContainers() );

			// Finally - update the cache
			diskUsageForServicesCache = diskCollection.toString();

			logger.debug( "Script to run: {} \n result: {}",
				Arrays.asList( diskUsageScript ), diskUsageForServicesCache );

		} catch (IOException e) {
			logger.error( "Failed getting disk: {}", CSAP.getCsapFilteredStackTrace( e ) );
		}

		//
		// Collect docker container size
		//

		split.stop();
	}

	private void updateDuScriptWithServicePaths ( String[] diskUsageScript ) {
		String paths = csapApp
			.getServicesOnHost().stream()
			.map( ServiceInstance::getDiskUsagePath )
			.distinct()
			.collect( Collectors.joining( " " ) );

		diskUsageScript[1] = diskUsageScript[1].replaceAll( "_PATHS_", paths );

		return;
	}

	private String diskUsageForDockerContainers () {

		if ( !csapApp.isDockerInstalledAndActive() ) {
			return "";
		}
		return csapApp
			.getServicesOnHost().stream()
			.filter( ServiceInstance::isDockerContainer )
			.map( dockerHelper::serviceDiskUsage )
			.collect( Collectors.joining( "\n" ) );
	}

	/**
	 *
	 * Allow for du to be kicked off via service activities. Eg. Refresh after a
	 * service has been deployed
	 *
	 */
	// public static void setDuValue(int duValue) {
	// OsManager.duValue = duValue;
	// }
	public final static List<String> PS_LIST = Arrays
		.asList( "bash",
			"-c",
			"ps -e --no-heading --sort -rss -o pcpu,rss,vsz,nlwp,ruser,pid,nice,args | sed 's/  */ /g'" );

	public String lastProcessStatsCollected () {
		return (String) processStatisticsCache.getCachedObject();
	}

	private volatile CsapSimpleCache processStatisticsCache = null;
	private volatile boolean initalPsComplete = false;

	public boolean isInitalPsComplete () {
		return initalPsComplete;
	}

	private ReentrantLock processStatusLock = new ReentrantLock();

	public void checkForProcessStatusUpdate () {
		if ( processStatisticsCache == null ) {
			processStatisticsCache = CsapSimpleCache.builder(
				9,
				TimeUnit.SECONDS,
				OsManager.class,
				"Process Stats" );
		}

		if ( !processStatisticsCache.isExpired() ) {

			logger.debug( "\n\n***** ReUsing  processStatisticsCache   *******\n\n" );

		} else if ( processStatusLock.tryLock() ) {

			logger.debug( "\n\n***** REFRESH  processStatisticsCache   *******\n\n" );
			Split generalSplit = SimonManager.getStopwatch( "java.OsManager.getProcessStats" )
				.start();
			try {

				String osOutput = osCommandRunner.executeString( null, PS_LIST );
				processStatisticsCache.reset( osOutput );
				// updateDiskUsageOnStagingCache();
				if ( Application.isRunningOnDesktop() ) {

					updateCachesWithTestData();
				}
				{
					// logger.debug("Local Results: " + psResult);
				}
				handleProcessList(
					(String) processStatisticsCache.getCachedObject(),
					diskUsageForServicesCache );

				initalPsComplete = true;

			} catch (Exception e) {
				logger.info( "Failed refreshing runtime" );
			} finally {
				processStatusLock.unlock();
			}

			generalSplit.stop();
		}

	}

	private final static List<String> DF_LIST = Arrays.asList( "bash", "-c",
		"df -Ph $STAGING  | tail -1 | grep -o '[0-9]*%'" );
	private int stagingDiskUsageCachedValue = -1;
	long lastStagingTimeStamp = 0;

	private int getCachedStagingDiskUsage () {
		// Use cache
		logger.debug( "Checking DF" );
		if ( System.currentTimeMillis() - lastStagingTimeStamp < 1000 * 60 ) {

			logger.debug( "\n\n***** ReUsing  DF cache   *******\n\n" );

			return stagingDiskUsageCachedValue;
		}

		logger.debug( "Updating DF" );
		lastStagingTimeStamp = System.currentTimeMillis();

		String dfOnStagingRawOutput = osCommandRunner.executeString( DF_LIST,
			new File( "." ) );

		if ( Application.isRunningOnDesktop() ) {
			dfOnStagingRawOutput = Application.getContents( new File( getClass()
				.getResource( "/linux/dfStaging.txt" ).getFile() ) );
		}

		try {
			String[] dfLines = dfOnStagingRawOutput.split( System
				.getProperty( "line.separator" ) );
			// Need to strip off the header
			stagingDiskUsageCachedValue = Integer.parseInt( dfLines[dfLines.length - 1].replace( "%",
				"" ) );
		} catch (Exception e) {
			if ( !Application.isRunningOnDesktop() ) {
				logger.error( "Failed to parse du: " + dfOnStagingRawOutput );
			}
		}
		return stagingDiskUsageCachedValue;
	}

	/**
	 * Full Listing for traping memory usage
	 *
	 */
	public final static List<String> PS_MEMORY_LIST = Arrays
		.asList( "bash",
			"-c",
			"ps -e --sort -rss -o pmem,rss,vsz,size,nlwp,ruser,pid,args | sed 's/  */ /g' | sed 's/,/ /g' |awk '{ for(i=1;i<=7;i++){$i=$i\",\"}; print }'" );

	public final static List<String> PS_PRIORITY_LIST = Arrays
		.asList( "bash",
			"-c",
			"ps -e --sort nice -o nice,pmem,rss,vsz,size,nlwp,ruser,pid,args | sed 's/  */ /g' | sed 's/,/ /g' |awk '{ for(i=1;i<=8;i++){$i=$i\",\"}; print }'" );

	public final static List<String> FREE_LIST = Arrays.asList( "bash", "-c",
		"free -g" );

	public final static List<String> FREE_BY_M_LIST = Arrays.asList( "bash",
		"-c", "free -m" );

	public String performMemoryProcessList (	boolean sortByPriority,
												boolean isShowOnlyCsap, boolean isShowOnlyUser ) {

		List<String> psList = PS_MEMORY_LIST;

		if ( sortByPriority ) {
			psList = PS_PRIORITY_LIST;
		}
		// ps -e --sort -rss -o pmem,rss,args | awk '{
		// for(i=0;i<=NF;i++){$i=$i","}; print }'
		// ps -e --sort -rss -o pmem,rss,vsz,size,nlwp,ruser,pid,args | sed
		// 's/ */ /g'
		// ps -e --sort -rss -o pmem,rss,vsz,size,nlwp,ruser,pid,args | sed
		// 's/ */ /g' |awk '{ for(i=0;i<=7;i++){$i=$i","}; print }'
		// size or rss...switch to size as it is bigger for now
		String psResult = osCommandRunner.executeString( null, psList );

		String freeResult = osCommandRunner.executeString( FREE_LIST, new File(
			"." ) );

		freeResult += osCommandRunner.executeString( FREE_BY_M_LIST, new File(
			"." ) );

		if ( Application.isRunningOnDesktop() ) {

			if ( csapApp.isDisplayOnDesktop() )
				logger.warn( "Application.isRunningOnDesktop() - load test files from eclipse" );

			freeResult = Application.getContents( new File( getClass()
				.getResource( "/linux/freeResults.txt" ).getFile() ) );
			psResult = Application.getContents( new File( getClass()
				.getResource( "/linux/psMemory.txt" ).getFile() ) );
			if ( sortByPriority ) {
				psResult = Application.getContents( new File( getClass()
					.getResource( "/linux/psNice.txt" ).getFile() ) );
			}
		}

		// hook to display output nicely in browser
		String[] psLines = psResult.split( LINE_SEPARATOR );
		StringBuilder psBuilder = new StringBuilder();

		String currUser = csapApp.getAgentRunUser();
		for ( int psIndex = 0; psIndex < psLines.length; psIndex++ ) {
			String currLine = psLines[psIndex].trim();
			String nameToken = "csapProcessId=";

			int nameStart = currLine.indexOf( nameToken );
			int headerStart = currLine.indexOf( "RSS" );
			int processingStart = currLine.indexOf( csapApp.getProcessingDir().getAbsolutePath() );

			if ( Application.isRunningOnDesktop() ) {
				processingStart = currLine.indexOf( "/home/ssadmin/processing" );
				currUser = "ssadmin";
			}

			// skip past any non csap processes
			if ( isShowOnlyCsap && nameStart == -1 && headerStart == -1 && processingStart == -1 ) {
				continue;
			}

			// only show ssadmin processes
			if ( currUser != null && isShowOnlyUser && headerStart == -1 && !currLine.contains( currUser ) ) {
				continue;
			}

			if ( nameStart != -1 ) {
				nameStart += nameToken.length();
				int nameEnd = currLine.substring( nameStart ).indexOf( " " )
						+ nameStart;
				if ( nameEnd == -1 ) {
					nameEnd = nameStart + 5;
				}

				logger.debug( "currLine: {}, \n\t nameStart: {}, \t nameEnd: {}",
					currLine, nameStart, nameEnd );

				String serviceName = currLine.substring( nameStart, nameEnd );

				int insertIndex = currLine.indexOf( "/" );

				if ( isShowOnlyCsap ) {
					currLine = currLine.substring( 0, insertIndex ) + serviceName;
				} else {
					currLine = currLine.substring( 0, insertIndex )
							+ serviceName
							+ " : "
							+ currLine
								.substring( insertIndex, currLine.length() );
				}

			}

			psBuilder.append( currLine + LINE_SEPARATOR );
		}

		return freeResult + "\n\n" + psBuilder;

	}

	private CsapSimpleCache ioStatisticsCache = null;
	private ReentrantLock ioStatusLock = new ReentrantLock();

	public ObjectNode diskActivity () {

		boolean initialRun = false;

		if ( ioStatisticsCache == null ) {
			initialRun = true;
			int cacheTime = 20;
			if ( Application.isRunningOnDesktop() ) {
				cacheTime = 1;
			}
			ioStatisticsCache = CsapSimpleCache.builder(
				cacheTime,
				TimeUnit.SECONDS,
				OsManager.class,
				"IO Statistics" );
		}

		if ( !ioStatisticsCache.isExpired() ) {

			logger.debug( "\n\n***** ReUsing  ioStatisticsCache   *******\n\n" );

		} else if ( ioStatusLock.tryLock() ) {

			logger.debug( "\n\n***** REFRESHING   ioStatisticsCache   *******\n\n" );
			Split iostatsTimer = SimonManager.getStopwatch( "java.OsManager.iostats.refresh" ).start();
			try {

				ioStatisticsCache.reset( updateIoCache( initialRun ) );

			} catch (Exception e) {
				logger.info( "Failed refreshing runtime {}",
					CSAP.getCsapFilteredStackTrace( e ) );

			} finally {
				ioStatusLock.unlock();
			}
			iostatsTimer.stop();
		}

		return (ObjectNode) ioStatisticsCache.getCachedObject();

	}

	private ObjectNode updateIoCache ( boolean isInitialRun ) {
		ObjectNode diskActivityReport = jacksonMapper.createObjectNode();

		String[] diskTestScript = {
				"#!/bin/bash",
				"iostat -dm",
				"" };
		String iostatOutput = "";
		try {
			iostatOutput = osCommandRunner.runUsingDefaultUser( "diskTest", diskTestScript );

			// Device: tps MB_read/s MB_wrtn/s MB_read MB_wrtn

			if ( isInitialRun ) {
				logger.info( "Results from {}, \n {}", Arrays.asList( diskTestScript ), iostatOutput );
			}

			if ( Application.isRunningOnDesktop() ) {

				iostatOutput = Application.getContents( new File( getClass()
					.getResource( "/linux/ioStatResults.txt" ).getFile() ) );

			}

			String[] iostatLines = iostatOutput.split( LINE_SEPARATOR );

			ArrayNode filteredLines = diskActivityReport.putArray( "filteredOutput" );
			int totalDiskReadMb = 0;
			int totalDiskWriteMb = 0;
			for ( int i = 0; i < iostatLines.length; i++ ) {

				String curline = iostatLines[i].replaceAll( "\\s+", " " );

				logger.debug( "Processing line: {}", curline );

				if ( curline != null && !curline.isEmpty() && curline.matches( csapApp.lifeCycleSettings().getIostatDeviceFilter() ) ) {
					filteredLines.add( curline );
					String[] fields = curline.split( " " );
					if ( fields.length == 6 ) {
						totalDiskReadMb += Integer.parseInt( fields[4] );
						totalDiskWriteMb += Integer.parseInt( fields[5] );
					}
				}

			}
			diskActivityReport.put( "totalDiskReadMb", totalDiskReadMb );
			diskActivityReport.put( "totalDiskWriteMb", totalDiskWriteMb );
		} catch (Exception e) {
			logger.info( "Results from {}, \n {}, \n {}",
				Arrays.asList( diskTestScript ), iostatOutput,
				CSAP.getCsapFilteredStackTrace( e ) );
		}

		return diskActivityReport;

	}

	public int getMemoryAvailbleLessCache () {

		if ( getMemoryStatistics() == null ) {
			return -1;
		}

		if ( isMemoryFreeAvailabe() ) {
			return getMemoryStatistics().get( RAM ).get( 6 ).asInt( -1 );
		}
		return getMemoryStatistics().get( BUFFER ).get( 3 ).asInt( -1 );
	}

	public int getMemoryCacheSize () {
		if ( getMemoryStatistics() == null ) {
			return -1;
		}

		if ( isMemoryFreeAvailabe() ) {
			return getMemoryStatistics().get( RAM ).get( 5 ).asInt();
		}
		return getMemoryStatistics().get( RAM ).get( 6 ).asInt();
	}

	// newer RH kernels have available as last column
	private boolean isMemoryFreeAvailabe () {
		return getMemoryStatistics().get( FREE_AVAILABLE ).asBoolean();
	}

	private CsapSimpleCache memoryStatisticsCache = null;

	@RequestMapping ( "/getMem" )
	public JsonNode getMemoryStatistics () {

		logger.debug( "Entered" );
		// logger.info( "{}", Application.getCsapFilteredStackTrace( new
		// Exception( "calltree" ), "csap" )) ;
		if ( memoryStatisticsCache == null ) {
			memoryStatisticsCache = CsapSimpleCache.builder(
				4,
				TimeUnit.SECONDS,
				OsManager.class,
				"Memory Stats" );
		}

		// Use cache
		if ( !memoryStatisticsCache.isExpired() ) {

			logger.debug( "\n\n***** ReUsing  mem cache   *******\n\n" );

			return (JsonNode) memoryStatisticsCache.getCachedObject();
		}

		JsonNode rootNode;

		List<String> parmList = Arrays.asList( "bash", "-c", "free -m" );
		String freeResult = osCommandRunner.executeString( parmList, new File(
			"." ) );
		parmList = Arrays.asList( "bash", "-c", "swapon -s " );
		String swapResult = osCommandRunner.executeString( parmList, new File(
			"." ) );
		try {
			// Lets refresh cache
			logger.debug( "\n\n***** Refreshing mem cache   *******\n\n" );

			if ( Application.isRunningOnDesktop() ) {

				if ( csapApp.isDisplayOnDesktop() )
					logger.warn( "Application.isRunningOnDesktop() - load test files from eclipse" );

				String freeTestData = "/linux/freeResults.txt";
				// freeTestData = "/linux/freeResults_rh7.txt";
				freeResult = Application.getContents( new File( getClass()
					.getResource( freeTestData ).getFile() ) );

				String swapTest = "/linux/swapResults.txt";
				// swapTest = "/linux/swapResults_rh7.txt";

				swapResult = Application.getContents( new File( getClass()
					.getResource( swapTest ).getFile() ) );

			}

			logger.debug( "freeResult: {}, \n swapResult: {} ", freeResult, swapResult );

			String headers = freeResult.substring( 0, freeResult.indexOf( "Mem:" ) );
			// handle newerKernel rh7+
			boolean isFreeAvailable = false;
			if ( headers.contains( "available" ) ) {
				isFreeAvailable = true;
			}

			// Strips off the headers
			String trimFree = freeResult.substring( freeResult.indexOf( "Mem:" ) );
			String[] memLines = trimFree
				.split( LINE_SEPARATOR );

			TreeMap<String, String[]> memResults = new TreeMap<String, String[]>();

			for ( int i = 0; i < memLines.length; i++ ) {

				if ( memLines[i].contains( "Mem:" ) ) {
					memResults.put( RAM,
						memLines[i].trim().replaceAll( "\\s+", " " ).split( " " ) );

					// default buffer to use RAM line. centos
					memResults.put( BUFFER, memLines[i].trim().replaceAll( "\\s+", " " ).split( " " ) );

				} else if ( memLines[i].contains( "cache:" ) ) {
					memResults.put( BUFFER, memLines[i].trim().replaceAll( "\\s+", " " )
						.split( " " ) );

				} else if ( memLines[i].contains( "Swap:" ) ) {

					memResults.put( SWAP, memLines[i].trim().replaceAll( "\\s+", " " )
						.split( " " ) );
				}

			}

			if ( (swapResult.trim().length() == 0) || (!swapResult.contains( "Filename" )) ) {
				String[] noResults = { "no Swap Found" };
				memResults.put( "swapon1", noResults );
			} else {
				String trimSwap = swapResult.substring( swapResult.indexOf( "Filename" ) );
				String[] swapLines = trimSwap.split( System
					.getProperty( "line.separator" ) );

				// skip past the header no matter what it is
				int i = 1;
				for ( String line : swapLines ) {
					ArrayList<String> swapList = new ArrayList<String>();
					String swapPer = "";
					// added host below for the simple view which needs to track
					// it
					String[] columns = line.trim().replaceAll( "\\s+", " " ).split( " " );
					if ( columns.length == 5 && columns[0].startsWith( "/" ) ) {
						swapPer = "";
						try {
							float j = Float.parseFloat( columns[3] );
							float k = Float.parseFloat( columns[2] );
							swapPer = (new Integer( Math.round( j / k * 100 ) )).toString();
						} catch (Exception e) {
							// ignore
						}
						swapList.add( columns[0] );
						swapList.add( columns[1] );
						swapList.add( swapPer );
						swapList.add( columns[3] + " / " + columns[2] );
						swapList.add( columns[4] );
						memResults.put( "swapon" + i++,
							swapList.toArray( new String[ swapList.size() ] ) );
					}
				}
			}
			rootNode = jacksonMapper.valueToTree( memResults );

			Format tsFormater = new SimpleDateFormat( "HH.mm.ss" );
			((ObjectNode) rootNode).put( "timestamp", tsFormater.format( new Date() ) );
			((ObjectNode) rootNode).put( FREE_AVAILABLE, isFreeAvailable );

			memoryStatisticsCache.reset( rootNode );
			return rootNode;
		} catch (Exception e) {
			logger.warn( "Failure parsing memory, free:\n {} \n swap:\n{}", freeResult, swapResult, e );
		}

		return null;
	}

	public static final String FREE_AVAILABLE = "isFreeAvailable";

	public String updatePlatformCore (	MultipartFile multiPartFile, String extractTargetPath,
										boolean skipExtract, String remoteServerName,
										String chownUserid, String auditUser, String deleteExisting, OutputFileMgr outputFileManager )
			throws IOException {

		StringBuilder results = new StringBuilder( "\n==  Host:" + remoteServerName );

		if ( multiPartFile == null ) {
			results.append( "\n========== multiPartFile is null \n\n" );
			return results.toString();
		}

		if ( extractTargetPath.trim()
			.length() == 0
				|| chownUserid.trim()
					.length() == 0
				|| auditUser.trim()
					.length() == 0 ) {
			logger.error( "extractTargetPath is empty, must be corrected" );
			results.append( "\n " + MISSING_PARAM_HACK
					+ " param was an empty string and is required. extractTargetPath: "
					+ extractTargetPath + ", chownUserid: " + chownUserid + ", auditUser:" + auditUser );
			return results.toString();

		}
		// byte[] fileBytes = file.getBytes();

		// We temporarily extract all files to followingFolder, then copy to
		// target location
		// Note the subFolder MUST be different from original source folder, or
		// files could get overwritten
		File tempFolder = new File( csapApp.getStagingFolder(), "/temp/CsAgentTransfer/" );

		if ( !tempFolder.exists() ) {
			tempFolder.mkdirs();
		}

		File tempExtractLocation = new File( tempFolder, multiPartFile.getOriginalFilename() );

		if ( Application.isRunningOnDesktop() && skipExtract ) {
			tempExtractLocation = new File( extractTargetPath );
		}
		;
		results.append( " uploaded file: " + multiPartFile.getOriginalFilename() );
		results.append( " Size: " + multiPartFile.getSize() );

		File extractTarget = new File( extractTargetPath );
		if ( !extractTarget.getParentFile().exists() ) {
			logger.warn( "parent folder for extraction does not exist: {} ",
				extractTarget.getParentFile().getAbsolutePath() );
		}

		if ( extractTarget.exists() && extractTarget.isFile() ) {
			results.append( "\n ===> Destination exists and will be overwritten." );

			if ( Application.isRunningOnDesktop() & !skipExtract ) {
				tempExtractLocation = new File( extractTarget.getAbsolutePath() + ".windebug" );
				results.append( "\n desktop destination for testing only: "
						+ tempExtractLocation.getAbsolutePath() );
			}
		}

		try {
			outputFileManager.printImmediate( "\n\n *** Temporary upload location: " + tempExtractLocation.getAbsolutePath() );
			multiPartFile.transferTo( tempExtractLocation );
		} catch (Exception e) {
			logger.error( "multiPartFile.transferTo : {}", tempExtractLocation, e );
			return "\n== " + CSAP.CONFIG_PARSE_ERROR
					+ " on multipart file transfer on Host " + Application.getHOST_NAME()
					+ ":" + e.getMessage();
		}

		if ( Application.isRunningOnDesktop() && !skipExtract
				&& (multiPartFile.getOriginalFilename()
					.endsWith( ".zip" )) ) {

			if ( extractTarget.exists() && extractTarget.isDirectory() ) {

				try {
					File winTarget = new File( extractTarget, "winDebug" );
					ZipUtility.unzip( tempExtractLocation, winTarget );
					results.append( "\n Unzipped to: " + winTarget.getAbsolutePath() );
				} catch (Exception e) {
					results.append( "\n Failed to unzip " + tempExtractLocation.getAbsolutePath()
							+ " due to: " + e.getMessage() );
					logger.error( "\n Failed to unzip " + tempExtractLocation.getAbsolutePath(), e );
				}
			} else {
				results.append( "\n== " + CSAP.CONFIG_PARSE_ERROR
						+ " extract target exists and is a file: "
						+ extractTarget.getAbsolutePath() );
			}
		}

		if ( !tempExtractLocation.exists() ) {
			results.append( " Could not run as root, extract file not located in "
					+ tempExtractLocation.getAbsolutePath() );
		} else {

			// backup existing
			if ( deleteExisting != null ) {
				createBackupAndDelete( extractTarget, results );
			}

			// hook for root ownership, and script execution
			// ALWAYS use CSAP user if files are extracted
			String user = chownUserid;
			if ( extractTargetPath.startsWith( csapApp.getAgentRunHome() ) ) {
				user = csapApp.getAgentRunUser();
				logger.info( "Specified directory starts with: {}, userid will be set to: {}", csapApp.getAgentRunHome(), user );
			}

			File scriptPath = Application.getStagingFile( "/bin/unzipAsRoot.sh" );
			List<String> parmList = new ArrayList<String>();
			if ( Application.isRunningAsRoot() ) {
				parmList.add( "/usr/bin/sudo" );
				parmList.add( scriptPath.getAbsolutePath() );
				parmList.add( tempExtractLocation.getAbsolutePath() );
				parmList.add( extractTargetPath );
				parmList.add( user );

				if ( skipExtract ) {
					parmList.add( "skipExtract" );
				}
			} else {
				parmList.add( "bash" );
				parmList.add( "-c" );
				String command = scriptPath.getAbsolutePath()
						+ " " + tempExtractLocation.getAbsolutePath()
						+ " " + extractTargetPath
						+ " " + chownUserid;

				if ( skipExtract ) {
					command += " skipExtract";
				}
				parmList.add( command );
			}

			results.append( "\n" + osCommandRunner.executeString( null, parmList ) );
		}

		csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/fileUpload", auditUser,
			multiPartFile.getOriginalFilename(), results.toString() );

		return results.toString();
	}

	public static String MISSING_PARAM_HACK = CSAP.CONFIG_PARSE_ERROR + "-BlankParamFound";

	// Adds a .old suffix
	private void createBackupAndDelete ( File globalModelBuildFolder, StringBuilder results )
			throws IOException {
		// In case of Check-IN - or reload - working folder will
		// contain propertyOverrid files from svn Checkout
		File backUpFolder = new File( globalModelBuildFolder.getCanonicalPath() + ".previous" );
		if ( backUpFolder.exists() ) {
			FileUtils.deleteQuietly( backUpFolder );
			results.append( "\n\n Deleting previous backup: " + backUpFolder.getAbsolutePath() + "\n" );
		}
		if ( globalModelBuildFolder.exists() ) {
			// check out into a clean folder every time.
			results.append( "\n\n Moving : " + globalModelBuildFolder.getAbsolutePath()
					+ " to: " + backUpFolder.getAbsolutePath() + "\n" );
			FileUtils.moveDirectory( globalModelBuildFolder, backUpFolder );
		} else {
			results.append( "Folder does not exist: " + globalModelBuildFolder.getCanonicalPath() );
			logger.warn( "Folder does not exist: {}", globalModelBuildFolder.getCanonicalPath() );
		}
	}

	/**
	 * Note that cluster can be none, in which case command is only run on
	 * current VM.
	 *
	 * @param timeoutSeconds
	 * @param contents
	 * @param chownUserid
	 * @param clusterName
	 * @param scriptName
	 * @param outputFm
	 * @return
	 * @throws IOException
	 */
	public ObjectNode executeShellScriptClustered (	String apiUser,
													int timeoutSeconds, String contents, String chownUserid,
													String[] hosts,
													String scriptName, OutputFileMgr outputFm )
			throws IOException {

		List<String> hostList = new ArrayList<>( Arrays.asList( hosts ) );

		ObjectNode resultsNode = jacksonMapper.createObjectNode();
		resultsNode.put( "scriptHost", Application.getHOST_NAME() );
		ArrayNode hostNode = resultsNode.putArray( "scriptOutput" );

		logger.info( "scriptName: {}, chownUserid: {} ,  chownUserid: {} , hosts: {}",
			scriptName, chownUserid, chownUserid, hostList );

		File scriptDir = csapApp.getScriptDir();
		if ( !scriptDir.exists() ) {
			logger.info( "Making: " + scriptDir.getAbsolutePath() );
			scriptDir.mkdirs();
		}

		File targetFile = new File( scriptDir, scriptName );

		if ( targetFile.exists() ) {
			logger.info( "Deleting" + targetFile.getAbsolutePath() );
			targetFile.delete();
			hostNode.add( "== Deleting existing script of same name: "
					+ targetFile.getAbsolutePath() );
		}

		hostNode.add( "\n == Script Output: " + outputFm.getOutputFile().getAbsolutePath() + "\n\n" );

		csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/execute", apiUser, targetFile.getName(),
			"Executing : script  as user " + chownUserid
					+ " hosts: " + hostList
					+ " time out seconds: " + timeoutSeconds
					+ "Script stored to: " + targetFile.getAbsolutePath() + ":\n" + contents );

		if ( !targetFile.exists() ) {
			try (FileWriter fstream = new FileWriter( targetFile );
					BufferedWriter out = new BufferedWriter( fstream );) {
				// Create file

				out.write( contents );

			} catch (Exception e) {
				hostNode.add( "ERROR: failed to createfile due to: " + e.getMessage() );
				;
			}

			hostNode.add( Application.getHOST_NAME() + ":" + " Script copied" );
		} else {
			hostNode.add( "ERROR: Script file still exists" );
		}

		// List<String> hostList = csapApp.getMutableHostsInActivePackage(
		// clusterName );
		// if ( hostList == null || hostList.size() == 0 ) {
		// hostList = new ArrayList<String>() ;
		// hostList.add(Application.getHOST_NAME() ); // invoke only on
		// // current VM
		// }
		// Script is transferred and executed on all VMs
		resultsNode.set( "otherHosts",
			zipAndTransfer( apiUser, timeoutSeconds, hostList,
				targetFile.getAbsolutePath(), CSAP.SAME_LOCATION, chownUserid, outputFm, null ) );

		return resultsNode;
	}

	/**
	 *
	 * Will zip and tar
	 *
	 * @param timeOutSeconds
	 * @param hostList
	 * @param locationToZip
	 * @param extractDir
	 * @param chownUserid
	 * @param auditUser
	 * @param outputFm
	 * @return
	 * @throws IOException
	 */
	public ArrayNode zipAndTransfer (	String apiUser,
										int timeOutSeconds, List<String> hostList, String locationToZip,
										String extractDir,
										String chownUserid, OutputFileMgr outputFm, String deleteExisting )
			throws IOException {

		logger.debug( "locationToZip: {}, extractDir: {}, chownUserid: {} , hosts: {}",
			locationToZip, extractDir, chownUserid, Arrays.asList( hostList )
				.toString() );

		ArrayNode resultNode = jacksonMapper.createArrayNode();

		if ( hostList == null || hostList.size() == 0 ) {
			return resultNode;
		}
		// return "No Additional Synchronization required";
		// logger.info("locationToZip" + locationToZip);

		TransferManager transferManager = new TransferManager( csapApp, timeOutSeconds, outputFm.getBufferedWriter() );

		if ( deleteExisting != null ) {
			transferManager.setDeleteExisting( true );
		}

		File zipLocation = new File( locationToZip );

		File targetFolder = new File( extractDir );
		if ( extractDir.equalsIgnoreCase( CSAP.SAME_LOCATION ) ) {
			targetFolder = zipLocation;
			if ( zipLocation.isFile() ) {
				// Hook when just a single file is being transferred
				targetFolder = zipLocation.getParentFile();
			}
		}

		String result = "Specified Location does not exist: " + locationToZip + " on host: "
				+ Application.getHOST_NAME();
		if ( zipLocation.exists() ) {

			transferManager.httpCopyViaCsAgent(
				apiUser,
				zipLocation,
				Application.filePathAllOs( targetFolder ),
				hostList,
				chownUserid );

			resultNode = transferManager.waitForCompleteJson();

		} else {
			logger.error( result );
		}

		return resultNode;
	}

	public String systemStatus () {
		String[] lines = {
				"#!/bin/bash",
				"echo systemctl status",
				"systemctl status --no-pager --full",
				"echo systemctl list-units",
				"systemctl list-units --type=service --no-pager",
				"" };

		String scriptOutput = "Failed to run";

		try {
			scriptOutput = osCommandRunner.runUsingRootUser( "OsManagerProcessTree", lines );
			logger.debug( "output from: {}  , \n{}", lines[1], scriptOutput );
		} catch (IOException e) {
			logger.info( "Failed to run docker nsenter: {} , \n reason: {}", lines,
				CSAP.getCsapFilteredStackTrace( e ) );
			scriptOutput += ", reason: " + e.getMessage() + " type: " +
					e.getClass().getName();
		}

		if ( Application.isRunningOnDesktop() ) {
			scriptOutput = Application.getContents( new File( getClass()
				.getResource( "/linux/systemctl.txt" ).getFile() ) );
		}

		return scriptOutput;
	}

	public String getProcessTree ( String pid, String serviceName ) {

		String[] lines = {
				"#!/bin/bash",
				// "echo;echo;echo;echo;echo === Service: " + serviceName + "
				// pid: " + pid + " ===; echo;",
				// "pstree -sl " + pid,

				"echo;echo;echo;echo;echo === pids ===; echo;",
				"pstree -slp " + pid + " | head -1",

				"echo;echo;echo;echo;echo === with arguments ===; echo;",
				"pstree -sla " + pid,

				"echo;echo;echo;echo;echo ===  full tree ===; echo;",
				"pstree",
				"" };

		String scriptOutput = "Failed to run";

		try {
			scriptOutput = osCommandRunner.runUsingRootUser( "OsManagerProcessTree", lines );
			logger.debug( "output from: {}  , \n{}", lines[1], scriptOutput );
		} catch (IOException e) {
			logger.info( "Failed to run docker nsenter: {} , \n reason: {}", lines,
				CSAP.getCsapFilteredStackTrace( e ) );
			scriptOutput += ", reason: " + e.getMessage() + " type: " +
					e.getClass().getName();
		}
		return scriptOutput;
	}
}
