package br.com.jhcomp.planner.instagram;

public class InstagramException extends RuntimeException {

    public InstagramException(String message) {
        super(message);
    }

    public InstagramException(String message, Throwable cause) {
        super(message, cause);
    }
}
