package at.aau.ainf.gitrepomonitor.gui;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.Transition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.eclipse.jgit.lib.ProgressMonitor;

import java.net.URL;
import java.text.DecimalFormat;
import java.util.ResourceBundle;

public class StatusBarController implements Initializable, StatusDisplay {
    @FXML
    protected Label lblStatus;
    private Animation statusAnimation;

    protected MainProgessMonitor progessMonitor;

    @Override
    public void displayStatus(String status) {
        Platform.runLater(() -> {
            lblStatus.setText(status);
            statusAnimation.play();
        });
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        progessMonitor = new MainProgessMonitor();
        statusAnimation = new Transition() {
            {
                setCycleDuration(Duration.millis(1000));
                setInterpolator(Interpolator.EASE_OUT);
            }

            @Override
            protected void interpolate(double frac) {
                Color vColor = new Color(1-frac, 0, 0, 1);
                lblStatus.setTextFill(vColor);
            }
        };
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
