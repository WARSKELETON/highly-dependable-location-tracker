package pt.tecnico.hdlt.T25.server.Domain.Exceptions;

public class InvalidSignatureException extends Exception{
    private static final long serialVersionUID = 1L;

    public InvalidSignatureException() {
        super("The provided signature does not match the content.");
    }
}
