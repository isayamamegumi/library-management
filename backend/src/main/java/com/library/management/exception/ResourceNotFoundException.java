package com.library.management.exception;

/**
 * リソースが見つからない場合の例外
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final Object resourceId;

    public ResourceNotFoundException(String message) {
        super(message);
        this.resourceType = null;
        this.resourceId = null;
    }

    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super(String.format("%s with id '%s' not found", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public ResourceNotFoundException(String resourceType, Object resourceId, String message) {
        super(message);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Object getResourceId() {
        return resourceId;
    }

    // よく使われるファクトリーメソッド
    public static ResourceNotFoundException job(String jobName) {
        return new ResourceNotFoundException("Job", jobName);
    }

    public static ResourceNotFoundException execution(Long executionId) {
        return new ResourceNotFoundException("JobExecution", executionId);
    }

    public static ResourceNotFoundException user(Long userId) {
        return new ResourceNotFoundException("User", userId);
    }

    public static ResourceNotFoundException book(Long bookId) {
        return new ResourceNotFoundException("Book", bookId);
    }
}