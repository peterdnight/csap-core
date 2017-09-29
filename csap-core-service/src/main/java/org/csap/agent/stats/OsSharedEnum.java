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
public enum OsSharedEnum {
	cpuCountAvg( "cpuCountAvg" ), memoryInMbAvg( "memoryInMbAvg" ), swapInMbAvg( "swapInMbAvg" ), totActivity( "totActivity" ),
		numberOfSamples("numberOfSamples"), totalUsrCpu("totalUsrCpu"), totalSysCpu("totalSysCpu"),
		totalMemFree("totalMemFree"), totalBufFree("totalBufFree"), totalIo("totalIo"),
		alertsCount("alertsCount"), totalLoad("totalLoad"), totalFiles("totalFiles"),
		threadsTotal("threadsTotal"), csapThreadsTotal("csapThreadsTotal"), fdTotal("fdTotal"),
		csapFdTotal("csapFdTotal"), socketTotal("socketTotal"), socketWaitTotal("socketWaitTotal"),
		totalDiskTestTime("totalDiskTestTime"),totalCpuTestTime("totalCpuTestTime"),
		totalIoReads("totalIoReads"),totalIoWrites("totalIoWrites") ;

	public String value;

	private OsSharedEnum(String value) {
		this.value = value;
	}

	static ObjectMapper jacksonMapper = new ObjectMapper();
	
	static public ObjectNode realTimeLabels() {

		ObjectNode labels = jacksonMapper.createObjectNode();

		labels.put("coresActive", "CPU Cores Busy") ;
		labels.put("usrCpu", "Usr Cpu (mpstat)") ;
		labels.put("sysCpu", "System Cpu (mpstat)") ;
		labels.put("IO", "IO Wait (mpstat)") ;
		labels.put("load", "CPU load") ;
		labels.put("openFiles", "Files - /proc") ;
		labels.put("totalThreads", "Threads - All") ;
		labels.put("csapThreads", "Threads - CSAP Services") ;
		labels.put("totalFileDescriptors", "Files - lsof ALL") ;
		labels.put("csapFileDescriptors", "Files - lsof CSAP") ;
		labels.put("networkConns", "Sockets - wait state") ;
		labels.put("networkWait", "Sockets - wait state") ;
		labels.put("diskTest", "Disk Test Time") ;
		labels.put("cpuTest", "CPU Test Time") ;
		labels.put("ioReads", "Device Reads MB") ;
		labels.put("ioWrites", "Device Writes MB") ;
		
		return labels;
		
	}

	static public ObjectNode graphLabels() {

		ObjectNode labels = jacksonMapper.createObjectNode();

		for ( OsSharedEnum os : OsSharedEnum.values() ) {
			switch ( os ) {
				case cpuCountAvg:
					labels.put(os.value, "CPU Cores Available") ;
					break;
				case memoryInMbAvg:
					labels.put(os.value, "Memory Configured (GB)") ;
					break;
				case swapInMbAvg:
					labels.put(os.value, "Disk Swap (GB)") ;
					break;
				case totActivity:
					labels.put(os.value, "Eng User Activity") ;
					break;
				case numberOfSamples:
					labels.put(os.value, "Collection Count") ;
					break;
				case totalUsrCpu:
					labels.put(os.value, "Usr Cpu (mpstat)") ;
					break;
				case totalSysCpu:
					labels.put(os.value, "System Cpu (mpstat)") ;
					break;
				case totalMemFree:
					labels.put(os.value, "Memory Not Used (GB)") ;
					break;
				case totalBufFree:
					labels.put(os.value, "Memory Buffer Free") ;
					break;
				case totalIo:
					labels.put(os.value, "IO Wait (mpstat)") ;
					break;
				case alertsCount:
					labels.put(os.value, "Host CPU Alerts") ;
					break;
				case totalLoad:
					labels.put(os.value, "Host Load") ;
					break;
				case totalFiles:
					labels.put(os.value, "Files - /proc") ;
					break;
				case threadsTotal:
					labels.put(os.value, "Threads - All") ;
					break;
				case csapThreadsTotal:
					labels.put(os.value, "Threads - CSAP Services") ;
					break;
				case fdTotal:
					labels.put(os.value, "Files - lsof ALL") ;
					break;
				case csapFdTotal:
					labels.put(os.value, "Files - lsof CSAP") ;
					break;
				case socketTotal:
					labels.put(os.value, "Sockets - open") ;
					break;
				case socketWaitTotal:
					labels.put(os.value, "Sockets - wait state") ;
					break;
				case totalDiskTestTime:
					labels.put(os.value, "Disk Test Time(s)") ;
					break;
				case totalCpuTestTime:
					labels.put(os.value, "CPU Test Time (s)") ;
					break;
				case totalIoReads:
					labels.put(os.value, "IO Reads (Mb)") ;
					break;
				case totalIoWrites:
					labels.put(os.value, "IO Writes (Mb)") ;
					break;
				default:
					throw new AssertionError( os.name() );
				
			}
		}
		
		return labels ;
	}
}


//				totalMemFree: "Memory Not Used (GB)",
//				fdTotal: "VM Open Files",
//				csapFdTotal: "Application Open Files",
//				totalUsrCpu: "Application CPU Percent",
//				totalSysCpu: "OS Kernel CPU Percent",
//				totalIo: "CPU Idle due to IO Wait"
			
