define( ["./tree-templates", "tree-json"], function ( treeTemplates, treeJson ) {

	console.log( "Module loaded" );

	var clusterKeys = new Array( "multiVmPartition", "singleVmPartition", "multiVm", "version" );

	return {
		//

		show: function ( capabilityJson ) {

			$( ".loadingLargePanel" ).html( "Loading Definition" ).show();
			setTimeout(() => {
				buildJsonEditorTree( capabilityJson ) ;
				showTreeTab();
			}, 300);
			
		},
		//
		registerToolTips: function (  ) {
			registerToolTips();
		},
		updateTextAreaWithLastOpened: function (  ) {

			updateTextAreaWithLastOpened();
		},
		
		highlightTextEditorRegion: function() {
			highlightTextEditorRegion() ;
		}
	}


	function buildJsonEditorTree( capabilityJson ) {
		console.log("buildJsonEditorTree(): building UI components") ;
		$( '#editor' ).jsonEditor( capabilityJson, {
			change: function () {
				// console.log("editor Updated") ;
				// console.log( "editor Updated: " + JSON.stringify(capabilityJson,
				// null,"\t")) ;
				$( '#json' ).val( JSON.stringify( capabilityJson, null, "\t" ) );
				treeEditor.registerToolTips();
			}
		} );
	}


	function updateTextAreaWithLastOpened() {
		// $('#json').caretTo('"' + currentElementInJson + '" {', true);
		// $('#json').caretTo( $('#json').val().indexOf(currentElementInJson+ )
		// , true);
		var code = $( '#json' ).val();
		var parentIndex = 0;
		if ( currentElementPath != "" ) {
			var parts = currentElementPath.split( '.' );
			for ( var i = 0; i < parts.length; i++ ) {
				// diver = diver[parts[i]];
				// console.log("parts[i] ====== " + parts[i]) ;
				parentIndex = code.indexOf( '"' + parts[i] + '"', parentIndex );
			}

			if ( parentIndex == -1 )
				parentIndex = 0;
		}
		var startPos = code.indexOf( '"' + currentElementInJson + '"', parentIndex );
		if ( currentElementInJson.length == 1 && currentElementPath.indexOf( "metricsPublication" ) != -1 ) {
			// Hook for arrays
			startPos = parentIndex;
		}
		console.log( "updateTextAreaWithLastOpened(): currentElementPath: " + currentElementPath + "    currentElementInJson: " + currentElementInJson );
		// parentIndex: " + parentIndex + " startPos : " + startPos ) ;
		// for (;startPos<code.length ;startPos++) {
		// if ( code.charAt(startPos) == '{' ) break ;
		// }
		// console.log("startPos : " + startPos )
		// $('#json').caretTo(code.indexOf("{", startPos) + 1);

		setTimeout( function () {
			$( '#json' ).caretTo( startPos );
			highlightTextEditorRegion();
		}, 100 );
	}

	function highlightTextEditorRegion() {
		var code = $( '#json' ).val();
		var startPos = $( "#json" ).getCursorPosition();

		// Highlight JSON region bound by {} or [], based on what is first found
		var delimToFind = '{';
		var endDelimToFind = '}';
		for ( ; startPos > 0; startPos++ ) {
			if ( code.charAt( startPos ) == delimToFind )
				break;
			if ( code.charAt( startPos ) == '[' ) {
				delimToFind = '['
				endDelimToFind = ']';
				break;
			}
			// if (code.charAt(startPos) == '"') {
			// delimToFind='"'
			// endDelimToFind='"' ;
			// break;
			// }
		}

		var endPos = startPos + 1;
		var numToFind = 1;
		console.log( "Searching for: " + delimToFind + " endPos: " + endPos + " numToFind: " + numToFind + " End Char: "
				+ code.charAt( endPos ) );
		for ( ; endPos < code.length; endPos++ ) {
			// console.log("Searching for: " + delimToFind + " endPos: " + endPos +
			// " numToFind: " + numToFind + " End Char: " + code.charAt(endPos)) ;
			if ( code.charAt( endPos ) == delimToFind )
				numToFind++;
			if ( code.charAt( endPos ) == endDelimToFind ) {
				if ( numToFind == 1 )
					break;
				else
					numToFind--;
			}
		}
		endPos++;

		$( '#json' ).setSelection( startPos, endPos );

		// The above scrolls in IE, and Firefox, but not chrome. Here is chrome
		// hook.
		if ( navigator.userAgent.search( "Chrome" ) >= 0 ) {
			var scrollHeight = $( '#json' ).prop( 'scrollHeight' ); // height in pixel
			// of the textarea
			// (n_rows*line_height)
			console.log( "scrollHeight: " + scrollHeight );
			var line_ht = $( '#json' ).css( 'line-height' ).replace( 'px', '' ); // height
			// in
			// pixel
			// of
			// each
			// row
			console.log( "line_ht: " + line_ht + " startPos: " + startPos );
			var countLines = 0;
			for ( var countPos = 0; countPos < startPos; countPos++ ) {
				if ( code.charAt( countPos ) == '\n' ) {
					countLines++;
				}
			}
			console.log( "Number of lines b4: " + countLines );
			$( '#json' ).scrollTop( (countLines - 5) * line_ht );
			console.log( "Hack for chrome scroll bug, Scrolled to: " + countLines * line_ht );

		}
	}

	function showTreeTab() {
		
		console.log("showTreeTab(): binding actions") ;

		// $(".pathFilters").remove() ;
		// cluster jvm member edits
		handleMapEdits( "helpMenuItems" );

		registerToolTips();

		refreshJvmOsTree();
		refreshClusterTree();

		// Already showing path
		if ( $( ".pathFilters" ).length > 0 )
			return;

		// traverses a path specified as jvms.CsspSample, or
		// clusterDefinitions.dev.settings.news
		if ( $.urlParam( "path" ) != null ) {

			// console.log("opening path: " + $.urlParam("path")) ;
			var pathArray = $.urlParam( "path" ).split( '.' );

			// hook for scale button
			if ( pathArray[0] == "clusterDefinitions" ) {
				var $cluster = $( '.property.clusterDefinitions' );
				$cluster.prev().trigger( "click" );
				$( ".property." + pathArray[1], $cluster.parent() ).prev().trigger( "click" );

				$clusterName = $( ".property." + pathArray[2], $cluster.parent() );
				$clusterName.prev().trigger( "click" );

				var filter = $clusterName.parent().data( "path" ) + "." + pathArray[2];
				console.log( "Filter: " + filter );

				var $hosts = $( ".property.hosts", $( '[data-path="' + filter + '"]' ) );

				// hosts is found in a number of different configs; so we use parents to open
				$( ".expander", $hosts.parent().parent().parent() ).each( function () {

					console.log( "Opening: " + $( this ).parent().data( "path" ) );
					$( this ).trigger( "click" );

				} );
				return;

			}



			var showItems = false;
			for ( var index = 0; index < pathArray.length; index++ ) {
				console.log( "showTreeTab(): " + pathArray[index] );

				$( '.property.' + pathArray[index] ).prev().trigger( "click" );
				// $('.property.' +
				// pathArray[index]).prev().toggleClass('expanded');
				// hook to hide other items in folder
				if ( showItems ) {
					$( '.property.' + pathArray[index] ).parent().removeClass( "hidden" );

					if ( newName != "" ) {
						$( '.property.' + newName ).parent().removeClass( "hidden" );
					}

					var filterNote = $( '<div class="pathFilters" style="font-style: italic">Other Services are filtered</div>' );
					var removeFilterButton = $( '<a href="#" style="padding-left: 2em" title="Remove Filter"><img src="../images/16x16/deleteFolder.png"></a>' );
					removeFilterButton.click( function () {
						$( '.item[data-path="jvms"]' ).removeClass( "hidden" );
						$( '.item[data-path="osProcesses"]' ).removeClass( "hidden" );
						$( this ).parent().remove();
						return false;
					} );
					filterNote.append( removeFilterButton );
					$( '[data-path^="jvms"] > .property.' + pathArray[index] ).parent().prepend( filterNote );
					$( '[data-path^="osProcesses"] > .property.' + pathArray[index] ).parent().prepend( filterNote );
				}
				if ( pathArray[index] == "jvms" ) {
					// $('.item[data-path="jvms"]').hide() ;
					$( '.item[data-path="jvms"]' ).addClass( "hidden" );
					showItems = true;
				}
				if ( pathArray[index] == "osProcesses" ) {
					$( '.item[data-path="osProcesses"]' ).addClass( "hidden" );
					showItems = true;
				}
			}
		}
	}

	/**
	 * When source JSON object is updated by adding new items, editor graph must be
	 * updated with customizations
	 */

	function refreshClusterTree() {

		handleMapEdits( "launchUrls" );
		handleArrayEdits( "newsItems", "60em" );
		handleArrayEdits( "releasePackages", "20em" );
		handleArrayEdits( "hosts", "14em" );

		handleMapEdits( "jvmPorts" );
		$( 'input.jvmPorts ~ div.array > input.property' ).each( function ( index ) {
			// console.log("$(this).val()" + $(this).val()) ;
			handleArrayEdits( $( this ).val(), "5em" );
		} );
		handleArrayEdits( "newsItems", "60em" );

		handleMapEdits( "version", "clusterDefinitions" );
		handleMapEdits( "multiVm", "clusterDefinitions" );
		handleMapEdits( "singleVmPartition", "clusterDefinitions" );
		handleMapEdits( "multiVmPartition", "clusterDefinitions" );
		handleArrayEdits( "osProcessesList", "10em" );
		handleMapEdits( "clusterDefinitions" );
		$( '.item[data-path$="clusterDefinitions"] > input.property' ).each(
				function ( index ) {
					// console.log("$(this).val()" + $(this).val()) ;
					handleMapEdits( $( this ).val(), "clusterDefinitions" );
				} );

		$( ".loading" ).hide();

		// console.log("Refreshed Cluster Definitions");
	}

	function refreshJvmOsTree() {
		// JVM edits
		$( ".loadingLargePanel" ).html( "Updating Tree" ).show();

		// Run in background to allow UI to display loading message
		setTimeout( function () {
			refreshServiceItems( "jvms" );
		}, 200 );
		setTimeout( function () {
			refreshServiceItems( "osProcesses" );
		}, 300 );

	}

	//
	// Hooks for handling service updates
	//

	function refreshServiceItems( path ) {
		var start = new Date();

		handleMapEdits( path );

		$( '.item[data-path$="' + path + '"] > input.property' ).each( function ( index ) {
			processProperties( $( this ), path )
		} );

		if ( path == "osProcesses" ) {
			$( ".loadingLargePanel" ).hide();

		}

		// alertify.notify("Updated Tree: " + path);
	}

	function processProperties( $property, path ) {

		var serviceJQueryObject = $property;
		// console.log("serviceJQueryObject.val()" +
		// serviceJQueryObject.val()) ;
		var serviceName = serviceJQueryObject.val();

		// if ( serviceName != "CsspSample") return ;
		handleMapEdits( serviceName, path, $property.parent() );

		var verPath = path + "." + serviceName;
		handleMapEdits( "version", verPath );
		$( '.item[data-path$="' + verPath + '.version"] > input.property' ).each(
				function ( index ) {
					var verNumber = $( this ).val();
					handleMapEdits( verNumber, verPath + ".version", $( this ).parent() );
					var lifecyclePath = verPath + ".version." + verNumber;
					$( '.item[data-path$="' + lifecyclePath + '"] > input.property' ).each(
							function ( index ) {
								var lifecycle = $( this ).val();
								handleMapEdits( lifecycle, lifecyclePath, $( this ).parent() );

							} );

				} );

		handleMapEdits( "customMetrics", verPath );
		$( '.item[data-path$="' + verPath + '.customMetrics"] > input.property' ).each(
				function ( index ) {
					var verNumber = $( this ).val();
					handleMapEdits( verNumber, verPath + ".customMetrics", $( this ).parent() );
					var lifecyclePath = verPath + ".customMetrics." + verNumber;
					$( '.item[data-path$="' + lifecyclePath + '"] > input.property' ).each(
							function ( index ) {
								var lifecycle = $( this ).val();
								handleMapEdits( lifecycle, lifecyclePath, $( this ).parent() );

							} );

				} );

		handleMapEdits( "monitors", verPath );

		// register for server drop down
		$( '.item[data-path$="' + verPath + '"] > input.server' ).each( function ( index ) {
			runtimeSelection( $( this ), path );
		} );

	}

	function runtimeSelection( $server, path ) {

		// parent contains both the server
		// label and the value
		$( '.value', $server.parent() ).off( "click" );
		$( '.value', $server.parent() ).click( function () {

			if ( path != "jvms" ) {
				alertify.alert( "Only Jvms can modify runtime selection.", function () {
					setTimeout( function () {
						$( "body" ).focus();
					}, 20 );
				} );

			} else {
				var serverInputObject = $( this );
				message = $( "#serversPrompt" ).html();

				var runtimeDialog = alertify.confirm( '<div id="runtimeDialog"></div>' );

				runtimeDialog.setting( {
					title: "Application Server Selection",
					'labels': {
						ok: 'Update Runtime',
						cancel: 'Cancel'
					},
					'onok': function () {
						$( "#serversPrompt" ).append( $( "#runtimeDialog div" ) );
						serverInputObject.val( $( ".runtimeSelect" ).val() )
								.trigger( "change" );
						alertify.success( "Updated runtime: " + $( ".runtimeSelect" ).val() );
					},
					'oncancel': function () {
						alertify.warning( "Rename cancelled" );
						$( "#serversPrompt" ).append( $( "#runtimeDialog div" ) );
					}

				} );

				$( "#runtimeDialog" ).append( $( "#serversPrompt div" ) );
				$( ".runtimeSelect" ).val( $( this ).val() );

			}

			return false;

		} );

	}

	function renameServiceId( $serviceInputId ) {

		var mapKey = $serviceInputId.val();

		var promptMessage = ""

		var numMatches = $( '[data-path^="clusterDefinitions"] :input[title="' + mapKey + '"]' ).length;
		// console.log("Matches for service: " + mapKey + " count: " + numMatches) ;
		var matches = "";
		if ( numMatches > 0 ) {
			$( '[data-path^="clusterDefinitions"] :input[title="' + mapKey + '"]' ).each( function ( index ) {
				var path = $( this ).parent().data( "path" );
				matches += "<br>" + path;
			} );

			promptMessage += '<div class="warning">You should kill/clean services prior to committing rename to release resources in use</div>';

			promptMessage += "<br> The following references were found and will be updated as well:<br>" + matches;

		}

		var newItemDialog = alertify.confirm( '<div id="popup_message">' + promptMessage + "<br><br> New Name:"
				+ '<input style="width:20em" id="popup_prompt" value="' + $serviceInputId.val() + '"><div>' );


		newItemDialog.setting( {
			title: "Renaming: " + $serviceInputId.val(),
			'labels': {
				ok: 'Rename',
				cancel: 'Cancel'
			},
			'onok': function () {
				var newItemKey = $( "#popup_prompt" ).val();

				if ( $( ".property." + newItemKey ).length > 0 ) {
					alertify.alert( "Error - Operation cannot proceed",
							newItemKey + " already exists. Either delete it or use a different name"
							);
					return;
				}

				var step1Function = function () {
					$serviceInputId.val( newItemKey );
					$serviceInputId.trigger( "change" );
				}

				var step2Function = function () {

					if ( numMatches > 0 ) {
						$( '[data-path^="clusterDefinitions"] :input[title="' + mapKey + '"]' ).each( function ( index ) {
							$( this ).val( newItemKey );
							$( this ).trigger( "change" );
						} );
					}

				}
				var step3Function = function () {

					// console.log( " getting path: " +
					// $serviceInputId.parent().data("path")) ;
					currentElementInJson = newItemKey;
					currentElementPath = $serviceInputId.parent().data( "path" );
					var index = $( '#tabs a[href="#showText"]' ).parent().index();
					$( "#tabs" ).tabs( "option", "active", index );
				}

				// browser refreshes cannot be forced
				alertify.success( 'Updating model.' );
				setTimeout( function () {
					step1Function();
					alertify.success( 'Updating Matches' );
					setTimeout( function () {
						step2Function();
						alertify.success( 'Switching to text view to validate' );
						setTimeout( function () {
							step3Function();
						}, 200 );
					}, 200 );
				}, 300 );

			},
			'oncancel': function () {
				alertify.warning( "Cancelled Rename" );
			}

		} );

	}

	//
	// NOTE: Custom tool tips use the title attribute which are is used by json
	// editor
	// -- tooltips CANNOT be used if field is editable
	//

	function registerToolTips() {

		// disable editing of property fields. Wizards will be used instead
		$( ".property" ).each( function ( index ) {
			configureRenaming( $( this ) )
		} );

		$( ".packageDefinition" ).attr( "data-qtip", "Use for defining release packages" );
		$( ".capability" ).attr( "data-qtip",
				"Capability global settings. Used for setting capability name, default source control, menu items, etc." );
		$( ".clusterDefinitions" ).attr( "data-qtip",
				"Use cluster definition to add/delete/edit lifecycles, versions, hosts and jvm/os assignment to them" );
		$( ".jvms" )
				.attr( "data-qtip",
						"Use jvms to add/delete/edit jvm defintions, including war locations in maven and svn, java heap params, etc." );
		$( ".osProcesses" )
				.attr( "data-qtip",
						"Use osProcesses to add/delete/edit wrapper defintions, including zip locations in maven, log directory, etc." );

		// Add in display of fields under jvms and osProcesses
		// $(".value", $('[data-path^="jvms"][data-path$=".version"]') ).show() ;
		// $(".value", $('[data-path^="osProcesses"][data-path$=".version"]')
		// ).show() ;
		// $('.version').tooltip({content: "Contains the host mappings per
		// version."});
		$( '.version' )
				.each(
						function ( index ) {
							if ( $( this ).parent().data( "path" ).indexOf( "jvms" ) == 0
									|| $( this ).parent().data( "path" ).indexOf( "osProcesses" ) == 0 )
								$( this )
										.attr(
												"data-qtip",
												"Optional attribute over rides for deployments, if none are found then the defaults above are used. This allows different settings for dev versus stage for example." )

						} );

		$( '#treeTab [data-qtip!=""]' ).qtip( {
			content: {
				title: "Description",
				attr: 'data-qtip',
				button: 'Close'
			},
			style: {
				classes: 'qtip-bootstrap'
			},
			position: {
				my: 'bottom left', // Position my top left...
				at: 'top left'
			}
		} );

		// Add some styling for ease of editing -
		$( '.item[data-path$="jvms"] > input.property' ).each( function ( index ) {
			// console.log("$(this).val()" + $(this).val()) ;
			$( this ).parent().addClass( "noteAlt" );
		} );

		$( '.item[data-path$="osProcesses"] > input.property' ).each( function ( index ) {
			// console.log("$(this).val()" + $(this).val()) ;
			$( this ).parent().addClass( "noteAlt" );
		} );

		$( '.item[data-path$="clusterDefinitions"] > input.property' ).each(
				function ( index ) {
					// console.log("$(this).val()" + $(this).val()) ;
					$( "> div", $( this ).parent() ).addClass( "noteAlt" );
				} );

		// 1. set global icons
		$( '.property[title*="ttpd"]' ).addClass( "routerIcon" );
		$( '.property[title*="ispatcher"]' ).addClass( "routerIcon" );
		$( '.property[title*="cheduler"]' ).addClass( "schedulerIcon" );
		$( '.property[title*="Inv"]' ).addClass( "networkIcon" );
		$( '.property[title*="IPP"]' ).addClass( "networkIcon" );
		$( '.property[title*="actory"]' ).addClass( "factoryIcon" );
		$( '.property[title*="ample"]' ).addClass( "sampleIcon" );

		// 2. Now set cluster icons
		$( 'div[data-path="clusterDefinitions"] > input.property' ).addClass( "deskTopIcon" );
		$( 'div[data-path="clusterDefinitions"] > div > input.property' ).addClass( "multiVmIcon" );
		$( 'div[data-path="clusterDefinitions"] > div > input.settings' ).removeClass( "multiVmIcon" );

		// 3. Now set custom partition icon
		// $( "> input.property" ,
		// $('.value[title="factory-64bit-1.0"]').parent().parent().parent().parent()
		// ).addClass("sharedNothingIcon") ;
		// $( ".singleVmParition").addClass("sharedNothingIcon") ;
		$( "input.multiVmPartition" ).addClass( "sharedNothingIcon" );
		$( "> input.property", $( "input.multiVmPartition" ).parent().parent() ).addClass( "sharedNothingIcon" );
		$( "input.singleVmParition" ).addClass( "sharedNothingIcon" );
		$( "> input.property", $( "input.singleVmPartition" ).parent().parent() ).addClass( "sharedNothingIcon" );
		$( "input.singleVmPartition" ).parent().addClass( "sharedNothingIcon" );

		// $(
		// "input.multiVmParition").parent().parent().addClass("sharedNothingIcon")
		// ;
		// $( document ).tooltip();
	}

	function configureRenaming( $property ) {

		// $(this).attr('disabled', true);
		$property.off( "click" );
		$property.click( function () {
//			console.log( $(this).val(), "path: " + $(this).parent().parent().parent().data("path") ) ;
//			if ( $property.tooltip != undefined )
//				$property.tooltip("close") ;

			if ( $( this ).parent().data( "path" ) == "jvms"
					|| $( this ).parent().data( "path" ) == "osProcesses" ) {

				renameServiceId( $( this ) );

			} else if ( $( this ).parent().data( "path" ) == "clusterDefinitions" ||
					($( this ).parent().parent().data( "path" ) == "clusterDefinitions")
					&& ($( this ).val() != "settings") ) {

				alertify.alert( "Caution Advised",
						"Cluster names are optionally used as a source of property files." +
						"Verify that any property folders with cluster name are renamed." );

			} else if ( ($( this ).val() == "version"
					|| $( this ).val() == "multiVmPartition"
					|| $( this ).val() == "singleVmPartition" || $( this ).val() == "multiVm")
					&& ($( this ).parent().parent().parent().data( "path" ) == "clusterDefinitions") ) {

				// configureClusterType( $( this ) );
				alertify.alert("Use lifecycle editor to modify cluster types") ;

			} else {

				var message = '<div class="warning">Warning: Editing field not recommended. Use text editor if certain change is required.</div><br>';
				alertify.alert( "Caution Advised", message );

			}
			// $(this).attr('disabled', true);
			return false; // prevents link
		} );

	}

	function handleMapEdits( pathSuffix, pathPrefix, context ) {

		// add a delete to all jvms
		// $('.item[data-path="jvms"] > input.property').after('<a href="#"><img
		// class="but" src="images/minus.jpg"></a>') ;
		var requestContext = $( "body" );
		if ( context != undefined )
			requestContext = context;

		// console.log("\n\n handleMapEdits - pathSuffix: " + pathSuffix + "
		// pathPrefix:" + pathPrefix + " context:" + requestContext.data("path")) ;
		$( '.item[data-path$="' + pathSuffix + '"] > input.property', requestContext ).each( function ( index ) {
			var inputObj = $( this );
			configureMapDeletes( index, inputObj, pathSuffix, pathPrefix, context );
		} );

		var regex = new RegExp( "\\.", "g" ); // strip out . for css
		var addIconClass = "mapAdd" + pathSuffix.replace( regex, "" );
		var pathAttribute = "";
		if ( pathPrefix != undefined ) {
			addIconClass += pathPrefix.replace( regex, "" );
			pathAttribute = '[data-path$="' + pathPrefix + '"] ';
		}

		// delete any adds if they are already present. Necessary because some items
		// such as jvmPorts occur multiple instances.
		// console.log( "Removing all: " + addIconClass ) ;
		$( "." + addIconClass, requestContext ).remove();
		$( pathAttribute + '.property.' + pathSuffix, requestContext.parent() ).each(
				function ( index ) {

					// console.log("Adding: " + $(this).val() + " to path: "
					// + $(this).parent().data("path") + " addIconClass: " +
					// addIconClass ) ;
					var title = "Click to add new " + treeTemplates.getDesc( pathSuffix );
					if ( $( this ).parent().data( "path" ) == "clusterDefinitions" ) {
						// hook for clusters since lifecycles are
						// dynamically added.
						title += " service cluster";
					}

					if ( $( this ).parent().data( "path" ).indexOf( "jvms" ) == 0
							|| $( this ).parent().data( "path" ).indexOf( "osProcesses" ) == 0 ) {
						// hook for clusters since lifecycles are
						// dynamically added.
						title += " service property";
					}

					// icon 2 adds padding for add icon
					var addButton = $( '<a class="icon2 ' + addIconClass + '" title="' + title
							+ '" href="#add"><img src="../images/16x16/newFolder.png"></a>' );

					// jsoneditor uses a lot of relative calls. this seems
					// safe
					$( this ).next().after( addButton );

					addButton.click( function () {
						newMapKeyPrompt( $( this ), pathSuffix );
						return false; // prevents link
					} );

				} );

	}

	function configureMapDeletes( index, inputObject, pathSuffix, pathPrefix, context ) {

		if ( pathPrefix != undefined && inputObject.parent().data( "path" ).indexOf( pathPrefix ) != 0 ) {
			// console.log("Skipping delete binding due to path
			// prefix: " + inputObject.parent().data("path") ) ;
			return;
		}
		// never allow csagent, admin, or settings to be deleted
		if ( inputObject.val() == "CsAgent" || inputObject.val() == "admin" || inputObject.val() == "settings" )
			return;

		// only certain params can be deleted on jvms and
		// osProcesses
		if ( inputObject.parent().parent().data( "path" ) == "jvms"
				|| inputObject.parent().parent().data( "path" ) == "osProcesses" ) {
			if ( jQuery.inArray( inputObject.val(), treeTemplates.getJvmOptionalSettings() ) == -1 )
				return;
		}

		var deleteId = "delete-" + inputObject.val();

		// icons are refreshed when items are added. Delete
		// icons is only added if not already present
		if ( $( '[title="' + deleteId + '"]', inputObject.parent() ).length != 0 )
			return;

		// console.log("deleteId" + deleteId) ;
		var deleteButton = $( '<a class="icon" href="#' + deleteId + '" title="' + deleteId
				+ '"><img src="../images/16x16/deleteFolder.png"></a>' );
		inputObject.after( deleteButton );

		var propertyInput = inputObject;

		deleteButton
				.click( function () {
					// propertyInput.attr('disabled', false);
					var mapKey = propertyInput.val();

					var parentPath = inputObject.parent().data( "path" );
					// if ( parentPath == undefined )
					// parentPath="";
					// osProcesses are in an array with quotes
					// in title
					// if ( inputObject.parent().data("path") ==
					// "osProcesses") mapKey = '"' + mapKey +
					// '"' ;
					var numMatches = $( '[data-path^="clusterDefinitions"] :input[title="' + mapKey + '"]' ).length;
					console.log( "Matches for service: " + mapKey + " count: " + numMatches );
					if ( numMatches > 0 && (parentPath == "jvms" || parentPath == "osProcesses") ) {
						var matches = "";

						$( '[data-path^="clusterDefinitions"] :input[title="' + mapKey + '"]' ).each( function ( index ) {

							console.log( "Matching paths: " + $( this ).parent().data( "path" ) );
							// var path = inputObject.parent().data("path");
							var path = $( this ).parent().data( "path" );
							matches += "\n" + path;
						} );
						// jError("The following reference(s)
						// must be removed before this service
						// can be deleted:\n" + matches, "Delete
						// Request cannot proceed") ;
						var message = '<div class="warning">Warning: ' + numMatches + ' references to : "' + mapKey
								+ '" still Exist';
						message += '</div><br>Request cannot proceed until the following are deleted: ' + matches
								+ '<br><br>';
						alertify.alert( message );
						return false;
					}

					var message = "Confirm delete of : " + propertyInput.val();
					// console.log("parentPath" + parentPath) ;
					if ( parentPath.indexOf( "jvmPorts" ) != -1 )
						message += '<div class="warning">You should kill/clean services prior to committing clustering changes to release resources in use</div>';

					var deleteDialog = alertify.confirm( message );

					deleteDialog.setting( {
						title: "Delete: " + propertyInput.val(),
						'labels': {
							ok: 'Remove From Definition',
							cancel: 'Cancel'
						},
						'onok': function () {
							alertify.notify( "Cluster updated" );
							propertyInput.val( "" );

							propertyInput.trigger( "change" );
							if ( parentPath.indexOf( "jvmPorts" ) != -1 ) {
								var matches = "";
								$( '.property[title="' + mapKey + '"]' ).each(
										function ( index ) {
											var path = $( this ).next().next().next().data( "path" );
											if ( path != undefined )
												matches += "\n" + path;
										} );
								if ( matches.length != 0 )
									jInfo( "Deleted item: " + mapKey + ". Note there are still some references : \n"
											+ matches );
							}
						},
						'oncancel': function () {
							alertify.warning( "Operation Cancelled" );
						}

					} );

					if ( parentPath == "jvms" || parentPath == "osProcesses" ) {
						$( "#popup_message" )
								.append(
										"<br><br>Prior to deleting services, ensure that they have been killed/cleaned to remove running processes and free diskspace." );
					}

					return false; // prevents link
				} );

	}

	function isArray( o ) {
		return Object.prototype.toString.call( o ) == '[object Array]';
	}

	function isString( o ) {
		return Object.prototype.toString.call( o ) == '[object String]';
	}

	function isObject( o ) {
		return Object.prototype.toString.call( o ) == '[object Object]';
	}

	function newMapKeyPrompt( addButton_JQ, pathSuffix ) {

		var regex = new RegExp( ' ', "g" );
		var itemName = treeTemplates.getDesc( pathSuffix ).replace( regex, "" );

		var newItemDialog = alertify.confirm( '<div id="popup_message">Enter New name'
				+ '<input style="width:20em" id="popup_prompt" value="' + itemName + '"><div>' );

		newItemDialog.setting( {
			title: '<span id="popup_title">' + "New Attribute: " + pathSuffix + '</span>',
			resizable: false,
			'labels': {
				ok: 'Add To Configuration',
				cancel: 'Cancel'
			},
			'onok': function () {
				var newItemKey = $( "#popup_prompt" ).val();

				alertify.success( "Updating model with:" + newItemKey );
				setTimeout( function () {
					newMapKeyAdd( newItemKey, addButton_JQ, pathSuffix );
				}, 300 );

			},
			'oncancel': function () {
				alertify.warning( "Cancelled new attribute" );
			}

		} );

		// Hook to ensure only alphanumerics and - are accepted
		if ( pathSuffix != "helpMenuItems" && pathSuffix != "launchUrls" ) {
			$( "#popup_prompt" ).keypress(
					function ( event ) {
						// var filter=$('#popup_prompt' ).val() ;
						// console.log("updated filter") ;
						// alertify.notify( "Key : " + event.which) ;
						if ( event.charCode != 0 ) {
							// numeric only: var regex = new RegExp("^[0-9]+$");
							var regex = new RegExp( "^[a-zA-Z0-9\-]+$" );
							var key = String.fromCharCode( !event.charCode ? event.which : event.charCode );
							if ( !regex.test( key ) ) {
								alertify.failure( "Invalid key : '" + key + "'" );
								event.preventDefault();
								return false;
							}
						}
					} );
		}

		// Hook for replacing input string with a dropdown to select from existing
		// jvms
		newMapCustomPrompt( addButton_JQ.parent(), pathSuffix );

	}

	function newMapKeyAdd( newItemKey, addButton_JQ, pathSuffix ) {

		var valInput = addButton_JQ.prev();
		var parentPath = addButton_JQ.parent().data( "path" );
		// has a delete button in front, go back one more
		if ( !valInput.hasClass( "value" ) ) {
			// console.log( "going back one more: " +
			// addButton_JQ.prev().prev().attr('title') ) ;
			valInput = addButton_JQ.next();
		}

		// console.log( "newItemKey: " + newItemKey + "newMapKeyAdd valInput.val():
		// " + valInput.val() +
		// "parentPath: " + parentPath + " pathSuffix: " + pathSuffix) ;
		// console.log( "newMapKeyAdd " + " parentPath: " + parentPath + "
		// pathSuffix: " + pathSuffix) ;
		var newMapObject = JSON.parse( valInput.val() );

		var templateCopy = jQuery.extend( true, { }, treeTemplates.getMapDefaults( pathSuffix ) );
		// console.log(" pathSuffix: " + pathSuffix + " parent path: " + parentPath
		// + " templateCopy: " + JSON.stringify(templateCopy, null,"\t") ) ;
		if ( isArray( treeTemplates.getMapDefaults( pathSuffix ) ) ) {
			templateCopy = jQuery.extend( true, [], treeTemplates.getMapDefaults( pathSuffix ) );
		}

		if ( isString( treeTemplates.getMapDefaults( pathSuffix ) ) && newItemKey != "java" ) {
			templateCopy = treeTemplates.getMapDefaults( pathSuffix );
		}

		// hook for version in cluster definition
		for ( var i = 0; i < clusterKeys.length; i++ ) {
			if ( pathSuffix == clusterKeys[i] ) {
				templateCopy = jQuery.extend( true, { }, treeTemplates.getMapDefaults( "clusterDefinitions" )["SampleCluster"]["multiVm"]["1"] );
			}
		}

		// hook for version in services
		if ( pathSuffix == "version" && (parentPath.indexOf( "jvms" ) == 0 || parentPath.indexOf( "osProcesses" ) == 0) ) {
			// console.log("found jvms with children count: " + $(".value",
			// addButton_JQ.parent()).length ) ;
			// For jvms - allow raw json editing
			templateCopy = jQuery.extend( true, { }, treeTemplates.getMapDefaults( "jvms" )["version"]["1"] );
			// return;
		}

		// Hook for property overrides. Starts with jvms*version.*
		if ( (parentPath.indexOf( "jvms" ) == 0 || parentPath.indexOf( "osProcesses" ) == 0)
				&& parentPath.indexOf( "version." ) != 0 ) {
			// console.log ("Adding override property") ;
			templateCopy = jQuery.extend( true, { }, treeTemplates.getLifeCycleParams()[newItemKey] );

			if ( isString( treeTemplates.getLifeCycleParams()[newItemKey] ) )
				templateCopy = treeTemplates.getLifeCycleParams()[newItemKey];
		}

		// hook for customMetrics in services
		if ( (pathSuffix == "customMetrics") && (parentPath.indexOf( "jvms" ) == 0 || parentPath.indexOf( "osProcesses" ) == 0) ) {
			// console.log("found customMetrics with children count: " + $(".value",
			// addButton_JQ.parent()).length ) ;
			templateCopy = jQuery.extend( true, { }, treeTemplates.getMapDefaults( "customMetrics" ) );
			// console.log( "found customMetrics: " + JSON.stringify( templateCopy )
			// ) ;
			// return;
		}

		// console.log ("Adding item to parent path:" +
		// addButton_JQ.parent().data("path")) ;
		var isClusterDef = (parentPath != undefined) && (parentPath == "clusterDefinitions") && (pathSuffix != "version")
				&& (parentPath.indexOf( "settings" ) == -1);

		if ( isClusterDef ) {
			// console.log("Found Cluster Def") ;
			templateCopy = jQuery.extend( true, { }, treeTemplates.getMapDefaults( "clusterDefinitions" )["SampleCluster"] );

		}

		// hook for generating unique ports
		if ( pathSuffix == "jvmPorts" ) {
			templateCopy = new Array();
			// 
			templateCopy.push( getNextAvailablePort( addButton_JQ.parent().parent().parent() ) ); // converts
			// to
			// string
			// and
			// adds
			// range
			// indicator
		}

		// Hook for property overrides. Starts with jvms*version.*
		if ( pathSuffix == "monitors" ) {
			// console.log("adding monitor") ;
			templateCopy = treeTemplates.getLifeCycleParams()["monitors"][newItemKey];
		}

		if ( newMapObject[newItemKey] != null ) {
			$( ".loadingLargePanel" ).hide();
			alertify
					.alert( '<div class="warning">'
							+ newItemKey
							+ "already exists.</div> <br>Either delete it or add using a different name<br><br>Error - Operation cannot proceed" );
			return;
		}
		// console.log( "Updating input: " + valInput.attr("title") + " with key: "
		// + newItemKey + " templateCopy: " + JSON.stringify(templateCopy,
		// null,"\t")) ;
		newMapObject[newItemKey] = templateCopy;
		// tleArray.push(pathSuffix + now) ;
		valInput.val( JSON.stringify( newMapObject ) );
		// console.log("newMapKeyAdd got here:" + valInput.parent().html() ) ;
		valInput.trigger( "change" );

		// refresh Entire tree
		showTreeTab();

	}

	// 
	// Generate a unique port for the specified lifeccycle
	//

	function getNextAvailablePort( lifecycleJQO ) {
		var testPort = "";
		// dividing by 10 because blocks are assigned in groups of 10. eg. 8210 thry
		// 8219 will be used by a single tomcat instance
		for ( var i = portStart / 10; i < portEnd / 10; i++ ) {

			testPort = i + "x";
			// var numInstances = $( '[title="\"' + testPort + '\""]',
			// addButton_JQ.parent().parent() ).length ;
			// var numInstances = $( "[value*='" + testPort + "']").length ;
			var numInstances = 0;
			$( "> input.value", lifecycleJQO ).each( function ( index ) {
				if ( $( this ).val().indexOf( testPort ) != -1 ) {
					numInstances++;
					// console.log("Found match:" + $(this).parent().data("path") )
					// ;
				}
			} );
			// console.log ("testPort: \"" + testPort + "\" numInstances: " +
			// numInstances + " in path: " +
			// addButton_JQ.parent().parent().data("path") ) ;
			if ( numInstances == 0 )
				break;
		}

		return testPort;
	}


	function newMapCustomPrompt( jquerySearchDiv, pathSuffix ) {

		// console.log("newMapCustomPrompt: " + jquerySearchDiv.data("path") ) ;
		var parentPath = jquerySearchDiv.data( "path" );

		if ( parentPath == "jvms" || parentPath == "osProcesses" ) {
			// hook for jvm properties
			var $serviceParametersSelect = $( '<select class="dropdown" id="jvmSelect" name="jvmSelect"></select>' );

			for ( var i = 0; i < treeTemplates.getJvmOptionalSettings().length; i++ ) {
				$serviceParametersSelect.append( '<option style="padding: 0;">' + treeTemplates.getJvmOptionalSettings()[i] + '</option>' );
			}
			$serviceParametersSelect.sortSelect();
			var prompt = $( "#popup_prompt" );
			$( "#popup_title" ).text( "New service configuration" );
			$( "#popup_message" ).text( "Select the configuration item to be added: " );
			$( "#popup_message" ).append( prompt );

			$( "#popup_prompt" ).after( $serviceParametersSelect );
			$( "#popup_prompt" ).val( $serviceParametersSelect.val() );
			$( "#popup_prompt" ).hide();
//			$serviceOptions.change( function () {
//				$( "#popup_prompt" ).val( $serviceOptions.val() );
//			} );
			$serviceParametersSelect.selectmenu( {
				width: "400px",
				change: function () {
					$( "#popup_prompt" ).val( $serviceParametersSelect.val() );
				}
			} );

			return;
		}

		if ( (parentPath.indexOf( "jvms" ) == 0 || parentPath.indexOf( "osProcesses" ) == 0) && pathSuffix == "monitors" ) {
			// hook for service alerts
			var $alertSelect = $( '<select class="dropdown" id="jvmSelect" name="jvmSelect"></select>' );

			for ( var i = 0; i < treeTemplates.getMonitorParams().length; i++ ) {
				$alertSelect.append( '<option style="padding: 0;">' + treeTemplates.getMonitorParams()[i] + '</option>' );
			}

			var prompt = $( "#popup_prompt" );
			$( "#popup_title" ).text( "New monitor configuration" );
			$( "#popup_message" ).text( "Select the monitor to be added: " );
			$( "#popup_message" ).append( prompt );

			$( "#popup_prompt" ).after( $alertSelect );
			$( "#popup_prompt" ).val( $alertSelect.val() );
			$( "#popup_prompt" ).hide();
			$alertSelect.change( function () {
				$( "#popup_prompt" ).val( $alertSelect.val() );
			} );
			return;
		}

		// Hook for version prompts
		if ( (parentPath.indexOf( "jvms" ) == 0 || parentPath.indexOf( "osProcesses" ) == 0) && pathSuffix == "version" ) {
			// console.log("service version prompt");
			var $versionSelect = $( '<select class="dropdown" id="jvmSelect" name="jvmSelect"></select>' );
			for ( var i = 0; i < clusterKeys.length; i++ ) {
				var versionsInCluster = '[data-path^="clusterDefinitions"][data-path$="' + clusterKeys[i]
						+ '"] > input.property';

				$( versionsInCluster ).each(
						function () {
							if ( $.inArray( $( this ).val(), $versionSelect.val() ) == -1 )
								$versionSelect.append( '<option style="padding: 0;" value="' + $( this ).val() + '">'
										+ $( this ).val() + '</option>' );

						} );
			}
			// console.log("contents: " + $( "#popup_message" ).text() ) ;
			var prompt = $( "#popup_prompt" );
			$( "#popup_title" ).text( "New service configuration version" );
			$( "#popup_message" ).text(
					"Select the version to be added. Versions are derived from each cluster in clusterDefinitions:" );
			$( "#popup_message" ).append( prompt );
			prompt.after( $versionSelect );
			prompt.val( $versionSelect.val() );
			prompt.hide();
			$versionSelect.change( function () {
				prompt.val( $versionSelect.val() );
			} );
			return;

		}

		// Hook for customMetric prompts
		if ( ((parentPath.indexOf( "jvms" ) == 0 || parentPath.indexOf( "osProcesses" ) == 0))
				&& ((pathSuffix == "customMetrics" || parentPath.indexOf( "customMetrics" ) != -1)) ) {
			// console.log("service version prompt");
			return;

		}

		// Hook for version lifecycle prompts
		if ( (parentPath.indexOf( "jvms" ) == 0 || parentPath.indexOf( "osProcesses" ) == 0)
				&& parentPath.indexOf( "version." ) == -1 ) {
			// console.log("service version prompt: parent: " + parentPath + "
			// pathSuffix: " + pathSuffix);
			var $lifecycleSelect = $( '<select class="dropdown" id="jvmSelect" name="jvmSelect"></select>' );
			var versionsInCluster = '[data-path="clusterDefinitions"] > input.property';

			$( versionsInCluster ).each( function () {
				if ( $.inArray( $( this ).val(), $lifecycleSelect.val() ) == -1 )
					$lifecycleSelect.append( '<option style="padding: 0;" value="' + $( this ).val() + '">' + $( this ).val()
							+ '</option>' );

			} );
			// console.log("contents: " + $( "#popup_message" ).text() ) ;
			var prompt = $( "#popup_prompt" );
			$( "#popup_title" ).text( "New service configuration version lifecycle" );
			$( "#popup_message" ).text(
					"Select the lifecycle to be added. Lifecycle are derived from lifecycles in clusterDefinitions: " );
			$( "#popup_message" ).append( prompt );
			prompt.after( $lifecycleSelect );
			prompt.val( $lifecycleSelect.val() );
			prompt.hide();
			$lifecycleSelect.change( function () {
				prompt.val( $lifecycleSelect.val() );
			} );
			return;

		}

		if ( (parentPath.indexOf( "jvms" ) == 0 || parentPath.indexOf( "osProcesses" ) == 0)
				&& parentPath.indexOf( "version." ) != -1 ) {
			// hook for overRide properties
			var $overRideSelect = $( '<select class="dropdown" id="jvmSelect" name="jvmSelect"></select>' );

			for ( var param in treeTemplates.getLifeCycleParams() ) {
				$overRideSelect.append( '<option style="padding: 0;">' + param + '</option>' );
			}
			var prompt = $( "#popup_prompt" );
			$( "#popup_title" ).text( "New service configuration lifecycle setting" );
			$( "#popup_message" ).text( "Select the override property to add to service: " );
			$( "#popup_message" ).append( prompt );

			$( "#popup_prompt" ).after( $overRideSelect );
			$( "#popup_prompt" ).val( $overRideSelect.val() );
			$( "#popup_prompt" ).hide();
			$overRideSelect.change( function () {
				$( "#popup_prompt" ).val( $overRideSelect.val() );
			} );
			return;
		}

		if ( pathSuffix == "jvmPorts" || pathSuffix == "osProcessesList" ) {
			// use a drop down to select from existing
			var refItems = '.item[data-path$="jvms"] > input.property';
			if ( pathSuffix == "osProcessesList" )
				refItems = '.item[data-path$="osProcesses"] > input.property';

			var $serviceClusterSelect = $( '<select class="dropdown" id="jvmSelect" name="jvmSelect"></select>' );
			$( refItems ).each(
					function () {

						// exclude CsAgent and existing jvms from dropdown
						if ( $( this ).val() == "CsAgent" )
							return;

						if ( $( '[title*="' + $( this ).val() + '"]', jquerySearchDiv ).length == 0 )
							$serviceClusterSelect.append( '<option style="padding: 0;">' + $( this ).val() + '</option>' );
					} );

			var prompt = $( "#popup_prompt" );
			$( "#popup_title" ).text( "New service assignment" );
			$( "#popup_message" ).text( "Select the service to add to cluster: " );
			$( "#popup_message" ).append( prompt );

			$( "#popup_prompt" ).after( $serviceClusterSelect );
			$( "#popup_prompt" ).val( $serviceClusterSelect.val() );
			$( "#popup_prompt" ).hide();

			$serviceClusterSelect.sortSelect();
			$serviceClusterSelect.change( function () {
				$( "#popup_prompt" ).val( $serviceClusterSelect.val() );
			} );

			return;
		}

	}

	// Helper function to add buttons to tree

	function handleArrayEdits( pathSuffix, width ) {

		// console.log("handleArrayEdits - pathSuffix: " + pathSuffix ) ;
		$( 'div.array .item[data-path$="' + pathSuffix + '"] > input.value' ).css( "width", width );
		$( 'div.array .item[data-path$="' + pathSuffix + '"] > input.property' ).each(
				function ( index ) {
					$( this ).hide();
					// console.log("deleteId" + deleteId) ;
					var regex = new RegExp( '"', "g" );
					var hostName = $( this ).next().attr( "title" ).replace( regex, "" );

					var title = "Click to delete: " + hostName;
					var deleteButton = $( '<a class="icon" title="' + title
							+ '" href="#delete"><img src="../images/16x16/deleteFolder.png"></a>' );
					$( this ).next().after( deleteButton );
					var valInput = $( this );

					deleteButton.click( function () {
						// alert("got here:" + valInput.parent().html() ) ;
						valInput.val( "" ); // Flag to delete
						valInput.trigger( "change" );
						return false; // prevents link
					} );

				} );

		var id = "arrayAdd" + pathSuffix;
		$( "." + id ).remove();

		$( 'div.array .property.' + pathSuffix ).each(
				function ( index ) {

					var title = "Click to add new " + treeTemplates.getDescLabel( pathSuffix );
					var addButton = $( '<a class="icon2 ' + id + '" title="' + title
							+ '" href="#add"><img src="../images/16x16/newFolder.png"></a>' );

					// jsoneditor uses a lot of relative calls. this seems
					// safe
					$( this ).next().after( addButton );

					addButton.click( function () {
						arrayValuePrompt( $( this ), pathSuffix, width );
						return false; // prevents link
					} );

				} );

	}

	//
	// JSON editor uses title field of relative elements to store structure. These
	// edits the title with new item
	//

	function arrayValuePrompt( jqueryObject, pathSuffix, width ) {

		var regex = new RegExp( ' ', "g" );

		// var itemName = treeTemplates.getDesc(pathSuffix).replace(regex, "") ;
		var itemName = treeTemplates.getDescLabel( pathSuffix ).replace( regex, "" );

		if ( itemName == "PortRange" )
			itemName = getNextAvailablePort( jqueryObject.parent().parent().parent() );

		// var alertMessage = " Enter the " + treeTemplates.getDesc(pathSuffix) + ":";

		var newItemDialog = alertify.confirm( 'New Item: '
				+ '<input style="width:20em" id="popup_prompt" value="' + treeTemplates.getDesc( pathSuffix ) + '">' );

		alertify.set( 'notifier', 'position', 'top-left' );

		newItemDialog.setting( {
			title: '<span id="popup_title">' + "New" + treeTemplates.getDesc( pathSuffix ) + '</span>',
			resizable: false,
			'labels': {
				ok: 'Add To Configuration',
				cancel: 'Cancel'
			},
			'onok': function () {
				var theNewItem = $( "#popup_prompt" ).val();

				alertify.success( "Updating model with:" + theNewItem );
				// couple of permutations on where json editor places content
				var valInput = jqueryObject.prev();
				// console.log("new item prompt valInput.val() : " + valInput.val()
				// ) ;
				if ( !valInput.hasClass( "value" ) )
					valInput = jqueryObject.next();
				// console.log("valInput.val() : " + valInput.val() ) ;
				// now parse content to ensure valid
				var titleArray = JSON.parse( valInput.val() );

				titleArray.push( theNewItem );
				valInput.val( JSON.stringify( titleArray ) );
				// alert("got here:" + valInput.parent().html() ) ;
				valInput.trigger( "change" );
				handleArrayEdits( pathSuffix, width );

			},
			'oncancel': function () {
				alertify.warning( "Operation Cancelled" );
			}

		} );

		// Hook for replacing input string with a dropdown to select from existing
		// jvms
		newMapCustomPrompt( jqueryObject.parent(), pathSuffix );

	}

} );


