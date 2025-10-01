package net.prestalife.elderblocks

import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.builder.text
import javax.swing.JComponent

class ElderblocksCodeFoldingOptionsProvider : CodeFoldingOptionsProvider {
    private var oldAge: Int = ElderBlocksFoldingSettings.instance.oldAge
    private var reFoldAfterManualUnfold: Int = ElderBlocksFoldingSettings.instance.reFoldAfterManualUnfold
    private var reFoldAfterEdit: Int = ElderBlocksFoldingSettings.instance.reFoldAfterEdit
    private var foldTopLevelBlocks: Boolean = ElderBlocksFoldingSettings.instance.foldTopLevelBlocks
    private var minBlockLines: Int = ElderBlocksFoldingSettings.instance.minBlockLines
    override fun createComponent(): JComponent {
        return panel {
            group("ElderBlocks") {
                row {
                    label ("Delay before folding inactive blocks (seconds)")
                }
                row("All blocks:") {
                    intTextField()
                        .text(oldAge.toString())
                        .onChanged {
                            oldAge = it.text.toIntOrNull() ?: 0
                        }
                        .comment("Delay before folding inactive blocks (default: 60 seconds)")
                }

                row("Manually unfolded blocks:") {
                    intTextField()
                        .text(reFoldAfterManualUnfold.toString())
                        .onChanged {
                            reFoldAfterManualUnfold = it.text.toIntOrNull() ?: 0
                        }
                        .comment("Delay before folding manually unfolded blocks (default: 90 seconds, 0 means never)")
                }

                row("Edited blocks:") {
                    intTextField()
                        .text(reFoldAfterEdit.toString())
                        .onChanged {
                            reFoldAfterEdit = it.text.toIntOrNull() ?: 0
                        }
                        .comment("Delay before folding edited blocks (default: 120 seconds, 0 means never)")
                }

                row(" ") {
                    checkBox("Fold top-level blocks")
                        .selected(foldTopLevelBlocks)
                        .onChanged {
                            foldTopLevelBlocks = it.isSelected
                        }
                }

                row("Minimum lines:") {
                    intTextField()
                        .text(minBlockLines.toString())
                        .onChanged {
                            minBlockLines = it.text.toIntOrNull() ?: 5
                        }
                        .comment("Blocks with fewer lines will not be folded (default: 5, 0 means no minimum)")
                }
            }
        }
    }

    override fun isModified(): Boolean {
        return oldAge != ElderBlocksFoldingSettings.instance.oldAge ||
                reFoldAfterManualUnfold != ElderBlocksFoldingSettings.instance.reFoldAfterManualUnfold ||
                reFoldAfterEdit != ElderBlocksFoldingSettings.instance.reFoldAfterEdit ||
                foldTopLevelBlocks != ElderBlocksFoldingSettings.instance.foldTopLevelBlocks ||
                minBlockLines != ElderBlocksFoldingSettings.instance.minBlockLines
    }

    override fun apply() {
        ElderBlocksFoldingSettings.instance.oldAge = oldAge
        ElderBlocksFoldingSettings.instance.reFoldAfterManualUnfold = reFoldAfterManualUnfold
        ElderBlocksFoldingSettings.instance.reFoldAfterEdit = reFoldAfterEdit
          ElderBlocksFoldingSettings.instance.minBlockLines = minBlockLines
        ElderBlocksFoldingSettings.instance.foldTopLevelBlocks = foldTopLevelBlocks
    }

    override fun reset() {
        oldAge = ElderBlocksFoldingSettings.instance.oldAge
        reFoldAfterManualUnfold = ElderBlocksFoldingSettings.instance.reFoldAfterManualUnfold
        reFoldAfterEdit = ElderBlocksFoldingSettings.instance.reFoldAfterEdit
        foldTopLevelBlocks = ElderBlocksFoldingSettings.instance.foldTopLevelBlocks
        minBlockLines = ElderBlocksFoldingSettings.instance.minBlockLines
    }
}

@State(name = "ElderBlocksFoldingSettings", storages = [(Storage("elderblocks.xml"))])
class ElderBlocksFoldingSettings : PersistentStateComponent<ElderBlocksFoldingSettings.State> {

    data class State(
        var oldAge: Int = 60,
        var reFoldAfterManualUnfold: Int = 90,
        var reFoldAfterEdit: Int = 0,
        var foldTopLevelBlocks: Boolean = false,
        var minBlockLines: Int = 5
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

    var foldTopLevelBlocks: Boolean
        get() = state.foldTopLevelBlocks
        set(value) {
            state.foldTopLevelBlocks = value
        }

    var minBlockLines: Int
        get() = state.minBlockLines
        set(value) {
            state.minBlockLines = value
        }

    companion object {
        val instance: ElderBlocksFoldingSettings
            get() = ApplicationManager.getApplication().getService(ElderBlocksFoldingSettings::class.java)
    }
}