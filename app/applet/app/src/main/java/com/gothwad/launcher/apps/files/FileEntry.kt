package com.gothwad.launcher.apps.files

import java.io.File

data class FileEntry(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val sizeString: String,
    val lastModifiedString: String,
    val extension: String
)
