require.config( {
	// 3rd party script alias names. note the relative path may need to be overwritten in user packages
	paths: {
		mathjs: uiSettings.baseUrl+"js/csapLibs/mathjs/modules/math.min"
	},
	waitSeconds: 15,
//	shim: {
//		"jsbn2": {
//			//These script dependencies should be loaded before loading
//			//backbone.js
//			deps: ["jsbn"]
//		}
//	}
} );

define( ["./DataManager", "./hostGraphs", "./graphLayout", "./settings"], function ( DataManager, hostGraphs, graphLayout, settings ) {

	console.log( "Module loaded: graphPackage/ResourceGraph" );

	function ResourceGraph( containerId, metricType, targetLifecycle, targetAppId, dataServiceUrl, optionalModel ) {

		// use of bind() is not advisable when multiple chained methods are used.
		var _currentResourceGraphInstance = this;

		var _hostArray = new Array();
		this.getHostCount = getHostCount;
		function getHostCount() {
			return _hostArray.length
		}

		var _customViews = null;
		this.getSelectedCustomView = getSelectedCustomView;
		function getSelectedCustomView() {
			if ( _customViews == null )
				return null;
			var viewSelection = $( ".customViews", $GRAPH_INSTANCE ).val();

			if ( _customViews[viewSelection] == undefined )
				return null;

			return _customViews[viewSelection];
		}
		this.addCustomViews = addCustomViews;
		function addCustomViews( customViews ) {
			_customViews = customViews;
			settings.addCustomViews( _currentResourceGraphInstance, _customViews );
		}

		var _performanceModel = null;

		this.isCurrentModePerformancePortal = isCurrentModePerformancePortal;
		function isCurrentModePerformancePortal() {
			return  _performanceModel != null
		}
		;

		var _dataManager = null;
		this.getDataManager = function () {
			return _dataManager;
		};

		var lifeCycle = targetLifecycle;
		var targetAppId = targetAppId;
		var targetMetricsUrl = dataServiceUrl;

		var $GRAPH_INSTANCE = null;
		this.getCurrentGraph = function () {
			return $GRAPH_INSTANCE;
		};

		this.dumpInfo = function () {
			console.log( "== graph.dumpInfo: " + $GRAPH_INSTANCE.attr( "id" ) + " _metricType: " + _metricType );
		}

		var _metricType = null;

		var _graphParam = null;

		/**
		 * 
		 *	invoked at bottom of Resource Graph to provide js context
		 */
		function initialize( containerId, metricType ) {

			if ( optionalModel != undefined )
				_performanceModel = optionalModel;

			if ( typeof _lifeForMetricsData != 'undefined' ) {
				console.log( "Eliminate use of _lifeForMetricsData with param" );
				this.lifeCycle = _lifeForMetricsData
			}

			if ( typeof appId != 'undefined' ) {
				console.log( "Eliminate use of appId with param" );
				this.targetAppId = appId;
			}
			if ( typeof metricsDataUrl != 'undefined' ) {
				console.log( "Eliminate use of metricsDataUrl with param" );
				this.targetMetricsUrl = metricsDataUrl
			}

			_metricType = metricType;

			console.log( "\n\n graph.init resourceId: " + containerId + "  metricType: " + metricType +
					" _performanceModel: " + _performanceModel );


			// alert(resourceId) ;
			$GRAPH_INSTANCE = $( containerId );

			if ( containerId != "#resourceTemplate" ) {
				var graphClone = $( "#resourceTemplate" ).clone();
				var id = containerId.substring( 1 ) + "Clone";
				graphClone.attr( "id", id );
				graphClone.data( "preference", _metricType );
				$( containerId ).append( graphClone );
				graphClone.show();

				$GRAPH_INSTANCE = graphClone;
			}
			// alert ($GRAPH_INSTANCE.attr("id")) ;
			$GRAPH_INSTANCE.show();

			// how to get localhost
			var hostsParams=$.urlParam( "hosts" ) ;
			if ( hostsParams != null && hostsParams!= 0) {
				console.log("hostsParams", hostsParams)
				_hostArray = hostsParams.split( "," );

			} else {
				// hook for hostDashboard
				if ( !isCurrentModePerformancePortal() )
					_hostArray.push( DEFAULT_RESOURCE_GRAPH_HOST );
			}

			// if service is passed default to line mode
			if ( !isCurrentModePerformancePortal() && $.urlParam( "service" ) != null ) {
				$( '.useLineGraph', $GRAPH_INSTANCE ).prop( "checked", true )
			}

			if ( $.urlParam( "graph" ) != null ) {
				_graphParam = $.urlParam( "graph" );
			}

			settingsUpdated();

			// pass call back
			settings.uiComponentsRegistration( _currentResourceGraphInstance );


		}



		this.selectOrSetDefaultVariable = selectOrSetDefaultVariable;
		function selectOrSetDefaultVariable( arg, def ) {
			return (typeof arg == 'null undefined' ? def : arg);
		}

		/**
		 * params currently controlled by metrics.jsp
		 */

		this.settingsUpdated = settingsUpdated;
		function settingsUpdated() {
			console.log( "settingsUpdated()" );
			updateConfigParams();
			triggerGraphRefresh();
		}


		function updateConfigParams() {
			if ( $( 'input[name=categoryRadio]:checked' ).length != 0 ) {
				var category = $( 'input[name=categoryRadio]:checked' ).data( "id" );
				_metricType = category;
			}

			if ( $( "#hostDisplay input:checked" ).length > 0 ) {
				_hostArray = new Array();
				$( "#hostDisplay input.instanceCheck:checked" ).each( function ( index ) {
					_hostArray.push( $( this ).data( 'host' ) );
				} );
			}
		}


		var _timersArray = new Array();

		this.clearRefreshTimers = clearRefreshTimers;
		function clearRefreshTimers( host ) {

			if ( host == undefined ) {
				for ( var hostTimerKey in _timersArray ) {
					clearTimeout( _timersArray[hostTimerKey] );
					_timersArray[hostTimerKey] = null;
				}
			} else {
				// alert("timer deleted: " + host) ;
				clearTimeout( _timersArray[host] );
				_timersArray[host] = null;
			}
		}


		// This looks for global variables defined in global scope. It enables customization of
		// internals.

		var _lastGraphsChecked = null;
		function handlePerformanceGlobalConfig() {
			if ( isCurrentModePerformancePortal() ) {

				_lastGraphsChecked = new Object();

				//console.log("handlePerformanceGlobalConfig():  _lastServiceNames: " + _lastServiceNames) ;
				// _lastGraphsChecked=new Object() ;
				$( ".graphCheckboxes input" ).each( function () {
					_lastGraphsChecked[ $( this ).val() ] = $( this ).prop( "checked" );
					//console.log("handlePerformanceGlobalConfig() : adding graph: " + $(this).attr("id") ) ;
				} );
			}
		}

		// Create a public method
		this.triggerGraphRefresh = triggerGraphRefresh;
		function triggerGraphRefresh() {

			var displayHeight = $( window ).outerHeight( true )
					- $( "header" ).outerHeight( true ) - 250;

			$( "#maxHeight", $GRAPH_INSTANCE ).attr( "value", displayHeight + "px" );


			handlePerformanceGlobalConfig();

			// Get rid of previous containers.
			// alert("Clearing container: " + $GRAPH_INSTANCE.attr("id") + " _metricType: " + _metricType ) ;
			$( ".hostChildren", $GRAPH_INSTANCE ).remove();

			clearRefreshTimers();
			// _isDataAutoSampled = false;
			_dataManager = new DataManager( _hostArray );

			// alert(type + ":" + label) ;

			// var _hostArray=unescape(getRequestParams("hosts")).split(",") ;
			console.log( "Graph Refresh: Number of Hosts: " + _hostArray.length );
			for ( var i = 0; i < _hostArray.length; i++ ) {
				var currentHost = _hostArray[i];
				console.log( "triggerGraphRefresh() currentHost: " + currentHost );

				var newHostContainer = buildHostContainer( currentHost, $GRAPH_INSTANCE );

				getMetrics( $( '#numSamples', $GRAPH_INSTANCE ).val(),
						newHostContainer, currentHost );

				settings.addContainerEvents( _currentResourceGraphInstance, newHostContainer );

			}

			settings.addToolsEvents()

		}

		function buildHostContainer( host, $GRAPH_INSTANCE ) {
			var $container = $( ".hostTemplate", $GRAPH_INSTANCE )
					.clone();
			$container.removeClass( "hostTemplate" );
			$container.addClass( "hostChildren" );
			$container.addClass( host + "Container" );
			$( ".hostContainer", $GRAPH_INSTANCE ).append( $container );
			$( ".hostName", $container ).html( host );

			$container.attr( "data-host", host );

			$container.show();

			return $container;
		}

		// Note the JQuery hook for doing JSONP: callback is inserted
		this.getMetrics = getMetrics;
		function getMetrics( numSamples, newHostContainerJQ, host ) {



			var serviceNameArray = [];
			$( '.serviceCheckbox:checked', $GRAPH_INSTANCE ).val(
					function ( i, e ) {
						//
						if ( e == "totalCpu" ) {
							// console.log("skipping: " + e);
							return e;
						}

						serviceNameArray.push( e );
						return e;
					} );

			console.groupCollapsed( $GRAPH_INSTANCE.attr( "id" ) + " getMetrics(): Number of Services: " + serviceNameArray.length );

			if ( $( ".sampleIntervals input", $GRAPH_INSTANCE ).length == 0 ) {
				if ( $.urlParam( "service" ) != null ) {
					serviceNameArray.push( $.urlParam( "service" ) );
				}
			}


			if ( typeof _lastServiceNames != 'undefined' ) {
				if ( _lastServiceNames.length > 0 )
					serviceNameArray = _lastServiceNames;
				console.log( "getMetrics():  Using previous selection: _lastServiceNames: " + serviceNameArray );
			}

			// hook for customizing JMX
			if ( $( ".triggerJmxCustom" ).length > 0 ) {
				console.log( "getMetrics() Triggering custom jmx: " + $( ".triggerJmxCustom" ).text() );
				if ( !isCurrentModePerformancePortal() && $.urlParam( "service" ) != null ) {
					$( ".triggerJmxCustom" ).html( $.urlParam( "service" ) );
				}
				// se
				serviceNameArray = new Array();
				// serviceNameArray.push("_custom_");
				serviceNameArray.push( $( ".triggerJmxCustom" ).html() );
			}
			if ( window.csapGraphSettings != undefined ) {
				var jmxService = window.csapGraphSettings.service;
				$( ".triggerJmxCustom" ).html( jmxService );
				serviceNameArray = new Array();
				serviceNameArray.push( jmxService );
				console.log( "csapGraphSettings(): add ing service: " + jmxService );
			}


			// alert($(".useHistorical", $GRAPH_INSTANCE).is(':checked')) ;
			var numdays = $( "#numberOfDays", $GRAPH_INSTANCE ).val();
			if ( !$( ".useHistorical", $GRAPH_INSTANCE ).is( ':checked' ) )
				numdays = -1;

			var id = "none";
			var dayOffset = $( "#dayOffset", $GRAPH_INSTANCE ).val();


			var serviceUrl = "metricsData";
			var paramObject = {
				"hostName": host,
				"metricChoice": _metricType,
				"numSamples": numSamples,
				"skipFirstItems": 1,
				"numberOfDays": numdays,
				"id": id,
				"dayOffset": dayOffset,
				"isLastDay": $( "#useOldest", $GRAPH_INSTANCE ).is( ':checked' ),
				"serviceName": serviceNameArray
			};
			// Use JSONP for historical data
			if ( numdays != -1 ) {

				if ( host == "localhost" ) {
					host = "csap-dev01";
					console
							.log( "Testing on localhost, setting host to csap-dev01 in resourceGraph.js for testing" );
				}

				// numberOfDays={numberOfDays}&dateOffSet={dateOffSet}&searchFromBegining={searchFromBegining}
				// /AuditService/show/metricsAPI/{hostName}/{id}?

				paramObject = {
					"appId": targetAppId,
					"life": lifeCycle,
					"numberOfDays": numdays,
					"dateOffSet": dayOffset,
					"serviceName": serviceNameArray,
					"padLatest": $( '.padLatest', $GRAPH_INSTANCE ).prop( "checked" ),
					"searchFromBegining": $( "#useOldest", $GRAPH_INSTANCE )
							.is( ':checked' )
				};
				serviceUrl = targetMetricsUrl;

				serviceUrl += host;

			}
//		alert("got 1" + serviceUrl) ;
			getIntervals( _metricType, serviceUrl, paramObject,
					$GRAPH_INSTANCE );

			console.groupEnd();

		}

		// will display 30 second updates for this or fewer
		var HOST_COUNT_SAMPLE_THRESHOLD = 10;

		this.getSampleInterval = getSampleInterval;
		function getSampleInterval( $GRAPH_INSTANCE, collectionIntervals ) {

			var sampleTimeSelection = $(
					'input[name=interval' + $GRAPH_INSTANCE.attr( "id" )
					+ ']:checked' ).val();
			var numDays = $( "#numberOfDays", $GRAPH_INSTANCE ).val();

			var useAutoSelect = $( ".useAutoInterval", $GRAPH_INSTANCE ).is(
					':checked' );

			if ( typeof sampleTimeSelection == 'undefined' ) {
				// By default -use the longest time interval
				// serviceUrl += "/" + _metricType + "_" +
				// _lastSamplesAvailable[_lastSamplesAvailable.length-1]
				if ( typeof getIntervalSamples == 'undefined' ) {
					// console.log("Intervals are null") ;
					sampleTimeSelection = _lastSamplesAvailable[0];

					if ( useAutoSelect && _hostArray.length > HOST_COUNT_SAMPLE_THRESHOLD ) {

						$( '.padLatest', $GRAPH_INSTANCE ).prop( "checked", false );

						sampleTimeSelection = _lastSamplesAvailable[_lastSamplesAvailable.length - 2];
						if ( _hostArray.length > HOST_COUNT_SAMPLE_THRESHOLD + 10 )
							sampleTimeSelection = _lastSamplesAvailable[_lastSamplesAvailable.length - 1];
					}

					// console.log("\n\n  ========  _lastSamplesAvailable" + sampleTimeSelection)
				} else {
					// console.log("Using passed vals") ;
					// This is escape when using metrics browser as initial samples
					// are not there
					sampleTimeSelection = collectionIntervals[0];
				}
			} else if ( useAutoSelect ) {
				// Select based on number of days chosen
				var selectedIndex = 0;
				if ( numDays > 3 ) {
					selectedIndex = _lastSamplesAvailable.length - 1;
				}
				if ( numDays > 3 && numDays < 14
						&& _lastSamplesAvailable.length == 3 ) {
					selectedIndex = _lastSamplesAvailable.length - 2;
				}

				if ( _hostArray.length > HOST_COUNT_SAMPLE_THRESHOLD ) {
					console.log( "\n\n  ========  Updating auto sample time" )
					selectedIndex = _lastSamplesAvailable.length - 2;
					$( '.padLatest', $GRAPH_INSTANCE ).prop( "checked", false );
				}
				if ( _hostArray.length > HOST_COUNT_SAMPLE_THRESHOLD + 10 ) {
					console.log( "\n\n  ========  Updating auto sample time" )
					selectedIndex = _lastSamplesAvailable.length - 1;
					$( '.padLatest', $GRAPH_INSTANCE ).prop( "checked", false );
				}

				// console.log("Selecting: " + selectedIndex) ;
				sampleTimeSelection = _lastSamplesAvailable[selectedIndex];
				$(
						'input:radio[name=interval'
						+ $GRAPH_INSTANCE.attr( "id" ) + ']:nth('
						+ selectedIndex + ')', $GRAPH_INSTANCE ).prop(
						'checked', true );
			}
			// console.log(" sampleTimeSelection: " + sampleTimeSelection) ;
			return sampleTimeSelection;
		}



		// Gets the sample intervals for UI to update
		this.getIntervals = getIntervals;
		function getIntervals( type, serviceUrl, paramObject, $GRAPH_INSTANCE ) {
			console.log( "getIntervals(): " , type , " url: " , serviceUrl , " performance packages:" , _performanceModel );

			// if (typeof _modelPackages != 'undefined') {
			if ( isCurrentModePerformancePortal() ) {

				intervalsForPerformance( type, serviceUrl, paramObject,
						$GRAPH_INSTANCE );

			} else {
				intervalsForVm( type, serviceUrl, paramObject, $GRAPH_INSTANCE );
			}
		}

		this.intervalsForPerformance = intervalsForPerformance;
		function intervalsForPerformance( type, serviceUrl, paramObject,
				$GRAPH_INSTANCE ) {
			// Used for performance.js
			var selectedPackage = _performanceModel.getSelectedProject();
			console.log( "intervalsForPerformance(): Using model packages for: " ,  
					serviceUrl , " type:" ,  type, " package: ", selectedPackage );
			// alert("got " + serviceUrl) ;

			_lastSamplesAvailable = _performanceModel.getPackageDetails( selectedPackage ).metrics[type];

			var sampleTimeSelection = getSampleInterval( $GRAPH_INSTANCE,
					_lastSamplesAvailable );

			if ( serviceUrl.indexOf( targetMetricsUrl ) != -1 ) {

				var metricUrl = _metricType;
				if ( $( ".triggerJmxCustom" ).length == 1 ) {
					metricUrl += $( ".triggerJmxCustom" ).text();
				}
				// console.log("Url from config: " + metricUrl) ;
				serviceUrl += "/" + metricUrl + "_" + sampleTimeSelection
						+ "?callback=?";
				var useBuckets = $( ".useBuckets", $GRAPH_INSTANCE ).is(
						':checked' );
				if ( useBuckets ) {

					$.extend( paramObject,
							{
								"bucketSize": $( ".bucketSize",
										$GRAPH_INSTANCE ).val(),
								"bucketSpacing": $( ".bucketSpacing",
										$GRAPH_INSTANCE ).val(),
							} );
				}

			} else {
				$.extend( paramObject, {
					"resourceTimer": sampleTimeSelection
				} );
			}

			console.log( "intervalsForPerformance(): metric Url: " + serviceUrl );

			// console.log("Getting metrics from: " + serviceUrl + "\n params: " +
			// JSON.stringify(paramObject, null, "\t")
			// + " Sample interval: " + sampleTimeSelection) ;

			$.getJSON( serviceUrl, paramObject )

					.done( function ( metricJson ) {
						metricDataSuccess( metricJson, true );
					} )

					.fail( function ( jqXHR, textStatus, errorThrown ) {

						// handleConnectionError("Performance Intervals", errorThrown);
						// console.log("Performance Intervals" + JSON.stringify(jqXHR, null, "\t")) ;
						handleConnectionError( "Performance Metrics", errorThrown );
					} );
		}

		this.intervalsForVm = intervalsForVm;
		function intervalsForVm( type, serviceUrl, paramObject,
				$GRAPH_INSTANCE ) {

			console.log( "intervalsForVm() :" + serviceUrl );

			// invoke on CSAP VMs when displaying metrics
			$.getJSON( "metricIntervals/" + type, {
				dummyParam: "dummyVal"
			} )

					.done( function ( resultsJson ) {
						// console.log( "_lastSamplesAvailable: " +
						// _lastSamplesAvailable + " intervals: "
						// +JSON.stringify(resultsJson, null, "\t") ) ;

						// Performance ui uses this settings
						_lastSamplesAvailable = resultsJson;
						var sampleTimeSelection = getSampleInterval(
								$GRAPH_INSTANCE,
								_lastSamplesAvailable );

						// if we are using historical, tweak the url
						if ( serviceUrl.indexOf( targetMetricsUrl ) != -1 ) {
							serviceUrl += "/" + _metricType + "_"
									+ sampleTimeSelection + "?callback=?";
							var useBuckets = $( ".useBuckets",
									$GRAPH_INSTANCE ).is( ':checked' );
							if ( useBuckets ) {

								$.extend( paramObject, {
									"bucketSize": $( ".bucketSize",
											$GRAPH_INSTANCE ).val(),
									"bucketSpacing": $( ".bucketSpacing",
											$GRAPH_INSTANCE ).val(),
								} );
							}

						} else {
							$.extend( paramObject, {
								"resourceTimer": sampleTimeSelection
							} );
						}

						// console.log("Getting metrics from: " + serviceUrl
						// + "\n params: " + JSON.stringify(paramObject,
						// null, "\t")
						// + " Sample interval: " + sampleTimeSelection) ;

						$.getJSON( serviceUrl, paramObject )

								.done(
										function ( metricJson ) {
											metricDataSuccess( metricJson, true );
										} )

								.fail(
										function ( jqXHR, textStatus, errorThrown ) {
											handleConnectionError(
													"Vm Metrics",
													errorThrown );
										} );

					} )

					.fail(
							function ( jqXHR, textStatus, errorThrown ) {

								handleConnectionError(
										"VM Intervals"
										+ serviceUrl, errorThrown );
							} );
		}

		function alertOrLog( message ) {
			if ( _hostArray.length <= 1 )
				alertify.alert( message );
			else
				alertify.notify( message );
		}

		var _lastSamplesAvailable = "NONE";

		function metricDataSuccess( hostGraphData, isSchedule ) {

			// console.log("metricDataSuccess() hostGraphData: " + hostGraphData.data ) 

			if ( hostGraphData == null ) {
				alertOrLog( "No Data Available for selected interval. Try another VM or time interval. " );
				return;
			}
			if ( hostGraphData.error ) {

				if ( hostGraphData.host ) {
					setTimeout( function () {

						$( '#instanceTable tbody tr.selected[data-host="' + hostGraphData.host + '"] input' )
								.each( function () {
									$( this ).parent().parent().removeClass( "selected" );
									$( this ).prop( "checked", false ).trigger( "change" );
								} );
					}, 500 );
				}
				alertOrLog( "Cannot display graph: " + hostGraphData.error );
				return;
			}
			if ( hostGraphData.errors ) {
				alertOrLog( "Cannot display graph: " + hostGraphData.errors );
				return;
			}
			if ( hostGraphData.attributes == undefined ) {
				//$("ul[data-group='Companies'] li[data-company='Microsoft']").attr

				console.log( "metricDataSuccess(): Did not find attributes in response, cannot display graph: "
						+ "<br> Verify that collection interval exists" );
				return;
			}
			if ( hostGraphData.attributes.errorMessage ) {
				alertOrLog( "Found errorMessage, cannot display graph: "
						+ hostGraphData.attributes.errorMessage
						+ "<br> Verify that collection interval exists" );
				return;
			}
			if ( hostGraphData.data.timeStamp == null ) {
				alertOrLog( "No timeStamp in data for selected interval. Try another VM or time interval. " + hostGraphData.attributes.hostName );
				return;
			}

			// hook for admin
			if ( uiSettings.isForceHostToLocalhost ) {
				console
						.log( "isForceHostToLocalhost is set, overriding response host name: "
								+ hostGraphData.attributes["hostName"] );
				hostGraphData.attributes["hostName"] = "localhost";
			}

			var host = hostGraphData.attributes.hostName;

			var $newGraphContainer = $( "." + host + "Container",
					$GRAPH_INSTANCE );
			;
			$( ".plotContainer", $newGraphContainer ).find( "*" ).off();
			var plotContainer = $( ".plotContainer", $newGraphContainer );


			plotContainer.empty(); // get rid of existing content

			var hostAuto = $( ".autoRefresh", $newGraphContainer );

			if ( hostAuto.prop( 'checked' ) == false ) {
				return

			}

			var optionallyAppendedData = _dataManager.addHostData( hostGraphData );

			configureSettings( hostGraphData.attributes.samplesAvailable,
					hostGraphData.attributes.sampleInterval,
					hostGraphData.attributes.graphs,
					hostGraphData.attributes.titles );

			//
			if ( hostGraphData.attributes.servicesAvailable != undefined ) {
				updateServiceCheckboxes( hostGraphData.attributes.servicesAvailable, hostGraphData.attributes.servicesRequested );
			}

			// console.log("Invoking drawGraph") ;
			hostGraphs.draw( _currentResourceGraphInstance, $newGraphContainer,
					optionallyAppendedData, host, _graphsInitialized );


			if ( isSchedule ) {
				scheduleUpdate( host, hostGraphData, $newGraphContainer )
			}

			settings.postDrawEvents( $newGraphContainer, $GRAPH_INSTANCE, hostGraphData.attributes.numDays )

			// if graph is provided - maximize the selected graph
			if ( _graphParam != null
					&& $( "." + _graphParam, $GRAPH_INSTANCE ).is( ":visible" ) ) {
				setTimeout( function () {
					$( "." + _graphParam + " .plotMinMaxButton" ).trigger( "click" );
					_graphParam = null;
				}, 500 )

			}


		}

		var _graphsInitialized = new $.Deferred();

		this.getGraphLoadedDeferred = getGraphLoadedDeferred;
		function getGraphLoadedDeferred() {
			return _graphsInitialized;
		}

		function scheduleUpdate( host, hostGraphData, $newGraphContainer ) {
			var durationMs = hostGraphData.attributes.sampleInterval * 1000;
			$( ".hostInterval", $newGraphContainer ).html(
					(durationMs / 60000).toFixed( 2 ) );
			var numRec = 1;

			if ( !$( ".useHistorical", $GRAPH_INSTANCE ).is( ':checked' ) ) {

				_timersArray[host] = setTimeout( function () {
					// Get the latest points only
					getMetrics( numRec, $newGraphContainer, host );
				}, durationMs );
			}
		}

		/**
		 * Only need to init when graph type is changed
		 * 
		 * @param samplesAvailable
		 */
		var _settingsConfiguredOnce = false;
		function configureSettings( samplesAvailable, currentInterval, graphsAvailable, graphTitles ) {

			// alert( $("#sampleIntervals input").length)
			// if ($(".sampleIntervals input", $GRAPH_INSTANCE).length != 0)
			// return;
			if ( _settingsConfiguredOnce )
				return;

			_settingsConfiguredOnce = true;
			//console.log("configureOptionsForSelectedHosts() - " + servicesAvailable) ;
			$( ".sampleIntervals", $GRAPH_INSTANCE ).empty();

			settings.dialogSetup( settingsUpdated, $GRAPH_INSTANCE );


			$( '.csv', $GRAPH_INSTANCE ).change( function () {
				// $('div.legend *').style("left", "30px" ) ;
				// triggerGraphRefresh();
				return; // 
			} );

			$( '.uncheckAll' ).click( function () {

				$( 'input', $( this ).parent().parent() ).prop( "checked", false ).trigger( "change" );
				return false; // prevents link
			} );
			$( '.checkAll' ).click( function () {

				$( 'input', $( this ).parent().parent() ).prop( "checked", true );
				return false; // prevents link
			} );
			// $("#intervals").empty() ;
			// <input class="custom" id="short" type="radio" name="intervalId"
			// value="-1" ><label class="radio" title="Shows Last 5 hours"
			// for="short">30 sec</label>

			var samples = samplesAvailable;
			if ( typeof samplesAvailable == 'undefined' ) {
				samples = _lastSamplesAvailable;
				// console.log( "samples: " + samples + " _lastSamplesAvailable: " +
				// _lastSamplesAvailable ) ;
			} else {
				_lastSamplesAvailable = samplesAvailable;
			}
			// console.log("Updating Samples list: " + samples) ;
			for ( var i = 0; i < samples.length; i++ ) {
				var id = "interval" + samples[i];

				var inputElement = jQuery( '<input/>', {
					id: id,
					class: "custom",
					type: "radio",
					checked: "checked",
					value: samples[i],
					name: "interval" + $GRAPH_INSTANCE.attr( "id" )
				} ).appendTo( $( ".sampleIntervals", $GRAPH_INSTANCE ) );

				var labelElement = jQuery( '<label/>', {
					class: "radio",
					"for": id,
					text: samples[i] + " Seconds"
				} ).appendTo( $( ".sampleIntervals", $GRAPH_INSTANCE ) );

			}

			var sortedGraphs = new Array();
			for ( var graphName in graphsAvailable ) {
				sortedGraphs.push( graphName );
			}
			sortedGraphs = sortedGraphs.sort( function ( a, b ) {
				return a.toLowerCase().localeCompare( b.toLowerCase() );
			} );
			// for ( var graphName in graphsAvailable ) {
			for ( var i = 0; i < sortedGraphs.length; i++ ) {
				var graphName = sortedGraphs[i];
				//var label = splitUpperCase( graphName );
				var label = graphTitles[ graphName ];

				var $graphLabel = jQuery( '<label/>', {
					class: "configLabels",
					html: label
				} ).appendTo( $( ".graphCheckboxes", $GRAPH_INSTANCE ) );

				var $graphCheckbox = jQuery( '<input/>', {
					id: graphName + "CheckBox",
					class: "customCheck graphs",
					type: "checkbox",
					value: graphName
				} ).prependTo( $graphLabel );


				// console.log("graph.configureOptionsForSelectedHosts(): previousGraphsSelection: " +  JSON.stringify(previousGraphsSelection, null, "\t") ) ;
				if ( _lastGraphsChecked != null ) {
					var graphSelect = _lastGraphsChecked[ graphName ];
					console.log( "graphSelect: " + graphSelect );
					if ( graphSelect == true || graphSelect == undefined ) {
						$graphCheckbox.prop( "checked", true );
					}

				} else {
					$graphCheckbox.prop( "checked", true );
				}

			}

		}


		function updateServiceCheckboxes( servicesAvailable, servicesRequested ) {

			var curCheckBoxCount = $( ".serviceCheckbox", $GRAPH_INSTANCE ).length;
			console.log( "updateServiceCheckboxes() size:" + curCheckBoxCount + " servicesAvailable size: " + servicesAvailable.length );

			if ( (servicesAvailable.length + 1) == curCheckBoxCount )
				return;

			$( ".serviceCheckboxes", $GRAPH_INSTANCE ).empty();


			var $serviceLabel = jQuery( '<label/>', {
				class: "configLabels",
				text: "Total Vm"
			} ).appendTo( $( ".serviceCheckboxes", $GRAPH_INSTANCE ) );

			var $serviceInput = jQuery( '<input/>', {
				id: "serviceCheckboxtotalCpu",
				class: "custom serviceCheckbox servicenameCheck",
				type: "checkbox",
				value: "totalCpu",
				name: "interval" + $GRAPH_INSTANCE.attr( "id" )
			} ).prependTo( $serviceLabel );


			servicesAvailable.sort( function ( a, b ) {
				return a.toLowerCase().localeCompare( b.toLowerCase() );
			} );

			for ( var i = 0; i < servicesAvailable.length; i++ ) {

				var serviceName_port = servicesAvailable[i];
				var data_service = serviceName_port
				var label = serviceName_port;
				// console.log("updateServiceCheckboxes() Adding:" + label) ;
				var foundIndex = serviceName_port.indexOf( "_" );


				if ( foundIndex > 3 ) {
					var nameOnly = serviceName_port.substring( 0, foundIndex );
					label = nameOnly + "<span>" + serviceName_port.substring( foundIndex + 1 ) + "</span>";
					if ( isCurrentModePerformancePortal() ) {
						data_service = nameOnly;
					}
				}


				var $serviceLabel = jQuery( '<label/>', {
					class: "configLabels",
					html: label
				} ).appendTo( $( ".serviceCheckboxes", $GRAPH_INSTANCE ) );

				var $serviceInput = jQuery( '<input/>', {
					id: "serviceCheckbox" + serviceName_port,
					class: "custom serviceCheckbox servicenameCheck",
					type: "checkbox",
					value: serviceName_port,
					"data-servicename": data_service,
					name: "interval" + $GRAPH_INSTANCE.attr( "id" )
				} ).prependTo( $serviceLabel );

				if ( $.inArray( data_service, servicesRequested ) != -1 ||
						$.inArray( serviceName_port, servicesRequested ) != -1 ) {
					// Note that multiple instnances on same host match the first on the first, and second on refreshes
					$serviceInput.prop( "checked", true );
				}

				// console.log("updateServiceCheckboxes() Adding:" + label + " selected: " + $serviceInput.prop( "checked") ) ;


			}
			$( ".serviceCheckboxes", $GRAPH_INSTANCE ).show();
		}

		Date.prototype.stdTimezoneOffset = function () {
			var jan = new Date( this.getFullYear(), 0, 1 );
			var jul = new Date( this.getFullYear(), 6, 1 );
			return Math.max( jan.getTimezoneOffset(), jul.getTimezoneOffset() );
		}

		Date.prototype.dst = function () {
			return this.getTimezoneOffset() < this.stdTimezoneOffset();
		}

		var _stackHost = "";
		this.setStackHostContainer = function ( hostName ) {
			_stackHost = hostName;
			console.log( " setStackHostContainer: " + _stackHost )
		}
		var _defaultOffset = -6;


		this.reDraw = function () {
			console.log( "reDraw(): " + $GRAPH_INSTANCE.attr( "id" ) );

			var dialog = null;
			if ( window.csapGraphSettings == undefined ) {
				dialog = alertify.notify( "Graphs are being redrawn", 0 );
			}
			// plot does not handle axis well: plot.resize().setupGrid().draw()

			setTimeout( function () {
				_dataManager.clearStackedGraphs();
				for ( var i = 0; i < _hostArray.length; i++ ) {
					var host = _hostArray[i];
					if ( host == _stackHost )
						continue; // always do last
					// console.log( "Drawing: " +  host);
					// race condition - the last graph should be the same.
					var lastData = _dataManager.clearHostData( host );
					if ( lastData != null )
						metricDataSuccess( lastData, false );
				}
				if ( _stackHost != null ) {
					var lastData = _dataManager.clearHostData( _stackHost );
					if ( lastData != null )
						metricDataSuccess( lastData, false );
				}
				if ( dialog != null )
					dialog.dismiss();

			}, 100 );

		}



		// initialize called last to set context
		initialize( containerId, metricType );

	}

	return ResourceGraph;

} );