package com.enduroplus.companion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A named course consisting of an ordered list of checkpoints with par times.
 *
 * @param name        Display name of the course (e.g. "Round 1 – Forest")
 * @param checkpoints Ordered list of checkpoints; each carries its own par time
 */
data class Course(
    val name: String,
    val checkpoints: List<CourseCheckpoint>,
)

/**
 * Persists and retrieves [Course] objects as JSON files in internal storage.
 *
 * One JSON file per course, stored in `<filesDir>/courses/<name>.json`.
 * File names are sanitised so they are safe on any filesystem.
 *
 * JSON structure:
 * ```json
 * {
 *   "name": "Round 1",
 *   "checkpoints": [
 *     { "name": "CP1-Start", "lat": 55.751244, "lon": 37.618423, "parSec": 120 },
 *     ...
 *   ]
 * }
 * ```
 */
class CourseRepository(context: Context) {

    private val coursesDir = File(context.filesDir, "courses").also { it.mkdirs() }

    /** Returns all saved courses, sorted alphabetically by name. */
    fun loadAll(): List<Course> =
        coursesDir.listFiles { f -> f.extension == "json" }
            .orEmpty()
            .mapNotNull { runCatching { fromJson(it.readText()) }.getOrNull() }
            .sortedBy { it.name }

    /** Persists [course], overwriting any existing course with the same name. */
    fun save(course: Course) {
        File(coursesDir, safeName(course.name) + ".json")
            .writeText(toJson(course))
    }

    /** Deletes the course with the given [name]. Returns true if deleted. */
    fun delete(name: String): Boolean =
        File(coursesDir, safeName(name) + ".json").delete()

    // ---------- JSON helpers ----------

    private fun toJson(course: Course): String {
        val cpsArray = JSONArray()
        for (cp in course.checkpoints) {
            cpsArray.put(JSONObject().apply {
                put("name",   cp.name)
                put("lat",    cp.lat)
                put("lon",    cp.lon)
                put("parSec", cp.parSec)
            })
        }
        return JSONObject()
            .put("name", course.name)
            .put("checkpoints", cpsArray)
            .toString(2)
    }

    private fun fromJson(json: String): Course {
        val obj  = JSONObject(json)
        val name = obj.getString("name")
        val cpsArray = obj.getJSONArray("checkpoints")
        val checkpoints = (0 until cpsArray.length()).map { i ->
            val cp = cpsArray.getJSONObject(i)
            CourseCheckpoint(
                name   = cp.getString("name"),
                lat    = cp.getDouble("lat"),
                lon    = cp.getDouble("lon"),
                parSec = cp.optInt("parSec", 120),
            )
        }
        return Course(name, checkpoints)
    }

    /** Strips characters that are unsafe in file names.
     *  Spaces are mapped to underscores; the result is capped at 64 characters
     *  (well within the 255-byte ext4/APFS limit and safe for any common FS).
     */
    private fun safeName(name: String): String =
        name.replace(' ', '_')
            .replace(Regex("[^A-Za-z0-9_\\-.]+"), "_")
            .trim('_', '-', '.')
            .take(64)
}
