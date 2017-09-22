package org.csap.agent.input.http.ui.rest;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;

import org.csap.agent.CSAP;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.Application;
import org.csap.agent.services.DockerContainerLogCallback;
import org.csap.agent.services.DockerHelper;
import org.csap.agent.services.DockerJson;
import org.csap.agent.services.OsManager;
import org.csap.integations.CsapSecurityConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.InspectVolumeResponse;
import com.github.dockerjava.api.command.ListVolumesResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Info;

@RestController
@RequestMapping ( ExplorerRequests.EXPLORER_URL )
public class ExplorerRequests {

	private static final String UNABLE_TO_CONNECT_TO_DOCKER = "Unable to connect to docker - ensure CSAP docker package is installed.";
	final Logger logger = LoggerFactory.getLogger( this.getClass() );
	ObjectMapper jacksonMapper = new ObjectMapper();

	public final static String EXPLORER_URL = "/explorer";

	@Inject
	public ExplorerRequests(
			Application csapApp,
			OsManager osManager,
			CsapEventClient csapEventClient ) {

		this.csapApp = csapApp;
		this.osManager = osManager;
		this.csapEventClient = csapEventClient;
	}

	Application csapApp;
	OsManager osManager;

	@Autowired ( required = false )
	CsapSecurityConfiguration securityConfig;

	CsapEventClient csapEventClient;

	// https://github.com/docker-java/docker-java
	@Autowired ( required = false )
	DockerClient dockerClient;

	@Inject
	DockerHelper dockerHelper;

	@GetMapping ( "/configuration" )
	public JsonNode dockerConfiguration () {

		ObjectNode result = jacksonMapper.createObjectNode();
		try {
			Info info = dockerClient.infoCmd().exec();
			result = jacksonMapper.convertValue( info, ObjectNode.class );
		} catch (Exception e) {
			logger.warn( "Failed connecting to docker: {}", CSAP.getCsapFilteredStackTrace( e ) );
			// result = jacksonMapper.createArrayNode();
			// ObjectNode item = result.addObject();
			result.put( DockerJson.error.key, UNABLE_TO_CONNECT_TO_DOCKER );
		}

		return result;
	}

	@GetMapping ( "/volumes" )
	public JsonNode volumes () {

		ArrayNode result;
		try {
			ArrayNode volumeListing = jacksonMapper.createArrayNode();
			ListVolumesResponse volumeResponse = dockerClient.listVolumesCmd().exec();

			if ( volumeResponse.getVolumes() == null ) {

				ObjectNode item = volumeListing.addObject();
				item.put( DockerJson.error.key, "No volumes defined" );
			} else {
				volumeResponse.getVolumes().forEach( volume -> {

					logger.info( "volume name: {}, \t string: {} , \t\t {}",
						volume.getName(), volume.toString() );

					ObjectNode volumeJson = jacksonMapper.convertValue( volume, ObjectNode.class );

					logger.debug( "volume: \n {}", volume.toString() );

					ObjectNode item = volumeListing.addObject();
					item.put( "label", volume.getName() );

					InspectVolumeResponse volumeInpect = dockerClient.inspectVolumeCmd( volume.getName() ).exec();
					ObjectNode inspectJson = jacksonMapper.convertValue( volumeInpect, ObjectNode.class );

					item.set( "attributes", inspectJson );
					item.put( "folder", true );
					item.put( "lazy", true );
				} );
			}

			result = volumeListing;
		} catch (Exception e) {
			logger.warn( "Failed connecting to docker: {}", CSAP.getCsapFilteredStackTrace( e ) );
			result = jacksonMapper.createArrayNode();
			ObjectNode item = result.addObject();
			item.put( DockerJson.error.key, UNABLE_TO_CONNECT_TO_DOCKER );
		}

		return result;
	}

	@GetMapping ( "/images" )
	public JsonNode imagesList () {

		ArrayNode result;
		try {
			ArrayNode imageListing = jacksonMapper.createArrayNode();
			List<Image> images = dockerClient.listImagesCmd().exec();

			// logger.info( "images: {}",images );

			images.forEach( image -> {

				logger.debug( "image: \n {}", image.toString() );

				ObjectNode item = imageListing.addObject();

				String label = "null";
				if ( image.getRepoTags() != null ) {

					label = Arrays.asList( image.getRepoTags() ).toString();
					if ( image.getRepoTags().length == 1 ) {
						label = image.getRepoTags()[0];
					}

					logger.debug( "tags: {}, length: {} ", image.getRepoTags(), image.getRepoTags().length );
					if ( label.equals( "<none>:<none>" ) ) {
						label = image.getId();
					}
				} else {
					label = image.getId();
				}

				item.put( "label", label );

				InspectImageResponse imageResponse = dockerClient.inspectImageCmd( image.getId() ).exec();

				ObjectNode inspectJson = jacksonMapper.convertValue( imageResponse, ObjectNode.class );

				item.set( "attributes", inspectJson );
				item.put( "folder", true );
				item.put( "lazy", true );
			} );

			if ( images.size() == 0 ) {
				ObjectNode item = imageListing.addObject();
				item.put( DockerJson.error.key, "No images defined" );
			}
			result = imageListing;
		} catch (Exception e) {
			logger.warn( "Failed connecting to docker: {}", CSAP.getCsapFilteredStackTrace( e ) );
			result = jacksonMapper.createArrayNode();
			ObjectNode item = result.addObject();
			item.put( DockerJson.error.key, UNABLE_TO_CONNECT_TO_DOCKER );
		}

		return result;
	}

	@GetMapping ( "/image/info" )
	public ObjectNode imageInfo (
									String id,
									String name )
			throws Exception {
		ObjectNode result = jacksonMapper.createObjectNode();
		try {

			Optional<Image> match = dockerHelper.findImageByName( name );

			if ( match.isPresent() ) {

				InspectImageResponse info = dockerClient.inspectImageCmd( match.get().getId() ).exec();
				result = jacksonMapper.convertValue( info, ObjectNode.class );
			} else {

				result.put( DockerJson.error.key, "Failed to locate image with name: " + name );
			}

		} catch (Exception e) {
			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.warn( "Failed starting {}, {}", name, reason );
			result.put( DockerJson.error.key, "Failed starting: " + name );
			result.put( DockerJson.errorReason.key, reason );
		}

		return result;
	}

	@GetMapping ( "/image/pull/progress" )
	public String imagePullProgress (
										@RequestParam ( defaultValue = "0" ) int offset ) {
		return dockerHelper.getLastResults( offset );
	}

	@PostMapping ( "/image/pull" )
	public ObjectNode imagePull (
									String id,
									String name )
			throws Exception {

		issueAudit( "Pulling Image: " + name, null );

		return dockerHelper.imagePull( name, null, 3 );
	}

	@PostMapping ( "/image/remove" )
	public ObjectNode imageRemove (
									String id,
									String name )
			throws Exception {

		issueAudit( "Removing Image: " + name, null );

		return dockerHelper.imageRemove( id, name );
	}

	@GetMapping ( "/containers" )
	public ArrayNode containersListing (
											@RequestParam ( value = "testUser" , required = false ) String testUser,
											@RequestParam ( value = "new" , required = false ) String preferencesString )
			throws Exception {
		ArrayNode result;
		try {
			ArrayNode containerListing = jacksonMapper.createArrayNode();

			List<Container> containers = dockerClient.listContainersCmd().withShowAll( true ).exec();

			containers.forEach( container -> {
				logger.debug( "container: \n {}", container.toString() );
				ObjectNode item = containerListing.addObject();

				String label = Arrays.asList( container.getNames() ).toString();
				if ( container.getNames().length == 1 ) {
					label = container.getNames()[0];
				}

				item.put( "label", label );

				ObjectNode attributes = jacksonMapper.convertValue( container, ObjectNode.class );
				item.set( "attributes", attributes );
				attributes.put( "Create Date", dockerHelper.getContainerCreationTime( container ) );
				item.put( "folder", true );
				item.put( "lazy", true );

			} );

			if ( containers.size() == 0 ) {
				ObjectNode item = containerListing.addObject();
				item.put( DockerJson.error.key, "No containers defined" );
			}

			result = containerListing;
		} catch (Exception e) {
			logger.warn( "Failed connecting to docker: {}", CSAP.getCsapFilteredStackTrace( e ) );
			result = jacksonMapper.createArrayNode();
			ObjectNode item = result.addObject();
			item.put( DockerJson.error.key, UNABLE_TO_CONNECT_TO_DOCKER );
		}

		return result;
	}

	@GetMapping ( "/container/info" )
	public ObjectNode containerInfo (
										String id,
										String name )
			throws Exception {
		ObjectNode result = jacksonMapper.createObjectNode();
		try {

			InspectContainerResponse info = dockerHelper.containerConfiguration( name );
			return jacksonMapper.convertValue( info, ObjectNode.class );

		} catch (Exception e) {
			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.warn( "Failed starting {}, {}", name, reason );
			result.put( DockerJson.error.key, "Failed starting: " + name );
			result.put( DockerJson.errorReason.key, reason );
		}

		return result;
	}

	@GetMapping ( "/container/sockets" )
	public ObjectNode containerSockets (
											String id,
											String name )
			throws Exception {
		ObjectNode result = jacksonMapper.createObjectNode();
		try {

			InspectContainerResponse info = dockerHelper.containerConfiguration( name );

			String socketInfo = dockerHelper.dockerOpenSockets( info.getState().getPid() + "" );
			result.put( "result", "socket info for pid: " + info.getState().getPid() );
			result.put( "plainText", socketInfo );

		} catch (Exception e) {
			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.warn( "Failed starting {}, {}", name, reason );
			result.put( DockerJson.error.key, "Failed starting: " + name );
			result.put( DockerJson.errorReason.key, reason );
		}

		return result;
	}

	@GetMapping ( "/container/processTree" )
	public ObjectNode containerProcessTree (
												String id,
												String name )
			throws Exception {
		ObjectNode result = jacksonMapper.createObjectNode();
		try {

			InspectContainerResponse info = dockerHelper.containerConfiguration( name );

			String socketInfo = osManager.getProcessTree( info.getState().getPid() + "", name );
			result.put( "result", "process tree for pid: " + info.getState().getPid() );
			result.put( "plainText", socketInfo );

		} catch (Exception e) {
			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.warn( "Failed starting {}, {}", name, reason );
			result.put( DockerJson.error.key, "Failed starting: " + name );
			result.put( DockerJson.errorReason.key, reason );
		}

		return result;
	}

	@GetMapping ( value = "/container/tail" , produces = MediaType.TEXT_HTML_VALUE )
	public void containerTailStream (
										String id,
										String name,
										HttpServletResponse response,
										@RequestParam ( defaultValue = "500" ) int numberOfLines )
			throws Exception {
		dockerHelper.containerTailStream( id, name, response, numberOfLines );
	}

	@GetMapping ( "/container/tail" )
	public ObjectNode containerTail (
										String id,
										String name,
										@RequestParam ( defaultValue = "500" ) int numberOfLines,
										@RequestParam ( defaultValue = "0" ) int since )
			throws Exception {

		return dockerHelper.containerTail( id, name, numberOfLines, since );
	}

	private void issueAudit ( String commandDesc, String details ) {

		csapEventClient.generateEvent( "docker",
			securityConfig.getUserIdFromContext(),
			commandDesc, details );
	}

	@PostMapping ( "/container/cpuQuota" )
	public ObjectNode containerCpuQuota (
											String name,
											Integer periodMs,
											Integer quotaMs )
			throws Exception {

		issueAudit( name + ": Updating CPU quota: " + quotaMs + "ms, period: " + periodMs, null );

		ObjectNode result = jacksonMapper.createObjectNode();
		try {
			result.put( "plainText",
				dockerHelper.updateContainerCpuAllow( periodMs, quotaMs, dockerHelper.findContainerByName( name ).get().getId() ) );
		} catch (Exception e) {

			String reason = CSAP.getCsapFilteredStackTrace( e );
			logger.warn( "Failed updating {}, {}", name, reason );

			result.put( DockerJson.error.key, "Failed updaing: " + name );
			result.put( DockerJson.errorReason.key, reason );

		}
		return result;
	}

	@PostMapping ( "/container/create" )
	public ObjectNode containerCreate (
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

		issueAudit( "creating container: " + name + " from image: " + usingImage, null );

		return dockerHelper.containerCreate(
			null, start, usingImage, name,
			command, entry, workingDirectory,
			networkMode, restartPolicy, runUser,
			ports, volumes, environmentVariables,
			limits );
	}

	@PostMapping ( "/container/start" )
	public ObjectNode containerStart (
										String id,
										String name )
			throws Exception {

		issueAudit( "starting container: " + name + "id: " + id, null );
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

	private String getContainerId ( String id, String name ) {
		String targetId = id;
		if ( id == null || id.isEmpty() ) {
			Optional<Container> matchContainer = dockerHelper.findContainerByName( name );
			targetId = matchContainer.get().getId();
		}
		return targetId;
	}

	@PostMapping ( "/container/stop" )
	public ObjectNode containerStop (
										String id,
										String name,
										boolean kill,
										int stopSeconds )
			throws Exception {

		issueAudit( "starting container: " + name, null );

		return dockerHelper.containerStop( id, name, kill, stopSeconds );
	}

	@PostMapping ( "/container/remove" )
	public ObjectNode containerRemove (
										String id,
										String name,
										boolean force,
										boolean removeVolumes )
			throws Exception {

		issueAudit( "removeing container: " + name, null );
		return dockerHelper.containerRemove( id, name, force, removeVolumes );
	}

	@RequestMapping ( "/memory" )
	public JsonNode memory ()
			throws Exception {

		return osManager.getMemoryStatistics();
	}

	@RequestMapping ( "/disk" )
	public JsonNode disk ()
			throws Exception {

		return osManager.getCachedFileSystemInfo();
	}

	@RequestMapping ( "/cpu" )
	public JsonNode cpu ()
			throws Exception {

		return osManager.getServiceMetrics( true ).get( "mp" );
	}

	@RequestMapping ( "/services" )
	public ArrayNode services ()
			throws Exception {
		ArrayNode result = jacksonMapper.createArrayNode();

		ObjectNode serviceMetricsJson = osManager.getServiceMetrics( true );

		JsonNode processItems = serviceMetricsJson.get( "ps" );

		processItems.fieldNames().forEachRemaining( name -> {
			JsonNode processAttributes = processItems.get( name );
			ObjectNode item = result.addObject();
			item.put( "label", name );

			item.set( "attributes", processAttributes );

			item.put( "folder", true );
			item.put( "lazy", true );
		} );

		return result;
	}

	@RequestMapping ( "/processes" )
	public ArrayNode processes ()
			throws Exception {
		ArrayNode processesGroupedByCommandPath = jacksonMapper.createArrayNode();

		osManager.checkForProcessStatusUpdate();

		ArrayNode processStatusItems = osManager.processStatus();
		ObjectNode processKeys = jacksonMapper.createObjectNode();
		ObjectNode rootKeys = jacksonMapper.createObjectNode();

		processStatusItems.forEach( processAttributes -> {

			String processCommandPath = "";
			String[] params = processAttributes.get( "command" ).asText().split( " " );
			if ( params.length >= 3 )
				processCommandPath = params[2];

			if ( processCommandPath.trim().length() == 0 ) {
				// every process should have a command path - but just in case a
				// parising error.
				processCommandPath += "Pid: " + processAttributes.get( "pid" ).asText();
			}

			if ( !processKeys.has( processCommandPath ) ) {
				ObjectNode keyItem = processesGroupedByCommandPath.addObject();

				processKeys.set( processCommandPath, keyItem );
				keyItem.put( "label", processCommandPath );

				keyItem.putArray( "attributes" );
				keyItem.put( "folder", true );
				keyItem.put( "lazy", true );

			}

			ArrayNode keyList = (ArrayNode) processKeys.get( processCommandPath ).get( "attributes" );
			keyList.add( processAttributes );

		} );

		return processesGroupedByCommandPath;
	}
}
