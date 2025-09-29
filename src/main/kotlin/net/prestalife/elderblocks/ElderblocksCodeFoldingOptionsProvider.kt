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
    private var reFoldAfterManualUnfoldField: String = ElderBlocksFoldingSettings.instance.reFoldAfterManualUnfold.toString()
    private var reFoldAfterEditField: String = ElderBlocksFoldingSettings.instance.reFoldAfterEdit.toString()

    override fun createComponent(): JComponent {
        return panel {
            group("Elder Blocks") {
                row("Age threshold (seconds):") {
                    textField()
                        .bindText(::ageField)
                        .comment("Blocks will be folded after this many seconds of inactivity (default: 60 seconds)")
                }

                row("Fold blocks that I manually unfolded after (seconds):") {
                    textField()
                        .bindText(::reFoldAfterManualUnfoldField)
                        .comment("Blocks will be folded after this many seconds of inactivity (default: 90 seconds, 0 means never)")
                }

                row("Fold blocks that I edited after (seconds):") {
                    textField()
                        .bindText(::reFoldAfterEditField)
                        .comment("Blocks will be folded after this many seconds of inactivity (default: 120 seconds, 0 means never)")
                }
            }
        }
    }

    override fun isModified(): Boolean {
        return ageField != ElderBlocksFoldingSettings.instance.oldAge.toString() ||
               reFoldAfterManualUnfoldField != ElderBlocksFoldingSettings.instance.reFoldAfterManualUnfold.toString() ||
               reFoldAfterEditField != ElderBlocksFoldingSettings.instance.reFoldAfterEdit.toString()
    }

    override fun apply() {
        ElderBlocksFoldingSettings.instance.oldAge = ageField.toIntOrNull() ?: 60
        ElderBlocksFoldingSettings.instance.reFoldAfterManualUnfold = reFoldAfterManualUnfoldField.toIntOrNull() ?: 90
        ElderBlocksFoldingSettings.instance.reFoldAfterEdit = reFoldAfterEditField.toIntOrNull() ?: 0
    }

    override fun reset() {
        ageField = ElderBlocksFoldingSettings.instance.oldAge.toString()
        reFoldAfterManualUnfoldField = ElderBlocksFoldingSettings.instance.reFoldAfterManualUnfold.toString()
        reFoldAfterEditField = ElderBlocksFoldingSettings.instance.reFoldAfterEdit.toString()
    }
}

@State(name = "ElderBlocksFoldingSettings", storages = [(Storage("elderblocks.xml"))])
class ElderBlocksFoldingSettings : PersistentStateComponent<ElderBlocksFoldingSettings.State> {

    data class State(
        var oldAge: Int = 60,
        var reFoldAfterManualUnfold: Int = 90,
        var reFoldAfterEdit: Int = 0
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

    var reFoldAfterManualUnfold: Int
        get() = state.reFoldAfterManualUnfold
        set(value) {
            state.reFoldAfterManualUnfold = value
        }

    var reFoldAfterEdit: Int
        get() = state.reFoldAfterEdit
        set(value) {
            state.reFoldAfterEdit = value
        }

    companion object {
        val instance: ElderBlocksFoldingSettings
            get() = ApplicationManager.getApplication().getService(ElderBlocksFoldingSettings::class.java)
    }
}