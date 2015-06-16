package eu.eubrazilcloudconnect.esc.megacc;

public class MegaCCFailureException extends RuntimeException
{
    public MegaCCFailureException() { super(); }
    public MegaCCFailureException(String message) { super(message); }
    public MegaCCFailureException(String message, Throwable cause) { super(message, cause); }
    public MegaCCFailureException(Throwable cause) { super(cause); }
}
