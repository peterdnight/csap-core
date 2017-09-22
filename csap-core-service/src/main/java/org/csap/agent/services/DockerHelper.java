package org.csap.agent.services;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.csap.agent.CSAP;
import org.csap.agent.linux.OsCommandRunner;
import org.csap.agent.linux.OutputFileMgr;
import org.csap.agent.model.Application;
import org.csap.agent.model.ServiceInstance;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.ListVolumesResponse;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.LogConfig;
import com.github.dockerjava.api.model.LogConfig.LoggingType;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.api.model.SELContext;
import com.github.dockerjava.api.model.Ulimit;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;

@Service
public class DockerHelper {

	final Logger logger = LoggerFactory.getLogger( this.getClass() );
	


	private CountDownLatch pullLatch = new CountDownLatch( 0 );
	private boolean pullSuccess = false;

	private StringBuilder lastResults = new StringBuilder();
	private volatile String lastImage = "";
	private OutputFileMgr _pullOutputManager;

	private static long MAX_PROGRESS = 1024 * 500;

	ObjectMapper jacksonMapper = new ObjectMapper();

	public final ObjectNode dockerTemplates;

	public ObjectNode getDockerTemplates () {
		return dockerTemplates;
	}

	private static final ClassPathResource dockerTemplateResource = new ClassPathResource( "dockerTemplates.json" );

	
	@Autowired
	public DockerHelper( Application csapApplication) {
		

		ObjectNode template = null;

		try {
			template = (ObjectNode) jacksonMapper.readTree( dockerTemplateResource.getFile() );
		} catch (Exception e) {
			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.warn( "Failed parsing {}, {}", dockerTemplateResource, reason );
		}
		dockerTemplates = template;
	}

	// https://github.com/docker-java/docker-java
	@Autowired ( required = false )
	DockerClient dockerClient;

	volatile private boolean foundPullError = false;

	public class PullHandler extends PullImageResultCallback {

		@Override
		public void onNext ( PullResponseItem item ) {

			logger.debug( "response: {} ", item );

			String progress = item.getStatus();
			if ( item.getErrorDetail() != null ) {
				progress = item.getErrorDetail().getMessage();
				setFoundPullError( true );
			} else if ( item.getProgressDetail() != null && item.getProgressDetail().getCurrent() != null ) {
				progress += "..." + Math.round( item.getProgressDetail().getCurrent() * 100 / item.getProgressDetail().getTotal() ) + "%";
			}

			if ( get_pullOutputManager() != null ) {
				get_pullOutputManager().print( progress );
			}

			if ( lastResults.length() < MAX_PROGRESS ) {

				// System.out.println( progress );
				lastResults.append( "\n" + progress );
			} else {
				logger.warn( "MAX progress messages exceeded: {}", MAX_PROGRESS );
			}

		}

		@Override
		public void onComplete () {
			// TODO Auto-generated method stub

			logger.info( "onComplete: {} ", Boolean.toString( isFoundPullError() ) );
			if ( !isFoundPullError() ) {
				setPullSuccess( true );
				if ( get_pullOutputManager() != null ) {
					get_pullOutputManager().print( ServiceOsManager.BUILD_SUCCESS );

				}
			}
			super.onComplete();

			// only a single volatile is needed to set scope
			pullLatch.countDown();
		}

		@Override
		protected void finalize ()
				throws Throwable {
			logger.debug( "\n\n\n  ************** JAVA GC ***************** \n\n" );
		}
	}

	private PullHandler buildPullHandler ( String imageName, OutputFileMgr pullOutput ) {

		PullHandler pullHandler = new PullHandler();
		pullLatch = new CountDownLatch( 1 );
		setPullSuccess( false );
		setFoundPullError( false );
		setLastImage( imageName );
		set_pullOutputManager( pullOutput );
		lastResults.setLength( 0 );

		return pullHandler;

	}

	public boolean isPullInProgress () {
		return pullLatch.getCount() > 0;
	}

	public String serviceDiskUsage ( ServiceInstance service ) {
		long resultInMb = 0;
		try {

			Optional<Image> match = findImageByName( service.getDockerImageName() );

			if ( match.isPresent() ) {

				InspectImageResponse info = dockerClient.inspectImageCmd( match.get().getId() ).exec();
				resultInMb = info.getSize();
				logger.debug( "resultInMb: {} bytes", resultInMb );
				resultInMb = resultInMb / CSAP.MB_FROM_BYTES;
			} else {
				resultInMb = 1;
			}

		} catch (Exception e) {
			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.warn( "Failed finding size {}, {}", service.getDockerImageName(), reason );
		}

		String duMessage = resultInMb + "M " + service.getServiceName_Port();

		logger.debug( duMessage );
		return duMessage;
	}

	public Optional<Image> findImageByName ( String name ) {
		List<Image> images = dockerClient.listImagesCmd().exec();

		Optional<Image> matchImage = images
			.stream()
			.filter( image -> {
				boolean foundMatch = false;
				
				if ( image.getRepoTags() != null ) {
					foundMatch = Arrays.asList( image.getRepoTags() ).contains( name ) ;
				}
				// Arrays.asList( image.getRepoTags() ).contains( name )
				return foundMatch ;
			})
			.findFirst();
		return matchImage;
	}

	public Optional<Container> findContainerByName ( String name ) {
		logger.debug( "searching for: {}", name );
		List<Container> containers = dockerClient.listContainersCmd().withShowAll( true ).exec();

		logger.debug( "resolved: {}", containers );

		Optional<Container> matchContainer = containers
			.stream()
			.filter( container -> Arrays.asList( container.getNames() ).contains( name ) )
			.findFirst();
		return matchContainer;
	}

	public ObjectNode buildSummary () {

		ObjectNode summary = jacksonMapper.createObjectNode();
		summary.put( "version", "not installed" );
		summary.put( "isAvailable", false );

		// summary.put( "rootDirectory", "/not/available" );
		try {
			Info info = dockerClient.infoCmd().exec();

			summary.put( "imageCount", info.getImages() );
			summary.put( "containerCount", info.getContainers() );
			summary.put( "containerRunning", info.getContainersRunning() );
			summary.put( "version", info.getServerVersion() );

			summary.put( "rootDirectory", info.getDockerRootDir() );

			summary.put( "isAvailable", true );

			ListVolumesResponse volumeResponse = dockerClient.listVolumesCmd().exec();

			int volumeCount = 0;
			if ( volumeResponse.getVolumes() != null ) {
				volumeCount = volumeResponse.getVolumes().size();
			}

			summary.put( "volumeCount", volumeCount );
		} catch (Exception e) {
			logger.debug( "Failed connecting to docker: {}", CSAP.getCsapFilteredStackTrace( e ) );
		}
		return summary;
	}

	public List<String> containerNames ( boolean showAll ) {

		List<String> names = new ArrayList<>();
		try {
			List<Container> containers = dockerClient.listContainersCmd().withShowAll( showAll ).exec();

			containers.forEach( container -> {
				logger.debug( "container: \n {}", container.toString() );
				String label = Arrays.asList( container.getNames() ).toString();
				if ( container.getNames().length == 1 ) {
					label = container.getNames()[0];
				}

				names.add( label );
			} );
			;
		} catch (Exception e) {
			logger.warn( "Failed connecting to docker: {}", CSAP.getCsapFilteredStackTrace( e ) );
			names.add( "Unable to get listing" );
		}

		return names;

	}

	public String getLastImage () {
		return lastImage;
	}

	public void setLastImage ( String lastImage ) {
		this.lastImage = lastImage;
	}

	public String getLastResults ( int offset ) {

		String results = "";
		if ( offset < lastResults.length() ) {
			results = lastResults.substring( offset );
		}
		;
		if ( !isPullInProgress() ) {
			results += "\n__Complete__";
		}
		return results;
	}

	public void setLastResults ( StringBuilder lastResults ) {
		this.lastResults = lastResults;
	}

	public String tailFile ( String name, String path, int numLines ) {

		String listingOutput = "";
		try {

			Optional<Container> matchContainer = findContainerByName( name );

			if ( matchContainer.isPresent() ) {

				ExecCreateCmdResponse execCreateCmdResponse = dockerClient
					.execCreateCmd( matchContainer.get().getId() )
					.withAttachStdout( true )
					.withCmd( "tail", "--lines=" + numLines, path )
					.withUser( "root" )
					.exec();

				ByteArrayOutputStream lsOutputStream = new ByteArrayOutputStream();
				dockerClient
					.execStartCmd( execCreateCmdResponse.getId() )
					.exec(
						new ExecStartResultCallback( lsOutputStream, lsOutputStream ) )
					.awaitCompletion();

				listingOutput = new String( lsOutputStream.toByteArray(), StandardCharsets.UTF_8 );

			} else {
				listingOutput = "frwxr-xr-x.  18 root root     4096 Apr 14 17:58 ERROR_containerNotFound_" + name;
				logger.warn( "Container not found: {} ", name );
			}

		} catch (Exception e) {

			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.warn( "Failed listing files in {}, {}", name, reason );

		}

		logger.debug( "Container: {}, path: {}, listing: {}", name, path, listingOutput );

		return listingOutput;
	}

	public String listFiles ( String name, String path ) {

		String listingOutput = containerCommand( name, "ls", "-al", path );
		if ( listingOutput.length() == 0 ) {
			listingOutput = "frwxr-xr-x.  18 root root     4096 Apr 14 17:58 ERROR_containerNotFound_" + name;
		}

		logger.debug( "Container: {}, path: {}, listing: {}", name, path, listingOutput );

		return listingOutput;
	}

	public String containerCommand ( String containerName, String... command ) {

		String commandOutput = "";
		try {

			Optional<Container> matchContainer = findContainerByName( containerName );

			if ( matchContainer.isPresent() ) {

				ExecCreateCmdResponse execCreateCmdResponse = dockerClient
					.execCreateCmd( matchContainer.get().getId() )
					.withAttachStdout( true )
					.withAttachStderr( true )
					.withCmd( command )
					.withUser( "root" )
					.exec();

				ByteArrayOutputStream lsOutputStream = new ByteArrayOutputStream();
				dockerClient
					.execStartCmd( execCreateCmdResponse.getId() )
					.exec(
						new ExecStartResultCallback( lsOutputStream, lsOutputStream ) )
					.awaitCompletion();

				commandOutput = new String( lsOutputStream.toByteArray(), StandardCharsets.UTF_8 );

			} else {

				logger.warn( "Container not found: {} ", containerName );
			}

		} catch (Exception e) {

			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.warn( "Failed listing files in {}, {}", containerName, reason );

		}

		logger.debug( "Container: {}, Command: {}, output: {}", containerName, Arrays.asList( command ), commandOutput );

		return commandOutput;
	}

	public void containerTailStream (
										String id,
										String name,
										HttpServletResponse response,
										int numberOfLines ) {
		try {
			String targetId = getContainerId( id, name );
			// docker.startContainer( container.id());
			DockerContainerLogCallback loggingCallback = new DockerContainerLogCallback( response.getWriter() );
			LogContainerCmd logCommand = dockerClient
				.logContainerCmd( targetId )
				.withStdErr( true )
				.withStdOut( true );

			if ( numberOfLines > 0 ) {
				logCommand.withTail( numberOfLines );
			}

			logCommand.exec( loggingCallback );

			loggingCallback.awaitCompletion( 3, TimeUnit.SECONDS );

		} catch (Exception e) {
			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.warn( "Failed starting {}, {}", name, reason );
			try {
				response.getWriter().println( "Failed getting file: " + reason );
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

		return;
	}

	// helper method for accessing files
	public void writeContainerFileToHttpResponse (	boolean isBinary,
													String name, String path,
													HttpServletResponse servletResponse,
													long maxEditSize, int chunkSize ) {

		logger.info( "Container: {}, path: {}, maxEditSize: {}", name, path, maxEditSize );
		Optional<Container> matchContainer = findContainerByName( name );

		if ( path.length() == 0 ) {
			containerTailStream( null, name, servletResponse, -1 );
			return;
		}
		byte[] bufferAsByteArray = new byte[ chunkSize ];
		try (
				InputStream dockerTarStream = dockerClient.copyArchiveFromContainerCmd( matchContainer.get().getId(), path ).exec();
				ServletOutputStream servletOutputStream = servletResponse.getOutputStream();) {
			int numReadIn = 0;
			try (TarArchiveInputStream tarInputStream = new TarArchiveInputStream( dockerTarStream )) {

				TarArchiveEntry tarEntry = tarInputStream.getNextTarEntry();
				long tarEntrySize = tarEntry.getSize();
				if ( isBinary ) {
					servletResponse.setContentLength( Math.toIntExact( tarEntrySize ) );
				}
				logger.info( "tar entry name: {}", tarEntry.getName() );

				if ( tarEntrySize > maxEditSize ) {
					servletOutputStream.println(
						"**** Warning: output truncated as max size reached: " + maxEditSize / 1024 +
								"Kb. \n\tView or download can be used to access entire file.\n=====================================" );
				}

				while (tarInputStream.available() > 0 && numReadIn < maxEditSize && numReadIn < tarEntrySize) {
					int numBytesRead = IOUtils.read( tarInputStream, bufferAsByteArray );
					numReadIn += numBytesRead;
					// String stringReadIn = new String( bufferAsByteArray,
					// 0,
					// numBytesRead );
					servletOutputStream.write( bufferAsByteArray, 0, numBytesRead );
					servletOutputStream.flush();
					// logger.debug( "numRead: {}, chunk: {}", numBytesRead,
					// stringReadIn );
				}

				while (IOUtils.read( dockerTarStream, bufferAsByteArray ) > 0) {
					// need to read fully or stream will leak
				}
				// response.close();

			}

			// response.close();

		} catch (Exception e) {
			logger.error( "Failed to close: {}",
				CSAP.getCsapFilteredStackTrace( e ) );

		}

	}

	public StringBuilder writeContainerFileToString (	String name, String path,
														long maxEditSize, int chunkSize ) {

		logger.info( "Container: {}, path: {}, maxEditSize: {}", name, path, maxEditSize );

		StringBuilder fileContents = new StringBuilder( "*WARNING: Docker Save to container not implemented yet\n" );
		Optional<Container> matchContainer = findContainerByName( name );

		byte[] bufferAsByteArray = new byte[ chunkSize ];
		try (
				InputStream dockerTarStream = dockerClient.copyArchiveFromContainerCmd( matchContainer.get().getId(), path ).exec();) {
			int numReadIn = 0;
			try (TarArchiveInputStream tarInputStream = new TarArchiveInputStream( dockerTarStream )) {

				TarArchiveEntry tarEntry = tarInputStream.getNextTarEntry();
				long tarEntrySize = tarEntry.getSize();

				logger.info( "tar entry name: {}", tarEntry.getName() );

				if ( tarEntrySize > maxEditSize ) {
					fileContents.append(
						"**** Warning: output truncated as max size reached: " + maxEditSize / 1024 +
								"Kb. \n\tView or download can be used to access entire file.\n=====================================" );
				}

				while (tarInputStream.available() > 0 && numReadIn < maxEditSize && numReadIn < tarEntrySize) {
					int numBytesRead = IOUtils.read( tarInputStream, bufferAsByteArray );
					numReadIn += numBytesRead;
					String stringReadIn = new String( bufferAsByteArray, 0, numBytesRead );
					fileContents.append( stringReadIn );
					// logger.debug( "numRead: {}, chunk: {}", numBytesRead,
					// stringReadIn );
				}

				while (IOUtils.read( dockerTarStream, bufferAsByteArray ) > 0) {
					// need to read fully or stream will leak
				}
				// response.close();

			}

			// response.close();

		} catch (Exception e) {
			logger.error( "Failed to close: {}",
				CSAP.getCsapFilteredStackTrace( e ) );

		}

		return fileContents;
	}

	private ArrayNode imageListNames () {
		ArrayNode result = jacksonMapper.createArrayNode();
		// list them
		List<Image> images = dockerClient.listImagesCmd().exec();

		StringBuilder builder = new StringBuilder();
		images.forEach( image -> {
			// builder.append( "\n" + Arrays.asList( image.getRepoTags() ) );
			result.add( Arrays.asList( image.getRepoTags() ).toString() );
		} );

		logger.info( "Current Images: {}", result );
		return result;
	}

	public ObjectNode imageRemove (
									String id,
									String name )
			throws Exception {

		logger.info( "Removing: {}", name );
		ObjectNode result = jacksonMapper.createObjectNode();
		try {
			dockerClient
				.removeImageCmd( name )
				// .withForce( true ) // force will remove image tags - but
				// leave in place as anonymous
				.exec();

			result.put( "result", "image has been removed: " + name );
			result.set( "listing", imageListNames() );

		} catch (Exception e) {
			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.warn( "Failed removing {}, {}", name, reason );
			result.put( DockerJson.error.key, "Failed removing: " + name );
			result.put( DockerJson.errorReason.key, reason );
		}

		return result;
	}

	public boolean imageSave ( String name, File destination ) {

		if ( !Application.isRunningOnDesktop() ) {
			return imageSaveOs( name, destination.getAbsolutePath() );

		} else {
			if ( Application.isRunningOnDesktop() ) {
				logger.warn( "\n\n *************** SKIPPING save on desktop as can be slow. Uncomment code to test\n\n" );

				try {
					Thread.sleep( 3000 );
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return true;
			}
			try (
					InputStream is = dockerClient.saveImageCmd( name ).exec();) {

				java.nio.file.Files.copy(
					is,
					destination.toPath(),
					StandardCopyOption.REPLACE_EXISTING );

			} catch (Exception e) {

				logger.error( "Failed to save image {} to local filesystem: {}, reason: {}",
					name, destination,
					CSAP.getCsapFilteredStackTrace( e ) );

				return false;
			}
		}

		return true;
	}

	private boolean imageSaveOs ( String imageName, String destTarPath ) {

		logger.info( "saving image using os shell: {}",
			destTarPath );

		String[] lines = {
				"#!/bin/bash",
				"docker save --output " + destTarPath + " " + imageName,
				"echo  ",
				"" };

		String scriptOutput = "Failed to run";
		try {
			scriptOutput = osCommandRunner.runUsingRootUser( "dockerSaveTar", lines );
			logger.info( "results: {}", scriptOutput );
		} catch (IOException e) {
			logger.warn( "Failed to save docker image {} tar: {}, reason: {} ",
				imageName, destTarPath, CSAP.getCsapFilteredStackTrace( e ) );
			scriptOutput += ", reason: " + e.getMessage() + " type: " + e.getClass().getName();
			return false;
		}

		return true;
	}

	public boolean imageLoad ( File sourceTarFile ) {

		if ( !Application.isRunningOnDesktop() ) {
			return imageLoadOs( sourceTarFile.getAbsolutePath() );

		} else {
			try (InputStream uploadStream = Files
				.newInputStream( sourceTarFile.toPath() )) {

				dockerClient.loadImageCmd( uploadStream ).exec();

			} catch (Exception e) {

				logger.error( "Failed to load image from  local filesystem: {}, reason: {}",
					sourceTarFile,
					CSAP.getCsapFilteredStackTrace( e ) );

				return false;
			}
		}

		return true;
	}

	private boolean imageLoadOs ( String sourceTarPath ) {

		logger.info( "loading image using os shell: {}",
			sourceTarPath );

		String[] lines = {
				"#!/bin/bash",
				"docker load --input " + sourceTarPath,
				"echo  ",
				"" };

		String scriptOutput = "Failed to run";
		try {
			scriptOutput = osCommandRunner.runUsingRootUser( "dockerLoadTar", lines );
			logger.info( "results: {}", scriptOutput );
		} catch (IOException e) {
			logger.warn( "Failed to load docker image tar: {}, reason: {} ",
				sourceTarPath, CSAP.getCsapFilteredStackTrace( e ) );
			scriptOutput += ", reason: " + e.getMessage() + " type: " + e.getClass().getName();
			return false;
		}

		return true;
	}

	synchronized public ObjectNode imagePull ( String name, OutputFileMgr pullOutput, int timeoutSeconds ) {

		logger.info( "Pulling: {} , Waiting for: {} seconds", name, timeoutSeconds );
		ObjectNode result = jacksonMapper.createObjectNode();
		try {
			if ( isPullInProgress() ) {
				result.put( DockerJson.error.key, "Failed starting: " + name );
				result.put( DockerJson.errorReason.key,
					"Docker pull already in progress: " + getLastImage() + "Wait a minute and try again later." );
			} else {
				PullHandler pullHandler = buildPullHandler( name, pullOutput );
				// AuthConfig authConfig = new AuthConfig()
				// .withUsername( "peterdnight" )
				// .withPassword( "pet6er82" )
				// .withEmail( "ben@me.com" )
				// .withRegistryAddress( registryName );
				// authConfig.wi
				PullImageResultCallback cb = dockerClient
					.pullImageCmd( name )
					// .withTag( "latest" )
					// .withAuthConfig( authConfig )
					.exec( pullHandler );

				boolean isComplete = cb.awaitCompletion( timeoutSeconds, TimeUnit.SECONDS );
				result.put( "result", "pulling image: " + name + " ..." );
				result.put( "isComplete", isComplete );
				result.put( "monitorProgress", true );

				// error checking relies on callbacks to complete. Wait a few
				// seconds for them to complete
				pullLatch.await( 3, TimeUnit.SECONDS );
				result.put( DockerJson.error.key, isFoundPullError() );
			}

		} catch (Exception e) {
			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.warn( "Failed starting {}, {}", name, reason );
			result.put( DockerJson.error.key, "Failed starting: " + name );
			result.put( DockerJson.errorReason.key, reason );
		}

		return result;
	}

	public ObjectNode containerTail (
										String id,
										String name,
										int numberOfLines,
										int since ) {

		ObjectNode result = jacksonMapper.createObjectNode();
		result.put( "since", since );
		try {
			String targetId = getContainerId( id, name );
			// docker.startContainer( container.id());
			if ( targetId == null ) {
				result.put( "result", "View logs: " + targetId );
				result.put( "plainText", "Unable to locate container - verify it is created and running" );
				return result;
			}
			DockerContainerLogCallback loggingCallback = new DockerContainerLogCallback( true );
			LogContainerCmd logCommand = dockerClient
				.logContainerCmd( targetId )
				.withStdErr( true )
				.withStdOut( true )
				.withTail( numberOfLines );

			if ( since < 0 ) {
				int timestamp = (int) (System.currentTimeMillis() / 1000) - 1;
				since = timestamp;
			}

			if ( since > 0 ) {
				logCommand.withSince( since );
			}

			result.put( "since", since );

			logCommand.exec( loggingCallback );
			loggingCallback.awaitCompletion( 3, TimeUnit.SECONDS );

			result.put( "result", "View logs: " + targetId );
			result.put( "plainText", loggingCallback.toString() );
			String parameterName = "?id=" + id;
			if ( id == null )
				parameterName = "?name=" + name;
			result.put( "url", parameterName + "&numberOfLines="
					+ numberOfLines );

		} catch (Exception e) {
			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.warn( "Failed getting logs {}, {}", name, reason );
			result.put( DockerJson.error.key, "Failed starting: " + name );
			result.put( DockerJson.errorReason.key, reason );
		}

		return result;
	}

	private List<String> jsonStringList ( String jsonInput )
			throws Exception {

		String hostname = Application.getHOST_NAME();
		if ( Application.isRunningOnDesktop() ) {
			logger.debug( "Swapping hostname to csap-dev04" );
			hostname = "csap-dev04";
		}
		jsonInput = jsonInput.replaceAll( "_HOST_NAME_", hostname );
		JsonNode jsonArray = jacksonMapper.readTree( jsonInput );
		List<String> trimmedList = CSAP
			.jsonStream( jsonArray )
			.map( JsonNode::asText )
			.map( String::trim )
			.collect( Collectors.toList() );

		return trimmedList;
	}

	public ObjectNode containerCreateAndStart (	ServiceInstance serviceInstance,
												ObjectNode dockerDetails )
			throws Exception {

		ObjectNode result = jacksonMapper.createObjectNode();

		try {

			String containerName = dockerDetails.get( DockerJson.containerName.key ).asText();
			containerName = serviceInstance.replaceParserVariables( containerName );


			result = containerCreate(
				serviceInstance,
				true,
				dockerDetails.get( DockerJson.imageName.key ).asText(),
				containerName,
				dockerDetails.get( DockerJson.command.key ).toString(),
				dockerDetails.get( DockerJson.entryPoint.key ).toString(),
				dockerDetails.path( DockerJson.workingDirectory.key).asText(),
				dockerDetails.path( DockerJson.networkMode.key).asText(),
				dockerDetails.path( DockerJson.restartPolicy.key).asText(),
				dockerDetails.path( DockerJson.runUser.key).asText(),
				dockerDetails.get( DockerJson.portMappings.key  ).toString(),
				dockerDetails.get( "volumes" ).toString(),
				dockerDetails.get( DockerJson.environmentVariables.key ).toString(),
				dockerDetails.get( "limits" ).toString() );

		} catch (Exception e) {

			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.warn( "Failed creating {}, {}", reason );

			result.put( DockerJson.error.key, "Failed creating container " );
			result.put( DockerJson.errorReason.key, reason );

		}
		return result;
	}

	public ObjectNode containerCreate (	ServiceInstance serviceInstance,
										boolean start,
										String usingImage,
										String name,
										String command,
										String entry,
										String workingDirectory,
										String networkMode,
										String restartPolicy,
										String runUser,
										String ports,
										String volumes,
										String environmentVariables,
										String limits )
			throws Exception {

		logger.info( "Creating container named: {} using Image: {},"
				+ "\n\t command: {}, entry: {}"
				+ "\n\t workingDirectory: {}"
				+ "\n\t networkMode: {}"
				+ "\n\t restartPolicy: {}"
				+ "\n\t runUser: {}"
				+ "\n\t ports: {}, \n\t volumes: {}, \n\t variables: {} , \n\t limits: {}",
			name, usingImage, command, entry, workingDirectory,
			networkMode, restartPolicy, runUser,
			ports, volumes, environmentVariables, limits );

		ObjectNode result = jacksonMapper.createObjectNode();
		try {
			CreateContainerCmd dockerCreateCommand = dockerClient
				.createContainerCmd( usingImage );

			if ( name != null && !name.isEmpty() ) {
				dockerCreateCommand.withName( name );
			}

			if ( workingDirectory != null && !workingDirectory.isEmpty() ) {
				if ( serviceInstance != null ) {
					workingDirectory = serviceInstance.replaceParserVariables( workingDirectory );
				}
				dockerCreateCommand.withWorkingDir( workingDirectory );
			}


			if ( networkMode != null && !networkMode.isEmpty() ) {
				dockerCreateCommand.withNetworkMode( networkMode );
			}

			if ( restartPolicy != null && !restartPolicy.isEmpty() ) {
				dockerCreateCommand.withRestartPolicy( RestartPolicy.parse( restartPolicy ) );
			}
			if ( runUser != null && !runUser.isEmpty() ) {
				
				if ( runUser.trim().equals( "$csapUser" )) {
					runUser = "$" + System.getProperty("user.name") ;
				}
				runUser =runUser.trim();
				if ( runUser.startsWith( "$" )  && runUser.length() > 1 ) {
					String[] uidScript = {
							"#!/bin/bash",
							"id -u " + runUser.substring( 1 )};
					String uid = osCommandRunner.runUsingRootUser( "id", uidScript ).trim() ;
					String[] groupScript = {
							"#!/bin/bash",
							"id -g " + runUser.substring( 1 )};
					String groupid = osCommandRunner.runUsingRootUser( "id", groupScript ).trim() ;
					runUser = uid +":"+groupid ;
				}
				
				logger.info( "Setting user: {}", runUser );
				
				
				dockerCreateCommand.withUser( runUser );
			}


			int jmxPort = -1;

			if ( command != null && !command.isEmpty() ) {
				List<String> commandList = jsonStringList( command );

				updateDockerJavaParameter( serviceInstance, commandList );

				int jmxPortc = updateJavaJmxAndTomcatParameters( serviceInstance, commandList );
				if ( jmxPortc > 1000 )
					jmxPort = jmxPortc;

				logger.debug( "commandList: {}", commandList );
				dockerCreateCommand.withCmd( commandList );
			}

			if ( entry != null && !entry.isEmpty() ) {
				List<String> entryList = jsonStringList( entry );

				updateDockerJavaParameter( serviceInstance, entryList );

				int jmxPortc = updateJavaJmxAndTomcatParameters( serviceInstance, entryList );
				if ( jmxPortc > 1000 )
					jmxPort = jmxPortc;

				logger.debug( "entryList: {}", entryList );
				dockerCreateCommand.withEntrypoint( entryList );
			}

			if ( (environmentVariables != null && !environmentVariables.isEmpty()) ) {

				List<String> envList = jsonStringList( environmentVariables );

				int jmxPortc = updateJavaJmxAndTomcatParameters( serviceInstance, envList );
				if ( jmxPortc > 1000 )
					jmxPort = jmxPortc;

				List<String> variablesUpdated = envList;
				if ( serviceInstance != null ) {
					variablesUpdated = envList
						.stream()
						.map( serviceInstance::replaceParserVariables )
						.filter( variable -> {
							boolean isCorrect=true;
							if ( variable.length() < 3 ) isCorrect=false;
							if ( !variable.contains("=") ) isCorrect=false;
							return isCorrect;
						} )
						.collect( Collectors.toList() );
				}

				logger.info( "envList: {}, \n\n variablesUpdated: {}", envList, variablesUpdated );
				dockerCreateCommand.withEnv( variablesUpdated );
			}

			if ( jmxPort > 1000 || (ports != null && !ports.isEmpty()) ) {

				List<ExposedPort> exposedList = new ArrayList<>();
				Ports portBindings = new Ports();

				ArrayNode portArray;
				if ( ports == null || ports.isEmpty() ) {
					portArray = jacksonMapper.createArrayNode();
				} else {
					portArray = (ArrayNode) jacksonMapper.readTree( ports );
				}

				if ( jmxPort > 1000 ) {
					ObjectNode portObject = portArray.addObject();
					portObject.put( "PrivatePort", jmxPort );
					portObject.put( "PublicPort", jmxPort );
				}

				portArray.forEach( portItem -> {

					int privatePortInt = portItem.get( "PrivatePort" ).asInt();
					String privatePortString = portItem.get( "PrivatePort" ).asText();
					if ( serviceInstance != null && privatePortString.startsWith( "$" ) ) {
						privatePortString = serviceInstance.replaceParserVariables( privatePortString );
						privatePortInt = Integer.parseInt( privatePortString );
					}

					int publicPort = portItem.get( "PublicPort" ).asInt();

					String publicPortString = portItem.get( "PublicPort" ).asText();
					if ( serviceInstance != null && publicPortString.startsWith( "$" ) ) {
						publicPortString = serviceInstance.replaceParserVariables( publicPortString );
						publicPort = Integer.parseInt( publicPortString );
					}
					portBindings.bind(
						ExposedPort.tcp( privatePortInt ),
						Ports.Binding.bindPort( publicPort ) );

					exposedList.add( ExposedPort.tcp( publicPort ) );

				} );

				logger.info( "portBindings: {}", portBindings );
				dockerCreateCommand.withPortBindings( portBindings );
				dockerCreateCommand.withExposedPorts( exposedList );
			}

			if ( (volumes != null && !volumes.isEmpty()) ) {
				List<Bind> dockerVolumeBinds = new ArrayList<>();

				ArrayNode volumeArray = (ArrayNode) jacksonMapper.readTree( volumes );

				volumeArray.forEach( volumeDef -> {
					AccessMode accessMode = AccessMode.rw;
					if ( volumeDef.get( "readOnly" ).asBoolean() ) {
						accessMode = AccessMode.ro;
					}
					SELContext context = SELContext.DEFAULT;
					if ( volumeDef.get( "sharedUser" ).asBoolean() ) {
						context = SELContext.shared;
					}

					String hostPath = volumeDef.get( "hostPath" ).asText();
					String containerMount = volumeDef.get( "containerMount" ).asText() ;
					if ( serviceInstance != null ) {
						hostPath = serviceInstance.replaceParserVariables( hostPath );
						containerMount = serviceInstance.replaceParserVariables( containerMount );
					}
					Bind volumeBind = new Bind(
						hostPath,
						new Volume( containerMount ),
						accessMode, context );
					dockerVolumeBinds.add( volumeBind );
				} );

				logger.debug( "dockerVolumeBinds: {}", dockerVolumeBinds );
				dockerCreateCommand.withBinds( dockerVolumeBinds );
			}

			LogConfig logConfig = null;
			int cpuIntervalMs = 0;
			int cpuQuotaMs = 0;
			if ( limits != null && !limits.isEmpty() ) {
				JsonNode limitsObject = jacksonMapper.readTree( limits );

				if ( limitsObject.has( "cpuCoresAssigned" ) ) {
					dockerCreateCommand.withCpusetCpus( limitsObject.get( "cpuCoresAssigned" ).asText() );
				}

				if ( limitsObject.has( "memoryInMb" ) ) {
					dockerCreateCommand.withMemory( limitsObject.get( "memoryInMb" ).asLong() * CSAP.MB_FROM_BYTES );
				}

				if ( limitsObject.has( "cpuCoresMax" ) ) {
					Double coresMax = limitsObject.get( "cpuCoresMax" ).asDouble();
					logger.info( "coresMax assigned via OS commands post startup: {}", coresMax );
					cpuIntervalMs = 100;
					cpuQuotaMs = Math.toIntExact( Math.round( cpuIntervalMs * coresMax ) );
					// missing api for quota - reverting to native cgroup
					// command
					// int basePeriod = 100000 ; // 100 ms
					// dockerCreateCommand.withCpuPeriod( basePeriod ) ;
					// dockerCreateCommand.withCpuQuota( Math.toIntExact(
					// Math.round( basePeriod * coresMax) ) ) ;
				}

				if ( limitsObject.has( "ulimits" ) ) {
					JsonNode limtArray = limitsObject.get( "ulimits" );

					List<Ulimit> ulimits = new ArrayList<>();

					limtArray.forEach( limitDef -> {

						ulimits.add(
							new Ulimit(
								limitDef.get( "name" ).asText(),
								limitDef.get( "soft" ).asInt(),
								limitDef.get( "hard" ).asInt() ) );
					} );
					dockerCreateCommand.withUlimits( ulimits );
					
					if ( runUser != null && !runUser.isEmpty() && 
							limitsObject.has( "skipValidation" ) && ! limitsObject.get( "skipValidation" ).asBoolean() ) {
						String warning = "Found ulimits and  user specified for container: causes start to fail unless extended ACLS."
								+ " Remove ulimits, or remove userid, or set skipValidation to try anyway." ;
						throw new Exception( warning) ;
					}
				}

				if ( limitsObject.has( "logs" ) ) {

					JsonNode logSettings = limitsObject.get( "logs" );

					if ( logSettings.has( "type" ) && logSettings.get( "type" ).asText().equals( "json-file" ) ) {

						Map<String, String> jsonLogConfig = new HashMap<>();
						jsonLogConfig.put( "max-size", "10m" );
						jsonLogConfig.put( "max-file", "2" );

						logSettings.fieldNames().forEachRemaining( logField -> {
							if ( !logField.equals( "type" ) ) {
								jsonLogConfig.put( logField, logSettings.get( logField ).asText() );
							}
						} );

						logConfig = new LogConfig( LoggingType.JSON_FILE, jsonLogConfig );
					}
				}
			}

			if ( logConfig != null ) {
				logger.info( "Log type: {} , settings: {}", logConfig.getType(), logConfig.getConfig() );
				dockerCreateCommand.withLogConfig( logConfig );
			}

			CreateContainerResponse createReponse = dockerCreateCommand.exec();

			result.set( "createResponse", jacksonMapper.convertValue( createReponse, ObjectNode.class ) );

			InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd( createReponse.getId() ).exec();
			int numAttempts = 3;
			while (containerInfo == null && numAttempts-- > 0) {

				Thread.sleep( 500 );
				containerInfo = dockerClient.inspectContainerCmd( createReponse.getId() ).exec();
				logger.info( "Polling for creation complete" );
			}

			if ( start ) {

				// need to start in order to set cpu constraints
				ObjectNode startResults = containerStart( containerInfo.getId(), name );
				result.set( "startResults", startResults );

				if ( cpuIntervalMs > 0 ) {
					String cpuConfigResult = updateContainerCpuAllow( cpuIntervalMs, cpuQuotaMs, containerInfo.getId() );
					result.put( "cpuConfigResult", cpuConfigResult );
				}
			}
			result.set( "container", jacksonMapper.convertValue( containerInfo, ObjectNode.class ) );

			// OsCommandRunner
			// /sys/fs/cgroup/cpu,cpuacct/system.slice/docker-e0f2fd1010ce4d2b8f2ef566b5843c24a2006defa44ff456ad566f58a562ee30.scope
		} catch (Exception e) {

			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.warn( "Failed creating {}, {}", name, reason );
			if ( e.getClass().getSimpleName().toLowerCase().contains( "notmodified" ) ) {
				result.put( "result", "Container was already running: " + name );
			} else {

				result.put( DockerJson.error.key, "Failed starting: " + name );
				result.put( DockerJson.errorReason.key, reason );
			}
		}

		return result;
	}

	private void updateDockerJavaParameter ( ServiceInstance serviceInstance, List<String> commandOrEntryParameters ) {

		Optional<String> java8Parameter = commandOrEntryParameters
			.stream()
			.filter( item -> item.contains( CSAP.DOCKER_JAVA_PARAMETER ) )
			.findFirst();

		if ( java8Parameter.isPresent() ) {
			int javaCommandIndex = commandOrEntryParameters.indexOf( java8Parameter.get() );

			String javaParam = java8Parameter.get().trim();

			boolean isShellWrapper = !javaParam.equals( CSAP.DOCKER_JAVA_PARAMETER );

			if ( isShellWrapper ) {
				String dockerJavaParams = " -Djava.security.egd=file:/dev/./urandom " ;
				
				if ( serviceInstance != null ) {
					// Add csapProcessId
					dockerJavaParams +=  " -DcsapProcessId=" + serviceInstance.getServiceName_Port() + " " ;
					dockerJavaParams +=  " " + CSAP.JMX_PARAMETER  + serviceInstance.getJmxPort() + " " ;
				}

				String updatedCommand = java8Parameter.get().replaceAll( CSAP.DOCKER_JAVA_PARAMETER,
					dockerJavaParams );
				commandOrEntryParameters.set( javaCommandIndex, updatedCommand );
				
			} else {
				commandOrEntryParameters.set( javaCommandIndex, "-Djava.security.egd=file:/dev/./urandom" );				
				if ( serviceInstance != null ) {
					// Add csapProcessId
					commandOrEntryParameters.add( javaCommandIndex, " -DcsapProcessId=" + serviceInstance.getServiceName_Port() );
					commandOrEntryParameters.add( javaCommandIndex, CSAP.JMX_PARAMETER  + serviceInstance.getJmxPort() );
				}
			}
		}

		return;
	}

	/**
	 * 
	 * Two scenarios require updating parameters: -DcsapDockerJava
	 * -DcsapJmxPort=$jmxPort - this is short hand for adding required jmx
	 * configuration, including adding port to exposed list
	 * 
	 *
	 */
	private int updateJavaJmxAndTomcatParameters (
										ServiceInstance serviceInstance,
										List<String> commandOrEntryOrEnvItems )
			throws JsonParseException, JsonMappingException, IOException {

		int jmxPort = -1;
		boolean isDockerShellWrapper = false;
		String matchedFullParameter = null;

		// Use the csap model for jmx ports if docker_java parameter is
		// specified
		Optional<String> javaDockerParameter = Optional.empty();
		if ( serviceInstance != null ) {
			javaDockerParameter = commandOrEntryOrEnvItems
				.stream()
				.filter( item -> item.contains( CSAP.DOCKER_JAVA_PARAMETER ) )
				.findFirst();
			if ( javaDockerParameter.isPresent() ) {
				jmxPort = Integer.parseInt( serviceInstance.getJmxPort() );
				matchedFullParameter = javaDockerParameter.get();
				// eg. JAVA_OPTS=-DcsapDockerJava
				isDockerShellWrapper = !matchedFullParameter.startsWith( CSAP.DOCKER_JAVA_PARAMETER );
			}
		}

		//
		// Handle when image is started from Host dashboard
		//
		if ( !javaDockerParameter.isPresent() ) {
			Optional<String> jmxPortParamOptional = commandOrEntryOrEnvItems
				.stream()
				.filter( item -> item.contains( CSAP.JMX_PARAMETER ) )
				.findFirst();

			if ( jmxPortParamOptional.isPresent() ) {
				try {
					matchedFullParameter = jmxPortParamOptional.get();
					String jmxPortParam = jmxPortParamOptional.get().trim();
					isDockerShellWrapper = !jmxPortParam.startsWith( CSAP.JMX_PARAMETER );

					jmxPortParam = jmxPortParam.substring( jmxPortParam.indexOf( CSAP.JMX_PARAMETER ) );
					String portString = jmxPortParam.substring( CSAP.JMX_PARAMETER.length() ).trim();

					int spaceIndex = portString.indexOf( " " );
					if ( spaceIndex != -1 ) {
						// shellWrapper
						portString = portString.substring( 0, spaceIndex );
					}
					if ( serviceInstance != null ) {
						portString = serviceInstance.replaceParserVariables( portString );
					}
					jmxPort = Integer.parseInt( portString );
				} catch (Exception e) {
					String reason = CSAP.getCsapFilteredStackTrace( e );
					logger.warn( "Failed parsing '{}', {}", jmxPortParamOptional.get(), reason );
				}
			}
		}

		boolean isDockerShell = isDockerShellWrapper; // for stream
		if ( jmxPort > 1000 ) {
			int commandEntryEnv_index = commandOrEntryOrEnvItems.indexOf( matchedFullParameter );

			JsonNode jmxParameterTemplate = dockerTemplates.get( "javaJmx" );
			ArrayList<String> jmxParams = jacksonMapper.readValue(
				jmxParameterTemplate.traverse(),
				new TypeReference<ArrayList<String>>() {
				} );

			String portString = Integer.toString( jmxPort );

			StringBuilder expandedParameterForShellLaunch = new StringBuilder();

			jmxParams
				.stream()
				.map( jmxParam -> jmxParam.replaceAll( "_HOST_NAME_", Application.getHOST_NAME() ) )
				.map( jmxParam -> jmxParam.replaceAll( "_JMX_PORT_", portString ) )
				.forEach( jmxParam -> {
					if ( isDockerShell ) {
						// add to shell parameters
						expandedParameterForShellLaunch.append( jmxParam + " " );
					} else {
						// update in place
						commandOrEntryOrEnvItems.add( commandEntryEnv_index, jmxParam );
					}
				} );

			// For shell - we are updating even more stuff...typically tomcat env vars
			if ( isDockerShell ) {
				if ( serviceInstance != null && javaDockerParameter.isPresent() ) {
					// Add java flags if not updated previously
					expandedParameterForShellLaunch.append( " -DcsapProcessId=" + serviceInstance.getServiceName_Port() + " " );
					expandedParameterForShellLaunch.append( " -Djava.security.egd=file:/dev/./urandom " );
				}
				StringBuilder updatedItem = new StringBuilder( matchedFullParameter );
				int insertPoint = updatedItem.indexOf( CSAP.DOCKER_JAVA_PARAMETER );
				if ( insertPoint == -1 ) {
					insertPoint = updatedItem.indexOf( CSAP.JMX_PARAMETER );
				}
				updatedItem.insert( insertPoint, expandedParameterForShellLaunch );
				commandOrEntryOrEnvItems.set( commandEntryEnv_index, updatedItem.toString() );
			}

		}

		// update service variables
		if ( serviceInstance != null ) {
			for ( int i = 0; i < commandOrEntryOrEnvItems.size(); i++ ) {
				commandOrEntryOrEnvItems.set( i,
					serviceInstance.replaceParserVariables(
						commandOrEntryOrEnvItems.get( i ) ) );
			}
		}

		logger.debug( "jmx string: {}, port: {}, commandlist: {}",
			matchedFullParameter, jmxPort, commandOrEntryOrEnvItems );
		return jmxPort;
	}

	OsCommandRunner osCommandRunner = new OsCommandRunner( 60, 2, "DockerHelper" );

	public String updateContainerCpuAllow ( int cpuPeriodMs, int cpuQuotaMs, String containerId ) {

		logger.info( "Updating quota: periodMs: {} , quotaMs: {}, containerId: {}",
			cpuPeriodMs, cpuQuotaMs, containerId );

		String[] lines = {
				"#!/bin/bash",
				"cd /sys/fs/cgroup/cpu/system.slice/docker-" + containerId + ".scope",
				"echo  " + cpuPeriodMs * 1000 + " > cpu.cfs_period_us",
				"echo  " + cpuQuotaMs * 1000 + " > cpu.cfs_quota_us",
				"echo == updated container cpu period " + cpuPeriodMs + "ms and quota " + cpuQuotaMs + "ms in: `pwd` ",
				"" };

		String scriptOutput = "Failed to run";
		try {
			scriptOutput = osCommandRunner.runUsingRootUser( "dockerCpu", lines );
		} catch (IOException e) {
			logger.info( "Failed to update cpu settings: {} ", CSAP.getCsapFilteredStackTrace( e ) );
			scriptOutput += ", reason: " + e.getMessage() + " type: " + e.getClass().getName();
		}

		return scriptOutput;
	}

	public static String socketStatCommand ( String pid ) {
		return "nsenter -t " + pid + " -n ss -p";
	}

	public String dockerOpenSockets ( String pid ) {

		String[] lines = {
				"#!/bin/bash",
				socketStatCommand( pid ) + "r", // resolve host names
				"" };

		String scriptOutput = "Failed to run";

		Split socketTimer = SimonManager.getStopwatch( "docker.socket" ).start();
		try {
			scriptOutput = osCommandRunner.runUsingRootUser( "dockerSocketStat", lines );
			logger.debug( "output from: {}  , \n{}", lines[1], scriptOutput );
		} catch (IOException e) {
			logger.info( "Failed to run docker nsenter: {} , \n reason: {}", lines,
				CSAP.getCsapFilteredStackTrace( e ) );
			scriptOutput += ", reason: " + e.getMessage() + " type: " +
					e.getClass().getName();
		}
		socketTimer.stop();
		return scriptOutput;
	}

	public ObjectNode containerRemove (
										String id,
										String name,
										boolean force,
										boolean removeVolumes )
			throws Exception {

		ObjectNode result = jacksonMapper.createObjectNode();
		try {

			String targetId = getContainerId( id, name );

			dockerClient
				.removeContainerCmd( targetId )
				.withForce( force )
				.withRemoveVolumes( removeVolumes )
				.exec();
			result.put( "result", "removed container: " + targetId );
			// InspectContainerResponse info = getContainerStatus(
			// containerName );
			// result.set( "state", jacksonMapper.convertValue(
			// info.getState(), ObjectNode.class ) );

		} catch (Exception e) {
			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.warn( "Failed removing {}, {}", name, reason );
			result.put( DockerJson.error.key, "Failed removing: " + name );
			result.put( DockerJson.errorReason.key, reason );
		}

		return result;
	}

	public ObjectNode containerStart (
										String id,
										String name )
			throws Exception {

		ObjectNode result = jacksonMapper.createObjectNode();
		try {

			String targetId = getContainerId( id, name );

			if ( targetId != null && !targetId.isEmpty() ) {
				dockerClient.startContainerCmd( targetId ).exec();
				result.put( "result", "Started container: " + name + " id:" + targetId );
				InspectContainerResponse info = dockerClient.inspectContainerCmd( targetId ).exec();
				;
				result.set( "state", jacksonMapper.convertValue( info.getState(), ObjectNode.class ) );
			} else {
				result.put( DockerJson.error.key, "Container not found: " + name );
			}

		} catch (Exception e) {

			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.warn( "Failed starting {}, {}", name, reason );
			if ( e.getClass().getSimpleName().toLowerCase().contains( "notmodified" ) ) {
				result.put( "result", "Container was already running: " + name );
			} else {

				result.put( DockerJson.error.key, "Failed starting: " + name );
				result.put( DockerJson.errorReason.key, reason );
			}
		}

		return result;
	}

	public String getContainerCreationTime ( Container container ) {

		long secondsSinceEpoch = container.getCreated();
		LocalDateTime createDT = LocalDateTime.ofInstant( Instant.ofEpochMilli( secondsSinceEpoch * 1000 ), ZoneId.systemDefault() );
		return createDT.format( DateTimeFormatter.ofPattern( "HH:mm:ss MMM d, yyyy" ) );
	}

	public InspectContainerResponse containerConfiguration ( String name ) {

		Optional<Container> matchContainer = findContainerByName( name );
		if ( matchContainer.isPresent() ) {
			return dockerClient.inspectContainerCmd( matchContainer.get().getId() ).exec();
		}
		return null;
	}

	public ObjectNode containerStop (
										String id,
										String name,
										boolean kill,
										int stopSeconds ) {

		logger.info( "Stopping container: {} ", name );

		ObjectNode result = jacksonMapper.createObjectNode();
		try {

			Optional<Container> matchContainer = findContainerByName( name );

			if ( matchContainer.isPresent() ) {

				if ( kill ) {

					dockerClient
						.killContainerCmd( matchContainer.get().getId() )
						.exec();

				} else {

					dockerClient
						.stopContainerCmd( matchContainer.get().getId() )
						.withTimeout( stopSeconds )
						.exec();
				}

				result.put( "result", "Stopped container: " + name );
				InspectContainerResponse info = containerConfiguration( name );
				result.set( "state", jacksonMapper.convertValue( info.getState(), ObjectNode.class ) );
			} else {
				result.put( DockerJson.error.key, "Container not found: " + name );
			}

		} catch (Exception e) {
			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.warn( "Failed stopping {}, {}", name, reason );
			if ( e.getClass().getSimpleName().toLowerCase().contains( "notmodified" ) ) {
				result.put( "result", "Container was already stopped: " + name );
			} else {

				result.put( DockerJson.error.key, "Failed stopping: " + name );
				result.put( DockerJson.errorReason.key, reason );
			}
		}

		return result;
	}

	private String getContainerId ( String id, String name ) {
		String targetId = id;
		if ( id == null || id.isEmpty() ) {
			Optional<Container> matchContainer = findContainerByName( name );
			if ( matchContainer.isPresent() ) {
				targetId = matchContainer.get().getId();
			} else {
				targetId = null;
			}
		}
		return targetId;
	}

	public OutputFileMgr get_pullOutputManager () {
		return _pullOutputManager;
	}

	public void set_pullOutputManager ( OutputFileMgr outputFileMgr ) {
		this._pullOutputManager = outputFileMgr;
	}

	public boolean isFoundPullError () {
		return foundPullError;
	}

	public void setFoundPullError ( boolean foundError ) {
		this.foundPullError = foundError;
	}

	public boolean isPullSuccess () {
		return pullSuccess;
	}

	public void setPullSuccess ( boolean pullSuccess ) {
		this.pullSuccess = pullSuccess;
	}

}
