package net.prestalife.elderblocks

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.FoldingModel
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.min

data class Result(val hash: Int, val lines: Int)

class EditorFileHandler {
    var project: Project
    val settings = ElderBlocksFoldingSettings.instance
    var lastAgeUpdate: Long = 0
    val ages = mutableMapOf<String, MutableMap<Int, Long>>()
    private var scheduledTask: ScheduledFuture<*>? = null

    val foldProcessed = mutableMapOf<String, Boolean>()

    val delaySeconds = 5L

    val log = Logger.getInstance(EditorFileHandler::class.java)

    constructor(project: Project) {
        this.project = project

        // Schedule a periodic task
        scheduledTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            {
                // Your periodic task logic here
                foldOldBlocks()
            },
            0,    // Initial delay
            delaySeconds,    // Period between executions
            TimeUnit.SECONDS  // Time unit
        )
    }

    fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val fileEditor = source.getSelectedEditor(file)
        val editor = getEditorForFile(source, file)
        if (editor != null && fileEditor != null) {
            ApplicationManager.getApplication().runReadAction {
                ages[file.path] = mutableMapOf()
                foldProcessed.remove(file.path)
                val foldingBlocks = getFoldingBlocks(editor)
                // Process folding blocks as needed
                foldingBlocks.forEach { foldRegion ->
                    if (foldRegion.isExpanded) {
                        val (hash) = getFoldingRegionHash(foldRegion)
                        ages[file.path]?.put(hash, 0)
                    }
                }

                // Add folding listener to detect when blocks are unfolded
                (editor as EditorEx).foldingModel.addListener(object : FoldingListener {
                    override fun onFoldRegionStateChange(foldRegion: FoldRegion) {
                        if (settings.reFoldAfterManualUnfold == 0) {
                            return
                        }
                        val filePath = foldRegion.editor.virtualFile?.path
                        if (filePath != file.path || foldProcessed[filePath] != true || !ages.containsKey(filePath)) {
                            return
                        }
                        if (foldRegion.isExpanded) {
                            getFoldRegionParents(foldRegion).forEach { region ->
                                val (hash, lines) = getFoldingRegionHash(foldRegion)
                                if (settings.minBlockLines > 0 && lines < settings.minBlockLines) {
                                    return@forEach
                                }
                                if (!settings.foldTopLevelBlocks && isTopLevelBlock(foldingBlocks, foldRegion)) {
                                    return@forEach
                                }
                                val age = -(settings.reFoldAfterManualUnfold - settings.oldAge).toLong() * 1000
                                ages[filePath]?.put(hash, age)
                                log.debug("Setting age of ${foldRegion.startOffset} to ${foldRegion.endOffset} to ${age / 1000}")
                            }
                        }
                    }

                    override fun onFoldProcessingEnd() {
                        foldProcessed[file.path] = true
                        super.onFoldProcessingEnd()
                    }
                }, fileEditor)

                EditorFactory.getInstance()
                    .eventMulticaster
                    .addCaretListener(object : CaretListener {
                        override fun caretPositionChanged(event: CaretEvent) {
                            val virtualFile = event.editor.virtualFile
                            if (virtualFile == null) {
                                return
                            }
                            val filePath = virtualFile.path
                            if (filePath != file.path) {
                                return
                            }
                            val position = event.editor.caretModel.offset
                            getFoldingBlocksForFile(filePath).forEach { region ->
                                if (!region.isExpanded) {
                                    return@forEach
                                }
                                if (region.startOffset <= position && region.endOffset >= position) {
                                    val (hash) = getFoldingRegionHash(region)
                                    if (ages[filePath]?.containsKey(hash) == true) {
                                        val age =
                                            -(settings.reFoldAfterManualUnfold - settings.oldAge).toLong() * 1000
                                        val currentAge = ages[filePath]?.get(hash) ?: 0L
                                        ages[filePath]?.put(hash, min(currentAge, age))
                                        log.debug("Setting age of ${region.startOffset} to ${region.endOffset} from ${currentAge / 1000} to ${age / 1000}")
                                    }
                                }
                            }

                            println("Caret moved to $position")
                        }
                    }, fileEditor)
            }
        }
    }

    fun fileClosed(file: VirtualFile) {
        ages.entries.removeIf { it.key == file.path }
        foldProcessed.remove(file.path)
    }

    private fun getEditorForFile(fileEditorManager: FileEditorManager, file: VirtualFile): Editor? {
        val fileEditor = fileEditorManager.getSelectedEditor(file)
        return if (fileEditor is TextEditor) {
            fileEditor.editor
        } else null
    }

    private fun getFoldingBlocks(editor: Editor): List<FoldRegion> {
        val foldingModel = editor.foldingModel
        return foldingModel.allFoldRegions.toList()
    }

    private fun foldOldBlocks() {
        log.info("Folding old blocks")
        val now = System.currentTimeMillis()
        if (lastAgeUpdate == 0L) {
            lastAgeUpdate = now
        }

        val diff = now - lastAgeUpdate
        ages.forEach { files ->
            files.value.forEach { age ->
                files.value[age.key] = age.value + diff
            }
        }
        lastAgeUpdate = now

        // get ages older than 3 seconds
        val foldingModelsMap = mutableMapOf<FoldingModel, List<FoldRegion>>()

        ApplicationManager.getApplication().invokeLater({
            run {
                WriteCommandAction.runWriteCommandAction(project) {
                    ages.forEach { age ->
                        val filePath = age.key
                        val foldingModel = getFoldingModel(filePath)
                        if (foldingModel == null) {
                            return@forEach
                        }
                        val foldingBlocks = getFoldingBlocksForFile(filePath)
                        val seenContentKeys = mutableListOf<Int>()
                        foldingBlocks.forEach { foldRegion ->
                            val content = getFoldingRegionContent(foldRegion)
                            val contentHash = content.hashCode()
                            val cursorPosition = getCursorPosition(foldRegion.editor)
                            if (settings.minBlockLines > 0 && content.lines().count() < settings.minBlockLines) {
                                ages[filePath]?.remove(contentHash)
                                return@forEach
                            }
                            if (!settings.foldTopLevelBlocks && isTopLevelBlock(foldingBlocks, foldRegion)) {
                                ages[filePath]?.remove(contentHash)
                                return@forEach
                            }
                            if (!settings.foldFocusedBlocks && cursorPosition != null && cursorPosition > foldRegion.startOffset && cursorPosition < foldRegion.endOffset) {
                                ages[filePath]?.remove(contentHash)
                                return@forEach
                            }
                            seenContentKeys.add(contentHash)

                            // if the block is old and expanded
                            val regionAge = age.value.getOrDefault(
                                contentHash,
                                0L
                            )
                            if (foldRegion.isExpanded &&
                                regionAge > settings.oldAge * 1000
                            ) {
                                val foldingModelRegions = foldingModelsMap.getOrDefault(foldingModel, emptyList())
                                foldingModelsMap.put(foldingModel, foldingModelRegions + foldRegion)
                                ages[filePath]?.remove(contentHash)
                                log.debug("Folding block at ${foldRegion.startOffset} to ${foldRegion.endOffset} with age ${regionAge / 1000} > ${settings.oldAge}")
                            }

                            // if the block does not exist in ages, add it
                            if (!age.value.contains(contentHash) && settings.reFoldAfterEdit != 0) {
                                ages[filePath]?.put(
                                    contentHash,
                                    -(settings.reFoldAfterEdit - settings.oldAge).toLong() * 1000
                                )
                            }
                        }

                        // cleanup
                        ages[filePath]?.entries?.removeIf { !seenContentKeys.contains(it.key) }
                    }

                    foldingModelsMap.forEach { (foldingModel, foldingBlocks) ->
                        foldingModel.runBatchFoldingOperation {
                            foldingBlocks.forEach { foldRegion ->
                                foldRegion.isExpanded = false
                                foldRegion.placeholderText = " \uD83D\uDCA4 "
                            }
                        }
                    }
                }
            }
        }, project.disposed)
    }

    private fun isTopLevelBlock(
        foldingBlocks: List<FoldRegion>,
        foldRegion: FoldRegion
    ): Boolean {
        return foldingBlocks.none { it.startOffset < foldRegion.startOffset && it.endOffset > foldRegion.endOffset }
    }

    private fun getFoldingModel(filePath: String): FoldingModel? {
        val file = VirtualFileManager.getInstance().findFileByUrl("file://$filePath")
        if (file != null) {
            val editor = getEditorForFile(FileEditorManager.getInstance(project), file)
            if (editor != null) {
                return editor.foldingModel
            }
        }
        return null
    }

    private fun getFoldingRegionContent(foldRegion: FoldRegion): String {
        return foldRegion.document.text.substring(foldRegion.startOffset, foldRegion.endOffset)
    }

    private fun getFoldingRegionHash(foldRegion: FoldRegion): Result {
        val content = getFoldingRegionContent(foldRegion)
        return Result(content.hashCode(), content.lines().count())
    }

    private fun getFoldingBlocksForFile(filePath: String): List<FoldRegion> {
        val file = VirtualFileManager.getInstance().findFileByUrl("file://$filePath")
        if (file != null) {
            val editor = getEditorForFile(FileEditorManager.getInstance(project), file)
            if (editor != null) {
                return getFoldingBlocks(editor)
            }
        }
        return emptyList()
    }

    private fun getFoldRegionParents(foldRegion: FoldRegion): List<FoldRegion> {
        val filePath = foldRegion.editor.virtualFile?.path
        if (filePath === null) {
            return listOf();
        }
        val parents = mutableListOf(foldRegion)
        val foldRegions = getFoldingBlocksForFile(filePath)
        foldRegions.filter { it.startOffset < foldRegion.startOffset && it.endOffset > foldRegion.endOffset }
            .forEach { parents.add(it) }
        return parents
    }

    private fun getCursorPosition(editor: Editor): Int {
        return editor.caretModel.offset
    }
}