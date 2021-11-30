/*******************************************************************************
 * Copyright (c) 2009-2021 Jean-François Lamy
 *
 * Licensed under the Non-Profit Open Software License version 3.0  ("NPOSL-3.0")
 * License text at https://opensource.org/licenses/NPOSL-3.0
 *******************************************************************************/
package app.owlcms.components.elements;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.DetachEvent;

import app.owlcms.init.OwlcmsSession;
import app.owlcms.publicresults.TimerReceiverServlet;
import app.owlcms.publicresults.UpdateReceiverServlet;
import app.owlcms.uievents.BreakTimerEvent;
import app.owlcms.uievents.UpdateEvent;
import app.owlcms.utils.LoggerUtils;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * Countdown timer element.
 */
public class BreakTimerElement extends TimerElement {

    final private Logger logger = (Logger) LoggerFactory.getLogger(BreakTimerElement.class);
    final private Logger uiEventLogger = (Logger) LoggerFactory.getLogger("UI" + logger.getName());
    private String parentName = "";

    {
        logger.setLevel(Level.INFO);
        uiEventLogger.setLevel(Level.INFO);
    }

    /**
     * Instantiates a new timer element.
     */
    public BreakTimerElement() {
        // logger./**/warn(LoggerUtils.stackTrace());
    }

    @Override
    public void clientFinalWarning() {
        // ignored
    }

    @Override
    public void clientInitialWarning() {
        // ignored
    }

    /**
     * Set the remaining time when the timer element has been hidden for a long time.
     */
    @Override
    @ClientCallable
    public void clientSyncTime() {
    }

    /**
     * Timer stopped
     *
     * @param remaining Time the remaining time
     */
    @Override
    @ClientCallable
    public void clientTimeOver() {
    }

    /**
     * Timer stopped
     *
     * @param remaining Time the remaining time
     */
    @Override
    @ClientCallable
    public void clientTimerStopped(double remainingTime) {
        logger.trace("timer stopped from client" + remainingTime);
    }

    public void setParent(String s) {
        parentName = s;
    }

    @Subscribe
    public void slaveBreakDone(BreakTimerEvent.BreakDone e) {
        if (getFopName() == null || e.getFopName() == null || !getFopName().contentEquals(e.getFopName())) {
            // event is not for us
            return;
        }
        uiEventLogger.debug("&&& break done {} {}", parentName);
        doStopTimer();
    }

    @Subscribe
    public void slaveBreakPause(BreakTimerEvent.BreakPaused e) {
        if (getFopName() == null || e.getFopName() == null || !getFopName().contentEquals(e.getFopName())) {
            // event is not for us
            return;
        }
        uiEventLogger.debug("&&& breakTimer pause {} {}", parentName);
        doStopTimer();
    }

    @Subscribe
    public void slaveBreakSet(BreakTimerEvent.BreakSetTime e) {
        if (getFopName() == null || e.getFopName() == null || !getFopName().contentEquals(e.getFopName())) {
            // event is not for us
            return;
        }

        Integer milliseconds;

        milliseconds = e.isIndefinite() ? null : e.getTimeRemaining();
        uiEventLogger.debug("&&& breakTimer set {} {} {} {}", parentName, formatDuration(milliseconds),
                e.isIndefinite(), LoggerUtils.whereFrom());
        doSetTimer(milliseconds);
    }

    @Subscribe
    public void slaveBreakStart(BreakTimerEvent.BreakStart e) {
        if (getFopName() == null || e.getFopName() == null || !getFopName().contentEquals(e.getFopName())) {
            // event is not for us
            return;
        }
        Integer tr = e.isIndefinite() ? null : e.getTimeRemaining();
        uiEventLogger.debug("&&& breakTimer start {} {} {}", parentName, tr, LoggerUtils.whereFrom());
        doStartTimer(tr, true); // true means "silent".
    }

    @Subscribe
    // not clear why we listen to this event.
    public void slaveOrderUpdated(UpdateEvent e) {
        if (getFopName() == null || e.getFopName() == null || !getFopName().contentEquals(e.getFopName())) {
            // event is not for us
            return;
        }
        Integer breakRemaining = e.getBreakRemaining();
        if (e.isBreak() && breakRemaining > 0) {
            doSetTimer(breakRemaining);
            doStartTimer(breakRemaining, true);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see app.owlcms.displays.attemptboard.TimerElement#init()
     */
    @Override
    protected void init() {
        super.init();
        setSilent(true);
        getModel().setSilent(true); // do not emit sounds
    }

    /*
     * @see com.vaadin.flow.component.Component#onAttach(com.vaadin.flow.component. AttachEvent)
     */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        this.ui = attachEvent.getUI();
        init();

        UpdateReceiverServlet.getEventBus().register(this);
        TimerReceiverServlet.getEventBus().register(this);
        setFopName((String) OwlcmsSession.getAttribute("fopName"));
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        this.ui = null;
        try {
            UpdateReceiverServlet.getEventBus().unregister(this);
        } catch (Exception e) {
        }
        try {
            TimerReceiverServlet.getEventBus().unregister(this);
        } catch (Exception e) {
        }
    }

    private String formatDuration(Integer milliseconds) {
        return milliseconds != null ? DurationFormatUtils.formatDurationHMS(milliseconds) : "null";
    }
}
