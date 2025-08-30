package com.gentrifiedapps.ftc_intellij_plugin.prerun

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import javax.swing.Icon

class FtcLintConfigurationType : ConfigurationType {
    override fun getDisplayName(): String = "FTC Lint Check"
    override fun getConfigurationTypeDescription(): String = "Runs FTC Linter checks and fails on issues"
    override fun getIcon(): Icon = AllIcons.RunConfigurations.Application
    override fun getId(): String = "FTC_LINT_RUN_CONFIG"

    private val factory = object : ConfigurationFactory(this) {
        override fun createTemplateConfiguration(project: Project): RunConfiguration {
            return FtcLintRunConfiguration(project, this, displayName)
        }
        override fun getId(): String = "FTC_LINT_FACTORY"
    }

    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(factory)
}
