/**
 * Copyright (C) 2015-2016 ultical contributors
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * If the program is linked with libraries which are licensed under one of the
 * following licenses, the combination of the program with the linked library is
 * not considered a "derivative work" of the program:
 *
 * * Apache License, version 2.0
 * * Apache Software License, version 1.0
 * * Mozilla Public License, versions 1.0, 1.1 and 2.0
 * * Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed under
 * the aforementioned licenses, is permitted by the copyright holders if the
 * distribution is compliant with both the GNU Affero General Public License
 * version 3 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the  GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package de.ultical.backend.app;

import java.time.LocalDate;
import java.util.EnumSet;

import javax.mail.Session;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.ws.rs.client.Client;

import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;

import de.spinscale.dropwizard.jobs.JobsBundle;
import de.ultical.backend.api.AuthResource;
import de.ultical.backend.api.ClubResource;
import de.ultical.backend.api.ContextResource;
import de.ultical.backend.api.DfvMvNameResource;
import de.ultical.backend.api.DivisionResource;
import de.ultical.backend.api.EventsResource;
import de.ultical.backend.api.MailResource;
import de.ultical.backend.api.RegisterResource;
import de.ultical.backend.api.RosterResource;
import de.ultical.backend.api.SeasonResource;
import de.ultical.backend.api.SitemapResource;
import de.ultical.backend.api.TeamResource;
import de.ultical.backend.api.TournamentFormatResource;
import de.ultical.backend.api.TournamentResource;
import de.ultical.backend.api.UserResource;
import de.ultical.backend.app.logging.UlticalLoggingFilter;
import de.ultical.backend.data.DataStore;
import de.ultical.backend.data.LocalDateMixIn;
import de.ultical.backend.data.mapper.UserMapper;
import de.ultical.backend.model.User;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.CachingAuthenticator;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.auth.basic.BasicCredentials;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;

public class Application extends io.dropwizard.Application<UltiCalConfig> {

    public static final int STARTUP_RETRY_PAUSE_SECS = 5;
    public static final int STARTUP_RETRY_ATTEMTPS = 12;

    public static void main(String[] args) throws Exception {
        Application ultiCal = new Application();
        
        int retryCounter = 1;
        while(retryCounter <= STARTUP_RETRY_ATTEMTPS) {
            try {
                ultiCal.run(args);
                break;
            }
            catch(java.net.ConnectException | java.util.concurrent.RejectedExecutionException | org.eclipse.jetty.util.MultiException e) {
                System.out.println("Error connecting to database, retrying in " + STARTUP_RETRY_PAUSE_SECS + " seconds. " + 
                    retryCounter + "/" + STARTUP_RETRY_ATTEMTPS + "\n\t" + e.getMessage());
                Thread.sleep(STARTUP_RETRY_PAUSE_SECS * 1000);
                retryCounter++;
            }
        }
        
        System.out.println("Started ultical!");
    }

    @Override
    public void initialize(Bootstrap<UltiCalConfig> bootstrap) {
        super.initialize(bootstrap);

        ObjectMapper objectMapper = bootstrap.getObjectMapper();
        objectMapper.addMixIn(LocalDate.class, LocalDateMixIn.class);

        // add Jobs bundle to provide schedules tasks
        bootstrap.addBundle(new JobsBundle("de.ultical.backend.jobs"));
    }

    @Override
    public void run(UltiCalConfig config, Environment env) throws Exception {

        ManagedDataSource mds = config.getDatabase().build(env.metrics(), "UltiCal DataSource");
        env.lifecycle().manage(mds);
        /*
         * We create a MyBatisManager and register it with the
         * dropwizard-lifecylce system. This ensures that MYBatis is started,
         * when the dropwizard environment starts and stopped accordingly.
         */
        final MyBatisManager mbm = new MyBatisManager(mds);
        env.lifecycle().manage(mbm);
        env.jersey().register(new AbstractBinder() {

            @Override
            protected void configure() {
                /*
                 * we use the MyBatisManager as a factory to provide access to a
                 * SqlSession.
                 */
                this.bindFactory(mbm).to(SqlSession.class);
                this.bindFactory(DataStoreFactory.class).to(DataStore.class);

                // Create factory to inject Client
                this.bindFactory(new Factory<Client>() {

                    private Client clientInstance;

                    @Override
                    public void dispose(Client instance) {
                        if (instance != null) {
                            instance.close();
                        }
                    }

                    @Override
                    public Client provide() {

                        if (this.clientInstance == null) {
                            JerseyClientConfiguration conf = new JerseyClientConfiguration();
                            conf.setTimeout(Duration.milliseconds(7000));
                            conf.setConnectionTimeout(Duration.milliseconds(7000));

                            this.clientInstance = new JerseyClientBuilder(env).using(conf).using(env).build("dfvApi");
                        }
                        return this.clientInstance;
                    }

                }).to(Client.class);

                this.bindFactory(new Factory<UltiCalConfig>() {

                    @Override
                    public UltiCalConfig provide() {
                        return config;
                    }

                    @Override
                    public void dispose(UltiCalConfig instance) {
                    }

                }).to(UltiCalConfig.class);
                this.bindAsContract(MailClient.class);
                this.bindFactory(SessionFactory.class).to(Session.class);

            }
        });

        // add healthcheck
        env.healthChecks().register("Database healthcheck", new DatabaseHealthCheck(mds));
        env.healthChecks().register("E-Mail health check", new MailHealthCheck());

        env.jersey().register(EventsResource.class);
        env.jersey().register(TournamentResource.class);
        env.jersey().register(SeasonResource.class);
        env.jersey().register(TournamentFormatResource.class);
        env.jersey().register(TeamResource.class);
        env.jersey().register(RegisterResource.class);
        env.jersey().register(AuthResource.class);
        env.jersey().register(DivisionResource.class);
        env.jersey().register(UserResource.class);
        env.jersey().register(RosterResource.class);
        env.jersey().register(DfvMvNameResource.class);
        env.jersey().register(MailResource.class);
        env.jersey().register(ClubResource.class);
        env.jersey().register(ContextResource.class);
        env.jersey().register(SitemapResource.class);

        env.jersey().register(UlticalLoggingFilter.class);

        /*
         * Authentication stuff. Basically the authenticator looks up the
         * provided user-name in the database and compares the password stored
         * in the db with the provided password. If these two match, it returns
         * the corresponding user object. In order to reduce database access the
         * results are cached by a CachingAuthenticator. The
         * AuthValueFactoryProvider could be used to inject the current user
         * into resource methods that need access to the current user. TODO: An
         * authorizer is still missing that assigns each user a role. However,
         * except for a few users which will be always admins the admin role
         * depends on the tournament-format or tournament-edition that is to be
         * changed.
         */
        Authenticator<BasicCredentials, User> authenticator = new Authenticator<BasicCredentials, User>() {

            @Override
            public Optional<User> authenticate(BasicCredentials credentials) throws AuthenticationException {
                final String providedUserName = credentials.getUsername();
                final String providedPassword = credentials.getPassword();
                SqlSession sqlSession = mbm.provide();
                User user = null;
                try {
                    UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
                    user = userMapper.getByEmail(providedUserName);
                } catch (PersistenceException pe) {
                    throw new AuthenticationException("Accessing the database failed", pe);
                } finally {
                    sqlSession.close();
                }
                Optional<User> result = Optional.absent();
                if (user != null && user.getPassword().equals(providedPassword)) {
                    result = Optional.of(user);
                }
                return result;
            }
        };

        CachingAuthenticator<BasicCredentials, User> cachingAuthenticator = new CachingAuthenticator<BasicCredentials, User>(
                env.metrics(), authenticator, config.getAuthenticationCache());
        env.jersey().register(new AuthDynamicFeature(new BasicCredentialAuthFilter.Builder<User>()
                .setAuthenticator(cachingAuthenticator).buildAuthFilter()));
        env.jersey().register(new AuthValueFactoryProvider.Binder<>(User.class));

        env.jersey().register(ServiceLocatorFeature.class);

        if (config.getDebugMode().isEnabled()) {
            env.jersey().property("jersey.config.server.tracing.type", "ALL");
        }

        if (config.isCorsFilterEnabled()) {
            this.addCorsFilter(env);
        }
    }

    /*
     * Add CORS filter to allow frontend to send requests to server
     */
    private void addCorsFilter(Environment env) {
        FilterRegistration.Dynamic corsFilter = env.servlets().addFilter("CORSFilter", CrossOriginFilter.class);

        // Add URL mapping
        corsFilter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
        corsFilter.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,PUT,POST,DELETE,OPTIONS");
        corsFilter.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
        corsFilter.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
        corsFilter.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM,
                "Content-Type,Authorization,X-Requested-With,Content-Length,Accept,Origin");
        corsFilter.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, "true");
    }

}
