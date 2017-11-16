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

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.xa.XAResource;

public class TransactionWrapper implements Transaction {
    private final Transaction delegate;
    private final TransactionSynchronizationRegistry tsr;
    private final TransactionManagerWrapper transactionManagerWrapper;

    public TransactionWrapper(Transaction delegate, TransactionSynchronizationRegistry tsr, TransactionManagerWrapper transactionManagerWrapper) {
        this.delegate = delegate;
        this.tsr = tsr;
        this.transactionManagerWrapper = transactionManagerWrapper;
    }

    @Override
    public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, SystemException {
        delegate.commit();
        transactionManagerWrapper.clear(this);
    }

    @Override
    public void rollback() throws IllegalStateException, SystemException {
        delegate.rollback();
        transactionManagerWrapper.clear(this);
    }

    @Override
    public void setRollbackOnly() throws IllegalStateException, SystemException {
        delegate.setRollbackOnly();
    }

    @Override
    public int getStatus() throws SystemException {
        return delegate.getStatus();
    }

    @Override
    public boolean enlistResource(XAResource xaRes) throws RollbackException, IllegalStateException, SystemException {
        return delegate.enlistResource(xaRes);
    }

    @Override
    public boolean delistResource(XAResource xaRes, int flag) throws IllegalStateException, SystemException {
        return delegate.delistResource(xaRes, flag);
    }

    @Override
    public void registerSynchronization(Synchronization sync) throws RollbackException, IllegalStateException, SystemException {
        if (sync.getClass().getName().startsWith("org.apache.commons.dbcp2")) {
            tsr.registerInterposedSynchronization(sync);
        } else {
            delegate.registerSynchronization(sync);
        }
    }

    public Transaction getDelegate() {
        return delegate;
    }

    public boolean equals(Object o) {
        if (o instanceof TransactionWrapper) {
            return delegate.equals(((TransactionWrapper) o).delegate);
        }
        return false;
    }
}
