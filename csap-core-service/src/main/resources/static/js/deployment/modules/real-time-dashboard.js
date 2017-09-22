// http://requirejs.org/docs/api.html#packages
// Packages are not quite as ez as they appear, review the above
require.config( {
	packages: []
} );



require( ["real-time-meter"], function ( realTimeMeter ) {

	console.log( "\n\n ************** _main: loaded *** \n\n" );



	var _selectedHistogram = null;

	var _progressBar = null;
	var _progressTimer;
	var _progressCount = 0;
	var NUM_REFRESH = 11; // 10 * 3 seconds = update interval
	// Shared variables need to be visible prior to scope

	$( document ).ready( function () {
		initialize();
	} );



	this.initialize = initialize;
	function initialize() {

		console.log( "Init app" );
		

		CsapCommon.configureCsapAlertify();

		// getLatestMeter() ;

		getRealTimeMeters();

	}


	function configureTables( sortColumn ) {
		
		console.log("sorting on: ", sortColumn) ;
		

		$( "#vmTable" ).tablesorter( {
			sortList: [[ sortColumn , 1]],
			theme: 'csapSummary',
			widgets: ['math'],
			widgetOptions: {
				math_mask: '##0',
				math_data: 'math',
				columns_tfoot: false,
				math_complete: tableMathFormat
			}
		} );

	}

	var tableMathFormat = function ( $cell, wo, result, value, arry ) {
		var txt = '<span class="align-decimal">' + result + '</span>';
		if ( $cell.attr( 'data-prefix' ) != null ) {
			txt = $cell.attr( 'data-prefix' ) + txt;
		}
		if ( $cell.attr( 'data-suffix' ) != null ) {
			txt += $cell.attr( 'data-suffix' );
		}
		return txt;
	}

	function updateProgress() {

		if ( _progressBar == null ) {

			_progressBar = $( "#progressbar" ).progressbar( {
				max: 11
			} );

			$( "#progressbar" ).css( { 'background': 'url(images/white-40x100.png) #ffffff repeat-x 50% 50%;' } );
			$( "#pbar1 > div" ).css( { 'background': 'url(images/lime-1x100.png) #cccccc repeat-x 50% 50%;' } );

		}
		_progressCount++;
		_progressBar.progressbar( "option", "value", _progressCount );
		if ( _progressCount == NUM_REFRESH ) {
			_progressCount = 0;
			getRealTimeMeters();
		} else {

			_progressTimer = setTimeout( function () {
				updateProgress();
			}, 3 * 1000 );
		}
	}


	function getRealTimeMeters() {
		clearTimeout( _progressTimer );

		var meters = new Array();
		if ( meterIdParam != "" ) {
			meters.push( meterIdParam );
			_selectedHistogram = meterIdParam;
			meterIdParam = "";
		} else {
			//console.log( "meterInput:checked: " + $(".meterInput:checked").length ) ;
			$( ".meterInput:checked" ).each( function () {
				//console.log ( "pushing: " +  $(this).data("meter") ) ;
				meters.push( $( this ).data( "meter" ) );
			} );
		}

		console.log( "project: " + projectParam + " _selectedHistogram: " + _selectedHistogram );

		var paramObject = {
			project: projectParam,
			meterId: meters
		};

		$.getJSON(
				baseUrl + "/realTimeMeters",
				paramObject )

				.done( function ( responseJson ) {
					realTimeMetersSuccess( responseJson );
					updateProgress();
				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {
					console.log( "Error: Retrieving lifeCycleSuccess fpr host " + hostName, errorThrown )
					// handleConnectionError( "Retrieving lifeCycleSuccess fpr host " + hostName , errorThrown ) ;
				} );

	}


	function  realTimeMetersSuccess( responseJson ) {

		//console.log(" Success: " +  JSON.stringify(responseJson, null,"\t") ) 
		buildMeters( responseJson )
	}


	function buildMeters( responseJson ) {


		$( "#analyticsMeters" ).empty();
		for ( var i = 0; i < responseJson.length; i++ ) {


			var meterJson = responseJson[ i ];

			var meterId = "meter" + i;
			// console.log( " meterJson: " +  JSON.stringify(meterJson, null,"\t") ) 

			if ( ! realTimeMeter.renderMeter( meterId, meterJson, meterSelected ) )
				continue;

			meterClickRegistration( meterId, meterJson )
		}

		addToMetricTable( responseJson );

		addSelectedHistogram( responseJson );

	}
	
	function meterSelected( selectedHistogram ) {
		console.log("meterSelected() " + selectedHistogram) ;
		_selectedHistogram = selectedHistogram ;
		getRealTimeMeters() ;
	}

	
//$(".meterInput:checked").each(function() {
//	//console.log ( "pushing: " +  $(this).data("meter") ) ;
//	meters.push( $(this).data("meter") ) ;
//}) ;

	function addSelectedHistogram( responseJson ) {

		if ( $( ".meterInput:checked" ).length == 0 ) {
			alertify.notify( "No meters are selected" );
			console.log("no inputs checked, _selectedHistogram: ", _selectedHistogram) ;
			return;
		}
		var heightAdjust = 0.65;
		var seriesTitles = [];
		var seriesColorsArray = ["#05B325"];
		var selectedMeter = null;


		for ( var i = 0; i < responseJson.length; i++ ) {
			var meterJson = responseJson[ i ];

			if ( !meterJson.hostNames )
				continue;
			if ( !meterJson.hostValues )
				continue;

			console.log( "addSelectedHistogram() : selectedMetricId: " + _selectedHistogram
					+ " current: " + meterJson.id );
			if ( meterJson.id == _selectedHistogram ) {
				seriesTitles = [meterJson.label];
				selectedMeter = meterJson;
				break;
			}

		}


		if ( selectedMeter == null ) {
			console.log( "There is no data for selected meter in current project: " + _selectedHistogram );
			alertify.alert( "There is no data for selected meter in current project: " + _selectedHistogram );
			return;
		}

		// ugly sorting of 2 related arrays
		var list = [];
		for ( var j = 0; j < selectedMeter.hostNames.length; j++ ) {
			list.push( { 'val': selectedMeter.hostValues[j], 'host': selectedMeter.hostNames[j] } );
		}

		list.sort( function ( a, b ) {
			return ((a.val < b.val) ? -1 : ((a.val == b.val) ? 0 : 1));
		} );

		var sortedVals = new Array();
		sortedLabels = new Array();
		for ( var k = 0; k < list.length; k++ ) {

			var meterValue = list[k].val;
			
				if ( meterJson.divideBy != undefined && meterJson.divideBy != "vmCount" )
					meterValue = (meterValue / meterJson.divideBy).toFixed( 1 );
				
			//meterValue = realTimeMeter.checkDivideBy( meterJson, meterValue );
			if ( meterJson.multiplyBy != undefined )
				meterValue = (meterValue * meterJson.multiplyBy).toFixed( 1 );

			sortedVals.push( Number( meterValue ) );
			sortedLabels.push( list[k].host );
		}


		// console.log( "Plot vals: " +  JSON.stringify(sortedVals, null,"\t")  + " labels: " +  JSON.stringify(sortedLabels, null,"\t") ) ;
		var seriesToPlot = [sortedVals];
		var seriesLabel = sortedLabels;

		var hightlightColorArray = new Array();
		for ( var i = 0; i < seriesTitles.length; i++ ) {
			hightlightColorArray.push( "black" );
		}

		var targetFrame = $( "#meterHistogram" );
		targetFrame.empty();

		var meterDiv = jQuery( '<div/>', {
			id: "metricPlot",
			title: "Last metric selected will be shown on histogram",
			class: "meterGraph"
		} ).css("width", "400px").css( "height", ((seriesLabel.length + 5) * heightAdjust) + "em" );

		targetFrame.append( meterDiv );


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
					ticks: seriesLabel,
					renderer: $.jqplot.CategoryAxisRenderer
				}
			},
			highlighter: { show: false },
			legend: {
				labels: seriesTitles,
				placement: "outside",
				show: true
			}
		} );
	}


	function addToMetricTable( responseJson ) {

		addTablePlaceHolders( responseJson );

		$( ".hostRow" ).hide();
		$( ".metricColumn" ).hide();
		for ( var i = 0; i < responseJson.length; i++ ) {
			var meterJson = responseJson[ i ];
			if ( !meterJson.hostNames )
				continue;

			var metricClass = (meterJson.id).replace( /\./g, '_' );
			$( "." + metricClass ).show();

			for ( var hostIndex = 0; hostIndex < meterJson.hostNames.length; hostIndex++ ) {
				var curHost = meterJson.hostNames[hostIndex];
				var meterValue = meterJson.hostValues[hostIndex];
				
				if ( meterJson.divideBy != undefined && meterJson.divideBy != "vmCount" )
					meterValue = (meterValue / meterJson.divideBy).toFixed( 1 );
				
				if ( meterJson.multiplyBy != undefined )
					meterValue = (meterValue * meterJson.multiplyBy).toFixed( 1 );


				var hostRowId = (curHost + "Row");
				$( "#" + hostRowId ).show();

				var metricHostClass = (meterJson.id + curHost).replace( /\./g, '_' );

				var containerJQ = $( "." + metricHostClass + " a" );
				var oldValue = containerJQ.text();
				containerJQ.text( meterValue );

				// crazy bug in math fixed to support ignore
				containerJQ.parent().attr( "data-math", "use" );

				if ( oldValue != meterValue )
					flashContainer( containerJQ );



			}
		}

		//  $( "#vmTable" ).trigger("update") ;
		// $("#vmTable").trigger("tablesorter-initialized");
		$.tablesorter.computeColumnIndex( $( "tbody tr", "#vmTable" ) );
		$( "#vmTable" ).trigger( "tablesorter-initialized" );
	}


	function addTablePlaceHolders( responseJson ) {
		// add headers and footers	
		
		var sortColumn = 1 ;
		for ( var i = 0; i < responseJson.length; i++ ) {
			var meterJson = responseJson[ i ];


			var metricClass = (meterJson.id).replace( /\./g, '_' );

			if ( $( "th." + metricClass ).length == 0 ) {

				console.log( "addToMetricTable() Adding header: " + metricClass );

				var headerColumn = jQuery( '<th/>', {
					class: "num metricColumn " + metricClass,
					text: meterJson.label
				} )

				$( "#vmTable thead tr" ).append( headerColumn );

				var footColumn = jQuery( '<td/>', {
					class: "num metricColumn " + metricClass,
					"data-math": "col-sum"
				} )

				$( "#vmTable tfoot .totalRow" ).append( footColumn );

				var meanColumn = jQuery( '<td/>', {
					class: "num metricColumn " + metricClass,
					"data-math": "col-mean"
				} )

				$( "#vmTable tfoot .meanRow" ).append( meanColumn );
			}

			if ( !meterJson.hostNames )
				continue;

			for ( var hostIndex = 0; hostIndex < meterJson.hostNames.length; hostIndex++ ) {
				// Add host Row if not present

				var curHost = meterJson.hostNames[hostIndex];

				var hostRowId = (curHost + "Row");

				console.log( "addToMetricTable():  hostRowId: " + hostRowId );

				if ( $( "#" + hostRowId ).length == 0 ) {

					var hostRow = jQuery( '<tr/>', {
						id: hostRowId,
						class: "hostRow "
					} )

					var hostCol = jQuery( '<td/>', {
						text: curHost,
					} ).appendTo( hostRow );

					$( "#vmTable tbody" ).append( hostRow );

					for ( var hostColIndex = 0; hostColIndex < responseJson.length; hostColIndex++ ) {
						var rowJson = responseJson[ hostColIndex ];
						
						if ( rowJson.id == _selectedHistogram ) {
							sortColumn = hostColIndex + 1 ;
						}

						var metricClass = (rowJson.id).replace( /\./g, '_' );
						var metricHostClass = (rowJson.id + curHost).replace( /\./g, '_' );
						var valColumn = jQuery( '<td/>', {
							class: "num metricColumn " + metricClass + " " + metricHostClass,
							"data-math": "ignore"
						} )

						var urlAction = getAgentUrl( curHost, "/os/HostDashboard" ) ;

						var paramArray = rowJson.id.split( "." );

						// console.log("Adding cell: " + rowJson.id) ;
						if ( paramArray[0] == "jmxCommon" ) {
							var serviceName = paramArray[1].substring( paramArray[1].indexOf( "_" ) + 1 )
							// serviceName = serviceName.substring(0, serviceName.indexOf("_"))
							// urlAction += "&report=jmx/detail" + "&service=" + serviceName  ;
							urlAction = getAgentUrl( curHost, "/os/jmx?hosts=" + curHost + "&service=" + serviceName ) ;
						}
						if ( paramArray[0] == "jmxCustom" ) {
							var serviceName = paramArray[1];
							urlAction = getAgentUrl( curHost, "/os/application?hosts=" + curHost + "&service=" + serviceName);
						}
						if ( paramArray[0] == "process" ) {

							var serviceName = paramArray[1].substring( paramArray[1].indexOf( "_" ) + 1 );
							urlAction += "?service=" + serviceName;
						}
						var valLink = jQuery( '<a/>', {
							href: urlAction,
							class: "simple",
							target: "_blank"
						} ).appendTo( valColumn );
						valLink.css( "padding", "3px" );
						hostRow.append( valColumn );
					}
				}
			}
		}
		configureTables( sortColumn );
	}
	
	function getAgentUrl( targetHost, extension ) {

		var hostUrl = agentHostUrlPattern.replace( /CSAP_HOST/g, targetHost ) + extension;
		
		return hostUrl;

	}

// note the required closure
	function meterClickRegistration( meterId, meterJson ) {


		$( '#' + meterId ).click( function () {
			var checkBoxes = $( ".meterInput", $( this ).parent() );
			checkBoxes.prop( "checked", !checkBoxes.prop( "checked" ) );
			checkBoxes.trigger( "change" );
			return false; // prevents link
		} ).hover(
				function () {
					$( this ).css( "text-decoration", "underline" );
					$( this ).css( 'cursor', 'pointer' );
				}, function () {
			$( this ).css( "text-decoration", "none" );
			$( this ).css( 'cursor', 'default' );
		}
		);
	}



	function flashContainer( containerJQ ) {
		containerJQ.animate( {
			backgroundColor: "#aa0000"
		}, 1000 );
		containerJQ.animate( {
			backgroundColor: "white"
		}, 1000 );

	}

} );

