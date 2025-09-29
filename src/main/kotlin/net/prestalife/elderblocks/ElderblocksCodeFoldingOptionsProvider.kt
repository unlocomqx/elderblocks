package net.prestalife.elderblocks;

import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class ElderblocksCodeFoldingOptionsProvider : CodeFoldingOptionsProvider {
    private var ageField: String = ElderBlocksFoldingSettings.instance.oldAge.toString()

    override fun createComponent(): JComponent {
        return panel {
            group("Elder Blocks") {
                row("Age threshold (seconds):") {
                    textField()
                        .bindText(::ageField)
                        .comment("Blocks will be folded after this many seconds of inactivity")
                }
            }
        }
    }

    override fun isModified(): Boolean {
        return ageField != ElderBlocksFoldingSettings.instance.oldAge.toString()
    }

    override fun apply() {
        ElderBlocksFoldingSettings.instance.oldAge = ageField.toIntOrNull() ?: 30
    }

}

@State(name = "ElderBlocksFoldingSettings", storages = [(Storage("elderblocks.xml"))])
class ElderBlocksFoldingSettings : PersistentStateComponent<ElderBlocksFoldingSettings.State> {

    data class State(
        var oldAge: Int = 60,
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