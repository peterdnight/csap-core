
$( document ).ready( function () {

	CsapCommon.configureCsapAlertify();
	appInit();

	showSection();

	// $("#resultsTextArea" ).show() ;
} );

function showSection() {

	// alert( "toggling" + sectionObj.value ) ;
	// $("section").hide() ;
	$( ".commandSection" ).hide();
	$( ".browserSection" ).hide();

	if ( command != "none" ) {
		$( "#" + command ).show();
	}

	// $("#uploadOp").toggle() ;
	// $("#synOp").toggle() ;
	return true;

}


function showLoadMessage( ) {
	showSection();
	$( "#resultsTextArea" ).html( "Request Submitted" );
	$( "#resultsTextArea" ).show();
}

var tailTemplate = '#!/bin/bash\n\n# modify as needed \n\ntail -f  /home/ssadmin/processing/httpd_8080/logs/access.log | stdbuf -o0 grep -i admin';
var lsTemplate = $( "#scriptText" ).val();

function appInit() {


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

	$( ".pushButton" ).hover(
			function () {
				$( this ).css( "text-decoration", "underline" );
				$( this ).css( 'cursor', 'pointer' );
			}, function () {
		$( this ).css( "text-decoration", "none" );
		$( this ).css( 'cursor', 'default' );
	}
	);
	$( "#resultsTable" ).tablesorter( {
		sortList: [[0, 0]],
		theme: 'csapSummary'
	} );
	$( "#helpButton" ).hover( function () {
		$( "#helpNotes" ).show();
	}, function () {
		$( "#helpNotes" ).hide();
	} );
	showSection();
	
	$("#wrapTextLines").change( function() {
		
		if ( $(this).is(":checked") ) {
			$( "#resultsTextArea" ).css("white-space", "pre-wrap") ;
			$(".outputColumn").css("white-space", "pre-wrap") ;
		} else {
			
			$( "#resultsTextArea" ).css("white-space", "pre") ;
			$(".outputColumn").css("white-space", "pre") ;
		}
	})

	$( "#initRadio" ).prop( 'checked', true );

	if ( command == "script" ) {
		if ( hasContentsParam ) {
			$( "#script" ).show();
		} else {
			loadTemplate();
		}
	}



	if ( command == "logSearch" ) {
		initLogSearch();
	}
	$( "#hostSelection" ).appendTo( $( "#hostButtonTarget" ) );

	$( '#hostSelectButton' ).click( showHostsDialog );

	$( '#templateButton' ).click( showTemplatesDialog );


	if ( $( "#resultPre" ).html() != "null" ) {
		$( "#resultsTextArea" ).show();
	}

	$( "#operationSection" ).show();

	$( window ).resize( function () {
		windowResize();
	} );

	windowResize();

	$( '.fileInSearchFolder' ).click( function () {
		// console.log("search item clicked: " + $(this).text() ) ;
		var relativeFolder = $( "#searchIn" ).val().split( "/" )[0];
		$( "#searchIn" ).val( relativeFolder.trim() + "/" + $( this ).text() );

		$( "#searchIn" ).css( "border-color", "red" );

		if ( $( this ).text().contains( ".gz" ) ) {
			$( "#zipSearch" ).prop( "checked", true )
					.parent().css( "background-color", "yellow" );
		}
	} );

	if ( !isAdmin ) {
		$( "#searchTimeout" ).prop( 'disabled', true );
	}


	$( "#deleteButton" ).click( function () {
		$( '#delete form' ).ajaxSubmit( buildFormOptions() );
		return false;
	} );

	$( "#uploadButton" ).click( function () {
		var requestParms = {
				uploadFilePath: $( "#uploadFileSelect" ).val(),
				extractDir: $("#uploadExtractDir").val(),
				skipExtract: $("#uploadSkipExtract").is(':checked'),
				overwriteTarget: $("#uploadOverwrite").is(':checked')
		}
		console.log( "uploading validation", requestParms );

		if ( !requestParms.uploadFilePath ) {
			alertify.alert( "Select a file to upload" );
			return false;
		}
		
		try {
			$.getJSON(
				"uploadToFsValidate",
				requestParms )

				.done( function ( validateResponse ) {
					
					if ( validateResponse.error ) {
						alertify.alert("Upload Alert", "The following item needs to be corrected:<br/>" + validateResponse.error) ;
					} else {

						$( '#upload form' ).ajaxSubmit( buildFormOptions() );
					}
				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					handleConnectionError( "Retrieving changes for file " + fromFolder, errorThrown );
				} );
			
		} catch ( e ) {
			alertify.alert( message );
		}
		return false;
	} );



	$( "#executeSubmitButton" ).click( function () {

		var formOptions = buildFormOptions();

		// $('#script form').ajaxForm( formOptions );
		// $('#sync form').ajaxForm( formOptions ); 

		$( "#jobIdInput" ).val( $.now() )


		$( "#cancelInput" ).val( "" ); // empty out the cancel flag
		$( "#jobIdInput" ).val( $.now() ); // empty out the cancel flag
		if ( $( "#executeUserid" ).val() == "root" ) {


			var newItemDialog = alertify.confirm( "Validate your content carefully<br><br>In case of errors, submitting root level requests require cases to be opened to recover VM." );

			newItemDialog.setting( {
				title: 'Caution: Root user speciied',
				'labels': {
					ok: 'Execute',
					cancel: 'Cancel Request'
				},
				'onok': function () {
					alertify.success( "Submitting Request" );
					$( '#script form' ).ajaxSubmit( formOptions );

				},
				'oncancel': function () {
					alertify.warning( "Cancelled Request" );
				}

			} );



		} else {
			$( '#script form' ).ajaxSubmit( formOptions );
		}
		return false;
	} );

	$( "#cancelButton" ).click( function () {
		alertify.error( "Cancelling command" );
		$( "#cancelInput" ).val( "cancel" ); // setthe cancel flag
		$( '#script form' ).ajaxSubmit( buildFormOptions() );

		return false;
	} );


	$( "#submitSearchButton" ).click( function () {

		$( "#cancelInput" ).val( "" ); // empty out the cancel flag
		$( "#jobIdInput" ).val( $.now() ); // empty out the cancel flag

		$( '#logSearch form' ).ajaxSubmit( buildFormOptions() );

		return false;
	} );

	$( "#syncSubmitButton" ).click( function () {
		// alert($("#executeUserid").val()) ; 
		if ( $( "#syncUserid" ).val() == "root" ) {

			var newItemDialog = alertify.confirm( "Validate your content carefully<br><br>In case of errors, submitting root level requests require cases to be opened to recover VM." );

			newItemDialog.setting( {
				title: 'Caution: Root user speciied',
				'labels': {
					ok: 'Proceed with synchronize',
					cancel: 'Cancel Request'
				},
				'onok': function () {
					alertify.success( "Submitting Request" );
					$( '#sync form' ).ajaxSubmit( buildFormOptions() );

				},
				'oncancel': function () {
					alertify.warning( "Cancelled Request" );
				}

			} );



		} else {
			$( '#sync form' ).ajaxSubmit( buildFormOptions() );
		}
	} );
	
	allowTabs() ;

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

function showHostsDialog() {

	if ( !alertify.hostsDialog ) {
		isNewDialog = true;
		alertify.dialog( 'hostsDialog', hostDialogFactory, false, 'alert' );
	}

	var targetWidth = Math.round( $( window ).outerWidth( true ) - 100 );
	var maxHeight = Math.round( $( window ).outerHeight( true ) - 100 );


	var dialog = alertify.hostsDialog().show();


//		console.log( "parent inner", $( "#hostDialogContainer" ).parent().innerHeight(),
//				"parent height" + $("#hostDialogContainer").parent().height(),
//				"parent scroll height" + $("#hostDialogContainer").parent()[0].scrollHeight,
//				"inner", $("#hostDialogContainer").innerHeight(),
//				"height" + $( "#hostDialogContainer" ).height() );
		var targetHeight = Math.round( $( "#hostDialogContainer" ).parent()[0].scrollHeight + 150 );
		if ( targetHeight > maxHeight ) {
			targetHeight = maxHeight;
		}
		console.log( "targetHeight:" + targetHeight );
		dialog.resizeTo( targetWidth, targetHeight );


	return false; // prevent link

}


function hostDialogFactory() {

	addHostsToDialog();
	
	$("#serviceHostFilter input").change( function() {
		addHostsToDialog();
	})
	return {
		build: function () {
			this.setContent( this.setContent( $( "#hostDialogContainer" ).show()[0] ) );
			this.setting( {
				'onok': function ( closeEvent ) {
					// console.log( "Submitting Request: "+ JSON.stringify( closeEvent ) );

				}
			} );
		},
		setup: function () {
			return {
				buttons: [
					{
						text: "Close",
						className: alertify.defaults.theme.cancel + " scriptSelectionClose",
						key: 27 // escape key
					}],
				options: {
					title: "Select Host(s) for commands",
					resizable: true,
					movable: false,
					autoReset: false,
					maximizable: false
				}
			};
		}

	};
}


function addHostsToDialog() {


	for ( var clusterName in clusterHostsMap ) {
		if ( clusterName == "all" )
			continue;
		var optionItem = jQuery( '<option/>', {
			value: clusterName,
			text: clusterName
		} );
		$( "#selectHostByCluster" ).append( optionItem );
	}

	$( "#selectHostByCluster" ).sortSelect();
	$( "#selectHostByCluster" ).selectmenu( {
		width: "30em",
		change: function () {

			var selected = $( "#selectHostByCluster" ).val();
			if ( selected == "none" )
				return;
			$( "#selectHostByCluster" ).val( "none" );
			$( "#selectHostByCluster" ).selectmenu( "refresh" );
			var clusterHosts = clusterHostsMap[ selected ];
			for ( var i = 0; i < clusterHosts.length; i++ ) {
				var host = clusterHosts[ i ];
				var id = host + "Check";
				console.log( "selecting: " + id );
				$( "#" + id ).prop( "checked", true ).trigger( "change" );
			}
		}
	} );

	$( "#hostDisplay" ).empty();

	var hostsToInclude=allHostsArray ;
	if ( serviceHostsArray == null || serviceHostsArray.length == 0 ) {
		 $("#serviceHostFilter ").empty() ;
	}
	if ( $("#serviceHostFilter input").is(":checked") ) {
		hostsToInclude=serviceHostsArray ;
	}
			
	for ( var i = 0; i < hostsToInclude.length; i++ ) {

		var host = hostsToInclude[i];
		var id = host + "Check";

		var $checkDiv = jQuery( '<div/>', { class: "hostCustom", title: "Include host in command" } );
		$( "#hostDisplay" ).append( $checkDiv )

		var $hostInput = jQuery( '<input/>', {
			class: "hostCheckbox",
			id: id,
			value: host,
			type: "checkbox"
		} ).appendTo( $checkDiv );

		if ( hostName.contains( host ) ) {
			$hostInput.prop( "checked", true );
			$hostInput.prop( 'disabled', true );
			$checkDiv.attr( "title", "Script host cannot be deselected. Switch to another host if necessary" );
		}

		jQuery( '<label/>', { class: "hostLabel", text: host, for : id } ).appendTo( $checkDiv );


	}



	$( ".hostLabel", $( "input.hostCheckbox:checked" ).parent() )
			.animate( {
				"background-color": "yellow"
			}, 1000 ).fadeOut( "fast" ).fadeIn( "fast" );

	$( "input.hostCheckbox" ).change( function () {
		var highlightColor = $( ".ajs-dialog" ).css( "background-color" );

		if ( $( this ).is( ":checked" ) )
			highlightColor = "yellow";

		// $(".hostLabel", $("input.instanceCheck").parent()).css("background-color", $(".ajs-dialog").css("background-color") ) ;
		$( ".hostLabel", $( this ).parent() ).css( "background-color", highlightColor );

		var numHosts = $( "#hostDisplay input:checked" ).length;
		$( "#hostSelectCount" ).text( numHosts );
		if ( numHosts > 1 ) {
			$( "#separateOutput" ).prop( 'checked', false );
		}

	} );

	// Dialog Event binding
	$( '#hostUnCheckAll' ).click( function () {

		$( 'input', "#hostDisplay" ).prop( "checked", false ).trigger( "change" );
		return false; // prevents link
	} );

	$( '#hostCheckAll' ).click( function () {
		$( 'input', "#hostDisplay" ).prop( "checked", true ).trigger( "change" );
	} );

}




function showTemplatesDialog() {

	if ( !alertify.osTemplates ) {
		isNewDialog = true;
		alertify.dialog( 'osTemplates', templateDialogFactory, false, 'alert' );
	}

	var targetWidth = $( window ).outerWidth( true ) - 100;
	var targetHeight = $( window ).outerHeight( true ) - 100;

	$( "#templateTable tbody" ).css( "height", targetHeight - 200 )

	var dialog = alertify.osTemplates().show();
	dialog.resizeTo( targetWidth, targetHeight )

	return false; // prevent link

}

function templateDialogFactory() {


	var templateTable = $( "#templatePrompt tbody" );

	templateTable.empty();

	$( ".osTemplate" ).each(
			function () {

				var nameColumn = jQuery( '<td/>', { text: $( this ).data( "name" ) } );
				var descColumn = jQuery( '<td/>', { text: $( this ).data( "desc" ) } );
				var templateRow = jQuery( '<tr/>', {
					id: "row" + $( this ).attr( "id" ),
					class: "templateRow"
				} ).append( nameColumn ).append( descColumn );

				templateTable.append( templateRow )

			} );

	templateTable.append( $( "#projectScriptTemplates tbody" ).html() )

	$( ".templateRow" ).click( function () {
		template = $( this ).attr( "id" ).substring( 3 );

		$( ".ajs-footer button" ).click();
		loadTemplate();
	} );


	$( ".fileRow" ).click( function () {
		var fullPath = definitionFolder + "/scripts/" + $( this ).data( "template" );

		var inputMap = {
			fromFolder: fullPath,
			hostName: hostName,
			command: "script"
		};
		postAndRemove( "_self", "command", inputMap );

		$( ".scriptSelectionClose" ).click();
	} );

//	$( ".runtimeSelect" ).val( $( this ).val() );




	return {
		build: function () {
			this.setContent( this.setContent( $( "#templatePrompt" ).show()[0] ) );
			this.setting( {
				'onok': function ( closeEvent ) {
					// console.log( "Submitting Request: "+ JSON.stringify( closeEvent ) );

				}
			} );
		},
		setup: function () {
			return {
				buttons: [
					{
						text: "Close",
						className: alertify.defaults.theme.cancel + " scriptSelectionClose",
						key: 27 // escape key
					}],
				options: {
					title: "OS Scripts Selection",
					resizable: true,
					movable: false,
					autoReset: false,
					maximizable: false
				}
			};
		}

	};
}


function getSelectedHosts() {
	var hostsArray = $( 'input.hostCheckbox:checked' ).map( function () {
		return this.value;
	} ).get();

	// default to current host
	if ( hostsArray.length == 0 )
		hostsArray = [hostName];


	console.log( "Hosts Selected: " + JSON.stringify( hostsArray ) );

	return hostsArray;
}


// http://jquery.malsup.com/form/#file-upload : jquery form: http://api.jquery.com/jQuery.ajax/#options
function buildFormOptions() {


	var $progressBar = $( '.bar' );
	var $percentLabel = $( '.percent' );
	var $statusLabel = $( '#status' );

	var targetHosts = getSelectedHosts();


	var actionForm = {
		dataType: "json", // json, xml, script

		data: {
			'hosts': targetHosts
		},
		beforeSend: function () {
			formBefore( $progressBar, $percentLabel, $statusLabel )
		},
		uploadProgress: function ( event, position, total, percentComplete ) {
			var percentVal = percentComplete + '%';
			$progressBar.width( percentVal );
			$percentLabel.html( "Upload Progress: " + percentVal );
		},
		success: function ( jsonData ) {
			var percentVal = '100%';
			$progressBar.width( percentVal );
			$percentLabel.html( "Upload Progress: " + percentVal );
		},
		complete: function ( $xmlHttpRequest ) {
			//console.log( JSON.stringify($xmlHttpRequest) )  ;
			if ( $xmlHttpRequest.status != 200 ) {
				alertify.csapWarning( $xmlHttpRequest.status + " : "
						+ $xmlHttpRequest.statusText + ": Verify your account is a member of the admin group." );
			}
			formComplete( $xmlHttpRequest.responseJSON, $progressBar, $percentLabel, $statusLabel )
		}
	};

	return actionForm;
}

function formBefore( $progressBar, $percent, $status ) {

	if ( $( "#cancelInput" ).val() == "cancel" ) {
		return;
	}
	$( 'body' ).css( 'cursor', 'wait' );

//	    	$("#resultPre").html('<pre class="result">Starting...</pre>');
	$( "#cancelButton" ).show();
	$( ".commandSection" ).hide();

	$( ".browserSection" ).show();
	windowResize();

	$( "#resultsSelectorBody" ).empty();

	addHostResultsSelector( "script" );
	addHostResultsSelector( "progress" );
	selectHostResults( "progress" );

	displayResults( "Starting Command", false );
	$( "#resultsContainer" ).show();

	$status.empty();
	var percentVal = '0%';
	$progressBar.width( percentVal );
	$percent.html( "Upload Progress: " + percentVal );

	// needto update
	fileOffset = "-1";
	fromFolder = FROM_BASE + "_sync.log";
	if ( command == "delete" ) {
		fromFolder = FROM_BASE + "_delete.log";
	} else if ( command == "script" ) {
		var fullName = $( "#scriptName" ).val() + "_" + $( "#jobIdInput" ).val() + ".log";
		fromFolder = SCRIPT_BASE + fullName;

		var targetHosts = getSelectedHosts();
		console.log( "targetHosts.length: " + targetHosts.length );
		if ( targetHosts.length == 1 ) {
			// hook for single host  - progress of command will be shown in progress tab
			fromFolder = XFER_BASE + fullName;
		} else if ( $( "#separateOutput" ).prop( 'checked' ) ) {
			// displayResults("\n\nUse Progress drop down to monitor results\n\n", true) ;
			for ( var i = 0; i < targetHosts.length; i++ ) {
				// var otherHostName = targetHosts[i];

				addHostResultsSelector( targetHosts[i] );
			}
		}
	} else if ( command == "upload" ) {
		fromFolder = FROM_BASE + "_upload.log";
	}

	console.log( "Monitoring log: " + fromFolder );

	registerSelectorClickEvents();
	deploySuccess = false;
	checkForChangesTimer = setTimeout( "getChanges()", 2000 );

}

// http://api.jquery.com/jQuery.ajax/#jqXHR 
function formComplete( commandResponseObject, $progressBar, $percentLabel, $statusLabel ) {

	$( "#cancelInput" ).val( "" );
	$( "#cancelButton" ).hide();
	haltChangesRefresh();

	$( 'body' ).css( 'cursor', 'default' );
	var percentVal = '100%';
	$progressBar.width( percentVal );
	$percentLabel.html( "Upload Progress: " + percentVal );
	// $("#resultsTextArea").val( JSON.stringify(xhr, null, "\t")) ;
	// $("#resultsTextArea").val("Status:" + xhr.statusText) ;
	// status.html(xhr.responseText);
	// $("#resultPre").html( xhr.responseText ) ;


	$( "#resultsSelectorBody" ).empty();
	addHostResultsSelector( "script" );
	// output is now dumped in generated locations.
	// buildRadio(commandOutputJson.scriptHost) ; 


	if ( commandResponseObject.otherHosts != undefined ) {
		if ( !$( "#separateOutput" ).prop( 'checked' ) )
			addHostResultsSelector( "TableOutput" );

		for ( var i = 0; i < commandResponseObject.otherHosts.length; i++ ) {
			var otherHostName = commandResponseObject.otherHosts[i].host;

			if ( $( "#separateOutput" ).prop( 'checked' ) ) {
				addHostResultsSelector( otherHostName );
			}
		}
	}

	addHostResultsSelector( "Unparsed" );
	registerSelectorClickEvents( commandResponseObject );

	var firstSelected = commandResponseObject.scriptHost;
	if ( !$( "#separateOutput" ).prop( 'checked' ) )
		firstSelected = "TableOutput";

	setTimeout( function () {
		selectHostResults( firstSelected );
	}, 500 );



	displayResults( JSON.stringify( commandResponseObject, null, "\t" ), false );
	


}

function initLogSearch() {
	for ( var i = 0; i < 201; i++ ) {

		if ( i > 5 && i % 10 != 0 )
			continue;
		if ( i > 50 && i % 50 != 0 )
			continue;

		var label = i + " Matches";
		if ( i == 0 )
			label = "Unlimited Matches";
		var optionItem = jQuery( '<option/>', {
			value: i,
			text: label
		} );
		$( "#maxMatches" ).append( optionItem );



		label = "Last " + i + " Line(s)";
		if ( i == 0 )
			label = "Entire File";
		var optionItem = jQuery( '<option/>', {
			value: i,
			text: label
		} );
		$( "#tailLines" ).append( optionItem );


		label = i + " Line(s) Before";
		var optionItem = jQuery( '<option/>', {
			value: i,
			text: label
		} );
		$( "#linesBefore" ).append( optionItem );

		label = i + " Line(s) After";
		var optionItem = jQuery( '<option/>', {
			value: i,
			text: label
		} );
		$( "#linesAfter" ).append( optionItem );
	}
	$( "#maxMatches" ).val( 10 );

	$( ".searchLine select" ).selectmenu( { width: "15em" } );


	if ( searchText.length > 0 )
		$( "#searchTarget" ).val( searchText );
	$( "#quickSearch" ).show();
	$( "#quickSearch" ).selectmenu( {
		width: "25em",
		change: function () {

			var curSelect = $( "#quickSearch" ).val();
			console.log( "Template " + curSelect );
			$( "#maxMatches" ).val( 1 ).selectmenu( "refresh" );
			$( "#linesBefore" ).val( 3 ).selectmenu( "refresh" );
			$( "#linesAfter" ).val( 3 ).selectmenu( "refresh" );
			$( "#tailLines" ).val( 0 ).selectmenu( "refresh" );
			$( "#reverseOrder" ).prop( "checked", true );

			switch ( curSelect ) {
				case "last10Lines":
					$( "#searchTarget" ).val( "." );
					$( "#maxMatches" ).val( 0 ).selectmenu( "refresh" );
					$( "#tailLines" ).val( 10 ).selectmenu( "refresh" );
					$( "#reverseOrder" ).prop( "checked", false );
					break;

				case "lastException":
					$( "#searchTarget" ).val( "Exception" );
					break;

				case "last100Exception":
					$( "#maxMatches" ).val( 0 ).selectmenu( "refresh" );
					$( "#tailLines" ).val( 100 ).selectmenu( "refresh" );
					$( "#reverseOrder" ).prop( "checked", false );
					$( "#searchTarget" ).val( "Exception" );
					break;

				case "allException":
					$( "#maxMatches" ).val( 0 ).selectmenu( "refresh" );
					$( "#searchTarget" ).val( "Exception" );
					$( "#reverseOrder" ).prop( "checked", false );
					break;

				case "StartupInfoLogger.logStarted":
					$( "#linesBefore" ).val( 1 ).selectmenu( "refresh" );
					$( "#linesAfter" ).val( 0 ).selectmenu( "refresh" );
					$( "#searchTarget" ).val( curSelect );
					break;

				case "CsapBootConfig.java":
					$( "#linesBefore" ).val( 0 ).selectmenu( "refresh" );
					$( "#linesAfter" ).val( 50 ).selectmenu( "refresh" );
					$( "#searchTarget" ).val( curSelect );
					break;

				case "Server startup":
					$( "#linesBefore" ).val( 0 ).selectmenu( "refresh" );
					$( "#linesAfter" ).val( 0 ).selectmenu( "refresh" );
					$( "#searchTarget" ).val( curSelect );
					break;

				default:
					$( "#searchTarget" ).val( curSelect );
					console.log( "Skipping " + curSelect );
			}

			$( "#submitSearchButton" ).trigger( "click" );
		}

	} );
}


var commonScriptHeader = "#!/bin/bash\n\n" +
		"function printIt() { echo; echo; echo =========; echo == $* ; echo =========; }\n\n" +
		'function checkInstalled() { verify=`which $1`; if [ "$verify" == "" ] ; then printIt error: $1 not found, install using yum -y install; exit; fi   }\n\n'

// |(^\s+)|(\s+$)
var trimRegEx = new RegExp( "\\t\\s+|^\\s+", "g" );

function loadTemplate() {

	alertify.notify( "Loading template: " + template );

	var templateId = template;

	if ( !templateId.contains( "Template" ) ) {
		templateId += "Template";
	}

	var templateContents = $( "#" + templateId ).val();

	//templateContents = templateContents.replace( trimRegEx, '\n' );

	// console.log( "trimRegEx: " + trimRegEx + "   templateContents: " + templateContents );


	var regexp = new RegExp( '_file_', 'g' );
	templateContents = templateContents.replace( regexp, defaultLocation );


	if ( pidParam != "" ) {
		regexp = new RegExp( '_pid_', 'g' );
		templateContents = templateContents.replace( regexp, pidParam );
	}

	if ( serviceNameParam != "" ) {
		regexp = new RegExp( '_serviceName_', 'g' );
		templateContents = templateContents.replace( regexp, serviceNameParam );
	}

	$( "#scriptText" ).val( commonScriptHeader + templateContents );
}

function selectHostResults( name ) {

	$( '#resultsSelector input[type=radio][value="' + name + '"]' ).prop( 'checked', 'checked' );
	$( '#resultsSelector input[value="' + name + '"]' ).parent().parent().trigger( "click" );
}

function addHostResultsSelector( name ) {

	var label = name + " output";

	if ( name == "script" )
		label = "Command Editor";


	$( '#resultsSelectorBody' )
			.append( $( '<tr>', { } )
					.html( '<td class="col1"><input type="radio" name="itemSelect" value="' + name + '">' + label + '</td>' ) );
}

var trimRegExp = new RegExp( ".*TRIM_OUTPUT_BEFORE_THIS.*\n" );
var trimShell = new RegExp( ".*___ ======= STAGING:.*\n" );
var trimNonRoot = new RegExp( ".*== Running as non root user.*\n" );
function trimOutput( textToTrim ) {

	var skipLine = textToTrim.search( trimRegExp );
	if ( skipLine != -1 ) {
		textToTrim = textToTrim.substr( skipLine );
		skipLine = textToTrim.search( new RegExp( "\n" ) );
		// console.log("skipLine" + skipLine + " hi" +  text.search( new RegExp("hi") ) ) ;
		textToTrim = textToTrim.substr( skipLine + 1 );
	}

	skipLine = textToTrim.search( trimShell );
	if ( skipLine != -1 ) {
		textToTrim = textToTrim.substr( skipLine );
		skipLine = textToTrim.search( new RegExp( "\n" ) );
		// console.log("skipLine" + skipLine + " hi" +  text.search( new RegExp("hi") ) ) ;
		textToTrim = textToTrim.substr( skipLine + 1 );
	}

	skipLine = textToTrim.search( trimNonRoot );
	if ( skipLine != -1 ) {
		textToTrim = textToTrim.substr( skipLine );
		skipLine = textToTrim.search( new RegExp( "\n" ) );
		// console.log("skipLine" + skipLine + " hi" +  text.search( new RegExp("hi") ) ) ;
		textToTrim = textToTrim.substr( skipLine + 1 );
	}

	return textToTrim;
}

function registerSelectorClickEvents( commandResponseObject ) {

	$( '#resultsSelector tr' ).off();
	$( '#resultsSelector tr' ).hover(
			function () {
				$( this ).css( "text-decoration", "underline" );
				$( this ).css( 'cursor', 'pointer' );
			}, function () {
		$( this ).css( "text-decoration", "none" );
		$( this ).css( 'cursor', 'default' );
	}
	);
	$( '#resultsSelector tr input' ).off();
	$( '#resultsSelector tr input' ).click( function () {
		// console.log("got click") ;
		var jq = $( this );
		setTimeout( function () {
			jq.prop( 'checked', 'checked' );
		}, 500 );
	} );


	$( '#resultsSelector tr' ).click( function () {

		$( "#outputButton" ).hide();
		$( ".selected" ).removeClass( "selected" );
		$( this ).addClass( "selected" );
		$( "input", $( this ) ).prop( 'checked', 'checked' );
		//var selected = $("#resultsSelector input[type='radio']:checked").val();
		var selected = $( "input", $( this ) ).attr( 'value' );
		//toggleResultsButton(this);
		//$("scriptText").hide() ;
//		console.log( selected ) ;
		showSection();
		$( ".browserSection" ).show();

		$( "#resultsTextArea" ).show();
		$( "#resultsTable" ).hide();
		if ( selected == "script" ) {
			// nop
		} else if ( selected == "progress" ) {
			$( ".commandSection" ).hide();
			$( "#resultsContainer" ).show();

		} else if ( selected == "TableOutput" || command == "logSearch" ) {

			showOutputInTableFormat( commandResponseObject )

		} else if ( selected == "Unparsed" ) {
			$( ".commandSection" ).hide();
			$( "#resultsContainer" ).show();
			displayResults( JSON.stringify( commandResponseObject, null, "\t" ), false );

		} else {
			showOutputInTextArea( commandResponseObject, selected );
		}
		return false;
	} );
}

function showOutputInTableFormat( commandResponseObject ) {
	$( ".commandSection" ).hide();
	$( "#resultsContainer" ).show();
	$( "#resultsTable" ).show();
	$( "#resultsTextArea" ).hide();
	$( "#resultsTableBody" ).empty();

	for ( var i = 0; i < commandResponseObject.otherHosts.length; i++ ) {

		var output = "";

		if ( commandResponseObject.otherHosts[i].error != undefined ) {
			output = commandResponseObject.otherHosts[i].error;

		} else {
			var jsonOutput = commandResponseObject.scriptOutput;
			if ( commandResponseObject.otherHosts[i].transferResults ) { 
				jsonOutput = commandResponseObject.otherHosts[i].transferResults.scriptResults;
				if ( jsonOutput == undefined ) {
					jsonOutput = commandResponseObject.otherHosts[i].transferResults.coreResults;
				}
	
				if ( jsonOutput == undefined ) {
					jsonOutput = commandResponseObject.otherHosts[i].transferResults;
				}
			} 

			for ( var line = 0; line < jsonOutput.length; line++ ) {

				output += trimOutput( jsonOutput[line] );
			}
		}

		// var grepGroup = new RegExp("__GROUP__", 'g');
		// output = output.replace(grepGroup, '<span class="info">INFO</span>') ;

		if ( command == "logSearch" ) {

			var groups = output.split( "__CSAPDELIM__" );

			for ( var group = 0; group < groups.length; group++ ) {
				var curGroup = groups[group];
				if ( curGroup.indexOf( "grep: unrecognized option" ) != -1 ) {
					curGroup += '<div class="warning"> Contact your admin to upgrade OS. Uncheack the Separate Matches option</div>'
				}

				if ( $( "#searchTarget" ).val() != "*" && $( "#searchTarget" ).val() != "." ) {
					var searchTarget = new RegExp( $( "#searchTarget" ).val(), 'g' );
					curGroup = curGroup.replace( searchTarget, '<span class="matchTarget">' + $( "#searchTarget" ).val() + '</span>' );
				}

				var matchContent = curGroup;
				if ( $( "#reverseOrder" ).is( ":checked" ) ) {
					var matchLines = curGroup.split( "\n" );
					matchContent = "";
					for ( var line = matchLines.length - 1; line >= 0; line-- ) {
						matchContent += matchLines[line] + "\n";
					}
				}
				var hostRow = jQuery( '<tr/>', {
					html: '<td class="hostColumn">' + commandResponseObject.otherHosts[i].host + '</td>' +
							'<td class="outputColumn">' + matchContent + '</td>'
				} )

				buildHostLink( $( ".hostColumn", hostRow ), commandResponseObject.otherHosts[i].host,
						'<div class="matchLabel"> Match: ' + (group + 1) + '</div>' );

				hostRow.appendTo( "#resultsTableBody" );
			}

		} else {

			var hostRow = jQuery( '<tr/>', {
				html: '<td class="hostColumn">' + commandResponseObject.otherHosts[i].host + '</td>' +
						'<td class="outputColumn">' + output + '</td>'
			} )

			buildHostLink( $( ".hostColumn", hostRow ), commandResponseObject.otherHosts[i].host, "" );


			hostRow.appendTo( "#resultsTableBody" );
		}

//				jQuery('<tr/>', {
//					html: '<td class="hostColumn">' + commandOutputJson.otherHosts[i].host + '</td>' +
//							'<td class="outputColumn">' + output + '</td>'
//				}).appendTo("#resultsTableBody") ;

	}

	$( "#resultsTable" ).trigger( "update" );
	windowResize();
}

function showOutputInTextArea( commandResponseObject, hostSelected ) {

	console.log( "showOutputInTextArea, cursor is:" , $( 'body' ).css( 'cursor' ) , " agentHostUrlPattern:" , agentHostUrlPattern );


	if ( $( 'body' ).css( 'cursor' ) != 'default' ) {
		// Launch fileMonitor when command is still running
		alertify.notify( "Tailing results on host: " + hostSelected );

		$( ".commandSection" ).hide();
		$( "#resultsContainer" ).show();

		var fullName = $( "#scriptName" ).val() + "_" + $( "#jobIdInput" ).val() + ".log";
		var inputMap = {
			fileName: XFER_BASE + fullName,
			"u": "1"
		};
		var baseUrl = agentHostUrlPattern.replace( /CSAP_HOST/g, hostSelected );
		
		var urlAction = baseUrl + "/CsAgent/file/FileMonitor";
		postAndRemove( "_blank", urlAction, inputMap );
		return;
	}

	// Update UI for command completed....

	$( "#outputButton" ).show();
	$( "#outputButton" ).off();
	$( '#outputButton' ).click( function () {
		alertify.notify( "Showing output on host: " + hostSelected );

		var fullName = $( "#scriptName" ).val() + "_" + $( "#jobIdInput" ).val() + ".log";
		var inputMap = {
			fromFolder: XFER_BASE + fullName
		};
		var baseUrl = agentHostUrlPattern.replace( /CSAP_HOST/g, hostSelected );
		var urlAction = baseUrl + "/file/downloadFile/xfer_" + $( "#scriptName" ).val() + ".log";
		postAndRemove( "_blank", urlAction, inputMap );
		return false; // prevents link
	} );
	$( ".commandSection" ).hide();
	$( "#resultsContainer" ).show();
	var jsonOutput = commandResponseObject.scriptOutput;
	// need to skip for synv
	// if ( commandOutputJson.scriptHost != hostSelected ) {

	for ( var i = 0; i < commandResponseObject.otherHosts.length; i++ ) {
		var otherHostName = commandResponseObject.otherHosts[i].host;
		if ( otherHostName == hostSelected ) {


			if ( commandResponseObject.otherHosts[i].error != undefined ) {
				jsonOutput = commandResponseObject.otherHosts[i].error;

			} else {

				if ( commandResponseObject.otherHosts[i].transferResults ) { 
					jsonOutput = commandResponseObject.otherHosts[i].transferResults.coreResults;
					if ( commandResponseObject.otherHosts[i].transferResults.scriptResults )
						jsonOutput = commandResponseObject.otherHosts[i].transferResults.scriptResults;
					if ( commandResponseObject.otherHosts[i].transferResults.errors ) {
						jsonOutput = commandResponseObject.otherHosts[i].transferResults.errors;
					}
				} else {
					jsonOutput = commandResponseObject.scriptOutput;
				}
			}
			//jsonOutput = commandOutputJson.otherHosts[i].transferResults.scriptResults;

			break;
		}
	}

	//}

	output = "\n";
	for ( var line = 0; line < jsonOutput.length; line++ ) {
		var text = jsonOutput[line];
		output += trimOutput( jsonOutput[line] );
	}

	var trimTrailingSpacesRegExp = new RegExp( " *\n", "g" );
	var result = output.replace( trimTrailingSpacesRegExp, "\n" )
	// console.log("result: " + result) ;
	displayResults( result );

}

function buildHostLink( $cell, hostName, content ) {
	var baseUrl = agentHostUrlPattern.replace( /CSAP_HOST/g, hostName );
	var linkUrl = baseUrl + "/os/HostDashboard"
	if ( serviceNameParam != "" && command == "logSearch" ) {
		var baseUrl = agentHostUrlPattern.replace( /CSAP_HOST/g, hostName );
		linkUrl = baseUrl + "/file/FileMonitor?isLogFile=true&serviceName="
				+ serviceNameParam + "&hostName=" + hostName;
	}

	var logLink = jQuery( '<a/>', {
		href: linkUrl,
		class: "simple",
		target: "_blank",
		title: "Open in new window",
		html: hostName
	} );

	$cell.html( logLink );
	$cell.append( content );
}

var commandOutputJson = null;

function windowResize() {


	var displayHeight = $( window ).outerHeight( true ) - $( "header" ).outerHeight( true ) - 20;

	if ( ($( "#scriptText" ).is( ":visible" )) && ($( ".lines" ).length == 0) ) {
		$( "#scriptText" ).css( "height", displayHeight - 125 );
		$( "#scriptText" ).css( "width", $( window ).outerWidth( true ) - 130 );
		// console.log("Contructing line numbers");
		$( "#scriptText" ).linedtextarea();
	}

	$( ".commandSection" ).css( "height", displayHeight );
	// $(".commandSection textarea").css("height",  displayHeight-100 ) ;
	var container = $( "#scriptText" ).parent().parent();
	container.css( "height", displayHeight - 125 );
	$( ".lines", container ).css( "height", displayHeight - 125 );
	$( "#sc1 thinriptText" ).css( "height", displayHeight - 130 );

	$( "#resultsTextArea" ).css( "height", displayHeight - 50 );
	
	var leftNavWidth = $("#resultsSelector").outerWidth( true ) + 100; // margins and spaces
	console.log("windowResize() leftNavWidth", leftNavWidth) ;

	if ( $( ".browserSection" ).is( ":visible" ) == true ) {
		$( ".commandSection" ).css( "width", $( window ).outerWidth( true ) - leftNavWidth );
		$( ".browserSection" ).css( "width", 220 );
		$( ".browserSection" ).css( "height", displayHeight );
		$( "#resultsSelectorBody" ).css( "height", displayHeight - 120 );


		container.css( "width", $( window ).outerWidth( true ) - leftNavWidth );
		$( "#scriptText" ).css( "width", $( window ).outerWidth( true ) - (leftNavWidth+80) );

	} else {
		$( ".commandSection" ).css( "width", $( window ).outerWidth( true ) - 100 );


		container.css( "width", $( window ).outerWidth( true ) - 50 );
		$( "#scriptText" ).css( "width", $( window ).outerWidth( true ) - 130 );
	}
	//$("#contents").css("margin-left",  "5px" ) ;
	$( ".outputColumn" ).css( "width", $( window ).outerWidth( true ) - leftNavWidth );
	$( ".outputColumn" ).css( "max-width", $( window ).outerWidth( true ) - leftNavWidth );

	// trigger lined text area to refresh
	$( "#contents" ).scrollTop( $( "#contents" ).scrollTop() + 1 );
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

	$.getJSON(
			"../file/getFileChanges",
			requestParms )

			.done( function ( hostJson ) {
				getChangesSuccess( hostJson );
			} )

			.fail( function ( jqXHR, textStatus, errorThrown ) {

				handleConnectionError( "Retrieving changes for file " + fromFolder, errorThrown );
			} );
}


var checkForChangesTimer = null;
var warnRe = new RegExp( "warning", 'gi' );
var errorRe = new RegExp( "error", 'gi' );
var infoRe = new RegExp( "info", 'gi' );
var debugRe = new RegExp( "debug", 'gi' );

var winHackRegEx = new RegExp( "\r", 'g' );
var newLineRegEx = new RegExp( "\n", 'g' );

function haltChangesRefresh() {
	clearTimeout( checkForChangesTimer );
	checkForChangesTimer = 0;
}

function  getChangesSuccess( changesJson ) {

	if ( changesJson.error ) {
		console.log( "Failed getting status from host due to:" + changesJson.error );
		console.log( "Retrying..." );

	} else {


		for ( var i = 0; i < changesJson.contents.length; i++ ) {
			var fileChanges = changesJson.contents[i];
			displayResults( fileChanges, true );
			checkResultsScroll( true );
		}


		fileOffset = changesJson.newOffset;
		// $("#fileSize").html("File Size:" + changesJson.currLength) ;

	}
	var refreshTimer = 2 * 1000;

	checkForChangesTimer = setTimeout( "getChanges()",
			refreshTimer );


}


function isResultsMinSize() {
//	if ( $("#toggleResultsImage").attr('src').indexOf("maxWindow") != -1 ) {
//		return true;
//	}

	return false;
}

function toggleResultsButton( toggleButton ) {

	// alert( $("#toggleResultsImage").attr('src').indexOf("maxWindow") ) ;
	if ( isResultsMinSize() ) {

		$( "#resultPre" ).css( 'overflow-y', 'visible' );
		$( "#resultPre" ).css( 'height', 'auto' );

		$( "#resultPre pre" ).css( 'overflow-y', 'visible' );
		$( "#resultPre pre" ).css( 'height', 'auto' );

		$( "#toggleResultsImage" ).attr( 'src', '../images/restoreWindow.gif' );

		$( "#mainDisplayArea" ).hide();
	} else {
		$( "#resultPre" ).css( 'height', '150px' );
		$( "#resultPre" ).css( 'overflow-y', 'auto' );


		$( "#resultPre pre" ).css( 'height', '150px' );
		$( "#resultPre pre" ).css( 'overflow-y', 'auto' );


		$( "#mainDisplayArea" ).show();
		$( "#toggleResultsImage" ).attr( 'src', '../images/maxWindow.gif' );
	}
}

function displayResults( results, append ) {

	if ( !append ) {
		$( "#resultsTextArea" ).val( "" );
	}
	//$("#resultPre pre").append(results);
	var testDataToFillOutput = "asdfasdfasdfasdf\ndfasdfsdf\nasdfasdfasdfasdf\ndfasdfsdf\nasdfasdfasdfasdf\ndfasdfsdf\nasdfasdfasdfasdf\ndfasdfsdf\nasdfasdfasdfasdf\ndfasdfsdf\nasdfasdfasdfasdf\ndfasdfsdf\n"
	testDataToFillOutput = "";

	$( "#resultsTextArea" ).val( $( "#resultsTextArea" ).val() + results + testDataToFillOutput );

	checkResultsScroll( append );

	$( "#resultsTextArea" ).show();

}

function checkResultsScroll( append ) {

	// needed when results is small
	// $("#resultPre pre").scrollTop($("#resultPre pre")[0].scrollHeight);

	if ( append )
		$( "#resultsTextArea" ).scrollTop( $( "#resultsTextArea" )[0].scrollHeight );
	else
		$( "#resultsTextArea" ).scrollTop( 0 );

	// Needed when results are maxed
	if ( !isResultsMinSize() ) {
		$( "html, body" ).animate( {
			scrollTop: $( document ).height()
		}, "fast" );
	}
}

