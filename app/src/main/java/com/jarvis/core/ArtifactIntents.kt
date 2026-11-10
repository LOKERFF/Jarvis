package com.jarvis.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Opens / shares an artifact that lives inside the app's private storage.
 *
 * The app never asks for MANAGE_EXTERNAL_STORAGE: every shared file goes through
 * the app's own FileProvider, which grants the receiving app a temporary read
 * permission for exactly one file.
 */
object ArtifactIntents {

    private const val AUTHORITY_SUFFIX = ".fileprovider"

    fun uriFor(context: Context, path: String): Uri? {
        val file = File(path)
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        }.getOrNull()
    }

    /** Returns false when no installed app can open this type. */
    fun open(context: Context, path: String, mime: String): Boolean {
        val uri = uriFor(context, path) ?: return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun share(context: Context, path: String, mime: String): Boolean {
        val uri = uriFor(context, path) ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share file").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }
}
