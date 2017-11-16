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
import javax.transaction.InvalidTransactionException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.UserTransaction;
import java.util.WeakHashMap;

public class TransactionManagerWrapper implements TransactionManager, UserTransaction {

    private final TransactionManager delegate;
    private TransactionSynchronizationRegistry tsr;
    private WeakHashMap<Transaction, TransactionWrapper> wrappers = new WeakHashMap<>();

    public TransactionManagerWrapper(TransactionManager delegate, TransactionSynchronizationRegistry tsr) {
        this.delegate = delegate;
        this.tsr = tsr;
    }

    @Override
    public void begin() throws NotSupportedException, SystemException {
        delegate.begin();
    }

    @Override
    public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, IllegalStateException, SystemException {
        Transaction transaction = delegate.getTransaction();
        delegate.commit();
        wrappers.remove(transaction);
    }

    @Override
    public void rollback() throws IllegalStateException, SecurityException, SystemException {
        Transaction transaction = delegate.getTransaction();
        delegate.rollback();
        wrappers.remove(transaction);
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
    public Transaction getTransaction() throws SystemException {
        Transaction transaction = delegate.getTransaction();
        if (transaction == null) {
            return null;
        }
        TransactionWrapper wrapper = wrappers.get(transaction);
        if (wrapper == null) {
            wrapper = new TransactionWrapper(transaction, tsr, this);
            wrappers.put(transaction, wrapper);
        }
        return wrapper;
    }

    @Override
    public void setTransactionTimeout(int seconds) throws SystemException {
        delegate.setTransactionTimeout(seconds);
    }

    @Override
    public Transaction suspend() throws SystemException {
        return delegate.suspend();
    }

    @Override
    public void resume(Transaction tobj) throws InvalidTransactionException, IllegalStateException, SystemException {
        delegate.resume(tobj);
    }

    public void clear(TransactionWrapper transactionWrapper) {
        wrappers.remove(transactionWrapper.getDelegate());
    }
}
