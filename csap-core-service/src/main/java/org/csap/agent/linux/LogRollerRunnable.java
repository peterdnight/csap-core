package org.csap.agent.linux;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.csap.agent.CSAP;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.Application;
import org.csap.agent.model.ServiceBaseParser;
import org.csap.agent.model.ServiceInstance;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.javasimon.Stopwatch;
import org.javasimon.StopwatchSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Helper thread to trigger log rolling on Service Instances.
 * 
 * 
 * @author someDeveloper
 * 
 */
public class LogRollerRunnable {

	private static final int LONG_LOG_ROTATE_SECONDS = 3;
	private static final String LOG_ROLLER_SIMON_ID = ServiceJobRunner.SERVICE_JOB_ID + "logs.";
	private static final String LOG_ROLLER_ALL = ServiceJobRunner.SERVICE_JOB_ID + "all.logs";
	final static Logger logger = LoggerFactory.getLogger( LogRollerRunnable.class );
	BasicThreadFactory schedFactory = new BasicThreadFactory.Builder()

		.namingPattern( "CsapLogRotation-%d" )
		.daemon( true )
		.priority( Thread.NORM_PRIORITY )
		.build();
	// limit Log Rolling to a single thread.
	ScheduledExecutorService scheduledExecutorService = Executors
		.newScheduledThreadPool( 1, schedFactory );

	public LogRollerRunnable( Application csapApp ) {

		this.csapApp = csapApp;

		long initialDelay = 5;
		long interval = csapApp.lifeCycleSettings().getLogRotationMinutes();

		TimeUnit logRotationTimeUnit = TimeUnit.MINUTES;

		if ( Application.isRunningOnDesktop() ) {
			logger.warn( "Setting DESKTOP to seconds" );
			logRotationTimeUnit = TimeUnit.SECONDS;
		}

		logger.warn(
			"Scheduling logrotates to be triggered every {} {}. Logs only rotated if size exceeds threshold (default is 10mb)",
			interval, logRotationTimeUnit );

		ScheduledFuture<?> jobHandle = scheduledExecutorService
			.scheduleAtFixedRate(
				() -> executeLogRotateForAllServices(),
				initialDelay,
				interval,
				logRotationTimeUnit );

	}

	OsCommandRunner osCommandRunner = new OsCommandRunner( 60, 1, LogRollerRunnable.class.getName() );
	Application csapApp;

	/**
	 * Kill off the spawned threads - triggered from ServiceRequests
	 */
	public void shutdown () {

		logger.warn( "Shutting down all jobs" );
		try {
			scheduledExecutorService.shutdown();
		} catch (Exception e) {
			logger.error( "Shutting down error {}", CSAP.getCsapFilteredStackTrace( e ) );
		}
	}

	Process logRotateLinuxProcess = null;

	ObjectMapper jacksonMapper = new ObjectMapper();

	int lastDay = -1;
	static long NANOS_IN_SECOND = 1000 * 1000000;

	private int previousLogConfigurationLength = 0;
	private int previousLogRotationLength = 0;

	public void executeLogRotateForAllServices () {
		// TODO Auto-generated method stub

		StringBuilder servicesWithLongRotations = new StringBuilder();
		StringBuilder configurationResults = new StringBuilder();
		StringBuilder rotationResults = new StringBuilder();

		ObjectNode dailyReport = jacksonMapper.createObjectNode();

		ArrayNode serviceArray = dailyReport.putArray( "summary" );

		Split timerForAllServices = SimonManager.getStopwatch( LOG_ROLLER_ALL ).start();
		logger.info( "Starting service log rotations" );

		// Generate the log configuration file
		csapApp.getActiveModel()
			.getServicesOnHost( Application.getHOST_NAME() )
			.filter( CSAP.not( ServiceInstance::isScript ) )
			.filter( CSAP.not( ServiceInstance::isRemoteCollection ) )
			.map( this::generateDefaultRotateConfig )
			.forEach( configurationResults::append );

		if ( configurationResults.length() != previousLogConfigurationLength ) {
			logger.info( "Configuration Update: Old:{} New:{} \n {}",
				previousLogConfigurationLength, configurationResults.length(),
				configurationResults.toString() );
		}
		previousLogConfigurationLength = configurationResults.length();

		// Run the log rotation for each service
		csapApp.getActiveModel()
			.getServicesOnHost( Application.getHOST_NAME() )
			.filter( CSAP.not( ServiceInstance::isScript ) )
			.filter( CSAP.not( ServiceInstance::isRemoteCollection ) )
			.map( service -> logRotateService( service, serviceArray, servicesWithLongRotations ) )
			.forEach( rotationResults::append );

		if ( rotationResults.length() != previousLogRotationLength ) {
			logger.info( "Log Status Update: {}", rotationResults.toString() );
		}
		previousLogRotationLength = rotationResults.length();

		timerForAllServices.stop();

		int nowDay = (Calendar.getInstance()).get( Calendar.DAY_OF_WEEK );
		if ( lastDay != nowDay ) {
			// Publish summary to event service
			StopwatchSample dailySample = SimonManager.getStopwatch( LOG_ROLLER_ALL ).sampleIncrement( "daily" );
			ObjectNode dailyServiceJson = dailyReport.putObject( "Total" );
			dailyServiceJson.put( "Count", dailySample.getCounter() );
			dailyServiceJson.put( "MeanSeconds", Math.round( dailySample.getMean() / NANOS_IN_SECOND ) );
			dailyServiceJson.put( "TotalSeconds", Math.round( dailySample.getTotal() / NANOS_IN_SECOND ) );
			csapApp.getEventClient().publishEvent( CsapEventClient.CSAP_REPORTS_CATEGORY + "/logRotate",
				"Daily Summary", null, dailyReport );
		}
		lastDay = nowDay;

		if ( servicesWithLongRotations.length() > 0 ) {
			logger.warn( "\n *** Services with rotations taking more then 3 seconds:\n {}", servicesWithLongRotations );

			csapApp.getEventClient().publishEvent(
				CsapEventClient.CSAP_SYSTEM_CATEGORY + "/logrotate", "Service with rotations",
				servicesWithLongRotations.toString() );
		}

	}

	public String rotate ( ServiceInstance serviceInstance ) {
		StringBuilder results = new StringBuilder("UI Request\n") ;
		results.append(  generateDefaultRotateConfig( serviceInstance ) );
		results.append( logRotateService( serviceInstance, null, null ) );
		return results.toString() ;
	}

	private String logRotateService ( ServiceInstance serviceInstance, ArrayNode serviceArray, StringBuilder servicesWithLongRotations ) {

		StringBuilder informationOutput = new StringBuilder( "\n\t" + serviceInstance.paddedId() + ": " );
		try {

			Split serviceLogRotateTimer = Split.start();

			File serviceLogDirectory = serviceInstance.getLogWorkingDirectory();

			boolean isRotated = false;

			// Check for config file

			File logRotateConfigFile = getLogRotateConfigurationFile( serviceInstance );

			if ( !logRotateConfigFile.exists() ) {

				logger.debug( "Skipping: {},  log rotation configuration does not exist: {}",
					serviceInstance.getServiceName(), logRotateConfigFile.toPath() );

				return informationOutput.append( " log rotation configuration does not exist: " + logRotateConfigFile.toPath() ).toString();
			}

			String[] logRotateLines = {
					"#!/bin/bash",
					"set -x",
					"/usr/sbin/logrotate -v -s " + serviceLogDirectory.getAbsolutePath()
							+ "/logRotate.state "
							+ serviceLogDirectory.getAbsolutePath()
							+ "/logRotate.config",
					"" };

			String rotateResults = "notRun";

			if ( serviceInstance.isRunningAsRoot() ) {
				String[] permissionsLines = {
						"#!/bin/bash",
						"set -x",
						"chown root " + serviceLogDirectory.getAbsolutePath() + "/logRotate.config",
						"chmod 644  " + serviceLogDirectory.getAbsolutePath() + "/logRotate.config",
						// "chmod 755 " +
						// serviceLogDirectory.getAbsolutePath() + "/*",
						"" };
				String results = osCommandRunner.runUsingRootUser( "backup" + serviceInstance.getServiceName_Port(), permissionsLines );
				logger.debug( "Root Results from {}, \n {}", Arrays.asList( permissionsLines ), results );

				rotateResults = osCommandRunner.runUsingRootUser( "backup" + serviceInstance.getServiceName_Port(), logRotateLines );
			} else {

				// String results = executeString( parmList ).toString();
				rotateResults = osCommandRunner.runUsingDefaultUser( "backup" + serviceInstance.getServiceName_Port(), logRotateLines );
			}

			logger.debug( "Results from {}, \n {}", Arrays.asList( logRotateLines ), rotateResults );

			try {

				// Leave some space between rotates to not overwhelm the
				// system
				Thread.sleep( 1000 );
			} catch (InterruptedException e) {
				logger.error( "Log rotation error", e );
				;
			}

			serviceLogRotateTimer.stop();

			if ( rotateResults.contains( "compressing" ) ) {
				isRotated = true;
			}

			if ( serviceArray == null || servicesWithLongRotations == null ) {
				logger.info( "Bypassing instrumentation calls for manual invokation" );
				// add in debug output
				informationOutput.append( "Results from: " + Arrays.asList( logRotateLines ) + "\n Results: \n" + rotateResults) ;
			} else {
				if ( isRotated ) {

					// Logroller takes ~ 1 second to check for work. We
					// increment only when more work is done.
					Stopwatch serviceStop = SimonManager
						.getStopwatch( LOG_ROLLER_SIMON_ID + serviceInstance.getServiceName() );
					serviceStop.addSplit( serviceLogRotateTimer );

					// long numSeconds = allStopWatch.getLast() /
					// NANOS_IN_SECOND;
					long numSeconds = serviceLogRotateTimer.runningFor() / NANOS_IN_SECOND;
					if ( numSeconds > LONG_LOG_ROTATE_SECONDS ) {
						servicesWithLongRotations.append( "\t" + serviceInstance.getServiceName()
								+ " logrotate duration: " + numSeconds + "\n" );
					}
				}

				int nowDay = (Calendar.getInstance()).get( Calendar.DAY_OF_WEEK );
				if ( lastDay != nowDay ) {
					StopwatchSample dailySample = SimonManager.getStopwatch(
						LOG_ROLLER_SIMON_ID + serviceInstance.getServiceName() ).sampleIncrement( "daily" );
					ObjectNode dailyServiceJson = serviceArray.addObject();
					dailyServiceJson.put( "serviceName", serviceInstance.getServiceName() );
					dailyServiceJson.put( "Count", dailySample.getCounter() );
					dailyServiceJson.put( "MeanSeconds", Math.round( dailySample.getMean() / NANOS_IN_SECOND ) );
					dailyServiceJson.put( "TotalSeconds", Math.round( dailySample.getTotal() / NANOS_IN_SECOND ) );
				}
			} 
		} catch (Exception e) {
			logger.error( "{} Failed logrotate: {}", serviceInstance.getServiceName(),
				CSAP.getCsapFilteredStackTrace( e ) );
		}

		return informationOutput.toString();
	}

	private File getLogRotateConfigurationFile ( ServiceInstance service ) {
		return new File( service.getLogWorkingDirectory(), "logRotate.config" );
	}

	public Map<String, Long> logFileLastModified = new HashMap<>();

	private String generateDefaultRotateConfig ( ServiceInstance serviceInstance ) {

		StringBuilder progress = new StringBuilder( "\n\t" + serviceInstance.paddedId() + ": " );
		File logRotateConfigFile = getLogRotateConfigurationFile( serviceInstance );

		if ( !logRotateConfigFile.getParentFile().exists() ) {
			// logger.info( "{} log folder does not exist: {}",
			// serviceInstance.getServiceName(),
			// logRotateConfigFile.getParentFile().toPath() );
			return progress.append( " log folder does not exist: " + logRotateConfigFile.getParentFile().toPath() ).toString();
		}

		List<String> configurationLines = new ArrayList<>();

		if ( !serviceInstance.getLogsToRotate().isEmpty() ) {

			if ( logRotateConfigFile.exists() && logFileLastModified.containsKey( serviceInstance.getServiceName_Port() )
					&& logRotateConfigFile.lastModified() > logFileLastModified.get( serviceInstance.getServiceName_Port() ) ) {
				// logger.info( "Skipping logRotateConfigFile not modified: {}",
				// logRotateConfigFile.getAbsolutePath() );
				return progress.append( " settings not modified: " + logRotateConfigFile.getParentFile().toPath() ).toString();
			}
			logFileLastModified.put( serviceInstance.getServiceName_Port(), logRotateConfigFile.lastModified() );
			serviceInstance
				.getLogsToRotate()
				.stream()
				.filter( ServiceBaseParser.LogRotation::isActive )
				.forEach( logRotation -> {

					if ( configurationLines.isEmpty() ) {
						configurationLines
							.add( "# created using csap application definition for service " + serviceInstance.getServiceName() );
						configurationLines.add( "# DO NOT MODIFY" );
						configurationLines.add( "# This will be regenerated using CSAP on every rotation" );
						configurationLines.add( "" );
					}

					configurationLines.add( logRotation.getPath() + " {" );

					String[] settings = logRotation.getSettings().split( "," );

					for ( String setting : settings ) {
						configurationLines.add( setting );
					}

					configurationLines.add( "}" );
					configurationLines.add( "" );
				} );

		} else if ( serviceInstance.isTomcatPackaging() && !logRotateConfigFile.exists() ) {

			progress.append( "Adding default tomcat settings" );
			configurationLines.add( "# created using default template for tomcat" );
			configurationLines.add( "# settings can be modified, but will be regenerated if service is deleted" );
			configurationLines.add( "# RECOMMENDED: add the rotation settings to the CSAP service definition jobs" );

			configurationLines.add( logRotateConfigFile.getParent() + "/catalina.out {" );
			configurationLines.add( "copytruncate" );
			configurationLines.add( "weekly" );
			configurationLines.add( "rotate 3" );
			configurationLines.add( "compress" );
			configurationLines.add( "missingok" );
			configurationLines.add( "size 5M" );
			configurationLines.add( "}" );
			configurationLines.add( "" );

		}

		if ( !configurationLines.isEmpty() ) {
			try {
				logger.debug( "{} Creating: {}",
					serviceInstance.getServiceName(),
					logRotateConfigFile.toPath() );
				
				StringBuilder details = new StringBuilder("Updated configurationfile: " + logRotateConfigFile.toPath() + "\n\n");
				configurationLines.stream().forEach( line -> {
					details.append( line );
					details.append( "\n" );
				}) ;
				csapApp.getEventClient()
					.generateEvent( CsapEventClient.CSAP_SVC_CATEGORY + "/" + serviceInstance.getServiceName(),
						csapApp.lifeCycleSettings().getAgentUser(), "Updated log configuration file" , details.toString() );
				progress.append( " updating configuration: " + logRotateConfigFile.getParentFile().toPath() );
				Files.write( logRotateConfigFile.toPath(), configurationLines, Charset.forName( "UTF-8" ) );
			} catch (Exception e) {
				logger.warn( "Failed creating configuration file: {} reason: {}",
					logRotateConfigFile.getAbsolutePath(),
					CSAP.getCsapFilteredStackTrace( e ) );
			}
		} else {
			progress.append( " - " );
		}

		return progress.toString();

	}

}
