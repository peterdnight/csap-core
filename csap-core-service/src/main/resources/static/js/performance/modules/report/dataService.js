define( ["model"], function ( model ) {

	console.log( "Module loaded: reports/reportRequest" );


	var _lastReportResults = null;

	var _successCallback = null;
	var _compareReportResults = null;

	var _dateOffset = 0;
	var _compareOffset = 0;

	var $loadingMessage = jQuery( '<div/>', {
		class: "loadingPanel",
		text: "Building Report: Time taken is proportional to time period selected."
	} );

	var $loadingMessageColumn = jQuery( '<td />', { colspan: 99 } ).append( $loadingMessage );
	var $loadingMessageWrapper = jQuery( '<div/>', { } ).append( $loadingMessageColumn );

	var _lastReport = "vm";
// var _lastReport = "" ;
	var _lastService = "", _lastServiceReport = ""; // Used for remembering lo

	return {
		//
		setSuccessCallback: function ( callbackFunction ) {
			_successCallback = callbackFunction;
		},
		//
		getLastReportResults: function ( ) {
			return _lastReportResults;
		},
		//
		findCompareMatch: function ( field, value ) {
			return findCompareMatch( field, value );
		},
		reset: function () {
			_compareReportResults = null;
		},
		//
		runReport: function ( reportId, optionalServiceName ) {
			getReport( reportId, optionalServiceName );
		},
		//
		updateSelectedDates: function ( $sourceOfRequest ) {
			updateSelectedDates( $sourceOfRequest );
		},
		//
		isCompareSelected: function (  ) {
			return isCompareSelected();
		},
		getLastService: function () {
			return _lastService;
		},
		//
		getLastServiceReport: function () {
			return _lastServiceReport;
		},
		//
		getLastReportId: function () {
			return _lastReport;
		},
	}


	function isCompareSelected() {

		// user reports do not support
		if ( !$( "#compareLabel" ).is( ":visible" ) )
			return false;

		// console.log( "_compareOffset: "+  _compareOffset) ;
		return _compareOffset > 0;
	}


	/**
	 * Invoked when any item changes.
	 * 
	 * @param {type} $sourceOfRequest
	 * @returns {undefined}
	 */
	function updateSelectedDates( $sourceOfRequest ) {

		var objectId = $sourceOfRequest.attr( "id" );
		console.log( "updateSelectedDates() : " + objectId );

		if ( objectId == "numReportDays" ) {
			// no change to the days.
			return;
		}
		// invoked in global registration of inputs
//		  if ( _datePickerIds.indexOf( objectId ) == -1 )
//				return;

		var days = 0;

		if ( $sourceOfRequest.datepicker( "getDate" ) != null ) {
			days = calculateUsCentralDays( $sourceOfRequest.datepicker( "getDate" )
					.getTime() );
		}

		// $("#dayOffset", resourceRootContainer).val(days) ;
		console.log( "updateSelectedDates() Num days offset: " + days + " id: "
				+ objectId );

		if ( objectId == "reportStartInput" )
			_dateOffset = days;
		else
			_compareOffset = days;
	}


	function  getReport( reportId, optionalServiceName ) {


		// cleanup and reset compare UI
		$( "#compareCurrent" ).remove();
		$( ".diffHigh" ).remove();


		if ( reportId.indexOf( "detail" ) != -1 ) {
			$( "#showAllServicesButton" ).show();
			_lastServiceReport = reportId;
		} else {
			if ( reportId.indexOf( "service" ) != -1 ) {
				_lastServiceReport = "";
			}
			$( "#showAllServicesButton" ).hide();
		}

		// if ( reportId == ("jmx/detail") ) {
		if ( reportId.indexOf( "detail" ) != -1 ) {
			$( "#showAppMetricsButton" ).show();
		} else {
			$( "#showAppMetricsButton" ).hide();
		}
		// if (numReports++ > 8) { console.log("skipping") ;return; }

		$( "#reportLabel" ).html( "Service Resources" );
		// if ( reportId.indexOf("service") != -1) $("#reportLabel").html("Service
		// Resources" ) ;
		if ( reportId.indexOf( "vm" ) != -1 )
			$( "#reportLabel" ).html( "Host OS Resources:" );

		if ( reportId.indexOf( "user" ) != -1 )
			$( "#reportLabel" ).html( "Users: " );

		if ( optionalServiceName != undefined ) {
			_lastService = optionalServiceName;
			$( '#showAppMetricsButton' ).off();
			$( '#showAppMetricsButton' ).click( function () {
				// summary table
				$( "#visualizeSelect" ).val( "table" );
				getReport( "jmxCustom/detail", optionalServiceName )
			} );
		}

		_numServiceSample = 0; // reset counters for service sample count

		var amountOffset = _dateOffset;

		// 
		if ( _compareReportResults != null && isCompareSelected() ) {
			// ( $("#visualizeSelect").val() == "compare") ) {
			// $("#compareLabel").show();
			amountOffset = _compareOffset;
			console.log( "_compareOffset:  " + _compareOffset )
		} else {
			// $("#compareLabel").hide();
		}

		if ( _lastReportResults != null
				&& amountOffset > _lastReportResults.numDaysAvailable ) {
			alertify.notify( " Invalid number selection days: " + amountOffset
					+ " , Setting the maximum: "
					+ _lastReportResults.numDaysAvailable );
			if ( _lastReportResults.numDaysAvailable > 1 )
				amountOffset = _lastReportResults.numDaysAvailable - 1;
		}

		var paramObject = {
			numDays: $( "#numReportDays" ).val(),
			dateOffSet: amountOffset
		};

		// normalizing only supported on custom/jmx or application
		$( "#nomalizeContainer" ).hide();

		var tableId = "#" + reportId + "Table";

		if ( reportId == "service" ) {
			tableId = "#serviceSummaryTable";
			showServiceTable( tableId );

		} else if ( reportId == "service/detail" ) {

			tableId = "#serviceDetailTable";
			showServiceTable( "#serviceDetailDiv" );
			$.extend( paramObject, {
				serviceName: _lastService
			} );

		} else if ( reportId == "jmx" ) {
			showServiceTable( "#jmxSummaryDiv" );
			tableId = "#jmxSummaryTable";

		} else if ( reportId == "jmx/detail" ) {

			showServiceTable( "#jmxDetailDiv" );
			tableId = "#jmxDetailTable";
			$.extend( paramObject, {
				serviceName: _lastService
			} );

		} else if ( reportId == "jmxCustom/detail" ) {

			$( "#nomalizeContainer" ).show();
			_jmxCustomNeedsInit = true;
			showServiceTable( "#jmxCustomDiv" );
			tableId = "#jmxCustomTable";
			$.extend( paramObject, {
				serviceName: _lastService
			} );

		}

		if ( $( "#clusterSelect" ).val() != "all" ) {

			$.extend( paramObject, {
				cluster: $( "#clusterSelect" ).val()
			} );
		}

		var bodyId = tableId + " tbody";
		$( bodyId ).empty();

		var $loadingRow = jQuery( '<tr/>', {
			html: $loadingMessageWrapper.html()
		} );

		if ( reportId.indexOf( "user" ) == -1 ) {
			// only user reports have column 1 visible
			$loadingRow.prepend( jQuery( '<td/>', { } ) );
		}


		$( bodyId ).append( $loadingRow );
		// return;

		$( 'body' ).css( 'cursor', 'wait' );
		_lastReport = reportId;
		// $('#serviceOps').css("display", "inline-block") ;

		if ( !$( "#isAllProjects" ).is( ':checked' ) ) {
			$.extend( paramObject, {
				project: model.getSelectedProject()
			} );
		}

		if ( !$( "#isAllLifes" ).is( ':checked' ) ) {
			var life = $( "#lifeSelect" ).val();
			if ( life == null )
				life = uiSettings.lifeParam;
			$.extend( paramObject, {
				life: life
			} );
		}

		if ( !$( "#isAllAppIds" ).is( ':checked' ) ) {
			var app = $( "#appIdFilterSelect" ).val();
			if ( app == null )
				app = uiSettings.appIdParam;
			$.extend( paramObject, {
				appId: app
			} );
		}

		// 
		// Support for trending Reports
		//
		$( ".trendOption" ).hide();
		if ( $( "#metricsTrendingSelect" ).val() > 0 && $( "#visualizeSelect" ).val() != "table" ) {

			$( ".trendOption" ).show();


			if ( $( "#isTrendAll" ).is( ':checked' ) ) {
				delete paramObject.life;
			}

			var app = $( "#appIdFilterSelect" ).val();
			$.extend( paramObject, {
				"trending": true,
				"metricsId": $( "#visualizeSelect" ).val().split( "," )
			} );
			if ( !$( "#isUseDailyTotal" ).is( ':checked' ) && $( "#visualizeSelect" ).val().indexOf( "Avg" ) == -1 ) {

				$.extend( paramObject, {
					"divideBy": "numberOfSamples"
				} );

				if ( $( "#isUseVmTotal" ).is( ':checked' ) && $( "#visualizeSelect" ).val().indexOf( "Avg" ) == -1 ) {
					$.extend( paramObject, {
						"allVmTotal": "true"
					} );
				}
			}
			if ( reportId.indexOf( "detail" ) != -1 ) {
				$.extend( paramObject, {
					"serviceName": _lastService
				} );

			}
			// overwrite numDays based on trending selection
			paramObject["numDays"] = $( "#metricsTrendingSelect" ).val();
		}

		console.groupCollapsed( "Building report: " , reportId ,  " params: ", paramObject );
		$.getJSON( uiSettings.metricsDataUrl + "../report/" + reportId + "?callback=?&",
				paramObject )

				.done( function ( responseJson ) {

					//setTimeout( function () {

					getReportSuccess( responseJson, reportId, tableId );

					console.groupEnd( "Completed report " + reportId );
					//}, 1000 ) ;
				} )

				.fail(
						function ( jqXHR, textStatus, errorThrown ) {

							handleConnectionError( "Retrieving lifeCycleSuccess fpr host "
									+ hostName, errorThrown );
						} );
	}

	function getReportSuccess( responseJson, reportId, tableId ) {
		isDeltaMessage = true;
		//return false;

		// console.log( JSON.stringify(responseJson, null,"\t") )
		$( 'body' ).css( 'cursor', 'default' );

		//  return;  // uncomment to tune loading position
		var bodyId = tableId + " tbody";

		if ( responseJson.data.length > 0 ) {
			_lastReportResults = responseJson;
		}

		$( bodyId ).empty();
		$( "#metricPlot" ).remove();

		$( "#metricsTrendingContainer" ).hide();

		console.log( "reportSuccess() reportRequest.isCompareSelected(): " + isCompareSelected() );
		if ( _compareReportResults == null && isCompareSelected() ) {
			console.log( "reportSuccess() Got compare results" );
			_compareReportResults = responseJson;
			getReport( _lastReport );
			return;
		}

		_successCallback( responseJson, reportId, tableId );

	}


	function showServiceTable( id ) {

		$( "#jmxSummaryDiv" ).hide();
		$( "#serviceSummaryTable" ).hide();
		$( "#serviceDetailDiv" ).hide();
		$( "#jmxDetailDiv" ).hide();
		$( "#jmxCustomDiv" ).hide();
		$( id ).show();

	}




	function findCompareMatch( field, value ) {
		//console.log("Looking for: " + field + " value: " + value + " in: " , _compareReportResults) ;
		for ( var i = 0; i < _compareReportResults.data.length; i++ ) {
			var rowJson = _compareReportResults.data[i];
			//console.log("Looking for: " + field + " value: " + value + " in: " , rowJson) ;
			if ( rowJson[field] == value ) {
				return rowJson
			}
		}
		return null;
	}

} )