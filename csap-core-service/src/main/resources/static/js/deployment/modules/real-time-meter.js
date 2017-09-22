define( [], function ( ) {
	console.log( "Module loaded" );

	var _linkProject = null;
	var _activePlots = new Array(); 

	return {
		getRealTimeMeters: function ( forProject, linkProject ) {
			_linkProject = linkProject;
			return getRealTimeMeters( forProject );
		},
		renderMeter: function ( meterId, meterJson, checkBoxFunction ) {
			return renderMeter( meterId, meterJson, checkBoxFunction );
		},
		checkDivideBy: function ( meterJson, meterValue ) {
			return checkDivideBy( meterJson, meterValue );
		}

	}

	function getRealTimeMeters( forProject ) {


		console.log( "project: " + forProject );

		var paramObject = {
			project: forProject
		};

		$.getJSON(
				baseUrl + "/realTimeMeters",
				paramObject )

				.done( function ( responseJson ) {
					realTimeMetersSuccess( responseJson );
				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {
					console.log( "Error: Retrieving lifeCycleSuccess fpr host " + hostName, errorThrown )
					// handleConnectionError( "Retrieving lifeCycleSuccess fpr host " + hostName , errorThrown ) ;
				} );

	}


	function  realTimeMetersSuccess( responseJson ) {
		console.log( "Purging old meters, count: " + _activePlots.length );
		for ( var i = 0; i < _activePlots.length; i++ ) {
			var thePlot=_activePlots.pop();
			
			console.log("deleteing plot", thePlot ) ;
			thePlot.destroy() ;
			delete thePlot ;
			
		}
		//console.log(" Success: " +  JSON.stringify(responseJson, null,"\t") ) 
		renderRealTimeMeters( responseJson )
	}


	function renderRealTimeMeters( responseJson ) {

		$( "#analyticsMeters" ).empty();
		for ( var i = 0; i < responseJson.length; i++ ) {

			var meterId = "meter" + i;

			var meterJson = responseJson[ i ];
			// console.log( " meterJson: " +  JSON.stringify(meterJson, null,"\t") ) 
			if ( !renderMeter( meterId, meterJson ) )
				continue;

			meterClickRegistration( meterId, meterJson )
		}


	}
// note the required closure
	function meterClickRegistration( meterId, meterJson ) {


		$( '#' + meterId ).click( function () {

			var targetUrl = "MeterActivity?" + "&project=" + _linkProject + "&meterId=" + meterJson.id;
			openWindowSafely( targetUrl, "_blank" );

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

	function renderMeter( meterId, meterJson, checkBoxFunction ) {

		console.log("meterId: ",  meterId ) ;
		var intervalNums = meterJson.intervals.map( function ( item ) {
			return parseInt( item, 10 );
		} );
		//console.log( " intervalNums: " +  JSON.stringify(intervalNums, null,"\t") ) 
		if ( meterJson.value == undefined || meterJson.vmCount == undefined || meterJson.vmCount == 0 ) {
			console.log( "No value found for meter, skipping: " + meterJson.id );
			return false;
		}

		var meterTitle = "Click to open Real Time Performance Dashboard"
		if ( checkBoxFunction ) {
			meterTitle = "Click to view last collected value per host";
		}

		var containerDiv = jQuery( '<div/>', {
			id: meterId + "Container",
			class: "meterContainer",
			title: meterTitle
		} );


		if ( checkBoxFunction ) {
			var checkedInput = jQuery( '<input/>', {
				id: meterId + "Input",
				class: "meterInput",
				type: "checkbox",
				"data-meter": meterJson.id
			} ).change( function () {

				var selectedHistogram = $( ".meterInput:checked" ).first().data( "meter" );
				if ( $( this ).prop( "checked" ) ) {
					selectedHistogram = $( this ).data( "meter" );
				}

				checkBoxFunction( selectedHistogram );
			} );
		}

		// checkedInput.data("metric", meterJson.id );

		containerDiv.append( checkedInput )

		var meterDiv = jQuery( '<div/>', {
			id: meterId,
			class: "meterGraph"
		} );
		containerDiv.append( meterDiv )

		//console.log("Checking meterIdParam: ", meterIdParam);
		if ( checkBoxFunction && ( (meterJson.id == meterIdParam)  || meterJson.hostNames) ) {

			checkedInput.attr( "checked", "checked" );

		}

		$( "#analyticsMeters" ).append( containerDiv );

		var meterValue = meterJson.value;

		if ( meterJson.id == "jmxCustom.httpd.UrlsProcessed" ) {
			// meterValue = meterValue * 100; // testing only
		}
		console.log( "meterJson: ", meterJson );

		meterValue = checkDivideBy( meterJson, meterValue );

		if ( meterJson.multiplyBy != undefined ) {
			meterValue = (meterValue * meterJson.multiplyBy).toFixed( 1 );
		}
		
		
		var meterLabel = meterJson.label, meterMin = meterJson.min, meterMax = meterJson.max;
		
		// test meter levels
//		if (meterLabel === "Http Requests Per Minute") {
//			meterValue=800;
//			intervalNums = [8000, 11000, 15000] ;
//			console.log("\n\n\n TESTING   intervalNums: ", intervalNums) ;
//		}
//		if (meterLabel === "VM coresActive") {
//			console.log("\n\n\n TESTING") ;
//			intervalNums = [3, 10, 15] ;
//			meterValue=50;
//			console.log("\n\n\n TESTING   intervalNums: ", intervalNums) ;
//		}

		var maxInterval = intervalNums[ intervalNums.length - 1];
		if ( meterValue > (maxInterval) ) {
			// meters will not display when interval is less then value.
			intervalNums[ (meterJson.intervals).length - 1] = meterValue*1.2;
		}
		if ( meterValue > (maxInterval*3) ) {
			// when a big delta - adjust the ui to make reading current value easier
			meterMin = Math.round(meterValue/6);
			intervalNums[ (meterJson.intervals).length - 3] = meterMin;
			intervalNums[ (meterJson.intervals).length - 2] = meterMin;
		}

		var meterColors = ['#66cc66', '#E7E658', '#cc6666'];
		if ( meterJson.reverseColors ) {
			meterColors = ['#cc6666', '#E7E658', '#66cc66'];
		}
		// console.log( " maxInterval:" + maxInterval) ;



		var ROUNDING = 1000;
		if ( 
				meterValue > (3 * ROUNDING) ||
				intervalNums[2] > (3 * ROUNDING)
				) {
			meterValue = Math.round(meterValue / ROUNDING);
			for ( var i = 0; i < intervalNums.length; i++ ) {
				intervalNums[i] = intervalNums[i] / ROUNDING;
			}
			meterMin = Math.round( intervalNums[0]  ) ;
			if (meterValue <  intervalNums[0]  ) {
				meterMin = Math.round( meterValue * 0.7  ) ;
			}

			meterLabel += '<span style="color: red; position: absolute; top: -100px; right: -10px">(1000s)</span>';
			//meterMin = Math.round( meterMin / ROUNDING ) ;
			//meterMax = ( meterMax / ROUNDING ).toFixed( 1 ) ;
		}
		for ( var i = 0; i < intervalNums.length; i++ ) {
				intervalNums[i] = Math.round( intervalNums[i] ) ;
		}
		console.log("meterValue: " + meterValue + "  intervalNums: ", intervalNums , " meterMin: ", meterMin,  " meterMax: ", meterMax) ;

		var thePlot = $.jqplot( meterId, [[meterValue]], {
			title: '',
			seriesDefaults: {
				renderer: $.jqplot.MeterGaugeRenderer,
				rendererOptions: {
					label: meterLabel,
					labelPosition: "bottom",
					min: meterMin,
//					max: meterMax,
					padding: 0,
					intervals: intervalNums,
					intervalColors: meterColors
				}
			}
		} );
		
		_activePlots.push( thePlot ) ;



		//  meter gauge leaves lots of padding on the bottom
		meterDiv.css( "height", "130px" ).css( "overflow", "hidden" );
		// meterDiv.css( "width", "200px" ).css( "overflow", "hidden" );

		return true;
	}

	function checkDivideBy( meterJson, meterValue ) {

		if ( meterJson.divideBy != undefined ) {

			if ( meterJson.divideBy == "vmCount" ) {
				console.log( "Dividing " + meterValue + " by meterJson.vmCount: " + meterJson.vmCount );
				meterValue = (meterValue / meterJson.vmCount).toFixed( 1 );
			} else {
				meterValue = (meterValue / meterJson.divideBy).toFixed( 1 );
			}

			if ( isNaN( meterValue ) )
				return 0;
		}

		return meterValue;
	}



} );