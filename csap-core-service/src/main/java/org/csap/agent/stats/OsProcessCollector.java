package org.csap.agent.stats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.csap.agent.CSAP;
import org.csap.agent.model.Application;
import org.csap.agent.model.ServiceInstance;
import org.csap.agent.services.OsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OsProcessCollector extends HostCollector implements Runnable {

	final Logger logger = LoggerFactory.getLogger( OsProcessCollector.class );

	public OsProcessCollector( Application manager,
			OsManager osManager, int intervalSeconds, boolean publishSummary ) {

		super( manager, osManager, intervalSeconds, publishSummary );

		timeStampArray = jacksonMapper.createArrayNode();
		totalCpuArray = jacksonMapper.createArrayNode();

		scheduleCollection( this );

	}

	public void testCollection () {
		logger.info( "\n\n ==================== Test =======================" );
		processLatestOsMetrics();
	}

	ArrayList<Long> timestamps = new ArrayList<Long>();

	Map<String, ObjectNode> servicesMapNodes = new HashMap<String, ObjectNode>();

	ArrayNode timeStampArray;
	ArrayNode totalCpuArray;

	ObjectMapper jacksonMapper = new ObjectMapper();



	@Override
	public void run () {

		try {
			// Step 1 - iteratate over latest metrics from top thread
			try {
				processLatestOsMetrics();
			} catch (Exception e1) {
				logger.error( "Failed processing latest metrics: {}", CSAP.getCsapFilteredStackTrace( e1 ) );
			}

			peformUploadIfNeeded();

		} catch (Exception e) {
			logger.error( "Exception in processing", e );
		}

		logger.debug( "Completed collection" );

	}

	private void processLatestOsMetrics () {

		logger.debug( "Getting updated service metrics" );

		ObjectNode hostRuntimeNode = (ObjectNode) osManager
			.getServiceMetrics( true );

		if ( hostRuntimeNode != null && hostRuntimeNode.has( "ps" ) ) {
			ObjectNode servicesNode = (ObjectNode) hostRuntimeNode.get( "ps" );

			timeStampArray.insert( 0, Long.toString( System.currentTimeMillis() ) );
			try {
				logger.debug( "servicesNode status: \n {}",
					jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString( servicesNode ) );
				// Use the the total CPU from top intervals
				totalCpuArray.insert( 0, osManager.getVmTotal() * osStats.getAvailableProcessors() );
			} catch (Exception e) {
				logger.error( "Failed getting total cpu", e );
				totalCpuArray.insert( 0, 0 );
			}

			if ( timeStampArray.size() > getInMemoryCacheSize() ) {
				timeStampArray.remove( timeStampArray.size() - 1 );
				totalCpuArray.remove( timeStampArray.size() - 1 );
			}

			servicesNode.fieldNames().forEachRemaining( serviceIdentifier -> {

				JsonNode serviceNode = servicesNode.get( serviceIdentifier );

				String serviceName = serviceIdentifier;
				String serverType = serviceNode.get( "serverType" ).asText();
				String cpuUtil = serviceNode.get( "cpuUtil" ).asText();

				// Skip scripts in collections
				if ( serverType == null || serverType.equals( ServiceInstance.SCRIPT ) || cpuUtil.equals( ServiceInstance.REMOTE_UTIL ) ) {
					return;
				}

				// Initialize the metric array if needed
				if ( !servicesMapNodes.containsKey( serviceName ) ) {
					logger.debug( "Adding cache for service: " + serviceName );
					ObjectNode serviceMetricNode = jacksonMapper
						.createObjectNode();
					serviceMetricNode.putArray( "timeStamp" );
					for ( OsProcessEnum os : OsProcessEnum.values() ) {
						String metricFullName = os.value + "_" + serviceName;
						serviceMetricNode.putArray( metricFullName );
					}
					servicesMapNodes.put( serviceName, serviceMetricNode );
				}

				ObjectNode serviceCacheNode = servicesMapNodes.get( serviceName );
				for ( OsProcessEnum os : OsProcessEnum.values() ) {
					String metric = os.value ;
					String metricFullName = metric + "_" + serviceName;
					ArrayNode metricArray = ((ArrayNode) serviceCacheNode
						.get( metricFullName ));

					// change rss to mb
					if ( !serviceNode.has( metric ) ) {
						metricArray.insert( 0, -1) ;
						logger.warn( "Did not find {} in {}", metric, serviceNode );
						continue;
					}
					if ( metric.equals( "rssMemory" ) ) {
						metricArray.insert( 0,
							serviceNode.get( metric ).asInt() / 1024 );
					} else {
						metricArray.insert( 0, serviceNode.get( metric ).asInt() );
					}
					if ( metricArray.size() > getInMemoryCacheSize() ) {
						metricArray.remove( metricArray.size() - 1 );
					}
				}

			} );

			cleanUpServiceCache( servicesNode );
		} else {
			logger.warn( "Runtime not availbable" );
		}
	}

	/**
	 *
	 *
	 */
	private void cleanUpServiceCache ( ObjectNode servicesNode ) {

		Iterator<String> keyIter = servicesMapNodes.keySet().iterator();
		while (keyIter.hasNext()) {
			String serviceName = keyIter.next();

			if ( !servicesNode.has( serviceName ) ) {
				keyIter.remove();
				logger.warn( "Removing service from monitor list: " + serviceName
						+ " , assumed due to definition update." );
			}
		}

	}

	public final static String PROCESS_METRICS_EVENT = METRICS_EVENT + "/process/";

	protected String uploadMetrics ( int iterationsBetweenAuditUploads ) {

		String result = "FAILED";

		try {

			String[] servicesArray = servicesMapNodes.keySet().toArray( new String[0] );
			ObjectNode metricSample = getCSVdata( true, servicesArray, iterationsBetweenAuditUploads, 0 );

			// new Event publisher - it checks if publish is enabled. If
			// services have been updated, then attributes are uploaded again
			if ( isCacheNeedsPublishing ) {
				// full upload. We could make call to event service to see if
				// they match...for now we do on restarts
				csapApplication.getEventClient().publishEvent( PROCESS_METRICS_EVENT + collectionIntervalSeconds + "/attributes",
					"Modified", null,
					attributeCacheJson );
				isCacheNeedsPublishing = false;
			}

			if ( processCorrellationAttributes == null ) {
				processCorrellationAttributes = jacksonMapper.createObjectNode();
				processCorrellationAttributes.put( "id", "service_" + collectionIntervalSeconds );
				processCorrellationAttributes.put( "hostName", Application.getHOST_NAME() );
			}
			// Send normalized data
			metricSample.set( "attributes", processCorrellationAttributes );
			csapApplication.getEventClient().publishEvent( PROCESS_METRICS_EVENT + collectionIntervalSeconds + "/data", "Upload", null,
				metricSample );

			publishSummaryReport( "process" );

		} catch (Exception e) {
			logger.error( "Failed upload", e );
			result = "Failed, Exception:"
					+ CSAP.getCsapFilteredStackTrace( e );
		}

		return result;

	}

	private ObjectNode processCorrellationAttributes = null;

	final OperatingSystemMXBean osStats = ManagementFactory
		.getOperatingSystemMXBean();

	final static int DEFAULT_SERVICES = 5;

	public ObjectNode getCollection (	int requestedSampleSize,
										int skipFirstItems,
										String... services ) {
		if ( services[0].toLowerCase().equals( ALL_SERVICES ) ) {
			services = servicesMapNodes.keySet().toArray( new String[0] );
		}
		return getCSVdata( false, services,
			requestedSampleSize, skipFirstItems );
	}

	public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue ( Map<K, V> map ) {
		List<Map.Entry<K, V>> list = new LinkedList<Map.Entry<K, V>>( map.entrySet() );
		Collections.sort( list, new Comparator<Map.Entry<K, V>>() {
			public int compare ( Map.Entry<K, V> o1, Map.Entry<K, V> o2 ) {
				return (o2.getValue()).compareTo( o1.getValue() );
			}
		} );

		Map<K, V> result = new LinkedHashMap<K, V>();
		for ( Map.Entry<K, V> entry : list ) {
			result.put( entry.getKey(), entry.getValue() );
		}
		return result;
	}

	public ObjectNode getCSVdata (	boolean isUpdateSummary, String[] serviceNameArray,
									int requestedSampleSize, int skipFirstItems, String... customArgs ) {

		List<String> serviceParams = new ArrayList<String>();
		if ( serviceNameArray == null ) {

			// if no services are specified - then ONLY the top 5 will be
			// returned

			Map<String, Integer> sumMap = new HashMap<String, Integer>();
			for ( String serviceName : servicesMapNodes.keySet() ) {

				ObjectNode serviceCacheNode = servicesMapNodes.get( serviceName );
				String metricFullName = "topCpu" + "_" + serviceName;
				ArrayNode cacheArray = (ArrayNode) serviceCacheNode
					.get( metricFullName );

				int summ = 0;
				for ( JsonNode node : cacheArray ) {
					summ += node.asInt();
				}
				sumMap.put( serviceName, summ );

			}

			Map<String, Integer> sumByValue = sortByValue( sumMap );

			logger.debug( "Sorted by value services: {}", sumByValue );

			int i = 0;
			for ( Map.Entry<String, Integer> entry : sumByValue.entrySet() ) {
				serviceParams.add( entry.getKey() );
				if ( ++i >= DEFAULT_SERVICES ) {
					break;
				}
			}
		} else {
			serviceParams = Arrays.asList( serviceNameArray );
		}

		// Support for autostripping of port when not found
		//logger.info( "{}", servicesMapNodes );
		ArrayList<String> servicesFilter = new ArrayList<>();
		for ( String serviceName : serviceParams ) {
			if ( serviceName.contains( "_" ) ) {
				ObjectNode serviceCacheNode = servicesMapNodes.get( serviceName );
				//logger.info( "serviceCacheNode: {}", serviceCacheNode );
				if (  serviceCacheNode == null ) {
					String nameOnly = serviceName.split( "_" )[0];
					//logger.info( "checking for {} in {}", nameOnly, servicesMapNodes.get( nameOnly ) );
					servicesFilter.add( nameOnly );
					
				}
			}
		}
		servicesFilter.addAll( serviceParams );
		// logger.info("numSamples: " + numSamples + " skipFirstItems: " +
		// skipFirstItems ) ;
		ObjectNode collectionData = jacksonMapper.createObjectNode();

		ObjectNode attributes = generateAttributes( servicesFilter, requestedSampleSize, skipFirstItems ) ;
		collectionData.set( "attributes", attributes );

		ObjectNode dataNode = collectionData.putObject( "data" );
		ArrayNode tsArray = dataNode.putArray( "timeStamp" );
		ArrayNode totalArray = dataNode.putArray( "totalCpu" );

		int numSamples = 0;
		if ( requestedSampleSize == -1 ) {
			tsArray.addAll( timeStampArray );
			totalArray.addAll( totalCpuArray );
		} else {
			for ( int i = 0; i < requestedSampleSize
					&& i < timeStampArray.size(); i++ ) {
				tsArray.add( timeStampArray.get( i ) );
				totalArray.add( totalCpuArray.get( i ) );
				numSamples++;
			}
		}

		//
		ObjectNode summaryJson = jacksonMapper.createObjectNode();

		for ( String serviceName : servicesFilter ) {
			//logger.info( "Searching for {}", serviceName );
			ObjectNode serviceCacheNode = servicesMapNodes.get( serviceName );
			ObjectNode serviceSummaryJson = summaryJson.putObject( serviceName );
			if ( serviceCacheNode != null ) {

				serviceSummaryJson.put( "numberOfSamples", numSamples );
				for ( OsProcessEnum os : OsProcessEnum.values() ) {

					String metric = os.value ;
					String metricFullName = metric + "_" + serviceName;
					ArrayNode metricArray = dataNode.putArray( metricFullName );
					ArrayNode cacheArray = (ArrayNode) serviceCacheNode.get( metricFullName );
					if ( requestedSampleSize == -1 ) {
						metricArray.addAll( cacheArray );
						// int metricTotal = 0;
						// for (JsonNode node : cacheArray) {
						// metricTotal+= node.asInt() ;
						// }
						// summaryJson.put(metricFullName + "Total",
						// metricTotal) ;
					} else {
						int metricTotal = 0;
						for ( int i = 0; i < requestedSampleSize
								&& i < cacheArray.size(); i++ ) {
							metricArray.add( cacheArray.get( i ) );
							metricTotal += cacheArray.get( i ).asInt();
						}
						serviceSummaryJson.put( metric, metricTotal );
					}
				}
			} else {
				( (ArrayNode) attributes.get( "servicesNotFound" )).add( serviceName );
			}
		}

		addSummary( summaryJson, isUpdateSummary );

		return collectionData;
	}

	// only cache for all services.
	private ObjectNode attributeCacheJson = null;
	private int serviceCacheHash = 0;

	private boolean isCacheNeedsPublishing = true;

	private ObjectNode generateAttributes (	List<String> servicesFilter,
											int requestedSampleSize, int skipFirstItems ) {

		// hash codes are used to identify when cluster definition has been
		// updated with new services
		int requestHashValue = servicesFilter.toString().hashCode();

		boolean isFullServicesRequest = servicesFilter.size() == servicesMapNodes.keySet().size();

		if ( attributeCacheJson != null && isFullServicesRequest ) {
			if ( requestHashValue == serviceCacheHash ) {

				logger.debug( "Using Cached attributes" );
				return attributeCacheJson;
			}
		}

		ObjectNode attributeJson = jacksonMapper.createObjectNode();

		attributeJson.put( "id", "service_" + collectionIntervalSeconds );
		attributeJson.put( "metricName", "Service Resources" );
		attributeJson.put( "description", "Contains service metrics" );
		attributeJson.put( "timezone", TIME_ZONE_OFFSET );
		attributeJson.put( "hostName", Application.getHOST_NAME() );
		attributeJson.put( "sampleInterval", collectionIntervalSeconds );
		attributeJson.put( "samplesRequested", requestedSampleSize );
		attributeJson.put( "samplesOffset", skipFirstItems );
		attributeJson.put( "currentTimeMillis", System.currentTimeMillis() );
		attributeJson.put( "cpuCount", osStats.getAvailableProcessors() );

		ArrayNode servicesAvailArray = attributeJson
			.putArray( "servicesAvailable" );
		for ( String serviceName : servicesMapNodes.keySet() ) {
			servicesAvailArray.add( serviceName );
		}
		ArrayNode servicesReqArray = attributeJson
			.putArray( "servicesRequested" );

		for ( String serviceName : servicesFilter ) {
			servicesReqArray.add( serviceName );
		}

		attributeJson
			.putArray( "servicesNotFound" );

		ObjectNode graphsObject = attributeJson.putObject( "graphs" );
		// ObjectNode titlesObject = attributeJson.putObject( "titles" );
		attributeJson.set( "titles", OsProcessEnum.graphLabels() );
		for ( OsProcessEnum os : OsProcessEnum.values() ) {

			String metric = os.value ;
			String desc = metric;
			// if ( desc.equals( "rssMemory" ) || desc.equals( "diskUtil" ) ) {
			// desc += "InMB";
			// }
			//
			// if ( desc.equals( "topCpu" ) ) {
			// desc = "Cpu_" + csapApplication.getTopInterval() + "s";
			// }

			ObjectNode resourceGraph = graphsObject.putObject( desc );

			for ( String serviceName : servicesFilter ) {
				String metricFullName = metric + "_" + serviceName;
				resourceGraph.put( metricFullName, serviceName );
			}

			// Added at the bottom so colors match across graphs
			if ( metric.equals( "topCpu" ) ) {
				resourceGraph.put( "totalCpu",
					"VM (OS+App)" );
				// resourceGraph.put("totalCpu",
				// "VM (Max: " + (osStats.getAvailableProcessors() * 100)
				// + ")");
			}
		}
		if ( isFullServicesRequest ) {
			attributeCacheJson = attributeJson;
			serviceCacheHash = requestHashValue;
			isCacheNeedsPublishing = true;
			logger.debug( "Updated attributes cache: \n {}", attributeCacheJson );
		}
		return attributeJson;
	}
}
