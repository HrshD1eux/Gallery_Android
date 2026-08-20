package com.HrshD1eux.Gallery.core.util

import java.util.Locale

object FormatUtils {

    /**
     * Formats file size according to requirements:
     * - If < 1 MB: shows in KB (e.g. "512.4 KB" or "84 KB")
     * - If >= 1 MB and < 1024 MB (1 GB): shows in MB (e.g. "4.52 MB" or "128.00 MB")
     * - If >= 1024 MB (1 GB): shows in GB (e.g. "1.24 GB")
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1.0 -> String.format(Locale.getDefault(), "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.getDefault(), "%.2f MB", mb)
            kb >= 1.0 -> {
                if (kb == kb.toLong().toDouble()) {
                    "${kb.toLong()} KB"
                } else {
                    String.format(Locale.getDefault(), "%.1f KB", kb)
                }
            }
            else -> "$bytes B"
        }
    }
}
