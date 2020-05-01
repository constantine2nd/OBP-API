$(document).ready(function() {
	//fallback for html5 placeholder
	if ( !("placeholder" in document.createElement("input")) ) {
		$("input[placeholder], textarea[placeholder]").each(function() {
			var val = $(this).attr("placeholder");
			if ( this.value == "" ) {
				this.value = val;
			}
			$(this).focus(function() {
				if ( this.value == val ) {
					this.value = "";
				}
			}).blur(function() {
				if ( $.trim(this.value) == "" ) {
					this.value = val;
				}
			})
		});

		// Clear default placeholder values on form submit
		$('form').submit(function() {
			$(this).find("input[placeholder], textarea[placeholder]").each(function() {
				if ( this.value == $(this).attr("placeholder") ) {
					this.value = "";
				}
			});
		});
	}


	// Enforce check of Terms and Conditions (if existing) on signup form
	$('#signup form').submit(function() {
		var agreeTerms = $('#signup #signup-agree-terms input');
		if (agreeTerms.length > 0) {
			if (!agreeTerms.prop('checked')) {
				var msg = 'Please agree to the Terms & Conditions';
				$('#signup #signup-general-error #error').html(msg);
				$('#signup #signup-general-error').removeClass('hide');
				return false;
			}
		}
		return true;
	});

	// Enforce check of Privacy Policy (if existing) on signup form
	$('#signup form').submit(function() {
		var agreePrivacyPolicy = $('#signup #signup-agree-privacy-policy input');
		if (agreePrivacyPolicy.length > 0) {
			if (!agreePrivacyPolicy.prop('checked')) {
				var msg = 'Please agree to the Privacy Policy';
				$('#signup #signup-general-error #error').html(msg);
				$('#signup #signup-general-error').removeClass('hide');
				return false;
			}
		}
		return true;
	});

	// Show sign up errors - FIXME: Change backend to (not) show errors
	var signupError = $('#signup #signup-error #authuser_firstName');
	if (signupError.length > 0 && signupError.html().length > 0) {
		signupError.parent().removeClass('hide');
	}
	var signupError = $('#signup #signup-error #authuser_lastName');
	if (signupError.length > 0 && signupError.html().length > 0) {
		signupError.parent().removeClass('hide');
	}
	var signupError = $('#signup #signup-error #authuser_email');
	if (signupError.length > 0 && signupError.html().length > 0) {
		signupError.parent().removeClass('hide');
	}
	var signupError = $('#signup #signup-error #authuser_username');
	if (signupError.length > 0 && signupError.html().length > 0) {
		signupError.parent().removeClass('hide');
	}
	var signupError = $('#signup #signup-error #authuser_password');
	if (signupError.length > 0 && signupError.html().length > 0) {
		signupError.parent().removeClass('hide');
	}
});
