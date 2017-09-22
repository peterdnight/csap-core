
$( document ).ready( function () {

	var $toolsMenu = $( "#devOps, #tools, #help" );
	$toolsMenu.selectmenu( {
		width: "9em",
		position: { my : "right+45 top+12", at: "bottom right" },
		change: function () {
			var item = $( this ).val();
			if ( item != "default" ) {
				console.log( "launching: " + item );
				CsapCommon.openWindowSafely( item, "_blank" );
				$( "header div.csapOptions select" ).val( "default" )
			}

			$toolsMenu.val( "default" );
			$toolsMenu.selectmenu( "refresh" );
		}

	} );


} );


function launchMenuItem( jqueryObject ) {

	// Menus will not be closed during event, so we schedule
	setTimeout( function () {
		//alert("closing menus") ;
		$( ".deployMenus" ).menu( "collapseAll", null, true );
	}, 100 );

	openWindowSafely( jqueryObject.attr( 'href' ), jqueryObject.text() );
}
