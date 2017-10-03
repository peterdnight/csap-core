package org.csap.agent.linux;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.csap.agent.CSAP;
import org.csap.agent.model.Application;
import org.csap.agent.model.CsAgentTimer;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.services.DockerHelper;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.javasimon.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Service Instances will be updated with socket and file information
 *
 * @author someDeveloper
 *
 */
public class ServiceResourceRunnable implements Runnable {

	final Logger logger = LoggerFactory.getLogger( this.getClass() );

	public ServiceResourceRunnable( Application csapApp, OsCommandRunner osCommandRunner ) {
		this.osCommandRunner = osCommandRunner;
		this.csapApp = csapApp;
	}

	private Application csapApp;
	private OsCommandRunner osCommandRunner;

	public void shutDown () {

		if ( osCommandRunner == null ) {
			return;
		}
		try {
			osCommandRunner.shutDown();
			logger.info( "osCommandRunner shutdown" );
		} catch (Exception e) {
			logger.error( "Failed shutting down", e );
		}
	}

	public void run () {

		try {
			// Get latest socket info
			Split socketTimer = SimonManager.getStopwatch( "java.OsManager.socketstat.all" ).start();
			String socketStatResult = executeSocketCollection();
			socketTimer.stop();

			// Get pidstat Sample
			Split pidTimer = SimonManager.getStopwatch( "java.OsManager.pidstat" ).start();
			String pidStatResult = executePidStatCollection();
			pidTimer.stop();

			Stopwatch allServicesStopWatch = SimonManager.getStopwatch( "java.OsManager.getOpenFiles" );

			logger.debug( "\n\n***** Refreshing open files cache   *******\n\n" );

			ArrayList<ServiceInstance> svcList = csapApp
				.getServicesOnHost();
			for ( ServiceInstance instance : svcList ) {

				if ( instance.isScript() || instance.isRemoteCollection() || !instance.isRunning() ) {
					// Set all to 0
					instance.setFileCount( 0 );
					instance.setSocketCount( 0 );
					instance.setDiskReadKb( 0 );
					instance.setDiskWriteKb( 0 );
					continue;
				}
				Split opSplit = Split.start();

				getOpenFilesForInstance( instance );
				updateInstanceSockets( socketStatResult, instance );
				updateInstanceFileIo( pidStatResult, instance );

				allServicesStopWatch.addSplit( opSplit.stop() );

				try {
					// logger.info("Sleeping 5 seconds between each call to
					// allow system to queisce a bit");
					Thread.sleep( 500 );
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		} catch (Exception e) {
			logger.error( "Exception in collection ", e );
		}

	}

	private boolean isPidStatAvailable = (new File( "/usr/bin/pidstat" )).exists();

	final static String[] pid_stat_lines = {
			"#!/bin/bash",
			"pidstat -hd 15 1 | sed 's/  */ /g'",
			""
	};

	private String executePidStatCollection () {
		String pidstatResults = "";

		logger.debug( "***X Starting" );
		if ( isPidStatAvailable ) {

			pidstatResults = "Failed to run";

			try {

				pidstatResults = osCommandRunner
					.runUsingRootUser(
						"hostSocketCollection",
						pid_stat_lines );

				logger.debug( "output from: {}  , \n{}", pid_stat_lines[1], pidstatResults );
			} catch (IOException e) {
				logger.info( "Failed to collect pidstat info: {} , \n reason: {}", pid_stat_lines,
					CSAP.getCsapFilteredStackTrace( e ) );

			}

		}

		if ( Application.isRunningOnDesktop() ) {
			logger.warn( "Desktop detected - loading : {}", PIDSTAT_STUB_FILE_RH7 );
			pidstatResults = Application.getContents( new File(
				getClass().getResource( PIDSTAT_STUB_FILE_RH7 ).getFile() ) );
			LINE_SEPARATOR = "\n";
		}

		logger.debug( "***X ENDINF" );
		return pidstatResults;
	}

	final static String PIDSTAT_STUB_FILE = "/linux/pidstatResults.txt";
	final static String PIDSTAT_STUB_FILE_RH7 = "/linux/pidstatResults_rh7.txt";
	final static String SOCKET_STAT_STUB_FILE_RH6 = "/linux/socketStat.txt";
	final static String SOCKET_STAT_STUB_FILE_RH7 = "/linux/socketStat_rh7.txt";
	static String SOCKET_STAT_STUB_FILE = SOCKET_STAT_STUB_FILE_RH7;

	public void testSocketParsing ( ServiceInstance service, boolean useRh6 ) {

		if ( useRh6 ) {
			SOCKET_STAT_STUB_FILE = SOCKET_STAT_STUB_FILE_RH6;
		} else {
			SOCKET_STAT_STUB_FILE = SOCKET_STAT_STUB_FILE_RH7;
		}
		updateInstanceSockets( executeSocketCollection(), service );
	}

	private final static String[] socket_collection_lines = {
			"#!/bin/bash",
			"ss -p",
			""
	};

	private String executeSocketCollection () {

		String socketStatResult = "Failed to run";

		try {

			socketStatResult = osCommandRunner
				.runUsingRootUser( "hostSocketCollection",
					socket_collection_lines );

			logger.debug( "output from: {}  , \n{}", socket_collection_lines[1], socketStatResult );

		} catch (Exception e) {
			logger.info( "Failed to collect socket info: {} , \n reason: {}", socket_collection_lines,
				CSAP.getCsapFilteredStackTrace( e ) );

		}

		if ( Application.isRunningOnDesktop() ) {
			logger.warn( "Desktop detected - loading : {}", SOCKET_STAT_STUB_FILE );
			socketStatResult = Application.getContents(
				new File( getClass().getResource( SOCKET_STAT_STUB_FILE ).getFile() ) );

			LINE_SEPARATOR = "\n";
		}

		if ( socketStatResult.contains( "pid=" ) ) {
			isPidEqualsFormat = true;
		}

		return socketStatResult;
	}

	static boolean isPidEqualsFormat = false; // redhat 7 and others

	private void updateInstanceSockets ( String socketStatResult, ServiceInstance instance ) {
		int socketCount = 0;

		String targetData = socketStatResult;

		if ( instance.isDockerContainer() && instance.getPid().size() > 0 && instance.getPid().get( 0 ) != ServiceInstance.NO_PIDS ) {
			String[] lines = {
					"#!/bin/bash",
					DockerHelper.socketStatCommand( instance.getPid().get( 0 ) ),
					"" };

			targetData = "Failed to run";

			Split socketTimer = SimonManager.getStopwatch( "java.OsManager.socketstat.docker" ).start();
			try {
				targetData = osCommandRunner.runUsingRootUser( "dockerSocketStat", lines );
				logger.debug( "output from: {}  , \n{}", lines[1], targetData );
			} catch (IOException e) {
				logger.info( "Failed to run docker nsenter: {} , \n reason: {}", lines,
					CSAP.getCsapFilteredStackTrace( e ) );

			}
			socketTimer.stop();
		}

		for ( String pid : instance.getPid() ) {

			try {
				Integer.parseInt( pid );
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
				if ( logger.isDebugEnabled() ) {
					logger.debug( "pid is not an int, skipping" + pid );
				}
				continue;
			}

			if ( isPidEqualsFormat ) {
				socketCount += StringUtils.countMatches( targetData, "pid=" + pid + "," );
			} else {
				socketCount += StringUtils.countMatches( targetData, "," + pid + "," );
			}
			// logger.info("xxx lsofResult:" + lsofResult);

		}

		instance.setSocketCount( socketCount );
		return;
	}

	private static String LINE_SEPARATOR = System.getProperty( "line.separator" );

	private void updateInstanceFileIo ( String pidStatResult, ServiceInstance instance ) {

		float diskReadKb = 0;
		float diskWriteKb = 0;

		String[] pidstatLines = pidStatResult.split( LINE_SEPARATOR );

		int logsOutput = 10;
		for ( String pid : instance.getPid() ) {

			for ( int i = 0; i < pidstatLines.length; i++ ) {
				String curline = pidstatLines[i].trim();
				String[] cols = curline.split( " " );

				if ( logger.isDebugEnabled() && logsOutput++ < 20 ) {
					logger.debug( "cols: " + Arrays.asList( cols ) );
				}

				try {
					if ( cols.length == 6 ) {
						// rh 5 line: Time PID kB_rd/s kB_wr/s kB_ccwr/s Command

						String pidParsed = cols[1].trim();
						if ( logger.isDebugEnabled() && logsOutput++ < 20 ) {
							logger.debug(
								instance.getServiceName() + " pidParsed: " + pidParsed + " search pid: " + pid );
						}

						if ( !pidParsed.equals( pid ) ) {
							continue;
						}
						// rh 6 line: Time UID PID kB_rd/s kB_wr/s kB_ccwr/s
						// Command
						float kbRead = Float.parseFloat( cols[2] );
						diskReadKb += kbRead;

						float kbWrite = Float.parseFloat( cols[3] );
						diskWriteKb += kbWrite;

						double cancelledWrites = Double.parseDouble( cols[4] );

					} else if ( cols.length == 7 ) {
						// rh 6 line: Time UID PID kB_rd/s kB_wr/s kB_ccwr/s
						// Command

						String pidParsed = cols[2].trim();
						if ( logger.isDebugEnabled() && logsOutput++ < 20 ) {
							logger.debug(
								instance.getServiceName() + " pidParsed: " + pidParsed + " search pid: " + pid );
						}

						if ( !pidParsed.equals( pid ) ) {
							continue;
						}
						float kbRead = Float.parseFloat( cols[3] );
						diskReadKb += kbRead;

						float kbWrite = Float.parseFloat( cols[4] );
						diskWriteKb += kbWrite;

						double cancelledWrites = Double.parseDouble( cols[5] );

					}
				} catch (NumberFormatException e) {
					pidstatLines[i] = ""; // wipe out unparsable lines
					// TODO Auto-generated catch block
					// e.printStackTrace();
					if ( logger.isDebugEnabled() ) {
						logger.debug( "pid is not an int, skipping" + pid );
					}
					continue;
				}

			}

		}

		instance.setDiskReadKb( Math.round( diskReadKb ) );
		instance.setDiskWriteKb( Math.round( diskWriteKb ) );

		return;
	}

	/**
	 *
	 *
	 *
	 *
	 */
	@CsAgentTimer
	private void getOpenFilesForInstance ( ServiceInstance instance ) {

		int fileCount = 0;

		boolean isCsapProcess = (instance.getUser() == null);

		Split openFilesTimer = SimonManager.getStopwatch( "java.OsManager.getOpenFiles." + instance.getServiceName() ).start();
		if ( isCsapProcess && !instance.isDockerContainer() ) {
			fileCount = getFilesUsingJavaNio( instance, fileCount );
		} else {
			fileCount = getOpenFilesForNonCsapProcesses( instance );
		}
		openFilesTimer.stop();

		instance.setFileCount( fileCount );

		if ( logger.isDebugEnabled() ) {
			logger.debug( instance.getServiceName() + " javaFileCount : " + fileCount );
		}

	}

	/**
	 * 
	 * services run using same user as agent can leverage java nios for getting
	 * file counts
	 * 
	 * @param instance
	 * @param fileCount
	 * @return
	 */
	private int getFilesUsingJavaNio ( ServiceInstance instance, int fileCount ) {
		for ( String pid : instance.getPid() ) {

			try {
				Integer.parseInt( pid );
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				// e.printStackTrace();
				if ( logger.isDebugEnabled() ) {
					logger.debug( "pid is not an int, skipping" + pid );
				}
				continue;
			}

			// more defensive - nio handles large directorys
			try (DirectoryStream<Path> ds = Files
				.newDirectoryStream( FileSystems.getDefault().getPath( "/proc/" + pid + "/fd" ) )) {
				for ( Path p : ds ) {

					if ( fileCount++ > 9999 ) {
						fileCount = -99;
						break;
					}
				}

			} catch (Exception e) {
				fileCount = 0;
				if ( logger.isDebugEnabled() ) {
					logger.debug( "Failed getting count for: " + instance.getServiceName() + " Due to:"
							+ e.getMessage() );
				}

			}
		}
		return fileCount;
	}

	/**
	 * 
	 * In order to access files run under another user then agent, must run a
	 * root command
	 * 
	 * @param instance
	 * @return
	 */
	int numOpenWarnings = 0;

	private int getOpenFilesForNonCsapProcesses ( ServiceInstance instance ) {

		if ( !Application.isRunningAsRoot() ) {
			if ( numOpenWarnings++ < 10 ) {
				logger.warn( "Root access is not available to determine file counts" );
			}
			return -1;
		}

		int fileCount = 0;
		StringBuilder lsCommandString = new StringBuilder();
		lsCommandString.append( "ls " );
		instance.getPid().forEach( pid -> {
			lsCommandString.append( " /proc/" );
			lsCommandString.append( pid );
			lsCommandString.append( "/fd " );
		} );
		lsCommandString.append( " | grep -v /proc/ | wc -w \n" );

		String[] lines = {
				"#!/bin/bash",
				lsCommandString.toString()
		};

		try {

			String commandResult = osCommandRunner.runUsingRootUser( "openFiles", lines );

			logger.debug( "{} commandScript: \n {} \n\n commandResult:\n {}", instance.getServiceName(), lsCommandString, commandResult );

			String[] lsoflines = commandResult.split( System
				.getProperty( "line.separator" ) );
			for ( int i = 0; i < lsoflines.length; i++ ) {
				if ( lsoflines[i].trim().contains( " " ) ) {
					continue;
				}
				try {
					fileCount += Integer.parseInt( lsoflines[i].trim() );
				} catch (Exception w) {

				}
			}
		} catch (Exception e) {
			logger.warn( "Failed running commandScript: \n {}", lsCommandString, e );
			fileCount = -55;
		}

		return fileCount;
	}
}
