package com.gentrifiedapps.ftc_intellij_plugin.actions;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ConnectWifiDirectAction extends AnAction implements DumbAware {
    @Override
    public void actionPerformed(AnActionEvent e) {
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
        if (target == null || target.trim().isEmpty()) return;
        String ipPort = target.trim();

        String adbPath = resolveAdbPath(project);
        GeneralCommandLine cmd = new GeneralCommandLine(adbPath, "connect", ipPort)
                .withWorkDirectory(project.getBasePath())
                .withCharset(StandardCharsets.UTF_8);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "ADB Connect", false) {
            @Override
            public void run(ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                String title = "ADB Connect";
                NotificationType type;
                String message;
                try {
                    CapturingProcessHandler handler = new CapturingProcessHandler(cmd);
                    ProcessOutput output = handler.runProcess(30_000);
                    StringBuilder msg = new StringBuilder();
                    if (!output.getStdout().isEmpty()) msg.append(output.getStdout().trim());
                    if (!output.getStderr().isEmpty()) {
                        if (!msg.isEmpty()) msg.append("\n");
                        msg.append(output.getStderr().trim());
                    }
                    type = output.getExitCode() == 0 ? NotificationType.INFORMATION : NotificationType.ERROR;
                    message = msg.isEmpty() ? (type == NotificationType.INFORMATION ? "Connected" : "Failed") : msg.toString();
                } catch (Exception ex) {
                    type = NotificationType.ERROR;
                    message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                }
                String finalMessage = message;
                NotificationType finalType = type;
                ApplicationManager.getApplication().invokeLater(() -> ConnectWifiDirectAction.notify(project, title, finalMessage, finalType));
            }
        });
    }

    private static void notify(Project project, String title, String content, NotificationType type) {
        Notification n = NotificationGroupManager.getInstance()
                .getNotificationGroup("FTC")
                .createNotification(title, content, type);
        n.notify(project);
    }

    private static String resolveAdbPath(Project project) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String exe = isWindows ? "adb.exe" : "adb";

        // 1) Try local.properties sdk.dir
        String basePath = project.getBasePath();
        if (basePath != null) {
            Path lp = Paths.get(basePath, "local.properties");
            Path p = resolveFromLocalProperties(lp, exe);
            if (p != null) return p.toString();
        }

        // 2) Try env vars
        String[] envs = new String[]{System.getenv("ANDROID_SDK_ROOT"), System.getenv("ANDROID_HOME")};
        for (String env : envs) {
            if (env == null || env.isBlank()) continue;
            Path p = Paths.get(env, "platform-tools", exe);
            if (Files.isRegularFile(p)) return p.toString();
        }

        // 3) Try common default on Windows
        if (isWindows) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                Path p = Paths.get(localAppData, "Android", "Sdk", "platform-tools", exe);
                if (Files.isRegularFile(p)) return p.toString();
            }
        }

        // 4) Fallback to relying on PATH
        return "adb";
    }

    private static Path resolveFromLocalProperties(Path localProps, String exe) {
        if (!Files.isRegularFile(localProps)) return null;
        Properties props = new Properties();
        try {
            try (var in = Files.newInputStream(localProps)) {
                props.load(in);
            }
        } catch (IOException ignored) {
            return null;
        }
        String sdkDir = props.getProperty("sdk.dir");
        if (sdkDir == null || sdkDir.isBlank()) return null;
        Path p = Paths.get(sdkDir, "platform-tools", exe);
        return Files.isRegularFile(p) ? p : null;
    }
}
