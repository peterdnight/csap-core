package org.csap.agent.model;

import java.util.HashMap;

import com.fasterxml.jackson.databind.JsonNode;

public enum ClusterPartition {

	ENTERPRISE( "multiVm", "Enterprise Loadbalancing" ),
	SHARED_NOTHING( "singleVmPartition", "Shared Nothing" ),
	MULTI_SHARED_NOTHING( "multiVmPartition", "Shared Nothing (Multi host)" ),
	unknown( "unknown", "Unknown" );

	private String json;
	private String description;

	private ClusterPartition( String json, String description ) {
		this.json = json;
		this.description = description;

	}

	public String getJson () {
		return json;
	}

	public String getDescription () {
		return description;
	}

	public static ClusterPartition getPartitionType ( JsonNode node ) {

		for ( ClusterPartition type : values() ) {

			if ( node.has( type.getJson() ) ) {
				return type;
			}

		}

		// SNTC still has some none updated - once removed this can be removed
		if ( node.has( "version" ) ) {
			if ( (node.path( "version" ).findValue( "vdcImage" ) != null)
					&& node.path( "version" ).findValue( "vdcImage" ).asText().toLowerCase()
						.contains( "factory" ) ) {
				return SHARED_NOTHING;
			} else {
				return ENTERPRISE ;
			}
		}
		return ClusterPartition.unknown;

	}
	


	public static HashMap<String,String> clusterEntries (  ) {
		
		HashMap<String,String> clusterMap = new HashMap<>() ;

		for ( ClusterPartition type : values() ) {

			if ( type == unknown ) continue;
			clusterMap.put( type.getJson(), type.getDescription() ) ;

		}


		return clusterMap;

	}
}
