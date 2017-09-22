package org.csap.agent.linux;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.csap.agent.CSAP;
import org.csap.agent.model.Application;
import org.csap.agent.model.LifeCycleSettings;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfrastructureRunner {

	final private Logger logger = LoggerFactory.getLogger( getClass() );

	public static final String INFRASTRUCTURE_TEST_SIMON_ID = "infra.test";
	public static final String INFRASTRUCTURE_TEST_DISK = INFRASTRUCTURE_TEST_SIMON_ID + ".disk";
	public static final String INFRASTRUCTURE_TEST_CPU = INFRASTRUCTURE_TEST_SIMON_ID + ".cpu";

	BasicThreadFactory schedFactory = new BasicThreadFactory.Builder()

		.namingPattern( "CsapLogRotation-%d" )
		.daemon( true )
		.priority( Thread.NORM_PRIORITY )
		.build();
	// limit Log Rolling to a single thread.
	ScheduledExecutorService scheduledExecutorService = Executors
		.newScheduledThreadPool( 1, schedFactory );

	Application csapApplication;

	public InfrastructureRunner( Application csapApplication ) {

		this.csapApplication = csapApplication;

	}

	private ScheduledFuture<?> diskTestJob = null;
	private ScheduledFuture<?> cpuTestJob = null;
	
	boolean showDiskOutputOnce=false;
	boolean showCpuOutputOnce=false;

	public void scheduleInfrastructure () {

		showDiskOutputOnce=true;
		showCpuOutputOnce=true ;
		LifeCycleSettings.InfraTests infraTestSettings = csapApplication.lifeCycleSettings().getInfraTests();
		if ( diskTestJob != null ) {
			logger.info( "Cancelling previous test schedule" );
			diskTestJob.cancel( false );
			cpuTestJob.cancel( false );
		}
		

		// avoid hitting infra from large number of hosts simultaniously. Spread it out
		Random initializeRandom = new Random();
		long initialDelaySeconds = 60 + initializeRandom.nextInt( 60 * 4 );
		long diskIntervalSeconds = infraTestSettings.getDiskIntervalMinutes()*60;
		long cpuIntervalSeconds = infraTestSettings.getCpuIntervalMinutes()*60;

		//TimeUnit testTimeUnit = TimeUnit.MINUTES;

		if ( Application.isRunningOnDesktop() ) {
			logger.warn( "Setting DESKTOP to run in seconds" );
			diskIntervalSeconds = diskIntervalSeconds / 60;
			cpuIntervalSeconds = cpuIntervalSeconds / 60;
			initialDelaySeconds = initializeRandom.nextInt( 5 ) ;
		}

		logger.warn(
			"Scheduling disk and cpu tests to be triggered every {} {} and Cpu tests: {} {}.\n\t Random Initial Delay: {} {}",
			diskIntervalSeconds, TimeUnit.SECONDS, 
			cpuIntervalSeconds, TimeUnit.SECONDS, 
			initialDelaySeconds, TimeUnit.SECONDS );

		diskTestJob = scheduledExecutorService
			.scheduleAtFixedRate(
				() -> runDiskTest( infraTestSettings.getDiskWriteMb() ),
				initialDelaySeconds,
				diskIntervalSeconds,
				TimeUnit.SECONDS );

		cpuTestJob = scheduledExecutorService
			.scheduleAtFixedRate(
				() -> runCpuTest( infraTestSettings.getCpuLoopsMillions() ),
				initialDelaySeconds,
				cpuIntervalSeconds,
				TimeUnit.SECONDS );

	}

	OsCommandRunner osCommandRunner = new OsCommandRunner( 60, 1, getClass().getName() );

	public double getLastDiskTimeInMs() {
		double d = 0.0 ;
		
		long lastResult = SimonManager.getStopwatch( INFRASTRUCTURE_TEST_DISK ).getLast() ;
		
		d = TimeUnit.NANOSECONDS.toMillis( lastResult ) / 1000d;
		
		return d;
	}
	private void runDiskTest ( int diskInMb ) {
		Split timer = SimonManager.getStopwatch( INFRASTRUCTURE_TEST_DISK ).start();
		try {
			int blockSize = 1024;
			long numBlocks = diskInMb * 1024 * 1024 / blockSize;
			String[] diskTestScript = {
					"#!/bin/bash",
					"cd ~",
					"time dd if=/dev/zero of=csap_test_file bs="+ blockSize + " count=" + numBlocks,
					"sync",
					"ls -l csap_test_file",
					"rm -rf  csap_test_file",
					"" };

			String testResults = osCommandRunner.runUsingDefaultUser( "diskTest", diskTestScript );

			if ( showDiskOutputOnce ) {
				logger.info( "Results from {}, \n {}", Arrays.asList( diskTestScript ), testResults );
				showDiskOutputOnce=false;
			}
		} catch (Exception e) {
			logger.warn( "Failed disk test execution: {}", CSAP.getCsapFilteredStackTrace( e ) );
		}
		timer.stop();

	}

	
	public double getLastCpuTimeInMs() {
		double d = 0.0 ;
		
		long lastResult = SimonManager.getStopwatch( INFRASTRUCTURE_TEST_CPU ).getLast() ;
		
		d = TimeUnit.NANOSECONDS.toMillis( lastResult ) / 1000d;
		
		return d;
	}
	
	private void runCpuTest ( int cpuLoopsMillions ) {

		Split timer = SimonManager.getStopwatch( INFRASTRUCTURE_TEST_CPU ).start();
		try {

			long numLoops = cpuLoopsMillions * 1000000;
			String[] diskTestScript = {
					"#!/bin/bash",
					"time $(i="
							+ numLoops
							+ "; while (( i > 0 )); do (( i=i-1 )); done)",
					"" };

			String testResults = osCommandRunner.runUsingDefaultUser( "diskTest", diskTestScript );

			if ( showCpuOutputOnce ) {
				logger.info( "Results from {}, \n {}", Arrays.asList( diskTestScript ), testResults );
				showCpuOutputOnce=false;
			}
		} catch (Exception e) {
			logger.warn( "Failed disk test execution: {}", CSAP.getCsapFilteredStackTrace( e ) );
		}
		timer.stop();
	}
}
