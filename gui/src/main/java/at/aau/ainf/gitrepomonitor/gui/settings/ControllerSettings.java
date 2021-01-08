package at.aau.ainf.gitrepomonitor.gui.settings;

import at.aau.ainf.gitrepomonitor.core.files.Utils;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorageSettings;
import at.aau.ainf.gitrepomonitor.gui.ErrorDisplay;
import at.aau.ainf.gitrepomonitor.gui.MasterPasswordQuery;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Pair;

import javax.naming.AuthenticationException;
import java.net.URL;
import java.util.Optional;
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
    @FXML
    public Button btnChangeMP;
    @FXML
    public Button btnResetMP;
    @FXML
    public HBox containerMPisSet;
    @FXML
    public Button btnSetMP;

    private SecureStorage secStorage;
    private final String REGEX_INTEGER_ONLY = "^\\d+$";

    public static FXMLLoader getLoader() {
        return new FXMLLoader(ControllerSettings.class.getResource("/at/aau/ainf/gitrepomonitor/gui/settings/settings.fxml"),
                ResourceStore.getResourceBundle());
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        secStorage = SecureStorage.getImplementation();
        setupUI();
        setMPButtonDisplay();
        loadSettings();
    }

    private void setMPButtonDisplay() {
        if (!secStorage.isMasterPasswordSet()) {
            btnSetMP.setVisible(true);
            containerMPisSet.setVisible(false);
        } else {
            btnSetMP.setVisible(false);
            containerMPisSet.setVisible(true);
        }
    }

    private void setupUI() {
        btnChangeMP.managedProperty().bind(btnChangeMP.visibleProperty());
        containerMPisSet.managedProperty().bind(containerMPisSet.visibleProperty());

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
        SecureStorageSettings settings = secStorage.getSettings();

        switch (settings.getClearMethod()) {
            case NONE:
                radioBtnDontClear.setSelected(true);
                break;
            case MAX_USES:
                radioBtnMaxUses.setSelected(true);
                txtMaxNumUses.setText(String.valueOf(settings.getClearValue()));
                break;
            case EXPIRATION_TIME:
                radioBtnExpirationTime.setSelected(true);
                txtExpirationTime.setText(String.valueOf(settings.getClearValue()));
                break;
        }
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
            secStorage.setMasterPasswordCacheMethod(getCacheClearMethod(), getCacheClearValue());

            Stage stage = (Stage) ckboxCacheMP.getScene().getWindow();
            stage.close();
        } catch (SecurityException ex) {
            showError("Wrong Master Password");
        } catch (Exception ex) {
            showError(ex.getMessage());
            ex.printStackTrace();
        }
    }

    private Integer getCacheClearValue() {
        if (radioBtnDontClear.isSelected()) {
            return null;
        } else if (radioBtnMaxUses.isSelected()) {
            return Integer.parseInt(txtMaxNumUses.getText());
        } else {
            return Integer.parseInt(txtExpirationTime.getText());
        }
    }

    private SecureStorageSettings.CacheClearMethod getCacheClearMethod() {
        if (radioBtnDontClear.isSelected()) {
            return SecureStorageSettings.CacheClearMethod.NONE;
        } else if (radioBtnMaxUses.isSelected()) {
            return SecureStorageSettings.CacheClearMethod.MAX_USES;
        } else {
            return SecureStorageSettings.CacheClearMethod.EXPIRATION_TIME;
        }
    }

    @FXML
    public void onBtnChangeMPClick(ActionEvent actionEvent) {
        try {
            if (secStorage.isMasterPasswordSet()) {
                Pair<String, String> input = showChangeMasterPasswordInputDialog();
                if (input != null) {
                    secStorage.updateMasterPassword(input.getKey().toCharArray(), input.getValue().toCharArray());
                }
                setMPButtonDisplay();
                showInfoDialog("Master Password Changed", "The Master Password was successfully changed!");
            }
        } catch (SecurityException | AuthenticationException ex) {
            showError("Wrong Master Password");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
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

    @FXML
    public void onBtnSetMPClick(ActionEvent actionEvent) {
        try {
            if (!secStorage.isMasterPasswordSet()) {
                String masterPW = showMasterPasswordInputDialog(true);
                if (masterPW != null) {
                    secStorage.setMasterPassword(Utils.toCharOrNull(masterPW));
                }
                setMPButtonDisplay();
            }
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    public void onBtnResetMPClick(ActionEvent actionEvent) {
        try {
            if (secStorage.isMasterPasswordSet()) {
                if (showConfirmResetMPDialog()) {
                    secStorage.resetMasterPassword();
                }
                setMPButtonDisplay();
                showInfoDialog("Master Password Reset", "The Master Password was reset and all stored credentials deleted!");
            }
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void showInfoDialog(String title, String text) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(text);
        alert.showAndWait();
    }

    private boolean showConfirmResetMPDialog() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Reset Master Password");
        alert.setHeaderText("Resetting the MP will delete ALL stored credential information.");
        alert.setContentText("Are you sure you want to continue?");

        Optional<ButtonType> res = alert.showAndWait();
        return res.isPresent() && res.get() == ButtonType.OK;
    }
}
