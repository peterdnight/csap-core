define( ["./validation-handler"], function ( validationHandler ) {

	console.log( "Module loaded" );

	var descMap = {
		"clusterDefinitions": "capability lifecycle",
		"monitors": "Peters monitor",
		"jvms": "java virtual machine definition",
		"jvmPorts": "cluster jvm",
		"osProcessesList": "cluster os process",
		"hosts": "host",
		"newsItems": "news Item",
		"releasePackages": 'releasePackageFilename.js',
		"osProcesses": "OS process definition",
		"helpMenuItems": "help menu item",
		"launchUrls": "Launch Url for jvms",
		"jvmPorts" : "Tomcat base port"
	};
	var mapTemplates = {
		"customMetrics": {
			"mbean": "Mbean Name - update, or delete if simon is used",
			"attribute": "Mbean Attribute  - update, or delete if simon is used",
			"simonCounter": "simon jmx name - update, or delete if MBean is used",
			"simonTotalPerInterval": "Delete this entry for simon attribute you want to divide by collection interval"
		},
		"environmentVariables": {
			"yourVar1": "yourValue1",
			"yourVar2": "yourValue2"
		},
		"customHttpdRouting": [
			"method=Next",
			"sticky_session=1"
		],
		"monitors": {
			"nagiosCommand": "check_http! -v -u /some/app/url --regex \".*Healthy.*true.*\"  -t 3  ",
			"jvm_jmxHeartbeat": false,
			"max_diskWriteKb": "5",
			"max_diskUtil": "150",
			"max_threadCount": "100",
			"max_fileCount": "300",
			"max_socketCount": "10",
			"max_rssMemory": "768m",
			"max_tomcatConnections": "40",
			"max_topCpu": "150"
		},
		"helpMenuItems": "http://yourHelpUrl",
		"launchUrls": "http://yourLbUrl",
		"jvms": {
			"docUrl": "https://github.com/csap-platform/csap-core/wiki,https://github.com/csap-platform/csap-core/wiki",
			"description": "ServletSample provides a simple tomcat 7 implementation to validate the tomcat runtime",
			"parameters": "-DcsapJava8 -Xms128M -Xmx128M ",
			"osProcessPriority": "0",
			"autoStart": "91",
			"server": "tomcat8.x",
			"source": {
				"scm": "svn",
				"path": "https://svn.yourcompany.com/svn/csap/trunk/public/javaProjects/Servlet3Sample",
				"branch": "HEAD"
			},
			"maven": {
				"dependency": "com.your.group:Servlet3Sample:1.0.3:war"
			},
			"version": {
				"1": { }
			}
		},
		"jvmPorts": ["999x"],
		"clusterDefinitions": {
			"settings": {
				"agent": {
					"maxJmxCollectionMs": 10000,
					"numberWorkerThreads": "4",
					"adminToAgentTimeoutInMs": 6000,
					"lsofIntervalMins": 1,
					"duIntervalMins": 1,
					"apiLocal": true,
					"apiUser": "agentUser",
					"apiPass": "CSAP Developmentdev",
					"infraTests": {
						"cpuIntervalMinutes": 10,
						"cpuLoopsMillions": 1,
						"diskIntervalMinutes": 10,
						"diskWriteMb": 500 
					}
				},
				"operatorNotifications": [
					"someUser@yourCompany.com",
					"asdfsd@sdfsd.com"
				],
				"newsItems": [
					"Simple News"
				],
				"portRange": {
					"start": 8200,
					"end": 9300
				},
				"csapData": {
					"eventUrl": "http://csaptools.yourcompany.com/data/api/event",
					"eventApiUrl": "http://csaptools.yourcompany.com/data/eventApi",
					"eventMetricsUrl": "http://csaptools.yourcompany.com/analytics/api/metrics/",
					"analyticsUiUrl": "http://csaptools.yourcompany.com/admin/os/performance",
					"historyUiUrl": "http://csaptools.yourcompany.com/data?appId={appId}&life={life}&category={category}&",
					"user": "XXXXX.gen",
					"pass": "Visit_adam.yourcompany.com"
				},
				
				"loadBalanceVmFilter": [
					"none"
				],
				
				"lbUrl": "https://csap-secure.yourcompany.com",
				
				"loadBalanceVmFilter": [
					"none"
				],
				"lsofIntervalMins": 1,

				"monitoringUrl": "",
				"mavenCommand": "-B -Dmaven.test.skip=true clean package",

				"secureUrl": "https://csap-secure.yourcompany.com/admin",
				"autoRestartHttpdOnClusterReload": "yes",
				"launchUrls": {
					"1) http(Tomcat Embed)": "default",
					"2) ajp(LB - Internal)": "http://yourlb.yourcompany.com"
				},
				"monitorDefaults": {
					"jvm_jmxHeartbeat": false,
					"maxDiskPercent": 60,
					"maxHostCpuLoad": 4,
					"maxHostCpu": 50,
					"maxHostCpuIoWait": 11,
					"minFreeMemoryMb": "2500",
					"max_diskUtil": "30",
					"max_threadCount": "100",
					"max_fileCount": "300",
					"max_socketCount": "10",
					"max_rssMemory": "768m",
					"max_tomcatConnections": "40",
					"max_topCpu": "150"
				},
				"metricsPublication": [
					{
						"type": "csapCallHome",
						"intervalInSeconds": 300,
						"url": "http://csaptools.yourcompany.com/CsapGlobalAnalytics/rest/vm/health",
						"token": "notUsed"
					}
				],
				"metricsCollectionInSeconds": {
					"processDumps": {
						"resouceInterval": 30,
						"maxInInterval": 3,
						"lowMemoryInMb": "1000"
					},
					"resource": [
						30,
						300,
						3600
					],
					"service": [
						30,
						300,
						3600
					],
					"jmx": [
						30,
						300,
						3600
					],
					"trending": [
						{
							"label": "Cores Active (mpstat)",
							"report": "custom/core",
							"metric": "coresUsed",
							"divideBy": "1"
						},
						{
							"label": "Total Threads",
							"report": "vm",
							"metric": "threadsTotal",
							"divideBy": "numberOfSamples",
							"allVmTotal": true
						}
					],
					"realTimeMeters": [
						{
							"label": "Http Requests Per Minute",
							"id": "jmxCustom.httpd.UrlsProcessed",
							"intervals": [
								50,
								100,
								200
							],
							"multiplyBy": "2"
						},
						{
							"label": "VM coresActive",
							"id": "vm.coresActive",
							"intervals": [
								3,
								5,
								10
							],
							"min": 0
						}
					]
				}
			},
			"SampleCluster": {
				"osProcessesList": [],
				"jvmPorts": { },
				"multiVm": {
					"1": {
						"hosts": []
					}
				}
			}
		},
		"osProcesses": {
			"description": "Enter your description yourUserid",
			"docUrl": "https://github.com/csap-platform/csap-core/wiki,https://github.com/csap-platform/csap-core/wiki",
			"autoStart": "04",
			"parameters": " -DcsapJava8  -Xms128M -Xmx133M -XX:MaxMetaspaceSize=96M",
			"environmentVariables": {
				"yourVar1": "yourValue1",
				"yourVar2": "yourValue2"
			},
			"url": "http://$host:8161/admin/queues.jsp",
			"port": "9011",
			"server": "wrapper",
			"metaData": "skipJmxCollection,isDataStore,killWarnings",
			"logDirectory": "/home/mquser/logs",
			"logRegEx": ".*\\.log",
			"propDirectory": "/home/mquser",
			"maven": {
				"dependency": "com.your.group:ActiveMqWrapper:5.6.0:zip"
			},
			"source": {
				"scm": "svn",
				"path": "http://yourSvnOrGit/svn/smartservices/coreservices/trunk/cssp/ActiveMqWrapper",
				"branch": "trunk"
			},
			"version": {
				"1": { }
			}
		}
	};
	var monitorOptionalParams = ["nagiosCommand", "max_diskWriteKb", "max_diskUtil", "max_threadCount", "max_fileCount", "max_socketCount", "max_rssMemory", "max_tomcatConnections", "max_topCpu"];
	var jvmOptionalParams = ["environmentVariables", "customHttpdRouting", "osProcessPriority", "autoStart", "monitors", "context", "metaData", "launchUrl", "compression", "compressableMimeType", "cookieName", "cookiePath", "cookieDomain",
		"servletThreadCount", "servletAccept", "servletMaxConnections", "servletTimeoutMs", "parameters", "processFilter", "jmxPort",
		"customMetrics"];
	var overRideParams = {
		"parameters": "-Xms128M -Xmx128M -XX:MaxPermSize=128m",
		"osProcessPriority": "0",
		"metaData": "exportWeb",
		"launchUrl": "/admin/info",
		"compression": "no",
		"compressableMimeType": "text/html,text/xml,text/plain,text/css,text/javascript,text/json,application/x-javascript,application/javascript,application/json",
		"cookieName": "yourCustomCookieName",
		"cookiePath": "yourCustomCookiePath",
		"cookieDomain": "yourCustomCookieDomain",
		"servletThreadCount": "50",
		"servletAccept": "10",
		"servletMaxConnections": "50",
		"servletTimeoutMs": "30000",
		"autoStart": "99",
		"monitors": {
			"nagiosCommand": "check_http! -v -u /some/app/url --regex \".*Healthy.*true.*\"  -t 3  ",
			"jvm_jmxHeartbeat": false,
			"max_diskWriteKb": "5",
			"max_diskUtil": "30",
			"max_threadCount": "100",
			"max_fileCount": "300",
			"max_socketCount": "10",
			"max_rssMemory": "768m",
			"max_tomcatConnections": "40",
			"max_topCpu": "150"
		},
		"scheduleOff": "friday,23:55,NotImplYet",
		"scheduleOn": "monday,00:00,NotImplYet",
		"maven": {
			"dependency": "com.your.group:YourService:1.2.3:zip"
		},
		"customMetrics": {
			"isHealthy": {
				"mbean": "spring.application:application=FactorySample,type=CstgCustom",
				"attribute": "HealthCheck"
			},
			"TotalVmCpu": {
				"mbean": "java.lang:type=OperatingSystem",
				"attribute": "SystemCpuLoad"
			},
			"ProcessCpu": {
				"mbean": "java.lang:type=OperatingSystem",
				"attribute": "ProcessCpuLoad"
			}
		},
		"processFilter": ".*someService.*someArg.*",
		"server": "cssp1.x",
		"jmxPort": "-1",
		"context": "yourCustomHttpContextUsedByBrowser"
	};


	
	return {
		//

		getDesc: function ( key ) {
			return getDesc( key );
		},

		getDescLabel: function ( key ) {
			return getDescLabel( key );
		},
		
		getJvmOptionalSettings: function () {
			return jvmOptionalParams;
		},
		
		getMapDefaults: function ( key ) {
			console.log( "getMapDefaults: " + key) ;
			return mapTemplates[ key ];
		},
		
		getLifeCycleParams: function () {
			console.log( "getLifeCycleParams: " ) ;
			return overRideParams;
		},
		
		getMonitorParams: function () {
			console.log( "getMonitorParams: " ) ;
			return monitorOptionalParams;
		},
	}
	
	/**
	 * 
	 * Json definitions for new items
	 * 
	 */

	function getDesc( key ) {
		var result = descMap [ key ];
		if ( result == undefined )
			result = key;
		var regex = new RegExp( "\\.", "g" );
		return result.replace( regex, " to " );
	}

	function getDescLabel( key ) {
		var result = descMap [ key ];
		if ( result == undefined )
			result = "PortRange";
		return result;
	}
	
	
}) ;