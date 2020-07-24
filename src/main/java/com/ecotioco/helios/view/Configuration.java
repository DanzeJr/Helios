package com.ecotioco.helios.view;

import com.ecotioco.helios.listener.OnProgressUpdate;
import com.ecotioco.helios.util.Constant;
import com.ecotioco.helios.util.DriveService;
import com.ecotioco.helios.util.Tools;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Configuration extends JFrame {
    private JPanel rootPanel;
    private JTextField txtPath;
    private JButton chooseBtn;
    private JLabel lblTitle;
    private JButton uploadBtn;
    private JButton changeAccountBtn;
    private JButton switchToDownloadBtn;
    private JButton switchToUploadBtn;
    private JButton changeAccountBtn1;
    private JButton downloadBtn;
    private JButton chooseSyncToBtn;
    private JComboBox cbOnlineFolders;
    private JTextField txtSyncTo;
    private String currentDir;
    private String rootId = null;
    private List<com.google.api.services.drive.model.File> onlineFolders;

    public Configuration(String title) throws HeadlessException {
        super(title);

        setContentPane(rootPanel);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
        setSize(1000, 650);
        setLocationRelativeTo(null);
        initComponents();
    }

    private void initComponents() {
        uploadBtn.setEnabled(false);
        downloadBtn.setEnabled(false);
        currentDir = System.getProperty("user.home");
        switchToDownloadBtn.addActionListener(e -> {
            try {
                onlineFolders = DriveService.getAllFolders("root", "name");
                for (com.google.api.services.drive.model.File folder : onlineFolders) {
                    cbOnlineFolders.addItem(folder.getName() + " | " + Tools.getFormattedDate(folder.getCreatedTime().getValue()));
                }
            } catch (IOException ioException) {
                JOptionPane.showMessageDialog(Configuration.this,
                        "Unable to fetch online folders!", "ERROR", JOptionPane.ERROR_MESSAGE);
                return;
            }
            switchLayout();
        });
        switchToUploadBtn.addActionListener(e -> {
            switchLayout();
        });
        changeAccountBtn1.setFocusPainted(false);
        changeAccountBtn1.addActionListener(e -> {
            DriveService.logOut();
            this.dispose();
            new Login("Sign in to Google Drive");
        });
        changeAccountBtn.setFocusPainted(false);
        changeAccountBtn.addActionListener(e -> {
            DriveService.logOut();
            this.dispose();
            new Login("Sign in to Google Drive");
        });
        chooseSyncToBtn.addActionListener(e -> {
            JFileChooser jf = new JFileChooser(currentDir);
            jf.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            jf.setAcceptAllFileFilterUsed(false);
            int opt = jf.showOpenDialog(this);
            currentDir = jf.getCurrentDirectory().getAbsolutePath();
            if (opt == JFileChooser.APPROVE_OPTION) {
                File folder = jf.getSelectedFile();
                txtSyncTo.setText(folder.getPath());
            }
        });
        chooseBtn.addActionListener(e -> {
            JFileChooser jf = new JFileChooser(currentDir);
            jf.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            jf.setAcceptAllFileFilterUsed(false);
            int opt = jf.showOpenDialog(this);
            currentDir = jf.getCurrentDirectory().getAbsolutePath();
            if (opt == JFileChooser.APPROVE_OPTION) {
                File folder = jf.getSelectedFile();
                txtPath.setText(folder.getPath());
            }
        });

        txtPath.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onPathChange();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onPathChange();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onPathChange();
            }
        });
        txtSyncTo.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onSyncToChange();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onSyncToChange();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onSyncToChange();
            }
        });

        uploadBtn.addActionListener(e -> {
            uploadBtn.setEnabled(false);
            File syncFolder = new File(txtPath.getText());
            String name;
            do {
                name = JOptionPane.showInputDialog(this,
                        "Please enter folder name to sync (leaving blank means same name as source folder)", syncFolder.getName());
                if (name == null) {
                    uploadBtn.setEnabled(true);
                    return;
                } else if (name.isEmpty()) {
                    name = syncFolder.getName();
                }
                try {
                    com.google.api.services.drive.model.File file = DriveService.checkExistName(name);
                    if (file != null) {
                        int option = JOptionPane.showConfirmDialog(Configuration.this,
                                String.format("A folder name '%s' already exist. Do you want to change name?", name),
                                "Confirmation", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
                        if (option == JOptionPane.YES_OPTION) {
                            name = null;
                        } else if (option == JOptionPane.CANCEL_OPTION) {
                            uploadBtn.setEnabled(true);
                            return;
                        }
                    }
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(Configuration.this,
                            "Error occurs", "ERROR", JOptionPane.ERROR_MESSAGE);
                    uploadBtn.setEnabled(true);
                    return;
                }
            } while (name == null);

            ProgressBarDialog dialog = new ProgressBarDialog(this, "Uploading", "GO TO DASHBOARD");
            dialog.setProgress("Saving sync foler path...", 0);
            if (Tools.setPreference(Constant.KEY_SYNC_FOLDER_PATH, syncFolder.getPath())) {
                String finalName = name;

                SwingWorker<Void, Void> swingWorker = new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() {
                        OnProgressUpdate listener = (current, total, message) -> {
                            double progress = current / (double) total * 100;
                            dialog.setProgress(message + " - " + current + "/" + total, progress);
                        };
                        try {
                            rootId = DriveService.uploadFolderInclusive(syncFolder.getPath(), finalName, null, listener);
                            Tools.setPreference(Constant.KEY_SYNC_FOLDER_ID, rootId);
                            Tools.setPreference(Constant.KEY_LAST_SYNC, System.currentTimeMillis() + "");
                        } catch (Exception exception) {
                            dialog.dispose();
                            Tools.resetConfiguration();
                            JOptionPane.showMessageDialog(Configuration.this,
                                    "Error occurs while uploading", "ERROR", JOptionPane.ERROR_MESSAGE);
                        }
                        return null;
                    }

                    @Override
                    protected void done() {
                        Toolkit.getDefaultToolkit().beep();
                        if (rootId == null) {
                            uploadBtn.setEnabled(true);
                            return;
                        }
                        dialog.setNextListener(new AbstractAction() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                dialog.dispose();
                                Configuration.this.dispose();
                                new Dashboard("Helios", 0);
                            }
                        });
                        dialog.finish();
                    }
                };
                swingWorker.execute();

                dialog.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this, "Cannot set up sync folder", "ERROR", JOptionPane.ERROR_MESSAGE);
                uploadBtn.setEnabled(true);
            }
        });

        downloadBtn.addActionListener(e -> {
            downloadBtn.setEnabled(false);
            com.google.api.services.drive.model.File syncFromFolder = onlineFolders.get(cbOnlineFolders.getSelectedIndex());
            String syncTo = txtSyncTo.getText();
            String name;
            do {
                name = JOptionPane.showInputDialog(this,
                        "Please enter folder name to download (leaving blank means same name as source folder)", syncFromFolder.getName());
                if (name == null) {
                    downloadBtn.setEnabled(true);
                    return;
                } else if (name.isEmpty()) {
                    name = syncFromFolder.getName();
                }
                File tmpFolder = new File(syncTo + File.separator + name);
                try {
                    if (tmpFolder.exists()) {
                        int option = JOptionPane.showConfirmDialog(Configuration.this,
                                String.format("A folder name '%s' already exist. Do you want to change name?\nSelect No to delete exist one.", name),
                                "Confirmation", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
                        if (option == JOptionPane.YES_OPTION) {
                            name = null;
                        } else if (option == JOptionPane.CANCEL_OPTION) {
                            downloadBtn.setEnabled(true);
                            return;
                        } else {
                            Tools.deleteFolder(tmpFolder.getPath());
                        }
                    }
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(Configuration.this,
                            "Error occurs", "ERROR", JOptionPane.ERROR_MESSAGE);
                    downloadBtn.setEnabled(true);
                    return;
                }
            } while (name == null);

            File syncFolder = new File(syncTo + File.separator + name);
            ProgressBarDialog dialog = new ProgressBarDialog(this, "Downloading", "GO TO DASHBOARD");
            dialog.setProgress("Saving sync foler path...", 0);
            if (Tools.setPreference(Constant.KEY_SYNC_FOLDER_PATH, syncFolder.getPath())) {

                SwingWorker<Void, Void> swingWorker = new SwingWorker<Void, Void>() {

                    @Override
                    protected Void doInBackground() {
                        try {
                            int total = DriveService.getOnlineFileMap("",
                                    syncFromFolder.getId(), System.currentTimeMillis(),
                                    new HashSet<>()).size() + 1;
                            AtomicInteger count = new AtomicInteger();
                            OnProgressUpdate listener = (c, t, message) -> {
                                count.getAndIncrement();
                                double progress = count.get() / (double) total * 100;
                                dialog.setProgress(message + " - " + count.get() + "/" + total, progress);
                            };
                            DriveService.downloadFolder(syncFolder.getPath(), syncFromFolder.getId(), DriveService.getExportFormats(), listener);
                            rootId = syncFromFolder.getId();
                            Tools.setPreference(Constant.KEY_SYNC_FOLDER_ID, rootId);
                            Tools.setPreference(Constant.KEY_LAST_SYNC, System.currentTimeMillis() + "");
                        } catch (Exception exception) {
                            dialog.dispose();
                            Tools.resetConfiguration();
                            JOptionPane.showMessageDialog(Configuration.this,
                                    "Error occurs while downloading.\n" + exception.getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
                        }
                        return null;
                    }

                    @Override
                    protected void done() {
                        Toolkit.getDefaultToolkit().beep();
                        if (rootId == null) {
                            downloadBtn.setEnabled(true);
                            return;
                        }
                        dialog.setNextListener(new AbstractAction() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                dialog.dispose();
                                Configuration.this.dispose();
                                new Dashboard("Helios", 0);
                            }
                        });
                        dialog.finish();
                    }
                };
                swingWorker.execute();

                dialog.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this, "Cannot set up sync folder", "ERROR", JOptionPane.ERROR_MESSAGE);
                downloadBtn.setEnabled(true);
            }
        });
    }

    private void onPathChange() {
        if (!txtPath.getText().isEmpty()) {
            File folder = new File(txtPath.getText());
            if (folder.exists() && folder.isDirectory()) {
                uploadBtn.setEnabled(true);
                return;
            }
        }

        uploadBtn.setEnabled(false);
    }

    private void onSyncToChange() {
        if (!txtSyncTo.getText().isEmpty()) {
            File folder = new File(txtSyncTo.getText());
            if (folder.exists() && folder.isDirectory()) {
                downloadBtn.setEnabled(true);
                return;
            }
        }

        downloadBtn.setEnabled(false);
    }

    private void switchLayout() {
        CardLayout layout = (CardLayout) getContentPane().getLayout();
        layout.next(getContentPane());
    }
}
