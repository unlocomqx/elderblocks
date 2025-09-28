package net.prestalife.elderblocks;

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile

class ElderblocksPostStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val fileHandler = EditorFileHandler(project)
        val listener = object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                fileHandler.fileOpened(source, file)
            }

            override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                fileHandler.fileClosed(source, file)
            }
        }

        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            listener
        )

        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFiles.forEach { fileHandler.fileOpened(fileEditorManager, it) }
    }
}