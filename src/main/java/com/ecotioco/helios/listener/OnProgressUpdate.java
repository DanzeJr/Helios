package com.ecotioco.helios.listener;

public interface OnProgressUpdate {
    void setProgress(int current, int total, String message);
}
