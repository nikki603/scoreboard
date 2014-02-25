package com.carolinarollergirls.scoreboard.defaults;
/**
 * Copyright (C) 2008-2012 Mr Temper <MrTemper@CarolinaRollergirls.com>
 *
 * This file is part of the Carolina Rollergirls (CRG) ScoreBoard.
 * The CRG ScoreBoard is licensed under either the GNU General Public
 * License version 3 (or later), or the Apache License 2.0, at your option.
 * See the file COPYING for details.
 */

import java.util.*;

import com.carolinarollergirls.scoreboard.*;
import com.carolinarollergirls.scoreboard.model.*;
import com.carolinarollergirls.scoreboard.event.*;
import com.carolinarollergirls.scoreboard.policy.ClockSyncPolicy;

public class DefaultClockModel extends DefaultScoreBoardEventProvider implements ClockModel
{
	public DefaultClockModel(ScoreBoardModel sbm, String i) {
		scoreBoardModel = sbm;
		id = i;

		// The default value for these cannot be known
		// as each clock has a different value,
		// so we only set a default here so something will be set.
		// These defaults are almost certainly not correct,
		// so we really are relying on later configuration (like xml loading)
		// to set the correct values for these.
		// Additionally, we can't do this in reset() because
		// then the clocks would all be reset to incorrect min,max,etc values.
		// Even better, would be to force passing these in the constructor so
		// we would have good default values, that could be reset to...
		// FIXME ^^
		setMinimumNumber(DEFAULT_MINIMUM_NUMBER);
		setMaximumNumber(DEFAULT_MAXIMUM_NUMBER);
		setCountDirectionDown(DEFAULT_DIRECTION);
		setMinimumTime(DEFAULT_MINIMUM_TIME);
		setMaximumTime(DEFAULT_MAXIMUM_TIME);

		reset();
	}

	public String getProviderName() { return "Clock"; }
	public Class getProviderClass() { return Clock.class; }
	public String getProviderId() { return getId(); }

	public ScoreBoard getScoreBoard() { return scoreBoardModel.getScoreBoard(); }
	public ScoreBoardModel getScoreBoardModel() { return scoreBoardModel; }

	public String getId() { return id; }

	public Clock getClock() { return this; }

	public void reset() {
		unstartTime = 0;
		unstopTime = 0;
		unstopLastTime = 0;

		stop();
		setName(getId());

		// We hardcode the assumption that numbers count up.
		setNumber(getMinimumNumber());

		resetTime();
	}

	public String getName() { return name; }
	public void setName(String n) {
		synchronized (nameLock) {
			String last = name;
			name = n;
			scoreBoardChange(new ScoreBoardEvent(this, EVENT_NAME, name, last));
		}
	}

	public int getNumber() { return number; }
	public void setNumber(int n) {
		synchronized (numberLock) {
			Integer last = new Integer(number);
			number = checkNewNumber(n);
			scoreBoardChange(new ScoreBoardEvent(this, EVENT_NUMBER, new Integer(number), last));
		}
	}
	public void changeNumber(int change) {
		synchronized (numberLock) {
			Integer last = new Integer(number);
			number = checkNewNumber(number + change);
			scoreBoardChange(new ScoreBoardEvent(this, EVENT_NUMBER, new Integer(number), last));
		}
	}
	protected int checkNewNumber(int n) {
		if (n < minimumNumber)
			return minimumNumber;
		else if (n > maximumNumber)
			return maximumNumber;
		else
			return n;
	}

	public int getMinimumNumber() { return minimumNumber; }
	public void setMinimumNumber(int n) {
		synchronized (numberLock) {
			Integer last = new Integer(minimumNumber);
			minimumNumber = n;
			if (maximumNumber < minimumNumber)
				setMaximumNumber(minimumNumber);
			if (getNumber() != checkNewNumber(getNumber()))
				setNumber(getNumber());
			scoreBoardChange(new ScoreBoardEvent(this, EVENT_MINIMUM_NUMBER, new Integer(minimumNumber), last));
		}
	}
	public void changeMinimumNumber(int change) {
		synchronized (numberLock) {
			setMinimumNumber(minimumNumber + change);
		}
	}

	public int getMaximumNumber() { return maximumNumber; }
	public void setMaximumNumber(int n) {
		synchronized (numberLock) {
			Integer last = new Integer(maximumNumber);
			if (n < minimumNumber)
				n = minimumNumber;
			maximumNumber = n;
			if (getNumber() != checkNewNumber(getNumber()))
				setNumber(getNumber());
			scoreBoardChange(new ScoreBoardEvent(this, EVENT_MAXIMUM_NUMBER, new Integer(maximumNumber), last));
		}
	}
	public void changeMaximumNumber(int change) {
		synchronized (numberLock) {
			setMaximumNumber(maximumNumber + change);
		}
	}

	private long _lastTime = 0;
	private void _scoreBoardChange(long time, long last) {
		// Send precise time
		scoreBoardChange(new ScoreBoardEvent(this, EVENT_TIME, time, last));
	
		// Send trimmed time (once per second) if needed
		long ms = time % 1000;
		time = time / 1000;
		if (isCountDirectionDown() && ms > 0) {
			// ms values will be rounded up, i.e. 1001-2000 ms converts to 2 sec.
			time++;
		}
		time *= 1000;
		// Send the time if the time doesn't match the last update
		if (time != _lastTime) {
			scoreBoardChange(new ScoreBoardEvent(this, EVENT_TIME_NORMALIZED, time, last));
			_lastTime = time;
		}
	}

	public long getTime() { return time; }
	public void setTime(long ms) {
		boolean doStop;
		synchronized (timeLock) {
			Long last = new Long(time);
			if (isRunning() && isSyncTime())
				ms = ((ms / 1000) * 1000) + (time % 1000);
			time = checkNewTime(ms);
			_scoreBoardChange(new Long(time), last);
			doStop = checkStop();
		}
		if (doStop)
			stop();
	}
	public void changeTime(long change) { _changeTime(change, true); }
	protected void _changeTime(long change, boolean sync) {
		boolean doStop;
		synchronized (timeLock) {
			Long last = new Long(time);
			if (sync && isRunning() && isSyncTime())
				change = ((change / 1000) * 1000);
			time = checkNewTime(time + change);
			_scoreBoardChange(new Long(time), last);
			doStop = checkStop();
		}
		if (doStop)
			stop();
	}
	public void resetTime() {
		if (isCountDirectionDown())
			setTime(getMaximumTime());
		else
			setTime(getMinimumTime());
	}
	protected long checkNewTime(long ms) {
		if (ms < minimumTime)
			return minimumTime;
		else if (ms > maximumTime)
			return maximumTime;
		else
			return ms;
	}
	protected boolean checkStop() {
		return (getTime() == (isCountDirectionDown() ? getMinimumTime() : getMaximumTime()));
	}

	public long getMinimumTime() { return minimumTime; }
	public void setMinimumTime(long ms) {
		synchronized (timeLock) {
			Long last = new Long(minimumTime);
			minimumTime = ms;
			if (maximumTime < minimumTime)
				setMaximumTime(minimumTime);
			if (getTime() != checkNewTime(getTime()))
				setTime(getTime());
			scoreBoardChange(new ScoreBoardEvent(this, EVENT_MINIMUM_TIME, new Long(minimumTime), last));
		}
	}
	public void changeMinimumTime(long change) {
		synchronized (timeLock) {
			setMinimumTime(minimumTime + change);
		}
	}
	public long getMaximumTime() { return maximumTime; }
	public void setMaximumTime(long ms) {
		synchronized (timeLock) {
			Long last = new Long(maximumTime);
			if (ms < minimumTime)
				ms = minimumTime;
			maximumTime = ms;
			if (getTime() != checkNewTime(getTime()))
				setTime(getTime());
			scoreBoardChange(new ScoreBoardEvent(this, EVENT_MAXIMUM_TIME, new Long(maximumTime), last));
		}
	}
	public void changeMaximumTime(long change) {
		synchronized (timeLock) {
			setMaximumTime(maximumTime + change);
		}
	}

	public boolean isCountDirectionDown() { return countDown; }
	public void setCountDirectionDown(boolean down) {
		synchronized (timeLock) {
			Boolean last = new Boolean(countDown);
			countDown = down;
			scoreBoardChange(new ScoreBoardEvent(this, EVENT_DIRECTION, new Boolean(countDown), last));
		}
	}

	public boolean isRunning() { return isRunning; }

	public void start() {
		synchronized (timeLock) {
			if (isRunning())
				return;

			isRunning = true;
			waitingForStart = true;
			unstartTime = getTime();

			long now = System.currentTimeMillis();
			long delayStartTime = 0;
			if (isSyncTime()) {
				// This syncs all the clocks to change second at the same time
				long timeMs = unstartTime % 1000;
				long nowMs = now % 1000;
				if (countDown)
					timeMs = (1000 - timeMs) % 1000;
				long delay = timeMs - nowMs;
				if (Math.abs(delay) >= 500)
					delay = (long)(Math.signum((float)-delay) * (1000 - Math.abs(delay)));
				lastTime = now + delay;
				delayStartTime = Math.max(0, delay);
			} else {
				lastTime = now;
			}
			scoreBoardChange(new ScoreBoardEvent(this, EVENT_RUNNING, Boolean.TRUE, Boolean.FALSE));
			timer.schedule(new StartTimerTask(), delayStartTime);
		}
	}
	public void stop() {
		synchronized (timeLock) {
			if (waitingForStart)
				try { timeLock.wait(2000); } catch ( InterruptedException iE ) { }

			if (!isRunning())
				return;

			isRunning = false;
			timerTask.cancel();
			unstopLastTime = lastTime;
			unstopTime = getTime();
			scoreBoardChange(new ScoreBoardEvent(this, EVENT_RUNNING, Boolean.FALSE, Boolean.TRUE));
		}
	}
	public void unstart() {
		synchronized (timeLock) {
			if (!isRunning())
				return;

			stop();
			setTime(unstartTime);
		}
	}
	public void unstop() {
		synchronized (timeLock) {
			if (isRunning())
				return;

			setTime(unstopTime);
			long change = System.currentTimeMillis() - unstopLastTime;
			changeTime(countDown?-change:change);
			start();
		}
	}

	protected void startTimer() {
		synchronized (timeLock) {
			timerTask = new UpdateClockTimerTask();
			timer.scheduleAtFixedRate(timerTask, CLOCK_UPDATE_INTERVAL, CLOCK_UPDATE_INTERVAL);
			waitingForStart = false;
			timeLock.notifyAll();
		}
	}
	protected void timerTick() {
		if (!isRunning())
			return;

		long change = System.currentTimeMillis() - lastTime;
		lastTime += change;

		_changeTime(countDown?-change:change, false);
	}

	protected boolean isSyncTime() {
		Policy syncPolicy = getScoreBoard().getPolicy(ClockSyncPolicy.ID);
		return (syncPolicy == null ? true : syncPolicy.isEnabled());
	}

	protected ScoreBoardModel scoreBoardModel;

	protected String id;
	protected String name;
	protected int number;
	protected int minimumNumber;
	protected int maximumNumber;
	protected long time;
	protected long minimumTime;
	protected long maximumTime;
	protected boolean countDown;

	protected Timer timer = new Timer();
	protected TimerTask timerTask;
	protected long lastTime;
	protected boolean waitingForStart = false;
	protected boolean isRunning = false;

	protected long unstartTime = 0;
	protected long unstopTime = 0;
	protected long unstopLastTime = 0;

	protected Object nameLock = new Object();
	protected Object numberLock = new Object();
	protected Object timeLock = new Object();

	protected static final long CLOCK_UPDATE_INTERVAL = 50; /* in ms */

	public static final int DEFAULT_MINIMUM_NUMBER = 1;
	public static final int DEFAULT_MAXIMUM_NUMBER = 999;
	public static final long DEFAULT_MINIMUM_TIME = 0;
	public static final long DEFAULT_MAXIMUM_TIME = 3600000;
	public static final boolean DEFAULT_DIRECTION = false;

	protected class StartTimerTask extends TimerTask {
		public void run() {
			DefaultClockModel.this.startTimer();
		}
	}
	protected class UpdateClockTimerTask extends TimerTask {
		public void run() {
			synchronized (DefaultClockModel.this.timeLock) {
				if (running)
					DefaultClockModel.this.timerTick();
			}
		}
		public boolean cancel() {
			synchronized (DefaultClockModel.this.timeLock) {
				running = false;
			}
			return super.cancel();
		}
		protected boolean running = true;
	}
}
