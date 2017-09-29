package org.csap.agent.linux;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.csap.agent.CsapCoreService;
import org.csap.agent.CSAP;
import org.csap.agent.input.http.api.AgentApi;
import org.csap.agent.input.http.ui.rest.HostRequests;
import org.csap.agent.model.Application;
import org.csap.agent.services.OsManager;
import org.csap.security.SpringAuthCachingFilter;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.javasimon.utils.SimonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TransferManager {

	final Logger logger = LoggerFactory.getLogger( TransferManager.class );

	ExecutorCompletionService<String> fileTransferComplete;
	ExecutorService fileTransferService;
	int jobCount = 0;

	OsCommandRunner osCommandRunner;

	BufferedWriter globalWriterForResults;

	int timeOutSeconds = 120;
	Application csapApp;

	/**
	 * 
	 * Very transient
	 * 
	 * @param timeOutSeconds
	 * @param numberOfThreads
	 * @param outputWriter
	 */
	public TransferManager( Application csapApp, int timeOutSeconds, BufferedWriter outputWriter ) {

		this.csapApp = csapApp;
		
		logger.debug( "Number of workers: {}", csapApp.lifeCycleSettings().getNumberWorkerThreads() );
		this.timeOutSeconds = timeOutSeconds;

		osCommandRunner = new OsCommandRunner( timeOutSeconds, 1, "TransferMgr" );

		this.globalWriterForResults = outputWriter;
		updateProgress( "\nExecuting distribution using : " + csapApp.lifeCycleSettings().getNumberWorkerThreads() + " threads.\n\n" );

		BasicThreadFactory schedFactory = new BasicThreadFactory.Builder()
			.namingPattern( "CsapFileTransfer-%d" )
			.daemon( true )
			.priority( Thread.NORM_PRIORITY )
			.build();

		fileTransferService = Executors.newFixedThreadPool( csapApp.lifeCycleSettings().getNumberWorkerThreads(), schedFactory );

		fileTransferComplete = new ExecutorCompletionService<String>( fileTransferService );
	}

	private boolean isDeleteExisting = false;

	public void setDeleteExisting ( boolean isDeleteExisting ) {
		this.isDeleteExisting = isDeleteExisting;
	}

	long lastFlush = System.currentTimeMillis();

	private void updateProgress ( String content ) {

		if ( globalWriterForResults != null ) {
			try {
				globalWriterForResults.write( content + "\n" );
				globalWriterForResults.flush(); // inefficient - but UI tracking
												// is desirable
				// if (System.currentTimeMillis() - lastFlush > 2 * 1000) {
				// globalWriterForResults.flush();
				// lastFlush = System.currentTimeMillis();
				// }
			} catch (IOException e) {
				logger.error( "Failed progress update", e );
			}
		}

	}

	public void shutdown () {
		logger.debug( "Shutdown of transfer Pool requested (normal)" );
		fileTransferService.shutdownNow();
	}

	@Override
	protected void finalize ()
			throws Throwable {
		shutdown();
		super.finalize();
	}

	public void httpCopyViaCsAgent (	String auditUser, File sourceLocation,
										String destName, List<String> targetHostList )
			throws IOException {

		httpCopyViaCsAgent( auditUser, sourceLocation, destName, targetHostList, csapApp.getAgentRunUser() );

	}

	// Combination of host and file name
	private volatile List<String> _itemsRemaining = Collections.synchronizedList( new ArrayList<String>() );

	/**
	 * 
	 * Very difficult to test. Requires timeouts in network...Very cautios when
	 * altering.
	 * 
	 * @param host
	 * @param sourceLocation
	 */
	synchronized public void removeJob ( String host, File sourceLocation ) {

		logger.debug( "{} Removing item: {}", host, sourceLocation.getName() );
		_itemsRemaining.remove( host + ":" + sourceLocation.getName() );
	}

	public void httpCopyViaCsAgent (	String auditUser, File sourceLocation,
										String destName, List<String> targetHostList, String chownUserid )
			throws IOException {

		// what about multiple lists?
		for ( String host : targetHostList ) {
			// if ( ! host.equals(Application.getHOST_NAME()))
			_itemsRemaining.add( host + ":" + sourceLocation.getName() );
		}

		;

		File workingFolder = new File( csapApp.getStagingFolder(), "/temp/" );

		if ( !workingFolder.exists() ) {
			workingFolder.mkdirs();
		}

		File fileName = new File( sourceLocation.getName() + ".tgz" );
		File zipLocation = new File( workingFolder, fileName.getName() );
		File scriptPath = new File( csapApp.getStagingFolder(), "/bin/unzipAsRoot.sh" );

		List<String> parmList = new ArrayList<String>();

		logger.warn( " Zipping: {} to {} \n\t then transferring to: {} ",
			sourceLocation.getName(), zipLocation.getAbsolutePath(), _itemsRemaining );

		// Using linux to build a compressed file
		if ( Application.isRunningAsRoot() ) {
			parmList.add( "/usr/bin/sudo" );
			parmList.add( scriptPath.getAbsolutePath() );
			parmList.add( sourceLocation.getAbsolutePath() );
			parmList.add( zipLocation.getAbsolutePath() );
			// parmList.add(chownUserid);

		} else {
			parmList.add( "bash" );
			parmList.add( "-c" );
			String command = scriptPath.getAbsolutePath()
					+ " " + sourceLocation.getAbsolutePath()
					+ " " + zipLocation;

			parmList.add( command );
		}

		// Build a .tgz file
		String results = osCommandRunner.executeString( null, parmList );

		logger.info( results );

		if ( Application.isRunningOnDesktop() ) {

			// Use a java based utility for windows desktop
			fileName = new File( sourceLocation.getName() + ".zip" );
			zipLocation = new File( workingFolder, fileName.getName() );
			ZipUtility.zipDirectory( sourceLocation, zipLocation );
		}

		final List<String> targetList = targetHostList;

		String extractDir = destName;
		// if ( sourceLocation.isFile() ) {
		// extractDir = sourceLocation.getParent() ;
		// }

		for ( String host : targetList ) {

			// Never use transfer to same host
			// if (host.equals(Application.getHOST_NAME()))
			// continue;

			if ( Application.isRunningOnDesktop() && !host.equals( "csap-dev01" )
					&& !host.equals( Application.getHOST_NAME() ) ) {
				logger.info( "Desktop development, skipping: " + host );
				updateProgress( "\nDesktop development, skipping: " + host );
				try {
					Thread.sleep( 5000 );
				} catch (InterruptedException e) {
					logger.error( "Failed waiting for results on host: " + host, e );
				}
				continue;
			}

			// transferExecutorPool.execute(new HttpTransferRunnable(host,
			// zipLocation,
			// extractDir, reload));
			jobCount++;
			fileTransferComplete.submit(
				new HttpTransferRunnable(
					csapApp, auditUser,
					host, sourceLocation, zipLocation,
					extractDir, chownUserid ) );
		}
	}

	public String waitForComplete () {

		return waitForComplete( "\n", "\n" );
	}

	ObjectMapper jacksonMapper = new ObjectMapper();

	public ArrayNode waitForCompleteJson () {

		ArrayNode resultsNode = jacksonMapper.createArrayNode();
		try {

			logger.info( "jobCount: " + jobCount );
			// boolean status = transferExecutorPool.awaitTermination(300,
			// TimeUnit.SECONDS);
			for ( int i = 1; i <= jobCount; i++ ) {
				Future<String> finishedJob = fileTransferComplete.take();
				String resultString = finishedJob.get();
				String summary = resultString;
				// logger.info("====================Got: " + resultString);
				resultsNode.add( (ObjectNode) jacksonMapper.readTree( resultString ) );
				if ( summary.length() > 140 )
					summary = summary.substring( 0, 139 );

				if ( resultString.indexOf( CSAP.CONFIG_PARSE_ERROR ) != -1 ) {
					updateProgress( "\nFailed job: " + i + " of " + jobCount + ": " + resultString );
					logger.error( "Failed job: " + i + " of " + jobCount + ": " + resultString );
				} else {
					logger.info( "Completed job " + i + " of " + jobCount + ": "
							+ ", summary of response: " + summary );
					updateProgress( "\nCompleted job " + i + " of " + jobCount + ": "
							+ ", summary of response: " + summary );
				}

				if ( jobCount - i <= 4 ) {
					updateProgress( "\n *** Waiting for response from: " + _itemsRemaining );
				}

				// Need to add progress indicator
			}

		} catch (Exception e) {
			logger.error( "One or more transfers failed to complete \n {}", CSAP.getCsapFilteredStackTrace( e) );

			resultsNode.add( CSAP.CONFIG_PARSE_ERROR
					+ CSAP.CONFIG_PARSE_ERROR
					+ ": One or more scps failed to complete" );
		}
		fileTransferService.shutdown();

		return resultsNode;

	}

	public String waitForComplete ( String pre, String post ) {

		StringBuffer results = new StringBuffer();
		try {

			logger.info( "jobCount: " + jobCount );
			// boolean status = transferExecutorPool.awaitTermination(300,
			// TimeUnit.SECONDS);
			for ( int i = 1; i <= jobCount; i++ ) {
				Future<String> finishedJob = fileTransferComplete.take();
				results.append( pre );
				String resultString = finishedJob.get();
				String summary = resultString;

				try {
					JsonNode responseJson = jacksonMapper.readTree( summary );
					resultString = jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
						responseJson.get( "transferResults" ) );
					summary = resultString;

				} catch (Exception e) {
					logger.error( "Failed parsing response" );
				}

				if ( summary.length() > 140 )
					summary = summary.substring( 0, 139 );

				if ( resultString.indexOf( CSAP.CONFIG_PARSE_ERROR ) != -1 ) {
					updateProgress( "\nFailed job: " + i + " of " + jobCount + ": " + resultString );
					logger.error( "Failed job: " + i + " of " + jobCount + ": " + resultString );
				} else {
					logger.info( "Completed job " + i + " of " + jobCount + ": "
							+ ", summary of response: " + summary );
					updateProgress( "\nCompleted job " + i + " of " + jobCount + ": "
							+ ", summary of response: " + summary );
				}

				if ( jobCount - i <= 6 ) {
					updateProgress( "\n *** Waiting for response from: " + _itemsRemaining );
				}
				results.append( resultString );
				results.append( post );

				// Need to add progress indicator
			}

		} catch (Exception e) {
			logger.error( "One or more transfers failed to complete", e );

			results.append( "\n\n" + CSAP.CONFIG_PARSE_ERROR
					+ CSAP.CONFIG_PARSE_ERROR
					+ ": One or more scps failed to complete" );
		}
		fileTransferService.shutdown();

		return results.toString();

	}

	final static int CHUNKING_SIZE = 16 * 1024;

	public class HttpTransferRunnable implements Callable<String> {

		private String extractDir = "";
		private String auditUser = "";
		private String host = "";
		private File zipLocation;
		private String chownUserid;
		private File sourceLocation;
		private Application csapApp;

		public HttpTransferRunnable( Application csapApp, String auditUser, String host, File sourceLocation,
				File zipLocation, String extractDir, String chownUserid ) {

			this.csapApp = csapApp;
			this.sourceLocation = sourceLocation;
			this.host = host;
			this.auditUser = auditUser;
			this.zipLocation = zipLocation;
			this.extractDir = extractDir;

			// support for hybrid transfers of files with different paths
			String staging = Application.filePathAllOs( csapApp.getStagingFolder() ) ;
			if ( extractDir.startsWith( staging ) ) {
				this.extractDir = Application.FileToken.STAGING.value + extractDir.substring( staging.length() );
			}

			logger.debug( "staging: {} extractDir: {}", staging, this.extractDir );
			this.chownUserid = chownUserid;

		}

		public String call () {
			StringBuilder results = new StringBuilder();

			ObjectNode transferResult = jacksonMapper.createObjectNode();

			Split transferTimer = SimonManager.getStopwatch( "java.TransferManager" ).start();

			// default to using csapApp connection
			RestTemplate restTemplate = csapApp.getAgentPooledConnection( zipLocation.length(), timeOutSeconds );
			String connectionType = "pooled";

			if ( restTemplate.getRequestFactory() instanceof SimpleClientHttpRequestFactory ) {
				connectionType = "transient";
			}

			logger.debug( "{} sending, timeout seconds: {}, file: {}, extractDir: {}, connection type: {}",
				host, timeOutSeconds, zipLocation, extractDir, connectionType );

			transferResult.put( "host", host );
			transferResult.put( "connectionType", connectionType );

			updateProgress( "Sending to host: " + host + " file: " + zipLocation + " using connection: " + connectionType );

			// HashMap<String,String> urlVariables = new
			// HashMap<String,String>() ;
			MultiValueMap<String, Object> urlVariables = new LinkedMultiValueMap<String, Object>();
			urlVariables.add( "distFile", new FileSystemResource( zipLocation ) );
			urlVariables.add( "extractDir", extractDir );
			urlVariables.add( "timeoutSeconds", Integer.toString( timeOutSeconds ) );
			urlVariables.add( "auditUser", auditUser );
			urlVariables.add( "chownUserid", chownUserid );

			urlVariables.set( SpringAuthCachingFilter.USERID, csapApp.lifeCycleSettings().getAgentUser() );
			urlVariables.set( SpringAuthCachingFilter.PASSWORD, csapApp.lifeCycleSettings().getAgentPass() );

			if ( isDeleteExisting ) {
				urlVariables.add( "deleteExisting", "deleteExisting" );
			}

			String url = csapApp.getAgentUrl( host, CsapCoreService.API_AGENT_URL + AgentApi.PLATFORM_UPDATE, true );

			// uncomment for local testing
			// if ( Application.isRunningOnDesktop() ) {
			// url = csapApp.getAgentUrl( "localhost", AgentMicroService.OS_URL
			// + HostRequests.UPDATE_PLATFORM );
			// }

			try {

				logger.debug( "Updating: {} , params: {}", url, urlVariables );

				ResponseEntity<String> response = restTemplate.postForEntity( url, urlVariables,
					String.class );

				String agentResponse = response.getBody();

				if ( agentResponse != null
						&& agentResponse.contains( CSAP.CONFIG_PARSE_ERROR ) ) {

					int numRetries = 3;
					while (agentResponse.contains( OsManager.MISSING_PARAM_HACK )
							&& (numRetries-- > 0)) {
						// Very rare, but 1 or more of params sent is an empty
						// string despite being set. Source of bug is unknown
						// (tomcat/spring/multipart/...)
						try {
							logger.warn( "Detected missing param in response, retrying  request :" + url
									+ "\n Variables: " + urlVariables + "\n  Response: \n"
									+ agentResponse );
							Thread.sleep( 3000 );
						} catch (InterruptedException e) {
							logger.error( "Failed thread.sleep waiting to retry push of files: " + e.getMessage() );
						}

						SimonManager.getCounter( "java.TransferManager.retryFilePush" ).increase();
						response = restTemplate.postForEntity( url, urlVariables, String.class );
						agentResponse = response.getBody();
					}
					if ( agentResponse != null
							&& agentResponse.contains( CSAP.CONFIG_PARSE_ERROR ) ) {
						logger.error( "Try manual - Found Error again transferring files to: " + url
								+ "\n Variables: " + urlVariables + "\n  Response: \n"
								+ agentResponse );
					}
				}

				// String agentResponse = restTemplate.postForObject(url,
				// urlVariables, String.class);

				logger.debug( "response from  url: {}, http status: {}, params: {}",
					url, response.getStatusCode(), urlVariables );

				if ( (agentResponse == null
						|| !agentResponse
							.startsWith( CSAP.AGENT_CONTEXT )) ) {
					results.append( "\n\n**" + CSAP.CONFIG_PARSE_ERROR
							+ " Missing header in response from " + url + "\n" );
				}

				transferTimer.stop();
				String duration = SimonUtils.presentNanoTime( transferTimer.runningFor() );
				// Need to search for missing operand?
				try {
					// logger.info("agentResponse\n" + agentResponse);

					ObjectNode hostResponse = (ObjectNode) jacksonMapper.readTree( agentResponse );
					String timeTaken = "*** Transfer time on " + host + ",   item " + sourceLocation.getName()
							+ " was: "
							+ duration;

					logger.debug( timeTaken );
					transferResult.put( "transferTime", duration );

					transferResult.set( "transferResults", hostResponse );
				} catch (Exception e) {
					transferResult.put( "transferResults", "Error - failed to parse transfer response: " + e );
					logger.error( "Failed to json parse response: \n" + agentResponse, e );
				}
				results.append( "\n" + agentResponse );

			} catch (Exception e) {

				// DEFINITELY TEST THIS: on a target VM, edit
				// Staging/bin/unzipAsroot, and add a sleep 200
				//

				results.append( "\n\n**" + CSAP.CONFIG_PARSE_ERROR
						+ " Failed to transfer to:" + url + " Message: "
						+ e.getMessage() );

				transferResult.put( "error", "\n\n**" + CSAP.CONFIG_PARSE_ERROR
						+ " Failed to transfer: " + sourceLocation.getName() + " to:" + url + " Message: "
						+ e.getMessage() );

				logger.debug( "{} Failed transfer, sourceLocation: {}",
					host, sourceLocation.getName(), e );

				logger.warn( "Failed transferring to  host:" + host + " sourceLocation: " + sourceLocation.getName()
						+ " Read timeout seconds: " + timeOutSeconds + "\n Exception Name: " + e.getLocalizedMessage() );

			}

			removeJob( host, sourceLocation );

			try {
				return jacksonMapper.writeValueAsString( transferResult );
			} catch (JsonProcessingException e) {
				logger.error( "Failed to write response", e );
				return "Error in connection";
			}

		}
	}

}
