# Highly Dependable Location Tracker 
_The world pandemic we live in calls for dependable location tracking and contact tracing tools._

## Structure

| Module               |      Description      |
| :------------------- | :-------------------: |
| [client](client)     |  Service Invocations  |
| [contract](contract) |   Proto Definitions   |
| [generator](generator)|    Grid Generation   |
| [crypto](location-tracker-crypto)     |      Crypto API       |
| [server](server)     | Service Implementation|

## Building the Project
To compile and install all modules:

```shell script
mvn clean compile install -DskipTests
```
## Running the Test Suites
In the root project directory, run:

```shell script
mvn verify
```

----
## Authors

**Group 25**

### Team members

| Number | Name              | User                                 | Email                                       |
| -------|-------------------|--------------------------------------|---------------------------------------------|
| 90704  | Andre Augusto     | <https://github.com/AndreAugusto11>  | <mailto:andre.augusto@tecnico.ulisboa.pt>   |
| 90744  | Lucas Vicente     | <https://github.com/WARSKELETON>     | <mailto:lucasvicente@tecnico.ulisboa.pt>    |
| 87678  | José Oliveira     | <https://github.com/zemfoliveira>    | <mailto:jose.f.oliveira@tecnico.ulisboa.pt> |
