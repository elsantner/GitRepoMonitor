package at.aau.ainf.gitrepomonitor.gui.auth;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import at.aau.ainf.gitrepomonitor.core.files.authentication.AuthenticationInformation;
import at.aau.ainf.gitrepomonitor.core.files.authentication.HttpsCredentials;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SSLInformation;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import at.aau.ainf.gitrepomonitor.core.git.GitManager;
import at.aau.ainf.gitrepomonitor.gui.ErrorDisplay;
import at.aau.ainf.gitrepomonitor.gui.MasterPasswordQuery;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import com.sun.javafx.collections.ImmutableObservableList;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import javafx.stage.*;
import javafx.util.Callback;

import javax.naming.AuthenticationException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class ControllerAuthList implements Initializable, ErrorDisplay, PropertyChangeListener {

    @FXML
    public ListView<AuthenticationInformation> listHTTPS;
    @FXML
    public ListView<AuthenticationInformation> listSSL;

    private FileManager fileManager;

    public static FXMLLoader getLoader() {
        return new FXMLLoader(ControllerAuthList.class.getResource("/at/aau/ainf/gitrepomonitor/gui/auth/auth_list.fxml"),
                ResourceStore.getResourceBundle());
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

        listHTTPS.setCellFactory(new AuthInfoCellFactory(listHTTPS));
        listSSL.setCellFactory(new AuthInfoCellFactory(listSSL));
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
}
