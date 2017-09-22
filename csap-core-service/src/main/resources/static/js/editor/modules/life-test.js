require( ["life-edit"], function ( lifeEdit ) {

	console.log( "\n\n ************** _main: loaded *** \n\n" );

	// Shared variables need to be visible prior to scope

	$( document ).ready( function () {
		initialize();
	} );


	function initialize() {

		$( '#updateServiceButton' ).click( function () {
			serviceEdit.updateServiceDefinition();
		} );
		$( '#validateServiceButton' ).click( function () {
			serviceEdit.validateServiceDefinition();
		} );
		
		$( '#showServiceDialog' ).click( function () {
			// alertify.notify("Getting clusters") ;
			$.get( serviceEditUrl,
					serviceEdit.showServiceDialog,
					'html' );
			return false;
		} );
		
		 $( ".releasePackage" ).selectmenu( {
			change: function () {
				lifeEdit.showSummaryView( "dev" , $("#lifeEditorWrapper"), $(this).val() ) 
			}
		} );
		// for testing page without launching dialog

		//serviceEdit.configureForTest() ;
//		$( ".lifeSelection" ).selectmenu( {
//			width: "200px",
//			change: function () {
//				console.log( "showLifeEditor editing env: " + $( this ).val() );
//			}
//		} );
		lifeEdit.showSummaryView( "dev" , $("#lifeEditorWrapper"),  $( ".releasePackage" ).val()) ;
	}

} );


