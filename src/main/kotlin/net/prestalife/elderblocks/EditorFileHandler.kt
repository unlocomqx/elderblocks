package net.prestalife.elderblocks

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.FoldingModel
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


class EditorFileHandler {
    var project: Project
    var lastAgeUpdate: Long = 0
    val ages = mutableMapOf<String, Long>()
    private var scheduledTask: ScheduledFuture<*>? = null

    val foldingModelsWithListener = mutableListOf<FoldingModel>()

    constructor(project: Project) {
        this.project = project

        // Schedule a periodic task
        scheduledTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            {
                // Your periodic task logic here
                foldOldBlocks()
            },
            0,    // Initial delay
            5,    // Period between executions
            TimeUnit.SECONDS  // Time unit
        )
    }

    fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val editor = getEditorForFile(source, file)
        if (editor != null) {
            if (!foldingModelsWithListener.contains(editor.foldingModel)) {
                // Add folding listener to detect when blocks are unfolded
                (editor as EditorEx).foldingModel.addListener(object : FoldingListener {
                    override fun onFoldRegionStateChange(foldRegion: FoldRegion) {
                        if (foldRegion.isExpanded) {
                            // Block was unfolded, reset its age
                            val content =
                                foldRegion.document.text.substring(foldRegion.startOffset, foldRegion.endOffset)
                            val hash = content.hashCode()
                            val key = file.path + ":" + hash.toString()
                            ages[key] = -5000
                            println("Block unfolded in ${file.name}, age reset for key: $key")
                        }
                    }
                }, project)
                foldingModelsWithListener.add(editor.foldingModel)
            }

            ApplicationManager.getApplication().runReadAction(Runnable {
                val foldingBlocks = getFoldingBlocks(editor)
                // Process folding blocks as needed
                processFoldingBlocks(foldingBlocks, file)
            })
        }
    }

    fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        ages.entries.removeIf { it.key.startsWith(file.path) }
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

    private fun processFoldingBlocks(foldingBlocks: List<FoldRegion>, file: VirtualFile) {
        println("File: ${file.name}")
        println("Found ${foldingBlocks.size} folding blocks:")

        foldingBlocks.forEach { foldRegion ->
            val content = foldRegion.document.text.substring(foldRegion.startOffset, foldRegion.endOffset)
            val hash = content.hashCode()
            val key: String = file.path + ":" + hash.toString()
            // set the age to 0
            ages[key] = 0
        }
    }

    private fun foldOldBlocks() {
        val now = System.currentTimeMillis()
        if (lastAgeUpdate == 0L) {
            lastAgeUpdate = now
        }

        val diff = now - lastAgeUpdate
        ages.entries.map { ages[it.key] = ages[it.key]!! + diff }
        lastAgeUpdate = now

        // get ages older than 3 seconds
        val oldBlocks = ages.entries.filter { it.value > 5000 }
        val fileBlocksMap = mutableMapOf<String, List<FoldRegion>>()
        val blockHashMap = mutableMapOf<String, Int>()
        val foldingModelsMap = mutableMapOf<FoldingModel, List<FoldRegion>>()

        ApplicationManager.getApplication().invokeAndWait {
            run {
                WriteCommandAction.runWriteCommandAction(project) {
                    oldBlocks.forEach {
                        val parts = it.key.split(":")
                        val filePath = parts[0]
                        val hash = parts[1].toInt()
                        var found = false
                        val foldingModel = getFoldingModel(filePath)
                        if (foldingModel == null) {
                            return@forEach
                        }
                        val foldingBlocks =
                            fileBlocksMap.getOrDefault(filePath, getFoldingBlocksForFile(filePath))

                        foldingBlocks.forEach { foldRegion ->
                            val blockKey = filePath + ":" + foldRegion.startOffset + ":" + foldRegion.endOffset
                            val contentHash =
                                blockHashMap.getOrDefault(blockKey, getFoldingRegionHash(foldRegion))

                            if (contentHash == hash) {
                                if (foldRegion.isExpanded) {
                                    val foldingModelRegions = foldingModelsMap.getOrDefault(foldingModel, emptyList())
                                    foldingModelsMap.put(foldingModel, foldingModelRegions + foldRegion)
                                }
                                ages.remove(it.key)
                                found = true
                                return@forEach
                            }
                        }
                        if (!found) {
                            // cleanup
                            ages.remove(it.key)
                        }
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
        }
    }

    private fun getFoldingModel(filePath: String): FoldingModel? {
        val file = VirtualFileManager.getInstance().findFileByUrl("file://$filePath");
        if (file != null) {
            val editor = getEditorForFile(FileEditorManager.getInstance(project), file)
            if (editor != null) {
                return editor.foldingModel
            }
        }
        return null
    }

    private fun getFoldingRegionHash(foldRegion: FoldRegion): Int {
        val content =
            foldRegion.document.text.substring(foldRegion.startOffset, foldRegion.endOffset)
        return content.hashCode()
    }

    private fun getFoldingBlocksForFile(filePath: String): List<FoldRegion> {
        val file = VirtualFileManager.getInstance().findFileByUrl("file://$filePath");
        if (file != null) {
            val editor = getEditorForFile(FileEditorManager.getInstance(project), file)
            if (editor != null) {
                return getFoldingBlocks(editor)
            }
        }
        return emptyList()
    }

    // Call this method to clean up resources when the handler is no longer needed
    fun dispose() {
        scheduledTask?.cancel(true)
    }
}