package com.gentrifiedapps.ftc_intellij_plugin.actions;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class AdbUtils {
    public static void runAdbCommand(Project project, String title, String... args) {
        String adbPath = resolveAdbPath(project);
        
        // Safety check: if we resolved to just "adb", check if it's on system PATH
        if (adbPath.equals("adb")) {
            if (!isOnPath("adb")) {
                showNotification(project, title, "ADB not found. Please set ANDROID_HOME or ensure adb is on your system PATH.", NotificationType.ERROR);
                return;
            }
        } else if (!new File(adbPath).exists()) {
             showNotification(project, title, "Resolved ADB path does not exist: " + adbPath, NotificationType.ERROR);
             return;
        }

        GeneralCommandLine cmd = new GeneralCommandLine(adbPath)
                .withParameters(args)
                .withWorkDirectory(project.getBasePath())
                .withCharset(StandardCharsets.UTF_8);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, title, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    CapturingProcessHandler handler = new CapturingProcessHandler(cmd);
                    ProcessOutput output = handler.runProcess(30_000);
                    
                    NotificationType type = output.getExitCode() == 0 ? NotificationType.INFORMATION : NotificationType.ERROR;
                    String message = formatOutput(output);
                    
                    if (message.isEmpty()) {
                        message = type == NotificationType.INFORMATION ? "Command successful" : "Command failed";
                    }

                    String finalMessage = message;
                    NotificationType finalType = type;
                    ApplicationManager.getApplication().invokeLater(() -> {
                        var notification = NotificationGroupManager.getInstance()
                            .getNotificationGroup("FTC Linter")
                            .createNotification(title, finalMessage, finalType);
                        
                        // Add retry action if it failed
                        if (finalType == NotificationType.ERROR) {
                            notification.addAction(NotificationAction.createSimple("Retry", () -> runAdbCommand(project, title, args)));
                        }
                        
                        notification.notify(project);
                    });
                } catch (Exception ex) {
                    String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                    ApplicationManager.getApplication().invokeLater(() -> 
                        showNotification(project, title, errorMsg, NotificationType.ERROR)
                    );
                }
            }
        });
    }

    private static boolean isOnPath(String cmd) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        String delimiter = File.pathSeparator;
        for (String dir : path.split(delimiter)) {
            File file = new File(dir, System.getProperty("os.name").toLowerCase().contains("win") ? cmd + ".exe" : cmd);
            if (file.isFile() && file.canExecute()) return true;
        }
        return false;
    }

    private static String formatOutput(ProcessOutput output) {
        StringBuilder sb = new StringBuilder();
        if (!output.getStdout().isEmpty()) sb.append(output.getStdout().trim());
        if (!output.getStderr().isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(output.getStderr().trim());
        }
        return sb.toString();
    }

    public static void showNotification(Project project, String title, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("FTC Linter")
                .createNotification(title, content, type)
                .notify(project);
    }

    private static String resolveAdbPath(Project project) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String exe = isWindows ? "adb.exe" : "adb";

        String basePath = project.getBasePath();
        if (basePath != null) {
            Path lp = Paths.get(basePath, "local.properties");
            if (Files.isRegularFile(lp)) {
                Properties props = new Properties();
                try (var in = Files.newInputStream(lp)) {
                    props.load(in);
                    String sdkDir = props.getProperty("sdk.dir");
                    if (sdkDir != null && !sdkDir.isBlank()) {
                        Path p = Paths.get(sdkDir, "platform-tools", exe);
                        if (Files.isRegularFile(p)) return p.toString();
                    }
                } catch (IOException ignored) {}
            }
        }

        String[] envs = {System.getenv("ANDROID_SDK_ROOT"), System.getenv("ANDROID_HOME")};
        for (String env : envs) {
            if (env != null && !env.isBlank()) {
                Path p = Paths.get(env, "platform-tools", exe);
                if (Files.isRegularFile(p)) return p.toString();
            }
        }

        if (isWindows) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                Path p = Paths.get(localAppData, "Android", "Sdk", "platform-tools", exe);
                if (Files.isRegularFile(p)) return p.toString();
            }
        }

        return "adb";
    }
}
