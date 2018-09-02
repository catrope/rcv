/*
 * Ranked Choice Voting Universal Tabulator
 * Copyright (c) 2018 Jonathan Moldover, Louis Eisenberg, and Hylton Edingfield
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See
 * the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this
 * program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.rcv;

import com.rcv.RawContestConfig.CVRSource;
import com.rcv.RawContestConfig.Candidate;
import com.rcv.RawContestConfig.ContestRules;
import com.rcv.RawContestConfig.OutputSettings;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.util.StringConverter;

public class GuiConfigController implements Initializable {

  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final String CONFIG_FILE_NAME = "config_file_documentation.txt";
  @FXML
  private TextArea textAreaHelp;
  @FXML
  private TextField textFieldContestName;
  @FXML
  private TextField textFieldOutputDirectory;
  @FXML
  private DatePicker datePickerContestDate;
  @FXML
  private TextField textFieldContestJurisdiction;
  @FXML
  private TextField textFieldContestOffice;
  @FXML
  private ToggleGroup toggleTabulateByPrecinct;
  @FXML
  private RadioButton radioTabulateByPrecinctTrue;
  @FXML
  private TableView<CVRSource> tableViewCvrFiles;
  @FXML
  private TableColumn<CVRSource, String> tableColumnCvrFilePath;
  @FXML
  private TableColumn<CVRSource, Integer> tableColumnCvrFirstVoteCol;
  @FXML
  private TableColumn<CVRSource, Integer> tableColumnCvrIdCol;
  @FXML
  private TableColumn<CVRSource, Integer> tableColumnCvrPrecinctCol;
  @FXML
  private TableColumn<CVRSource, String> tableColumnCvrProvider;
  @FXML
  private TextField textFieldCvrFilePath;
  @FXML
  private TextField textFieldCvrFirstVoteCol;
  @FXML
  private TextField textFieldCvrIdCol;
  @FXML
  private TextField textFieldCvrPrecinctCol;
  @FXML
  private TextField textFieldCvrProvider;
  @FXML
  private TableView<Candidate> tableViewCandidates;
  @FXML
  private TableColumn<Candidate, String> tableColumnCandidateName;
  @FXML
  private TableColumn<Candidate, String> tableColumnCandidateCode;
  @FXML
  private TextField textFieldCandidateName;
  @FXML
  private TextField textFieldCandidateCode;
  @FXML
  private ChoiceBox<Tabulator.TieBreakMode> choiceTiebreakMode;
  @FXML
  private ChoiceBox<Tabulator.OvervoteRule> choiceOvervoteRule;
  @FXML
  private TextField textFieldMaxRankingsAllowed;
  @FXML
  private TextField textFieldMaxSkippedRanksAllowed;
  @FXML
  private TextField textFieldNumberOfWinners;
  @FXML
  private TextField textFieldDecimalPlacesForVoteArithmetic;
  @FXML
  private TextField textFieldMinimumVoteThreshold;
  @FXML
  private TextField textFieldOvervoteLabel;
  @FXML
  private TextField textFieldUndervoteLabel;
  @FXML
  private TextField textFieldUndeclaredWriteInLabel;
  @FXML
  private TextField textFieldRulesDescription;
  @FXML
  private ToggleGroup toggleBatchElimination;
  @FXML
  private RadioButton radioBatchEliminationTrue;
  @FXML
  private ToggleGroup toggleContinueUntilTwoCandidatesRemain;
  @FXML
  private RadioButton radioContinueUntilTwoCandidatesRemainTrue;
  @FXML
  private ToggleGroup toggleExhaustOnDuplicateCandidate;
  @FXML
  private RadioButton radioExhaustOnDuplicateCandidateTrue;
  @FXML
  private ToggleGroup toggleTreatBlankAsUndeclaredWriteIn;
  @FXML
  private RadioButton radioTreatBlankAsUndeclaredWriteInTrue;
  @FXML
  private ButtonBar buttonBar;

  public void buttonMenuClicked() {
    GuiContext.getInstance().showContent("/GuiMenuLayout.fxml");
    GuiContext.getInstance().setConfig(null);
    GuiContext.getInstance().setSelectedFile(null);
  }

  public void buttonValidateClicked() {
    buttonBar.setDisable(true);
    ValidatorService service = new ValidatorService(createRawContestConfig());
    service.setOnSucceeded(event -> buttonBar.setDisable(false));
    service.setOnCancelled(event -> buttonBar.setDisable(false));
    service.setOnFailed(event -> buttonBar.setDisable(false));
    service.start();
  }

  public void buttonSaveClicked() {
    FileChooser fc = new FileChooser();
    if (GuiContext.getInstance().getSelectedFile() == null) {
      fc.setInitialDirectory(new File(System.getProperty("user.dir")));
    } else {
      fc.setInitialDirectory(new File(GuiContext.getInstance().getSelectedFile().getParent()));
      fc.setInitialFileName(GuiContext.getInstance().getSelectedFile().getName());
    }
    fc.getExtensionFilters().add(new ExtensionFilter("JSON files", "*.json"));
    fc.setTitle("Save Config");

    File saveFile = fc.showSaveDialog(null);
    if (saveFile != null) {
      JsonParser.createFileFromRawContestConfig(saveFile, createRawContestConfig());
    }
  }

  public void buttonOutputDirectoryClicked() {
    DirectoryChooser dc = new DirectoryChooser();
    dc.setInitialDirectory(new File(System.getProperty("user.dir")));
    dc.setTitle("Output Directory");

    File outputDirectory = dc.showDialog(null);
    if (outputDirectory != null) {
      textFieldOutputDirectory.setText(outputDirectory.getAbsolutePath());
    }
  }

  public void buttonClearDatePickerContestDateClicked() {
    datePickerContestDate.getEditor().clear();
    datePickerContestDate.setValue(null);
  }

  public void buttonCvrFilePathClicked() {
    FileChooser fc = new FileChooser();
    fc.setInitialDirectory(new File(System.getProperty("user.dir")));
    fc.getExtensionFilters().add(new ExtensionFilter("Excel files", "*.xls", "*.xlsx"));
    fc.setTitle("Select CVR File");

    File openFile = fc.showOpenDialog(null);
    if (openFile != null) {
      textFieldCvrFilePath.setText(openFile.getAbsolutePath());
    }
  }

  public void buttonAddCvrFileClicked() {
    CVRSource cvrSource = new CVRSource();
    if (textFieldCvrFilePath.getText().isEmpty()) {
      Logger.guiLog(Level.WARNING, "CVR file path is required!");
    } else if (textFieldCvrFirstVoteCol.getText().isEmpty()) {
      Logger.guiLog(Level.WARNING, "CVR first vote column is required!");
    } else {
      cvrSource.setFilePath(textFieldCvrFilePath.getText());
      cvrSource.setFirstVoteColumnIndex(getIntValueElse(textFieldCvrFirstVoteCol, null));
      cvrSource.setIdColumnIndex(getIntValueElse(textFieldCvrIdCol, null));
      cvrSource.setPrecinctColumnIndex(getIntValueElse(textFieldCvrPrecinctCol, null));
      cvrSource.setProvider(textFieldCvrProvider.getText());
      tableViewCvrFiles.getItems().add(cvrSource);
      textFieldCvrFilePath.clear();
      textFieldCvrFirstVoteCol.clear();
      textFieldCvrIdCol.clear();
      textFieldCvrPrecinctCol.clear();
      textFieldCvrProvider.clear();
    }
  }

  public void buttonDeleteCvrFileClicked() {
    tableViewCvrFiles
        .getItems()
        .removeAll(tableViewCvrFiles.getSelectionModel().getSelectedItems());
  }

  public void buttonAddCandidateClicked() {
    Candidate candidate = new Candidate();
    if (textFieldCandidateName.getText().isEmpty()) {
      Logger.guiLog(Level.WARNING, "Candidate name field is required!");
    } else {
      candidate.setName(textFieldCandidateName.getText());
      candidate.setCode(textFieldCandidateCode.getText());
      tableViewCandidates.getItems().add(candidate);
      textFieldCandidateName.clear();
      textFieldCandidateCode.clear();
    }
  }

  public void buttonDeleteCandidateClicked() {
    tableViewCandidates
        .getItems()
        .removeAll(tableViewCandidates.getSelectionModel().getSelectedItems());
  }

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    Logger.executionLog(Level.FINE, "Opening config creator GUI...");

    String helpText;
    try {
      helpText =
          new BufferedReader(
              new InputStreamReader(ClassLoader.getSystemResourceAsStream(CONFIG_FILE_NAME)))
              .lines()
              .collect(Collectors.joining("\n"));
    } catch (Exception exception) {
      Logger.executionLog(
          Level.SEVERE, "Error loading: %s\n%s", CONFIG_FILE_NAME, exception.toString());
      helpText = String.format("<Error loading %s>", CONFIG_FILE_NAME);
    }
    textAreaHelp.setText(helpText);

    datePickerContestDate.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(LocalDate date) {
            return date != null ? DATE_TIME_FORMATTER.format(date) : "";
          }

          @Override
          public LocalDate fromString(String string) {
            return string != null && !string.isEmpty()
                ? LocalDate.parse(string, DATE_TIME_FORMATTER)
                : null;
          }
        });

    textFieldCvrFirstVoteCol
        .textProperty()
        .addListener(new TextFieldListenerNonNegInt(textFieldCvrFirstVoteCol));
    textFieldCvrIdCol.textProperty().addListener(new TextFieldListenerNonNegInt(textFieldCvrIdCol));
    textFieldCvrPrecinctCol
        .textProperty()
        .addListener(new TextFieldListenerNonNegInt(textFieldCvrPrecinctCol));
    tableColumnCvrFilePath.setCellValueFactory(new PropertyValueFactory<>("filePath"));
    tableColumnCvrFirstVoteCol.setCellValueFactory(
        new PropertyValueFactory<>("firstVoteColumnIndex"));
    tableColumnCvrIdCol.setCellValueFactory(new PropertyValueFactory<>("idColumnIndex"));
    tableColumnCvrPrecinctCol.setCellValueFactory(
        new PropertyValueFactory<>("precinctColumnIndex"));
    tableColumnCvrProvider.setCellValueFactory(new PropertyValueFactory<>("provider"));
    tableViewCvrFiles.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

    tableColumnCandidateName.setCellValueFactory(new PropertyValueFactory<>("name"));
    tableColumnCandidateCode.setCellValueFactory(new PropertyValueFactory<>("code"));
    tableViewCandidates.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

    choiceTiebreakMode.getItems().addAll(Tabulator.TieBreakMode.values());
    choiceTiebreakMode.getItems().remove(Tabulator.TieBreakMode.MODE_UNKNOWN);
    choiceOvervoteRule.getItems().addAll(Tabulator.OvervoteRule.values());
    choiceOvervoteRule.getItems().remove(Tabulator.OvervoteRule.RULE_UNKNOWN);

    textFieldMaxRankingsAllowed
        .textProperty()
        .addListener(new TextFieldListenerNonNegInt(textFieldMaxRankingsAllowed));
    textFieldMaxSkippedRanksAllowed
        .textProperty()
        .addListener(new TextFieldListenerNonNegInt(textFieldMaxSkippedRanksAllowed));
    textFieldNumberOfWinners
        .textProperty()
        .addListener(new TextFieldListenerNonNegInt(textFieldNumberOfWinners));
    textFieldNumberOfWinners.setText(String.valueOf(ContestConfig.DEFAULT_NUMBER_OF_WINNERS));
    textFieldDecimalPlacesForVoteArithmetic
        .textProperty()
        .addListener(new TextFieldListenerNonNegInt(textFieldDecimalPlacesForVoteArithmetic));
    textFieldDecimalPlacesForVoteArithmetic.setText(
        String.valueOf(ContestConfig.DEFAULT_DECIMAL_PLACES_FOR_VOTE_ARITHMETIC));
    textFieldMinimumVoteThreshold
        .textProperty()
        .addListener(new TextFieldListenerNonNegInt(textFieldMinimumVoteThreshold));
    textFieldMinimumVoteThreshold.setText(
        String.valueOf(ContestConfig.DEFAULT_MINIMUM_VOTE_THRESHOLD));

    if (GuiContext.getInstance().getConfig() != null) {
      loadConfig(GuiContext.getInstance().getConfig());
    }
  }

  private void setTextFieldToInteger(TextField textField, Integer value) {
    if (value != null) {
      textField.setText(Integer.toString(value));
    }
  }

  private void loadConfig(ContestConfig config) {
    textFieldContestName.setText(config.getContestName());
    textFieldOutputDirectory.setText(config.getOutputDirectory());
    if (config.getContestDate() != null && !config.getContestDate().isEmpty()) {
      datePickerContestDate.setValue(LocalDate.parse(config.getContestDate(), DATE_TIME_FORMATTER));
    }
    textFieldContestJurisdiction.setText(config.getContestJurisdiction());
    textFieldContestOffice.setText(config.getContestOffice());
    radioTabulateByPrecinctTrue.setSelected(config.isTabulateByPrecinctEnabled());

    if (config.rawConfig.cvrFileSources != null) {
      tableViewCvrFiles.setItems(
          FXCollections.observableArrayList(config.rawConfig.cvrFileSources));
    }

    if (config.rawConfig.candidates != null) {
      tableViewCandidates.setItems(FXCollections.observableArrayList(config.rawConfig.candidates));
    }

    choiceTiebreakMode.setValue(config.getTiebreakMode());
    choiceOvervoteRule.setValue(config.getOvervoteRule());
    setTextFieldToInteger(textFieldMaxRankingsAllowed, config.getMaxRankingsAllowed());
    setTextFieldToInteger(textFieldMaxSkippedRanksAllowed, config.getMaxSkippedRanksAllowed());
    setTextFieldToInteger(textFieldNumberOfWinners, config.getNumberOfWinners());
    setTextFieldToInteger(
        textFieldDecimalPlacesForVoteArithmetic, config.getDecimalPlacesForVoteArithmetic());
    setTextFieldToInteger(
        textFieldMinimumVoteThreshold, config.getMinimumVoteThreshold().intValue());
    textFieldOvervoteLabel.setText(config.getOvervoteLabel());
    textFieldUndervoteLabel.setText(config.getUndervoteLabel());
    textFieldUndeclaredWriteInLabel.setText(config.getUndeclaredWriteInLabel());
    textFieldRulesDescription.setText(config.getRulesDescription());
    radioBatchEliminationTrue.setSelected(config.isBatchEliminationEnabled());
    radioContinueUntilTwoCandidatesRemainTrue.setSelected(
        config.willContinueUntilTwoCandidatesRemain());
    radioExhaustOnDuplicateCandidateTrue.setSelected(config.isExhaustOnDuplicateCandidateEnabled());
    radioTreatBlankAsUndeclaredWriteInTrue.setSelected(
        config.isTreatBlankAsUndeclaredWriteInEnabled());
  }

  private boolean getToggleBoolean(ToggleGroup toggleGroup) {
    return ((RadioButton) toggleGroup.getSelectedToggle()).getText().equals("True");
  }

  private Integer getIntValueElse(TextField textField, Integer defaultValue) {
    return !textField.getText().isEmpty() ? Integer.valueOf(textField.getText()) : defaultValue;
  }

  private String getChoiceElse(ChoiceBox choiceBox, Enum defaultValue) {
    return choiceBox.getValue() != null ? choiceBox.getValue().toString() : defaultValue.toString();
  }

  private RawContestConfig createRawContestConfig() {
    RawContestConfig config = new RawContestConfig();

    OutputSettings outputSettings = new OutputSettings();
    outputSettings.contestName = textFieldContestName.getText();
    outputSettings.outputDirectory = textFieldOutputDirectory.getText();
    outputSettings.contestDate =
        datePickerContestDate.getValue() != null ? datePickerContestDate.getValue().toString() : "";
    outputSettings.contestJurisdiction = textFieldContestJurisdiction.getText();
    outputSettings.contestOffice = textFieldContestOffice.getText();
    outputSettings.tabulateByPrecinct = getToggleBoolean(toggleTabulateByPrecinct);
    config.outputSettings = outputSettings;

    config.cvrFileSources = new ArrayList<>(tableViewCvrFiles.getItems());

    config.candidates = new ArrayList<>(tableViewCandidates.getItems());

    ContestRules rules = new ContestRules();
    rules.tiebreakMode = getChoiceElse(choiceTiebreakMode, Tabulator.TieBreakMode.MODE_UNKNOWN);
    rules.overvoteRule = getChoiceElse(choiceOvervoteRule, Tabulator.OvervoteRule.RULE_UNKNOWN);
    rules.maxRankingsAllowed = getIntValueElse(textFieldMaxRankingsAllowed, null);
    rules.maxSkippedRanksAllowed = getIntValueElse(textFieldMaxSkippedRanksAllowed, null);
    rules.numberOfWinners =
        getIntValueElse(textFieldNumberOfWinners, ContestConfig.DEFAULT_NUMBER_OF_WINNERS);
    rules.decimalPlacesForVoteArithmetic =
        getIntValueElse(
            textFieldDecimalPlacesForVoteArithmetic,
            ContestConfig.DEFAULT_DECIMAL_PLACES_FOR_VOTE_ARITHMETIC);
    rules.minimumVoteThreshold =
        getIntValueElse(
            textFieldMinimumVoteThreshold,
            ContestConfig.DEFAULT_MINIMUM_VOTE_THRESHOLD.intValue());
    rules.batchElimination = getToggleBoolean(toggleBatchElimination);
    rules.continueUntilTwoCandidatesRemain =
        getToggleBoolean(toggleContinueUntilTwoCandidatesRemain);
    rules.exhaustOnDuplicateCandidate = getToggleBoolean(toggleExhaustOnDuplicateCandidate);
    rules.treatBlankAsUndeclaredWriteIn = getToggleBoolean(toggleTreatBlankAsUndeclaredWriteIn);
    rules.overvoteLabel = textFieldOvervoteLabel.getText();
    rules.undervoteLabel = textFieldUndervoteLabel.getText();
    rules.undeclaredWriteInLabel = textFieldUndeclaredWriteInLabel.getText();
    rules.rulesDescription = textFieldRulesDescription.getText();
    config.rules = rules;

    return config;
  }

  private static class ValidatorService extends Service<Void> {

    private final RawContestConfig rawContestConfig;

    ValidatorService(RawContestConfig rawContestConfig) {
      this.rawContestConfig = rawContestConfig;
    }

    @Override
    protected Task<Void> createTask() {
      return new Task<>() {
        @Override
        protected Void call() {
          new ContestConfig(rawContestConfig).validate();
          return null;
        }
      };
    }
  }

  private class TextFieldListenerNonNegInt implements ChangeListener<String> {
    // Restricts text fields to non-negative integers

    private final TextField textField;

    TextFieldListenerNonNegInt(TextField textField) {
      this.textField = textField;
    }

    @Override
    public void changed(
        ObservableValue<? extends String> observable, String oldValue, String newValue) {
      if (!newValue.matches("\\d*")) {
        textField.setText(oldValue);
      }
    }
  }
}
