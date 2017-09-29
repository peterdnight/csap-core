package org.csap.agent.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.csap.agent.CSAP;
import org.csap.agent.model.Application.FileToken;
import org.csap.agent.services.HostKeys;
import org.csap.agent.stats.JmxCommonEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *
 * metadata associated with each service
 *
 *
 *
 *
 * @author someDeveloper
 *
 *         JsonIgnoreProperties: provides backwards compatability when instance
 *         metrics are updated
 *
 */
@JsonIgnoreProperties ( ignoreUnknown = true )
public class ServiceInstance extends ServiceBaseParser {

	private static final String CPU_AUTO_MODE_TO_TRIGGER_HOUR_GLASS = "AUTO";

	final Logger logger = LoggerFactory.getLogger( ServiceInstance.class );

	public final static String INACTIVE = "-";

	private String cpuUtil = INACTIVE; // flag for inactive process
	private List<String> pid = new ArrayList<String>();
	private int rssMemory = 0;
	private int virtualMemory = 0;

	private boolean autoKillInProgress = false;
	// Collected
	private long jmxHeartbeatMs = 0;
	private int numTomcatConns = 0;
	private int jvmThreadCount = 0;
	private int diskReadKb = 0;
	private int diskWriteKb = 0;
	private String topCpu = INACTIVE; // flag for inactive process
	private int diskUsageInMb = 0;
	private int fileCount = 0;
	private int socketCount = 0;
	private int threadCount = 0;
	private ObjectNode healthReportCollected = null;

	// service stopped by a user - avoids alerts
	private boolean userStopped = false;

	// Scans by Application services
	private String warDate = "";
	private ArrayNode eolJars = jacksonMapper.createArrayNode();
	private String runHeap = "";
	private boolean fileSystemScanRequired = true;

	/**
	 * used by deploy manager to poll service state from hosts
	 *
	 * reloaded in admin ServiceInstance serviceInstance =
	 * jacksonMapper.readValue( serviceNode.get( serviceInstanceName
	 * ).traverse(), ServiceInstance.class );
	 *
	 * @return
	 */
	@JsonIgnore
	public ObjectNode getRuntime () {

		ObjectNode runStatus = jacksonMapper.createObjectNode();

		runStatus.put( "serviceName", getServiceName() );
		runStatus.put( "port", getPort() );
		runStatus.put( "servletThreadCount", getServletThreadCount() );
		runStatus.put( "servletAccept", getServletAccept() );
		runStatus.put( JmxCommonEnum.jvmThreadCount.value, getJvmThreadCount() );
		runStatus.put( JmxCommonEnum.httpConnections.value, getNumTomcatConns() );
		runStatus.put( ServiceAlertsEnum.JAVA_HEARTBEAT, getJmxHeartbeatMs() );
		runStatus.put( "servletMaxConnections", getServletMaxConnections() );
		runStatus.put( "servletTimeoutMs", getServletTimeoutMs() );
		runStatus.put( ServiceAlertsEnum.diskSpace.value, getDiskUsageInMb() );
		runStatus.put( USER_STOP, isUserStopped() );
		runStatus.put( "cpuUtil", getCpuUtil() );
		runStatus.put( ServiceAlertsEnum.cpu.value, getTopCpu() );
		runStatus.put( "currentProcessPriority", getCurrentProcessPriority() );
		runStatus.put( "runHeap", getRunHeap() );
		runStatus.put( "scmVersion", getScmVersion() );
		runStatus.put( "warDate", getWarDate() );
		runStatus.set( "eolJars", getEolJars() );

		runStatus.put( "serverType", getServerQualifedType() );
		runStatus.put( "user", getUser() );

		runStatus.put( "deployedArtifacts", getDeployedArtifacts() );
		runStatus.put( ServiceAlertsEnum.threads.value,
			Integer.toString( getThreadCount() ) );
		runStatus.put( ServiceAlertsEnum.openFileHandles.value, Integer.toString( getFileCount() ) );
		runStatus.put( ServiceAlertsEnum.sockets.value, Integer.toString( getSocketCount() ) );

		runStatus.put( "diskReadKb", Integer.toString( getDiskReadKb() ) );
		runStatus.put( ServiceAlertsEnum.diskWriteRate.value, Integer.toString( getDiskWriteKb() ) );

		runStatus.set( "pid", jacksonMapper.valueToTree( getPid() ) );

		runStatus.put( ServiceAlertsEnum.memory.value, Integer.toString( getRssMemory() ) );
		runStatus.put( "virtualMemory", Integer.toString( getVirtualMemory() ) );

		if ( isRemoteCollection() ) {
			runStatus.put( "deployedArtifacts", "Remote Managed" );
		}

		if ( isHealthReportConfigured() ) {
			// pass it forward to admin services for aggregation
			runStatus.set( HostKeys.healthReportCollected.jsonId, getHealthReportCollected() );
		}

		return runStatus;
	}

	public void setPid ( List<String> pid ) {
		// new Error().printStackTrace();
		this.pid = pid;
	}

	final public static String NO_PIDS = "-";

	public List<String> getPid () {

		if ( pid.size() == 0 ) {
			return Arrays.asList( NO_PIDS ); // flag for empty pids
		}
		return new ArrayList<String>( pid );
	}

	public String getPidsAsString () {

		StringBuffer result = new StringBuffer( "" );
		if ( pid.size() > 0 ) {
			pid.forEach( pid -> {
				if ( result.length() != 0 ) {
					result.append( " " );
				}
				if ( pid.equals( NO_PIDS ) ) {
					result.append( "noMatches" );
				} else {

					result.append( pid );
				}
			} );
		}
		return result.toString();
	}

	public void addPid ( String pid ) {
		this.pid.add( pid );
	}

	public void setRssMemory ( int rssMemory ) {
		this.rssMemory = rssMemory;
	}

	public void addRssMemory ( String rssMemory ) {
		try {
			int val = Integer.parseInt( rssMemory );
			this.rssMemory += val;

		} catch (NumberFormatException e) {
			logger.error( " Failed to parse rssMemory:" + rssMemory, e );
		}
	}

	public void setVirtualMemory ( int virtualMemory ) {
		this.virtualMemory = virtualMemory;
	}

	public void addVirtualMemory ( String virtualMemory ) {
		try {
			int val = Integer.parseInt( virtualMemory );
			this.virtualMemory += val;

		} catch (NumberFormatException e) {
			logger.error( " Failed to parse virtualMemory:" + virtualMemory, e );
		}
	}

	public int getRssMemory () {
		return rssMemory;
	}

	public int getVirtualMemory () {
		return virtualMemory;
	}

	public int getNumTomcatConns () {
		return numTomcatConns;
	}

	public int getJvmThreadCount () {
		return jvmThreadCount;
	}

	public void setJvmThreadCount ( Long jvmThreadCount ) {
		this.jvmThreadCount = jvmThreadCount.intValue();
	}

	public long getJmxHeartbeatMs () {
		return jmxHeartbeatMs;
	}

	public void setJmxHeartbeatMs ( long jmxHeartbeat ) {
		this.jmxHeartbeatMs = jmxHeartbeat;
	}

	public void setNumTomcatConns ( Long numTomcatConns ) {
		this.numTomcatConns = numTomcatConns.intValue();
	}

	public int getDiskReadKb () {
		return diskReadKb;
	}

	public void setDiskReadKb ( int diskReadKb ) {
		this.diskReadKb = diskReadKb;
	}

	public int getDiskWriteKb () {
		return diskWriteKb;
	}

	public void setDiskWriteKb ( int diskWriteKb ) {
		this.diskWriteKb = diskWriteKb;
	}

	public void setCpuClean () {
		this.cpuUtil = "CLEAN";
	}

	/**
	 * service is fully started
	 * @return
	 */
	public boolean isRunning () {
		if ( isInactive()
				|| cpuUtil.equals( CPU_AUTO_MODE_TO_TRIGGER_HOUR_GLASS )
				|| cpuUtil.equals( "CLEAN" ) ) {

			return false;
		}
		return true;
	}

	public boolean isInactive () {
		return cpuUtil.equals( INACTIVE );
	}

	public static final String REMOTE_UTIL = "REMOTE";

	public String getCpuUtil () {

		if ( getCollectHost() != null ) {
			return REMOTE_UTIL;
		}

		return cpuUtil;
	}

	public void setCpuAuto () {
		this.cpuUtil = CPU_AUTO_MODE_TO_TRIGGER_HOUR_GLASS;
	}

	public void setCpuReset () {
		this.cpuUtil = "-";
	}

	public void setCpuUtil ( String collectedCpuUsage ) {
		if ( cpuUtil.equals( CPU_AUTO_MODE_TO_TRIGGER_HOUR_GLASS ) || cpuUtil.equals( "CLEAN" ) ) {
			logger.info( "{} Ignoring cpu: {}, service is in startup mode: {}", getServiceName_Port(), collectedCpuUsage, cpuUtil );
			return;
		}
		this.cpuUtil = collectedCpuUsage;
	}

	// Hook to add make cpu cumulative
	public void addCpuUtil ( String collectedCpuUsage ) {

		if ( cpuUtil.equals( CPU_AUTO_MODE_TO_TRIGGER_HOUR_GLASS ) || cpuUtil.equals( "CLEAN" ) ) {
			logger.debug( "Ignoring cpu: {}, service is in startup mode: {}", collectedCpuUsage, cpuUtil );
			return;
		}

		try {
			float currCpu = Float.valueOf( this.cpuUtil );
			float testCpu = Float.valueOf( collectedCpuUsage );
			this.cpuUtil = Float.toString( currCpu + testCpu );
			return;
		} catch (NumberFormatException e) {
			// logger.warn("Ignoring float arithmetic") ;
		}

		this.cpuUtil = replaceVariablesAndTrim( collectedCpuUsage );
	}

	public void setDiskUsageInMb ( int diskSpaceUsedInMb ) {
		this.diskUsageInMb = diskSpaceUsedInMb;
	}

	public void setDiskUsageInMb ( String diskSpaceUsedInMb ) {

		int latestCollection = 0;
		try {

			latestCollection = Integer.parseInt( diskSpaceUsedInMb.replaceAll( "[\\D]", "" ) );

		} catch (Exception e) {
			
			logger.info( "{} Failed parsing disk: {}, {}", getServiceName_Port(), diskSpaceUsedInMb, CSAP.getCsapFilteredStackTrace( e ) );
		}
		this.diskUsageInMb = this.diskUsageInMb + latestCollection;

		logger.debug( "{} Raw: {} Disk updated: {} ", getServiceName(), diskSpaceUsedInMb, diskUsageInMb );
	}

	public int getDiskUsageInMb () {
		return diskUsageInMb;
	}

	public String getTopCpu () {
		return topCpu;
	}

	public void setTopCpu ( String topCpu ) {
		this.topCpu = topCpu;
	}

	/**
	 * @return the socketCount
	 */
	public int getSocketCount () {
		return socketCount;
	}

	/**
	 * @return the fileCount
	 */
	public int getFileCount () {
		return fileCount;
	}

	public void setFileCount ( int fileCount ) {
		this.fileCount = fileCount;
	}

	// public void addFileCount(int fileCount) {
	// this.fileCount += fileCount;
	// }
	public void setSocketCount ( int socketCount ) {
		this.socketCount = socketCount;
	}

	public int getThreadCount () {
		return threadCount;
	}

	public void setThreadCount ( int threadCount ) {
		this.threadCount = threadCount;
	}

	public void addThreadCount ( String threadCount ) {
		try {
			int val = Integer.parseInt( threadCount );
			this.threadCount += val;

		} catch (NumberFormatException e) {
			logger.error( " Failed to parse threadCount:" + threadCount, e );
		}
	}

	public String getWarDate () {
		return warDate;
	}

	public void setWarDate ( String warDate ) {
		this.warDate = replaceVariablesAndTrim( warDate );
	}

	/**
	 * @return the userStopped
	 */
	public boolean isUserStopped () {
		return userStopped;
	}

	/**
	 * @param userStopped
	 *            the userStopped to set
	 */
	public void setUserStopped ( boolean userStopped ) {
		this.userStopped = userStopped;
	}

	/**
	 * @return the eolJars
	 */
	public ArrayNode getEolJars () {
		return eolJars;
	}

	/**
	 * @param eolJars
	 *            the eolJars to set
	 */
	public void setEolJars ( ArrayNode eolJars ) {
		this.eolJars = eolJars;
	}

	public String getRunHeap () {
		return runHeap;
	}

	public void setRunHeap ( String runHeap ) {
		this.runHeap = runHeap;
	}

	public void addRunHeap ( String runHeap ) {

		// Handle for processes with multiple heaps specified
		if ( this.runHeap.length() != 0 ) {
			this.runHeap += ",";
		}
		this.runHeap += runHeap;
	}

	/**
	 * @return the fileSystemScanRequired
	 */
	public boolean isFileSystemScanRequired () {
		return fileSystemScanRequired;
	}

	/**
	 * @param fileSystemScanRequired
	 *            the fileSystemScanRequired to set
	 */
	public void setFileSystemScanRequired ( boolean fileSystemScanRequired ) {
		this.fileSystemScanRequired = fileSystemScanRequired;
	}

	@Override
	public String toString () {
		return "ServiceInstance{" + "serviceNamePort=" + getServiceName_Port() + ", cpuUtil=" + cpuUtil + ", pid=" + pid
				+ ", rssMemory=" + rssMemory + ", virtualMemory=" + virtualMemory + ", jmxHeartbeatMs=" + jmxHeartbeatMs
				+ ", numTomcatConns=" + numTomcatConns + ", jvmThreadCount=" + jvmThreadCount + ", diskReadKb=" + diskReadKb
				+ ", diskWriteKb=" + diskWriteKb + ", topCpu=" + topCpu + ", diskUsageInMb=" + diskUsageInMb
				+ ", socketCount=" + socketCount + ", fileCount=" + fileCount + ", threadCount=" + threadCount
				+ ", userStopped=" + userStopped + ", warDate=" + warDate + ", eolJars=" + eolJars
				+ ", runHeap=" + runHeap + '}';
	}

	public ObjectNode getHealthReportCollected () {
		return healthReportCollected;
	}

	public void setHealthReportCollected ( ObjectNode healthReportCollected ) {
		this.healthReportCollected = healthReportCollected;
	}

	public boolean isAutoKillInProgress () {
		return autoKillInProgress;
	}

	public void setAutoKillInProgress ( boolean autoKillInProgress ) {
		this.autoKillInProgress = autoKillInProgress;
	}

}
