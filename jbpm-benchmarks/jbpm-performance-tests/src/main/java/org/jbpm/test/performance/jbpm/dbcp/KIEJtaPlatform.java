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


import org.hibernate.engine.transaction.jta.platform.internal.JtaSynchronizationStrategy;
import org.hibernate.engine.transaction.jta.platform.internal.SynchronizationRegistryAccess;
import org.hibernate.engine.transaction.jta.platform.internal.SynchronizationRegistryBasedSynchronizationStrategy;

import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;

public class KIEJtaPlatform extends  org.hibernate.engine.transaction.jta.platform.internal.JBossAppServerJtaPlatform {

    private TransactionSynchronizationRegistry tsr;
    private final JtaSynchronizationStrategy synchronizationStrategy;

    public KIEJtaPlatform() {
        synchronizationStrategy = new SynchronizationRegistryBasedSynchronizationStrategy(new SynchronizationRegistryAccess() {
            @Override
            public TransactionSynchronizationRegistry getSynchronizationRegistry() {
                if (tsr == null) {
                    tsr = (TransactionSynchronizationRegistry) KIEJtaPlatform.this.jndiService().locate("java:comp/TransactionSynchronizationRegistry");
                }
                return tsr;
            }
        });
    }

    @Override
    protected TransactionManager locateTransactionManager() {
        return (TransactionManager)this.jndiService().locate("java:comp/TransactionManager");
    }

    @Override
    protected JtaSynchronizationStrategy getSynchronizationStrategy() {
        return synchronizationStrategy;
    }
}
