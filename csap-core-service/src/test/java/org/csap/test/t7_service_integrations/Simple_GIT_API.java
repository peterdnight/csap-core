/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.test.t7_service_integrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.csap.test.t1_container.Boot_Container_Test;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 *
 *
 * https://github.com/centic9/jgit-cookbook http://www.codeaffine.com/2015/11/30/jgit-clone-repository/
 *
 *
 * @author someDeveloper
 */
public class Simple_GIT_API {

	final static private Logger logger = LoggerFactory.getLogger( Simple_GIT_API.class );
	
	private final String baseLocation= "/agentUnitTests/junit-" + this.getClass().getSimpleName() + "/" ;

	String inputUrl = "https://github.com/spring-projects/spring-mvc-showcase.git";

	String branch = "refs/heads/master"; // branch = "fw_shell";//

	@Test
	public void simple_checkout_with_no_branch() throws Exception {

		File outputFolder = new File( baseLocation + "testGitNoBranch" );

		FileUtils.deleteDirectory( outputFolder );

		String message = "Perform git checkout of " + inputUrl + " to destination: "
				+ outputFolder.getAbsolutePath();
		logger.info(Boot_Container_Test.TC_HEAD + message );

		//
		Git r = Git
				.cloneRepository()
				.setURI( inputUrl )
				.setDirectory( outputFolder )
				.setCredentialsProvider(
						new UsernamePasswordCredentialsProvider( "dummy", "dummy" ) )
				.call();

		File buildPom = new File( outputFolder + "/pom.xml" );
		assertThat( buildPom )
				.as( "Pom file found" )
				.exists().isFile();
	}

	@Ignore
	@Test
	public void simple_checkout_with_authentication() throws Exception {

		File outputFolder = new File( baseLocation + "testGitWithPassword" );
		FileUtils.deleteDirectory( outputFolder );

		String authGitUrl = "https://stash-eng.yourcompany.com/sjc/shared/1/scm/eed/csap.git";
		String userid = "someDeveloper";
		String pass = "FIXME";

		assertThat( pass ).isNotEqualTo( "FIXME" ).as( "Update the password" );

		String message = "Perform git checkout of " + authGitUrl + " to destination: "
				+ outputFolder.getAbsolutePath();
		logger.info(Boot_Container_Test.TC_HEAD + message );

		CloneCommand cloneCommand
				= Git.cloneRepository()
				.setURI( authGitUrl )
				.setDirectory( outputFolder )
				.setCredentialsProvider(
						new UsernamePasswordCredentialsProvider( userid, pass ) );

		cloneCommand.call();
		File buildPom = new File( outputFolder + "/BootReference/pom.xml" );
		assertThat( buildPom )
				.as( "Pom file found" )
				.exists().isFile();
	}

	@Ignore
	@Test
	public void simple_checkout_with_subFolder() throws Exception {

		File outputFolder = new File( baseLocation + "testGitWithSubFolder" );
		FileUtils.deleteDirectory( outputFolder );

		String authGitUrl = "https://stash-eng.yourcompany.com/sjc/shared/1/scm/eed/csap.git";
		String userid = "someDeveloper";
		String pass = "FIXME";

		assertThat( "jgit support for folders" ).isEqualTo( "false" ).as( "not supported" );
		assertThat( pass ).isNotEqualTo( "FIXME" ).as( "Update the password" );

		String message = "Perform git checkout of " + authGitUrl + " to destination: "
				+ outputFolder.getAbsolutePath();
		logger.info(Boot_Container_Test.TC_HEAD + message );

		CloneCommand cloneCommand
				= Git.cloneRepository()
				.setURI( authGitUrl )
				.setDirectory( outputFolder )
				.setCredentialsProvider(
						new UsernamePasswordCredentialsProvider( userid, pass ) );

		Git gitRepo = cloneCommand.setNoCheckout( true ).call();
		gitRepo.checkout().setStartPoint( "master").call();
		gitRepo.getRepository().close();

		File buildPom = new File( outputFolder + "/pom.xml" );
		assertThat( buildPom )
				.as( "Pom file found" )
				.exists().isFile();
	}

	@Test
	public void simple_checkout_with_branch() throws Exception {

		File outputFolder = new File( baseLocation + "testGitBranch" );

		FileUtils.deleteDirectory( outputFolder );

		String message = "Perform git checkout of " + inputUrl + " to destination: "
				+ outputFolder.getAbsolutePath();
		logger.info(Boot_Container_Test.TC_HEAD + message );

		Git r = Git
				.cloneRepository()
				.setURI( inputUrl )
				.setDirectory( outputFolder )
				//.setBranchesToClone( singleton( "refs/heads/" ) )
				.setBranch( branch )
				.call();

		File buildPom = new File( outputFolder + "/pom.xml" );
		assertThat( buildPom )
				.as( "Pom file found" )
				.exists().isFile();
	}

}
