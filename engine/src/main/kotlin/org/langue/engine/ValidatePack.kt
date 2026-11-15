package org.langue.engine

import java.io.File
import java.nio.file.Files
import kotlin.system.exitProcess

/** `./gradlew :engine:run --args=/absolute/path/pack.zip` */
fun main(args: Array<String>) {
    if (args.size != 1) {
        System.err.println("Usage : validate-pack <pack.zip>")
        exitProcess(2)
    }
    val temp = Files.createTempDirectory("validate-language-pack-").toFile()
    val result = try {
        val pack = File(args.single()).inputStream().use { PackArchive.extract(it, File(temp, "validated")) }
        println("Pack valide : ${pack.manifest.id}, version ${pack.manifest.version}, ${pack.categories.size} catégories, ${pack.entries.size} éléments.")
        0
    } catch (failure: Exception) {
        System.err.println("Pack refusé : ${failure.message}")
        1
    } finally {
        temp.deleteRecursively()
    }
    if (result != 0) exitProcess(result)
}
