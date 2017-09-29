package org.csap.agent.stats;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.csap.agent.model.Application;
import org.csap.agent.model.ServiceAlertsEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MetricsPublisher implements Runnable {

	final Logger logger = LoggerFactory.getLogger( getClass() );
	
	BasicThreadFactory publishFactory = new BasicThreadFactory.Builder()
		.namingPattern("CsapPublish-%d")
		.daemon(true)
		.priority(Thread.NORM_PRIORITY)
		.build();
	ScheduledExecutorService publishExecutorService = Executors
			.newScheduledThreadPool(1, publishFactory);

	ScheduledFuture<?> publishServiceHandle;

	private Application csapApplication;
	private ObjectNode definitionNode;

//	ClassPathResource templateLocation = new ClassPathResource("nagiosResultTemplate.xml");
//	String statusTemplate = "";

	public MetricsPublisher(Application manager, ObjectNode definitionNode) {

		this.csapApplication = manager;
		this.definitionNode = definitionNode;

		// seed defaults to System.currentTimeMillis(), which is generally good
		// enough to spread upload requests
		Random rg = new Random();
		int waitTime = rg.nextInt(120);
		// waitTime=5 ; // For testing

		if (definitionNode.get("intervalInSeconds").asInt() > 0) {
			publishServiceHandle = publishExecutorService
					.scheduleWithFixedDelay(this, waitTime, definitionNode.get("intervalInSeconds")
							.asInt(), TimeUnit.SECONDS);
			logger.warn( "Scheduling Health publishing: {} seconds, \n {} ",
				definitionNode.get("intervalInSeconds").asInt(), definitionNode.toString() );
		}
	}

	String lastResults = "";

	public String getLastResults() {
		return lastResults;
	}

	@Override
	public void run() {

		Thread.currentThread().setName("Csap_" + definitionNode.get("type").asText() );
		logger.debug("Invoking:  " + definitionNode.toString());

		if (definitionNode.get("type").asText().equals("nagios")) {
			lastResults = NagiosIntegration.performNagiosPublish( definitionNode, csapApplication, isIntegrationEnabled() );
		} else if (definitionNode.get("type").asText().equals("csapCallHome")) {
			lastResults = performCsapPublish();
		} else {
			logger.warn("Unknown publish type: " + definitionNode.toString());
		}

	}

	public void stop() {

		logger.debug("*************** Shutting down  **********************");
		if (publishServiceHandle != null) {
			publishExecutorService.shutdownNow();
		}

	}

	boolean integrationEnabled = false;

	public boolean isIntegrationEnabled() {
		return integrationEnabled;
	}

	public void setIntegrationEnabled(boolean integrationEnabled) {
		this.integrationEnabled = integrationEnabled;
	}

	ObjectMapper jacksonMapper = new ObjectMapper();

	final public static String CSAP_HEALTH="/csap/health";
	private String  performCsapPublish() {

		StringBuilder uploadResults = new StringBuilder("**Uploaded CSAP health: ");
		ObjectNode applicationStatus = csapApplication.statusForAdminOrAgent(ServiceAlertsEnum.ALERT_LEVEL);

		try {

			// new Event publisher - it checks if publish is enabled
			csapApplication.getEventClient().
					publishEvent(CSAP_HEALTH ,
							"Health Data", null, applicationStatus);

		} catch (Throwable e) {
			uploadResults.append("  -  Warning Failed response: " + e.getMessage());
		}

		return uploadResults.toString();
	}

	/**
	 * 
	 * Nagios requires 3 hooks - disable SSL checks - auth headers - form params
	 * 
	 */

	// MUST update nagiosTemplat.cfg
	private static String[] nagiosServices = { "cpuLoad", "memory", "disk", "processes" };

	public static final String NAGIOS_WARN = "1";

	public static String[] getNagiosServices() {
		return nagiosServices;
	}
	


}
