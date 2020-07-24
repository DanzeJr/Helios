package com.ecotioco.helios.util;

import net.sf.jmimemagic.Magic;
import net.sf.jmimemagic.MagicException;
import net.sf.jmimemagic.MagicMatchNotFoundException;
import net.sf.jmimemagic.MagicParseException;

import javax.activation.MimetypesFileTypeMap;
import javax.swing.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.CharacterIterator;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.StringCharacterIterator;
import java.util.Comparator;
import java.util.Properties;

public class Tools {

    private static final String CONFIGURATION_PATH = "configuration.xml";

    //Writing and Saving Configurations
    public static boolean setPreference(String key, String value) {
        Properties configFile = new Properties();
        try {
            File file = new File(CONFIGURATION_PATH);
            if (!file.createNewFile()) {
                InputStream inputStream = new FileInputStream(file);
                configFile.loadFromXML(inputStream);
                inputStream.close();
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage());
            return false;
        }

        configFile.setProperty(key, value);
        try {
            OutputStream outputStream = new FileOutputStream(CONFIGURATION_PATH);
            configFile.storeToXML(outputStream, "Configuration file for the Helios Uploader");
            outputStream.close();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage());
            return false;
        }

        return true;
    }

    //Reading Configurations
    public static String getPreference(String key) {
        Properties configFile = new Properties();
        try {
            File file = new File(CONFIGURATION_PATH);
            if (!file.exists()) {
                return null;
            }
            InputStream inputStream = new FileInputStream(file);
            configFile.loadFromXML(inputStream);
            inputStream.close();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage());
            return null;
        }

        return (configFile.getProperty(key));
    }

    public static boolean isConfigured() {
        String syncPath = getPreference(Constant.KEY_SYNC_FOLDER_PATH);
        String lastSyncTime = getPreference(Constant.KEY_LAST_SYNC);
        String rootId = getPreference(Constant.KEY_SYNC_FOLDER_ID);
        if (syncPath != null && lastSyncTime != null && rootId != null) {
            File folder = new File(syncPath);
            if (folder.exists() && folder.isDirectory()) {
                return true;
            }
        }

        return false;
    }

    public static void resetConfiguration() {
        try {
            Files.deleteIfExists(Paths.get(CONFIGURATION_PATH));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static long countSlash(String s) {
        return s.chars().filter(c -> c == File.separator.charAt(0)).count();
    }

    public static boolean deleteFolder(String path) {
        try {
            Files.walk(Paths.get(path))
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);

            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static String getFormattedDate(long dateTime) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return df.format(dateTime);
    }

    public static String getMimeType(String fileName) throws IOException {
        File file = new File(fileName);
        try {
            return Magic.getMagicMatch(file, true).getMimeType();
        } catch (MagicParseException | MagicMatchNotFoundException | MagicException e) {
            return Files.probeContentType(Paths.get(fileName));
        }
    }

    public static String getFormattedDate(long dateTime, String pattern) {
        DateFormat df = new SimpleDateFormat(pattern);
        return df.format(dateTime);
    }

    public static String getFormattedSize(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.1f %cB", value / 1024.0, ci.current());
    }

}
