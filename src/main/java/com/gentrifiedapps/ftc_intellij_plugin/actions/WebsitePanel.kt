package com.gentrifiedapps.ftc_intellij_plugin.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel

class WebsiteToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val websites = linkedMapOf(
            "Control Hub" to "http://192.168.43.1:8080/dash",
            "RC Phone" to "http://192.168.49.1:8080/dash"
        )
//        val websites = linkedMapOf(
//            "Control Hub" to "https://google.com",
//            "RC Phone" to "http://192.168.49.1:8080/dash"
//        )
        val panel = WebsitePanel(websites)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
//        Disposer.register(content.disposable, panel) // ensure JavaFX resources are cleaned up
    }
}

private class WebsitePanel(
    private val websites: Map<String, String>
) : JPanel(BorderLayout()), Disposable {

    private val jfxPanel = JFXPanel() // bootstraps JavaFX runtime
    private var engine: WebEngine? = null
    private var webView: WebView? = null

    init {
        // Top bar: site chooser + reload
        val topBar = JPanel(BorderLayout())
        val combo = ComboBox(websites.keys.toTypedArray())
        val reload = JButton("Reload")
        topBar.add(combo, BorderLayout.CENTER)
        topBar.add(reload, BorderLayout.EAST)
        add(topBar, BorderLayout.NORTH)
        add(jfxPanel, BorderLayout.CENTER)

        val urlField = JBTextField().apply {
            isEditable = false
            text = websites.values.first()
        }
        add(urlField, BorderLayout.SOUTH)

        // Initialize JavaFX scene + first page
        val firstUrl = websites.values.first()
        Platform.runLater {
            webView = WebView()
            engine = webView!!.engine
            jfxPanel.scene = Scene(BorderPane(webView))
            engine!!.load(firstUrl)
        }

        // Handle selection changes
        combo.addActionListener {
            val selected = combo.selectedItem as String
            val url = websites.getValue(selected)
            urlField.text = url
            Platform.runLater { engine?.load(url) }
        }

        // Reload
        reload.addActionListener {
            Platform.runLater { engine?.reload() }
        }
    }

    override fun dispose() {
        // Ensure WebView resources are released when tool window is disposed
        Platform.runLater {
            try {
                engine?.load("about:blank")
                webView?.engine?.history?.entries?.clear()
            } catch (_: Throwable) { /* ignore */ }
            webView = null
            engine = null
        }
    }
}
