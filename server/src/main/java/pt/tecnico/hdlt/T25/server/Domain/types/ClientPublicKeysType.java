package pt.tecnico.hdlt.T25.server.Domain.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import pt.tecnico.hdlt.T25.crypto.Crypto;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;


public class ClientPublicKeysType {
    @JsonProperty("clientPublicKeysList")
    private final List<CustomClientPublicKeys> clientPublicKeysList;

    @JsonCreator
    public ClientPublicKeysType(@JsonProperty("numberOfUsers") int numberOfUsers) {
        this.clientPublicKeysList = new ArrayList<>();
        loadPublicKeys(numberOfUsers);
    }

    public PublicKey getPublicKey(int userId) {
        for (CustomClientPublicKeys customClientPublicKeys : clientPublicKeysList)
            if (customClientPublicKeys.getUserId() == userId)
                return customClientPublicKeys.getPublicKey();
        return null;
    }

    private void loadPublicKeys(int numberOfUsers) {
        for (int i = 0; i < numberOfUsers; i++) {
            String fileName = "client" + i + "-pub.key";
            this.clientPublicKeysList.add(new CustomClientPublicKeys(i, Crypto.getPub(fileName)));
        }

        this.clientPublicKeysList.add(new CustomClientPublicKeys(-1, Crypto.getPub("ha-pub.key")));
    }

    private static class CustomClientPublicKeys {
        @JsonProperty("userId")
        private final int userId;
        @JsonProperty("publicKey")
        private final PublicKey publicKey;

        @JsonCreator
        public CustomClientPublicKeys(@JsonProperty("userId") int userId, @JsonProperty("publicKey") PublicKey publicKey) {
            this.userId = userId;
            this.publicKey = publicKey;
        }

        public int getUserId() {
            return userId;
        }

        public PublicKey getPublicKey() {
            return publicKey;
        }
    }
}

