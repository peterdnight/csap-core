package org.csap.agent.input.http.ui.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import static org.csap.agent.model.Application.VALIDATION_ERRORS;
import static org.csap.agent.services.SourceControlManager.CONFIG_SUFFIX_FOR_UPDATE;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.csap.agent.CsapCoreService;
import org.csap.agent.CSAP;
import org.csap.agent.input.http.ui.windows.CorePortals;
import org.csap.agent.linux.OutputFileMgr;
import org.csap.agent.linux.TransferManager;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.Application;
import org.csap.agent.model.DefinitionParser;
import org.csap.agent.model.ReleasePackage;
import org.csap.agent.model.ServiceAttributes;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.services.DockerHelper;
import org.csap.agent.services.DockerJson;
import org.csap.agent.services.SourceControlManager;
import org.csap.agent.services.SourceControlManager.ScmProvider;
import org.csap.agent.stats.JmxCommonEnum;
import org.csap.agent.stats.MetricCategory;
import org.csap.agent.stats.OsProcessEnum;
import org.csap.agent.stats.OsSharedEnum;
import org.csap.docs.CsapDoc;
import org.csap.integations.CsapEncryptableProperties;
import org.csap.security.CsapUser;
import org.eclipse.jgit.api.errors.TransportException;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring4.SpringTemplateEngine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.FileReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.PostConstruct;
import static org.csap.agent.input.http.ui.rest.ServiceRequests.logger;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 *
 * UI controller for managing cluster definition files
 *
 * @author someDeveloper
 *
 *
 * @see <a href=
 *      "http://static.springsource.org/spring/docs/current/spring-framework-reference/html/mvc.html">
 *      SpringMvc Docs </a>
 *
 * @see SpringContext_agentSvcServlet
 *
 *
 */
@RestController
@RequestMapping ( CsapCoreService.DEFINITION_URL )
@CsapDoc ( title = "Application Definition Operations" , notes = {
		"Update, Reload and similar operations to manage the running application",
		"<a class='pushButton' target='_blank' href='https://github.com/csap-platform/csap-core/wiki'>learn more</a>",
		"<img class='csapDocImage' src='CSAP_BASE/images/portals.png' />" } )
public class DefinitionRequests {

	final Logger logger = LoggerFactory.getLogger( getClass() );

	public DefinitionRequests(
			Application csapApp,
			CsapEventClient csapEventClient,
			SourceControlManager sourceControlManager,
			StandardPBEStringEncryptor encryptor,
			CsapEncryptableProperties csapEncProps,
			CorePortals corePortals ) {

		this.csapApp = csapApp;
		this.corePortals = corePortals;
		this.csapEventClient = csapEventClient;
		this.sourceControlManager = sourceControlManager;
		this.encryptor = encryptor;
		this.csapEncProps = csapEncProps;

	}

	@Autowired ( required = false )
	JavaMailSender csapMailSender;
	// standalone csap may optionally configure notifications.

	CorePortals corePortals;
	Application csapApp;

	CsapEventClient csapEventClient;

	SourceControlManager sourceControlManager;

	StandardPBEStringEncryptor encryptor;
	CsapEncryptableProperties csapEncProps;

	ObjectMapper jacksonMapper = new ObjectMapper();

	@RequestMapping ( value = { CsapCoreService.ENCODE_URL, CsapCoreService.DECODE_URL } , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode getSecureProperties (
											@RequestParam ( "propertyFileContents" ) String propertyFileContents,
											@RequestParam ( value = "customToken" , defaultValue = "" ) String customToken,
											HttpServletRequest request ) {

		String command = (new File( request.getRequestURI() )).getName();
		logger.debug( "propertyFileContents: {}", propertyFileContents );

		csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/getSecureProperties",
			CsapUser.currentUsersID(),
			command, "Token Length" + customToken.length() );

		ObjectNode resultNode = jacksonMapper.createObjectNode();
		ArrayNode codeLines = resultNode.putArray( "converted" );
		ArrayNode ignoredLines = resultNode.putArray( "ignored" );

		StringBuilder updatedContent = new StringBuilder();

		String[] lines = propertyFileContents.split( "\\r?\\n" );
		// String[] lines = propertyFileContents.split( "\\r?\\n" );

		boolean isYaml = false;
		if ( lines[0].contains( "yaml" ) ) {
			isYaml = true;
		}
		StandardPBEStringEncryptor csapEncrypter = encryptor;
		if ( customToken.length() > 0 ) {
			csapEncrypter = new StandardPBEStringEncryptor();
			csapEncrypter.setAlgorithm( csapEncProps.getAlgorithm() );
			csapEncrypter.setPassword( customToken );
		}

		logger.info( "command: {}, Token Length: {}, lines of input: {}", command, customToken.length(), lines.length );

		for ( int i = 0; i < lines.length; i++ ) {

			String currentLine = lines[i];

			logger.info( "line index {} to be transformed: {}", i, currentLine );
			// empty lines
			if ( currentLine.trim().length() == 0
					|| currentLine.trim().startsWith( "#" )
					|| (isYaml && currentLine.trim().split( " " ).length <= 1)
					|| (isYaml && !currentLine.contains( ":" )) ) {
				ignoredLines.add( currentLine );
				updatedContent.append( currentLine );
				updatedContent.append( "\n" );
				continue;
			}

			if ( command.equalsIgnoreCase( "decode" ) ) {
				codeLines.add( decodeLine( isYaml, currentLine, csapEncrypter, updatedContent ) );
			} else {
				codeLines.add( encodeLine( isYaml, currentLine, csapEncrypter, updatedContent ) );
			}

		}

		resultNode.put( "updatedContent", updatedContent.toString() );

		return resultNode;
	}

	private ObjectNode decodeLine ( boolean isYaml, String line, StandardPBEStringEncryptor csapEncrypter, StringBuilder updatedContent ) {

		ObjectNode decodeResults = jacksonMapper.createObjectNode();
		String result = "decoding did not work, verify input is correct, and algorithm and token are consistent";

		if ( line.contains( "=" ) && line.indexOf( "=" ) + 1 < line.length() ) {
			String propKey = line.substring( 0, line.indexOf( "=" ) );
			String propValue = line.substring( line.indexOf( "=" ) + 1 );
			decodeResults.put( "key", propKey );
			// encrypting the value
			decodeResults.put( "original", propValue );

			if ( propValue.startsWith( "ENC(" ) ) {
				propValue = propValue.substring( 4, propValue.length() - 1 );
				try {
					result = csapEncrypter.decrypt( propValue );
				} catch (Exception e) {
					logger.debug( "Failed to decrypt", e );
				}
			} else {
				result = propValue;
			}

			updatedContent.append( propKey + "=" + result );
			updatedContent.append( "\n" );

		} else if ( isYaml ) {
			// YAML files
			String propKey = line.substring( 0, line.indexOf( ":" ) ).trim();
			String propValue = line.substring( line.indexOf( ":" ) + 1 ).trim();
			decodeResults.put( "key", propKey );
			// encrypting the value
			decodeResults.put( "original", propValue );
			String encryptVal = propValue;
			try {
				result = csapEncrypter.decrypt( encryptVal );
			} catch (Exception e) {
				logger.debug( "Failed to decrypt", e );
			}

			decodeResults.put( "encrypted", encryptVal );

			updatedContent.append( line.substring( 0, line.indexOf( ":" ) + 2 ) + result );
			updatedContent.append( "\n" );

		} else {

			decodeResults.put( "key", "none" );
			// encrypting the value
			decodeResults.put( "original", line );
			try {
				result = csapEncrypter.decrypt( line );
			} catch (Exception e) {
				logger.debug( "Failed to decrypt", e );
			}

		}
		decodeResults.put( "decrypted", result );

		return decodeResults;

	}

	private ObjectNode encodeLine ( boolean isYaml, String line, StandardPBEStringEncryptor csapEncrypter, StringBuilder updatedContent ) {

		ObjectNode encodeResults = jacksonMapper.createObjectNode();

		if ( line.contains( "=" ) && line.indexOf( "=" ) + 1 < line.length() ) {
			// a property file

			String propKey = line.substring( 0, line.indexOf( "=" ) );
			String propValue = line.substring( line.indexOf( "=" ) + 1 );
			encodeResults.put( "key", propKey );
			// encrypting the value
			encodeResults.put( "original", propValue );
			String encryptVal = propValue;

			if ( !propValue.startsWith( "ENC(" ) ) {
				encryptVal = csapEncrypter.encrypt( propValue );
			}

			encodeResults.put( "encrypted", encryptVal );

			updatedContent.append( propKey + "=ENC(" + encryptVal + ")" );
			updatedContent.append( "\n" );

		} else if ( isYaml ) {
			// YAML files
			String propKey = line.substring( 0, line.indexOf( ":" ) ).trim();
			String propValue = line.substring( line.indexOf( ":" ) + 1 ).trim();
			encodeResults.put( "key", propKey );
			// encrypting the value
			encodeResults.put( "original", propValue );
			String encryptVal = propValue;

			if ( !propValue.startsWith( "ENC(" ) ) {
				encryptVal = csapEncrypter.encrypt( propValue );
			}

			encodeResults.put( "encrypted", encryptVal );

			updatedContent.append( line.substring( 0, line.indexOf( ":" ) + 2 ) + encryptVal );
			updatedContent.append( "\n" );

		} else {
			// Encrypt entire line
			encodeResults.put( "key", "none" );
			// encrypting the value
			encodeResults.put( "original", line );
			encodeResults.put( "encrypted", csapEncrypter.encrypt( line ) );
		}

		return encodeResults;
	}

	@RequestMapping ( value = "/getDefinition" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode getDefinition (
										@RequestParam ( value = "releasePackage" , required = false ) String releasePackage ) {

		logger.info( "releasePackage" + releasePackage );

		if ( releasePackage == null ) {
			releasePackage = csapApp.getActiveModelName();
		}
		csapApp.updateCache( true );

		ObjectNode modelObject ;
		
		if ( csapApp.getModel( releasePackage ) != null ) {
			modelObject = (ObjectNode) csapApp
					.getModel( releasePackage )
					.getJsonModelDefinition();
		} else {

			logger.warn( "Did not find requested model: {}", releasePackage );
			modelObject = jacksonMapper.createObjectNode() ;
			modelObject.put( "error", "Release package not found: " + releasePackage ) ;
		}

		return modelObject;
	}

	@RequestMapping ( value = "/releaseFile" , produces = MediaType.APPLICATION_JSON_VALUE )
	public ObjectNode getReleaseFile (
										@RequestParam ( value = "releasePackage" , required = false ) String releasePackage )
			throws IOException {

		logger.info( "releasePackage" + releasePackage );

		if ( releasePackage == null ) {
			releasePackage = csapApp.getActiveModelName();
		}
		csapApp.updateCache( true );

		File releaseFile = csapApp.getModel( releasePackage )
			.getReleaseFile( csapApp.getDefinitionFile() );

		ObjectNode releaseJson = null;
		try {
			releaseJson = csapApp.getParser().parseJsonConfig( releaseFile );
		} catch (IOException iOException) {
			releaseJson = jacksonMapper.createObjectNode();
			releaseJson.put( "error", iOException.getMessage() );
		}

		return releaseJson;
	}

	@RequestMapping ( value = "/lifecycle" , produces = MediaType.APPLICATION_JSON_VALUE , method = RequestMethod.POST )
	public ObjectNode updateLife (
									@RequestParam ( value = "lifeToEdit" , required = false ) String lifeToEdit,
									@RequestParam ( "operation" ) String operation,
									@RequestParam ( value = "newName" , required = false , defaultValue = "dummy" ) String newName,
									@RequestParam ( value = "releasePackage" , required = false ) String releasePackage ) {
		if ( lifeToEdit == null ) {
			lifeToEdit = Application.getCurrentLifeCycle();
		}

		if ( releasePackage == null ) {
			releasePackage = csapApp.getActiveModelName();
		}

		logger.info( "operation: {} , newName: {},  lifeToEdit: {} , releasePackage: {}, isUpdate: {}",
			operation, newName, lifeToEdit, releasePackage );

		ObjectNode updateResultNode = jacksonMapper.createObjectNode();

		try {

			ReleasePackage currentPackage = csapApp.getModel( releasePackage );
			ObjectNode modelNode = (ObjectNode) currentPackage.getJsonModelDefinition();

			ObjectNode testPackageModel = modelNode.deepCopy();
			ObjectNode testClusterBase = (ObjectNode) testPackageModel.at( DefinitionParser.buildClusterPtr() );

			JsonNode testLifeCycleDefinition = testPackageModel.at( DefinitionParser.buildLifePtr( lifeToEdit ) );
			if ( operation.equals( "add" ) ) {
				if ( !StringUtils.isAlphanumeric( newName ) ) {
					throw new IOException(
						"LifeCycle names must be alpha numeric only, with no spaces or other special characters: "
								+ newName );
				}
				if ( testClusterBase.has( newName ) ) {
					throw new IOException(
						"LifeCycle name already exists. Delete it or choose a different name: "
								+ newName );
				}

				ObjectNode newLife = testLifeCycleDefinition.deepCopy();
				testClusterBase.set( newName, newLife );

				// replace all hosts
				AtomicInteger hostCount = new AtomicInteger( 1 );
				String sampleHost = "ChangeMe-" + newName;
				newLife.findValues( "hosts" ).forEach( hostContainer -> {
					logger.info( "Removing hosts: " + hostContainer.toString() );
					if ( hostContainer.isArray() ) {
						((ArrayNode) hostContainer).removeAll();
						((ArrayNode) hostContainer).add( sampleHost + hostCount.getAndIncrement() );
					}
				} );

			} else if ( operation.equals( "delete" ) ) {

				if ( !testClusterBase.has( lifeToEdit ) ) {
					throw new IOException(
						"LifeCycle name not found: "
								+ lifeToEdit );
				}

				List<String> lifeNames = new ArrayList<>();
				testClusterBase
					.fieldNames()
					.forEachRemaining( name -> {
						if ( !name.equals( "settings" ) ) {
							lifeNames.add( name );
						}
					} );

				if ( lifeNames.size() <= 1 ) {
					throw new IOException(
						"Application  must contain at least 1 lifecycle. Add at least"
								+ " one new lifecycle prior to removing: " + lifeToEdit );
				}

				testClusterBase.remove( lifeToEdit );

			} else {
				throw new IOException(
					"Un supported  operation:"
							+ operation );
			}

			updateResultNode.put( "lifeToEdit", lifeToEdit );
			// logger.debug( "updateNode: \n{}",
			// updatedClusterDefinition.toString() );

			ObjectNode validationResults = csapApp.checkDefinitionForParsingIssues(
				testPackageModel.toString(),
				currentPackage.getReleasePackageName(),
				"clusterUpdate" );

			updateResultNode.set( "validationResults", validationResults );

			boolean validatePassed = ((ArrayNode) validationResults.get( Application.VALIDATION_ERRORS )).size() == 0;

			if ( validatePassed ) {
				updateResultNode.put( "updatedHost", Application.getHOST_NAME() );
				currentPackage.setJsonModelDefinition( testPackageModel );
			}

		} catch (Throwable ex) {
			logger.error( "Failed to parse", ex );
			return buildEditingErrorResponse( ex, updateResultNode );
		}

		return updateResultNode;

	}

	@RequestMapping ( value = "/cluster" , produces = MediaType.APPLICATION_JSON_VALUE , method = RequestMethod.GET )
	public ObjectNode getClusterDefinition (
												@RequestParam ( "clusterName" ) String clusterName,
												@RequestParam ( value = "lifeToEdit" , required = false ) String lifeToEdit,
												@RequestParam ( value = "releasePackage" , required = false ) String releasePackage ) {

		if ( lifeToEdit == null ) {
			lifeToEdit = Application.getCurrentLifeCycle();
		}

		if ( releasePackage == null ) {
			releasePackage = csapApp.getActiveModelName();
		}

		logger.info( "lifeToEdit: {}, clusterPath: {}, releasePackage: {}",
			lifeToEdit, clusterName, releasePackage );

		// ReleasePackage package = csapApp.getModel( releasePackage );
		ReleasePackage currentPackage = csapApp.getModel( releasePackage );

		csapApp.updateCache( true );

		ObjectNode modelObject = (ObjectNode) currentPackage.getJsonModelDefinition();
		// //ReleasePackage serviceModel = csapApp.getModel( hostName,
		// serviceName ) ;
		// //logger.info( "Found model: {}",
		// serviceModel.getReleasePackageName() );
		ObjectNode clusterNode = (ObjectNode) modelObject.at(
			DefinitionParser.buildLifePtr( lifeToEdit ) + "/" + clusterName );

		return clusterNode;
	}

	@RequestMapping ( value = "/cluster" , produces = MediaType.APPLICATION_JSON_VALUE , method = RequestMethod.POST )
	public ObjectNode updateOrValidateCluster (
												@RequestParam ( value = "lifeToEdit" , required = false ) String lifeToEdit,
												@RequestParam ( "clusterName" ) String clusterName,
												@RequestParam ( "operation" ) String operation,
												@RequestParam ( value = "newName" , required = false , defaultValue = "dummy" ) String newName,
												@RequestParam ( value = "releasePackage" , required = false ) String releasePackage,
												@RequestParam ( "definition" ) String definitionText,
												@RequestParam ( value = "isUpdate" , required = false ) String isUpdate ) {

		if ( lifeToEdit == null ) {
			lifeToEdit = Application.getCurrentLifeCycle();
		}

		if ( releasePackage == null ) {
			releasePackage = csapApp.getActiveModelName();
		}

		logger.info( "operation: {} , newName: {},  lifeToEdit: {} , clusterName: {}, releasePackage: {}, isUpdate: {}",
			operation, newName, lifeToEdit, clusterName, releasePackage, isUpdate );
		logger.debug( "definitionText: \n{}", definitionText );

		ObjectNode updateResultNode = jacksonMapper.createObjectNode();

		try {
			ObjectNode updatedClusterDefinition = (ObjectNode) jacksonMapper.readTree( definitionText );
			updatedClusterDefinition.put( "lastModifiedBy", CsapUser.currentUsersID() );

			ReleasePackage currentPackage = csapApp.getModel( releasePackage );
			ObjectNode modelNode = (ObjectNode) currentPackage.getJsonModelDefinition();

			ObjectNode testPackageModel = modelNode.deepCopy();

			JsonNode testLifeCycleDefinition = testPackageModel.at( DefinitionParser.buildLifePtr( lifeToEdit ) );

			if ( operation.equals( "delete" ) ) {
				((ObjectNode) testLifeCycleDefinition).remove( clusterName );

			} else if ( operation.equals( "rename" ) ) {
				if ( !StringUtils.isAlphanumeric( newName ) ) {
					throw new IOException(
						"Cluster names must be alpha numeric only, with no spaces or other special characters: "
								+ newName );
				}

				((ObjectNode) testLifeCycleDefinition).remove( clusterName );
				((ObjectNode) testLifeCycleDefinition).set( newName, updatedClusterDefinition );

			} else if ( operation.equals( "add" ) ) {
				if ( !StringUtils.isAlphanumeric( clusterName ) ) {
					throw new IOException(
						"Cluster names must be alpha numeric only, with no spaces or other special characters: "
								+ clusterName );
				}

				((ObjectNode) testLifeCycleDefinition).set( clusterName, updatedClusterDefinition );

			} else if ( operation.equals( "copy" ) ) {
				if ( !StringUtils.isAlphanumeric( newName ) ) {
					throw new IOException(
						"Cluster names must be alpha numeric only, with no spaces or other special characters: "
								+ clusterName );
				}

				((ObjectNode) testLifeCycleDefinition).set( newName, updatedClusterDefinition );

			} else { // modify
				((ObjectNode) testLifeCycleDefinition).set( clusterName, updatedClusterDefinition );
			}

			updateResultNode.put( "lifeToEdit", lifeToEdit );
			logger.debug( "updateNode: \n{}", updatedClusterDefinition.toString() );

			ObjectNode validationResults = csapApp.checkDefinitionForParsingIssues(
				testPackageModel.toString(),
				currentPackage.getReleasePackageName(),
				"clusterUpdate" );

			updateResultNode.set( "validationResults", validationResults );

			boolean validatePassed = ((ArrayNode) validationResults.get( Application.VALIDATION_ERRORS )).size() == 0;

			if ( isUpdate != null && validatePassed ) {
				updateResultNode.put( "updatedHost", Application.getHOST_NAME() );
				currentPackage.setJsonModelDefinition( testPackageModel );
			}

		} catch (Throwable ex) {

			logger.error( "Failed to parse", ex );
			return buildEditingErrorResponse( ex, updateResultNode );

		}

		return updateResultNode;
	}

	@RequestMapping ( value = "/settings/config" , produces = MediaType.APPLICATION_JSON_VALUE , method = RequestMethod.GET )
	public ObjectNode settingsConfig (
										@RequestParam ( value = "lifeToEdit" , required = false ) String lifeToEdit ) {

		logger.info( "lifeToEdit: {}", lifeToEdit );

		if ( lifeToEdit == null ) {
			lifeToEdit = Application.getCurrentLifeCycle();
		}

		ObjectNode config = jacksonMapper.createObjectNode();
		ArrayNode services = config.putArray( "services" );

		try {

			ObjectNode performanceCollections = jacksonMapper.createObjectNode();
			ObjectNode hostLabels = OsSharedEnum.graphLabels();
			hostLabels.put( "coresActive", "CPU Cores Busy" );
			performanceCollections.set( "Host", hostLabels );
			performanceCollections.set( "HostRealTime", OsSharedEnum.realTimeLabels() );

			performanceCollections.set( "OS", OsProcessEnum.graphLabels() );
			performanceCollections.set( "Java", JmxCommonEnum.graphLabels() );
			performanceCollections.set( "App", csapApp.servicePerformanceLabels() );
			config.set( "performanceLabels", performanceCollections );

		} catch (Exception e) {
			logger.error( "Failed configuring dialog", e );
		}

		csapApp.getActiveModel().getAllPackagesModel().findServiceNamesInLifecycle( lifeToEdit )
			.forEach( services::add );

		return config;

	}

	@RequestMapping ( value = "/settings" , produces = MediaType.APPLICATION_JSON_VALUE , method = RequestMethod.GET )
	public ObjectNode settings (
									@RequestParam ( value = "lifeToEdit" , required = false ) String lifeToEdit ) {

		logger.info( "lifeToEdit: {}", lifeToEdit );

		if ( lifeToEdit == null ) {
			lifeToEdit = Application.getCurrentLifeCycle();
		}

		csapApp.updateCache( true );

		// ReleasePackage serviceModel = csapApp.getModel( hostName, serviceName
		// ) ;
		// logger.info( "Found model: {}", serviceModel.getReleasePackageName()
		// );
		JsonNode rootNode = csapApp.getRootModel().getJsonModelDefinition();
		ObjectNode settingsNode = (ObjectNode) rootNode.at(
			DefinitionParser.buildLifePtr( lifeToEdit ) + "/"
					+ DefinitionParser.PARSER_SETTINGS );

		JsonNode realtime = settingsNode.at( "/metricsCollectionInSeconds/realTimeMeters" );

		if ( !realtime.isMissingNode() && realtime.isArray() ) {
			((ArrayNode) realtime).elements().forEachRemaining( realTimeDef -> {
				MetricCategory performanceCategory = MetricCategory.parse( realTimeDef );
				if ( performanceCategory != MetricCategory.notDefined ) {
					if ( performanceCategory == MetricCategory.java ) {
						String id = realTimeDef.get( "id" ).asText();
						String[] ids = id.split( "\\." );
						String[] attributes = ids[1].split( "_" );
						if ( attributes.length == 3 ) {
							logger.info( "Stripping off port from {}, no longer needed", attributes );
							((ObjectNode) realTimeDef).put( "id", id.substring( 0, id.lastIndexOf( "_" ) ) );
						}
					}
				}

			} );
		}

		return settingsNode;
	}

	@RequestMapping ( value = "/settings" , produces = MediaType.APPLICATION_JSON_VALUE , method = RequestMethod.POST )
	public ObjectNode updateOrValidateSettings (
													@RequestParam ( "lifeToEdit" ) String lifeToEdit,
													@RequestParam ( "definition" ) String definitionText,
													@RequestParam ( value = "isUpdate" , required = false ) String isUpdate ) {

		if ( lifeToEdit == null ) {
			lifeToEdit = Application.getCurrentLifeCycle();
		}

		logger.info( "lifeToEdit: {} , isUpdate: {}", lifeToEdit, isUpdate );
		logger.debug( "definitionText: \n{}", definitionText );

		ObjectNode updateResultNode = jacksonMapper.createObjectNode();

		try {
			ObjectNode updatedSettingsDefinition = (ObjectNode) jacksonMapper.readTree( definitionText );
			updatedSettingsDefinition.put( "lastModifiedBy", CsapUser.currentUsersID() );

			ObjectNode modelNode = (ObjectNode) csapApp.getRootModel().getJsonModelDefinition();

			ObjectNode testPackageModel = modelNode.deepCopy();

			JsonNode currentLifeNode = testPackageModel.at( DefinitionParser.buildLifePtr( lifeToEdit ) );

			((ObjectNode) currentLifeNode).set( DefinitionParser.PARSER_SETTINGS, updatedSettingsDefinition );

			updateResultNode.put( "lifeToEdit", lifeToEdit );
			logger.debug( "updateNode: \n{}", updatedSettingsDefinition.toString() );

			ObjectNode validationResults = csapApp.checkDefinitionForParsingIssues(
				testPackageModel.toString(),
				csapApp.getRootModel().getReleasePackageName(),
				"settingsUpdate" );

			updateResultNode.set( "validationResults", validationResults );

			boolean validatePassed = ((ArrayNode) validationResults.get( Application.VALIDATION_ERRORS )).size() == 0;

			if ( validatePassed ) {
				validateTrendingAndRealTimeDefinition( lifeToEdit, updatedSettingsDefinition, validationResults );
			}

			if ( isUpdate != null && validatePassed ) {
				updateResultNode.put( "updatedHost", Application.getHOST_NAME() );
				csapApp.getRootModel().setJsonModelDefinition( testPackageModel );
			}

		} catch (Throwable ex) {
			logger.error( "Failed to parse", ex );
			return buildEditingErrorResponse( ex, updateResultNode );
		}

		return updateResultNode;
	}

	public void validateTrendingAndRealTimeDefinition (	String lifeToEdit, ObjectNode updatedSettingsDefinition,
														ObjectNode validationResults ) {
		// these checks require LC be specified
		Set<String> allowedNames = csapApp.getActiveModel().getAllPackagesModel().findServiceNamesInLifecycle( lifeToEdit );

		JsonNode trending = updatedSettingsDefinition.at( "/metricsCollectionInSeconds/trending" );

		final String lc = lifeToEdit;
		if ( !trending.isMissingNode() && trending.isArray() ) {
			((ArrayNode) trending).elements().forEachRemaining( trendDef -> {
				if ( trendDef.has( "serviceName" ) ) {
					String serviceNameCommaSeparated = trendDef.get( "serviceName" ).asText();
					String[] serviceNames = serviceNameCommaSeparated.split( "," );
					for ( String serviceName : serviceNames ) {

						if ( !allowedNames.contains( serviceName ) && !"all".equals( serviceName ) ) {
							((ArrayNode) validationResults.get( Application.VALIDATION_WARNINGS ))
								.add( "Service: " + serviceName + " - found in trending definition but not found in lifecycle: " + lc );
						}
					}
				}
			} );
		}

		JsonNode realtime = updatedSettingsDefinition.at( "/metricsCollectionInSeconds/realTimeMeters" );

		if ( !realtime.isMissingNode() && realtime.isArray() ) {
			((ArrayNode) realtime).elements().forEachRemaining( realTimeDef -> {

				MetricCategory performanceCategory = MetricCategory.parse( realTimeDef );
				if ( performanceCategory != MetricCategory.notDefined
						&& performanceCategory != MetricCategory.osShared ) {

					String serviceName = performanceCategory.serviceName( realTimeDef );

					if ( !allowedNames.contains( serviceName ) && !"all".equals( serviceName ) ) {
						((ArrayNode) validationResults.get( Application.VALIDATION_WARNINGS ))
							.add( "Service: " + serviceName + " - found in real time definition but not found in lifecycle: "
									+ lc + ", item:" + realTimeDef.toString() );
					}

				} else if ( performanceCategory == MetricCategory.notDefined ) {
					((ArrayNode) validationResults.get( Application.VALIDATION_WARNINGS ))
						.add( "Real time meters in life cycle: " + lc + " contains unexpected category: " + realTimeDef.toString() );
				}
			} );
		}
	}

	@Inject 
	DockerHelper dockerHelper;
	
	@GetMapping ( value = "/serviceDockerTemplate" , produces = MediaType.APPLICATION_JSON_VALUE )
	public JsonNode getDockerTemplate (
	                                   String templateName
			)
	
			throws IOException {
		
		// templateName = DockerJson.defaultJavaDocker.key
		ObjectNode dockerTemplate = (ObjectNode) dockerHelper.getDockerTemplates().get( templateName ) ;
		String imageName =  dockerTemplate.get( "image" ).asText().replaceAll(
			Matcher.quoteReplacement( CSAP.DOCKER_REPOSITORY ),
			Matcher.quoteReplacement( csapApp.getCsapCoreService().getDocker().getTemplateRepository() ) );
		dockerTemplate.put( "image", imageName) ;
		
		return dockerTemplate ;
		
	}
	@RequestMapping ( value = "/service" , produces = MediaType.APPLICATION_JSON_VALUE , method = RequestMethod.GET )
	public ObjectNode getService (
									@RequestParam ( "serviceName" ) String serviceName,
									@RequestParam ( "hostName" ) String hostName,
									@RequestParam ( value = "releasePackage" , required = false ) String releasePackage )
			throws IOException {

		logger.info( "serviceName: {}, hostName: {} ", serviceName, hostName );

		if ( serviceName.contains( "_" ) ) {
			serviceName = serviceName.split( "_" )[0];
		}

		csapApp.updateCache( true );

		ReleasePackage serviceModel = null;

		if ( releasePackage == null ) {
			serviceModel = csapApp.getModel( hostName, serviceName );
		} else {
			serviceModel = csapApp.getModel( releasePackage );
		}

		logger.info( "Found model: {}", serviceModel.getReleasePackageName() );

		// clone the root object because it is updated with property files for
		// UI
		ObjectNode serviceNode = (ObjectNode) jacksonMapper.readTree(
			serviceModel.getServiceDefinition( serviceName ).toString() );

		if ( serviceNode.has( ServiceAttributes.eolParameters.value ) ) {
			// rename to new naming conventins
			String parameters = serviceNode.get( ServiceAttributes.eolParameters.value ).asText();
			serviceNode.remove( ServiceAttributes.eolParameters.value );
			serviceNode.put( ServiceAttributes.parameters.value, parameters );
		}

		if ( serviceNode.has( ServiceAttributes.eolEnv.value ) ) {
			// rename to new naming conventins
			ObjectNode vars = (ObjectNode) serviceNode.get( ServiceAttributes.eolEnv.value );
			serviceNode.remove( ServiceAttributes.eolEnv.value );
			serviceNode.set( ServiceAttributes.environmentVariables.value, vars );
		}

		if ( !serviceNode.has( ServiceAttributes.documentation.value ) ) {

			serviceNode.put( ServiceAttributes.documentation.value,
				csapApp.lifeCycleSettings().getUserLookupUrl( CsapUser.currentUsersID() ) );
		}
		if ( !serviceNode.has( ServiceAttributes.documentation.value ) ) {

			serviceNode.put( ServiceAttributes.description.value,
				CsapUser.currentUsersID() + " added, and needs to update this description" );
		}

		addServicePropertyFiles( serviceNode, serviceName );

		return serviceNode;
	}

	private List<File> buildFileList ( File dir ) {

		File[] files = dir.listFiles();

		if ( files == null ) {
			return new ArrayList<File>();
		}

		return Arrays.asList( files );
	}

	private void addServicePropertyFiles ( ObjectNode serviceNode, String serviceName ) {
		// remove preivous external first
		if ( serviceNode.has( ServiceAttributes.files.value ) ) {
			ArrayNode files = (ArrayNode) serviceNode.get( ServiceAttributes.files.value );
			ArrayNode internalOnly = extractEmbeddedServiceFiles( files );
			serviceNode.set( ServiceAttributes.files.value, internalOnly );
		}

		HashSet<String> loadedFiles = new HashSet<>();
		ArrayList<File> allServiceFiles = new ArrayList<>();
		String targetPath = serviceName + "/resources/";
		File serviceWorking = new File( csapApp.getResourcesWorkingFolder(), targetPath );

		// get all working - then the current files
		allServiceFiles.addAll( buildFileList( serviceWorking ) );
		allServiceFiles.addAll( buildFileList( csapApp.getResourcesFolder( serviceName ) ) );

		// external files
		allServiceFiles.stream()
			.filter( File::isDirectory )
			.flatMap( lifeDirectory -> {
				return buildFileList( lifeDirectory ).stream();
			} )

			.filter( propFile -> {
				return !loadedFiles.contains( propFile.getParentFile().getName() + "/" + propFile.getName() );
			} )

			.forEach( propFile -> {

				// only load once first from working - then from original
				loadedFiles.add( propFile.getParentFile().getName() + "/" + propFile.getName() );

				if ( !serviceNode.has( ServiceAttributes.files.value ) ) {

					serviceNode.putArray( ServiceAttributes.files.value );
				}
				ArrayNode serviceFiles = (ArrayNode) serviceNode.get( ServiceAttributes.files.value );
				serviceFiles.add( buildPropertyFileNode( propFile, serviceName ) );

			} );

	}

	private ObjectNode buildPropertyFileNode ( File propFile, String serviceName ) {
		//
		ObjectNode propertyNode = jacksonMapper.createObjectNode();
		String targetName = propFile.getName();
		String targetLife = propFile.getParentFile().getName();
		propertyNode.put( ServiceAttributes.FileAttributes.name.json, targetName );
		propertyNode.put( ServiceAttributes.FileAttributes.lifecycle.json, targetLife );
		propertyNode.put( ServiceAttributes.FileAttributes.external.json, "true" );

		// check for working version
		String targetPath = serviceName + "/resources/" + targetLife + "/" + targetName;

		ArrayNode content = propertyNode.arrayNode();
		try (BufferedReader br = new BufferedReader( new FileReader( propFile ) )) {

			String sCurrentLine;

			while ((sCurrentLine = br.readLine()) != null) {
				content.add( sCurrentLine );
			}

		} catch (IOException e) {
			logger.error( "Failed reading file: {}", propFile, e );
		}

		propertyNode.set( ServiceAttributes.FileAttributes.content.json, content );

		return propertyNode;
	}

	private ArrayNode extractEmbeddedServiceFiles ( ArrayNode files ) {
		ArrayNode internalOnly = jacksonMapper.createArrayNode();
		files.forEach( propFile -> {
			if ( propFile.has( ServiceAttributes.FileAttributes.external.json )
					&& !propFile.get( ServiceAttributes.FileAttributes.external.json ).asBoolean() ) {
				internalOnly.add( propFile );
			}
		} );
		return internalOnly;
	}

	private void addToPendingResources ( ObjectNode serviceNode, String serviceName ) {

		// File resourceDir = csapApp.getResourcesFolder( serviceName );
		File serviceResourceDir = csapApp.getResourcesWorkingFolder();

		// only internal files are stored.
		ArrayNode files = (ArrayNode) serviceNode.get( ServiceAttributes.files.value );
		ArrayNode internalOnly = extractEmbeddedServiceFiles( files );
		serviceNode.set( ServiceAttributes.files.value, internalOnly );

		files.forEach( propFile -> {
			String targetLife = propFile.get( ServiceAttributes.FileAttributes.lifecycle.json ).asText();
			String targetName = propFile.get( ServiceAttributes.FileAttributes.name.json ).asText();
			String targetPath = serviceName + "/resources/" + targetLife + "/" + targetName;
			if ( propFile.has( ServiceAttributes.FileAttributes.external.json )
					&& propFile.get( ServiceAttributes.FileAttributes.external.json ).asBoolean() ) {

				if ( propFile.has( ServiceAttributes.FileAttributes.newFile.json )
						&& propFile.get( ServiceAttributes.FileAttributes.newFile.json ).asBoolean() ) {

					writePropertyFile( serviceResourceDir, targetPath, propFile );

					csapApp.getPendingResourceAdds().add( targetPath );

				} else if ( propFile.has( ServiceAttributes.FileAttributes.contentUpdated.json )
						&& propFile.get( ServiceAttributes.FileAttributes.contentUpdated.json ).asBoolean() ) {

					writePropertyFile( serviceResourceDir, targetPath, propFile );
				}

				if ( propFile.has( ServiceAttributes.FileAttributes.deleteFile.json )
						&& propFile.get( ServiceAttributes.FileAttributes.deleteFile.json ).asBoolean() ) {

					csapApp.getPendingResourceDeletes().add( targetPath );
				}
			}
		} );

		logger.info( "Pending Operations: {}", csapApp.getPendingResourceOperations() );
	}

	private void writePropertyFile ( File resourceWorking, String path, JsonNode propFile ) {
		logger.debug( "creating: {} in: {}",
			path, resourceWorking.getAbsolutePath() );
		try {

			File targetFile = new File( resourceWorking.getCanonicalFile(), path );

			targetFile.getParentFile().mkdirs();
			logger.info( "Creating: {}", targetFile.getCanonicalPath() );
			ArrayList<String> lines = jacksonMapper.readValue(
				propFile.path( ServiceAttributes.FileAttributes.content.json ).traverse(),
				new TypeReference<ArrayList<String>>() {
				} );
			Files.write( targetFile.toPath(), lines, Charset.forName( "UTF-8" ) );

		} catch (IOException e) {
			logger.error( "Failed creating resource file {} ", path,
				CSAP.getCsapFilteredStackTrace( e ) );
		}
	}

	@Inject
	SpringTemplateEngine springTemplateEngine;

	public static String EMAIL_DISABLED = "Email notifications disabled";

	private String sendEmailToInfraAdmin (
											ReleasePackage currentPackageModel,
											String message,
											String comment,
											String attachment,
											String attachmentName ) {

		String results = "emailNotifications: ";
		// CustomUserDetails person = CsapUser.securityUser();

		try {

			// TemplateEngine engine = new TemplateEngine();
			// engine.addTemplateResolver( templateResolver );
			Context context = new Context();
			context.setVariable( "name", CsapUser.currentUsersID() );
			context.setVariable( "appUrl", csapApp.lifeCycleSettings().getLbUrl() );
			context.setVariable( "sourceUrl", csapApp.getSourceLocation() );
			context.setVariable( "life", Application.getCurrentLifeCycle() );
			context.setVariable( "package", currentPackageModel.getReleasePackageName() );
			context.setVariable( "message", message );
			context.setVariable( "comment", comment );
			String testBody = springTemplateEngine.process( "infraEmail", context );

			logger.info( "{} package {} : \n\t to: {}\n\t message: {}",
				csapApp.getName(), currentPackageModel.getReleasePackageName(), currentPackageModel.getEmailNotifications(),
				message );

			if ( (csapMailSender == null) || (currentPackageModel.getEmailNotifications() == null) ) {
				results += EMAIL_DISABLED;
				logger.warn(
					"Email notifications are not configured. Ensure that mail server is in application.yml and Application.json contacts are configured." );
			} else {
				results += "Email has been sent to: " + currentPackageModel.getEmailNotifications();
				csapMailSender.send( mimeMessage -> {
					MimeMessageHelper messageHelper = new MimeMessageHelper( mimeMessage, true, "UTF-8" );
					messageHelper.setTo( currentPackageModel.getEmailNotifications().split( "," ) );

					if ( !currentPackageModel.getEmailNotifications().contains( CsapUser.currentUsersEmailAddress() ) ) {
						messageHelper.setCc( CsapUser.currentUsersEmailAddress() );
					}
					messageHelper.setFrom( CsapUser.currentUsersEmailAddress() );
					messageHelper.setSubject( "CSAP Notification: " + csapApp.getName() );
					messageHelper.setText( testBody, true );
					messageHelper.addAttachment( attachmentName,
						new ByteArrayResource( attachment.getBytes() ) );
				} );
			}
		} catch (Exception e) {
			results += "Failed to notify, contact your administrator for assistance: "
					+ e.getMessage();

			logger.error( "Failed to send message - verify settings in application.yml;  Error: \n {}", CSAP.getCsapFilteredStackTrace( e ) );
		}

		return results;
	}

	@RequestMapping ( value = CsapCoreService.NOTIFY_URL , produces = MediaType.APPLICATION_JSON_VALUE , method = RequestMethod.POST )
	public ObjectNode notifyAdmin (
									@RequestParam ( "itemName" ) String itemName,
									@RequestParam ( "hostName" ) String hostName,
									@RequestParam ( value = "releasePackage" , required = false ) String releasePackage,
									@RequestParam ( "message" ) String message,
									@RequestParam ( "definition" ) String definition ) {
		ObjectNode updateResultNode = jacksonMapper.createObjectNode();

		logger.debug( "Sending email to admin: {}", message );

		ReleasePackage currentPackageModel = null;
		if ( releasePackage == null ) {
			currentPackageModel = csapApp.getModel( hostName, itemName );
		} else {
			currentPackageModel = csapApp.getModel( releasePackage );
		}

		updateResultNode.put( "Results",
			sendEmailToInfraAdmin( currentPackageModel, message,
				"Admin: Please review request and contact the user", definition,
				itemName + ".json" ) );

		return updateResultNode;
	}

	@RequestMapping ( value = "/service" , produces = MediaType.APPLICATION_JSON_VALUE , method = RequestMethod.POST )
	public ObjectNode updateOrValidateService (
												@RequestParam ( "serviceName" ) String serviceName,
												@RequestParam ( "operation" ) String operation,
												@RequestParam ( "hostName" ) String hostName,
												@RequestParam ( value = "newName" , required = false , defaultValue = "dummy" ) String newName,
												@RequestParam ( value = "releasePackage" , required = false ) String releasePackage,
												@RequestParam ( "definition" ) String definition,
												@RequestParam ( value = "isUpdate" , required = false ) String isUpdate ) {

		if ( serviceName.contains( "_" ) ) {
			serviceName = serviceName.split( "_" )[0];
		}

		logger.info( "releasePackage: {}, serviceName: {} , isUpdate: {}, operation: {}",
			releasePackage, serviceName, isUpdate, operation );

		ObjectNode updateResultNode = jacksonMapper.createObjectNode();

		try {
			ObjectNode updatedServiceDefinition = (ObjectNode) jacksonMapper.readTree( definition );
			updatedServiceDefinition.put( "lastModifiedBy", CsapUser.currentUsersID() );

			ReleasePackage currentPackageModel = null;
			if ( releasePackage == null ) {
				currentPackageModel = csapApp.getModel( hostName, serviceName );
			} else {
				currentPackageModel = csapApp.getModel( releasePackage );
			}

			ObjectNode modelNode = (ObjectNode) currentPackageModel.getJsonModelDefinition();

			ObjectNode testPackageModel = modelNode.deepCopy();

			JsonNode currentServiceNode = testPackageModel.at( DefinitionParser.buildServicePtr( serviceName, true ) );
			JsonNode serviceContainer = testPackageModel.at( "/" + DefinitionParser.PARSER_JVMS );

			boolean isUpdatedTomcat = ServiceInstance.isJeeServer( updatedServiceDefinition.path( "server" ).asText( "notFound" ) );

			if ( currentServiceNode.isMissingNode() && !isUpdatedTomcat ) {
				serviceContainer = testPackageModel.at( "/" + DefinitionParser.OS_PROCESSES );
			}

			if ( !operation.equals( "modify" ) ) {
				if ( serviceName.equalsIgnoreCase( Application.AGENT_ID ) ) {
					throw new IOException( "Agent only supports modify operation: " + newName );
				}
			}

			if ( operation.equals( "delete" ) ) {
				((ObjectNode) serviceContainer).remove( serviceName );

				String oldServiceName = serviceName;
				AtomicInteger numJavaClusters = removeDefinitionReferences( DefinitionParser.CLUSTER_JAVA_SERVICES, testPackageModel,
					oldServiceName );

				AtomicInteger numOsClusters = removeDefinitionReferences(
					DefinitionParser.CLUSTER_OS_SERVICES, testPackageModel,
					oldServiceName );

				updateResultNode.put( "message",
					"Java Cluster references removed: " + numJavaClusters.toString()
							+ "<br/>OS Cluster references removed: " + numOsClusters.toString() );

			} else if ( operation.equals( "add" ) ) {
				verifyNewServiceName( currentPackageModel, serviceName );
				((ObjectNode) serviceContainer).set( serviceName, updatedServiceDefinition );

			} else if ( operation.equals( "copy" ) ) {
				verifyNewServiceName( currentPackageModel, newName );
				((ObjectNode) serviceContainer).set( newName, updatedServiceDefinition );

			} else if ( operation.equals( "rename" ) ) {

				String oldServiceName = serviceName;
				verifyNewServiceName( currentPackageModel, newName );

				((ObjectNode) serviceContainer).remove( serviceName );
				((ObjectNode) serviceContainer).set( newName, updatedServiceDefinition );

				AtomicInteger numJavaClusters = updateDefinitionWithRenameReferences( DefinitionParser.CLUSTER_JAVA_SERVICES,
					testPackageModel, oldServiceName, newName );

				AtomicInteger numOsClusters = updateDefinitionWithRenameReferences( DefinitionParser.CLUSTER_OS_SERVICES,
					testPackageModel, oldServiceName, newName );

				updateResultNode.put( "message",
					"Java Cluster references updated: " + numJavaClusters.toString()
							+ "<br/>OS Cluster references updated: " + numOsClusters.toString() );

			} else {
				((ObjectNode) serviceContainer).set( serviceName, updatedServiceDefinition );

				if ( updatedServiceDefinition.has( ServiceAttributes.files.value ) ) {
					addToPendingResources( updatedServiceDefinition, serviceName );
				}

			}

			updateResultNode.put( "releasePackage", currentPackageModel.getReleasePackageName() );
			logger.debug( "updateNode: \n{}", updatedServiceDefinition.toString() );

			ObjectNode validationResults = csapApp.checkDefinitionForParsingIssues(
				testPackageModel.toString(),
				currentPackageModel.getReleasePackageName(),
				"serviceUpdate" );

			updateResultNode.set( "validationResults", validationResults );

			boolean validatePassed = ((ArrayNode) validationResults.get( Application.VALIDATION_ERRORS )).size() == 0;

			if ( isUpdate != null && validatePassed ) {
				updateResultNode.put( "updatedHost", Application.getHOST_NAME() );
				currentPackageModel.setJsonModelDefinition( testPackageModel );
			}

		} catch (Throwable ex) {
			logger.error( "Failed to parse", ex );
			return buildEditingErrorResponse( ex, updateResultNode );
		}

		return updateResultNode;
	}

	public ObjectNode buildEditingErrorResponse ( Throwable ex, ObjectNode updateResultNode ) {
		ObjectNode resultNode = jacksonMapper.createObjectNode();
		ArrayNode errorNode = resultNode.putArray( VALIDATION_ERRORS );
		String errMessage = ex.getMessage();
		if ( errMessage == null ) {
			errMessage = ex.getClass().getSimpleName();
		}
		errorNode.add( errMessage );
		updateResultNode.set( "validationResults", resultNode );
		updateResultNode.put( "Stage", "Merged Validation" );
		if ( ex.getCause() != null ) {
			updateResultNode.put( "Cause", ex.getCause().getClass().getName() );
		}
		updateResultNode.put( "Type", ex.getClass().getName() );
		updateResultNode.put( "Message", ex.getMessage() );
		return updateResultNode;
	}

	public AtomicInteger removeDefinitionReferences ( String containerId, ObjectNode testPackageModel, String oldServiceName ) {
		AtomicInteger numClusters = new AtomicInteger( 0 );
		testPackageModel.findValues( containerId ).forEach( serviceNode -> {

			if ( serviceNode.has( oldServiceName ) && serviceNode.isObject() ) {
				numClusters.getAndIncrement();
				((ObjectNode) serviceNode).remove( oldServiceName );
				;
			} else if ( serviceNode.isArray() ) {
				// This is a OS node
				ArrayNode osNode = (ArrayNode) serviceNode;
				for ( int i = 0; i < osNode.size(); i++ ) {
					String curItem = osNode.get( i ).asText();
					if ( curItem.equals( oldServiceName ) ) {
						numClusters.getAndIncrement();
						osNode.remove( i );
						break;
					}
				}

			}
		} );
		return numClusters;
	}

	private AtomicInteger updateDefinitionWithRenameReferences (	String containerId, ObjectNode testPackageModel, String oldServiceName,
																	String newName ) {
		// find and replace references
		StringBuilder replaceResults = new StringBuilder( "Updated Clusters: " );
		AtomicInteger numClusters = new AtomicInteger( 0 );
		testPackageModel.findValues( containerId ).forEach( serviceJson -> {
			if ( serviceJson.has( oldServiceName ) && serviceJson.isObject() ) {

				// this is a jvm node
				numClusters.getAndIncrement();
				JsonNode currentJvmPort = serviceJson.get( oldServiceName );
				((ObjectNode) serviceJson).remove( oldServiceName );
				((ObjectNode) serviceJson).set( newName, currentJvmPort );
				replaceResults.append( "\n" + currentJvmPort.toString() );

			} else if ( serviceJson.isArray() ) {
				// This is a OS node
				ArrayNode osNode = (ArrayNode) serviceJson;
				for ( int i = 0; i < osNode.size(); i++ ) {
					String curItem = osNode.get( i ).asText();
					if ( curItem.equals( oldServiceName ) ) {
						numClusters.getAndIncrement();
						osNode.remove( i );
						osNode.add( newName );
						break;
					}
				}

			}
		} );
		logger.info( "Number of Updates: {}", numClusters );
		return numClusters;
	}

	private void verifyNewServiceName ( ReleasePackage currentPackageModel, String newName )
			throws IOException {
		ArrayList<String> currentServices = corePortals.getServices( currentPackageModel );

		if ( !StringUtils.isAlphanumeric( newName ) ) {
			throw new IOException( "Service names must be alpha numeric only, with no spaces or other special characters: " + newName );
		}

		if ( currentServices.contains( newName ) ) {
			throw new IOException( "Found existing service, try another name: " + newName );
		}
	}

	@RequestMapping ( value = "/validateDefinition" , produces = MediaType.APPLICATION_JSON_VALUE )
	synchronized public ObjectNode validateDefinition (
														@RequestParam ( "updatedConfig" ) String updatedConfig,
														@RequestParam ( "releasePackage" ) String releasePackage,
														HttpServletRequest request ) {

		logger.info( " releasePackage: " + releasePackage + " request.getPathInfo() " + request.getPathInfo() );

		return csapApp.checkDefinitionForParsingIssues( updatedConfig, releasePackage, request.getPathInfo() );

	}

	/**
	 *
	 * Method for applying changes made in browser to server. - Optional support
	 * for checkin
	 *
	 */
	@RequestMapping ( value = { "/CapabilityApply", "/CapabilityCheckIn" } , produces = MediaType.APPLICATION_JSON_VALUE )
	synchronized ObjectNode applyOrCheckInApplication (
														@RequestParam ( "scmUserid" ) String scmUserid,
														@RequestParam ( "scmPass" ) String rawPass,
														@RequestParam ( defaultValue = "false" ) boolean isUpdateAll,

														@RequestParam ( "scmBranch" ) String scmBranch,
														@RequestParam ( value = "comment" , required = false ) String comment,
														@RequestParam ( "updatedConfig" ) String updatedConfig,
														@RequestParam ( "releasePackage" ) String releasePackage,
														@RequestParam ( value = "applyButNoCheckin" , defaultValue = "false" ) boolean isApplyButNoCheckin,
														HttpServletRequest request )
			throws Exception {

		String encryptedPass = encryptor.encrypt( rawPass ); // immediately
		// encrypt pass

		logger.info( "user:{}, branch: {}, package: {}, applyOnly: {} , uri: {} ",
			scmUserid ,  scmBranch,  releasePackage ,  isApplyButNoCheckin , request.getRequestURI() );

		ServiceInstance dummyServiceInstanceForApp = new ServiceInstance();
		dummyServiceInstanceForApp.setScmLocation( csapApp.getSourceLocation() );
		dummyServiceInstanceForApp.setScm( csapApp.getSourceType() );

		File globalModelBuildFolder = new File( csapApp.getRootModelBuildLocation() );

		String selectedConfig = csapApp
			.getModel( releasePackage )
			.getReleasePackageFileName();
		// Critical hook - need to blow away previous folder since there is no
		// clean
		String command = "CapabilityApply";
		if ( request.getRequestURI().contains( "CapabilityCheckIn" ) ) {
			command = "CapabilityCheckIn";
		}
		OutputFileMgr outputManager = new OutputFileMgr( csapApp.getProcessingDir(),
			command );
		try {

			// Create a new empty working folder for the uploaded file
			// Working folder is used solely to validate contents, then will be
			// moved to build folder prior to triggering reload
			File defWorkingFolder = new File( csapApp.getRootModelBuildLocation() + CONFIG_SUFFIX_FOR_UPDATE );

			FileUtils.deleteQuietly( defWorkingFolder ); // maybe just be doing
															// updates instead
															// of deletes here.

			if ( !isApplyButNoCheckin ) {

				// This is optional for the occasional case that SVN location
				// does not actually exist.
				// Also - it is a little quicker....
				// Do a fresh checkout only if we are about to check in. This is
				// for comparison purposes, and to get all the definition files
				sourceControlManager.checkOutFolder(
					scmUserid, encryptedPass, scmBranch,
					defWorkingFolder.getName(),
					dummyServiceInstanceForApp, outputManager.getBufferedWriter() );

				outputManager.print( "\n\n Replaced  working folder: " + defWorkingFolder.getAbsolutePath()
						+ "\n using content retrieved from source control system" );

				if ( isUpdateAll ) {
					if ( csapApp.getSourceType().equals( ScmProvider.git.key ) ) {
						logger.warn( "overwriting source control files with current definition on disk" );
						File activeDefinitionFolder = csapApp.getDefinitionFolder();
						FileUtils.copyDirectory( activeDefinitionFolder, defWorkingFolder, getGitFilter() );
						outputManager.print( "\n\n *** overwriting source control files with current definition on disk: " + defWorkingFolder.getAbsolutePath()
								+ "\n initialized from: " + activeDefinitionFolder.getAbsolutePath()
								+ "\n containing: " + Arrays.asList( defWorkingFolder.list() ) );
					} else {
						outputManager.print( "\n **WARNING: only git supports the update all option") ;
					}
				}
			} else {

				// createWorkingFolder using existing live files
				File sourceLocation = csapApp.getDefinitionFolder();

				// FileUtils.copyDirectory( sourceLocation, workingFolder,
				// FileFilterUtils.suffixFileFilter( ".js" ) );
				// using all files found.....
				FileUtils.copyDirectory( sourceLocation, defWorkingFolder );
				outputManager.print( "Created working folder: " + defWorkingFolder.getAbsolutePath()
						+ "\n initialized from: " + sourceLocation.getAbsolutePath()
						+ "\n containing: " + Arrays.asList( defWorkingFolder.list() ) );

			}

			// First put the uploaded file into working directory
			File tempConfigFile = new File( defWorkingFolder, selectedConfig );
			File tempGlobalCluster = new File( defWorkingFolder, csapApp
				.getRootModel()
				.getReleasePackageFileName() );

			FileUtils.writeStringToFile( tempConfigFile, updatedConfig.replaceAll( "\r", "\n" ) );
			outputManager.print( "Pushed updated definition File to : " + tempConfigFile.getAbsolutePath() );

			//
			// Run the parser
			//
			try {

				// first we run in test mode to verify content
				StringBuffer parsingResultsBuffer = csapApp.getParser().parseConfig( true, tempGlobalCluster );

				if ( (parsingResultsBuffer != null)
						&& parsingResultsBuffer.indexOf( CSAP.CONFIG_PARSE_ERROR ) == -1 ) {

					applicationUpdate(
						outputManager, parsingResultsBuffer,
						isApplyButNoCheckin, tempConfigFile, scmBranch,
						defWorkingFolder, scmUserid, encryptedPass, comment,
						releasePackage, globalModelBuildFolder, selectedConfig );

				} else {
					logger.error( "Failed to parse" );
					if ( (parsingResultsBuffer != null) ) {
						outputManager.print( "-" );
						outputManager
							.print( "\n\n============= Found Semantic Errors !! ====================\n"
									+ "Filtered output for :"
									+ CSAP.CONFIG_PARSE_ERROR );
						List<String> parmList = new ArrayList<String>();
						Collections.addAll(
							parmList,
							"bash",
							"-c",
							"diff  " + csapApp.getDefinitionFile().getAbsolutePath() + " "
									+ tempConfigFile.getAbsolutePath() );
						sourceControlManager.executeShell( parmList,
							outputManager.getBufferedWriter() );

						csapApp.updateOutputWithLimitedInfo( CSAP.CONFIG_PARSE_ERROR, 999, outputManager,
							parsingResultsBuffer, null );
					}
				}
			} catch (Exception parseException) {
				String errorStack = CSAP.getCsapFilteredStackTrace( parseException );
				logger.error( "Parsing error in application: {}", errorStack );
				List<String> parmList = new ArrayList<String>();
				Collections.addAll( parmList, "bash", "-c", "diff  "
						+ csapApp.getDefinitionFile().getAbsolutePath() + " "
						+ tempConfigFile.getAbsolutePath() );
				sourceControlManager.executeShell( parmList, outputManager.getBufferedWriter() );

				outputManager.print( CSAP.CONFIG_PARSE_ERROR
						+ "Parsing error in application: " + errorStack );
				// Application.getCustomStackTrace(e1)
				outputManager.print( "\n     ACTION Required: Fix the error, and try again." );
			}

		} catch (Exception e) {
			// svn failures come in here
			String errorStack = CSAP.getCsapFilteredStackTrace( e );
			logger.error( "Failed updating:  {}", errorStack );

			outputManager.print( CSAP.CONFIG_PARSE_ERROR
					+ "Failed updating application definition:\n" + errorStack );
		} finally {
			outputManager.close();
		}

		// this is what triggers local host to update.
		// in case of password update --- this will occur AFTER transfers have
		// completed
		csapApp.updateCache( true );

		ObjectNode resultJson = jacksonMapper.createObjectNode();
		resultJson.put( "result", outputManager.getContents() );

		return resultJson;
	}

	private void applicationUpdate (
										OutputFileMgr outputManager, StringBuffer parsingResultsBuffer,
										boolean isApplyButNoCheckin, File tempConfigFile,
										String scmBranch, File checkedOutSourceFolder, String scmUserid, String encryptedPass,
										String comment, String releasePackage,
										File globalModelBuildFolder, String selectedConfig )
			throws IOException, Exception {

		outputManager.print( "\n\n =============  Parsing file success \n\n" );
		if ( parsingResultsBuffer.indexOf( CSAP.CONFIG_PARSE_WARN ) != -1 ) {

			csapApp.updateOutputWithLimitedInfo( CSAP.CONFIG_PARSE_WARN, 25, outputManager,
				parsingResultsBuffer, null );

		}
		if ( !isApplyButNoCheckin ) {
			logger.info( "checking updated config into source control:"
					+ tempConfigFile.getAbsolutePath() );

			// Only used if new package has been added
			String applicationSvnUrl = csapApp.getSourceLocation();
			if ( csapApp.getSourceType().equals( SourceControlManager.ScmProvider.svn.key ) ) {
				applicationSvnUrl = csapApp.getSourceLocation().replace( "trunk", scmBranch );
			}

			List<File> filesToAdd = new ArrayList<>();
			List<File> filesToDelete = new ArrayList<>();

			// copy in any property files that have been updated in working
			// folder to the checked folder
			File resourcesWorkingFolder = csapApp.getResourcesWorkingFolder();
			File overRideWorkingFolder = new File( checkedOutSourceFolder, "propertyOverride" );
			if ( resourcesWorkingFolder.exists() && resourcesWorkingFolder.isDirectory() ) {

				overRideWorkingFolder.mkdirs(); // in case it is not already
												// present

				outputManager.print(
					"\n\n ===== Copying updated service property files in : "
							+ resourcesWorkingFolder.getAbsolutePath()
							+ " to: " + overRideWorkingFolder.getAbsolutePath() );

				FileUtils.copyDirectory( resourcesWorkingFolder, overRideWorkingFolder );
				FileUtils.deleteQuietly( resourcesWorkingFolder );

				ArrayNode adds = csapApp.getPendingResourceAdds();
				filesToAdd = IntStream.range( 0, adds.size() )
					.mapToObj( adds::get )
					.map( JsonNode::asText )
					.distinct()
					.map( item -> {
						return new File( overRideWorkingFolder, item );
					} )
					.collect( Collectors.toList() );

			}

			ArrayNode deletes = csapApp.getPendingResourceDeletes();
			if ( deletes.size() > 0 ) {
				filesToDelete = IntStream.range( 0, deletes.size() )
					.mapToObj( deletes::get )
					.map( JsonNode::asText )
					.distinct()
					.map( item -> {
						return new File( overRideWorkingFolder, item );
					} )
					.collect( Collectors.toList() );
			}

			sourceControlManager.checkInFolder(
				SourceControlManager.ScmProvider.parse( csapApp.getSourceType() ),
				applicationSvnUrl, tempConfigFile,
				scmUserid, encryptedPass,
				scmBranch, comment,
				filesToAdd, filesToDelete,
				outputManager.getBufferedWriter() );

			csapApp.resetPendingResourceUpdates();

			String details = "\nFull Path: " + tempConfigFile.getAbsolutePath();
			details += "\n Comments: " + comment;
			csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/definition/checkin",
				CsapUser.currentUsersID(),
				releasePackage, details );
		} else {

			csapEventClient.generateEvent( CsapEventClient.CSAP_OS_CATEGORY + "/definition/apply",
				CsapUser.currentUsersID(),
				releasePackage, "No check in, warning." );
		}

		// Finally - lets reload
		File liveConfigFile = new File( globalModelBuildFolder, selectedConfig );

		// create the scm folder if it does not exist - would occur
		// on a new host
		if ( !liveConfigFile.getParentFile().exists() ) {
			// liveConfigFile.getParentFile().mkdir();
			// Support for clean installs with no svn

			File defaultConfig = new File( csapApp.getStagingFolder(), "bin/defaultConf" );

			if ( defaultConfig.exists() ) {

				logger.warn( "Did not find: " + liveConfigFile.getAbsolutePath()
						+ ", so using defaultCluster: " + defaultConfig );

				FileUtils.copyDirectory( defaultConfig, liveConfigFile.getParentFile() );
				outputManager.print( "Created  default Config: "
						+ liveConfigFile.getParentFile().getAbsolutePath()
						+ "\n initialized from: " + defaultConfig.getAbsolutePath()
						+ "\n containing: " + Arrays.asList( liveConfigFile.getParentFile().list() ) );
			} else {
				// OK for unit tests
				logger.warn( "Did not find default configuration: " + defaultConfig.getAbsolutePath() );
			}

		}

		backupCurrentApplication( globalModelBuildFolder, outputManager.getBufferedWriter() );

		FileUtils.copyDirectory( checkedOutSourceFolder, globalModelBuildFolder );
		outputManager.print( " Copied: " + checkedOutSourceFolder.getAbsolutePath() + " to: "
				+ globalModelBuildFolder.getAbsolutePath() );

		// results pushed directly onto httpResponseBuffeer
		activateUpdatedDefinitionAndTransfer(
			csapApp.getModel( releasePackage ),
			scmUserid, liveConfigFile, parsingResultsBuffer,
			outputManager, comment );
	}

	// Adds a .old suffix
	private void backupCurrentApplication ( File globalModelBuildFolder, BufferedWriter writer )
			throws IOException {
		// In case of Check-IN - or reload - working folder will
		// contain propertyOverrid files from svn Checkout
		File backUpFolder = new File( globalModelBuildFolder.getCanonicalPath() + ".old" );
		if ( backUpFolder.exists() ) {
			FileUtils.deleteQuietly( backUpFolder );
			writer.write( "\n\n Deleting previous backup: " + backUpFolder.getAbsolutePath() + "\n" );
		}
		if ( globalModelBuildFolder.exists() ) {
			// check out into a clean folder every time.
			writer.write( "\n\n Moving : " + globalModelBuildFolder.getAbsolutePath()
					+ " to: " + backUpFolder.getAbsolutePath() + "\n" );
			FileUtils.moveDirectory( globalModelBuildFolder, backUpFolder );
		} else {
			logger.warn( "Folder does not exist: {}", globalModelBuildFolder.getCanonicalPath() );
		}
	}

	private Lock configLock = new ReentrantLock();

	private String configLockMessage = "";

	/**
	 * Method for getting the latest server from CVS
	 */
	@RequestMapping ( value = "/CapabiltyReload" , produces = MediaType.TEXT_PLAIN_VALUE )
	public String reloadApplication (
										@RequestParam ( "scmUserid" ) String scmUserid,
										@RequestParam ( "scmPass" ) String rawPass,
										@RequestParam ( "scmBranch" ) String scmBranch,
										@RequestParam ( value = CSAP.SERVICE_PORT_PARAM , required = false ) String svcName )
			throws Exception {

		String scmPass = encryptor.encrypt( rawPass ); // immediately encrypt
		// pass

		StringBuilder results = new StringBuilder();

		OutputFileMgr outputManager = new OutputFileMgr( csapApp.getProcessingDir(),
			"/CapabiltyReload" );

		logger.info( "scmUserid: " + scmUserid + " scmBranch: " + scmBranch + " svcName: " + svcName );

		String buildItem = " User: " + scmUserid + " Service: " + svcName;

		if ( configLock.tryLock() ) {
			try {
				configLockMessage = buildItem;
				boolean checkOutSuccess = reloadUsingSourceControl(
					scmUserid, scmPass, scmBranch,
					svcName, outputManager.getBufferedWriter() );

				if ( checkOutSuccess ) {
					reloadParseAndStore( scmUserid, results, outputManager );
					csapApp.updateCache( true );
					csapApp.resetPendingResourceUpdates();
				} else {
					results.append( "CS-AP Reload error - check out Failed: " + outputManager.getContents() );
				}

			} catch (Throwable e) {
				results.append( "CS-AP Reload error - got an exception due to cluster reload. Please try again" );
				logger.error( "Failed to reload", e );

			} finally {
				configLock.unlock();
				configLockMessage = "";

			}
		} else {
			results.append( CSAP.CONFIG_PARSE_WARN
					+ "\nPlease try again in a few minutes, reload already in progress on host: "
					+ configLockMessage );
		}
		outputManager.close();

		return outputManager.getContents();

	}

	private void reloadParseAndStore (	String scmUserid,
										StringBuilder results, OutputFileMgr outputMgr )
			throws IOException {

		File jsConfigFile = new File( csapApp.getRootModelBuildLocation()
				+ "/" + csapApp.getDefinitionFile().getName() );

		try {
			StringBuffer resultBuf = csapApp.getParser().parseConfig( true, jsConfigFile );

			if ( (resultBuf != null)
					&& resultBuf.indexOf( CSAP.CONFIG_PARSE_ERROR ) == -1 ) {
				// results are auto appended to resultsBuf
				activateUpdatedDefinitionAndTransfer(
					csapApp.getRootModel(),
					scmUserid, jsConfigFile, resultBuf, outputMgr,
					"Restoring definition from source control system." );

			} else {
				logger.error( "Failed to parse" );
				if ( (resultBuf != null) ) {
					outputMgr.print( "-" );
					outputMgr.print(
						"\n\n =============  Parsing file errors \n\n" + resultBuf );

					outputMgr.print( "-" );

					outputMgr.print( "-" );
					outputMgr.print( "\n\n============= Found Semantic Errors !! ====================\n"
							+ "Filtered output for :"
							+ CSAP.CONFIG_PARSE_ERROR );
					List<String> parmList = new ArrayList<String>();
					Collections
						.addAll( parmList,
							"bash",
							"-c",
							"diff  " + csapApp.getDefinitionFile().getAbsolutePath() + " "
									+ jsConfigFile.getAbsolutePath() );
					results.append( sourceControlManager.executeShell( parmList,
						outputMgr.getBufferedWriter() ) );

					csapApp.updateOutputWithLimitedInfo( CSAP.CONFIG_PARSE_ERROR, 999, outputMgr, resultBuf,
						null );
				}
			}
		} catch (Exception e1) {

			logger.error( "Definition reload failed: {}", CSAP.getCsapFilteredStackTrace( e1 ) );
			// List<String> parmList = new ArrayList<String>();
			// Collections.addAll( parmList, "bash", "-c",
			// "diff " + csapApp.getDefinitionFile().getAbsolutePath()
			// + " " + jsConfigFile.getAbsolutePath() );
			// results.append( sourceControlManager.executeShell( parmList,
			// outputMgr.getBufferedWriter() ) );

			outputMgr.print( CSAP.CONFIG_PARSE_ERROR +
					" Failed to load updated definition: " + CSAP.getCsapFilteredStackTrace( e1 ) );
			// Application.getCustomStackTrace(e1)
			outputMgr.print( "===== ACTION Required: Fix the error, reload config" );
		}

	}

	/**
	 * Currently svn Update
	 *
	 *
	 * @param scmUserid
	 * @param scmPass
	 * @param scmBranch
	 * @param requireXml
	 * @param svcName
	 * @param request
	 * @param response
	 * @return
	 * @throws IOException
	 */
	private boolean reloadUsingSourceControl (
												String scmUserid, String encryptedPass,
												String scmBranch, String svcName,
												BufferedWriter outputWriter )
			throws IOException {

		try {
			ServiceInstance instanceConfig = new ServiceInstance();
			instanceConfig.setScmLocation( csapApp.getSourceLocation() );
			instanceConfig.setScm( csapApp.getSourceType() );

			File clusterFileName = new File( csapApp.getRootModelBuildLocation() );

			backupCurrentApplication( clusterFileName, outputWriter );

			sourceControlManager.checkOutFolder(
				scmUserid, encryptedPass, scmBranch,
				clusterFileName.getName(), instanceConfig, outputWriter );

		} catch (TransportException gitException) {
			logger.error( "Definition reload failed: {}", CSAP.getCsapFilteredStackTrace( gitException ) );

			outputWriter.write( "\n\n" + CSAP.CONFIG_PARSE_ERROR
					+ "Git Access Error: Verify credentials and path is correct:\n" + gitException.getMessage() );

			return false;
		} catch (Exception e) {
			logger.error( "Definition reload failed: {}", CSAP.getCsapFilteredStackTrace( e ) );

			outputWriter.write( "\n\n" + CSAP.CONFIG_PARSE_ERROR
					+ "SVN Failure: Verify password and target is correct:\n" + e );

			if ( e.toString().indexOf( "is already a working copy for a different URL" ) != -1 ) {
				File svnCheckoutFolder = new File( Application.BUILD_DIR + svcName );
				outputWriter.write( "Blowing away previous build folder, try again:"
						+ svnCheckoutFolder );
				FileUtils.deleteQuietly( svnCheckoutFolder );
			}
			return false;
		}
		return true;
	}

	private void activateUpdatedDefinitionAndTransfer (
														ReleasePackage releasePackage,
														String user,
														File updatedDefinitionFile,
														StringBuffer resultBuf,
														OutputFileMgr outputMgr,
														String comment )
			throws IOException {

		StringBuilder results = new StringBuilder();

		File liveDefinitionFolder = csapApp.getDefinitionFolder();
		File workingFolder = updatedDefinitionFile.getParentFile();

		logger.info( "Parsing file success: \n" + resultBuf );

		outputMgr.print( "\n\n =============  Parsing file success,  Overwriting: "
				+ csapApp.getDefinitionFile().getAbsolutePath() + "\n\n" );

		if ( Application.isRunningOnDesktop() && !csapApp.isTestMode() ) {
			File testLocation = new File( workingFolder.getParentFile(), workingFolder.getName() + ".desktop" );

			FileUtils.deleteQuietly( testLocation );
			logger.warn( "Exiting early on desktop. Filtered copy location: {}", testLocation );
			FileUtils.copyDirectory( workingFolder, testLocation, getGitFilter() );

			outputMgr.print( "Copying checked out location: " + workingFolder.getAbsolutePath()
					+ " to testLocation: " + testLocation );

			sendEmailToInfraAdmin(
				releasePackage,
				"Updated definition, diff is attached. Refer to source history for more information",
				"Exiting early on desktop ===> " + comment,
				resultBuf.toString(),
				"resultBuf.txt" );
			outputMgr.print( "\n\n WARNING: Desktop server configured, skipping step\n\n" );
			return;
		}

		StringBuffer commandResultsBuf = new StringBuffer(
			"\n\n========================= saveAndLoadConfig ====================\n" );

		// Folder level comparison via OS
		List<String> parmList = new ArrayList<String>();
		Collections.addAll(
			parmList,
			"bash",
			"-c",
			"diff  " + liveDefinitionFolder.getAbsolutePath() + " "
					+ workingFolder.getAbsolutePath() );

		commandResultsBuf.append(
			sourceControlManager
				.executeShell( parmList, outputMgr.getBufferedWriter() ) );

		logger.info( "Diff results in output buffer" );

		String emailResults = sendEmailToInfraAdmin(
			releasePackage,
			"Updated definition, diff is attached. Refer to source history for more information",
			comment,
			commandResultsBuf.toString(),
			releasePackage.getReleasePackageName() + "_diff.txt" );

		outputMgr.print( emailResults );

		results.append( commandResultsBuf ); // only adding the diff, others
		// clutter the ui

		// FileUtils.deleteQuietly( liveDefinitionFolder );
		// outputMgr.print( "Deleted: " + liveDefinitionFolder );
		backupCurrentApplication( liveDefinitionFolder, outputMgr.getBufferedWriter() );
		liveDefinitionFolder.mkdir();

		// FileUtils.copyDirectory( updatedDefinitionFile.getParentFile(),
		// liveDefinitionFolder );
		outputMgr.print( "Copying checked out location: " + workingFolder.getAbsolutePath()
				+ " to live location: " + liveDefinitionFolder.getAbsolutePath() );
		FileUtils.copyDirectory( updatedDefinitionFile.getParentFile(), liveDefinitionFolder, getGitFilter() );

		if ( Application.isJvmInManagerMode() ) {
			TransferManager transferManager = new TransferManager( csapApp, 120, outputMgr.getBufferedWriter() );

			transferManager.setDeleteExisting( true );

			transferManager.httpCopyViaCsAgent( CsapUser.currentUsersID(),
				liveDefinitionFolder,
				csapApp.getDefinitionToken(),
				new ArrayList<String>( csapApp
					.getAllHostsInAllPackagesInCurrentLifecycle() ) );

			// in case of agent password update is there a race condition?
			String transferResults = transferManager.waitForComplete();

			if ( transferResults.contains( CSAP.CONFIG_PARSE_ERROR ) ) {
				results.append( transferResults );
			}
			commandResultsBuf.append( transferResults );
		} else {
			outputMgr.print( "\n Running on Agent - Definition has ONLY been updated on: "
					+ Application.getHOST_NAME() );
			commandResultsBuf.append( "\n Running on Agent - Definition has ONLY been updated on: "
					+ Application.getHOST_NAME() );
		}

		commandResultsBuf
			.append( "\n\n ============       definitions updated, reloads will occur within 60 seconds \n\n" );

		// Scince not all output is logged to console, we dump to logs
		// if (logger.isDebugEnabled())
		// logger.debug(commandResultsBuf);
		csapEventClient.generateEvent(
			CsapEventClient.CSAP_OS_CATEGORY + "/definition/reload",
			user,
			"Cluster Reload",
			commandResultsBuf.toString() );

		outputMgr
			.print( "\n\n ============       definitions updated, reloads will occur within 60 seconds \n\n" );

		if ( resultBuf.indexOf( CSAP.CONFIG_PARSE_WARN ) != -1 ) {

			csapApp.updateOutputWithLimitedInfo( CSAP.CONFIG_PARSE_WARN, 25, outputMgr, resultBuf, null );

		}

		return;
	}

	private FileFilter getGitFilter () {
		FileFilter gitFilter = new FileFilter() {
			public boolean accept ( File file ) {
				logger.debug( "name: {}, dir: {}", file.getName(), file.isDirectory() );
				if ( file.getName().startsWith( "." ) )
					return false;
				return true;
			}
		};
		return gitFilter;
	}

}
