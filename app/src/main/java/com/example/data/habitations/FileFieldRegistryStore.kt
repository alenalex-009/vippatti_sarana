package com.example.data.habitations

import com.example.data.model.SafeZone
import java.io.File

/**
 * File-backed field registry (atomic tmp+rename writes, same discipline as
 * the disaster/news caches). One JSON file per record type inside the app's
 * private cache directory; corrupt files decode to empty + a stated reason
 * rather than crashing or silently losing everything.
 */
class FileFieldRegistryStore(dir: File) : FieldRegistryStore {

  private val registryDir: File = dir.apply { mkdirs() }
  private val sheltersFile = File(registryDir, "field_shelters.json")
  private val habitationsFile = File(registryDir, "field_habitations.json")

  override fun loadShelters(): List<SafeZone> =
    FieldRegistryJson.decodeShelters(readOrNull(sheltersFile)).records

  override fun saveShelters(zones: List<SafeZone>) {
    writeAtomically(sheltersFile, FieldRegistryJson.encodeShelters(zones))
  }

  override fun loadHabitations(): List<Habitation> =
    FieldRegistryJson.decodeHabitations(readOrNull(habitationsFile)).records

  override fun saveHabitations(habitations: List<Habitation>) {
    writeAtomically(habitationsFile, FieldRegistryJson.encodeHabitations(habitations))
  }

  /** Rejection notes from the last decode of either file (for honest display). */
  fun lastRejections(): List<String> = buildList {
    addAll(FieldRegistryJson.decodeShelters(readOrNull(sheltersFile)).rejected)
    addAll(FieldRegistryJson.decodeHabitations(readOrNull(habitationsFile)).rejected)
  }

  private fun readOrNull(file: File): String? =
    if (file.isFile) try { file.readText() } catch (_: Exception) { null } else null

  private fun writeAtomically(file: File, content: String) {
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.writeText(content)
    if (file.exists()) file.delete()
    tmp.renameTo(file)
  }
}
