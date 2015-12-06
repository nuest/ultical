package de.ultical.backend.api;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import de.ultical.backend.app.DfvApiConfig;
import de.ultical.backend.app.UltiCalConfig;
import de.ultical.backend.data.DataStore;
import de.ultical.backend.model.ApiDfvMvName;
import de.ultical.backend.model.ApiRegisterRequest;

/**
 * Temporary resource to init server
 *
 * @author bas
 *
 */
@Path("/init")
public class TempInitResource {

	@Inject
	private Client client;

	private DfvApiConfig dfvApi;

	@Inject
	private DataStore dataStore;

	public TempInitResource(UltiCalConfig conf) {
		this.dfvApi = conf.getDfvApi();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public boolean apiRegisterRequest(ApiRegisterRequest apiRegisterRequest) {
		System.out.println("Init DFV API");

		WebTarget target = this.client.target(this.dfvApi.getUrl()).path("profile").queryParam("token", this.dfvApi.getToken()).queryParam("secret", this.dfvApi.getSecret());

		Invocation.Builder invocationBuilder = target.request(MediaType.APPLICATION_JSON);

		@SuppressWarnings("unchecked")
		List<ApiDfvMvName> response = invocationBuilder.get(ArrayList.class);
		System.out.println("out ? " + response.get(1).getVorname() + " - " + response.get(1).getNachname());
		ApiDfvMvName a = response.get(1);
		System.out.println(a);
		this.dataStore.refreshDfvNames(response);
		return true;
	}

}