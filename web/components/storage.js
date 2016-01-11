'use strict';

app.factory('storage', ['$filter', 'serverApi', 'authorizer',
                        function($filter, serverApi, authorizer) {

	var returnObject = {
			// init structures
			selected: {
				event: {},
			},

			own: {
				teams: [],
			},
			events: {},
			teams: [],
			seasons: [],

			tournamentEditions: {},
			tournamentFormats: {},

			teamsIndexed: {},
			userIndexed: {},
			playerIndexed: {},
			rosterIndexed: {},
			seasonIndexed: {},

			getEmptyEvent: function() {
				return createEmptyEvent();
			},

			getEmptyEdition: function() {
				return createEmptyEdition();
			},

			getEmptyFormat: function() {
				return createEmptyFormat();
			},

			getTeam: function(teamId, callback) {
				var that = this;
				serverApi.getTeam(teamId, function(team) {
					that.teams[team.id] = team;
					storeTeam(that, team);
					callback(team);
				});
			},

			getAllTeams: function(callback) {
				var that = this;
				callback(that.teams);
				serverApi.getAllTeams(function(teams) {
					that.teams = teams;
					angular.forEach(teams, function(team) {
						storeTeam(that, team);
					});
					callback(that.teams);
				});
			},

			getOwnTeams: function(callback) {
				var that = this;
				callback(that.own.teams);
				serverApi.getOwnTeams(function(teams) {
					that.own.teams = teams;
					angular.forEach(teams, function(team) {
						storeTeam(that, team);
					});
					callback(that.own.teams);
				});
			},

			saveRoster: function(roster, team, callback) {
				serverApi.postRoster(roster, team.id, callback);
			},

			deleteRoster: function(roster, team) {
				serverApi.deleteRoster(roster, function() {
					var rosterDeleteIdx = -1;
					angular.forEach(team.rosters, function(teamRoster, idx) {
						if (teamRoster.id == roster.id) {
							rosterDeleteIdx = idx;
						}
					});
					if (rosterDeleteIdx >= 0) {
						team.rosters.splice(rosterDeleteIdx, 1);
					}
				});
			},

			addPlayerToRoster: function(player, roster, callback) {
				serverApi.addPlayerToRoster(player, roster, function(newPlayer) {
					playerIndexed[newPlayer.id] = newPlayer;
					roster.players.push(newPlayer);
					callback(newPlayer);
				});
			},

			getSeasons: function(callback) {
				var that = this;
				if (isEmpty(this.seasons)) {
					serverApi.getSeasons(function(seasons) {
						that.seasons = seasons;
						angular.forEach(seasons, function(season) {
							storeSeason(that, season);
						});
					});
				} else {
					callback(this.seasons);
				}
			},

			getEvent: function(eventId, callback) {
				this.getEvents(function(events) {
					var foundEvent = null;
					angular.forEach(events, function(event, idx) {
						if (event.id == eventId)
							foundEvent = event;
					});
					callback(foundEvent);
				});

			},

			getEvents: function(callback) {
				var that = this;
				if (isEmpty(this.allEvents)) {
					// make API call
					serverApi.getEvents(function(data) {

						// add some fields
						angular.forEach(data, function(event) {
							storeEvent(that, event);
						});

						callback(that.events);
					});
				} else {
					callback(this.events);
				}
			},

			saveTeam: function(team, callback, activeList) {
				var that = this;
				var oldTeam;
				if (team.id == -1) {
					oldTeam = null;
				} else {
					oldTeam = this.teamsIndexed[team.id];
				}
				serverApi.saveTeam(team, oldTeam, function(savedTeam) {
					that.teamsIndexed[team.id] = savedTeam;
					var index = -1;
					angular.forEach(that.own.teams, function(ownTeam, idx) {
						if (ownTeam.id == savedTeam.id) {
							index = idx;
						}
					});
					if (index >= 0) {
						that.own.teams.splice(index, 1);
					}
					that.own.teams.push(savedTeam);

					index = -1;
					angular.forEach(that.teams, function(existingTeam, idx) {
						if (existingTeam.id == savedTeam.id) {
							index = idx;
						}
					});
					if (index >= 0) {
						that.teams.splice(index, 1);
					}
					that.teams.push(savedTeam);

					storeTeam(that, savedTeam);

					if (activeList == 'own') {
						callback(that.own.teams);
					} else if (activeList == 'all') {
						callback(that.teams);
					}
				});
			},
	}

	function storeTeam(that, team) {
		that.teamsIndexed[team.id] = team;

		if (!isEmpty(team.emails)) {
			team.emails = team.emails.split(',');
		} else {
			team.emails = [];
		}

		angular.forEach(team.admins, function(admin, idx) {
			if (angular.isObject(admin)) {
				storeUser(that, admin);
			} else {
				team.admins[idx] = that.userIndexed[admin];
			}
			team.own = authorizer.getUser() != null && (team.own || admin.id == authorizer.getUser().id);
		});

		angular.forEach(team.rosters, function(roster, idx) {
			if (angular.isObject(roster)) {
				storeRoster(that, roster);
			} else {
				team.rosters[idx] = that.rosterIndexed[roster];
			}
		});
	}

	function storeRoster(that, roster) {
		// add team?
		if (angular.isObject(roster.season)) {
			storeSeason(that, roster.season);
		} else {
			roster.season = that.seasonIndexed[roster.season];
		}

		angular.forEach(roster.players, function(player, idx) {
			if (angular.isObject(player)) {
				storePlayer(that, player);
			} else {
				roster.players[idx] = that.playerIndexed[player];
			}
		});
	}

	function storeSeason(that, season) {
		that.seasonIndexed[season.id] = season;
	}

	function storeUser(that, user) {
		that.userIndexed[user.id] = user;

		if (angular.isObject(user.dfvPlayer)) {
			storePlayer(that, user.dfvPlayer);
		} else {
			user.dfvPlayer = that.playerIndexed[user.dfvPlayer];
		}
	}

	function storePlayer(that, player) {
		that.playerIndexed[player.id] = player;
	}

	function storeEvent(that, event) {
		that.events[event.id] = event;

		if (angular.isObject(event.tournamentEdition)) {
			storeTournamentEdition(that, event.tournamentEdition);
		} else {
			if (event.tournamentEdition in that.tournamentEditions) {
				event.tournamentEdition = that.tournamentEditions[event.tournamentEdition];
			} else {
				alert("Missing an edition: " + event.tournamentEdition);
			}
		}

		var todayDateString = $filter('date')(new Date(), 'yyyy-MM-dd');

		event.tournamentEdition.registrationIsOpen = !isEmpty(event.tournamentEdition.registrationStart) && event.tournamentEdition.registrationStart.string <= todayDateString && event.tournamentEdition.registrationEnd.string >= todayDateString;
		event.tournamentEdition.registrationTime = isEmpty(event.tournamentEdition.registrationStart) ? 'never' : (event.tournamentEdition.registrationStart.string > todayDateString ? 'future' : 'past');

		var hasEditionFee = false;
		var hasEventFee = false;

		angular.forEach(['Player', 'Team', 'Guest'], function(feeType) {
			if (event.tournamentEdition['feePer' + feeType] != 0) {
				hasEditionFee = true;
			}
			if (event['feePer' + feeType] != 0) {
				hasEventFee = true;
			}
		});
		angular.forEach(['Breakfast', 'Lunch', 'Dinner', 'Night'], function(feeType) {
			if (event['feePer' + feeType] != 0) {
				hasEventFee = true;
			}
		});

		event.tournamentEdition.hasFees = hasEditionFee;
		event.hasFees = hasEventFee;
	}

	function storeTournamentEdition(that, edition) {
		that.tournamentEditions[edition.id] = edition;

		if (angular.isObject(edition.tournamentFormat)) {
			storeTournamentFormat(that, edition.tournamentFormat);
		} else {
			if (edition.tournamentFormat in that.tournamentFormats) {
				edition.tournamentFormat = that.tournamentFormats[edition.tournamentFormat];
			} else {
				alert("Missing a format: " + edition.tournamentFormat);
			}
		}
	}

	function storeTournamentFormat(that, format) {
		that.tournamentFormats[format.id] = format;
	}

	function createEmptyEvent() {
		return {
			matchdayNumber: -1,
			location: {},
			startDate: '2016-01-01',
			endDate: '2016-01-03',
			fees: {},
			admins: [],
			localOrganizerName: '',
			localOrganizerEmail: '',
			localOrganizerPhone: '',
			divisionConfirmations: {},
			tournamentEdition: {},
		};
	}

	function createEmptyEdition() {
		return {
			alternativeName: '',
			season: {
				year: 2016,
				surface: 'TURF',
				plusOneYear: false,
			},
			registrationStart: '',
			registrationEnd: '',
			fees: {},
			currency: 'EUR',
			organizerName: '',
			organizerEmail: '',
			organizerPhone: '',
			alternativeMatchdayName: '',
			divisionRegistrations: {},
			events: [],
			tournamentFormat: {},
		};
	};

	function createEmptyFormat() {
		return {
			name: '',
			description: '',
			admins: [],
			editions: [],
		};
	};

	return returnObject;

}]);
