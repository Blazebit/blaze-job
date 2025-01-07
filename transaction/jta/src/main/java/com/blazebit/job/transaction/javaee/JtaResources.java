/*
 * Copyright 2018 - 2025 Blazebit.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blazebit.job.transaction.javaee;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.UserTransaction;

/**
 * A holder of JTA resources.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class JtaResources {

    private static final String DEFAULT_USER_TRANSACTION_NAME = "java:comp/UserTransaction";
    private static final String[] TRANSACTION_MANAGER_NAMES = {
        "java:comp/TransactionManager",
        "java:appserver/TransactionManager",
        "java:pm/TransactionManager",
        "java:/TransactionManager"
    };
    private static final String[] TRANSACTION_SYNCHRONIZATION_REGISTRY_NAMES = {
        "java:comp/TransactionSynchronizationRegistry", // Java EE Standard name
        "java:/TransactionSynchronizationRegistry", // Local providers
        "java:comp/env/TransactionSynchronizationRegistry", // Tomcat
    };

    private final TransactionManager transactionManager;
    private final TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    /**
     * Creates a new holder for the given resources.
     *
     * @param transactionManager                 The transaction manager
     * @param transactionSynchronizationRegistry The transaction synchronization registry
     */
    public JtaResources(TransactionManager transactionManager, TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
        this.transactionManager = transactionManager;
        this.transactionSynchronizationRegistry = transactionSynchronizationRegistry;
    }

    /**
     * Returns a new instance containing JTA resources fetched via JNDI from well known names.
     *
     * @return a new instance
     */
    public static JtaResources getInstance() {
        InitialContext context = null;

        try {
            context = new InitialContext();
        } catch (NamingException e) {
            // Maybe in Java SE environment
        }

        UserTransaction ut = null;
        try {
            ut = (UserTransaction) context.lookup(DEFAULT_USER_TRANSACTION_NAME);
        } catch (NamingException ex) {
        }

        TransactionManager tm = null;
        if (ut instanceof TransactionManager) {
            tm = (TransactionManager) ut;
        }

        for (String jndiName : TRANSACTION_MANAGER_NAMES) {
            try {
                tm = (TransactionManager) context.lookup(jndiName);
                break;
            } catch (NamingException ex) {
            }
        }

        TransactionSynchronizationRegistry tsr = null;
        if (ut instanceof TransactionSynchronizationRegistry) {
            tsr = (TransactionSynchronizationRegistry) ut;
        } else if (tm instanceof TransactionSynchronizationRegistry) {
            tsr = (TransactionSynchronizationRegistry) tm;
        }
        if (tsr == null) {
            for (String name : TRANSACTION_SYNCHRONIZATION_REGISTRY_NAMES) {
                try {
                    tsr = (TransactionSynchronizationRegistry) context.lookup(name);
                    break;
                } catch (NamingException ex) {
                }
            }
        }

        if (tm == null) {
            throw new IllegalStateException("Couldn't find TransactionManager!");
        }
        if (tsr == null) {
            throw new IllegalStateException("Couldn't find TransactionSynchronizationRegistry!");
        }

        return new JtaResources(tm, tsr);
    }

    /**
     * Returns the transaction manager.
     *
     * @return the transaction manager
     */
    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    /**
     * Returns the transaction synchronization registry.
     *
     * @return the transaction synchronization registry
     */
    public TransactionSynchronizationRegistry getTransactionSynchronizationRegistry() {
        return transactionSynchronizationRegistry;
    }
}
