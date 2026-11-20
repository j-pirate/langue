package org.langue.engine

import java.util.Locale

object PackValidator {
    private val identifier = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    private val languageTag = Regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")

    fun validate(pack: LanguagePack) {
        requirePack(pack.manifest.schemaVersion == 1, "Version de schéma non prise en charge.", FailureReason.INCOMPATIBLE_PACK)
        requirePack(identifier.matches(pack.manifest.id), "Identifiant de pack invalide.")
        text(pack.manifest.name, "Nom du pack")
        requirePack(pack.manifest.version >= 1, "La version du pack doit être positive.", FailureReason.INVALID_VERSION)
        listOf(pack.manifest.sourceLanguage, pack.manifest.targetLanguage).forEach {
            requirePack(languageTag.matches(it.tag), "Code de langue BCP 47 invalide.", FailureReason.INCOMPATIBLE_PACK)
            text(it.name, "Nom de langue")
            requirePack(it.direction in setOf("auto", "ltr", "rtl"), "Direction de langue invalide.", FailureReason.INCOMPATIBLE_PACK)
        }
        pack.manifest.description?.let { text(it, "Description", 10_000) }
        requirePack(pack.categories.size <= 5_000, "Trop de catégories.")
        requirePack(pack.entries.size <= 50_000, "Trop d’éléments.")
        requirePack(pack.categories.map { it.id }.toSet().size == pack.categories.size, "Identifiants de catégorie dupliqués.")
        requirePack(pack.entries.map { it.id }.toSet().size == pack.entries.size, "Identifiants d’élément dupliqués.")
        val categoryIds = pack.categories.map { it.id }.toSet()
        pack.categories.forEach {
            requirePack(identifier.matches(it.id), "Identifiant de catégorie invalide.")
            text(it.name, "Nom de catégorie")
            it.targetName?.let { name -> text(name, "Nom de catégorie cible") }
            it.icon?.let { icon -> text(icon, "Icône", 32) }
            requirePack(it.order >= 0, "Ordre de catégorie négatif.")
        }
        pack.entries.forEach {
            requirePack(identifier.matches(it.id), "Identifiant d’élément invalide.")
            requirePack(it.categoryId in categoryIds, "Une catégorie référencée est introuvable.", FailureReason.UNKNOWN_CATEGORY)
            text(it.source, "Texte source")
            text(it.target, "Texte cible")
            requirePack(it.alternatives.size <= 100, "Trop de variantes pour un élément.")
            it.alternatives.forEach { variant -> text(variant, "Variante") }
            it.transliteration?.let { value -> text(value, "Translittération") }
            it.audio?.let { path -> requirePack(validAudioPath(path), "Référence audio invalide.") }
        }
    }

    fun validAudioPath(path: String): Boolean =
        path.startsWith("audio/") && safeArchivePath(path) &&
            path.substringAfterLast('.').lowercase(Locale.ROOT) in setOf("mp3", "m4a", "aac", "wav", "ogg", "flac")

    internal fun safeArchivePath(path: String): Boolean = path.isNotEmpty() && path.length <= 240 &&
        !path.startsWith('/') && !path.contains('\\') && !path.contains(':') &&
        path.none { it.code < 32 || it.code == 127 } &&
        path.split('/').all { it.isNotBlank() && it != "." && it != ".." }

    private fun text(value: String, field: String, max: Int = 2_000) {
        requirePack(value.isNotBlank() && value.length <= max && value.none { it == '\u0000' }, "$field vide ou trop long.")
    }

    internal fun requirePack(valid: Boolean, message: String, reason: FailureReason = FailureReason.INVALID_CONTENT) {
        if (!valid) throw PackException(reason, message)
    }
}
