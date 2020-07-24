package com.ecotioco.helios.view;

import com.ecotioco.helios.listener.OnProgressUpdate;
import com.ecotioco.helios.util.Constant;
import com.ecotioco.helios.util.DriveService;
import com.ecotioco.helios.util.Tools;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.List;

public class Dashboard extends JFrame {

    private JTable uploadedTbl;
    private DefaultTableModel uploadedTblModel, localChangeTblModel, onlineChangeTblModel;
    private JPanel rootPanel;
    private JLabel lblTitle;
    private JButton prevBtn;
    private JButton nextBtn;
    private JLabel txtPage;
    private JButton syncButton;
    private JTable localTbl;
    private JTabbedPane tabPane;
    private JLabel lblLastSync;
    private JButton signOutBtn;
    private JButton changeConfigBtn;
    private JButton refreshLocalButton;
    private JLabel lblTotalLocal;
    private JTable onlineTbl;
    private JLabel lblTotalOnline;
    private JButton refreshOnlineBtn;
    private int currentPage = 0;
    private FileList fileList;
    private List<String> pageTokens = new ArrayList<>();

    public Dashboard(String title, int tab) throws HeadlessException {
        super(title);

        setContentPane(rootPanel);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
        setSize(1200, 600);
        setLocationRelativeTo(null);
        initComponents();
        changeTab(tab);
    }

    private void initComponents() {
        uploadedTblModel = new DefaultTableModel(
                new Object[][]{},
                new java.lang.String[]{
                        "File Name", "Extension", "Size", "Created Time", "Modified Time", "Last Modified User"
                }
        ) {
            final Class<?>[] types = new Class[]{
                    String.class, String.class, String.class, DateTime.class, DateTime.class, String.class
            };
            final boolean[] canEdit = new boolean[]{
                    false, false, false, false, false, false
            };

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return types[columnIndex];
            }

            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit[columnIndex];
            }
        };
        uploadedTbl.setModel(uploadedTblModel);
        uploadedTbl.setFillsViewportHeight(true);

        localChangeTblModel = new DefaultTableModel(
                new Object[][]{},
                new java.lang.String[]{
                        "File Name", "Extension", "Size", "Created Time", "Modified Time", "Action"
                }
        ) {
            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }
        };
        localTbl.setModel(localChangeTblModel);
        localTbl.setFillsViewportHeight(true);
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        localTbl.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        localTbl.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        localTbl.getColumnModel().getColumn(5).setCellRenderer(centerRenderer);

        onlineChangeTblModel = new DefaultTableModel(
                new Object[][]{},
                new java.lang.String[]{
                        "File Name", "Extension", "Size", "Created Time", "Modified Time", "Last Modifying User", "Action"
                }
        ) {
            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }
        };
        onlineTbl.setModel(onlineChangeTblModel);
        onlineTbl.setFillsViewportHeight(true);
        onlineTbl.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        onlineTbl.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        onlineTbl.getColumnModel().getColumn(6).setCellRenderer(centerRenderer);

        long lastSyncTime = Long.parseLong(Tools.getPreference(Constant.KEY_LAST_SYNC));
        lblLastSync.setText(Tools.getFormattedDate(lastSyncTime));
        pageTokens.add(null);
        prevBtn.addActionListener(e -> {
            showUploadedFiles(true);
        });
        nextBtn.addActionListener(e -> {
            showUploadedFiles(false);
        });

        refreshOnlineBtn.addActionListener(e -> {
            showOnlineChangedFiles();
        });
        refreshLocalButton.addActionListener(e -> {
            showLocalChangedFiles();
        });

        changeConfigBtn.setFocusPainted(false);
        changeConfigBtn.addActionListener(e -> {
            Tools.resetConfiguration();
            this.dispose();
            new Configuration("Set up sync folder");
        });

        signOutBtn.addActionListener(e -> {
            DriveService.logOut();
            Tools.resetConfiguration();
            this.dispose();
            new Login("Sign in to Google Drive");
        });

        tabPane.addChangeListener(e -> {
            int index = tabPane.getSelectedIndex();
            pageTokens = new ArrayList<>();
            pageTokens.add(null);
            currentPage = 0;
            changeTab(index);
        });

        syncButton.addActionListener(e -> {
            syncData();
        });
    }

    private void changeTab(int index) {
        if (index == 0) {
            showOnlineChangedFiles();
        } else if (index == 1) {
            showLocalChangedFiles();
        } else if (index == 2) {
            showUploadedFiles(false);
        }
    }

    private void syncData() {
        String id = Tools.getPreference(Constant.KEY_SYNC_FOLDER_ID);
        long lastSyncTime = Long.parseLong(Tools.getPreference(Constant.KEY_LAST_SYNC));
        try {
            if (onlineChangeTblModel.getRowCount() > 0 || localChangeTblModel.getRowCount() > 0
                || !DriveService.isSyncedFromCloud() || !DriveService.isSyncedToCloud()) {
                ProgressBarDialog dialog = new ProgressBarDialog(this, "Syncing", "OK");
                dialog.setProgress("Start synchronizing from cloud...", 0);
                SwingWorker<Void, Void> swingWorker = new SwingWorker<Void, Void>() {
                    private boolean isSuccess = false;

                    @Override
                    protected Void doInBackground() {
                        OnProgressUpdate listener = (current, total, message) -> {
                            double progress = current / (double) total * 100;
                            dialog.setProgress(message + " - " + current + "/" + total, progress);
                        };
                        try {
                            Map<String, File> onlineFiles = DriveService.getOnlineModifiedFiles("", id, lastSyncTime, DriveService.getOnlineModifiedFiles(id, "DELETE").keySet());
                            if (!onlineFiles.isEmpty()) {
                                DriveService.downloadFiles(onlineFiles, listener);
                            }

                            dialog.setProgress("Finish synchronizing from cloud", 100);
                            dialog.setProgress("Start synchronizing to cloud...", 0);

                            isSuccess = DriveService.syncToCloud(listener);
                            Tools.setPreference(Constant.KEY_LAST_SYNC, System.currentTimeMillis() + "");
                        } catch (Exception exception) {
                            dialog.dispose();
                            JOptionPane.showMessageDialog(Dashboard.this,
                                    "Error occurs while syncing", "ERROR", JOptionPane.ERROR_MESSAGE);
                        }
                        return null;
                    }

                    @Override
                    protected void done() {
                        Toolkit.getDefaultToolkit().beep();
                        if (!isSuccess) {
                            syncButton.setEnabled(true);
                            return;
                        }
                        dialog.setNextListener(new AbstractAction() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                dialog.dispose();
                                if (tabPane.getSelectedIndex() == 0) {
                                    onlineChangeTblModel.setRowCount(0);
                                    lblTotalOnline.setText("Total: 0");
                                } else {
                                    localChangeTblModel.setRowCount(0);
                                    lblTotalLocal.setText("Total: 0");
                                }

                                long lastSyncTime = Long.parseLong(Tools.getPreference(Constant.KEY_LAST_SYNC));
                                lblLastSync.setText(Tools.getFormattedDate(lastSyncTime));
                            }
                        });
                        dialog.finish();
                    }
                };
                swingWorker.execute();

                dialog.setVisible(true);

            } else {
                JOptionPane.showMessageDialog(Dashboard.this,
                        "Already synced!", "INFO", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(Dashboard.this,
                    "Error occurs\n" + e.getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showOnlineChangedFiles() {
        lblTotalOnline.setText("Total: 0");
        onlineChangeTblModel.setRowCount(0);

        LoadingDialog dialog = new LoadingDialog(this, "Loading", true);
        SwingWorker<Void, Void> swingWorker = new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() {
                try {
                    String id = Tools.getPreference(Constant.KEY_SYNC_FOLDER_ID);
                    long lastSyncTime = Long.parseLong(Tools.getPreference(Constant.KEY_LAST_SYNC));
                    Map<String, File> changes = DriveService.getOnlineModifiedFiles("", id, lastSyncTime, DriveService.getOnlineModifiedFiles(id, "DELETE").keySet());
                    for (File file : changes.values()) {
                        Vector vector = new Vector();
                        vector.add(file.getName() + (file.getMimeType().equalsIgnoreCase(Constant.MIME_TYPE_FOLDER) ? " (Folder)" : ""));
                        vector.add(file.getFileExtension());
                        vector.add(file.getSize() == null ? "" : Tools.getFormattedSize(file.getSize()));
                        vector.add(Tools.getFormattedDate(file.getCreatedTime().getValue()));
                        vector.add(file.getModifiedTime().getValue());
                        vector.add(file.getLastModifyingUser().getEmailAddress());
                        vector.add(file.getTrashed()
                                ? Constant.DELETE
                                : file.getCreatedTime().getValue() > lastSyncTime
                                    ? Constant.CREATE
                                    : Constant.MODIFY);
                        onlineChangeTblModel.addRow(vector);
                    }
                } catch (Exception e) {
                    dialog.dispose();
                    JOptionPane.showMessageDialog(Dashboard.this,
                            "Error occurs\n" + e.getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
                }
                return null;
            }

            @Override
            protected void done() {
                lblTotalOnline.setText("Total: " + onlineChangeTblModel.getRowCount());
                dialog.dispose();
            }
        };
        swingWorker.execute();

        dialog.setVisible(true);
    }

    private void showLocalChangedFiles() {
        lblTotalLocal.setText("Total: 0");
        localChangeTblModel.setRowCount(0);

        LoadingDialog dialog = new LoadingDialog(this, "Loading", true);
        SwingWorker<Void, Void> swingWorker = new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() {
                try {
                    Map<String, String> changes = DriveService.getLocalModifiedFiles();
                    String rootPath = Tools.getPreference(Constant.KEY_SYNC_FOLDER_PATH);
                    for (String path : changes.keySet()) {
                        boolean isDeleted = changes.get(path) == Constant.DELETE;
                        java.io.File file = new java.io.File(Paths.get(rootPath, path).toString());
                        BasicFileAttributes attr = null;
                        if (!isDeleted) {
                            attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                        }
                        Vector vector = new Vector();
                        if (file.isDirectory()) {
                            vector.add(file.getName() + " (Folder)");
                            vector.add("");
                        } else {
                            vector.add(file.getName());
                            vector.add(com.google.common.io.Files.getFileExtension(file.getName()));
                        }

                        vector.add(isDeleted ? "" : Tools.getFormattedSize(attr.size()));
                        vector.add(isDeleted ? "" : Tools.getFormattedDate(attr.creationTime().toMillis()));
                        vector.add(isDeleted ? "" : Tools.getFormattedDate(attr.lastModifiedTime().toMillis()));
                        vector.add(changes.get(path));
                        localChangeTblModel.addRow(vector);
                    }
                } catch (Exception e) {
                    dialog.dispose();
                    JOptionPane.showMessageDialog(Dashboard.this,
                            "Error occurs\n" + e.getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
                }
                return null;
            }

            @Override
            protected void done() {
                lblTotalLocal.setText("Total: " + localChangeTblModel.getRowCount());
                dialog.dispose();
            }
        };
        swingWorker.execute();

        dialog.setVisible(true);
    }

    private void showUploadedFiles(boolean isBack) {
        if (isBack) {
            currentPage--;
        } else {
            currentPage++;
        }

        txtPage.setText("Page " + currentPage);
        uploadedTblModel.setRowCount(0);

        LoadingDialog dialog = new LoadingDialog(this, "Loading", true);
        SwingWorker<Void, Void> swingWorker = new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() {
                try {
                    fileList = DriveService.getAllFiles(pageTokens.get(currentPage - 1), "modifiedTime");

                    if (currentPage == pageTokens.size()) {
                        if (fileList != null && fileList.getNextPageToken() != null) {
                            pageTokens.add(fileList.getNextPageToken());
                            nextBtn.setEnabled(true);
                        } else {
                            nextBtn.setEnabled(false);
                        }
                    } else {
                        nextBtn.setEnabled(true);
                    }

                    if (currentPage == 1) {
                        prevBtn.setEnabled(false);
                    } else {
                        prevBtn.setEnabled(true);
                    }

                    List<File> files = fileList.getFiles();
                    for (File file : files) {
                        Vector vector = new Vector();
                        vector.add(file.getName());
                        if (file.getFileExtension() != null) {
                            vector.add(file.getFileExtension());
                        } else {
                            vector.add(null);
                        }
                        if (file.getSize() != null) {
                            vector.add(Tools.getFormattedSize(file.getSize()));
                        } else {
                            vector.add(Tools.getFormattedSize(0));
                        }
                        vector.add(Tools.getFormattedDate(file.getCreatedTime().getValue()));
                        vector.add(Tools.getFormattedDate(file.getModifiedTime().getValue()));
                        vector.add(file.getLastModifyingUser().getEmailAddress());
                        uploadedTblModel.addRow(vector);
                    }
                } catch (Exception e) {
                    dialog.dispose();
                    JOptionPane.showMessageDialog(Dashboard.this,
                            "Error occurs\n" + e.getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
                }
                return null;
            }

            @Override
            protected void done() {
                dialog.dispose();
            }
        };
        swingWorker.execute();

        dialog.setVisible(true);
    }
}
