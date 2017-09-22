define( ["./service-graph"], function ( serviceGraph ) {

	console.log( "Module  loaded" );


	var $osTable = $( "#serviceStats tbody" );
	var _hostNameArray = null;
	
	var _showMissingDataMessage = true;

	var _sevenDayOffset = 1;

	var _osKeys = { "topCpu": "Cpu", "threadCount": "Threads", "rssMemory": "Memory",
		"fileCount": "Open Files", "socketCount": "Sockets",
		"diskUtil": "Disk Used", "diskWriteKb": "Disk Writes(kb)" };

	var _needsInit = true;

	return {
		// 
		show: function ( hostNameArray ) {
			_hostNameArray = hostNameArray;
			show();
		},
		updateOffset: function ( numDays ) {
			_sevenDayOffset = numDays;
		}

	}

	function init() {

		if ( _needsInit ) {
			_needsInit = false;
			$( "#viewAlertsColumn" ).click( function () {
				$( ".limitsColumn" ).toggle();
			} )

			$( '#osAnalytics' ).click( function () {
				var urlAction = analyticsUrl + "&project=" + selectedProject + "&report=service/detail"
						+ "&service=" + serviceShortName + "&appId=" + appId + "&";

				openWindowSafely( urlAction, "_blank" );
				return false;
			} );

		}

	}

	function show() {

		init();

		csapRotate( $( "#eolWarningButton" ), 30, "rotate" );
		setTimeout( function () {
			csapRotate( $( "#eolWarningButton" ), 0, "rotate" )
		}, 3000 )


		var paramObject = {
			serviceName: serviceName,
			hostName: _hostNameArray
		};

		$osTable.empty();
		var $appRow = jQuery( '<tr/>', { } );
		$appRow.append( jQuery( '<td/>', { "colspan": "99", "html": '<div class="loadingPanel">Retrieving current, 24 hour and 7 day</div>' } ) )
		$osTable.append( $appRow );

		// console.log("getServiceReport: " + report + " days: " + numDays)
		// ;
		$.getJSON(
				"service/getLatestServiceStats",
				paramObject )

				.done( function ( responseJson ) {

					getLatestServiceStatsSuccess( responseJson );

				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {
					console.log( "Error: getLatestServiceStats " + hostName + " reason:", errorThrown )
					// handleConnectionError( "Retrieving
					// lifeCycleSuccess fpr host
					// " + hostName , errorThrown ) ;
				} );

	}

	function getLatestServiceStatsSuccess( responseJsonArray ) {
		console.log( " getLatestServiceStatsSuccess(): " + JSON.stringify( responseJsonArray, null, "\t" ) );
		//_latestServiceMetrics = responseJson;

		var mergedServiceData = mergeArrayValues( responseJsonArray.data );
		$osTable.empty();
		if ( !mergedServiceData ) {
			// alertify.alert() ;
			var $appRow = jQuery( '<tr/>', { } );
			var message = "Real time data not present - validate service is deployed.";
			$appRow.append( jQuery( '<td/>', { "colspan": "99",
				"html": '<div class="">' + message + '</div>' } ) )
			$osTable.append( $appRow );
			return;
		}

		// initialize all values to be empty
		//$( "#serviceStats >div span" ).text( "-" );

		for ( var osResourceKey in  _osKeys ) {
			var graphId = getGraphId( osResourceKey );
			var osClass = "stats" + osResourceKey;
			var $osRow = jQuery( '<tr/>', {
				"data-graph": graphId,
				"data-type": "service",
				class: osClass
			} ); //.hover( serviceGraph.show, serviceGraph.hide );



			var currentValue = getValue( osResourceKey, mergedServiceData );
			var rawValue = parseFloat( currentValue );


			var limit = parseInt( resourceLimits[ osResourceKey ] );
			if ( osResourceKey == "diskUtil" && isDiskGb() ) {
				
				limit = parseFloat (limit / 1024) ;
			}

			var labelClass = "";
			var description = "";
			var statusImage = '<img class="statusIcon"  src="images/16x16/green.png">';
			var numHosts = responseJsonArray.data.length;

			// console.log( "osResourceKey: " + osResourceKey + " limit: " + limit + " currentValue: " + currentValue);
			if ( (parseInt( currentValue ) / numHosts) > limit ) {
				statusImage = '<img class="statusIcon"  src="images/16x16/red.png">';
				labelClass = "resourceWarning";
				description = "Current value exceeds specified limit: " + limit;
			}

			var $labelCell = jQuery( '<td/>', {
				title: "Click to view trends", 
				class: "showGraphCell",
				"data-raw": rawValue,
				"data-number": numHosts
			} ).appendTo( $osRow );
			
			$labelCell.click( serviceGraph.show ) ;

			var $label = jQuery( '<span/>', {
				class: labelClass,
				title: description,
				html: statusImage + _osKeys[ osResourceKey ],
			} );

			$label.appendTo( $labelCell );

			var $currentLink = jQuery( '<a/>', {
				class: "simple",
				href: "#ViewCurrent",
				"data-resource": graphId,
				html: currentValue
			} ).click( function () {

				var targetUrl = "?service=" + servicePerformanceId + "&graph=" + getGraphIdFromAttribute( $( this ) );
				console.log( "launching: " + targetUrl );
				launchHostDash( $( "#instanceTable *.selected" ), targetUrl );
				return false;
			} );

			var $currentCell = jQuery( '<td/>', { } );
			$currentCell.append( $currentLink );
			$currentCell.appendTo( $osRow );

			jQuery( '<td/>', {
				class: "day1",
				text: "-",
			} ).appendTo( $osRow );

			jQuery( '<td/>', {
				class: "day7",
				text: "-",
			} ).appendTo( $osRow );

//			var $averageCell = jQuery( '<td/>', { class: "average" } ).appendTo( $osRow );
//			jQuery( '<div/>', { class: "day1", text: "-" } ).appendTo( $averageCell );
//			jQuery( '<div/>', { class: "day7", text: "-" } ).appendTo( $averageCell );


			if ( osResourceKey == "diskUtil" && isDiskGb() ) {
				limit = (limit / 1000).toFixed( 1 ) + getUnits( true );
			}

			jQuery( '<td/>', {
				class: "limitsColumn",
				html: limit,
			} ).appendTo( $osRow );


			$osTable.append( $osRow );
		}
		getServiceReport( 1 );
		return;
	}

	function getValue( osKey, osData ) {

		var osValue = "-";

		var isParse = false;
		var divideBy = 1;

		var isRealTime = false;

		if ( osData ) {
			if ( osData.cpuUtil && osData.cpuUtil != "-" ) {
				isParse = true;
				isRealTime = true;
			}

			if ( !isRealTime && osData.topCpu != undefined ) {
				isParse = true;
			}
			if ( osData.numberOfSamples ) {
				divideBy = osData.numberOfSamples;
			}
		}



		if ( isParse ) {
			osValue = Math.round( osData[ osKey ] / divideBy );

			if ( osKey == "topCpu" ) {
				osValue = (osData[ osKey ] / divideBy).toFixed( 1 ) + '<div class="units">%</div>';
			}

			if ( osKey == "rssMemory" ) {
				if ( isRealTime ) {
					// real time always returns raw bytes
					// osValue = Math.round( osValue / 1024 );
					osValue = osValue ;
				}
				if ( isMemoryMb() ) {
					osValue = (osValue).toFixed( 0 ) + getUnits();
				} else if ( isMemoryGb() ) {
					osValue = (osValue / 1024).toFixed( 1 ) + getUnits( true );
				}
			}

			if ( osKey == "diskUtil" ) {

				if ( isDiskGb() ) {
					osValue = (osValue / 1024).toFixed( 1 ) + getUnits( true );
				} else {
					osValue = osValue + getUnits();
				}
			}

		}

//		console.log("getValue() : " + osKey + " isParse: " + isParse + " osValue:" + osValue)

		//console.log(JSON.stringify( osData, null, "\t" ) + " getValue() : osKey: " + osKey + " returning: " + osValue) ;

		return osValue;

	}

	function getUnits( isGb ) {
		var unit = "Mb";
		if ( isGb )
			unit = "Gb";
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

		

		var targetHost = hostName;
		if ( window.isDesktop ) {
			targetHost = "csap-dev01"  // agent testing
			// targetProject = "All Packages" ;   
			console.log( "Hook for desktop: " + _graphReleasePackage + " targetHost: " + targetHost + " For agent: uncomment previous line" );
		}
		

		
		var paramObject = {
			appId: appId,
			report: "service",
			project: _graphReleasePackage,
			life: life,
			serviceName: servicePerformanceId, 
			numDays: numDays,
			dateOffSet: reportStartOffset
		};

		if ( _hostNameArray.length == 1 ) {
			$.extend( paramObject, {
				host: targetHost
			} );
		}
		
		console.log( "getServiceReport(): " , reportUrl, paramObject );
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
					reportSuccess( null );
					console.log( "Error: Retrieving lifeCycleSuccess fpr host " + hostName, errorThrown )
					// handleConnectionError( "Retrieving
					// lifeCycleSuccess fpr host
					// " + hostName , errorThrown ) ;
				} );

	}

	function getMetricLabel( performanceMetric ) {
		var osClass = " .stats" + performanceMetric;
		return $( "td:nth-child(1)", osClass );
	}
	function showReportAverage( responseJson, reportStartOffset ) {

		//console.log( " showReportAverage(): " + JSON.stringify( responseJson, null, "\t" ) );

		var mergedHostReports = mergeArrayValues( responseJson.data );
		if ( ! mergedHostReports ) {
			if ( _showMissingDataMessage ) {
				alertify.notify("Historical data not found - verify application - settings - data collection.") ;
				_showMissingDataMessage=false;
			}
			console.log("showReportAverage() - Did not find data for service", responseJson)
			return;
		}
		
		var reportColumn = "td:nth-child(" + (reportStartOffset + 3) + ")";

		for ( var osResourceName in  _osKeys ) {

			var osClass = ".stats" + osResourceName;

			var reportAverage = getValue( osResourceName, mergedHostReports );
			var rawReport = 0;

			if ( mergedHostReports[osResourceName] && mergedHostReports.numberOfSamples ) {
				rawReport = Math.round( mergedHostReports[osResourceName] / mergedHostReports.numberOfSamples );
			}

			var $labelColumn = getMetricLabel( osResourceName );
			// multiple hosts
			var rawCurrent = ($labelColumn.data( "raw" ) / $labelColumn.data( "number" ));
			if ( (osResourceName == "rssMemory" || osResourceName == "diskUtil"  )
					&&  reportAverage.contains("Gb")  ) {
				// special case for GB
				rawCurrent = (rawCurrent * 1024).toFixed(1) ;
			}

			var attributePercent = Math.round(
					Math.abs( (rawCurrent - rawReport) / rawReport ) * 100 );

			// lots of corner cases...
			if ( rawCurrent <= 1 && parseFloat( reportAverage ) <= 1 ) {
				attributePercent = 0;
			}

//			console.log( "showReportAverage() current: " + osResourceName + " reportAverage: " + reportAverage
//					+ " rawCurrent: " + rawCurrent + " rawReport: " + rawReport
//					+ " attributePercent: " + attributePercent + " reportStartOffset: " + reportStartOffset
//					+ " column raw: " + $labelColumn.data( "raw" ) );

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

			//console.log( "showReportAverage() osClass: " + osClass + " reportColumn:" + reportColumn );
			var $historicalLink = jQuery( '<a/>', {
				class: "simple",
				href: "#ViewHistorical",
				"data-resource": osResourceName,
				html: reportAverage
			} ).click( function () {
				var urlAction = analyticsUrl + "&graph=" + getGraphIdFromAttribute( $( this ) ) + "&project=" + selectedProject
						+ "&report=graphService&host=" + hostName
						+ "&service=" + serviceShortName + "&appId=" + appId + "&";

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

	function getGraphId( name ) {
		var graphId = name;

//		if ( graphId.contains( "rssMemory" ) || graphId.contains( "diskUtil" ) )
//			graphId += "InMB";
//
//		if ( graphId.contains( "topCpu" ) )
//			graphId = "Cpu_15s";

		return graphId;
	}
	// historical hack for some graphs
	function getGraphIdFromAttribute( $link ) {
		var graphId = $link.data( "resource" );

		return graphId;
	}

	// ForAllServicePackages
	function mergeArrayValues( inputArray ) {

		//return inputArray[0] ;

		var mergedArray = inputArray[0];

		for ( var i = 1; i < inputArray.length; i++ ) {
			for ( var attribute in mergedArray ) {
				var mergeValue = mergedArray[ attribute ];
				var iterValue = inputArray[i][ attribute ];
				if ( _osKeys[ attribute ] ) {
					iterValue = parseInt( iterValue );
					mergeValue = parseInt( mergeValue );
				}

				if ( $.isNumeric( mergeValue ) && $.isNumeric( iterValue ) ) {
					mergedArray[ attribute ] = mergeValue + iterValue;
					//console.log( attribute + " numeric mergeValue: " + mergeValue + " iterValue: " + iterValue)
				} else {

					//console.log( attribute + " nonNUMERIC mergeValue: " + mergeValue + " iterValue: " + iterValue)
				}

			}
		}


		return mergedArray;
	}

} );