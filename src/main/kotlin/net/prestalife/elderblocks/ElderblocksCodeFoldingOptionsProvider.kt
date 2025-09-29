package net.prestalife.elderblocks;

import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.BeanConfigurable

class ElderblocksCodeFoldingOptionsProvider :
    BeanConfigurable<ElderBlocksFoldingSettings>(ElderBlocksFoldingSettings.instance), CodeFoldingOptionsProvider {
    init {
        title = "Unused Blocks"
        checkBox(
            "A code block is considered unused if it has not been used for more than:",
            ElderBlocksFoldingSettings.instance::oldAge,
        ) {
            ElderBlocksFoldingSettings.instance.oldAge = it
        }

    }
}

@State(name = "ElderBlocksFoldingSettings", storages = [(Storage("elderblocks.xml"))])
class ElderBlocksFoldingSettings : PersistentStateComponent<ElderBlocksFoldingSettings.State> {

    data class State(
        var oldAge: Int = 30,
    )

    private var state = State()

    override fun getState(): State {
        return state
    }

    override fun loadState(state: State) {
        this.state = state
    }

    // State mappings
    var oldAge: Int
        get() = state.oldAge
        set(value) {
            state.oldAge = value
        }

    companion object {
        val instance: ElderBlocksFoldingSettings
            get() = ApplicationManager.getApplication().getService(ElderBlocksFoldingSettings::class.java)
    }
}