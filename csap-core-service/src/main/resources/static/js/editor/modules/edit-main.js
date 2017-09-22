// http://requirejs.org/docs/api.html#packages
// Packages are not quite as ez as they appear, review the above
//require.config( {
//	packages: [
//		{ name: 'editPackage',
//			location: '../../editor/modules', // default 'packagename'
//			main: 'service-edit'                // default 'main' 
//		}
//	]
//} );



require( ["definition-browser", "life-edit", "json-forms", "tab-release"], function ( defBrowser, lifeEdit, jsonForms, releaseTab ) {

	console.log( "\n\n ************** _main: loaded *** \n\n" );

	var $packageSelected = $( '.releasePackage' );
	var resizeTimer = 0;

	var defaultComment = "Check in comments are critical - enter meaningful comments as this is maintained by a team. 10 characters are minimum.";
	// Shared variables need to be visible prior to scope

	$( document ).ready( function () {
		initialize();
	} );

	var _lastTabSelected = $( "#showSummary" ).text().trim();
	this.initialize = initialize;
	function initialize() {
		CsapCommon.configureCsapAlertify();
		console.log( "main::initialize" );

		$( '#summaryTab [title!=""]' ).qtip( {
			content: {
				attr: 'title',
				button: true
			},
			style: {
				classes: 'qtip-bootstrap'
			},
			position: {
				my: 'bottom left', // Position my top left...
				at: 'top left',
				adjust: {
					x: +5,
					y: -5
				}
			},

//			hide: {
//				event: true
//			}
		} );
		allowTabs();
		// console.log("AppInit - trace") ;
		// console.trace() ;
		// $( document ).tooltip(); // jqueryui registration
		$( window ).resize( scheduleWindowResize );

		$( "#comment" ).attr( "placeholder", defaultComment );

		$( "#showPackageDialog" ).click( function () {
			showPackageDialog();
			return false;
		} )


		// Update definition when needed for apply
		jsonForms.configureUpdateFunction( getDefinitionFromServer );


		$( '#refreshButton' ).click( function () {
			document.location.reload( true );
			return false;
		} );

		$( '#showCiButton' ).click( function () {
			if ( !isValidDefinition( false ) ) {
				alertify.csapWarning( "Errors found in defintion. Correct before continuing." );
//					return;
			}
			showCheckinDefintionDialog();
		} );

		$( '.regionButton' ).click( function () {
			// $("#json").select() ;
			// from commonJQ
			// highlightRegion();
			//treeEditor.highlightTextEditorRegion();
			return false;
		} );

		$( '#applyButton' ).click(
				function () {
					var message = "Confirm apply changes to hosts in lifecycle " + defaultLifeCycle
							+ ". Note this may be overwritten if not checked in.<br><br>";

					var applyDialog = alertify.confirm( message );

					applyDialog.setting( {
						title: "Application Cluster Update",
						'onok': function () {
							applyCluster()
						},
						'oncancel': function () {
							alertify.warning( "Operation Cancelled" );
						}

					} );

					return false;
				} );

		$( '#validateConfig' ).click( function () {
			alertify.notify( "Validating definition" );
			checkDefaultUser();
			validateDefinition( false );
			return false;
		} );

		$( '#showReloadButton' ).click( function () {
			// $('#reloadDiv').toggle();
			showReloadDefintionDialog();

		} );

		$( '#cleanFsButton' ).click(
				function () {
					var inputMap = {
						fromFolder: "__staging__/build/" + definitionName,
						hostName: hostName,
						command: "delete"
					};
					postAndRemove( "_blank", osUrl + "/command", inputMap );

					$( "#editorMain" ).focus();
					return false;
				} );

		$( '#filesButton' ).click(
				function () {

					var urlAction = fileUrl + "/FileManager?hostName=" + hostName + "&ts=1&fromFolder=__staging__/conf&";

					openWindowSafely( urlAction, hostName + "ClusterFiles" );

					$( "#editorMain" ).focus();
					return false;
				} );

		// resetFocus to avoid visual glitches
		$( '#rawButton' ).click( function () {
			var urlAction = baseUrl + "api/capability";
			openWindowSafely( urlAction, hostName + "Definition" );
			$( "#editorMain" ).focus();

			return false;
		} );

		$( '#defButton' ).click( function () {
			var urlAction = "showSummary" + "?releasePackage=" + $packageSelected.val();
			openWindowSafely( urlAction, "_blank" );
			$( "#editorMain" ).focus();

			return false;
		} );
		// http://api.jqueryui.com/tabs/#event-beforeActivate
		$( "#tabs" ).tabs( {
			beforeActivate: function ( event, ui ) {
				var tabToOpen = ui.newTab.text().trim();
				console.log( "beforeActivate(): " + tabToOpen + " _lastTabSelected:" + _lastTabSelected );
				$( "#jsonFileContainer" ).append( $( "#json" ) );
				if ( tabToOpen == $( "#showLifeForm" ).text().trim() ) {
					getDefinitionFromServer( false );
					var needsConfirm = false;
					if ( _lastTabSelected != $( "#showSummary" ).text().trim() )
						needsConfirm = true;

					var msg = "Click OK to proceed to the Life Cycle Editor. Any uncommited changes will be lost."
					msg += "\n\nClick cancel to commit changes first";

					if ( needsConfirm && !window.confirm( msg ) ) {
						return false;
					}
					
					console.log( "Showing Life Editor tab" );
					lifeEdit.showSummaryView(
							lifecycle, $( "#lifeEditor" ), $( ".releasePackage" ).val() );

					_lastTabSelected = tabToOpen;
					$( "#lifeFormTab" ).prepend( $( "#clusterButtons" ) );
					$( "#clusterButtons" ).show();
				} else {
					if ( tabToOpen != $( "#showSummary" ).text() ) {
						// def can be shared between explorer, manager, text tab
						getDefinitionFromServer( false );
						if ( isValidDefinition( true ) ) {
							ui.newPanel.prepend( $( "#clusterButtons" ) );
							$( "#clusterButtons" ).show();
						} else {
							return false;
						}
					}
					_lastTabSelected = tabToOpen;

				}

			}
		} );

		// Tabs are initially hidden because browsers need to load styles first
		$( "#tabs" ).show();

		if ( $.urlParam( "path" ) != null ) {
			// alertify.alert("found path");
			$( "#tabs" ).tabs( "option", "active", 1 );
		}

	}

	var _packageDialogId = "selectPackage";
	function showPackageDialog() {

		if ( !alertify[ _packageDialogId ] ) {
			//isNewDialog = true;
			_base = alertify.dialog( _packageDialogId, packageDialogFactory, false, 'alert' );
			$( "button.packageSelectButton" ).click( function () {
				console.log( "showPackageDialog() - closing" );
				alertify[ _packageDialogId ]().close()
				var newPackage = $( this ).text().trim();

				$( "#showPackageDialog" ).text( newPackage );
				$packageSelected.val( newPackage );

				if ( _lastTabSelected == $( "#showLifeForm" ).text() ) {
					lifeEdit.showSummaryView( lifecycle, $( "#lifeEditor" ), newPackage );
				} else {
					getDefinitionFromServer( true );
				}
				;
			} )
		}

		alertify[ _packageDialogId ]().show();
	}

	function packageDialogFactory() {
		return {
			build: function () {
				this.setContent( $( "#packageDialogWrapper" ).show()[0] );
				this.setting( {
					'onok': function ( closeEvent ) {
						console.log( "packageDialogFactory(): dialog event:  "
								+ JSON.stringify( closeEvent ) );

					}
				} );
			},
			setup: function () {
				return {
					buttons: [
						{
							text: "Cancel Change",
							className: alertify.defaults.theme.cancel,
							key: 27 // escape key
						}],
					options: {
						title: "Package Selection: ",
						resizable: false,
						movable: false,
						autoReset: false,
						maximizable: false
					}
				};
			}

		};
	}


	function allowTabs() {

		$( "#json" ).keydown( function ( e ) {
			if ( e.keyCode === 9 ) { // tab was pressed
				// get caret position/selection
				var start = this.selectionStart;
				end = this.selectionEnd;

				var $this = $( this );

				// set textarea value to: text before caret + tab + text after caret
				$this.val( $this.val().substring( 0, start )
						+ "\t"
						+ $this.val().substring( end ) );

				// put caret at right position again
				this.selectionStart = this.selectionEnd = start + 1;

				// prevent the focus lose
				return false;
			}
		} );


	}

	// ref https://www.cambiaresearch.com/articles/15/javascript-char-codes-key-codes
	function reloadDialogFactory() {
		return{
			build: function () {
				// Move content from template
				this.setContent( $( "#reloadDiv" ).show()[0] );
				this.setting( {
					'onok': function () {
						console.log( "showReloadDefintionDialog(): ok pressed" )
						reloadCluster();
					},
					'oncancel': function () {
						alertify.warning( "Cancelled Request" );
					}
				} );
			},
			setup: function () {
				return {
					buttons: [
						{ text: "Perform Reload", className: alertify.defaults.theme.ok, key: 0 /* enter */ },
						{ text: "Cancel", className: alertify.defaults.theme.cancel, key: 27/* Esc */ }
					],
					options: {
						title: "Reload Application Definition :",
						resizable: false,
						movable: false,
						maximizable: true
					}
				};
			}

		};
	}


	function showReloadDefintionDialog() {
		console.log( "showReloadDefintionDialog() - init" );
		// Lazy create
		if ( !alertify.appReloadDialog ) {
			console.log( "showReloadDefintionDialog() - creating new dialog" );

			alertify.dialog( 'appReloadDialog', reloadDialogFactory, false, 'confirm' );


			// Do not want to bind since there are multiple commands
//			$( '#scmPass' ).keypress( function ( e ) {
//				console.log( "keypress detected in password" );
//				if ( e.which == 13 ) {
//					//alertify.closeAll();
//					//reloadCluster();
//				}
//			} );

		}

		alertify.appReloadDialog().show();


	}


	function showCheckinDefintionDialog() {
		// Lazy create
		if ( !alertify.checkInDefinition ) {
			var ciFactory = function factory() {
				return{
					build: function () {
						// Move content from template
						this.setContent( $( "#ciDiv" ).show()[0] );
						this.setting( {
							'onok': function () {
								ciCluster();
							},
							'oncancel': function () {
								alertify.warning( "Cancelled Request" );
							}
						} );
					},
					setup: function () {
						return {
							buttons: [{ text: "Commit Changes", className: alertify.defaults.theme.ok },
								{ text: "Cancel", className: alertify.defaults.theme.cancel, key: 27/* Esc */ }
							],
							options: {
								title: "Check In Application Definition :",
								resizable: false, movable: false, maximizable: false
							}
						};
					}

				};
			};
			alertify.dialog( 'checkInDefinition', ciFactory, false, 'confirm' );

			$( '#ciPass' ).keypress( function ( e ) {
				if ( e.which == 13 ) {
					alertify.closeAll();
					ciCluster();
				}
			} );
		}

		alertify.checkInDefinition().show();


	}

	var _LOADING_MESSAGE = "loading...";

	function isValidDefinition( isReload ) {
		try {
			if ( $( '#json' ).val() != _LOADING_MESSAGE ) {

				// console.log("Starting parse") ;
				if ( isReload ) {

					// var parsedJson = JSON.parse( $( '#json' ).val() );
					console.log( "isValidDefinition(): Scheduling reload" );
					setTimeout( function () {
						var parsedJson = JSON.parse( $( '#json' ).val() );
						getDefinitionSuccess( parsedJson );
					}, 200 );
				}
				checkForEmptyStrings()
			}

		} catch ( e ) {
			var message = "Failed to parse document: " + e;

			alertify.alert( message );
			return false;
		}
		return true;
	}

	function checkForEmptyStrings() {

		// files may legititmately contins
//		var emptyIndex = $( '#json' ).val().indexOf( '""' );
//		if ( emptyIndex != -1 ) {
//			var message = 'Warning: Found Empty Strings ("") in document. It is highly recommended to switch to text editor to search and remove all instances.  First Instance:\n';
//
//			var blockIndex = $( '#json' ).val().substr( 0, emptyIndex ).lastIndexOf( "{" );
//			// console.log ( "blockIndex: " + blockIndex ) ;
//			if ( blockIndex != -1 )
//				message += $( '#json' ).val().substr( blockIndex, emptyIndex - blockIndex );
//
//			alertify.alert( message );
//		}
	}

	function getDefinitionFromServer( forceUpdate ) {

		console.log( "getDefinitionFromServer(): forceUpdate: ", forceUpdate );

		if ( (!forceUpdate) && $( '#json' ).val() != _LOADING_MESSAGE ) {
			console.log( "Skipping definition get - re using existing" );
			return;
		}
		$( ".loadingLargePanel" )
				.html( "Retrieving capability definition from server" )
				.show();

		// only lagged to display loading icon
		setTimeout( () => {

			$.getJSON( definitionBaseUrl + "/getDefinition", {
				dummy: hostName,
				releasePackage: $packageSelected.val()
			} )

					.done( function ( loadJson ) {
						console.log( "Hiding load message" );
						$( ".loadingLargePanel" ).hide();
						getDefinitionSuccess( loadJson );
					} )

					.fail(
							function ( jqXHR, textStatus, errorThrown ) {

								handleConnectionError( "Retrieving definitionGetSuccess " + hostName, errorThrown );
							} );
		}, 500 );

	}

	var defaultPackageDefinition = {
		"name": "SampleDefaultPackage",
		"architect": "someUser@yourCompany.com",
		"emailNotifications": "support@notConfigured.com"
	};


	function getDefinitionSuccess( capabilityJson ) {

		console.log( "getDefinitionSuccess() _lastTabSelected: " + _lastTabSelected );

		//console.log("getDefinitionSuccess() News: " + JSON.stringify( capabilityJson.clusterDefinitions.dev.settings.newsItems, null, "\t" ) ) ;

		if ( capabilityJson.error != undefined ) {
			var msg = "Unable to retrieve definition due to: \n" 
					+ capabilityJson.error
					+ "\n\nRecommendation: select package by click button in title bar." ;
			alertify.csapWarning( msg ) ;
			return;
			
		}
		if ( capabilityJson.packageDefinition == undefined ) {
			var oldObject = capabilityJson;
			capabilityJson = new Object();
			capabilityJson.packageDefinition = defaultPackageDefinition;
			$.extend( capabilityJson, oldObject );
		}

		if ( capabilityJson.capability != undefined && capabilityJson.capability.releasePackages == undefined )
			capabilityJson.capability.releasePackages = new Array();

		$( '#json' ).val( JSON.stringify( capabilityJson, null, "\t" ) );
		checkForEmptyStrings();


		switch ( _lastTabSelected ) {
			case $("#showRelease").text().trim():
				releaseTab.show( $( '#json' ) );
				break;

			case $("#showTree").text().trim():
				defBrowser.reset( capabilityJson );
				defBrowser.show( capabilityJson, $( '#json' ) );
				scheduleWindowResize();
				break;

			case $("#showLifeForm").text().trim():
				// nothing to do
				break;

			default:
				console.log( "Unexpected tab: " + _lastTabSelected );
		}

	}

	var newName = ""; // handle rename


	function checkDefaultUser() {
		if ( $( "#json" ).val().indexOf( "defaultUser" ) != -1 ) {
			var msg = 'Warning: Multiple instances of string <span class="error">defaultUser</span> found in definition.';
			msg += "Switch to text view and update all references to defaultUser.";
			msg += "Operational Support requires either a person, a document, a wiki, or similar.";
			alertify.alert( msg );
			// window.alert( msg ) ;
		}
	}

	function ciCluster() {

		if ( !isValidDefinition( false ) )
			return;
		// note that host command triggers handling on server side
		if ( $( "#comment" ).val() == defaultComment || $( "#comment" ).val().length < 10 ) {

			alertify.csapWarning(
					'Check in comments are mandatory<br><br>'
					+ 'Validation fails if comment is fewer then 10 characters<br><br> Add comment and try again'
					)

			return;
		}

		if ( $( "#ciPass" ).val().length == 0 ) {

			alertify.csapWarning(
					'Password is required<br><br>'
					+ 'Add Password and try again'
					)
			return;
		}
		var paramObject = {
			scmUserid: $( "#ciUser" ).val(),
			scmPass: $( "#ciPass" ).val(),
			scmBranch: $( "#ciBranch" ).val(),
			comment: $( "#comment" ).val(),
			serviceName: "HostCommand",
			releasePackage: $packageSelected.val(),
			applyButNoCheckin: false,
			hostName: hostName,
			updatedConfig: $( "#json" ).val(),
			isUpdateAll: $( '#ciUpdateAll' ).is( ":checked" )
		};

		executeOnSelectedHosts( "CapabilityCheckIn", paramObject, "Check In Editor Changes and Load cluster" );
	}

	function applyCluster() {

		// note that host command triggers handling on server side
		if ( !isValidDefinition( false ) )
			return;
		var paramObject = {
			scmUserid: $( "#scmUserid" ).val(),
			scmPass: $( "#scmPass" ).val(),
			scmBranch: $( "#scmBranch" ).val(),
			serviceName: "HostCommand",
			applyButNoCheckin: true,
			releasePackage: $packageSelected.val(),
			hostName: hostName,
			updatedConfig: $( "#json" ).val()
		};

		executeOnSelectedHosts( "CapabilityApply", paramObject, "Apply Editor Changes to Cluster" );
	}

	function reloadCluster() {

		// note that host command triggers handling on server side
		var paramObject = {
			scmUserid: $( "#scmUserid" ).val(),
			scmPass: $( "#scmPass" ).val(),
			scmBranch: $( "#scmBranch" ).val(),
			serviceName: "HostCommand",
			hostName: hostName
		};

		executeOnSelectedHosts( "CapabiltyReload", paramObject, "Reload Cluster from SCM" );
	}

	function executeOnSelectedHosts( command, paramObject, desc ) {

		displayResults( "Performing: " + desc + "\n" );
		$( 'body' ).css( 'cursor', 'wait' );

		fileOffset = "-1";
		fromFolder = "//" + command + ".log";

		checkForChangesTimer = setTimeout( function () {
			getChanges();
		}, 2000 );
		$.post( definitionBaseUrl + "/" + command, paramObject )

				.done( function ( results ) {
					haltProgressRefresh();
					setTimeout( function () {
						// letting in progress queryies complete
						displayResults( "Command Completed" );
						displayHostResults( hostName, command, results );
					}, 500 );

				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( hostName + ":" + command, errorThrown );
					haltProgressRefresh();
				} );

	}

	var intervalInSeconds = 5;

	function haltProgressRefresh() {
		clearTimeout( checkForChangesTimer );
		checkForChangesTimer = 0;
	}

	var fileOffset = "-1";
	var fromFolder = "";
	var isLogFile = false;

	function getChanges() {

		clearTimeout( checkForChangesTimer );
		// $('#serviceOps').css("display", "inline-block") ;
		// console.log("Hitting Offset: " + fileOffset) ;
		var requestParms = {
			serviceName: "CsAgent_8011",
			hostName: hostName,
			fromFolder: fromFolder,
			bufferSize: 100 * 1024,
			logFileOffset: fileOffset,
			isLogFile: isLogFile
		};

		$.getJSON( fileUrl + "/getFileChanges", requestParms )

				.done( function ( hostJson ) {
					getChangesSuccess( hostJson );
				} )

				.fail(
						function ( jqXHR, textStatus, errorThrown ) {

							handleConnectionError( "Failed getting status from host file: " + fromFolder, errorThrown );

						} );
	}

	var checkForChangesTimer = null;
	var warnRe = new RegExp( "warning", 'gi' );
	var errorRe = new RegExp( "error", 'gi' );
	var infoRe = new RegExp( "info", 'gi' );
	var debugRe = new RegExp( "debug", 'gi' );

	var winHackRegEx = new RegExp( "\r", 'g' );
	var newLineRegEx = new RegExp( "\n", 'g' );

	function getChangesSuccess( changesJson ) {

		if ( changesJson.error ) {
			console.log( "Failed getting status from host due to:" + changesJson.error );
			console.log( "Retrying..." );
		} else {
			// $("#"+ hostName + "Result").append("<br>peter") ;
			// console.log( JSON.stringify( changesJson ) ) ;
			// console.log("Number of changes :" + changesJson.contents.length);
			for ( var i = 0; i < changesJson.contents.length; i++ ) {
				var fileChanges = changesJson.contents[i];
				var htmlFormated = fileChanges.replace( warnRe, '<span class="warn">WARNING</span>' );
				htmlFormated = htmlFormated.replace( errorRe, '<span class="error">ERROR</span>' );
				htmlFormated = htmlFormated.replace( debugRe, '<span class="debug">DEBUG</span>' );
				htmlFormated = htmlFormated.replace( infoRe, '<span class="info">INFO</span>' );

				// htmlFormated = htmlFormated.replace(winHackRegEx, '') ;
				// htmlFormated = htmlFormated.replace(newLineRegEx, '<br>') ;
				// displayResults( '<span class="chunk">' + htmlFormated +
				// "</span>", true);
				// $("#"+ hostName + "Result").append( '<span class="chunk">' +
				// htmlFormated + "</span>" ) ;
				displayResults( '<span class="chunk">' + htmlFormated + "</span>", true );
			}

			fileOffset = changesJson.newOffset;
			// $("#fileSize").html("File Size:" + changesJson.currLength) ;
		}
		var refreshTimer = 2 * 1000;

		checkForChangesTimer = setTimeout( function () {
			getChanges();
		}, refreshTimer );

	}

	function displayResults( results, append ) {

		// console.log( "displayResults()") ;

		// if (!append) {
		// $("#resultPre").html("");
		// $('body').css('cursor', 'default');
		// }
		// $("#resultPre").append(results);
		//	
		// // needed when results is small: ERROR sequence
		// $("#resultPre").scrollTop($("#resultPre")[0].scrollHeight);
		//	
		// // Needed when results are maxed
		// if (!isResultsMinSize()) {
		// $("html, body").animate({
		// scrollTop: $(document).height()
		// }, "fast");
		// }
		// if ( ! isAlertifyResults() )
		// $("#resultsSection").show();
		//	    
		// checkResultsScroll();

		if ( !append ) {
			$( "#resultPre" ).html( "" );
			$( 'body' ).css( 'cursor', 'default' );
		}
		$( "#resultPre" ).append( results );

		showResultsDialog();

	}

	function displayHostResults( host, command, resultsJson ) {

		// console.log( "displayHostResults()") ;
		var results = JSON.stringify( resultsJson, null, "\t" );
		results = results.replace( /\\n/g, "<br />" );

		// console.log( "Results from: " + host + " are\n" + results) ;

		var isDetailsShown = false;
		var style = 'style="display: none; font-size: 0.9em"';
		if ( results.indexOf( "__ERROR" ) != -1 || results.indexOf( "__WARN" ) != -1 ) {
			style = 'style="display: block; font-size: 0.9em"';
			isDetailsShown = true;
		}
		var hostHtml = '\n' + host + ':' + command + ' completed.(<a class="simple" style="display:inline" id="' + host
				+ 'Toggle" href="toggle">Results</a>)';
		hostHtml += '\n<div class="note" ' + style + ' id="' + host + 'Result" > ' + results + '</div>';

		$( "#resultPre" ).append( hostHtml );

		$( '#' + host + 'Toggle' ).click( function () {
			var $resultPanel = $( '#' + host + 'Result' );

			if ( $resultPanel.is( ":visible" ) ) {
				$resultPanel.hide();
				restoreDialog();
			} else {
				$resultPanel.show();
				maximizeDialog();
			}


			return false; // prevents link
		} );
		$( "#resultPre" ).append( "\nNote: Use refresh page to load updated cluster configuration.\n" );

		showResultsDialog();
		if ( !isDetailsShown )
			restoreDialog();

		$( 'body' ).css( 'cursor', 'default' );

		// alertify confirm collides with alert. We put inside to avoid conflict
		checkDefaultUser();

	}

	var resultsDialog = null;

	function maximizeDialog() {
		var targetWidth = $( window ).outerWidth( true ) - 100;
		var targetHeight = $( window ).outerHeight( true ) - 100;
		resultsDialog.resizeTo( targetWidth, targetHeight )
	}
	function restoreDialog() {
		resultsDialog.show();
	}


	function showResultsDialog() {

		// if dialog is active, just updated scroll and return
		if ( resultsDialog != null ) {
			var heightToScroll = $( "#resultPre" )[0].scrollHeight;
			// console.log("Scrolling to bottom: " + heightToScroll) ;
			$( ".ajs-content" ).scrollTop( heightToScroll );
			return;
		}

		resultsDialog = alertify.alert( '<div id="resultsAlertify"></div>' );

		resultsDialog.setting( {
			title: "CSAP Service Commands",
			resizable: true,
			'label': "Close after reviewing output",
			'onok': function () {
				$( "#resultsSection" ).append( $( "#resultPre" ) );
				resultsDialog = null;
			}

		} );

		maximizeDialog();

		$( "#resultsAlertify" ).append( $( "#resultPre" ) );

	}

	function scheduleWindowResize() {
		clearTimeout( resizeTimer );
		resizeTimer = setTimeout( function () {
			windowResize();
		}, 200 );

	}

	var validateTimer = 0;
	function windowResize() {

		console.log( "Resizing window" )

		var $jsonBrowser = $( "#jsonFileBrowser" );
		var $tree = $( "ul.fancytree-container", $jsonBrowser );

		var browserHeight = Math.round( $( window ).outerHeight( true ) - $jsonBrowser.offset().top - 30 );
		console.log( "browserHeight", browserHeight );
		$tree.css( "height", browserHeight );
	}


	var lineOnly = false;

	function validateDefinition( full ) {

		$( ".textWarning" ).empty().hide();

		lineOnly = full;
		$( 'body' ).css( 'cursor', 'wait' );

		var paramObject = {
			releasePackage: $packageSelected.val(),
			updatedConfig: $( "#json" ).val()
		};

		$.post( definitionBaseUrl + "/validateDefinition", paramObject )

				.done( function ( resultsJson ) {
					// displayResults(results);
					validateDefinitionSuccess( resultsJson );
					haltProgressRefresh();
				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( hostName + ": validateDefinition", errorThrown );
					haltProgressRefresh();
				} );

	}

	function validateDefinitionSuccess( resultsJson ) {

		// console.log("Results: " + resultsJson) ;
		$( 'body' ).css( 'cursor', 'default' );
		var containerJQ = jQuery( '<div/>', { } );

		processValidationResults( resultsJson ).appendTo( containerJQ );

		if ( !lineOnly ) {
			alertify.alert( "Application Definition Validation", containerJQ.html() );
		}
	}

	function processValidationResults( parsedJson ) {

		// Clear previous errors from text area
		$( ".errorIcon" ).removeClass( "errorIcon" );

		var resultsContainer = jQuery( '<div/>', {
			id: "parseResults"
		} ).css( "font-size", "1.2em" );


		var head = "Parsing Successful";

		var resultsImage = "../images/correct.jpg";
		var resultsText = "Parsing Successful";


		if ( !parsedJson.success ) {
			resultsImage = "../images/error.jpg";
			resultsText = "Parsing Failed";
		}

		var resultImage = jQuery( '<img/>', {
			src: resultsImage
		} ).css( "height", "1.2em" ).appendTo( resultsContainer );
		resultsContainer.append( resultsText + "<br>" );

		if ( parsedJson.errors && parsedJson.errors.length > 0 ) {

			var errorsObj = jQuery( '<div/>', {
				class: "warning"
			} ).text( "Errors: " ).css( {
				"overflow-y": "auto"
			} );
			var listJQ = jQuery( '<ol/>', {
				class: "error"
			} );
			for ( var i = 0; i < parsedJson.errors.length; i++ ) {

				// 2 scenarios: a parsing error with a line number, and a semantic
				// error with just contents
				$( ".textWarning" ).html( "Found some Errors<br> Run validator to view" ).show();
				var error = parsedJson.errors[i];
				var errorMessage = parsedJson.errors[i];
				if ( error.line ) {
					console.log( "Found error: " + error.line );
					errorMessage = '<span style="font-weight: bold"> Line: ' + error.line + "</span> Message: <br>"
							+ error.message;
					// $(".line" + error.line).addClass("errorIcon");
					$( '.lineno:contains("' + error.line + '")' ).addClass( "errorIcon" );
					$( ".errorIcon" ).qtip( {
						content: {
							title: "Error Information",
							text: errorMessage
						}
					} );
				} else {
					errorMessage = JSON.stringify( error, null, "\t" );
					errorMessage = errorMessage.replace( "__ERROR", "Error" );
				}
				jQuery( '<li/>', {
					class: "error"
				} ).html( errorMessage ).appendTo( listJQ );

			}
			listJQ.appendTo( errorsObj );
			errorsObj.appendTo( resultsContainer );
		} else {
			if ( parsedJson.warnings && parsedJson.warnings.length > 0 ) {

				var errorsObj = jQuery( '<div/>', {
					class: "warning"
				} ).text( "Warnings: " );
				var listJQ = jQuery( '<ol/>', {
					class: "error"
				} );
				for ( var i = 0; i < parsedJson.warnings.length; i++ ) {
					$( ".textWarning" ).html( "Found some Warnings<br> Run validator to view" ).show();
					var noteItem = parsedJson.warnings[i];
					noteItem = noteItem.replace( "__WARN:", "" );
					jQuery( '<li/>', {
						class: "error"
					} ).html( noteItem ).appendTo( listJQ );
				}
				listJQ.appendTo( errorsObj );
				errorsObj.appendTo( resultsContainer );
			}
		}

		resultsContainer.append( "<br>" );

		return resultsContainer;
	}

} );