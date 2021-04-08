package pt.tecnico.hdlt.T25.server.Domain.Exceptions;

public class ReportNotFoundException extends Exception {
    private static final long serialVersionUID = 1L;

    public ReportNotFoundException(int userId, int epoch) {
        super("Report not found for user" + userId + " at epoch " + epoch);
    }
}
