package com.carolinarollergirls.scoreboard.xml;
/**
 * Copyright (C) 2008-2012 Mr Temper <MrTemper@CarolinaRollergirls.com>
 *
 * This file is part of the Carolina Rollergirls (CRG) ScoreBoard.
 * The CRG ScoreBoard is licensed under either the GNU General Public
 * License version 3 (or later), or the Apache License 2.0, at your option.
 * See the file COPYING for details.
 */

import java.util.*;
import java.util.concurrent.*;

import org.jdom.*;

public class SleepingQueueXmlScoreBoardListener extends QueueXmlScoreBoardListener
{
	public SleepingQueueXmlScoreBoardListener() { super(); }
	public SleepingQueueXmlScoreBoardListener(XmlScoreBoard sb) { super(sb); }

	public void setIgnoreTime(boolean ignoreTime) {
		this.ignoreTime = ignoreTime;
	}

	private StringBuilder treeCheck = new StringBuilder();
	private boolean checkTree(Element e, int pos) {
		if (pos == 0)
			treeCheck.setLength(0);
		if (pos > 3)
			return false;
		treeCheck.append('/');
		treeCheck.append(e.getName());

		boolean ret = true;
		for (Object o : e.getChildren()) {
			if (ret && !checkTree((Element)o, ++pos))
				ret = false;
		}
		return true;
	}

	@Override
	public void xmlChange(Document d) {
		// If ignoreTime, look for single update containing ScoreBoard/Clock/Time and do nothing
		if (ignoreTime) {
			checkTree(d.getRootElement(), 0);
			if (treeCheck.toString().equals("/document/ScoreBoard/Clock/Time"))
				return;
		}
		synchronized (documentsLock) {
			if (queueNextDocument || documents.isEmpty()) {
				documents.addLast(d);
				// Wake any sleeping threads
				ListIterator itr = sleepingThreads.listIterator();
				while(itr.hasNext()) {
					((Thread)itr.next()).interrupt();
				}
			} else
				editor.mergeDocuments(documents.getLast(), d);

			queueNextDocument = editor.hasRemovePI(d);
		}
	}

	@Override
	public Document getNextDocument() { return getNextDocument(0); }

	public Document getNextDocument(int timeout) {
		synchronized (documentsLock) {
			Document ret = documents.poll();
			if (null == ret) {
				// No results.  Add to sleeping thread list and sleep for timeout / 1000 seconds
				sleepingThreads.addLast(Thread.currentThread());
			} else
				return ret;
		}
		try {
			Thread.sleep(timeout);
		} catch (InterruptedException e) {}
		try {
			Thread.sleep(20);
		} catch (InterruptedException e) {}

		synchronized (documentsLock) {
			// Remove from sleeping list and return the next document (if any)
			sleepingThreads.remove(Thread.currentThread());
			return documents.poll();
		}
	}

	protected LinkedList<Thread> sleepingThreads = new LinkedList<Thread>();
	private boolean ignoreTime = false;
}
