$( document ).ready( function () {

	CsapCommon.configureCsapAlertify();
	var csapEncoder = new CsapEncoder();
	csapEncoder.appInit();
} );

function CsapEncoder() {

	// note the public method
	this.appInit = function () {
		console.log( "Init" );


		$( '#encodeButton' ).click( function () {

			getDataEncoded();
			return false; // prevents link
		} );
		$( '#decodeButton' ).click( function () {
			getDataDecoded();
			return false; // prevents link
		} );

		$( '#replaceButton' ).hide();
		$( '#replaceButton' ).click( function () {

			var orig = $( "#contents" ).val();
			$( "#contents" ).val( replaceText );
			replaceText = orig;
			return false; // prevents link
		} );

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
	};

	function getDataDecoded() {

		$( 'body' ).css( 'cursor', 'wait' );
		$.post( baseUrl + "definition/properties/decode", {
			propertyFileContents: $( "#contents" ).val() ,
			customToken: $( "#customToken" ).val()
			
		} ).done( function ( loadJson ) {
			getDataSuccess( loadJson );
			
		} ).fail( function ( jqXHR, textStatus, errorThrown ) {
			alertify.alert( "Failed Operation: " + jqXHR.statusText, "Only super users can decode" );
		}, 'json' );
	}

	function getDataEncoded() {

		$( 'body' ).css( 'cursor', 'wait' );
		$.post( baseUrl + "definition/properties/encode", {
			propertyFileContents: $( "#contents" ).val(),
			customToken: $( "#customToken" ).val()
		} )

				.done( function ( loadJson ) {
					getDataSuccess( loadJson );
				} )

				.fail( function ( jqXHR, textStatus, errorThrown ) {

					// handleConnectionError("Getting Items in DB", errorThrown);
					alertify.alert( "Failed Operation: " + jqXHR.statusText, "Only admins can generate encoded values" );
				}, 'json' );
	}

	var replaceText = "";

	function getDataSuccess( dataJson ) {

		alertify.notify( "Number of lines processed:"
				+ (dataJson.converted.length + dataJson.ignored.length) );

		var table = $( "#ajaxResults table" ).clone();

		for ( var i = 0; i < dataJson.converted.length; i++ ) {

			var recordObj = dataJson.converted[i];

			var encValue = recordObj.encrypted;
			var wrappedValue = recordObj.encrypted;
			if ( recordObj.decrypted != null ) {
				encValue = recordObj.decrypted;
			} else {
				if ( encValue.indexOf( "ENC(" ) == -1 ) {
					wrappedValue = "ENC(" + recordObj.encrypted + ")";
				}
			}
			var trContent = '<td style="" class="resp">'
					+ recordObj.key
					+ '</td><td style="" class="resp">'
					+ recordObj.original
					+ '</td><td  style="" class="resp">'
					+ encValue + '</td><td  style="" class="resp">'
					+ wrappedValue + '</td>';

			var tr = $( '<tr />', {
				'class': "peter",
				'style': "height: auto;",
				html: trContent
			} );
			$( "tbody", table ).append( tr );
		}

		var message = "Number of lines converted: " + dataJson.converted.length
				+ ", of total: " + (dataJson.converted.length + dataJson.ignored.length) + "<br><br>";
		if ( dataJson.converted.length == 0 ) {
			var trContent = '<td style="padding: 2px;text-align: left">-</td><td style="padding: 2px;text-align: left">No Data Found</td>';
			var tr = $( '<tr />', {
				'class': "peter",
				html: trContent
			} );
			$( "tbody", table ).append( tr );
		}

		replaceText = dataJson.updatedContent;
		$( '#replaceButton' ).show();

		// alertify.alert(message + table.clone().wrap('<p>').parent().html());

		var resultsDialog = alertify.alert( message + table.clone().wrap( '<p>' ).parent().html() );

		var targetWidth = $( window ).outerWidth( true ) - 100;
		var targetHeight = $( window ).outerHeight( true ) - 100;

		resultsDialog.setting( {
			title: "Encryption/Decryption Results",
			resizable: true,
			movable: false,
			width: targetWidth,
			'label': "Close after reviewing output",
			'onok': function () { }


		} ).resizeTo( targetWidth, targetHeight );



		$( 'body' ).css( 'cursor', 'default' );
	}




}
