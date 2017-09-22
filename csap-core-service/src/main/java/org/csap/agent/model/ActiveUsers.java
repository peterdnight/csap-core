package org.csap.agent.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.csap.agent.CsapCoreService;
import org.csap.agent.CSAP;
import org.csap.agent.input.http.api.AgentApi;
import org.csap.agent.misc.CsapEventClient;
import org.csap.helpers.CsapSimpleCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

@Service
public class ActiveUsers {

	final  Logger logger = LoggerFactory.getLogger( getClass() );
	
	public ActiveUsers(CsapEventClient csapEventClient, Application csapApp) {
		this.csapEventClient = csapEventClient ;
		this.csapApp = csapApp ;
	}

	private CsapEventClient csapEventClient;
	private Application csapApp;

	private ObjectMapper jacksonMapper = new ObjectMapper();
	
	private volatile HashMap<String, CsapSimpleCache> activeUsersMap = new HashMap<String, CsapSimpleCache>();
	
	final static int EXPIRED_USERS_INTERVAL = 10 ;
	
	public synchronized ArrayNode updateUserAccessAndReturnAllActive ( String userid, boolean logAccess ) {

		//
		if ( !activeUsersMap.containsKey( userid ) ) {
			activeUsersMap.put( userid, CsapSimpleCache.builder( 6*EXPIRED_USERS_INTERVAL, TimeUnit.MINUTES, getClass(), "Active Portal Users" ) );
			if ( logAccess ) {
				// agents on local host will get their access removed by admin.
				// need to throttle.
				csapEventClient.generateEvent( CsapEventClient.CSAP_UI_CATEGORY + "/who", userid,
					"Accessing system", "Note this will NOT be output continually" );
			}

		}
		
		activeUsersMap.get( userid ).reset(); // extend timer

		logger.debug( "Reset user: {} in  Active Users: {}", userid, activeUsersMap.keySet() );
		return getActive();
	}
	
	@Cacheable(CsapCoreService.TIMEOUT_CACHE_60s)
	synchronized public ArrayNode allAdminUsers () {

		ArrayNode users = jacksonMapper.createArrayNode() ;
		
		
		// remove calls for other hosts
		csapApp.getAllPackages()
			.getServiceInstances( "admin" )
			.filter( instance -> ! instance.getHostName().equals( Application.getHOST_NAME() ) )
			.map( this::getUsersOnRemoteAdmins )
			.forEach( users::addAll );
		
		// add the local host entries
		users.addAll( getActive() ) ;
		
		// now make them distinct
		HashSet<String> uniqueUsers = new HashSet<>() ;
		users.forEach( userJson -> uniqueUsers.add( userJson.asText() ));
		
		// Now transform 
		users.removeAll() ;
		uniqueUsers.forEach( users::add );

		
		return users ;
	}

	@Inject
	@Qualifier ( "adminConnection" )
	private RestTemplate adminTemplate;
	
	private ArrayNode getUsersOnRemoteAdmins( ServiceInstance service ) {
		
		String adminUrl = service.getUrl() + CsapCoreService.API_AGENT_URL + AgentApi.USERS_URL ;
		
		ArrayNode remoteUsers;
		try {
			remoteUsers = adminTemplate.getForObject( adminUrl, ArrayNode.class );
		} catch (Exception e) {
			logger.warn( "Failed getting admin users: {}" , CSAP.getCsapFilteredStackTrace( e ) );
			remoteUsers=jacksonMapper.createArrayNode() ;
			remoteUsers.add( "Failed " + service.getHostName() ) ;
		}

		logger.debug( "Remote users: {}  url: {}", remoteUsers, adminUrl );
		
		return remoteUsers ;
	}


	public synchronized ArrayNode getActive() {
		
		ArrayNode users = jacksonMapper.createArrayNode() ;
		activeUsersMap.keySet()
			.stream()
			.forEach( users::add );
		
		return users ;
	}



	@Scheduled ( initialDelay = EXPIRED_USERS_INTERVAL * CSAP.ONE_MINUTE_MS , fixedDelay = EXPIRED_USERS_INTERVAL * CSAP.ONE_MINUTE_MS )
	public synchronized void checkExpired () throws InterruptedException {
//		logger.info( "CHecking for expired users" );
//		Thread.sleep( 5000 );
		activeUsersMap.entrySet().removeIf( userCacheEntry -> userCacheEntry.getValue().isExpired() );
		logger.debug( "Active Users: {}", activeUsersMap.keySet() );
	}
}
