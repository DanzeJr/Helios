package com.ecotioco.helios.view;

import javax.swing.*;
import java.awt.*;

public class LoadingDialog extends JDialog {
    private JPanel contentPane;
    private JProgressBar progressBar;
    private JLabel lblMessage;

    public LoadingDialog(Frame owner, String title, boolean modal) {
        setContentPane(contentPane);
        setModal(modal);
        setTitle(title);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
    }

    public void setMessage(String message) {
        lblMessage.setText(message);
    }
}
