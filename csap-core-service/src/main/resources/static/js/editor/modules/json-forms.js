define( [], function () {

	console.log( "Module loaded" );

	var _jsonParseTimer = null;
	var _currentDialog = null;
	var ROOT = "ROOT";
	var _updateDefinitionFunction = null;


	var _notifyChangeFunction = null;

	var _updateGlobalDefinitionFunction = null;

	return {

		getRoot: function () {
			return ROOT;
		},

		closeDialog: function () {

			if ( _currentDialog != null ) {
				_currentDialog.destroy();
			}

		},

		// used to alert when changes are completed
		registerForChangeNotification: function ( notifyChangeFunction ) {

			_notifyChangeFunction = notifyChangeFunction;
		},

		registerOperations: function ( updateDefinitionFunction ) {

			_updateDefinitionFunction = updateDefinitionFunction;
			registerOperations();
		},
		areThereErrors: function () {
			var numErrors = $( ".jsonFormsError" ).length;
			if ( numErrors > 0 ) {
				alertify.alert( "Request aborted", "Parsing errors must be corrected prior to further processing" );
				return true
			}
			return false;
		},
		//
		//  need to reload definiton in js editor after updates
		//
		configureUpdateFunction: function ( updateFunction ) {
			_updateGlobalDefinitionFunction = updateFunction;
		},
		runUpdateFunction: function () {
			console.log( "runUpdateFunction() ", _updateGlobalDefinitionFunction );
			if ( _updateGlobalDefinitionFunction != null )
				_updateGlobalDefinitionFunction( true );
		},
		//
		//
		loadValues: function ( dialogId, definitionJson, defaultValues ) {
			loadValues( dialogId, definitionJson, defaultValues );
		},
		getValue: function ( jsonKey, definitionJson ) {
			var defManager = new DefinitionManager( definitionJson, jsonKey, false );
			return defManager.getValue();
		},
		isDialogBuilt: function ( dialogId ) {
			return alertify[ dialogId ];
		},
		showDialog: function ( dialogId, title, editDialogHtml, updateDefinitionFunction ) {

			_updateDefinitionFunction = updateDefinitionFunction;
			showDialog( dialogId, title, editDialogHtml, updateDefinitionFunction );
		},
		//
		updateDefinition: function ( definitionJson, $inputChanged, optionalValue ) {
			return updateDefinition( definitionJson, $inputChanged, optionalValue );
		},
		resizeVisibleEditors: function ( definitionId, dialogContainer, editorPanelId ) {
			resizeVisibleEditors( definitionId, dialogContainer, editorPanelId );
		},
		configureJsonEditors: function ( getDefinitionFunction, dialogContainer, editorPanelId, definitionDomId ) {

			// Adding a delay due to jquery 3 speedups
			setTimeout( () => {
				configureJsonEditors( getDefinitionFunction, dialogContainer, editorPanelId, definitionDomId );
			}, 250 );
		},
		parseAndUpdateJsonEdits: function ( definitionJson, $jsonTextArea, definitionDomId ) {
			return parseAndUpdateJsonEdits( definitionJson, $jsonTextArea, definitionDomId );
		},

		resizeLinedText: function ( $targetTextArea, dialogContainer, editorPanelId ) {
			resizeLinedText( $targetTextArea, dialogContainer, editorPanelId )
		},

		showUpateResponseDialog: function ( resultsTitle, responseMessage, okFunction ) {
			showUpateResponseDialog( resultsTitle, responseMessage, okFunction );
		}
	}

	function showUpateResponseDialog( resultsTitle, responseMessage, okFunction ) {

		// modifying OK function, so we do NOT use the global alert definition

		if ( !alertify.updateResponseDialog ) {
			//define a new dialog
			alertify.dialog( 'updateResponseDialog', function factory() {
				return { };
			}, false, "alert" );
		}

		//launch it.
		alertify.updateResponseDialog( resultsTitle, responseMessage, okFunction );
	}



	function loadValues( dialogId, definitionJson, defaultValues ) {
		console.groupCollapsed( "loadValues() for dialog: ", dialogId );
		//console.log( "definitionJson: ", definitionJson, " defaults: ", defaultValues );

		var undefinedAttributes = new Array();
		$( dialogId + ' [data-path]' ).each( function () {

			var $itemContainer = $( this );
			var jsonKey = $itemContainer.data( "path" ); // (this).data("host")

			var isRemoveNewLines = $itemContainer.data( "removenewlines" );
			var isSort = $itemContainer.data( "sort" );

			//console.log( "Resolving: " + jsonKey );
			var defManager = new DefinitionManager( definitionJson, jsonKey, false );
			var dataValue = defManager.getValue();

			if ( jsonKey == ROOT ) {
				dataValue = definitionJson;
			}
			//var dataValue = getValue( jsonKey, definitionJson );

			console.log( "jsonKey: ", jsonKey, " dataValue: ", dataValue, " type: " + typeof dataValue );

			if ( dataValue != undefined ) {


				if ( isConvertLines( $itemContainer ) ) {
					console.log( "Loading jsonarrayToText", jsonKey )
					var textLines = "";
					for ( var i = 0; i < dataValue.length; i++ ) {
						textLines += dataValue[i] + "\n";
					}
					$itemContainer.val( textLines );
					$itemContainer.css( "white-space", "pre" );

				} else if ( typeof dataValue == 'object' || typeof dataValue == 'boolean' ) {

					if ( isSort ) {

						if ( dataValue.sort ) {
							dataValue = dataValue.sort();
						} else {
							var fields = new Array();
							for ( var field in dataValue )
								fields.push( field );
							fields.sort();
							var sortedObject = new Object();
							for ( var i = 0; i < fields.length; i++ ) {
								sortedObject[ fields[i] ] = dataValue[ fields[i] ];
							}
							dataValue = sortedObject;

						}

					}

					var valAsString = JSON.stringify( dataValue, null, "\t" );
					if ( isRemoveNewLines ) {
						$itemContainer.css( "white-space", "normal" );
						valAsString = JSON.stringify( dataValue, null, " " );
						valAsString = valAsString.replace( /(\r\n|\n|\r)/gm, "  " );
					}
					$itemContainer.val( valAsString );
				} else if ( dataValue != "" ) {
					if ( isRemoveNewLines ) {
						$itemContainer.css( "white-space", "normal" );
					}
					$itemContainer.val( dataValue );
				}
			} else {
				undefinedAttributes.push( jsonKey );


				var defaultDefinitionManager = new DefinitionManager( defaultValues, jsonKey, false );
				var foundValues = defaultDefinitionManager.getValue();
				//console.log( "Defaults: key", jsonKey, foundValues );
				// defaultValues
				if ( foundValues ) {
					//console.log( "Found Default	for key: ", jsonKey );
					$itemContainer.val( "*Sample\n" + JSON.stringify( foundValues, null, "\t" ) );
					$itemContainer.css( "font-style", "italic" );
					$itemContainer.css( "color", "grey" );
				} else {
					console.log( "no Default for key:", jsonKey );
					$itemContainer.val( "" );
				}
			}

		} );

		console.log( "Service does not contain the following elements", undefinedAttributes );
		console.groupEnd();
	}

	function registerOperations() {

		$( ".notifyButton" ).off().click( function () {
			alertify.prompt( "Definition Update Request",
					'Your account does not have infra admin permissions. After modifying the configuration, provide a summary of the change to be submitted for review:',
					"sample: Java Heap has been updated based on last LT.",
					function ( evt, reason ) {
						//console.log( 'reason: ' + reason );
						_updateDefinitionFunction( "notify", null, null, null, reason );
					},
					function () {
						console.log( "canceled" );
					}
			);
		} );

		$( ".addDefButton" ).off().click( function () {
			var isUpdate = !$( "#validateOnlyCheckbox" ).prop( "checked" );
			_updateDefinitionFunction( "add", isUpdate, _updateGlobalDefinitionFunction );
		} );

		$( ".updateDefButton" ).off().click( function () {
			var isUpdate = !$( "#validateOnlyCheckbox" ).prop( "checked" );
			_updateDefinitionFunction( "modify", isUpdate, _updateGlobalDefinitionFunction );
		} );

		$( ".deleteDefButton" ).off().click( function () {
			var isUpdate = !$( "#validateOnlyCheckbox" ).prop( "checked" );
			_updateDefinitionFunction( "delete", isUpdate, _updateGlobalDefinitionFunction );
		} );

		$( ".renameDefButton" ).off().click( function () {

			if ( !$( "#opsNewName" ).is( ':visible' ) ) {
				//$("#opsNewName").show() ;
				// alertify.log("Enter new name for services");
				alertify.prompt( "Renaming Dialog", 'Enter the new name', "updatedName",
						function ( evt, value ) {
							console.log( 'You entered: ' + value );
							var isUpdate = !$( "#validateOnlyCheckbox" ).prop( "checked" );
							_updateDefinitionFunction( "rename", isUpdate, _updateGlobalDefinitionFunction, value );
						},
						function () {
							console.log( "canceled" );
						}
				);
				return;
			}
		} );
		$( ".copyDefButton" ).off().click( function () {

			if ( !$( "#opsNewName" ).is( ':visible' ) ) {
				//$("#opsNewName").show() ;
				// alertify.log("Enter new name for services");
				alertify.prompt( "Copy Dialog", 'Enter the name for the copy', "updatedName",
						function ( evt, value ) {
							console.log( 'You entered: ' + value );
							var isUpdate = !$( "#validateOnlyCheckbox" ).prop( "checked" );
							_updateDefinitionFunction( "copy", isUpdate, _updateGlobalDefinitionFunction, value );
						},
						function () {
							console.log( "canceled" );
						}
				);
				return;
			}
		} );

	}


	function showDialog( dialogId, title, editDialogHtml, updateDefinitionFunction ) {


		var $batchContent = $( "<html/>" ).html( editDialogHtml ).find( '#dialogContents' );
		console.log( "showDialog(): content Length: " + $batchContent.text().length );

		if ( !alertify[ dialogId ] ) {
			//isNewDialog = true;
			_base = alertify.dialog( dialogId, dialogFactory, true, 'alert' );
		}

		_currentDialog = alertify[ dialogId ]( $batchContent.html() );

		sizeTheDialog();
		registerOperations();
		// $( ".ajs-header" ).append( $( "#optionsTitle" ) );
		// $( ".ajs-header" ).append( title );

//		$(".ajs-close")
//				.html("Close")
//				.css("width", "80px")
//				.css("padding", "0px")
//				.css("padding-left", "25px")
//				.css("font-weight", "bold")

	}

	function sizeTheDialog() {


		var targetWidth = $( window ).outerWidth( true ) - 100;
		var targetHeight = $( window ).outerHeight( true ) - 100;
		console.log( `sizeTheDialog() :  height: ${targetHeight} width: ${targetWidth} ` );
		_currentDialog.resizeTo( targetWidth, targetHeight );

	}


	function dialogFactory() {
		return {
			build: function () {
				this.setting( {
					'onok': function ( closeEvent ) {
						console.log( "dialogFactory(): dialog event:  "
								+ JSON.stringify( closeEvent ) );

						if ( closeEvent.button.text == "Update" ) {

							_updateDefinitionFunction( true, _updateGlobalDefinitionFunction );

							return false;
						} else if ( closeEvent.button.text == "Validate" ) {

							_updateDefinitionFunction( false );
							return false;
						} else {

							console.log( "Destroying _currentDialog" );
							_currentDialog.destroy();
						}

					}
				} );
			},
			setup: function () {
				return {
					buttons: [{
							text: "Update",
							className: alertify.defaults.theme.ok
						}, {
							text: "Validate",
							className: alertify.defaults.theme.ok
						},
						{
							text: "Close",
							invokeOnClose: true,
							className: alertify.defaults.theme.cancel,
							key: 27 // escape key
						}],
					options: {
						title: "Definition Editor: ",
						resizable: true,
						movable: false,
						frameless: true,
						autoReset: false,
						maximizable: false
					}
				};
			}

		};
	}



	function resizeLinedText( $targetTextArea, dialogContainer, editorPanelId ) {

		console.log( "resizeLinedText" );

		// lots of race conditions occure if tab is not visible.
		if ( !$targetTextArea.is( ':visible' ) )
			return;

		if ( $( ".lines" ).length == 0 ) {
			console.log( "Contructing line numbers" );
			$targetTextArea.css( "height", "2000px" ); // sets a minimum for numbers
			$targetTextArea.linedtextarea();
			$targetTextArea.keyup( function ( e ) {
				// console.log("textArea changed") ;
//			clearTimeout( validateTimer );
//			validateTimer = setTimeout( function () {
//				validateDefinition( true );
//			}, 2000 );

			} );
		}

		var displayHeight = containerHeight( dialogContainer, editorPanelId );
		//console.log("Dialog height: " + displayHeight) ;

		// Hooke for lined text area which runs against parent containers
		var $linedText = $targetTextArea.parent().parent();
		$linedText.css( "height", displayHeight );
		$( ".lines", $linedText ).css( "height", displayHeight );
		$targetTextArea.css( "height", displayHeight - 10 );
		$linedText.css( "width", containerWidth( dialogContainer, editorPanelId ) );
		$targetTextArea.css( "width", containerWidth( dialogContainer, editorPanelId ) - 100 );

	}


	function DefinitionManager( definitionJson, jsonKey, isCreateMissing ) {

		var definitionJson = definitionJson;
		var jsonKey = jsonKey;
		var isCreateMissing = isCreateMissing;

		var elementToUpdate = null;
		var attributeMatched = null;
		var attributeIndex = null;

		// traverses the specified definition to find the object.
		// support for arrays?
		function initialize() {
			//console.log("definitionJson", definitionJson) ;

			var attributeKeys = jsonKey.split( '.' );
			elementToUpdate = definitionJson;
			attributeIndex = 0;
			for ( var attributeIndex = 0; attributeIndex < attributeKeys.length - 1; attributeIndex++ ) {
				try {

					var currentPath = attributeKeys[attributeIndex];
					//console.log( "processing path: ", currentPath );
					var arrayStart = currentPath.indexOf( "[" );
					var arrayIndex = -1;
					if ( arrayStart > 0 ) {
						//console.log( "Detected array" );
						//elementToUpdate = elementToUpdate[   ];
						arrayIndex = currentPath.substring( arrayStart + 1, currentPath.length - 1 );

						currentPath = currentPath.substring( 0, arrayStart );
						//console.log( "Index found: ", arrayIndex, " from: ", currentPath );

						if ( isCreateMissing && elementToUpdate[ currentPath  ] == undefined ) {
							console.log( "Creating array: ", currentPath );
							elementToUpdate[ currentPath  ] = new Array();
						}
						elementToUpdate = elementToUpdate[ currentPath ];

						// now roll forward to index
						currentPath = arrayIndex;

					}
					// parent does not exist
					if ( isCreateMissing && elementToUpdate[ currentPath  ] == undefined ) {
						console.log( "Creating object: ", currentPath );
						elementToUpdate[ currentPath  ] = { };
					}

					//console.log( "Assigning: ", elementToUpdate[ currentPath ] );
					if ( elementToUpdate )
						elementToUpdate = elementToUpdate[ currentPath ];
//					if ( arrayIndex >= 0 ) {
//						// console.log("Getting index", arrayIndex , " from : ", JSON.stringify(elementToUpdate)) ;
//						elementToUpdate = elementToUpdate[ arrayIndex ];
//					}

				} catch ( e ) {
					var message = "Failed to locate:  " + jsonKey + ", reason: " + e;
					console.log( message, e );
				}
			}

			attributeMatched = attributeKeys[ attributeIndex ];
			//console.log( "DefinitionManager: attributeMatched: ", attributeMatched, " object:", elementToUpdate );

		}
		;

		this.getValue = getValue;
		function getValue() {
			console.log( "DefinitionManager: getValue: ", attributeMatched, " object:", elementToUpdate );
			if ( elementToUpdate )
				return elementToUpdate[ attributeMatched ];

			//console.log("No value found for: ", jsonKey) ;
			return undefined;
		}

		this.updateValue = updateValue;
		function updateValue( newValue ) {
			console.log( "updating: ", newValue );
			elementToUpdate[ attributeMatched ] = newValue;

			if ( _notifyChangeFunction ) {
				_notifyChangeFunction();
			}
		}

		this.deleteValue = deleteValue;
		function deleteValue( ) {
			console.log( "Removing null element: " + attributeIndex );
			delete elementToUpdate[ attributeMatched ];
		}

		initialize();
	}
	;

	function updateDefinition( definitionJson, $inputChanged, optionalValue ) {
		var jsonKey = $inputChanged.data( "path" );

		if ( !jsonKey )
			return;

		var jsonUpdater = new DefinitionManager( definitionJson, jsonKey, true );

		var isRawJson = $inputChanged.data( "json" );
		var isRemoveNewLines = $inputChanged.data( "removenewlines" );

		if ( optionalValue ) {
			// usually for textareas with raw json
			jsonUpdater.updateValue( optionalValue );
		} else {

			if ( $inputChanged.is( "span" ) ) {
				console.log( "simple text substition" );
				jsonUpdater.updateValue( $inputChanged.text() );
			} else {

				try {
					if ( isRawJson ) {

						console.log( "raw json: " + $inputChanged.val() );

						var parsedJson = JSON.parse( $inputChanged.val() );
						jsonUpdater.updateValue( parsedJson );


					} else {
						var newVal = $inputChanged.val();
						if ( isRemoveNewLines ) {
							console.log( "Removing new lines" );
							newVal = newVal.replace( /(\r\n|\n|\r)/gm, " " );
						}
						jsonUpdater.updateValue( newVal );
						if ( newVal.trim() == "" ) {
							jsonUpdater.deleteValue();
						}
					}
					$inputChanged.animate( {
						"background-color": "yellow"
					}, 1000 ).fadeOut( "fast" ).fadeIn( "fast" );

				} catch ( e ) {
					$inputChanged.animate( {
						"background-color": "#F2D3D3"
					}, 1000 ).fadeOut( "fast" ).fadeIn( "fast" );
					console.error( "Failed parsing input", $inputChanged.val(), e );
					alertify.csapWarning( "Parsing Errors found in defintion. Correct before continuing." + e );
				}
			}

		}
		//_serviceJson[ jsonKey ] = $( this ).val(); 
		return;
	}


	function resizeVisibleEditors( definitionId, dialogContainer, editorPanelId ) {

		$( ".group:visible textarea" ).each( function () {

			var $editor = $( this );

			var textHeight = containerHeight( dialogContainer, editorPanelId );
			var currentId = $editor.attr( "id" );
			var isFitSize = $editor.data( "fit" );
			var adjustSize = $editor.data( "adjust" );

			if ( !adjustSize ) {
				adjustSize = 0;
			}

			//console.log( " resizeVisibleEditors() container: " + currentId + " to: " + textHeight );

			if ( isFitSize )
				textHeight = $editor[0].scrollHeight + adjustSize;
			if ( $editor.val().indexOf( "*Sample" ) == 0 ) {
				textHeight = $editor[0].scrollHeight;
			}

			if ( currentId == "monitors" )
				textHeight = "12em";


			if ( currentId != $( definitionId ).attr( "id" ) ) {
				$editor.css( "height", textHeight );
			}

			console.log( `resizeVisibleEditors(): container: ${ $editor.attr( "id" ) } height: ${ $editor.css( "height" )}  width: ${ $editor.css( "width" ) } ` );

		} );
	}

	function configureJsonEditors( getDefinitionFunction, dialogContainer, editorPanelId, definitionDomId ) {

		console.groupCollapsed( "configureJsonEditors(): Updating all textAreas: " + editorPanelId );

		//$( editorPanelId + " textarea" ).css( "width", containerWidth( dialogContainer, editorPanelId ) );
		$( editorPanelId + " textarea" ).each( function () {
			var $editor = $( this );
			console.log( "configureJsonEditors(): Updating textarea width : " + $editor.attr( "id" ) );
			if ( dialogContainer == "fitContent" ) {

				$editor.css( "width", 0 );
				$editor.css( "width", $editor.prop( 'scrollWidth' ) + 100 );
				$editor.css( "height", 0 );
				$editor.css( "height", $editor.prop( 'scrollHeight' ) );
			} else {

				var targetWidth =  containerWidth( dialogContainer, editorPanelId ) ;
				var adjustWidth = $editor.data( "adjustwidth" );
				if ( adjustWidth ) {
					targetWidth = targetWidth - adjustWidth;
				}
				
				var fixedwidth = $editor.data( "fixedwidth" );
				if ( fixedwidth ) {
					targetWidth = fixedwidth;
				}
				
				$editor.css( "width", targetWidth);
			}

			$editor.change( function ( e ) {
				var $editor = $( this );
				console.log( "configureJsonEditors(): Updating textarea: " + $editor.attr( "id" ) );
				clearTimeout( _jsonParseTimer );
				parseAndUpdateJsonEdits( getDefinitionFunction(), $editor, definitionDomId )
			} );
		} );

		console.groupEnd();

		// allow tabs and run Json Parsing checks
		$( editorPanelId + " textarea" ).keydown( function ( e ) {

			console.log( "keydown in : " + $( this ).attr( "id" ) );
			var $editor = $( this );
			// tabs
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
			} else {
				clearTimeout( _jsonParseTimer );
				_jsonParseTimer = setTimeout( function () {
					parseAndUpdateJsonEdits( getDefinitionFunction(), $editor, definitionDomId );
				}, 2000 )
			}
		} );


	}
	function isConvertLines( $textArea ) {
		return $textArea.data( "convert_lines" );
	}

	function parseAndUpdateJsonEdits( definitionJson, $jsonTextArea, definitionDomId ) {
		var isPlainData = $jsonTextArea.data( "plain" );

		if ( isPlainData ) {
			updateDefinition( definitionJson, $jsonTextArea );
			return;
		} else if ( isConvertLines( $jsonTextArea ) ) {
			console.log( "Converting to json arrary", $jsonTextArea.attr( "id" ) );
			var rawText = $jsonTextArea.val();
			var lines = rawText.split( "\n" );
			var jsonLines = new Array();

			for ( var i = 0; i < lines.length; i++ ) {
				jsonLines.push( lines[i] );
			}
			var jsonKey = $jsonTextArea.data( "path" );
			console.log( "parseAndUpdateJsonEdits() updating: " + jsonKey );
			updateDefinition( definitionJson, $jsonTextArea, jsonLines );

			$jsonTextArea.css( "background-color", "#D5F7DE" );
			return;
		}

		try {
			if ( $jsonTextArea.val().indexOf( "*Sample" ) != 0 ) {
				$jsonTextArea.css( "font-style", "normal" );
				$jsonTextArea.css( "color", "black" );
			} else {
				return; // no parsing on sample
			}
			var editorId = $jsonTextArea.attr( "id" );
			console.log( "checkParsing : " + editorId );
			// console.log("Starting parse") ;

			$( ".ui-tabs-active img" ).remove();
			var parsedJson = JSON.parse( $jsonTextArea.val() );

			$jsonTextArea.css( "background-color", "#D5F7DE" );

			if ( editorId == $( definitionDomId ).attr( "id" ) ) {
				console.log( "Root document updated" );
				//definitionJson = parsedJson; // root node
			} else {
				var jsonKey = $jsonTextArea.data( "path" );
				console.log( "parseAndUpdateJsonEdits() updating: " + jsonKey );
				updateDefinition( definitionJson, $jsonTextArea, parsedJson );
				//console.log("parseAndUpdateJsonEdits() replacing: " + JSON.stringify( _settingsJson[ jsonKey ], null, "\t" ))
				//_settingsJson[ jsonKey ] = parsedJson;

			}


			$( ".ui-tabs-active" ).qtip( {
				content: {
					text: "Parsing Successful"
				},
				style: {
					classes: 'qtip-bootstrap'
				},
				position: {
					my: 'top right', // Position my top left...
					at: 'bottom left'
				}
			} );


		} catch ( e ) {
			// console.error( e ) ;
			var message = "Failed to parse document: " + e;
			$jsonTextArea.css( "background-color", "#F2D3D3" );

			var resultsImage = baseUrl + "images/error.jpg";
			var $error = jQuery( '<img/>', {
				class: "jsonFormsError",
				src: resultsImage
			} ).css( "height", "1.2em" );

			// alertify.alert( message );
			$( ".ui-tabs-active" ).append( $error );

			$( ".ui-tabs-active" ).qtip( {
				content: {
					text: message
				},
				style: {
					classes: 'editorqtip qtip-bootstrap '
				},
				position: {
					my: 'top left', // Position my top left...
					at: 'bottom left',
					target: $( ".ui-tabs-nav" )
				}
				//hide: false
			} );
			return false;
		}

		return true;
	}

	function getContainer( editorPanelId ) {
		var $editor = $( editorPanelId ).parent();
		//console.log(`getContainer(): container: ${ $editor.attr( "id") } height: ${ $editor.css( "height")}  width: ${ $editor.css( "width") } `) ;
		return $editor;
	}

	function containerHeight( dialogContainer, editorPanelId ) {

		var containerHeight = getContainer( editorPanelId ).outerHeight( true ) - 150;

		if ( containerHeight < 300 )
			containerHeight = 300;

		if ( dialogContainer == window ) {
			containerHeight = $( window ).outerHeight( true ) - 300;
		}

		return    Math.round( containerHeight );
	}

	function containerWidth( dialogContainer, editorPanelId ) {

		var width = $( window ).outerWidth( true ) - 100;

		if ( dialogContainer != window ) {
			width = getContainer( editorPanelId ).outerWidth( true ) - 50;
		}

		//console.log("containerWidth(): " + width);
		return Math.round( width );

	}


} );

