package org.csap.test.t7_service_integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.csap.agent.CSAP;
import org.csap.agent.linux.OutputFileMgr;
import org.csap.agent.model.Application;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.services.SourceControlManager;
import org.csap.agent.services.SourceControlManager.ScmProvider;
import org.csap.test.InitializeLogging;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;



@RunWith ( SpringRunner.class )
@SpringBootTest
@ConfigurationProperties ( prefix = "test.variables" )
@ActiveProfiles ( profiles = { "junit", "company" } )
public class Source_Control_Test {

	final static private Logger logger = LoggerFactory.getLogger( Source_Control_Test.class );

	// needed to resolve test properties
	@SpringBootApplication
	static class SimpleConfiguration {
	}

	// updated by @ConfigurationProperties
	private String scmUserid = null;

	public void setScmUserid ( String scmUserid ) {
		this.scmUserid = scmUserid;
	}

	private String scmPass = null;

	public void setScmPass ( String scmPass ) {
		this.scmPass = scmPass;
	}

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
		//
		// File csapApplicationDefinition = new File(
		// getClass().getResource(
		// "/org/csap/test/data/test_application_model.json" ).getPath() );

		logger.info( "Loading test configuration: {}", InitializeLogging.DEFINITION_DEFAULT );
		Application.setJvmInManagerMode( false );
		csapApp = new Application();
		csapApp.setAutoReload( false );
		csapApp.initialize();

		assertThat( csapApp.loadDefinitionForJunits( false, InitializeLogging.DEFINITION_DEFAULT ) )
			.as( "No Errors or warnings" )
			.isTrue();

		encryptor = new StandardPBEStringEncryptor();
		encryptor.setPassword( "junittest" );
		sourceControlManager = new SourceControlManager( csapApp, encryptor );
	}

	@After
	public void tearDown ()
			throws Exception {
	}

	// @Inject
	Application csapApp;

	// @Inject
	private SourceControlManager sourceControlManager;

	private StandardPBEStringEncryptor encryptor;

	@Test
	public void verify_setup () {

		logger.info( "Got here: {}", scmUserid );

		assertThat( isSetupOk() ).as( "setup ok, ~home/csap/application-company.yml loaded" ).isTrue();

	}

	private boolean isSetupOk () {

		ServiceInstance serviceInstance = csapApp.findFirstServiceInstanceInLifecycle( "CsapSimple" );

		if ( scmUserid == null || scmPass == null || serviceInstance == null )
			return false;

		logger.info( "serviceInstance: {}", serviceInstance.getScmBuildLocation() );

		return true;
	}

	@Test
	public void verify_git_service_checkout ()
			throws Exception {

		if ( !isSetupOk() )
			return;

		logger.info( InitializeLogging.TC_HEAD );
		ServiceInstance serviceInstance = csapApp.findFirstServiceInstanceInLifecycle( "CsapSimple" );
		File serviceBuildFolder = new File( csapApp.getBUILD_DIR(), serviceInstance.getServiceName_Port() );
		File buildPom = new File( serviceBuildFolder, serviceInstance.getScmBuildLocation() + "/pom.xml" );

		FileUtils.deleteDirectory( serviceBuildFolder );

		OutputFileMgr outputFm = new OutputFileMgr(
			csapApp.getProcessingDir(), "/"
					+ serviceInstance.getServiceName_Port()
					+ "_testDeploy" );

		logger.info( "Perform git checkout of {} to destination: {}", 
			serviceInstance.getServiceName_Port(),
			serviceBuildFolder.getAbsolutePath() );

		sourceControlManager.checkOutFolder(
			scmUserid, encryptor.encrypt( scmPass ),
			serviceInstance.getDefaultBranch(), serviceInstance.getServiceName_Port(), serviceInstance,
			outputFm.getBufferedWriter() );

		assertThat( buildPom )
			.as( "Pom file found" )
			.exists().isFile();

		logger.info( "Deleting: {}", serviceBuildFolder.getAbsolutePath() );
		Files.list( serviceBuildFolder.toPath() )
			.forEach( System.out::println );

		FileUtils.deleteDirectory( serviceBuildFolder );

	}


	@Test
	public void verify_git_service_checkout_default_branch ()
			throws Exception {

		if ( !isSetupOk() )
			return;

		logger.info( InitializeLogging.TC_HEAD );
		ServiceInstance serviceInstance = csapApp.findFirstServiceInstanceInLifecycle( "CsapSimple" );
		File serviceBuildFolder = new File( csapApp.getBUILD_DIR(), serviceInstance.getServiceName_Port() );
		File buildPom = new File( serviceBuildFolder, serviceInstance.getScmBuildLocation() + "/pom.xml" );

		FileUtils.deleteDirectory( serviceBuildFolder );

		OutputFileMgr outputFm = new OutputFileMgr(
			csapApp.getProcessingDir(), "/"
					+ serviceInstance.getServiceName_Port()
					+ "_testDeploy" );

		logger.info( "Perform git checkout of {} to destination: {}", 
			serviceInstance.getServiceName_Port(),
			serviceBuildFolder.getAbsolutePath() );

		sourceControlManager.checkOutFolder(
			scmUserid, encryptor.encrypt( scmPass ),
			sourceControlManager.GIT_NO_BRANCH, serviceInstance.getServiceName_Port(), serviceInstance,
			outputFm.getBufferedWriter() );

		assertThat( buildPom )
			.as( "Pom file found" )
			.exists().isFile();

		logger.info( "Deleting: {}", serviceBuildFolder.getAbsolutePath() );
		Files.list( serviceBuildFolder.toPath() )
			.forEach( System.out::println );

		FileUtils.deleteDirectory( serviceBuildFolder );

	}
	
//	@Test
//	public void verify_git_sourceControlManager_default_branch_checkout ()
//			throws Exception {
//
//		String csapService_Port = "springmvc-showcase_8061";
//		String scmBranch = sourceControlManager.GIT_NO_BRANCH;
//		String scmLocation = "https://github.com/spring-projects/spring-mvc-showcase.git";
//
//		File checkOutLocation = new File( csapApp.getBUILD_DIR() + csapService_Port );
//		FileUtils.deleteQuietly( checkOutLocation );
//
//		OutputFileMgr outputFm = new OutputFileMgr(
//			csapApp.getProcessingDir(), "/"
//					+ csapService_Port
//					+ "_testDeploy" );
//		String message = "Perform git checkout of " + csapService_Port + " to destination: ";
//		logger.info( InitializeLogging.TC_HEAD + message );
//
//		CSAP.setLogToInfo( "org.eclipse.jgit" );
//
//		ServiceInstance serviceInstance = new ServiceInstance();
//		serviceInstance.setScmLocation( scmLocation );
//		serviceInstance.setScm( SourceControlManager.ScmProvider.git.key );
//		serviceInstance.setDefaultBranch( scmBranch );
//		;
//
//		sourceControlManager.checkOutFolder(
//			scmUserid, encryptor.encrypt( scmPass ),
//			scmBranch, csapService_Port, serviceInstance,
//			outputFm.getBufferedWriter() );
//
//		File buildPom = new File( csapApp.getBUILD_DIR() + csapService_Port + "/pom.xml" );
//		assertThat( buildPom )
//			.as( "Pom file found" )
//			.exists().isFile();
//
//	}

	@Ignore
	@Test
	public void verify_git_update_with_authentication_required ()
			throws Exception {
		String csapService_Port = "BootReference_7111";
		File checkOutLocation = new File( csapApp.getBUILD_DIR() + csapService_Port );

		OutputFileMgr outputFm = new OutputFileMgr(
			csapApp.getProcessingDir(), "/"
					+ csapService_Port
					+ "_gitUpdate" );
		Writer w = new PrintWriter( System.err );
		sourceControlManager.pullGitUpdate( scmUserid, encryptor.encrypt( scmPass ), checkOutLocation, w );
		outputFm.opCompleted();
	}

	@Ignore
	@Test
	public void verify_git_checkout_of_agent_at_github ()
			throws Exception {

		String csapService_Port = "CsAgent_8011_Junit";
		String scmBranch = sourceControlManager.GIT_NO_BRANCH;
		String scmLocation = "https://bitbucket.yourcompany.com/bitbucket/scm/csap/agent.git";

		assertThat( scmPass ).isNotEqualTo( "FIXME" ).as( "Update the password" );

		File checkOutLocation = new File( csapApp.getBUILD_DIR() + csapService_Port );

		boolean forceCloneEveryTime = false;
		if ( forceCloneEveryTime ) {
			FileUtils.deleteQuietly( checkOutLocation );

			if ( checkOutLocation.exists() ) {
				assertThat( true ).as( "Unable to delete: " + checkOutLocation.getCanonicalPath() ).isFalse();
			}
		}

		OutputFileMgr outputFm = new OutputFileMgr(
			csapApp.getProcessingDir(), "/"
					+ csapService_Port
					+ "_gitClone" );

		String message = "Perform git checkout of " + csapService_Port + " to destination: ";
		logger.info( InitializeLogging.TC_HEAD + message );

		CSAP.setLogToInfo( "org.eclipse.jgit" );

		ServiceInstance serviceInstance = new ServiceInstance();
		serviceInstance.setScmLocation( scmLocation );
		serviceInstance.setScm( SourceControlManager.ScmProvider.git.key );

		sourceControlManager.checkOutFolder(
			scmUserid, encryptor.encrypt( scmPass ),
			scmBranch, csapService_Port, serviceInstance,
			outputFm.getBufferedWriter() );

		// sourceControlManager.updateGitRepository( checkOutLocation,
		// outputFm.getBufferedWriter() );

		// File buildPom = new File( csapApp.getBUILD_DIR() + csapService_Port +
		// "/BootReference/pom.xml" );
		File buildPom = new File( csapApp.getBUILD_DIR() + csapService_Port + "/pom.xml" );
		assertThat( buildPom )
			.as( "Pom file found" )
			.exists().isFile();

	}

	@Ignore
	@Test
	public void verify_git_checkout_of_agent ()
			throws Exception {

		String csapService_Port = "CsAgent_8011_Junit";
		String scmBranch = sourceControlManager.GIT_NO_BRANCH;
		String scmLocation = "https://bitbucket.yourcompany.com/bitbucket/scm/csap/agent.git";

		assertThat( scmPass ).isNotEqualTo( "FIXME" ).as( "Update the password" );

		File checkOutLocation = new File( csapApp.getBUILD_DIR() + csapService_Port );

		boolean forceCloneEveryTime = false;
		if ( forceCloneEveryTime ) {
			FileUtils.deleteQuietly( checkOutLocation );

			if ( checkOutLocation.exists() ) {
				assertThat( true ).as( "Unable to delete: " + checkOutLocation.getCanonicalPath() ).isFalse();
			}
		}

		OutputFileMgr outputFm = new OutputFileMgr(
			csapApp.getProcessingDir(), "/"
					+ csapService_Port
					+ "_gitClone" );

		String message = "Perform git checkout of " + csapService_Port + " to destination: ";
		logger.info( InitializeLogging.TC_HEAD + message );

		CSAP.setLogToInfo( "org.eclipse.jgit" );

		ServiceInstance serviceInstance = new ServiceInstance();
		serviceInstance.setScmLocation( scmLocation );
		serviceInstance.setScm( SourceControlManager.ScmProvider.git.key );

		sourceControlManager.checkOutFolder(
			scmUserid, encryptor.encrypt( scmPass ),
			scmBranch, csapService_Port, serviceInstance,
			outputFm.getBufferedWriter() );

		// sourceControlManager.updateGitRepository( checkOutLocation,
		// outputFm.getBufferedWriter() );

		// File buildPom = new File( csapApp.getBUILD_DIR() + csapService_Port +
		// "/BootReference/pom.xml" );
		File buildPom = new File( csapApp.getBUILD_DIR() + csapService_Port + "/pom.xml" );
		assertThat( buildPom )
			.as( "Pom file found" )
			.exists().isFile();

	}

	@Ignore
	@Test
	public void verify_git_checkout_of_multi_project ()
			throws Exception {

		String csapService_Port = "BootReference_7111";
		String scmBranch = sourceControlManager.GIT_NO_BRANCH;
		String scmBuildSubDir = "/BootReference";
		String scmLocation = "https://bitbucket.yourcompany.com/bitbucket/scm/csap/javasamples.git";

		assertThat( scmPass ).isNotEqualTo( "FIXME" ).as( "Update the password" );

		File checkOutLocation = new File( csapApp.getBUILD_DIR() + csapService_Port );

		boolean forceCloneEveryTime = false;
		if ( forceCloneEveryTime ) {
			// set to true to force clone evry time
			FileUtils.deleteQuietly( checkOutLocation );

			if ( checkOutLocation.exists() ) {
				assertThat( true ).as( "Unable to delete: " + checkOutLocation.getCanonicalPath() ).isFalse();
			}
		}

		OutputFileMgr outputFm = new OutputFileMgr(
			csapApp.getProcessingDir(), "/"
					+ csapService_Port
					+ "_gitClone" );

		logger.info( InitializeLogging.TC_HEAD + "Perform git checkout of {} to {}, output in: {}", csapService_Port, checkOutLocation,
			outputFm.getOutputFile().getCanonicalPath() );

		CSAP.setLogToInfo( "org.eclipse.jgit" );

		ServiceInstance serviceInstance = new ServiceInstance();
		serviceInstance.setScmLocation( scmLocation );
		serviceInstance.setScmBuildLocation( scmBuildSubDir );
		serviceInstance.setScm( SourceControlManager.ScmProvider.git.key );

		sourceControlManager.checkOutFolder(
			scmUserid, encryptor.encrypt( scmPass ),
			scmBranch, csapService_Port, serviceInstance,
			outputFm.getBufferedWriter() );

		// sourceControlManager.updateGitRepository( checkOutLocation,
		// outputFm.getBufferedWriter() );

		// File buildPom = new File( csapApp.getBUILD_DIR() + csapService_Port +
		// "/BootReference/pom.xml" );
		File buildPom = new File( csapApp.getBUILD_DIR() + csapService_Port + scmBuildSubDir + "/pom.xml" );
		assertThat( buildPom )
			.as( "Pom file found" )
			.exists().isFile();

		logger.info( "Results: {}", FileUtils.readFileToString( outputFm.getOutputFile() ) );

	}

	@Ignore // requires a legit id for checkout
	@Test
	public void verify_svn_checkout_of_service ()
			throws Exception {

		String rawPass = scmPass;
		assertThat( rawPass ).isNotEqualTo( "FIXME" ).as( "Update the password" );

		String svnBranch = "trunk";
		String svcName = "TestCheckoutFolder";

		File svnCheckoutFolder = new File( Application.BUILD_DIR + svcName );
		FileUtils.deleteDirectory( svnCheckoutFolder );
		// InstanceConfig instanceConfig=
		// csapApp.getServiceInstance("CsAgent_8011");
		ServiceInstance instanceConfig = new ServiceInstance();
		instanceConfig.setScmLocation( "https://svn.yourcompany.com/svn/csap/trunk/public/javaProjects/BootReference" );
		instanceConfig.setScm( SourceControlManager.ScmProvider.svn.key );

		String encryptedPass = encryptor.encrypt( rawPass ); // immediately

		// BufferedWriter outputWriter = new BufferedWriter(new
		// OutputStreamWriter(System.out));
		// OutputFileMgr outputManager = new OutputFileMgr(
		// csapApp.getProcessingDir(),
		// "unitTestFor" + this.getClass().getSimpleName() );
		StringWriter sw = new StringWriter();
		BufferedWriter stringWriter = new BufferedWriter( sw );

		sourceControlManager.checkOutFolder( scmUserid, encryptedPass, svnBranch, svcName,
			instanceConfig, stringWriter );

		assertThat( svnCheckoutFolder )
			.as( "checkout folder created" )
			.exists();

		assertTrue( "SVn Checkout folder created",
			svnCheckoutFolder.exists() );
		// FileUtils.deleteDirectory(svnCheckoutFolder);
		stringWriter.flush();
		String results = sw.toString();
		logger.info( "output messages: {}", results );

		assertThat( results )
			.as( "svn messages" )
			.contains(
				"Checking out: https://svn.yourcompany.com/svn/csap/trunk/public/javaProjects/BootReference" );

	}

	// @Ignore // requires a legit id for checkout
	// @Test
	// public void verify_svn_checkin_of_definition ()
	// throws Exception {
	//
	// logger.info( InitializeLogging.TC_HEAD + "Scenario: SVN Definition" );
	//
	// String rawPass = scmPass;
	// String definitionUrl =
	// "https://svn.yourcompany.com/svn/csap/trunk/core/Agent/src/test/java/org/csap/test/data/fullSample";
	// assertThat( rawPass ).isNotEqualTo( "FIXME" ).as( "Update the password"
	// );
	//
	// String svnBranch = "trunk";
	// String svcName = "Svn_junit_Definition";
	//
	// File sourceFolderOnFileSystem = new File( Application.BUILD_DIR + svcName
	// );
	// FileUtils.deleteDirectory( sourceFolderOnFileSystem );
	// // InstanceConfig instanceConfig=
	// // csapApp.getServiceInstance("CsAgent_8011");
	// ServiceInstance instanceConfig = new ServiceInstance();
	// instanceConfig.setScmLocation( definitionUrl );
	// instanceConfig.setScm( SourceControlManager.ScmProvider.svn.key );
	//
	// String encryptedPass = encryptor.encrypt( rawPass ); // immediately
	//
	// definitionCheckOut( scmUserid, encryptedPass, svnBranch, svcName,
	// instanceConfig, sourceFolderOnFileSystem, "Application.json" );
	//
	// // update the folder with new content, files, etc
	// File definitionFile = new File( sourceFolderOnFileSystem,
	// "Application.json" );
	//
	// definitionCheckInPlain( SourceControlManager.ScmProvider.svn,
	// definitionUrl, scmUserid, encryptedPass, svnBranch, definitionFile );
	//
	// definitionCheckInChanges( SourceControlManager.ScmProvider.svn,
	// definitionUrl, scmUserid, encryptedPass, svnBranch,
	// definitionFile );
	//
	// File newFile = verify_application_update_with_adds_and_deletes(
	// ScmProvider.svn,
	// definitionUrl, scmUserid, encryptedPass, svnBranch,
	// definitionFile.getParentFile() );
	// // File newFile = new File(sourceFolderOnFileSystem,
	// // "testJan.18-11.42.58");
	// deleteSvnFile( definitionUrl, scmUserid, encryptedPass, svnBranch,
	// newFile );
	//
	// }


	@Ignore
	@Test
	public void verify_git_checkin_of_definition ()
			throws Exception {

		logger.info( InitializeLogging.TC_HEAD + "Scenario: GIT Definition" );

		String rawPass = scmPass;
		// String definitionUrl =
		// "https://bitbucket.yourcompany.com/bitbucket/scm/csap/definition.git";
		String definitionUrl = "https://bitbucket.yourcompany.com/bitbucket/scm/csap/agent.git";
		assertThat( rawPass ).isNotEqualTo( "FIXME" ).as( "Update the password" );

		String svnBranch = SourceControlManager.GIT_NO_BRANCH;
		String svcName = "git_junit_Definition";

		File sourceFolderOnFileSystem = new File( Application.BUILD_DIR + svcName );

		// FileUtils.deleteDirectory( sourceFolderOnFileSystem ); // comment out
		// for speed

		// InstanceConfig instanceConfig=
		// csapApp.getServiceInstance("CsAgent_8011");
		ServiceInstance serviceInstance = new ServiceInstance();
		serviceInstance.setScmLocation( definitionUrl );
		serviceInstance.setScm( SourceControlManager.ScmProvider.git.key );

		String encryptedPass = encryptor.encrypt( rawPass ); // immediately
		String definitionLocation = "src/test/java/org/csap/test/data/gitDefinition/Application.json";

		definitionCheckOut( scmUserid, encryptedPass, svnBranch, svcName,
			serviceInstance, sourceFolderOnFileSystem, definitionLocation );

		// update the folder with new content, files, etc
		File definitionFile = new File( sourceFolderOnFileSystem, definitionLocation );
		//
		definitionCheckInPlain( SourceControlManager.ScmProvider.git, definitionUrl, scmUserid, encryptedPass, svnBranch, definitionFile );
		//
		definitionCheckInChanges( SourceControlManager.ScmProvider.git, definitionUrl, scmUserid, encryptedPass, svnBranch,
			definitionFile );
		//
		File newFile = verify_application_update_with_adds_and_deletes(
			ScmProvider.git,
			definitionUrl, scmUserid, encryptedPass, svnBranch,
			definitionFile.getParentFile() );
		// // File newFile = new File(sourceFolderOnFileSystem,
		// // "testJan.18-11.42.58");
		deleteGitFile( definitionFile, definitionUrl, scmUserid, encryptedPass, svnBranch, newFile );

	}

	private void deleteGitFile (	File definitionFile,
									String definitionUrl, String scmUserid, String encryptedPass,
									String svnBranch, File deleteFile )
			throws IOException, Exception {

		StringWriter sw = new StringWriter();
		BufferedWriter stringWriter = new BufferedWriter( sw );

		List<File> filesToDelete = new ArrayList<>();

		filesToDelete.add( deleteFile );

		sourceControlManager.checkInFolder(
			ScmProvider.git,
			definitionUrl, definitionFile, scmUserid, encryptedPass,
			svnBranch, "Deleteing test file", null, filesToDelete, stringWriter );

		stringWriter.flush();
		String ciResults = sw.toString();
		logger.info( "output messages: {}", ciResults );

		assertThat( ciResults )
			.as( "git messages" )
			.contains(
				"DiffEntry[DELETE" );

	}

	private void definitionCheckOut (	String scmUserid, String encryptedPass, String svnBranch, String svcName,
										ServiceInstance instanceConfig, File sourceFolderOnFileSystem,
										String definitionLocation )
			throws IOException, Exception {
		StringWriter sw = new StringWriter();
		BufferedWriter stringWriter = new BufferedWriter( sw );

		sourceControlManager.checkOutFolder(
			scmUserid, encryptedPass, svnBranch, svcName,
			instanceConfig, stringWriter );

		assertThat( sourceFolderOnFileSystem )
			.as( "checkout folder created" )
			.exists();

		assertThat( new File( sourceFolderOnFileSystem, definitionLocation ) )
			.as( "Application created" )
			.exists();

		// FileUtils.deleteDirectory(svnCheckoutFolder);
		stringWriter.flush();
		String coResults = sw.toString();
		logger.debug( "output messages: {}", coResults );

		if ( instanceConfig.isGit() ) {

			assertThat( coResults )
				.as( "git messages" )
				.containsPattern(
					".*git.*complete.*" );

			assertTrue( coResults.contains( "git clone complete" ) || coResults.contains( "git sync complete" ) );
		} else {

			assertThat( coResults )
				.as( "svn messages" )
				.contains(
					"Checking out: https://svn.yourcompany.com/svn/csap/trunk/core/Agent/src/test/java/org/csap/test/data/fullSample" );
		}

	}

	private void definitionCheckInPlain (	ScmProvider scmType, String definitionUrl, String scmUserid, String encryptedPass,
											String svnBranch,
											File definitionFile )
			throws IOException, Exception {
		StringWriter sw = new StringWriter();
		BufferedWriter stringWriter = new BufferedWriter( sw );

		logger.info( "Checkin - no changes." );
		sourceControlManager.checkInFolder(
			scmType,
			definitionUrl, definitionFile, scmUserid, encryptedPass,
			svnBranch, "No changes test comment", null, null, stringWriter );

		stringWriter.flush();
		String ciResults = sw.toString();
		logger.info( "output messages: {}", ciResults );

		if ( scmType == ScmProvider.svn ) {
			assertThat( ciResults )
				.as( "svn messages" )
				.contains( "EMPTY COMMIT" );
		} else {

			assertThat( ciResults )
				.as( "git messages" )
				.contains( "Completed GIT checkin" ); // UP_TO_DATE
		}

	}

	private void definitionCheckInChanges (	ScmProvider scmType,
											String definitionUrl, String scmUserid, String encryptedPass,
											String branch, File definitionFile )
			throws IOException, Exception {

		// String[] definitionLines = FileUtils.readFileToString( definitionFile
		// ).split( "\n" );

		List<String> lines = Files.readAllLines( definitionFile.toPath(), Charset.forName( "UTF-8" ) );
		String now = LocalDateTime.now().format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) );
		lines.set( 0, "{ \"testpeter\": \"" + now + "\", " );

		logger.info( "updating line 1: {}", lines.get( 0 ) );

		try {
			Files.write( definitionFile.toPath(), lines, Charset.forName( "UTF-8" ) );
		} catch (IOException ex) {
			logger.error( "Failed creating def file", ex );
		}

		StringWriter sw = new StringWriter();
		BufferedWriter stringWriter = new BufferedWriter( sw );

		sourceControlManager.checkInFolder(
			scmType,
			definitionUrl, definitionFile, scmUserid, encryptedPass,
			branch, "Updating Application.json test", null, null, stringWriter );

		stringWriter.flush();
		String ciResults = sw.toString();
		logger.info( "output messages: {}", ciResults );

		if ( scmType == ScmProvider.svn ) {
			assertThat( ciResults )
				.as( "svn messages" )
				.contains(
					"updating: Application.json" );
		} else {

			assertThat( ciResults )
				.as( "git messages" )
				.contains( "MODIFY src/test/java/org/csap/test/data/gitDefinition/Application.json" ); // UP_TO_DATE
		}

	}

	private void deleteSvnFile (
									String definitionUrl, String scmUserid, String encryptedPass,
									String svnBranch, File deleteFile )
			throws IOException, Exception {

		StringWriter sw = new StringWriter();
		BufferedWriter stringWriter = new BufferedWriter( sw );

		List<File> filesToDelete = new ArrayList<>();

		filesToDelete.add( deleteFile );

		sourceControlManager.doSvnDelete(
			definitionUrl, filesToDelete,
			scmUserid, encryptedPass,
			svnBranch, "Deleting files for test", stringWriter );

		stringWriter.flush();
		String ciResults = sw.toString();
		logger.info( "output messages: {}", ciResults );

		assertThat( ciResults )
			.as( "svn messages" )
			.contains(
				deleteFile.getName(), "commit_completed" );

	}

	private File verify_application_update_with_adds_and_deletes (
																	ScmProvider scmType,
																	String definitionUrl, String scmUserid, String encryptedPass,
																	String svnBranch, File definitionFolder )
			throws IOException, Exception {

		String now = LocalDateTime.now().format( DateTimeFormatter.ofPattern( "MMM.d-HH.mm.ss" ) );

		File newDefinitionFile = new File( definitionFolder, "test" + now + ".json" );

		logger.info( "creating new file: {}", newDefinitionFile );
		List<String> lines = Arrays.asList(
			"TestNewFile",
			now );

		File delFolder = new File( definitionFolder, "test1" );
		List<File> filesToDelete = new ArrayList<>();

		if ( delFolder.exists() ) {
			// delete 1st file found
			File delFile = new File( delFolder, delFolder.list()[0] );
			filesToDelete.add( delFile );
			// note delete folders work as well
		} else {
			logger.info( "Need to add test1 folder for testing" );
		}

		List<File> filesToAdd = new ArrayList<>();

		File newPropFile = new File( definitionFolder, "test1/testProp" + now + "/test" + now + ".properties" );
		newPropFile.getParentFile().mkdirs();
		filesToAdd.add( newPropFile );

		try {
			Files.write( newDefinitionFile.toPath(), lines, Charset.forName( "UTF-8" ) );
			Files.write( newPropFile.toPath(), lines, Charset.forName( "UTF-8" ) );
		} catch (IOException ex) {
			logger.error( "Failed creating def file", ex );
		}

		StringWriter sw = new StringWriter();
		BufferedWriter stringWriter = new BufferedWriter( sw );

		sourceControlManager.checkInFolder(
			scmType,
			definitionUrl, newDefinitionFile, scmUserid, encryptedPass,
			svnBranch, "Adding new file for test", filesToAdd, filesToDelete, stringWriter );

		stringWriter.flush();
		String ciResults = sw.toString();
		logger.info( "output messages: {}", ciResults );

		if ( scmType == ScmProvider.svn ) {
			assertThat( ciResults )
				.as( "svn messages" )
				.contains(
					"updating: " + newDefinitionFile.getName(),
					"updating: " + newPropFile.getName() );
		} else {

			assertThat( ciResults )
				.as( "git messages" )
				.contains( "DiffEntry[ADD" ); // DiffEntry[DELETE
		}
		return newDefinitionFile;
	}

}
