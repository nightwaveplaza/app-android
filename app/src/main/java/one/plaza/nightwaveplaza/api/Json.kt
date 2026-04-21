package one.plaza.nightwaveplaza.api

import kotlinx.serialization.json.Json

object Json {
    // Default json config
    val mapper = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }
}