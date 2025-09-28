package net.prestalife.elderblocks

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.FoldingModel
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

        ages.entries.map { ages[it.key] = ages[it.key]!! + now - lastAgeUpdate }
        lastAgeUpdate = now

        // get ages older than 3 seconds
        val oldBlocks = ages.entries.filter { it.value > 5000 }
        val fileBlocksMap = mutableMapOf<String, List<FoldRegion>>()
        val blockHashMap = mutableMapOf<String, Int>()

        val foldingModel = getFoldingModel(project)

        ApplicationManager.getApplication().invokeAndWait {
            run {
                WriteCommandAction.runWriteCommandAction(project) {
                    foldingModel?.runBatchFoldingOperation(Runnable {
                        oldBlocks.forEach {
                            val parts = it.key.split(":")
                            val filePath = parts[0]
                            val hash = parts[1].toInt()
                            var found = false
                            val foldingBlocks =
                                fileBlocksMap.getOrDefault(filePath, getFoldingBlocksForFile(filePath))

                            foldingBlocks.forEach { foldRegion ->
                                val blockKey = filePath + ":" + foldRegion.startOffset + ":" + foldRegion.endOffset
                                val contentHash =
                                    blockHashMap.getOrDefault(blockKey, getFoldingRegionHash(foldRegion))

                                if (contentHash == hash) {
                                    if (foldRegion.isExpanded) {
                                        foldRegion.isExpanded = false
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
                    })
                }
            }
        }
    }

    private fun getFoldingModel(project: Project): FoldingModel? {
        return FileEditorManager.getInstance(project).selectedTextEditor?.foldingModel
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