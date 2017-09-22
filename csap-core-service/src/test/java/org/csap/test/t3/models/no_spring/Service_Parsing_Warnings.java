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
import org.csap.agent.model.ServiceAttributes;
import org.csap.agent.model.ServiceBaseParser;
import org.csap.agent.model.ServiceInstance;
import org.csap.test.t1_container.Boot_Container_Test;
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
public class Service_Parsing_Warnings {

	final static private Logger logger = LoggerFactory.getLogger( Service_Parsing_Warnings.class );

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Boot_Container_Test.printTestHeader( logger.getName() );

		Application.setJvmInManagerMode( false );
		csapApp = new Application();
		csapApp.setAutoReload( false );

		File csapApplicationDefinition = new File(
				Service_Parsing_Warnings.class.getResource( "application-warnings.json" ).getPath() );

		StringBuffer parseResults = csapApp.getParser().parseConfig( false, csapApplicationDefinition );
		assertThat( parseResults.toString() )
				.as( "Found Warnings" )
				.contains( CSAP.CONFIG_PARSE_WARN );
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	//@Inject
	static Application csapApp;

	@Before
	public void setUp() throws Exception {

	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void verify_httpd_attributes() {
		ServiceInstance httpdInstance = csapApp.getSvcToConfigMap().get( "httpd" ).get( 0 );

		assertThat( httpdInstance.isWrapper() )
				.as( "is wrapper" )
				.isTrue();

		assertThat( httpdInstance.getProcessFilter() )
				.as( "process" )
				.isEqualTo( "httpdFilter" );

	}

	@Test
	public void verify_vmware_attributes() {
		ServiceInstance wmwareService = csapApp.getSvcToConfigMap().get( "vmtoolsd" ).get( 0 );

		assertThat( wmwareService.isOs() )
				.as( "is script" )
				.isTrue();

		assertThat( wmwareService.getScm() )
				.as( "scm type" )
				.isEqualTo( "" );
	}

	@Test
	public void verify_jdk_attributes() {
		ServiceInstance jdkService = csapApp.getSvcToConfigMap().get( "jdk" ).get( 0 );

		assertThat( jdkService.isScript() )
				.as( "is script" )
				.isTrue();

		assertThat( jdkService.getScmLocation() )
				.as( "source location" )
				.contains( "JavaDevKitPackage8" );

		assertThat( jdkService.getScm() )
				.as( "scm type" )
				.isEqualTo( "svn" );
	}

	@Test
	public void verify_reference_attributes() {
		ServiceInstance refService = csapApp.getSvcToConfigMap().get( "Cssp3ReferenceMq" ).get( 0 );

		assertThat( refService.isSpringBoot() )
				.as( "server override" )
				.isTrue();

		assertThat( refService.isTomcatPackaging() )
				.as( "server override" )
				.isFalse();

	}

	@Test
	public void verify_serviceWithWarning_attributes() {
		ServiceInstance refService = csapApp.getSvcToConfigMap().get( "ServiceWithWarnings" ).get( 0 );

		
		assertThat( refService.isSpringBoot() )
				.as( "server override" )
				.isTrue();

		assertThat( refService.isTomcatPackaging() )
				.as( "server override" )
				.isFalse();
		
		// WARNINGS
		
		assertThat( refService.getPerformanceConfiguration().fieldNames() )
				.as( "performance items" )
				.contains( "ProcessCpu", "jmxHeartbeatMs") ;
		
		assertThat( refService.getPerformanceConfiguration().get("Total VmCpu").get(ServiceBaseParser.ERRORS).asBoolean() ) 
				.as( "performance items space is ignored" )
				.isTrue() ;
		
		// NO Warnings

		assertThat( refService.getJmxPort() )
				.as( "jmx port" )
				.isEqualTo( "8256" );

		assertThat(refService.getParameters() )
				.as( "params" )
				.isEqualTo( "-Xms16M -Xmx256M paramsOveride" );

		assertThat( refService.getScm() )
				.as( "scm type" )
				.isEqualTo( "svn" );

		assertThat(refService.getAttributeAsObject(ServiceAttributes.webServerTomcat ) )
				.as( "webServer" )
				.isNotNull();

		assertThat(refService.getAttributeAsObject(ServiceAttributes.webServerTomcat ).get( "loadBalance" ).toString() )
				.as( "webServer load balance" )
				.contains( "method=Next", "sticky_session=1" );

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
				.isEqualTo( "ServiceWithWarnings" );

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
	public void verify_agent_attributes() {
		ServiceInstance agentInstance = csapApp.getSvcToConfigMap().get( "CsAgent" ).get( 0 );

		assertThat( agentInstance.isSpringBoot() )
				.as( "server override" )
				.isTrue();

		assertThat( agentInstance.getOsProcessPriority() )
				.as( "process override" )
				.isEqualTo( "-99" );

		assertThat(agentInstance.getAttribute(ServiceAttributes.parameters ) )
				.as( "process override" )
				.contains( "-Doverride=true" );

		assertThat( agentInstance.getMavenId() )
				.as( "maven override" )
				.contains( "9.9.9" );

		assertThat( agentInstance.getScm() )
				.as( "scm type" )
				.isEqualTo( "svn" );
	}

}
