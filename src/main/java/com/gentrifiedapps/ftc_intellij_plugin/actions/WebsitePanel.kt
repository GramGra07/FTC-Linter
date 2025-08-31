package com.gentrifiedapps.ftc_intellij_plugin.actions

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.net.URL
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.SwingWorker
import javax.swing.event.HyperlinkEvent

class WebsiteToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val websites = linkedMapOf(
            "Control Hub" to "http://192.168.43.1:8080/dash",
            "RC Phone" to "http://192.168.49.1:8080/dash",
            "test" to "https://google.com"
        )

        val panel = WebsitePanel(websites)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
        // Register panel to be disposed with the content
        content.setDisposer(panel)
    }
}

private class WebsitePanel(
    private val websites: Map<String, String>
) : JPanel(BorderLayout()), Disposable {

    private val editorPane = JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
    }

    private val scrollPane = JBScrollPane(editorPane)

    @Volatile
    private var loader: SwingWorker<Unit, Unit>? = null

    init {
        // Top bar: site selector and reload button
        val topBar = JPanel(BorderLayout())
        val combo = ComboBox(websites.keys.toTypedArray())
        val reload = javax.swing.JButton("Reload")
        topBar.add(combo, BorderLayout.CENTER)
        topBar.add(reload, BorderLayout.EAST)
        add(topBar, BorderLayout.NORTH)

        // Main viewer
        add(scrollPane, BorderLayout.CENTER)

        // URL indicator
        val urlField = JBTextField().apply {
            isEditable = false
            text = websites.values.first()
        }
        add(urlField, BorderLayout.SOUTH)

        // Hyperlink handling
        editorPane.addHyperlinkListener { e: HyperlinkEvent ->
            if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                val u = e.url?.toString() ?: return@addHyperlinkListener
                urlField.text = u
                loadUrl(u)
            }
        }

        // Load initial page
        loadUrl(websites.values.first())

        // Selection changes
        combo.addActionListener {
            val selected = combo.selectedItem as String
            val u = websites.getValue(selected)
            urlField.text = u
            loadUrl(u)
        }

        // Reload
        reload.addActionListener {
            loadUrl(urlField.text)
        }
    }

    private fun loadUrl(url: String) {
        loader?.cancel(true)

        loader = object : SwingWorker<Unit, Unit>() {
            override fun doInBackground() {
                try {
                    editorPane.setPage(URL(url))
                } catch (t: Throwable) {
                    val err = "<html><body><h3>Failed to load:</h3><p>${escapeHtml(url)}</p><pre>${escapeHtml(t.message ?: t.toString())}</pre></body></html>"
                    javax.swing.SwingUtilities.invokeLater { editorPane.text = err }
                }
            }
        }

        loader?.execute()
    }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    override fun dispose() {
        try {
            loader?.cancel(true)
        } catch (_: Throwable) {
            // ignore
        }
    }
}
