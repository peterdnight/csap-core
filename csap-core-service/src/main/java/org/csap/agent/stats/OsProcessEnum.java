/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.csap.agent.stats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *
 * @author someDeveloper
 */
public enum OsProcessEnum {
	topCpu( "topCpu" ), threadCount( "threadCount" ), fileCount( "fileCount" ), socketCount( "socketCount" ),
	rssMemory( "rssMemory" ), diskUsedInMb( "diskUtil" ), diskReadKb( "diskReadKb" ), diskWriteKb( "diskWriteKb" );

	public String value;

	private OsProcessEnum(String value) {
		this.value = value;
	}

	static ObjectMapper jacksonMapper = new ObjectMapper();

	static public ObjectNode graphLabels() {

		ObjectNode labels = jacksonMapper.createObjectNode();

		for ( OsProcessEnum os : OsProcessEnum.values() ) {
			switch ( os ) {
				case topCpu:
					labels.put( os.value, "Cpu (Top)" );
					break;
				case threadCount:
					labels.put( os.value, "Threads" );
					break;
				case fileCount:
					labels.put( os.value, "Open Files" );
					break;
				case socketCount:
					labels.put( os.value, "Open Sockets" );
					break;
				case rssMemory:
					labels.put( os.value, "Memory RSS (MB)" );
					break;
				case diskUsedInMb:
					labels.put( os.value, "Disk Usage (MB)" );
					break;
				case diskReadKb:
					labels.put( os.value, "Disk Reads (KB/s)" );
					break;
				case diskWriteKb:
					labels.put( os.value, "Disk Writes (KB/s)" );
					break;
				default:
					throw new AssertionError( os.name() );

			}
		}

		return labels;
	}
}

//				totalMemFree: "Memory Not Used (GB)",
//				fdTotal: "VM Open Files",
//				csapFdTotal: "Application Open Files",
//				totalUsrCpu: "Application CPU Percent",
//				totalSysCpu: "OS Kernel CPU Percent",
//				totalIo: "CPU Idle due to IO Wait"

