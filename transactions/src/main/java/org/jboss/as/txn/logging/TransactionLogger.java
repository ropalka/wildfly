/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.txn.logging;

import static org.jboss.logging.Logger.Level.DEBUG;
import static org.jboss.logging.Logger.Level.ERROR;
import static org.jboss.logging.Logger.Level.WARN;

import java.lang.invoke.MethodHandles;

import jakarta.resource.spi.work.Work;
import jakarta.resource.spi.work.WorkCompletedException;
import jakarta.transaction.Synchronization;
import jakarta.transaction.Transaction;
import javax.transaction.xa.Xid;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.Param;
import org.jboss.msc.service.StartException;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@MessageLogger(projectCode = "WFLYTX", length = 4)
public interface TransactionLogger extends BasicLogger {
    /**
     * A logger with the category of the default transaction package.
     */
    TransactionLogger ROOT_LOGGER = Logger.getMessageLogger(MethodHandles.lookup(), TransactionLogger.class, "org.jboss.as.txn");

    /**
     * If a transaction could not be rolled back
     */
    @LogMessage(level = ERROR)
    @Message(id = 1, value = "Unable to roll back active transaction")
    void unableToRollBack(@Cause Throwable cause);


    /**
     * If the current transaction status could not be determined
     */
    @LogMessage(level = ERROR)
    @Message(id = 2, value = "Unable to get transaction state")
    void unableToGetTransactionStatus(@Cause Throwable cause);


    /**
     * If the user left a transaction open
     */
    @LogMessage(level = ERROR)
    @Message(id = 3, value = "APPLICATION ERROR: transaction still active in request with status %s")
    void transactionStillOpen(int status);

    /**
     * Creates an exception indicating a create failed.
     *
     * @param cause the reason the creation failed.
     *
     * @return a {@link StartException} initialized with the cause.
     */
    @Message(id = 4, value = "Create failed")
    StartException createFailed(@Cause Throwable cause);

    /**
     * Creates an exception indicating the start of a manager failed.
     *
     * @param cause       the reason the start failed.
     * @param managerName the name of the manager that didn't start.
     *
     * @return a {@link StartException} initialized with the cause and error message.
     */
    @Message(id = 5, value = "%s manager create failed")
    StartException managerStartFailure(@Cause Throwable cause, String managerName);

    /**
     * Creates an exception indicating the failure of the object store browser.
     *
     * @param cause the reason the start failed.
     *
     * @return a {@link StartException} initialized with the cause and error message.
     */
    @Message(id = 6, value = "Failed to configure object store browser bean")
    StartException objectStoreStartFailure(@Cause Throwable cause);


    /**
     * Creates an exception indicating that a service was not started.
     *
     * @return a {@link IllegalStateException} initialized with the cause and error message.
     */
    @Message(id = 7, value = "Service not started")
    IllegalStateException serviceNotStarted();

    /**
     * Creates an exception indicating the start failed.
     *
     * @param cause the reason the start failed.
     *
     * @return a {@link StartException} initialized with the cause.
     */
    @Message(id = 8, value = "Start failed")
    StartException startFailure(@Cause Throwable cause);

    /**
     * A message indicating the metric is unknown.
     *
     * @param metric the unknown metric.
     *
     * @return the message.
     */
    @Message(id = 9, value = "Unknown metric %s")
    String unknownMetric(Object metric);

    @Message(id = 10, value = "MBean Server service not installed, this functionality is not available if the JMX subsystem has not been installed.")
    RuntimeException jmxSubsystemNotInstalled();

//    @Message(id = 11, value = "'journal-store-enable-async-io' must be true.")
//    String transformJournalStoreEnableAsyncIoMustBeTrue();

    @Message(id = 12, value = "Attributes %s and %s are alternatives; both cannot be set with conflicting values.")
    OperationFailedException inconsistentStatisticsSettings(String attrOne, String attrTwo);

    /**
     * If the user has set node identifier to the default value
     */
    @LogMessage(level = WARN)
    @Message(id = 13, value = "The %s attribute on the %s is set to the default value. This is a danger for environments running "
            + "multiple servers. Please make sure the attribute value is unique.")
    void nodeIdentifierIsSetToDefault(String attributeName, String subsystemAddress);

//    /**
//     * A message indicating that jndi-name is missing and it's a required attribute
//     *
//     * @return the message.
//     */
//    @Message(id = 14, value = "Jndi name is required")
//    OperationFailedException jndiNameRequired();

    /**
     * A message indicating that jndi-name has an invalid format
     *
     * @return the message.
     */
    @Message(id = 15, value = "Jndi names have to start with java:/ or java:jboss/")
    OperationFailedException jndiNameInvalidFormat();

//    @LogMessage(level = WARN)
//    @Message(id = 16, value = "Transaction started in EE Concurrent invocation left open, starting rollback to prevent leak.")
//    void rollbackOfTransactionStartedInEEConcurrentInvocation();

//    @LogMessage(level = WARN)
//    @Message(id = 17, value = "Failed to rollback transaction.")
//    void failedToRollbackTransaction(@Cause Throwable cause);

//    @LogMessage(level = WARN)
//    @Message(id = 18, value = "Failed to suspend transaction.")
//    void failedToSuspendTransaction(@Cause Throwable cause);

//    @LogMessage(level = WARN)
//    @Message(id = 19, value = "System error while checking for transaction leak in EE Concurrent invocation.")
//    void systemErrorWhileCheckingForTransactionLeak(@Cause Throwable cause);

//    @Message(id = 20, value = "EE Concurrent ContextHandle serialization must be handled by the factory.")
//    IOException serializationMustBeHandledByTheFactory();

//    @Message(id = 21, value = "EE Concurrent's TransactionSetupProviderService not started.")
//    IllegalStateException transactionSetupProviderServiceNotStarted();

//    @Message(id = 22, value = "EE Concurrent's TransactionSetupProviderService not installed.")
//    IllegalStateException transactionSetupProviderServiceNotInstalled();

    @Message(id = 23, value = "%s must be undefined if %s is 'true'.")
    OperationFailedException mustBeUndefinedIfTrue(String attrOne, String attrTwo);

    @Message(id = 24, value = "%s must be defined if %s is defined.")
    OperationFailedException mustBedefinedIfDefined(String attrOne, String attrTwo);

    @Message(id = 25, value = "Either %s must be 'true' or  %s must be defined.")
    OperationFailedException eitherTrueOrDefined(String attrOne, String attrTwo);

    @LogMessage(level = WARN)
    @Message(id = 26, value = "The transaction %s could not be removed from the cache during cleanup.")
    void transactionNotFound(Transaction tx);

    @LogMessage(level = WARN)
    @Message(id = 27, value = "The pre-Jakarta Connectors synchronization %s associated with tx %s failed during after completion")
    void preJcaSyncAfterCompletionFailed(Synchronization preJcaSync, Transaction tx, @Cause Exception e);

    @LogMessage(level = WARN)
    @Message(id = 28, value = "The Jakarta Connectors synchronization %s associated with tx %s failed during after completion")
    void jcaSyncAfterCompletionFailed(Synchronization jcaSync, Transaction tx, @Cause Exception e);

    @Message(id = 29, value = "Syncs are not allowed to be registered when the tx is in state %s")
    IllegalStateException syncsnotallowed(int status);

    @Message(id = 30, value = "Indexed child resources can only be registered if the parent resource supports ordered children. The parent of '%s' is not indexed")
    IllegalStateException indexedChildResourceRegistrationNotAvailable(PathElement address);

    @Message(id = 31, value = "The attribute '%s' is no longer supported")
    XMLStreamException unsupportedAttribute(String attribute, @Param Location location);

    @Message(id = 32, value = "%s must be defined if %s is 'true'.")
    OperationFailedException mustBeDefinedIfTrue(String attrOne, String attrTwo);

    @Message(id = 33, value = "Only one of %s and %s can be 'true'.")
    OperationFailedException onlyOneCanBeTrue(String attrOne, String attrTwo);

    @LogMessage(level = DEBUG)
    @Message(id = 34, value = "relative_to property of the object-store is set to the default value with jboss.server.data.dir")
    void objectStoreRelativeToIsSetToDefault();

    @Message(id = 35, value = "Cannot find or import inflow transaction for xid %s and work %s")
    WorkCompletedException cannotFindOrImportInflowTransaction(Xid xid, Work work, @Cause Exception e);

    @Message(id = 36, value = "Imported Jakarta Connectors inflow transaction with xid %s of work %s is inactive")
    WorkCompletedException importedInflowTransactionIsInactive(Xid xid, Work work, @Cause Exception e);

    @Message(id = 37, value = "Unexpected error on resuming transaction %s for work %s")
    WorkCompletedException cannotResumeInflowTransactionUnexpectedError(Transaction txn, Work work, @Cause Exception e);

    @Message(id = 38, value = "Unexpected error on suspending transaction for work %s")
    RuntimeException cannotSuspendInflowTransactionUnexpectedError(Work txn, @Cause Exception e);

    @LogMessage(level = WARN)
    @Message(id = 39, value = "A value of zero is not permitted for the maximum timeout, as such the timeout has been set to %s")
    void timeoutValueIsSetToMaximum(int maximum_timeout);

    @Message(id = 40, value = "There is no active transaction at the current context to register synchronization '%s'")
    IllegalStateException noActiveTransactionToRegisterSynchronization(Synchronization sync);

    @Message(id = 41, value = "JMX error: %s")
    OperationFailedException jmxError(String message);

    @Message(id = 42, value = "Transaction discovery error")
    OperationFailedException transactionDiscoveryError(@Cause Exception e);

    @Message(id = 43, value = "InboundTransactionCurrentImpl unable to determine inbound transaction context")
    RuntimeException unableToDetermineInboundTransactionContext(@Cause Exception e);

    @Message(id = 44, value = "InboundTransactionCurrentImpl unable to suspend inbound transaction context")
    RuntimeException unableToSuspendInboundTransactionContext(@Cause Exception e);

    @Message(id = 45, value = "Could not register initial reference for InboundTransactionCurrent implementation")
    RuntimeException cannotRegister(@Cause Exception e);
}
