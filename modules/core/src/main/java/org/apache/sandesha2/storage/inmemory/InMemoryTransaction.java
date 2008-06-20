/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sandesha2.storage.inmemory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.sandesha2.i18n.SandeshaMessageHelper;
import org.apache.sandesha2.i18n.SandeshaMessageKeys;
import org.apache.sandesha2.storage.SandeshaStorageException;
import org.apache.sandesha2.storage.Transaction;
import org.apache.sandesha2.storage.beans.RMBean;
import org.apache.sandesha2.util.LoggingControl;

/**
 * This class does not really implement transactions, but it is a good
 * place to implement locking for the in memory storage manager.
 */

public class InMemoryTransaction implements Transaction {

	private static final Log log = LogFactory.getLog(InMemoryTransaction.class);

	private InMemoryStorageManager manager;
	private String threadName;
	private ArrayList enlistedBeans = new ArrayList();
	private InMemoryTransaction waitingForTran = null;
	private boolean sentMessages = false;
	private boolean active = true;
	private Thread thread;
	private boolean useSerialization;
	
	InMemoryTransaction(InMemoryStorageManager manager, Thread thread, boolean useSerialization) {
		if(LoggingControl.isAnyTracingEnabled() && log.isDebugEnabled()) log.debug("Entry: InMemoryTransaction::<init>");
		this.manager = manager;
		this.thread = thread;
		this.threadName = thread.getName();
		this.useSerialization = useSerialization;
		if(LoggingControl.isAnyTracingEnabled() && log.isDebugEnabled()) log.debug("Exit: InMemoryTransaction::<init>, " + this);
	}
	
	public void commit() {
		releaseLocks();
		if(sentMessages && useSerialization) manager.getSender().wakeThread();
		active = false;
	}

	public void rollback() {
		releaseLocks();
		active = false;
	}
	
	public boolean isActive () {
		return active;
	}

	public void enlist(RMBean bean) throws SandeshaStorageException {
		if(LoggingControl.isAnyTracingEnabled() && log.isDebugEnabled()) log.debug("Entry: InMemoryTransaction::enlist, " + bean);
		if(bean != null) {
			synchronized (bean) {
				InMemoryTransaction other = (InMemoryTransaction) bean.getTransaction();
				while(other != null && other != this) {
					// Put ourselves into the list of waiters
					waitingForTran = other;

					// Look to see if there is a loop in the chain of waiters
					if(!enlistedBeans.isEmpty()) {
						HashSet set = new HashSet();
						set.add(this);
						while(other != null) {
							if(set.contains(other)) {
								String message = SandeshaMessageHelper.getMessage(SandeshaMessageKeys.deadlock, this.toString(), bean.toString());
								SandeshaStorageException e = new SandeshaStorageException(message);
								
								// Do our best to get out of the way of the other work in the system
								waitingForTran = null;
								releaseLocks();
								
								if(LoggingControl.isAnyTracingEnabled() && log.isDebugEnabled()) log.debug(message, e);
								throw e;
							}
							set.add(other);
							other = other.waitingForTran;
						}
					}
					
					boolean warn = false;
					try {
						if(LoggingControl.isAnyTracingEnabled() && log.isDebugEnabled()) log.debug("This " + this + " waiting for " + waitingForTran);
						long pre = System.currentTimeMillis();
						bean.wait(5000); 
						long post = System.currentTimeMillis();
						if ((post - pre) > 50000)
							warn = true;
					} catch(InterruptedException e) {
						// Do nothing
					}
					other = (InMemoryTransaction) bean.getTransaction();
					if (other != null && warn) {
						//we have been waiting for a long time - this might imply a three way deadlock so error condition
						if(LoggingControl.isAnyTracingEnabled() && log.isDebugEnabled()) log.debug("possible deadlock :" + this.toString() + " : " + bean.toString());
					}
				}
				
				waitingForTran = null;
				if(other == null) {
					if(LoggingControl.isAnyTracingEnabled() && log.isDebugEnabled()) log.debug(this + " locking bean");
					bean.setTransaction(this);
					enlistedBeans.add(bean);
				}
			}
		}
		
		if(LoggingControl.isAnyTracingEnabled() && log.isDebugEnabled()) log.debug("Exit: InMemoryTransaction::enlist");
	}
	
	private void releaseLocks() {
		if(LoggingControl.isAnyTracingEnabled() && log.isDebugEnabled()) log.debug("Entry: InMemoryTransaction::releaseLocks, " + this);
		manager.removeTransaction(this);

		Iterator beans = enlistedBeans.iterator();
		while(beans.hasNext()) {
			RMBean bean = (RMBean) beans.next();
			synchronized (bean) {
				bean.setTransaction(null);
				bean.notifyAll();
			}
		}
		enlistedBeans.clear();
		
		if(LoggingControl.isAnyTracingEnabled() && log.isDebugEnabled()) log.debug("Exit: InMemoryTransaction::releaseLocks");
	}
	
	public String toString() {
		StringBuffer result = new StringBuffer();
		result.append("[InMemoryTransaction, thread:");
		result.append(thread);
		result.append(", name: ");
		result.append(threadName);
		result.append(", locks: ");
		result.append(enlistedBeans.size());
		result.append("]");
		return result.toString();
	}

	public void setSentMessages(boolean sentMessages) {
		this.sentMessages = sentMessages;
	}
	
	/**
	 * Get the thread which this transaction is associated with.
	 * @return
	 */
	public Thread getThread(){
		return thread;
	}
}




