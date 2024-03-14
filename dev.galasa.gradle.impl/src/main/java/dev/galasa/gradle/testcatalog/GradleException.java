package dev.galasa.gradle.testcatalog;

public class GradleException extends Exception {
    public GradleException(String msg) {
        super(msg);
    }
    public GradleException(String msg, Throwable cause) {
        super(msg,cause);
    }
}
