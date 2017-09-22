define( ["./service-graph"], function ( serviceGraph ) {

	console.log( "Module  loaded" );

	var _reportType;
	var _hostNameArray = null;

	var _sevenDayOffset = 1;

	var $appTable = $( "#appStats tbody" );
	var jmxRates = {
		httpRequestCount: { reportRate: "perHour" },
		sessionsCount: { reportRate: "perDay" },
	}

	var reportRateLookup = {
		perSecond: 1 / 30,
		per30Second: 1,
		perMinute: 2,
		perHour: 120,
		perDay: 2880
	}

	var _needsInit = true;

	return {
		// peter
		show: function ( hostNameArray ) {
			_hostNameArray = hostNameArray;
			show(  );
		},
		updateOffset: function ( numDays ) {
			console.log( "Updated _sevenDayOffset: " + numDays );
			_sevenDayOffset = numDays;
		}

	}

	function init() {

		if ( _needsInit ) {
			_needsInit = false;
			$( 'input[name=metricChoice]' ).click( function () {
				show();
			} );
			
		}

	}

	function show(  ) {


		init();
		_reportType = $( 'input[name=metricChoice]:checked' ).val();
		var paramObject = {
			hostName: _hostNameArray,
			serviceName: serviceName,
			type: _reportType,
			number: $("#numAppSamples").val(),
			interval: 30
		};

		if ( _reportType == "app" && customMetrics == null ) {
			$( ".loadingPanel", $appTable ).hide();
			$( ".info", $appTable ).show();
			return;
		}

		console.log( "show() Application for Service " + serviceName + " _reportType: " + _reportType );

		$appTable.empty();
		var $appRow = jQuery( '<tr/>', { } );
		$appRow.append( jQuery( '<td/>', { "colspan": "99", "html": '<div class="loadingPanel">Retrieving current, 24 hour and 7 day</div>' } ) )
		$appTable.append( $appRow );
		// ;
		$.getJSON(
				"service/query/getLatestAppStats",
				paramObject )

				.done( function ( responseJson ) {

					getLatestAppStatsSuccess( responseJson );

				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {
					console.log( "Error: getLatestAppStats " + hostName + " reason:", errorThrown )
					// handleConnectionError( "Retrieving
					// lifeCycleSuccess fpr host
					// " + hostName , errorThrown ) ;
				} );

	}

	function calculateAverage( inputArray ) {
		var total = 0;
		for ( var sampleIndex = 0; sampleIndex < inputArray.length; sampleIndex++ ) {
			total += inputArray[ sampleIndex ];
		}
		return total / inputArray.length;
	}

	function getLatestAppStatsSuccess( allHostsData ) {

		// console.log( " getLatestAppStatsSuccess(): " + JSON.stringify( allHostsData, null, "\t" ) );


		var serviceHostsArray = new Array();


		for ( var i = 0; i < _hostNameArray.length; i++ ) {
			var hostRow = allHostsData[ _hostNameArray[i] ];
			if ( hostRow != undefined && hostRow.data != undefined ) {
				var hostNumericObject = new Object();
				// strip out from array.
				for ( var metricName in hostRow.data ) {

//					if ( hostRow.data[ metricName].length && hostRow.data[ metricName].length == 1 ) {
//						hostNumericObject[ metricName ] = (hostRow.data[ metricName])[0];
//					}
					var numSamples = hostRow.data[ metricName].length;
					if ( numSamples ) {
						hostNumericObject[ metricName ] = calculateAverage( hostRow.data[ metricName] );
					}

				}
				serviceHostsArray.push( hostNumericObject );
			}

		}

		// agent mode does not have host prefix
		if ( serviceHostsArray.length == 0 ) {
			// direct connection?
			var singleHostData = allHostsData.data  ;
			var hostNumericObject = new Object();
			for ( var metricName in singleHostData) {
				//serviceHostsArray.push( allHostsData.data );
				hostNumericObject[ metricName ] = calculateAverage( singleHostData[ metricName] );
			}
			serviceHostsArray.push( hostNumericObject );
		}

		//


		//console.log( " getLatestAppStatsSuccess() input: " + JSON.stringify( serviceHostsArray, null, "\t" ) );
		var mergedServiceData = mergeArrayValues( serviceHostsArray );
		// console.log( " getLatestAppStatsSuccess(): merged: " + JSON.stringify( mergedServiceData, null, "\t" ) );
		//_latestServiceMetrics = responseJson;
		$( "#appStats .loadingPanel" ).hide();

		// initialize all values to be empty
		//$( "#serviceStats >div span" ).text( "-" );
		$appTable.empty();
		// return
		for ( var graphAndServiceName in  mergedServiceData ) {

			var graphName = graphAndServiceName.split( "_" )[0];
			if ( graphName == "timeStamp" || graphName == "totalCpu" )
				continue;
			// console.log("graphName: " + graphName + " graphAndServiceName:" + graphAndServiceName) ;
			var osClass = "stats" + graphName;

			var $appRow = jQuery( '<tr/>', {
				class: osClass,
				"data-key": graphName,
				"data-graph": graphName,
				"data-type": _reportType
			} ); 
			// .hover( serviceGraph.show, serviceGraph.hide );
		

			$appTable.append( $appRow );


			var label = getTitle( graphName );
			var currentValue = getValue( graphAndServiceName, mergedServiceData, label );

			var limit =
					getSetting( graphName, "max", null );
			var statusImage = '<img class="statusIcon"  src="images/16x16/green.png">';
			var labelClass = "";
			var description = "";
			var numHosts = serviceHostsArray.length;
//
			if ( limit != null && parseInt( currentValue / numHosts ) > limit ) {
				labelClass = "resourceWarning";
				statusImage = '<img class="statusIcon"  src="images/16x16/red.png">';
				description = "Current value exceeds specified limit: " + limit;
			}

			var $labelCell = jQuery( '<td/>', {
				title: "Click to view trends", 
				class: "showGraphCell",
				"data-raw": mergedServiceData[graphAndServiceName],
				"data-number": numHosts
			} ).appendTo( $appRow );
			
			$labelCell.click( serviceGraph.show ) ;

			var $label = jQuery( '<span/>', {
				class: labelClass,
				title: description,
				html: statusImage + label,
			} ).appendTo( $labelCell );


			var $currentLink = jQuery( '<a/>', {
				class: "simple",
				href: "#ViewCurrent",
				"data-resource": graphAndServiceName,
				html: currentValue
			} ).click( function () {
				if ( _reportType.contains( "app" ) ) {
					launchCustomJmxDash( $( "#instanceTable *.selected" ), "&graph=" + getGraphId( $( this ) ) );
				} else {
					launchJmxDash( $( "#instanceTable *.selected" ), "&graph=" + getGraphId( $( this ) ) );
				}

//				var targetUrl = "?service=" + serviceFilter + "&graph=" + getGraphId( $( this ) );
//				console.log( "launching: " + targetUrl );
//				launchHostDash( $( "#instanceTable *.selected" ), targetUrl );
				return false;
			} );

			var $currentCell = jQuery( '<td/>', { } );
			$currentCell.append( $currentLink );
			$currentCell.appendTo( $appRow );


			jQuery( '<td/>', {
				class: "day1",
				text: "-",
			} ).appendTo( $appRow );

			jQuery( '<td/>', {
				class: "day7",
				text: "-",
			} ).appendTo( $appRow );

//			var $averageCell = jQuery( '<td/>', { class: "average" } ).appendTo( $osRow );
//			jQuery( '<div/>', { class: "day1", text: "-" } ).appendTo( $averageCell );
//			jQuery( '<div/>', { class: "day7", text: "-" } ).appendTo( $averageCell );


			if ( graphAndServiceName == "diskUtil" && isDiskGb() ) {
				limit = (limit / 1000).toFixed( 1 ) + getUnits( true );
			}

			jQuery( '<td/>', {
				class: "limitsColumn",
				html: limit,
			} ).appendTo( $appRow );


		}
		getServiceReport( 1 );
		return;
	}

	function getSetting( meterName, element, defaultValue ) {
		if ( customMetrics != null ) {
			var attributeConfig = customMetrics[meterName];
			if ( attributeConfig && attributeConfig[element] )
				return attributeConfig[element];
		}
		return defaultValue;
	}

	function getTitle( meterName ) {
		var graphTitle =
				getSetting( meterName, "title", splitUpperCase( meterName ) );

		// jmx Titles hack - need to get from collector, hardcoding for
		// now.
		if ( jmxLabels[meterName] != undefined ) {
			graphTitle = jmxLabels[meterName];

			if ( jmxRates[ meterName ] != undefined ) {
				//attributeConfig = jmxRates[ meterName ];
			}
		}

		return graphTitle;
	}

	function getRateAdjust( performanceItemName ) {

		var attributeConfig = null;
		var rateAdjust = 1;
		var rateCustom = getSetting( performanceItemName, "reportRate", null );

		if ( $( '#rateSelect' ).val() != "default" ) {
			rateAdjust = reportRateLookup [ $( '#rateSelect' ).val() ];
		} else if ( rateCustom != null ) {
			rateAdjust = reportRateLookup [ rateCustom ];
			// add custom label...
			var $labelColumn = getMetricLabel( performanceItemName );
			$( "span.customRate", $labelColumn ).remove();
			var $label = jQuery( '<span/>', {
				class: "customRate",
				text: rateCustom,
			} ).appendTo( $labelColumn );
		}

		return rateAdjust;
	}

	function getValue( performanceItemName, appData, label ) {

		if ( !label )
			label = getTitle( performanceItemName );

		var divideBy = 1;

		var appValue = "-";

		if ( appData && (appData[performanceItemName] != "undefined") ) {

			if ( appData.numberOfSamples ) {
				divideBy = appData.numberOfSamples;
			}

			divideBy = divideBy / getRateAdjust( performanceItemName );

			appValue = Math.round( appData[performanceItemName] / divideBy );

			if ( Math.abs( appValue ) > 2000 ) {
				if ( label.toLowerCase().contains( "(mb)" ) ) {
					appValue = (appValue / 1024).toFixed( 1 );
					appValue += getUnits( "Gb" )
				} else if ( label.toLowerCase().contains( "(ms)" ) ) {

					appValue = (appValue / 1000).toFixed( 2 );
					appValue += getUnits( "s" )
				} else {
					if ( Math.abs( appValue ) > 1000000 ) {
						appValue = (appValue / 1000000).toFixed( 2 );
						appValue += getUnits( "M" )
					} else {
						appValue = (appValue / 1000).toFixed( 1 );
						appValue += getUnits( "K" )
					}
				}
			}
		}

		return appValue;

	}

	function getUnits( unit ) {
//		var unit = "Mb";
//		if ( isGb )
//			unit = "Gb";
		return '<div class="units">' + unit + '</div>';
	}



	/** @memberOf ServiceAdmin */
	function isMemoryMb() {
		return  (resourceLimits[ "rssMemory" ].search( /m/i ) != -1);
	}


	/** @memberOf ServiceAdmin */
	function isMemoryGb() {
		return  (resourceLimits[ "rssMemory" ].search( /g/i ) != -1);
	}


	/** @memberOf ServiceAdmin */
	function isDiskGb() {
		return  (resourceLimits[ "diskUtil" ] > 1000);
	}

	function getServiceReport( numDays, reportStartOffset ) {

		if ( reportStartOffset == undefined )
			reportStartOffset = 0;

		console.log( "getServiceReport(): " + " numDays: " + numDays + " reportStartOffset: " + reportStartOffset );

		var targetHost = hostName;
		if ( window.isDesktop ) {
			// hook for desktop testing
			targetHost = "csap-dev01"  // agent testing
			// targetProject = "All Packages" ;   
			console.log( "Hook for desktop: " + _graphReleasePackage + " targetHost: " + targetHost + " For agent: uncomment previous line" );
		}

		var paramObject = {
			appId: appId,
			report: _reportType,
			project: _graphReleasePackage,
			life: life,
			serviceName: serviceShortName,
			numDays: numDays,
			dateOffSet: reportStartOffset
		};

		if ( _hostNameArray.length == 1 ) {
			$.extend( paramObject, {
				host: targetHost
			} );
		}
		// console.log("getServiceReport: " + report + " days: " + numDays)
		// ;
		$.getJSON(
				reportUrl,
				paramObject )

				.done( function ( responseJson ) {
					showReportAverage( responseJson, reportStartOffset );
					//getServiceReport( 7, _compareOffset );
				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {
					//reportSuccess( null );
					console.log( "Error: Retrieving report fpr host " + hostName, errorThrown )
					// handleConnectionError( "Retrieving
					// lifeCycleSuccess fpr host
					// " + hostName , errorThrown ) ;
				} );

	}

	function getMetricLabel( performanceMetric ) {
		var osClass = " .stats" + performanceMetric;
		return $( "td:nth-child(1)", osClass );
	}

	function showReportAverage( dataForSelectedPackages, reportStartOffset ) {

		// console.log( " showReportAverage(): " + JSON.stringify( responseJson, null, "\t" ) );

		var mergedPackageReports = mergeArrayValues( dataForSelectedPackages.data );
		var reportColumn = "td:nth-child(" + (reportStartOffset + 3) + ")";

		for ( var performanceMetric in  mergedPackageReports ) {

			var osClass = " .stats" + performanceMetric;
			//console.log( "showReportAverage() osClass: " + osClass + " reportColumn:" + reportColumn );
			var reportAverage = getValue( performanceMetric, mergedPackageReports );
			var rawReport = 0;
			if ( mergedPackageReports[performanceMetric] && mergedPackageReports.numberOfSamples ) {
				rawReport = Math.round( mergedPackageReports[performanceMetric] / mergedPackageReports.numberOfSamples );
			}

			var $labelColumn = getMetricLabel( performanceMetric );
			var rawCurrent = Math.round( $labelColumn.data( "raw" ) / $labelColumn.data( "number" ) );
			var attributePercent = Math.round(
					Math.abs( (rawCurrent - rawReport) / rawReport ) * 100 );

			// lots of corner cases...
			if ( rawCurrent <= 1 && reportAverage <= 1 ) {
				attributePercent = 0;
			}

//			console.log("showReportAverage() current: "  + performanceMetric 
//					+ " rawCurrent: " + rawCurrent + " rawReport" + rawReport 
//					+ " attributePercent: " + attributePercent) ;

			var filterThreshold = $( "#filterThreshold" ).val();

			var warningPresent = $( ".resourceWarning", $labelColumn ).length > 0;
			//if ()
			if ( !warningPresent && attributePercent > filterThreshold && reportStartOffset == 0 ) {
				var msg = "Last collected value differs from 24 hour average by " + attributePercent + "%";
				$( "span", $labelColumn )
						.addClass( "resourceWarning" )
						.attr( "title", msg );

				$( "img", $labelColumn ).attr( "src", "images/16x16/yellow.png" );

			}
			var $historicalLink = jQuery( '<a/>', {
				class: "simple",
				href: "#ViewHistorical",
				"data-resource": performanceMetric,
				html: reportAverage
			} ).click( function () {
//				var reportType = _reportType;
//				var urlAction = analyticsUrl + "&graph=" + getGraphId( $( this ) ) + "&project=" + selectedProject
//						+ "&report=graphService&host=" + hostName
//						+ "&service=" + serviceShortName + "&appId=" + appId + "&";
				var urlAction = analyticsUrl + "&graph=" + getGraphId( $( this ) )
						+ "&project=" + selectedProject + "&host=" + hostName
						+ "&service=" + serviceShortName + "&report=graphJmx&appId=" + appId + "&";

				if ( _reportType.contains( "jmxCustom" ) ) {
					urlAction += "appGraph=appGraph&"
				}

				openWindowSafely( urlAction, "_blank" );
				return;
			} );
			$( reportColumn, osClass ).empty();
			$( reportColumn, osClass ).append( $historicalLink );

		}

		if ( reportStartOffset == 0 ) {
			getServiceReport( 7, _sevenDayOffset );
		}
	}

	// historical hack for some graphs
	function getGraphId( $link ) {
		var graphId = $link.data( "resource" );
//		if ( graphId.contains( "rssMemory" ) || graphId.contains( "diskUtil" ) )
//			graphId += "InMB";
//
//		if ( graphId.contains( "topCpu" ) )
//			graphId = "Cpu_15s";

		return graphId;
	}

	// ForAllServicePackages
	function mergeArrayValues( inputArray ) {

		//return inputArray[0] ;

		//console.log( "mergeArrayValues() - merging object count: " + inputArray.length );

		var mergedArray = inputArray[0];

		for ( var i = 1; i < inputArray.length; i++ ) {
			for ( var attribute in mergedArray ) {
				var mergeValue = mergedArray[ attribute ];
				var iterValue = inputArray[i][ attribute ];

				if ( $.isNumeric( mergeValue ) && $.isNumeric( iterValue ) ) {
					mergedArray[ attribute ] = mergeValue + iterValue;
					//console.log( attribute + " numeric mergeValue: " + mergeValue + " iterValue: " + iterValue )
				} else {
					//console.log( attribute + " nonNUMERIC mergeValue: " + mergeValue + " iterValue: " + iterValue )
				}

			}
		}


		return mergedArray;
	}

} );