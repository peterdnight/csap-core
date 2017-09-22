define( ["./json-forms", "./service-edit", "./settings-edit", "./dialog-cluster-edit"], function ( jsonForms, serviceEdit, settingsEdit, clusterEditor ) {

	var _panelId = "#jsonFileBrowser";
	var $jsonBrowser = $( _panelId );
	var $packageSelected = $( '.releasePackage' );
	var $definition = null;
	var _definition = null;
	var _isTreeUpdated = false;

	var _lastPathSelected = null;

	console.log( "Module loaded" );

	var clusterKeys = new Array( "multiVmPartition", "singleVmPartition", "multiVm", "version" );

	var displayNames = {
		jvms: "Services: Application Servers",
		osProcesses: "Services: OS",
		clusterDefinitions: "Clusters By Lifecycle",
		capability: "Attributes: Capability",
		packageDefinition: "Attributes: Package",
	}

	var _initComplete = false;
	return {
		show: function ( definition, $definitionTextArea ) {
			_definition = definition;
			$definition = $definitionTextArea;
			show();
		},

		reset: function ( definition ) {

			if ( !_initComplete )
				return;
			//https://github.com/mar10/fancytree/wiki/TutorialLoadData
			console.log( "reloading tree" );
			_definition = definition;
			openFirstItem();

		}

	}

	function showTreeUpdateWarnings() {

		if ( _isTreeUpdated ) {
			setTimeout( () => {

				alertify.alert(
						"Release package has been updated. Operations in dialog will overwrite."
						+ "Alternately - close dialog and apply or checkin changes."
						);

			}, 1000 );
		}
	}

	function openFirstItem() {
		
		try {
			var tree = $jsonBrowser.fancytree( 'getTree' );
			tree.reload( getChildren() );
			//tree.getFirstChild().setExpanded( true ); // http://wwwendt.de/tech/fancytree/doc/jsdoc/FancytreeNode.html
			//tree.getFirstChild().getNextSibling().setExpanded( true );
			tree.getFirstChild().setExpanded( true );
	//		expandFolder( "packageDefinition" ) ;
	//		expandFolder( "clusterDefinitions" ) ;
	//		expandFolder( "capability" ) ;
		} catch ( e ) {
			//alertify.alert( message );
			console.log("tree not found", e) ;
			setTimeout( function() {
				openFirstItem();
			}, 100 );
		}
	}

	function buildPackageRoot() {

		//console.log( "event", event, "data", data );
		var children = new Array();

		var selectedFileName = __packageToFileMap[ $packageSelected.val() ];

		children.push( {
			"title": selectedFileName,
			"name": "property folder",
			"location": jsonForms.getRoot(),
			"folder": true,
			"lazy": true,
			"icon": baseUrl + "/images/text-x-script.png"
		} );
		return children
	}
//	
	function expandFolder( location ) {

		var tree = $jsonBrowser.fancytree( 'getTree' );
		var node = tree.findFirst( function ( node ) {
			return findByPath( node.data, location );
		} );
		if ( node )
			node.setExpanded( true );
	}

	function findByPath( treeData, path ) {
		//console.log("findByPath", treeData, path) ;
		return treeData.location == path;
	}
	function show() {
		init();
		$jsonBrowser.show();

		console.log( "Draw" );
	}

	function openInText() {
		console.log( "Launching to text view", _lastPathSelected );
		$( "#tabs" ).tabs( "option", "active", 4 );
	}

	function definitionUpdated() {
		console.log( "definitionUpdated ", _definition );
		$definition.val( JSON.stringify( _definition, null, "\t" ) );
	}
	function init() {
		if ( _initComplete )
			return;



		jsonForms.registerForChangeNotification( definitionUpdated );
		$( ".openInTextButton" ).click( openInText );

		_initComplete = true;
		console.log( "Running init" );

		$jsonBrowser.fancytree( buildTreeConfiguration() );


		openFirstItem();

	}

	function buildTreeConfiguration() {

		var config = {
			source: getChildren(),
			keyboard: false,

			collapse: function ( event, data ) {

				var fancyTreeData = data;
				var nodeSelected = fancyTreeData.node;
				var nodeData = nodeSelected.data;
				//var versionNode = findFirst 
				console.log( "collapse", nodeData );

				if ( nodeData.location == jsonForms.getRoot() ) {
					$( "#jsonFileContainer" ).append( $definition );
				}

				// reload values from definition - could optimize and only reset when modified
				nodeSelected.resetLazy();

			},
			expand: function ( event, data ) {

				var fancyTreeData = data;
				var nodeSelected = fancyTreeData.node;
				var nodeData = nodeSelected.data;
				//var versionNode = findFirst 
				console.log( "expand" );

				//nodeSelected.sortChildren(elementSort );

				_lastPathSelected = nodeData.location;

			},
			activate: function ( event, data ) {

				var fancyTreeData = data;
				var nodeSelected = fancyTreeData.node;
				var nodeData = nodeSelected.data;
				lastFileNodeSelected = nodeSelected;


				$( this ).css( "border", "5px" );


				console.log( "last node:", nodeData );
				_lastPathSelected = nodeData.location;

			},

			renderNode: function ( event, data ) {

				var fancyTreeData = data;
				//console.log( "event: " + event.type );
				renderValue( fancyTreeData.node );
			},
			lazyLoad: function ( e, data ) {

				//console.log( "lazyload", e, data );

				var fancyTreeData = data;
				
				data.result = getChildren( e, fancyTreeData.node.data );
			}
		};
		
		return config;
	}



	function getDefinition() {
		console.log( "getDefinition()" );
		return _definition;
	}
	function renderValue( element ) {
		console.log( "renderValue", element.data.fieldName, element.data );
		// note - no references to title are used or rendering is broken

		var $description = jQuery( '<div/>', { } );
		var label = element.data.fieldName;
		var value = element.data.fieldValue;
		var path = element.data.location;
		var cssClass = "tedit";

		var isService = false;
		if ( element.folder &&
				((path == "jvms." + label) ||
						(path == "osProcesses." + label)) ) {
			isService = true;
		}

		var isSettings = false;
		var settingsRegEx = new RegExp( "clusterDefinitions.*settings" ); // strip out . for css
		var isCluster = false;
		var clusterRegEx = new RegExp( "clusterDefinitions\.[a-z0-9]*\." + label, "i" ); // strip out . for css
		var lifecycle = ""
		if ( element.folder &&
				(path.match( settingsRegEx )) ) {
			isSettings = true;
			lifecycle = path.split( "." )[1];
		} else if ( element.folder &&
				(path.match( clusterRegEx )) ) {
			isCluster = true;
			lifecycle = path.split( "." )[1];
		}

		if ( element.folder && path == "clusterDefinitions." + label ) {

			value = label;
			label = "Lifecycle: ";
			cssClass = "folderDesc"

		} else if ( isService || isSettings || isCluster ) {
			label = element.data.fieldName;

		} else if ( !element.folder ) {
			label = element.data.fieldName;

		} else {
			console.log( "path", path, "label", label );
			return;
		}

		if ( path == jsonForms.getRoot() ) {
			//if ( false ) {

			var rootContainerId="rootJsonEditorForjson" ;
			console.log( "renderValue() - showing root json document")
			jQuery( '<div/>', {
				id: rootContainerId,
				class: "treeText",
				spellcheck: false,
				"data-path": path
			} ).appendTo( $description );

			element.setTitle( $description.html() );

			// need to delay for rendering
			setTimeout( () => {

				// relocating definition - when this collapses - def is gone
				$( "#" + rootContainerId ).append( $definition );

				addLineNumbers( $definition );
				$(".rootEditing")
							.css("background-color", "yellow")
							.text("Document tree will reload following any changes");
					
				$definition.change( function() {
					$("#treeTab").append( $(".linedwrap") ) ;
					// $jsonBrowser.hide(); 
					_definition = JSON.parse( $definition.val() ); ;
					openFirstItem() ;
					// 
				}) ;

			}, 500 );
			// 


		} else if ( isCluster ) {

			jQuery( '<label/>', {
				text: label
			} ).appendTo( $description );

			var $button = jQuery( '<button/>', {
				title: "Click to open cluster editor",
				class: "treeClusterButton custom",
				"data-clusterlife": lifecycle,
				"data-clustername": label,
			} ).appendTo( $description );

			element.setTitle( $description.html() );

			setTimeout( () => {

				registerClusterForm(
						$( ".treeClusterButton[data-clustername='" + label + "']",
								$jsonBrowser ) );
			}, 500 );


		} else if ( isSettings ) {

			jQuery( '<label/>', {
				text: label
			} ).appendTo( $description );

			var $button = jQuery( '<button/>', {
				title: "Click to open settings editor",
				class: "treeSettingsButton custom",
				"data-lifecycle": lifecycle
			} ).appendTo( $description );

			element.setTitle( $description.html() );

			setTimeout( () => {

				registerSettingsForm(
						$( ".treeSettingsButton[data-lifecycle='" + lifecycle + "']",
								$jsonBrowser ) );
			}, 500 );


		} else if ( isService ) {

			jQuery( '<label/>', {
				text: label
			} ).appendTo( $description );

			var $button = jQuery( '<button/>', {
				title: "Click to open service editor",
				class: "treeServiceButton custom",
				"data-service": label
			} ).appendTo( $description );

			element.setTitle( $description.html() );

			setTimeout( () => {

				registerServiceForm(
						$( ".treeServiceButton[data-service='" + label + "']",
								$jsonBrowser ) );
			}, 500 );


		} else if ( isArray( value ) || isObject( value ) ) {
			//if ( false ) {

			var editorId = "jsonEdit" + path.replace( /\./g, "_" );
			jQuery( '<textarea/>', {
				id: editorId,
				class: "treeText",
				spellcheck: false,
				"data-path": path
			} ).appendTo( $description );

			element.setTitle( $description.html() );

			setTimeout( () => {

				jsonForms.loadValues( _panelId, _definition );
				// 

				jsonForms.configureJsonEditors(
						getDefinition, "fitContent", _panelId, '#json' );
				addLineNumbers( $( '#' + editorId ) );
			}, 500 );
//				jsonForms.configureJsonEditors( 
//						getDefinition, "fitContent", _panelId, '#json' );

		} else {

			jQuery( '<label/>', {
				text: label
			} ).appendTo( $description );

			jQuery( '<span/>', {
				class: cssClass,
				"data-path": path,
				text: value
			} ).appendTo( $description );

			element.setTitle( $description.html() );

			setTimeout( () => {

				registerClickEdits( $( ".tedit[data-path='" + element.data.location + "']", $jsonBrowser ) );
			}, 500 );
		}


	}


	function registerClusterForm( $clusterButton ) {
		$clusterButton.click( function () {
			var lifeEdit = $clusterButton.data( "clusterlife" ).trim();
			var clusterName = $clusterButton.data( "clustername" ).trim();
			var relPkg = $packageSelected.val();

			console.log( "\n\n registerClusterForm(): launching: " + clusterEditUrl
					+ " lifeEdit: " + lifeEdit + " clusterName: " + clusterName + " releasePackage: " + relPkg );

			var params = {
				releasePackage: relPkg,
				lifeToEdit: lifeEdit,
				clusterName: clusterName
			}

			//clusterEditor.setRefresh( _refreshFunction )


			$.get( clusterEditUrl,
					params,
					clusterEditor.show,
					'html' );

			showTreeUpdateWarnings();
			return false;
		} );
	}

	function registerSettingsForm( $settingsButton ) {
		$settingsButton.click( function () {
			var lifeEdit = $settingsButton.data( "lifecycle" ).trim();

			console.log( "\n registerSettingsForm(): launching: " + settingsEditUrl + " lifeEdit: " + lifeEdit );

			var params = {
				lifeToEdit: lifeEdit
			}

			$.get( settingsEditUrl,
					params,
					settingsEdit.showSettingsDialog,
					'html' );
			showTreeUpdateWarnings();
			return false;
		} );
	}

	function registerServiceForm( $serviceButton ) {
		$serviceButton.click( function () {
			var targetService = $serviceButton.data( "service" ).trim();
			console.log( "\n registerServiceForm(): launching: "
					+ serviceEditUrl + " hostName: " + hostName
					+ " serviceName: " + targetService );

			// global used in service portal
			serviceName = targetService;
			var params = {
				releasePackage: $packageSelected.val(),
				serviceName: targetService,
				hostName: "*"
			}
			$.get( serviceEditUrl,
					params,
					serviceEdit.showServiceDialog,
					'html' );
			showTreeUpdateWarnings();
			return false;
		} );
	}

	function registerClickEdits( $clickableValue ) {

		$clickableValue.off();
		$clickableValue.click( function () {
			var content = $( this ).text();
			var path = $( this ).data( "path" );
			var id = $( this ).data( "id" );
			var type = $( this ).data( "type" );

			$( this ).off();
			$( this ).empty();
			_isTreeUpdated = true;
			var $editValueContainer = jQuery( '<input/>', {
				class: "",
				"data-id": id,
				"data-path": path,
				value: content
			} );
			$( this ).append( $editValueContainer );
			$editValueContainer.change( function () {
				//jsonForms.updateDefinition( _definition, $( this ) );
				updateDefinition( $( this ) );
			} );
		} );
	}

	function updateDefinition( $inputThatChanged ) {

		var jsonPath = $( $inputThatChanged ).data( "path" );
		console.log( "modified: " + jsonPath );

		//var definitionJson = JSON.parse( $definition.val() );

		jsonForms.updateDefinition( _definition, $inputThatChanged );
		$definition.val( JSON.stringify( _definition, null, "\t" ) );
	}

	function addLineNumbers( $editor ) {
		var displayHeight = Math.round( $( window ).outerHeight( true ) ) - 200;
		var displayWidth = Math.round( $jsonBrowser.outerWidth( true ) ) - 100;
		var scrollHeight = $editor.prop( 'scrollHeight' );

		if ( displayHeight > scrollHeight ) {
			displayHeight = scrollHeight + 50;
		}
		setTimeout( () => {
			$editor.linedtextarea();
			var $container = $editor.parent().parent();
			var $containerParent = $container.parent();
			$container.css( "height", displayHeight );
			$( ".lines", $container ).css( "height", displayHeight );
			$editor.css( "height", displayHeight - 20 );
			$container.css( "width", displayWidth );

			// shift editors to left tab
			$containerParent.css( "position", "relative" );
			$containerParent.css( "display", "inline-block" );
			$containerParent.css( "left", 50 - Math.round( $container.offset().left ) );
			$editor.css( "width", displayWidth - 80 );
		}, 500 );

	}

	function getChildren( event, parentFolder ) {

		var parentLocation = "";
		var subTree = _definition;
		if ( parentFolder ) {
			if ( parentFolder.location != jsonForms.getRoot() ) {
				parentLocation = parentFolder.location;
				subTree = jsonForms.getValue( parentLocation, _definition );
				parentLocation += ".";
			}
		} else {
			return buildPackageRoot();
		}
		//console.log( "getChildren: parentFolder", parentFolder, "parentLocation", parentLocation, "subTree", subTree );
		//console.log( "getChildren: event", event );

		var defaultTree = new Array();

		//if ( isArray( subTree ) ) {
		if ( parentFolder && parentFolder.jsonEdit ) {
			console.log( "Adding json editor", parentFolder.location );
			defaultTree.push( {
				"title": "jsonContent",
				"folder": false,
				"lazy": false,
				"icon": baseUrl + "/images/text-x-script.png",
				//"icon": "/dummy",
				// data fields for rendering
				"fieldName": "jsonContent",
				"fieldValue": subTree,
				"location": parentFolder.location,
				"sortByField": "zzzzzz"
			} );

			return defaultTree;
		}

		// generate fields in sorted order
		var sortedFields = sortElements( subTree, parentLocation );

		for ( var i = 0; i < sortedFields.length; i++ ) {
			var field = sortedFields[i];
			var fieldLocation = parentLocation + field;
			var fieldTree = jsonForms.getValue( fieldLocation, _definition );
			//console.log("fieldLocation", fieldLocation, "fieldTree", fieldTree) ;

			var isFolder = true;
			var isLazy = true;
			var fieldValue = "";
			if ( isString( fieldTree ) ) {
				isFolder = false;
				isLazy = false;
				fieldValue = fieldTree;
			}

			var extraClasses = "";

			if ( field == "jvms" || field == "jvmPorts" ) {
				extraClasses = "ft_java";
			} else if ( field == "osProcesses" || field == "osProcessesList" ) {
				extraClasses = "ft_os";
			} else if ( fieldTree.server ) {
				extraClasses = getServerIconStyle( fieldTree.server, fieldTree.metaData );
			} else if ( field == "settings" ) {
				extraClasses = "ft_settings";
			} else if ( field == "parameters" ) {
				extraClasses = "ft_settings";
			} else if ( field.toLowerCase().contains( "metrics" ) ) {
				extraClasses = "ft_performance";
			} else if ( field.toLowerCase().contains( "monitor" ) ) {
				extraClasses = "ft_monitor";
			} else if ( !isFolder ) {
				extraClasses = "default";
			}

			defaultTree.push( {
				"title": displayName( field ),
				"folder": isFolder,
				"lazy": isLazy,
				"extraClasses": extraClasses,
				// data fields for rendering
				"fieldName": field,
				"fieldValue": fieldValue,
				"location": fieldLocation
			} );
		}

		if ( parentFolder ) {
			console.log( "Adding editor entry", parentFolder.location );
			
			var description="<span>View Source</span>";
			if ( parentFolder.location && parentFolder.location == jsonForms.getRoot() ) {
				description = "<span class='rootEditing'>View Source</span>" ;
			}
			defaultTree.push( {
				"title": description,
				"folder": true,
				"lazy": true,
				"extraClasses": "ft_jsonedit",
				// data fields for rendering
				"jsonEdit": true,
				"fieldName": "...",
				"fieldValue": subTree,
				"location": parentFolder.location,
				"sortByField": "zzzzzz"
			} );

		}

		return defaultTree
	}


	function displayName( attributeName ) {

		if ( displayNames[attributeName] )
			return displayNames[attributeName];
		return attributeName;
	}

	function sortElements( subTree, parentLocation ) {

		console.log( "sortElements", parentLocation );

		var sortedFields = new Array();
		if ( parentLocation == "" ) {
			for ( var field in subTree )
				sortedFields.push( field );
			return sortedFields;
		}
		// generate fields in sorted order

		for ( var field in subTree ) {

			var fieldLocation = parentLocation + field;
			var fieldTree = jsonForms.getValue( fieldLocation, _definition );
			var sortValue = field;
			if ( !isString( fieldTree ) ) {
				sortValue = "zz" + field;
			}
			if ( field == "settings" ) {
				sortValue = "aaSettings"
			} else if ( field == "parameters" ) {
				sortValue = "aa" + field
			} else if ( field == "server" ) {
				sortValue = "aaa" + field
			}
			sortedFields.push( { field: field, sortByField: sortValue } );
		}

		sortedFields = sortedFields.sort( fieldSort );
		// console.log("sortedFields", sortedFields) ;

		return sortedFields.map( function ( a ) {
			return a.field
		} );
	}

	function fieldSort( a, b ) {
		//console.log("sorting: " ,a.sortByField ) 
		var x = a.sortByField.toLowerCase(), y = b.sortByField.toLowerCase();
		return x === y ? 0 : x > y ? 1 : -1;
	}
	;

	function getServerIconStyle( server, metaData ) {

		var style = server;

		if ( server.contains( "cssp" ) ||
				server.contains( "tomcat" ) ) {
			style = "tomcatEol";
		}

		switch ( server ) {
			case "tomcat8-5":
				style = "tomcatLatest";
				break;
		}

		if ( metaData ) {
			if ( metaData.contains( "isDataStore" ) ) {
				style = "db";
			}
			if ( metaData.contains( "isJms" ) ) {
				style = "jms";
			}
			if ( metaData.contains( "isScript" ) ) {
				style = "script";
			}
		}

		return "ft_" + style;
	}

	function isObject( o ) {
		return Object.prototype.toString.call( o ) == '[object Object]';
	}
	function isArray( o ) {
		return Object.prototype.toString.call( o ) == '[object Array]';
	}
	function isBoolean( o ) {
		return Object.prototype.toString.call( o ) == '[object Boolean]';
	}
	function isNumber( o ) {
		return Object.prototype.toString.call( o ) == '[object Number]';
	}
	function isString( o ) {
		return Object.prototype.toString.call( o ) == '[object String]';
	}

} );