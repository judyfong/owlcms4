/*******************************************************************************
 * Copyright (c) 2009-2022 Jean-François Lamy
 *
 * Licensed under the Non-Profit Open Software License version 3.0  ("NPOSL-3.0")
 * License text at https://opensource.org/licenses/NPOSL-3.0
 *******************************************************************************/
package app.owlcms.displays.monitor;

import java.util.LinkedList;
import java.util.List;

import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.templatemodel.TemplateModel;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

import app.owlcms.apputils.queryparameters.FOPParameters;
import app.owlcms.data.athlete.Athlete;
import app.owlcms.fieldofplay.FOPState;
import app.owlcms.i18n.Translator;
import app.owlcms.init.OwlcmsFactory;
import app.owlcms.init.OwlcmsSession;
import app.owlcms.ui.lifting.UIEventProcessor;
import app.owlcms.ui.shared.SafeEventBusRegistration;
import app.owlcms.uievents.BreakType;
import app.owlcms.uievents.UIEvent;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * Class Monitor
 *
 * Show athlete lifting order
 *
 */
@SuppressWarnings("serial")
@Tag("monitor-template")
@JsModule("./components/Monitor.js")
@Route("displays/monitor")
@Theme(value = Lumo.class)
@Push
public class Monitor extends PolymerTemplate<Monitor.MonitorModel> implements FOPParameters,
        SafeEventBusRegistration, UIEventProcessor {
    
    class Status {
        public Status(FOPState state, BreakType breakType, Boolean decision) {
            this.state = state;
            this.breakType = breakType;
            this.decision = decision;
        }
        FOPState state;
        BreakType breakType;
        Boolean decision;
    }
    
    final static int HISTORY_SIZE = 3;
    List<Status> history = new LinkedList<>();

    /**
     * unused
     */
    public interface MonitorModel extends TemplateModel {
    }

    final private static Logger logger = (Logger) LoggerFactory.getLogger(Monitor.class);
    final private static Logger uiEventLogger = (Logger) LoggerFactory.getLogger("UI" + logger.getName());

    static {
        logger.setLevel(Level.INFO);
        uiEventLogger.setLevel(Level.INFO);
    }

    private EventBus uiEventBus;
    private Location location;
    private UI locationUI;
    private String currentFOP;
    private String title;
    private String prevTitle;


    /**
     * Instantiates a new results board.
     */
    public Monitor() {
        OwlcmsFactory.waitDBInitialized();
        this.getElement().getStyle().set("width", "100%");
        doPush(new Status(FOPState.INACTIVE, null, null));
        doPush(new Status(FOPState.INACTIVE, null, null));
    }

    @Override
    public Location getLocation() {
        return this.location;
    }

    @Override
    public UI getLocationUI() {
        return this.locationUI;
    }

    public String getPageTitle() {
        String string = computePageTitle();
        return string;
    }

    @Override
    public boolean isIgnoreGroupFromURL() {
        return true;
    }

    /**
     * @see app.owlcms.apputils.queryparameters.DisplayParameters#isShowInitialDialog()
     */
    @Override
    public boolean isShowInitialDialog() {
        return false;
    }

    @Override
    public void setLocation(Location location) {
        this.location = location;
    }

    @Override
    public void setLocationUI(UI locationUI) {
        this.locationUI = locationUI;
    }

    @Subscribe
    public void slaveUIEvent(UIEvent e) {
        // uiEventLogger.debug("### {} {} {} {}", this.getClass().getSimpleName(),
        // e.getClass().getSimpleName(),e.getTrace());
        UIEventProcessor.uiAccess(this, uiEventBus, () -> {
            if (syncWithFOP(e)) {
                // significant transition
                doUpdate();
            }
        });
    }

    /*
     * @see com.vaadin.flow.component.Component#onAttach(com.vaadin.flow.component. AttachEvent)
     */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        // fop obtained via FOPParameters interface default methods.
        OwlcmsSession.withFop(fop -> {
            init();
            // sync with current status of FOP
            syncWithFOP(null);
            // we listen on uiEventBus.
            uiEventBus = uiEventBusRegister(this, fop);
        });
        doUpdate();
    }

    void uiLog(UIEvent e) {
        uiEventLogger.debug("### {} {} {} {}", this.getClass().getSimpleName(), e.getClass().getSimpleName(),
                this.getOrigin(), e.getOrigin());
    }

    @SuppressWarnings("unused")
    private String computeLiftType(Athlete a) {
        if (a == null) {
            return "";
        }
        String liftType = a.getAttemptsDone() >= 3 ? Translator.translate("Clean_and_Jerk")
                : Translator.translate("Snatch");
        return liftType;
    }

    private String computePageTitle() {
        StringBuilder pageTitle = new StringBuilder();
        Status h0 = history.size() > 0 ? history.get(0) : null;
        Status h1 = history.size() > 1 ? history.get(1) : null;
        Status h2 = history.size() > 2 ? history.get(2) : null;
        
        FOPState currentState = h0 != null ? h0.state : null;
        BreakType currentBreakType = h0 != null ? h0.breakType : null;
        Boolean currentDecision = h0 != null ? h0.decision : null;
        
        FOPState previousState;
        BreakType previousBreakType;
        Boolean previousDecision;
        if (h0 != null && h0.state == FOPState.CURRENT_ATHLETE_DISPLAYED && h1 != null && h1.state == FOPState.BREAK && h1.breakType == BreakType.GROUP_DONE) {
            // ignore the current_athlete_displayed middle state
            previousState = h2 != null ? h2.state : null;
            previousBreakType = h2 != null ? h2.breakType : null;
            previousDecision = h2 != null ? h2.decision : null;
        } else {
            // ignore the current_athlete_displayed middle state
            previousState = h1 != null ? h1.state : null;
            previousBreakType = h1 != null ? h1.breakType : null;
            previousDecision = h1 != null ? h1.decision : null;
        }
        
        if (currentState == FOPState.INACTIVE || currentState == FOPState.BREAK) {
            pageTitle.append("break=");
        } else {
            pageTitle.append("state=");
        }
        pageTitle.append(currentState.name());

        if (currentState == FOPState.BREAK && currentBreakType != null) {
            pageTitle.append(".");
            pageTitle.append(currentBreakType.name());
        } else if (currentState == FOPState.DECISION_VISIBLE) {
            pageTitle.append(".");
            pageTitle.append(currentDecision == null ? "UNDECIDED" : (currentDecision ? "GOOD_LIFT" : "BAD_LIFT"));
        }
        pageTitle.append(";");
        pageTitle.append("previous=");
        pageTitle.append(previousState.name());
        if (previousState == FOPState.BREAK && previousBreakType != null) {
            pageTitle.append(".");
            pageTitle.append(previousBreakType.name());
        } else if (previousState == FOPState.DECISION_VISIBLE) {
            pageTitle.append(".");
            pageTitle.append(previousDecision == null ? "UNDECIDED" : (previousDecision ? "GOOD_LIFT" : "BAD_LIFT"));
        }
        pageTitle.append(";");
        pageTitle.append("fop=");
        pageTitle.append(currentFOP);

        String string = pageTitle.toString();
        if (currentState == FOPState.BREAK && currentBreakType == BreakType.GROUP_DONE
                && previousState == FOPState.DECISION_VISIBLE) {
            // skip this update. There will be another group done after the decision reset.
            // logger.debug("skipping first group done");
            string = null;
        }
        return string;
    }

    private void doUpdate() {
        title = computePageTitle();
        boolean same = false;
        if (prevTitle == null || title == null) {
            // same if both null
            same = (title == prevTitle);
        } else if (title != null) {
            // same if same content comparison
            // prevTitle cannot be null (tested in previous branch)
            same = title.contentEquals(prevTitle);
        }
        if (!same && !(title == null) && !title.isBlank()) {
            this.getElement().setProperty("title", title);
            this.getElement().callJsFunction("setTitle", title);
            logger.warn("{} ---- monitor update {}", title, System.identityHashCode(this.getOrigin()));
            prevTitle = title;
        }
    }

    private Object getOrigin() {
        return this;
    }

    private void init() {
        OwlcmsSession.withFop(fop -> {
            logger.trace("{}Starting monitoring", fop.getLoggingName());
            setId("scoreboard-" + fop.getName());
        });
    }

    private boolean syncWithFOP(UIEvent e) {
        boolean significant = true;
        OwlcmsSession.withFop(fop -> {
            currentFOP = fop.getName();

            if (fop.getState() != history.get(0).state) {
                doPush(new Status(fop.getState(), fop.getBreakType(), fop.getGoodLift()));
            } else if (fop.getState() == FOPState.BREAK) {
                if (fop.getBreakType() != history.get(0).breakType) {
                    doPush(new Status(fop.getState(), fop.getBreakType(), null));
                }
            }
        });
        return significant;
    }

    private void doPush(Status status) {
        history.add(0, status);
        if (history.size() > HISTORY_SIZE) {
            history.remove(HISTORY_SIZE);
        }
    }
}
