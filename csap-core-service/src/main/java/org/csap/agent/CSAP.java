/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.csap.agent.stats.OsProcessCollector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 *
 * Constants shared in multiple packages or components
 * 
 * 
 * @author someDeveloper
 */
public class CSAP {

	
	
	public static final String SAME_LOCATION = "sameLocation";
	
	public static final String CONFIG_PARSE_ERROR = "__ERROR: ";
	public static final String CONFIG_PARSE_WARN = "__WARN: ";
	private CSAP() {} ; // constants

	public static final String DOCKER_REPOSITORY = "$dockerRepository";
	public static final String SERVICE_PARAMETERS = "$parameters";
	public static final String JMX_PARAMETER = "-DcsapJmxPort=";
	public static final String DOCKER_JAVA_PARAMETER = "-DcsapDockerJava";
	

	public static final String AGENT_CONTEXT = "CsAgent";
	public static final String AGENT_PORT = "8011";
	public static final String DEFAULT_DOMAIN = "yourorg.org";
	public static final String ADMIN = "admin";
	public static final String PACKAGE_PARAM = "releasePackage";
	public static final String ALL_PACKAGES = "All Packages";
	public static final String ROLES = "ROLES";
	public static final String HOST_PARAM = "hostName";
	public static final String SERVICE_PORT_PARAM = "serviceName";
	public static final String SERVICE_NOPORT_PARAM = "service";
	

	public static final long ONE_SECOND_MS = 1000;
	public static final long ONE_MINUTE_MS = 60*1000;
	
	public static final long MB_FROM_BYTES = 1024*1024*1 ;

	public static Stream<JsonNode> jsonStream ( JsonNode node ) {
		return StreamSupport.stream(node.spliterator(), false) ;
	}
	
	public static String jsonPrint( ObjectMapper jacksonMapper, JsonNode j) throws JsonProcessingException {
		return jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( j ) ;
	}
	
	public static String getRequestSource () {
		String stack = Arrays.asList( Thread.currentThread().getStackTrace() ).stream()
			.filter( stackElement -> {
				return  ( !stackElement.getClassName().equals( CSAP.class.getName()) )
						&& (  !stackElement.getClassName().startsWith( "java." )  ) ;
			} )
			.map( StackTraceElement::toString )
			.findFirst()
			.orElse( "Stack not found" );
		return stack;
	}

	public static String getCsapFilteredStackTrace ( Throwable possibleNestedThrowable ) {
		return getFilteredStackTrace( possibleNestedThrowable, "csap" ) ;
	}
	public static String getFilteredStackTrace ( Throwable possibleNestedThrowable, String pattern ) {
		// add the class name and any message passed to constructor
		final StringBuffer result = new StringBuffer();
	
		Throwable currentThrowable = possibleNestedThrowable;
	
		int nestedCount = 1;
		while (currentThrowable != null) {
	
			if ( nestedCount == 1 ) {
				result.append( "\n========== CSAP Exception, Filter:  " + pattern );
			} else {
				result.append( "\n========== Nested Count: " );
				result.append( nestedCount );
				result.append( " ===============================" );
			}
			result.append( "\n\n Exception: " + currentThrowable
				.getClass()
				.getName() );
			result.append( "\n Message: " + currentThrowable.getMessage() );
			result.append( "\n\n StackTrace: \n" );
	
			// add each element of the stack trace
			List<StackTraceElement> traceElements = Arrays.asList( currentThrowable.getStackTrace() );
	
			Iterator<StackTraceElement> traceIt = traceElements.iterator();
			while (traceIt.hasNext()) {
				StackTraceElement element = traceIt.next();
				String stackDesc = element.toString();
				if ( pattern == null || stackDesc.contains( pattern ) ) {
					result.append( stackDesc );
					result.append( "\n" );
				}
			}
			result.append( "\n========================================================" );
			currentThrowable = currentThrowable.getCause();
			nestedCount++;
		}
		return result.toString();
	}
	
	public static <T> Predicate<T> not(Predicate<T> t) {
	    return t.negate();
	}
	
	public static String alphaNumericOnly( String input) {
		return input.replaceAll( "[^A-Za-z0-9]", "_") ;
	}
	

	public static String pad( String input) {
		return StringUtils.rightPad( input, 25 ) ;
	}
	
	public static void logLevel(String className, Level l) {
		Configurator.setAllLevels( className, l );
	}
	

	public static void setLogToDebug(String className) {
		Configurator.setAllLevels( className, Level.DEBUG );
	}
	public static void setLogToInfo(String className) {
		Configurator.setAllLevels( className, Level.INFO );
	}
}
