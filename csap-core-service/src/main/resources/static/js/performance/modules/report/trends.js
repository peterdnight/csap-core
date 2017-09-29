define( ["model"], function ( model ) {

	console.log( "Module loaded: reports/trends" );

	var isDisplayZoomHelpOnce = true;

	return {
		//
		loadData: function ( currentResults, reportId ) {
			generateAttributeTrending( currentResults, reportId );
		},
		//
		buildSeries: function ( xArray, yArray ) {
			return buildSeries( xArray, yArray );
		},
		//

	}

// Critical - in order for plots to resolve - tab must be active


	function generateAttributeTrending( currentResults, reportId ) {

		var userSelectedAttributes = $( "#visualizeSelect" ).val().split( "," );

		var primaryAttribute = userSelectedAttributes[0];

		if ( userSelectedAttributes.length > 1 ) {
			primaryAttribute = "total";
		}

		var labelArray = new Array();


		console.log( "generateAttributeTrending() : " + reportId );

		var currData = currentResults.data[0]
		if ( currData == undefined || currData["date"] == undefined ) {
			$( "#" + meterDiv ).text( "Report Disabled due to excessive instability. Contact admin to renable once your vms are actively managed" ).css( "height", "2em" );
			alertify.error( "Trending Data is not available for report: " + report + ". Select an individual service, then run the trending report." );
			return;
		}

		var meterDiv = jQuery( '<div/>', {
			id: "metricPlot",
			title: "Trending " + primaryAttribute,
			class: "meterGraph"
		} ).css( "height", "20em" );

		var targetFrame = $( "#vmDiv .metricHistogram" );
		if ( reportId != "vm" )
			targetFrame = $( "#serviceDiv .metricHistogram" );
		if ( reportId == "userid" )
			targetFrame = $( "#useridDiv .metricHistogram" );

		// Exclude userid trending for now
		//if (reportId != "userid")
		showTrending( targetFrame );

		targetFrame.append( meterDiv );

		var seriesToPlot = new Array();
		var seriesLabel = new Array();

		for ( var i = 0; i < currentResults.data.length; i++ ) {

			currData = currentResults.data[i];
			//console.log( "buildComputeTrends() Series size: " + currData[  "date"].length) ;


			seriesToPlot.push( buildSeries( currData[  "date"], currData[  primaryAttribute] ) );
			var curLabel = currData["lifecycle"];

			if ( currData["host"] != undefined )
				curLabel += ":" + currData["host"];
			seriesLabel.push( curLabel );

			// service/detail and jmx detail both need label
			if ( i == 0 && reportId.contains( "detail" ) ) {
				$( "#serviceLabel" ).html(
						currData.serviceName + " : " + primaryAttribute );
			}
		}


		var yAxisSettings = { min: calculateGraphMin( seriesToPlot ) };

		if ( $( "#isZeroGraph" ).is( ':checked' ) )
			yAxisSettings = { min: 0 };

		// http://www.jqplot.com/docs/files/jqPlotOptions-txt.html

		$.jqplot( "metricPlot", seriesToPlot, {
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
				formatString: "%s : %s"
			},
			legend: {
				labels: seriesLabel,
				placement: "outside",
				show: true
			}
		} );

		$( '#metricPlot'  ).on( 'jqplotClick',
				function ( ev, seriesIndex, pointIndex, data ) {
					if ( isDisplayZoomHelpOnce ) {
						isDisplayZoomHelpOnce = false;
						alertify.notify( "double click to reset zoom to original" );
					}
					$( '#' + meterDiv ).off( 'jqplotClick' );
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
	function buildSeries( xArray, yArray ) {
		var graphPoints = new Array();

		for ( var i = 0; i < xArray.length; i++ ) {
			var resourcePoint = new Array();
			resourcePoint.push( xArray[i] );

			var metricVal = yArray[i];
			if ( $( "#nomalizeContainer" ).is( ":visible" ) ) {
				metricVal = metricVal * $( "#nomalizeContainer select" ).val();
			}

			resourcePoint.push( metricVal );

			graphPoints.push( resourcePoint );
		}

		xArray = null;
		yArray = null;
		// console.log( "Points: " + JSON.stringify(graphPoints ) );

		return graphPoints;
	}

	function showTrending( targetFrame ) {

		targetFrame.append( $( "#metricsTrendingContainer" ) );
		$( "#metricsTrendingContainer" ).show();
	}
} );