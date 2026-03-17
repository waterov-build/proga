package com.enduroplus.companion

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the course editor screen.
 *
 * Exposes the list of checkpoints being edited and handles persistence via
 * [CourseRepository].  The [bleManager] reference is set by the Activity
 * so that "Send to watches" can push the course over BLE.
 */
class CourseViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = CourseRepository(application)

    /** Full list of saved courses (refreshed on load). */
    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses

    /** Checkpoints being edited for the currently open course. */
    private val _checkpoints = MutableStateFlow<List<CourseCheckpoint>>(emptyList())
    val checkpoints: StateFlow<List<CourseCheckpoint>> = _checkpoints

    /** Name of the course being edited. */
    private val _courseName = MutableStateFlow("New Course")
    val courseName: StateFlow<String> = _courseName

    /** Injected by the Activity; used to push the course to connected watches. */
    var bleManager: BleManager? = null

    init {
        loadCourses()
    }

    fun loadCourses() {
        viewModelScope.launch(Dispatchers.IO) {
            _courses.value = repo.loadAll()
        }
    }

    /** Begin editing [course] (or start a new empty course if null). */
    fun openCourse(course: Course?) {
        if (course != null) {
            _courseName.value = course.name
            _checkpoints.value = course.checkpoints.toList()
        } else {
            _courseName.value = "New Course"
            _checkpoints.value = emptyList()
        }
    }

    fun setCourseName(name: String) {
        _courseName.value = name
    }

    fun addCheckpoint(cp: CourseCheckpoint) {
        _checkpoints.value = _checkpoints.value + cp
    }

    fun updateCheckpoint(index: Int, cp: CourseCheckpoint) {
        val list = _checkpoints.value.toMutableList()
        if (index in list.indices) {
            list[index] = cp
            _checkpoints.value = list
        }
    }

    fun deleteCheckpoint(index: Int) {
        val list = _checkpoints.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _checkpoints.value = list
        }
    }

    /** Persist the current course and reload the list. */
    fun saveCourse(onDone: () -> Unit = {}) {
        val course = Course(_courseName.value.trim().ifBlank { "New Course" }, _checkpoints.value)
        viewModelScope.launch(Dispatchers.IO) {
            repo.save(course)
            _courses.value = repo.loadAll()
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun deleteCourse(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.delete(name)
            _courses.value = repo.loadAll()
        }
    }

    /**
     * Send the current checkpoint list to all connected watches via BLE.
     * Returns the number of checkpoints sent, or -1 if bleManager is not set.
     */
    fun sendToWatches(): Int {
        val mgr = bleManager ?: return -1
        val cps = _checkpoints.value
        mgr.sendCheckpointsAll(cps)
        return cps.size
    }
}
