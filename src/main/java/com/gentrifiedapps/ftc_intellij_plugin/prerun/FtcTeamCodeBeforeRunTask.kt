package com.gentrifiedapps.ftc_intellij_plugin.prerun

import com.intellij.execution.BeforeRunTask
import com.intellij.openapi.util.Key

class FtcTeamCodeBeforeRunTask : BeforeRunTask<FtcTeamCodeBeforeRunTask>(ID) {
    companion object {
        val ID: Key<FtcTeamCodeBeforeRunTask> =
            Key.create("ftc.beforeRun.teamcode.guard")
    }
}