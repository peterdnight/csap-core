package org.csap.test.t7_service_integrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
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
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.csap.agent.CSAP;
import org.csap.test.InitializeLogging;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.DockerCmdExecFactory;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.InspectVolumeResponse;
import com.github.dockerjava.api.command.ListVolumesResponse;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
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
import com.github.dockerjava.api.model.Volumes;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.core.command.WaitContainerResultCallback;
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;
import com.github.dockerjava.netty.NettyDockerCmdExecFactory;

/**
 *   Ensure docker is started, with port open
 *
 */

// @Ignore
public class Docker_Java {

	private static final String DOCKER_CONNECTION = "tcp://localhost:2375"; // tcp://csap-dev04:4243

	final static private Logger logger = LoggerFactory.getLogger( Docker_Java.class );

	
	@BeforeClass
	public static void setUpBeforeClass ()
			throws Exception {
		InitializeLogging.printTestHeader( logger.getName() );
	}

	@AfterClass
	public static void tearDownAfterClass ()
			throws Exception {
	}

	@Before
	public void setUp ()
			throws Exception {
	}

	@After
	public void tearDown ()
			throws Exception {
	}

	// needed for property placedholder context
	@SpringBootApplication
	static class TestConfiguration {
	}

	@PostConstruct
	void printVals () {
	}

	ObjectMapper jacksonMapper = new ObjectMapper();

	// https://github.com/docker-java/docker-java

	/**
	 * 
	 * - ref https://github.com/docker-java/docker-java
	 * 
	 * - test cases:
	 * https://github.com/docker-java/docker-java/tree/master/src/test/java/com/github/dockerjava/core/command
	 * 
	 * 
	 * 
	 */
	// DockerClientConfig config =
	// DefaultDockerClientConfig.createDefaultConfigBuilder()
	// .withDockerHost( "tcp://my-docker-host.tld:2376" )
	// .withDockerTlsVerify( true )
	// .withDockerCertPath( "/home/user/.docker/certs" )
	// .withDockerConfig( "/home/user/.docker" )
	// .withApiVersion( "1.23" )
	// .withRegistryUrl( "https://index.docker.io/v1/" )
	// .withRegistryUsername( "dockeruser" )
	// .withRegistryPassword( "ilovedocker" )
	// .withRegistryEmail( "dockeruser@github.com" )
	// .build();

	DockerClientConfig dockerConfiguration = DefaultDockerClientConfig.createDefaultConfigBuilder()
		.withDockerHost( DOCKER_CONNECTION )
		.build();

	public final int dockerPoolSize = 1;
	// DockerCmdExecFactory dockerCmdExecFactory = buildDockerCommandFactory() ;

	public final int MAX_WAIT_TIME_MS = 20000;

	private JerseyDockerCmdExecFactory buildDockerJerseyCommandFactory () {
		return new JerseyDockerCmdExecFactory()
			.withReadTimeout( MAX_WAIT_TIME_MS )
			.withConnectTimeout( MAX_WAIT_TIME_MS )
			.withMaxTotalConnections( dockerPoolSize )
			.withMaxPerRouteConnections( dockerPoolSize );
	}

	private NettyDockerCmdExecFactory buildNettyDockerCommandFactory () {
		return new NettyDockerCmdExecFactory()
			.withConnectTimeout( 10000 );
	}

	private DockerCmdExecFactory lastFactory = null;

	private DockerClient buildDockerClientWithShutdown () {

		logger.warn( "\n\n\n *********** Dockerclient: {} poolsize: {}  *** \n\n\n", 
			dockerConfiguration.getDockerHost(), dockerPoolSize );

		// need to support connection leaks in docker java image load code
		// means shutdown needs to occur - which can then cause intermittent
		// race conditions.
		//
		if ( lastFactory != null ) {
			try {
				lastFactory.close();
			} catch (IOException e) {
				logger.error( "Failed closing factory", e );
			}
		}
		// Netty: use 2*core count threads
		// lastFactory = buildNettyDockerCommandFactory() ;
		lastFactory = buildDockerJerseyCommandFactory();
		return DockerClientBuilder
			.getInstance( dockerConfiguration )
			.withDockerCmdExecFactory( lastFactory )
			.build();
	}

	DockerClient dockerClient = buildDockerClientWithShutdown();

	@Test
	public void verify_docker_info ()
			throws Exception {
		Info info = dockerClient.infoCmd().exec();

		logger.info( "info: {}", info );
	}

	@Ignore
	@Test
	public void ERROR_CASE_docker_load_leaks_connections ()
			throws Exception {

		MyHandler handler = new MyHandler();

		String imageName = BUSY_BOX;

		/**
		 * Image pull
		 */
		dockerClient
			.pullImageCmd( imageName )
			.withTag( "latest" )
			// .withAuthConfig( authConfig )
			.exec( handler ).awaitCompletion();

		logger.info( "\n\n - pulled image" );

		/**
		 * image save to file
		 */

		File targetFile = new File( "target/peter.tar" );
		try (
				InputStream is = dockerClient.saveImageCmd( imageName ).exec();) {

			java.nio.file.Files.copy(
				is,
				targetFile.toPath(),
				StandardCopyOption.REPLACE_EXISTING );

		} catch (Exception e) {

		}

		logger.info( "create targetFile: {}", targetFile.getCanonicalPath() );
		assertThat( targetFile )
			.as( "exported image" )
			.exists();

		/**
		 * Image remove
		 */
		dockerClient
			.removeImageCmd( imageName )
			.withForce( true )
			.exec();

		logger.info( "Removed image: {}", imageName );

		try (
				InputStream uploadStream = Files.newInputStream( targetFile.toPath() )) {
			dockerClient.loadImageCmd( uploadStream ).exec();
		}

		logger.info( "loaded image targetFile: {}", targetFile.getCanonicalPath() );

		logger.warn( "loadImage leaks connections. If bypassed - this will hang on the image list" );

		// assertThat( false )
		// .as( "docker java load leak" )
		// .isTrue();

		logger.info( "\n\n - loaded image" );

		// lastFactory.close();
		// dockerClient = buildDockerClientWithShutdown() ;

		/**
		 * Image list
		 */
		verify_images_list();

		logger.info( "\n\n - listed image" );
		// Thread.sleep( 60000 );

	}

	@Test
	public void verify_container_command ()
			throws Exception {

		CreateContainerResponse createResponse = startNginx();

		logger.info( "Started" );

		String commandResult = containerCommand( createResponse.getId(), "nginx", "-v" );
		// commandResult = containerCommand( "/nginx", "ls", "-l" ) ;

		logger.info( "Command output: {}", commandResult );
		assertThat( commandResult )
			.as( "version message" )
			.contains( "nginx version" );

		logger.info( "Started success, removing: {}", createResponse.getId() );

		dockerClient
			.removeContainerCmd( createResponse.getId() )
			.withForce( true )
			.exec();

	}

	private String containerCommand ( String containerId, String... command ) {

		String commandOutput = "";
		try {

			ExecCreateCmdResponse execCreateCmdResponse = dockerClient
				.execCreateCmd( containerId )
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

		} catch (Exception e) {

			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.warn( "Failed listing files in {}, {}", containerId, reason );

		}

		logger.debug( "Container: {}, Command: {}, output: {}", containerId, Arrays.asList( command ), commandOutput );

		return commandOutput;
	}

	final public static String TEST_CONTAINER_NAME = "/csap-java-simple";
	final public static String BUSY_BOX = "busybox";

	@Test
	public void verify_docker_file_list_and_copy ()
			throws Exception {

		CreateContainerResponse createResponse = startNginx();

		logger.info( "Started nginx container, id: {}", createResponse.getId() );

		ExecCreateCmdResponse execCreateCmdResponse = dockerClient
			.execCreateCmd( createResponse.getId() )
			.withAttachStdout( true )
			.withCmd( "ls", "-al", "/etc" )
			.exec();

		ByteArrayOutputStream lsOutputStream = new ByteArrayOutputStream();
		dockerClient
			.execStartCmd( execCreateCmdResponse.getId() )
			.exec(
				new ExecStartResultCallback( lsOutputStream, lsOutputStream ) )
			.awaitCompletion();

		String fileListingOutput = new String( lsOutputStream.toByteArray(), StandardCharsets.UTF_8 );

		logger.info( "File listing: {}", fileListingOutput );

		String targetFile = "/etc/bash.bashrc";
		
		assertThat( fileListingOutput )
			.as( "found:" + targetFile)
			.contains( "bash.bashrc" );

		// targetFile = "/etc/fstab";
		logger.info( "Reading file {} from container", targetFile );

		InputStream dockerTarStream = dockerClient
			.copyArchiveFromContainerCmd(
				createResponse.getId(),
				targetFile )
			.exec();

		// read the stream fully. Otherwise, the underlying stream will not be
		// closed.
		// String responseAsString = consumeAsString( response, 10 );
		int limit = 10 * 1024;
		String responseAsString = consumeAsTar( dockerTarStream, 512, limit );
		// String responseAsString = consumeChunksAsString( response, 1000, 100
		// );

		logger.info( "First {} chars from {} : \n {}", limit, targetFile, responseAsString );
		

		assertThat( responseAsString )
			.as( "found first line in " + targetFile)
			.contains( "System-wide .bashrc file" );


		logger.info( "removing: {}", createResponse.getId() );
		dockerClient
			.removeContainerCmd( createResponse.getId() )
			.withForce( true )
			.exec();
	}

	public static String consumeAsTar ( InputStream dockerTarStream, int chunkSize, int maxSize )
			throws Exception {

		StringWriter logwriter = new StringWriter();

		try (TarArchiveInputStream tarInputStream = new TarArchiveInputStream( dockerTarStream )) {
			TarArchiveEntry tarEntry = tarInputStream.getNextTarEntry();

			logger.info( "tar name: {}", tarEntry.getName() );
			byte[] bufferAsByteArray = new byte[ chunkSize ];

			long tarEntrySize = tarEntry.getSize();

			int numReadIn = 0;
			while (tarInputStream.available() > 0 && numReadIn < maxSize && numReadIn <= tarEntrySize) {
				int numRead = IOUtils.read( tarInputStream, bufferAsByteArray );

				numReadIn += numRead;

				String stringReadIn = new String( bufferAsByteArray, 0, numRead );
				logwriter.write( stringReadIn );
				logger.debug( "numRead: {}, chunk: {}", numRead, stringReadIn );
			}
			while (IOUtils.read( dockerTarStream, bufferAsByteArray ) > 0) {
				// need to read fully or stream will leak
			}
			// response.close();

		}
		return logwriter.toString();
	}

	public static String consumeAsString ( InputStream response, int maxLines ) {

		StringWriter logwriter = new StringWriter();

		try {
			LineIterator itr = IOUtils.lineIterator( response, "UTF-8" );

			int numLines = 0;
			while (itr.hasNext() && numLines < maxLines) {
				String line = itr.next();
				logwriter.write( line + (itr.hasNext() ? "\n" : "") );
				numLines++;
				System.out.println( line );
				// logger.info( line);
				// LOG.info("line: " + line);
			}
			while (itr.hasNext())
				itr.next();
			// response.close();

		} catch (Exception e) {
			logger.error( "Failed to close: {}",
				CSAP.getCsapFilteredStackTrace( e ) );
			return "Failed";
		} finally {
			IOUtils.closeQuietly( response );
		}
		return logwriter.toString();
	}

	public static String consumeChunksAsString ( InputStream response, int maxChars, int chunkSize ) {

		StringWriter logwriter = new StringWriter();

		byte[] bufferAsByteArray = new byte[ chunkSize ];
		try {
			int numReadIn = 0;
			while (response.available() > 0 && numReadIn < maxChars) {
				int numRead = IOUtils.read( response, bufferAsByteArray );

				numReadIn += numRead;

				String stringReadIn = new String( bufferAsByteArray, 0, numRead );
				logwriter.write( stringReadIn );
				logger.debug( "numRead: {}, chunk: {}", numRead, stringReadIn );
				// LOG.info("line: " + line);
			}
			while (IOUtils.read( response, bufferAsByteArray ) > 0)
				;
			;
			// response.close();

		} catch (Exception e) {
			logger.error( "Failed to close: {}",
				CSAP.getCsapFilteredStackTrace( e ) );
			return "Failed";
		} finally {
			IOUtils.closeQuietly( response );
		}
		return logwriter.toString();
	}

	private String pp ( ObjectNode json ) {
		String result = json.toString();
		try {
			result = jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( json );
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return result;
	}

	@Test
	public void verify_volume_list ()
			throws Exception {

		ListVolumesResponse volumeResponse = dockerClient.listVolumesCmd().exec();

		if ( volumeResponse.getVolumes() == null ) {
			logger.info( "No Volumes" );
			return;
		}
		volumeResponse.getVolumes().forEach( volume -> {

			logger.info( "volume name: {}, \t string: {} , \t\t {}",
				volume.getName(), volume.toString() );

			ObjectNode volumeJson = jacksonMapper.convertValue( volume, ObjectNode.class );

			logger.info( "Json: \n {}", pp( volumeJson ) );

			InspectVolumeResponse volumeInpect = dockerClient.inspectVolumeCmd( volume.getName() ).exec();
			ObjectNode inspectJson = jacksonMapper.convertValue( volumeInpect, ObjectNode.class );

			logger.info( "inspectJson: \n {}", pp( inspectJson ) );
		} );

	}

	@Test
	public void verify_images_list ()
			throws Exception {

		logger.info( "Listing images." );
		List<Image> images = dockerClient.listImagesCmd().exec();

		images.forEach( image -> {

			logger.info( "container id: {}, \t string: {} , \t\t {}",
				image.getId(), image.toString() );

			ObjectNode imageJson = jacksonMapper.convertValue( image, ObjectNode.class );

			logger.info( "Json: \n {}", pp( imageJson ) );

			InspectImageResponse imageResponse = dockerClient.inspectImageCmd( image.getId() ).exec();

			ObjectNode inspectJson = jacksonMapper.convertValue( imageResponse, ObjectNode.class );

			logger.info( "inspectJson: \n {}", pp( inspectJson ) );
		} );

	}

	@Test
	public void verify_image_list ()
			throws Exception {

		logger.info( "Listing images." );
		List<Image> images = dockerClient.listImagesCmd().exec();

		images.forEach( image -> {

			logger.info( "container id: {}, \t tags: {} , \t\t {}",
				image.getId(), image.getRepoTags() );
		} );

	}

	@Test
	public void verify_image_pull ()
			throws Exception {

		// String registryName = "index.docker.io/v1";
		String imageName = BUSY_BOX;
		// imageName = "peterdnight/demo"; // docker.io

		// imageName = CSAP_DOCKER_REPOSITORY +"/csap-simple"; //

		try {
			dockerClient
				.removeImageCmd( imageName )
				.withForce( true )
				.exec();

			logger.info( "Removed image: {}", imageName );
		} catch (Exception e) {
			logger.error( "Failed to remove: {}, {}",
				imageName,
				CSAP.getCsapFilteredStackTrace( e ) );
		}

		String imageList = listImages();
		assertThat( imageList )
			.as( imageName + "not present" )
			.doesNotContain( imageName );

		// imageName = CSAP_DOCKER_REPOSITORY +"/csap-simple" ;

		// Info info = dockerClient.infoCmd().exec();
		// logger.info( "Docker Config: {}", info );

		loadImage( imageName );

		// handler.awaitCompletion() ;
		imageList = listImages();
		assertThat( imageList )
			.as( imageName + "is present" )
			.contains( imageName );

	}

	private void loadImage ( String imageName )
			throws InterruptedException {
		MyHandler handler = new MyHandler();

		// AuthConfig authConfig = new AuthConfig()
		// .withUsername( "peterdnight" )
		// .withPassword( "pet6er82" )
		// .withEmail( "ben@me.com" )
		// .withRegistryAddress( registryName );
		// authConfig.wi

		PullImageResultCallback cb = dockerClient
			.pullImageCmd( imageName )
			.withTag( "latest" )
			// .withAuthConfig( authConfig )
			.exec( handler ).awaitCompletion();
	}

	public class MyHandler extends PullImageResultCallback {
		@Override
		public void onNext ( PullResponseItem item ) {

			logger.debug( "response: {} ", item );

			String progress = item.getStatus();

			if ( item.getProgressDetail() != null && item.getProgressDetail().getCurrent() != null ) {
				progress += "..." + Math.round( item.getProgressDetail().getCurrent() * 100 / item.getProgressDetail().getTotal() ) + "%";
			}

			if ( item.getErrorDetail() != null ) {
				System.err.println( item.getErrorDetail().getMessage() );
			} else {

				System.out.println( progress );
			}
		}

	}

	private void loadImageIfNeeded ( String imageName )
			throws InterruptedException {

		String currentImages = listImages();

		if ( !currentImages.contains( imageName ) ) {
			logger.info( "loading: {}", imageName );
			loadImage( imageName );
		}

	}

	private String listImages () {
		// list them
		List<Image> images = dockerClient.listImagesCmd().exec();

		StringBuilder builder = new StringBuilder();
		images.forEach( image -> {
			builder.append( "\n" + Arrays.asList( image.getRepoTags() ) );
		} );

		logger.info( "Current Images: {}", builder.toString() );

		return builder.toString();
	}

	static public class LogContainerTestCallback extends LogContainerResultCallback {
		protected final StringBuffer log = new StringBuffer();

		List<Frame> collectedFrames = new ArrayList<Frame>();

		boolean collectFrames = false;
		boolean collectLog = true;

		Writer writer = null;

		public LogContainerTestCallback( ) {
			this( false );
		}

		public LogContainerTestCallback( boolean collectFrames ) {
			this.collectFrames = collectFrames;
		}

		public LogContainerTestCallback( Writer writer ) {
			this.writer = writer;
			this.collectLog = false;
		}

		@Override
		public void onNext ( Frame frame ) {
			// logger.info( "Got frame: {}", frame );
			if ( collectFrames )
				collectedFrames.add( frame );

			if ( collectLog ) {
				String lastLog = new String( frame.getPayload() );
				// logger.info( "lastLog: {}", lastLog );
				log.append( lastLog );
			}
			if ( writer != null ) {
				try {
					writer.write( new String( frame.getPayload() ) );
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			//
		}

		@Override
		public String toString () {
			return log.toString();
		}

		public List<Frame> getCollectedFrames () {
			return collectedFrames;
		}
	}

	@Test
	public void verify_container_SIMPLE_create_with_ports_and_volumes_and_variables ()
			throws Exception {

		String imageName = BUSY_BOX;
		loadImageIfNeeded( imageName );

		String containerName = "/java-simple-" + LocalDateTime.now().format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) );
		List<String> entryParameters = Arrays.asList( "nginx", "-g", "daemon off;" );
		List<String> cmdParameters = Arrays.asList( "nginx", "-v" );

		// ref. https://github.com/docker-java/docker-java/wiki
		ExposedPort tcp80 = ExposedPort.tcp( 80 );
		List<ExposedPort> exposedList = new ArrayList<>();
		// exposedList.add( tcp80 ) ;
		// ExposedPort tcp23 = ExposedPort.tcp(23);

		Ports portBindings = new Ports();
		portBindings.bind( tcp80, Ports.Binding.bindPort( 90 ) );
		// portBindings.bind( tcp80, Ports.Binding("") );

		List<Volumes> volumes = new ArrayList<>();
		Bind javaVolumeBind = new Bind( "/opt/java", new Volume( "/java" ), AccessMode.ro, SELContext.shared );

		List<String> environmentVariables = new ArrayList<>();
		environmentVariables.add( "JAVA_HOME=/opt/java" );
		environmentVariables.add( "WORKING_DIR=/working" );
		environmentVariables.add( "JAVA_OPTS=some path" );

		List<Ulimit> ulimits = new ArrayList<>();
		ulimits.add( new Ulimit( "nofile", 1000, 1000 ) );
		ulimits.add( new Ulimit( "nproc", 10, 10 ) );

		Map<String, String> jsonLogConfig = new HashMap<>();
		jsonLogConfig.put( "max-size", "10m" );
		jsonLogConfig.put( "max-file", "2" );

		LogConfig logConfig = new LogConfig( LoggingType.JSON_FILE, jsonLogConfig );
		CreateContainerResponse container = dockerClient
			.createContainerCmd( imageName )
			.withName( containerName )
			// .withCmd( cmdParameters )
			// .withEntrypoint( entryParameters )
			.withCpusetCpus( "0-1" )
			.withLogConfig( logConfig )
			.withCpuPeriod( 400000 )
			.withMemory( 20 * CSAP.MB_FROM_BYTES )
			.withUlimits( ulimits )
			.withExposedPorts( exposedList )
			.withPortBindings( portBindings )
			.withBinds( javaVolumeBind )
			.withHostName( "peter" )
			// .withNetworkMode( "host" )
			.withEnv( environmentVariables )
			.exec();

		InspectContainerResponse containerInfo = getContainerStatus( containerName );

		ObjectNode containerInfoJson = jacksonMapper.convertValue( containerInfo, ObjectNode.class );
		logger.info( "Name: {} ,  InfoJson: \n {}", containerInfo.getName(), pp( containerInfoJson ) );

		dockerClient.startContainerCmd( container.getId() )
			.exec();

		dockerClient
			.removeContainerCmd( container.getId() )
			.withRemoveVolumes( true )
			.withForce( true )
			.exec();
	}

	@Test
	public void verify_nginx_with_port_mappings_and_get ()
			throws Exception {

		CreateContainerResponse createResponse = startNginx();

		logger.info( "Started success, removing: {}", createResponse.getId() );

		dockerClient
			.removeContainerCmd( createResponse.getId() )
			.withForce( true )
			.exec();

	}

	private CreateContainerResponse startNginx ()
			throws InterruptedException, IOException {
		String imageName = "nginx";
		loadImageIfNeeded( imageName );
		String containerName = "/junit-nginx-"
				+ LocalDateTime.now().format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) );

		List<String> entryParameters = Arrays.asList( "nginx", "-g", "daemon off;" );
		List<String> cmdParameters = Arrays.asList( "nginx", "-v" );

		ExposedPort exposedPort = ExposedPort.tcp( 7080 );
		List<ExposedPort> exposedList = new ArrayList<>();
		exposedList.add( exposedPort );

		Ports portBindings = new Ports();
		portBindings.bind(
			ExposedPort.tcp( 80 ),
			Ports.Binding.bindPort( exposedPort.getPort() ) );

		CreateContainerResponse createResponse = dockerClient
			.createContainerCmd( imageName )
			.withName( containerName )
			// .withCmd( cmdParameters )
			.withEntrypoint( entryParameters )
			.withExposedPorts( exposedList )
			.withPortBindings( portBindings )
			.exec();

		dockerClient
			.startContainerCmd( createResponse.getId() )
			.exec();

		Thread.sleep( 500 );
		RestTemplate springTemplate = new RestTemplate();
		String testUrl = "http://localhost:" + exposedPort.getPort();
		String response = springTemplate.getForObject( testUrl, String.class );

		logger.info( "Testing url: {} \n\t response: {}", testUrl, response );

		assertThat( response )
			.as( "welcome message" )
			.contains( "Welcome to nginx!" );

		int maxAttempts = 20;
		String startUpMessage = "GET";

		boolean foundStartMessage = waitForMessageInLogs(
			createResponse.getId(), maxAttempts, startUpMessage );

		assertThat( foundStartMessage )
			.as( "found in logs:" + startUpMessage )
			.isTrue();
		return createResponse;
	}

	private static String CSAP_DOCKER_REPOSITORY="updateThis";
	@Ignore
	@Test
	public void verify_csap_base ()
			throws Exception {

		// ref. https://github.com/docker-java/docker-java/wiki

		String containerName = "/junit-csapbase-"
				+ LocalDateTime.now().format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) );

		String imageName = CSAP_DOCKER_REPOSITORY +"/csap-base";
		// imageName = "centos";
		loadImageIfNeeded( imageName );

		CreateContainerResponse containerResponse = dockerClient.createContainerCmd( imageName )
			.withName( containerName )
			.withWorkingDir( "/" )
			.exec();

		dockerClient
			.startContainerCmd( containerResponse.getId() )
			.exec();

		// Thread.sleep( 3000 );
		int exitCode = dockerClient.waitContainerCmd( containerResponse.getId() )
			.exec( new WaitContainerResultCallback() )
			.awaitStatusCode( 5, TimeUnit.SECONDS );

		logger.info( "Container exited with: {}", exitCode );

		LogContainerTestCallback loggingCallback = new LogContainerTestCallback( false );
		dockerClient
			.logContainerCmd( containerResponse.getId() )
			.withStdErr( true )
			.withStdOut( true )
			.withTailAll()
			.exec( loggingCallback );

		loggingCallback.awaitCompletion( 3, TimeUnit.SECONDS );

		logger.info( "logs: \n {} ", loggingCallback.toString() );

		loggingCallback.close();

		assertThat( loggingCallback.toString() )
			.as( "csap-base logs" )
			.contains( "Java HotSpot(TM) 64-Bit Server VM" );

		dockerClient
			.removeContainerCmd( containerResponse.getId() )
			.withForce( true )
			.exec();
	}

	@Ignore
	@Test
	public void verify_csap_test_app_default ()
			throws Exception {

		// ref. https://github.com/docker-java/docker-java/wiki

		String containerName = "/junit-csap-test-app-default"
				+ LocalDateTime.now().format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) );

		String imageName = CSAP_DOCKER_REPOSITORY +"/csap-test-app";
		// imageName = "centos";
		loadImageIfNeeded( imageName );

		CreateContainerResponse containerResponse = dockerClient.createContainerCmd( imageName )
			.withName( containerName )
			.withWorkingDir( "/" )
			.exec();

		dockerClient
			.startContainerCmd( containerResponse.getId() )
			.exec();

		// Thread.sleep( 3000 );

		int maxAttempts = 20;
		String startUpMessage = "Started BootEnterpriseApplication";

		boolean foundStartMessage = waitForMessageInLogs(
			containerResponse.getId(), maxAttempts, startUpMessage );

		assertThat( foundStartMessage )
			.as( "found in logs:" + startUpMessage )
			.isTrue();

		logger.info( "Started success, removing: {}", containerResponse.getId() );

		dockerClient
			.removeContainerCmd( containerResponse.getId() )
			.withForce( true )
			.exec();
	}

	private boolean waitForMessageInLogs ( String containerId, int maxAttempts, String startUpMessage )
			throws InterruptedException, IOException {

		boolean foundStartMessage = false;
		int since = (int) (System.currentTimeMillis() / 1000);
		logger.info( "{} Waiting for start  - up to {}  seconds...",
			containerId, maxAttempts );

		for ( int attempt = 1; attempt < maxAttempts; attempt++ ) {
			// completed = dockerClient.waitContainerCmd(
			// containerResponse.getId() )
			// .exec( new WaitContainerResultCallback() )
			// .awaitCompletion( 1, TimeUnit.SECONDS ) ;
			//
			// logger.info( "Container completed: {}", completed );

			Thread.sleep( 1000 );

			LogContainerTestCallback loggingCallback = new LogContainerTestCallback( false );
			dockerClient
				.logContainerCmd( containerId )
				.withStdErr( true )
				.withStdOut( true )
				.withSince( since )
				.exec( loggingCallback );

			loggingCallback.awaitCompletion( 3, TimeUnit.SECONDS );
			loggingCallback.close();

			logger.info( "attempt: {} since {} : \n {} \n looking for: {} ",
				attempt, since, loggingCallback.toString(), startUpMessage );
			since = (int) (System.currentTimeMillis() / 1000);

			if ( loggingCallback.toString().contains( startUpMessage ) ) {
				foundStartMessage = true;
				loggingCallback.close();
				break;
			}

		}
		return foundStartMessage;
	}

	@Ignore
	@Test
	public void verify_csap_test_app_with_limits_logs ()
			throws Exception {

		// ref. https://github.com/docker-java/docker-java/wiki

		String containerName = "/junit-csap-test-app-custom"
				+ LocalDateTime.now().format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) );

		String imageName = CSAP_DOCKER_REPOSITORY +"/csap-test-app";
		// imageName = "centos";
		loadImageIfNeeded( imageName );

		int serverPort = 8080;
		int publicPort = 7080;

		List<String> entryParameters = Arrays.asList(
			"java", "-Xms256M", "-Xmx256M",
			"-Dspring.profiles.active=embedded",
			"-DcsapJmxPort=8086",
			"-Dserver.port=" + serverPort,
			"-jar",
			"/csapTest.jar" );

		List<ExposedPort> exposedList = new ArrayList<>();
		exposedList.add( ExposedPort.tcp( publicPort ) );
		// ExposedPort tcp23 = ExposedPort.tcp(23);

		Ports portBindings = new Ports();
		portBindings.bind(
			ExposedPort.tcp( serverPort ),
			Ports.Binding.bindPort( publicPort ) );
		// portBindings.bind( tcp80, Ports.Binding("") ); // exports as same
		// port

		List<Volumes> volumes = new ArrayList<>();
		Bind javaVolumeBind = new Bind( "/opt/test", new Volume( "/testHostVolume" ), AccessMode.ro, SELContext.shared );

		List<String> environmentVariables = new ArrayList<>();
		environmentVariables.add( "testVar=some Var" );

		List<Ulimit> ulimits = new ArrayList<>();
		ulimits.add( new Ulimit( "nofile", 1000, 1000 ) );
		ulimits.add( new Ulimit( "nproc", 200, 200 ) );

		Map<String, String> jsonLogConfig = new HashMap<>();
		jsonLogConfig.put( "max-size", "10m" );
		jsonLogConfig.put( "max-file", "2" );

		LogConfig logConfig = new LogConfig( LoggingType.JSON_FILE, jsonLogConfig );
		CreateContainerResponse containerResponse = dockerClient
			.createContainerCmd( imageName )
			.withName( containerName )

			// .withCmd( cmdParameters )
			.withEntrypoint( entryParameters )
			.withEnv( environmentVariables )

			// resources
			.withCpusetCpus( "0-1" )
			.withLogConfig( logConfig )
			.withCpuPeriod( 400000 )
			.withMemory( 500 * CSAP.MB_FROM_BYTES )
			.withUlimits( ulimits )

			// network
			.withPortBindings( portBindings )
			.withExposedPorts( exposedList )
			// .withNetworkMode( "host" )
			.withBinds( javaVolumeBind )
			.exec();

		dockerClient
			.startContainerCmd( containerResponse.getId() )
			.exec();

		// Thread.sleep( 3000 );

		int maxAttempts = 20;
		String startUpMessage = "Started BootEnterpriseApplication";

		boolean foundStartMessage = waitForMessageInLogs(
			containerResponse.getId(), maxAttempts, startUpMessage );

		assertThat( foundStartMessage )
			.as( "found in logs:" + startUpMessage )
			.isTrue();

		logger.info( "Started success, removing: {}", containerResponse.getId() );

		dockerClient
			.removeContainerCmd( containerResponse.getId() )
			.withForce( true )
			.exec();
	}

	@Test
	public void verify_container_with_user ()
			throws Exception {

		String imageName = BUSY_BOX;
		loadImageIfNeeded( imageName );
		String containerName = "/junit-container-with-user-"
				+ LocalDateTime.now().format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) );

		CreateContainerResponse createResponse = dockerClient.createContainerCmd( imageName )
			.withName( containerName )
			.withWorkingDir( "/" )
			.withRestartPolicy( RestartPolicy.onFailureRestart( 3 ) )
			.withUser( "1001:1001" ) // uid:gid
			// .withCmd( "ls", "-l" )
			.withCmd( "id" )
			.exec();

		logger.info( "Create: {}", createResponse );

		dockerClient
			.startContainerCmd( createResponse.getId() )
			.exec();

		LogContainerTestCallback loggingCallback = new LogContainerTestCallback( false );

		dockerClient
			.logContainerCmd( createResponse.getId() )
			.withStdErr( true )
			.withStdOut( true )
			.withTailAll()
			.exec( loggingCallback );
		//
		loggingCallback.awaitCompletion( 3, TimeUnit.SECONDS );

		logger.info( "{} logs: \n {} ", containerName, loggingCallback.toString() );

		loggingCallback.close();

		assertThat( loggingCallback.toString() )
			.as( "id command output" )
			.contains( "uid=1001 gid=1001" );

		dockerClient
			.removeContainerCmd( createResponse.getId() )
			.withRemoveVolumes( true )
			.withForce( true )
			.exec();

		logger.info( "Container removed: {}", createResponse.getId() );

	}

	@Test
	public void verify_container_logs ()
			throws Exception {

		String imageName = "hello-world";
		loadImageIfNeeded( imageName );
		String containerName = "/junit-logtest-"
				+ LocalDateTime.now().format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) );

		CreateContainerResponse createResponse = dockerClient
			.createContainerCmd( imageName )
			.withName( containerName )
			.withCmd( "/hello" )
			.withTty( true )
			.exec();

		// CreateContainerResponse createResponse =
		// dockerClient.createContainerCmd( "busybox" )
		// .withCmd( "/bin/ls" )
		// .withName( containerName )
		// .exec();

		logger.info( "Create: {}", createResponse );

		dockerClient
			.startContainerCmd( createResponse.getId() )
			.exec();

		int exitCode = dockerClient.waitContainerCmd( createResponse.getId() )
			.exec( new WaitContainerResultCallback() )
			.awaitStatusCode( 5, TimeUnit.SECONDS );

		LogContainerTestCallback loggingCallback = new LogContainerTestCallback( false );

		dockerClient
			.logContainerCmd( createResponse.getId() )
			.withStdErr( true )
			.withStdOut( true )
			.withTailAll()
			.exec( loggingCallback );
		//
		loggingCallback.awaitCompletion( 3, TimeUnit.SECONDS );

		logger.info( "{} logs: \n {} ", containerName, loggingCallback.toString() );

		loggingCallback.close();

		assertThat( loggingCallback.toString() )
			.as( "HelloWorld logs" )
			.contains( "Hello from Docker!" );

		dockerClient
			.removeContainerCmd( createResponse.getId() )
			.withRemoveVolumes( true )
			.withForce( true )
			.exec();

		logger.info( "Container removed: {}", createResponse.getId() );

	}

	@Test
	public void verify_container_logs_stream ()
			throws Exception {

		String imageName = "hello-world";
		loadImageIfNeeded( imageName );
		String containerName = "/junit-logtest-"
				+ LocalDateTime.now().format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) );

		CreateContainerResponse createResponse = dockerClient
			.createContainerCmd( imageName )
			.withName( containerName )
			.withCmd( "/hello" )
			.withTty( true )
			.exec();

		// CreateContainerResponse createResponse =
		// dockerClient.createContainerCmd( "busybox" )
		// .withCmd( "/bin/ls" )
		// .withName( containerName )
		// .exec();

		logger.info( "Create: {}", createResponse );

		dockerClient
			.startContainerCmd( createResponse.getId() )
			.exec();

		int exitCode = dockerClient.waitContainerCmd( createResponse.getId() )
			.exec( new WaitContainerResultCallback() )
			.awaitStatusCode( 5, TimeUnit.SECONDS );

		StringWriter sw = new StringWriter();
		LogContainerTestCallback loggingCallback = new LogContainerTestCallback( sw );

		dockerClient
			.logContainerCmd( createResponse.getId() )
			.withStdErr( true )
			.withStdOut( true )
			.withTailAll()
			.exec( loggingCallback );
		//
		loggingCallback.awaitCompletion( 3, TimeUnit.SECONDS );

		sw.flush();
		logger.info( "{} logs from stream: \n {} ", containerName, sw.toString() );

		loggingCallback.close();

		assertThat( sw.toString() )
			.as( "HelloWorld logs" )
			.contains( "Hello from Docker!" );

		dockerClient
			.removeContainerCmd( createResponse.getId() )
			.withRemoveVolumes( true )
			.withForce( true )
			.exec();

		logger.info( "Container removed: {}", createResponse.getId() );

		loggingCallback.close();

	}

	@Test
	public void verify_containers_list ()
			throws Exception {

		List<Container> containers = dockerClient.listContainersCmd().withShowAll( true ).exec();

		containers.forEach( container -> {

			logger.info( "container id: {}, \tnames: {} , \t\t {}",
				container.getId(), container.getNames(), container.toString() );

			ObjectNode containerJson = jacksonMapper.convertValue( container, ObjectNode.class );

			logger.info( "Json: \n {}", pp( containerJson ) );
		} );

	}

	@Test
	public void verify_containers_created_date ()
			throws Exception {

		Optional<Container> matchContainer = findContainerByName( "/peter" );

		if ( matchContainer.isPresent() ) {
			Container c = matchContainer.get();
			LocalDateTime date = LocalDateTime.ofInstant( Instant.ofEpochMilli( c.getCreated() * 1000 ), ZoneId.systemDefault() );

			logger.info( "name: {}, created: {} , text: {}",
				Arrays.asList( c.getNames() ), c.getCreated(),
				date.format( DateTimeFormatter.ofPattern( "HH:mm:ss MMM d, yyyy" ) ) );

		}

	}

	@Test
	public void verify_containers_info ()
			throws Exception {

		String imageName = BUSY_BOX;
		loadImageIfNeeded( imageName );
		String containerName = "/junit-info-"
				+ LocalDateTime.now().format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) );

		CreateContainerResponse createResponse = dockerClient.createContainerCmd( imageName )
			.withCmd( "/bin/ls" )
			.withName( containerName )
			.exec();

		logger.info( "Create: {}", createResponse );

		dockerClient
			.startContainerCmd( createResponse.getId() )
			.exec();

		Optional<Container> matchContainer = findContainerByName( containerName );

		assertThat( matchContainer.isPresent() )
			.as( "found container" )
			.isTrue();

		ObjectNode containerJson = jacksonMapper.convertValue( matchContainer.get(), ObjectNode.class );
		logger.info( " Json: \n {}", pp( containerJson ) );

		InspectContainerResponse containerInfo = getContainerStatus( containerName );

		ObjectNode containerInfoJson = jacksonMapper.convertValue( containerInfo, ObjectNode.class );
		logger.info( "Name: {} ,  InfoJson: \n {}", containerInfo.getName(), pp( containerInfoJson ) );

		dockerClient
			.removeContainerCmd( createResponse.getId() )
			.withRemoveVolumes( true )
			.withForce( true )
			.exec();

		logger.info( "Container removed: {}", createResponse.getId() );

	}

	private Optional<Container> findContainerByName ( String name ) {
		List<Container> containers = dockerClient.listContainersCmd().withShowAll( true ).exec();

		Optional<Container> matchContainer = containers
			.stream()
			.filter( container -> Arrays.asList( container.getNames() ).contains( name ) )
			.findFirst();
		return matchContainer;
	}

	private InspectContainerResponse getContainerStatus ( String name ) {

		Optional<Container> matchContainer = findContainerByName( name );
		if ( matchContainer.isPresent() ) {
			return dockerClient.inspectContainerCmd( matchContainer.get().getId() ).exec();
		}
		return null;
	}

	private InspectContainerResponse waitForRunning ( String containerName, boolean isRunning ) {
		InspectContainerResponse containerInfo;
		while (true) {

			containerInfo = getContainerStatus( containerName );
			logger.info( "Waiting for: {} current: {}", isRunning, containerInfo.getState().getRunning() );

			if ( isRunning == containerInfo.getState().getRunning() ) {
				break;
			}

			try {
				Thread.sleep( 10 );
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return containerInfo;
	}

	@Ignore
	@Test
	public void verify_containers_stop ()
			throws Exception {

		String containerName = "/peter";
		Optional<Container> matchContainer = findContainerByName( containerName );

		if ( matchContainer.isPresent() ) {
			Container container = matchContainer.get();

			InspectContainerResponse containerInfo = getContainerStatus( containerName );

			// containerInfo.

			logger.info( "Running: {} status: {}", containerInfo.getState().getRunning(), container.getStatus() );

			dockerClient.stopContainerCmd( matchContainer.get().getId() ).exec();
			containerInfo = waitForRunning( containerName, false );

			logger.info( "Running: {} status: {}", containerInfo.getState().getRunning(), container.getStatus() );
		}
		;

	}

	@Ignore
	@Test
	public void verify_containers_start ()
			throws Exception {

		String containerName = "/peter";
		Optional<Container> matchContainer = findContainerByName( containerName );

		if ( matchContainer.isPresent() ) {
			Container container = matchContainer.get();

			InspectContainerResponse containerInfo = getContainerStatus( containerName );

			// containerInfo.

			logger.info( "Running: {} status: {}", containerInfo.getState().getRunning(), container.getStatus() );

			dockerClient.startContainerCmd( matchContainer.get().getId() ).exec();

			containerInfo = waitForRunning( containerName, true );

			logger.info( "Running: {} status: {}",
				containerInfo.getState().getRunning(),
				findContainerByName( containerName ).get().getStatus() );
		}
		;

	}

	@Ignore
	@Test
	public void verify_containers_stop_and_start ()
			throws Exception {

		verify_containers_stop();
		verify_containers_start();
	}

}
