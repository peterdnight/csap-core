define( ["./validation-handler", "./json-forms"], function ( validationHandler, jsonForms ) {

	console.log( "Service Edit Module loaded" );

	var _defaultService = {
		"autoStart": "80",
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
			"1": {
				"dev": { },
				"stage": {
					"attributeName": "aValueToOverrideDefaults"
				},
				"lt": { },
				"prod": { }
			}
		},
		"parameters": "-Xms128M -Xmx128M "
	}

	var defaultValues = {

		"remoteCollections": [
			{
				"host": "csap-dev01",
				"port": "8996"
			},
			{
				"host": "csap-dev02",
				"port": "8996"
			}],
		"environmentVariables": {
			"csapExternalPropertyFolder": "$STAGING/conf/shared",
			"anotherName": "anotherValue"
		},

		"scheduledJobs": {
			"scripts": [
				{
					"description": "Agent Demo: checkLimits",
					"frequency": "onDemand",
					"script": "$staging/bin/checkLimits.sh"
				}
			],
			"diskCleanUp": [
				{
					"path": "$processing/*.logs",
					"olderThenDays": 30,
					"maxDepth": 1
				},
				{
					"path": "$staging/saved",
					"olderThenDays": 30,
					"maxDepth": 10,
					"pruneEmptyFolders": true
				},
				{
					"path": "$staging/mavenRepo",
					"olderThenDays": 60
				},
				{
					"path": "$workingFolder/temp",
					"olderThenDays": 60
				}
			],
			"logRotation": [
				{
					"path": "$logFolder/consoleLogs.txt",
					"settings": "copytruncate,weekly,rotate 3,compress,missingok,size 10M"
				},
				{
					"path": "$logFolder/warnings.log",
					"lifecycles": "dev,stage,lt",
					"settings": "copytruncate,weekly,rotate 3,compress,missingok,size 10M"
				},
				{
					"path": "$logFolder/warnings.log",
					"lifecycles": "prod",
					"settings": "copytruncate,weekly,rotate 3,compress,missingok,size 10M"
				}
			]
		},
		"apacheModJk": {
			"comments": "It is strongly recommended to set timeout to 30000 (30 seconds)",
			"loadBalance": [
				"method=Request",
				"sticky_session=1"
			],
			"connection": [
				"reply_timeout=15"
			]
		},
		"apacheModRewrite": [
			"RewriteRule ^/test1/(.*)$  /ServletSample/$1 [PT]",
			"RewriteRule ^/test2/(.*)$  /ServletSample/$1 [PT]"
		],
		"customMetrics": {
			"systemCpu": {
				"title": "Sample 1: Host Cpu",
				"mbean": "java.lang:type=OperatingSystem",
				"attribute": "SystemCpuLoad"
			},
			"processCpu": {
				"title": "Sample 2: JVM Cpu",
				"mbean": "java.lang:type=OperatingSystem",
				"attribute": "ProcessCpuLoad",
				"max": 10
			},
			"requests": {
				"title": "Sample 3: MBean With Delta Collector",
				"mbean": "Catalina:j2eeType=Servlet,WebModule=__CONTEXT__,name=dispatcher,J2EEApplication=none,J2EEServer=none",
				"attribute": "requestCount",
				"delta": "delta"
			},
			"simonexample1": {
				"title": "Sample 4: JavaSimon Counter",
				"simonCounter": "your.java.simon.id",
				"max": 10
			},
			"simonexample2": {
				"title": "Sample 5: JavaSimon Mean in millis",
				"simonMedianTime": "your.java.simon.id",
				"divideBy": "1000000"
			}
		},
		"docker": {
			"volumes": [
				{
					"hostPath": "/opt/java",
					"containerMount": "/java",
					"readOnly": true,
					"sharedUser": true
				},
				{
					"hostPath": "$workingFolder",
					"containerMount": "/_working",
					"readOnly": false,
					"sharedUser": true
				}
			],
			"portMappings": [
				{
					"PrivatePort": "$port",
					"PublicPort": "$port"
				},
				{
					"PrivatePort": "$ajpPort",
					"PublicPort": "$ajpPort"
				}
			],
			"limits": {
				"ulimits": [
					{
						"name": "nofile",
						"soft": 500,
						"hard": 500
					},
					{
						"name": "nproc",
						"soft": 200,
						"hard": 200
					}
				]
			}
		}

	}
	return {

		defaultFields: function (  ) {
			return defaultValues;
		},

		defaultService: function (  ) {
			return _defaultService;
		},

	}
} );
