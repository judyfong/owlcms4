/*******************************************************************************
 * Copyright (c) 2009-2023 Jean-François Lamy
 *
 * Licensed under the Non-Profit Open Software License version 3.0  ("NPOSL-3.0")
 * License text at https://opensource.org/licenses/NPOSL-3.0
 *******************************************************************************/
package app.owlcms.apputils.queryparameters;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.QueryParameters;

import app.owlcms.i18n.Translator;
import app.owlcms.utils.URLUtils;
import ch.qos.logback.classic.Logger;

/**
 * Navigating to a component that implements this interface will trigger the setParameter method. The setParameter
 * method will invoke readParams method to actually process the query parameter.
 * 
 * Display pages extend an abstract class that stores all the options.
 * 
 * @author jflamy
 *
 */
public interface DisplayParameters extends ContentParameters {

	final Logger logger = (Logger) LoggerFactory.getLogger(DisplayParameters.class);
	
	public static final String CATEGORY = "cat";
	public static final String DARK = "dark";
	public static final String FONTSIZE = "em";
	public static final String LIGHT = "light";
	public static final String PUBLIC = "public";
	public static final String SOUND = "sound";
	public static final String RECORDS = "records";
	public static final String LEADERS = "leaders";
	public static final String TEAMWIDTH = "tw";
	public static final String ABBREVIATED = "abb";
	public static final String VIDEO = "video";

	public void addDialogContent(Component page, VerticalLayout vl);

	@Override
	@SuppressWarnings("unchecked")
	public default void buildDialog(Component page) {
		Dialog dialog = getDialogCreateIfMissing();
		if (dialog == null) {
			return;
		}
		dialog.removeAll();

		dialog.setCloseOnOutsideClick(true);
		dialog.setCloseOnEsc(true);
		dialog.setModal(true);
		dialog.addDialogCloseActionListener(e -> {
			// logger.debug("closeActionListener {}", getDialog());
			getDialog().close();
		});
		dialog.setWidth("75%");

		VerticalLayout vl = new VerticalLayout();
		dialog.add(vl);

		addDialogContent(page, vl);

		Button button = new Button(Translator.translate("Close"), e -> {
			// logger.debug("close button {}", getDialog());
			getDialog().close();
		});
		button.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_PRIMARY);
		VerticalLayout buttons = new VerticalLayout();
		buttons.add(button);
		buttons.setWidthFull();
		buttons.setAlignSelf(Alignment.END, button);
		buttons.setMargin(false);
		vl.setAlignSelf(Alignment.END, buttons);

		vl.add(new Div());
		vl.add(buttons);
	}

	public default void doNotification(boolean dark) {
		Notification n = new Notification();
		H2 h2 = new H2();
		h2.setText(h2.getTranslation("darkMode." + (dark ? DARK : LIGHT)));
		h2.getStyle().set("margin", "0");
		n.add(h2);
		n.setDuration(3000);
		n.setPosition(Position.MIDDLE);
		if (dark) {
			n.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
			h2.getStyle().set("color", "white");
		}
		n.getElement().getStyle().set("font-size", "x-large");
		n.open();
	}

	public Dialog getDialog();

	default public Dialog getDialogCreateIfMissing() {
		if (getDialog() == null) {
			setDialog(new Dialog());
		}
		getDialog().setResizable(true);
		getDialog().setDraggable(true);
		return getDialog();
	}

	public default Double getEmFontSize() {
		return 1.0D;
	}

	public default long getLastDialogClick() {
		return 0;
	}

	public String getRouteParameter();

	public default Double getTeamWidth() {
		return 0.0D;
	}

	public default boolean isAbbreviatedName() {
		return false;
	}

	public boolean isVideo();

	public boolean isDarkMode();

	public default boolean isDefaultLeadersDisplay() {
		return false;
	}

	public default boolean isDefaultRecordsDisplay() {
		return false;
	}

	public default boolean isLeadersDisplay() {
		return false;
	}

	public default boolean isRecordsDisplay() {
		return false;
	}

	@Override
	public boolean isShowInitialDialog();

	/**
	 * @return true if the display can switch during breaks (for example, to medals)
	 */
	public default boolean isSwitchableDisplay() {
		return false;
	}

	public default void openDialog(Dialog dialog) {
		logger.warn("openDialog {} {}", dialog, (dialog != null ? dialog.isOpened() : "-"));
		if (dialog == null) {
			buildDialog((Component) this);
			dialog = this.getDialog();
		}
		final Dialog nDialog = dialog;
		if (!nDialog.isOpened()) {
			nDialog.open();
			setDialog(nDialog);
			UI ui = UI.getCurrent();
			Timer timer = new Timer();
			timer.schedule(
			        new TimerTask() {
				        @Override
				        public void run() {
					        try {
						        ui.access(() -> {
							        // logger.debug("timer closing {}", dialog);
							        nDialog.close();
						        });
					        } catch (Throwable e) {
						        // ignore.
					        }
				        }
			        }, 8 * 1000L);
			setDialogTimer(timer);
		}
	}

	@Override
	public default HashMap<String, List<String>> readParams(Location location,
	        Map<String, List<String>> parametersMap) {
		// handle FOP and Group by calling superclass
		HashMap<String, List<String>> params = ContentParameters.super.readParams(location, parametersMap);

		List<String> darkParams = params.get(DARK);
		// dark is the default. dark=false or dark=no or ... will turn off dark mode.
		boolean darkMode = darkParams == null || darkParams.isEmpty() || darkParams.get(0).toLowerCase().equals("true");
		setDarkMode(darkMode);
		switchLightingMode((Component) this, darkMode, false);
		updateParam(params, DARK, !isDarkMode() ? "false" : null);

		List<String> switchableParams = params.get(PUBLIC);
		boolean switchable = switchableParams != null && !switchableParams.isEmpty()
		        && switchableParams.get(0).toLowerCase().equals("true");
		setSwitchableDisplay(switchable);
		switchSwitchable((Component) this, switchable, false);
		updateParam(params, PUBLIC, isSwitchableDisplay() ? "true" : null);

		List<String> records = params.get(RECORDS);
		boolean showRecords = isDefaultRecordsDisplay();
		if (isDefaultRecordsDisplay()) {
			showRecords = records == null || records.isEmpty() || !"false".contentEquals(records.get(0));
		} else {
			showRecords = records != null && !records.isEmpty() && "true".contentEquals(records.get(0));
		}
		setRecordsDisplay(showRecords);
		switchRecords((Component) this, showRecords, false);
		updateParam(params, RECORDS,
		        isRecordsDisplay() != isDefaultRecordsDisplay() ? Boolean.toString(isRecordsDisplay()) : null);

		List<String> leaders = params.get(LEADERS);
		boolean showLeaders = isDefaultLeadersDisplay();
		if (isDefaultLeadersDisplay()) {
			showLeaders = leaders == null || leaders.isEmpty() || !"false".contentEquals(leaders.get(0));
		} else {
			showLeaders = leaders != null && !leaders.isEmpty() && "true".contentEquals(leaders.get(0));
		}
		setLeadersDisplay(showLeaders);
		switchLeaders((Component) this, showLeaders, false);
		updateParam(params, LEADERS,
		        isLeadersDisplay() != isDefaultLeadersDisplay() ? Boolean.toString(isLeadersDisplay()) : null);

		List<String> sizeParams = params.get(FONTSIZE);
		Double emSize;
		try {
			emSize = (sizeParams != null && !sizeParams.isEmpty() ? Double.parseDouble(sizeParams.get(0)) : 0.0D);
			if (emSize > 0.0D) {
				setEmFontSize(emSize);
				updateParam(params, FONTSIZE, emSize.toString());
			} else {
				setEmFontSize(null);
				updateParam(params, FONTSIZE, null);
			}
		} catch (NumberFormatException e) {
			emSize = 0.0D;
			setEmFontSize(null);
			updateParam(params, FONTSIZE, null);
		}

		List<String> twParams = params.get(TEAMWIDTH);
		Double tWidth;
		try {
			tWidth = (twParams != null && !twParams.isEmpty() ? Double.parseDouble(twParams.get(0)) : 0.0D);
			if (tWidth > 0.0D) {
				setTeamWidth(tWidth);
				updateParam(params, FONTSIZE, tWidth.toString());
			} else {
				setTeamWidth(null);
				updateParam(params, FONTSIZE, null);
			}
		} catch (NumberFormatException e) {
			tWidth = 10.0D;
			setEmFontSize(null);
			updateParam(params, FONTSIZE, null);
		}

		List<String> abbParams = params.get(ABBREVIATED);
		boolean abb;
		abb = (abbParams != null && !abbParams.isEmpty() ? Boolean.valueOf(abbParams.get(0)) : false);
		setAbbreviatedName(abb);
		updateParam(params, ABBREVIATED, abb ? "true" : null);

		List<String> videoParams = params.get(ABBREVIATED);
		boolean video;
		video = (videoParams != null && !videoParams.isEmpty() ? Boolean.valueOf(videoParams.get(0)) : false);
		setVideo(video);
		updateParam(params, VIDEO, abb ? "video" : null);

		setUrlParameterMap(params);
		return params;
	}

	public default void setVideo(boolean video2) {
	}

	public default void setAbbreviatedName(boolean b) {
	}

	public void setDarkMode(boolean dark);

	public default void setDefaultLeadersDisplay(boolean b) {
	}

	public default void setDefaultRecordsDisplay(boolean b) {
	}

	public void setDialog(Dialog dialog);

	public void setDialogTimer(Timer timer);

	public default void setEmFontSize(Double emFontSize) {
	}

	public default void setLastDialogClick(long now) {
	}

	public default void setLeadersDisplay(boolean showLeaders) {
	}

	/*
	 * Process query parameters
	 *
	 * Note: what Vaadin calls a parameter is in the REST style, actually part of the URL path. We use the old-style
	 * Query parameters for our purposes.
	 *
	 * @see app.owlcms.apputils.queryparameters.FOPParameters#setParameter(com.vaadin. flow.router.BeforeEvent,
	 * java.lang.String)
	 */
	@Override
	public default void setParameter(BeforeEvent event, @OptionalParameter String routeParameter) {
		Location location = event.getLocation();
		setLocation(location);
		setLocationUI(event.getUI());
		setRouteParameter(routeParameter);

		// the OptionalParameter string is the part of the URL path that can be
		// interpreted as REST arguments
		// we use the ? query parameters instead.
		QueryParameters queryParameters = location.getQueryParameters();
		Map<String, List<String>> parametersMap = queryParameters.getParameters();

		// we inject the video route parameter as a normal query parameter to simplify processing.
		if (routeParameter != null && routeParameter.contentEquals("video")) {
			parametersMap.put("video", List.of("true"));
		}
		HashMap<String, List<String>> params = readParams(location, parametersMap);

		Location location2 = new Location(location.getPath(), new QueryParameters(URLUtils.cleanParams(params)));
		event.getUI().getPage().getHistory().replaceState(null,
		        location2);
		storeReturnURL(location2);
	}

	public default void setRecordsDisplay(boolean showRecords) {
	}

	public void setRouteParameter(String routeParameter);

	@Override
	public void setShowInitialDialog(boolean b);

	public default void setSwitchableDisplay(boolean switchable) {
	}

	public default void setTeamWidth(Double tw) {
	}

	/**
	 * called by updateURLLocation
	 *
	 * @see app.owlcms.apputils.queryparameters.FOPParameters#storeReturnURL()
	 * @see app.owlcms.apputils.queryparameters.FOPParameters#updateURLLocation(UI, Location, String, String)
	 */
	@Override
	public default void storeReturnURL(Location location) {
		if (isSwitchableDisplay()) {
			// String trace = LoggerUtils.stackTrace();
			UI.getCurrent().getPage().fetchCurrentURL(url -> {
				String urlNonRelative = url.getProtocol() + "://" + url.getHost() + ":" + url.getPort() + "/";
				String arg1 = urlNonRelative + location.getPathWithQueryParameters();
				if (arg1.contains("%")) {
					try {
						arg1 = URLDecoder.decode(arg1, StandardCharsets.UTF_8.name());
					} catch (UnsupportedEncodingException e) {
					}
				}
				// logger.debug("storing pageURL {} {}", arg1, trace);
				storeInSessionStorage("pageURL", url.toExternalForm());
			});
		}
	}

	public default void switchAbbreviated(Component target, boolean abbreviated, boolean updateURL) {
		setAbbreviatedName(abbreviated);
		if (updateURL) {
			updateURLLocation(getLocationUI(), getLocation(), ABBREVIATED, abbreviated ? "true" : "false");
		}
	}

	public default void switchEmFontSize(Component target, Double emFontSize, boolean updateURL) {
		setEmFontSize(emFontSize);
		if (updateURL) {
			updateURLLocation(getLocationUI(), getLocation(), FONTSIZE,
			        emFontSize != null ? emFontSize.toString() : null);
		}
	}

	public default void switchLeaders(Component target, boolean showLeaders, boolean updateURL) {
		setLeadersDisplay(showLeaders);
		if (updateURL) {
			updateURLLocation(getLocationUI(), getLocation(), LEADERS, showLeaders ? "true" : "false");
		}

	}

	public default void switchLightingMode(Component target, boolean dark, boolean updateURL) {
		target.getElement().getClassList().set(DARK, dark);
		target.getElement().getClassList().set(LIGHT, !dark);
		UI.getCurrent().getElement().getStyle().set("overflow", "hidden");
		// logger.debug("switching lighting");
		if (updateURL) {
			updateURLLocation(getLocationUI(), getLocation(), DARK, dark ? null : "false");
		}

		// after updateURL so that this method is usable to store the location if it
		// needs it.
		setDarkMode(dark);
	}

	public default void switchRecords(Component target, boolean showRecords, boolean updateURL) {
		setRecordsDisplay(showRecords);
		if (updateURL) {
			updateURLLocation(getLocationUI(), getLocation(), RECORDS, showRecords ? "true" : "false");
		}
	}

	public default void switchSwitchable(Component target, boolean switchable, boolean updateURL) {
		setSwitchableDisplay(switchable);
		if (updateURL) {
			updateURLLocation(getLocationUI(), getLocation(), PUBLIC, switchable ? "true" : "false");
		}
	}

	public default void switchTeamWidth(Component target, Double teamWidth, boolean updateURL) {
		setTeamWidth(teamWidth);
		if (updateURL) {
			updateURLLocation(getLocationUI(), getLocation(), TEAMWIDTH,
			        teamWidth != null ? teamWidth.toString() : null);
		}

	}

	Timer getDialogTimer();

}
