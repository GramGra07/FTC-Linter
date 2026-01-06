package com.gentrifiedapps.ftc_intellij_plugin.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DisconnectWifiDirectAction extends AnAction implements DumbAware {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        String defaultTarget = "192.168.43.1:5555";
        String target = Messages.showInputDialog(
                project,
                "Enter IP:port to adb disconnect (leave blank to disconnect all)",
                "Disconnect Wi‑Fi Device",
                null,
                defaultTarget,
                null
        );
        
        if (target != null) {
            String ipPort = target.trim();
            if (ipPort.isEmpty()) {
                AdbUtils.runAdbCommand(project, "ADB Disconnect", "disconnect");
            } else {
                AdbUtils.runAdbCommand(project, "ADB Disconnect", "disconnect", ipPort);
            }
        }
    }
}
