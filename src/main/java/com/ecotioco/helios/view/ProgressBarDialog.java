package com.ecotioco.helios.view;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;

public class ProgressBarDialog extends JDialog {
    private JPanel contentPane;
    private JProgressBar progressBar;
    private JTextArea txtProgress;
    private JButton nextBtn;
    private String buttonText;

    public ProgressBarDialog(Frame owner, String title, String buttonText) {
        setContentPane(contentPane);
        setModal(true);
        setTitle(title);
        setSize(500, 250);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        DefaultCaret caret = (DefaultCaret) txtProgress.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        this.buttonText = buttonText;
        nextBtn.setVisible(false);
    }

    public void setProgress(String message, double progress) {
        progressBar.setValue((int) progress);
        progressBar.setString(String.format("%.1f", progress) + "%");
        txtProgress.append(message + "\n");
    }

    public void finish() {
        txtProgress.append("Finished");
        if (this.buttonText != null) {
            nextBtn.setText(buttonText);
            nextBtn.setVisible(true);
        }
    }

    public void setNextListener(AbstractAction listener) {
        nextBtn.addActionListener(listener);
    }
}
