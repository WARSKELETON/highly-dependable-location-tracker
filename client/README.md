# gRPC Client

The client application makes up the behaviour of a user. Users are able to communicate with nearby users as well as the server.

The communication between the users and the server is encrypted using hybrid keys, from a combination of RSA 2048 with AES 256.

The integrity of the messages between the users and other nearby users as well as the users and the server is ensured by protecting messages with digital signatures.

The client depends on the contract module, where the protocol buffers shared between server and client are defined.
The client needs to know the interface to make remote calls.


## Instructions for using Maven

Make sure that you installed the contract module first.
There are three different clients: HAClient, ByzantineClient, Client (correct user)

To compile and run the Client (correct user):
```
mvn compile exec:java
```
or 
```
mvn compile exec:java -Dexec.args="${serverHost} ${serverPort} ${clientId} ${maxByzantineUsers} ${maxNearbyByzantineUsers}"
```

To compile and run the HAClient:
```
mvn compile exec:java -Dexec.args="${serverHost} ${serverPort} -1 ${maxByzantineUsers} ${maxNearbyByzantineUsers}"
```

To compile and run the ByzantineClient:
```
mvn compile exec:java -Dexec.args="${serverHost} ${serverPort} ${clientId} ${maxByzantineUsers} ${maxNearbyByzantineUsers} ${byzantineType}"
```


## User Commands

Users are able to execute commands to communicate with other nearby users as well as the server. Correct users and byzantine users are able to perform the commands below
```
proof ${epoch} ${latitude} ${longitude}
```
```
submit ${epoch}
```
```
obtain ${epoch}
```

Healthcare Authorities clients are able to perform the commands below
```
obtainUsers ${latitude} ${longitude} ${epoch}
```
```
obtainLocation ${userId} ${epoch}
```


## To configure the Maven project in Eclipse

'File', 'Import...', 'Maven'-'Existing Maven Projects'

'Select root directory' and 'Browse' to the project base folder.

Check that the desired POM is selected and 'Finish'.


----

[SD Faculty](mailto:leic-sod@disciplinas.tecnico.ulisboa.pt)
