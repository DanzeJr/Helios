package com.ecotioco.helios.util;

import com.ecotioco.helios.listener.OnProgressUpdate;
import com.ecotioco.helios.view.Configuration;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.driveactivity.v2.DriveActivityScopes;
import com.google.api.services.driveactivity.v2.model.ActionDetail;
import com.google.api.services.driveactivity.v2.model.DriveActivity;
import com.google.api.services.driveactivity.v2.model.QueryDriveActivityRequest;
import com.google.api.services.driveactivity.v2.model.QueryDriveActivityResponse;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import org.checkerframework.checker.nullness.qual.NonNull;

import javax.swing.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class DriveService {
    private static Drive instance;
    private static com.google.api.services.driveactivity.v2.DriveActivity driveActivity;

    private static final String APPLICATION_NAME = "Helios";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String STORED_CREDENTIAL = "StoredCredential";

    /**
     * Global instance of the scopes required.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE, DriveActivityScopes.DRIVE_ACTIVITY);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    /**
     * Creates an authorized Credential object.
     *
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT, boolean forceLogin) throws IOException {
        // Load client secrets.
        InputStream in = DriveService.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        Credential credential = flow.loadCredential("user");
        if (credential == null && forceLogin) {
            LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8142).build();
            return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        }
        return credential;
    }

    public static boolean isLoggedIn() {
        try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            if (getCredentials(HTTP_TRANSPORT, false) != null) {
                return true;
            }
        } catch (GeneralSecurityException | IOException e) {
            JOptionPane.showMessageDialog(null,
                    "Error!\n" + e.getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }

    public static boolean logOut() {
        instance = null;
        java.io.File storedCredential = new java.io.File(Paths.get(TOKENS_DIRECTORY_PATH, STORED_CREDENTIAL).toString());
        return storedCredential.delete();
    }

    public static Drive getInstance() {
        if (instance == null) {
            try {
                final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
                instance = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT, true))
                        .setApplicationName(APPLICATION_NAME)
                        .build();
            } catch (GeneralSecurityException | IOException e) {
                JOptionPane.showMessageDialog(null,
                        "Error!\n" + e.getMessage(), "ERROR", JOptionPane.ERROR_MESSAGE);
            }
        }

        return instance;
    }

    /**
     * Build and return an authorized Drive Activity client service.
     *
     * @return an authorized DriveActivity client service
     * @throws IOException
     */
    public static com.google.api.services.driveactivity.v2.DriveActivity getDriveActivityService() {
        if (driveActivity == null) {
            try {
                final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

                return new com.google.api.services.driveactivity.v2.DriveActivity.Builder(
                        HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT, true))
                        .setApplicationName(APPLICATION_NAME)
                        .build();
            } catch (GeneralSecurityException | IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public static List<File> getAllFolders(String rootId, String orderBy) throws IOException {
        List<File> files = new ArrayList<>();

        String pageToken = null;
        do {
            FileList fileList = getInstance()
                    .files()
                    .list()
                    .setQ(String.format("'%s' in parents and trashed = false and mimeType = '%s'", rootId, Constant.MIME_TYPE_FOLDER))
                    .setPageSize(1000)
                    .setPageToken(pageToken)
                    .setFields("nextPageToken, files(id, name, createdTime)")
                    .setOrderBy(orderBy)
                    .execute();
            pageToken = fileList.getNextPageToken();

            if (!fileList.getFiles().isEmpty()) {
                files.addAll(fileList.getFiles());
            }
        } while (pageToken != null);

        return files;
    }

    public static FileList getAllFiles(String nextPageToken, String orderBy) throws IOException {
        String rootId = Tools.getPreference(Constant.KEY_SYNC_FOLDER_ID);
        return getInstance()
                .files()
                .list()
                .setQ(String.format("'%s' in parents and trashed = false", rootId))
                .setPageSize(20)
                .setPageToken(nextPageToken)
                .setFields("nextPageToken, files(*)")
                .setOrderBy(orderBy)
                .execute();
    }

    public static Map<String, List<String>> getExportFormats() throws IOException {
        About about = getInstance().about().get().setFields("exportFormats").execute();
        return about.getExportFormats();
    }

    public static File checkExistName(String fileName) throws IOException {
        FileList fileList;
        String nextPageToken = null;
        do {
            fileList = getInstance()
                    .files()
                    .list()
                    .setQ(String.format("name = '%s' and trashed = false and mimeType = '%s'", fileName, Constant.MIME_TYPE_FOLDER))
                    .setFields("nextPageToken, files(id, name, modifiedTime)")
                    .setPageSize(1000)
                    .setPageToken(nextPageToken)
                    .execute();
            nextPageToken = fileList.getNextPageToken();

            List<File> files = fileList.getFiles();
            if (files != null && !files.isEmpty()) {
                return fileList.getFiles().get(0);
            }
        } while (nextPageToken != null);

        return null;
    }

    public static File checkExist(String id) throws IOException {
        File file = getInstance()
                .files()
                .get(id)
                .execute();
        return file;
    }

    public static boolean isSyncedFromCloud() throws IOException {
        String rootId = Tools.getPreference(Constant.KEY_SYNC_FOLDER_ID);
        long lastSyncTime = Long.parseLong(Tools.getPreference(Constant.KEY_LAST_SYNC));
        QueryDriveActivityResponse response = DriveService.getDriveActivityService()
                .activity().query(new QueryDriveActivityRequest()
                        .setFilter("time > " + lastSyncTime)
                        .setAncestorName(String.format("items/%s", rootId))
                        .setPageSize(1))
                .execute();

        if (response.getActivities() == null || response.getActivities().isEmpty()) {
            return false;
        }

        return true;
    }

    public static boolean isSyncedToCloud() throws IOException {
        String id = Tools.getPreference(Constant.KEY_SYNC_FOLDER_ID);
        String syncPath = Tools.getPreference(Constant.KEY_SYNC_FOLDER_PATH);
        long lastSyncTime = Long.parseLong(Tools.getPreference(Constant.KEY_LAST_SYNC));
        TreeSet<String> localFiles = new TreeSet<>();
        // Get all file created before last sync
        HashMap<String, File> onlineFiles = getOnlineFileMap("", id, lastSyncTime,
                getOnlineModifiedFiles(id, "DELETE").keySet());

        boolean isSynced;
        try (Stream<Path> paths = Files.walk(Paths.get(syncPath))) {
            isSynced = paths
                    .skip(1)
                    .anyMatch(p -> {
                        try {
                            String filePath = p.toString();
                            filePath = filePath
                                    .substring(filePath.indexOf(syncPath) + syncPath.length() + 1);
                            localFiles.add(filePath);

                            if (!onlineFiles.containsKey(filePath)) {
                                return true;
                            }

                            BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
                            if (attr.lastModifiedTime().toMillis() > lastSyncTime) {
                                return true;
                            }
                            return false;
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
        if (!isSynced) {
            return false;
        }

        // if any file is on online but local then it was deleted
        Set<String> deletes = Sets.difference(onlineFiles.keySet(), localFiles);

        if (!deletes.isEmpty()) {
            isSynced = deletes.stream().allMatch(f -> onlineFiles.get(f).getTrashed());
        }

        return isSynced;
    }

    public static HashMap<String, String> getOnlineModifiedFiles(String folderId, String actionFilter) throws IOException {
        HashMap<String, String> map = new HashMap<>();
        long lastSyncTime = Long.parseLong(Tools.getPreference(Constant.KEY_LAST_SYNC));

        String pageToken = null;
        String filter = String.format("time > %d", lastSyncTime);
        if (actionFilter != null) {
            filter += String.format(" AND detail.action_detail_case: %s", actionFilter);
        }

        do {
            QueryDriveActivityResponse response = DriveService.getDriveActivityService()
                    .activity().query(new QueryDriveActivityRequest()
                            .setFilter(filter)
                            .setAncestorName(String.format("items/%s", folderId))
                            .setPageSize(100)
                            .setPageToken(pageToken))
                    .execute();

            pageToken = response.getNextPageToken();
            List<DriveActivity> activities = response.getActivities();
            if (activities != null) {
                for (DriveActivity activity : activities) {
                    String fileId = activity.getTargets().get(0).getDriveItem().getName();
                    fileId = fileId.substring(6);
                    ActionDetail actionDetail = activity.getPrimaryActionDetail();
                    String action = Constant.MODIFY;
                    if (actionDetail.getCreate() != null) {
                        action = Constant.CREATE;
                    } else if (actionDetail.getDelete() != null) {
                        action = Constant.DELETE;
                    }
                    map.put(fileId, action);
                }
            }
        } while (pageToken != null);

        return map;
    }

    public static HashMap<String, File> getOnlineModifiedFiles(String path, String folderId, long lastSyncTime, Set<String> deletedFiles) throws IOException {
        HashMap<String, File> map = new HashMap<>();

        if (!path.isEmpty()) {
            path += java.io.File.separator;
        }

        String nextPageToken = null;
        String query = String.format("'%s' in parents" +
                        " and (modifiedTime > '%s' or createdTime > '%s' or trashed = true or mimeType = '%s')", folderId,
                new DateTime(lastSyncTime).toStringRfc3339(), new DateTime(lastSyncTime).toStringRfc3339(), Constant.MIME_TYPE_FOLDER);
        do {
            FileList fileList = getInstance()
                    .files()
                    .list()
                    .setQ(query)
                    .setFields("nextPageToken, files(id, name, size, fileExtension, mimeType, trashed, createdTime, modifiedTime, lastModifyingUser)")
                    .setPageSize(1000)
                    .setPageToken(nextPageToken)
                    .execute();

            nextPageToken = fileList.getNextPageToken();
            List<File> files = fileList.getFiles();
            for (File file : files) {
                String filePath = path + file.getName();
                if (file.getTrashed()) {
                    if (deletedFiles.contains(file.getId())) {
                        map.put(filePath, file);
                    }
                } else if (file.getMimeType().equalsIgnoreCase(Constant.MIME_TYPE_FOLDER)) {
                    if (file.getModifiedTime().getValue() > lastSyncTime) {
                        map.put(filePath, file);
                    }
                    HashMap<String, File> subFiles = getOnlineModifiedFiles(filePath, file.getId(), lastSyncTime, deletedFiles);
                    if (!subFiles.isEmpty()) {
                        map.putAll(subFiles);
                    }
                } else {
                    map.put(filePath, file);
                }
            }

        } while (nextPageToken != null);

        return map;
    }

    public static void downloadFiles(Map<String, File> modifiedFiles, OnProgressUpdate listener) throws IOException {
        Map<String, List<String>> exportFormats = getExportFormats();
        String syncPath = Tools.getPreference(Constant.KEY_SYNC_FOLDER_PATH);
        int count = 0;
        for (String key : modifiedFiles.keySet()) {
            count++;
            File file = modifiedFiles.get(key);
            java.io.File localFile = new java.io.File(syncPath + java.io.File.separator + key);
            if (file.getTrashed()) {
                localFile.delete();
                if (listener != null) {
                    listener.setProgress(count, modifiedFiles.size(), "Deleting " + localFile.getName());
                }
            } else {
                if (file.getMimeType().equalsIgnoreCase(Constant.MIME_TYPE_FOLDER)) {
                    localFile.mkdir();
                } else {
                    try (OutputStream outputStream = new FileOutputStream(localFile)) {
                        // If is G Suite document
                        if (exportFormats.containsKey(file.getMimeType())) {
                            String mimeType = exportFormats.get(file.getMimeType()).get(0);
                            getInstance()
                                    .files()
                                    .export(file.getId(), mimeType)
                                    .executeMediaAndDownloadTo(outputStream);
                        } else {
                            getInstance()
                                    .files()
                                    .get(file.getId())
                                    .executeMediaAndDownloadTo(outputStream);
                        }
                    }
                }
                if (listener != null) {
                    listener.setProgress(count, modifiedFiles.size(), "Downloading " + localFile.getName());
                }
            }
        }
    }

    public static void downloadFolder(String path, String folderId, Map<String, List<String>> exportFormats, OnProgressUpdate listener) throws IOException {
        java.io.File folder = new java.io.File(path);
        if (listener != null) {
            listener.setProgress(0, 1, "Downloading folder " + folder.getName());
        }
        folder.mkdir();
        path += java.io.File.separator;
        String nextPageToken = null;
        do {
            String query = String.format("'%s' in parents" +
                    " and trashed = false", folderId);
            FileList fileList = getInstance()
                    .files()
                    .list()
                    .setQ(query)
                    .setFields("nextPageToken, files(id, size, name, mimeType)")
                    .setPageSize(1000)
                    .setPageToken(nextPageToken)
                    .execute();

            nextPageToken = fileList.getNextPageToken();
            List<File> files = fileList.getFiles();
            for (File file : files) {
                if (file.getMimeType().equalsIgnoreCase(Constant.MIME_TYPE_FOLDER)) {
                    downloadFolder(path + file.getName(), file.getId(), exportFormats, listener);
                } else {
                    if (listener != null) {
                        listener.setProgress(0, 1, "Downloading file " + file.getName());
                    }
                    try (OutputStream outputStream = new FileOutputStream(path + file.getName())) {
                        // If is G Suite document
                        if (exportFormats.containsKey(file.getMimeType())) {
                            String mimeType = exportFormats.get(file.getMimeType()).get(0);
                            getInstance()
                                    .files()
                                    .export(file.getId(), mimeType)
                                    .executeMediaAndDownloadTo(outputStream);
                        } else {
                            getInstance()
                                    .files()
                                    .get(file.getId())
                                    .executeMediaAndDownloadTo(outputStream);
                        }
                    }
                }
            }

        } while (nextPageToken != null);
    }

    public static String uploadFolderInclusive(String path, @NonNull String folderName, String rootParentId, OnProgressUpdate listener) throws IOException {
        HashMap<String, String> parents = new HashMap<>();
        String localRootName = new java.io.File(path).getName();
        int total;
        AtomicInteger current = new AtomicInteger(1);

        try (Stream<Path> paths = Files.walk(Paths.get(path))) {
            total = (int) paths.count();
        }

        try (Stream<Path> paths = Files.walk(Paths.get(path))) {
            paths.forEach(p -> {
                java.io.File localFile = new java.io.File(p.toString());
                List<String> parentIds = new ArrayList<>();

                // If it is not root folder
                if (!parents.isEmpty()) {
                    String parentDir = p.getParent().toString();
                    parentDir = parentDir
                            .substring(parentDir.indexOf(localRootName) + localRootName.length());

                    if (parents.containsKey(parentDir)) {
                        parentIds.add(parents.get(parentDir));
                    }
                } else if (rootParentId != null) {
                    parentIds.add(rootParentId);
                }

                File fileMetadata = new File();
                fileMetadata.setName(parents.isEmpty() ? folderName : localFile.getName());
                fileMetadata.setParents(parentIds);

                try {
                    File file;
                    if (localFile.isDirectory()) {
                        fileMetadata.setMimeType(Constant.MIME_TYPE_FOLDER);

                        file = getInstance().files().create(fileMetadata)
                                .setFields("id")
                                .execute();

                        String localFilePath = "";
                        if (!parents.isEmpty()) {
                            localFilePath = localFile.toPath().toString();
                            localFilePath = localFilePath
                                    .substring(localFilePath.indexOf(localRootName) + localRootName.length());
                        }
                        parents.put(localFilePath, file.getId());
                    } else {
                        FileContent mediaContent = new FileContent(
                                Tools.getMimeType(localFile.getName()),
                                localFile);

                        getInstance().files().create(fileMetadata, mediaContent)
                                .setFields("id, parents")
                                .execute();
                    }

                    if (listener != null) {
                        listener.setProgress(current.getAndIncrement(), total, "Uploading " + localFile.getName());
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

        return parents.get("");
    }

    public static HashMap<String, String> uploadFolder(String syncRoot, String uploadTreePath, String rootParentId) throws IOException {
        HashMap<String, String> uploadFolder = new HashMap<>();

        try (Stream<Path> paths = Files.walk(Paths.get(syncRoot, uploadTreePath), 1)) {
            paths.filter(p -> Files.isRegularFile(p) || p.toString().equalsIgnoreCase(Paths.get(syncRoot, uploadTreePath).toString()))
                    .forEach(p -> {
                        java.io.File localFile = new java.io.File(p.toString());

                        File fileMetadata = new File();
                        fileMetadata.setName(localFile.getName());
                        fileMetadata.setParents(Collections.singletonList(uploadFolder.isEmpty() ? rootParentId : uploadFolder.get(uploadTreePath)));

                        try {
                            File file;
                            if (localFile.isDirectory()) {
                                fileMetadata.setMimeType(Constant.MIME_TYPE_FOLDER);

                                file = getInstance().files().create(fileMetadata)
                                        .setFields("id")
                                        .execute();

                                uploadFolder.put(uploadTreePath, file.getId());
                            } else {
                                FileContent mediaContent = new FileContent(
                                        Tools.getMimeType(localFile.getName()),
                                        localFile);

                                getInstance().files().create(fileMetadata, mediaContent)
                                        .setFields("id, parents")
                                        .execute();
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
        return uploadFolder;
    }

    public static void updateFolder(String localPath, @NonNull String parentId) throws IOException {
        TreeSet<String> localFiles = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(Paths.get(localPath), 1)) {
            paths
                    .filter(Files::isRegularFile)
                    .forEach(p -> {
                        localFiles.add(p.getFileName().toString());
                    });
        }

        HashMap<String, File> onlineFiles = new HashMap<>();
        String nextPageToken = null;
        do {
            String query = String.format("'%s' in parents" +
                    " and mimeType != '%s'" +
                    " and trashed = false", parentId, Constant.MIME_TYPE_FOLDER);
            FileList fileList = getInstance()
                    .files()
                    .list()
                    .setQ(query)
                    .setFields("nextPageToken, files(id, name, mimeType, modifiedTime, md5Checksum)")
                    .setPageSize(1000)
                    .setPageToken(nextPageToken)
                    .execute();

            nextPageToken = fileList.getNextPageToken();
            for (File file : fileList.getFiles()) {
                onlineFiles.put(file.getName(), file);
            }

        } while (nextPageToken != null);

        Set<String> refreshFiles = Sets.intersection(localFiles, onlineFiles.keySet());
        Set<String> removeFiles = Sets.difference(onlineFiles.keySet(), localFiles);
        Set<String> uploadFiles = Sets.difference(localFiles, onlineFiles.keySet());

        long lastSyncTime = Long.parseLong(Tools.getPreference(Constant.KEY_LAST_SYNC));
        for (String filePath : refreshFiles) {
            java.io.File localFile = new java.io.File(localPath + java.io.File.separator + filePath);
            if (Files.getLastModifiedTime(localFile.toPath()).toMillis() <= lastSyncTime) {
                continue;
            }
            File onlineFile = onlineFiles.get(filePath);
            HashCode hc = com.google.common.io.Files
                    .asByteSource(localFile).hash(Hashing.md5());
            if (hc.toString().equals(onlineFile.getMd5Checksum())) {

                File updateFile = new File();
                updateFile.setName(onlineFile.getName());
                updateFile.setParents(onlineFile.getParents());
                updateFile.setMimeType(onlineFile.getMimeType());

                FileContent fileContent = new FileContent(onlineFile.getMimeType(), localFile);

                getInstance()
                        .files()
                        .update(onlineFile.getId(), updateFile, fileContent)
                        .execute();
            }
        }

        for (String filePath : removeFiles) {
            getInstance()
                    .files()
                    .delete(onlineFiles.get(filePath).getId())
                    .execute();
        }

        for (String filePath : uploadFiles) {
            java.io.File localFile = new java.io.File(localPath + java.io.File.separator + filePath);
            File fileMetadata = new File();
            fileMetadata.setName(localFile.getName());
            fileMetadata.setParents(Collections.singletonList(parentId));
            FileContent mediaContent = new FileContent(
                    Tools.getMimeType(localFile.getName()),
                    localFile);

            getInstance().files().create(fileMetadata, mediaContent)
                    .setFields("id, parents")
                    .execute();
        }
    }

    public static boolean syncToCloud(OnProgressUpdate listener) throws IOException {
        String id = Tools.getPreference(Constant.KEY_SYNC_FOLDER_ID);
        String syncPath = Tools.getPreference(Constant.KEY_SYNC_FOLDER_PATH);
        File root = DriveService.checkExist(id);
        if (root != null) {
            HashMap<String, String> onlineFolderMap =
                    DriveService.getOnlineFolderMap("", root.getId());
            TreeSet<String> localFolderTree =
                    DriveService.getLocalFolderTree(syncPath);
            List<String> removes = new ArrayList<>(Sets.difference(onlineFolderMap.keySet(), localFolderTree));
            List<String> uploads = new ArrayList<>(Sets.difference(localFolderTree, onlineFolderMap.keySet()));
            List<String> exacts = new ArrayList<>(Sets.intersection(localFolderTree, onlineFolderMap.keySet()));
            exacts.add(0, "");

            removes.sort(Comparator.comparingInt(o -> Paths.get(o).getNameCount()));
            uploads.sort(Comparator.comparingInt(o -> Paths.get(o).getNameCount()));
            exacts.sort(Comparator.comparingInt(o -> Paths.get(o).getNameCount()));

            int total = removes.size() + uploads.size() + exacts.size();
            int count = 0;

            for (String path : uploads) {
                String parents = "";
                if (path.contains(java.io.File.separator)) {
                    parents = path.substring(0, path.indexOf(java.io.File.separator));
                }
                onlineFolderMap.putAll(DriveService.uploadFolder(syncPath, path, parents.isEmpty() ? root.getId() : onlineFolderMap.get(parents)));
                count++;
                if (listener != null) {
                    listener.setProgress(count, total, "Uploading folder " + Paths.get(path).getFileName().toString());
                }
            }

            for (String path : exacts) {
                java.io.File exactFolder = new java.io.File(syncPath + (path.isEmpty() ? "" : java.io.File.separator + path));
                DriveService.updateFolder(exactFolder.getPath(), path.isEmpty() ? id : onlineFolderMap.get(path));
                count++;
                if (listener != null) {
                    listener.setProgress(count, total, "Updating folder " + exactFolder.getName());
                }
            }

            for (String path : removes) {
                DriveService.getInstance()
                        .files()
                        .delete(onlineFolderMap.get(path))
                        .execute();
                count++;
                if (listener != null) {
                    listener.setProgress(count, total, "Deleting folder " + Paths.get(path).getFileName().toString());
                }
            }

            return true;
        }

        return false;
    }

    public static HashMap<String, String> getLocalModifiedFiles() throws IOException {
        String id = Tools.getPreference(Constant.KEY_SYNC_FOLDER_ID);
        String syncPath = Tools.getPreference(Constant.KEY_SYNC_FOLDER_PATH);
        long lastSyncTime = Long.parseLong(Tools.getPreference(Constant.KEY_LAST_SYNC));
        HashMap<String, String> map = new HashMap<>();

        TreeSet<String> localFiles = new TreeSet<>();

        // Get all file created before last sync
        HashMap<String, File> onlineFiles =
                getOnlineFileMap("", id, lastSyncTime, getOnlineModifiedFiles(id, "DELETE").keySet());

        try (Stream<Path> paths = Files.walk(Paths.get(syncPath))) {
            paths
                    .skip(1)
                    .forEach(p -> {
                        try {
                            String filePath = p.toString();
                            filePath = filePath
                                    .substring(filePath.indexOf(syncPath) + syncPath.length() + 1);

                            localFiles.add(filePath);

                            if (!onlineFiles.containsKey(filePath)) {
                                map.put(filePath, Constant.CREATE);
                            } else if (Files.getLastModifiedTime(Paths.get(syncPath, filePath)).toMillis() > lastSyncTime) {
                                    map.put(filePath, Constant.MODIFY);
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }

        Set<String> deletes = Sets.difference(onlineFiles.keySet(), localFiles);
        if (!deletes.isEmpty()) {
            deletes.stream().forEach(f -> map.put(f, Constant.DELETE));
        }

        return map;
    }

    public static HashMap<String, String> getOnlineFolderMap(String path, String folderId) throws IOException {
        HashMap<String, String> map = new HashMap<>();
        String nextPageToken = null;
        do {
            String query = String.format("'%s' in parents" +
                    " and trashed = false and mimeType = '%s'", folderId, Constant.MIME_TYPE_FOLDER);
            FileList fileList = getInstance()
                    .files()
                    .list()
                    .setQ(query)
                    .setFields("nextPageToken, files(id, name)")
                    .setPageSize(1000)
                    .setPageToken(nextPageToken)
                    .execute();
            nextPageToken = fileList.getNextPageToken();

            List<File> files = fileList.getFiles();
            for (File file : files) {
                String filePath = path + file.getName();
                map.put(path + file.getName(), file.getId());
                map.putAll(getOnlineFolderMap(filePath + java.io.File.separator, file.getId()));
            }
        } while (nextPageToken != null);

        return map;
    }

    public static HashMap<String, File> getOnlineFileMap(String path, String folderId, long endTime, Set<String> deleteFiles) throws IOException {
        HashMap<String, File> map = new HashMap<>();
        String nextPageToken = null;
        String query = String.format("'%s' in parents and createdTime < '%s'", folderId, new DateTime(endTime).toStringRfc3339());
        do {
            FileList fileList = getInstance()
                    .files()
                    .list()
                    .setQ(query)
                    .setFields("nextPageToken, files(id, name, trashed, mimeType, createdTime, modifiedTime)")
                    .setPageSize(1000)
                    .setPageToken(nextPageToken)
                    .execute();
            nextPageToken = fileList.getNextPageToken();

            List<File> files = fileList.getFiles();
            for (File file : files) {
                String filePath = path + file.getName();
                if (!file.getTrashed() || deleteFiles.contains(file.getId())) {
                    map.put(path + file.getName(), file);
                }
                if (file.getMimeType().equalsIgnoreCase(Constant.MIME_TYPE_FOLDER)) {
                    map.putAll(getOnlineFileMap(filePath + java.io.File.separator, file.getId(), endTime, deleteFiles));
                }
            }
        } while (nextPageToken != null);

        return map;
    }

    public static TreeSet<String> getLocalFolderTree(String path) throws IOException {
        TreeSet<String> treeSet = new TreeSet<>();
        java.io.File root = new java.io.File(path);
        try (Stream<Path> paths = Files.walk(Paths.get(path))) {
            paths
                    .filter(Files::isDirectory)
                    .skip(1)
                    .forEach(p -> {
                        String dirPath = p.toString();
                        dirPath = dirPath
                                .substring(dirPath.indexOf(root.getName()) + root.getName().length() + 1);
                        treeSet.add(dirPath);
                    });
        }

        return treeSet;
    }

    public static TreeSet<String> getLocalFileTree(String path) throws IOException {
        TreeSet<String> treeSet = new TreeSet<>();
        java.io.File root = new java.io.File(path);
        try (Stream<Path> paths = Files.walk(Paths.get(path))) {
            paths
                    .skip(1)
                    .forEach(p -> {
                        String filePath = p.toString();
                        filePath = filePath
                                .substring(filePath.indexOf(root.getName()) + root.getName().length() + 1);
                        treeSet.add(filePath);
                    });
        }

        return treeSet;
    }
}