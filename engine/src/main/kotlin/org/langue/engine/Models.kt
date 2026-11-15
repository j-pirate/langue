package org.langue.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The schema version governs structural compatibility; version governs a particular pack update. */
@Serializable
data class Language(val tag: String, val name: String, val direction: String = "auto")

@Serializable
data class Manifest(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val version: Int,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val description: String? = null,
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val targetName: String? = null,
    val icon: String? = null,
    val order: Int = 0,
)

@Serializable
data class Entry(
    val id: String,
    val categoryId: String,
    val source: String,
    val target: String,
    val alternatives: List<String> = emptyList(),
    val transliteration: String? = null,
    val audio: String? = null,
)

@Serializable
data class LanguagePack(val manifest: Manifest, val categories: List<Category>, val entries: List<Entry>)

object PackJson {
    val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = false
        isLenient = false
        explicitNulls = false
    }
}

class PackException(message: String, cause: Throwable? = null) : Exception(message, cause)
