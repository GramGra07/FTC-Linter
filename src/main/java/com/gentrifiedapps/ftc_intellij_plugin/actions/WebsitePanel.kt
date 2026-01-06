package com.gentrifiedapps.ftc_intellij_plugin.actions

import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingConstants

class WebsiteToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val websites = linkedMapOf(
//            "Temp" to "http://192.168.86.244:5173/dash/",
            "Control Hub" to "http://192.168.43.1:8080/dash",
            "RC Phone" to "http://192.168.49.1:8080/dash",
//            "Google" to "https://google.com"
        )

        // Safety check: JCEF requires a specific JetBrains Runtime.
        // If it's not available, we show a fallback UI instead of crashing.
        val panel=
            JcefFallbackPanel(websites)

        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
        
        if (panel is Disposable) {
            content.setDisposer(panel)
        }
    }

    private fun isJcefSupported(): Boolean {
        return try {
            JBCefApp.isSupported()
        } catch (t: Throwable) {
            false
        }
    }
}

/**
 * Fallback UI shown when JCEF (Chromium) is not available in the IDE's runtime.
 * Allows the user to open the dashboard in their system browser.
 */
private class JcefFallbackPanel(private val websites: Map<String, String>) : JPanel(BorderLayout()) {
    init {
        val message = "<html><center>"+
                "Please use the button below to open FTC Dashboard in your system browser.</center></html>"
        
        add(JBLabel(message, SwingConstants.CENTER), BorderLayout.CENTER)

        val bottomPanel = JPanel(FlowLayout())
        val combo = ComboBox(websites.keys.toTypedArray())
        val openButton = JButton("Open in System Browser")
        
        bottomPanel.add(combo)
        bottomPanel.add(openButton)
        add(bottomPanel, BorderLayout.SOUTH)

        openButton.addActionListener {
            val selected = combo.selectedItem as String
            val url = websites[selected] ?: return@addActionListener
            // Use instance to avoid conflicts with other getInstance() methods
            BrowserLauncher.instance.browse(url)
        }
    }
}
