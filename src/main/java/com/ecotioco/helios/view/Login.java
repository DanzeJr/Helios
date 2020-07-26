package com.ecotioco.helios.view;

import com.ecotioco.helios.util.DriveService;
import com.ecotioco.helios.util.Tools;
import com.google.api.services.drive.model.Change;
import com.google.api.services.drive.model.ChangeList;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;

public class Login extends JFrame {

    private JPanel rootPanel;
    private JLabel lblApp;
    private JButton signInBtn;

    public Login(String title) throws HeadlessException {
        super(title);

        setContentPane(rootPanel);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
        setSize(600, 600);
        setLocationRelativeTo(null);
        initComponents();
    }

    private void initComponents() {
        signInBtn.addActionListener(e -> {
            if (DriveService.getInstance() != null) {
                JOptionPane.showMessageDialog(this, "Sign in successfully!", "SUCCESS", JOptionPane.INFORMATION_MESSAGE);
                this.dispose();
                if (Tools.isConfigured()) {
                    new Dashboard("Helios", 0);
                } else {
                    new Configuration("Set up sync folder");
                }
            }
        });
    }

    public static void main(String[] args) {
        if (!DriveService.isLoggedIn()) {
            new Login("Sign in to Google Drive");
        } else if ( Tools.isConfigured()) {
            new Dashboard("Helios", 0);
        } else {
            new Configuration("Set up sync folder");
        }
    }
}
