# Client

There are three different clients: HAClient, ByzantineClient, Client (correct user)

## Build and Run the Clients

Make sure you have installed the contract module first.

To compile and run the Client (correct user):
```
mvn compile exec:java -Dexec.args="${serverHost} ${serverPort} ${clientId} ${maxByzantineUsers} ${maxNearbyByzantineUsers} ${maxReplicas} ${maxByzantineReplicas} ${keystorePassword}"
```

To compile and run the HAClient:
```
mvn compile exec:java -Dexec.args="${serverHost} ${serverPort} -1 ${maxByzantineUsers} ${maxNearbyByzantineUsers} ${maxReplicas} ${maxByzantineReplicas} ${keystorePassword}"
```

To compile and run the ByzantineClient:
```
mvn compile exec:java -Dexec.args="${serverHost} ${serverPort} ${clientId} ${maxByzantineUsers} ${maxNearbyByzantineUsers} ${maxReplicas} ${maxByzantineReplicas} ${keystorePassword} ${isByzantine}"
```

## User Commands

Users are able to execute commands to communicate with other nearby users as well as the server. Correct users and byzantine users are able to perform the commands below:

Obtain a proof legitimate report for a specific epoch and location
```
proof ${epoch} ${latitude} ${longitude}
```
Submit a report for a specific epoch
```
submit ${epoch}
```
Obtain a previously submitted report for a specific epoch
```
obtain ${epoch}
```

Healthcare Authorities clients are able to perform the commands below:

Obtain a previously submitted report for a user in a specific epoch
```
obtainLocation ${userId} ${epoch}
```
Obtain previously submitted reports for all users in a specific epoch and location
```
obtainUsers ${latitude} ${longitude} ${epoch}
```
