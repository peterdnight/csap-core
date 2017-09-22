define( [], function ( ) {


	var $dockerTree;

	var _jsonParseTimer, _windowResizeTimer, _progressTimer, _tailTimer;
	var $mainLoading;
	var $containerControls, $imageControls;

	var $pullImageDialog, $pullInput;

	var CONTAINER_TYPE = "/container/";
	var IMAGE_TYPE = "/image/";

	var $resultsText, $resultsDialog;

	// tail and logs
	var _logSince = 0, _tailParameters;
	_firstTail = false;
	
	var $quotaDialog ;

	console.log( "Module loaded" );

	var _initComplete = false;

	var _currentImageNode, _currentContainerNode;

	return {

		initialize: function ( dockerTree ) {

			$mainLoading = $( "#initialMessage" );

			$resultsDialog = $( "#resultsDialog" );
			$resultsText = $( ".dockerResults", $resultsDialog );

			$( "#clearLogs" ).click( function () {
				console.log( "clearing" );
				_logSince = -1;
				$resultsText.text( "" );
			} );

			$dockerTree = dockerTree;
			if ( !_initComplete )
				initialize();
		},

		showLoading: function ( isShow ) {
			if ( isShow )
				$mainLoading.show();
			else
				$mainLoading.hide();
		},

		refreshFolder: function ( location ) {

			refreshFolder( location );
		},

		showResultsDialog: function ( operation, commandResults, commandUrl ) {

			showResultsDialog( operation, commandResults, commandUrl );
		},

		buildTreeSummary: function ( loadResponse ) {

			buildTreeSummary( loadResponse );

		},

		showPullImagePrompt: function (  ) {
			showPullImagePrompt(  );
		},

		adminOperation: function ( type, selectedNode, operation, skipPrompt ) {
			adminOperation( type, selectedNode, operation, skipPrompt );
		},

		setCurrentImage: function ( node, nodeSelected ) {
			_currentImageNode = node;
			$( ".lastImage", $dockerTree ).removeClass( "lastImage" );
			nodeSelected.addClass( "lastImage" );
			$( ".lastImage", $dockerTree ).append( $imageControls );

		},

		getCurrentImage: function (  ) {
			return _currentImageNode;
		},

		setCurrentContainer: function ( node, nodeSelected ) {
			_currentContainerNode = node;

			$( ".lastContainer", $dockerTree ).removeClass( "lastContainer" );
			nodeSelected.addClass( "lastContainer" );
			$( ".lastContainer", $dockerTree ).append( $containerControls );
		},

		restoreControls: function (  ) {
			restoreTemplate( $containerControls );
			restoreTemplate( $imageControls );
		}


	}

	function initialize() {
		_initComplete = true;
		console.log( "Initializing" );


		$( window ).resize( function () {
			windowResize();
		} );

		$containerControls = $( "#containerControls" );

		$( "select", $containerControls ).change( function () {

			adminOperation(
					CONTAINER_TYPE,
					_currentContainerNode,
					$( "select", $containerControls ).val() )

			// reset to perform next
			$( "select", $containerControls )[0].selectedIndex = 0;
		} );


		$( "button", $containerControls ).click( function () {
			adminOperation(
					CONTAINER_TYPE,
					_currentContainerNode,
					$( this ).data( "command" ) );
		} );


		$imageControls = $( "#imageControls" );

		$( "select", $imageControls ).change( function () {

			var operation = "";
			adminOperation(
					IMAGE_TYPE,
					_currentImageNode,
					$( "select", $imageControls ).val() )

			// reset to perform next
			$( "select", $imageControls )[0].selectedIndex = 0;
		} );


		$( "#imageBatch, #imageRemove", $imageControls ).off().click( function () {
			adminOperation(
					IMAGE_TYPE,
					_currentImageNode,
					$( this ).data( "command" ) );
		} );


		$pullImageDialog = $( "#pullImageDialog" );
		$pullInput = $( "#pullName" );
		var $pullSelect = $( "select", $pullImageDialog );

		$pullSelect.selectmenu( {
			width: "10em",
			change: function () {

				var selected = $pullSelect.val();
				console.log( "selected", selected );
				if ( selected == "none" )
					return;
				$pullInput.val( selected );
				$pullSelect.val( "none" );
				$pullSelect.selectmenu( "refresh" );

			}
		} );
		$quotaDialog = $( "#cpuQuotaDialog" );
		$("input", $quotaDialog).keyup( function() {
			this.value = this.value.replace(/[^0-9\.]/g,'');
			var $cpuPeriod = $( "#promptCpuPeriod" );
			var $cpuQuota = $( "#promptCpuQuota" );
		
			var coresUsed = $cpuQuota.val() / $cpuPeriod.val() ;
			
			$("#promptCpuCoresUsed").text(coresUsed.toFixed(1)) ;
		})


	}

	function refreshFolder( location ) {

		var tree = $dockerTree.fancytree( 'getTree' );
		var firstNodeMatch = tree.findFirst( function ( node ) {
			return findByType( node.data, location );
		} );
		firstNodeMatch.setExpanded( false );
		setTimeout( () => {
			firstNodeMatch.setExpanded( true );
		}, 500 );
	}

	function findByType( treeData, type ) {
		//console.log("findByPath", treeData, path) ;
		return treeData.path == "/" && treeData.type == type;
	}
	
	function windowResize() {
		
		clearTimeout( _windowResizeTimer ) ;
		
		_windowResizeTimer = setTimeout( function() {
			
			var maxHeight = Math.round( $( window ).outerHeight( true ) - 300 );
			$resultsText.css("max-height", maxHeight) ;
		}, 100) ;
	}


	function showResultsDialog( operation, commandResults, commandUrl ) {

		windowResize() ;
		$mainLoading.hide();
		if ( commandResults.error ) {
			showErrorDialog( operation, commandResults.error, commandResults.reason );
			return;
		}

		if ( operation != "tail" ) {
			_firstTail = true;
		}


		var $tailButton = $( "#tailLogs" );
		$tailButton.hide();
		$( "#logControls" ).hide();
		console.log()
		if ( commandUrl.contains( "container" ) && (operation != "remove" && operation != "tail") ) {

			var id;
			if ( commandResults.createResponse ) {
				id = commandResults.createResponse.Id;
			}
			$tailButton.off().click( function () {
				$tailButton.hide();
				performContainerTail( 10, id );
			} );
			$tailButton.show();
		}

		var alertsDialog = alertify.alert(
				"Success Operation: " + operation,
				'<div id="alertContainer"></div>' );

		if ( commandResults.plainText ) {
			$resultsText.text( commandResults.plainText );
		} else {
			$resultsText.text( JSON.stringify( commandResults, "\n", "\t" ) );
		}


		$( "#alertContainer" ).append( $resultsDialog );

		alertsDialog.setting( {
//			title: alertHeader.html(),resizable: true,modal: true,movable: false,'label': "Close",
			autoReset: true,
			'onok': function () {

				clearTimeout( _tailTimer );
				restoreTemplate( $resultsDialog );
			}

		} );
		dialogResize();

		if ( operation == "tail" ) {
			$resultsText.scrollTop( $resultsText[0].scrollHeight );
			console.log( "results tail", _currentContainerNode )
			performContainerTail( 2000 );
		} else {

			$( ".dockerResults" ).css(
					"height",
					"auto" );
		}

	}

	function getContainerId() {
		//return _currentContainerNode.containerName ;
		return _currentContainerNode.attributes.Id;
	}

	function performContainerTail( delay, id ) {

		$( ".dockerResults" ).css(
				"height",
				"2000px" );

		clearTimeout( _tailTimer );

		_tailTimer = setTimeout( function () {


			if ( id ) {
				_tailParameters = {
					"id": id,
					"since": _logSince
				};
			} else {
				_tailParameters = {
					"name": _currentContainerNode.containerName,
					"since": _logSince
				};
			}

			$.get( explorerUrl + "/container/tail", _tailParameters )
					.done( function ( commandResults ) {
						// showContainerTail( commandUrl, commandResults );
						updateResultText( commandResults );
						_logSince = commandResults.since;
						performContainerTail( 2000, id );
					} )
					.fail( function ( jqXHR, textStatus, errorThrown ) {
						alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
					} );

		}, delay );



	}

	function updateResultText( result ) {

		console.log( "updateResultText() _firstTail", _firstTail, " result", result );

		var $tailLink = $( "a", $resultsDialog );
		$( "#logControls" ).show();

		$tailLink.attr( "href", explorerUrl + "/container/tail" + result.url );

		//console.log("updateResultText() ", result.plainText.length,   $resultsText.text().length)

		var emptyLogsMessage = "No container logs found - verify start has been issued";

		if ( (_firstTail && result.plainText.length == 0) ||
				emptyLogsMessage == $resultsText.text() ) {
			$resultsText.text( emptyLogsMessage );
		} else if ( result.plainText.length != $resultsText.text().length ) {
			$resultsText.text( result.plainText );
			$resultsText.scrollTop( $resultsText[0].scrollHeight );

		} else if ( result.error ) {

			$resultsText.text( result.error + result.reason );
			$resultsText.scrollTop( $resultsText[0].scrollHeight );

		}
		
		var time = new Date();
		var lastTime = time.getHours() + ":" + time.getMinutes() + ":" + time.getSeconds() ;
		$("#tailRefresh").text(lastTime) ;
			
		_firstTail = false;
	}




	function showErrorDialog( operation, error, reason ) {
		var $description = jQuery( '<div/>', { } );
		var $warn = jQuery( '<div/>', { class: "warning" } );
		$warn.append( error );
		$warn.append( reason );
		$warn.appendTo( $description );
		alertify.alert( "Failed Operation: " + operation, $description.html() );

	}




	function dialogResize() {
//		clearTimeout( _windowResizeTimer );
//		_windowResizeTimer = setTimeout( function () {

		var $dialog = $( ".ajs-content .dockerResults" ).parent();
		//var $contentTextArea = $( "textarea", $dialog )

		var displayHeight = Math.round( $dialog.outerHeight( true ) ) - 100;
		var displayWidth = Math.round( $dialog.outerWidth( true ) ) - 50;
		console.log( "displayHeight", displayHeight );

		if ( displayHeight > 600 ) {
			$( ".ajs-content .dockerResults" ).css( "maxHeight", displayHeight );
		}




//		}, 100 )

	}

	function spanForValue( value, limit ) {
		var $description = jQuery( '<div/>', { } );
		if ( value > limit ) {
			jQuery( '<span/>', {
				class: "warning",
				html: value
			} ).appendTo( $description );
		} else {
			jQuery( '<span/>', {
				class: "normal",
				html: value
			} ).appendTo( $description );
		}

		return $description.html();
	}

	function buildTreeSummary( loadResponse ) {

		var htmlSpacer = '<span style="padding-left: 3em"></span>';

		$( "#cpuTree" ).html(
				"Cpu: " + spanForValue( loadResponse.cpu, 70 ) + "%"
				+ htmlSpacer
				+ " current load: "
				+ spanForValue( loadResponse.cpuLoad, loadResponse.cpuCount )
				+ " on " + loadResponse.cpuCount + " cores" );



		$( "#memoryTree" ).html(
				spanForValue( loadResponse.memory.total, 99 )
				+ ", free: " + spanForValue( loadResponse.memory.free, 99 )
				+ htmlSpacer + 'swap: ' + loadResponse.memory.swapTotal + " swap free: " + loadResponse.memory.swapFree );

		$( "#processTree" ).html( spanForValue( loadResponse.processCount, 200 ) + " active processes" );
		$( "#csapTree" ).html( spanForValue( loadResponse.csapCount, 20 ) + " services" );
		$( "#diskTree" ).html( spanForValue( loadResponse.diskCount, 20 ) + " partitions mounted" );

		if ( loadResponse.docker ) {

			$( "#configTree" ).html( "version: " + spanForValue( loadResponse.docker.version, "zz" ) );
			$( "#containerTree" ).html( spanForValue( loadResponse.docker.containerCount, 10 ) + " total, " + loadResponse.docker.containerRunning + " running" );
			$( "#imageTree" ).html( spanForValue( loadResponse.docker.imageCount, 10 ) + " images" );
			$( "#volumeTree" ).html( spanForValue( loadResponse.docker.volumeCount, 10 ) + " volumes" );
		} else {

			$( "#configTree" ).html( "Not Installed" );
			$( "#containerTree" ).html( "-" );
			$( "#imageTree" ).html( "-" );
			$( "#volumeTree" ).html( "-" );
		}
	}



	function showCpuQuotaPrompt() {

		var title = _currentContainerNode.containerName + " CPU Quota"

		
		var $cpuPeriod = $( "#promptCpuPeriod" );
		var $cpuQuota = $( "#promptCpuQuota" );

		var okFunction = function ( evt, value ) {

			$mainLoading.show();
			restoreTemplate( $quotaDialog )
			alertify.notify( "Updating cpu: " + $( "#promptCpuPeriod" ).val() );
			var paramObject = {
				"name": _currentContainerNode.containerName,
				"periodMs": $cpuPeriod.val(),
				"quotaMs": $cpuQuota.val()
			};
			var commandUrl = explorerUrl + "/container/cpuQuota";

			$.post( commandUrl, paramObject )
					.done( function ( commandResults ) {
						showResultsDialog( "cpuQuota", commandResults, commandUrl );
						refreshFolder( "containers" );

					} )
					.fail( function ( jqXHR, textStatus, errorThrown ) {
						console.log( "Failed command", textStatus, jqXHR );
						if ( jqXHR.status == 403 ) {
							alertify.alert( "Permission Denied: " + jqXHR.status, "Contact your administrator to request permissions" );
						} else {
							alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
						}
					} );
		}
		var cancelFunction = function () {
			restoreTemplate( $quotaDialog )
		}

		alertify.confirm(
				title,
				'<div id="cpuPrompt"></div>',
				okFunction,
				cancelFunction
				);
		$quotaDialog.appendTo( $( '#cpuPrompt' ) );
	}

	function restoreTemplate( $item ) {
		console.log( "restoring", $item.attr( "id" ) );
		$item.appendTo( $( '#jsTemplates' ) );
	}

	function showContainerStopDialog(  ) {

		var containerName = _currentContainerNode.containerName;

		var $stopDialog = $( "#stopContainerDialog" );
		var $killCheckbox = $( "#containerKill" );
		var $stopSeconds = $( "#containerStopSeconds" );


		var okFunction = function () {
			$mainLoading.show();
			restoreTemplate( $stopDialog );
			var commandUrl = explorerUrl + "/container/stop";
			var paramObject = {
				"name": containerName,
				"kill": $killCheckbox.is( ':checked' ),
				"stopSeconds": $stopSeconds.val()
			};
			console.log( "hitting: ", commandUrl, paramObject );
			$.post( commandUrl, paramObject )
					.done( function ( commandResults ) {
						showResultsDialog( "/container/stop", commandResults, commandUrl );
						refreshFolder( "containers" );
					} )
					.fail( function ( jqXHR, textStatus, errorThrown ) {
						$mainLoading.hide();
						alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
					} );
		}

		var cancelFunction = function () {
			alertify.error( 'Canceled' );
			restoreTemplate( $stopDialog );
		}
		//var $description = jQuery( '<div/>', { "id:", ""} );
		alertify.confirm(
				"Stop Container: " + containerName,
				'<div id="stopPrompt"></div>',
				okFunction,
				cancelFunction
				);

		$stopDialog.appendTo( $( '#stopPrompt' ) );

	}

	function showContainerRemoveDialog(  ) {

		var containerName = _currentContainerNode.containerName;

		var $removeDialog = $( "#removeContainerDialog" );
		var $removeForce = $( "#containerRemoveForce" );
		var $removeVolumes = $( "#containerRemoveVolumes" );


		var okFunction = function () {
			$mainLoading.show();
			restoreTemplate( $removeDialog );
			var commandUrl = explorerUrl + "/container/remove";
			var paramObject = {
				"name": containerName,
				"force": $removeForce.is( ':checked' ),
				"removeVolumes": $removeVolumes.is( ':checked' )
			};
			console.log( "hitting: ", commandUrl, paramObject );
			$.post( commandUrl, paramObject )
					.done( function ( commandResults ) {
						showResultsDialog( "/container/remove", commandResults, commandUrl );
						refreshFolder( "containers" );
					} )
					.fail( function ( jqXHR, textStatus, errorThrown ) {
						$mainLoading.hide();
						alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
					} );
		}

		var cancelFunction = function () {
			alertify.error( 'Canceled' );
			restoreTemplate( $removeDialog );
		}
		//var $description = jQuery( '<div/>', { "id:", ""} );
		alertify.confirm(
				"Remove Container: " + containerName,
				'<div id="removePrompt"></div>',
				okFunction,
				cancelFunction
				);

		$removeDialog.appendTo( $( '#removePrompt' ) );

	}
	function showPullImagePrompt(  ) {

		var imageName = "docker.io/hello-world";

		if ( _currentImageNode != null ) {
			imageName = _currentImageNode.imageName;
		}
		;

		$pullInput.val( imageName );


		var okFunction = function () {
			$mainLoading.show();

			var pullName = $pullInput.val();
			if ( !pullName.contains( ":" ) ) {
				pullName += ":latest";
			}
			restoreTemplate( $pullImageDialog );
			var commandUrl = explorerUrl + "/image/pull";
			var paramObject = {
				"name": pullName
			};
			console.log( "hitting: ", commandUrl, paramObject );
			$.post( commandUrl, paramObject )
					.done( function ( commandResults ) {
						showResultsDialog( "/image/pull", commandResults, commandUrl );

						if ( commandResults.monitorProgress ) {
							showPullProgress( 0 );
						}

					} )
					.fail( function ( jqXHR, textStatus, errorThrown ) {

						$mainLoading.hide();
						alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
					} );
		}

		var cancelFunction = function () {
			alertify.error( 'Canceled' );
			restoreTemplate( $pullImageDialog );
		}
		//var $description = jQuery( '<div/>', { "id:", ""} );
		alertify.confirm(
				"Add new image to host using docker pull ...",
				'<div id="pullPrompt"></div>',
				okFunction,
				cancelFunction
				);

		$pullImageDialog.appendTo( $( '#pullPrompt' ) );

	}

	function showPullProgress( offset ) {
		$( ".dockerResults" ).append();
		clearTimeout( _progressTimer );
		_progressTimer = setTimeout( function () {
			var requestParms = {
				"offset": offset
			};

			$.get(
					explorerUrl + "/image/pull/progress",
					requestParms )

					.done( function ( responseText ) {

						if ( offset == 0 ) {
							$( ".dockerResults" ).text( "" );
						}
						$( ".dockerResults" ).append( responseText );
						$( ".dockerResults" ).scrollTop( $( ".dockerResults" )[0].scrollHeight );
						//console.log("resultProgress()", responseText) ; 

						if ( !responseText.contains( "__Complete__" ) ) {
							showPullProgress( $( ".dockerResults" ).text().length );
						} else {
							refreshFolder( "images" );
						}

					} )

					.fail( function ( jqXHR, textStatus, errorThrown ) {

						handleConnectionError( "Retrieving changes for file " + $( "#logFileSelect" ).val(), errorThrown );
					} );
		}, 2000 );
	}

	function adminOperation( type, selectedNode, operation, skipPrompt ) {

		console.log( "operation: ", operation, " node:", selectedNode );

		var name = selectedNode.containerName;
		if ( selectedNode.imageName ) {
			name = selectedNode.imageName;
		}
		var paramObject = {
			"name": name
		};

		var commandUrl = explorerUrl + type + operation;

		var targetItem = selectedNode.imageName;
		var templateName = "dockerImage"
		if ( type != IMAGE_TYPE ) {
			templateName = "dockerContainer"
			// strip leading slash for commands
			targetItem = selectedNode.containerName.substring( 1 );
		}


		if ( !skipPrompt && (operation == "remove" || operation == "stop") ) {
			if ( type == CONTAINER_TYPE && operation == "remove" ) {
				showContainerRemoveDialog();
			} else if ( type == CONTAINER_TYPE && operation == "stop" ) {
				showContainerStopDialog();
			} else {

				showConfirmPrompt( type, selectedNode, operation, targetItem );
			}
			return;
		}

		switch ( operation ) {

			case "create":
				$( "#containerCreate" ).trigger("click") ;
				//showCreateContainerDialog()
				break;


			case "pull":
				showPullImagePrompt()
				break;

			case "cpuQuota":
				showCpuQuotaPrompt()
				break;

			case "batch":


				commandUrl = commandScreen + '?command=script&'
						+ 'template=' + templateName + '&'
						+ 'serviceName=' + targetItem + '&';

				openWindowSafely( commandUrl, "_blank" );
				break;

			case "fileBrowser":
				commandUrl = fileManagerUrl + '?'
						+ 'fromFolder=__docker__' + selectedNode.containerName + '&'

				openWindowSafely( commandUrl, "_blank" );
				break;

			case "info" :
			case "processTree" :
			case "sockets" :

				$.get( commandUrl, paramObject )
						.done( function ( commandResults ) {
							showResultsDialog( operation, commandResults, commandUrl );
						} )
						.fail( function ( jqXHR, textStatus, errorThrown ) {
							alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
						} );
				break;

			case "tail" :
				showResultsDialog( operation, { plainText: "loading..." }, commandUrl );
				_logSince = 0;
				_firstTail = true;
				performContainerTail( 100 );
				break;


			default:

				$mainLoading.show();
				$.post( commandUrl, paramObject )
						.done( function ( commandResults ) {
							showResultsDialog( operation, commandResults, commandUrl );

							if ( type == IMAGE_TYPE && operation != "create" ) {

								refreshFolder( "images" );
							} else {

								refreshFolder( "containers" );
							}

						} )
						.fail( function ( jqXHR, textStatus, errorThrown ) {

							$mainLoading.hide();
							console.log( "Failed command", textStatus, jqXHR );
							if ( jqXHR.status == 403 ) {
								alertify.alert( "Permission Denied: " + jqXHR.status, "Contact your administrator to request permissions" );
							} else {
								alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
							}
						} );
				break;

		}


	}


	function showConfirmPrompt( type, selectedNode, operation, desc ) {

		var title = "Confirmation Required"

		var $description = jQuery( '<div/>', {
			text: "Proceed with " + operation + " of: " + desc
		} );

		var okFunction = function ( evt, value ) {
			adminOperation(
					type, selectedNode, operation, true );
		}
		var cancelFunction = function () {
			alertify.error( 'Canceled' );
		}

		alertify.confirm(
				title,
				$description.html(),
				okFunction,
				cancelFunction
				);
	}

} );