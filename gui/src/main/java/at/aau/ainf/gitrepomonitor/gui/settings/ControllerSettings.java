package at.aau.ainf.gitrepomonitor.gui.settings;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.Settings;
import at.aau.ainf.gitrepomonitor.core.files.StoragePath;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import at.aau.ainf.gitrepomonitor.core.authentication.SecureStorage;
import at.aau.ainf.gitrepomonitor.gui.AlertDisplay;
import at.aau.ainf.gitrepomonitor.gui.MasterPasswordQuery;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Pair;

import javax.naming.AuthenticationException;
import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

/**
 * Controller for settings window.
 */
public class ControllerSettings implements Initializable, AlertDisplay, MasterPasswordQuery {

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
    @FXML
    public TextField txtPath;

    private SecureStorage secStorage;
    private final String REGEX_INTEGER_ONLY = "^\\d+$";

    /**
     * Get FXML loader for this GUI component.
     * @return configured FXML loader
     */
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

    /**
     * Set master password related buttons depending on if an MP is set or not.
     */
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

    /**
     * Load settings and display on GUI.
     */
    private void loadSettings() {
        ckboxCacheMP.setSelected(secStorage.isMasterPasswordCacheEnabled());
        Settings settings = Settings.getSettings();

        txtPath.setText(StoragePath.getCurrentPath());

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

    /**
     * Persistently update settings.
     * @param actionEvent Event
     */
    @FXML
    public void onBtnSaveClick(ActionEvent actionEvent) {
        try {
            validatePath();

            // if path was changed, reload data
            if (!StoragePath.getCurrentPath().equals(txtPath.getText())) {

                // check if new path already contains program files
                if (!StoragePath.containsProgramFiles(txtPath.getText())) {
                    // if user does not agree, abort method
                    if (!showConfirmationDialog(Alert.AlertType.WARNING,
                            ResourceStore.getString("settings.path_change_new.title"),
                            ResourceStore.getString("settings.path_change_new.header"),
                            ResourceStore.getString("settings.path_change_new.content"))) {
                        return;
                    }
                }

                StoragePath.setCurrentPath(txtPath.getText());
                FileManager.getInstance().storagePathChanged();
                Settings.storagePathChanged();

                showInformationDialog(ResourceStore.getString("settings.migrate_success.title"),
                        ResourceStore.getString("settings.migrate_success.header"),
                        null);
            }

            // update master password cache settings
            secStorage.enableMasterPasswordCache(ckboxCacheMP.isSelected());
            if (ckboxCacheMP.isSelected()) {
                secStorage.setMasterPasswordCacheMethod(getCacheClearMethod(), getCacheClearValue());
            }

            Stage stage = (Stage) ckboxCacheMP.getScene().getWindow();
            stage.close();
        } catch (SecurityException ex) {
            showErrorWrongMasterPW();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    /**
     * Validate path input.
     * Path must exist and be a directory with RW rights.
     */
    private void validatePath() {
        File storagePath = new File(txtPath.getText());
        if (!storagePath.exists() || !storagePath.isDirectory()) {
            throw new IllegalArgumentException(ResourceStore.getString("settings.invalid_path"));
        }
        if (!storagePath.canWrite() || !storagePath.canRead()) {
            throw new IllegalArgumentException(ResourceStore.getString("settings.insufficient_path_rights"));
        }
    }

    /**
     * Get value for selected cache clear method (MAX_USES or EXPIRATION_TIME).
     * @return Cache clear value, or null if "don't clear" is selected
     */
    private Integer getCacheClearValue() {
        if (radioBtnDontClear.isSelected()) {
            return null;
        } else if (radioBtnMaxUses.isSelected()) {
            return Integer.parseInt(txtMaxNumUses.getText());
        } else {
            return Integer.parseInt(txtExpirationTime.getText());
        }
    }

    /**
     * Get selected cache clear method.
     * @return Selected cache clear method.
     */
    private Settings.CacheClearMethod getCacheClearMethod() {
        if (radioBtnDontClear.isSelected()) {
            return Settings.CacheClearMethod.NONE;
        } else if (radioBtnMaxUses.isSelected()) {
            return Settings.CacheClearMethod.MAX_USES;
        } else {
            return Settings.CacheClearMethod.EXPIRATION_TIME;
        }
    }

    /**
     * Propt user to change master password.
     * @param actionEvent Event
     */
    @FXML
    public void onBtnChangeMPClick(ActionEvent actionEvent) {
        try {
            if (secStorage.isMasterPasswordSet()) {
                Pair<String, String> input = showChangeMasterPasswordInputDialog();
                if (input != null) {
                    secStorage.updateMasterPassword(input.getKey().toCharArray(), input.getValue().toCharArray());
                    showInformationDialog(ResourceStore.getString("settings.mp_changed"),
                            ResourceStore.getString("settings.mp_changed.content"), "");
                }
                setMPButtonDisplay();
            }
        } catch (SecurityException | AuthenticationException ex) {
            showErrorWrongMasterPW();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    /**
     * Validate cache clear method input and enable/disable save button accordingly.
     * Max uses and expiration time must be integers
     */
    private void validateInput() {
        if (!ckboxCacheMP.isSelected() || radioBtnDontClear.isSelected()) {
            btnSave.setDisable(false);
        } else if (radioBtnMaxUses.isSelected()) {
            btnSave.setDisable(!Pattern.matches(REGEX_INTEGER_ONLY, txtMaxNumUses.getText()));
        } else if (radioBtnExpirationTime.isSelected()) {
            btnSave.setDisable(!Pattern.matches(REGEX_INTEGER_ONLY, txtExpirationTime.getText()));
        }
    }

    /**
     * Propt user to set master password.
     * @param actionEvent Event
     */
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

    /**
     * Show confirmation dialog and if confirmed reset master password
     * @param actionEvent Event
     */
    @FXML
    public void onBtnResetMPClick(ActionEvent actionEvent) {
        try {
            if (secStorage.isMasterPasswordSet()) {
                if (showConfirmResetMPDialog()) {
                    secStorage.resetMasterPassword();
                    showInformationDialog(ResourceStore.getString("settings.mp_reset"),
                            ResourceStore.getString("settings.mp_reset.content"), "");
                }
                setMPButtonDisplay();
            }
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    /**
     * Show confirmation dialog for master password reset.
     * @return True, iff confirmed.
     */
    private boolean showConfirmResetMPDialog() {
        return showConfirmationDialog(Alert.AlertType.WARNING,
                ResourceStore.getString("settings.confirm_reset_mp.title"),
                ResourceStore.getString("settings.confirm_reset_mp.header"),
                ResourceStore.getString("settings.confirm_reset_mp.content"));
    }

    @FXML
    public void onBtnChoosePathClick(ActionEvent actionEvent) {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle(ResourceStore.getString("settings.select_path.title"));
        dirChooser.setInitialDirectory(Utils.getDeepestExistingDirectory(txtPath.getText()));
        File selectedFile = dirChooser.showDialog(txtPath.getScene().getWindow());
        if (selectedFile != null) {
            txtPath.setText(selectedFile.getAbsolutePath());
        }
    }
}
