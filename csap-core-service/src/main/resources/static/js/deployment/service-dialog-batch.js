var batchResultTemplate = null;

function showBatchDialog( batchHtml ) {

	var $batchContent = $( "<html/>" ).html( batchHtml ).find( '#batchSelect' );
	batchResultTemplate = $( "<html/>" ).html( batchHtml ).find( '#batchResultTemplate' );
	// console.log( "cluster: " + $cluster.html()) ;

	var isNewDialog = false;
	if ( !alertify.deploy ) {
		isNewDialog = true;
		alertify.dialog( 'deploy', batchRequestDialogFactory, false, 'alert' );
	}

	alertify.deploy( $batchContent.html() ).show();

	var targetWidth = $( window ).outerWidth( true ) - 100;
	var targetHeight = $( window ).outerHeight( true ) - 100;
	alertify.deploy().resizeTo( targetWidth, targetHeight );

	if ( isNewDialog ) {
		batchEventRegistration();
	}

}

// Need to register events after dialog is loaded
function batchEventRegistration() {

	$( '.showFiltersButton' ).click( function () {
		console.log( "showing" );
		$( '.batchFilter' ).show( 500 );
	} );


	$( '.batchDialog .uncheckAll' ).click(
			function () {

				$( 'input', $( this ).parent().parent() )
						.prop( "checked", false ).trigger( "change" );
				return false; // prevents link
			} );

	$( '.batchDialog .checkAll' ).click(
			function () {

				console.log( "checking all" );

				if ( $( this ).hasClass( "services" )
						&& !$( "#osProcessInclude" ).is( ':checked' ) ) {
					$( 'input.java', $( this ).parent().parent() ).prop(
							"checked", true ).trigger( "change" );
				} else {
					$( 'input', $( this ).parent().parent() ).prop( "checked",
							true ).trigger( "change" );
				}

				return false; // prevents link
			} );

	$( ".batchDialog input:checked" ).parent().animate( {
		"background-color": "yellow"
	}, 1000 ).fadeOut( "fast" ).fadeIn( "fast" );

	$( ".batchDialog input" ).change( function () {
		var highlightColor = "white";

		if ( $( this ).is( ":checked" ) )
			highlightColor = "yellow";

		// $(".hostLabel",
		// $("input.instanceCheck").parent()).css("background-color",
		// $(".ajs-dialog").css("background-color") ) ;
		$( $( this ).parent() ).css( "background-color", highlightColor );
	} );

	// $("#batchResult table tbody").resizable();
	$( ".batchClusterSelect" ).change(
			function () {
				$( '.batchFilter' ).show( 500 );

				var clusterSelected = $( this ).val();
				var clusterHostJson = jQuery
						.parseJSON( $( "#clusterHostJson" ).text() );
				var hosts = clusterHostJson[clusterSelected];
				// console.log("Selecting: " + clusterSelected + " vals: " +
				// hosts) ;
				for ( var i = 0; i < hosts.length; i++ ) {
					console.log( "Selecting: " + hosts[i] );
					$( ".batchDialog input[value=" + hosts[i] + "]" ).prop(
							'checked', true ).trigger( "change" );
				}

				var clusterServiceJson = jQuery.parseJSON( $(
						"#clusterServiceJson" ).text() );
				var serviceArray = clusterServiceJson[clusterSelected];
				// console.log("Selecting: " + clusterSelected + " vals: " +
				// hosts) ;
				for ( var i = 0; i < serviceArray.length; i++ ) {
					console.log( "Selecting: " + serviceArray[i] );
					if ( !$( "#osProcessInclude" ).is( ':checked' ) ) {
						$(
								".batchDialog input.java[value="
								+ serviceArray[i] + "]" ).prop(
								'checked', true ).trigger( "change" );
					} else {
						$(
								".batchDialog input[value="
								+ serviceArray[i] + "]" ).prop(
								'checked', true ).trigger( "change" );
					}

				}

				$( this ).val( "none" );
				return;
			} );

}


function batchRequestDialogFactory() {
	return {
		build: function () {
			this.setting( {
				'onok': function ( closeEvent ) {
					console.log( "Submitting Request: "
							+ JSON.stringify( closeEvent ) );

					if ( closeEvent.button.text == "Kill" ) {
						return issueBatchKillRequest();
					} else if ( closeEvent.button.text == "Start" ) {
						return issueBatchStartRequest();
					} else if ( closeEvent.button.text == "Deploy" ) {
						return issueBatchDeployRequest();
					} else {
						alertify.warning( "Cancelled Request" );
					}

				}
			} );
		},
		setup: function () {
			return {
				buttons: [{
						text: "Deploy",
						className: alertify.defaults.theme.ok,
						key: 27
								/* Esc */ }, {
						text: "Kill",
						className: alertify.defaults.theme.ok,
						key: 27
								/* Esc */ }, {
						text: "Start",
						className: alertify.defaults.theme.ok,
						key: 27
								/* Esc */ }, {
						text: "Cancel",
						className: alertify.defaults.theme.cancel,
						key: 27
								/* Esc */ }],
				options: {
					title: "Batch Deploy Operations",
					resizable: true,
					movable: false,
					maximizable: false
				}
			};
		}

	};
}
;


function showBatchResults( results ) {

	// console.log( "cluster: " + $cluster.html()) ;
	if ( !alertify.deployResults ) {
		buildBatchResultDialog();
	}

	var targetWidth = Math.floor( $( window ).outerWidth( true ) - 100 );
	var targetHeight = Math.floor( $( window ).outerHeight( true ) - 100 );


	alertify.deployResults( batchResultTemplate.html() )
			.show()
			.resizeTo( targetWidth, targetHeight );


	var $resultContainer=$(".batchResult") ; // inside of dialog
	
	$( "#batchMessage", $resultContainer ).text( results.result );
	$( "#jobsOperations", $resultContainer ).text( results.jobsOperations );
	$( "#jobsCount", $resultContainer ).text( results.jobsCount );
	$( "#jobsRemaining", $resultContainer ).text( results.jobsRemaining );
	$( "#batchParallel", $resultContainer ).text( results.parallelRequests );

	setTimeout( function () {
		addHostBatchTable( results );
	}, 500 )

	$( "#batchProgressBar", $resultContainer ).progressbar( {
		value: 0,
		max: results.jobsRemaining
	} );
	$( "#batchProgressLabel", $resultContainer ).text( "Jobs Remaining: " + results.jobsRemaining )

	$( '#refreshButton' ).trigger( "click" );
	setTimeout( function () {
		upateJobAndTaskCountsUntilDone();
	}, 5000 );

}

function buildBatchResultDialog() {
	var deployResultsFactory = function factory() {
		return {
			build: function () {
				this.setting( {
					'onok': function ( closeEvent ) {
						// console.log( "Submitting Request: " +
						// JSON.stringify(closeEvent) );
						// $( '#refreshButton' ).trigger("click") ;
					}
				} );
			},
			setup: function () {
				return {
					buttons: [{
							text: "Close",
							className: alertify.defaults.theme.ok,
							key: 27
									/* Esc */ }],
					options: {
						title: "Batch Schedule Results",
						resizable: true,
						movable: false,
						maximizable: false
					}
				};
			}

		};
	};

	alertify.dialog( 'deployResults', deployResultsFactory, false, 'alert' );

}

function addHostBatchTable( results ) {
	$tbody = $( "#hostJobsTable tbody" );
	$tbody.empty();
	console.log("agentHostUrlPattern", agentHostUrlPattern);
	for ( var hostName in results.hostInfo ) {
		var $item = jQuery( '<tr/>', { } );

		
		var urlAction = agentHostUrlPattern.replace( /CSAP_HOST/g, hostName );
		urlAction += "/file/FileMonitor?u=1&isLogFile=true&serviceName=CsAgent_8011&hostName=" + hostName;
		
		
		var $hostLogs = jQuery( '<a/>', {
			class: "simple",
			title: "View host logs",
			target: "_blank",
			href: urlAction,
			text: hostName + " Logs"
		} ).click( function () {
			openWindowSafely( urlAction, "_blank" );
			var targetWidth = $( window ).outerWidth( true ) - 100;
			var targetHeight = $( window ).outerHeight( true ) - 100;
			alertify.deployResults().resizeTo( targetWidth, targetHeight );

			return false;
		} );

		$item.append( jQuery( '<td/>', {
			class: "col1"
		} ).append( $hostLogs ) );

		var $hostDiv = jQuery( '<div/>', {
			class: "serviceLogs",
			text: results.hostInfo[hostName].info
		} );


		var hostServiceArray = results.hostInfo[hostName].services;
		if ( hostServiceArray == undefined )
			hostServiceArray = new Array();
		for ( var i = 0; i < hostServiceArray.length; i++ ) {

			var serviceName = hostServiceArray[i];
			var baseUrl = agentHostUrlPattern.replace( /CSAP_HOST/g, hostName );
			var svcUrl = baseUrl + "/file/FileMonitor?isLogFile=true&serviceName="
					+ serviceName + "&hostName=" + hostName;

			if ( results.result.contains( "kill" ) ) {

				svcUrl = baseUrl + "/file/FileMonitor?serviceName=" + serviceName
						+ "&hostName=" + hostName + "&ts=1&fileName=//" + serviceName + "_kill.log&isLogFile=false&";
			}

			var $svcLogs = jQuery( '<a/>', {
				class: "simple",
				title: "View service logs",
				target: "_blank",
				href: svcUrl,
				text: serviceName
			} ).click( function () {
				openWindowSafely( $( this ).attr( "href" ), "_blank" );
				var targetWidth = $( window ).outerWidth( true ) - 100;
				var targetHeight = $( window ).outerHeight( true ) - 100;
				alertify.deployResults().resizeTo( targetWidth, targetHeight );

				return false;
			} );

			$hostDiv.append( $svcLogs );
		}

		$item.append( jQuery( '<td/>', {
			class: "col2"
		} ).append( $hostDiv ) );

		$tbody.append( $item );
	}


	var $dialog = $tbody.parent().parent();
	var tableHeight = Math.floor(
			$dialog.outerHeight( true ) - 80
			- $( ".batchResult" ).outerHeight( true ) );

	console.log( "tableHeight", tableHeight, " dialog height", $dialog.outerHeight( true ) - 80 );
	$tbody.css( "max-height", tableHeight );

	var col2Width = $( ".batchResult" ).outerWidth( true )
			- $( "th:nth-child(1)", "#hostJobsTable" ).outerWidth( true ) - 100;

	console.log( "col2Width", col2Width );
	$( "th:nth-child(2), td:nth-child(2)", "#hostJobsTable" )
			.css( "width", Math.floor( col2Width ) );
}

function isValidBatchParams() {

	if ( $( 'input.hostCheckbox:checked' ).map( function () {
		return this.value;
	} ).get().length == 0 ) {
		alertify.warning( "No hosts Selected" );
		return false;
	}

	if ( $( 'input.serviceCheckbox:checked' ).map( function () {
		return this.value;
	} ).get().length == 0 ) {
		alertify.warning( "No services Selected" );
		return false;
	}

	return true;
}

function issueBatchKillRequest() {
	if ( !isValidBatchParams() ) {
		return false;
	}
	var hostParamObject = {
		'hostName': $( 'input.hostCheckbox:checked' ).map( function () {
			return this.value;
		} ).get(),
		'serviceName': $( 'input.serviceCheckbox:checked' ).map( function () {
			return this.value;
		} ).get()
	};

	if ( $( "#batchCleanCheckbox" ).is( ':checked' ) ) {
		$.extend( hostParamObject, {
			clean: "clean"
		} );
	}

	$.post( baseUrl + "/batchKill", hostParamObject )
			.done( showBatchResults )
			.fail( function ( jqXHR, textStatus, errorThrown ) {

				handleConnectionError( "batchKill", errorThrown );
			} );

	return true;

}

function issueBatchStartRequest() {

	if ( !isValidBatchParams() ) {
		return false;
	}
	var hostParamObject = {
		'hostName': $( 'input.hostCheckbox:checked' ).map( function () {
			return this.value;
		} ).get(),
		'serviceName': $( 'input.serviceCheckbox:checked' ).map( function () {
			return this.value;
		} ).get()
	};
	
	console.log("posting to:", baseUrl + "/batchStart" , " parameters: ", hostParamObject )

	$.post( baseUrl + "/batchStart", hostParamObject )

			.done( showBatchResults )

			.fail( function ( jqXHR, textStatus, errorThrown ) {

				handleConnectionError( "batchStart", errorThrown );
			} );

	return true;

}

function issueBatchDeployRequest() {

	if ( !isValidBatchParams() ) {
		return false;
	}

	var hostParamObject = {
		'releasePackage': $( '.releasePackage' ).val(),
		'hostName': $( 'input.hostCheckbox:checked' ).map( function () {
			return this.value;
		} ).get(),
		'serviceName': $( 'input.serviceCheckbox:checked' ).map( function () {
			return this.value;
		} ).get()
	};

	$.post( baseUrl + "/batchDeploy", hostParamObject )

			.done( showBatchResults )

			.fail( function ( jqXHR, textStatus, errorThrown ) {

				handleConnectionError( "batchStart", errorThrown );
			} );

	return true;
}

function upateJobAndTaskCountsUntilDone( $resultContainer ) {
	console.log( "updateJobsRemaining..." );

	// get latest status from servers
	$( '#refreshButton' ).trigger( "click" );

	$.getJSON( "service/batchJobs" )

			.done(
					function ( responseJson ) {
						// console.log("jobsRemaining: " + responseJson.jobsRemaining )
						$( "#jobsRemaining", $resultContainer ).text( responseJson.jobsRemaining ).animate( {
							"background-color": "yellow"
						}, 1000 ).fadeOut( "fast" ).fadeIn( "fast" );

						if ( responseJson.jobsRemaining > 0
								|| responseJson.tasksRemaining == 0 ) {
							
							$( "#batchProgressLabel", $resultContainer ).text(
									"Jobs Remaining: " + responseJson.jobsRemaining );

							var progressMax = $( "#batchProgressBar" ).progressbar( "option", "max" ) ;
							console.log("progressMax: ", progressMax,   "jobsRemaining: ", responseJson.jobsRemaining )

							$( "#batchProgressBar", $resultContainer ).progressbar(
									"value",
									progressMax - responseJson.jobsRemaining );
						} else {
							var tasksRemaining = responseJson.tasksRemaining ;
							$( "#batchProgressLabel", $resultContainer ).text(
									"Tasks Remaining: " + tasksRemaining  ) ;
							
							var jobsCount = parseInt ( $( "#jobsOperations" ).text() );
							$( "#batchProgressBar", $resultContainer ).progressbar( "option", "max", jobsCount );
									
							console.log("jobsCount: ", jobsCount,   "tasksRemaining: ", tasksRemaining )
							
							$( "#batchProgressBar", $resultContainer ).progressbar(
									"value",
									jobsCount - tasksRemaining );
						}

						if ( responseJson.jobsRemaining > 0
								|| responseJson.tasksRemaining > 0 ) {
							setTimeout( function () {
								upateJobAndTaskCountsUntilDone();
							}, 5000 );
						} else {
							$( "#batchProgressLabel", $resultContainer ).text(
									"Batch tasks completed, verify services." ).css(
									"left", "1em" );
						}

					} )
}
