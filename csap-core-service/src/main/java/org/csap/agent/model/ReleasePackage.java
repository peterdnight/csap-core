package org.csap.agent.model;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.csap.agent.linux.HostInfo;
import org.csap.agent.services.SourceControlManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *
 * In Memory Model for parsed capability definitions
 *
 *
 * @author someDeveloper
 *
 *
 * @see <a href="https://github.com/csap-platform/csap-core/wiki#updateRefDomain+Model"> CS -AP Definition Model in reference guide
 * </a>
 *
 *
 * @see <a href="doc-files/csagent.jpg"> Click to enlarge
 * <IMG width=300 SRC="doc-files/csagent.jpg"></a>
 *
 * @see <a href="doc-files/spring.jpg" > Click to enlarge
 * <IMG width=300 SRC="doc-files/modelDocs.jpg"></a>
 *
 */
public class ReleasePackage {

	final Logger logger = LoggerFactory.getLogger( ReleasePackage.class );

	public final static String GLOBAL_PACKAGE = "global";

	public ReleasePackage(ObjectNode jsonModelDefinition) {
		this.jsonModelDefinition = jsonModelDefinition;
	}

	private String capabilityAjpSecret = "dummySecretYouShouldUpdateClusterDef";

	private String capabilityName = "DefaultName";

	private String capabilityScm = "NoSourceControl";

	private String capabilityScmType = SourceControlManager.ScmProvider.svn.key;
	private String capabilityScmBranch = "trunk";

	public String getCapabilityScmBranch () {
		return capabilityScmBranch;
	}

	public void setCapabilityScmBranch ( String capabilityScmBranch ) {
		this.capabilityScmBranch = capabilityScmBranch;
	}

	public String getCapabilityScmType () {
		return capabilityScmType;
	}

	public void setCapabilityScmType ( String capabilityScmType ) {
		this.capabilityScmType = capabilityScmType;
	}

	private ObjectMapper jacksonMapper = new ObjectMapper();
	private ObjectNode infrastructure = jacksonMapper.createObjectNode();

	public String getArchitect() {
		return architect;
	}

	// Test urls for connecting to httpds to view status on ui
	private List<String> httpdTestUrls = new ArrayList<String>();

	public List<String> getHttpdTestUrls() {
		return httpdTestUrls;
	}

	public void setArchitect(String architect) {
		this.architect = architect;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	private String architect = "UpdateArchitectName";

	private String emailNotifications = "support@notConfigured.com";

	public String getEmailNotifications() {
		if ( emailNotifications.equals( "support@notConfigured.com" ) || emailNotifications.length() == 0 ) {
			return null;
		}
		return emailNotifications;
	}

	public void setEmailNotifications(String emailNotifications) {
		this.emailNotifications = emailNotifications;
	}

	private String description = "Update the package description.";

	private String releasePackageName = GLOBAL_PACKAGE;
	private String releasePackageFileName = "";

	public String getReleasePackageName() {
		return releasePackageName;
	}

	public String getReleasePackageFileName() {
		return releasePackageFileName;
	}

	public File getReleaseFile(File applicationDefinition) {
		StringBuilder releaseFile = new StringBuilder( releasePackageFileName );
		releaseFile.insert( releaseFile.indexOf( "." ), "-release" );
		// Use path relative to parent to determin child
		File releasePackageFile = new File( applicationDefinition.getAbsoluteFile().getParent(),
				releaseFile.toString() );
		return releasePackageFile;
	}

	public void setReleaseInfo(String releasePackageName, String releasePackageFileName) {
		this.releasePackageName = releasePackageName;
		this.releasePackageFileName = releasePackageFileName;
	}

	private ReleasePackage allPackagesModel = this;

	public ReleasePackage getAllPackagesModel() {
		return allPackagesModel;
	}

	public void setAllPackagesModel(ReleasePackage allPackagesModel) {
		this.allPackagesModel = allPackagesModel;
	}

	// We look for the current host in ALL models, and assign the active model
	// based on where it is found
	private ReleasePackage activeModel = this;

	public ReleasePackage getActiveModel() {
		return activeModel;
	}

	public void setActiveModel(ReleasePackage activeModel) {
		this.activeModel = activeModel;
	}

	private TreeMap<String, ReleasePackage> releaseModelMap = new TreeMap<String, ReleasePackage>();

	// Children packages will get this from the parent
	// public void setReleaseModelMap(TreeMap<String, CapabilityDataModel>
	// releaseModelMap) {
	// this.releaseModelMap = releaseModelMap;
	// }
	public void setReleaseModelMap(ReleasePackage model) {
		this.releaseModelMap = model.getReleaseModelMap();
	}

	private TreeMap<String, ReleasePackage> getReleaseModelMap() {
		return releaseModelMap;
	}

	public void putReleasePackage(String packageName, ReleasePackage model) {
		releaseModelMap.put( packageName, model );
	}

	public ReleasePackage getReleasePackage(String packageName) {
		return releaseModelMap.get( packageName );
	}

	public ReleasePackage getReleasePackageForHost(String hostName) {

		if ( logger.isDebugEnabled() ) {
			StringBuilder modelInfo = new StringBuilder( "\n\n Models for hosts:" );

			getReleasePackages()
					.forEach( model -> {
						modelInfo.append( "\n\t model: \t" + model.getReleasePackageName() + " hosts: " );
						modelInfo.append( "\t\t" + " hosts: " + model.getHostToAdminMap().keySet() );
					} );

			logger.debug( modelInfo.toString() );
		}

		return getReleasePackages()
				.filter( model -> model.getHostToAdminMap().containsKey( hostName ) )
				.findFirst()
				.get();
	}

	public ReleasePackage getModelForService(String serviceName) {

		if ( logger.isDebugEnabled() ) {
			getReleasePackages().forEach( model -> {
				logger.info( "Model: {}, services: {}", model.getReleasePackageName(), model.getServiceNamesInLifecycle() );
			} );
		}

		Optional<ReleasePackage> matchingPackage = getReleasePackages()
				.filter( model -> model.getServiceNamesInLifecycle().contains( serviceName ) )
				.findFirst();

		return matchingPackage.orElse( this );
	}

	public Stream<ReleasePackage> getReleasePackages() {
		return releaseModelMap.values().stream();
	}

	public Stream<String> getReleasePackageNames() {
		return releaseModelMap.keySet().stream();
	}

	public Stream<Entry<String, ReleasePackage>> getReleaseEntryStream() {
		return releaseModelMap.entrySet().stream();
	}

	public int getReleaseModelCount() {
		return releaseModelMap.size();
	}

	private JsonNode jsonModelDefinition = null;

	public void setJsonModelDefinition(JsonNode jsonModelDefinition) {
		this.jsonModelDefinition = jsonModelDefinition;
	}

	private TreeMap<String, String> clusterVersionToTypeMap = new TreeMap<String, String>();

	public void setClusterVersionToTypeMap(TreeMap<String, String> clusterVersionToTypeMap) {
		this.clusterVersionToTypeMap = clusterVersionToTypeMap;
	}

	public TreeMap<String, String> getClusterVersionToTypeMap() {
		return clusterVersionToTypeMap;
	}

	private TreeMap<String, ArrayList<String>> groupToVersionMap = new TreeMap<String, ArrayList<String>>();

	private Map<String, String> helpMenuUrlMap = new LinkedHashMap<String, String>();

	private TreeMap<String, ServiceInstance> hostToAdminMap = new TreeMap<String, ServiceInstance>();

	private TreeMap<String, ArrayList<ServiceInstance>> hostToConfigMap = new TreeMap<String, ArrayList<ServiceInstance>>();

	private TreeMap<String, ArrayList<String>> lcGroupVerToHostMap = new TreeMap<String, ArrayList<String>>();

	private TreeMap<String, Map<String, ArrayList<String>>> lcGroupVerToJvmMap = new TreeMap<String, Map<String, ArrayList<String>>>();
	private TreeMap<String, ArrayList<String>> lcGroupVerToOsMap = new TreeMap<String, ArrayList<String>>();

	private ArrayList<String> lifecycleList = new ArrayList<String>();

	private TreeMap<String, ArrayList<String>> lifeCycleToGroupMap = new TreeMap<String, ArrayList<String>>();

	private TreeMap<String, ArrayList<HostInfo>> lifeCycleToHostInfoMap = new TreeMap<String, ArrayList<HostInfo>>();
	private TreeMap<String, ArrayList<String>> lifeCycleToHostMap = new TreeMap<String, ArrayList<String>>();

	private TreeMap<String, LifeCycleSettings> lifeToMetaDataMap = new TreeMap<String, LifeCycleSettings>();

	// All Service instances, All lifecycles
	private TreeMap<String, ArrayList<ServiceInstance>> serviceNameToAllInstancesMap = new TreeMap<String, ArrayList<ServiceInstance>>();

	// Service Instances for Current Lifecycles
	private TreeMap<String, List<ServiceInstance>> serviceNameToLifeInstancesMap = new TreeMap<String, List<ServiceInstance>>();

	private String defaultMavenRepo;

	public String getDefaultMavenRepo() {
		return defaultMavenRepo;
	}

	public void setDefaultMavenRepo(String defaultMavenRepo) {
		this.defaultMavenRepo = defaultMavenRepo;
	}

	private ArrayList<String> versionList = new ArrayList<String>();

	public String getCapabilityAjpSecret() {
		return capabilityAjpSecret;
	}

	public String getName() {
		return capabilityName;
	}

	public String getSourceLocation() {
		return capabilityScm;
	}

	public JsonNode getJsonModelDefinition() {
		return jsonModelDefinition;
	}

	public JsonNode getServiceDefinition(String serviceName) {
		JsonNode serviceNode = jsonModelDefinition.at( DefinitionParser.buildServicePtr( serviceName, true ) );

		if ( serviceNode.isMissingNode() ) {
			serviceNode = jsonModelDefinition.at( DefinitionParser.buildServicePtr( serviceName, false ) );
		}
		return serviceNode;
	}

	public TreeMap<String, ArrayList<String>> getGroupToVersionMap() {
		return groupToVersionMap;
	}

	public Map<String, String> getHelpMenuUrlMap() {
		return helpMenuUrlMap;
	}

	public TreeMap<String, ServiceInstance> getHostToAdminMap() {
		return hostToAdminMap;
	}

	public Stream<String> getAdminHostNameStream() {
		return hostToAdminMap.keySet().stream();
	}

	public TreeMap<String, ArrayList<ServiceInstance>> getHostToConfigMap() {
		return hostToConfigMap;
	}

	public Stream<ServiceInstance> getServicesOnHost(String hostName) {
		return getHostToConfigMap().get( hostName ).stream();
	}

	public Map<String, List<String>> getClustersToHostMap() {

		Map<String, List<String>> modelClustersMap = new HashMap<String, List<String>>();

		String vmLifeCycle = Application.getCurrentLifeCycle();

		logger.debug( "csapApp.getActiveModel().getLifeCycleToGroupMap(): {}",
				getLifeCycleToGroupMap() );

		for ( String cluster : getLifeCycleToGroupMap()
				.get( vmLifeCycle ) ) {

			// logger.info("cluster: " + cluster);
			for ( String version : getGroupToVersionMap()
					.get( vmLifeCycle + cluster ) ) {
				String label = cluster + "-" + version;
				ArrayList<String> clusterHosts = getLcGroupVerToHostMap().get(
						vmLifeCycle + cluster + version );
				if ( clusterHosts == null ) {
					continue;
				}

				ArrayList<String> hostList = new ArrayList<String>();
				hostList.addAll( clusterHosts );
				modelClustersMap.put( label, hostList );

			}
		}

		return modelClustersMap;

	}

	public Map<String, List<String>> getClustersToServicesMap() {

		Map<String, List<String>> modelClustersMap = new HashMap<String, List<String>>();

		String vmLifeCycle = Application.getCurrentLifeCycle();

		logger.debug( "csapApp.getActiveModel().getLifeCycleToGroupMap(): {}",
				getLifeCycleToGroupMap() );

		for ( String cluster : getLifeCycleToGroupMap()
				.get( vmLifeCycle ) ) {

			// logger.info("cluster: " + cluster);
			for ( String version : getGroupToVersionMap()
					.get( vmLifeCycle + cluster ) ) {
				String clusterLabel = cluster + "-" + version;

				ArrayList<String> serviceList = new ArrayList<String>();
				// jvms
				Map<String, ArrayList<String>> clusterServices = getLcGroupVerToJvmMap().get(
						vmLifeCycle + cluster + version );
				if ( clusterServices != null ) {
					serviceList.addAll( clusterServices.keySet() );
				}

				// OS processes
				ArrayList<String> clusterOs = getLcGroupVerToOsMap().get(
						vmLifeCycle + cluster + version );
				if ( clusterOs != null ) {
					serviceList.addAll( clusterOs );
				}

				if ( serviceList.size() > 0 ) {
					modelClustersMap.put( clusterLabel, serviceList );
				}

			}
		}

		return modelClustersMap;

	}

	public TreeMap<String, ArrayList<String>> getLcGroupVerToHostMap() {
		return lcGroupVerToHostMap;
	}

	public TreeMap<String, Map<String, ArrayList<String>>> getLcGroupVerToJvmMap() {
		return lcGroupVerToJvmMap;
	}
	
	public Set<String> findServiceNamesInLifecycle( String life) {
		
		Set<String> serviceNames = new HashSet<>() ;
		getLcGroupVerToJvmMap().keySet().forEach( lc -> {
			if ( lc.startsWith( life)) {
				serviceNames.addAll( getLcGroupVerToJvmMap().get( lc).keySet() ) ;
			}
		}) ;
		
		getLcGroupVerToOsMap().keySet().forEach( lc -> {
			if ( lc.startsWith( life)  ) {
				serviceNames.addAll( getLcGroupVerToOsMap().get( lc)) ;
			}
		}) ;
		
		return serviceNames ; 
		
	}

	public TreeMap<String, ArrayList<String>> getLcGroupVerToOsMap() {
		return lcGroupVerToOsMap;
	}

	public ArrayList<String> getLifecycleList() {
		return lifecycleList;
	}

	public TreeMap<String, ArrayList<String>> getLifeCycleToGroupMap() {
		return lifeCycleToGroupMap;
	}

	public TreeMap<String, ArrayList<HostInfo>> getLifeCycleToHostInfoMap() {
		return lifeCycleToHostInfoMap;
	}

	public TreeMap<String, ArrayList<String>> getLifeCycleToHostMap() {
		return lifeCycleToHostMap;
	}

	public Stream<String> getHostNamesInCurrentLcStream() {
		if ( lifeCycleToHostMap.get( Application.getCurrentLifeCycle() ) == null ) {
			return (new ArrayList<String>()).stream();
		}
		return lifeCycleToHostMap.get( Application.getCurrentLifeCycle() ).stream();
	}

	public List<String> getHostsCurrentLc() {
		return lifeCycleToHostMap.get( Application.getCurrentLifeCycle() );
	}

	public TreeMap<String, LifeCycleSettings> getLifeToMetaDataMap() {
		return lifeToMetaDataMap;
	}

	public Map<String, ArrayList<ServiceInstance>> getServiceToAllInstancesMap() {
		return serviceNameToAllInstancesMap;
	}

	public Stream<String> getServiceNameStream() {
		return serviceNameToAllInstancesMap.keySet().stream();
	}
	

	public Set<String> getServiceNamesInLifecycle() {
		return serviceNameToLifeInstancesMap.keySet();
	}

	public TreeMap<String, List<ServiceInstance>> serviceInstancesInCurrentLifeByName() {
		return serviceNameToLifeInstancesMap;
	}

	public Stream<Entry<String, List<ServiceInstance>>> getServiceConfigStreamInCurrentLC() {
		return serviceInstancesInCurrentLifeByName().entrySet().stream();
	}

	/*
	 * gets instances in current lifecycle. Note that empty stream can be
	 * returned.
	 */
	public Stream<ServiceInstance> getServiceInstances(String serviceName) {

		// Return an EmptyStream.
		if ( serviceInstancesInCurrentLifeByName().get( serviceName ) == null ) {
			return (new ArrayList<ServiceInstance>()).stream();
		}

		return serviceInstancesInCurrentLifeByName().get( serviceName ).stream();
	}

	public Stream<ServiceInstance> getServiceInstancesInAllLifecycles(String serviceName) {

		// Return an EmptyStream.
		if ( getServiceToAllInstancesMap().get( serviceName ) == null ) {
			return (new ArrayList<ServiceInstance>()).stream();
		}

		return getServiceToAllInstancesMap().get( serviceName ).stream();
	}

	/**
	 * Get peer instances of the specified service in current lifecycle
	 *
	 * @param serviceName
	 * @return
	 */
	public List<ServiceInstance> getServicePeers(String serviceName) {

		logger.debug( "{} instances: {}", serviceName,
				getServiceInstances( serviceName )
						.filter( instance -> !instance.getHostName().equals( Application.getHOST_NAME() ) )
						.collect( Collectors.toList() ).size() );

		return getServiceInstances( serviceName )
				.filter( instance -> !instance.getHostName().equals( Application.getHOST_NAME() ) )
				.collect( Collectors.toList() );
	}

	public List<String> findOtherHostsForService(String serviceName) {

		logger.debug( "{} instances: {}", serviceName,
				getServiceInstances( serviceName )
						.filter( instance -> !instance.getHostName().equals( Application.getHOST_NAME() ) )
						.collect( Collectors.toList() ).size() );

		return getServiceInstances( serviceName )
				.filter( instance -> !instance.getHostName().equals( Application.getHOST_NAME() ) )
				.map( ServiceInstance::getHostName )
				.distinct()
				.collect( Collectors.toList() );
	}

	/**
	 * gets hosts in current life cycle
	 *
	 * @param serviceName
	 * @return
	 */
	public List<String> findHostsForService(String serviceName) {

		logger.debug( " looking for: {} in: {} ", serviceName, serviceInstancesInCurrentLifeByName().keySet() );

		if ( !serviceInstancesInCurrentLifeByName().containsKey( serviceName ) ) {
			return new ArrayList<>();
		}

		return serviceInstancesInCurrentLifeByName()
				.get( serviceName ).stream()
				.map( ServiceInstance::getHostName )
				.distinct()
				.collect( Collectors.toList() );
	}

	public int getInstanceTotalCountInCurrentLC() {

		int instances = (int) getServiceConfigStreamInCurrentLC()
				.flatMap( serviceEntry -> serviceEntry.getValue().stream() )
				.count();

		return instances;
	}

	public ObjectNode getInstanceCountInCurrentLC() {

		ObjectNode resultNode = jacksonMapper.createObjectNode();

		ArrayNode instanceCount = jacksonMapper.createArrayNode();

		serviceNameToLifeInstancesMap
				.entrySet()
				.stream()
				.map( serviceListEntry -> {
					String service = serviceListEntry.getKey();
					ObjectNode serviceNode = jacksonMapper.createObjectNode();
					serviceNode.put( "serviceName", service );
					serviceNode.put( "count", serviceListEntry.getValue().size() );

					boolean hasCustomJmx = false;

					if ( serviceListEntry.getValue().size() >= 1 ) {
						ServiceInstance firstInstance = serviceListEntry.getValue().get( 0 );

						serviceNode.put( "type", firstInstance.getServerType() );
						serviceNode.put( "script", firstInstance.isScript() );
						serviceNode.put( "cluster", firstInstance.getCluster() );
						if ( firstInstance.getPerformanceConfiguration() != null ) {
							hasCustomJmx = true;
						}
					}
					serviceNode.put( "hasCustom", hasCustomJmx );

					return serviceNode;
				} )
				.forEach( serviceNode -> instanceCount.add( serviceNode ) );

		resultNode.put( "total", getInstanceTotalCountInCurrentLC() );
		resultNode.set( "instanceCount", instanceCount );

		return resultNode;
	}

	public ArrayList<String> getVersionList() {
		return versionList;
	}

	public void setCapabilityAjpSecret(String capabilityAjpSecret) {
		this.capabilityAjpSecret = capabilityAjpSecret;
	}

	public void setCapabilityName(String capabilityName) {
		this.capabilityName = capabilityName;
	}

	public void setCapabilityScm(String capabilityScm) {
		this.capabilityScm = capabilityScm;
	}

	/**
	 * Updates the globalModel with the contents of all the sub packages
	 *
	 * @param testGlobalModel
	 */
	protected void generateAllPackageModel(String applicationName) {

		ReleasePackage allPackageDataModel = new ReleasePackage(
				(ObjectNode) getJsonModelDefinition() );

		allPackageDataModel.setReleaseInfo( Application.ALL_PACKAGES, applicationName );

		allPackageDataModel.setReleaseModelMap( this );

		getReleasePackages()
				.forEach( model -> loadModelIntoAllModel( model, allPackageDataModel ) );
		// Finally add the all model
		// testGlobalModel.putReleasePackage(allPackageDataModel.getReleasePackageName(),
		// allPackageDataModel);
		setAllPackagesModel( allPackageDataModel );

	}

	private void loadModelIntoAllModel(ReleasePackage model, ReleasePackage allPackageDataModel) {

		allPackageDataModel.getGroupToVersionMap().putAll( model.getGroupToVersionMap() );
		allPackageDataModel.getHostToAdminMap().putAll( model.getHostToAdminMap() );
		allPackageDataModel.getHostToConfigMap().putAll( model.getHostToConfigMap() );
		allPackageDataModel.getLcGroupVerToHostMap().putAll( model.getLcGroupVerToHostMap() );
		allPackageDataModel.getLcGroupVerToJvmMap().putAll( model.getLcGroupVerToJvmMap() );
		allPackageDataModel.getLcGroupVerToOsMap().putAll( model.getLcGroupVerToOsMap() );
		allPackageDataModel.getLifecycleList().addAll( model.getLifecycleList() );
		// allPackageDataModel.getLifeCycleToGroupMap().putAll(model.getLifeCycleToGroupMap());

		for ( String lc : model.getLifeCycleToGroupMap().keySet() ) {
			logger.debug( "Adding to global groups: {}", model.getLifeCycleToGroupMap().get( lc ) );

			if ( !allPackageDataModel.getLifeCycleToGroupMap().containsKey( lc ) ) {
				allPackageDataModel.getLifeCycleToGroupMap().put( lc,
						new ArrayList<String>() );
			}
			allPackageDataModel.getLifeCycleToGroupMap().get( lc )
					.addAll( model.getLifeCycleToGroupMap().get( lc ) );
		}
		logger.debug( "All Model groups: {}", allPackageDataModel.getLifeCycleToGroupMap() );
		// allPackageDataModel.getLifeCycleToHostInfoMap().putAll(
		// model.getLifeCycleToHostInfoMap());

		for ( String lc : model.getLifeCycleToHostInfoMap().keySet() ) {

			if ( !allPackageDataModel.getLifeCycleToHostInfoMap().containsKey( lc ) ) {
				allPackageDataModel.getLifeCycleToHostInfoMap().put( lc,
						new ArrayList<HostInfo>() );
			}
			allPackageDataModel.getLifeCycleToHostInfoMap().get( lc )
					.addAll( model.getLifeCycleToHostInfoMap().get( lc ) );
		}

		// allPackageDataModel.getLifeCycleToHostMap().putAll(
		// model.getLifeCycleToHostMap() );
		for ( String lc : model.getLifeCycleToHostMap().keySet() ) {

			if ( !allPackageDataModel.getLifeCycleToHostMap().containsKey( lc ) ) {
				allPackageDataModel.getLifeCycleToHostMap().put( lc, new ArrayList<String>() );
			}
			allPackageDataModel.getLifeCycleToHostMap().get( lc )
					.addAll( model.getLifeCycleToHostMap().get( lc ) );
		}

		// allPackageDataModel.getSvcToConfigMap().putAll(
		// model.getSvcToConfigMap() );
		for ( String serviceName : model.getServiceToAllInstancesMap().keySet() ) {

			if ( !allPackageDataModel.getServiceToAllInstancesMap().containsKey( serviceName ) ) {
				allPackageDataModel.getServiceToAllInstancesMap().put( serviceName,
						new ArrayList<ServiceInstance>() );
			}
			allPackageDataModel.getServiceToAllInstancesMap().get( serviceName )
					.addAll( model.getServiceToAllInstancesMap().get( serviceName ) );
		}

		// allPackageDataModel.getSvcToConfigMapCurrentLC().putAll(
		// model.getSvcToConfigMapCurrentLC() ); fff
		for ( String serviceName : model.serviceInstancesInCurrentLifeByName().keySet() ) {

			if ( !allPackageDataModel.serviceInstancesInCurrentLifeByName().containsKey( serviceName ) ) {
				allPackageDataModel.serviceInstancesInCurrentLifeByName().put( serviceName,
						new ArrayList<ServiceInstance>() );
			}
			allPackageDataModel.serviceInstancesInCurrentLifeByName().get( serviceName )
					.addAll( model.serviceInstancesInCurrentLifeByName().get( serviceName ) );
		}

	}

	@Override
	public String toString() {
		return "CapabilityDataModel [capabilityAjpSecret=" + capabilityAjpSecret + ", capabilityName=" + capabilityName
				+ ", capabilityScm=" + capabilityScm + ", httpdTestUrls="
				+ httpdTestUrls + ", architect=" + architect + ", description=" + description + ", releasePackageName="
				+ releasePackageName + ", releasePackageFileName=" + releasePackageFileName
				+ ", clusterVersionToTypeMap=" + clusterVersionToTypeMap + ", groupToVersionMap=" + groupToVersionMap
				+ ", helpMenuUrlMap=" + helpMenuUrlMap + ", lifecycleList=" + lifecycleList
				+ ", defaultMavenRepo=" + defaultMavenRepo + ", versionList="
				+ versionList + "]";
	}

	/**
	 * @return the infrastructure
	 */
	public ObjectNode getInfrastructure() {
		return infrastructure;
	}

	public String getInfraProvider() {

		JsonNode item = getInfrastructure().get( "provider" );
		if ( item != null && item.isTextual() ) {
			return item.asText();
		}
		return "Not Configured";
	}

	public String getInfraAddHost() {

		JsonNode item = getInfrastructure().get( "addHost" );
		if ( item != null && item.isTextual() ) {
			return item.asText();
		}
		return "Not Configured";
	}

	public String getInfraCatalog() {

		JsonNode item = getInfrastructure().get( "catalog" );
		if ( item != null && item.isTextual() ) {
			return item.asText();
		}
		return "Not Configured";
	}

	/**
	 * @param infrastructure the infrastructure to set
	 */
	public void setInfrastructure(ObjectNode infrastructure) {
		this.infrastructure = infrastructure;
	}

}
