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

import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Most of this implementation delegates down to the underlying transactions implementation to provide the services of the
 * TransactionSynchronizationRegistry. The one area it modifies is the registration of the interposed Synchronizations. The
 * reason this implementation needs to differ is because the DBCP Synchronization and JPA Synchronizations have defined ordering requirements between them both.
 * <p>
 * The current implementation orders DBCP relative to all other Synchronizations. For beforeCompletion, it would be possible to
 * restrict this to the one case where DBCP is ordered before JPA, however it is possible that other interposed Synchronizations
 * would require the services of DBCP and as such if the DBCP is allowed to execute delistResource during beforeCompletion as
 * mandated in DBCP spec the behaviour of those subsequent interactions would be broken. For afterCompletion the DBCP
 * synchronizations are called last as that allows DBCP to detect connection leaks from frameworks that have not closed the DBCP
 * managed resources. This is described in (for example)
 * http://docs.oracle.com/javaee/5/api/javax/transaction/TransactionSynchronizationRegistry
 * .html#registerInterposedSynchronization(javax.transaction.Synchronization) where it says that during afterCompletion
 * "Resources can be closed but no transactional work can be performed with them".
 * <p>
 * One implication of this approach is that if the underlying transactions implementation has special handling for various types
 * of Synchronization that can also implement other interfaces (i.e. if interposedSync instanceof OtherInterface) these
 * behaviours cannot take effect as the underlying implementation will never directly see the actual Synchronizations.
 */
public class TransactionSynchronizationRegistryWrapper implements TransactionSynchronizationRegistry {

    private TransactionSynchronizationRegistry delegate;
    private TransactionManager transactionManager;
    private ConcurrentHashMap<Transaction, DBCPOrderedLastSynchronizationList> interposedSyncs = new ConcurrentHashMap<Transaction, DBCPOrderedLastSynchronizationList>();

    public TransactionSynchronizationRegistryWrapper(TransactionSynchronizationRegistry delegate) {
        this.delegate = delegate;
        transactionManager = com.arjuna.ats.jta.TransactionManager
                .transactionManager();
    }

    @Override
    public void registerInterposedSynchronization(Synchronization sync)
            throws IllegalStateException {
        try {
            Transaction tx = transactionManager.getTransaction();
            DBCPOrderedLastSynchronizationList DBCPOrderedLastSynchronization = interposedSyncs.get(tx);
            if (DBCPOrderedLastSynchronization == null) {
                DBCPOrderedLastSynchronizationList toPut = new DBCPOrderedLastSynchronizationList((com.arjuna.ats.jta.transaction.Transaction) tx, interposedSyncs);
                DBCPOrderedLastSynchronization = interposedSyncs.putIfAbsent(tx, toPut);
                if (DBCPOrderedLastSynchronization == null) {
                    DBCPOrderedLastSynchronization = toPut;
                    delegate.registerInterposedSynchronization(DBCPOrderedLastSynchronization);
                }
            }
            DBCPOrderedLastSynchronization.registerInterposedSynchronization(sync);
        } catch (SystemException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Object getTransactionKey() {
        return delegate.getTransactionKey();
    }

    @Override
    public int getTransactionStatus() {
        return delegate.getTransactionStatus();
    }

    @Override
    public boolean getRollbackOnly() throws IllegalStateException {
        return delegate.getRollbackOnly();
    }

    @Override
    public void setRollbackOnly() throws IllegalStateException {
        delegate.setRollbackOnly();
    }

    @Override
    public Object getResource(Object key) throws IllegalStateException {
        return delegate.getResource(key);
    }

    @Override
    public void putResource(Object key, Object value)
            throws IllegalStateException {
        delegate.putResource(key, value);
    }

}
