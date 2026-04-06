package seedu.address.ui;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import seedu.address.commons.core.GuiSettings;
import seedu.address.commons.core.LogsCenter;
import seedu.address.commons.core.index.Index;
import seedu.address.logic.Logic;
import seedu.address.logic.commands.CommandResult;
import seedu.address.logic.commands.exceptions.CommandException;
import seedu.address.logic.parser.exceptions.ParseException;
import seedu.address.model.person.Person;

/**
 * The Main Window. Provides the basic application layout containing
 * a menu bar and space where other JavaFX elements can be placed.
 */
public class MainWindow extends UiPart<Stage> {

    private static final String FXML = "MainWindow.fxml";
    private static final String DARK_THEME_CSS = "/view/DarkTheme.css";
    private static final String LIGHT_THEME_CSS = "/view/LightTheme.css";

    private final Logger logger = LogsCenter.getLogger(getClass());

    private Stage primaryStage;
    private Logic logic;
    private boolean isDarkMode = true;

    // Independent Ui parts residing in this Ui container
    private PersonListPanel personListPanel;
    private ResultDisplay resultDisplay;
    private HelpWindow helpWindow;
    private CommandBox commandBox;

    @FXML
    private StackPane commandBoxPlaceholder;

    @FXML
    private MenuItem helpMenuItem;

    @FXML
    private StackPane personListPanelPlaceholder;

    @FXML
    private StackPane contactDetailPanelPlaceholder;

    @FXML
    private StackPane resultDisplayPlaceholder;

    @FXML
    private StackPane statusbarPlaceholder;

    @FXML
    private Button colorModeButton;

    /**
     * Creates a {@code MainWindow} with the given {@code Stage} and {@code Logic}.
     */
    public MainWindow(Stage primaryStage, Logic logic) {
        super(FXML, primaryStage);

        // Set dependencies
        this.primaryStage = primaryStage;
        this.logic = logic;

        // Configure the UI
        setWindowDefaultSize(logic.getGuiSettings());

        setAccelerators();

        helpWindow = new HelpWindow();
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    private void setAccelerators() {
        setAccelerator(helpMenuItem, KeyCombination.valueOf("F1"));
    }

    /**
     * Sets the accelerator of a MenuItem.
     * @param keyCombination the KeyCombination value of the accelerator
     */
    private void setAccelerator(MenuItem menuItem, KeyCombination keyCombination) {
        menuItem.setAccelerator(keyCombination);

        /*
         * TODO: the code below can be removed once the bug reported here
         * https://bugs.openjdk.java.net/browse/JDK-8131666
         * is fixed in later version of SDK.
         *
         * According to the bug report, TextInputControl (TextField, TextArea) will
         * consume function-key events. Because CommandBox contains a TextField, and
         * ResultDisplay contains a TextArea, thus some accelerators (e.g F1) will
         * not work when the focus is in them because the key event is consumed by
         * the TextInputControl(s).
         *
         * For now, we add following event filter to capture such key events and open
         * help window purposely so to support accelerators even when focus is
         * in CommandBox or ResultDisplay.
         */
        getRoot().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getTarget() instanceof TextInputControl && keyCombination.match(event)) {
                menuItem.getOnAction().handle(new ActionEvent());
                event.consume();
            }
        });
    }

    /**
     * Fills up all the placeholders of this window.
     */
    void fillInnerParts() {
        ContactDetailPanel contactDetailPanel = new ContactDetailPanel();
        contactDetailPanelPlaceholder.getChildren().add(contactDetailPanel.getRoot());

        personListPanel = new PersonListPanel(logic.getDisplayedPersonList(), index -> {
            try {
                executeCommand("delete " + index);
            } catch (Exception e) {
                // error shown in resultDisplay
            }
        }, text -> commandBox.setCommandTextField(text),
                index -> handlePicUpload(Index.fromOneBased(index)),
                person -> contactDetailPanel.updatePerson(person,
                        logic.getDisplayedPersonList().indexOf(person) + 1));

        personListPanelPlaceholder.getChildren().add(personListPanel.getRoot());

        // Select first by default
        personListPanel.selectFirstIfAvailable();

        // Listen to changes in the list to re-select if needed
        logic.getDisplayedPersonList().addListener((javafx.collections.ListChangeListener<Person>) c -> {
            // Only force selection if nothing is selected or if we just started
            if (personListPanel.getRoot().getScene() != null
                    && getPersonListPanel().getRoot().lookup(".list-view").isFocused() == false) {
                personListPanel.selectFirstIfAvailable();
            }
        });

        resultDisplay = new ResultDisplay();
        resultDisplayPlaceholder.getChildren().add(resultDisplay.getRoot());

        StatusBarFooter statusBarFooter = new StatusBarFooter(logic.getAddressBookFilePath());
        statusbarPlaceholder.getChildren().add(statusBarFooter.getRoot());

        commandBox = new CommandBox(this::executeCommand);
        commandBoxPlaceholder.getChildren().add(commandBox.getRoot());

        Platform.runLater(this::showFollowUpReminders);
    }

    /**
     * Shows follow-up reminders in the result display on startup.
     */
    private void showFollowUpReminders() {
        List<Person> followUps = logic.getPersonsWithFollowUp();
        if (followUps.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder("Follow-up reminders:\n");
        for (Person p : followUps) {
            sb.append("  - ").append(p.getName()).append(": ").append(p.getFollowUp()).append("\n");
        }
        resultDisplay.setFeedbackToUser(sb.toString().trim());
    }

    /**
     * Sets the default size based on {@code guiSettings}.
     */
    private void setWindowDefaultSize(GuiSettings guiSettings) {
        primaryStage.setHeight(guiSettings.getWindowHeight());
        primaryStage.setWidth(guiSettings.getWindowWidth());
        if (guiSettings.getWindowCoordinates() != null) {
            primaryStage.setX(guiSettings.getWindowCoordinates().getX());
            primaryStage.setY(guiSettings.getWindowCoordinates().getY());
        }
    }

    /**
     * Opens the help window or focuses on it if it's already opened.
     */
    @FXML
    public void handleHelp() {
        if (!helpWindow.isShowing()) {
            helpWindow.show();
        } else {
            helpWindow.focus();
        }
    }

    void show() {
        primaryStage.show();
    }

    /**
     * Handles picture upload for a person.
     */
    void handlePicUpload(Index index) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Profile Picture");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File file = fileChooser.showOpenDialog(primaryStage);
        if (file != null) {
            try {
                logger.info("Setting up picture...");
                logic.setPicture(index, file.getAbsolutePath());
                resultDisplay.setFeedbackToUser("Profile picture updated for person " + index.getOneBased() + ".");
            } catch (CommandException e) {
                logger.warning("An error occurred while setting picture!");
                resultDisplay.setFeedbackToUser("Failed to update picture: " + e.getMessage());
            }
        }
    }

    /**
     * Toggles between dark and light color mode.
     */
    @FXML
    void handleToggleColorMode() {
        Scene scene = primaryStage.getScene();
        scene.getStylesheets().clear();
        if (isDarkMode) {
            scene.getStylesheets().add(getClass().getResource(LIGHT_THEME_CSS).toExternalForm());
            colorModeButton.setText("🌙");
            isDarkMode = false;
        } else {
            scene.getStylesheets().add(getClass().getResource(DARK_THEME_CSS).toExternalForm());
            colorModeButton.setText("☀");
            isDarkMode = true;
        }
        scene.getStylesheets().add(getClass().getResource("/view/Extensions.css").toExternalForm());
    }

    /**
     * Closes the application.
     */
    @FXML
    private void handleExit() {
        GuiSettings guiSettings = new GuiSettings(primaryStage.getWidth(), primaryStage.getHeight(),
                (int) primaryStage.getX(), (int) primaryStage.getY());
        logic.setGuiSettings(guiSettings);
        helpWindow.hide();
        primaryStage.hide();
    }

    public PersonListPanel getPersonListPanel() {
        return personListPanel;
    }

    /**
     * Executes the command and returns the result.
     *
     * @see seedu.address.logic.Logic#execute(String)
     */
    private CommandResult executeCommand(String commandText) throws CommandException, ParseException {
        try {
            CommandResult commandResult = logic.execute(commandText);
            logger.info("Result: " + commandResult.getFeedbackToUser());
            resultDisplay.setFeedbackToUser(commandResult.getFeedbackToUser());

            if (commandResult.isShowHelp()) {
                handleHelp();
            }

            if (commandResult.isExit()) {
                handleExit();
            }

            if (commandResult.isToggleColorMode()) {
                handleToggleColorMode();
            }

            if (commandResult.isShowPicPicker()) {
                handlePicUpload(Index.fromZeroBased(commandResult.getPicPickerIndex()));
            }

            return commandResult;
        } catch (CommandException | ParseException e) {
            logger.info("An error occurred while executing command: " + commandText);
            resultDisplay.setFeedbackToUser(e.getMessage());
            throw e;
        }
    }
}
