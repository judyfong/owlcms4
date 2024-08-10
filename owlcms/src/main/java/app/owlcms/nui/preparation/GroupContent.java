/*******************************************************************************
 * Copyright (c) 2009-2023 Jean-François Lamy
 *
 * Licensed under the Non-Profit Open Software License version 3.0  ("NPOSL-3.0")
 * License text at https://opensource.org/licenses/NPOSL-3.0
 *******************************************************************************/
package app.owlcms.nui.preparation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

import org.apache.poi.ss.formula.functions.T;
import org.slf4j.LoggerFactory;
import org.vaadin.crudui.crud.CrudListener;
import org.vaadin.crudui.crud.CrudOperation;
import org.vaadin.crudui.crud.impl.GridCrud;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.SelectionMode;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;

import app.owlcms.apputils.queryparameters.BaseContent;
import app.owlcms.components.elements.StopProcessingException;
import app.owlcms.components.fields.LocalDateTimeField;
import app.owlcms.data.athlete.Athlete;
import app.owlcms.data.athleteSort.AthleteSorter;
import app.owlcms.data.athleteSort.RegistrationOrderComparator;
import app.owlcms.data.competition.Competition;
import app.owlcms.data.group.Group;
import app.owlcms.data.group.GroupRepository;
import app.owlcms.i18n.Translator;
import app.owlcms.nui.crudui.OwlcmsCrudFormFactory;
import app.owlcms.nui.crudui.OwlcmsCrudGrid;
import app.owlcms.nui.crudui.OwlcmsGridLayout;
import app.owlcms.nui.shared.DownloadButtonFactory;
import app.owlcms.nui.shared.OwlcmsContent;
import app.owlcms.nui.shared.OwlcmsLayout;
import app.owlcms.spreadsheet.JXLSCardsDocs;
import app.owlcms.spreadsheet.JXLSCardsWeighIn;
import app.owlcms.utils.LoggerUtils;
import app.owlcms.utils.ResourceWalker;
import app.owlcms.utils.URLUtils;
import app.owlcms.utils.ZipUtils;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * Class GroupContent.
 *
 * Defines the toolbar and the table for editing data on sessions.
 */
@SuppressWarnings("serial")
@Route(value = "preparation/groups", layout = OwlcmsLayout.class)
public class GroupContent extends BaseContent implements CrudListener<Group>, OwlcmsContent {

	final static Logger logger = (Logger) LoggerFactory.getLogger(GroupContent.class);
	static {
		logger.setLevel(Level.INFO);
	}
	private OwlcmsCrudFormFactory<Group> editingFormFactory;
	private OwlcmsLayout routerLayout;
	private FlexLayout topBar;
	private GroupGrid crud;

	/**
	 * Instantiates the Group crudGrid.
	 */
	public GroupContent() {
		this.editingFormFactory = new GroupEditingFormFactory(Group.class, this);
		GridCrud<Group> crud = createGrid(this.editingFormFactory);
		// defineFilters(crudGrid);
		fillHW(crud, this);
	}

	@Override
	public Group add(Group domainObjectToAdd) {
		return this.editingFormFactory.add(domainObjectToAdd);
	}

	public void closeDialog() {
	}

	@Override
	public FlexLayout createMenuArea() {
		this.topBar = new FlexLayout();

		Div cardsButton = createCardsButton();

		Hr hr = new Hr();
		hr.setWidthFull();
		hr.getStyle().set("margin", "0");
		hr.getStyle().set("padding", "0");
		FlexLayout buttons = new FlexLayout(
		        new NativeLabel(Translator.translate("Preparation_Groups")), cardsButton);
		buttons.getStyle().set("flex-wrap", "wrap");
		buttons.getStyle().set("gap", "1ex");
		buttons.getStyle().set("margin-left", "5em");
		buttons.setAlignItems(FlexComponent.Alignment.BASELINE);

		this.topBar.getStyle().set("flex", "100 1");
		this.topBar.add(buttons);
		this.topBar.setJustifyContentMode(FlexComponent.JustifyContentMode.START);
		this.topBar.setAlignItems(FlexComponent.Alignment.CENTER);

		return this.topBar;
	}

	private Div createCardsButton() {
		Div localDirZipDiv = null;
		UI ui = UI.getCurrent();
		Competition comp = Competition.getCurrent();
		localDirZipDiv = DownloadButtonFactory.createDynamicZipDownloadButton("cards",
		        Translator.translate("DownloadPreWeighInKit"),
		        () -> {
			        List<KitElement> elements = prepareKits(getSortedSelection(), comp, (e, m) -> notifyError(e, ui, m));
			        return zipKitToInputStream(getSortedSelection(), elements, (e, m) -> notifyError(e, ui, m));
		        });
		return localDirZipDiv;
	}

	private InputStream zipKitToInputStream(List<Group> selectedItems, List<KitElement> elements, BiConsumer<Throwable, String> processError) {
		PipedOutputStream out;
		PipedInputStream in;

		try {
			out = new PipedOutputStream();
			in = new PipedInputStream(out);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		new Thread(() -> {
			try {
				zipKit(selectedItems, out, elements);
				out.flush();
				out.close();
			} catch (Throwable e) {
				processError.accept(e, e.getMessage());
			}
		}).start();
		return in;
	}

	private void notifyError(Throwable e, UI ui, final String m) {
		logger.info(Translator.translateExplicitLocale(m, Locale.ENGLISH));
		this.getUI().get().access(() -> {
			Notification notif = new Notification();
			notif.addThemeVariants(NotificationVariant.LUMO_ERROR);
			notif.setPosition(Position.TOP_STRETCH);
			notif.setDuration(3000);
			notif.setText(m);
			notif.open();
		});
	}

	private List<Group> getSortedSelection() {
		return crud.getSelectedItems().stream().sorted(Group.groupWeighinTimeComparator).toList();
	}

	private ZipOutputStream zipKit(List<Group> selectedItems, PipedOutputStream os, List<KitElement> elements) throws IOException {
		// try {
		int i = 1;
		ZipOutputStream zipOut = null;
		try {
			zipOut = new ZipOutputStream(os);
			doPrintScript(zipOut);
			for (Group g : selectedItems) {
				String seq = String.format("%02d", i);
				// get current version of athletes.
				List<Athlete> athletes = groupAthletes(g, true);
				doWeighIn(seq, zipOut, g, athletes, null);
				doCards(seq, zipOut, g, athletes, null);
				i++;
			}
			return zipOut;
		} finally {
			if (zipOut != null) {
				zipOut.finish();
				zipOut.close();
			}
		}

		// } catch (IOException e) {
		// LoggerUtils.logError(logger, e, true);
		// throw e;
		// }
	}

	private void doPrintScript(ZipOutputStream zipOut) {
		try {
			ZipUtils.zipStream(ResourceWalker.getFileOrResource("/templates/cards/print.ps1"), "print.ps1", false, zipOut);
		} catch (IOException e) {
			LoggerUtils.logError(logger, e, true);
		}
	}

	private void doCards(String seq, ZipOutputStream zipOut, Group g, List<Athlete> athletes, String templateName) throws IOException {
		// group may have been edited since the page was loaded
		JXLSCardsDocs cardsXlsWriter = new JXLSCardsDocs();
		cardsXlsWriter.setGroup(g);
		if (athletes.size() > cardsXlsWriter.getSizeLimit()) {
			logger.error("too many athletes : no report");
		} else if (athletes.size() == 0) {
			logger./**/warn("no athletes: empty report.");
		}
		cardsXlsWriter.setSortedAthletes(athletes);

		cardsXlsWriter.setTemplateFileName("templates/cards/" + templateName);
		String name = seq + "_b_cards_" + g.getName() + ".xls";
		InputStream in = cardsXlsWriter.createInputStream();
		ZipUtils.zipStream(in, name, false, zipOut);
	}

	private void doWeighIn(String seq, ZipOutputStream zipOut, Group g, List<Athlete> athletes, String templateName) throws IOException {
		// group may have been edited since the page was loaded
		JXLSCardsDocs cardsXlsWriter = new JXLSCardsWeighIn();
		cardsXlsWriter.setGroup(g);
		if (athletes.size() > cardsXlsWriter.getSizeLimit()) {
			logger.error("too many athletes : no report");
		} else if (athletes.size() == 0) {
			logger./**/warn("no athletes: empty report.");
		}
		cardsXlsWriter.setSortedAthletes(athletes);
		cardsXlsWriter.setTemplateFileName(templateName);
		String name = seq + "_a_weighin_" + g.getName() + ".xlsx";
		InputStream in = cardsXlsWriter.createInputStream();
		ZipUtils.zipStream(in, name, false, zipOut);

	}

	private record KitElement(String name, InputStream is, int count) {
	}

	private List<KitElement> prepareKits(List<Group> selectedItems, Competition comp, BiConsumer<Throwable, String> processError) {
		if (selectedItems == null || selectedItems.size() == 0) {
			Exception e = new Exception("NoAthletes");
			processError.accept(e, e.getMessage());
			throw new StopProcessingException(e.getMessage(), e);
		}

		List<KitElement> elements = new ArrayList<>();
		elements.add(
		        checkKit(() -> comp.getCardsTemplateFileName(),
		                "/templates/cards/",
		                "NoCardsTemplate", processError));
		elements.add(
		        checkKit(() -> comp.getStartingWeightsSheetTemplateFileName(),
		                "/templates/weighin/",
		                "NoWeighInTemplate", processError));
		return elements;
	}

	private KitElement checkKit(Supplier<String> templateNameSupplier, String prefix, String message, BiConsumer<Throwable, String> processError) {
		String template = templateNameSupplier.get();// Competition.getCurrent().getCardsTemplateFileName();
		String templateName = prefix + template; // "/templates/cards/"
		try {
			InputStream is = ResourceWalker.getFileOrResource(templateName);
			return new KitElement(templateName, is, 1);
		} catch (FileNotFoundException e) {
			processError.accept(e, message);
			throw new StopProcessingException(message, e); // "NoCardsTemplate"
		}
	}

	protected List<Athlete> groupAthletes(Group g, boolean sessionOrder) {
		List<Athlete> regCatAthletesList = new ArrayList<>(g.getAthletes());
		if (sessionOrder) {
			Collections.sort(regCatAthletesList, RegistrationOrderComparator.athleteSessionRegistrationOrderComparator);
		} else {
			AthleteSorter.registrationOrder(regCatAthletesList);
		}
		return regCatAthletesList;
	}

	@Override
	public void delete(Group domainObjectToDelete) {
		this.editingFormFactory.delete(domainObjectToDelete);
	}

	/**
	 * The refresh button on the toolbar
	 *
	 * @see org.vaadin.crudui.crud.CrudListener#findAll()
	 */
	@Override
	public Collection<Group> findAll() {
		return GroupRepository.findAll().stream().sorted(Group::compareToWeighIn).collect(Collectors.toList());
	}

	@Override
	public String getMenuTitle() {
		return getPageTitle();
	}

	/**
	 * @see com.vaadin.flow.router.HasDynamicTitle#getPageTitle()
	 */
	@Override
	public String getPageTitle() {
		return Translator.translate("Preparation_Groups");
	}

	@Override
	public OwlcmsLayout getRouterLayout() {
		return this.routerLayout;
	}

	@Override
	public void setRouterLayout(OwlcmsLayout routerLayout) {
		this.routerLayout = routerLayout;
	}

	@Override
	public Group update(Group domainObjectToUpdate) {
		return this.editingFormFactory.update(domainObjectToUpdate);
	}

	/**
	 * The columns of the crudGrid
	 *
	 * @param crudFormFactory what to call to create the form for editing an athlete
	 * @return
	 */
	protected GridCrud<Group> createGrid(OwlcmsCrudFormFactory<Group> crudFormFactory) {
		Grid<Group> grid = new Grid<>(Group.class, false);
		crud = new GroupGrid(Group.class, new OwlcmsGridLayout(Group.class), crudFormFactory, grid);
		grid.getThemeNames().add("row-stripes");
		grid.addColumn(Group::getName).setHeader(Translator.translate("Name")).setComparator(Group::compareTo);
		grid.addColumn(Group::getDescription).setHeader(Translator.translate("Group.Description"));
		grid.addColumn(Group::size).setHeader(Translator.translate("GroupSize")).setTextAlign(ColumnTextAlign.CENTER);
		grid.addColumn(LocalDateTimeField.getRenderer(Group::getWeighInTime, this.getLocale()))
		        .setHeader(Translator.translate("WeighInTime")).setComparator(Group::compareToWeighIn);
		grid.addColumn(LocalDateTimeField.getRenderer(Group::getCompetitionTime, this.getLocale()))
		        .setHeader(Translator.translate("StartTime"));
		grid.addColumn(Group::getPlatform).setHeader(Translator.translate("Platform"));
		String translation = Translator.translate("EditAthletes");
		int tSize = translation.length();
		grid.addColumn(new ComponentRenderer<>(p -> {
			Button technical = openInNewTab(RegistrationContent.class, translation, p != null ? p.getName() : "?");
			// prevent grid row selection from triggering
			technical.getElement().addEventListener("click", ignore -> {
			}).addEventData("event.stopPropagation()");
			technical.addThemeVariants(ButtonVariant.LUMO_SMALL);
			return technical;
		})).setHeader("").setWidth(tSize + "ch");

		crud.setCrudListener(this);
		crud.setClickRowToUpdate(true);
		grid.setSelectionMode(SelectionMode.MULTI);
		return crud;
	}

	private <C extends Component> String getWindowOpenerFromClass(Class<C> targetClass,
	        String parameter) {
		return "window.open('" + URLUtils.getUrlFromTargetClass(targetClass) + "?group="
		        + URLEncoder.encode(parameter, StandardCharsets.UTF_8)
		        + "','" + targetClass.getSimpleName() + "')";
	}

	private <C extends Component> Button openInNewTab(Class<C> targetClass,
	        String label, String parameter) {
		Button button = new Button(label);
		button.getElement().setAttribute("onClick", getWindowOpenerFromClass(targetClass, parameter != null ? parameter : "-"));
		return button;
	}

	protected void saveCallBack(OwlcmsCrudGrid<T> owlcmsCrudGrid, String successMessage, CrudOperation operation, T domainObject) {
		try {
			// logger.debug("postOperation {}", domainObject);
			owlcmsCrudGrid.getOwlcmsGridLayout().hideForm();
			crud.refreshGrid();
			Notification.show(successMessage);
			logger.trace("operation performed");
		} catch (Exception e) {
			LoggerUtils.logError(logger, e);
		}
	}

}
