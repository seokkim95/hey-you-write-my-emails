# Flyway CLI (Maven) usage

## Why `flyway:clean` can't use `application-local.yml` automatically

`flyway-maven-plugin` runs inside Maven, not inside Spring Boot.
So it **does not load** Spring profiles (`local`) nor read `application*.yml` automatically.

That is why you see:

```
org.flywaydb.core.api.FlywayException: Unable to connect to the database. Configure the url, user and password!
```

## Recommended way: pass connection info via Maven properties

Provide connection properties to the Maven Flyway plugin:

```bash
./mvnw -Dflyway.url="jdbc:postgresql://<host>/<db>?sslmode=require" \
       -Dflyway.user="<user>" \
       -Dflyway.password="<password>" \
       flyway:clean
```

Then run migrations:

```bash
./mvnw -Dflyway.url="jdbc:postgresql://<host>/<db>?sslmode=require" \
       -Dflyway.user="<user>" \
       -Dflyway.password="<password>" \
       flyway:migrate
```

## Alternative: use environment variables

```bash
export FLYWAY_URL="jdbc:postgresql://<host>/<db>?sslmode=require"
export FLYWAY_USER="<user>"
export FLYWAY_PASSWORD="<password>"

./mvnw -Dflyway.url="$FLYWAY_URL" -Dflyway.user="$FLYWAY_USER" -Dflyway.password="$FLYWAY_PASSWORD" flyway:clean
```

## Note for Neon

Neon Postgres usually requires TLS.
If your JDBC url doesn't include SSL info, add:

- `?sslmode=require`

Example:

`jdbc:postgresql://ep-xxxx.c-3.us-east-1.aws.neon.tech/neondb?sslmode=require`

