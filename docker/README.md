# Docker development environment

The `docker-compose.yml` has containers to

* run the backend
* run a MariaDB database
* deploy the data model with a liquibase container
* run a webserver serving the frontend

_To use the configuration, manually build the backend and frontend beforehand_ (see main `README.md`)!

```bash
cd docker
docker-compose up
```

The website is then available at **http://localhost/ultical-web/**.

You can stop all containers with `docker-compose down` and remove them containers with `docker-compose rm`. Use the option `-v` to remove the persistent volumes when stopping. Use the option `-d` on starting to run the containers in daemon mode.

## MySQL console

To start a MySQL console into the database container, run the following command (you can also connect with ultical username and password, see `docker.yaml`).

```bash
docker run -it --link docker_ultical-db_1:db --network docker_network --rm mariadb:10 sh -c 'exec mysql -h"db" -uroot -p"" -P"3306"'
SHOW DATABASES;
\u ultical
\s
SHOW TABLES;
```

## Enter web container

For debugging, you can enter single containers, e.g. the web server:

```bash
docker exec -it docker_ultical-web_1 /bin/bash
apt-get update && apt-get install curl
curl http://backend:8765/events/basics -v
```

## Database only

If you just want to start a database instance for use with a locally running server, you can start a MySQL 5 container with the database `ultical` and the required default username and password and publishing the port locally with

```bash
docker run -it --name ultical-mysql -p 3306:3306 -e MYSQL_RANDOM_ROOT_PASSWORD=yes -e MYSQL_PASSWORD=ultical -e MYSQL_DATABASE=ultical -e MYSQL_USER=ultical mysql:5
```

You can run a MySQL command line client with

```bash
docker run -it --link ultical-mysql:mysql --rm mysql sh -c 'exec mysql -h"$MYSQL_PORT_3306_TCP_ADDR" -P"$MYSQL_PORT_3306_TCP_PORT" -uroot -p"$MYSQL_ENV_MYSQL_ROOT_PASSWORD"'
```
