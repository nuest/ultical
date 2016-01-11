'use strict';

app.filter('isEmpty', [function() {
	return function(obj) {
		return isEmpty(obj);
	};
}]);

app.filter('notEmpty', [function() {
	return function(obj) {
		return !isEmpty(obj);
	};
}]);

app.filter('url', [function() {
	return function(url) {
		if (isEmpty(url)) {
			return '';
		}
		if (url.indexOf('http') != 0) {
			url = 'http://' + url; 
		}
		return url;
	};
}]);

app.filter('locationObject', ['$translate', function($translate) {
	return function (location) {
		if (isEmpty(location)) {
			return null;
		}

		var loc = {
				id: location.id,
				version: location.version,
				city: '',
				country: '',
				countryCode: '',
				street: '',
				zipCode: '',
				additionalInfo: '',
				longitude: 0,
				latitude: 0,
		};

		var components = [];
		components.push({ id: location.mapBoxId, text: location.text, place_name: location.place_name});
		angular.forEach(location.context, function(component) {
			components.push(component);
		});

		var region = '';
		angular.forEach(components, function(component) {
			if (component.id.indexOf('country') == 0) {
				loc.country = component.text;
				loc.countryCode = component.short_code;
			}
			if (component.id.indexOf('region') == 0) {
				region = component.text;
			}
			if (component.id.indexOf('place') == 0) {
				loc.city = component.text;
			}
			if (component.id.indexOf('postcode') == 0) {
				loc.zipCode = component.text;
			}
			if (component.id.indexOf('address') == 0) {
				loc.street = component.text;

				var completeStreet = component.place_name.split(',')[0];
				var streetParts = completeStreet.split(' ');

				var numbers = [];
				// store the first occurence of number to see if it's before or after the street
				var firstNumberIndex = -1;
				angular.forEach(streetParts, function(streetPart, idx) {
					if (streetPart.match(/\d+/)) {
						// check if there is a number in the street name
						if (streetPart != component.text) {
							numbers.push(streetPart);
							if (firstNumberIndex == -1) {
								firstNumberIndex = idx;
							}
						}
					}
				});

				if (numbers.length == 1) {
					if (firstNumberIndex == 0) {
						// number before street
						loc.street = numbers[0] + ' ' + loc.street;
					} else {
						loc.street += ' ' + numbers[0];
					}
				} else if (numbers.length > 1) {
					// multiple numbers, we take the string as provided by mapbox
					loc.street = completeStreet;
				}
			}
		});

		// add state for us cities
		if (!isEmpty(location.countryCode) && location.countryCode.toLowerCase() == 'us' && !isEmptyString(region)) {
			loc.city += ', ' + region;
		}

		loc.longitude = location.center[0];
		loc.latitude = location.center[1];

		return loc;
	};
}]);

app.filter('location', ['$translate', 'locationObjectFilter', function ($translate, locationObjectFilter) {
	return function (location, type) {
		if (isEmpty(location)) {
			return '';
		}

		if (undefined === type) {
			type = 'full';
		}

		// check if it's a raw location directly from mapbox or an location object
		if (!('city' in location)) {
			location = locationObjectFilter(location);
		}

		return getLocationFromObject(location, type);
	};

	function getLocationFromObject(location, type) {
		var locationString = '';

		if (type == 'full') {
			if (!isEmpty(location.street)) {
				locationString += location.street;
			}
		}

		if (!isEmpty(location.city)) {
			if (!isEmptyString(locationString)) {
				locationString += ', ';
			}
			locationString += location.city;
		}

		if (!isEmpty(location.country)) {
			if (!isEmpty(locationString)) {
				locationString += ', ';
			}
			var countryTranslation = $translate.instant('countries.' + location.countryCode);
			if (countryTranslation != 'countries.' + location.countryCode) {
				locationString += countryTranslation;
			} else {
				locationString += location.country;
			}
		}
		return locationString;
	}
}]);

app.filter('username', [function () {
	return function (user, fullName) {
		if (isEmpty(user)) {
			return '';
		}

		if (undefined === fullName) {
			fullName = false;
		}

		if (user.dfvPlayer == null) {
			return user.email;
		}

		var username = user.dfvPlayer.firstName;
		if (fullName) {
			username += ' ' + user.dfvPlayer.lastName;
		}

		return username;
	};
}]);

app.filter('eventname', ['$translate', 'matchdaynameFilter', function ($translate, matchdaynameFilter) {
	return function (event) {
		if (isEmpty(event)) {
			return '';
		}

		var eventName;

		if (isEmptyString(event.tournamentEdition.alternativeName)) {
			// use tournament format name
			eventName = event.tournamentEdition.tournamentFormat.name;
		} else {
			// this tournament edition uses a different name than the tournament format
			eventName = event.tournamentEdition.alternativeName;
		}

		// this is a multi-matchday-tournament
		if (event.matchdayNumber != -1) {
			var matchday = '';
			eventName += ' ' + event.matchdayNumber + '. ';

			eventName += matchdaynameFilter(event);

		}

		return eventName;
	};
}]);

app.filter('matchdayname', ['$translate', function ($translate) {
	return function (event) {
		if (isEmpty(event)) {
			return '';
		}

		var matchdayName = '';

		if (!isEmptyString(event.tournamentEdition.alternativeMatchdayName)) {
			// this league uses a different name to mark each matchday
			matchdayName += event.tournamentEdition.alternativeMatchdayName;
		} else {
			// this league uses the default matchday translation
			matchdayName += $translate.instant('event.matchday');
		}

		return matchdayName;
	};
}]);

app.filter('season', ['$translate', function($translate) {
	return function(season, fullName) {
		if (isEmpty(season)) {
			return '';
		}
		if (undefined === fullName) {
			fullName = false;
		}

		var seasonString = '';
		if (season.surface == 'GYM') {
			seasonString += $translate.instant(fullName ? 'season.indoorFullName' : 'season.indoor');
		} else {
			seasonString += $translate.instant(fullName ? 'season.outdoorFullName' : 'season.outdoor');
		}
		seasonString += ' ' + season.year;

		if (season.plusOneYear) {
			var yearString = ''+(season.year + 1);
			seasonString += '/' + yearString.substring(2,4);
		}

		return seasonString;
	}
}]);

app.filter('division', ['$translate', function($translate) {
	return function(obj) {
		if (isEmpty(obj) || isEmpty(obj.divisionAge) || isEmpty(obj.divisionType)) {
			return '';
		}

		var divisionString = '';

		var divAgeStr = '';
		if (obj.divisionAge.toLowerCase() != 'regular') {
			divAgeStr = ' ' + $translate.instant('division.' + obj.divisionAge.toLowerCase());
		}

		divisionString += $translate.instant('division.' + obj.divisionType.toLowerCase()) + divAgeStr;

		return divisionString;
	}
}]);