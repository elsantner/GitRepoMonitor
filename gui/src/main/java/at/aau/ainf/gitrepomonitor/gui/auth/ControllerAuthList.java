package at.aau.ainf.gitrepomonitor.gui.auth;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.authentication.AuthenticationInformation;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import at.aau.ainf.gitrepomonitor.gui.AlertDisplay;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.*;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class ControllerAuthList implements Initializable, AlertDisplay, PropertyChangeListener {

    @FXML
    public ListView<AuthenticationInformation> listHTTPS;
    @FXML
    public ListView<AuthenticationInformation> listSSL;

    private FileManager fileManager;

    public static FXMLLoader getLoader() {
        return new FXMLLoader(ControllerAuthList.class.getResource("/at/aau/ainf/gitrepomonitor/gui/auth/auth_list.fxml"),
                ResourceStore.getResourceBundle());
    }

    public static void openWindow() throws IOException {
        FXMLLoader loader = ControllerAuthList.getLoader();
        Parent root = loader.load();

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.DECORATED);
        stage.setTitle(ResourceStore.getString("auth_list"));
        stage.getIcons().add(ResourceStore.getImage("icon_app.png"));
        stage.setScene(new Scene(root));
        stage.setOnHidden(event -> ((ControllerAuthList) loader.getController()).cleanup());
        stage.sizeToScene();
        stage.show();
        stage.setMinWidth(stage.getWidth());
        stage.setMinHeight(stage.getHeight());
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.fileManager = FileManager.getInstance();
        fileManager.addAuthInfoListener(this);
        setupUI();
    }

    private void setupUI() {
        updateAuthLists();
        listHTTPS.setPlaceholder(new Label(ResourceStore.getString("auth_list.no_entries")));
        listSSL.setPlaceholder(new Label(ResourceStore.getString("auth_list.no_entries")));

        listHTTPS.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listSSL.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        listHTTPS.setCellFactory(new AuthInfoCellFactory());
        listSSL.setCellFactory(new AuthInfoCellFactory());

        listHTTPS.setOnKeyPressed(new KeyPressHandler(listHTTPS));
        listSSL.setOnKeyPressed(new KeyPressHandler(listSSL));
    }

    @FXML
    public void onBtnAddHttps(ActionEvent actionEvent) throws IOException {
        ControllerEditAuth.openWindowNewAuth(RepositoryInformation.AuthMethod.HTTPS);
    }

    @FXML
    public void onBtnAddSsl(ActionEvent actionEvent) throws IOException {
        ControllerEditAuth.openWindowNewAuth(RepositoryInformation.AuthMethod.SSL);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        updateAuthLists();
    }

    private void updateAuthLists() {
        listHTTPS.getItems().clear();
        listSSL.getItems().clear();
        for (AuthenticationInformation authInfo : fileManager.getAllAuthenticationInfos()) {
            if (authInfo.getAuthMethod() == RepositoryInformation.AuthMethod.HTTPS) {
                listHTTPS.getItems().add(authInfo);
            } else {
                listSSL.getItems().add(authInfo);
            }
        }
    }

    public void cleanup() {
        fileManager.removeAuthInfoListener(this);
    }

    static class KeyPressHandler implements EventHandler<KeyEvent>, AlertDisplay {

        private ListView<AuthenticationInformation> listView;

        public KeyPressHandler(ListView<AuthenticationInformation> listView) {
            this.listView = listView;
        }

        @Override
        public void handle(KeyEvent event) {
            try {
                if (event.getCode() == KeyCode.ENTER) {
                    AuthenticationInformation authInfo = listView.getSelectionModel().getSelectedItem();
                    if (authInfo != null) {
                        ControllerEditAuth.openWindow(authInfo);
                    }
                } else if (event.getCode() == KeyCode.DELETE) {
                    List<AuthenticationInformation> selItems = new ArrayList<>(listView.getSelectionModel().getSelectedItems());
                    if (!selItems.isEmpty()) {
                        if (showConfirmationDialog(Alert.AlertType.WARNING,
                                ResourceStore.getString("auth_list.confirm_delete_multiple.title"),
                                ResourceStore.getString("auth_list.confirm_delete_multiple.header", selItems.size()),
                                ResourceStore.getString("auth_list.confirm_delete_multiple.content"))) {

                            for (AuthenticationInformation ai : selItems) {
                                SecureStorage.getImplementation().delete(ai.getID());
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        }
    }
}