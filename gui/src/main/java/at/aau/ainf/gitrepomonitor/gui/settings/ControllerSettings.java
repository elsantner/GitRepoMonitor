package at.aau.ainf.gitrepomonitor.gui.settings;

import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import at.aau.ainf.gitrepomonitor.gui.ErrorDisplay;
import at.aau.ainf.gitrepomonitor.gui.MasterPasswordQuery;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

public class ControllerSettings implements Initializable, ErrorDisplay, MasterPasswordQuery {

    @FXML
    public CheckBox ckboxCacheMP;
    @FXML
    public HBox containerMaxUses;
    @FXML
    public HBox containerExpiryTime;
    @FXML
    public TextField txtMaxNumUses;
    @FXML
    public TextField txtExpirationTime;
    @FXML
    public RadioButton radioBtnDontClear;
    @FXML
    public RadioButton radioBtnMaxUses;
    @FXML
    public RadioButton radioBtnExpirationTime;
    @FXML
    public AnchorPane containerCacheSettings;
    @FXML
    public Button btnSave;

    private SecureStorage secStorage = SecureStorage.getImplementation();
    private final String REGEX_INTEGER_ONLY = "^\\d+$";

    public static FXMLLoader getLoader() {
        return new FXMLLoader(ControllerSettings.class.getResource("/at/aau/ainf/gitrepomonitor/gui/settings/settings.fxml"),
                ResourceStore.getResourceBundle());
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupUI();
        loadSettings();
    }

    private void setupUI() {
        containerMaxUses.managedProperty().bind(containerMaxUses.visibleProperty());
        containerMaxUses.visibleProperty().bind(radioBtnMaxUses.selectedProperty());
        containerExpiryTime.managedProperty().bind(containerExpiryTime.visibleProperty());
        containerExpiryTime.visibleProperty().bind(radioBtnExpirationTime.selectedProperty());
        containerCacheSettings.disableProperty().bind(ckboxCacheMP.selectedProperty().not());
        // validate input whenever the cache method is changed
        txtMaxNumUses.textProperty().addListener((observable, oldValue, newValue) -> validateInput());
        txtExpirationTime.textProperty().addListener((observable, oldValue, newValue) -> validateInput());
        radioBtnDontClear.selectedProperty().addListener((observable, oldValue, newValue) -> validateInput());
        radioBtnMaxUses.selectedProperty().addListener((observable, oldValue, newValue) -> validateInput());
        radioBtnExpirationTime.selectedProperty().addListener((observable, oldValue, newValue) -> validateInput());
        ckboxCacheMP.selectedProperty().addListener((observable, oldValue, newValue) -> validateInput());
    }

    private void loadSettings() {
        ckboxCacheMP.setSelected(secStorage.isMasterPasswordCacheEnabled());
    }

    @FXML
    public void onBtnCancelClick(ActionEvent actionEvent) {
        Stage stage = (Stage) ckboxCacheMP.getScene().getWindow();
        stage.setMinHeight(stage.getHeight());
        stage.close();
    }

    @FXML
    public void onBtnSaveClick(ActionEvent actionEvent) {
        try {
            secStorage.enableMasterPasswordCache(ckboxCacheMP.isSelected());

            Stage stage = (Stage) ckboxCacheMP.getScene().getWindow();
            stage.close();
        } catch (SecurityException ex) {
            showError("Wrong Master Password");
        } catch (Exception ex) {
            showError(ex.getMessage());
            ex.printStackTrace();
        }
    }

    @FXML
    public void onBtnChangeMPClick(ActionEvent actionEvent) {
    }

    private void validateInput() {
        if (!ckboxCacheMP.isSelected() || radioBtnDontClear.isSelected()) {
            btnSave.setDisable(false);
        } else if (radioBtnMaxUses.isSelected()) {
            btnSave.setDisable(!Pattern.matches(REGEX_INTEGER_ONLY, txtMaxNumUses.getText()));
        } else if (radioBtnExpirationTime.isSelected()) {
            btnSave.setDisable(!Pattern.matches(REGEX_INTEGER_ONLY, txtExpirationTime.getText()));
        }
    }
}
