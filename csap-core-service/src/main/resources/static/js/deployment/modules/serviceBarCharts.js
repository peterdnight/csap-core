define( [], function (  ) {

	console.log( "Module19 loaded" );

	var _resultPanel = "#serviceEditorResult";

	var reportRateLookup = {
		perSecond: 1 / 30,
		perMinute: 2,
		perHour: 120,
		perDay: 2880
	}

	var jmxRates = {
		httpRequestCount: { reportRate: "perHour" },
		sessionsCount: { reportRate: "perDay" },
	}

	var ApplicationMeter = function () {
		this.title = "";
		this.maxValue = 0;
	}

	var _lastJmx = null;
	var _lastReport = null;

	return {
		// peter
		show: function ( responseJson, reportStartOffset, report ) {
			_lastReport=report ;
			return addMeters( responseJson, reportStartOffset, report );
		}

	}

	/** peter */
	function  addMeters( responseJson, reportStartOffset, report ) {



		// console.log( "addMeters: " + report + " Success: " + JSON.stringify( responseJson, null, "\t" ) )

		if ( reportStartOffset == 0 ) {
			// console.log ("responseJson.data.length: " +
			// responseJson.data.length
			// ) ;
			if ( responseJson.data == undefined || responseJson.data.length == 0 ) {
				$( "#metersContainer .info" ).show();
				$( "#metersContainer .loadingLargePanel" ).hide();
				return true; // no data
			}
			_lastJmx = responseJson.data;
			return false;
		}

		$( "#metersContainer .loadingLargePanel" ).hide();
		$( "#metersContainer .info" ).hide();
		$( "#metersContainer .meterGraph" ).remove();

		if ( responseJson.data.length == 0 )
			responseJson.data = _lastJmx;

		buildMeters( _lastJmx, responseJson.data, report );

		return true;

	}


	// ForAllServicePackages
	function mergeArrayValues( inputArray ) {
		
		var mergedArray = inputArray[0] ;
		
		for ( var i = 1; i < inputArray.length; i++ ) {
			for ( var attribute in mergedArray ) {
				var mergeValue = mergedArray[ attribute ] ;
				var iterValue = inputArray[i][ attribute ] ;
				
				if ( $.isNumeric( mergeValue ) && $.isNumeric( iterValue ) ) {	
					 mergedArray[ attribute ] =  mergeValue + iterValue ;
					//console.log( attribute + " numeric mergeValue: " + mergeValue + " iterValue: " + iterValue)
				} else {	
					//console.log( attribute + " nonNUMERIC mergeValue: " + mergeValue + " iterValue: " + iterValue)
				}
				
			}
		}

		 
		return mergedArray;
	}
	function buildMeters( dataValuesTodayArray, dataValuesWeekArray, report ) {

//		console.log( "dataValuesTodayArray: " + dataValuesTodayArray.length
//				+ " dataValuesWeekArray: " + dataValuesWeekArray.length )

		var dataValuesToday = mergeArrayValues( dataValuesTodayArray );
		var dataValuesWeek = mergeArrayValues( dataValuesWeekArray );

		// we can get multiple package results when all is selected.
		// either merge together? or take latest?

		for ( var meterName in dataValuesToday ) {
			// console.log("item: " + item)
			if ( !$.isNumeric( dataValuesToday[meterName] ) )
				continue;

			var config = new ApplicationMeter();

			config.maxValue = resourceLimits [meterName];

			var meterId = report + "meter" + meterName;

			// console.log("meterId: " + meterId)


			var graphTitle = splitUpperCase( meterName );

			var attributeConfig = null
			if ( customMetrics != null ) {
				var attributeConfig = customMetrics[meterName];
			}

			// console.log("\n\n item: " + item) ;
			if ( attributeConfig != null && attributeConfig.title != undefined ) {
				graphTitle = attributeConfig.title;
			}

			// jmx Titles hack - need to get from collector, hardcoding for
			// now.
			if ( jmxLabels[meterName] != undefined ) {
				graphTitle = jmxLabels[meterName];

				if ( jmxRates[ meterName ] != undefined ) {
					attributeConfig = jmxRates[ meterName ];
				}
			}

			var itemValueToday = dataValuesToday[meterName];
			var itemValueWeek = dataValuesWeek[meterName];

			if ( meterId.contains( "numberOfSamples" ) ) {
				$( "#dailySamples" ).text( (itemValueToday / 1000).toFixed( 1 ) );
				$( "#weeklySamples" ).text( (itemValueWeek / 1000).toFixed( 1 ) );
				continue;
			}

			if ( meterName != "numberOfSamples" && !$( "#isShowTotals" ).is( ':checked' ) ) {

				var todayDivide = dataValuesToday["numberOfSamples"];
				var weekDivide = dataValuesWeek["numberOfSamples"];

				// Allow for UI override - but only for rate based attributes
				if ( $( '#rateSelect' ).val() != "default" &&
						attributeConfig != null &&
						attributeConfig.reportRate != undefined ) {
					var rateAdjust = reportRateLookup [ $( '#rateSelect' ).val() ];
					config.maxValue = undefined;
					todayDivide = todayDivide / rateAdjust;
					weekDivide = weekDivide / rateAdjust;
					graphTitle = '<span class="reportRate">(' + $( '#rateSelect' ).val() + ")</span>" + graphTitle;

				} else if ( customMetrics != null ) {
					if ( attributeConfig != null && attributeConfig.reportRate != undefined ) {
						var rateAdjust = reportRateLookup [ attributeConfig.reportRate];

						// console.log( graphTitle + " rateAdjust: " + rateAdjust );
						if ( rateAdjust != undefined ) {
							todayDivide = todayDivide / rateAdjust;
							weekDivide = weekDivide / rateAdjust;
							graphTitle = '<span class="reportRate">(' + attributeConfig.reportRate + ")</span>" + graphTitle;
						}
					} else {
						graphTitle = '<span class="reportRate">(30s sampling)</span>' + graphTitle;
					}

				} else {
					graphTitle = '<span class="reportRate">(30s sampling)</span>' + graphTitle;
				}

				itemValueToday = dataValuesToday[meterName] / todayDivide;
				itemValueWeek = dataValuesWeek[meterName] / weekDivide;

			}

			itemValueToday = Math.round( itemValueToday );
			itemValueWeek = Math.round( itemValueWeek );



			var labelFormatter = "%d";

			if ( (Math.abs( itemValueToday ) > 2000 || Math.abs( itemValueWeek ) > 2000)
					&& (config.maxValue == undefined ||
							config.maxValue > 2000) ) {

				if ( graphTitle.contains( "(Mb)" ) ) {
					graphTitle = graphTitle.substring( 0, graphTitle.indexOf( "(Mb)" ) );
					labelFormatter = "%#.1f Gb";
					itemValueToday = itemValueToday / 1024;
					itemValueWeek = itemValueWeek / 1024;

					if ( config.maxValue != undefined )
						config.maxValue = config.maxValue / 1024
				} else if ( graphTitle.contains( "(ms)" ) ) {

					graphTitle = graphTitle.substring( 0, graphTitle.indexOf( "(ms)" ) );
					labelFormatter = "%#.2f s";
					itemValueToday = itemValueToday / 1000;
					itemValueWeek = itemValueWeek / 1000;

					if ( config.maxValue != undefined )
						config.maxValue = config.maxValue / 1000

				} else {
					if ( Math.abs( itemValueToday ) > 1000000 ) {
						labelFormatter = "%#.2f M";
						itemValueToday = itemValueToday / 1000000;
						itemValueWeek = itemValueWeek / 1000000;
						if ( config.maxValue != undefined )
							config.maxValue = config.maxValue / 1000000
					} else {

						labelFormatter = "%#.1f K";
						itemValueToday = itemValueToday / 1000;
						itemValueWeek = itemValueWeek / 1000;
						if ( config.maxValue != undefined )
							config.maxValue = config.maxValue / 1000
					}
				}
			} else {
				if ( graphTitle.contains( "(Mb)" ) ) {
					graphTitle = graphTitle.substring( 0, graphTitle.indexOf( "(Mb)" ) );
					labelFormatter = "%d Mb";
				} else if ( graphTitle.toUpperCase().contains( "(MS)" ) ) {

					graphTitle = graphTitle.substring( 0, graphTitle.toUpperCase().indexOf( "(MS)" ) );
					labelFormatter = "%d ms";
				}
			}



			// console.log( graphTitle + " itemValueToday: " + itemValueToday + " week: " + itemValueWeek) ;
			s1 = [itemValueToday, itemValueWeek];


			$( "#metersContainer" ).append( jQuery( '<div/>', {
				id: meterId,
				"data-graph": meterName,
				class: "meterGraph"
			} )
					.css( "height", "140" )
					.css( "width", "160" ) );

			var plotConfig = buildMeterConfiguration(
					meterName, graphTitle, itemValueToday, itemValueWeek, config.maxValue, labelFormatter );

			$.jqplot(
					meterId,
					[[itemValueToday], [itemValueWeek]],
					plotConfig
					);


			var $meter = $( "#" + meterId );
			hideFilteredMeters( $meter, plotConfig.isMaxExceeded, itemValueToday, itemValueWeek )
			addMeterBindings( $meter )


		}

		// tweak layouts
		$( ".jqplot-series-canvas", "#metersContainer" ).css( "top", "9px" );

	}

	function buildMeterConfiguration(
			item, graphTitle, itemValueToday, itemValueWeek, maxValueForItem, labelFormatter ) {

		var limitTip = '<div class="jmxToolTipLimit"><div>Click To View <span class="tipType"></span> Graphs</div></div>';
		var limitArray = new Array();

		if ( maxValueForItem != undefined ) {
			limitArray.push( addLimitLine( 1, maxValueForItem, item ) );
			limitTip = '<div class="jmxToolTipLimit">Limit: ' + maxValueForItem
					+ '<div>Click To View <span class="tipType"></span> Graph</div></div>';
		}


		var yMax = itemValueToday;
		if ( yMax < itemValueWeek )
			yMax = itemValueWeek;

		var isMaxExceeded = false;
		if ( maxValueForItem != undefined && yMax > maxValueForItem ) {
			isMaxExceeded = true;
		}

		if ( maxValueForItem != undefined && yMax < maxValueForItem ) {
			yMax = maxValueForItem;
		}

		yMax = yMax * 1.2;


		var yMin = 0;
		if ( yMax == 0 ) {
			yMax = 4;
			yMin = -1;
		}

		return {
			isMaxExceeded: isMaxExceeded, // ugly - internal state in object used by jqplot
			stackSeries: false,
			// animate: !$.jqplot.use_excanvas,
			seriesColors: [CSAP_BAR_COLORS[0], CSAP_BAR_COLORS[1]],
			seriesDefaults: {
				renderer: $.jqplot.BarRenderer,
				pointLabels: { show: true, ypadding: 5, location: "s", formatString: labelFormatter }
			},
			axes: {
				xaxis: {
					ticks: [graphTitle],
					renderer: $.jqplot.CategoryAxisRenderer
				},
				yaxis: {
					tickOptions: { show: false },
					max: yMax,
					min: yMin
				}
			},
			legend: {
				labels: ["Last 24 hours", "Last 7 days"],
				show: false
			},
			canvasOverlay: {
				show: true,
				objects: limitArray
			},
			highlighter: {
				show: true,
				tooltipAxes: "y",
				tooltipOffset: 10,
				sizeAdjust: 0,
				formatString: labelFormatter + limitTip,
				lineWidthAdjust: 0,
				tooltipLocation: "n"
			}
		}
	}


	/** @memberOf ServiceAdmin */
	function addLimitLine( xOffset, maxValue, yKey ) {
		var item = { dashedHorizontalLine: {
				name: yKey,
				dashPattern: [16, 12],
				y: maxValue,
				lineWidth: 2,
				showTooltip: false,
				tooltipFormatString: "Limit: " + maxValue,
				showTooltipPrecision: 0.4,
				tooltipLocation: 'ne',
				tooltipOffset: 0,
				lineCap: 'round',
				color: "#FF0000",
				shadow: false
			} }
		return item;
	}



	function hideFilteredMeters( $attributeMeter, isMaxExceeded, itemValueToday, itemValueWeek ) {

		$attributeMeter.css( "height", "145" ); // Note we tweak after for the second line of title

		var attributePercent = Math.abs( (itemValueToday - itemValueWeek) / itemValueWeek ) * 100;

		if ( itemValueToday == 0 && itemValueWeek == 0 )
			attributePercent = 0;

		var filterThreshold = $( "#filterThreshold" ).val();
		if ( !isNaN( attributePercent )
				&& attributePercent < filterThreshold
				&& !isMaxExceeded ) {
			$attributeMeter.addClass( "hiddenMeter" );
			$attributeMeter.hide();
			// console.log( "Hiding: " + graphTitle + " differnce: " + attributePercent )
			// continue ;
		}

		$( "#showFilteredMetersButton" ).show();
		var numHidden = $( ".hiddenMeter" ).length;
		$( "#metersFiltered .noteHighlight" ).text( numHidden );
		if ( numHidden == 0 ) {
			$( "#showFilteredMetersButton" ).hide();
		}
	}

	function addMeterBindings( $attributeMeter ) {
		var plotClickFunction = function ( ev, seriesIndex, pointIndex, data ) {
			// console.log( "seriesIndex: " + seriesIndex +
			// " Data: " + jsonToString( data ) )
			var graphId = $( this ).data( "graph" );
			// console.log( "Open graph: " + graphId + "
			// _lastReport" + _lastReport);

			if ( seriesIndex == 1 ) {
				// historical graphs
				var urlAction = analyticsUrl + "&graph=" + graphId
						+ "&project=" + selectedProject + "&host=" + hostName
						+ "&service=" + serviceShortName + "&report=graphJmx&appId=" + appId + "&";

				if ( _lastReport.contains( "jmxCustom" ) ) {
					urlAction += "appGraph=appGraph&"
				}

				openWindowSafely( urlAction, "_blank" );
				return;
			}
			// os dashboard graphs
			if ( _lastReport.contains( "jmxCustom" ) ) {
				launchCustomJmxDash( $( "#instanceTable *.selected" ), "&graph=" + graphId );
			} else {
				launchJmxDash( $( "#instanceTable *.selected" ), "&graph=" + graphId );
			}


		}
		$attributeMeter.bind( 'jqplotDataClick', plotClickFunction );

		var plotHighlightFunction = function ( ev, seriesIndex, pointIndex, data ) {
			$( " .jqplot-xaxis-tick", $( this ) ).css( "text-decoration", "underline" );

			$( this ).css( 'cursor', 'pointer' );
			// $( this ).css( 'background-color', '#e5f5f5' );
			var updateTip = function () {
				if ( seriesIndex == 1 ) {
					$( ".tipType" ).text( "historical" );
					;
				} else {
					$( ".tipType" ).text( "real-time" );
					;
				}
			};

			// race condition - try twice
			setTimeout( updateTip, 100 );
			setTimeout( updateTip, 500 );

		}

		$attributeMeter.bind( 'jqplotDataHighlight', plotHighlightFunction );


		$attributeMeter.bind( 'jqplotDataUnhighlight',
				function () {
					$( " .jqplot-xaxis-tick", $( this ) ).css( "text-decoration", "none" );
					$( this ).css( 'cursor', 'default' );
					$( ".jqplot-highlighter-tooltip" ).hide();
				} );
	}


} );