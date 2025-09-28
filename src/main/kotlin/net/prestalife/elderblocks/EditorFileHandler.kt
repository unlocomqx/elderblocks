package net.prestalife.elderblocks

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class EditorFileHandler {
    var project: Project
    val ages = mutableMapOf<Int, Int>()

    constructor(project: Project) {
        this.project = project
    }

    fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val editor = getEditorForFile(source, file)
        if (editor != null) {
            val foldingBlocks = getFoldingBlocks(editor)
            // Process folding blocks as needed
            processFoldingBlocks(foldingBlocks, file)
        }
    }

    fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        // Clean up any resources related to this file if needed
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
            // set the age to 0
            ages[hash] = 0
        }
    }

    // Alternative method to get folding blocks when you have direct access to an editor
    fun getFoldingBlocksForCurrentEditor(): List<FoldRegion>? {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val selectedEditor = fileEditorManager.selectedTextEditor

        return selectedEditor?.let { editor ->
            editor.foldingModel.allFoldRegions.toList()
        }
    }

    // Method to get folding blocks for a specific file by path
    fun getFoldingBlocksForFile(filePath: String): List<FoldRegion>? {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val virtualFile = project.baseDir?.findFileByRelativePath(filePath)

        return virtualFile?.let { file ->
            val editor = getEditorForFile(fileEditorManager, file)
            editor?.let { getFoldingBlocks(it) }
        }
    }
}