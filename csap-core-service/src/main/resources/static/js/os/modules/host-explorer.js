define( ["dialog-create-container", "explorer-operations"], function ( createContainerDialog, operations ) {

	var $dockerTree;

	// filter controls
	var _filterTimer = null;
	var $filterControls;
	var $fileFilter = null;

	var $dockerApiDeferred = null;


	var _lastOpenedFolder ;

	console.log( "Module loaded" );

	var _initComplete = false;
	return {

		show: function ( ) {
			// lazy
			if ( !_initComplete )
				initialize();
		}
	}

	function initialize() {
		console.log( "Initializing tree" );
		$dockerTree = $( "#dockerTree" );
		
		operations.initialize($dockerTree) ;
		createContainerDialog.initialize();

		$filterControls = $( "#filterControls" );
		$fileFilter = $( "#fileFilter" );

		$dockerTree.fancytree( buildTreeConfiguration() );

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

				console.log( "nodeData.type", nodeData.type );
				operations.restoreControls() ;
				
			
				$( "#jsTemplates" ).append( $filterControls );
			
				// reload values from definition - could optimize and only reset when modified
				if (  nodeData.type == "processes" && nodeData.attributes != undefined) {
					// process rendering will not work when child attributes are closed then re-opened
					console.log("Cached data will still be used.") ;
				} else {
					console.log("Node will be reloaded") ;
					nodeSelected.resetLazy();
				}

			},

			expand: treeExpandNode,

			activate: function ( event, data ) {

				var fancyTreeData = data;
				var nodeSelected = fancyTreeData.node;
				var nodeData = nodeSelected.data;
				lastFileNodeSelected = nodeSelected;


				$( this ).css( "border", "5px" );


				console.log( "activate() last node:", nodeData );

				if ( nodeData.customValue ) {
					$( "#treeLastValue" ).text( nodeData.customValue );
				}

				if ( isContainerNode( nodeData ) ) {

					operations.setCurrentContainer( nodeData, nodeSelected );

				}
				
				if ( isImageNode( nodeData ) ) {

					operations.setCurrentImage( nodeData, nodeSelected );

				}

			},

			renderNode: function ( event, data ) {

				var fancyTreeData = data;
				// console.log( "fancyTreeData: " , fancyTreeData );
				treeRenderNode( fancyTreeData.node );
			},
			lazyLoad: function ( e, data ) {

				var fancyTreeData = data;
				var nodeSettings = fancyTreeData.node.data;


				if ( nodeSettings.attributes ) {

					return data.result = getChildAttributes( fancyTreeData.node.data );

				}

				var requestParms = {
					"csapFilter": nodeSettings.csapFilter
				};

				$dockerApiDeferred = new $.Deferred();

				// More flexible rendering logic
				$.getJSON(
						explorerUrl + "/" + nodeSettings.type,
						requestParms )

						.done( function ( responseJson ) {

							if ( isObject( responseJson ) ) {

								getChildrenFromObject( e, fancyTreeData.node.data, responseJson, $dockerApiDeferred );

							} else {
								getChildren( e, fancyTreeData.node.data, responseJson, $dockerApiDeferred );
							}

						} )

						.fail( function ( jqXHR, textStatus, errorThrown ) {

							handleConnectionError( "Retrieving changes for file " + $( "#logFileSelect" ).val(), errorThrown );
						} );

				data.result = $dockerApiDeferred.promise();
			}
		};

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

			$( ".lastOpened", $dockerTree ).removeClass( "lastOpened" );
			nodeSelected.addClass( "lastOpened" );
			$( ".lastOpened", $dockerTree ).append( $filterControls );
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

	}

	function filterLastOpened() {
		var fileFilter = $fileFilter.val().trim();
		console.log( "filterLastOpened()", fileFilter );
		if ( _lastOpenedFolder != null ) {
			var kids = _lastOpenedFolder.getChildren();
			for ( var i = 0; i < kids.length; i++ ) {
				var child = kids[i];
				var name = child.data.filterValue;
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

	function buildPackageRoot() {

		//console.log( "event", event, "data", data );
		var children = new Array();

		children.push( {
			"title": buildCommentTitle( "Csap Services", "loading", "csapTree" ).html(),
			"type": "services",
			"csapFilter": true,
			"path": "/",
			"folder": true,
			"lazy": true,
			"extraClasses": "ft_wrapper"
		} );

		children.push( {
			"title": buildCommentTitle( "Processes", "loading", "processTree" ).html(),
			"type": "processes",
			"csapFilter": false,
			"path": "/",
			"folder": true,
			"lazy": true,
			"extraClasses": "ft_os"
		} );


		children.push( {
			"title": buildCommentTitle( "Cpu Cores", "loading", "cpuTree" ).html(),
			"type": "cpu",
			"csapFilter": false,
			"path": "/",
			"folder": true,
			"lazy": true,
			"extraClasses": "ft_os"
		} );


		children.push( {
			"title": buildCommentTitle( "Memory", "loading", "memoryTree" ).html(),
			"type": "memory",
			"csapFilter": false,
			"path": "/",
			"folder": true,
			"lazy": true,
			"extraClasses": "ft_os"
		} );

		children.push( {
			"title": buildCommentTitle( "Disk", "loading", "diskTree" ).html(),
			"type": "disk",
			"csapFilter": false,
			"path": "/",
			"folder": true,
			"lazy": true,
			"extraClasses": "ft_db"
		} );


		children.push( {
			"title": buildCommentTitle( "Docker Configuration", "loading", "configTree" ).html(),
			"type": "configuration",
			"path": "/",
			"folder": true,
			"lazy": true,
			"extraClasses": "ft_settings"
		} );

		children.push( {
			"title": buildCommentTitle( "Docker Containers", "loading", "containerTree" ).html(),
			"type": "containers",
			"path": "/",
			"folder": true,
			"lazy": true,
			"extraClasses": "ft_wrapper"
		} );

		children.push( {
			"title": "Will Be Replaced", //,
			"type": "images",
			"path": "/",
			"folder": true,
			"lazy": true,
			"extraClasses": "ft_images"
		} );

		children.push( {
			"title": buildCommentTitle( "Docker Volumes", "loading", "volumeTree" ).html(), //"Docker Volumes",
			"type": "volumes",
			"path": "/",
			"folder": true,
			"lazy": true,
			"extraClasses": "ft_db"
		} );

		return children
	}

	function resolve( childrenArray ) {

		if ( $dockerApiDeferred != null ) {
			$dockerApiDeferred.resolve( childrenArray );
			$dockerApiDeferred = null;
		} else {
			return childrenArray;
		}
	}

	function buildDockerPortsTable( portArray ) {

		console.log( "buildDockerPortsTable", portArray );
		$portTable = $( "#dockerPortsTemplate" ).clone();

		$portBody = $( "tbody", $portTable );

		portArray.forEach( function ( portSetting ) {

			var $row = jQuery( '<tr/>', { } );

			jQuery( '<td/>', { text: portSetting.IP } ).appendTo( $row );

			$publicCol = jQuery( '<td/>', { } );
			$publicCol.appendTo( $row );

			console.log( "window.location.hostname ", window.location.hostname );

			var portUrl = window.location.hostname;
			portUrl = "http://" + portUrl + ":" + portSetting.PublicPort;
			if ( !dockerUrl.contains( "localhost" ) ) {
				portUrl = dockerUrl.substring( 0, dockerUrl.length - 4 ) + portSetting.PublicPort;
				if ( portUrl.startsWith( "tcp" ) ) {
					portUrl = "http" + portUrl.substring( 3 );
				}
			}
			jQuery( '<a/>', {
				href: portUrl,
				class: "simple",
				target: "_blank",
				text: portSetting.PublicPort
			} ).appendTo( $publicCol );


			jQuery( '<td/>', { text: portSetting.PrivatePort } ).appendTo( $row );
			jQuery( '<td/>', { text: portSetting.Type } ).appendTo( $row );

			$row.appendTo( $portBody );

		} );

		return $portTable;
	}
	function moveElementInArray( array, value ) {
		var oldIndex = array.indexOf( value );
		if ( oldIndex > -1 ) {
			var newIndex = 0;

			if ( newIndex < 0 ) {
				newIndex = 0
			} else if ( newIndex >= array.length ) {
				newIndex = array.length
			}

			var arrayClone = array.slice();
			arrayClone.splice( oldIndex, 1 );
			arrayClone.splice( newIndex, 0, value );

			return arrayClone
		}
		return array
	}

	function getChildAttributes( parentFolder ) {

		var attributes = parentFolder.attributes;
		console.log( "attributes", attributes, parentFolder )
		var treeArray = new Array();

		var attributesKeys = Object.keys( attributes );

		attributesKeys = attributesKeys.sort();
		attributesKeys = moveElementInArray( attributesKeys, "Status", 0 )
		attributesKeys = moveElementInArray( attributesKeys, "Create Date", 0 )
		attributesKeys = moveElementInArray( attributesKeys, "Created", 0 )

		//for ( var attributeName in attributes) {

		if ( parentFolder.containerName ) {
			var customLabel = "runtime";
			var customValue = "";
			treeArray.push( {
				"title": customLabel,
				"folder": true,
				"lazy": true,
				"extraClasses": "ft_os",
				// data fields for rendering
				"customLabel": customLabel,
				"customValue": customValue,

				"filterValue": customLabel + "," + customValue,

				"type": "container/info?name=" + parentFolder.containerName,
			} );
		} else if ( parentFolder.imageName ) {
			var customLabel = "settings";
			var customValue = "";
			treeArray.push( {
				"title": customLabel,
				"folder": true,
				"lazy": true,
				"extraClasses": "ft_settings",
				// data fields for rendering
				"customLabel": customLabel,
				"customValue": customValue,

				"filterValue": customLabel + "," + customValue,

				"type": "image/info?name=" + parentFolder.imageName,
			} );
		}

		attributesKeys.forEach( function ( attributeName ) {

			var attributeValue = attributes [attributeName ];
			var childAttributes = null;

			var isFolder = false, isLazy = false;

			var extraClasses = "ft_attribute";


			if ( attributeValue == null || attributeValue == "" ) {
				attributeValue = '<span style="font-style: italic" >not specified</span>'
			} else if ( isArray( attributeValue ) ) {

				if ( attributeValue.length == 0 ) {
					attributeValue = '<span style="font-style: italic" >none</span>'

				} else if ( attributeName == "Ports" ) {
					attributeValue = buildDockerPortsTable( attributeValue ).html();

				} else {
					isFolder = true, isLazy = true;
					extraClasses = "default"
					childAttributes = attributeValue;
					attributeValue = "";
				}
			} else if ( isObject( attributeValue ) ) {

				if ( Object.keys( attributeValue ).length === 0 ) {
					attributeValue = '<span style="font-style: italic" >none</span>'
				} else {
					isFolder = true, isLazy = true;
					childAttributes = attributeValue;
					attributeValue = "";
				}

			}


			// console.log("attribute ", attributeName,  attributeValue) ;

			var title = attributeName + ": " + attributeValue;


			var customLabel = attributeName, customValue = attributeValue;

			if ( isArray( attributes ) && attributes[attributeName].pid ) {
				customLabel = "pid: " + attributes[attributeName].pid;
				customValue = attributes[attributeName].command
				delete attributes[attributeName].command;
			} else if ( isArray( attributes ) && attributes[attributeName].pid ) {

			}

			if ( !isFolder ) {
				customValue = attributeValue;
			}


			// console.log("customLabel ", customLabel,  "customValue", customValue) ;
			treeArray.push( {
				"title": customLabel,
				"folder": isFolder,
				"lazy": isLazy,
				"extraClasses": extraClasses,
				// data fields for rendering
				"customLabel": customLabel,
				"customValue": customValue,

				"filterValue": customLabel + "," + customValue,

				"type": parentFolder.type,
				"attributes": childAttributes
			} );
		} );
		return treeArray;
	}



	function getChildrenFromObject( event, parentFolder, rawObject, $dockerApiDeferred ) {

		console.log( "parentFolder", parentFolder, "rawObject", rawObject );

		var treeArray = new Array();

		var keys = Object.keys( rawObject ).sort();

		keys.forEach( function ( key ) {

			var item = rawObject[ key ];
			var isFolder = false;

			var containerName = null;
			var imageName = null;
			var label = key;
			var value = rawObject[ key ];
			var attributes = null;
			var extraClasses = "ft_attribute";

			if ( key == "DriverStatus" && isArray( value ) ) {

				attributes = new Object();
				value.forEach( function ( driverItem ) {
					attributes[driverItem[0] ] = driverItem[1];
				} );

				value = "";
				isFolder = true;
				extraClasses = "ft_more";

			} else if ( isArray( value ) || isObject( value ) ) {
				attributes = value;
				value = "";
				isFolder = true;
				extraClasses = "ft_more";
				if ( parentFolder.type == "cpu" ) {
					var comment = 'User: ' + item.puser + '%</div>';
					label = buildCommentTitle( item.cpu, comment ).html();
				}
				if ( parentFolder.type == "disk" ) {
					var comment = 'Used: ' + item.used + ", Available: " + item.avail;
					label = buildCommentTitle( label, comment ).html();
				}
			}

			var nodeData = {
				"title": label,
				"folder": isFolder,
				"lazy": isFolder,
				"extraClasses": extraClasses,

				// data fields for rendering
				"customLabel": label,
				"customValue": value,
				"filterValue": label,
				"containerName": containerName,
				"imageName": imageName,

				"type": parentFolder.type,
				"attributes": attributes
			};
			// console.log("nodeData", nodeData ) ;
			treeArray.push( nodeData );
		} );


		return resolve( treeArray )
	}

	// this is done prior to rendering - allowing for sorting - and rendering once.
	function getChildren( event, parentFolder, rawChildrenArray, $dockerApiDeferred ) {

		console.log( "rawChildrenArray", rawChildrenArray );

		if ( !rawChildrenArray ) {
			return resolve( buildPackageRoot() );
		}

		var treeArray = new Array();


		// generate fields in sorted order


		rawChildrenArray.sort( superSort( "label" ) );

		console.log( "sorted", rawChildrenArray );


		//for ( var i = 0; i < rawChildrenArray.length; i++ ) {
		rawChildrenArray.forEach( function ( child ) {
			//console.log("child", child ) ;
			var title = child.label;
			var isFolder = true;
			var isLazy = true;
			var fieldValue = "";
			var extraClasses = "";
			var user = "";
			var filterValue = child.label;
			var containerName = null;
			var imageName = null;


			if ( child.error ) {
				title = "Error: " + child.error;
				extraClasses = "ft_red";
				isFolder = false;
				 isLazy = false;
			} else if ( parentFolder.type == "containers" ) {
				extraClasses = "ft_red";
				containerName = child.label;
				filterValue += "," + child.attributes["Image"];
				if ( child.attributes.Status && child.attributes.Status.startsWith( "Up" ) ) {

					extraClasses = "ft_green";
				}
			} else if ( parentFolder.type == "services" ) {
				extraClasses = getCsapIcon( child.attributes );

			} else if ( parentFolder.type == "processes" ) {
				extraClasses = getProcessIcon( title, child.attributes );
				user = child.attributes[0].user;
				filterValue += "," + user;

			} else if ( parentFolder.type == "processes" ) {
				extraClasses = getProcessIcon( title, child.attributes );
				user = child.attributes[0].user;
				filterValue += "," + user;

			} else if ( parentFolder.type == "images" ) {
				imageName = child.label;
				extraClasses = "ft_db";
			}else if ( parentFolder.type == "volumes" ) {
				extraClasses = "ft_db";
			}


			var nodeData = {
				"title": title,
				"folder": child.folder,
				"lazy": isLazy,
				"extraClasses": extraClasses,

				// data fields for rendering
				"filterValue": filterValue,
				"containerName": containerName,
				"imageName": imageName,

				"user": user,
				"type": parentFolder.type,
				"attributes": child.attributes
			};
			console.log( "nodeData", nodeData );
			treeArray.push( nodeData );
		} );


		return resolve( treeArray )
	}

	function getProcessIcon( title, attributes ) {
		var icon = "ft_os";

		if ( title.contains( "java" ) ) {
			icon = "ft_java";
		}
		return icon;

	}
	function getCsapIcon( attributes ) {
		var icon = "ft_script";


		switch ( attributes.serverType ) {
			case "runtime" :
			case "wrapper" :
				icon = "ft_wrapper";
				break;
			case "csapAgent" :
				serviceImage = '<img src="images/16x16/run3.png"/>';
				break;
			case "database" :
				serviceImage = '<img src="images/16x16/dbSmall.png"/>';
				break;
			case "messaging" :
				serviceImage = '<img src="images/16x16/mail-send-receive.png"/>';
				break;
			case "webServer" :
				serviceImage = '<img src="images/httpd.png"/>';
				break;
			case "docker" :
				icon = "ft_docker";
				break;
			case "monitor" :
			case "os" :
				icon = "ft_os";
				break;
			case "script" :
			case "scripts" :
			case "package" :
				icon = "ft_script";
				break;
			case "SpringBoot":
				icon = "ft_SpringBoot";
				break;
			default:
				icon = "ft_tomcatLatest";
		}
		if ( attributes.serverType != "script" && attributes.cpuUtil && attributes.cpuUtil == "-" ) {
			icon = "ft_stopped";
		}

		return icon;
	}

	function singleSort( property ) {
		var sortOrder = 1;
		if ( property[0] === "-" ) {
			sortOrder = -1;
			property = property.substr( 1 );
		}
		return function ( a, b ) {
			var result = (a[property] < b[property]) ? -1 : (a[property] > b[property]) ? 1 : 0;
			return result * sortOrder;
		}
	}

	function superSort() {
		/*
		 * save the arguments object as it will be overwritten
		 * note that arguments object is an array-like object
		 * consisting of the names of the properties to sort by
		 */
		var props = arguments;
		console.log( "props", props.length, props );
		return function ( obj1, obj2 ) {
			var i = 0, result = 0, numberOfProperties = props.length;
			/* try getting a different result from 0 (equal)
			 * as long as we have extra properties to compare
			 */
			while ( result === 0 && i < numberOfProperties ) {
				result = singleSort( props[i] )( obj1, obj2 );
				i++;
			}
			return result;
		}
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


	function treeRenderNode( element ) {

		var configuration = element.data;
		//console.log( "treeRenderNode", configuration );
		// note - no references to title are used or rendering is broken

		if ( configuration.user && configuration.user != "" ) {
			// process groups
			element.setTitle( buildProcessTitle( configuration ).html() );

		} else if ( configuration.customLabel && configuration.type == "processes" ) {
			element.setTitle( buildAttributeTitle( configuration ).html() )

		} else if ( configuration.customLabel && !element.folder ) {
			element.setTitle( buildAttributeTitle( configuration ).html() )

		} else if ( isContainerNode( configuration ) ) {
			element.setTitle( buildContainerTitle( configuration ).html() )

		} else if ( isImageNode( configuration ) ) {
			element.setTitle( buildImageTitle( configuration ).html() )

		} else if ( configuration.type == "images" && configuration.path == "/" ) {
			element.setTitle( buildRootImageTitle( configuration ).html() )
		}


	}

	function buildRootImageTitle( configuration ) {

		var $description = jQuery( '<div/>', { } );
		var label = "Docker Images";

		$label = jQuery( '<label/>', {
			class: "summary",
			html: label
		} );

		$label.appendTo( $description );


		var $button = jQuery( '<button/>', {
			id: "pullButton",
			class: "custom tree",
			title: "Download (docker pull) new image ..."
		} )

		$button.appendTo( $label );

		jQuery( '<img/>', {
			class: "custom",
			src: baseUrl + "/images/16x16/download.png"
		} ).appendTo( $button );


		jQuery( '<div/>', {
			class: "comment",
			id: "imageTree",
			html: "loading"
		} ).appendTo( $description );

		setTimeout( () => {
			$( "#pullButton" ).click( function () {
				// console.log() ;
				operations.showPullImagePrompt();
			} )
		}, 500 );

		return $description;
	}

	function isContainerNode( configuration ) {
		return configuration.containerName && configuration.containerName != ""
	}

	function isImageNode( configuration ) {
		return configuration.imageName && configuration.imageName != ""
	}

	function buildImageTitle( configuration ) {

		var $description = jQuery( '<div/>', { } );
		var label = configuration.imageName;

		jQuery( '<label/>', {
			class: "image",
			html: label
		} ).appendTo( $description );

		console.log( "label", label, configuration );
		var imageSize = configuration.attributes["Size"] ;
		
		var imageSizeInMb = imageSize/1024/1024;
		if (imageSizeInMb > 100) {
			imageSizeInMb = Math.round( imageSizeInMb );
		} else {
			imageSizeInMb = imageSizeInMb.toFixed(2) ;
		}
		jQuery( '<div/>', {
			class: "empty",
			html: imageSizeInMb + " Mb"
		} ).appendTo( $description );

		return $description;
	}
	
	function buildContainerTitle( configuration ) {

		var $description = jQuery( '<div/>', { } );
		var label = configuration.containerName;

		jQuery( '<label/>', {
			class: "container",
			html: label
		} ).appendTo( $description );

		console.log( "label", label, configuration );
		jQuery( '<div/>', {
			class: "empty",
			html: configuration.attributes["Image"]
		} ).appendTo( $description );

		return $description;
	}

	function buildAttributeTitle( configuration ) {

		var $description = jQuery( '<div/>', { } );
		var label = configuration.customLabel + ":";
		var value = configuration.customValue;

		var valueStyle = "value";
		if ( value == "" ) {
			value = '-';
			valueStyle = "empty";
		}
		jQuery( '<label/>', {
			class: "name",
			html: label
		} ).appendTo( $description );

		jQuery( '<div/>', {
			class: valueStyle,
			html: value
		} ).appendTo( $description );

		return $description;
	}

	function buildProcessTitle( configuration ) {

		//console.log("renderProcess", configuration);

		var $description = jQuery( '<div/>', { } );
		var label = configuration.filterValue;
		var user = configuration.user;

		jQuery( '<label/>', {
			class: "processName",
			html: label
		} ).appendTo( $description );

		jQuery( '<div/>', {
			class: "user",
			html: user
		} ).appendTo( $description );

		//console.log( "$description", $description.html() ) ;

		return $description;
	}

	function buildCommentTitle( label, comment, commentId ) {

		//console.log("renderProcess", configuration);

		var $description = jQuery( '<div/>', { } );

		jQuery( '<label/>', {
			class: "summary",
			html: label
		} ).appendTo( $description );

		jQuery( '<div/>', {
			class: "comment",
			id: commentId,
			html: comment
		} ).appendTo( $description );

		//console.log( "$description", $description.html() ) ;

		return $description;
	}
} );