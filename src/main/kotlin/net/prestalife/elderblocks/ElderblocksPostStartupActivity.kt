package net.prestalife.elderblocks;

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VirtualFile

class ElderblocksPostStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        val fileHandler = EditorFileHandler(project)
        val listener = object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                fileHandler.fileOpened(source,file)
            }

            override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                fileHandler.fileClosed(source,file)
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