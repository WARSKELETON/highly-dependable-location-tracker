package pt.tecnico.hdlt.T25.server.Domain.Exceptions;

public class StaleException extends Exception {
    private static final long serialVersionUID = 1L;

    public StaleException(int userId, int receivedSeqNumber, int expectedSeqNumber) {
        super("Received sequence number " + receivedSeqNumber + " from user" + userId + " but expected " + expectedSeqNumber);
    }
}
