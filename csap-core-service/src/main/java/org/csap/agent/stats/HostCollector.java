package org.csap.agent.stats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.util.Iterator;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.Application;
import org.csap.agent.services.OsManager;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.javasimon.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HostCollector {

	final Logger logger = LoggerFactory.getLogger(HostCollector.class);

	ObjectMapper jacksonMapper = new ObjectMapper();

	final private int inMemoryCacheSize ;

	public int getInMemoryCacheSize () {
		return inMemoryCacheSize;
	}

	public static String ALL_SERVICES = "all" ;
	
	protected int publicationInterval;

	protected Application csapApplication = null;

	public Application getCsapApplication() {
		return csapApplication;
	}

	// convert to minutes
	public final static int TIME_ZONE_OFFSET = TimeZone.getDefault().getRawOffset() / 1000 / 60 / 60;

	public void setManager(Application app) {
		this.csapApplication = app;
	}

	protected OsManager osManager;

	protected int collectionIntervalSeconds;

	public HostCollector(Application manager, OsManager osManager,
			int intervalSeconds, boolean publishSummaryAndPerformHeartBeat) {
		
		this.inMemoryCacheSize = manager.lifeCycleSettings().getInMemoryCacheSize() ;

		this.collectionIntervalSeconds = intervalSeconds;
		this.csapApplication = manager;
		this.osManager = osManager;
		this.publishSummaryAndPerformHeartBeat = publishSummaryAndPerformHeartBeat;
		this.publicationInterval = manager.lifeCycleSettings().getMetricsUploadSeconds(intervalSeconds);
		this.SIZE_OF_REPORT_CACHE = 24 * 60 * 60 / publicationInterval;
		double pubHours = publicationInterval/3600.0 ;
		double collectMinutes = intervalSeconds/60.0 ;

		logger.info("Collector: {}, collection: {} minutes, publication: {} hours",
		            this.getClass().getSimpleName(), collectMinutes , pubHours );

	}

	public abstract ObjectNode getCSVdata(boolean isUpdateSummary, String[] serviceNameArray,
			int requestedSampleSize, int skipFirstItems, String... customArgs);

	public int getIterationsBetweenUploads() {

		int iterationsBetweenAuditUploads = publicationInterval / collectionIntervalSeconds;

		// Always a minimum of 1
		if (iterationsBetweenAuditUploads == 0)
			iterationsBetweenAuditUploads = 1;

		return iterationsBetweenAuditUploads;
	}

	protected abstract String uploadMetrics(int iterationsBetweenAuditUploads);

	// seed defaults to System.currentTimeMillis(), which is generally good
	// enough to spread upload requests
	Random rg = new Random();

	public void resetCollectionCount() {
		logger.debug("Reseting {}", metricsCollectedSinceLastUpload);
		metricsCollectedSinceLastUpload = 0;
	}

	public int getCollectionCount() {
		return metricsCollectedSinceLastUpload;
	}

	private int incrementCollectionCounter() {
		metricsCollectedSinceLastUpload++;

		logger.debug("increasing {}", metricsCollectedSinceLastUpload);
		return metricsCollectedSinceLastUpload;
	}

	// private ReentrantLock uploadLock = new ReentrantLock();

	public String uploadMetricsNow() {
		if (metricsCollectedSinceLastUpload == 0) {
			logger.debug("No items in collection");
			return "NoItemsToUpload";
		}
		String uploadResult = uploadMetrics(metricsCollectedSinceLastUpload);
		resetCollectionCount();
		return uploadResult;
	}

	volatile private boolean keepRunning = true;

	public boolean isKeepRunning() {
		return this.keepRunning;
	}

	/**
	 * Kill off the spawned threads - triggered from ServiceRequests
	 */
	public void shutdown() {

		logger.debug("*************** Shutting down  **********************");
		this.keepRunning = false;
		if ( scheduledExecutorService != null) {
			logger.info( "Shutting down collection for {}", this.getClass().getSimpleName() );
//			try {
//				scheduledExecutorService.awaitTermination( 5, TimeUnit.SECONDS ) ;
//			} catch (InterruptedException e) {
//				logger.error( "Shut service down" );
//			}
			scheduledExecutorService.shutdown();
		}

	}

	volatile private int metricsCollectedSinceLastUpload = 0;

	// used for event publication
	public final static String METRICS_EVENT = "/csap/metrics";

	protected boolean peformUploadIfNeeded() {
		
		int collectionCount = incrementCollectionCounter()  ;

		if (publicationInterval < 1000) {
			logger.warn("COLLECTION_INTERVAL_SECONDS is low: " + publicationInterval);
		}
		// Hook to avoid lots of concurrent requests hitting
		// service at same time.
		// eg. many agents on many host will be started at same
		// time.
		boolean isUploaded = false;
		int maximumDelaySeconds = 60;
		if (collectionIntervalSeconds < 65)
			maximumDelaySeconds = collectionIntervalSeconds - 5;

		// logger.info( "Added new Entry for interval:  " + intervalSeconds +
		// " lastAddedElementIndex: " + lastAddedElementIndex );

		if (collectionCount >= getIterationsBetweenUploads()) {

			int waitSeconds = rg.nextInt(maximumDelaySeconds);
			try {
				Thread.sleep( waitSeconds * 1000 );
			} catch (InterruptedException e) {
				logger.error( "Failed to Upload Metrics", e );
			}
			uploadMetrics(getIterationsBetweenUploads());
			isUploaded = true;

			resetCollectionCount();
		}

		return isUploaded;
	}
	
	private ScheduledExecutorService scheduledExecutorService = null;

	protected void scheduleCollection( Runnable collector) {
		// Thread commandThread = new Thread( this );
		// commandThread.start();
		String scheduleName = collector.getClass().getSimpleName() + "_" + collectionIntervalSeconds ;
		BasicThreadFactory schedFactory = new BasicThreadFactory.Builder()

				.namingPattern( scheduleName +"-%d" )
					.daemon( true )
					.priority( Thread.NORM_PRIORITY )
					.build();
		// Single collection thread
		scheduledExecutorService = Executors
				.newScheduledThreadPool( 1, schedFactory );
		int initialSleep = 10 ;
		if (this.collectionIntervalSeconds >= 60) {
			initialSleep += 30 + rg.nextInt(30) ;
		}
		
		scheduledExecutorService
				.scheduleAtFixedRate( collector, initialSleep, collectionIntervalSeconds, TimeUnit.SECONDS );
		
		
		
		logger.info("Adding Job: {}", scheduleName);
	}

	final int SIZE_OF_REPORT_CACHE;
	private boolean publishSummaryAndPerformHeartBeat = false;

	public boolean isPublishSummaryAndPerformHeartBeat () {
		return publishSummaryAndPerformHeartBeat;
	}

	public void setPublishSummaryAndPerformHeartBeat ( boolean publishSummaryAndPerformHeartBeat ) {
		this.publishSummaryAndPerformHeartBeat = publishSummaryAndPerformHeartBeat;
	}

	volatile ArrayNode summary24HourCache = jacksonMapper.createArrayNode();
	volatile ArrayNode summary24HourApplicationCache = jacksonMapper.createArrayNode();

	protected JsonNode buildSummaryReport(boolean isSecondary) {

		// Step 1 - build map with total for services
		ObjectNode summaryTotalJson = jacksonMapper.createObjectNode();

		ArrayNode cache = summary24HourCache;
		if (isSecondary)
			cache = summary24HourApplicationCache;

		logger.debug("** intervalReports size: {}, isSecondary: {}", cache.size(), isSecondary);

		for (JsonNode intervalReport : cache) {

			Iterator<String> fields = intervalReport.fieldNames();
			while (fields.hasNext()) {

				String field = fields.next();

				ObjectNode serviceInterval = (ObjectNode) intervalReport.get(field);

				if (!summaryTotalJson.has(field))
					summaryTotalJson.putObject(field);
				ObjectNode serviceSummaryNode = (ObjectNode) summaryTotalJson.get(field);

				Iterator<String> subFields = serviceInterval.fieldNames();
				while (subFields.hasNext()) {
					String subField = subFields.next();

					// logger.info(" subField: " + subField);
					addItemToTotals(serviceInterval, serviceSummaryNode, subField);
				}

			}

		}

		// Step 2 convert to mongo aggregation friendly array
		ArrayNode summaryArray = jacksonMapper.createArrayNode();
		Iterator<String> serviceNames = summaryTotalJson.fieldNames();
		while (serviceNames.hasNext()) {
			String serviceName = serviceNames.next();
			ObjectNode serviceItem = summaryArray.addObject();
			serviceItem.put("serviceName", serviceName);

			ObjectNode serviceData = (ObjectNode) summaryTotalJson.get(serviceName);
			serviceItem.setAll(serviceData);
		}

		logger.debug("** Report: {}", summaryArray);

		return summaryArray;
	}

	/**
	 * jackson apis do not store longs natively...so we need to iterate over
	 *  data types.
	 * @param itemJson
	 * @param summaryJson
	 * @param fieldName 
	 */
	protected void addItemToTotals(ObjectNode itemJson, ObjectNode summaryJson, String fieldName) {
		 logger.debug( "fieldName: {} int: {}, long: {}", fieldName ,itemJson.get(fieldName).isInt(), itemJson.get(fieldName).isLong() ) ;
		if (!summaryJson.has(fieldName) || fieldName.endsWith("Avg")) {
			if (itemJson.get(fieldName).isInt() || itemJson.get(fieldName).isLong())
				summaryJson.put(fieldName, itemJson.get(fieldName).asLong());
			
			else
				summaryJson.put(fieldName, itemJson.get(fieldName).asDouble());
			
		} else {
			if (itemJson.get(fieldName).isInt() || itemJson.get(fieldName).isLong())
				summaryJson.put(fieldName, itemJson.get(fieldName).asLong()
						+ summaryJson.get(fieldName).asLong());
			else
				summaryJson.put(fieldName,
						itemJson.get(fieldName).asDouble() + summaryJson.get(fieldName).asDouble());
		}

	}

	void publishSummaryReport(String source) {
		publishSummaryReport(source, false);
	}

	void publishSummaryReport(String source, boolean isSecondary) {
		if ( isPublishSummaryAndPerformHeartBeat() ) {

			ObjectNode intervalReport = jacksonMapper.createObjectNode();
			intervalReport.set("summary", buildSummaryReport(isSecondary));
			
			csapApplication.getEventClient().
					publishEvent(CsapEventClient.CSAP_REPORTS_CATEGORY + "/" + source + "/daily", "Summary Report",
							null,
							intervalReport);
			
		}
	}

	// updateSummary is NOT done if UI is doing a request for data. It IS done
	// when
	// a publish is being done by collector thread.
	void addSummary(ObjectNode summaryNode, boolean isUpdateSummary) {
		if (!isUpdateSummary || !publishSummaryAndPerformHeartBeat)
			return;

		if (summary24HourCache.size() == 0) {
			csapApplication.loadCacheFromDisk(summary24HourCache, this.getClass().getSimpleName());
		}

		logger.debug("size: {}, max: {}, adding: {}", summary24HourCache.size(), SIZE_OF_REPORT_CACHE, summaryNode);

		summary24HourCache.insert(0, summaryNode);
		if (summary24HourCache.size() > SIZE_OF_REPORT_CACHE)
			summary24HourCache.remove(summary24HourCache.size() - 1);

		if (csapApplication.isShutdown()) {
			csapApplication.flushCacheToDisk(summary24HourCache, this.getClass().getSimpleName());
		}

	}

	// Special hook for the double collection in VmApplicationCollector
	void addApplicationSummary(ObjectNode summaryNode, boolean isUpdateSummary) {

		// many more items are being added here.......
		if (!isUpdateSummary || !publishSummaryAndPerformHeartBeat)
			return;

		if (summary24HourApplicationCache.size() == 0) {
			csapApplication.loadCacheFromDisk(summary24HourApplicationCache, this.getClass().getSimpleName() + "_Secondary");
		}

		logger.debug("size: {}, max: {}, adding: {}", summary24HourCache.size(), SIZE_OF_REPORT_CACHE, summaryNode);

		summary24HourApplicationCache.insert(0, summaryNode);
		if (summary24HourApplicationCache.size() > SIZE_OF_REPORT_CACHE) {//

			logger.debug("Removing item from application cache");
			summary24HourApplicationCache.remove(summary24HourApplicationCache.size() - 1);
		}

		if (csapApplication.isShutdown()) {
			csapApplication.flushCacheToDisk(summary24HourApplicationCache, this.getClass().getSimpleName() + "_Secondary");
		}
	}



	public int getCollectionIntervalSeconds () {
		return collectionIntervalSeconds;
	}

	public void setCollectionIntervalSeconds ( int intervalSeconds ) {
		this.collectionIntervalSeconds = intervalSeconds;
	}
}
