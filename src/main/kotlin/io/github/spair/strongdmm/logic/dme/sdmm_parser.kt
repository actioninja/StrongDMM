package io.github.spair.strongdmm.logic.dme

import com.sun.jna.Library
import com.sun.jna.Native

/**
 * Under the hood StrongDMM uses SpacemanDMM parser (https://github.com/SpaceManiac/SpacemanDMM).
 * Parser wrapped into dynamic lib (sdmmparser.dll) with one function `parseEnv(String): String`.
 * Method accepts env path as string and returns parsed env as json.
 * 'error' string will be returned if exception during parsing occurred.
 *
 * Json contract:
 *  {
 *   "path": "{type_path}",
 *   "vars": [
 *     {
 *       "name": "{var_name}",
 *       "value: "{var_value}"
 *     }
 *   ],
 *   "children: [
 *     ... << object with path, vars and children field
 *   ]
 *  }
 *
 *  Root object contains every #define, constants and all other environment objects in children field.
 */
private interface SDMMParser : Library {
    fun parseEnv(envPath: String): String
}

private object SpacemanDmmParser {
    val api: SDMMParser = Native.load("sdmmparser64", SDMMParser::class.java)
}

fun parseEnvToJson(envPath: String): String {
    return SpacemanDmmParser.api.parseEnv(envPath)
}
