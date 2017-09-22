package org.csap.agent.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.csap.agent.CsapCoreService;
import org.csap.agent.CSAP;
import org.csap.agent.input.http.api.AgentApi;
import org.csap.agent.misc.CsapEventClient;
import org.csap.agent.model.Application;
import org.csap.agent.model.ServiceInstance;
import org.csap.security.SpringAuthCachingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class ServiceCommands {

	final Logger logger = LoggerFactory.getLogger( this.getClass() );

	@Autowired
	public ServiceCommands(
			Application csapApp,
			ServiceOsManager serviceManager,
			CsapEventClient csapEventClient ) {
		this.csapApp = csapApp;
		this.serviceOsManager = serviceManager;
		this.csapEventClient = csapEventClient;

	}

	private ServiceOsManager serviceOsManager;
	private CsapEventClient csapEventClient;
	private Application csapApp;

	ObjectMapper jacksonMapper = new ObjectMapper();

	public ObjectNode killRemoteRequests (	String auditUserid,
											List<String> services, List<String> hosts,
											String clean, String keepLogs,
											String apiUserid,
											String apiPass ) {
		ObjectNode resultJson = jacksonMapper.createObjectNode();

		ArrayNode servicesArray = resultJson.putArray( "services" );
		services.forEach( servicesArray::add );
		ArrayNode hostsArray = resultJson.putArray( "hosts" );
		hosts.forEach( hostsArray::add );

		if ( !Application.isJvmInManagerMode() ) {
			resultJson
				.put( "error",
					"refer to /api/deploy/host/* to deploy on hosts" );
		} else if ( hosts.size() == 0 || services.size() == 0 ) {
			resultJson
				.put( "error",
					"missing cluster parameter" );

		} else {
			MultiValueMap<String, String> stopParameters = new LinkedMultiValueMap<String, String>();
			stopParameters.set( "auditUserid", auditUserid );
			stopParameters.put( "services", services );
			stopParameters.put( "hosts", hosts );
			;
			if ( isPresent( clean ) ) {
				stopParameters.set( "clean", clean );
			}
			if ( isPresent( keepLogs ) ) {
				stopParameters.set( "keepLogs", keepLogs );
			}

			logger.debug( "* Stopping to: {}, params: {}", Arrays.asList( hosts ), stopParameters );

			ObjectNode clusterResponse = serviceOsManager.remoteAgentsApi(
				apiUserid,
				apiPass,
				hosts,
				CsapCoreService.API_AGENT_URL + AgentApi.KILL_SERVICES_URL,
				stopParameters );

			logger.debug( "Results: {}", clusterResponse );

			resultJson.set( "clusteredResults", clusterResponse );
			
			csapApp.getHostStatusManager().refreshAndWaitForComplete( null);

		}
		return resultJson;
	}

	public ObjectNode killRequest ( String apiUserid, List<String> services, String clean, String keepLogs, String auditUserid )
			throws JsonProcessingException {

		ObjectNode resultJson = jacksonMapper.createObjectNode();
		if ( Application.isJvmInManagerMode() ) {

			resultJson
				.put( CSAP.CONFIG_PARSE_ERROR,
					"Common only valid on agents" );

		} else {
			services.stream().forEach( service_port -> {

				resultJson.put( "serviceName", service_port );

				try {
					ServiceInstance instance = csapApp.getServiceInstanceCurrentHost( service_port );

					if ( instance == null ) {
						resultJson.put( "error", "Service requested does not exist: " + service_port );
					} else {

						// (userid, serviceNamePort, params, javaOpts, runtime)
						ArrayList<String> params = new ArrayList<String>();
						if ( isPresent( clean ) ) {
							params.add( "-cleanType" );
							params.add( "clean" );
						}
						if ( isPresent( keepLogs ) ) {
							params.add( "-keepLogs" );
						}

						if ( isPresent( auditUserid ) ) {

							csapEventClient.generateEvent(
								CsapEventClient.CSAP_SVC_CATEGORY + "/" + instance.getServiceName(),
								auditUserid, "Kill Request Received", " clean:" + clean );
							// log the originator of request
						}

						String task = serviceOsManager.submitKillJob( apiUserid, service_port, params );

						resultJson
							.put( "results", "Request queued" );

					}
				} catch (Exception e) {
					logger.error( "Failed deployment", e );
				}

			} );
		}

		return resultJson;
	}

	public ObjectNode startRemoteRequests (	String auditUserid,
											List<String> services, List<String> hosts,
											String commandArguments,
											String runtime, String hotDeploy,
											String startClean, String noDeploy,
											String apiUserid, String apiPass,
											String deployId) {
		ObjectNode resultJson = jacksonMapper.createObjectNode();

		ArrayNode servicesArray = resultJson.putArray( "services" );
		services.forEach( servicesArray::add );
		ArrayNode hostsArray = resultJson.putArray( "hosts" );
		hosts.forEach( hostsArray::add );

		if ( !Application.isJvmInManagerMode() ) {
			resultJson
				.put( "error",
					"refer to /api/deploy/host/* to deploy on hosts" );
		} else if ( hosts.size() == 0 || services.size() == 0 ) {
			resultJson
				.put( "error",
					"missing cluster parameter" );

		} else {
			MultiValueMap<String, String> startParameters = new LinkedMultiValueMap<String, String>();
			startParameters.set( "auditUserid", auditUserid );
			startParameters.put( "services", services );
			startParameters.put( "hosts", hosts );
			if ( isPresent( deployId ) ) {
				startParameters.set( "deployId", deployId );
			}

			if ( isPresent( runtime ) ) {
				startParameters.add( "runtime", runtime );
			}

			if ( isPresent( commandArguments ) ) {
				startParameters.add( "commandArguments", commandArguments );
			}

			if ( isPresent( startClean ) ) {
				startParameters.add( "startClean", startClean );
			}

			if ( isPresent( hotDeploy ) ) {
				startParameters.add( "hotDeploy", hotDeploy );
			}
			if ( isPresent( noDeploy ) ) {
				startParameters.add( "noDeploy", noDeploy );
			}

			logger.debug( "* Stopping to: {}, params: {}", Arrays.asList( hosts ), startParameters );

			ObjectNode clusterResponse = serviceOsManager.remoteAgentsApi(
				apiUserid,
				apiPass,
				hosts,
				CsapCoreService.API_AGENT_URL + AgentApi.START_SERVICES_URL,
				startParameters );

			logger.debug( "Results: {}", clusterResponse );

			resultJson.set( "clusteredResults", clusterResponse );
			
			csapApp.getHostStatusManager().refreshAndWaitForComplete( null);

		}
		return resultJson;
	}

	private boolean isPresent ( String parameter ) {
		if ( parameter == null || parameter.length() == 0 )
			return false;
		return true;
	}

	public ObjectNode startRequest (	String apiUserid, List<String> services,
										String commandArguments, String runtime, String hotDeploy,
										String startClean, String startNoDeploy,
										String auditUserid,
										String deployId) {

		ObjectNode resultJson = jacksonMapper.createObjectNode();
		if ( Application.isJvmInManagerMode() ) {

			resultJson
				.put( CSAP.CONFIG_PARSE_ERROR,
					"Common only valid on agents" );

		}
		if ( !csapApp.isBootstrapComplete() ) {

			resultJson
				.put( CSAP.CONFIG_PARSE_WARN,
					"Agent is currently restarting - wait a few minutes and try again" );

		} else {

			services.stream().forEach( service_port -> {

				resultJson.put( "serviceName", service_port );

				try {
					ServiceInstance instance = csapApp.getServiceInstanceCurrentHost( service_port );

					if ( instance == null ) {
						resultJson.put( "error", "Service requested does not exist: " + service_port );
					} else {

						// (userid, serviceNamePort, params, javaOpts, runtime)
						ArrayList<String> params = new ArrayList<String>();

						if ( isPresent( startClean ) ) {
							params.add( "-cleanType" );
							params.add( "clean" );
						}

						if ( isPresent( hotDeploy ) ) {
							params.add( "-hotDeploy" );
						}
						if ( isPresent( startNoDeploy ) ) {
							params.add( "-skipDeployment" );
						}

						if ( instance.getServiceName().startsWith( CSAP.AGENT_CONTEXT + "_" ) ) {
							try {
								csapApp.shutdown();
							} catch (Exception e) {
								logger.info( "Failed shutting down manager services", e );
							}
						}

						logger.debug( "params: {}", params );

//						resultJson.put( service_port,
//							"Queueing start, service configuration: " + params + " commandArguments:" + commandArguments );

						if ( serviceOsManager.getStartList().size() > 0 ) {
							resultJson.put( CSAP.CONFIG_PARSE_WARN,
								" Multiple services are currently queued: \n"
										+ serviceOsManager.getStartList() );
						}

						if ( auditUserid != null && auditUserid.length() > 0 ) {

							csapEventClient.generateEvent(
								CsapEventClient.CSAP_SVC_CATEGORY + "/" + instance.getServiceName(),
								auditUserid, "Start Request Received", " params:" + params );
							// log the originator of request
						}

						serviceOsManager.submitStartJob( apiUserid, service_port, params, commandArguments, runtime, deployId );

						resultJson
							.put( "results", "Request queued" );

					}
				} catch (Exception e) {
					logger.error( "Failed deployment", e );
				}

			} );
		}

		logger.debug( "Results: {}", resultJson );

		return resultJson;
	}

	/**
	 * 
	 * if more then 1 hosts are specified and transfersHosts is null,
	 * primaryHost will be first host and remainder will be synced
	 * 
	 * 
	 * @param auditUserid
	 * @param services
	 * @param hosts
	 * @param scmUserid
	 * @param scmPass
	 * @param scmBranch
	 * @param commandArguments
	 * @param runtime
	 * @param hotDeploy
	 * @param mavenDeployArtifact
	 * @param scmCommand
	 * @param transferHostsSpaceSeparated
	 * @param apiUserid
	 * @param apiPass
	 * @return
	 */
	public ObjectNode deployRemoteRequests (
	                                        	String deployId,
												String auditUserid,
												List<String> services,
												List<String> hosts,

												String scmUserid, String scmPass,
												String scmBranch, String commandArguments,
												String runtime, String hotDeploy,
												String mavenDeployArtifact, String scmCommand,
												String transferHostsSpaceSeparated,

												String apiUserid, String apiPass ) {

		ObjectNode resultJson = jacksonMapper.createObjectNode();

		ArrayNode servicesArray = resultJson.putArray( "services" );
		services.forEach( servicesArray::add );
		ArrayNode hostsArray = resultJson.putArray( "hosts" );

		List<String> primaryHost = hosts;

		if ( hosts.size() > 1 && transferHostsSpaceSeparated == null ) {
			StringBuilder syncHosts = new StringBuilder();
			hosts
				.stream()
				.distinct()
				.map( host -> host + " " )
				.forEach( syncHosts::append );

			// strip off first host
			String firstHost = syncHosts.substring( 0, syncHosts.indexOf( " " ) ).trim();
			transferHostsSpaceSeparated = syncHosts.substring( syncHosts.indexOf( " " ) ).trim();

			primaryHost = new ArrayList<>();
			primaryHost.add( firstHost );

			logger.info( "Converted hosts to primary: {}, transferHostsSpaceSeparated: {}", primaryHost, transferHostsSpaceSeparated );
		}
		hosts.forEach( hostsArray::add );

		if ( !Application.isJvmInManagerMode() ) {
			resultJson
				.put( "error",
					"refer to /api/deploy/host/* to deploy on hosts" );
		} else if ( hosts.size() == 0 || services.size() == 0 ) {
			resultJson
				.put( "error",
					"missing cluster parameter" );

		} else {
			MultiValueMap<String, String> deployParameters = new LinkedMultiValueMap<String, String>();
			deployParameters.set( "auditUserid", auditUserid );
			deployParameters.set( "primaryHost", primaryHost.get( 0 ));
			deployParameters.set( "deployId", deployId );
			deployParameters.put( "services", services );
			deployParameters.put( "hosts", hosts );

			if ( isPresent( runtime ) ) {
				deployParameters.set( "runtime", runtime );
			}

			if ( isPresent( commandArguments ) ) {
				deployParameters.set( "commandArguments", commandArguments );
			}

			if ( isPresent( scmUserid ) ) {
				deployParameters.set( "scmUserid", scmUserid );
			}

			if ( isPresent( scmPass ) ) {
				deployParameters.set( "scmPass", scmPass );
			}

			if ( isPresent( scmBranch ) ) {
				deployParameters.set( "scmBranch", scmBranch );
			}

			if ( isPresent( hotDeploy ) ) {
				deployParameters.set( "hotDeploy", hotDeploy );
			}

			if ( isPresent( mavenDeployArtifact ) ) {
				deployParameters.set( "mavenDeployArtifact", mavenDeployArtifact );
			}

			if ( isPresent( scmCommand ) ) {
				deployParameters.set( "scmCommand", scmCommand );
			}

			if ( isPresent( transferHostsSpaceSeparated ) ) {
				deployParameters.set( "targetScpHosts", transferHostsSpaceSeparated );
			}

			logger.debug( "* Stopping to: {}, params: {}", Arrays.asList( hosts ), deployParameters );

			ObjectNode clusterResponse = serviceOsManager.remoteAgentsApi(
				apiUserid,
				apiPass,
				hosts,
//				primaryHost,
				CsapCoreService.API_AGENT_URL + AgentApi.DEPLOY_SERVICES_URL,
				deployParameters );

			logger.debug( "Results: {}", clusterResponse );

			resultJson.set( "clusteredResults", clusterResponse );
			
			csapApp.getHostStatusManager().refreshAndWaitForComplete( null);

		}
		return resultJson;
	}

	public ObjectNode deployRequest (	String apiUserid, 
	                                 	String primaryHost,
	                                 	String deployId,
	                                 	ArrayList<String> services,
										String scmUserid, String scmPass,
										String scmBranch, String commandArguments,
										String runtime, String hotDeploy,
										String mavenDeployArtifact, String scmCommand,
										String targetScpHosts,
										String auditUserid ) {

		ObjectNode resultJson = jacksonMapper.createObjectNode();
		if ( Application.isJvmInManagerMode() ) {

			resultJson
				.put( CSAP.CONFIG_PARSE_ERROR,
					"Common only valid on agents" );

		}
		if ( !csapApp.isBootstrapComplete() ) {

			resultJson
				.put( CSAP.CONFIG_PARSE_WARN,
					"Agent is currently restarting - wait a few minutes and try again" );

		} else {

			services.stream().forEach( service_port -> {

				resultJson.put( "serviceName", service_port );

				try {
					ServiceInstance instance = csapApp.getServiceInstanceCurrentHost( service_port );
					if ( instance == null ) {
						// some deploys select different ports - bad practice - but handled
						instance = csapApp.findServiceByNameOnCurrentHost( service_port.split( "_" )[0] ) ;

						resultJson.put( "serviceName", instance.getServiceName_Port() );
					}
					
					if ( instance == null ) {
						// some deploys select different ports - bad practice - but handled
						resultJson.put( "error", "Service requested does not exist: " + service_port );

					} else {

						MultiValueMap<String, String> rebuildVariables = new LinkedMultiValueMap<String, String>();

						// all variables are added even if null as they are used
						// in deploy
						rebuildVariables.set( "primaryHost", primaryHost );
						rebuildVariables.set( "deployId", deployId );
						rebuildVariables.set( "scmCommand", scmCommand );
						rebuildVariables.set( "mavenDeployArtifact", mavenDeployArtifact );
						rebuildVariables.set( "javaOpts", commandArguments );
						rebuildVariables.set( "runtime", runtime );
						rebuildVariables.set( "scmUserid", scmUserid );
						rebuildVariables.set( "scmPass", scmPass );
						rebuildVariables.set( "scmBranch", scmBranch );
						rebuildVariables.set( "hotDeploy", hotDeploy );
						rebuildVariables.set( "targetScpHosts", targetScpHosts );

						if ( instance.getServiceName().startsWith( CSAP.AGENT_CONTEXT + "_" ) ) {
							try {
								csapApp.shutdown();
							} catch (Exception e) {
								logger.info( "Failed shutting down manager services", e );
							}
						}

						String filteredVariables = ServiceOsManager.filterField( rebuildVariables.toString(), "scmPass" );
						logger.debug( "rebuildVariables: {}", filteredVariables );

//						resultJson.put( service_port,
//							"Queueing deploy, service configuration: " + filteredVariables + " commandArguments:" + commandArguments );

						if ( serviceOsManager.getBuildList().size() > 1 ) {
							resultJson.put( CSAP.CONFIG_PARSE_WARN,
								"Request Queued behind others:\n" + serviceOsManager.getBuildList() );
						}
						if ( auditUserid != null && auditUserid.length() > 0 ) {

							csapEventClient.generateEvent(
								CsapEventClient.CSAP_SVC_CATEGORY + "/" + instance.getServiceName(),
								auditUserid, "Deploy Request Received", " rebuildVariables:" + filteredVariables );
							// log the originator of request
						}

						instance.setCpuAuto(); // will show hour class on ui
						serviceOsManager.submitDeployJob( apiUserid, instance, rebuildVariables, false, true );
						resultJson
							.put( "results", "Request queued" );

					}
				} catch (Exception e) {
					logger.error( "Failed deployment: {}", service_port,  e );
				}

			} );
		}
		
		if ( resultJson.has( "error" ) ) {
			logger.warn( "Found errors: {}", resultJson );
		}

		logger.debug( "Results: {}", resultJson );

		return resultJson;
	}

	// public void startServiceAndWaitABit ( String serviceName_port, String
	// userid, ObjectNode resultJson ) {
	// try {
	// ServiceInstance instance = csapApp.getServiceInstanceCurrentHost(
	// serviceName_port );
	//
	// if ( instance == null ) {
	// resultJson.put( "error", "Jvm requested does not exist: " +
	// serviceName_port );
	// } else {
	//
	// // (userid, serviceNamePort, params, javaOpts, runtime)
	// ArrayList<String> params = new ArrayList<String>();
	// String taskId = serviceOsManager.submitStartJob( userid,
	// serviceName_port, params,
	// null,
	// null );
	// // int maxAttempts = 6;
	// // while (serviceManager.getStartList().contains( taskId ) &&
	// // maxAttempts-- > 0) {
	// // try {
	// // Thread.sleep( 5000 );
	// // } catch ( InterruptedException e ) {
	// // logger.error( "Failed polling results" );
	// // }
	// // }
	//
	// // if ( maxAttempts == 0 ) {
	// resultJson
	// .put( "results",
	// "Request Queued: "
	// + serviceOsManager.getStartList()
	// + " Verify results in CS-AP start logs, and application logs." );
	// // } else {
	// // resultJson
	// // .put( "results",
	// // "Request completed. Verify results in CS-AP start logs, and
	// // application logs." );
	// //
	// // }
	//
	// }
	// } catch (Exception e) {
	// logger.error( "Failed deployment", e );
	// }
	// }

}
