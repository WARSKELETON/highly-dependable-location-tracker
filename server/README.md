# gRPC Server

The server handles information that is sent by the users, regarding users' location reports which are used to prove the location of a user.

The communication between the users and the server is encrypted using hybrid keys, from a combination of RSA 2048 with AES 256.

The integrity of the messages between the users and the server is ensured by protecting messages with digital signatures, which are verified by the server.

The server depends on the contract module, where the protocol buffers shared between server and client are defined.
The server needs to know the interface to provide an implementation for it.


## Instructions for using Maven

Make sure that you installed the contract module first.

To compile and run the server:

```
mvn compile exec:java
```


## To configure the Maven project in Eclipse

'File', 'Import...', 'Maven'-'Existing Maven Projects'

'Select root directory' and 'Browse' to the project base folder.

Check that the desired POM is selected and 'Finish'.


----

[SD Faculty](mailto:leic-sod@disciplinas.tecnico.ulisboa.pt)
