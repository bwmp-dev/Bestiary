package dev.bwmp.bestiary.config;

/**
 * One definition failed to parse.
 * <p>
 * Thrown per definition rather than per file, and caught by the loader: one
 * broken skill must not take out the other forty in its file. The message
 * names the file, the YAML path, the offending value and what was expected.
 */
public class ParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String location;

    public ParseException(String location, String message) {
        super((location == null || location.isEmpty() ? "" : location + ": ") + message);
        this.location = location == null ? "" : location;
    }

    public ParseException(String location, String message, Throwable cause) {
        super((location == null || location.isEmpty() ? "" : location + ": ") + message, cause);
        this.location = location == null ? "" : location;
    }

    /** File and YAML path, for the load report. */
    public String location() {
        return location;
    }
}
