package com.khushu.store

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Pure JSON codec + schema-migration seam for [SettingsSnapshot] — no
 * DataStore dependency here, so alternative persistence bindings (files,
 * SQLDelight, iOS NSUserDefaults) can reuse it unchanged.
 *
 * Decode leniency (forward/backward compatibility):
 * - unknown keys are ignored (file written by a newer release),
 * - missing fields fall back to field defaults (file from an older release),
 * - unknown enum values coerce to the field's default.
 */
class SettingsCodec {

    val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun encode(snapshot: SettingsSnapshot): ByteArray =
        json.encodeToString(snapshot).encodeToByteArray()

    /**
     * @throws SerializationException when [bytes] is not a valid settings file.
     */
    fun decode(bytes: ByteArray): SettingsSnapshot =
        migrate(json.decodeFromString(bytes.decodeToString()))

    private fun migrate(raw: SettingsSnapshot): SettingsSnapshot = when (raw.schemaVersion) {
        SETTINGS_SCHEMA_VERSION -> raw
        // Future schemas land their backfill/coercion branches here; decoding
        // is lenient enough that unchanged fields survive either direction.
        else -> raw
    }
}
