package pt.tecnico.hdlt.T25.server.Domain.Exceptions;

public class InvalidNumberOfProofsException extends Exception {
    private static final long serialVersionUID = 1L;

    public InvalidNumberOfProofsException(int received, int supposed) {
        super("There were needed " + supposed + " proofs, but only received " + received);
    }
}
