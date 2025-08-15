package com.gentrifiedapps.ftc_intellij_plugin.prerun

import com.intellij.openapi.options.Configurable
import javax.swing.*

class FtcInspectionConfigurable : Configurable {
    private var panel: JPanel = JPanel()
    private var blockOnFatal = JCheckBox("Block run when fatal FTC issues found", true)

    override fun getDisplayName(): String = "FTC Checks"
    override fun createComponent(): JComponent {
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.add(blockOnFatal)
        return panel
    }
    override fun isModified(): Boolean = false
    override fun apply() {}
}
