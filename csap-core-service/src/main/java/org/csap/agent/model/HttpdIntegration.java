package org.csap.agent.model;

import static org.csap.agent.model.Application.AGENT_ID;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.csap.agent.CSAP;
import org.csap.agent.linux.OsCommandRunner;
import org.csap.agent.misc.CsapEventClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *
 * Utility class for generating httpd configuration from CSAP Model
 *
 * @author someDeveloper
 * @see <a href="doc-files/csagent.jpg"> Click to enlarge <IMG width=300
 *      SRC="doc-files/csagent.jpg"></a>
 *
 * @see <a href="doc-files/spring.jpg" > Click to enlarge <IMG width=300
 *      SRC="doc-files/modelDocs.jpg"></a>
 */
public class HttpdIntegration {

	final Logger logger = LoggerFactory.getLogger( this.getClass() );

	private static int NUM_WORKERS_PER_LINE = 5;
	public static String HTTP_WORKER_EXPORT_FILE;
	public static String HTTP_WORKER_FILE;
	public static String HTTP_MODJK_EXPORT_FILE;
	public static String HTTP_MODJK_FILE;
	public static String EXPORT_TRIGGER_FILE;
	public static String EXPORT_WEB_TAG = "exportWeb";
	public static String PROXY_FILE;
	public static String REWRITE_FILE;

	public static String SKIP_INTERNAL_AJP_TAG = "skipInternalAjp";
	public static String SKIP_INTERNAL_HTTP_TAG = "skipInternalHttp";

	OsCommandRunner osCommandRunner = new OsCommandRunner( 90, 3, "HttpdIntegration" ); // apachectl

	Application csapApp = null;

	public HttpdIntegration(Application manager) {
		this.csapApp = manager;
	}

	public void updateConstants() {
		String httpConfFolder = csapApp.stagingFolderAsString() + "/httpdConf/";
		REWRITE_FILE = httpConfFolder + "csspRewrite.conf";
		HTTP_WORKER_FILE = httpConfFolder + "worker.properties";
		HTTP_WORKER_EXPORT_FILE = csapApp.stagingFolderAsString()+ "/httpdConf/csspWorkerExport.properties";
		HTTP_MODJK_FILE = httpConfFolder + "csspJkMount.conf";
		EXPORT_TRIGGER_FILE = httpConfFolder + "exportTrigger.txt";
		HTTP_MODJK_EXPORT_FILE = httpConfFolder + "csspJkMountExport.conf";
		PROXY_FILE = httpConfFolder + "proxy.conf";
		
		logger.info( "Http Configuration Folder: {}", httpConfFolder );
	}

	private void createHttpConfigurationFolder () {
		
		String httpConfFolder = csapApp.stagingFolderAsString() + "/httpdConf/";
		
		File httpdConfFile = new File( httpConfFolder );
		if ( !httpdConfFile.exists() ) {
			logger.info( "Did not find " + httpdConfFile + ", creating now" );
			if ( !httpdConfFile.mkdirs() ) {
				logger.error( "Failed creating " + httpdConfFile );
			}
		}
	}

	/**
	 * Generates the apache modjk and worker.properties files.
	 *
	 * For testing in eclipse, update the eclipse/getIntancesResults.txt file
	 *
	 * @return
	 */

	public File getHttpdWorkersFile() {
		return  new File( HTTP_WORKER_FILE );
	}

	public String buildHttpdConfiguration() {
		
		createHttpConfigurationFolder();

		logger.warn( "\n ============= Updating Loadbalanceing configs ========\n" );

		csapApp.getEventClient().generateEvent(
				CsapEventClient.CSAP_SYSTEM_CATEGORY + "/httpd/update",
				Application.SYS_USER, "configuration files modified",
				"Httpd Updating: " + HTTP_MODJK_FILE + ", " + HTTP_WORKER_FILE );

		StringBuffer workerSettingsBuffer = new StringBuffer( "" );
		StringBuffer workerSettingsExportBuffer = new StringBuffer( "" );
		// updateHostInfo(true, hostFilter);
		// for (String host : getEnvHosts( Application.getEnv() )) {

		StringBuffer jkMountBuffer = new StringBuffer( "\n\n# Mod_jk httpd.conf for lifecycle: "
				+ Application.getCurrentLifeCycle() + "\n\n" );

		StringBuffer jkMountExportBuffer = new StringBuffer(
				"\n\n# Mod_jk Secure Exports (Usually for export to OAM Server): "
				+ Application.getCurrentLifeCycle() + "\n\n" );

		StringBuffer workerListBuffer = new StringBuffer( "\n\n# Mod_jk worker.properties:\n\n" );
		workerListBuffer
				.append( "# ref. http://tomcat.apache.org/connectors-doc/generic_howto/loadbalancers.html\n\n" );

		StringBuffer workerListExportBuffer = new StringBuffer(
				"\n\n# Mod_jk worker.properties Secure Exports (Usually for export to OAM Server):\n\n" );
		workerListExportBuffer
				.append( "# ref. http://tomcat.apache.org/connectors-doc/generic_howto/loadbalancers.html\n\n" );

		if ( csapApp.getSvcToConfigMap().keySet().size() == 0 ) {
			// lets reload - only happens in eclipse
			csapApp.updateCache( true );
		}

		generateHttpProxy();

		buildHttpdConfigForStandardClusters( workerSettingsBuffer, workerSettingsExportBuffer, jkMountBuffer,
				jkMountExportBuffer, workerListBuffer, workerListExportBuffer );

		generateHttpdConfigForMultipleVmPartition( workerSettingsBuffer, workerSettingsExportBuffer,
				jkMountBuffer,
				jkMountExportBuffer, workerListBuffer, workerListExportBuffer );

		buildHttpdConfigForSingleVmPartition( workerSettingsBuffer, jkMountBuffer, workerListBuffer,
				SKIP_INTERNAL_AJP_TAG );

		buildHttpdConfigForSingleVmPartition( workerSettingsExportBuffer, jkMountExportBuffer,
				workerListExportBuffer, EXPORT_WEB_TAG );

		// need to strip the newline
		workerListBuffer.deleteCharAt( workerListBuffer.length() - 2 );

		// modjk status hooks
		workerListBuffer.append( ", mystatus" );
		workerSettingsBuffer.append( "\nworker.mystatus.type=status\n" );
		workerSettingsBuffer.append( "\nworker.mystatus.css=/CsAgent/css/modjk.css\n" );
		// Use CsAgent to protect the url
		// workerSettingsBuffer.append("\nworker.mystatus.read_only=true\n");
		workerListBuffer.append( "\n\n" );
		jkMountBuffer.append( "\nJkMount /status* mystatus\n" );

		workerListExportBuffer.append( ", mystatus" );
		workerSettingsExportBuffer.append( "\nworker.mystatus.type=status\n" );
		workerSettingsExportBuffer.append( "\nworker.mystatus.css=/CsAgent/css/modjk.css\n" );
		// Use CsAgent to protect the url
		// workerSettingsExportBuffer.append("\nworker.mystatus.read_only=true\n");
		workerListExportBuffer.append( "\n\n" );
		jkMountExportBuffer.append( "\nJkMount /status* mystatus\n" );

		generateModJKFile( jkMountBuffer, jkMountExportBuffer );

		generateWorkerFile( workerListBuffer, workerListExportBuffer, workerSettingsBuffer,
				workerSettingsExportBuffer );
		generateRewriteFile();

		if ( csapApp.lifeCycleSettings().isAutoRestartHttpdOnClusterReload() ) {
			logger.info( "Doing a graceful restart on apache" );
			List<String> parmList;
			parmList = Arrays.asList( "bash", "-c", "apachectl graceful" );

			logger.info( "Restarting httpd" );
			String results = osCommandRunner.executeString( parmList, csapApp.getProcessingDir() );

			csapApp.getEventClient().generateEvent( CsapEventClient.CSAP_SYSTEM_CATEGORY + "/httpd/restart",
					Application.SYS_USER,
					"Graceful", results );
		} else {
			csapApp.getEventClient()
					.generateEvent( CsapEventClient.CSAP_SYSTEM_CATEGORY + "/httpd/restart",
							Application.SYS_USER,
							"Auto restart disabled in definition",
							"Operator should manually restart httpds 1 at a time if any new services have been added." );
		}

		// Finally
		logger.info( "Updating status file - used to ripple export of modjk" );
		// header.append("JkRequestLogFormat \"%w %V %T\"\n");
		try {
			FileWriter fstream = new FileWriter( new File( EXPORT_TRIGGER_FILE ) );
			BufferedWriter out = new BufferedWriter( fstream );
			out.write( Long.toString( System.currentTimeMillis() ) );
			out.close();
		} catch ( IOException e ) {
			logger.error( "Failed to write file", e );
		}

		return jkMountBuffer.toString() + workerListBuffer.toString()
				+ workerSettingsBuffer.toString();
	}

	private void buildHttpdConfigForStandardClusters(StringBuffer workerSettingsBuffer,
			StringBuffer workerSettingsSecureBuffer, StringBuffer jkMountBuffer,
			StringBuffer jkMountSecureBuffer, StringBuffer workerListBuffer,
			StringBuffer workerListSecureBuffer) {

		int workerListCount = 0;
		int workerListSecureCount = 0;

		csapApp.getRootModel().getReleasePackages()
				.forEach( model -> {

					model.getServiceNameStream()
							.forEach( serviceName -> {

								buildHttpdConfigForStandardClustersModel(
										model, serviceName,
										workerSettingsBuffer, workerSettingsSecureBuffer, jkMountBuffer,
										jkMountSecureBuffer, workerListBuffer, workerListSecureBuffer, workerListCount,
										workerListSecureCount );
							} );
				} );
	}

	private ObjectMapper jacksonMapper = new ObjectMapper();

	private void buildHttpdConfigForStandardClustersModel(
			ReleasePackage model, String serviceName,
			StringBuffer workerSettingsBuffer,
			StringBuffer workerSettingsSecureBuffer, StringBuffer jkMountBuffer, StringBuffer jkMountSecureBuffer,
			StringBuffer workerListBuffer, StringBuffer workerListSecureBuffer, int workerListCount,
			int workerListSecureCount) {
		// logger.debug("Service Name: " + serviceName);
		// sbuf.append("\n\t" + service + "\n\t\t");
		if ( serviceName.equalsIgnoreCase( "httpd" ) ) {
			return;
		}
		String workerName = serviceName + "_";
		String workerId = ("worker." + workerName);
		String workerLB = workerId + "LB";
		String workerLBHeader = workerName + "LB";
		// results.append("\n\t" + serviceName + "\n\t\t");
		// ArrayList<File> svcList =
		// svcNameToSvcPathList.get(serviceName);
		StringBuffer instances = new StringBuffer();
		workerSettingsBuffer.append( "\n ##### Service: " + serviceName + "\n\n" );
		workerSettingsSecureBuffer.append( "\n ##### Service: " + serviceName + "\n\n" );
		ArrayList<ServiceInstance> svcList = model.getServiceToAllInstancesMap().get( serviceName );
		String context = serviceName;

		// httpd filtering as not everything is exported
		boolean isIncludedInInternal = false;
		boolean isIncludedInExport = false;

		String cookieName = "";
		ObjectNode apacheModjkIntegration = jacksonMapper.createObjectNode();
		for ( ServiceInstance svcInstance : svcList ) {

			StringBuffer tempWorkerSettingsBuffer = new StringBuffer();

			if ( csapApp.lifeCycleSettings().isLoadBalanceVmFilter( svcInstance.getHostName() ) ) {
				continue;
			}

			if ( (!checkInstanceInCurrentLifecycle( svcInstance )
					|| svcInstance.isOs()
					|| svcInstance.isWrapper()
					|| svcInstance.isDockerContainer()
					|| (svcInstance.isSpringBoot() && !svcInstance.isApacheWebIntegration())
					|| (svcInstance.isConfigureAsSingleVmPartition() || svcInstance
					.isConfigureAsMultiVmPartition()) || (svcInstance
							.getLifecycle()
							.toLowerCase().indexOf( "sandbox" ) != -1)) ) {
				continue;
			}

			// Need to ignore CsAgent on sandboxes in dev.
			if ( serviceName.equalsIgnoreCase( AGENT_ID ) ) {
				// InstanceConfig testInstance =
				// model.getHostToConfigMap()
				// .get(svcInstance.getHostName()).get(0);
				// if
				// (testInstance.getLifecycle().toLowerCase().indexOf("sandbox")
				// != -1
				// || !testInstance.isConfigureAsFactory() )
				continue;
			}

			// factory instances are hooked in special below
			String host = svcInstance.getHostName();
			String svcPort = svcInstance.getPort();
			String ajpPort = svcPort;
			try {
				ajpPort = ajpPort.substring( 0, 3 ) + "2";
			} catch ( Exception e ) {
				logger.error( "Failed to parse port: " + ajpPort );
			}
			String instanceId = workerId + svcPort + host;
			cookieName = svcInstance.getCookieName();
			ObjectNode serviceWeb = svcInstance.getAttributeAsObject( ServiceAttributes.webServerTomcat );
			if ( serviceWeb != null ) {
				apacheModjkIntegration = serviceWeb;
			}
			instances.append( serviceName + "_" + svcPort + host + "," );
			tempWorkerSettingsBuffer.append( instanceId + ".port=" + ajpPort + "\n" );
			tempWorkerSettingsBuffer.append( instanceId + ".host=" + host + "\n" );
			tempWorkerSettingsBuffer.append( instanceId + ".type=ajp13\n" );
			tempWorkerSettingsBuffer.append( instanceId + ".secret="
					+ csapApp.getTomcatAjpKey() + "\n" );

			// adding in lifecycle to prevent cross-infra calls
			tempWorkerSettingsBuffer.append( instanceId + ".lbfactor=1\n" );
			tempWorkerSettingsBuffer.append( instanceId + ".socket_keepalive=True\n" );
			tempWorkerSettingsBuffer.append( instanceId + ".connection_pool_timeout=10\n" );
			tempWorkerSettingsBuffer.append( instanceId + ".connection_pool_minsize=0\n" );

			if ( apacheModjkIntegration.has( "connection" ) && apacheModjkIntegration.get( "connection" ).isArray() ) {
				ArrayNode connectRules = (ArrayNode) apacheModjkIntegration.get( "connection" );
				// http://tomcat.apache.org/connectors-doc/reference/workers.html
				tempWorkerSettingsBuffer.append( "# Custom Settings Used, refer to http://tomcat.apache.org/connectors-doc/reference/workers.html \n" );
				for ( JsonNode configItem : connectRules ) {
					tempWorkerSettingsBuffer.append( instanceId + "." + configItem.asText() + "\n" );
				}
			}
			tempWorkerSettingsBuffer.append( "\n\n" );

			if ( !svcInstance.getMetaData().contains( SKIP_INTERNAL_AJP_TAG ) ) {
				workerSettingsBuffer.append( tempWorkerSettingsBuffer );
				isIncludedInInternal = true;
			}
			if ( svcInstance.getMetaData().contains( EXPORT_WEB_TAG ) ) {
				workerSettingsSecureBuffer.append( tempWorkerSettingsBuffer );
				isIncludedInExport = true;
			}

			context = svcInstance.getContext();

		}

		// only wire in services for which there is at least one
		// instance
		// configured
		if ( instances.length() > 0 ) {

			// Get rid of trailing ,
			instances.deleteCharAt( instances.length() - 1 );

			if ( isIncludedInInternal ) {

				jkMountBuffer.append( "JkMount /" + context + "* " + workerLBHeader + "\n" );

				if ( workerListCount++ % NUM_WORKERS_PER_LINE == 0 ) {
					workerListBuffer.append( "\nworker.list=" );
				}
				workerListBuffer.append( workerLBHeader + ", " );

				workerSettingsBuffer.append( workerLB + ".type=lb\n" );
				workerSettingsBuffer.append( workerLB + ".balance_workers=" + instances
						+ "\n" );

				if ( cookieName.length() > 0 ) {
					workerSettingsBuffer.append( workerLB + ".session_cookie=" + cookieName
							+ "\n" );
				}
				// logger.info("{} apacheModjkIntegration: {}", serviceName, apacheModjkIntegration.toString() ) ;

				if ( apacheModjkIntegration.has( "loadBalance" ) && apacheModjkIntegration.get( "loadBalance" ).isArray() ) {
					ArrayNode lbRules = (ArrayNode) apacheModjkIntegration.get( "loadBalance" );
					// http://tomcat.apache.org/connectors-doc/reference/workers.html
					workerSettingsBuffer.append( "# Custom Settings Used, refer to http://tomcat.apache.org/connectors-doc/reference/workers.html \n" );
					for ( JsonNode configItem : lbRules ) {
						workerSettingsBuffer.append( workerLB + "." + configItem.asText() + "\n" );
					}
					workerSettingsBuffer.append( "\n\n" );
				} else {
					workerSettingsBuffer.append( workerLB + ".sticky_session=1\n\n" );
				}

			}
			if ( isIncludedInExport ) {

				jkMountSecureBuffer.append( "JkMount /" + context + "* " + workerLBHeader
						+ "\n" );

				if ( workerListSecureCount++ % NUM_WORKERS_PER_LINE == 0 ) {
					workerListSecureBuffer.append( "\nworker.list=" );
				}
				workerListSecureBuffer.append( workerLBHeader + ", " );

				workerSettingsSecureBuffer.append( workerLB + ".type=lb\n" );
				// instances.deleteCharAt(instances.length() - 1);
				workerSettingsSecureBuffer.append( workerLB + ".balance_workers="
						+ instances + "\n" );

				if ( apacheModjkIntegration.size() > 0 ) {
					for ( JsonNode configItem : apacheModjkIntegration ) {
						workerSettingsSecureBuffer.append( workerLB + "." + configItem.asText() + "\n" );
					}
				} else {
					workerSettingsSecureBuffer.append( workerLB + ".sticky_session=1\n\n" );
				}
			} else {
				workerSettingsSecureBuffer
						.append( "### Skipping service - add the secure flag to metadata in clusterConfig to include\n\n" );
			}
		} else {
			workerSettingsBuffer
					.append( "### Skipping service - maybe service is os/wrapper/factory\n\n" );
			workerSettingsSecureBuffer
					.append( "### Skipping service - maybe service is os/wrapper/factory\n\n" );
		}
	}

	private boolean checkInstanceInCurrentLifecycle(ServiceInstance svcInstance) {

		if ( svcInstance.getLifecycle().startsWith( Application.getCurrentLifeCycle() + "-" ) ) {
			return true;
		} else {
			return false;
		}

		// return getCurrentLifeCycle().startsWith(svcInstance.getLifecycle());
		// return svcInstance.getLifecycle().equalsIgnoreCase(
		// getCurrentLifeCycle());
	}

	/*
	 * 
	 * Runs in one of 2 modes: SKIP_INTERNAL_AJP_TAG EXPORT_WEB_TAG
	 */
	private void buildHttpdConfigForSingleVmPartition(StringBuffer workerConfigBuffer,
			StringBuffer modjkMountsBuffer, StringBuffer workerListBuffer, String filter) {

		//
		csapApp.getRootModel()
				.getReleasePackages()
				.forEach(
						model -> {

							model.getHostNamesInCurrentLcStream()
									.forEach(
											hostName -> {
												buildHttpdConfigSingleVmModel( hostName, workerConfigBuffer,
														modjkMountsBuffer,
														workerListBuffer, filter, model );
											} );
						} );

	}

	private void buildHttpdConfigSingleVmModel(String lifecycleHost, StringBuffer workerConfigBuffer,
			StringBuffer modjkMountsBuffer,
			StringBuffer workerListBuffer, String filter, ReleasePackage model) {
		int workerListCount = 0;

		Map<String, String> svcToLBMap = new HashMap<String, String>();
		Map<String, String> svcToCookieNameMap = new HashMap<String, String>();
		String workerName;
		String workerId = "";
		String workerLB = "";
		String workerLBHeader = "";
		// results.append("\n\t" + serviceName + "\n\t\t");
		// ArrayList<File> svcList =
		// svcNameToSvcPathList.get(serviceName);
		// workerSettings.append("\n ##### Service: " + serviceName +
		// "\n\n");
		ArrayList<ServiceInstance> svcList = model.getHostToConfigMap().get( lifecycleHost );
		for ( ServiceInstance svcInstance : svcList ) {
			if ( (!svcInstance.isConfigureAsSingleVmPartition())
					|| svcInstance.isOs()
					|| svcInstance.isWrapper()
					|| svcInstance.isDockerContainer()
					|| (svcInstance.isSpringBoot() && !svcInstance.isApacheWebIntegration())
					|| svcInstance.getServiceName().startsWith( AGENT_ID ) ) {
				continue;
			}

			if ( filter.equals( SKIP_INTERNAL_AJP_TAG )
					&& svcInstance.getMetaData().contains( filter ) ) {
				continue;
			}

			if ( filter.equals( EXPORT_WEB_TAG )
					&& !svcInstance.getMetaData().contains( filter ) ) {
				continue;
			}
			String svcName = svcInstance.getContext();
			// String svcName = svcInstance.getServiceName();
			// String svcContext = svcInstance.getContext();
			String host = svcInstance.getHostName();
			String svcPort = svcInstance.getPort();
			String ajpPort = svcPort;

			if ( !svcToLBMap.containsKey( svcName ) ) {
				workerName = svcName + "_";
				workerId = ("worker." + workerName);
				workerLB = workerId + "LB";
				workerLBHeader = workerName + "LB";

				if ( workerListCount++ % NUM_WORKERS_PER_LINE == 0 ) {
					workerListBuffer.append( "\nworker.list=" );
				}
				workerListBuffer.append( workerLBHeader + ", " );

				svcToLBMap.put( svcName, workerLB + ".balance_workers=" );
				modjkMountsBuffer.append( "JkMount /" + svcInstance.getContext() + "* "
						+ workerLBHeader + "\n" );
			}

			try {
				ajpPort = ajpPort.substring( 0, 3 ) + "2";
			} catch ( Exception e ) {
				logger.error( "Failed to parse port: " + ajpPort );
			}

			String instanceId = workerLB + svcPort;
			workerConfigBuffer.append( instanceId + ".port=" + ajpPort + "\n" );
			workerConfigBuffer.append( instanceId + ".host=" + host + "\n" );
			workerConfigBuffer.append( instanceId + ".type=ajp13\n" );
			workerConfigBuffer.append( instanceId + ".secret=" + csapApp.getTomcatAjpKey()
					+ "\n" );
			workerConfigBuffer.append( instanceId + ".lbfactor=1\n" );
			workerConfigBuffer.append( instanceId + ".socket_keepalive=True\n" );
			workerConfigBuffer.append( instanceId + ".connection_pool_timeout=60\n\n" );

			// may have multiple factory instances, append to previous
			// entry
			String newLBline = svcToLBMap.get( svcName ) + workerLBHeader + svcPort + ",";
			svcToLBMap.put( svcName, newLBline );
			svcToCookieNameMap.put( svcName, svcInstance.getCookieName() );
		}
		// only wire in services for which there is at least one
		// instance
		// configured
		for ( String svcKey : svcToLBMap.keySet() ) {
			logger.debug( " lbSvcString: " + svcKey );
			String lbString = svcToLBMap.get( svcKey );
			String prefix = lbString.substring( 0, lbString.indexOf( ".balance" ) );
			workerConfigBuffer.append( prefix + ".type=lb\n" );

			workerConfigBuffer
					.append( lbString.subSequence( 0, lbString.length() - 1 ) + "\n" );

			// Factories hack: Smart Dispatcher relies on
			// modjk/modrewrite
			// "customerIds" getting inserted before the
			// context name. in order for this to work, all cookies on
			// all
			// services in factorys
			// must use the "/" target, which then requires them to have
			// a
			// unique name
			workerConfigBuffer.append( prefix + ".session_cookie="
					+ svcToCookieNameMap.get( svcKey ) + "\n" );
			workerConfigBuffer.append( prefix + ".sticky_session=1\n\n" );

		}

	}

	private void generateHttpdConfigForMultipleVmPartition(StringBuffer workerSettingsBuffer,
			StringBuffer workerSettingsSecureBuffer, StringBuffer jkMountBuffer,
			StringBuffer jkMountSecureBuffer, StringBuffer workerListBuffer,
			StringBuffer workerListSecureBuffer) {

		csapApp.getRootModel()
				.getReleasePackages()
				.forEach(
						model -> {
							// This is a corner case for old IR deployment.
							// Basically multiVM deployments are
							// restricted to a single digit as this is hardcoded
							// in bash shell scripts.
							for ( int partionCount = 0; partionCount < 10; partionCount++ ) {
								final int id = partionCount;
								model.getServiceNameStream()
										.forEach(
												serviceName -> {

													generateHttpdConfigForMultiVmModel( serviceName, model,
															id, workerSettingsBuffer,
															workerSettingsSecureBuffer, jkMountBuffer,
															jkMountSecureBuffer, workerListBuffer,
															workerListSecureBuffer );
												} );
							}
						} );
	}

	private void generateHttpdConfigForMultiVmModel(String serviceName, ReleasePackage model, int partionCount,
			StringBuffer workerSettingsBuffer,
			StringBuffer workerSettingsSecureBuffer, StringBuffer jkMountBuffer,
			StringBuffer jkMountSecureBuffer, StringBuffer workerListBuffer,
			StringBuffer workerListSecureBuffer) {

		int workerListCount = 0;
		int workerListSecureCount = 0;
		// logger.debug("Service Name: " + serviceName);
		// sbuf.append("\n\t" + service + "\n\t\t");
		if ( serviceName.equalsIgnoreCase( "httpd" ) ) {
			return;
		}
		String workerName = serviceName + "-" + partionCount + "_";
		String workerId = ("worker." + workerName);
		String workerLB = workerId + "LB";
		String workerLBHeader = workerName + "LB";
		// results.append("\n\t" + serviceName + "\n\t\t");
		// ArrayList<File> svcList =
		// svcNameToSvcPathList.get(serviceName);
		StringBuffer instances = new StringBuffer();
		// workerSettingsBuffer.append("\n ##### Service: " +
		// serviceName + "\n\n");
		// workerSettingsSecureBuffer.append("\n ##### Service: " +
		// serviceName + "\n\n");
		ArrayList<ServiceInstance> svcList = model.getServiceToAllInstancesMap().get( serviceName );
		String context = serviceName;

		// httpd filtering as not everything is exported
		boolean isIncludedInInternal = false;
		boolean isIncludedInExport = false;

		String cookieName = "";

		for ( ServiceInstance svcInstance : svcList ) {

			// The hook for enterprise partitions
			if ( !svcInstance.getPlatformVersion()
					.equals( Integer.toString( partionCount ) ) ) {
				continue;
			}

			StringBuffer tempWorkerSettingsBuffer = new StringBuffer();

			if ( (!checkInstanceInCurrentLifecycle( svcInstance )
					|| !svcInstance.isConfigureAsMultiVmPartition()
					|| svcInstance.isOs()
					|| svcInstance.isWrapper()
					|| svcInstance.isDockerContainer()
					|| (svcInstance.isSpringBoot() && !svcInstance.isApacheWebIntegration())
					|| (svcInstance.getLifecycle()
							.toLowerCase().indexOf( "sandbox" ) != -1)) ) {
				continue;
			}

			// Need to ignore CsAgent on sandboxes in dev.
			if ( serviceName.equalsIgnoreCase( AGENT_ID ) ) {
				// InstanceConfig testInstance =
				// model.getHostToConfigMap()
				// .get(svcInstance.getHostName()).get(0);
				// if
				// (testInstance.getLifecycle().toLowerCase().indexOf("sandbox")
				// != -1
				// || !testInstance.isConfigureAsFactory() )
				continue;
			}

			// factory instances are hooked in special below
			String host = svcInstance.getHostName();
			String svcPort = svcInstance.getPort();
			String ajpPort = svcPort;
			try {
				ajpPort = ajpPort.substring( 0, 3 ) + "2";
			} catch ( Exception e ) {
				logger.error( "Failed to parse port: " + ajpPort );
			}
			String instanceId = workerId + svcPort + host;
			cookieName = svcInstance.getCookieName();
			instances.append( workerName + svcPort + host + "," );
			// instances.append(serviceName + "_" + svcPort + host +
			// ",");
			tempWorkerSettingsBuffer.append( instanceId + ".port=" + ajpPort + "\n" );
			tempWorkerSettingsBuffer.append( instanceId + ".host=" + host + "\n" );
			tempWorkerSettingsBuffer.append( instanceId + ".type=ajp13\n" );
			tempWorkerSettingsBuffer.append( instanceId + ".secret="
					+ csapApp.getTomcatAjpKey() + "\n" );

			// adding in lifecycle to prevent cross-infra calls
			tempWorkerSettingsBuffer.append( instanceId + ".lbfactor=1\n" );
			tempWorkerSettingsBuffer.append( instanceId + ".socket_keepalive=True\n" );
			tempWorkerSettingsBuffer.append( instanceId
					+ ".connection_pool_timeout=10\n\n" );
			tempWorkerSettingsBuffer.append( instanceId
					+ ".connection_pool_minsize=0\n\n" );

			if ( !svcInstance.getMetaData().contains( SKIP_INTERNAL_AJP_TAG ) ) {
				workerSettingsBuffer.append( tempWorkerSettingsBuffer );
				isIncludedInInternal = true;
			}
			if ( svcInstance.getMetaData().contains( EXPORT_WEB_TAG ) ) {
				workerSettingsSecureBuffer.append( tempWorkerSettingsBuffer );
				isIncludedInExport = true;
			}

			context = svcInstance.getContext();

		}

		// only wire in services for which there is at least one
		// instance
		// configured
		if ( instances.length() > 0 ) {

			// Get rid of trailing ,
			instances.deleteCharAt( instances.length() - 1 );

			if ( isIncludedInInternal ) {

				jkMountBuffer.append( "JkMount /" + context + "* " + workerLBHeader
						+ "\n" );

				if ( workerListCount++ % NUM_WORKERS_PER_LINE == 0 ) {
					workerListBuffer.append( "\nworker.list=" );
				}
				workerListBuffer.append( workerLBHeader + ", " );

				workerSettingsBuffer.append( workerLB + ".type=lb\n" );
				workerSettingsBuffer.append( workerLB + ".balance_workers=" + instances
						+ "\n" );

				if ( cookieName.length() > 0 ) {
					workerSettingsBuffer.append( workerLB + ".session_cookie="
							+ cookieName
							+ "\n" );
				}

				workerSettingsBuffer.append( workerLB + ".sticky_session=1\n\n" );
			}
			if ( isIncludedInExport ) {

				jkMountSecureBuffer.append( "JkMount /" + context + "* "
						+ workerLBHeader
						+ "\n" );

				if ( workerListSecureCount++ % NUM_WORKERS_PER_LINE == 0 ) {
					workerListSecureBuffer.append( "\nworker.list=" );
				}
				workerListSecureBuffer.append( workerLBHeader + ", " );

				workerSettingsSecureBuffer.append( workerLB + ".type=lb\n" );
				// instances.deleteCharAt(instances.length() - 1);
				workerSettingsSecureBuffer.append( workerLB + ".balance_workers="
						+ instances + "\n" );

				workerSettingsSecureBuffer.append( workerLB + ".sticky_session=1\n\n" );
			} else {
				workerSettingsSecureBuffer
						.append( "### Skipping service - add the secure flag to metadata in clusterConfig to include\n\n" );
			}
		} else {
			// workerSettingsBuffer
			// .append("### Skipping service - maybe service is os/wrapper/factory\n\n");
			// workerSettingsSecureBuffer
			// .append("### Skipping service - maybe service is os/wrapper/factory\n\n");
		}
	}

	public File getHttpdModjkFile() {
		return new File( HTTP_MODJK_FILE );
	}

	private void generateModJKFile(StringBuffer jkMountBuffer, StringBuffer jkMountSecureBuffer) {
		logger.info( "Generating modJK mount: " + HTTP_MODJK_FILE );

		StringBuffer header = new StringBuffer( "# Generated by " + getClass().getCanonicalName()
				+ "\n" );

		// The following is loaded in the template in staging/bin
		// header.append("LoadModule jk_module modules/mod_jk.so\n");
		// header.append("JkWorkersFile conf/worker.properties\n");
		// header.append("JkLogFile logs/mod_jk.log\n");
		// header.append("JkLogLevel info\n"); // debug info
		// header.append("JkLogStampFormat \"[%a %b %d %H:%M:%S %Y] \"\n");
		// header.append("JkOptions +ForwardKeySize +ForwardURICompat -ForwardDirectories\n");
		// header.append("JkRequestLogFormat \"%w %V %T\"\n");
		try {
			FileWriter fstream = new FileWriter( getHttpdModjkFile() );
			BufferedWriter out = new BufferedWriter( fstream );
			out.write( header.toString() );
			out.write( jkMountBuffer.toString() );
			out.close();

			fstream = new FileWriter( new File( HTTP_MODJK_EXPORT_FILE ) );
			out = new BufferedWriter( fstream );
			out.write( header.toString() );
			out.write( jkMountSecureBuffer.toString() );
			out.close();
		} catch ( IOException e ) {
			logger.error( "Failed to write file", e );
		}

	}

	private void generateProxyFile(StringBuffer mappingsBuffer) {
		logger.info( "Generating Proxy file: " + PROXY_FILE );

		File proxyFile = new File( PROXY_FILE );

		StringBuffer header = new StringBuffer( "# Generated by " + getClass().getCanonicalName()
				+ "\n" );

		// The following is loaded in the template in staging/bin
		// header.append("LoadModule jk_module modules/mod_jk.so\n");
		// header.append("JkWorkersFile conf/worker.properties\n");
		// header.append("JkLogFile logs/mod_jk.log\n");
		// header.append("JkLogLevel info\n"); // debug info
		// header.append("JkLogStampFormat \"[%a %b %d %H:%M:%S %Y] \"\n");
		// header.append("JkOptions +ForwardKeySize +ForwardURICompat -ForwardDirectories\n");
		// header.append("JkRequestLogFormat \"%w %V %T\"\n");
		try {
			FileWriter fstream = new FileWriter( proxyFile );
			BufferedWriter out = new BufferedWriter( fstream );
			out.write( header.toString() );
			out.write( mappingsBuffer.toString() );
			out.close();
		} catch ( IOException e ) {
			logger.error( "Failed to write file", e );
		}

	}


	public File getHttpdModReWriteFile() {
		return new File( REWRITE_FILE );
	}
	/**
	 * generate a test file for customers
	 */
	private void generateRewriteFile() {

		File generatedRewriteFile = getHttpdModReWriteFile();
		logger.info( "Generating Mod Rewrite file: {}",  generatedRewriteFile.getAbsolutePath() );

		StringBuffer mappingsBuffer = new StringBuffer( "# Generated by "
				+ getClass().getCanonicalName() + "\n" );
		mappingsBuffer.append( "RewriteEngine on" + "\n\n" );
		mappingsBuffer.append( "# CsAgent LB is handled by admin service\n" );
		// mappingsBuffer.append( "RewriteRule ^/CsAgent/(.*)$  /admin/$1 [R]\n\n" );
		mappingsBuffer.append( "RewriteRule ^/CsAgent/(.*)$ http://%{SERVER_NAME}/admin/$1 [R]\n\n" );

		TreeMap<String, List<ServiceInstance>> serviceToConfigs = csapApp.getRootModel()
				.serviceInstancesInCurrentLifeByName();
		for ( String svcName : serviceToConfigs.keySet() ) {

			for ( int customerId = 0; customerId < 10; ) {

				if ( svcName.equals( AGENT_ID ) ) {
					break;
				}

				List<ServiceInstance> svcConfigList = serviceToConfigs.get( svcName );

				if ( svcConfigList.size() == 0 ) break;
				
				ServiceInstance firstService = svcConfigList.get( 0 ) ;
				
				JsonNode reWrites = firstService.getAttributeAsJson(ServiceAttributes.webServerReWrite ) ;
				
				if ( reWrites != null ) {
					mappingsBuffer.append( "# Custom service rewrite from service: " + firstService.getServiceName() + "\n") ;
					reWrites.forEach( line -> {
						mappingsBuffer.append( line.asText()  + "\n") ;
					});
					mappingsBuffer.append( "\n") ;
				}
				
				
				if ( ! firstService.isConfigureAsSingleVmPartition()
						|| firstService.isWrapper() || firstService.isDockerContainer() || firstService.isOs() ) {
					break;
				}

				// logger.info("==========" + svcName + " customerId " +
				// customerId + " svcConfigList.size " + svcConfigList.size());
				boolean foundFactory = false; // hook to skip services without
				// any filter matches
				for ( ServiceInstance svcInstance : svcConfigList ) {
					foundFactory = true;
					customerId++;
					String svcContext = svcInstance.getContext();
					// String svcContext = svcInstance.getContext();
					// RewriteRule ^/customer1/ims/(.*)$
					// /CsspFactorySampleV1-tory01/$1 [PT]
					if ( svcContext.indexOf( "-" ) != -1 ) {
						mappingsBuffer.append( "RewriteRule " );

						mappingsBuffer.append( "^/csagenttestcust" + customerId + "/"
								+ svcContext.substring( 0, svcContext.indexOf( "-" ) ) + "/(.*)$ /"
								+ svcContext + "/$1" );

						mappingsBuffer.append( " [PT]\n" );
					}
				}

				if ( !foundFactory ) {
					break;
				}

			}

			try {
				FileWriter fstream = new FileWriter( generatedRewriteFile );
				BufferedWriter out = new BufferedWriter( fstream );
				out.write( mappingsBuffer.toString() );
				out.close();
			} catch ( IOException e ) {
				logger.error( "Failed to write file", e );
			}
		}

	}

	private void generateWorkerFile(StringBuffer workerListBuffer,
			StringBuffer workerListSecureBuffer, StringBuffer workerSettingsBuffer,
			StringBuffer workerSettingsSecureBuffer) {

		try {
			FileWriter fstream = new FileWriter( getHttpdWorkersFile() );
			BufferedWriter out = new BufferedWriter( fstream );
			out.write( workerListBuffer.toString() );
			out.write( "\n\n" );
			out.write( workerSettingsBuffer.toString() );
			out.close();
			// fstream = new FileWriter(new File(WORKER_EXPORT_FILE.replace(
			// "/cssp", "/" + ajpExportPrefix)));
			fstream = new FileWriter( new File( HTTP_WORKER_EXPORT_FILE ) );
			out = new BufferedWriter( fstream );
			out.write( workerListSecureBuffer.toString() );
			out.write( workerSettingsSecureBuffer.toString() );
			out.close();
		} catch ( Exception e ) {
			logger.error( "Failed to write file: {}", CSAP.getCsapFilteredStackTrace( e ) );
		}
	}

	/**
	 * Method for creating a proxy file that utilizes httpd mod_proxy to direct CsAgent requests through the LB to a
	 * Specific host
	 */
	private void generateHttpProxy() {
		StringBuffer proxyLines = new StringBuffer( "# Proxys Not In use.\n\n" );

		proxyLines.append( "# End OF Proxy config \n\n" );
		generateProxyFile( proxyLines );
	}

}
