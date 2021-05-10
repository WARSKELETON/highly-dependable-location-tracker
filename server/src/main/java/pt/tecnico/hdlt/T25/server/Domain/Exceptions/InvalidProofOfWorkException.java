package pt.tecnico.hdlt.T25.server.Domain.Exceptions;

public class InvalidProofOfWorkException extends Exception {
    private static final long serialVersionUID = 1L;

    public InvalidProofOfWorkException() {
        super("The provided proof of work is invalid.");
    }
}
