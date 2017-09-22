

define( ["model", "windowEvents", "./dataService", "./table", "./utils", "./histogram", "./trends", "./summaryTab"], function ( model, windowEvents, dataService, table, utils, histogram, trends, summaryTab ) {

	console.log( "Module loaded: reports/reports: model: " + model + " win: " + windowEvents );
	var _numServiceSample = 0;
	var numReports = 0;
	return {
		//
		//	function init() {
		initialize: function () {
			console.log("initializing callbacks") ;
			configureDatePickers();
			dataService.setSuccessCallback( reportResponseRouter );
			$( "#isShowJmx" ).change( function () {
				showJmxReport();
			} )

		},
		// 
		showAllServices: function () {
			dataService.reset();
			var reportTarget = "service";
			reportLabel = "OS Resources";
			console.log( "showAllServices(): " + dataService.getLastReportId() )
//				if ( dataService.getLastReportId() == "jmxCustom/detail" ) {
//					 reportTarget="jmx" ; reportLabel = "Application Resources" ;
//				} 
			if ( dataService.getLastReportId() == "jmx/detail" ) {
				reportTarget = "jmx";
				reportLabel = "JMX Resources";
			}
			$( "#serviceLabel" ).html( reportLabel );
			dataService.runReport( reportTarget );
		},
		//
		resetReportResults: function () {
			dataService.reset();
		},
		//
		getLastService: function () {
			return dataService.getLastService();
		},
		//
		getLastServiceReport: function () {
			return dataService.getLastServiceReport();
		},
		//
		runSummaryReports: function ( runOnlyIfNeeded ) {
			runOnlyIfNeeded = typeof runOnlyIfNeeded !== 'undefined' ? runOnlyIfNeeded : false;
			summaryTab.runSummaryReports( runOnlyIfNeeded );
		},
		//
		hide: function () {

			$( "#reportsSection" ).hide();
			$( "#reportsSection table" ).hide();
		},
		getReport: function ( reportId, optionalServiceName ) {
			dataService.runReport( reportId, optionalServiceName ) ;
		},
		// helper used in model module
		getLastReport: function () {
			dataService.runReport( dataService.getLastReportId() );
		},
		//
		triggerReport: function ( $sourceOfRequest ) {
			triggerReport( $sourceOfRequest );
		}

	};
	function reportResponseRouter( responseJson, reportId, tableId ) {
		// 
		if ( $( "#visualizeSelect" ).val() != "table" ) {
			// alertify.notify("Visualize") ;
			// $("#compareLabel").show();
			if ( $( "#metricsTrendingSelect" ).val() == 0 ) {
				histogram.loadData( responseJson, reportId );
			} else {
				trends.loadData( responseJson, reportId );
			}
			windowEvents.resizeComponents();
		} else {
			table.loadData( responseJson, reportId, tableId );
		}
	}

	function configureDatePickers() {
		var _datePickerIds = "#reportStartInput, #compareStartInput";
		$( _datePickerIds ).datepicker( {
			maxDate: '0'
		} );
		$( _datePickerIds ).css( "width", "7em" );
		$( _datePickerIds ).change( function () {
			dataService.updateSelectedDates( $( this ) );
		} );
	}

	function triggerReport( $sourceOfRequest ) {

		console.log( "Value Changed: " + $sourceOfRequest.attr( "id" ) + "  - Clicking: "
				+ dataService.getLastReportId() + " " );
		dataService.reset();
		dataService.updateSelectedDates( $sourceOfRequest );
		if ( $sourceOfRequest.attr( "id" ) == "metricsTrendingSelect" ) {
			console.log( "Triggering trending report" );
		}

		dataService.runReport( dataService.getLastReportId() );
		$( "#clearCompareButton" ).hide();
		if ( dataService.isCompareSelected() ) {
			$( "#clearCompareButton" ).show();
		}
	}

	function showJmxReport() {

		console.log( "showJmxReport()" );
		$( "#visualizeSelect" ).val( "table" )
		var targetReport = "service";
		if ( $( "#isShowJmx" ).is( ':checked' ) ) {
			targetReport = "jmx";
			// console.log("showJmx() _lastReport: " + reportRequest.getLastReportId())
		}

		if ( dataService.getLastReportId().indexOf( "detail" ) != -1 ) {
			targetReport = targetReport + "/detail"
		}

		dataService.runReport( targetReport );
	}


} );




