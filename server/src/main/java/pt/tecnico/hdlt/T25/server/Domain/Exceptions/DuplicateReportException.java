package pt.tecnico.hdlt.T25.server.Domain.Exceptions;

public class DuplicateReportException extends Exception{
    private static final long serialVersionUID = 1L;

    public DuplicateReportException(int userId, int epoch) {
        super("Duplicate report found for user" + userId + " at epoch " + epoch);
    }
}
