package com.ecotioco.helios.view;

import com.ecotioco.helios.listener.OnProgressUpdate;
import com.ecotioco.helios.util.Constant;
import com.ecotioco.helios.util.DriveService;
import com.ecotioco.helios.util.Tools;
import com.google.api.client.http.FileContent;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import sun.reflect.generics.tree.Tree;

import javax.naming.directory.BasicAttribute;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

public class Dashboard extends JFrame {

    private JTable onlineFilesTbl;
    private DefaultTableModel onlineFilesTblModel, localFilesTblModel, localChangeTblModel, onlineChangeTblModel;
    private List<String> tableFiles;
    private JPanel rootPanel;
    private JLabel lblTitle;
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
    private JTable localFilesTbl;
    private JTextField txtName;
    private JLabel lblType;
    private JLabel lblSize;
    private JLabel lblCreatedTime;
    private JButton updateFileBtn;
    private JButton deleteBtn;
    private JLabel lblModifiedTime;
    private JPanel detailsControlPanel;
    private JLabel lblTotalOnlineFiles;
    private JButton refreshOnlineFilesBtn;
    private JButton refreshLocalFilesBtn;
    private JLabel lblTotalLocalFiles;
    private JTree folderTree;
    private JScrollPane folderTreePane;
    private JLabel lblStatus;
    private long detailsSize;
    private String parrentId;
    private String latestName;

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
        initTables();
        long lastSyncTime = Long.parseLong(Tools.getPreference(Constant.KEY_LAST_SYNC));
        lblLastSync.setText(Tools.getFormattedDate(lastSyncTime));
        refreshOnlineBtn.addActionListener(e -> {
            showOnlineChangedFiles();
        });
        refreshLocalButton.addActionListener(e -> {
            showLocalChangedFiles();
        });
        refreshOnlineFilesBtn.addActionListener(e -> {
            showOnlineFiles();
        });
        refreshLocalFilesBtn.addActionListener(e -> {
            showLocalFiles();
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
            changeTab(index);
        });

        syncButton.addActionListener(e -> {
            syncData();
        });

        updateFileBtn.addActionListener(e -> {
            updateFile();
        });

        deleteBtn.addActionListener(e -> {
            deleteFile();
        });
    }

    private void changeTab(int index) {
        resetDetails();
        switch (index) {
            case 0: {
                showOnlineChangedFiles();
                break;
            }
            case 1: {
                showLocalChangedFiles();
                break;
            }
            case 2: {
                showOnlineFiles();
                break;
            }
            case 3: {
                showLocalFiles();
                break;
            }
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
                                    "Error occurs while syncing\n" + exception.getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
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
        tableFiles = new ArrayList<>();
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
                        vector.add(file.getName());
                        vector.add(file.getMimeType().equalsIgnoreCase(Constant.MIME_TYPE_FOLDER) ? "FOLDER" : file.getFileExtension());
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
                        tableFiles.add(file.getId());
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
        tableFiles = new ArrayList<>();
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
                        vector.add(file.getName());
                        vector.add(file.isDirectory() ? "FOLDER" : com.google.common.io.Files.getFileExtension(file.getName()));
                        vector.add(isDeleted || file.isDirectory() ? "" : Tools.getFormattedSize(attr.size()));
                        vector.add(isDeleted ? "" : Tools.getFormattedDate(attr.creationTime().toMillis()));
                        vector.add(isDeleted ? "" : Tools.getFormattedDate(attr.lastModifiedTime().toMillis()));
                        vector.add(changes.get(path));
                        localChangeTblModel.addRow(vector);
                        tableFiles.add(file.getPath());
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

    private void showOnlineFiles() {
        tableFiles = new ArrayList<>();

        lblTotalOnlineFiles.setText("Total: 0");
        onlineFilesTblModel.setRowCount(0);

        LoadingDialog dialog = new LoadingDialog(this, "Loading", true);
        SwingWorker<Void, Void> swingWorker = new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() {
                try {
                    String id = Tools.getPreference(Constant.KEY_SYNC_FOLDER_ID);
                    List<File> files = DriveService.getAllFiles(id, "modifiedTime");
                    for (File file : files) {
                        Vector vector = new Vector();
                        vector.add(file.getName());
                        vector.add(file.getMimeType().equalsIgnoreCase(Constant.MIME_TYPE_FOLDER) ? "FOLDER" : file.getFileExtension());
                        if (file.getSize() != null) {
                            vector.add(Tools.getFormattedSize(file.getSize()));
                        } else {
                            vector.add("");
                        }
                        vector.add(Tools.getFormattedDate(file.getCreatedTime().getValue()));
                        vector.add(Tools.getFormattedDate(file.getModifiedTime().getValue()));
                        vector.add(file.getLastModifyingUser().getEmailAddress());
                        onlineFilesTblModel.addRow(vector);
                        tableFiles.add(file.getId());
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
                lblTotalOnlineFiles.setText("Total: " + onlineFilesTbl.getRowCount());
                dialog.dispose();
            }
        };
        swingWorker.execute();

        dialog.setVisible(true);
    }

    private void showLocalFiles() {
        tableFiles = new ArrayList<>();

        lblTotalLocalFiles.setText("Total: 0");
        localFilesTblModel.setRowCount(0);

        LoadingDialog dialog = new LoadingDialog(this, "Loading", true);
        SwingWorker<Void, Void> swingWorker = new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() {
                try {
                    String rootPath = Tools.getPreference(Constant.KEY_SYNC_FOLDER_PATH);
                    TreeSet<String> paths = DriveService.getLocalFileTree(rootPath);
                    for (String path : paths) {
                        java.io.File file = new java.io.File(Paths.get(rootPath, path).toString());
                        BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);

                        Vector vector = new Vector();
                        vector.add(file.getName());
                        vector.add(file.isDirectory() ? "FOLDER" : com.google.common.io.Files.getFileExtension(file.getName()));

                        vector.add(file.isDirectory() ? "" : Tools.getFormattedSize(attr.size()));
                        vector.add(Tools.getFormattedDate(attr.creationTime().toMillis()));
                        vector.add(Tools.getFormattedDate(attr.lastModifiedTime().toMillis()));
                        localFilesTblModel.addRow(vector);
                        tableFiles.add(file.getPath());
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
                lblTotalLocalFiles.setText("Total: " + localFilesTblModel.getRowCount());
                dialog.dispose();
            }
        };
        swingWorker.execute();

        dialog.setVisible(true);
    }

    private void initTables() {
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        onlineFilesTblModel = new DefaultTableModel(
                new Object[][]{},
                new java.lang.String[]{
                        "File Name", "Type", "Size", "Created Time", "Modified Time", "Last Modified User"
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
        onlineFilesTbl.setModel(onlineFilesTblModel);
        onlineFilesTbl.setFillsViewportHeight(true);
        onlineFilesTbl.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineFilesTbl.getSelectionModel().addListSelectionListener(e -> {
            if (onlineFilesTbl.getSelectedRow() >= 0) loadDetails(onlineFilesTbl.getSelectedRow());
        });

        localFilesTblModel = new DefaultTableModel(
                new Object[][]{},
                new java.lang.String[]{
                        "File Name", "Type", "Size", "Created Time", "Modified Time"
                }
        ) {
            final Class<?>[] types = new Class[]{
                    String.class, String.class, String.class, DateTime.class, DateTime.class
            };
            final boolean[] canEdit = new boolean[]{
                    false, false, false, false, false
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
        localFilesTbl.setModel(localFilesTblModel);
        localFilesTbl.setFillsViewportHeight(true);
        localFilesTbl.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        localFilesTbl.getSelectionModel().addListSelectionListener(e -> {
            if (localFilesTbl.getSelectedRow() >= 0) loadDetails(localFilesTbl.getSelectedRow());
        });

        localChangeTblModel = new DefaultTableModel(
                new Object[][]{},
                new java.lang.String[]{
                        "File Name", "Type", "Size", "Created Time", "Modified Time", "Action"
                }
        ) {
            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }
        };
        localTbl.setModel(localChangeTblModel);
        localTbl.setFillsViewportHeight(true);
        localTbl.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        localTbl.getSelectionModel().addListSelectionListener(e -> {
            if (localTbl.getSelectedRow() >= 0) loadDetails(localTbl.getSelectedRow());
        });
        localTbl.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        localTbl.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        localTbl.getColumnModel().getColumn(5).setCellRenderer(centerRenderer);

        onlineChangeTblModel = new DefaultTableModel(
                new Object[][]{},
                new java.lang.String[]{
                        "File Name", "Type", "Size", "Created Time", "Modified Time", "Last Modifying User", "Action"
                }
        ) {
            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }
        };
        onlineTbl.setModel(onlineChangeTblModel);
        onlineTbl.setFillsViewportHeight(true);
        onlineTbl.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineTbl.getSelectionModel().addListSelectionListener(e -> {
            if (onlineTbl.getSelectedRow() >= 0) loadDetails(onlineTbl.getSelectedRow());
        });
        onlineTbl.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        onlineTbl.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        onlineTbl.getColumnModel().getColumn(6).setCellRenderer(centerRenderer);
    }

    private void resetDetails() {
        txtName.setText("");
        txtName.setEnabled(false);
        lblType.setText("");
        lblSize.setText("");
        lblCreatedTime.setText("");
        lblModifiedTime.setText("");
        folderTreePane.setVisible(false);
        detailsControlPanel.setVisible(false);
        lblStatus.setText("");
    }

    private void setDetails(String name, String type, Long size, Long createdTime, Long modifiedTime, boolean isFolder, boolean isDeleted) {
        latestName = name;
        txtName.setText(name);
        if (isDeleted) {
            txtName.setEnabled(false);
            lblStatus.setText("Deleted");
            lblStatus.setForeground(Color.red);
            detailsControlPanel.setVisible(false);
        } else {
            txtName.setEnabled(true);
            lblStatus.setText("Available");
            lblStatus.setForeground(Color.green);
            detailsControlPanel.setVisible(true);
        }
        lblType.setText(isFolder ? "FOLDER" : type);
        lblSize.setText(size == null ? "" : Tools.getFormattedSize(size));
        lblCreatedTime.setText(createdTime == null ? "" : Tools.getFormattedDate(createdTime));
        lblModifiedTime.setText(modifiedTime == null ? "" : Tools.getFormattedDate(modifiedTime));
        if (isFolder && !isDeleted) {
            folderTreePane.setVisible(true);
        } else {
            folderTreePane.setVisible(false);
        }
    }

    private DefaultMutableTreeNode getOnlineFolderTreeNode(String folderId, String folderName) throws IOException {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(folderName);
        String pageToken = null;
        do {
            FileList fileList = DriveService.getInstance()
                    .files()
                    .list()
                    .setQ(String.format("'%s' in parents and trashed = false", folderId))
                    .setPageSize(1000)
                    .setPageToken(pageToken)
                    .setFields("nextPageToken, files(id, name, size, mimeType)")
                    .execute();

            pageToken = fileList.getNextPageToken();
            for (File file : fileList.getFiles()) {
                if (file.getMimeType().equalsIgnoreCase(Constant.MIME_TYPE_FOLDER)) {
                    node.add(getOnlineFolderTreeNode(file.getId(), file.getName()));
                } else {
                    detailsSize += file.getSize();
                    node.add(new DefaultMutableTreeNode(file.getName(), false));
                }
            }
        } while (pageToken != null);

        return node;
    }

    private DefaultMutableTreeNode getLocalFolderTreeNode(String path) throws IOException {
        java.io.File root = new java.io.File(path);
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(root.getName());

        try (Stream<Path> paths = Files.walk(Paths.get(path), 1)) {
            paths
                    .skip(1)
                    .forEach(p -> {
                        try {
                            java.io.File file = new java.io.File(p.toString());
                            if (file.isDirectory()) {
                                node.add(getLocalFolderTreeNode(file.getPath()));
                            } else {
                                BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                                detailsSize += attr.size();
                                node.add(new DefaultMutableTreeNode(file.getName(), false));
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }

        return node;
    }

    private void loadDetails(int row) {
        LoadingDialog dialog = new LoadingDialog(this, "Loading", true);
        SwingWorker<Void, Void> swingWorker = new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() {
                try {
                    detailsSize = 0;
                    switch (tabPane.getSelectedIndex()) {
                        case 0:
                        case 2: {
                            String id = tableFiles.get(row);
                            File file = DriveService.getInstance()
                                    .files()
                                    .get(id)
                                    .setFields("id, name, fileExtension, size, createdTime, modifiedTime, mimeType, trashed, parents")
                                    .execute();

                            parrentId = file.getParents().get(0);
                            boolean isFolder = file.getMimeType().equalsIgnoreCase(Constant.MIME_TYPE_FOLDER);

                            if (isFolder && !file.getTrashed()) {
                                folderTree.setModel(new DefaultTreeModel(getOnlineFolderTreeNode(file.getId(), file.getName()), true));
                            }
                            setDetails(file.getName(), file.getFileExtension(), isFolder ? detailsSize : file.getSize(), file.getCreatedTime().getValue(), file.getModifiedTime().getValue(),
                                    isFolder, file.getTrashed());
                            break;
                        }
                        case 1:
                        case 3: {
                            java.io.File file = new java.io.File(tableFiles.get(row));
                            BasicFileAttributes attr = null;
                            if (file.exists()) {
                                attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);

                                if (file.isDirectory()) {
                                    folderTree.setModel(new DefaultTreeModel(getLocalFolderTreeNode(file.getPath()), true));
                                }
                            }

                            setDetails(file.getName(),
                                    file.exists()
                                            ? file.isDirectory()
                                            ? "FOLDER"
                                            : com.google.common.io.Files.getFileExtension(file.getPath())
                                            : "",
                                    !file.exists() ? null : file.isDirectory() ? detailsSize : attr.size(),
                                    attr == null ? null : attr.creationTime().toMillis(),
                                    attr == null ? null : attr.lastModifiedTime().toMillis(),
                                    file.isDirectory(), !file.exists());
                            break;
                        }
                    }
                } catch (Exception e) {
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

    private void updateFile() {
        String name = txtName.getText();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(Dashboard.this, "Name can't be blank!");
            return;
        } else if (latestName.equalsIgnoreCase(name)) {
            return;
        }
        int option = JOptionPane.showConfirmDialog(this, "Are you sure you want to update this?", "CONFIRMATION", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (option == JOptionPane.NO_OPTION) {
            return;
        }
        LoadingDialog dialog = new LoadingDialog(this, "Updating", true);
        SwingWorker<Void, Void> swingWorker = new SwingWorker<Void, Void>() {
            int result = -1;
            int row = 0;

            @Override
            protected Void doInBackground() {
                try {
                    switch (tabPane.getSelectedIndex()) {
                        case 0: {
                            row = onlineTbl.getSelectionModel().getLeadSelectionIndex();
                            String id = tableFiles.get(row);

                            File file = DriveService.checkExistName(parrentId, name);

                            if (file != null && !file.getId().equalsIgnoreCase(id)) {
                                int option = JOptionPane.showConfirmDialog(Dashboard.this, "Name already exist, replace anyway?",
                                        "CONFIRMATION", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
                                if (option == JOptionPane.NO_OPTION) {
                                    result = 0;
                                    return null;
                                } else {
                                    File deleteFile = new File();
                                    deleteFile.setTrashed(true);

                                    DriveService.getInstance()
                                            .files()
                                            .update(file.getId(), deleteFile)
                                            .execute();
                                }
                            }

                            File newFile = new File();
                            newFile.setName(name);

                            DriveService.getInstance()
                                    .files()
                                    .update(id, newFile)
                                    .execute();
                            break;
                        }
                        case 1: {
                            row = localTbl.getSelectionModel().getLeadSelectionIndex();
                            java.io.File file = new java.io.File(tableFiles.get(row));
                            java.io.File newFile = new java.io.File(file.toPath().resolveSibling(name).toString());
                            if (newFile.exists()) {
                                int option = JOptionPane.showConfirmDialog(Dashboard.this, "Name already exist, replace anyway?",
                                        "CONFIRMATION", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
                                if (option == JOptionPane.NO_OPTION) {
                                    result = 0;
                                    return null;
                                } else {
                                    if (newFile.isDirectory()) {
                                        Tools.deleteFolder(newFile.getPath());
                                    } else {
                                        newFile.delete();
                                    }
                                }
                            }

                            Files.move(file.toPath(), newFile.toPath());
                            break;
                        }
                        case 2: {
                            row = onlineFilesTbl.getSelectionModel().getLeadSelectionIndex();
                            String id = tableFiles.get(row);

                            File file = DriveService.checkExistName(parrentId, name);

                            if (file != null && !file.getId().equalsIgnoreCase(id)) {
                                int option = JOptionPane.showConfirmDialog(Dashboard.this, "Name already exist, replace anyway?",
                                        "CONFIRMATION", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
                                if (option == JOptionPane.NO_OPTION) {
                                    result = 0;
                                    return null;
                                } else {
                                    File deleteFile = new File();
                                    deleteFile.setTrashed(true);

                                    DriveService.getInstance()
                                            .files()
                                            .update(file.getId(), deleteFile)
                                            .execute();
                                }
                            }

                            File newFile = new File();
                            newFile.setName(name);

                            DriveService.getInstance()
                                    .files()
                                    .update(id, newFile)
                                    .execute();
                            break;
                        }
                        case 3: {
                            row = localFilesTbl.getSelectionModel().getLeadSelectionIndex();
                            java.io.File file = new java.io.File(tableFiles.get(row));
                            java.io.File newFile = new java.io.File(file.toPath().resolveSibling(name).toString());
                            if (newFile.exists()) {
                                int option = JOptionPane.showConfirmDialog(Dashboard.this, "Name already exist, replace anyway?",
                                        "CONFIRMATION", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
                                if (option == JOptionPane.NO_OPTION) {
                                    result = 0;
                                    return null;
                                } else {
                                    if (newFile.isDirectory()) {
                                        Tools.deleteFolder(newFile.getPath());
                                    } else {
                                        newFile.delete();
                                    }
                                }
                            }

                            Files.move(file.toPath(), newFile.toPath());
                            break;
                        }
                    }
                    result = 1;
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(Dashboard.this,
                            "Error occurs\n" + e.getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
                }
                return null;
            }

            @Override
            protected void done() {
                dialog.dispose();
                if (result == 1) {
                    JOptionPane.showMessageDialog(Dashboard.this, "Update successfully");
                    changeTab(tabPane.getSelectedIndex());
                }
            }
        };
        swingWorker.execute();

        dialog.setVisible(true);
    }

    private void deleteFile() {
        int option = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this?", "CONFIRMATION", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (option == JOptionPane.NO_OPTION) {
            return;
        }
        LoadingDialog dialog = new LoadingDialog(this, "Deleting", true);
        SwingWorker<Void, Void> swingWorker = new SwingWorker<Void, Void>() {
            int result = -1;
            int row = 0;

            @Override
            protected Void doInBackground() {
                try {
                    switch (tabPane.getSelectedIndex()) {
                        case 0: {
                            row = onlineTbl.getSelectionModel().getLeadSelectionIndex();
                            String id = tableFiles.get(row);

                            File deleteFile = new File();
                            deleteFile.setTrashed(true);

                            DriveService.getInstance()
                                    .files()
                                    .update(id, deleteFile)
                                    .execute();
                            break;
                        }
                        case 1: {
                            row = localTbl.getSelectionModel().getLeadSelectionIndex();
                            java.io.File file = new java.io.File(tableFiles.get(row));
                            if (file.exists()) {
                                Tools.deleteFolder(file.getPath());
                            }
                            break;
                        }
                        case 2: {
                            row = onlineFilesTbl.getSelectionModel().getLeadSelectionIndex();
                            String id = tableFiles.get(row);

                            File deleteFile = new File();
                            deleteFile.setTrashed(true);

                            DriveService.getInstance()
                                    .files()
                                    .update(id, deleteFile)
                                    .execute();
                            break;
                        }
                        case 3: {
                            row = localFilesTbl.getSelectionModel().getLeadSelectionIndex();
                            java.io.File file = new java.io.File(tableFiles.get(row));
                            if (file.exists()) {
                                Tools.deleteFolder(file.getPath());
                            }
                            break;
                        }
                    }
                    result = 1;
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(Dashboard.this,
                            "Error occurs\n" + e.getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
                }
                return null;
            }

            @Override
            protected void done() {
                dialog.dispose();
                if (result == 1) {
                    JOptionPane.showMessageDialog(Dashboard.this, "Delete successfully");
                    changeTab(tabPane.getSelectedIndex());
                }
            }
        };
        swingWorker.execute();

        dialog.setVisible(true);
    }
}
