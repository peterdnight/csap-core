define( ["./flotUtils", "./graphLayout", "./settings"], function ( flotUtils, graphLayout, settings ) {

	console.log( "Module loaded: graphPackage/hostGraphs" );

	var _statsForHostsGraphs = new Object();


	return {
		draw: function ( resourceGraph, $newContainer, metricsJson, host, _graphsInitialized ) {
			console.groupCollapsed( "Drawing graphs for host: " + host );
			drawGraphs( resourceGraph, $newContainer, metricsJson, host, _graphsInitialized );
			console.groupEnd();
		}
	};

	function getGraphId( graphName ) {
		var graphId = graphName;
		// backwards compatiblity: attribute was inferred from graphs, which were tweak for 
		switch ( graphName ) {
			case "Cpu_15s":
				graphId = "topCpu";
				break;

			case "diskUtilInMB":
				graphId = "diskUtil";
				break;

			case "rssMemoryInMB":
				graphId = "rssMemory";
				break;

			case "Cpu_As_Reported_By_JVM":
				graphId = "cpuPercent";
				break;

		}

		return graphId;
	}

	function getGraphCheckboxId( graphName, $currentGraph ) {

		var checkId = getGraphId( graphName ) + "CheckBox";
		if ( $( "#" + checkId, $currentGraph ).is( ':checked' ) ) {
			return checkId;
		}

		return  graphName + "CheckBox";
	}

	function isGraphSelected( graphName, $currentGraph ) {

		if ( $( "#" + graphName + "CheckBox", $currentGraph ).is( ':checked' ) ) {
			return true;
		}
		var checkId = getGraphId( graphName ) + "CheckBox";
		if ( $( "#" + checkId, $currentGraph ).is( ':checked' ) ) {
			return true;
		}

		return false;
	}

	function drawGraphs( resourceGraph, $newContainer, metricsJson, host, onCompleteDeferred ) {
// console.log("drawGraph(): " + JSON.stringify(metricsJson, null, "\t") ) ;
		var $currentGraph = resourceGraph.getCurrentGraph();
		var dataManager = resourceGraph.getDataManager();

		$( 'div.qtip:visible' ).qtip( 'hide' );

		console.log( "drawGraphs():  layout: " + $currentGraph.attr( "id" ) + " "
				+ $( ".layoutSelect", $currentGraph ).val() );
		var currentDate = new Date();
		// FLOT apis work in GMT //
		var metricsOffset = $( ".graphTimeZone", $currentGraph ).val();
		if ( metricsOffset == "Browser" ) {
			metricsOffset = currentDate.getTimezoneOffset();
		} else {
			if ( metricsOffset == "Host" ) {
				if ( typeof metricsJson.attributes.timezone == 'undefined' ) {
					alertify.notify( "Update CsAgent to get host timezone information" );
					$( ".graphTimeZone", $currentGraph ).val(
							_defaultOffset );
					metricsOffset = _defaultOffset;
				} else {
					metricsOffset = metricsJson.attributes.timezone;
				}
			}
			var today = new Date();
			if ( today.dst() && metricsOffset != 0 ) {  // no dst for GMT
				// adjust for DST which logs are using
				metricsOffset = parseFloat( metricsOffset ) + 1;
			}

			metricsOffset = metricsOffset * -60;
		}
		$( "#metricsZoneDisplay", $currentGraph ).html(
				"GMT(" + metricsOffset + ")" );

		//metricsOffset=240;
		console.log( "drawGraph(): " + metricsJson.attributes.id + " timezone: "
				+ metricsOffset );

		var flotTimeOffsetArray = dataManager.getLocalTime( metricsJson.data.timeStamp,
				metricsOffset );

		// javascript built in apis work on local. For displaying navigation to
		// work
		var sliderTimeOffsetArray = dataManager.getLocalTime( flotTimeOffsetArray, 0 - currentDate.getTimezoneOffset() );
//		var sliderTimeOffsetArray = dataManager.getLocalTime( flotTimeOffsetArray, "-"
//				+ currentDate.getTimezoneOffset() );
		// Very tricky
		// FLOT api require a full GMT offset, but local js only needs relative
		settings.modifyTimeSlider( $newContainer, flotTimeOffsetArray,
				sliderTimeOffsetArray, resourceGraph );


		if ( dataManager.getHostGraph( host ) ) {
			for ( var i = 0; i < dataManager.getHostGraph( host ).length; i++ ) {
				// Custom Hook into FLOT nav plugin.
				var currPlot = dataManager.getHostGraph( host )[i];
				// currPlot.clear() ;
				currPlot.setData( new Array() );
				currPlot.draw();
				currPlot.shutdown();
				jQuery.removeData( currPlot );
				//console.log("drawGraph(): Shutting down Plot " + i ) ;
			}
		}

		var isFlash = metricsJson.attributes.servicesAvailable != undefined
				&& metricsJson.attributes.servicesAvailable.length != metricsJson.attributes.servicesRequested.length;

		settings.flashDialogButton( isFlash, $currentGraph );


		var hostPlotInstances = new Array();
		// resource graph
		// $("#debug").html( JSON.stringify(
		// buildPoints(metricsJson.data.timeStamp,
		// metricsJson.data.usrCpu) , null,"\t") ) ;

		var $hostPlotContainer = $( ".plotContainer", $newContainer );
		graphLayout.restore( resourceGraph, $hostPlotContainer );

		var graphs = metricsJson.attributes.graphs;
		var numGraphs = 0;
		for ( var graphName in graphs ) {

			if ( !isGraphSelected( graphName, $currentGraph ) )
				continue;

			// empty graphs check 
			if ( !graphs[getGraphId( graphName )] || Object.keys( graphs[getGraphId( graphName )] ).length === 0 ) {
				console.error( "Warning - found empty graphs for: " + graphName );
				continue;
			}
			numGraphs++;
		}
		console.log( "numGraphs", numGraphs );
		if ( numGraphs == 0 ) {
			alertify.notify( "Host: " + host + " Does not contain ANY matching graphs. Remove from host selection." );
//			$("#" + host + "Check").prop('checked', false);
//			$("#" + host + "Check").trigger("click");
			// return;
		}

		if ( $( '.csv', $currentGraph ).prop( "checked" ) ) {

			alertify.notify( "Rendering csv" );

			drawCsv( graphs, metricsJson, $hostPlotContainer );

			return;
		}

		var isStackHosts = false;
		if ( $( "#isStackHosts" ).length > 0 && dataManager.getHostCount() > 1 )
			isStackHosts = $( "#isStackHosts" ).val() > -1;

		var lastGraphName = "";
		var isGroupOpen = false;
		for ( var graphName in graphs ) {


			if ( isGroupOpen ) {
				// lots of breaks and continues
				console.groupEnd( "Done: " + host + " - " + graphName );
			}
			console.groupCollapsed( "Building: " + graphName + " on " + host );
			isGroupOpen = true;

			if ( Object.keys( graphs[graphName] ).length === 0 ) {
				console.log( "Skipping: " + graphName + " on " + host + " because no keys found" );
				continue;
			}


			// if ( graphName != "topCpu") continue ;
			// alert(numGraphsChecked) ;
			// alert("Drawing: " + graphName) ;
			if ( !isGraphSelected( graphName, $currentGraph ) ) {
				console.log( "Skipping: " + graphName + " on " + host + " because it is not selected" );
				continue;
			}

			if ( window.csapGraphSettings != undefined ) {
				if ( getGraphId( graphName ) != getGraphId( window.csapGraphSettings.graph ) ) {
					console.groupCollapsed( "Skipping: " + graphName + " on " + host + " because panel selection " );
					continue;
				}
				console.log( "csapGraphSettings: ", getGraphId( window.csapGraphSettings.graph ) );
			}


			lastGraphName = graphName;
			var graphItems = graphs[graphName];
			var linesOnGraphArray = new Array(); // multiple series on each graph
			// Build the flot points

			_isNeedsLabel = true; // Full Label is used only on first item when stacked
			if ( isStackHosts && dataManager.getStackedGraph( graphName ) != undefined )
				_isNeedsLabel = false;

			_colorCount = 0;
			var numberOfSeries = 0
			for ( var seriesName in graphItems ) {
				numberOfSeries++;
			}
			;
			for ( var seriesName in graphItems ) {

				//console.log("Adding Graph: " + graphName + " Series: " + seriesName) ;

				var optionalSeries = getOptionalSeries( resourceGraph, seriesName, metricsJson, $currentGraph );

				var seriesLabelSuffix = "";
				if ( optionalSeries.title != undefined ) {
					seriesLabelSuffix = metricsJson.attributes.titles[graphName];
				}

				// support for remote collections: use label
				var collectionHost = null;
				//console.log( "Adding Graph: ", graphName, " Series: ", seriesName, " seriesLabel: ", seriesLabel );
				if ( metricsJson.attributes.remoteCollect ) {

					if ( metricsJson.attributes.remoteCollect["default"] ) {
						// remote application collections
						if ( isStackHosts ) {
							collectionHost = metricsJson.attributes.remoteCollect["default"];
						} else {
							$( ".resourceGraphTitle .hostName" ).text( metricsJson.attributes.remoteCollect["default"] );
						}
					} else {
						// remote JMX collections
						var seriesLabel = graphItems[ seriesName ];
						if ( metricsJson.attributes.remoteCollect[seriesLabel] ) {
							// service ports
							if ( isStackHosts ) {
								collectionHost = metricsJson.attributes.remoteCollect[seriesLabel];
							} else {
								$( ".resourceGraphTitle .hostName" ).text( metricsJson.attributes.remoteCollect[seriesLabel] );
							}
						}
					}
				}
				if ( collectionHost != null && host != collectionHost ) {
					seriesLabelSuffix += collectionHost;
				}

				var graphSeries = buildSeriesForGraph( $currentGraph, dataManager, graphName,
						seriesName, numberOfSeries, graphItems,
						metricsJson, flotTimeOffsetArray, seriesLabelSuffix );

				if ( graphSeries != null ) {
					linesOnGraphArray.push( graphSeries );
				} else {
					console.log( "*** graphSeries is null" );
				}

				if ( optionalSeries.title != undefined ) {

					graphSeries = buildSeriesForGraph( $currentGraph, dataManager, graphName,
							optionalSeries.seriesName, numberOfSeries + 1,
							graphs[ optionalSeries.key ],
							metricsJson, flotTimeOffsetArray, optionalSeries.title );

					linesOnGraphArray.push( graphSeries );

				}

			}

			console.log( "*** linesOnGraphArray length: " + linesOnGraphArray.length );

			var graphCheckedId = getGraphCheckboxId( graphName, $currentGraph );
			if ( isStackHosts ) {
				if ( buildStackedPanels( resourceGraph, host, graphName, numGraphs,
						$hostPlotContainer, flotTimeOffsetArray, metricsJson,
						$newContainer, hostPlotInstances,
						linesOnGraphArray, graphCheckedId ) ) {
					console.log( "Failed to render data" );
					onCompleteDeferred.resolve();
					break;
				}
			} else {
				var title = getGraphTitle( graphName, metricsJson );
				var builder = graphStatisticsBuilder( linesOnGraphArray, title );
				var $plotPanel = flotUtils.buildPlotPanel(
						title, resourceGraph, numGraphs, host,
						graphName, graphCheckedId, builder );

				var plotDiv = flotUtils.addPlotAndLegend( resourceGraph, numGraphs, graphName, host, $plotPanel, $hostPlotContainer );

				var plotOptions = flotUtils.getPlotOptionsAndXaxis( $plotPanel, graphName, flotTimeOffsetArray, linesOnGraphArray,
						$currentGraph, metricsJson.attributes.sampleInterval, dataManager.isDataAutoSampled(), false );

				console.log( "Plotting: " + title + " plotDiv: " + plotDiv.attr( "id" ) )
				var plotObj = $.plot( plotDiv, linesOnGraphArray, plotOptions );

				flotUtils.configurePanelEvents( $plotPanel, $newContainer, dataManager.getAllHostGraphs() );

				hostPlotInstances.push( plotObj );
				checkSampling( resourceGraph, flotTimeOffsetArray, linesOnGraphArray, plotDiv );
			}

		}
		if ( isGroupOpen ) {
			console.groupEnd( "Graphs all Build " );
		}

		//auto refresh keeps appending - so delete then add
		$( ".spacerForFloat", $hostPlotContainer ).remove();
		$hostPlotContainer.append( '<div class="spacerForFloat" ></div>' );
		$( ".spacerForFloat", $hostPlotContainer.parent() ).remove();
		$hostPlotContainer.parent().append( '<div class="spacerForFloat" ></div>' );

		if ( !isStackHosts ) {
			graphLayout.addContainer( $currentGraph, $hostPlotContainer );
			graphLayout.restore( resourceGraph, $hostPlotContainer );

			dataManager.updateHostGraphs( host, hostPlotInstances );

			onCompleteDeferred.resolve();

		} else {

			if ( dataManager.getStackedGraph( lastGraphName ) != undefined ) {
				var numberOfHostsSoFar = dataManager.getStackedGraph( lastGraphName ).length;
				if ( dataManager.getHostCount() == numberOfHostsSoFar ) {
					console.log( " ******************* Rendering stacked graphs: " + lastGraphName );

					graphLayout.addContainer( $currentGraph, $hostPlotContainer );
					graphLayout.restore( resourceGraph, $hostPlotContainer );


					dataManager.updateHostGraphs( host, hostPlotInstances );

					onCompleteDeferred.resolve();

				}
			}
		}

		// Doubled up to get back to top level on initial load. Schedule also adds
		console.groupEnd();


	}

	function graphStatisticsBuilder( lines, title, builder ) {

		if ( builder == null ) {
			//console.log("graphStatisticsBuilder() statsBuilder - new Object") ;
			builder = new Object();
			builder.function = flotUtils.buildHostStatsPanel;
			builder.lines = new Array();
			builder.title = new Array();
		}
		builder.lines.push( lines );
		builder.title.push( title );
		console.log( "graphStatisticsBuilder() statsBuilder length:" + builder.lines.length );
		return builder;
	}

	function getGraphTitle( graphName, metricsJson ) {
		var title = splitUpperCase( graphName );
		if ( graphName == "jmxHeartbeatMs" )
			title = "Service Heartbeat (ms)"
		//  console.log("getGraphTitle() " + graphName + " converted to: " + title) ;

		var optionalTitles = metricsJson.attributes.titles;
		if ( optionalTitles != undefined && optionalTitles[graphName] != undefined ) {
			title = optionalTitles[graphName];
		}
		return title;
	}



	function buildStackedPanels( resourceGraph, host, graphName, numGraphs,
			plotContainer, flotTimeOffsetArray, metricsJson,
			$newContainer, stackedHostPlots,
			linesOnGraphArray, checkId ) {

		var currentGraph = resourceGraph.getCurrentGraph();
		var dataManager = resourceGraph.getDataManager();

		if ( dataManager.getStackedGraph( graphName ) == undefined ) {
			console.log( "buildStackedPanels(): creating array for " + graphName );
//						+ JSON.stringify( linesOnGraphArray[0] , null,"\t") ) ;
			dataManager.initStackedGraph( graphName );
			;
		}

		//default to the first series when stacking hosts
		var graphDataOnHost = linesOnGraphArray[0];

		var userSeriesSelection = $( "#isStackHosts" ).val();

		var isShowAllSeries = (userSeriesSelection == "99");
		//console.log("isShowAllSeries: " + isShowAllSeries) ;


		for ( var seriesIndexToGraph = 0; seriesIndexToGraph < linesOnGraphArray.length; seriesIndexToGraph++ ) {

			if ( (seriesIndexToGraph != userSeriesSelection)
					&& !isShowAllSeries ) {
				continue;
			}

			graphDataOnHost = linesOnGraphArray[ seriesIndexToGraph  ];


			if ( graphDataOnHost === undefined ) {
				alertify.csapWarning( "Unable to render graphs for multiple hosts<br><br> Verify that selected hosts all contain selected service."
						+ "<br><br> When selecting multiple hosts, cluster selection will ensure VMs have consistent services deployed." );

				return true;
			}

			// push the series onto the global Variable
			dataManager.pushStackedGraph( graphName, graphDataOnHost );

			var numberOfHostsForGraphSoFar = dataManager.getStackedGraphCount( graphName );

			// Replace label on host 
			var fullLabel = graphDataOnHost.label;
			if ( isShowAllSeries ) {
				graphDataOnHost.label += " " + host;
				numberOfHostsForGraphSoFar = numberOfHostsForGraphSoFar / linesOnGraphArray.length;
			} else {
				graphDataOnHost.label = host;
			}
			//

			// give each host a distinct color
			graphDataOnHost.color = CSAP_THEME_COLORS[ numberOfHostsForGraphSoFar  ];
			graphDataOnHost.lines = { lineWidth: 2 };

			//console.log("Adding About for: " + host) ;
			var containerDiv = $( "." + host + "Container", currentGraph );
			// Draw once we have all responses
			//console.log( fullLabel + " numberOfHostsSoFar: " + numberOfHostsSoFar + " of " + dataManager.getHostCount())


			// add multiple hosts to statsBuilder array
			var statsBuilder = null;
			if ( numberOfHostsForGraphSoFar > 1 ) {
				statsBuilder = _statsForHostsGraphs[graphName];
				console.log( "statsBuilder retreived: " + statsBuilder );
			}


			_statsForHostsGraphs[graphName] = graphStatisticsBuilder(
					linesOnGraphArray,
					getGraphTitle( graphName, metricsJson ),
					statsBuilder );
			console.log( "statsBuilder: hostItems:" + _statsForHostsGraphs[graphName].lines.length );

			if ( dataManager.getHostCount() > numberOfHostsForGraphSoFar ) {
				// Stacked/restore seems to require
				containerDiv.hide();
			} else {

				resourceGraph.setStackHostContainer( host );
				containerDiv.show(); // customLayouts requires
				$( ".hostName", containerDiv ).text( "StackedVM" );

				var title = getGraphTitle( graphName, metricsJson );
				var $plotPanel = flotUtils.buildPlotPanel(
						title, resourceGraph, numGraphs, host,
						graphName, checkId, _statsForHostsGraphs[graphName] )

				var plotDiv = flotUtils.addPlotAndLegend( resourceGraph, numGraphs, graphName, host, $plotPanel, plotContainer );

//					 console.log( "\n ==== drawGraph(): Stack Plotting: " +  graphName
//								+ "  on host: " + host + ", " + curHostIndex
//								+ " of " + _hostArray.length ) ;

				var plotOptions = flotUtils.getPlotOptionsAndXaxis(
						$plotPanel, graphName, flotTimeOffsetArray, linesOnGraphArray,
						currentGraph, metricsJson.attributes.sampleInterval, dataManager.isDataAutoSampled(), true );

				var plotObj = $.plot( plotDiv, dataManager.getStackedGraph( graphName ), plotOptions );

				flotUtils.configurePanelEvents( $plotPanel, $newContainer, dataManager.getAllHostGraphs() );

				stackedHostPlots.push( plotObj );

				checkSampling( resourceGraph, flotTimeOffsetArray, linesOnGraphArray, plotDiv );
				$( ".graphLegend table", $plotPanel ).css( "display", "inline-block" );
//				var seriesLabel = jQuery( "<span/>", {
//					text: fullLabel
//				} );
				var seriesLabel = jQuery( "<span/>", {
					class: "stackLabel",
					html: fullLabel
				} );

				var graphType = resourceGraph.getCurrentGraph().attr( "id" );
				console.log( "Stack graph resource type", graphType );
				if ( graphType != "resourceGraphsClone" ) {
					var isAppData = $( ".triggerJmxCustom" ).length == 1;
					if ( !isAppData ) {
						$( "#applicationNameLabel" ).text( "Service: " + fullLabel ).show();
					}
				} else {
					$( "#applicationNameLabel" ).hide();

					// $( "#applicationNameLabel" ).text( resourceGraph.getCurrentGraph().attr("id") ).show();
				}
				$( ".graphLegend", $plotPanel ).append( seriesLabel );
//				if (dataManager.getHostCount() > 3 ) {
//					seriesLabel.css("position", "absolute") ; 
//					seriesLabel.css("top", "6px") ; 
//					seriesLabel.css("right", "45px") ; 
//				}

				if ( isShowAllSeries ) {
					$plotPanel.css( "height", $plotPanel.outerHeight( true ) - 20 );
				}
			}
		}


		return false;
	}

	function checkSampling( resourceGraph, flotTimeOffsetArray, linesOnGraphArray, $plotDiv ) {

		var currentGraph = resourceGraph.getCurrentGraph();
		var dataManager = resourceGraph.getDataManager();

		var graphNotes = "";
		if ( dataManager.isDataAutoSampled() ) {

			graphNotes += "Reduced:" + flotTimeOffsetArray.length + " points to: "
					+ linesOnGraphArray[0].data.length;

			// console.log("linesOnGraphArray: " + JSON.stringify(linesOnGraphArray[0], null, "\t"))
			//$(".zoomSelect").val(AUTO_SAMPLE_LIMIT).trigger("change") ;
		}

		if ( !$( '.padLatest', currentGraph ).prop( "checked" ) ) {
			graphNotes += " *Latest Excluded";
		}


		$( ".graphNotes", $plotDiv.parent() ).text( graphNotes );
		$plotDiv.attr( "title", graphNotes );

	}


	var _colorCount = 0;
	var _isNeedsLabel = true;
	function buildSeriesForGraph( $GRAPH_INSTANCE, _dataManager,
			graphName, seriesName, numberOfSeries, graphItems,
			metricsJson, flotTimeOffsetArray, seriesLabelOverride ) {

		console.log( "buildSeriesForGraph() " + $GRAPH_INSTANCE.attr( "id" ) + " graphName: " + graphName + " seriesName:" + seriesName );


		if ( window.csapGraphSettings != undefined ) {
			if ( window.csapGraphSettings.type == "service" && graphItems[seriesName] != window.csapGraphSettings.service ) {
				return null;
			}
			console.log( "csapGraphSettings: " + window.csapGraphSettings.graph );
		}

		if ( jQuery.inArray( graphItems[seriesName],
				metricsJson.attributes["servicesRequested"] ) != -1 ) {
			if ( !$( "#serviceCheckbox" + graphItems[seriesName],
					$GRAPH_INSTANCE ).is( ':checked' ) ) {

				console.log( "buildSeriesForGraph() : Not selected:  seriesName: "
						+ seriesName + " key: " + graphItems[seriesName] );
				// console.log("Skipping: " + graphKey) ;
				return null;
			}
		}
		if ( seriesName == "totalCpu" ) {
			if ( !$( "#serviceCheckboxtotalCpu", $GRAPH_INSTANCE )
					.is( ':checked' ) ) {
				// console.log("Skipping: " + graphKey) ;
				return null;
			}
		}

		var graphDefn = new Object();

		if ( !isAttribute( seriesName ) ) {

			if ( metricsJson.data[seriesName] == null ) {
				alertify.notify( "No Data available for: " + seriesName );
				_dataManager.buildPoints( flotTimeOffsetArray, -1, $GRAPH_INSTANCE, graphLayout.getWidth( graphName, $GRAPH_INSTANCE ) );
				// continue ;
			} else {
				graphDefn.data = _dataManager.buildPoints( flotTimeOffsetArray,
						metricsJson.data[seriesName], $GRAPH_INSTANCE, graphLayout.getWidth( graphName, $GRAPH_INSTANCE ) );

				graphDefn.rawData = metricsJson.data[seriesName];
				//graphDefn.stats = _dataManager.graphStats( metricsJson.data[seriesName] );
				// console.log("graphDefn.stats: " + JSON.stringify(graphDefn.stats, null, "\t")  ) ;
			}
		} else {
			// Hook for inserting a straight line in graph. useful for
			// establishing thresholds
			// both "." historical and "_" (curent" may be have been
			// used.
			var specialIndex = seriesName.indexOf( "_" );
			if ( specialIndex == -1 )
				specialIndex = seriesName.indexOf( "." );
			var attKey = seriesName.substring( specialIndex + 1 );
			graphDefn.data = _dataManager.buildPoints( flotTimeOffsetArray,
					metricsJson.attributes[attKey], $GRAPH_INSTANCE, graphLayout.getWidth( graphName, $GRAPH_INSTANCE ) );
		}
		// alertify.alert( JSON.stringify(graphDefn.data, null,"\t") ) ;

		var seriesLabel = graphItems[seriesName];
		console.log( "seriesLabel: " + seriesLabel );

		if ( seriesLabel == null ) {
			seriesLabel = seriesName;
			console.log( "Null name: " + seriesName );
		}

		if ( seriesLabel.contains( "_" ) ) {
			// graphItems
			var testMatchLabel = seriesLabel.substring( 0, seriesLabel.indexOf( "_" ) );

			var matchCount = 0;
			for ( var testName in graphItems ) {

				if ( testName.contains( "_" ) ) {
					var shortName = testName.split( "_" );
					if ( shortName[1] == testMatchLabel )
						matchCount++
				}
			}

			if ( matchCount <= 1 ) {
				seriesLabel = testMatchLabel;
			}

		}


		if ( seriesLabelOverride != "" )
			seriesLabel += ":" + seriesLabelOverride;


		if ( _isNeedsLabel ) {
			_isNeedsLabel = false;
			graphDefn.label = seriesLabel
					+ '<span class="graphInlineTitle">'
					+ splitUpperCase( graphName ) + "</span>";


		} else {
			graphDefn.label = seriesLabel;
		}

		// console.log("graphDefn.label: " + graphDefn.label);
		if ( seriesName.contains( "attributes_" ) ) {
			graphDefn.color = "green";
			var t = {
				lineWidth: 2
			};
			// var t = { lineWidth: 3 , fill: true, fillColor:
			// "rgba(243, 255, 219, 0.3)"} ;
			graphDefn.lines = t;
		} else if ( seriesName == "totalCpu" ) {
			var blackColor = "#0F0F0F";
			graphDefn.color = blackColor;
			var t = {
				lineWidth: 1
			};
			graphDefn.lines = t;
		} else if ( numberOfSeries == 1 ) {
			graphDefn.color = "#60BD68";
			graphDefn.lines = {
				lineWidth: 4
			};
		} else {
			if ( _colorCount < CSAP_THEME_COLORS.length ) {
				graphDefn.color = CSAP_THEME_COLORS[_colorCount++];
			}
		}

		//console.log("numItems on graph: " + graphItems[seriesName].length)


		return graphDefn;
	}

	// Used to build custom combinations of elements
	function getOptionalSeries( resourceGraph, seriesName, metricsJson, $graphContainer ) {

		var optionalSeries = Object();

		var customView = resourceGraph.getSelectedCustomView();
		if ( customView == null )
			return optionalSeries;

		var seriesType = seriesName.substring( 0, seriesName.indexOf( "_" ) )
		var seriesPrefix = seriesName.substring( seriesName.indexOf( "_" ) )

		// console.log("seriesType: " + seriesType + "  seriesPrefix: " +seriesPrefix + " viewSelected: " + jsonToString( customView ))

		if ( customView.graphMerged[seriesType] != undefined ) {
			optionalSeries.seriesName = customView.graphMerged[seriesType] + seriesPrefix;
			optionalSeries.title = metricsJson.attributes.titles[customView.graphMerged[seriesType]];
			optionalSeries.key = customView.graphMerged[seriesType]
		}

		if ( optionalSeries.title != undefined )
			console.log( "custom Views: " + customView + "\n" + jsonToString( optionalSeries ) )


		return optionalSeries;
	}

	this.drawCsv = drawCsv;
	function drawCsv( graphs, metricsJson, plotContainer ) {
		for ( var graphName in graphs ) {
			var csvText = jQuery( '<textArea/>', {
				title: "Click to toggle size",
				rows: "5",
				cols: "50",
			} );
			var graphItems = graphs[graphName];
			var plotArray = new Array();
			var count = 0;
			var colorCount = 0;

			// Build the flot points
			var csvContent = "timeStamp,";
			for ( var graphKey in graphItems ) {
				csvContent += graphKey + ",";
			}
			for ( i = 0; i < metricsJson.data.timeStamp.length; i++ ) {
				var reverseOrderIndex = metricsJson.data.timeStamp.length
						- 1 - i;
				var t = new Date();
				t.setTime( metricsJson.data.timeStamp[reverseOrderIndex] );
				csvContent += "\n" + excelFormat( t ) + ",";
				for ( var graphKey in graphItems ) {
					if ( !isAttribute( graphKey ) ) {
						// console.log("Adding cvs content: " + graphKey) ;
						csvContent += metricsJson.data[graphKey][reverseOrderIndex]
								+ ",";
					} else {
						var attKey = graphKey.substring( graphKey
								.indexOf( "." ) + 1 );
						// console.log( attKey ) ;
						csvContent += metricsJson.attributes[attKey] + ",";
					}
				}
			}

			csvText.val( csvContent );
			plotContainer.append( csvText );
		}
	}

	function isAttribute( graphKey ) {
		if ( graphKey.indexOf( "attributes_" ) != -1 )
			return true;
		if ( graphKey.indexOf( "attributes." ) != -1 )
			return true;

		return false;
	}
	function excelFormat( inDate ) {

		var returnDateTime = 25569.0 + ((inDate.getTime() - (inDate
				.getTimezoneOffset() * 60 * 1000)) / (1000 * 60 * 60 * 24));
		return returnDateTime.toString().substr( 0, 20 );

	}

} );