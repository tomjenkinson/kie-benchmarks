/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jbpm.test.performance.jbpm.dbcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * This class was added to:
 *
 * 1. workaround an issue discussed in https://java.net/jira/browse/JTA_SPEC-4 whereby the DBCP Synchronization(s) need to be
 * called after the JPA Synchronization(s). Currently the implementation orders DBCP relative to all interposed Synchronizations,
 * if this is not desirable it would be possible to modify this class to store just the JPA and DBCP syncs and the other syncs
 * can simply be passed to a delegate (would need the reference to this in the constructor).
 *
 * 2. During afterCompletion the DBCP synchronizations should be called last as so the connection is released to the pool at the end.
 */
public class DBCPOrderedLastSynchronizationList implements Synchronization {
    protected static final Logger log = LoggerFactory.getLogger(DBCPOrderedLastSynchronizationList.class);

    private final com.arjuna.ats.jta.transaction.Transaction tx;
    private final Map<Transaction, DBCPOrderedLastSynchronizationList> dbcpOrderedLastSynchronizations;
    private final List<Synchronization> otherSyncs = new ArrayList<Synchronization>();
    private final List<Synchronization> dbcpSyncs = new ArrayList<>();
    private final List<Synchronization> droolsSyncs = new ArrayList<>();

    public DBCPOrderedLastSynchronizationList(com.arjuna.ats.jta.transaction.Transaction tx,
                                              Map<Transaction, DBCPOrderedLastSynchronizationList> dbcpOrderedLastSynchronizations) {
        this.tx = tx;
        this.dbcpOrderedLastSynchronizations = dbcpOrderedLastSynchronizations;
    }

    /**
     * This is only allowed at various points of the transaction lifecycle.
     *
     * @param synchronization The synchronization to register
     * @throws IllegalStateException In case the transaction was in a state that was not valid to register under
     * @throws SystemException In case the transaction status was not known
     */
    public void registerInterposedSynchronization(Synchronization synchronization) throws IllegalStateException, SystemException {
        int status = tx.getStatus();
        switch (status) {
            case javax.transaction.Status.STATUS_ACTIVE:
            case javax.transaction.Status.STATUS_PREPARING:
                break;
            default:
                throw new IllegalStateException("Syncs are not allowed to be registered when the tx is in state:" + status);
        }
        if (synchronization.getClass().getName().startsWith("org.apache.commons.dbcp2")) {
            if (log.isTraceEnabled()) {
                log.trace("DBCPOrderedLastSynchronizationList.dbcpSyncs.add - Class: " + synchronization.getClass() + " HashCode: "
                    + synchronization.hashCode() + " toString: " + synchronization);
            }
            dbcpSyncs.add(synchronization);
        } else if (synchronization.getClass().getName().startsWith("org.drools")) {
            if (log.isTraceEnabled()) {
                log.trace("DBCPOrderedLastSynchronizationList.dbcpSyncs.add - Class: " + synchronization.getClass() + " HashCode: "
                        + synchronization.hashCode() + " toString: " + synchronization);
            }
            droolsSyncs.add(synchronization);
        } else {
            if (log.isTraceEnabled()) {
                log.trace("DBCPOrderedLastSynchronizationList.otherSyncs.add - Class: " + synchronization.getClass() + " HashCode: "
                    + synchronization.hashCode() + " toString: " + synchronization);
            }
            otherSyncs.add(synchronization);
        }
    }

    /**
     * Exceptions from Synchronizations that are registered with this TSR are not trapped for before completion. This is because
     * an error in a Sync here should result in the transaction rolling back.
     *
     * You can see that in effect in these classes:
     * https://github.com/jbosstm/narayana/blob/5.0.4.Final/ArjunaCore/arjuna/classes
     * /com/arjuna/ats/arjuna/coordinator/TwoPhaseCoordinator.java#L91
     * https://github.com/jbosstm/narayana/blob/5.0.4.Final/ArjunaJTA
     * /jta/classes/com/arjuna/ats/internal/jta/resources/arjunacore/SynchronizationImple.java#L76
     */
    @Override
    public void beforeCompletion() {
        // This is needed to guard against syncs being registered during the run, otherwise we could have used an iterator
        int lastIndexProcessed = 0;
        while ((lastIndexProcessed < droolsSyncs.size())) {
            Synchronization sync = droolsSyncs.get(lastIndexProcessed);
            if (log.isTraceEnabled()) {
                log.trace("DBCPOrderedLastSynchronizationList.droolsSyncs.before_completion - Class: " + sync.getClass() + " HashCode: "
                        + sync.hashCode()
                        + " toString: "
                        + sync);
            }
            sync.beforeCompletion();
            lastIndexProcessed = lastIndexProcessed + 1;
        }

        // Do the same for the dbcp syncs
        lastIndexProcessed = 0;
        while ((lastIndexProcessed < dbcpSyncs.size())) {
            Synchronization sync = dbcpSyncs.get(lastIndexProcessed);
            if (log.isTraceEnabled()) {
                log.trace("DBCPOrderedLastSynchronizationList.dbcpSyncs.before_completion - Class: " + sync.getClass() + " HashCode: "
                    + sync.hashCode()
                    + " toString: "
                    + sync);
            }
            sync.beforeCompletion();
            lastIndexProcessed = lastIndexProcessed + 1;
        }

        // Do the same for the drools syncs
        lastIndexProcessed = 0;
        while ((lastIndexProcessed < otherSyncs.size())) {
            Synchronization sync = otherSyncs.get(lastIndexProcessed);
            if (log.isTraceEnabled()) {
                log.trace("DBCPOrderedLastSynchronizationList.otherSyncs.before_completion - Class: " + sync.getClass() + " HashCode: "
                        + sync.hashCode()
                        + " toString: "
                        + sync);
            }
            sync.beforeCompletion();
            lastIndexProcessed = lastIndexProcessed + 1;
        }
    }

    @Override
    public void afterCompletion(int status) {
        for (int i = droolsSyncs.size() - 1; i>= 0; --i) {
            Synchronization sync = this.droolsSyncs.get(i);
            if (log.isTraceEnabled()) {
                log.trace("DBCPOrderedLastSynchronizationList.droolsSyncs.afterCompletion - Class: " + sync.getClass() + " HashCode: "
                        + sync.hashCode()
                        + " toString: "
                        + sync);
            }
            try {
                sync.afterCompletion(status);
            } catch (Exception e) {
                // Trap these exceptions so the rest of the synchronizations get the chance to complete
                // https://github.com/jbosstm/narayana/blob/5.0.4.Final/ArjunaJTA/jta/classes/com/arjuna/ats/internal/jta/resources/arjunacore/SynchronizationImple.java#L102
                log.warn("The droolsSyncs synchronization %s associated with tx %s failed during after completion", sync, tx, e);
            }
        }

        // The list should be iterated in reverse order - has issues with EJB3 if not
        // https://github.com/jbosstm/narayana/blob/master/ArjunaCore/arjuna/classes/com/arjuna/ats/arjuna/coordinator/TwoPhaseCoordinator.java#L509
        for (int i = otherSyncs.size() - 1; i>= 0; --i) {
            Synchronization sync = otherSyncs.get(i);
            if (log.isTraceEnabled()) {
                log.trace("DBCPOrderedLastSynchronizationList.otherSyncs.afterCompletion - Class: " + sync.getClass() + " HashCode: "
                    + sync.hashCode()
                    + " toString: " + sync);
            }
            try {
                sync.afterCompletion(status);
            } catch (Exception e) {
                // Trap these exceptions so the rest of the synchronizations get the chance to complete
                // https://github.com/jbosstm/narayana/blob/5.0.4.Final/ArjunaJTA/jta/classes/com/arjuna/ats/internal/jta/resources/arjunacore/SynchronizationImple.java#L102
                log.warn("The pre-dbcp synchronization %s associated with tx %s failed during after completion", sync, tx, e);
            }
        }

        // Can't do this before you have done HHH or it will fail with isClosed because of the way the connection is discarded
        for (int i = dbcpSyncs.size() - 1; i>= 0; --i) {
            Synchronization sync = this.dbcpSyncs.get(i);
            if (log.isTraceEnabled()) {
                log.trace("DBCPOrderedLastSynchronizationList.dbcpSyncs.afterCompletion - Class: " + sync.getClass() + " HashCode: "
                        + sync.hashCode()
                        + " toString: "
                        + sync);
            }
            try {
                sync.afterCompletion(status);
            } catch (Exception e) {
                // Trap these exceptions so the rest of the synchronizations get the chance to complete
                // https://github.com/jbosstm/narayana/blob/5.0.4.Final/ArjunaJTA/jta/classes/com/arjuna/ats/internal/jta/resources/arjunacore/SynchronizationImple.java#L102
                log.warn("The DBCP synchronization %s associated with tx %s failed during after completion", sync, tx, e);
            }
        }


        if (dbcpOrderedLastSynchronizations.remove(tx) == null) {
            log.warn("The transaction %s could not be removed from the cache during cleanup.", tx);
        }
    }
}
