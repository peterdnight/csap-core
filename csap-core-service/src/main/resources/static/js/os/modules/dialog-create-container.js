define( ["explorer-operations"], function ( operations ) {


	var _jsonParseTimer;
	var _targetImage;
	var $dialog;
	console.log( "Module loaded" );

	var _initComplete = false;

	return {
		
		initialize: function() {
			initialize();
		}


	}

	function initialize() {
		_initComplete = true;
		console.log( "Initializing" );
		
		$( "#containerCreate" ).click( function () {
			showDialog(  );
		});
		
		$dialog = $( "#createContainerDialog" );
		

		$( "button", $dialog ).click( function () {
			var $button = $( this );
			var type = $button.data( "type" );
			console.log( "Updating model", type, $( this ) );

			var $targetText = $( "textarea", $button.parent() );
			
			if ( type == "ignore") {
				// do nothing
			} else if ( type == "variable") {
				var envs = JSON.parse( $targetText.val( ) ) ;
				envs.push("your_name=your_value") ;
				console.log("updated", envs) ;
				$targetText.val( JSON.stringify( envs, "\n", "\t" ) );
				
			} else if ( type == "port") {
				var ports = JSON.parse( $targetText.val( ) ) ;
				var p = JSON.parse( getSample( type ) )
				ports.push( p[0] ) ;
				console.log("updated", ports) ;
				$targetText.val( JSON.stringify( ports, "\n", "\t" ) );
				
			} else if ( type == "volume") {
				var volumes = JSON.parse( $targetText.val( ) ) ;
				var p = JSON.parse( getSample( type ) )
				volumes.push( p[0] ) ;
				console.log("updated", volumes) ;
				$targetText.val( JSON.stringify( volumes, "\n", "\t" ) );
				
			} else{
				$targetText.val( getSample( type ) );
			}
		} )

	}

	function getSample( type ) {
		var sample = {
			"variable": [
				"JAVA_HOME=/opt/java",
				"WORKING_DIR=/working"
			],
			"volume": [
				{
					"hostPath": "/opt/java",
					"containerMount": "/java",
					"readOnly": true,
					"sharedUser": true
				}
			],
			"port": [
				{
					"PrivatePort": "8080",
					"PublicPort": "8080"
				}
			],
			"limit": {
				"cpuCoresMax": 2,
				"cpuCoresAssigned": "0-7",
				"memoryInMb": 512,
				"logs": {
					"type": "json-file",
					"max-size": "10m",
					"max-file": "2"
				}, 
				"skipValidation": false,
				"ulimits": [
					{
						"name": "nofile",
						"soft": 500,
						"hard": 500
					},
					{
						"name": "nproc",
						"soft": 200,
						"hard": 200
					}
				]
			}
		}

		return JSON.stringify( sample[ type ], "\n", "\t" );
	}

	function showDialog(  ) {
		// Lazy create
		_targetImage = operations.getCurrentImage();

		$( ".warning", $dialog ).hide();
		if ( !alertify.createContainer ) {

			var containerFactory = function factory() {
				return{
					build: function () {
						// Move content from template
						this.setContent( $dialog.show()[0] );
						this.setting( {
							'onok': function () {
								createContainer(  );
							},
							'oncancel': function () {
								alertify.warning( "Cancelled Request" );
							}
						} );
					},
					setup: function () {
						return {
							buttons: [{ text: "Create", className: alertify.defaults.theme.ok },
								{ text: "Cancel", className: alertify.defaults.theme.cancel, key: 27/* Esc */ }
							],
							options: {
								title: "Create Container:",
								resizable: false, movable: true, maximizable: true
							}
						};
					}

				};
			};
			alertify.dialog( 'createContainer', containerFactory, false, 'confirm' );

//			$( '#createContainerName' ).keypress( function ( e ) {
//				if ( e.which == 13 ) {
//					alertify.closeAll();
//					createContainer();
//				}
//			} );
		}

		var instance = alertify.createContainer().show();

		instance.setting( {
			title: "Create Container using: " + _targetImage.imageName
		} );



		// Populate the fields
		console.log( "_lastImageSelected", _targetImage );
		$( "#createWorkingDirectory" ).val( _targetImage.attributes.Config.WorkingDir );
		$( "#createContainerCommand" ).val(
				getDockerCommandString( _targetImage.attributes.Config.Cmd )
				);

		$( "#createContainerEntry" ).val(
				getDockerCommandString( _targetImage.attributes.ContainerConfig.Entrypoint )
				);


		// pete test
		var portMap = new Array();
		if ( _targetImage.attributes.ContainerConfig.ExposedPorts ) {
			for ( exposedItem in _targetImage.attributes.ContainerConfig.ExposedPorts ) {
				// "80/tcp":
				var port = exposedItem.split( "\/" )[0];
				var binding = {
					"PrivatePort": port,
					"PublicPort": port
				}
				portMap.push( binding );
			}
		}
		$( "#createContainerPorts" ).val( JSON.stringify( portMap, "\n", "\t" ) );



		$( "#createContainerVolumes" ).val("[]" );

		var envVariables = new Array();
		if ( _targetImage.attributes.ContainerConfig.Env ) {
			envVariables = _targetImage.attributes.ContainerConfig.Env ;
		}
		
		$( "#createContainerEnvVariables" ).val( JSON.stringify( envVariables, "\n", "\t" ) );

		$( "#createContainerLimits" ).val( getSample("limit") );

		$( ".jsonCompile" ).off().keydown( verifyJsonChanges );
	}

	function isArray( o ) {
		return Object.prototype.toString.call( o ) == '[object Array]';
	}

	function getDockerCommandString( attribute ) {
		var displayItem = "";
		if ( isArray( attribute ) ) {

			var anyParamsWithSpaces = false;
			attribute.forEach( function ( item ) {
				if ( item.contains( " " ) ) {
					anyParamsWithSpaces = true;
				}
			} );

			if ( anyParamsWithSpaces ) {
				displayItem = JSON.stringify( attribute, "", "  " );
			} else {
				displayItem = attribute.join( " " );
			}
		}
		return displayItem
	}
	function verifyJsonChanges( e ) {
		var $jsonTextArea = $( this );

		// support tab in textarea
		if ( e.keyCode === 9 ) {

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
		} else {
			clearTimeout( _jsonParseTimer );
			_jsonParseTimer = setTimeout( function () {
				validateJsonTextArea( $jsonTextArea );
			}, 2000 )
		}
	}

	function validateJsonTextArea( $jsonTextArea ) {

		try {

			var parsedJson = JSON.parse( $jsonTextArea.val() );
			$jsonTextArea.css( "background-color", "#D5F7DE" );

		} catch ( e ) {
			$jsonTextArea.css( "background-color", "#F2D3D3" );
		}
	}


	function buildJsonArray( entry ) {
		var json = entry;
		if ( entry.length > 0 && !entry.trim().startsWith( "[" ) ) {
			json = JSON.stringify( entry.split( " " ), "", "" );
		}

		return json;
	}

	function createContainer(  ) {

		operations.showLoading( true );
//		if ( $( "#createContainerName" ).val() == "" ) {
//			// alertify.alert("Container name is required") ;
//			$( ".warning", $dialog )
//					.text( "Container name is required" )
//					.show();
//			return false;
//		}

		var commandUrl = explorerUrl + "/container/create";

		var dockerCommand = $( "#createContainerCommand" ).val();
		var dockerEntry = $( "#createContainerEntry" ).val();
		var paramObject = {
			"start": $("#createStart").is(':checked'),
			"usingImage": _targetImage.imageName,
			"name": $( "#createContainerName" ).val(),
			"workingDirectory": $( "#createWorkingDirectory" ).val(),
			"networkMode": $( "#networkMode" ).val(),
			"restartPolicy": $( "#restartPolicy" ).val(),
			"runUser": $( "#runUser" ).val(),
			"command": buildJsonArray( dockerCommand ),
			"entry": buildJsonArray( dockerEntry ),
			"ports": $( "#createContainerPorts" ).val(),
			"volumes": $( "#createContainerVolumes" ).val(),
			"environmentVariables": $( "#createContainerEnvVariables" ).val(),
			"limits": $( "#createContainerLimits" ).val()
		};

		$.post( commandUrl, paramObject )
				.done( function ( commandResults ) {
					operations.showResultsDialog( "/container/create", commandResults, commandUrl );
					operations.refreshFolder( "containers" );

				} )
				.fail( function ( jqXHR, textStatus, errorThrown ) {
					operations.showLoading( false );
					alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" );
				} );

	}


} );
