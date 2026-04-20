package com.taskqueue.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class TaskQueueException extends RuntimeException {

    private final HttpStatus status;

    public TaskQueueException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    // Common factory methods
    public static TaskQueueException notFound(String entity, String id) {
        return new TaskQueueException(entity + " not found: " + id, HttpStatus.NOT_FOUND);
    }

    public static TaskQueueException badRequest(String message) {
        return new TaskQueueException(message, HttpStatus.BAD_REQUEST);
    }

    public static TaskQueueException conflict(String message) {
        return new TaskQueueException(message, HttpStatus.CONFLICT);
    }

    public static TaskQueueException forbidden(String message) {
        return new TaskQueueException(message, HttpStatus.FORBIDDEN);
    }
}