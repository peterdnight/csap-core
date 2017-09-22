// http://requirejs.org/docs/api.html#packages
// Packages are not quite as ez as they appear, review the above
require.config( {
	packages: []
} );



require( [], function ( ) {

	console.log( "\n\n ************** _main: loaded *** \n\n" );

	var _logSince = 0;
	var _showRootMessage = true;
	$( document ).ready( function () {
		initialize();
	} );

	this.initialize = initialize;
	function initialize() {
		appInit();
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
	}



	function appInit() {
		CsapCommon.configureCsapAlertify();

		$( window ).resize( function () {
			windowResize();
		} );
		windowResize();

		$( '#downloadButton' ).click(
				function () {

					var dummyParamForUrl = $( "#logFileSelect option:selected" ).text();
					if ( dummyParamForUrl.indexOf( "\\" ) != -1 ) {
						dummyParamForUrl = dummyParamForUrl.substring( dummyParamForUrl.indexOf( "\\" ) + 1 );
					}
					if ( dummyParamForUrl.indexOf( "/" ) != -1 ) {
						dummyParamForUrl = dummyParamForUrl.substring( dummyParamForUrl.indexOf( "/" ) + 1 );
					}
					console.log( "dummyParamForUrl: " + dummyParamForUrl );

					var inputMap = {
						fromFolder: $( "#logFileSelect" ).val(),
						serviceName: serviceName,
						hostName: hostName
					};
					// alert( $("#logFileSelect option:selected").text() ) ;
					postAndRemove( "_blank", "downloadFile/" + dummyParamForUrl, inputMap );
					return false;
				} );

		$( '#grepButton' ).click(
				function () {
					var inputMap = {
						fromFolder: $( "#logFileSelect" ).val(),
						serviceName: serviceFullName,
						command: "script",
						template: "grep",
						hostName: hostName
					};
					alertify.notify( "Launching script grep for file: " + $( "#logFileSelect" ).val() );
					postAndRemove( "_blank", commandScreen, inputMap );
					return false;
				} );

		$( '#monitorDiv' ).keyup( function ( event ) {
			//alertify.notify("checking search on " + event.keyCode) ;
			if ( event.keyCode == 83 ) {
				//alertify.notify("triggering search on ") ;
				$( '#searchButton' ).trigger( "click" );
			}
		} );

		$( '#searchButton' ).click(
				function () {
					var selText = "";
					if ( window.getSelection ) {
						selText = window.getSelection().toString();
					} else if ( document.selection && document.selection.type != "Control" ) {
						selText = document.selection.createRange().text;
					}

					var inputMap = {
						fromFolder: $( "#logFileSelect option:selected" ).text(),
						serviceName: serviceFullName,
						searchText: selText,
						command: "logSearch",
						hostName: hostName
					};
					alertify.notify( "Launching search for file: " + $( "#logFileSelect" ).val() );
					postAndRemove( "_blank", commandScreen, inputMap );
					return false;
				} );

		$( '#tailButton' ).click(
				function () {
					var inputMap = {
						fromFolder: $( "#logFileSelect" ).val(),
						serviceName: serviceFullName,
						command: "script",
						template: "tail",
						hostName: hostName
					};
					alertify.notify( "Launching script tail for file: " + $( "#logFileSelect" ).val() );
					postAndRemove( "_blank", commandScreen, inputMap );
					return false;
				} );

		$( '#clearButton' ).click(
				function () {
					$( "#monitorDiv" ).empty();
					_logSince = -1;
					return false;
				} );

		$( '#wrapButton' ).change(
				function () {
					if ( $( "#wrapButton" ).prop( 'checked' ) ) {
						$( ".chunk" ).css( 'white-space', 'normal' );
					} else {
						$( ".chunk" ).css( 'white-space', 'nowrap' );
					}
				} );


		$( "#logFileSelect" ).selectmenu( {
			width: "25em",
			change: function () {
				if ( $( this ).val().indexOf( ".gz" ) != -1 || $( this ).val().indexOf( ".zip" ) != -1 ) {
					var inputMap = {
						fromFolder: $( this ).val(),
						hostName: hostName
					};
					var downloadName = $( "#logFileSelect option:selected" ).text();
					var lastIndex = downloadName.indexOf( "/" );
					// hook for windows testing
					if ( lastIndex == -1 )
						lastIndex = downloadName.indexOf( "\\" );
					if ( lastIndex != -1 )
						downloadName = downloadName.substring( lastIndex + 1 );
					console.log( "downloadName " + downloadName );
					postAndRemove( "_blank", "downloadFile/" + downloadName, inputMap );
					return;
				}

				fileOffset = "-1";
				lastSelectedFile = $( '#logFileSelect' ).val();
				$( "#monitorDiv" ).empty();
				getChanges();
				return false; // prevents link
			}
		} );

		$( "#highlightText" ).keypress( function ( e ) {
			if ( e.which == 13 ) {
				fileOffset = "-1";
				$( "#monitorDiv" ).empty();
				getChanges();
			}
		} );



		lastSelectedFile = $( '#logFileSelect' ).val();

		$( '#bufferSelect' ).change( function () {
			fileOffset = "-1";
			$( "#monitorDiv" ).empty();
			getChanges();
			return false; // prevents link
		} );

		$( '#refreshSelect' ).change( function () {
			getChanges();
			return false; // prevents link
		} );

		getChanges();
	}


	function windowResize() {

		var displayHeight = $( window ).outerHeight( true ) - $( "#header" ).outerHeight( true ) - $( ".resourceConfig" ).outerHeight( true );

		$( "#monitorDiv" ).css( "height", displayHeight - 40 );
		$( "#monitorDiv" ).css( "width", $( window ).width() - 40 );


	}

	var lastSelectedFile = "";
	var fileOffset = "-1";
	function getChanges() {

		clearTimeout( checkForChangesTimer );
		//$('#serviceOps').css("display", "inline-block") ;

		var selectedItem = lastSelectedFile;
		var dockerLineCount = $( "#dockerLineCount" ).val();

		var requestParms = {
			fromFolder: selectedItem,
			dockerLineCount: dockerLineCount,
			dockerSince: _logSince,
			bufferSize: $( "#bufferSelect" ).val() * 1024,
			logFileOffset: fileOffset
		};
		if ( serviceFullName != null ) {
			$.extend( requestParms, {
				serviceName: serviceFullName
			} );
		}

		$.getJSON(
				"getFileChanges",
				requestParms )

				.done( function ( hostJson ) {
					getChangesSuccess( hostJson );
				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( "Retrieving changes for file " + $( "#logFileSelect" ).val(), errorThrown );
				} );
	}

	var serviceGraph = null;

	var checkForChangesTimer = null;
	var warnRe = new RegExp( "warn", 'gi' );
	var errorRe = new RegExp( "error", 'gi' );
	var infoRe = new RegExp( "info", 'gi' );
	var debugRe = new RegExp( "debug", 'gi' );

	var winHackRegEx = new RegExp( "\r", 'g' );
	var newLineRegEx = new RegExp( "\n", 'g' );

	function  getChangesSuccess( changesJson ) {

		if ( changesJson.source == "docker" ) {
			$( "#monitorDiv" ).empty();
			$( "#dockerControls" ).show();

			_logSince = changesJson.since;
			//return ;
		}


		if ( changesJson.error || changesJson.contents == undefined ) {
			if ( changesJson.error ) {
				$( "#monitorDiv" ).append( '<span class="error">' + changesJson.error + "</span>" );
				return;
			}

			console.log( "No results found, rescheduling" );
			checkForChangesTimer = setTimeout( "getChanges()",
					2000 );
			return;
		}

		//console.log( JSON.stringify( changesJson ) ) ;

		var highLightText = $( "#highlightText" ).val();
		for ( var i = 0; i < changesJson.contents.length; i++ ) {
			var fileChanges = changesJson.contents[i];
			if ( highLightText == "" ) {
				var htmlFormated = fileChanges.replace( warnRe, '<span class="highlight">WARN</span>' );
				htmlFormated = htmlFormated.replace( errorRe, '<span class="highlight">ERROR</span>' );
				htmlFormated = htmlFormated.replace( debugRe, '<span class="debug">DEBUG</span>' );
				htmlFormated = htmlFormated.replace( infoRe, '<span class="info">INFO</span>' );

				htmlFormated = htmlFormated.replace( winHackRegEx, '' );
				htmlFormated = htmlFormated.replace( newLineRegEx, '<br>' );
			} else {

				var htmlFormated = fileChanges.replace( newLineRegEx, '<br>' );
				var highRegex = new RegExp( highLightText, 'g' );
				htmlFormated = htmlFormated.replace( highRegex, '<span class="highlight">$&</span>' );
			}



			$( "#monitorDiv" ).append( '<span class="chunk">' + htmlFormated + "</span>" );
		}

		// we need to continually purge elements out of buffer to avoid OOM. We remove a block at a time to avoid
		// breaking html syntax
		var maxBytes = $( "#bufferSelect" ).val() * 1024;
		// console.log("buffer size: " + $("#monitorDiv").html().length + " max:" +  maxBytes ); 
		while ( $( "#monitorDiv" ).html().length > maxBytes ) {

			var b4 = $( "#monitorDiv" ).html().length;
			$( "#monitorDiv :first" ).remove();
			// console.log("Purged items, size before: " + b4 + " After: " + $("#monitorDiv").html().length) ;
		}

		if ( changesJson.contents.length > 0 && $( "#scrollButton" ).prop( 'checked' ) ) {
			$( "#monitorDiv" ).scrollTop( $( "#monitorDiv" )[0].scrollHeight );
		}

		fileOffset = changesJson.newOffset;
		$( "#fileSize" ).html( "File Size:" + changesJson.currLength );

		var refreshTimer = $( "#refreshSelect" ).val() * 1000;
		;
		if ( changesJson.newOffset != changesJson.currLength ) {
			refreshTimer = 50;
			$( "#fileSize" ).html( "loading: " + changesJson.lastPosition );
		}

		checkForChangesTimer = setTimeout( function () {
			getChanges()
		}, refreshTimer );


	}
} );