package com.fnphoto.tv.update

object VersionComparator {
    fun isRemoteNewer(remote: String, current: String): Boolean {
        val remoteParts = parse(remote)
        val currentParts = parse(current)
        val maxSize = maxOf(remoteParts.size, currentParts.size)

        for (index in 0 until maxSize) {
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (remotePart != currentPart) {
                return remotePart > currentPart
            }
        }

        return false
    }

    private fun parse(version: String): List<Int> {
        val normalized = version.trim().removePrefix("v").removePrefix("V")
        if (normalized.isBlank()) {
            return listOf(0)
        }

        return normalized
            .split('.')
            .mapNotNull { segment ->
                LEADING_NUMBER.find(segment)?.value?.toIntOrNull()
            }
            .ifEmpty { listOf(0) }
    }

    private val LEADING_NUMBER = Regex("^\\d+")
}
