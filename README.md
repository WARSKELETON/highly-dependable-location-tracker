# Highly Dependable Location Tracker 
_The world pandemic we live in calls for dependable location tracking and contact tracing tools._

This project was made in the scope of the _"Highly Dependable Systems"_ course at Instituto Superior Técnico. Check the [project-brief](project-brief) folder for more details on the project.

Final Grade: _20_/_20_

## Structure

| Module               |      Description      |
| :------------------- | :-------------------: |
| [project-brief](project-brief)     | Two Stages Project Briefs  |
| [reports](reports)     | Two Stages Reports |
| [client](client)     |  Service Invocations  |
| [contract](contract) |   Proto Definitions   |
| [generator](generator)|    Grid Generation   |
| [crypto](location-tracker-crypto)     |      Crypto API       |
| [server](server)     | Service Implementation|

## Building the Project
To compile and install the contract and then all the modules, in the root folder:

```shell script
cd contract
```

```shell script
mvn clean compile install
```

```shell script
cd ../
```

```shell script
mvn clean compile install -DskipTests
```
## Running the Stage 1 Test Suites
In the root project directory, run:

```shell script
mvn -Dit.test=Stage1IT verify
```

## Running the Stage 2 Test Suites
In the root project directory, run:

```shell script
mvn -Dit.test=Stage2IT verify
```

----
## Authors

**Group 25**

### Team members

| Name | University | More info |
| ---- | ---- | ---- |
| André Augusto | Instituto Superior Técnico | [<img src="https://i.ibb.co/brG8fnX/mail-6.png" width="17">](mailto:andre.9a@gmail.com "andre.9a@gmail.com") [<img src="https://github.githubassets.com/favicon.ico" width="17">](https://github.com/AndreAugusto11 "AndreAugusto11") [<img src="https://i.ibb.co/TvQPw7N/linkedin-logo.png" width="17">](https://www.linkedin.com/in/andreaaugusto/ "andreaaugusto") |
| Lucas Vicente | Instituto Superior Técnico | [<img src="https://i.ibb.co/brG8fnX/mail-6.png" width="17">](mailto:lucasdhvicente@gmail.com "lucasdhvicente@gmail.com") [<img src="https://github.githubassets.com/favicon.ico" width="17">](https://github.com/WARSKELETON "WARSKELETON") [<img src="https://i.ibb.co/TvQPw7N/linkedin-logo.png" width="17">](https://www.linkedin.com/in/lucas-vicente-a91819184/ "lucas-vicente-a91819184") |
