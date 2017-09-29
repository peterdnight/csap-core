define( ["model", "windowEvents", "./dataService", "./trends"], function ( model, windowEvents, dataService, trends ) {

	console.log( "Module loaded: reports/summaryTab" );

	var isDisplayZoomHelpOnce = true;


	var _computeDone = false;
//var THREAD_REPORT="vm" ; // metricsId=csapThreadsTotal, allVmTotal=true

	var USER_ACTIVITY_REPORT = "vm";
	var HEALTH_REPORT = "custom/health";
	var LOGROTATE_REPORT = "custom/logRotate";
	var CORE_REPORT = "custom/core";
	var CORES_USED = "coresUsed";


	return {
		//
		runSummaryReports: function ( runOnlyIfNeeded ) {

			if ( runOnlyIfNeeded ) {
				if ( !_computeDone )
					runReports();
			} else {
				runReports();
			}
		},
		//
		updateHeader: function ( coresActive, coresTotal ) {
			updateHeader( coresActive, coresTotal );
		},
		//
	}

	function updateHeader( coresActive, coresTotal ) {
		console.log( "updateHeader() " + coresActive );
		var targetHeader = $( ".head" + CORES_USED );

		if ( $( "#coresActive", targetHeader ).length == 0 ) {
			targetHeader.append( $( "#coreSummaryTemplate" ).html() );

			$( "#coresActive", targetHeader ).text( coresActive.toFixed( 1 ) + " (" + (coresActive / coresTotal * 100).toFixed( 0 ) + "%)" );

			var vdcMin = (coresActive * 3 * 1.5).toFixed( 0 );
			if ( vdcMin < 20 )
				vdcMin = "20"
			$( "#vmRecommend", targetHeader ).text( vdcMin + "Ghz" ); // 3ghz cpu * 1.5 for bursts
		}
	}

	function runReports() {

		var _computeVisible = $( "#coreTrending" ).is( ":visible" );

		// only run if tab is active. jqplot requires dive to be visible.
		if ( !_computeVisible ) {
			console.log( "runComputeReports() - tab not active, skipping" );
			_computeDone = false; // run the report next time it is selected
			return;
		}

		console.log( "runComputeReports() - Running reports" );
		_computeDone = true;
		$( "#coreTrending" ).empty();

		runSummaryForReport( CORE_REPORT, CORES_USED, "Active Cpu Cores - All VMs" );
		runSummaryForReport( USER_ACTIVITY_REPORT, "totActivity", "User Activity - All VMs" );
		runSummaryForReport( HEALTH_REPORT, "UnHealthyCount", "Health Alerts - All VMs" );

		runSummaryForReport( LOGROTATE_REPORT, "MeanSeconds", "Log Rotation Time (seconds) - All VMs" );

		// getComputeTrending( THREAD_REPORT , "csapThreadsTotal" , "Application Threads - All VMs") ;


		// populate the 24 hours core and VDC
		dataService.runReport( "vm" );



		if ( $( "#isCustomPerVm" ).is( ':checked' ) ) {
			$( "#vmSummary .entry" ).show();
		} else {
			$( "#vmSummary .entry" ).hide();
		}

	}



	function     runSummaryForReport( report, selectedColumn, reportLabel ) {

		var targetId = selectedColumn + "ComputePlot";
		//build container first

		var targetFrame = $( "#coreTrending" );

		var computeHead = jQuery( '<div/>', {
			class: "computeHead head" + selectedColumn,
			text: reportLabel
		} );

		targetFrame.append( computeHead );

		var meterDiv = jQuery( '<div/>', {
			id: targetId,
			// 	title: "Trending " + selectedColumn + ": Click on any data point for report",
			class: "metricPlot computePlot"
		} );

		var displayHeight = $( window ).outerHeight( true )
				- $( "header" ).outerHeight( true ) - 300;
		var trendHeight = Math.floor( displayHeight / 3 );
		if ( trendHeight < 100 )
			trendHeight = 100;
		meterDiv.css( "height", trendHeight + "px" );

		targetFrame.append( meterDiv );

		var $loadingMessage = jQuery( '<div/>', {
			class: "loadingPanel",
			text: "Building Report: Time taken is proportional to time period selected."
		} );

		meterDiv.append( $loadingMessage );

		// return;

		var paramObject = {
			appId: $( "#appIdFilterSelect" ).val(),
			numDays: $( "#coreTrendingSelect" ).val(),
			project: model.getSelectedProject(),
			metricsId: selectedColumn
		};

		// 

		var life = $( "#lifeSelect" ).val();
		if ( life == null )
			life = uiSettings.lifeParam;
		if ( !$( "#isAllCoreLife" ).is( ':checked' ) ) {
			$.extend( paramObject, {
				life: life
			} );
		}
		if ( $( "#isCustomPerVm" ).is( ':checked' ) && ($( "#topVmCustom" ).val() != 0 || $( "#lowVmCustom" ).val() != 0) ) {
			$.extend( paramObject, {
				perVm: true,
				top: $( "#topVmCustom" ).val(),
				low: $( "#lowVmCustom" ).val()
			} );
		}

		if ( report == USER_ACTIVITY_REPORT ) {
			$.extend( paramObject, {
				"metricsId": "totActivity",
				"trending": true,
				"allVmTotal": true
			} );
		}
//	
//	 if ( report == THREAD_REPORT) {
//		$.extend(paramObject, {
//			"metricsId": "csapThreadsTotal",
//			"trending" : true,
//			"allVmTotal": true,
//			"divideBy": "numberOfSamples"
//		});
//	}

		// custom/core
		var coreUrl = uiSettings.metricsDataUrl + "../report/" + report + "?callback=?";
		$.getJSON( coreUrl,
				paramObject )

				.done( function ( responseJson ) {
					buildSummaryReportTrend( selectedColumn, targetId, report, responseJson );

				} )

				.fail(
						function ( jqXHR, textStatus, errorThrown ) {
							$( "#" + targetId ).text( "Data not available. Ensure the latest agent is installed." );
							alertify.notify( "Failed getting trending report for: " + report );

						} );


	}


	function    buildSummaryReportTrend( selectedColumn, meterDiv, report, currentResults ) {

		var labelArray = new Array();


		console.log( "buildSummaryReportTrend() : " + report );

		$( "#" + meterDiv ).empty();

		var currData = currentResults.data[0]
		if ( currData == undefined || currData["date"] == undefined ) {
			$( "#" + meterDiv ).text( "Report Disabled due to excessive instability. Contact admin to renable once your vms are actively managed" ).css( "height", "2em" );
			console.log( "Trending Data is not available for report: " + report + ". Select an individual service, then run the trending report." );
			return;
		}
		// var seriesToPlot = [  currData[  selectedColumn]  ];
		var seriesToPlot = new Array();
		var seriesLabel = new Array();


		for ( var i = 0; i < currentResults.data.length; i++ ) {

			currData = currentResults.data[i];
			//console.log( "buildComputeTrends() Series size: " + currData[  "date"].length) ;


			seriesToPlot.push( trends.buildSeries( currData[  "date"], currData[  selectedColumn] ) );
			var curLabel = currData["lifecycle"];

			if ( currData["host"] != undefined )
				curLabel += ":" + currData["host"];
			seriesLabel.push( curLabel );
		}
		// var seriesToPlot = [  buildSeries( currData[  "date"], currData[  selectedColumn])  ];
		// $.jqplot("metricPlot", [[itemValueToday], [itemValueWeek]], {


		padMissingPointsInArray( seriesToPlot );
//	console.log( "buildComputeTrends() Plot vals: " +  JSON.stringify(seriesToPlot, null,"\t")  + " labels: " +  JSON.stringify(seriesLabel, null,"\t") ) ;

		var yAxisSettings = { min: calculateGraphMin( seriesToPlot ) };

		if ( $( "#isZeroGraph" ).is( ':checked' ) )
			yAxisSettings = { min: 0 };

		// http://www.jqplot.com/docs/files/jqPlotOptions-txt.html
		$.jqplot( meterDiv, seriesToPlot, {
			seriesColors: CSAP_THEME_COLORS,
			stackSeries: !$( "#isLineGraph" ).is( ':checked' ),
			seriesDefaults: {
				fill: !$( "#isLineGraph" ).is( ':checked' ),
				fillAndStroke: true,
				fillAlpha: 0.5,
				pointLabels: { //http://www.jqplot.com/docs/files/plugins/jqplot-pointLabels-js.html
					show: false,
					ypadding: 0
				},
				rendererOptions: {
					smooth: true
				},
				markerOptions: {
					show: true,
					size: 4,
					color: "black"
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
					tickOptions: { formatString: '%b %#d' }
				},
				yaxis: yAxisSettings

			},
			// http://www.jqplot.com/docs/files/plugins/jqplot-highlighter-js.html
			highlighter: {
				show: true,
				showMarker: true,
				sizeAdjust: 20,
				tooltipLocation: "ne",
				tooltipOffset: 5,
				formatString: '%s : %s <br><div class="trendSeries"></div><div class="trendOpen">Click to view daily report</div>'
			},
			legend: {
				labels: seriesLabel,
				placement: "outside",
				show: true
			}
		} );

		$( '#' + meterDiv ).on( 'jqplotClick',
				function ( ev, seriesIndex, pointIndex, data ) {
					if ( isDisplayZoomHelpOnce) {
						isDisplayZoomHelpOnce = false;
						alertify.notify( "double click to reset zoom to original" );
					}
					$( '#' + meterDiv ).off( 'jqplotClick' );
				}
		);
		$( '#' + meterDiv ).on(
				'jqplotDataClick',
				function ( ev, seriesIndex, pointIndex, data ) {

					console.log( "Clicked seriesIndex: " + seriesIndex +
							"pointIndex: " + pointIndex + " graph: " + data );
					if ( report == HEALTH_REPORT || report == LOGROTATE_REPORT || report == CORE_REPORT ) {

						var cat = "/csap/reports/health";
						if ( report == LOGROTATE_REPORT )
							cat = "/csap/reports/logRotate";
						if ( report == CORE_REPORT )
							cat = "/csap/reports/host/daily";

						var lifeHostArray = (seriesLabel[seriesIndex]).split( ":" );
						hostString = "";
						if ( lifeHostArray.length > 1 )
							hostString = "&hostName=" + lifeHostArray[1];

						var healthUrl = uiSettings.eventApiUrl + "/..?life=" + lifeHostArray[0] + hostString
								+ "&category=" + cat
								+ "&date=" + data[0]
								+ "&project=" + model.getSelectedProject() + "&appId=" + $( "#appIdFilterSelect" ).val();
						openWindowSafely( healthUrl, "_blank" );
					}

				} );

		windowEvents.resizeComponents();
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


} );