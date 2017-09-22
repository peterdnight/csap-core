package org.csap.agent.misc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.text.WordUtils;
import org.csap.agent.CSAP;
import org.csap.agent.model.Application;
import org.csap.agent.model.LifeCycleSettings;
import org.csap.agent.stats.MetricsPublisher;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * sends messages to csap event service
 */
@Service
public class CsapEventClient {

	final Logger logger = LoggerFactory.getLogger( this.getClass() );

	LifeCycleSettings lifecycleSettings = new LifeCycleSettings();

	private static int MAX_EVENT_BACKLOG = 2048;

	volatile BlockingQueue<Runnable> eventPostQueue;

	public int getBacklogCount () {
		return eventPostQueue.size();
	}

	public CsapEventClient( ) {

		BasicThreadFactory eventThreadFactory = new BasicThreadFactory.Builder()
			.namingPattern( "CsapEventPost-%d" )
			.daemon( true )
			.priority( Thread.NORM_PRIORITY + 1 )
			.build();

		eventPostQueue = new ArrayBlockingQueue<>( MAX_EVENT_BACKLOG );
		// Use a single thread to sequence and post
		// eventPostPool = Executors.newFixedThreadPool(1, schedFactory, queue);
		// really only needs to be 1 - adding the others for lt scenario
		eventPostPool = new ThreadPoolExecutor( 1, 1,
			30, TimeUnit.SECONDS,
			eventPostQueue, eventThreadFactory );

		eventPostCompletionService = new ExecutorCompletionService<String>(
			eventPostPool );
	}

	public LifeCycleSettings getLifeCycleMetaData () {
		return lifecycleSettings;
	}

	private String projectName = "notInit";

	/**
	 * Injected by Capability Manager after loading cluster
	 *
	 * @param lifeCycleMetaData
	 */
	public void initialize ( LifeCycleSettings lifeCycleMetaData, String project ) {
		this.lifecycleSettings = lifeCycleMetaData;
		this.projectName = project;

		logger.info( "\n\n****************** CSAP Event Publishing:  " + lifeCycleMetaData.isEventPublishEnabled()
				+ "\n\n" );

	}

	@EventListener
	public void onSpringContextRefreshedEvent ( ContextRefreshedEvent event ) {

		logger.warn( "Receive Spring ContextRefreshedEvent" );

	}

	@EventListener
	public void handleContextStarted ( ContextStartedEvent event ) {

		logger.warn( "This will never get invoked because it is for configuratble context only" );

	}

	public void shutdown () {

		logger.warn( "\n\n ******** Shutting Down and Purging: " + eventPostQueue.size() + " Events \n\n " );
		eventPostQueue.clear();
		eventPostPool.shutdown();

	}

	/**
	 * Helper method that constructs event JSON
	 *
	 * @param service
	 * @param userid
	 * @param summary
	 * @param details
	 */
	public void generateEvent (	String service, String userid, String summary,
								String details ) {

		// Always log events to log4j using caller call stack
		// System.out.println ("===============>"
		// +this.getClass().getCanonicalName());
		translateAuditsToEvents( service, summary, userid, details );

		return;
	}

	ObjectMapper jacksonMapper = new ObjectMapper();

	@Inject
	@Qualifier ( "csapEventsService" )
	private RestTemplate csapEventsService;

	private void translateAuditsToEvents (	String itemToConvert, String summary, String userId,
											String text ) {

		String category = itemToConvert;

		if ( !itemToConvert.startsWith( "/csap" ) ) {
			category = CSAP_SVC_CATEGORY + "/" + itemToConvert;

			if ( userId.equalsIgnoreCase( "system" ) ) {
				category = CSAP_SYSTEM_CATEGORY + "/old/" + itemToConvert;
			}
		}

		ObjectNode data = jacksonMapper.createObjectNode();
		data.put( "csapText", text );

		ObjectNode metaData = jacksonMapper.createObjectNode();

		if ( category.startsWith( "/csap/ui" ) ) {
			metaData.put( "uiUser", userId );
		}

		publishEvent( category, summary, metaData, data );
	}

	public void publishEvent (	String category, String summary,
								String details ) {

		ObjectNode data = jacksonMapper.createObjectNode();
		data.put( "csapText", details );
		publishEvent( category, summary, null, data );

		return;
	}

	public void publishEvent (	String category, String summary,
								String details, Throwable t ) {

		ObjectNode data = jacksonMapper.createObjectNode();
		data.put( "csapText", details + "\n Exception " + getCustomStackTrace( t ) );
		publishEvent( category, summary, null, data );

		return;
	}

	public static final String SIMON_EVENT = "csapdata.publish.";
	public static final String CSAP_CATEGORY = "/csap";
	public static final String CSAP_USER_SETTINGS_CATEGORY = "/csap/settings/user/";
	public static final String CSAP_UI_CATEGORY = "/csap/ui";
	public static final String CSAP_OS_CATEGORY = CSAP_UI_CATEGORY + "/os";
	public static final String CSAP_SVC_CATEGORY = CSAP_UI_CATEGORY + "/svc";
	public static final String CSAP_SYSTEM_CATEGORY = "/csap/system";
	public static final String CSAP_REPORTS_CATEGORY = "/csap/reports";

	// http://logging.apache.org/log4j/2.x/manual/customloglevels.html#CustomLoggers
	private int maxTextSize = 50 * 1024;// 50kb

	public int getMaxTextSize () {
		return maxTextSize;
	}

	public void setMaxTextSize ( int maxTextSize ) {
		logger.warn( "Current: {}, New: {} ", this.maxTextSize, maxTextSize );
		this.maxTextSize = maxTextSize;
	}

	public void publishEvent (	String category, String summary, JsonNode metaData,
								JsonNode data ) {

		logger.debug( "category: {},  queue length {}", category, getBacklogCount() );
		try {
			if ( category.equals( MetricsPublisher.CSAP_HEALTH ) ) {

				// When events are being queued, skip health publis
				if ( getBacklogCount() >= 10 ) {
					logger.info( "Skipping health publish due to queue length {}", getBacklogCount() );
				}

			} else {

				CsapEventClientLogUnwind eventLogger = CsapEventClientLogUnwind.create();
				eventLogger.info( "Category: {} \n\t Summary: {}", category, summary );
				// logger.info( "Category: {} \t\t {}", category, summary );
			}

		} catch (Throwable t) {
			logger.error( "Failed to parse data", t );
		}

		if ( lifecycleSettings == null || !lifecycleSettings.isEventPublishEnabled() ) {
			return;
		}

		MultiValueMap<String, String> formParams = new LinkedMultiValueMap<String, String>();
		// Build jsDoc parameter
		// Map<String, String> map = new HashMap<String, String>();

		ObjectNode eventJson = jacksonMapper.createObjectNode();
		eventJson.put( "category", category );
		eventJson.put( "summary", summary );
		eventJson.put( "lifecycle", Application.getCurrentLifeCycle() );
		eventJson.put( "project", this.projectName );
		eventJson.put( "host", Application.getHOST_NAME() );

		ObjectNode createdOn = eventJson.putObject( "createdOn" );

		createdOn.put( "unixMs", System.currentTimeMillis() );

		DateFormat df2 = new SimpleDateFormat( "yyyy-MM-dd" );
		Date now = new Date();
		createdOn.put( "date", df2.format( now ) );

		DateFormat df1 = new SimpleDateFormat( "HH:mm:ss" );
		createdOn.put( "time", df1.format( now ) );

		if ( metaData != null ) {
			eventJson.set( "metaData", metaData );
		}

		if ( data != null ) {

			if ( data.isObject() && data.has( "csapText" ) ) {
				ObjectNode dataNode = jacksonMapper.createObjectNode();
				String text = data.get( "csapText" ).asText();

				if ( text.length() > maxTextSize ) {
					logger.warn( "Warning: truncating details of item: {}. Found: {}, max: {} ",
						category, text.length(), maxTextSize );
					text = text.substring( text.length() - maxTextSize );
				}
				dataNode.put( "csapText", text );
				eventJson.set( "data", dataNode );
			} else {
				eventJson.set( "data", data );
			}

		}
		try {

			formParams.add( "eventJson",
				jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( eventJson ) );

			// logger.info("Message Converters loaded: " +
			// auditPostTemplate.getMessageConverters());
			logger.debug( "Posting url: {}, params:\n {} ", lifecycleSettings.getEventUrl(),
				WordUtils.wrap( formParams.toString(), 140 ) );


			logger.info( "eventPostPool terminated: {}", eventPostPool.isTerminated() );
			eventPostPool.submit(
				new EventPostRunnable( lifecycleSettings.getEventUrl(), formParams, category, summary ) );

			logger.debug( " post size: {}", getBacklogCount() );
		} catch (Exception e) {
			SimonManager.getCounter( SIMON_EVENT + "queue.failure" );

			logger.error( "Failed submitting to job queue: {} ,\n params: {}, \n {}",
				lifecycleSettings.getEventUrl(), WordUtils.wrap( formParams.toString(), 140 ),
				CSAP.getCsapFilteredStackTrace( e ) );
		}

		return;
	}

	/**
	 * For testing
	 *
	 * @param formParams
	 */
	transient int numSent = 0;

	public void testingOnlyPostEvent ( MultiValueMap<String, String> formParams )
			throws Exception {

		// Support for LT. do not blow through queue
		if ( numSent++ % 1000 == 0 ) {
			logger.info( "Messages sent: " + numSent + " backlog: " + eventPostQueue.size() );
		}

		ThreadPoolExecutor pool = (ThreadPoolExecutor) eventPostPool;
		if ( eventPostQueue.size() > 100 && pool.getCorePoolSize() == 1 ) {
			pool.setCorePoolSize( 6 );
			pool.setMaximumPoolSize( 6 );
		}

		// blocking for testing
		logger.info( "eventPostPool terminated: {}", eventPostPool.isTerminated() );
		Future<String> futureResult = eventPostPool.submit( new EventPostRunnable( lifecycleSettings.getEventUrl(),
			formParams, "test", "test" ) );

		// Non Blocking to test event caching/pooling
		// futureResult.get() ;
	}

	ExecutorService eventPostPool;
	ExecutorCompletionService<String> eventPostCompletionService;

	/**
	 * Making Http requests survive a minor service outage for migrations, etc.
	 *
	 * @author someDeveloper
	 *
	 */
	private int numberPublished = 0;

	public int getNumberPublished () {
		return numberPublished;
	}

	public void setNumberPublished ( int numberPublished ) {
		this.numberPublished = numberPublished;
	}

	// only intended for junits
	public boolean waitForFlushOfAllEvents ( int maxAttempts )
			throws InterruptedException {

		int attempts = 0;

		logger.info( "Sleeping for 3 seconds for in process queries" );
		Thread.sleep( 3000 );

		while (eventPostQueue.size() > 0
				&& attempts++ < maxAttempts) {
			Thread.sleep( 500 );
			logger.info( "Sleeping on response for items: " + eventPostQueue.size() + " \t\t Attempt: " + attempts );
		}

		if ( eventPostQueue.size() == 0 ) {
			return true;
		}
		return false;
	}

	private int consecutiveFailedEventPostAttempts = 0;

	public int getConsecutiveFailedEventPostAttempts () {
		return consecutiveFailedEventPostAttempts;
	}

	private int eventPostFailures = 0;

	public int getEventPostFailures () {
		return eventPostFailures;
	}

	private int numberPostedEvents = 0;

	public int getNumberOfPostedEventsAndReset () {
		int current = numberPostedEvents;
		numberPostedEvents = 0;
		return current;
	}

	// This prevents an infinite loop occurring when an event is needed to be
	// purged.
	// An extended outage may result in intermittent events not being posted,
	// but logs will contain records
	private final int MAX_EVENT_RETRIES = 500;
	private final int EVENT_SIZE_WARNING = 1024 * 1024 * 1; // default tomcat
															// post limit is
															// 2MB, warning
															// output at 1

	public class EventPostRunnable implements Callable<String> {

		private MultiValueMap<String, String> formParams;
		private String url;
		private String simonId;
		private String infoMessage;

		public EventPostRunnable( String url, MultiValueMap<String, String> formParams, String category, String infoMessage ) {
			this.formParams = formParams;
			this.url = url;
			this.simonId = category.substring( 1 ).replaceAll( "/", "-" );
			if ( this.simonId.startsWith( "csap-metrics" ) )
				this.simonId = "csap-metrics";
			if ( this.simonId.startsWith( "csap-reports" ) )
				this.simonId = "csap-reports";
			if ( this.simonId.startsWith( "csap-ui" ) )
				this.simonId = "csap-ui";
			if ( this.simonId.startsWith( "csap-system" ) )
				this.simonId = "csap-system";
			this.infoMessage = category + ":" + infoMessage;
		}

		@Override
		public String call ()
				throws Exception {
			if ( (eventPostQueue.size() > 10) && (eventPostQueue.size() % 10 == 0) ) {

				logger.warn(
					" Events in backlog: " + eventPostQueue.size() + ", maximum allowed: " + MAX_EVENT_BACKLOG );
			}

			int payloadSize = formParams.toString().length();
			if ( payloadSize > EVENT_SIZE_WARNING ) {
				logger.warn( "Event: {}, size: {} is larger then max allowed: {}", infoMessage, payloadSize, EVENT_SIZE_WARNING );
			}
			numberPublished++;
			boolean isRemoveEventFromQueue = false;
			int numAttempts = 0;
			String rc = "nadda";
			while (!isRemoveEventFromQueue) {

				numAttempts++;
				Split allDataTimer = SimonManager
					.getStopwatch( SIMON_EVENT + "all" )
					.start();
				try {
					// set password immediately before sending - in case it has
					// been updated in definition.
					formParams.set( "userid", lifecycleSettings.getEventDataUser() );
					formParams.set( "pass", lifecycleSettings.getEventDataPass() );

					ResponseEntity<String> response = csapEventsService.postForEntity( lifecycleSettings.getEventUrl(),
						formParams, String.class );

					logger.debug( "Results from post to: {}, http Code: {}, body: {} ",
						lifecycleSettings.getEventUrl(), response.getStatusCode(), response.getBody() );

					if ( response.getStatusCode().is2xxSuccessful() ) {
						rc = response.getStatusCode().toString();
						isRemoveEventFromQueue = true;
						numberPostedEvents++;
						if ( consecutiveFailedEventPostAttempts > 0 ) {
							consecutiveFailedEventPostAttempts--;
						}
					} else {
						logger.warn( "Event publish failed, attempt: {} \t\t Status: {} \t\t Body: {}",
							numAttempts, response.getStatusCode(), response.getBody() );

					}
				} catch (Exception e) {
					SimonManager.getCounter( SIMON_EVENT + "all.failures" );
					consecutiveFailedEventPostAttempts++;
					eventPostFailures++;
					logger.warn(
						"Event post to {} failed for:\n {}\n\t attempt: {}, events in backlog: {}, max backlog size: {}, Reason: {}, Length: {}",
						lifecycleSettings.getEventUrl(),
						infoMessage, numAttempts, eventPostQueue.size(), MAX_EVENT_BACKLOG, e.getMessage(),
						formParams.toString().length() );

					logger.debug( "Failed to post: \n {}", WordUtils.wrap( formParams.toString(), 140 ) );

					if ( e.getMessage().equals( "403 Forbidden" ) ) {
						isRemoveEventFromQueue = true;
						logger.warn( "Invalid user: {} or password(xxx) - Audits and Analytics will not be published.",
							formParams.get( "userid" ) );
					} else if ( e.getMessage().startsWith( "400 Bad Request" ) ) {
						isRemoveEventFromQueue = true;
						logger
							.warn(
								"Failed to post event, response: 400 Bad Request. This is usually due to exceeding max event size (2MB default). Size posted: {}",
								formParams.toString().length() );
					} else if ( e.getMessage().startsWith( "400" ) ) {
						// Latest Boot may be not setting the request correctly.
						isRemoveEventFromQueue = true;
						logger
							.warn(
								"Failed to post event, response: 400. This is usually due to exceeding max event size (2MB default). Size posted: {}",
								formParams.toString().length() );
					} else {
						// 503 Service Unavailable
						// in case of outage/migration of data service retry
						logger.debug( "Some other failure reason occured, retry is set to 1 minute" );
					}
				} finally {
					long spaceRequestsMs = 1; // avoid hammering event service

					if ( getConsecutiveFailedEventPostAttempts() > 10 ) {
						spaceRequestsMs = 200;
					}

					// During migrations extended service delays could occur.
					// if ( numAttempts > MAX_EVENT_RETRIES) {
					// logger.warn( "Purging Event as attempts is greater then
					// MAX_EVENT_RETRIES: {}", MAX_EVENT_RETRIES );
					// isRemoveEventFromQueue = true;
					// }
					if ( !isRemoveEventFromQueue ) {
						// Adding spread to retry requests with variance
						spaceRequestsMs = (30 + javaRandom.nextInt( 60 )) * 1000;
					}

					try {
						Thread.currentThread().sleep( spaceRequestsMs );
					} catch (InterruptedException e1) {
						logger.info( "Sleep exception" );
					}

				}

				try {
					allDataTimer.stop();
					// add per class
					SimonManager.getStopwatch( SIMON_EVENT + simonId ).addSplit( allDataTimer );
				} catch (Exception e) {
					logger.error( "Failed to stop", e );
				}
			}

			return rc;

		}

	}

	private Random javaRandom = new Random();

	/**
	 * Shorten the summary field so that it does not scroll to far
	 *
	 * @param summary
	 * @return
	 */
	private static String shortenSummary ( String summary ) {
		if ( summary.length() > 60 ) {
			return summary.substring( 0, 60 );
		} else {
			return summary;
		}
	}

	@SuppressWarnings ( "unchecked" )
	public static String getCustomStackTrace ( Throwable possibleNestedThrowable ) {
		// add the class name and any message passed to constructor
		final StringBuffer result = new StringBuffer();

		Throwable currentThrowable = possibleNestedThrowable;

		int nestedCount = 1;
		while (currentThrowable != null) {
			// if (log.isDebugEnabled()) {
			// log.debug("currentThrowable: " + currentThrowable.getMessage()
			// + " nestedCount: " + nestedCount + " resultBuf size: "
			// + result.length());
			// }

			if ( nestedCount == 1 ) {
				result.append( "\n__========== TOP Exception ================================__" );
			} else {
				result.append( "\n========== Nested Count: " );
				result.append( nestedCount );
				result.append( " ===============================__" );
			}
			result.append( "\n\n Exception: __"
					+ currentThrowable.getClass().getName() );
			result.append( "\n Message: " + currentThrowable.getMessage() );
			result.append( "__\n\n StackTrace: __\n" );

			// add each element of the stack trace
			List traceElements = Arrays
				.asList( currentThrowable.getStackTrace() );

			Iterator traceIt = traceElements.iterator();
			while (traceIt.hasNext()) {
				StackTraceElement element = (StackTraceElement) traceIt.next();
				result.append( element );
				result.append( "__\n" );
			}
			result.append( "\n========================================================__" );
			currentThrowable = currentThrowable.getCause();
			nestedCount++;
		}
		return result.toString();
	}

}
