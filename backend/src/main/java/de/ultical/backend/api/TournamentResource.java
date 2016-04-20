package de.ultical.backend.api;

import java.util.List;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.apache.ibatis.exceptions.PersistenceException;

import de.ultical.backend.app.Authenticator;
import de.ultical.backend.data.DataStore;
import de.ultical.backend.model.DivisionRegistration.DivisionRegistrationStatus;
import de.ultical.backend.model.TeamRegistration;
import de.ultical.backend.model.TournamentEdition;
import de.ultical.backend.model.User;
import io.dropwizard.auth.Auth;

@Path("/tournaments")
public class TournamentResource {

    @Inject
    DataStore dataStore;

    private void checkDataStore() {
        if (this.dataStore == null) {
            throw new WebApplicationException("Dependency injection for datastore failed!",
                    Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<TournamentEdition> getAllTournaments() {
        this.checkDataStore();
        try {
            List<TournamentEdition> result = this.dataStore.getAll(TournamentEdition.class);
            return result;
        } catch (PersistenceException pe) {
            throw new WebApplicationException("Accessing database failed", pe, Status.INTERNAL_SERVER_ERROR);
        }

    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public TournamentEdition storeTournament(final TournamentEdition newEdition) {
        this.checkDataStore();
        try {
            TournamentEdition result = this.dataStore.addNew(newEdition);
            return result;
        } catch (PersistenceException pe) {
            throw new WebApplicationException("Accessing database failed", pe, Status.INTERNAL_SERVER_ERROR);
        }
    }

    @PUT
    @Path("/{editionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public void updateTournament(final @PathParam("editionId") Integer editionId, TournamentEdition edition) {
        this.checkDataStore();
        try {
            if (editionId.equals(edition.getId()) == false) {
                throw new WebApplicationException("Request URL and payload do not match!", Status.NOT_ACCEPTABLE);
            }
            boolean updated = this.dataStore.update(edition);
            if (!updated) {
                throw new WebApplicationException(
                        "Update failed, eventually someone else update the resource before you", Status.CONFLICT);
            }

        } catch (PersistenceException pe) {
            throw new WebApplicationException("Accessing database failed", pe, Status.INTERNAL_SERVER_ERROR);
        }
    }

    /*
     * REGISTRATION
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/division/{divisionRegistrationId}/register/team")
    public TeamRegistration registerTeam(@PathParam("divisionRegistrationId") Integer divisionRegistrationId,
            TeamRegistration teamRegistration, @Auth @NotNull User currentUser) throws Exception {

        this.checkDataStore();

        try (AutoCloseable c = this.dataStore.getClosable()) {
            Authenticator.assureRosterAdmin(this.dataStore, teamRegistration.getRoster().getId(), currentUser);

            teamRegistration.setPaid(false);
            teamRegistration.setStatus(DivisionRegistrationStatus.PENDING);
            teamRegistration.setNotQualified(false);
            teamRegistration.setSpiritScore(-1);
            teamRegistration.setSequence(-1);
            teamRegistration.setStanding(-1);

            return this.dataStore.registerTeamForEdition(divisionRegistrationId, teamRegistration);
        } catch (PersistenceException pe) {
            throw new WebApplicationException("Probably duplicate entry" + pe.getMessage(), Status.CONFLICT);
        }
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/registration/{eventId}/{teamRegistrationId}")
    public void updateTeamRegistration(@PathParam("teamRegistrationId") Integer teamRegistrationId,
            @PathParam("eventId") Integer eventId, TeamRegistration teamRegistration, @Auth @NotNull User currentUser)
                    throws Exception {

        this.checkDataStore();

        try (AutoCloseable c = this.dataStore.getClosable()) {

            TournamentEdition edition = this.dataStore.getEditionByTeamRegistration(teamRegistration.getId());

            if (eventId > 0) {
                if (!edition.isAllowEventTeamRegManagement()) {
                    throw new WebApplicationException("You are not allowed to make those changes", Status.FORBIDDEN);
                }
                Authenticator.assureEventAdmin(this.dataStore, eventId, currentUser);
            } else {
                Authenticator.assureEditionAdmin(this.dataStore, edition.getId(), currentUser);
            }

            this.dataStore.update(teamRegistration);

        } catch (PersistenceException pe) {
            throw new WebApplicationException("Error writing update" + pe.getMessage(), Status.CONFLICT);
        }
    }
}
