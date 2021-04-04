package pt.tecnico.hdlt.T25.server.Domain.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.security.PrivateKey;
import java.security.PublicKey;

public class PrivateKeyType {
    @JsonProperty("privateKey")
    private final PrivateKey privateKey;

    @JsonCreator
    public PrivateKeyType(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }
}
