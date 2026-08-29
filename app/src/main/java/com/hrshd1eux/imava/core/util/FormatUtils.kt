package com.hrshd1eux.imava.core.util

import java.util.Locale

object FormatUtils {

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
