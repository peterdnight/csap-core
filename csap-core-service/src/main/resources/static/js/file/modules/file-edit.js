// http://requirejs.org/docs/api.html#packages
// Packages are not quite as ez as they appear, review the above
require.config( {
	packages: []
} );



require( [], function ( ) {

	console.log( "\n\n ************** _main: loaded *** \n\n" );
	
	var _showRootMessage = true;
	$( document ).ready( function () {
		initialize();
	} );

	this.initialize = initialize;
	function initialize() {
		
		editorInit();
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
		
		if ( rootFile != null ) {
			$("#chownUserid").val("root") ;
			alertify.alert("Restriced Access Warning", "The host operating system is restricting access to selected file. Options:" +
					"<br/> - use csap shell to change permisssions; or "+
					"<br/> - use csap editor to save modifications - carefully review prior to saving"+
					'<br/><div class="options"> User is set to ROOT.' +
					"<br/>Changing to non-root is permitted - but note some OS files are required to be owned by root.</div>" ) ;
		}
	}

	
	function editorInit() {
	
		var resizeTimer = 0;
		CsapCommon.configureCsapAlertify();
		$( window ).resize( function () {
			clearTimeout( resizeTimer );
			resizeTimer = setTimeout( function () {
				windowResize();
			}, 200 );
	
		} );
	
		windowResize();
	
		allowTabs();
	
		$( "#formSubmitButton" ).click( function () {
			if ( $( "#chownUserid" ).val() == "root" ) {
	
				var newItemDialog = alertify.confirm( "Validate your content carefully<br><br>In case of errors, submitting root level requests require cases to be opened to recover VM." );
	
				newItemDialog.setting( {
					title: 'Caution: Root user speciied',
					'labels': {
						ok: 'Execute',
						cancel: 'Cancel Request'
					},
					'onok': function () {
						alertify.success( "Submitting Request" );
						$( "#editForm" ).submit();
	
					},
					'oncancel': function () {
						alertify.warning( "Cancelled Request" );
					}
	
				} );
	
			} else {
				$( "#editForm" ).submit();
			}
		} );
	
		$( '#sync' ).click( function () {
	
			var trimmedCommand = "sync";
			console.log( "Invoking: " + trimmedCommand );
			// fileCommand(trimmedCommand) ;
			var inputMap = {
				fromFolder: fromFolder,
				serviceName: serviceName,
				hostName: hostName,
				command: trimmedCommand
			};
			postAndRemove( "sync" + fromFolder, commandScreen, inputMap );
	
			return false; // prevents link
		} );
	
	
		if ( hasResults ) {
			console.log( "Found results" );
			var message = "";
			for ( var i = 0; i < saveResult.length; i++ ) {
				message += saveResult[i] + "<br><br>";
			}
			alertify.alert( message );
		}
	
		var message = "Note: Use of editor should be restricted to non-prod hosts during development.<br><br>" +
				"All configuration files should adhere to SCM practices by using SVN, GIT, etc. for production deployments";
		//	alertify.csapWarning(message) ;
	
	}
	
	function allowTabs() {
	
		$( "textarea" ).keydown( function ( e ) {
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
	
	function windowResize() {
	
	
		var displayHeight = $( window ).outerHeight( true ) - $( "header" ).outerHeight( true ) - 20;
	
	
	//	$("#contents").css("height",  displayHeight ) ;
	//	$("#contents").css("width",  $(window).outerWidth( true) - 50 ) ;
	//	$("#contents").css("margin-left",  "5px" ) ;
	
		if ( $( ".lines" ).length == 0 ) {
			$( "#contents" ).css( "height", displayHeight - 5 );
			$( "#contents" ).css( "width", $( window ).outerWidth( true ) - 90 );
			//console.log("Contructing line numbers");
			$( "#contents" ).linedtextarea();
		}
	
		// Hook for lined text area which runs against parent containers
		var container = $( "#contents" ).parent().parent();
		container.css( "height", displayHeight );
		$( ".lines", container ).css( "height", displayHeight );
		$( "#contents" ).css( "height", displayHeight - 5 );
		container.css( "width", $( window ).outerWidth( true ) - 20 );
		$( "#contents" ).css( "width", $( window ).outerWidth( true ) - 90 );
	
		// trigger lined text area to refresh
		$( "#contents" ).scrollTop( $( "#contents" ).scrollTop() + 1 );
	
	}
	
}) ;