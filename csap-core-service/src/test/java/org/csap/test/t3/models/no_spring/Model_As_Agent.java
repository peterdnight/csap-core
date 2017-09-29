/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.test.t3.models.no_spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.csap.agent.CSAP;
import org.csap.agent.model.Application;
import org.csap.agent.model.ServiceAlertsEnum;
import org.csap.agent.model.ServiceAttributes;
import org.csap.agent.model.ServiceBaseParser;
import org.csap.agent.model.ServiceInstance;
import org.csap.test.InitializeLogging;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author someDeveloper
 */

public class Model_As_Agent {

	final static private Logger logger = LoggerFactory.getLogger( Model_As_Agent.class );

	final static public File csapApplicationDefinition = new File(
		Model_As_Agent.class.getResource( "application-agent.json" ).getPath() );

	@BeforeClass
	public static void setUpBeforeClass ()
			throws Exception {
		InitializeLogging.printTestHeader( logger.getName() );

		Application.setJvmInManagerMode( false );
		csapApp = new Application();
		csapApp.setAutoReload( false );

		assertThat( csapApp.loadDefinitionForJunits( false, csapApplicationDefinition ) )
			.as( "No Errors or warnings" )
			.isTrue();

		logger.info( InitializeLogging.SETUP_HEAD + "Using: " + csapApplicationDefinition.getAbsolutePath() );
	}

	@AfterClass
	public static void tearDownAfterClass ()
			throws Exception {
	}

	// @Inject
	static Application csapApp;

	@Before
	public void setUp ()
			throws Exception {

	}

	@After
	public void tearDown ()
			throws Exception {
	}

	@Test
	public void verify_script () {

		logger.info( InitializeLogging.TC_HEAD );

		ServiceInstance wmwareService = csapApp.getSvcToConfigMap().get( "vmtoolsd" ).get( 0 );

		assertThat( wmwareService.isOs() )
			.as( "is script" )
			.isTrue();

		assertThat( wmwareService.getScm() )
			.as( "scm type" )
			.isEqualTo( "" );
	}

	@Test
	public void verify_lifecycle_settings () {

		logger.info( InitializeLogging.TC_HEAD );

		assertThat( csapApp.lifeCycleSettings().getAdminToAgentTimeoutSeconds() )
			.as( "agent time out" )
			.isEqualTo( 6 );

		assertThat( csapApp.lifeCycleSettings().isEventPublishEnabled() )
			.as( "isEventPublishEnabled" )
			.isFalse();

		logger.info( "infra settings: {}", csapApp.lifeCycleSettings().getInfraTests() );
		assertThat( csapApp.lifeCycleSettings().getInfraTests().getCpuLoopsMillions() )
			.as( "cpu loops" )
			.isEqualTo( 1 );

	}

	@Test
	public void verify_httpd_attributes () {

		logger.info( InitializeLogging.TC_HEAD );

		ServiceInstance httpdInstance = csapApp.getSvcToConfigMap().get( "httpd" ).get( 0 );

		assertThat( httpdInstance.isWrapper() )
			.as( "is wrapper" )
			.isTrue();

		assertThat( httpdInstance.getProcessFilter() )
			.as( "process" )
			.isEqualTo( "httpdFilter" );

		assertThat( httpdInstance.getMonitors().get( ServiceAlertsEnum.diskSpace.maxId() ).asText() )
			.as( "disk limit" )
			.isEqualTo( "1000" );

		assertThat( httpdInstance.getPerformanceConfiguration().get( "config" ).get( "httpCollectionUrl" ).asText() )
			.as( "colleciton url" )
			.isEqualTo( "http://localhost:8080/server-status?auto" );

		assertThat( httpdInstance.getLifeEnvironmentVariables().get( "test" ).asText() )
			.as( "environmentVariabls" )
			.isEqualTo( "someDevDefault" );

		assertThat( httpdInstance.getJobs().size() )
			.as( "number of jobs" )
			.isEqualTo( 3 );

		assertThat( httpdInstance.getJobs().get( 0 ).getScript() )
			.as( "script path" )
			.endsWith( "/processing/httpd_8080/jobs/eventsWarmup.sh" );

		assertThat( httpdInstance.getJobs().get( 1 ).getHour() )
			.as( "invokation hour" )
			.isEqualTo( "01" );

		assertThat( httpdInstance.getDefaultLogToShow() )
			.as( "log to show" )
			.isEqualTo( "access.log" );

		assertThat( httpdInstance.getPropDirectory() )
			.as( "propery folder" )
			.isEqualTo( "/home/ssadmin/staging/httpdConf" );
		logger.info( "httpd service meters:\n{}", httpdInstance.getServiceMeters() );
		assertThat( httpdInstance.getServiceMeters().toString() )
			.as( "service meters" )
			.contains( "collectionId: BusyWorkers, type: attribute http" );
	}

	@Test
	public void verify_vmware_attributes () {

		logger.info( InitializeLogging.TC_HEAD );

		ServiceInstance wmwareService = csapApp.getSvcToConfigMap().get( "vmtoolsd" ).get( 0 );

		assertThat( wmwareService.isOs() )
			.as( "is script" )
			.isTrue();

		assertThat( wmwareService.getScm() )
			.as( "scm type" )
			.isEqualTo( "" );
	}

	@Test
	public void verify_jdk_attributes () {

		logger.info( InitializeLogging.TC_HEAD );

		ServiceInstance jdkService = csapApp.getSvcToConfigMap().get( "jdk" ).get( 0 );

		assertThat( jdkService.isScript() )
			.as( "is script" )
			.isTrue();

		assertThat( jdkService.getScmLocation() )
			.as( "source location" )
			.contains( "JavaDevKitPackage8" );

		assertThat( jdkService.getProcessFilter() )
			.as( "processFilter" )
			.contains( ServiceBaseParser.SCRIPTS_NEVER_MATCH );

		assertThat( jdkService.getScm() )
			.as( "scm type" )
			.isEqualTo( "svn" );
		

		assertThat( jdkService.getDiskUsageMatcher().length )
			.as( "disk matching patterns" )
			.isEqualTo( 2 );
	}

	@Test
	public void verify_cassandra_attributes () {

		logger.info( InitializeLogging.TC_HEAD );

		ServiceInstance cassandraService = csapApp.getSvcToConfigMap().get( "cassandra" ).get( 0 );

		assertThat( cassandraService.isWrapper() )
			.as( "is wrapper" )
			.isTrue();

		assertThat( cassandraService.getScmLocation() )
			.as( "source location" )
			.isEqualTo( "https://bitbucket.yourcompany.com/bitbucket/scm/smas/csap-cassandra.git" );

		assertThat( cassandraService.getScm() )
			.as( "scm type" )
			.isEqualTo( "git" );

		assertThat( cassandraService.getJmxPort() )
			.as( "jmxPort" )
			.isEqualTo( "7199" );
	}

	@Test
	public void verify_reference_attributes () {

		logger.info( InitializeLogging.TC_HEAD );

		ServiceInstance refService = csapApp.getSvcToConfigMap().get( "Cssp3ReferenceMq" ).get( 0 );

		assertThat( refService.isSpringBoot() )
			.as( "server override" )
			.isTrue();

		assertThat( refService.isTomcatPackaging() )
			.as( "server override" )
			.isFalse();

		assertThat( refService.isUseDockerJavaContainer() )
			.as( "useDockerJavaContainer" )
			.isTrue();
		

		assertThat( refService.getDockerImageName() )
			.as( "docker image name" )
			.isEqualTo( "containers.yourcompany.com/cssp-legacy:latest" );

		assertThat( refService.getJobs().size() )
			.as( "number of jobs" )
			.isEqualTo( 1 );

		assertThat( refService.getLogsToRotate().get( 0 ).getPath().contains( "catalina.out" ) )
			.as( "log file to rotate" )
			.isTrue();

		assertThat( refService.getProcessFilter() )
			.as( "process filter" )
			.isEqualTo( ".*java.*csapProcessId=Cssp3ReferenceMq_8241.*" );

		assertThat( refService.getJmxPort() )
			.as( "jmx port" )
			.isEqualTo( "8246" );

		assertThat( refService.getAttribute( ServiceAttributes.parameters ) )
			.as( "params" )
			.isEqualTo( "-Xms16M -Xmx256M paramsOveride -Dsimple.life=$life" );
		

		assertThat( refService.replaceParserVariables( refService.getParameters()) )
			.as( "params" )
			.isEqualTo( "-Xms16M -Xmx256M paramsOveride -Dsimple.life=dev" );

		assertThat( refService.getScm() )
			.as( "scm type" )
			.isEqualTo( "svn" );

		assertThat( refService.getAttributeAsObject( ServiceAttributes.webServerTomcat ) )
			.as( "webServerTomcat" )
			.isNotNull();

		assertThat( refService.getAttributeAsObject( ServiceAttributes.webServerTomcat ).get( "loadBalance" ).toString() )
			.as( "webServerTomcat load balance" )
			.contains( "method=Next", "sticky_session=1" );

		assertThat( refService.getAttributeAsJson( ServiceAttributes.webServerReWrite ) )
			.as( "webServerReWrite" )
			.isNotNull();

		assertThat( refService.getAttributeAsJson( ServiceAttributes.webServerReWrite ).toString() )
			.as( "webServerReWrite" )
			.contains( "RewriteRule ^/test1/(.*)$  /Cssp3ReferenceMq/$1 [PT]" );

		assertThat( refService.getDefaultBranch() )
			.as( "scm version" )
			.isEqualTo( "branchOver" );

		assertThat( refService.getMetaData() )
			.as( "metadata" )
			.isEqualTo( "exportWeb, -nio" );

		assertThat( refService.getRawAutoStart() )
			.as( "autostart" )
			.isEqualTo( "989" );

		assertThat( refService.getContext() )
			.as( "servlet context" )
			.isEqualTo( "TestContext" );

		assertThat( refService.getServletThreadCount() )
			.as( "servletThreadCount" )
			.isEqualTo( "999" );

		assertThat( refService.getDescription() )
			.as( "description" )
			.isEqualTo( "Provides cssp-3.x reference implementation for engineering, along with core platform regression tests." );

		assertThat( refService.getDocUrl() )
			.as( "docs" )
			.isEqualTo( "https://github.com/csap-platform/csap-core/wiki#updateRefCode+Samples" );

		assertThat( refService.getCompression() )
			.as( "compression" )
			.isEqualTo( "yes" );

		assertThat( refService.getCookieName() )
			.as( "compression" )
			.isEqualTo( "csapTestCookieName" );

		assertThat( refService.getDeployTimeOutMinutes() )
			.as( "deploy timeout" )
			.isEqualTo( "55" );
	}

		
	@Test
	public void verify_agent_attributes () {

		logger.info( "{}, \n\n {}", InitializeLogging.TC_HEAD );

		ServiceInstance agentInstance = csapApp.getSvcToConfigMap().get( "CsAgent" ).get( 0 );

		assertThat( agentInstance.isSpringBoot() )
			.as( "server override" )
			.isTrue();

		assertThat( agentInstance.getProcessFilter() )
			.as( "process filter" )
			.isEqualTo( ".*java.*csapProcessId=CsAgent_8011.*" );

		assertThat( agentInstance.getOsProcessPriority() )
			.as( "process override" )
			.isEqualTo( "-99" );

		assertThat( agentInstance.getAttribute( ServiceAttributes.parameters ) )
			.as( "process override" )
			.contains( "-Doverride=true" );

		assertThat( agentInstance.getMavenId() )
			.as( "maven override" )
			.contains( "9.9.9" );

		assertThat( agentInstance.getScm() )
			.as( "scm type" )
			.isEqualTo( "svn" );

		assertThat( agentInstance.getPerformanceConfiguration().isMissingNode() )
			.as( "missing performance data" )
			.isFalse();

		/**
		 * Health reports
		 */
		assertThat( agentInstance.isHealthReportConfigured() )
			.as( "health report" )
			.isTrue();

		assertThat( agentInstance.getHealthReportMbean() )
			.as( "health report mbean" )
			.isEqualTo( "org.csap:application=CsapPerformance,name=PerformanceMonitor" );

		assertThat( agentInstance.getHealthReportAttribute() )
			.as( "health report attribute" )
			.isEqualTo( "HealthReport" );

		assertThat( agentInstance.isHealthStatusConfigured() )
			.as( "health status" )
			.isTrue();

		assertThat( agentInstance.getHealthStatusMbean() )
			.as( "health status mbean" )
			.isEqualTo( "org.csap:application=CsapPerformance,name=PerformanceMonitor" );

		assertThat( agentInstance.getHealthStatusAttribute() )
			.as( "health status attribute" )
			.isEqualTo( "HealthStatus" );

		/**
		 * 
		 */

		logger.info( "Agent service meters:\n{}", agentInstance.getServiceMeters() );

		assertThat( agentInstance.getServiceMeters().toString() )
			.as( "service meters" )
			.contains( "collectionId: jmxHeartbeatMs, type: mbean" )
			.contains( "collector.jmx ** Collect failures ignored" );

		assertThat( agentInstance.getServiceMeters() )
			.as( "service meters" )
			.hasSize( 19 );

		assertThat( agentInstance.getServiceMeterTitles() )
			.as( "service meter titles" )
			.hasSize( 19 );

		assertThat( agentInstance.getServiceMeterTitles().toString() )
			.as( "service title string" )
			.contains( "TotalVmCpu", "Host Cpu" );

		// file support

		assertThat( agentInstance.getFiles() )
			.as( "property files" )
			.isNotNull();

		assertThat( agentInstance.getFiles().at( "/0/name" ).asText() )
			.as( "property file name" )
			.isEqualTo( "simpleFile.properties" );

		/**
		 * Job support
		 */
		assertThat( agentInstance.getJobs().size() )
			.as( "number of jobs" )
			.isEqualTo( 4 );

		assertThat( agentInstance.getJobs().get( 0 ).getScript() )
			.as( "script path" )
			.isEqualTo( csapApp.stagingFolderAsString() + "/bin/checkLimits.sh" );

		assertThat( agentInstance.getJobs().get( 0 ).getHour() )
			.as( "invokation hour" )
			.isEqualTo( "01" );

		assertThat( agentInstance.getJobs().get( 1 ).isDiskCleanJob() )
			.as( "is disk clean job" )
			.isTrue();

		assertThat( agentInstance.getJobs().get( 1 ).getMaxDepth() )
			.as( "maxDepth to search" )
			.isEqualTo( 5 );

		assertThat( agentInstance.getJobs().get( 1 ).isPruneEmptyFolders() )
			.as( "prune empty folders" )
			.isTrue();

		assertThat( agentInstance.getJobs().get( 1 ).getPath() )
			.as( "disk path" )
			.isEqualTo( csapApp.stagingFolderAsString() + "/scripts" );

		assertThat( agentInstance.getLogsToRotate().size() )
			.as( "number of logs" )
			.isEqualTo( 3 );

		assertThat( agentInstance.getLogsToRotate().get( 0 ).getPath() )
			.as( "log file" )
			.endsWith( "logs/consoleLogs.txt" );

		assertThat( agentInstance.getLogsToRotate().get( 0 ).isActive() )
			.as( "log settings active" )
			.isTrue();

		assertThat( agentInstance.getLogsToRotate().get( 0 ).getLifecycles() )
			.as( "log file" )
			.isEqualTo( "all" );

		assertThat( agentInstance.getLogsToRotate().get( 1 ).isActive() )
			.as( "log settings active" )
			.isTrue();

		assertThat( agentInstance.getLogsToRotate().get( 2 ).isActive() )
			.as( "log settings active" )
			.isFalse();

	}

	@Test
	public void verify_boot_attributes () {

		logger.info( InitializeLogging.TC_HEAD );

		ServiceInstance bootService = csapApp.getSvcToConfigMap().get( "SpringBootRest" ).get( 0 );

		assertThat( bootService.isSpringBoot() )
			.as( "server override" )
			.isTrue();

		assertThat( bootService.getContext() )
			.as( "context" )
			.isEqualTo( "SpringBootRest" );

		assertThat( bootService.getUrl() )
			.as( "boot url" )
			.isEqualTo( "http://localhost." + CSAP.DEFAULT_DOMAIN + ":8291/admin/info" );

		assertThat( bootService.getProcessFilter() )
			.as( "process filter" )
			.isEqualTo( ".*java.*csapProcessId=SpringBootRest_8291.*" );

		assertThat( bootService.getOsProcessPriority() )
			.as( "process override" )
			.isEqualTo( "0" );

		assertThat( bootService.getAttribute( ServiceAttributes.parameters ) )
			.as( "parameters" )
			.contains( "-DcsapJava8  -Xms128M -Xmx133M -XX:MaxMetaspaceSize=96M" );

		assertThat( bootService.getMavenId() )
			.as( "maven" )
			.contains( "org.demo:SpringBootRest:0.0.1-SNAPSHOT:jar" );

		assertThat( bootService.getScm() )
			.as( "scm type" )
			.isEqualTo( "svn" );
	}

	@Test
	public void verify_FactoryService_attributes () {

		logger.info( InitializeLogging.TC_HEAD );

		ServiceInstance factoryService = csapApp.getSvcToConfigMap().get( "FactoryService" ).get( 0 );

		assertThat( factoryService.isSpringBoot() )
			.as( "server override" )
			.isTrue();

		assertThat( factoryService.getContext() )
			.as( "context" )
			.isEqualTo( "TestContext-dev001" );

		assertThat( factoryService.getProcessFilter() )
			.as( "process filter" )
			.isEqualTo( ".*java.*csapProcessId=FactoryService_8441.*" );

		assertThat( factoryService.getHostName() )
			.as( "hostname" )
			.isEqualTo( "factory-dev001" );

		assertThat( factoryService.getUrl() )
			.as( "url" )
			.isEqualTo( "http://factory-dev001." + CSAP.DEFAULT_DOMAIN + ":8441/TestContext-dev001" );

	}

	@Test
	public void verify_JmxRemoteService_attributes () {

		logger.info( InitializeLogging.TC_HEAD );
		ServiceInstance jmxRemoteService = csapApp.getSvcToConfigMap().get( "JmxRemoteService" ).get( 0 );

		assertThat( jmxRemoteService.isTomcatJarsPresent() )
			.as( "tomcat based service" )
			.isTrue();

		assertThat( jmxRemoteService.isPerformJmxCollection() )
			.as( "is jmx collection" )
			.isTrue();

		assertThat( jmxRemoteService.isRemoteCollection() )
			.as( "has collection host" )
			.isTrue();

		assertThat( jmxRemoteService.getServerType() )
			.as( "server type" )
			.isEqualTo( "tomcat8-5.x" );

		assertThat( jmxRemoteService.getCollectHost() )
			.as( "collection host" )
			.isEqualTo( "csap-dev99" );

		assertThat( jmxRemoteService.getCollectPort() )
			.as( "collect port" )
			.isEqualTo( "8996" );

		assertThat( jmxRemoteService.getPerformanceConfiguration().isMissingNode() )
			.as( "missing performance data" )
			.isFalse();

		assertThat( jmxRemoteService.getPerformanceConfiguration().at( "/classesLoaded/mbean" ).asText() )
			.as( "mbean setting" )
			.isEqualTo( "java.lang:type=ClassLoading" );

	}

	@Test
	public void verify_release_file () {

		logger.info( InitializeLogging.TC_HEAD );

		ServiceInstance releaseService = csapApp.getSvcToConfigMap().get( "ReleaseService" ).get( 0 );

		assertThat( releaseService.isSpringBoot() )
			.as( "spring boot server" )
			.isTrue();

		// assertThat( releaseService.getMavenId() )
		// .as( "mavenId" )
		// .isEqualTo( "org.demo:SpringBootRest:0.0.1-SNAPSHOT:jar" );

		assertThat( releaseService.getMavenId() )
			.as( "mavenId" )
			.isEqualTo( "org.demo:SpringBootRest:9.9.9-SNAPSHOT:jar" );

	}

}
