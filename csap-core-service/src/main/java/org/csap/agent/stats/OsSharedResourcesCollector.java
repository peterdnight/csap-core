package org.csap.agent.stats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.csap.agent.CSAP;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.Application;
import org.csap.agent.services.OsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings ( "restriction" )
public class OsSharedResourcesCollector extends HostCollector implements Runnable {

	final Logger logger = LoggerFactory.getLogger( OsSharedResourcesCollector.class );

	private int[] openFiles;
	private int[] totalThreads;
	private int[] csapThreads;
	private int[] totalFileDescriptors;
	private int[] csapFileDescriptors;
	private int[] networkConns;
	private int[] networkWait;

	private int[] usrCpuLevel;
	private int[] memLevel;
	private int[] bufLevel;
	private int[] sysCpuLevel;
	private int[] ioLevel;
	private double[] loadLevel;
	

	private double[] cpuTestTime;
	private double[] diskTestTime;

	private int[] ioReads;
	private int[] ioWrites;
	
	private long[] collectionMs;

	private int lastAddedElementIndex = 0;

	public int getLastAddedElementIndex () {
		return lastAddedElementIndex;
	}

	public void setLastAddedElementIndex ( int lastAddedElementIndex ) {
		this.lastAddedElementIndex = lastAddedElementIndex;
	}

	private String command = "";

	private String uploadUrl = null;

	public String getUploadUrl () {
		return uploadUrl;
	}

	public OsSharedResourcesCollector( Application manager, OsManager osManager,
			int intervalSeconds, boolean publishSummary ) {

		super( manager, osManager, intervalSeconds, publishSummary );

		openFiles = new int[ getInMemoryCacheSize() ];
		totalThreads = new int[ getInMemoryCacheSize() ];
		csapThreads = new int[ getInMemoryCacheSize() ];
		totalFileDescriptors = new int[ getInMemoryCacheSize() ];
		csapFileDescriptors = new int[ getInMemoryCacheSize() ];
		networkConns = new int[ getInMemoryCacheSize() ];
		networkWait = new int[ getInMemoryCacheSize() ];

		usrCpuLevel = new int[ getInMemoryCacheSize() ];
		memLevel = new int[ getInMemoryCacheSize() ];
		bufLevel = new int[ getInMemoryCacheSize() ];
		sysCpuLevel = new int[ getInMemoryCacheSize() ];
		ioLevel = new int[ getInMemoryCacheSize() ];
		loadLevel = new double[ getInMemoryCacheSize() ];
		

		cpuTestTime = new double[ getInMemoryCacheSize() ];
		diskTestTime = new double[ getInMemoryCacheSize() ];
		

		ioReads = new int[ getInMemoryCacheSize() ];
		ioWrites = new int[ getInMemoryCacheSize() ];
		
		collectionMs = new long[ getInMemoryCacheSize() ];

		this.command = "mpstat " + intervalSeconds;
		// this.uploadUrl = uploadUrl;

		// Initialize values so that UI can display without waiting for first
		// interval to occur
		openFiles[0] = -1;
		totalThreads[0] = -1;
		csapThreads[0] = -1;
		totalFileDescriptors[0] = -1;
		csapFileDescriptors[0] = -1;

		networkConns[0] = -1;
		networkWait[0] = -1;

		usrCpuLevel[0] = -1;
		memLevel[0] = -1;
		bufLevel[0] = -1;
		sysCpuLevel[0] = -1;
		ioLevel[0] = -1;
		loadLevel[0] = 0;
		
		diskTestTime[0] = 0;
		cpuTestTime[0] = 0;

		ioReads[0] = 0;
		ioWrites[0] = 0;
		
		collectionMs[0] = new Date().getTime();

		setLastAddedElementIndex( 0 );

		// logger.warn("Construction");
		// hack for for windows
		if ( Application.isRunningOnDesktop() ) {
			buildTestData( osManager, intervalSeconds );
		}

		mpstatThread = new Thread( this );
		mpstatThread.start();
	}

	Thread mpstatThread;

	public void buildTestData ( OsManager osManager, int intervalSeconds ) {
		logger.info( "Inserting dummy data for windows." );

		long currMs = System.currentTimeMillis();
		int i = 0;
		Random rg = new Random( System.currentTimeMillis() );
		while (i < getInMemoryCacheSize()) {
			usrCpuLevel[i] = rg.nextInt( 100 );
			// memLevel[i] = rg.nextInt(2000);
			// bufLevel[i] = rg.nextInt(8000);
			try {
				openFiles[i] = osManager.getHostSummaryItem( "openFiles" );
				totalThreads[i] = osManager.getHostSummaryItem( "totalThreads" );
				csapThreads[i] = osManager.getHostSummaryItem( "csapThreads" );
				totalFileDescriptors[i] = osManager.getHostSummaryItem( "totalFileDescriptors" );
				csapFileDescriptors[i] = osManager.getHostSummaryItem( "csapFileDescriptors" );
				networkConns[i] = osManager.getHostSummaryItem( "networkConns" );
				networkWait[i] = osManager.getHostSummaryItem( "networkWait" );

				memLevel[i] = osManager.getMemoryCacheSize();

				bufLevel[i] = osManager.getMemoryAvailbleLessCache();
			} catch (Exception e) {
				logger.error( "Failed parsing memory json: \n"
						+ osManager.getMemoryStatistics(),
					e );
				memLevel[i] = -1;
				bufLevel[i] = -1;
				openFiles[i] = -1;
				totalThreads[i] = -1;
				csapThreads[i] = -1;
				totalFileDescriptors[i] = -1;
				csapFileDescriptors[i] = -1;
				networkConns[i] = -1;
				networkWait[i] = -1;
			}

			cpuTestTime[i] = rg.nextInt( 6 );
			diskTestTime[i] = rg.nextInt( 3 );
			

			ioReads[i] = rg.nextInt( 3 );
			ioWrites[i] = rg.nextInt( 3 );
			
			sysCpuLevel[i] = rg.nextInt( 100 );
			ioLevel[i] = rg.nextInt( 100 );
			loadLevel[i] = Double
				.valueOf( decimalFormat2Places.format( rg.nextDouble() * 4 ) );
			collectionMs[i] = currMs
					- ((getInMemoryCacheSize() - i) * intervalSeconds * 1000);
			i++;
		}
		lastAddedElementIndex = getInMemoryCacheSize() - 1;
	}

	/**
	 * Kill off the spawned threads - triggered from ServiceRequests
	 */
	public void shutdown () {

		logger.debug( "*************** Shutting down  **********************" );
		if ( mpstatLinuxProcess != null ) {
			mpstatLinuxProcess.destroy();
		}

		super.shutdown();

		if ( mpstatThread != null ) {
			mpstatThread.interrupt();
		}
	}

	Process mpstatLinuxProcess = null;

	@Override
	public void run () {

		// runs continuously using MPstat output to control gathering.
		Thread.currentThread().setName( "Csap" +
				this.getClass().getSimpleName() + "_" + collectionIntervalSeconds );

		try {

			// Spread out events
			Thread.sleep( 10000 );
			if ( this.collectionIntervalSeconds >= 60 ) {
				Thread.sleep( (30 + rg.nextInt( 30 )) * 1000 );
			}
		} catch (InterruptedException e1) {

		}

		try {

			List<String> linuxCommandString;
			linuxCommandString = Arrays.asList( "bash", "-c", command );

			while (isKeepRunning()) {
				logger.warn( "Launching:  " + command );
				runLinuxMpStatAndMonitorOutput( linuxCommandString );
			}
		} catch (Exception e) {
			logger.error( "Exception in processing", e );
		}

	}

	final com.sun.management.OperatingSystemMXBean osStats = (com.sun.management.OperatingSystemMXBean) ManagementFactory
		.getOperatingSystemMXBean();

	DecimalFormat decimalFormat2Places = new DecimalFormat( "###.##" );

	int allowPsDebug = 0;

	private String runLinuxMpStatAndMonitorOutput ( List<String> params ) {

		ProcessBuilder processBuilder = new ProcessBuilder( params );
		processBuilder.redirectErrorStream( true );

		mpstatLinuxProcess = null;
		String result = "Result from executing: ";
		BufferedReader stdOutAndErrReader = null; // stdout and error combined
		InputStreamReader isReader = null;

		try {
			// very important to pick up paths from parent!!
			// logger.info( "intervalSeconds " + intervalSeconds + " spawning: "
			// + params.get(0));

			if ( !Application.isRunningOnDesktop() ) {
				mpstatLinuxProcess = processBuilder.start();
				isReader = new InputStreamReader(
					mpstatLinuxProcess.getInputStream() );
				stdOutAndErrReader = new BufferedReader( isReader );
			}

			while (isKeepRunning()
					&& (mpstatLinuxProcess == null || mpstatLinuxProcess.isAlive())) {

				collectOsResources( stdOutAndErrReader );
			}
			logger.debug( "Done {}", params.get( 0 ) );
		} catch (Exception e) {
			if ( !isKeepRunning() ) {
				logger.warn( "Shutdown in progress" );
			} else {
				logger.error(
					"This should never happen, maybe someone killed the mpstat",
					e );
			}
		} finally {

			if ( isReader != null ) {
				try {
					isReader.close();
				} catch (IOException e) {
					logger.error( "failed closing reader", e );
				}
			}
			if ( mpstatLinuxProcess != null ) {
				mpstatLinuxProcess.destroy();
			}
			if ( stdOutAndErrReader != null ) {
				try {
					stdOutAndErrReader.close();
				} catch (IOException e) {
					logger.error( "failed closing reader", e );
				}
			}
		}

		logger.error( "\n\n ===================================== MpStat Thread is exiting. \n\n" );

		return result;

	}

	private void collectOsResources ( BufferedReader stdOutAndErrReader )
			throws InterruptedException, NumberFormatException, IOException {
		String mpstatOutputLine;
		if ( Application.isRunningOnDesktop() ) {
			mpstatOutputLine = "09:24:22 AM  all    50.35    0.00    30.35    5.06    0.00    0.05    0.00    0.00   98.19";
			Thread.sleep( collectionIntervalSeconds * 1000 );
		} else {

			// Put thread to sleep unless command output is available
			while (isKeepRunning() &&
					mpstatLinuxProcess.isAlive() && !stdOutAndErrReader.ready()) {
				try {
					// Only do work if needed

					logger.debug( "Sleeping for 1 seconds until next top output is available" );

					Thread.sleep( 1000 );

				} catch (InterruptedException e1) {
					logger.error( "Got interuption while sleeping on top output" );
				}
			}
			mpstatOutputLine = stdOutAndErrReader.readLine();
			if ( mpstatOutputLine == null ) {
				logger.error( "Null output from mpstat, did it crash? " );
			}

		}

		logger.debug( "mpstat line: {}", mpstatOutputLine );
		// logger.info( "Got a line for: " + intervalSeconds +
		// " lastAddedElementIndex: " + lastAddedElementIndex);
		int allTokenIndex = mpstatOutputLine.indexOf( "all" );
		if ( allTokenIndex != -1 ) {
			// item has been read in.
			// Timestamp can mess with columns - so substring to strip
			// off
			// 12:56:41 all 0.15 0.00 0.00 1.10 0.00 0.05 0.00 98.70
			// 1057.77
			// 12:56:41 PM all 0.15 0.00 0.00 1.10 0.00 0.05 0.00 98.70
			// 1057.77
			String[] mpStatColumns = mpstatOutputLine
				.substring( allTokenIndex + 3 )
				.trim()
				.split( " +" );
			// logger.info(" mpstat line: " +
			// mpstatOutputLine.substring(allTokenIndex+3).trim() ) ;
			int nextIndex = getLastAddedElementIndex() + 1;
			if ( nextIndex >= getInMemoryCacheSize() ) {
				nextIndex = 0; // wrap the array
			}
			try {

				collectionMs[nextIndex] = new Date().getTime();

				usrCpuLevel[nextIndex] = Math.round( Float
					.parseFloat( mpStatColumns[0] ) );
				sysCpuLevel[nextIndex] = Math.round( Float
					.parseFloat( mpStatColumns[2] ) );
				ioLevel[nextIndex] = Math.round( Float
					.parseFloat( mpStatColumns[3] ) );
				// collectionMs[ nextIndex ] = System.currentTimeMillis() ;

				openFiles[nextIndex] = osManager.getHostSummaryItem( "openFiles" );
				totalThreads[nextIndex] = osManager.getHostSummaryItem( "totalThreads" );
				csapThreads[nextIndex] = osManager.getHostSummaryItem( "csapThreads" );
				totalFileDescriptors[nextIndex] = osManager.getHostSummaryItem( "totalFileDescriptors" );
				csapFileDescriptors[nextIndex] = osManager.getHostSummaryItem( "csapFileDescriptors" );
				networkConns[nextIndex] = osManager.getHostSummaryItem( "networkConns" );
				networkWait[nextIndex] = osManager.getHostSummaryItem( "networkWait" );

				diskTestTime[nextIndex] = Double.valueOf( decimalFormat2Places
					.format( osManager.getInfraRunner().getLastDiskTimeInMs() ) );
				cpuTestTime[nextIndex] = Double.valueOf( decimalFormat2Places
					.format( osManager.getInfraRunner().getLastCpuTimeInMs() ) );
				
				buildIoStatDeltaReport( nextIndex );

				// aggregate memory
				memLevel[nextIndex] = osManager.getMemoryAvailbleLessCache();

				checkForLowMemoryEventGeneration( nextIndex );
				// + osManager.getMem().get("ram").get(5).asInt()
				// + osManager.getMem().get("ram").get(3)
				// .asInt();
				bufLevel[nextIndex] = osManager.getMemoryCacheSize();
				loadLevel[nextIndex] = Double.valueOf( decimalFormat2Places
					.format( osStats.getSystemLoadAverage() ) );
			} catch (Exception e) {
				logger.error( "Failed parsing OS stats: {}", 
					CSAP.getCsapFilteredStackTrace( e ) );
				memLevel[nextIndex] = -1;
				bufLevel[nextIndex] = -1;
				openFiles[nextIndex] = -1;
				totalThreads[nextIndex] = -1;
				csapThreads[nextIndex] = -1;
				totalFileDescriptors[nextIndex] = -1;
				csapFileDescriptors[nextIndex] = -1;
				networkConns[nextIndex] = -1;
				networkWait[nextIndex] = -1;
				diskTestTime[nextIndex] = 0 ;
				cpuTestTime[nextIndex] = 0 ;

				ioReads[nextIndex] = 0 ;
				ioWrites[nextIndex] = 0 ;
			}

			setLastAddedElementIndex( nextIndex );

			peformUploadIfNeeded();

			logger.debug( "Got result, then blocking on next output: {}", mpstatOutputLine );
			// Thread.currentThread().sleep( intervalSeconds * 1000 ) ;
		}
	}


	/**
	 *  Special case: use iostat deltas to determine reads and writes across all disks in 
	 *  collection interval
	 */
	private ObjectNode previousDiskActivity=null;
	private void buildIoStatDeltaReport ( int nextIndex ) {
		// DeltaReports for disk read and writes
		int ioReadChange=0;
		int ioWriteChange=0 ;
		try {
			ObjectNode latestDiskActivity = osManager.diskActivity() ;
			if ( previousDiskActivity != null) {
				ioReadChange = latestDiskActivity.get( "totalDiskReadMb" ).asInt()
						- previousDiskActivity.get( "totalDiskReadMb" ).asInt();
				ioWriteChange = latestDiskActivity.get( "totalDiskWriteMb" ).asInt()
						- previousDiskActivity.get( "totalDiskWriteMb" ).asInt();
			} 
			previousDiskActivity=latestDiskActivity;
			ioReads[ nextIndex ] = ioReadChange ;
			ioWrites[ nextIndex ] = ioWriteChange ;
		} catch (Exception e) {
			logger.error( "Failed parsing iostat report: {}", 
				CSAP.getCsapFilteredStackTrace( e ) );
		}
	}

	private void checkForLowMemoryEventGeneration ( int nextIndex ) {
		// selectively enabled
		if ( collectionIntervalSeconds == csapApplication
			.lifeCycleSettings()
			.getPsDumpInterval()
				&& allowPsDebug < csapApplication
					.lifeCycleSettings()
					.getPsDumpCount()
				&& memLevel[nextIndex] < csapApplication
					.lifeCycleSettings()
					.getPsDumpLowMemoryInMb() ) {
			// logger.warn("Low memory" + memLevel[nextIndex]);
			allowPsDebug++;

			String psOutput = osManager.performMemoryProcessList( false, false, true );

			ObjectNode memoryWarning = jacksonMapper.createObjectNode();
			memoryWarning.put( "currentFree", memLevel[nextIndex] );
			memoryWarning.put( "configuredMinimum", csapApplication
				.lifeCycleSettings()
				.getPsDumpLowMemoryInMb() );
			memoryWarning.put( "csapText", psOutput );

			csapApplication.getEventClient().publishEvent(
				CsapEventClient.CSAP_SYSTEM_CATEGORY + "/memory/low",
				memLevel[nextIndex] + " Remaining", null, memoryWarning );

			logger.warn( "Low memory detected on VM, refer to events log: " + memLevel[nextIndex] + " MB remaining." );

		}
	}

	public final static String VM_METRICS_EVENT = METRICS_EVENT + "/host/";

	protected String uploadMetrics ( int iterationsBetweenUploads ) {

		String result = "FAILED";

		try {
			allowPsDebug = 0;

			ObjectNode metricSample = getCSVdata( true, null, iterationsBetweenUploads, 0 );

			// new Event publisher - it checks if publish is enabled. First time
			// full attributes, then only sub attributes
			if ( vmCorrellationAttributes == null ) {
				vmCorrellationAttributes = jacksonMapper.createObjectNode();
				vmCorrellationAttributes.put( "id", "resource_" + collectionIntervalSeconds );
				vmCorrellationAttributes.put( "source", VM_METRICS_EVENT + collectionIntervalSeconds );
				vmCorrellationAttributes.put( "hostName", Application.getHOST_NAME() );

				// full upload. We could make call to event service to see if
				// they match...for now we do on restarts
				logger.info( "Uploading VM attributes, count: {}", iterationsBetweenUploads );

				csapApplication.getEventClient().publishEvent( VM_METRICS_EVENT + collectionIntervalSeconds + "/attributes", "Modified",
					null,
					attributeJson );
			}

			// Send normalized data
			metricSample.set( "attributes", vmCorrellationAttributes );

			csapApplication.getEventClient().publishEvent( VM_METRICS_EVENT + collectionIntervalSeconds + "/data", "Upload", null,
				metricSample );

			publishSummaryReport( "host" );

		} catch (Exception e) {
			logger.error( "Failed upload", e );
			result = "Failed, Exception:"
					+ CSAP.getCsapFilteredStackTrace( e );
		}

		return result;
	}

	private ObjectNode vmCorrellationAttributes = null;

	public String getUsrCpuLevel () {
		// logger.info("currIndex:" + lastAddedElementIndex) ;
		return Integer.toString( usrCpuLevel[getLastAddedElementIndex()] );
	}

	public String getSysCpuLevel () {
		// logger.info("currIndex:" + lastAddedElementIndex) ;
		return Integer.toString( sysCpuLevel[getLastAddedElementIndex()] );
	}

	public int getLatestCpu () {
		return usrCpuLevel[getLastAddedElementIndex()] + sysCpuLevel[getLastAddedElementIndex()];
	}

	public int getLatestIoWait () {
		return ioLevel[getLastAddedElementIndex()];
	}

	public void clear () {

		setLastAddedElementIndex( 0 );
		for ( int i = 0; i < collectionMs.length; i++ ) {
			collectionMs[i] = 0;
		}
	}

	// serviceName array is a dummy param so interface is consistent
	public ObjectNode getCSVdata (	boolean isUpdateSummary, String[] serviceNameArrayNotUsed,
									int requestedSampleSize, int skipFirstItems, String... customArgs ) {

		// logger.info("numSamples: " + numSamples + " skipFirstItems: " +
		// skipFirstItems ) ;
		ObjectNode rootNode = jacksonMapper.createObjectNode();

		rootNode.set( "attributes", generateAttributes( requestedSampleSize, skipFirstItems ) );

		ObjectNode summaryNode = jacksonMapper.createObjectNode();

		summaryNode.put( "cpuCountAvg", osStats.getAvailableProcessors() );
		try {
			summaryNode.put( "memoryInMbAvg", osStats.getTotalPhysicalMemorySize() / 1024 / 1024 );
			summaryNode.put( "swapInMbAvg", osStats.getTotalSwapSpaceSize() / 1024 / 1024 );
		} catch (Exception e) {
			summaryNode.put( "memoryInMbAvg", -1 );
			summaryNode.put( "swapInMbAvg", -1 );
		}
		summaryNode.put( "totActivity", eventCount() );

		ObjectNode dataNode = rootNode.putObject( "data" );

		ArrayNode timeStampArray = dataNode.putArray( "timeStamp" );
		ArrayNode usrArray = dataNode.putArray( "usrCpu" );
		ArrayNode memArray = dataNode.putArray( "memFree" );
		ArrayNode bufArray = dataNode.putArray( "bufFree" );
		ArrayNode sysArray = dataNode.putArray( "sysCpu" );
		ArrayNode ioArray = dataNode.putArray( "IO" );
		ArrayNode loadArray = dataNode.putArray( "load" );

		ArrayNode openFilesArray = dataNode.putArray( "openFiles" );
		ArrayNode totalThreadsArray = dataNode.putArray( "totalThreads" );
		ArrayNode csapThreadsArray = dataNode.putArray( "csapThreads" );
		ArrayNode totalFileDescriptorsArray = dataNode.putArray( "totalFileDescriptors" );
		ArrayNode csapFileDescriptorsArray = dataNode.putArray( "csapFileDescriptors" );
		ArrayNode networkConnsArray = dataNode.putArray( "networkConns" );
		ArrayNode networkWaitArray = dataNode.putArray( "networkWait" );
		

		ArrayNode diskTestArray = dataNode.putArray( "diskTest" );
		ArrayNode cpuTestArray = dataNode.putArray( "cpuTest" );
		

		ArrayNode ioReadArray = dataNode.putArray( "ioReads" );
		ArrayNode ioWriteArray = dataNode.putArray( "ioWrites" );

		int i = getLastAddedElementIndex();
		boolean wrapped = false;

		int curIndex = 0;
		int curSampleCount = 0;

		if ( requestedSampleSize == -1 ) {
			curSampleCount = -999;
		}

		int totalUsrCpu = 0;
		int totalSysCpu = 0;
		int totalMemFree = 0;
		int totalBufFree = 0;
		int totalIo = 0;
		int alertsCount = 0;
		double totalLoad = 0;
		int totalFiles = 0;
		int threadsTotal = 0;
		int csapThreadsTotal = 0;
		int fdTotal = 0;
		int csapFdTotal = 0;
		int socketTotal = 0;
		int socketWaitTotal = 0;

		double totalDiskTestTime=0;
		double totalCpuTestTime=0;

		int totalIoReads = 0;
		int totalIoWrites = 0;
		
		int numberOfSamples = 0;

		while (collectionMs[i] <= collectionMs[getLastAddedElementIndex()]
				&& collectionMs[i] != 0 && curSampleCount < requestedSampleSize) {

			// logger.info("Adding Element " + i + " collectionMs[i] " +
			// collectionMs[i] ) ;
			if ( curIndex++ >= skipFirstItems ) {
				if ( requestedSampleSize != -1 ) {
					curSampleCount++;
				}

				// long offset = collectionMs[i]-timeZoneMinutesFromGmt*60000 ;
				numberOfSamples++;
				timeStampArray.add( Long.toString( collectionMs[i] ) );

				usrArray.add( usrCpuLevel[i] );
				totalUsrCpu += usrCpuLevel[i];

				sysArray.add( sysCpuLevel[i] );
				totalSysCpu += sysCpuLevel[i];

				// High CPU or load means we should look very closely
				if ( (usrCpuLevel[i] + sysCpuLevel[i]) > 60 || loadLevel[i] > osStats.getAvailableProcessors() ) {
					alertsCount++;
				}

				memArray.add( memLevel[i] );
				totalMemFree += memLevel[i];

				bufArray.add( bufLevel[i] );
				totalBufFree += bufLevel[i];

				ioArray.add( ioLevel[i] );
				totalIo += ioLevel[i];

				loadArray.add( loadLevel[i] );
				totalLoad += loadLevel[i];
				if ( loadLevel[i] == -1 ) {
					totalLoad += 1;
				}

				openFilesArray.add( openFiles[i] );
				totalFiles += openFiles[i];

				totalThreadsArray.add( totalThreads[i] );
				threadsTotal += totalThreads[i];

				csapThreadsArray.add( csapThreads[i] );
				csapThreadsTotal += csapThreads[i];

				totalFileDescriptorsArray.add( totalFileDescriptors[i] );
				fdTotal += totalFileDescriptors[i];

				csapFileDescriptorsArray.add( csapFileDescriptors[i] );
				csapFdTotal += csapFileDescriptors[i];

				networkConnsArray.add( networkConns[i] );
				socketTotal += networkConns[i];
				
				networkWaitArray.add( networkWait[i] );
				socketWaitTotal += networkWait[i];

				diskTestArray.add( diskTestTime[i] );
				totalDiskTestTime += diskTestTime[i];
				
				cpuTestArray.add( cpuTestTime[i] );
				totalCpuTestTime += cpuTestTime[i];
				
				ioReadArray.add( ioReads[i] );
				totalIoReads += ioReads[i]; 
				
				ioWriteArray.add( ioWrites[i] );
				totalIoWrites += ioWrites[i];

				
				

				// logger.debug(resultAL.get( resultAL.size()-1) );
			}

			i--;
			if ( i < 0 ) {
				wrapped = true;
				i = getInMemoryCacheSize() - 1;
			}
			// only go around once
			if ( wrapped && (i == getLastAddedElementIndex()) ) {
				break;
			}
		}

		summaryNode.put( "numberOfSamples", numberOfSamples );
		summaryNode.put( "totalUsrCpu", totalUsrCpu );
		summaryNode.put( "totalSysCpu", totalSysCpu );
		summaryNode.put( "totalMemFree", totalMemFree );
		summaryNode.put( "totalBufFree", totalBufFree );
		summaryNode.put( "totalIo", totalIo );
		summaryNode.put( "alertsCount", alertsCount );
		summaryNode.put( "totalLoad", totalLoad );
		summaryNode.put( "totalFiles", totalFiles );
		summaryNode.put( "threadsTotal", threadsTotal );
		summaryNode.put( "csapThreadsTotal", csapThreadsTotal );
		summaryNode.put( "fdTotal", fdTotal );
		summaryNode.put( "csapFdTotal", csapFdTotal );
		summaryNode.put( "socketTotal", socketTotal );
		summaryNode.put( "socketWaitTotal", socketWaitTotal );

		summaryNode.put( "totalDiskTestTime", totalDiskTestTime );
		summaryNode.put( "totalCpuTestTime", totalCpuTestTime );
		
		summaryNode.put( "totalIoReads", totalIoReads );
		summaryNode.put( "totalIoWrites", totalIoWrites );

		addSummary( summaryNode, isUpdateSummary );

		return rootNode;
	}

	/**
	 * Not using remote calls. Might be worth it if additional activities are
	 * published outside of agent
	 */
	private int lastCount = 0;

	public int eventCount () {
		int count = getCsapApplication().getEventClient().getNumberPublished() - lastCount;
		lastCount = getCsapApplication().getEventClient().getNumberPublished();
		return count;
	}

	private ObjectNode attributeJson = null;

	private ObjectNode generateAttributes (	int requestedSampleSize,
											int skipFirstItems ) {

		if ( attributeJson != null ) {
			// return attributeJson;
		}

		attributeJson = jacksonMapper.createObjectNode();

		attributeJson.put( "id", "resource_" + collectionIntervalSeconds );
		attributeJson.put( "hostName", Application.getHOST_NAME() );
		attributeJson.put( "metricName", "System Resource" );
		attributeJson.put( "timezone", TIME_ZONE_OFFSET );
		attributeJson.put( "description",
			"Contains usr,sys,io, and load level metrics" );
		attributeJson.put( "sampleInterval", collectionIntervalSeconds );
		attributeJson.put( "samplesRequested", requestedSampleSize );
		attributeJson.put( "samplesOffset", skipFirstItems );
		attributeJson.put( "currentTimeMillis", System.currentTimeMillis() );
		attributeJson.put( "cpuCount", osStats.getAvailableProcessors() );

		ObjectNode titlesObject = attributeJson.putObject( "titles" );

		ObjectNode graphsObject = attributeJson.putObject( "graphs" );

		titlesObject.put( "OS_MpStat", "Linux mpstat" );
		ObjectNode resourceGraph = graphsObject.putObject( "OS_MpStat" );
		resourceGraph.put( "usrCpu", "User CPU" );
		resourceGraph.put( "sysCpu", "System CPU" );
		resourceGraph.put( "IO", "Input Output" );

		titlesObject.put( "OS_Load", "CPU Load" );
		ObjectNode loadGraph = graphsObject.putObject( "OS_Load" );
		loadGraph.put( "load", "Load" );
		loadGraph.put( "attributes_cpuCount", "Cpu Count" );

		titlesObject.put( "InfraTest", "Infrastructure Tests (seconds)" );
		ObjectNode infraGraph = graphsObject.putObject( "InfraTest" );
		infraGraph.put( "diskTest", "Disk Test" );
		infraGraph.put( "cpuTest", "CPU Test" );
		

		titlesObject.put( "iostat", "Disk Total iostat (MB)" );
		ObjectNode ioGraph = graphsObject.putObject( "iostat" );
		ioGraph.put( "ioReads", "Disk Reads" );
		ioGraph.put( "ioWrites", "Disk Writes" );
		
		titlesObject.put( "VmFiles", "Files Open" );
		ObjectNode vmGraph = graphsObject.putObject( "VmFiles" );
		vmGraph.put( "openFiles", "/proc/sys/fs/file-nr" );
		vmGraph.put( "totalFileDescriptors", "lsof - users" );
		vmGraph.put( "csapFileDescriptors", "lsof - " + System.getenv( "USER" ) );

		titlesObject.put( "Network", "Network Activity" );
		ObjectNode socketGraph = graphsObject.putObject( "Network" );
		socketGraph.put( "networkConns", "Sockets Active" );
		socketGraph.put( "networkWait", "Sockets Wait" );

		titlesObject.put( "VmThreads", "Threads" );
		ObjectNode threadGraph = graphsObject.putObject( "VmThreads" );
		threadGraph.put( "totalThreads", "ALL" );
		threadGraph.put( "csapThreads", System.getenv( "USER" ) );

		titlesObject.put( "Memory_Remaining", "Memory <span class='highlight'>Available</span> (Mb)" );
		ObjectNode memGraph = graphsObject.putObject( "Memory_Remaining" );
		memGraph.put( "memFree", "Memory Aggregate" );
		memGraph.put( "bufFree", "Buffer Cache" );
		

		return attributeJson;
	}

	// Simple Object Map
	@Override
	protected JsonNode buildSummaryReport ( boolean isSecondary ) {

		// Step 1 - build map with total for services
		ObjectNode summaryTotalJson = jacksonMapper.createObjectNode();

		ArrayNode cache = summary24HourCache;
		if ( isSecondary ) {
			cache = summary24HourApplicationCache;
		}

		for ( JsonNode intervalReport : cache ) {
			Iterator<String> fields = intervalReport.fieldNames();
			while (fields.hasNext()) {

				String field = fields.next();

				addItemToTotals( (ObjectNode) intervalReport, summaryTotalJson, field );

			}

		}

		return summaryTotalJson;
	}
}
