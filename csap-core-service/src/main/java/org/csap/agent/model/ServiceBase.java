package org.csap.agent.model;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;

import org.apache.commons.lang.StringUtils;
import org.aspectj.util.IStructureModel;
import org.csap.agent.model.Application.FileToken;
import org.csap.agent.services.SourceControlManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * metadata associated with each service
 *
 *
 * @author someDeveloper
 *
 *         JsonIgnoreProperties: provides backwards compatability when instance
 *         metrics are updated
 *
 */
public abstract class ServiceBase {

	/**
	 * @return the collectPort
	 */
	public String getCollectPort () {
		return collectPort;
	}

	/**
	 * @param collectPort
	 *            the collectPort to set
	 */
	public void setCollectPort ( String collectPort ) {
		this.collectPort = collectPort;
	}

	/**
	 * @return the jmxRemoteHost
	 */
	public String getCollectHost () {
		return collectHost;
	}

	/**
	 * @param collectHost
	 *            the jmxRemoteHost to set
	 */
	public void setCollectHost ( String collectHost ) {
		this.collectHost = collectHost;
	}

	/**
	 * @return the isAllowReleaseFileToOverride
	 */
	public boolean isIsAllowReleaseFileToOverride () {
		return allowReleaseFileToOverride;
	}

	/**
	 * @param isAllowReleaseFileToOverride
	 *            the isAllowReleaseFileToOverride to set
	 */
	public void setAllowReleaseFileToOverride ( boolean allowReleaseFileToOverride ) {
		this.allowReleaseFileToOverride = allowReleaseFileToOverride;
	}

	final Logger logger = LoggerFactory.getLogger( ServiceBase.class );

	public static final String SCRIPT = "script";
	public static final String SPRING_BOOT = "SpringBoot";
	public static final String DOCKER = "docker";
	public static final String WRAPPER = "wrapper";
	public static final String CSSP3 = "cssp-3.x";

	private final static String[] serversWithTomcatJars = { ServiceInstance.SPRING_BOOT,
			"tomcat9.x", "tomcat8-5.x", "tomcat8.x", "tomcat7.x",
			CSSP3, "cssp-2.x", "cssp-1.x", };

	private final static List<String> jeeServerList = Arrays.asList( serversWithTomcatJars );

	private final static String IS_OS_WRAPPER = "isOsWrapper";
	private final static String IS_SCRIPT = "isScript";

	//
	public static final String USER_STOP = "userStop";
	public final static String DO_NOT_RESTART = "do-not-restart";
	static public final String NOT_DEPLOYED = "Service not deployed yet";

	public static boolean isJeeServer ( String checkServer ) {
		if ( jeeServerList.contains( checkServer ) ) {
			return true;
		}
		return false;
	}

	public static String[] getServertypes () {
		return serversWithTomcatJars;
	}

	public static String getBootFolder ( boolean isLegacy ) {
		if ( isLegacy ) {
			return "jarExtract/";
		} else {
			return "jarExtract/BOOT-INF/";
		}
	}

	private String context = "";
	private String defaultLogToShow = "";

	private String defaultBranch = "";

	// This is the configured via definition
	private String osProcessPriority = "0";
	// This is the actual
	private String currentProcessPriority = "0";
	private String hostFilter = "";
	private String hostName = "";
	private String autoStart = DO_NOT_RESTART;
	private String deployTimeOutMinutes = "5";

	private boolean useDockerJavaContainer = false;

	public boolean isUseDockerJavaContainer () {
		return useDockerJavaContainer;
	}

	public void setUseDockerJavaContainer ( boolean userDockerJava ) {
		this.useDockerJavaContainer = userDockerJava;
	}

	public String deployedArtifacts = "";
	private String cookieName = "";
	private String cookieDomain = "";
	private String cookiePath = "";
	private String compression = "";
	private String compressableMimeType = "text/html,text/xml,text/javascript,text/css";
	private String docUrl = "";
	private String description = "";
	private String jmxPort = "";
	private String simonMbean = "";

	private String lifecycle = "";
	private String logDirectory = "logs";
	private String logRegEx = ".*";
	private String mavenId = "";
	private String mavenSecondary = null;
	private boolean allowReleaseFileToOverride = true;
	private String mavenRepo = "";
	private String metaData = "none";
	private String platformVersion = "";
	private String port = "";
	private String libDirectory = "";
	private String propDirectory = "";
	private String scm = "";
	private String scmLocation = "";
	private String scmBuildLocation = "";

	public String getScmBuildLocation () {
		return scmBuildLocation;
	}

	public void setScmBuildLocation ( String scmBuildSubDir ) {
		this.scmBuildLocation = scmBuildSubDir;
	}

	private String scmVersion = "";
	private String serverType = "";
	private String serviceName = "";
	private String servletThreadCount = "50";
	private String servletMaxConnections = "50";
	// ajp nio connector collides with large headers -1 disables timeouts seems
	// to address
	private String servletTimeoutMs = "30000";
	private String servletAccept = "0";
	private String url = "";
	private String user = null;
	private String disk = "$workingFolder";
	private ClusterPartition partitionType = ClusterPartition.ENTERPRISE;
	private boolean multiplePortsOnHost = false;

	public String getDefaultLogToShow () {
		if ( defaultLogToShow.length() != 0 ) {
			return defaultLogToShow;
		}
		if ( isSpringBoot() ) {
			return "consoleLogs.txt";
		}
		return "catalina.out";
	}

	public void setDefaultLogToShow ( String defaultLogToShow ) {
		this.defaultLogToShow = defaultLogToShow;
	}

	public boolean isUpToDate () {

		if ( isSpringBoot() ) {
			return true;
		}

		if ( getServerType().equals( "tomcat8-5.x" ) ) {
			return true;
		}
		if ( getServerType().equals( "tomcat9.x" ) ) {
			return true;
		}
		return false;
	}

	public boolean isGenerateWebMappings () {
		return metaData.indexOf( "generateWorkerProperties" ) != -1;
	}

	public boolean isPerformJmxCollection () {
		return !isSkipJmxCollection();
	}

	public boolean isJavaCollectionEnabled () {
		return !isSkipJmxCollection();
	}

	public boolean isSkipJmxCollection () {

		if ( isScript() ) {
			return true;
		}

		if ( getJmxPort().equals( "-1" ) ) {
			return true;
		}

		return metaData.indexOf( "skipJmxCollection" ) != -1;
	}

	public boolean isSecure () {
		return metaData.indexOf( "secure" ) != -1;
	}

	public boolean isNio () {
		return metaData.indexOf( "-nio" ) != -1;
	}

	public String getOsProcessPriority () {
		return osProcessPriority;
	}

	public void setOsProcessPriority ( String osProcessPriority ) {
		this.osProcessPriority = osProcessPriority;
	}

	public String getCurrentProcessPriority () {
		return currentProcessPriority;
	}

	public void setCurrentProcessPriority ( String currentProcessPriority ) {
		this.currentProcessPriority = currentProcessPriority;
	}

	public boolean isAutoStart () {
		if ( autoStart.equals( DO_NOT_RESTART ) ) {
			return false;
		}
		return true;
	}

	public String getDeployedArtifacts () {
		return deployedArtifacts;
	}

	public boolean isWrapper () {
		if ( getServerType().equals( ServiceInstance.WRAPPER ) ) {
			return true;
		}
		return false;
	}

	public boolean isSpringBoot () {
		if ( getServerType().equals( ServiceInstance.SPRING_BOOT ) ) {
			return true;
		}
		return false;
	}

	public boolean isDockerContainer () {
		if ( getServerType().equals( ServiceInstance.DOCKER ) ) {
			return true;
		}
		return false;
	}

	public String[] getDiskUsageMatcher () {

		String matchString = replaceVariablesAndTrim( getDisk() );

		return matchString.split( " " );
	}

	public String getDiskUsagePath () {

		String duPathForService = replaceVariablesAndTrim( getDisk() );
		logger.debug("{} getDisk {}, {}", getServiceName(), getDisk(), duPathForService ) ;
		if ( isDockerContainer() || ! duPathForService.startsWith( "/" ) )
			return "";


		return duPathForService;

	}

	public String getTomcatJmxName () {
		if ( isSpringBoot() ) {
			return "Tomcat";
		}

		return "Catalina";
	}

	public String getDeployFileName () {

		String ext = ".zip";

		if ( isTomcatPackaging() ) {
			ext = ".war";
		}

		if ( isSpringBoot() ) {
			ext = ".jar";
		}

		if ( isDockerContainer() ) {
			ext = ".tar";
		}

		return getServiceName() + ext;
	}

	public boolean isOs () {
		if ( getServerType().equals( DefinitionParser.PARSER_OS ) ) {
			return true;
		}
		return false;
	}

	private String collectHost = null;
	private String collectPort = null;

	public boolean isTomcatJarsPresent () {
		if ( Arrays.asList( getServertypes() ).contains( getServerType() ) ) {
			return true;
		}
		return false;
	}

	public boolean isTomcatPackaging () {

		if ( isSpringBoot() ) {
			return false;
		}
		if ( Arrays.asList( getServertypes() ).contains( getServerType() ) ) {
			return true;
		}
		return false;
	}

	public String getServerClass () {
		if ( isTomcatJarsPresent() || isSpringBoot() || getServiceName().startsWith( "IPPW" ) ) {
			return "java";
		}
		return "other";
	}


	public boolean isScript () {
		if ( getMetaData().contains( IS_SCRIPT ) ) {
			return true;
		}
		return false;
	}

	public boolean isApacheWebIntegration () {
		if ( getMetaData().contains( "webServerIntegration" ) ) {
			return true;
		}
		return false;
	}

	public boolean isKillWarnings () {
		if ( getMetaData().contains( "killWarnings" ) ) {
			return true;
		}
		return false;
	}

	public boolean isDataStore () {
		if ( getMetaData().contains( "isDataStore" ) ) {
			return true;
		}
		return false;
	}

	public boolean isJms () {
		if ( getMetaData().contains( "isJms" ) ) {
			return true;
		}
		return false;
	}

	public boolean isOsWrapper () {
		if ( getMetaData().contains( IS_OS_WRAPPER ) ) {
			return true;
		}
		return false;
	}

	public void setDeployedArtifacts ( String deployedArtifacts ) {
		this.deployedArtifacts = deployedArtifacts;
	}

	public String getDeployTimeOutMinutes () {
		return deployTimeOutMinutes;
	}

	public int getDeployTimeOutSeconds () {

		int timeAllowedInSeconds = 120;

		try {
			timeAllowedInSeconds = Integer.parseInt( getDeployTimeOutMinutes() ) * 60;
		} catch (NumberFormatException e) {
			logger.error( "Failed to parse deployment timeout for " + toString() );
		}

		return timeAllowedInSeconds;
	}

	public void setDeployTimeOutMinutes ( String deployTimeOutMinutes ) {
		this.deployTimeOutMinutes = deployTimeOutMinutes;
	}

	public String getCookieName () {
		return cookieName;
	}

	public void setCookieName ( String cookieName ) {
		this.cookieName = cookieName;
	}

	public String getCookieDomain () {
		return cookieDomain;
	}

	public void setCookieDomain ( String cookieDomain ) {
		this.cookieDomain = cookieDomain;
	}

	public String getCookiePath () {
		return cookiePath;
	}

	public void setCookiePath ( String cookiePath ) {
		this.cookiePath = cookiePath;
	}

	public String getCompression () {
		return compression;
	}

	public void setCompression ( String compression ) {
		this.compression = compression;
	}

	public String getCompressableMimeType () {
		return compressableMimeType;
	}

	public void setCompressableMimeType ( String compressableMimeType ) {
		this.compressableMimeType = compressableMimeType;
	}

	public String getDocUrl () {
		if ( docUrl.isEmpty() && !getScmLocation().isEmpty() ) {
			String target = getScmLocation() ;
			if ( target.endsWith( ".git" ) ) {
				target = target.substring( 0, target.length() - 4  ) ;
			}
			if ( !getScmBuildLocation().isEmpty() && getScmLocation().contains( "github.com" )) {
				target += "/tree/master" + getScmBuildLocation() ;
			}
			return target ;
		}
		return docUrl;
	}

	public void setDocUrl ( String docUrl ) {
		this.docUrl = docUrl;
	}

	public String getDescription () {
		if ( description.length() == 0 ) {
			return getServiceName();
		}
		return description;
	}

	public void setDescription ( String description ) {
		this.description = description;
	}

	public String getMavenSecondary () {
		return mavenSecondary;
	}

	public void setMavenSecondary ( String mavenSecondary ) {
		this.mavenSecondary = mavenSecondary;
	}

	public String getServletMaxConnections () {
		return servletMaxConnections;
	}

	public void setServletMaxConnections ( String servletMaxConnections ) {
		this.servletMaxConnections = servletMaxConnections;
	}

	public String getServletTimeoutMs () {
		return servletTimeoutMs;
	}

	public void setServletTimeoutMs ( String servletTimeoutMs ) {
		this.servletTimeoutMs = servletTimeoutMs;
	}

	public String getServletAccept () {
		return servletAccept;
	}

	public void setServletAccept ( String servletAccept ) {
		this.servletAccept = servletAccept;
	}

	public ClusterPartition getPartitionType () {
		return partitionType;
	}

	public void setPartitionType ( ClusterPartition partitionType ) {
		this.partitionType = partitionType;
	}

	protected String replaceVariablesAndTrim ( String input ) {

		if ( input == null ) {
			return null;
		}
		// String result = input.trim().replaceAll( "\\$host",
		// Application.getHOST_NAME() );
		String result = input.trim().replaceAll( "\\$host", getHostName() );
		result = result.trim().replaceAll( Matcher.quoteReplacement("$life"), getPlatformLifecycle() );
		result = result.trim().replaceAll( Matcher.quoteReplacement("$port"), getPort() );
		result = result.trim().replaceAll( Matcher.quoteReplacement("$ajpPort"), getAjpPort() );
		result = result.trim().replaceAll( Matcher.quoteReplacement("$jmxPort"), getJmxPort() );
		result = result.trim().replaceAll( Matcher.quoteReplacement("$instance"), getServiceName_Port() );
		String working = Application.getPROCESSING() + "/" + getServiceName_Port();
		if ( Application.isRunningOnDesktop() ) {
			working = "/home/ssadmin/processing/" + getServiceName_Port();
		}
		result = result.trim().replaceAll( Matcher.quoteReplacement("$workingFolder"), working );
		result = result.trim().replaceAll( Matcher.quoteReplacement("$context"), getContext() );
		String jvmName = "notFound";
		String envJvm = System.getenv( "JAVA_HOME" );
		if ( envJvm != null ) {
			File j = new File( envJvm );
			jvmName = j.getName();
		}

		result = result.trim().replaceAll( Matcher.quoteReplacement("$jvm"), jvmName );

		result = result.trim().replaceAll( Matcher.quoteReplacement("$processing"), Application.getPROCESSING() );
		result = result.trim().replaceAll( Matcher.quoteReplacement("$staging"), Application.stagingPathForParsingOnly() );

		return result;
	}
	

	public String getContext () {
		return context;
	}

	public String getDefaultBranch () {

		// Temp fix because many places have wrong entry for svn
		if ( defaultBranch.equalsIgnoreCase( "head" ) && getScm().equalsIgnoreCase( "svn" ) ) {
			return "trunk";
		}
		return defaultBranch;
	}

	public String getHostFilter () {
		return hostFilter;
	}

	public String getHostName () {
		return hostName;
	}

	public String getJmxPort () {
		return jmxPort;
	}

	public String getLifecycle () {
		return lifecycle;
	}

	public String getCluster () {
		if ( lifecycle.contains( "-" ) ) {
			return lifecycle.substring( lifecycle.indexOf( "-" ) + 1 );
		} else {
			return "all";
		}
	}

	public String getPlatformLifecycle () {
		if ( lifecycle.contains( "-" ) ) {
			return lifecycle.substring( 0, lifecycle.indexOf( "-" ) );
		}
		return lifecycle;
	}

	// used in admin.jsp to launch editor
	public String getEditorClusterPath () {
		String suffix = "";
		try {
			suffix = lifecycle.replaceAll( "-", "." );
		} catch (Exception e) {

		}
		return "clusterDefinitions." + suffix;
	}

	public String getEditorProcessPath () {

		String editPath = "jvms." + getServiceName();
		if ( isWrapper() || isOs() ) {
			editPath = "osProcesses." + getServiceName();
		}
		return editPath;
	}

	public String getLogDirectory () {

		return logDirectory;
	}

	
	public String getLogRegEx () {
		return logRegEx;
	}

	public String getMavenId () {
		return mavenId;
	}

	public String getMavenVersion () {

		String version = "none";
		try {
			String[] mavenArray = mavenId.split( ":" );
			version = mavenArray[2];
		} catch (Exception e) {
			logger.debug( "Failed to parse mavenId: {}", mavenId, e );
		}
		return version;
	}

	public String getMavenRepo () {
		return mavenRepo;
	}

	public String getMetaData () {
		return metaData;
	}

	public String getPlatformVersion () {
		return platformVersion;
	}

	public String getPort () {
		return port;
	}

	public String getDebugPort () {
		if ( getPort().length() >= 3 ) {
			return getPort().substring( 0, 3 ) + "9";
		}
		return port;
	}
	public String getAjpPort () {
		if ( getPort().length() >= 3 ) {
			return getPort().substring( 0, 3 ) + "2";
		}
		return port;
	}

	public String getLibDirectory () {
		if ( libDirectory.length() == 0 ) {
			if ( isSpringBoot() ) {
				return getBootFolder( false ) + "lib";
			}
		}
		return libDirectory;
	}

	public String getPropDirectory () {
		if ( propDirectory.length() == 0 ) {
			if ( isSpringBoot() ) {
				return getBootFolder( false ) + "classes";
			}
			return "WEB-INF/classes";
		}
		return propDirectory;
	}

	public String paddedId () {
		return StringUtils.rightPad( getServiceName(), 25 ) + " " + StringUtils.leftPad(getPort(),6);
	}
	
	public String getServiceName_Port () {
		return serviceName + "_" + getPort();
	}

	public String getStoppedFileName () {
		return serviceName + "_" + getPort() + ".stopped";
	}

	/**
	 * Hooks for UI
	 *
	 * @return
	 */
	public String getServerQualifedType () {

		if ( metaData.contains( IS_SCRIPT ) ) {
			return ServiceInstance.SCRIPT;
		}

		return getServerType();
	}

	public String getServerUiIconType () {

		if ( isScript() ) {
			return "package";
		}
		// if ( getServiceName().equals( "docker" ) ) {
		// return "docker";
		// }
		if ( isGenerateWebMappings() ) {
			return "webServer";
		}
		if ( isJms() ) {
			return "messaging";
		}
		if ( isDataStore() ) {
			return "database";
		}

		if ( isWrapper() ) {
			return "runtime";
		}

		if ( isOs() ) {
			return "monitor";
		}

		if ( getServiceName().equals( Application.AGENT_ID ) ) {
			return "csapAgent";
		}

		return getServerType();
	}

	public String getScm () {
		return scm;
	}

	public boolean isGit () {
		return getScm().equals( SourceControlManager.ScmProvider.git.key );
	}

	public String getScmLocation () {
		return scmLocation;
	}

	public String getScmVersion () {

		return scmVersion;
	}

	public String getServerType () {

		// corner case hook for migrations
		if ( getServiceName().equals( "CsAgent" ) || getServiceName().equals( "admin" ) ) {
			return ServiceInstance.SPRING_BOOT;
		}
		return serverType;
	}

	public String getServiceName () {
		return serviceName;
	}

	public String getServletThreadCount () {
		return servletThreadCount;
	}

	public String getUrl () {
		return url;
	}

	public String getUser () {
		return user;
	}

	public void setContext ( String context ) {

		// if ( getServiceName().equals( "Cssp3ReferenceMq" ) ) {
		// Thread.dumpStack();
		// }
		this.context = replaceVariablesAndTrim( context );
	}

	public void setDefaultBranch ( String defaultBranch ) {
		this.defaultBranch = replaceVariablesAndTrim( defaultBranch );
	}

	// public void addSocketCount(int socketCount) {
	// this.socketCount += socketCount;
	// }
	public void setHostFilter ( String hostFilter ) {
		this.hostFilter = replaceVariablesAndTrim( hostFilter );
	}

	public void setHostName ( String hostName ) {

		this.hostName = replaceVariablesAndTrim( hostName );

	}

	public void setJmxPort ( String jmxPort ) {
		this.jmxPort = replaceVariablesAndTrim( jmxPort );
	}

	public void setLifecycle ( String lifecycle ) {
		this.lifecycle = replaceVariablesAndTrim( lifecycle );
	}

	public void setLogDirectory ( String logDirectory ) {

		String hname[] = hostName.split( "-" );

		String suffix = "checkHostSuffix";
		if ( hname.length == 2 ) {
			suffix = hname[1];
		}

		this.logDirectory = replaceVariablesAndTrim( logDirectory ).replaceAll( "\\$hsuffix", suffix );
	}

	public void setLogRegEx ( String logRegEx ) {
		this.logRegEx = logRegEx;
	}

	public void setMavenId ( String mavenId ) {
		this.mavenId = replaceVariablesAndTrim( mavenId );
	}

	public void setMavenRepo ( String mavenRepo ) {
		this.mavenRepo = replaceVariablesAndTrim( mavenRepo );
	}

	public void setMetaData ( String metaData ) {
		this.metaData = metaData;
	}

	public void setPlatformVersion ( String platformVersion ) {
		this.platformVersion = replaceVariablesAndTrim( platformVersion );
	}

	public void setPort ( String port ) {
		this.port = replaceVariablesAndTrim( port );
	}

	public void setPropDirectory ( String propDirectory ) {
		this.propDirectory = replaceVariablesAndTrim( propDirectory );
	}

	public void setScm ( String scm ) {
		this.scm = replaceVariablesAndTrim( scm );
	}

	public void setScmLocation ( String scmLocation ) {
		this.scmLocation = replaceVariablesAndTrim( scmLocation );
	}

	public void setScmVersion ( String scmVersion ) {
		this.scmVersion = replaceVariablesAndTrim( scmVersion );
	}

	public void setScmVersionNotDeployed () {
		this.scmVersion = replaceVariablesAndTrim( NOT_DEPLOYED );
	}

	public boolean isScmDeployed () {
		return this.scmVersion.length() > 0 && !this.scmVersion.equals( NOT_DEPLOYED );
	}

	public void setServerType ( String serverType ) {
		this.serverType = replaceVariablesAndTrim( serverType );
	}

	public void setServiceName ( String serviceName ) {
		this.serviceName = replaceVariablesAndTrim( serviceName );
	}

	public void setServletThreadCount ( String servletThreadCount ) {
		this.servletThreadCount = servletThreadCount;
	}

	public void setUrl ( String url ) {
		// this.url = doTrim(url);
		// this.url = url.trim().replaceAll( "\\$host", hostName );
		this.url = replaceVariablesAndTrim( url );
	}

	public void setUser ( String user ) {
		this.user = user;
	}

	/**
	 *
	 * Factorys are a hook to setup a unique context by appending hostName to
	 * the regular service context.
	 *
	 * skipFactoryConfig in metaData is a way for jvm to opt out
	 *
	 * @return
	 */
	public boolean isConfigureAsSingleVmPartition () {
		if ( (partitionType.equals( ClusterPartition.SHARED_NOTHING ))
				&& !getServiceName().equalsIgnoreCase(
					Application.AGENT_ID )
				&& !getMetaData().contains( "skipFactoryConfig" ) ) {
			return true;
		}
		return false;
	}

	public boolean isConfigureAsMultiVmPartition () {
		if ( (partitionType.equals( ClusterPartition.MULTI_SHARED_NOTHING ))
				&& !getServiceName().equalsIgnoreCase(
					Application.AGENT_ID )
				&& !getMetaData().contains( "skipFactoryConfig" ) ) {
			return true;
		}
		return false;
	}

	public boolean isConfigureAsEnterprise () {
		if ( partitionType.equals( ClusterPartition.ENTERPRISE ) ) {
			return true;
		}
		return false;
	}

	public String toSummaryString () {
		return serviceName + "@" + hostName + ":" + port;
	}

	/**
	 * We use a treeMap to sort, so append the service name and port to ensure
	 * uniqueness only if autostart has been specified
	 *
	 * @return
	 */
	public String getAutoStart () {

		if ( autoStart.length() != 0 ) {
			return autoStart + getServiceName() + getPort();
		}

		return autoStart;
	}

	public String getRawAutoStart () {
		if ( autoStart.length() == 0 ) {
			return "99";
		}
		return autoStart;
	}

	public void setAutoStart ( String autoStart ) {
		this.autoStart = autoStart;
	}

	public String getDisk () {
		return disk;
	}

	public void setDisk ( String disk ) {
		// single space separation
		this.disk = replaceVariablesAndTrim( disk.replaceAll("( )+", " ") );
	}

	/**
	 * @return the multiplePortsOnHost
	 */
	private boolean isMultiplePortsOnHost () {
		return multiplePortsOnHost;
	}

	public String getPerformanceId () {
		if ( isMultiplePortsOnHost() ) {
			return getServiceName_Port();
		}
		return getServiceName();
	}

	/**
	 * @param multiplePortsOnHost
	 *            the multiplePortsOnHost to set
	 */
	public void setMultiplePortsOnHost ( boolean multiplePortsOnHost ) {
		this.multiplePortsOnHost = multiplePortsOnHost;
	}

	/**
	 * @param libDirectory
	 *            the libDirectory to set
	 */
	public void setLibDirectory ( String libDirectory ) {
		this.libDirectory = libDirectory;
	}

	public String getSimonMbean () {
		return simonMbean;
	}

	public void setSimonMbean ( String simonMbean ) {
		this.simonMbean = simonMbean;
	}

}
