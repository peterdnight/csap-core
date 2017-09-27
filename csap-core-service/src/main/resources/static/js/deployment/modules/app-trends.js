define( [], function ( ) {
	console.log( "Module loaded" );


	// Hooks for not reIssuing null requests
// Critical for trending graphs which require explicity tool tip
// layout based on container index
	var nullTrendItems = new Array();
	var _activePlots = new Array();
	var trendTimer = 0;
	var TREND_CHECK_FOR_DATA_SECONDS = 4;

	var _linkProject = null;
	var _project = null;
	var isDisplayZoomHelpOnce=true;

	return {
		init: function ( project, linkProject ) {
			_project = project;
			_linkProject = linkProject;
			return init();
		},

		getConfiguredTrends: function (  ) {
			return getConfiguredTrends( );
		}

	};

	function init() {

		$( '#trendingDays' ).selectmenu( {
			change: function ( event, ui ) {
				// force full screen every time
				$( "#trendPanel" ).removeClass( 'panelEnlarged' );
				$( "#minMaxTrendsButton" ).trigger( "click" );
			}
		} );

	}

	function getConfiguredTrends() {


		console.log( "Purging old Trends, count: " + _activePlots.length );
		for ( var i = 0; i < _activePlots.length; i++ ) {
			var thePlot=_activePlots.pop();
			
			console.log("deleteing plot", thePlot ) ;
			thePlot.destroy() ;
			delete thePlot ;
			
		}
		
		console.groupCollapsed( "Building Trends, count: " + trendingConfig.length );
		// trendingConfig
		// data is incremental now....
		var containerIndex = 0;
		config = null;
		for ( var i = 0; i < trendingConfig.length; i++ ) {

			config = trendingConfig[ i ];
			// skip some items....
			if ( $.inArray( config.label, nullTrendItems ) == -1 ) {
				buildTrendingGraph( containerIndex, config );
				containerIndex++;
			}
		}

		// Some graphs are removed as there is no data - clean those up
		for ( var i = 0; i < nullTrendItems.length; i++ ) {
			$( "#trend" + (trendingConfig.length - i - 1) )
					.empty()
					.text( "No Data for: " + nullTrendItems[i] )
					.hide();
		}
		console.groupEnd();

//	 getTrending( "custom/core", "coresUsed", 1 ) ;
//		//	getTrending( "service/detail", "threadCount", "numberOfSamples", "CsAgent" ) ;
//	getTrending( "service/detail", "threadCount", "numberOfSamples", "CsAgent" ) ;
	}


	function buildTrendingGraph( index, trendItem ) {

		var containerId = "trend" + index;

		if ( $( "#" + containerId ).length == 0 ) {
			var meterDiv = jQuery( '<div/>', {
				id: containerId,
				class: "trendGraph"
			} ).css( "display", "none" );

			var targetFrame = $( "#trendContainer" );

			targetFrame.append( meterDiv );
		}

		// metricTarget, attributeName, trendDivide, serviceName
		//hideCoreTrending() ;
		//$(".trendGraph").remove();

		// if ( trendItem.label == "CsAgent Socket Count Per Vm" ) targetProject = "DummyForTest" ;
		var paramObject = {
			report: trendItem.report,
			appId: appId,
			metricsId: trendItem.metric,
			project: _project,
			life: defaultLifeCycle,
			trending: 1,
			trendDivide: trendItem.divideBy,
			numDays: $( "#trendingDays" ).val()
		};

		if ( trendItem.serviceName != undefined ) {
			$.extend( paramObject, {
				serviceName: trendItem.serviceName
			} );
		}

		if ( trendItem.allVmTotal != undefined ) {
			$.extend( paramObject, {
				allVmTotal: trendItem.allVmTotal
			} );
		}

		// console.log("Getting Trending data for: " + trendItem.report) ;
		$.getJSON( trendUrl,
				paramObject )

				.done( function ( responseJson ) {
					renderTrendingGraph( containerId, trendItem, responseJson );

				} )

				.fail(
						function ( jqXHR, textStatus, errorThrown ) {

							handleConnectionError( "Trending request: " + trendItem.metric, errorThrown );

						} );


	}

	function resetTrendTimer( delaySeconds ) {

		console.log( "resetTrendTimer() - resetting timer: " + delaySeconds + " seconds" );
		clearTimeout( trendTimer );
		trendTimer = setTimeout(
				function () {
					if ( $( "#trendPanel" ).is( ":visible" ) ) {
						console.log( "\n\n\n resetTrendTimer() - refreshingTrends\n\n\n" );
						getConfiguredTrends();

					}
				}, delaySeconds * 1000 );
	}

	function renderTrendingGraph( containerId, trendItem, currentResults ) {

		var labelArray = new Array();
		var $trendContainer = $( "#" + containerId );

		console.log( "renderTrendingGraph() : ", trendItem );
		// console.log( trendItem );

		if ( $( "#trendPanel" ).hasClass( "panelEnlarged" ) ) {
			$trendContainer.css( "width", $( "#trendPanel" ).outerWidth() - 100 );
			$trendContainer.css( "height", "250" );
		} else {
			var targetWidth = $( "#news" ).outerWidth() / 2 - 50;
			$trendContainer.css( "width", targetWidth );
			$trendContainer.css( "height", "120" );
		}

		// no data available yet
		if ( currentResults.lastUpdateMs == 0 ) {
			console.log( "renderTrendingGraph() no data available, scheduling a refresh" );
			$( '#' + containerId ).show();
			var $loadingMessage = jQuery( '<div/>', {
				class: "loadingPanel",
				html: "loading: <br>" + trendItem.label
			} );

			var $refreshDiv = jQuery( '<div/>', {
				class: "vmid"
			} ).append( $loadingMessage );
			$( '#' + containerId ).html( $refreshDiv );
			var targetWidth = $( "#news" ).outerWidth() / 2 - 50;
			$trendContainer.css( "width", targetWidth );
			resetTrendTimer( TREND_CHECK_FOR_DATA_SECONDS )
			return;
		}


		// data available - but stale. Update display, then refresh.
		if ( currentResults.lastUpdateMs < 0 ) {
			resetTrendTimer( TREND_CHECK_FOR_DATA_SECONDS )
		}

		if ( currentResults.message != undefined ) {
			console.log( "Undefined trending results for:" + trendItem.label + " message:" + currentResults.message );
			return;
		}

		// for testing revised layouts || trendItem.label == "Cores Active" currentResults.data == undefined
		var trendingData = currentResults.data;
		console.log( "trendingData: ", trendingData );
		if ( (trendingData === undefined)
				|| trendingData[0] === undefined
				|| trendingData[0]["date"] === undefined ) {
			console.log( "Trending Data is not available for: " + trendItem.label );
			nullTrendItems.push( trendItem.label );
			resetTrendTimer( TREND_CHECK_FOR_DATA_SECONDS );
			return;
		}

		var currData = currentResults.data[0];
		$( '#' + containerId ).empty(); // get rid of previous rendering

		// graph libraries do not work unless rendering container is visible
		$( '#' + containerId ).show();

		// var seriesToPlot = [  currData[  selectedColumn]  ];
		var seriesToPlot = new Array();
		var seriesLabel = new Array();


		var graphTitle = "";
		for ( var i = 0; i < currentResults.data.length; i++ ) {
			//console.log()
			currData = currentResults.data[i];
			var metricName = trendItem.metric ;
			graphTitle = trendItem.label;
			if ( currData[ metricName ] == undefined) {
				metricName = "StubData" ;
				graphTitle += "(Test Data)"
				console.log("using stub data")
			}
			seriesToPlot.push( buildSeries( currData[  "date"], currData[ metricName ] ) );

			// var label = trendItem.label;
			var label = currData.project;
//		  if ( currentResults.data.length > 1 )
//				label += " : " + currData.project + " "; // currData.lifecycle
			if ( has10kValue( currData[  metricName] ) )
				graphTitle += " (1000's)";

			if ( currentResults.lastUpdateMs < 0 ) {
				graphTitle += '<img width="14" src="' + contextUrl + 'images/animated/loadSmall.gif"/>';
			}
			seriesLabel.push( label );
		}


		//var height = 10 + currentResults.data.length;  // handle legend heights
		// $trendContainer.css( "height", height + "em" );
		// var seriesToPlot = [  buildSeries( currData[  "date"], currData[  selectedColumn])  ];
		// $.jqplot("metricPlot", [[itemValueToday], [itemValueWeek]], {

		// console.log ("Checking for padding: " + graphTitle) ;
		padMissingPointsInArray( seriesToPlot );

		// console.log( "seriesToPlot:" + JSON.stringify( seriesToPlot, null, "\t" ) );
		// http://www.jqplot.com/docs/files/jqPlotOptions-txt.html

		var toolTipLocation = "ne";
		var containerIndex = containerId.substring( 5 );
		if ( containerIndex % 2 == 1 )
			toolTipLocation = "nw";
		if ( $( "#trendPanel" ).hasClass( "panelEnlarged" ) )
			toolTipLocation = "n";

		var trendConfiguration = buildTrendConfiguration( );
		trendConfiguration.title = graphTitle;
		trendConfiguration.axes.xaxis.min = new Date( seriesToPlot[0][0][0] );
		trendConfiguration.highlighter.tooltipLocation = toolTipLocation;
		trendConfiguration.legend.labels = seriesLabel;



		trendConfiguration.axes.yaxis = { min: calculateGraphMin( seriesToPlot ) };

		var thePlot = $.jqplot( containerId, seriesToPlot, trendConfiguration );
		_activePlots.push( thePlot ) ;

		$jqPlotTitle = $( ".jqplot-title", $trendContainer );

		$jqPlotTitle.css( "top", "3px" );
		// console.log( "generateTrending(): ploting series count: " + seriesToPlot.length );

		if ( currentResults.data.length > 1 ) {
			// jqplotDataMouseOver for lines, jqplotDataHighlight for fills
			$( '#' + containerId ).bind( 'jqplotDataMouseOver', function ( ev, seriesIndex, pointIndex, data ) {
				//console.log("binding serires");
				var seriesHighted = currentResults.data[seriesIndex]
				trendHighlight( seriesHighted.project )
			} );
		} else {
			$( '#' + containerId ).bind( 'jqplotDataMouseOver', function ( ev, seriesIndex, pointIndex, data ) {
				trendHighlight( trendItem.label )
			} );
		}

		registerTrendEvents( containerId, $trendContainer, trendItem );
	}

	function registerTrendEvents( containerId, $trendContainer, trendItem ) {

		var trendEventsFunction = function () {
			console.log( "rebinding events - because cursor zooms will loose them" );
			var $jqPlotTitle = $( ".jqplot-title", $trendContainer );
			$jqPlotTitle.off();
			$jqPlotTitle.click( function () {
				var urlAction = analyticsUrl + "&project=" + _linkProject + "&appId=" + appId + "&";

				if ( trendItem.serviceName != undefined ) {
					var targetService = trendItem.serviceName;
					if ( targetService.contains( "," ) ) {
						console.log( "Picking first service in set: " + targetService );
						var services = targetService.split( "," );
						targetService = services[0];
					}
					urlAction += "&report=" + trendItem.report + "&service=" + targetService;
				}
				console.log( "Opening: " + urlAction );

				openWindowSafely( urlAction, "_blank" );
				return false; // prevents link
			} );

			$jqPlotTitle.hover(
					function () {
						$( this ).css( "text-decoration", "underline" );
						$( this ).parent().css( 'background-color', '#D4F5C9' );
						$( ".jqplot-highlighter-tooltip" ).hide();
					},
					function () {
						$( this ).css( "text-decoration", "none" );
						$( this ).parent().css( 'background-color', '#EDF1F5' );
					}
			);
		};

		// initial registration
		trendEventsFunction();

		// reregister on redraws
		$( '#' + containerId ).off();
		$( '#' + containerId ).bind( 'jqplotClick',
				function ( ev, seriesIndex, pointIndex, data ) {
					if ( isDisplayZoomHelpOnce) {
						isDisplayZoomHelpOnce = false;
						alertify.notify( "double click to reset zoom to original" );
					}
					trendEventsFunction();
				}
		);
	}

	function calculateGraphMin( seriesToPlot ) {
		// set the min
		var lowestValue = -999;

		for ( var i = 0; i < seriesToPlot.length; i++ ) {
			var lineSeries = seriesToPlot[i];
			for ( var j = 0; j < lineSeries.length; j++ ) {
				var pointsOnLine = lineSeries[j];
				var current = pointsOnLine[1];
				if ( lowestValue == -999 ) {
					lowestValue = current;
				} else if ( current < lowestValue ) {
					lowestValue = current;
				}
			}
		}
		//console.log( "theMinForY pre lower: ", lowestValue) ;
		lowestValue = lowestValue * 0.8;
		if ( lowestValue < 5 ) {
			lowestValue = parseFloat( lowestValue.toFixed( 1 ) );
		} else {
			lowestValue = Math.round( lowestValue );
		}


		console.log( "theMinForY: ", lowestValue );
		return lowestValue;
	}

	function buildTrendConfiguration( ) {
		var config = {
			title: "placeholder",
			seriesColors: CSAP_THEME_COLORS,
			stackSeries: false,
			seriesDefaults: {
				fill: false,
				fillAndStroke: true,
				fillAlpha: 0.5,
				pointLabels: { //http://www.jqplot.com/docs/files/plugins/jqplot-pointLabels-js.html
					show: false,
					ypadding: 0
				},
				markerOptions: {
					show: true,
					size: 3,
					color: "#6495ED"
				},
				rendererOptions: {
					smooth: true
				}
			},
			cursor: {
				show: true,
				tooltipLocation: 'nw',
				zoom: true
			},
			axesDefaults: {
				tickOptions: { showGridline: false }
			},
			axes: {
				xaxis: {
					// http://www.jqplot.com/tests/date-axes.php
					renderer: $.jqplot.DateAxisRenderer,
					min: new Date( ),
					max: new Date(),
					tickOptions: { formatString: '%b %#d' }
				}

			},
			// http://www.jqplot.com/docs/files/plugins/jqplot-highlighter-js.html
			highlighter: {
				show: true,
				showMarker: true,
				sizeAdjust: 10,
				tooltipLocation: "s",
				tooltipOffset: 20,
				formatString: '<div class="trendSeries"></div>%s: <div class="tipValue">%s</div> <br><div class="trendOpen">Click to view analytics</div>' //%d
			},
			legend: {
				show: false,
				labels: "seriesLabel",
				location: 's',
				placement: "inside"
			}
		}

		return config;
	}

	function trendHighlight( project ) {
		console.log( "Project: " + project );
		//$(".jqplot-highlighter-tooltip").hide() ;
		var updateTip = function () {
			$( ".trendSeries" ).text( project );
		};

		// race condition - try twice
		setTimeout( updateTip, 100 );
		setTimeout( updateTip, 500 );
	}

	function has10kValue( valueArray ) {
		for ( var i = 0; i < valueArray.length; i++ ) {
			if ( valueArray[i] > 10000 )
				return true;
		}
		return false;

	}

	function buildSeries( xArray, yArray ) {
		var graphPoints = new Array();

		var divideBy = 1;

		if ( has10kValue( yArray ) )
			divideBy = 1000
		for ( var i = 0; i < xArray.length; i++ ) {
			var resourcePoint = new Array();
			resourcePoint.push( xArray[i] );
			// resourcePoint.push(i  );
			//console.log("****** Rounding: " + yArray[i]/divideBy)
			resourcePoint.push( yArray[i] / divideBy );

			graphPoints.push( resourcePoint );
		}

		xArray = null;
		yArray = null;
		// console.log( "Points: " + JSON.stringify(graphPoints ) );

		return graphPoints;
	}


} );
