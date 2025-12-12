package de.findusl.homebox.client

fun TreeItem.flatten(): List<TreeItem> = listOf(this) + children.flatMap { it.flatten() }
