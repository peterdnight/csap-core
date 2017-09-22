
define( ["graphPackage", "reportPackage", "model", "hostSelectForGraphs"], function (ResourceGraph, reports, model, hostSelectForGraphs) {

	 console.log( "Module loaded : tabs" );


	 var serviceGraph = null;
	 var resourceGraph = null;
	 var jmxGraph = null;
	 var customJmxViews = {
		  "Java Heap": {
				graphs: ["Cpu_As_Reported_By_JVM", "heapUsed", "minorGcInMs"],
				graphMerged: {"heapUsed": "heapMax", "minorGcInMs": "majorGcInMs"},
				graphSize: {
					 Cpu_As_Reported_By_JVM: {"width": "100%", "height": "100"},
					 heapUsed: {"width": "100%", "height": "35%"},
					 minorGcInMs: {"width": "100%", "height": "45%"}
				}
		  },
		  "Tomcat Http": {
				graphs: ["Cpu_As_Reported_By_JVM", "sessionsCount", "httpRequestCount", "httpKbytesReceived", "httpProcessingTime"],
				graphMerged: {"httpKbytesReceived": "httpKbytesSent", "sessionsCount": "sessionsActive", "httpRequestCount": "tomcatConnections"}},
		  "Java Thread": {
				graphs: ["Cpu_As_Reported_By_JVM", "jvmThreadCount", "tomcatThreadsBusy"], 
				graphMerged: {"jvmThreadCount": "jvmThreadsMax", "tomcatThreadsBusy": "tomcatThreadCount"}}
	 }

	 return {
		  //
		  activateTab: function (tabId) {
				activateTab( tabId )
		  },
		  //
		  reInitJmxGraphs: function ( ) {
				reInitJmxGraphs();
		  },
		  //
		  reInitGraphs: function ( ) {
				reInitGraphs();
		  },
		  //
		  updateHostLabels: function () {
				updateHostLabels();
		  },
		  //
		  reInitMetricGraphs: function ( ) {
				reInitGraphs();
		  },
		  //
		  registerTabChanges: function () {
				registerForTabActivates();
		  }


	 }

	 function registerForTabActivates() {
		  $( "#reportTabs" ).tabs( {
				activate: function (event, ui) {
//					 console.log( "Tab: " + ui.newTab.text() + " report: "
//								+ ui.newTab.data( "report" ) + " metric:"
//								+ ui.newTab.data( "metric" ) );

					 $( ".category input" ).prop( 'checked', false );
					 $( "#hostSelection" ).hide();

					 $( "#visualizeSelect" ).val( "table" );
					 reports.hide();

					 $( "#reportOptions" ).prependTo(
								"#" + ui.newTab.data( "report" ) + "Div" );
					 if ( ui.newTab.data( "report" ) != undefined ) {
						  console.log( "\n\n Report Tab Activited: " + ui.newTab.data( "report" ) );
						  reports.resetReportResults();
						  $( "#compareLabel" ).show();
						  if ( ui.newTab.data( "report" ) == "userid" )
								$( "#compareLabel" ).hide();

						  if ( uiSettings.reportRequest.indexOf( "jmx" ) == 0 ) {
								// use request params on
								// first go
								$( "#isShowJmx" ).prop( "checked", true );

								if ( uiSettings.serviceParam == "null" ) {
									 reports.getReport( uiSettings.reportRequest );
								} else {
									 reports.getReport( uiSettings.reportRequest, uiSettings.serviceParam );
								}

								uiSettings.reportRequest = "";
						  } else if ( uiSettings.reportRequest.indexOf( "service" ) == 0 ) {

								if ( uiSettings.serviceParam == "null" ) {
									 reports.getReport( uiSettings.reportRequest );
								} else {
									 reports.getReport( uiSettings.reportRequest, uiSettings.serviceParam );
								}

								uiSettings.reportRequest = "";


						  } else {
								// Use Tab Data
								if ( ui.newTab.data( "report" ) == "service" && reports.getLastServiceReport() != "" ) {
									 reports.getReport( reports.getLastServiceReport(), reports.getLastService() );
								} else if ( ui.newTab.data( "report" ) == "compute" ) {


									 reports.runSummaryReports( true );

									 // Options do not apply to compute tab, move them out
									 $( "#reportOptions" ).prependTo( "#vmDiv" );

								} else {
									 reports.getReport( ui.newTab.data( "report" ) );
								}
						  }
					 } else {

						  // handle graph tabs - need to defer until model is loaded....
						  // graph needs it
						  $.when( model.getModelLoadedDeffered() ).done( function () {
								showGraphTab( ui.newTab.data( "metric" ) )
						  } );

					 }

				}
		  } );
	 }

	 function buildGraphObject(containerId, metricType) {

		  console.log( "buildGraphObject: " + containerId + " type: " + metricType );

		  var theNewGraph = new ResourceGraph(
					 containerId, metricType, $( "#lifeSelect" ).val(),
					 uiSettings.appId, uiSettings.metricsDataUrl, model );

		  if ( metricType == "jmx" ) {
				theNewGraph.addCustomViews( customJmxViews );
		  }
		  // needed to pull model related information from events DB
//		  console.log( "buildGraphObject() model: " + model.getSelectedProject()) ;
//		  theNewGraph.setModel( model );

		  return theNewGraph;
	 }

	 // JMX tab is double used - standard or custom. So we need a full init when switching
	 function reInitJmxGraphs() {

		  // Moved back into template while new graph is constructed.
		  $( "#appMetricsSection" ).appendTo( $( "#appMetricsTemplate" ) );

		  console.log( "reInitJmxGraphs(): rebuilding graphs" );
		  if ( jmxGraph != null ) {
				jmxGraph.clearRefreshTimers();
				$( "#jmxGraphs" ).empty();
				jmxGraph = buildGraphObject( "#jmxGraphs", getJmxType() );
				_currentGraph = jmxGraph;
				$.when( jmxGraph.getGraphLoadedDeferred() ).done( function () {
					 $( "#appMetricsSection" ).prependTo( $( "div#jmxGraphs div.resourceConfigDialog" ) );

				} );
		  } else {
				console.log( "reInitJmxGraphs() no active jmx graphs" );
		  }
		  alertify.closeAll();
		  // reInitGraphs();
	 }


	 function activateTab(tabId) {
		  var tabIndex = $( 'li[data-tab="' + tabId + '"]' ).index();

		  console.log( "activateTab(): " + tabId + " with index: " + tabIndex );

		  // $("#jmx" ).prop("checked", true) ;

		  $( "#reportTabs" ).tabs( "option", "active", tabIndex );

		  return;
	 }


	 /**
	  * 
	  * Graphs will remain active even after tab is changed; making switching back and forth efficient
	  * - Host selection are for all graphs; if they change on one - then when previous graph is displayed it will 
	  *   be refreshed.
	  * 
	  */
	 var _currentGraph = null;
	 function showGraphTab(metric) {
		  // Move to

		  $( "#graphCustomizeDiv" ).appendTo( "#" + metric + "GraphDiv" );

		  console.log( "showGraphTab():   " + metric );
		  $( "#" + metric ).prop( "checked", true );

		  // Very hacky - graph.js customJmx  is managed by DOM element
		  // We remove and selectively reenable when JMX
		  $( "#jmxCustomWhenClassSet" ).removeClass( "triggerJmxCustom" );


		  $( ".graphDiv" ).hide();

		  $( "#" + metric + "Graphs" ).show();

		  if ( $( "#hostDisplay input:checked" ).length == 0 ) {
				alertify.notify( "Defaulting first host" );
				$( "#hostDisplay input:eq(0)" ).prop( "checked", true );
		  }

		  var isGraphRefreshed = false; // race condition on displaying twice

		  $( "#applicationNameLabel" ).hide();
		  if ( metric == "service" ) {
				if ( serviceGraph == null ) {
					 serviceGraph = buildGraphObject( "#serviceGraphs", "service" );
					 isGraphRefreshed = true;
				}
				_currentGraph = serviceGraph;
		  }
		  if ( metric == "resource" ) {
				if ( resourceGraph == null ) {
					 resourceGraph = buildGraphObject( "#resourceGraphs", "resource" );
					 isGraphRefreshed = true;
				}
				_currentGraph = resourceGraph;
		  }
		  if ( metric == "jmx" ) {

				checkForCustomJmx( $( "#appMetricsSection input:checked" ) );

				if ( jmxGraph == null ) {
					 // jmxGraph = buildGraphObject( "#jmxGraphs"  , getJmxType() );
					 updateCustomJmxService();
					 //
					 // jmxCustom services - require standard JMX attributes loaded first

					 isGraphRefreshed = true;

					 if ( uiSettings.appGraphParam != "null" ) {
						  //$.when( jmxGraph.getGraphLoadedDeferred() ).done( function () {
						  console.log( "Switching to appgraph display using id: " + "#"
									 + uiSettings.serviceParam + "jmxCustom, size found: "
									 + $( "#" + uiSettings.serviceParam + "jmxCustom" ).length );
						  $( "#" + uiSettings.serviceParam + "jmxCustom" ).trigger( "click" );
						  uiSettings.appGraphParam = "null";
						  jmxGraph = buildGraphObject( "#jmxGraphs", getJmxType() );
//								serviceRequestParamForGraph = "null";

						  // } );
					 } else {
						  jmxGraph = buildGraphObject( "#jmxGraphs", getJmxType() );
					 }

					 $.when( jmxGraph.getGraphLoadedDeferred() ).done( function () {
						  $( "#appMetricsSection" ).prependTo( $( "div#jmxGraphs div.resourceConfigDialog" ) );
					 } );

				}
				_currentGraph = jmxGraph;
		  }

		  _currentGraph.dumpInfo();

		  if ( $( "#hostCusomizeDialog input:checked" ).length > 0
					 && _currentGraph.getHostCount() != $( "#hostCusomizeDialog input:checked" )
					 .length ) {
				//if host counts have changed on another graph, trigger re-init
				updateCustomJmxService();

				if ( !isGraphRefreshed ) {
					 // why reInit? Because hostSelection may have changed.
					 reInitGraphs();
				}

		  }
		  $( "#reportsSection" ).hide();
		  $( "#reportsSection table" ).hide();

		  updateHostLabels();

	 }

	 function updateHostLabels() {

		  $( "#hostSelection" ).show();
		  $( "#multiHostCustomize" ).show();
		  var numHosts = $( "#hostDisplay input:checked" ).length;
		  $( "#hostSelectCount" ).text( numHosts );
		  if ( numHosts <= 1 )
				$( "#multiHostCustomize" ).hide();


	 }
	 function reInitGraphs() {

		  console.log( "reInitGraphs() - reDrawing" );

		  if ( $( "#hostCusomizeDialog input:checked" ).length == 0 )
				return;

		  _currentGraph.settingsUpdated();

		  console.log( "reInitGraphs(): host count:  " + _currentGraph.getHostCount()
					 + " numChecked: " + $( "#hostCusomizeDialog input:checked" ).length );

	 }

	 function reInitMetricGraphs() {
		  if ( serviceGraph != null ) {
				serviceGraph.clearRefreshTimers();
				$( "#serviceGraphs" ).empty();
		  }

		  serviceGraph = buildGraphObject( "#serviceGraphs", "service" );

	 }


	 function updateCustomJmxService() {

		  var selectedPackage = model.getSelectedProject();

		  console.log( "updateCustomJmxService() selectedProject: " + selectedPackage );

		  $( ".jmxRadio" ).off();

		  $( "#jmxCustomServicesDiv" ).empty();

		  var serviceInstances = model.getPackageDetails( selectedPackage ).instances.instanceCount;

		  for ( var i = 0; i < serviceInstances.length; i++ ) {

				var serviceInstance = serviceInstances[i];

				if ( !serviceInstance.hasCustom )
					 continue;

				var serviceDiv = jQuery( '<div/>', {
					 class: "svcDiv",
				} );

				var jmxCustomInput = jQuery( '<input/>', {
					 id: serviceInstance.serviceName + "jmxCustom",
					 "data-servicename": serviceInstance.serviceName,
					 type: "radio",
					 class: "jmxRadio",
					 name: "jmxMetricsRadio",
					 title: "Custom attributes"
				} ).css( "margin-right", "0.25em" );
				// console.log("updateCustomJmxService() - triggering: " + uiSettings.serviceParam + " serviceInstance.serviceName: " + serviceInstance.serviceName) ;
				if ( uiSettings.serviceParam == serviceInstance.serviceName
						  && uiSettings.customParam == "jmxCustom" ) {
					 console.log( "updateCustomJmxService() - triggering: " + uiSettings.serviceParam );
					 jmxCustomInput.prop( "checked", true );
					 $( "#jmxCustomWhenClassSet" ).addClass( "triggerJmxCustom" );

					 uiSettings.serviceParam = "";
				}

				var jmxLabel = jQuery( '<label/>', {
					 class: "configLabels",
					 text: serviceInstance.serviceName
				} );

				jmxLabel.prepend( jmxCustomInput );
				serviceDiv.append( jmxLabel );

				$( "#jmxCustomServicesDiv" ).append( serviceDiv );
		  }

		  $( ".jmxRadio" )
					 .click(
								function () {
									 checkForCustomJmx( $( this ) );
									 reInitJmxGraphs();

								} );

	 }

	 function getJmxType() {
		  var type = $( "#jmxCustomWhenClassSet" ).text();

		  return "jmx" + type;
	 }


	 function checkForCustomJmx(selectedJmx_JQ) {

		  var serviceName = selectedJmx_JQ.data( "servicename" );
		  console.log( "checkForCustomJmx():  jmxChecked: " + serviceName );


		  if ( serviceName != "standard" ) {

				$( "#applicationNameLabel" ).text( "Service: " + serviceName ).show();
				$( "#jmxCustomWhenClassSet" )
						  .addClass( "triggerJmxCustom" )
						  .text( serviceName );


		  } else {
				// $( "#applicationNameLabel" ).hide();
				$( "#jmxCustomWhenClassSet" )
						  .removeClass( "triggerJmxCustom" );
		  }

		  // 
	 }

} );