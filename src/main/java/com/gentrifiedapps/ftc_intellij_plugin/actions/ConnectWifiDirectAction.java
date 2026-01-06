package com.gentrifiedapps.ftc_intellij_plugin.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public class ConnectWifiDirectAction extends AnAction implements DumbAware {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        String defaultTarget = "192.168.43.1:5555";
        String target = Messages.showInputDialog(
                project,
                "Enter IP:port to adb connect",
                "Connect over Wi‑Fi Direct",
                null,
                defaultTarget,
                null
        );
        
        if (target != null && !target.trim().isEmpty()) {
            AdbUtils.runAdbCommand(project, "ADB Connect", "connect", target.trim());
        }
    }
}
