// http://requirejs.org/docs/api.html#packages
// Packages are not quite as ez as they appear, review the above
require.config( {
	packages: []
} );



require( [], function ( ) {

	console.log( "\n\n ************** _main: loaded *** \n\n" );

	// Shared variables need to be visible prior to scope


	var _lastNodeSelected = "";
	var _windowResizeTimer = null;

	var myDropzone = null;
	var $fileTree = null;
	var $fileFilter = null;
	var $filterControls = null;
	var $fileControls = null;
	var _nodeTimer = null;
	var _filterTimer = null;
	var _lastOpenedFolder = null;
	var _lastFileSelected = null;


	var dropOptions = {
		paramName: "distFile",
		url: "../os/uploadToFs",
		addRemoveLinks: true,
		dictDefaultMessage: "Drag and drop, or Click to browse",
// previewTemplate: $('#preview-template').html() ,

	};

	$( document ).ready( function () {
		initialize();
	} );

	this.initialize = initialize;
	function initialize() {
		 $fileTree = $( "#fileTree" );
		 $fileFilter = $( "#fileFilter" );
		 $filterControls = $( "#filterControls" );
		 $fileControls = $( "#fileControls" );
		
		CsapCommon.configureCsapAlertify();

		console.log( "starting up" );
		appInit();

		$( "#fileOptions" ).change( function () {

			// console.log("#fileOptions changed", $( "#fileOptions" ).val()) ;
			var operation = "";
			performFileOrFolderOperation(
					_lastNodeSelected,
					$( "#fileOptions" ).val() )

			// reset to perform next
			$( "#fileOptions" )[0].selectedIndex = 0;
		} );

//		$( '[title!=""]' ).qtip( {
//			style: {
//				classes: 'qtip-bootstrap'
//			},
//			position: {
//				my: 'top left', // Position my top left...
//				at: 'bottom left',
//				adjust: { x: 5, y: 10 }
//			}
//		} );

		$('input[type=radio][name=sortRadio]').change(function() {
			refreshLastFolder() ;
		}) ;

		$('#showMeta').change(function() {
			refreshLastFolder() ;
		}) ;

		$('#useRoot').change(function() {
			refreshLastFolder() ;
		}) ;
		
		$('#newWindow').click(function() {
			quickView() ;
		}) ;

	}



	function quickView() {
		console.log( "quickView: ", _lastFileSelected, _lastNodeSelected );
		
		if ( _lastNodeSelected.folder ) {
			// alertify.alert("launch browser");
			var urlAction = MANAGER_URL + "?fromFolder=" + _lastNodeSelected.data.location
			
			if ( serviceName != null ) {
				urlAction += "&serviceName=" + serviceName
			}

			openWindowSafely( urlAction, "_blank" );
			console.log( "lastFileNodeSelected: " + _lastNodeSelected );
//			var inputMap = {
//				fromFolder: _lastNodeSelected.data.location,
//				serviceName: serviceName
//			};
//			postAndRemove( "_blank", MANAGER_URL, inputMap );
		} else {


			var inputMap = {
				fromFolder: _lastFileSelected.data.location,
				serviceName: serviceName,
				"browseId": browseId,
				forceText: true,
				hostName: hostName
			};
			
			var quickUrl = "downloadFile/quickView" ;
			if ( browseId != "" ) {
				quickUrl ="../downloadFile/quickView"
			}

			$.post( quickUrl, inputMap )

					.done( function ( fileContents ) {
						//getDataSuccess( loadJson );
						//console.log("fileContents", fileContents) ;
						showQuickViewDialog( fileContents );

					} ).fail( function ( jqXHR, textStatus, errorThrown ) {
				alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact support" );
			}, 'json' );
		}
	}

	function showQuickViewDialog( fileText ) {

		var $container = $( "#fileEditorTemplate" );
		var quickViewDialog = alertify.alert( $container.html() );

		var targetWidth = $( window ).outerWidth( true ) - 100;
		var targetHeight = $( window ).outerHeight( true ) - 100;

		quickViewDialog.setting( {
			title: "Read Only (use edit to modify): " + _lastFileSelected.title,
			resizable: true,
			movable: true,
			width: targetWidth,
			'label': "Close",
			autoReset: false,
			'onok': function () {
			}
		} ).resizeTo( targetWidth, targetHeight );

		$( ".fileEditor textarea" ).val( fileText );

		dialogResize();
	}


	function dialogResize() {
		clearTimeout( _windowResizeTimer );
		_windowResizeTimer = setTimeout( function () {

			var $dialog = $( ".ajs-content .fileEditor" ).parent();
			var $contentTextArea = $( "textarea", $dialog )
			
			//$(".ajs-footer", $dialog.parent().parent()).hide() ;

			var displayHeight = Math.round( $dialog.outerHeight( true ) ) - 20;
			var displayWidth = Math.round( $dialog.outerWidth( true ) ) ;
			console.log( "displayHeight", displayHeight );

			if ( $( ".lines", $dialog ).length == 0 ) {
				$contentTextArea.css( "height", displayHeight - 5 );
				$contentTextArea.css( "width", $dialog.width - 90 );
				//console.log("Contructing line numbers");
				$contentTextArea.linedtextarea();
			}

			// Hook for lined text area which runs against parent containers
			var container = $contentTextArea.parent().parent();
			container.css( "height", displayHeight );
			$( ".lines", container ).css( "height", displayHeight );
			$contentTextArea.css( "height", displayHeight - 5 );
			
			container.css( "width", displayWidth - 20 );
			$contentTextArea.css( "width", displayWidth - 90 );

			// trigger lined text area to refresh
			$contentTextArea.scrollTop( $contentTextArea.scrollTop() + 1 );

		}, 500 )



	}


	function appInit() {

		console.log( "appInit" );


		simpleUploadSetup();

		$( '#showDu' ).change( showDuDialog );

		setupFileBrowser();

		$( ".news" ).animate( {
			backgroundColor: "yellow"
		}, 1500 ).animate( {
			backgroundColor: "white"
		}, 1500 );

	}

	function showDuDialog() {

		// console.log( "checked" + $(this).prop('checked') ) ;

		if ( $( this ).prop( 'checked' ) ) {
			var message = "Linux timeout command will be used to ensure a maximum time of 60 seconds is used to calculated disk; use CSAP OS commands if more time is needed.<br><br>"
					+ "If max time expires only partial results will appear."
					+ " Note that Linux du command can consume significant resources, CSAP OS dashboard can be used to show."
					+ "<br><br>NOTE: Only availabe on RH6 or later. <br>";

			var applyDialog = alertify.confirm( message );
			applyDialog
					.setting( {
						title: "Caution Advised: Calculating Disk Usage may consume signficant resources",
						'labels': {
							ok: 'Include Folder Disk Usage',
							cancel: 'Cancel request'
						},
						'onok': function () {
							var $radios = $('input:radio[name=sortRadio]');
							console.log("showDuDialog() selecting size radio button", $radios.length) ;
							
							$radios.filter('[value=size]').prop('checked', true);
							
							refreshLastFolder() ;
//											alertify
//													.warning("Disk space will be calculated");
						},
						'oncancel': function () {
//											alertify
//													.warning("Operation Cancelled");
							$( "#showDu" ).prop( 'checked', false )
						}

					} );

		}
		return;
	}
	
	function refreshLastFolder( ) {

		if ( _lastOpenedFolder == null ) return;
		_lastOpenedFolder.setExpanded( false );
		setTimeout(() => {

			_lastOpenedFolder.setExpanded( true );
		}, 500);
	}
	
	function expandFolder( location ) {

		var tree = $jsonBrowser.fancytree( 'getTree' );
		var node = tree.findFirst( function ( node ) {
			return findByPath( node.data, location );
		} );
		if ( node )
			node.setExpanded( true );
	}
	
	function simpleUploadSetup() {
		myDropzone = new Dropzone( ".dropzone", dropOptions );


		$( "#hideUpload" ).click( function () {
			$( "#uploadSection" ).hide();
			return false;
		} );

		// http://www.dropzonejs.com/#configuration
		// https://github.com/enyo/dropzone/wiki

		myDropzone.on( "complete", function ( file ) {
			myDropzone.removeFile( file );
		} );

		myDropzone.on( "success", function ( file, response ) {
			var results = JSON.stringify( response.scriptOutput, null, "\t" );
			results = results.replace( /\\n/g, "<br />" );

			alertify.success( results );
		} );

		myDropzone.on( "error", function ( file, message ) {
			alertify.csapWarning( "Failed to upload: " + message
					+ ",  Default max size is 500m - confirm file size." );
		} );

		myDropzone.on( "failure", function ( file, response ) {
			var results = JSON.stringify( response.scriptOutput, null, "\t" );
			results = results.replace( /\\n/g, "<br />" );

			alertify.success( results );
		} );

		myDropzone.on( "sending",
				function ( file, xhr, formData ) {
					formData.append( "extractDir",
							_lastNodeSelected.data.location );
					formData.append( "chownUserid", "ssadmin" );
					formData.append( "skipExtract", "on" );
					formData.append( "hosts", hostName );
					formData.append( "timeoutSeconds", "30" );
					formData.append( "serviceName", serviceName );
					formData.append( "overwriteTarget", $( "#overWriteFile" ).prop(
							'checked' ) );
					alertify.notify( "sending: " + file.name );
				} );
	}


	function isLastItemAFolder ( key, opt ) {

		return _lastNodeSelected.isFolder();
	}

	function setupFileBrowser() {


		var initialFolders;

		var contextMenu = buildContextMenu();
		if ( browseId == "" ) {
			initialFolders = buildFileTreeForManager();
		} else {
			$( ".news" ).append( "<br>Protected by group: " + browseGroup );
			initialFolders = buildFileTreeForBrowser();
			contextMenu = {
				'view': {
					'name': 'View',
					'icon': 'view',
					disabled: isLastItemAFolder
				},
				'download': {
					'name': 'Download',
					'icon': 'download',
					disabled: isLastItemAFolder
				},
				"sep1": "---------",
			}
			//$fileControls.hide();

		}

		// http://wwwendt.de/tech/fancytree/demo/#../3rd-party/extensions/contextmenu/contextmenu.html

		$.ui.fancytree.debugLevel = 1;


		$( "#fileTree" ).fancytree( buildBrowserConfiguration( initialFolders, contextMenu ) );
	}

	function buildContextMenu() {

		// http://medialize.github.io/jQuery-contextMenu/demo.html
		var contextMenu = {
				
			'quickView': {
				'name': 'Quick View',
				'icon': 'quickView'
			},
			"sep1": "---------",
			'view': {
				'name': 'View',
				'icon': 'view',
				disabled: isLastItemAFolder
			},
			'download': {
				'name': 'Download',
				'icon': 'download',
				disabled: isLastItemAFolder
			},
			'tail': {
				'name': 'Tail',
				'icon': 'copy',
				disabled: isLastItemAFolder
			},
			'edit': {
				'name': 'Edit',
				'icon': 'edit',
				disabled: isLastItemAFolder
			},
			"sep1": "---------",
		}

		if ( isAdmin ) {

			$.extend( contextMenu, {
				'delete': {
					'name': 'Delete',
					'icon': 'delFolder'
				},
				'script': {
					'name': 'Script',
					'icon': 'script'
				},
				'uploadSimple': {
					'name': 'Upload',
					'icon': 'add'
				},
				'upload': {
					'name': 'Upload (Advanced)',
					'icon': 'add'
				},
				'sync': {
					'name': 'Sync to other Vm',
					'icon': 'copy'
				}
			} );

		}

		return contextMenu;
	}

	function buildBrowserConfiguration( initialFolders, contextMenu ) {
		var config = {

			extensions: ["contextMenu"],
			source: initialFolders,
			keyboard: false,

			contextMenu: {
				menu: contextMenu,
				actions: performFileOrFolderOperation
			},
			collapse: function ( event, data ) {
				console.log( "buildBrowserConfiguration(): collapse tree" );
				$( ".commands" ).hide();
				$( "#jsTemplates" ).append( $filterControls );
				// console.log("resetting fileControls to template", $fileControls) ;  
				$( "#jsTemplates" ).append( $fileControls );
				// logEvent(event, data);
				if ( $( '#cacheResults:checked' ).length == 0 ) {
					data.node.resetLazy();
				}

			},
			
			expand: treeExpandNode,
			
			activate: function ( event, data ) {

				console.log( "buildBrowserConfiguration(): activate tree" );
				var fancyTreeData = data;
				var nodeSelected = fancyTreeData.node;
				var nodeData = nodeSelected.data;

				// Used for positioning and generation of menu
				_lastNodeSelected = nodeSelected;

				if ( !nodeSelected.folder ) {
					$( ".lastFile", $fileTree ).removeClass( "lastFile" );
					nodeSelected.addClass( "lastFile" );
					console.log("Moving file controls to lastFile", nodeSelected) ;
					$( ".lastFile", $fileTree ).append( $fileControls );

					_lastFileSelected = nodeSelected;
					
					$( "#quickView" ).off();
					$( "#quickView" ).click( quickView );
				}

			},
			// fall back for users with no right mouse click
			dblclick: function ( event, data ) { },

			renderNode: treeRenderNode,
			lazyLoad: function ( e, data ) {

				console.log( "buildBrowserConfiguration(): lazy load tree" );

				var fancyTreeData = data;

				var requestParms = {
					"serviceName": serviceName,
					"browseId": browseId,
					"fromFolder": fancyTreeData.node.data.location
							+ "/"
				};
				if ( $( '#showDu' ).is(":checked") ) {
					$.extend( requestParms, {
						showDu: "true"
					} );
				}
				if ( $( '#useRoot' ).is(":checked") ) {
					$.extend( requestParms, {
						useRoot: "true"
					} );
				}

				// gets data
				fancyTreeData.result = {
					url: GET_FILES_URL,
					data: requestParms,
					cache: false
				};

			}
		}

		return config;
	}

	function treeExpandNode( event, data ) {
		var fancyTreeData = data;
		var nodeSelected = fancyTreeData.node;
		var nodeData = nodeSelected.data;

		console.log( "expand" );
		$( ".commands" ).hide();
		console.log( "Sort id: "
				+ $( 'input[name=sortRadio]:checked' ).attr(
				'id' ) );
		//
		if ( nodeSelected.folder ) {
			
			$( ".lastOpened", $fileTree ).removeClass( "lastOpened" );
			nodeSelected.addClass( "lastOpened" );
			$( ".lastOpened", $fileTree ).append( $filterControls );
			_lastOpenedFolder = nodeSelected;
			$fileFilter.val( "" );
			$fileFilter.off();
			
			$fileFilter.keyup( function () {
				// 
				clearTimeout( _filterTimer );
				_filterTimer = setTimeout( function () {
					filterLastOpened();
				}, 500 );


				return false;
			} );

		}
		if ( $( 'input[name=sortRadio]:checked' ).attr( 'id' ) == "sortName" ) {
			nodeSelected.sortChildren( titleSort );
		} else {
			nodeSelected.sortChildren( sizeSort );
		}
	}

	function treeRenderNode( event, fancyTreeNode ) {

		var userData = fancyTreeNode.node.data;
		//console.log( "treeRenderNode() ", userData );


		var $description = jQuery( '<div/>', { } );

		var bootRegEx = new RegExp("application.*yml");
		var logRegEx = new RegExp("(.*log)$|(.*txt)$|(catalina.out)$");
		var propertyRegEx = new RegExp("(.*properties)$");
		var shellRegEx = new RegExp("(.*sh)$");
		var compressedRegEx = new RegExp("(.*gz)$|(.*tar)$|(.*zip)$");
		var javaRegEx = new RegExp("(.*jar)$");
		var jsonRegEx = new RegExp("(.*json)$|(.*xml)$|(.*yml)$");
		
		if ( bootRegEx.test( fancyTreeNode.node.title ) ) {
			fancyTreeNode.node.extraClasses= "boot";
		} else if ( logRegEx.test( fancyTreeNode.node.title ) ) {
			fancyTreeNode.node.extraClasses= "logs";
		} else if ( propertyRegEx.test( fancyTreeNode.node.title ) ) {
			fancyTreeNode.node.extraClasses= "properties";
		} else if ( shellRegEx.test( fancyTreeNode.node.title ) ) {
			fancyTreeNode.node.extraClasses= "shell";
		} else if ( compressedRegEx.test( fancyTreeNode.node.title ) ) {
			fancyTreeNode.node.extraClasses= "compressed";
		} else if ( javaRegEx.test( fancyTreeNode.node.title ) ) {
			fancyTreeNode.node.extraClasses= "java";
		} else if ( jsonRegEx.test( fancyTreeNode.node.title ) ) {
			fancyTreeNode.node.extraClasses= "json";
		}
		
		//fancyTreeNode.node.extraClasses= "peter";
		if ( $( '#showMeta:checked' ).length != 0 ) {
			if ( userData.meta != undefined
					&& fancyTreeNode.node.title.indexOf( "meta" ) == -1 ) {
				var metaArray = userData.meta
						.split( "," );

				jQuery( '<span/>', {
					class: "metaTitle",
					text: fancyTreeNode.node.title
				} ).appendTo( $description );

				var $extra = jQuery( '<span/>', {
					class: "meta",
					text: metaArray[1]
				} );
				$extra.appendTo( $description );

				jQuery( '<span/>', {
					class: "metaSize",
					text: metaArray[0]
				} ).prependTo( $extra );
				
				for ( var i=2; i< metaArray.length; i++ ) {
					jQuery( '<span/>', {
						class: "metaExtra",
						text: metaArray[i]
					} ).appendTo( $extra );
				}

				fancyTreeNode.node.setTitle( $description.html() );
				registerNodeActions();

			}
		}

		// logEvent(event, data);

	}

	function filterLastOpened() {
		var fileFilter = $fileFilter.val().trim();
		console.log( "filterLastOpened()", fileFilter );
		if ( _lastOpenedFolder != null ) {
			var kids = _lastOpenedFolder.getChildren();
			for ( var i = 0; i < kids.length; i++ ) {
				var child = kids[i];
				var name = child.data.name;
				//console.log("child title", name)
				if ( fileFilter.length > 0 &&
						!name.toLowerCase().contains( fileFilter.toLowerCase() ) ) {
					child.addClass( "fileHidden" );
				} else {
					child.removeClass( "fileHidden" );
				}
				child.render( );
			}
		}

	}

	function registerNodeActions( ) {

		clearTimeout( _nodeTimer );
//		_nodeTimer = setTimeout( () => {
//
//			$( ".fancytree-title" ).attr( "title", "right mouse click for operations" );
//
//		}, 500 );
	}


	function buildFileTreeForManager() {

		var defaultTree = new Array();

		if ( (serviceName != "null") && (folder == ".") ) {

			defaultTree.push( {
				"title": serviceName + " Property Files           ",
				"name": "property folder",
				"location": "__props__",
				"folder": true,
				"lazy": true,
				"extraClasses": "run",
				"tooltip": diskPathsForTips.propDisk 
			} );

			defaultTree.push( {
				"title": serviceName + " Application Files         ",
				"name": serviceName,
				"location": "__processing__" + "/" + serviceName ,
				"folder": true,
				"cache": false,
				"lazy": true,
				"tooltip": diskPathsForTips.fromDisk
			} );
		} else {
			// handle cross launches
			var variablesTestRegEx = new RegExp("__.*__");
			var csapKey=folder.match(variablesTestRegEx) ;
			console.log("csapKey match: ", csapKey ) ;
			if ( !variablesTestRegEx.test( folder )  ) {
				defaultTree.push( {
					"title": folder,
					"name": folder,
					"location": "__root__" + folder,
					"folder": true,
					"lazy": true,
					"extraClasses": "run"
				} );
			} else {

				defaultTree.push( {
					"title": "..." + folder.substring( csapKey[0].length   ),
					"name": "..." + folder.substring( csapKey[0].length  ),
					"location": folder,
					"folder": true,
					"lazy": true,
					"extraClasses": "run",
					"tooltip": diskPathsForTips.fromDisk
				} );
			}
		}

		defaultTree.push( {
			"title": "CSAP Runtime                          ",
			"name": "processing",
			"location": "__processing__",
			"folder": true,
			"lazy": true,
			"tooltip": diskPathsForTips.processingDisk
		} );

		defaultTree.push( {
			"title": "CSAP Staging                           ",
			"name": "staging",
			"location": "__staging__",
			"folder": true,
			"lazy": true,
			"tooltip": diskPathsForTips.stagingDisk
		} );

		if ( installDisk != homeDisk ) {
			defaultTree.push( {
				"title": "CSAP Install                           ",
				"name": "csapInstall",
				"location": "__staging__/..",
				"folder": true,
				"lazy": true, 
				"tooltip": diskPathsForTips.installDisk
			} );
		}

		defaultTree.push( {
			"title": "CSAP Home                               ",
			"name": "csapHome",
			"location": "__home__",
			"folder": true,
			"lazy": true,
			"tooltip": diskPathsForTips.homeDisk
		} );

		if ( diskPathsForTips.dockerDisk ) { 
			defaultTree.push( {
				"title": "Docker Containers",
				"name": "docker",
				"location": "__docker__",
				"folder": true,
				"lazy": true,
				"extraClasses": "ft_docker",
				"tooltip": diskPathsForTips.dockerDisk
			} );
		}
		
		defaultTree.push( {
			"title": "System: /var/log/",
			"name": "syslogs",
			"location": "__root__/var/log",
			"folder": true,
			"lazy": true,
			"extraClasses": "root"
		} );

		defaultTree.push( {
			"title": "System: /etc/sysconfig/",
			"name": "sysconfig",
			"location": "__root__/etc/sysconfig/",
			"folder": true,
			"lazy": true,
			"extraClasses": "root"
		} );
		
		defaultTree.push( {
			"title": "System: /",
			"name": "RootFS",
			"location": "__root__",
			"folder": true,
			"lazy": true,
			"extraClasses": "root"
		} );

		return defaultTree;
	}


	function buildFileTreeForBrowser() {

		var defaultTree = new Array();


		defaultTree.push( {
			"title": browseId,
			"name": "Browse",
			"location": browseId,
			"folder": true,
			"lazy": true
		} );

		return defaultTree;
	}




	jQuery.fn.center = function () {
		this.css( "position", "absolute" );
		this.css( "top", Math.max( 0,
				(($( window ).height() - $( this ).outerHeight()) / 2)
				+ $( window ).scrollTop() )
				+ "px" );
		this.css( "left", Math.max( 0,
				(($( window ).width() - $( this ).outerWidth()) / 2)
				+ $( window ).scrollLeft() )
				+ "px" );
		return this;
	}

	function performFileOrFolderOperation( node, action, options ) {

		console.log( 'Selected action "' + action + '" on node ' + node );

		switch ( action ) {

			case "quickView":
				quickView() ;
				break;

			case "view":
				console.log( "lastFileNodeSelected: " + _lastNodeSelected );
				var inputMap = {
					fromFolder: _lastNodeSelected.data.location,
					serviceName: serviceName,
					"browseId": browseId,
					hostName: hostName
				};
				postAndRemove( "_blank", contextUrl + "downloadFile/"
						+ _lastNodeSelected.data.name, inputMap );
				break;

			case "download":
				console.log( "lastFileNodeSelected: " + _lastNodeSelected );
				var inputMap = {
					fromFolder: _lastNodeSelected.data.location,
					serviceName: serviceName,
					isBinary: true,
					"browseId": browseId,
					hostName: hostName
				};
				postAndRemove( "_blank", contextUrl + "downloadFile/"
						+ _lastNodeSelected.data.name, inputMap );
				break;

			case "tail":
				var inputMap = {
					fileName: _lastNodeSelected.data.location,
					serviceName: serviceName,
					hostName: hostName
				};
				postAndRemove( "_blank", contextUrl + "FileMonitor", inputMap );
				break;

			case "edit":

				var inputMap = {
					fromFolder: _lastNodeSelected.data.location,
					serviceName: serviceName,
					hostName: hostName
				};
				postAndRemove( "_blank", contextUrl + "editFile", inputMap );
				break;

			case "uploadSimple":

				$( "#extractDir" ).val( _lastNodeSelected.data.location );

				// params= {
				// extractDir : "/aWorkspaces/nov2014/CsAgent/target" ,
				// chownUserid : "root",
				// skipExtract : "on",
				// clusterName : "none",
				// timeoutSeconds: 30
				// 					
				// }

				$( "#uploadToSpan" ).text( _lastNodeSelected.data.location );
				// lastFileNodeSelected.data.name

				$( "#uploadSection" ).show().center();
				break;

			default:
				// var trimmedCommand=data.menuId.substring(1) ;
				// console.log("Invoking: " + trimmedCommand) ;
				// fileCommand(trimmedCommand) ;
				var inputMap = {
					fromFolder: _lastNodeSelected.data.location,
					serviceName: serviceName,
					hostName: hostName,
					command: action
				};
				postAndRemove( "_blank", osUrl, inputMap );
		}
	}

	function logEvent( event, data, msg ) {
		// var args = $.isArray(args) ? args.join(", ") :
		msg = msg ? ": " + msg : "";
		$.ui.fancytree.info( "Event('" + event.type + "', node=" + data.node + ")"
				+ msg );
	}

// Sort folder by item name in alphabetical order
	var titleSort = function ( a, b ) {
		var x = a.title.toLowerCase(), y = b.title.toLowerCase();
		return x === y ? 0 : x > y ? 1 : -1;
	};

// sort folder by item size, largest to smallest
	var sizeSort = function ( a, b ) {
		var x = a.data.size, y = b.data.size;

		// console.log(" x " + x + " y: " + y) ;
		return x === y ? 0 : x > y ? -1 : 1;
	};

} );