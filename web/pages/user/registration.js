'use strict';

angular.module('ultical.user', [])

.controller('RegistrationCtrl', ['$scope', 'serverApi', 'CONFIG', '$alert', '$translate',
                                 function($scope, serverApi, CONFIG, $alert, $translate) {

	$scope.minPasswordLength = CONFIG.registration.minPasswordLength;
	$scope.error = {};
	$scope.registrationPending = false;
	
	$scope.user = {
			dob: {day: '01', month: '01', year: '1990' },
			getDob: function() {
				return this.dob.year + '-' + this.dob.month + '-' + this.dob.day;
			}
	};

	$scope.doRegister = function() {
		if (!$scope.registrationForm.$valid) {
			$scope.error.personals = true;
			return;
		}

		if ($scope.user.password != $scope.user.passwordCheck ||
				$scope.user.password.length < CONFIG.general.minPasswordLength) {
			createError('validation_error');
			return;
		}

		var userRequest = {
				email: $scope.user.email,
				password: $scope.user.password,
				firstName: $scope.user.firstname,
				lastName: $scope.user.lastname,
				birthDate: $scope.user.getDob(),
		};

		if ($scope.error.email_not_found) {
			userRequest.dfvEmail = $scope.user.dfvEmail;
		}

		$scope.registrationPending = true;
		
		serverApi.registerUser(userRequest, function(userResponse) {
			$scope.registrationPending = false;

			if (userResponse.status != 'SUCCESS') {
				// registration was not successful
				createError(userResponse.status.toLowerCase());
			} else {
				var alertContent = $translate.instant('user.registration.success.confirmationEmail', { email: userRequest.email });
				if (!isEmpty(userResponse.dfvEmail)) {
					alertContent += ' ' + $translate.instant('user.registration.success.dfvEmail', { dfvEmail: userResponse.dfvEmail });
				}
				$alert({title: $translate.instant('user.registration.success.title'), content: alertContent, container: '#pageAlertSpace', placement: 'top', type: 'success', show: true});

				$scope.$hide();
			}
		});
	};

	function createError(errorType) {
		$scope.error[errorType] = true;
		$alert({title: $translate.instant('user.registration.error.title'), content: $translate.instant('user.registration.error.' + errorType), container: '#registrationModalError', placement: 'top', type: 'danger', show: true});
	}

}]);
