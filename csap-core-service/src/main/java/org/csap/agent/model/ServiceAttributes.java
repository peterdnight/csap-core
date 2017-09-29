/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.model;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 *
 * @author someDeveloper
 */
public enum ServiceAttributes {

	serviceType( "server" ),
	description( "description" ), documentation( "docUrl" ),
	serviceUrl( "url" ),
	//
	// adhoc configuration for rare cases. Placed up front to enable semantic
	// checks
	//
	metaData( "metaData" ),
	//
	// Core OS
	//
	startOrder( "autoStart" ), folderToMonitor( "disk" ),
	eolParameters( "java" ), parameters( "parameters" ), // critical ordering
	environmentVariables( "environmentVariables" ), eolEnv( "customAttributes" ),
	osProcessPriority( "osProcessPriority" ), processFilter( "processFilter" ),
	//
	// logging
	//
	logFolder( "logDirectory" ), logDefaultFile( "defaultLogToShow" ), logFilter( "logRegEx" ),
	propertyFolder( "propDirectory" ), libraryFolder( "libDirectory" ),
	//
	// remoteCollection - hosts assigned in order
	//
	remoteCollections( "remoteCollections" ),
	//
	// deployment
	//
	deployFromSource( "source" ), deployFromRepository( "maven" ),
	deployTimeMinutes( "deployTimeoutMinutes" ),
	//
	//
	dockerSettings( "docker" ), useDockerJavaContainer( "useDockerJavaContainer" ),
	//
	// monitoring and performance
	//
	osAlertLimits( "monitors" ), health( "health" ), notifications( "notifications" ), javaAlertWarnings( "standardJmx" ),
	javaJmxPort( "jmxPort" ),
	performanceApplication( "customMetrics" ), simonMbean( "simonMbean" ),
	//
	// service jobs
	//
	scheduledJobs( "scheduledJobs" ), 
	//
	// tomcat only
	//
	servletContext( "context" ),
	servletThreads( "servletThreadCount" ), servletAccept( "servletAccept" ),
	servletMaxConnections( "servletMaxConnections" ), servletTimeoutMs( "servletTimeoutMs" ),
	cookieName( "cookieName" ), cookiePath( "cookiePath" ), cookieDomain( "cookieDomain" ),
	httpCompression( "compression" ), httpCompressTypes( "compressableMimeType" ),

	// ref. http://tomcat.apache.org/connectors-doc/reference/workers.html
	webServerTomcat( "apacheModJk" ), webServerReWrite( "apacheModRewrite" ),
	// beta mode
	files( "files" );

	public String value;

	private ServiceAttributes( String value ) {
		this.value = value;
	}

	public static Stream<ServiceAttributes> stream () {
		return Arrays.stream( ServiceAttributes.values() );
	}

	public enum FileAttributes {
		name( "name" ), content( "content" ), external( "external" ), lifecycle( "lifecycle" ),
		newFile( "newFile" ), deleteFile( "deleteFile" ), contentUpdated( "contentUpdated" );

		public String json = "";

		private FileAttributes( String json ) {
			this.json = json;
		}
	}

}
