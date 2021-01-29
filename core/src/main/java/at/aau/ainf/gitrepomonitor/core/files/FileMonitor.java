package at.aau.ainf.gitrepomonitor.core.files;

import java.io.File;

/**
 * Periodically (every 2 seconds) check if a file / directory still exists.
 * This can be useful to detect unexpected disconnects of external storage devices (e.g. USB sticks).
 */
class FileMonitor {

    private File file;
    private Listener listener;
    private Thread thread;
    private boolean isStopped;

    public FileMonitor(File f, Listener l) {
        this.file = f;
        this.listener = l;
    }

    public void start() {
        if (thread != null) {
            throw new IllegalStateException("monitor already running");
        }

        isStopped = false;
        thread = new Thread(() -> {
            while (!isStopped) {
                if (!file.exists()) {
                    notifyFileUnavailable();
                    isStopped = true;
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    // no action required
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void notifyFileUnavailable() {
        if (listener != null) {
            listener.fileUnavailable(file);
        }
    }

    public void stop() {
        isStopped = true;
    }

    public interface Listener {
        void fileUnavailable(File file);
    }
}
