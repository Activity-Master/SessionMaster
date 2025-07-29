package com.guicedee.activitymaster.sessions.util;

import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for working with Mutiny sessions.
 * This class provides helper methods for common session operations
 * and replaces the functionality of ReactiveTransactionUtil.
 */
public class SessionUtil {
    private static final Logger log = Logger.getLogger(SessionUtil.class.getName());

    /**
     * Executes an operation with the provided session and handles errors.
     * This method replaces ReactiveTransactionUtil.withTransaction() calls.
     *
     * @param session The Mutiny.Session to use for database operations
     * @param operation The operation to execute with the session
     * @param <T> The type of result returned by the operation
     * @return A Uni emitting the result of the operation
     */
    public static <T> Uni<T> executeWithSession(Mutiny.Session session, java.util.function.Function<Mutiny.Session, Uni<T>> operation) {
        if (session == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Session cannot be null"));
        }
        
        try {
            return operation.apply(session)
                .onFailure().invoke(error -> 
                    log.log(Level.SEVERE, "Error executing operation with session: " + error.getMessage(), error)
                );
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception while executing operation with session: " + e.getMessage(), e);
            return Uni.createFrom().failure(e);
        }
    }
    
    /**
     * Executes an operation with the provided session that returns void and handles errors.
     *
     * @param session The Mutiny.Session to use for database operations
     * @param operation The operation to execute with the session
     * @return A Uni that completes when the operation is done
     */
    public static Uni<Void> executeVoidWithSession(Mutiny.Session session, java.util.function.Function<Mutiny.Session, Uni<Void>> operation) {
        return executeWithSession(session, operation);
    }
}