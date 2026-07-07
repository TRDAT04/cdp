package vn.vnpost.cdp.rule.exception;

/**
 * Exception thrown when deploying a rule to Unomi fails.
 */
public class RuleDeploymentException extends RuntimeException {
    
    public RuleDeploymentException(String message) {
        super(message);
    }

    public RuleDeploymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
