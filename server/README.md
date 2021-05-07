# Location Server

## Build and Run Server

Make sure that you installed the contract module first.

To compile and run the server:

```
mvn compile exec:java -Dexec.args="${serverId} ${numberOfUsers} ${step} ${maxByzantineUsers} ${maxNearbyByzantineUsers}"
```
