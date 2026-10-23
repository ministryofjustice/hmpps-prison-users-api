# HMPPS Prison Users API

[![Ministry of Justice Repository Compliance Badge](https://github-community.service.justice.gov.uk/repository-standards/api/hmpps-prison-users-api/badge?style=flat)](https://github-community.service.justice.gov.uk/repository-standards/hmpps-prison-users-api)
[![Docker Repository on ghcr](https://img.shields.io/badge/ghcr.io-repository-2496ED.svg?logo=docker)](https://ghcr.io/ministryofjustice/hmpps-prison-users-api)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://prison-users-api-dev.prison.service.justice.gov.uk/swagger-ui/index.html)
[![Event docs](https://img.shields.io/badge/Event_docs-view-85EA2D.svg)](https://studio.asyncapi.com/?url=https://raw.githubusercontent.com/ministryofjustice/hmpps-prison-users-api/main/async-api.yml&readOnly)

# Features
API supporting the management of Prison system users, including their caseloads and roles.

# Instructions

## Tests
TODO

## Running locally
TODO

## HMPPS domain events
This service publishes a number of domain events to the HMPPS domain event bus, as follows:

- `prison-users.user.unlocked` domain event raised when a prison system user is unlocked.

### Publish -> prison-users.user.unlocked
The message is published via amazon sns. The payload is defined below.
```json
{
   "eventType": "prison-users.user.unlocked",
   "occurredAt": "2021-02-08T14:41:11.526762Z", // ISO offset date time when the restricted patient was added
   "publishedAt": "2021-02-08T14:41:11.526762Z", // ISO offset date time when the event was published
   "version": 1, 
   "description": "Prison user is now unlocked",   
   "additionalInformation": { "prisonerNumber": "A12345"}     
}
```

## Running the application locally

The application comes with a `dev` spring profile that includes default settings for running locally. This is not
necessary when deploying to kubernetes as these values are included in the helm configuration templates -
e.g. `values-dev.yaml`.

There is also a `docker-compose.yml` that can be used to run a local instance of the template in docker and also an
instance of HMPPS Auth (required if your service calls out to other services using a token).

```bash
docker compose pull && docker compose up
```

will run the application and HMPPS Auth within a local docker instance.

### Running the application in Intellij

```bash
docker compose pull && docker compose up --scale hmpps-prison-users-api=0
```

will just start a docker instance of HMPPS Auth. The application should then be started with a `dev` active profile
in Intellij.

### Building and running the docker image locally

The `Dockerfile` relies on the application being built first. Steps to build the docker image:
1. Build the jar files
```
./gradlew clean assemble
```
2. Copy the jar files to the base directory so that the docker build can find them
```
cp build/libs/*.jar .
```
3. Build the docker image with required arguments
```
docker build --build-arg GIT_REF=21345 --build-arg GIT_BRANCH=bob --build-arg BUILD_NUMBER=$(date '+%Y-%m-%d') .
```
4. Run the docker image, setting the auth url so that it starts up
```
docker run -e HMPPS_AUTH_URL="https://sign-in-dev.hmpps.service.justice.gov.uk/auth" <sha from step 3>
```
