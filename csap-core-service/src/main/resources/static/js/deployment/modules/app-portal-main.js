// http://requirejs.org/docs/api.html#packages
// Packages are not quite as ez as they appear, review the above
require.config( {
	packages: []
} );



require( ["real-time-meter", "app-trends"], function ( realTimeMeter, appTrends ) {

	console.log( "\n\n ************** _main: loaded *** \n\n" );

	// Shared variables need to be visible prior to scope

	$( document ).ready( function () {
		initialize();
	} );

	var $errorsContainer=null ;
	var $alertList=null ;

	var csapUser = new CsapUser();

	var REFRESH_SUMMARY_SECONDS = 30; // 30 seconds is preferre
	var serviceTimer = 0;

	this.initialize = initialize;
	function initialize() {
		$errorsContainer = $("#currentErrors" );
		$alertList = $("ol", $errorsContainer );
		CsapCommon.configureCsapAlertify();
		appTrends.init( getProject(), getLinkProject() );

		console.log( "starting up" );


		registerButtonEvents();

		$( '#showClusterDialog' ).click( function () {
			// alertify.notify("Getting clusters") ;
			$.get( "clusterDialog?releasePackage=" + $( '.releasePackage' ).val(), showClusterDialog, 'html' );
			return false;
		} );

		$( '#showBatchDialog' ).click( function () {
			// alertify.notify("Getting clusters") ;
			$.get( "batchDialog?releasePackage=" + $( '.releasePackage' ).val(), showBatchDialog, 'html' );
			return false;
		} );

		$( '#showAlerts' ).click( showAlertsDialog );

		$( '#analyticsUrl' ).click( function () {

			var targetUrl = analyticsUrl + "&project=" + getLinkProject() + "&appId=" + appId;
			openWindowSafely( targetUrl, "package" + getProject() );
			return false;
		} );

		$( '#selectAllHosts' ).click( function () {
			$( "#instanceTable tbody tr" ).addClass( "selected" );
			$( '#instanceTable tbody tr.selected input' ).prop( 'checked', true );
			// alert( $("#instanceTable *.selected").length ) ;
			return false;
		} );

		$( '#deselectAllHosts' ).click( function () {
			$( '#instanceTable tbody tr.selected input' ).prop( 'checked', false );
			$( "#instanceTable tbody tr" ).removeClass( "selected" );
			return false;
		} );


		var reImageCancelClicked = function () {
			$( this ).dialog( "close" );
		};
		$( "#reImageDialog" )
				.dialog( {
					buttons: [{ text: "Ok", click: reImageOkClicked }, { text: "Cancel", click: reImageCancelClicked }],
					autoOpen: false,
					minWidth: 600,
					position: { my: "left top", at: "bottom", of: "#reImageButton" }
				} );

		serviceSummaryGet();

		addServiceContext( '#instanceTable', getLast );

		$( ".instanceFilter" ).hide();

		//http://mottie.github.io/tablesorter/docs
		$( "#summaryTable" ).tablesorter( {
			sortList: [[0, 0]],
			ignoreCase: true,
			theme: 'csapSummary'
		} );

		var sortOrder = [[0, 0]];
		if ( inHostMode ) {
			sortOrder = [[1, 0]];
		}
		$( "#instanceTable" ).tablesorter( {
			sortList: sortOrder,
			theme: 'csapSummary'
		} );


		realTimeMeter.getRealTimeMeters( getProject(), getLinkProject() );

		// buildHistogram( sample1.data[0], sample2.data[0], "vm")  ;
		//getServiceReport("vm" , 1) ;

	}

	function showAlertsDialog() {
		var alertHeader = jQuery( '<div/>', {
			html: "Service Alerts for: " + lb + " Time: " + _lastErrorTime
		} );

		var alertTip = jQuery( '<div/>', {
			text: "For help refer to: "
		} )
				.css( "float", "right" )
				.css( "margin-right", "8em" )
				.appendTo( alertHeader );

		jQuery( '<a/>', {
			class: "simple",
			target: "_blank",
			title: "Open Reference Guide",
			href: "https://github.com/csap-platform/csap-core/wiki#updateRefCS-AP+Monitoring",
			text: "CSAP Reference Guide"
		} )
				.css( "display", "inline" )
				.appendTo( alertTip );


		var alertsDialog = alertify.alert( "<br/>" + $errorsContainer.html() );


		alertsDialog.setting( {
			title: alertHeader.html(),
			resizable: true,
			modal: true,
			movable: false,
			'label': "Close",
			autoReset: false,
			'onok': function () {
			}

		} );

		var targetWidth = $( window ).outerWidth( true ) - 100;
		var targetHeight = $( window ).outerHeight( true ) - 100;

		var $warningsList = $( ".alertifyWarnings li" );
		var $brList = $( "br", $warningsList );
		console.log( "$brList", $brList.length, "  $warningsList: ", $warningsList.length )
		//var customHeight = ($warningsList.length * 44) + 100;
		var customHeight = ($warningsList.length * 26) + ($brList.length * 16) + 100;
		//var customHeight = Math.round( $warningsList.height()  ) ;

		console.log( "customHeight: ", customHeight )

		if ( customHeight > targetHeight )
			customHeight = targetHeight;

		alertsDialog.resizeTo( targetWidth, customHeight )

		return false;
	}




	function getProject() {
		var project = $( '.releasePackage' ).val();
		//if ( project == "All Packages") project = $('.releasePackage option').eq(0).val() ;

		if ( project != "All Packages" && desktopTestProject != "" )
			project = desktopTestProject;

		return project;
	}

	function getLinkProject() {

		var project = $( '.releasePackage' ).val();
		if ( project == "All Packages" )
			project = $( '.releasePackage option' ).eq( 0 ).val();

		if ( project != "All Packages" && desktopTestProject != "" )
			project = desktopTestProject;

		return project;
	}


	function getServiceReport( report, numDays ) {

		var project = $( '.releasePackage' ).val();
		if ( project == "All Packages" )
			project = $( '.releasePackage option' ).eq( 0 ).val();

		if ( desktopTestProject != "" )
			project = desktopTestProject;

		console.log( "project: " + project );

		var paramObject = {
			report: report,
			appId: appId,
			project: project,
			life: defaultLifeCycle,
			numDays: numDays
		};

		// Skip current day when computing last week averages
		if ( numDays > 1 ) {
			$.extend( paramObject, {
				dateOffSet: 1
			} );
		}

		console.log( reportUrl );
		$.getJSON(
				reportUrl,
				paramObject )

				.done( function ( responseJson ) {
					reportServiceSuccess( responseJson, numDays, report );

				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {
					reportSuccess( null );
					console.log( "Error: Retrieving lifeCycleSuccess fpr host " + hostName, errorThrown )
					// handleConnectionError( "Retrieving lifeCycleSuccess fpr host " + hostName , errorThrown ) ;
				} );

	}

	function  reportServiceSuccess( responseJson, numDays ) {

		if ( responseJson != null ) {
			var serviceArray = responseJson.data;
			if ( serviceArray == undefined ) {
				console.log( "Warning: no services in response: " + JSON.stringify( responseJson, null, "\t" ) );
				return;
			}
			var metricSelect = $( "#osSelect" ).val();
			for ( var i = 0; i < serviceArray.length; i++ ) {

				var serviceJson = serviceArray[i];

				if ( i == 0 ) {
					$( "#osSelect" ).empty();
					for ( var item in serviceJson ) {
						if ( !$.isNumeric( serviceJson[item] ) )
							continue;
						var label = splitUpperCase( item );
						if ( item == "numberOfSamples" )
							label = "Samples";
						var optionItem = jQuery( '<option/>', {
							value: item,
							text: label
						} );

						$( "#osSelect" ).append( optionItem );
					}
					$( "#osSelect" ).val( metricSelect );
				}
				// console.log ("reportServiceSuccess" + item.serviceName ) ;
				var value = serviceJson[metricSelect];
				if ( metricSelect != "numberOfSamples" && !$( "#isShowTotals" ).is( ':checked' ) ) {
					value = (serviceJson[metricSelect] / serviceJson.numberOfSamples).toFixed( 1 );
					if ( metricSelect == "topCpu" )
						value += "%";
				}

				$( '#summaryTableDiv [data-service="' + serviceJson.serviceName + '"] .summaryColumn3' ).text( value );

			}
		}
		$( "#summaryTable" ).trigger( "update" );

	}


	function getVmTotals( responseJson ) {

		var totalResults = new Object();
		var firstHost = responseJson.data[0];
		for ( var key in firstHost ) {
			totalResults[key] = 0;
		}


		for ( var key in firstHost ) {
			// console.log( " key: " + key + " responseJson.data.length: " + responseJson.data.length )  ;
			for ( var i = 0; i < responseJson.data.length; i++ ) {
				// console.log( " key: =============== " )  ;
				var hostJson = responseJson.data[i];

				if ( $.isNumeric( hostJson[key] ) ) {
					totalResults[key] = Math.round( totalResults[key] + hostJson[key] );
					// totalResults[key] = (totalResults[key]).toFixed(2) ;
				} else {
					totalResults[key] = hostJson[key];
				}
			}
		}

//	console.log( " totalResults: " +  JSON.stringify(totalResults, null,"\t") ) 
		return totalResults;
	}

	function trimValues( val ) {

	}

//var _lastGraph=null ;

	function showClusterDialog( clusterHtml ) {

		$cluster = $( "<html/>" ).html( clusterHtml ).find( '#alertifyContents' )
		// console.log(  "cluster: " + $cluster.html()) ;
		var alertsDialog = alertify.alert( $cluster.html() );

		var targetWidth = $( window ).outerWidth( true ) - 100;
		var targetHeight = $( window ).outerHeight( true ) - 100;

		alertsDialog.setting( {
			title: "CSAP Cluster Browser",
			resizable: true,
			movable: false,
			width: targetWidth,
			'label': "Close",
			autoReset: false,
			'onok': function () {
			}


		} ).resizeTo( targetWidth, targetHeight );

		$( 'article.host a[title]' ).qtip( {
			style: {
				classes: 'qtip-rounded'
			}
		} );

	}



	function reImageOkClicked() {
		$( this ).dialog( "close" );
		$( "#summaryTable *.selected" ).each( function ( index ) {

			var reqHost = $( this ).data( "host" ); // (this).data("host")
			var hostParamObject = {
				hostName: reqHost,
				serviceName: $( "#reImageSelect" ).val()

			};

			$.post( baseUrl + "/reImage", hostParamObject )
					.done(
							function ( results ) {
								// displayResults(results);
								displayHostResults( reqHost, " reImage", results );
							} )
					.fail( function ( jqXHR, textStatus, errorThrown ) {

						handleConnectionError( reqHost + ":" + "reImage", errorThrown );
					} );
		} );
	}



	var lastInstanceRollOver = null;
	function getLast() {
		return lastInstanceRollOver;
	}


	function select( event, ui ) {
		$( '#messageTs' ).html( ui.item.text() );
		// $("<div/>").text("Selected: " + ui.item.text()).appendTo("#log");
		if ( ui.item.text() == 'Quit' ) {
			$( this ).menubar( 'destroy' );
		}
	}

	var windowResizeTimer;

	function registerButtonEvents() {
		// Do event bindings
		$( window ).resize( function () {

			clearTimeout( windowResizeTimer );
			windowResizeTimer = setTimeout( function () {
				windowResize()
			}, 200 );
		} );

		$( '#activityCount' ).click( function () {
			var targetUrl = historyUrl;
			var curPackage = $( '.releasePackage' ).val();
			if ( curPackage != "All Packages" ) {
				targetUrl += "&project=" + curPackage;
			}
			openWindowSafely( targetUrl, "_blank" );
			return false;
		} );

		$( '#myActivity' ).click( function () {
			openWindowSafely( eventApiUrl + "/../?category=/csap/ui/*&userid=" + userid, "_blank" );
			return false;
		} );

		getActivityCount();

		$( "#mainDisplayNav input, #osSelect" ).change( function () {
			alertify.notify( "Status being refreshed" );
			serviceSummaryGet( true );
		} );
		$( '#refreshButton' ).click( function () {
			$( "#resultsSection" ).hide();
			$( ".instanceFilter" ).hide();

			if ( !isResultsMinSize() ) {
				toggleResultsButton();
			}
			;

			$( '#messageTs' ).html( "Refreshing..." );

			$( "#news" ).hide();

			$( "#loadingMessage" )
					.text( "Querying all hosts for latest OS process information" )
					.css( "width", getPanel2Width() - 200 )
					.addClass( "loadingRefresh" )
					.show();

			getActivityCount();
			$( "#instanceTableDiv" ).hide();
			serviceSummaryGet( true );

			return false; // prevents link
		} );

		$( '.releasePackage' ).selectmenu( {
			change: function ( event, ui ) {
				var url = "services?releasePackage=" + $( '.releasePackage' ).val();
				document.location.href = url;
			}
		} );

		$( '#stopOptionsButton' ).click( function () {
			showOptions( $( '#stopOptions' ) );
			return false; // prevents link
		} );

		$( '#startOptionsButton' ).click( function () {

			showOptions( $( '#startOptions' ) );
			return false; // prevents link
		} );

		$( '#killButton' ).click( function () {
			killService();
			return false; // prevents link
		} );

		$( '#startButton' ).click( function () {
			startJvm();
			return false; // prevents link
		} );

		$( '#deployWarButton' ).click( function () {
			deployWar();
			return false; // prevents link
		} );

		$( '#rebuildJvmButton' ).click( function () {
			deploySource();
			return false; // prevents link
		} );

		$( '#scmPass' ).keypress( function ( e ) {
			if ( e.which == 13 ) {
				deploySource();
			}
		} );


		$( '#stopButton' ).click( function () {
			executeOnSelectedHosts( "stopServer", new Object );
			return false; // prevents link
		} );

		$( '#reImageButton' ).click( function () {
			if ( !confirmMinSelections( false, " ReDeploy Command" ) )
				return false;
			$( "#reImageSelect" ).empty();
			$( "#stackDiv" ).hide();
			$( "#instanceTable *.middleware" ).each( function () {
				$( "#reImageSelect" ).append( '<option>' + $( this ).data( 'instance' ) + '</option>' );
				$( "#stackDiv" ).show();
			} );
			$( "#reImageDialog" ).dialog( "open" );
			return false; // prevents link
		} );

		$( '#deployOptionsButton' ).click( function () {
			toggleDeployDetails();
		} );

		$( '#toggleResultsButton' ).click( function () {
			toggleResultsButton( this );
			return false; // prevents link
		} );

		$( '#closeResultsButton' ).click( function () {
			$( "#resultsSection" ).hide();
			if ( !isResultsMinSize() ) {
				toggleResultsButton();
			}
			;
			return false; // prevents link
		} );

		$( '#hostFilesButton' ).click( function () {
			if ( !confirmMinSelections( false, " Show Host Files" ) )
				return;
			launchFiles( $( "#summaryTable *.selected" ) );
			return false; // prevents link
		} );

		$( '#hostDashboardButton' ).click( function () {
			launchHostDash( $( "#summaryTable *.selected" ) );
			return false; // prevents link
		} );

		configureTrending();

	}

	function flash( $item ) {
		for ( i = 0; i < 5; i++ ) {
			$item.animate( {
				backgroundColor: "#aa0000",
				color: "#fff"
			}, 1000 );
			$item.animate( {
				backgroundColor: "white",
				color: "black"
			}, 1000 );
		}
	}

	function configureTrending() {

		$( "#minMaxTrendsButton" ).click( function () {
			var currPosition = $( "#trendPanel" ).position();
			console.log( "trend min max" );
			$( "#trendPanel" ).toggleClass( 'panelEnlarged' );
			$( "#minMaxTrendsButton" ).qtip( "hide" );
			//ToolqTip.hide()
			if ( $( "#trendPanel" ).hasClass( "panelEnlarged" ) ) {
				$( "#trendPanel" ).appendTo( $( "body" ) );
				$( "#trendPanel" ).offset( { top: 10, left: 10 } );
//				$( "#trendPanel" ).position( { my: "left top", at: "left top",
//					of: "#newsTrends", collision: "none" } );
//				$( "#trendPanel" ).position( { my: "left top", at: "left+10 top+10",
//					of: "#mainDisplayArea", collision: "none",
//					using: function ( css, calc ) {
//						$( this ).animate( css, 500, "linear" );
//					} } );
				$( "#mainDisplayArea,#header,#footer" ).hide();
//			$("#trendPanel").position( { my: "left top", at: "left top", 
//				of: "#mainDisplayArea", collision: "none"}) ;
				$( "#minMaxTrendsButton img" )
						.attr( "src", "images/restoreWindow.gif" )
						.css( "width", "20" )
				flash( $( "#minMaxTrendsButton" ) );
			} else {
				$( "#mainDisplayArea,#header,#footer" ).show();
				$( "#trendPanel" ).prependTo( $( "#newsTrends" ) );
				$( "#minMaxTrendsButton img" )
						.attr( "src", "images/maxWindow.gif" )
						.css( "width", "16" );
				windowResize();
			}
			appTrends.getConfiguredTrends();

			return false;
		} );
		$( "#minMaxTrendsButton" ).qtip( {
			content: {
				text: "Maximize/Minimize trends"
			},
			style: {
				classes: 'qtip-bootstrap'
			},
			position: {
				my: 'middle right', // Position my top left...
				at: 'middle left'
			},
			hide: {
				inactive: 5000
			}
		} );
	}

	function showOptions( object ) {
		// alert(" showing: " +  object.html()) ;
		$( "#resultsSection" ).hide();
		if ( object.is( ":visible" ) ) {
			object.hide();
			$( "#commandOptionsDiv" ).hide();
		} else {
			$( "#commandOptionsDiv .options" ).hide();
			$( "#commandOptionsDiv" ).show();
			object.show();
		}

		windowResize();
	}


	function isDeployOptionsVisible() {
		if ( $( '#deployOptions' ).first().is( ":visible" ) ) {
			return true;
		}

		return false;
	}

	function toggleDeployDetails() {

		showOptions( $( '#deployOptions' ) );

		// alert ( isDeployOptionsVisible() ) ;
		if ( isDeployOptionsVisible() ) {
			$( '#summaryTableDiv' ).hide();
			$( '.mavenId' ).show();
		} else {
			$( '#summaryTableDiv' ).show();
			$( '.mavenId' ).hide();
		}
		windowResize();
		return false; // prevents link
	}


	function isResultsMinSize() {

		if ( $( "#toggleResultsImage" ).length == 0 ||
				$( "#toggleResultsImage" ).attr( 'src' ).indexOf( "maxWindow" ) != -1 ) {
			return true;
		}

		return false;
	}

	function toggleResultsButton( toggleButton ) {

		// alert( $("#toggleResultsImage").attr('src').indexOf("maxWindow") ) ;
		if ( isResultsMinSize() ) {

			$( "#resultPre" ).css( 'overflow-y', 'visible' );
			$( "#resultPre" ).css( 'height', 'auto' );
			$( "#toggleResultsImage" ).attr( 'src', 'images/restoreWindow.gif' );

			$( "#instanceTableDiv" ).hide();
			$( "#summaryTableDiv" ).hide();
			$( "#commandOptionsDiv" ).hide();

		} else {
			$( "#resultPre" ).css( 'height', '100px' );
			$( "#resultPre" ).css( 'overflow-y', 'auto' );
			$( "#toggleResultsImage" ).attr( 'src', 'images/maxWindow.gif' );

			$( "#summaryTableDiv" ).show();
		}
	}

	/**
	 * 
	 * Handle scrolling tables with fixed headers
	 * - note that scrollbars mess with box-shadows - so they are removed
	 * 
	 * @param displayHeight
	 * @param containerJQ
	 * @param tableJQ
	 * @param headerJQ
	 */
	function handleScrollbars( displayHeight, containerJQ, tableJQ, headerJQ ) {

		var cloneId = headerJQ.attr( "id" ) + "HeaderClone";

		var scrollId = headerJQ.attr( "id" ) + "Scroll";

		// alert("displayHeight: " + displayHeight + " instanceContainer.outerHeight: " + instanceContainer.outerHeight(true) ) ;

		var scrollingDivJQ = $( "#" + scrollId );
		if ( scrollingDivJQ.length == 0 ) {
			// Create a div to host the scrollbars
			scrollingDivJQ = $( '<div>' );
			scrollingDivJQ.attr( "id", scrollId );
			// scrollingDivJQ.css("border", "1px solid red") ;
			tableJQ.parent().prepend( scrollingDivJQ );
			scrollingDivJQ.append( tableJQ );
		}

		scrollingDivJQ.css( "overflow-y", "auto" );
		scrollingDivJQ.css( "height", "auto" );


		var cloneInstanceHeader = $( "#" + cloneId );
		if ( cloneInstanceHeader.length == 0 ) {

			cloneInstanceHeader = tableJQ.clone();
			cloneInstanceHeader.attr( "id", cloneId );

			cloneInstanceHeader.css( "margin-bottom", "0" );

			cloneInstanceHeader.width( headerJQ.width() );
			cloneInstanceHeader.css( "box-shadow", "none" );
			cloneInstanceHeader.css( "border-radius", "0" );
			cloneInstanceHeader.css( "border-top-left-radius", "10px" );
			cloneInstanceHeader.css( "border-top-right-radius", "10px" );
			cloneInstanceHeader.css( "border-bottom", "0px" );
			tableJQ.css( "border-top-left-radius", "0px" );
			tableJQ.css( "border-top-right-radius", "0px" );
			tableJQ.css( "box-shadow", "none" );
			jQuery( "tbody", cloneInstanceHeader ).html( '' );
			containerJQ.prepend( cloneInstanceHeader );

			headerJQ.css( 'display', "none" );
		}

		// set the column widths so that all are set when some are hidden/filtered
		jQuery( "th", tableJQ ).each( function ( headerIndex ) {
			//jQuery("tbody tr:eq(0) td:eq(" + index + ")", tableJQ).css('width', $(this).css("width") ) ;
			var headerWidth = $( this ).css( "width" );
			jQuery( "tbody tr", tableJQ ).each( function ( rowIndex ) {
				// console.log("header: " +headerIndex + " rowIndex:" + rowIndex) ;
				$( 'td:eq(' + headerIndex + ')', $( this ) ).css( 'width', headerWidth );
			} );

		} );

		displayHeight = displayHeight - cloneInstanceHeader.outerHeight( true );

		if ( containerJQ.outerHeight( true ) > displayHeight ) {
			scrollingDivJQ.css( "height", displayHeight );
		}


		if ( tableJQ.is( ':visible' ) ) {
			cloneInstanceHeader.width( tableJQ.width() );
			cloneInstanceHeader.show();
		} else {
			cloneInstanceHeader.hide();
		}

	}

// Case Insensitive contains for filtering
//
	$.expr[":"].contains = $.expr.createPseudo( function ( arg ) {
		return function ( elem ) {
			return $( elem ).text().toUpperCase().indexOf( arg.toUpperCase() ) >= 0;
		};
	} );


	function serviceInstanceGetForHost( hostName ) {


		$( '#serviceOps' ).css( "display", "inline-block" );
		if ( hostName == "null" ) {
			return;
		}



		$.getJSON(
				baseUrl + "/getServiceInstances",
				{
					hostName: hostName,
					releasePackage: $( '.releasePackage' ).val()
				} )

				.done( function ( loadJson ) {
					serviceInstanceSuccess( loadJson );
				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( "Retrieving service instances fpr host " + hostName, errorThrown );
				} );
	}


	function serviceInstanceGet( serviceName ) {

		if ( serviceName == "null" ) {
			return;
		}

		var paramObject = {
			serviceName: serviceName,
			releasePackage: $( '.releasePackage' ).val()
		};


		$.getJSON(
				baseUrl + "/getServiceInstances", paramObject )

				.done( function ( loadJson ) {
					serviceInstanceSuccess( loadJson );
				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( "Retrieving service instances", errorThrown );
				} );
	}

	function serviceInstanceSuccess( loadJson ) {

		//var tableHtml = "";
		var $updatedTableBody = jQuery( '<tbody/>', { } );

		for ( var i = 0; i < loadJson.serviceStatus.length; i++ ) {

			var serviceInstance = loadJson.serviceStatus [i];


			if ( serviceInstance.lc.indexOf( $( "#clusterSelect" ).val() ) != 0 )
				continue;


			var instancePort = serviceInstance.serviceName
					+ "_" + serviceInstance.port;

			var inputId = ' id="input' + instancePort + '" ';

			var cssClass = "";
			if ( $( "#" + serviceInstance.host + "_" + instancePort ).hasClass( "selected" ) ) {
				cssClass = 'selected ';
			}

			if ( (serviceInstance.user != null && serviceInstance.serverType == "wrapper") ) {
				cssClass += " middleware ";
			}


			var $instanceRow = jQuery( '<tr/>', {
				id: serviceInstance.host + "_" + instancePort,
				class: cssClass,
				"data-host": serviceInstance.host,
				"data-context": serviceInstance.context,
				"data-jmx": serviceInstance.jmx,
				"data-jmxrmi": serviceInstance.jmxrmi,
				"data-launchurl": serviceInstance.launchUrl,
				"data-port": serviceInstance.port,
				"data-instance": instancePort,
				title: serviceInstance.scmVersion + " on cluster " + serviceInstance.lc

			} );

			$updatedTableBody.append( $instanceRow );



			// Figure out status Image
			var statusImage = '<img class="statusIcon"  src="images/16x16/green.png">';

			if ( !(serviceInstance.cpuUtil) ) {
				statusImage = '<img class="statusIcon"  src="images/16x16/red.png">';
			} else {
				if ( (serviceInstance.cpuUtil == "-") && (serviceInstance.serverType == "script") ) {
					statusImage = '<img style="height: 12px"  src="images/16x16/files.png">';
				} else {

					if ( serviceInstance.cpuUtil == "AUTO" ) {
						statusImage = '<img style="height: 12px"  src="images/32x32/appointment-new.png">';
					} else if ( serviceInstance.cpuUtil == "REMOTE" ) {
						if ( serviceInstance.jvmThreadCount > 0 ) {
							statusImage = '<img style="height: 12px"  src="images/16x16/download.png">';
						} else {
							statusImage = '<img  src="images/16x16/red.png">';
						}
					} else if ( serviceInstance.cpuUtil == "CLEAN" ) {
						statusImage = '<img style="height: 12px"  src="images/16x16/clean.png">';
					} else if ( serviceInstance.cpuUtil == "-" ) {
						statusImage = '<img  src="images/16x16/red.png">';
					}
				}

			}

			//tableHtml += '<td  class="' + tdCss + ' instanceColumn1" >' + statusImage + '</td>';

			if ( inHostMode ) {
//				tableHtml += '<td class="instanceService"><input class="instanceCheck" type="checkbox" title="Select to include in operations"/>' + statusImage + serviceInstance.serviceName;
//				tableHtml += '<div class="typeLabel">' + serviceInstance.serverType + '</div></td>';
//				tableHtml += '<td class="instanceColumn3">' + serviceInstance.autoStart;
//				tableHtml += '</td><td  class="instanceColumn4">' + serviceInstance.port;
				var desc = '<input class="instanceCheck" type="checkbox" title="Select to include in operations"/>'
						+ statusImage + serviceInstance.serviceName
						+ '<div class="typeLabel">' + serviceInstance.serverType + '</div>'
				var $serviceColumn = jQuery( '<td/>', {
					class: "instanceService"
				} );

				jQuery( '<input/>', {
					title: "Select to include in operations",
					class: "instanceCheck",
					type: "checkbox"
				} ).appendTo( $serviceColumn );

				var $servicePortalLink = jQuery( '<a/>', {
					title: "Open Service Portal for extended management capabilities",
					class: "simple",
					href: "#",
					html: statusImage + serviceInstance.serviceName
				} );
				$servicePortalLink.appendTo( $serviceColumn );
				$servicePortalLink.click( function () {
					openServicePortal( $( this ).parent().parent() );
				} );

				jQuery( '<div/>', {
					class: "typeLabel",
					text: serviceInstance.serverType
				} ).appendTo( $serviceColumn );


				$instanceRow.append( $serviceColumn );

				var $orderColumn = jQuery( '<td/>', {
					class: "instanceColumn3",
					text: serviceInstance.autoStart
				} )
				$instanceRow.append( $orderColumn );

				var $portColumn = jQuery( '<td/>', {
					class: "instanceColumn4",
					text: serviceInstance.port
				} )
				$instanceRow.append( $portColumn );

			} else {
				var port = serviceInstance.port;
				if ( port == 0 ) {
					port = "";
				}

				var $hostColumn = jQuery( '<td/>', {
					class: "instanceColumn2",
					html: statusImage + serviceInstance.host + "<span>" + port + "</span>"
				} )

				$instanceRow.append( $hostColumn );
//				tableHtml += '<td  class="instanceColumn2">' + statusImage + serviceInstance.host + "<span>" + port + "</span>";
			}



			var regexVersion = new RegExp( ".*##", "g" );
			var ver = serviceInstance.deployedArtifacts;
			// alert(ver) ;
			if ( serviceInstance.deployedArtifacts ) {
				ver = "";
				var verArray = serviceInstance.deployedArtifacts.split( ',' );
				for ( var j = 0; j < verArray.length; j++ ) {
					if ( ver != "" )
						ver += ",<br>";

					if ( verArray.length == 1 )
						ver += verArray[j].replace( regexVersion, "" );
					else
						ver += verArray[j];
					//ver += verArray[j] + " ";

				}
				if ( serviceInstance.scmVersion.indexOf( "Custom artifact uploaded" ) != -1 )
					ver += "upload";
			}


			//tableHtml += '</td><td class="instanceVersion">' + ver;
			var $versionColumn = jQuery( '<td/>', {
				class: "instanceVersion",
				html: ver
			} );
			$instanceRow.append( $versionColumn );


			if ( inHostMode ) {
				// tableHtml += '</td><td  class="mavenId"><input' + inputId + ' class="small" value="' + serviceInstance.mavenId + '"/>';
				var $mavenColumn = jQuery( '<td/>', {
					class: "mavenId",
					html: '<input' + inputId + ' class="small" value="' + serviceInstance.mavenId + '"/>'
				} );
				$instanceRow.append( $mavenColumn );
			}

			if ( serviceInstance.cpuUtil ) {
				if ( !inHostMode ) {
					//tableHtml += '</td><td class="instanceLoad">' + serviceInstance.cpuLoad + ' / ' + serviceInstance.cpuCount;
					var $loadColumn = jQuery( '<td/>', {
						class: "instanceLoad",
						text: serviceInstance.cpuLoad + ' / ' + serviceInstance.cpuCount
					} );
					$instanceRow.append( $loadColumn );
				}

				var disk = serviceInstance.diskUtil;
				if ( disk > 1024 ) {
					disk = (disk / 1024).toFixed( 1 ) + " Gb"
				}
				//tableHtml += '</td><td class="instanceDisk">' + disk;
				var $diskColumn = jQuery( '<td/>', {
					class: "instanceDisk",
					html: disk
				} );
				$instanceRow.append( $diskColumn );
			} else {
				if ( !inHostMode ) {
					//tableHtml += '</td><td  class="instanceLoad">-';
					var $loadColumn = jQuery( '<td/>', {
						class: "instanceLoad",
						text: "-"
					} );
					$instanceRow.append( $loadColumn );
				}
				//tableHtml += '</td><td class="instanceDisk">-';
				var $diskColumn = jQuery( '<td/>', {
					class: "instanceDisk",
					text: "-"
				} );
				$instanceRow.append( $diskColumn );
			}
			//tableHtml += '</td></tr>';
		}
//		$( '#instanceTable tbody tr input' ).off();
//		$( "#instanceTable tbody" ).html( tableHtml );
		$( '.qtip.ui-tooltip' ).qtip( 'hide' );
		$( "#instanceTable tbody" ).remove();
		$( "#instanceTable" ).append( $updatedTableBody );


		$( "#instanceTable" ).trigger( "update" );

//		$( '#instanceTable tbody tr' ).qtip( {
//			content: {
//				title: serviceInstance.serviceName + " Deployment Status",
//				button: 'Close'
//			},
//			style: {
//				classes: 'qtip-bootstrap versionTip'
//			},
//			position: {
//				my: 'top right', // Position my top left...
//				at: 'top left',
//				adjust: {
//					x: -5
//				}
//			}
//		} );

		$( '.instanceCheck' ).click( function () {
			if ( $( this ).prop( 'checked' ) == true ) {
				$( this ).parent().parent().addClass( "selected" );
			} else {
				$( this ).parent().parent().removeClass( "selected" );
			}
		} );


		if ( inHostMode ) {
			// Used host check boxes
			$( '#instanceTable tbody tr.selected input' ).prop( 'checked', true );
		} else {
			$( '#instanceTable tbody tr' ).click( function () {
				openServicePortal( $( this ) )
			} );
		}

		$( '#instanceTable tbody tr' ).hover( function () {
			lastInstanceRollOver = $( this );
		} );

		$( '.instanceFilter' ).keyup( function () {

			var filter = $( '.instanceFilter' ).val();
			// console.log("updated filter") ;

			if ( filter.length > 0 ) {
				$( '#instanceTable tbody tr' ).hide();
				$( '#instanceTable tbody tr td:contains("' + filter + '")' ).parent().show();
			} else {
				$( '#instanceTable tbody tr' ).show();
			}
			return true; // prevents link
		} );

		$( '.instanceFilter' ).keyup();

		windowResize();


		$( ".instanceFilter" ).show();

	}

	function openServicePortal( $row ) {
		var host = $row.data( 'host' );
		var serviceName = $row.data( 'instance' );
		var url = "admin?serviceName=" + serviceName
				+ "&hostName=" + host + "&releasePackage=" + $( '.releasePackage' ).val();
		document.location.href = url;
	}

	function serviceSummaryGet( blockingUpdate ) {

		// alert("serviceSummaryGet")  ;
		clearTimeout( serviceTimer );

		var paramObject = {
			serviceName: $( "#serviceSelect" ).val(),
			releasePackage: $( '.releasePackage' ).val()
		};

		if ( blockingUpdate ) {
			// paramObject.push({noDeploy: "noDeploy"}) ;
			$.extend( paramObject, {
				blocking: "true"
			} );
		}

		if ( $( "#clusterSelect option" ).length > 0 ) {
			$.extend( paramObject, {
				cluster: $( "#clusterSelect" ).val()
			} );
		} else {
			$.extend( paramObject, {
				cluster: defaultLifeCycle
			} );
		}
		
		if ( $.urlParam("noRefresh") == "true" ) {
			REFRESH_SUMMARY_SECONDS=3000;
		}
		console.log("REFRESH_SUMMARY_SECONDS: ", REFRESH_SUMMARY_SECONDS) ;
		// getActivityCount() ;

		$.getJSON(
				baseUrl + "/getServicesInLifeCycleSummary",
				paramObject )

				.done( function ( loadJson ) {
					serviceSummarySuccess( loadJson );
					serviceTimer = setTimeout( function () {
						serviceSummaryGet();
					}, REFRESH_SUMMARY_SECONDS * 1000 );
				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( "Retrieving service summary", errorThrown );
				} );
		;


	}


	function getActivityCount() {

		var targetUrl = activityUrl;

		var requestParms = { };

		var curPackage = $( '.releasePackage' ).val();
		if ( curPackage != "All Packages" ) {
			$.extend( requestParms, {
				releasePackage: curPackage
			} );
		}

		//targetUrl + "&callback=?",  
		$.getJSON(
				targetUrl,
				requestParms )

				.done( function ( countJson ) {
					getActivitySuccess( countJson );
				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {
					console.log( "getActivityCount " + errorThrown + " :" + JSON.stringify( jqXHR ) );
					//handleConnectionError( "Retrieving changes for file " +  $("#logFileSelect").val() , errorThrown ) ;
				} );

		//$('#activityCount').html( loadJson.activityCount + " Events" ) ;


	}

	function getActivitySuccess( countJson ) {

		// 	console.log( JSON.stringify( countJson ) ) ;
		$( '#activityCount' ).html( countJson.count + " Events" );

	}

	function serviceSummarySuccess( loadJson ) {

		// alert("got success") ;

		if ( $( "#loadingMessage" ).is( ":visible" ) ) {
			$( "#loadingMessage" ).hide();
			$( "#news" ).show();
		}

		// avoid refreshes when about to deploy
		if ( isDeployOptionsVisible() ) {
			return;
		}


		if ( loadJson.packageCount != undefined && loadJson.packageCount > 1 ) {
			$( '.packageControls' ).css( "display", "block" );
		} else {
			$( '.packageControls' ).css( "display", "none" );
		}

		$( '#messageTs' ).html( loadJson.timeStamp );
		$( '#lastOp' ).html( loadJson.lastOp );

		var usersJQ = $( '#users' );
		usersJQ.empty();
		//var userArray = loadJson.users.trim().split( " " );
		var userArray = loadJson.users;
		for ( var i = 0; i < userArray.length; i++ ) {

			jQuery( '<span/>', {
				id: 'user_' + userArray[i],
				class: 'userids',
				text: userArray[i]
			} ).appendTo( usersJQ );

			//$('#users').append( $("<a />").attr("src", "your link") ) ;
		}
		csapUser.onHover( $( ".userids" ), 500 );
		// $('#users').html( loadJson.users ) ;

		var newsItems = loadJson["news"];
		var newsHtml = "";
		var newRegex = new RegExp( "_newIcon_", "g" );
		var newIcon = '<img class="newsIcon" src="images/16x16/new.png">';
		var noteRegex = new RegExp( "_noteIcon_", "g" );
		var noteIcon = '<img class="newsIcon" src="images/16x16/note.png">';

		for ( var i = 0; i < newsItems.length; i++ ) {
			newsHtml += "<li>";
			var line = newsItems[i].replace( newRegex, newIcon );
			line = line.replace( noteRegex, noteIcon );
			newsHtml += line + "</li>";
		}
		$( "#newsList" ).html( newsHtml );


		var scoreItems = loadJson["scoreCard"];
		var scoreHtml = scoreItems.platform.upToDate + " of " + scoreItems.platform.total + "<br/>";
		scoreHtml += scoreItems.app.upToDate + " of " + scoreItems.app.total + "<br/>";
		scoreHtml += loadJson["errors"].length;
		$( "#scoreList" ).html( scoreHtml );

		var totalScore = 33 - loadJson["errors"].length;
		if ( totalScore < 0 )
			totalScore = 0;
		totalScore += ((scoreItems.app.upToDate / scoreItems.app.total) * 33);
		totalScore += ((scoreItems.platform.upToDate / scoreItems.platform.total) * 33);

		$( "#totalScore" ).html( Math.round( totalScore ) + "%" );

		$( '#scoreTable' ).qtip( {
			content: {
				title: "Recommended Version Installed",
				text: "CSAP: " + scoreItems.platform.CsAgent + "<br>  "
						+ "JDK: " + scoreItems.platform.JDK + "<br> "
						+ "Redhat: " + scoreItems.platform.Redhat,
				button: 'Close'
			},
			position: {
				my: 'top left', // Position my top left...
				at: 'bottom left',
				of: '#scoreTable'
			},
			style: {
				classes: 'qtip-bootstrap'
			}
		} );



		// var hostStat = parseInt(loadJson["cpuCount"]) ;
		var servicesActive = loadJson["servicesActive"];
		var servicesTotal = loadJson["servicesTotal"];
		var servicesType = loadJson["servicesType"];
		var hostStatus = loadJson["hostStatus"];
		var lbUrls = loadJson["lbUrls"];

		$( "#hostSummary" ).html( loadJson.totalHostsActive + "/" + loadJson.totalHosts );
		$( "#serviceSummary" ).html( loadJson.totalServicesActive + "/" + loadJson.totalServices );

		if ( $( "#clusterSelect option" ).length == 0 ) {
			for ( var i = 0; i < loadJson["clusters"].length; i++ ) {
				var desc = loadJson["clusters"][i];
				if ( desc == loadJson["currentlc"] ) {
					desc = " Lifecycle: " + desc;
				} else {
					desc = desc.substring( loadJson["currentlc"].length + 1 );
				}
				var selected = "";
				if ( loadJson["clusters"][i] == defaultLifeCycle )
					selected = 'selected="selected"';
				$( "#clusterSelect" ).append( '<option value="' + loadJson["clusters"][i] + '" ' + selected + '>' + desc + "</option>" );
			}

			// add in other lcs
			for ( var lifeCycle in lbUrls ) {
				if ( lifeCycle != loadJson["currentlc"] ) {
					$( "#clusterSelect" ).append( '<option value="' + lbUrls[lifeCycle] + '">Lifecycle: ' + lifeCycle + " </option>" );
				}
			}

			$( "#clusterSelect" ).sortSelect();
			var clusterSelect = $( "#clusterSelect" ).selectmenu( {
				change: function () {

					$( "#instanceTableDiv" ).hide();
					$( "#serviceOps" ).hide();
					$( "#summaryTable tbody" ).html( "<tr><td> loading</td></tr>" );

					$( '#messageTs' ).html( "Refreshing..." );

					if ( $( this ).val().indexOf( "http" ) == 0 ) {
						document.location.href = $( this ).val();
					} else {
						serviceSummaryGet();
					}
					return false; // prevents link
				}
			} ).css( "height", "12px" );
		}
//	for (var serviceInstance in loadJson.instances ) {
//	alert( loadJson.instances.length ) ;

		if ( inHostMode ) {
			updateTableWithHostSummary( hostStatus );
		} else {

			updateTableWithServiceSummary( servicesActive, servicesTotal, servicesType );
		}

		alertProcessing( loadJson );
		windowResize();


		$( '[title!=""]' ).qtip( {
			style: {
				classes: 'qtip-bootstrap'
			},
			position: {
				my: 'top left', // Position my top left...
				at: 'bottom left',
				adjust: { x: 5, y: 10 }
			}
		} );

		$( '[title!=""]' ).qtip( {
			style: {
				classes: 'qtip-bootstrap'
			},
			position: {
				my: 'top left', // Position my top left...
				at: 'bottom left',
				adjust: { x: 5, y: 10 }
			}
		} );

		$( "#logout" ).qtip( {
			content: {
				text: "Click to logout; this will logout all CSAP SSO sessions"
			},
			style: {
				classes: 'qtip-bootstrap'
			},
			position: {
				my: 'top right', // Position my top left...
				at: 'bottom right',
				adjust: { x: 5, y: 10 }

			}
		} );

		$( "#summaryTable" ).trigger( "update" );

		setTimeout( function () {
			// delay a bit to ensure table sort of services has occured

			if ( $( "#news" ).is( ":visible" ) ) {
				realTimeMeter.getRealTimeMeters( getProject(), getLinkProject() );
				getServiceReport( "service", 1 );
				appTrends.getConfiguredTrends();
			}
		}, 500 )


	}

	var _oldErrorCount = 0;
	var _lastErrorTime = 0;
	//var _currentErrors = jQuery( '<div/>', { } );
	function alertProcessing( summaryResponse ) {

		var errors = summaryResponse["errors"];

		$alertList.empty() ;

//		var $alertDialogPanel = jQuery( '<div/>', {
//			class: "alertifyWarnings"
//		} ).appendTo( _currentErrors );

		_lastErrorTime = summaryResponse.timeStamp

		//var $errorList = jQuery( '<ol/>', { class: "decimal" } );
		var $errorList = $alertList ;
		//$alertDialogPanel.append( $errorList );

		var errorHostGroup = new Object();

		var errorCount = 0;
		if ( errors.length != 0 ) {

			for ( var i = 0; i < errors.length; i++ ) {

				errorCount++;
				
				var $errorItem = jQuery( '<li/>', { class: "decimal" } );

				var wordArray = errors[i].split( " " );
				if ( wordArray[0].contains( ":" ) ) {
					try {
						var targetHost = wordArray[0].substring( 0, wordArray[0].length - 1 );
						var targetMessage = errors[i].substring( errors[i].indexOf( ":" ) + 1 );
						
						var healthReport = "health report:";
						var $healthLink = null;
						if ( targetMessage.contains(healthReport)) {
							var reportStartIndex = targetMessage.indexOf(healthReport) ;
							var healthUrl=targetMessage.substring( reportStartIndex + healthReport.length )
							targetMessage = targetMessage.substring(0,reportStartIndex );

							var $healthLink = jQuery( '<a/>', {
								target: "_blank",
								class: "simple healthReport",
								href: healthUrl,
								text: targetHost + " health report."
							} );
						}

						var hostUrl = agentHostUrlPattern.replace( /CSAP_HOST/g, targetHost )
								+ "/os/HostDashboard";

						var $hostPortalLink = jQuery( '<a/>', {
							target: "_blank",
							class: "simple",
							href: hostUrl,
							text: targetHost
						} );

						var first3words = getWords( targetMessage, 3 );

						if ( errorHostGroup[ first3words ] != undefined ) {
							var $existingItem = errorHostGroup[ first3words ];
							$existingItem.append( "<br/>" );
							$existingItem.append( $hostPortalLink );
							$existingItem.append( targetMessage );
							if ( $healthLink) {
								$existingItem.append( $healthLink ) ;
							}
							// add host;
						} else {
							$errorItem.text( targetMessage );

							if ( $healthLink) {
								$errorItem.append( $healthLink ) ;
							}
							$errorItem.prepend( $hostPortalLink );
							errorHostGroup[ first3words ] = $errorItem;
							$errorList.append( $errorItem );
						}
						



					} catch ( e ) {
						console.log( "Failed parsing words" + errors[i] );
						$errorItem.text( errors[i] );
						$errorList.append( $errorItem );
					}
				} else {
					$errorItem.html( errors[i] );
					$errorList.append( $errorItem );
				}
			}
		}

		var hostStatus = summaryResponse["hostStatus"];
		for ( var hostKey in hostStatus ) {
			var statusObject = hostStatus[hostKey];

			if ( statusObject != undefined ) {
				var vmSessions = statusObject["vmLoggedIn"];
				if ( vmSessions != undefined && vmSessions.length > 0 ) {
					foundErrors = true;

					for ( var i = 0; i < vmSessions.length; i++ ) {

						errorCount++;
						var $errorItem = jQuery( '<li/>', { class: "decimal" } );
						$errorList.append( $errorItem );

						try {
							var hostUrl = agentHostUrlPattern.replace( /CSAP_HOST/g, hostKey )
									+ "/os/HostDashboard";


							var $hostPortalLink = jQuery( '<a/>', {
								target: "_blank",
								class: "simple",
								href: hostUrl,
								text: hostKey
							} );


							$errorItem.text( " session:" + vmSessions[i] );
							$errorItem.prepend( $hostPortalLink );

						} catch ( e ) {
							console.log( "Failed parsing words" + errors[i] );
							$errorItem.text( hostKey + " session:" + vmSessions[i] );
						}

					}
				}
			}
		}

		if ( errorCount > 0 ) {

			var warningBanner = summaryResponse.timeStamp + "  Found " + errorCount + " alerts";
			// errorHtml += "</ol>" ;
			//$("#warning").html(errorHtml);
			$( "#warning span" ).text( warningBanner );
			$( "#warning" ).css( "display", "inline-block" );
			if ( _oldErrorCount != errorCount ) {
				var origWidth = $( "#warning" ).css( "width" );
				flash( $( "#warning" ) );
				// $("#warning").effect("highlight", {}, 1000) ;
			}
		} else {
			$( "#warning" ).hide();
		}

		_oldErrorCount = errorCount;
	}

	function getWords( str, count ) {
		return str.split( /\s+/ ).slice( 0, count ).join( " " );
	}

	function getServiceImage( serviceType ) {


		var serviceImage = "";

		switch ( serviceType ) {
			case "runtime" :
			case "wrapper" :
				serviceImage = '<img src="images/16x16/run2.png"/>';
				break;
			case "csapAgent" :
				serviceImage = '<img src="images/16x16/run3.png"/>';
				break;
			case "database" :
				serviceImage = '<img src="images/16x16/dbSmall.png"/>';
				break;
			case "messaging" :
				serviceImage = '<img src="images/16x16/mail-send-receive.png"/>';
				break;
			case "webServer" :
				serviceImage = '<img src="images/httpd.png"/>';
				break;
			case "docker" :
				serviceImage = '<img src="images/docker.png"/>';
				break;
			case "monitor" :
			case "os" :
				serviceImage = '<img src="images/16x16/sysMon.png"/>';
				break;
			case "script" :
			case "scripts" :
			case "package" :
				serviceImage = '<img src="images/16x16/files.png"/>';
				break;
			case "SpringBoot":
				serviceImage = '<img src="images/bootSmall.png"/>';
				break;
			default:
				serviceImage = '<img src="images/16x16/tomcat.png"/>';
		}

		return serviceImage;
	}

	function updateTableWithServiceSummary( servicesActive, servicesTotal, servicesType ) {

		var tableHtml = "";
		var i = 0;

		for ( var svcName in servicesTotal ) {

			var cssClass = "";
			if ( $( ".selected" ).length != 0 && $( ".selected" ).first().data( 'service' ) == svcName ) {
				cssClass = "selected";
				serviceInstanceGet( svcName );
			}

			var tdCss = "";
			var tdImage = "";
			if ( servicesType != undefined ) {
				//
				tdImage = getServiceImage( servicesType[svcName] );
			}


			tableHtml += '<tr class="' + cssClass + '" data-service="' + svcName + '"><td class="summaryColumn1 ' + tdCss + '">' + tdImage + svcName;
			tableHtml += '<div class="typeLabel">' + servicesType[svcName] + '</div>';
			tableHtml += '</td><td class="summaryColumn2">';
			if ( servicesType != undefined && servicesType[svcName] == "package" ) {
				tableHtml += '<span style="font-size: 0.8em">-</span>';
			} else {
				tableHtml += servicesActive[svcName] + " / " + servicesTotal[svcName];
				if ( servicesActive[svcName] != servicesTotal[svcName] ) {
					if ( servicesActive[svcName] == 0 ) {
						tableHtml += '<img title="No service instances running." style="height: 12px"  src="images/16x16/red.png">';
					} else {
						tableHtml += '<img title="Warning - 1 or more services stopped" style="height: 12px"  src="images/16x16/yellow.png">';
					}
				} else {
					tableHtml += '<img style="height: 12px"  src="images/16x16/green.png">';
				}
			}
			tableHtml += '</td>';
			tableHtml += '<td  class="summaryColumn3"></td>';
			tableHtml += '</tr>';
		}
		$( "#summaryTable tbody" ).html( tableHtml );

		$( '#summaryTable tbody tr' ).click( function () {

			$( "#news" ).hide();
			$( "#resultsSection" ).hide();

			$( "#instanceTable tbody" ).html( '<tr><td colspan=5>loading</td></tr>' );
			// $("#instanceTable").parent().css("height", "100px") ;
			$( "#instanceTableDiv" ).css( "display", "inline-block" );

			$( ".selected" ).removeClass( "selected" );
			$( this ).addClass( "selected" );
			serviceInstanceGet( $( this ).data( 'service' ) );

			return false; // prevents link

		} );


		$( '.serviceFilter' ).keyup( function () {

			var filter = $( '.serviceFilter' ).val();
			// console.log("updated filter") ;

			if ( filter.length > 0 ) {
				$( '#summaryTable tbody tr' ).hide();
				$( '#summaryTable tbody tr td:contains("' + filter + '")' ).parent().show();
			} else {
				$( '#summaryTable tbody tr' ).show();
			}
			return true; // prevents link
		} );

		$( '.serviceFilter' ).keyup();
	}

	var maxDu = 90;

	function updateTableWithHostSummary( hostStatus ) {

		var tableHtml = "";
		for ( var hostName in hostStatus ) {

			//var hostName = sortedArray[ i ] ;
			var statusObject = hostStatus[hostName];

			var style = "";
			if ( $( ".selected" ).length != 0 && $( ".selected" ).first().data( 'host' ) == hostName ) {
				style = "selected";
				serviceInstanceGetForHost( hostName );
			}
			// set CsAgent as the default instance for host for file launches
			tableHtml += '<tr class="' + style + '" data-host="' + hostName + '" data-instance="CsAgent_8011"><td class="summaryColumn1">' + hostName;

			if ( statusObject.cpuLoad == undefined ) {
				tableHtml += '</td><td class="summaryColumn2">-</td><td  class="summaryColumn3">-</td><td  class="summaryColumn4">-';
			} else {
				tableHtml += '</td><td  class="summaryColumn2">' + statusObject.serviceActive + " / " + statusObject.serviceTotal;
				if ( statusObject.serviceActive != statusObject.serviceTotal ) {
					tableHtml += '<img class="statusIcon"  src="images/16x16/yellow.png">';
				} else {
					tableHtml += '<img class="statusIcon"  src="images/16x16/green.png">';
				}

				tableHtml += '</td><td  class="summaryColumn3">';
				if ( statusObject.cpuLoad > statusObject.cpuCount ) {
					tableHtml += '<img class="statusIcon"  src="images/16x16/red.png">';
				} else {
					tableHtml += '<img class="statusIcon"  src="images/16x16/green.png">';
				}
				tableHtml += statusObject.cpuLoad + " / " + statusObject.cpuCount;

				tableHtml += '</td><td  class="summaryColumn4">';
				if ( statusObject.du > maxDu ) {
					tableHtml += '<img class="statusIcon"  src="images/16x16/yellow.png">';
				} else {
					tableHtml += '<img class="statusIcon"  src="images/16x16/green.png">';
				}
				tableHtml += statusObject.du + "%";
			}

			tableHtml += '</td></tr>';
		}
		$( "#summaryTable tbody" ).html( tableHtml );


		$( '#summaryTable tbody tr' ).click( function () {
			// alert( $(this).attr('id') ) ;
			// $("#instanceTable tbody").empty() ;

			// $('#instanceTable tr:last').after('<tr><td>' +$(this).attr('id') + '</td</tr>');

			$( "#news" ).hide();
			$( "#resultsSection" ).hide();


			$( "#instanceTable tbody" ).html( '<tr><td colspan=7>loading</td></tr>' );
			// $("#instanceTable").parent().css("height", "100px") ;
			$( "#instanceTableDiv" ).css( "display", "inline-block" );
			// alert("click host") ;

			serviceInstanceGetForHost( $( this ).data( "host" ) );

			$( ".selected" ).removeClass( "selected" );
			$( this ).addClass( "selected" );

			return false; // prevents link


		} );



		$( '.hostFilter' ).keyup( function () {

			var filter = $( '.hostFilter' ).val();
			// console.log("updated filter") ;

			if ( filter.length > 0 ) {
				$( '#summaryTable tbody tr' ).hide();
				$( '#summaryTable tbody tr td:contains("' + filter + '")' ).parent().show();
			} else {
				$( '#summaryTable tbody tr' ).show();
			}
			return true; // prevents link
		} );

		$( '.hostFilter' ).keyup();


	}

	var isMavenDeploy = true;
	function deployWar() {

		isMavenDeploy = true;
		var paramObject = {
			scmUserid: "dummy",
			scmPass: "dummy",
			scmBranch: "dummy"
		};
		toggleDeployDetails();
		executeOnSelectedHosts( "rebuildServer", paramObject );

	}

	function deploySource() {

		isMavenDeploy = false;
		var paramObject = {
			scmUserid: $( "#scmUserid" ).val(),
			scmPass: $( "#scmPass" ).val(),
			scmBranch: $( "#scmBranch" ).val()
		};

		var scmCommand = $( "#scmCommand" ).val();

		if ( scmCommand.indexOf( "deploy" ) == -1
				&& $( "#isScmUpload" ).is( ':checked' ) ) {
			scmCommand += " deploy";
		}

		$.extend( paramObject, {
			scmCommand: scmCommand
		} );

		toggleDeployDetails();
		executeOnSelectedHosts( "rebuildServer", paramObject );

	}


	function killService() {

		var paramObject = new Object();

		if ( $( "#isClean" ).is( ':checked' ) ) {
			// paramObject.push({noDeploy: "noDeploy"}) ;
			$.extend( paramObject, {
				clean: "clean"
			} );
		}

		executeOnSelectedHosts( "killServer", paramObject );

	}


	function startJvm() {


		var paramObject = new Object();

		if ( $( "#noDeploy" ).is( ':checked' ) ) {
			// paramObject.push({noDeploy: "noDeploy"}) ;
			$.extend( paramObject, {
				noDeploy: "noDeploy"
			} );
		}

		if ( $( "#isDebug" ).is( ':checked' )
				&& $( "#javaOpts" ).val().indexOf( "Xdebug" ) == -1 ) {
			// paramObject.push({noDeploy: "noDeploy"}) ;

			var opts = $( "#javaOpts" ).val()
					+ " -Xdebug -Xrunjdwp:transport=dt_socket,address=" + debugPort
					+ ",server=y,suspend=n";
			alert( "Service starting in debug mode, debug port is: " + debugPort
					+ ", options to service:" + opts );
			$.extend( paramObject, {
				commandArguments: opts
			} );
		} else {
			$.extend( paramObject, {
				commandArguments: $( "#javaOpts" ).val()
			} );
		}


		executeOnSelectedHosts( "startServer", paramObject );

	}

	var skipCurrent = false;
	var sortedCommandItems;
	var currCommandIndex = 0;
	var numCommands = 0;
	var currCommand;
	var currParamObject;

	function executeOnSelectedHosts( command, paramObject ) {

		if ( !confirmMinSelections( true, command ) )
			return;

		currCommand = command;
		currParamObject = paramObject;

		//$( "#resultsSection" ).show();
		$( "#resultPre" ).append( command + " Request is being processed\n" );

		$( 'body' ).css( 'cursor', 'wait' );

		numCommands = 0;

		sortedCommandItems = new Array();

		$( "#instanceTable *.selected" ).sort( function ( a, b ) {

			// alert ( "id: " + a.id );
			var aInt = parseInt( $( "#" + a.id ).column( 1 ).text(), 10 );
			var bInt = parseInt( $( "#" + b.id ).column( 1 ).text(), 10 );
			// alert ( "a: " + $("#" + a.id).column(1).text() + " b: "  +$("#" + b.id).column(1).text() ) ;
			if ( command.indexOf( "stop" ) != -1 || command.indexOf( "kill" ) != -1 ) {
				return bInt - aInt;
			} else {
				return aInt - bInt;
			}
		} ).each( function () {
			// alert($(this).data("instance")) ;
			sortedCommandItems[numCommands] = $( this );
			numCommands++;
		} );

		currCommandIndex = 0;
		executeNextSelection();

	}

	function addResult( result ) {
		$( "#resultPre" ).append( "\n" + result );
		$( "#resultPre" ).scrollTop( $( "#resultPre" ).scrollHeight );
	}

	function executeNextSelection() {

		if ( currCommandIndex == numCommands ) {
			$( 'body' ).css( 'cursor', 'default' );

			addResult( "Completed command on selected services: " + currCommand );
			return;
		}

		// alert("numCommands: " + numCommands + " currCommandIndex: " + currCommandIndex ) ; 

		var currItem = sortedCommandItems[ currCommandIndex ];
		currCommandIndex++;


		var hostParamObject = new Object();

		// alert ($(this).attr("data-host") )
		var reqHost = $( "#summaryTable *.selected" ).first().data( "host" ); // (this).data("host")
		var reqInstance = currItem.data( "instance" );

		if ( reqInstance.indexOf( "8011" ) != -1 ) {
			displayHostResults( reqHost + "_" + reqInstance, "Skipping: " + currCommand, "CsAgent can only be deployed via service admin." );
			executeNextSelection();
			return;
		}

		// alert(currParamObject.clean) ;

		$.extend( hostParamObject, currParamObject, {
			hostName: reqHost,
			serviceName: reqInstance,
		} );

		if ( currCommand == "rebuildServer" && isMavenDeploy ) {
			// alert(  $("#input"+reqInstance).first().val() ) ;
			$.extend( hostParamObject, {
				mavenDeployArtifact: $( "#input" + reqInstance ).first().val()
			} );
		}

		addResult( currCommand + " of " + reqInstance + " on " + reqHost + " Scheduled" );

		$.post( baseUrl + "/" + currCommand, hostParamObject )
				.done(
						function ( results ) {
							// displayResults(results);
							displayHostResults( reqHost + "_" + reqInstance, currCommand, results );
							serviceSummaryGet();
							executeNextSelection();
						} )
				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( reqHost + ":" + currCommand, errorThrown );
				} );
	}



	function displayHostResults( host, command, resultsJson ) {

		var results = JSON.stringify( resultsJson, null, "\t" );
		results = results.replace( /\\n/g, "<br />" );

		var style = 'style="display: none; font-size: 0.9em"';
		if ( results.indexOf( "__ERROR" ) != -1 || results.indexOf( "__WARN" ) != -1 ) {
			style = 'style="display: block; font-size: 0.9em"';
		}
		var hostHtml = '\n' + host + ':' + command + ' completed.(<a class="simple" style="display:inline" id="' + host + 'Toggle" href="toggle">Results</a>)';
		hostHtml += '\n<div class="note" ' + style + ' id="' + host + 'Result" > ' + results + '</div>';

		$( "#resultPre" ).append( hostHtml );

		$( '#' + host + 'Toggle' ).click( function () {

			$( '#' + host + 'Result' ).toggle();

			if ( $( '#' + host + 'Result' ).is( ':visible' ) ) {
				if ( isResultsMinSize() ) {
					toggleResultsButton();
				}
			} else {
				if ( !isResultsMinSize() ) {
					toggleResultsButton();
				}
			}

			return false; // prevents link
		} );

		showResultsDialog( command );

		// needed when results is small
//	 $( "#resultPre" ).scrollTop( $( "#resultPre" )[0].scrollHeight );
//
//	 // Needed when results are maxed
//	 if ( !isResultsMinSize() ) {
//		  $( "html, body" ).animate( {
//				scrollTop: $( document ).height()
//		  }, "fast" );
//	 }
//
//	 $( "#resultsSection" ).show();

	}

	var resultsDialog = null;

	function showResultsDialog( commandName ) {

		$( "#commandOptionsDiv" ).hide();

		// if dialog is active, just updated scroll and return
		if ( resultsDialog != null ) {
			var heightToScroll = $( "#resultPre" )[0].scrollHeight;
			// console.log("Scrolling to bottom: " + heightToScroll) ;
			$( ".ajs-content" ).scrollTop( heightToScroll );
			return;
		}



		if ( !alertify.results ) {
			alertify.dialog( 'results', function factory() {
				return{
					build: function () {

					}
				};
			}, false, 'alert' );
		}

		resultsDialog = alertify.results( '<div id="resultsAlertify"></div>' );

		var targetWidth = $( window ).outerWidth( true ) - 100;
		var targetHeight = $( window ).outerHeight( true ) - 100;

		var dialogTitle = commandName;

		resultsDialog.setting( {
			title: "Results: " + dialogTitle,
			resizable: true,
			movable: false,
			'label': "Close after reviewing output",
			'onok': function () {
				$( "#resultsSection" ).append( $( "#resultPre" ) );
				resultsDialog = null;
			}


		} );

		// resultsDialog.resizeTo(targetWidth, targetHeight)

		$( "#resultsAlertify" ).append( $( "#resultPre" ) );


	}


	function confirmMinSelections( isServiceCheck, command ) {

		$( "#resultPre" ).html( "" );

		var numHosts = $( "#summaryTable *.selected" ).length;

		if ( numHosts == 0 ) {

			var message = "Select at least 1 Host for " + command;
			alertify.alert( message );
			return false;
		}

		if ( !isServiceCheck || command == "reImage" )
			return true;

		var numServices = $( "#instanceTable *.selected" ).length;

		if ( numServices == 0 ) {

			alertify.alert( "Select at least 1 instance for " + command );
			return false;
		}

		return true;
	}

	function getPanel2Width() {
		var targetWidth = $( window ).outerWidth( true ) - $( "#summaryTableDiv" ).outerWidth( true ) - 60;
		// console.log( "targetWidth: " + targetWidth + "summaryWidth: " + $( "#summaryTableDiv" ).outerWidth( true ) ) ;
		return targetWidth;
	}

	function windowResize() {

		//var displayHeight = $(window).outerHeight( true) - $("header").outerHeight( true) - 20;
		// var $("#contents").css("width", $(window).outerWidth(true) - 90)
		$( "#news" ).css( "width", getPanel2Width() );


		var summaryContainer = $( "#summaryTableDiv" );
		var instanceContainer = $( "#instanceTableDiv" );

//		var displayHeight = $( window ).outerHeight( true ) - $( "#header" ).outerHeight( true ) - $( "#footer" ).outerHeight( true )
//				- $( "#summaryHeader" ).outerHeight( true ) - $( "#mainDisplayNav" ).outerHeight( true )
//				- $( "#commandOptionsDiv" ).outerHeight( true ) - $( "#summaryTotal" ).outerHeight( true );

		var displayHeight = $( window ).outerHeight( true )
				- $( "#summaryTableDiv" ).offset().top - $( "#footer" ).outerHeight( true ) - 30;

		$( "#news" ).css( "height", displayHeight - 10 );
		// displayHeight += $("#mainDisplayArea").height() - $("#mainDisplayArea").outerHeight( true)  ; 
		// displayHeight += $("#summaryTable").height() - $("#summaryTableDiv").outerHeight( true)  ; 

		var targetHeight = Math.floor( displayHeight - $( "#summaryTable thead" ).outerHeight( true ) );
		// console.log("thead: " + $("#summaryTable thead").outerHeight( true) + " targetHeight: " + targetHeight + " summaryTBody:" + $("#summaryTable tbody").outerHeight( true)) ;

		$( "#summaryTable tbody" ).css( "height", targetHeight );
		var rowCount = $( "#summaryTable tr" ).length;
		var rowHeight = $( "#summaryTable thead" ).outerHeight( true ) - 4;
		var measuredHeight = Math.floor( rowCount * rowHeight );
		if ( (measuredHeight) < targetHeight ) {
			// Compress table so it does not steal space
			//console.log("Setting summary height to auto, measuredHeight: " + measuredHeight + "targetHeight: " + targetHeight) ;
			$( "#summaryTable tbody" ).css( "height", "auto" );
		}

		$( "#instanceTable tbody" ).css( "height", targetHeight );
		rowCount = $( "#instanceTable tr" ).length;
		measuredHeight = Math.floor( rowCount * rowHeight );
		if ( measuredHeight < targetHeight ) {
			$( "#instanceTable tbody" ).css( "height", "auto" );
		}

		// handleScrollbars( displayHeight, summaryContainer , $("#summaryTable"), $("#summaryHeader") ) ;

		// $("body").append("id: " + $("#summaryHeader").attr("id") ) ;

		// handleScrollbars( displayHeight, instanceContainer , $("#instanceTable"), $("#instanceHeader") ) ;

	}



} );
