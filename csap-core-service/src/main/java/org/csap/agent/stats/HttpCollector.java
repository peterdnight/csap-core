package org.csap.agent.stats;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.csap.agent.CSAP;
import org.csap.agent.model.Application;
import org.csap.agent.model.ServiceInstance;
import org.javasimon.SimonManager;
import org.javasimon.Split;
import org.javasimon.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class HttpCollector {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	ObjectMapper jacksonMapper = new ObjectMapper();
	
	Application csapApplication ;

	private ObjectNode deltaLastCollected = jacksonMapper.createObjectNode();
	private ServiceCollector serviceCollector;
	
	public HttpCollector( Application csapApp, ServiceCollector serviceCollector ) {
		this.csapApplication = csapApp ;
		this.serviceCollector = serviceCollector;
	}
	
	public void collect () {
		// TODO Auto-generated method stub
		Stopwatch stopwatch = SimonManager
			.getStopwatch( "collector.http" );
		Split split = stopwatch.start();

		csapApplication
			.getActiveModel()
			.getServicesOnHost( Application.getHOST_NAME() )
			.filter( serviceInstance -> serviceInstance.hasHttpCollection() )
			.forEach( this::executeHttpCollection );

		split.stop();
	}

	private void executeHttpCollection ( ServiceInstance serviceInstance ) {

		Stopwatch stopwatch = SimonManager
			.getStopwatch( "collector.http." + serviceInstance.getServiceName() );
		Split split = stopwatch.start();

		logger.debug( "titles for attributes: {} ", serviceInstance.getServiceMeterTitles() );

		// initial results with negative values for graphs to indicate
		// collection failure
		ServiceCollectionResults applicationResults = new ServiceCollectionResults( serviceInstance, serviceCollector.getInMemoryCacheSize() );
		serviceInstance
			.getServiceMeters()
			.stream()
			.forEach( serviceMeter -> applicationResults.addCustomResultLong( serviceMeter.getCollectionId(), 0l ) );

		if ( ! serviceInstance.isRunning() ) {
			logger.debug( "Skipping collections as service is down: " + serviceInstance.getServiceName() );
		} else {

			// ObjectNode serviceCustomMetrics =
			// serviceInstance.getPerformanceConfiguration();
			try {

				ObjectNode httpConfig = serviceInstance.getHttpMeterCollectionConfig();

				ResponseEntity<String> collectionResponse = serviceHttpCollection( serviceInstance.getServiceName(), httpConfig );

				String patternMatch = httpConfig
					.get( "patternMatch" )
					.asText();
				if ( collectionResponse != null && collectionResponse
					.getStatusCode()
					.is2xxSuccessful() ) {
					if ( patternMatch.equalsIgnoreCase( "JSON" ) ) {
						JsonNode jsonResponse = jacksonMapper.readTree( collectionResponse.getBody() );
						serviceInstance
							.getServiceMeters()
							.stream()
							.forEach( serviceMeter -> processHttpMeterUsingJson( serviceMeter, patternMatch,
								applicationResults,
								httpConfig, jsonResponse ) );

					} else {

						final String textResponse = collectionResponse.getBody();

						serviceInstance
							.getServiceMeters()
							.stream()
							.forEach( serviceMeter -> processHttpMeterUsingRegex( serviceMeter, patternMatch,
								applicationResults,
								httpConfig, textResponse ) );

					}
				} else {
					logger.warn( "Unable to collectionResponse: " + collectionResponse );
				}

			} catch (Exception e) {
				logger.warn(
					"Collection failed for service: {}; reason: \"{}\";  ==> verify collection settings in definition",
					serviceInstance.getServiceName(), e.getMessage() );
				logger.info( "Reason: {} ", CSAP.getCsapFilteredStackTrace( e ) );
			}

		}

		// will update based on collected values.
		applicationResults.updateCustomResultCache( serviceCollector.getCustomResultsCache() );

		// other collection intervals will reuse the data from shorter intervals
		serviceCollector.getLastCollectedResults().put( serviceInstance.getServiceName_Port(), applicationResults );
		split.stop();

	}

	private ResponseEntity<String> serviceHttpCollection ( String serviceName, ObjectNode httpConfig )
			throws IOException {
		String httpCollectionUrl = httpConfig
			.get( "httpCollectionUrl" )
			.asText();

		JsonNode user = httpConfig.get( "user" );
		JsonNode pass = httpConfig.get( "pass" );

		if ( httpConfig.has( Application.getCurrentLifeCycle() ) ) {
			user = httpConfig
				.get( Application.getCurrentLifeCycle() )
				.get( "user" );
			pass = httpConfig
				.get( Application.getCurrentLifeCycle() )
				.get( "pass" );
		}
		RestTemplate localRestTemplate = getRestTemplate( serviceCollector.getMaxCollectionAllowedInMs(), user,
			pass, serviceName + " collection password" );

		ResponseEntity<String> collectionResponse;

		if ( Application.isRunningOnDesktop() && httpCollectionUrl.startsWith( "classpath" ) ) {
			File stubResults = new File( getClass()
				.getResource( httpCollectionUrl.substring( httpCollectionUrl.indexOf( ":" ) + 1 ) )
				.getFile() );

			logger.warn( "******** Application.isRunningOnDesktop() - using: " + stubResults
				.getAbsolutePath() );
			collectionResponse = new ResponseEntity<String>( FileUtils.readFileToString( stubResults ),
				HttpStatus.OK );

		} else {
			collectionResponse = localRestTemplate.getForEntity( httpCollectionUrl, String.class );
			// logger.debug("Raw Response: \n{}",
			// collectionResponse.toString());
		}
		return collectionResponse;
	}

	private void processHttpMeterUsingRegex (
												ServiceMeter serviceMeter, String patternMatch, ServiceCollectionResults applicationResults,
												JsonNode httpConfig, String collectionResponse ) {

		Pattern p = Pattern.compile( serviceMeter.getHttpAttribute() + httpConfig
			.get( "patternMatch" )
			.asText() );
		Matcher regExMatcher = p.matcher( collectionResponse );

		// logger.debug("{} Using match: {}" , collectionResponse,
		// httpConfig.get("patternMatch").asText()) ;
		if ( regExMatcher.find() ) {

			logger.debug( "{} matched {}", serviceMeter.getHttpAttribute(), regExMatcher.group( 1 ) );
			try {
				double divideBy = serviceMeter.getDivideBy( serviceCollector.getCollectionIntervalSeconds() );

				if ( serviceMeter.getDecimals() != 0 ) {
					Double collectedMetric = Double.parseDouble( regExMatcher.group( 1 ) );
					double precision = Math.pow( 10, serviceMeter.getDecimals() );

					double roundedMetric = Math.round( collectedMetric / divideBy * precision ) / precision;
					applicationResults.addCustomResultDouble( serviceMeter.getCollectionId(), roundedMetric );

				} else {
					// default to round
					Double collectedMetric = Double.parseDouble( regExMatcher.group( 1 ) );
					long collectedMetricAsLong = Math.round( collectedMetric / divideBy );
					long last = collectedMetricAsLong;
					if ( serviceMeter.isDelta() ) {
						String key = applicationResults
							.getServiceInstance()
							.getServiceName_Port()
								+ serviceMeter.getHttpAttribute();
						if ( deltaLastCollected.has( key ) ) {
							collectedMetricAsLong = collectedMetricAsLong - deltaLastCollected
								.get( key )
								.asLong();
							if ( collectedMetricAsLong < 0 ) {
								collectedMetricAsLong = 0;
							}
						} else {
							collectedMetricAsLong = 0;
						}

						deltaLastCollected.put( key, last );
					}
					applicationResults.addCustomResultLong( serviceMeter.getCollectionId(), collectedMetricAsLong );

				}

			} catch (NumberFormatException e) {
				logger.warn( "Failed to parse {} using {}", serviceMeter.getHttpAttribute(), patternMatch );
				logger.debug( "Exception", e );
			}

		} else {
			logger.warn( "No match for: " + serviceMeter.getHttpAttribute() );
		}

	}


	private void processHttpMeterUsingJson (
												ServiceMeter serviceMeter, String patternMatch, ServiceCollectionResults applicationResults,
												JsonNode httpConfig, JsonNode collectedFromService ) {

		// support for JSON
		try {
			JsonNode attributeValue = collectedFromService.at( serviceMeter.getHttpAttribute() );

			// hook for mongo server stats which sometimes adds this.
			if ( attributeValue.has( "$numberLong" ) ) {
				// make more general?
				attributeValue = attributeValue.get( "$numberLong" );
			}

			double divideBy = serviceMeter.getDivideBy( serviceCollector.getCollectionIntervalSeconds() );

			if ( serviceMeter.getDecimals() != 0 ) {
				Double collectedMetric = attributeValue.asDouble();
				double precision = Math.pow( 10, serviceMeter.getDecimals() );
				double roundedMetric = Math.round( collectedMetric / divideBy * precision ) / precision;
				double last = roundedMetric;
				if ( serviceMeter.isDelta() ) {
					String key = applicationResults
						.getServiceInstance()
						.getServiceName_Port()
							+ serviceMeter.getHttpAttribute();
					if ( deltaLastCollected.has( key ) ) {
						roundedMetric = roundedMetric - deltaLastCollected
							.get( key )
							.asDouble();
						if ( roundedMetric < 0 ) {
							roundedMetric = 0;
						}
					} else {
						roundedMetric = 0;
					}

					deltaLastCollected.put( key, last );
				}

				applicationResults.addCustomResultDouble( serviceMeter.getCollectionId(), roundedMetric );
			} else {
				// default to round
				Double collectedMetric = attributeValue.asDouble();
				long collectedMetricAsLong = Math.round( collectedMetric / divideBy );
				long last = collectedMetricAsLong;
				if ( serviceMeter.isDelta() ) {
					String key = applicationResults
						.getServiceInstance()
						.getServiceName_Port()
							+ serviceMeter.getHttpAttribute();
					if ( deltaLastCollected.has( key ) ) {
						collectedMetricAsLong = collectedMetricAsLong - deltaLastCollected
							.get( key )
							.asLong();
						if ( collectedMetricAsLong < 0 ) {
							collectedMetricAsLong = 0;
						}
					} else {
						collectedMetricAsLong = 0;
					}
					deltaLastCollected.put( key, last );
				}
				applicationResults.addCustomResultLong( serviceMeter.getCollectionId(), collectedMetricAsLong );
			}
		} catch (Exception e) {
			logger.warn( "Skipping attribute: \"" + serviceMeter.getHttpAttribute() + "\" Due to exception: " + e.getMessage() );
		}

	}

	private RestTemplate getRestTemplate ( long maxConnectionInMs, JsonNode user, JsonNode pass, String desc ) {

		logger.debug( "maxConnectionInMs: {} , user: {} , Pass: {} ", maxConnectionInMs, user, pass );

		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();

		// "user" : "$csapUser1", "pass" : "$csapPass1"
		if ( user != null && pass != null ) {
			CredentialsProvider credsProvider = new BasicCredentialsProvider();
			credsProvider.setCredentials(
				new AuthScope( null, -1 ),
				new UsernamePasswordCredentials(
					user.asText(),
					csapApplication.decode( pass.asText(), desc ) ) );

			HttpClient httpClient = HttpClients
				.custom()
				.setDefaultCredentialsProvider( credsProvider )
				.build();
			factory.setHttpClient( httpClient );
			// factory = new HttpComponentsClientHttpRequestFactory(httpClient);
		}

		factory.setConnectTimeout( (int) maxConnectionInMs );
		factory.setReadTimeout( (int) maxConnectionInMs );

		RestTemplate restTemplate = new RestTemplate( factory );

		return restTemplate;
	}
}
