package at.aau.ainf.gitrepomonitor.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import org.eclipse.jgit.lib.ProgressMonitor;

import java.net.URL;
import java.text.DecimalFormat;
import java.util.ResourceBundle;

public class StatusBarController implements Initializable, StatusDisplay {
    @FXML
    protected Label lblStatus;

    protected MainProgessMonitor progessMonitor;

    @Override
    public void displayStatus(String status) {
        Platform.runLater(() -> lblStatus.setText(status));
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        progessMonitor = new MainProgessMonitor();
    }

    protected class MainProgessMonitor implements ProgressMonitor {

        private int totalWork;
        private final DecimalFormat df = new DecimalFormat("##.##%");

        @Override
        public void start(int totalTasks) {

        }

        @Override
        public void beginTask(String title, int totalWork) {
            displayStatus("Pull started ...");
            this.totalWork = totalWork;
        }

        @Override
        public void update(int completed) {
            displayStatus("Status : " + df.format((double) completed/totalWork));
        }

        @Override
        public void endTask() {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}
