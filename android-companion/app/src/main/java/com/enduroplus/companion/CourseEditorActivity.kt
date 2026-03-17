package com.enduroplus.companion

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.enduroplus.companion.databinding.ActivityCourseEditorBinding
import com.enduroplus.companion.databinding.DialogCheckpointBinding
import kotlinx.coroutines.launch

/**
 * Course editor — lets race operators define a list of checkpoints (name,
 * latitude, longitude, par time) and push the course to all connected watches
 * with a single tap.
 *
 * The [BleManager] is retrieved from [EnduroPlusApplication.bleManager] which
 * is initialised in [EnduroPlusApplication.onCreate] before any Activity starts.
 */
class CourseEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCourseEditorBinding
    private val viewModel: CourseViewModel by viewModels()
    private val adapter = CheckpointAdapter(
        onEdit   = { index, cp -> showCheckpointDialog(index, cp) },
        onDelete = { index     -> viewModel.deleteCheckpoint(index) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCourseEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = getString(R.string.course_editor_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Inject BleManager from Application singleton
        viewModel.bleManager = EnduroPlusApplication.instance?.bleManager

        // RecyclerView
        binding.checkpointsRecycler.layoutManager = LinearLayoutManager(this)
        binding.checkpointsRecycler.adapter = adapter

        // Keep the course name field in sync with ViewModel
        binding.courseNameEdit.doAfterTextChanged { text ->
            viewModel.setCourseName(text.toString())
        }

        // Observe checkpoints
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.checkpoints.collect { cps ->
                    adapter.submitList(cps)
                    binding.noCheckpointsLabel.visibility =
                        if (cps.isEmpty()) View.VISIBLE else View.GONE
                    binding.checkpointsRecycler.visibility =
                        if (cps.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }

        // Observe course name (initial value from ViewModel → update field)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.courseName.collect { name ->
                    if (binding.courseNameEdit.text.toString() != name) {
                        binding.courseNameEdit.setText(name)
                    }
                }
            }
        }

        // Buttons
        binding.addCheckpointButton.setOnClickListener {
            showCheckpointDialog(null, null)
        }

        binding.sendToWatchesButton.setOnClickListener {
            val sent = viewModel.sendToWatches()
            when {
                sent < 0 -> Toast.makeText(this,
                    R.string.no_watches_connected, Toast.LENGTH_SHORT).show()
                sent == 0 -> Toast.makeText(this,
                    R.string.no_checkpoints, Toast.LENGTH_SHORT).show()
                else -> Toast.makeText(this,
                    getString(R.string.sent_to_watches, sent), Toast.LENGTH_SHORT).show()
            }
        }

        binding.saveCourseButton.setOnClickListener {
            viewModel.saveCourse {
                Toast.makeText(this, R.string.course_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ---------- Checkpoint dialog ----------

    private fun showCheckpointDialog(editIndex: Int?, existing: CourseCheckpoint?) {
        val dialogBinding = DialogCheckpointBinding.inflate(layoutInflater)

        // Pre-fill if editing
        existing?.let { cp ->
            dialogBinding.nameEdit.setText(cp.name)
            dialogBinding.latEdit.setText(cp.lat.toString())
            dialogBinding.lonEdit.setText(cp.lon.toString())
            dialogBinding.parEdit.setText(cp.parSec.toString())
        }

        val title = if (existing == null)
            getString(R.string.add_checkpoint_title)
        else
            getString(R.string.edit_checkpoint_title)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name   = dialogBinding.nameEdit.text.toString().trim()
                val latStr = dialogBinding.latEdit.text.toString().trim()
                val lonStr = dialogBinding.lonEdit.text.toString().trim()
                val parStr = dialogBinding.parEdit.text.toString().trim()

                val lat = latStr.toDoubleOrNull()
                val lon = lonStr.toDoubleOrNull()

                if (name.isBlank() || lat == null || lon == null
                    || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                    Toast.makeText(this, R.string.invalid_coordinates,
                        Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val parSec = parStr.toIntOrNull()?.coerceAtLeast(1) ?: 120

                val cp = CourseCheckpoint(name, lat, lon, parSec)
                if (editIndex != null) {
                    viewModel.updateCheckpoint(editIndex, cp)
                } else {
                    viewModel.addCheckpoint(cp)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

// ---------- RecyclerView adapter ----------

private class CheckpointAdapter(
    private val onEdit:   (index: Int, cp: CourseCheckpoint) -> Unit,
    private val onDelete: (index: Int) -> Unit,
) : ListAdapter<CourseCheckpoint, CheckpointAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_checkpoint, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameText:   TextView    = view.findViewById(R.id.cpNameText)
        private val coordsText: TextView    = view.findViewById(R.id.cpCoordsText)
        private val parText:    TextView    = view.findViewById(R.id.cpParText)
        private val editBtn:    ImageButton = view.findViewById(R.id.editButton)
        private val deleteBtn:  ImageButton = view.findViewById(R.id.deleteButton)

        fun bind(cp: CourseCheckpoint, index: Int) {
            nameText.text   = "#${index + 1}  ${cp.name}"
            coordsText.text = "%.6f, %.6f".format(cp.lat, cp.lon)
            parText.text    = "Par: ${cp.parSec}s"
            editBtn.setOnClickListener   { onEdit(index, cp) }
            deleteBtn.setOnClickListener { onDelete(index) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CourseCheckpoint>() {
            override fun areItemsTheSame(a: CourseCheckpoint, b: CourseCheckpoint) =
                a.name == b.name
            override fun areContentsTheSame(a: CourseCheckpoint, b: CourseCheckpoint) =
                a == b
        }
    }
}
