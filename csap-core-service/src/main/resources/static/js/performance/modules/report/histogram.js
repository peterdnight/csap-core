define( ["model", "windowEvents", "./dataService", "./utils"], function ( model, windowEvents, dataService, utils ) {

	console.log( "Module loaded: reports/histogram" );


	return {
		//
		loadData: function ( currentResults, reportId ) {
			generateAttributeHistogram( currentResults, reportId );
		},
		//
	}



	function generateAttributeHistogram( currentResults, reportId ) {

		var userSelectedAttributes = $( "#visualizeSelect" ).val().split( "," );

		var primaryAttribute = userSelectedAttributes[0];
		var labelArray = new Array();
		var valueArray = new Array();
		var deltaArray = new Array();

		console.log( "generateAttributeHistogram() : " + reportId );

		var numSamples = 0;



		// Sort by host
		if ( $( "#histogramSort" ).val() == "metric" ) {
			currentResults.data.sort( function ( a, b ) {
				// console.log( "b[selectedColumn]: " + b[selectedColumn])  ;
				if ( reportId == "userid" || $( "#isUseTotal" ).is( ':checked' ) )
					return a[primaryAttribute] - b[primaryAttribute];
				return a[primaryAttribute] / a.numberOfSamples - b[primaryAttribute] / b.numberOfSamples;
			} );
		} else {

			var labelSort = "";
			if ( reportId == "vm" ) {
				labelSort = "hostName";
			} else if ( reportId == "userid" ) {
				labelSort = "uiUser";
			} else if ( reportId.indexOf( "detail" ) != -1 ) {
				labelSort = "host";
			} else {
				labelSort = "serviceName";
			}

			currentResults.data.sort( function ( a, b ) {
				return b[ labelSort ].toLowerCase().localeCompare( a[ labelSort ].toLowerCase() );
			} );

		}

		// console.log("generateAttributeHistogram() Sorted :" + selectedColumn + " " +  JSON.stringify(currentResults.data, null,"\t") ) ;



		var hightlightColorArray = new Array();
		for ( var i = 0; i < currentResults.data.length; i++ ) {
			hightlightColorArray.push( "black" );
			var rowJson = currentResults.data[i];


			// console.log("generateAttributeHistogram() divideBy: " + divideBy + " usTotal: "
			// + $("#isUseTotal").is(':checked'));

			if ( reportId == "vm" ) {
				if ( !model.isHostInSelectedCluster( rowJson.hostName ) )
					continue;
				labelArray.push( rowJson.hostName );
			} else if ( reportId == "userid" ) {
				if ( rowJson.uiUser == "System" || rowJson.uiUser == "CsAgent"|| rowJson.uiUser == "agentUser")
					continue;
				labelArray.push( rowJson.uiUser );
			} else if ( reportId.indexOf( "detail" ) != -1 ) {
				if ( !model.isHostInSelectedCluster( rowJson.host ) )
					continue;
				labelArray.push( rowJson.host );
			} else {
				if ( !model.isServiceInSelectedCluster( rowJson.serviceName ) )
					continue;
				labelArray.push( rowJson.serviceName );
			}

			numSamples += rowJson.numberOfSamples;

			// service/detail and jmx detail both need label
			if ( i == 0 && reportId.contains( "detail" ) ) {
				$( "#serviceLabel" ).html(
						rowJson.serviceName + " : " + primaryAttribute );
			}

			valueArray.push( calculateHistogramValue( reportId, userSelectedAttributes,
					rowJson ) );

			if ( dataService.isCompareSelected() ) {
				var deltaRow = null;
				if ( rowJson.host != undefined ) {
					deltaRow = dataService.findCompareMatch( "host", rowJson.host );

				} else if ( rowJson.hostName != undefined ) {
					deltaRow = dataService.findCompareMatch( "hostName", rowJson.hostName );

				} else {
					deltaRow = dataService.findCompareMatch( "serviceName", rowJson.serviceName );

				}

//				console.log( "deltaRow ", deltaRow )
				// console.log("Adding delta row") ;
				deltaArray.push( calculateHistogramValue( reportId, userSelectedAttributes,
						deltaRow ) );


//				console.log( "deltaArray ", deltaArray );
			}
		}

		utils.updateSampleCountDisplayed( numSamples );

		var heightAdjust = 0.75;
		var seriesToPlot = [valueArray];

		var primaryLabel = $( "#reportStartInput" ).val();
		if ( primaryLabel == "" ) {
			primaryLabel = "Today";
		}

		var seriesLabel = [primaryLabel]
		var seriesColorsArray = [CSAP_THEME_COLORS[0]];


		if ( dataService.isCompareSelected() ) {
			heightAdjust = 1.25;
			seriesToPlot = [valueArray, deltaArray, ]
			seriesLabel = [$( "#compareStartInput" ).val(), primaryLabel]
			var seriesColorsArray = [CSAP_THEME_COLORS[1], CSAP_THEME_COLORS[0]];
		}


		// console.log( "seriesToPlot ", JSON.stringify( seriesToPlot, null, "\t" ) );

		var launchType = "host";
		if ( rowJson.host == undefined && rowJson.hostName == undefined ) {
			launchType = "service";
		}
		var meterDiv = jQuery( '<div/>', {
			id: "metricPlot",
			title: "Click on bar to view " + launchType + " graphs",
			class: "meterGraph"
		} ).css( "height", ((labelArray.length + 5) * heightAdjust) + "em" );

		// $("#reportOptions").append(meterDiv) ;

		var targetFrame = $( "#vmDiv .metricHistogram" );
		if ( reportId != "vm" )
			targetFrame = $( "#serviceDiv .metricHistogram" );
		if ( reportId == "userid" )
			targetFrame = $( "#useridDiv .metricHistogram" );

		targetFrame.append( $( "#metricsTrendingContainer" ) );
		$( "#metricsTrendingContainer" ).show();

		targetFrame.append( meterDiv );

		// $.jqplot("metricPlot", [[itemValueToday], [itemValueWeek]], {

		$.jqplot( "metricPlot", seriesToPlot, {
			stackSeries: false,
			animate: !$.jqplot.use_excanvas,
			seriesColors: seriesColorsArray,
			seriesDefaults: {
				renderer: $.jqplot.BarRenderer,
				pointLabels: {
					show: true,
					ypadding: 0
				},
				rendererOptions: {
					barDirection: 'horizontal',
					barPadding: 2,
					barMargin: 2,
					varyBarColor: false,
					highlightColors: hightlightColorArray,
					shadow: false
				}
			},
			axes: {
				yaxis: {
					ticks: labelArray,
					renderer: $.jqplot.CategoryAxisRenderer
				}
			},
			highlighter: { show: false },
			legend: {
				labels: seriesLabel,
				placement: "outside",
				show: true
			}
		} );
		if ( dataService.isCompareSelected() ) {
			$( ".jqplot-point-label" ).css( "font-size", "0.6em" );
		} else {
			$( ".jqplot-point-label" ).css( "font-size", "0.85em" );
		}

		$( '#metricPlot' ).bind(
				'jqplotDataClick',
				function ( ev, seriesIndex, pointIndex, data ) {
					// console.log("Clicked seriesIndex: " + seriesIndex + "
					// pointIndex: " + pointIndex + " graph: " + data) ;

					var launchTarget = labelArray[pointIndex];
					console.log( "item clicked: " + launchTarget + " reportId:" + reportId );

					if ( reportId == "userid" ) {
						openWindowSafely( utils.getUserUrl( "userid=" + launchTarget ),
								"_blank" );

					} else if ( reportId == "service" ) {
						utils.launchServiceOsReport( launchTarget );

					} else if ( reportId == "jmx" ) {
						utils.launchServiceJmxReport( launchTarget )
					} else {
						var hostUrl = uiSettings.analyticsUiUrl + "?host=" + launchTarget
								+ "&life=" + $( "#lifeSelect" ).val() + "&appId="
								+ $( "#appIdFilterSelect" ).val() + "&project="
								+ model.getSelectedProject();

						if ( primaryAttribute == "totActivity" ) {
							hostUrl = utils.getUserUrl( "hostName=" + launchTarget )
						}

						openWindowSafely( hostUrl, "_blank" );
					}
				} );

	}





	function calculateHistogramValue( reportId, attributeArray, rowJson ) {

		// console.log( reportId, attributeArray,  rowJson ) ;
		var total = 0;
		for ( var i = 0; i < attributeArray.length; i++ ) {
			var attributeKey = attributeArray[i];
			var divideBy = 1;
			if ( (reportId != "userid")
					&& !$( "#isUseDailyTotal" ).is( ':checked' )
					&& attributeKey.indexOf( "Avg" ) == -1 ) {
				divideBy = rowJson.numberOfSamples;

			}

			if ( !$( "#nomalizeContainer" ).is( ":visible" ) ) {
				divideBy = divideBy * (1 / $( "#nomalizeContainer select" ).val());
			}

			// console.log("calculateHistogramValue() " + columnName + " divideBy: " + divideBy) ;
			if ( attributeKey == "totalMemFree" || attributeKey == "memoryInMbAvg"
					|| attributeKey == "SwapInMbAvg" ) {

				return rowJson[attributeKey] / divideBy / 1024;
			}
			total += rowJson[attributeKey] / divideBy;
		}

		return total;
	}


} );