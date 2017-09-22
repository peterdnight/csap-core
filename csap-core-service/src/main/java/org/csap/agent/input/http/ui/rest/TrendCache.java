/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.input.http.ui.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.csap.agent.model.Application;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 *
 * @author someDeveloper
 */
@Component
@CacheConfig(cacheNames = "AnalyticsTrendingCache")
public class TrendCache {

	final Logger logger = LoggerFactory.getLogger( this.getClass() );

	ObjectMapper jacksonMapper = new ObjectMapper();

	@Autowired
	Application csapApp;

	@Autowired
	@Qualifier("trendRestTemplate")
	private RestTemplate trendRestTemplate;

	DateFormat shortFormatter = new SimpleDateFormat( "HH:mm:ss MMM-dd" );

	final static String KEY_IS_RESTURL="{#restUrl}" ;
	
	@Cacheable( key = KEY_IS_RESTURL )  
	public ObjectNode get(String restUrl) throws Exception {

		ObjectNode resultsJson = refreshRequiredResponse();

		resultsJson.put( TrendCacheManager.HASH,
				buildReportHash( restUrl ) );
		
		resultsJson.put( TrendCacheManager.NEEDS_LOAD, "initialLoadInProgress" );

		return resultsJson;
	}

	private ObjectNode refreshRequiredResponse() {
		ObjectNode resultsJson = jacksonMapper.createObjectNode();

		resultsJson.put( TrendCacheManager.LAST_UPDATE_TOKEN, 0 );
		return resultsJson;
	}

	// Report Hashes used to prevent multiple request for the same report being scheduled
	// Large number of users could hit..
	static public Integer buildReportHash(String restUrl) {
		return restUrl.hashCode();
	}

	@CachePut(key = KEY_IS_RESTURL)
	public ObjectNode update(String restUrl, String timerName) throws Exception {

		ObjectNode resultsJson = refreshRequiredResponse();

		int reportHash
				= buildReportHash( restUrl );
		//	logger.info( "ReportHash: {}, url: {} ", reportHash, restUrl );

//		logger.warn( "SLEEEPING ++++++++++++++ ");
//		Thread.sleep(10000) ;
		Split allTimer = SimonManager.getStopwatch( "trendCache.reload" ).start();
		timerName = "trendCache.reload." + timerName.replaceAll( "/", "-" ).replaceAll( " ", "-" ).replaceAll( "=", "-" ).replaceAll( "\\?", "." ).replaceAll( "&", "." );
		Split trendTimer = SimonManager.getStopwatch( timerName ).start();
		try {

			ObjectNode restResponse = trendRestTemplate.getForObject( restUrl, ObjectNode.class );
			resultsJson = restResponse;
			if ( resultsJson != null ) {
				resultsJson.put( "source", restUrl );
				resultsJson.put( "updated", shortFormatter.format( new Date() ) );
				resultsJson.put( TrendCacheManager.LAST_UPDATE_TOKEN, System.currentTimeMillis() );

			}
		} catch ( Exception e ) {
			logger.error( "Failed getting report from url: {}, Reason: ", restUrl, e.getMessage() );
			logger.debug( "Stack Trace ", e );
			resultsJson.put( "url", restUrl );
			resultsJson.put( "message", "Error during Access: " + e.getMessage() );
		}
		trendTimer.stop();
		allTimer.stop();
		resultsJson.put( TrendCacheManager.HASH, reportHash );

		return resultsJson;
	}

}
