package org.zarroboogs.maps.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.zarroboogs.maps.R
import org.zarroboogs.maps.data.local.AppDatabase
import org.zarroboogs.maps.data.local.FuelCalculator
import org.zarroboogs.maps.data.model.Profile
import org.zarroboogs.maps.data.model.ProfileRepository
import org.zarroboogs.maps.data.model.VehicleSubType
import kotlin.concurrent.thread

class VehicleOverviewFragment : Fragment() {

    companion object {
        private const val REQUEST_PICK_IMAGE = 1001
        private const val REQUEST_PICK_OVERVIEW_IMAGE = 1002
    }

    private var profiles = mutableListOf<Profile>()
    private var pendingImageUri: Uri? = null
    private var dialogImageNameTv: TextView? = null
    private var isPickingOverviewImage = false

    private lateinit var rv: RecyclerView
    private lateinit var adapter: VehicleOverviewAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_vehicle_overview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rv = view.findViewById(R.id.rv_vehicles)
        rv.layoutManager = LinearLayoutManager(requireContext())

        adapter = VehicleOverviewAdapter(
            onItemClick = { profile ->
                (parentFragment as? HomeFragment)?.showDetail(profile.name)
            },
            onImageClick = {
                isPickingOverviewImage = true
                @Suppress("DEPRECATION")
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivityForResult(intent, REQUEST_PICK_OVERVIEW_IMAGE)
            },
            onAddClick = { showAddProfileDialog() }
        )
        rv.adapter = adapter

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        profiles = ProfileRepository.loadProfiles(requireContext())
        val overviewUri = ProfileRepository.loadOverviewImageUri(requireContext())
        val count = profiles.size

        thread {
            val dao = AppDatabase.getInstance(requireContext()).fuelRecordDao()
            val spent = FuelCalculator.totalSpent(dao.loadAll())
            activity?.runOnUiThread {
                adapter.submitList(profiles, overviewUri, count, spent)
            }
        }
    }

    private fun showAddProfileDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_profile, null)

        val spinnerType = dialogView.findViewById<Spinner>(R.id.spinner_type)
        val spinnerSubtype = dialogView.findViewById<Spinner>(R.id.spinner_subtype)
        val etName = dialogView.findViewById<EditText>(R.id.et_profile_name)
        val etDisplacement = dialogView.findViewById<EditText>(R.id.et_displacement)
        val etPlate = dialogView.findViewById<EditText>(R.id.et_plate)
        val btnImport = dialogView.findViewById<Button>(R.id.btn_import_image)
        dialogImageNameTv = dialogView.findViewById(R.id.tv_image_name)

        pendingImageUri = null

        val types = ProfileRepository.vehicleTypeMap.keys.toList()
        val typeAdapter = ArrayAdapter(
            requireContext(), R.layout.spinner_item_white, types
        )
        typeAdapter.setDropDownViewResource(R.layout.spinner_item_white)
        spinnerType.adapter = typeAdapter

        fun updateSubtypes(typeIndex: Int) {
            val selectedType = types[typeIndex]
            val subtypes = ProfileRepository.vehicleTypeMap[selectedType] ?: emptyList()
            val subtypeAdapter = ArrayAdapter(
                requireContext(), R.layout.spinner_item_white, subtypes
            )
            subtypeAdapter.setDropDownViewResource(R.layout.spinner_item_white)
            spinnerSubtype.adapter = subtypeAdapter
        }
        updateSubtypes(0)

        spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                updateSubtypes(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnImport.setOnClickListener {
            @Suppress("DEPRECATION")
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivityForResult(intent, REQUEST_PICK_IMAGE)
        }

        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("新建配置")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                val displacementStr = etDisplacement.text.toString().trim()
                val plate = etPlate.text.toString().trim()

                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入配置名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (profiles.any { it.name == name }) {
                    Toast.makeText(requireContext(), "配置名称已存在", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val displacement = displacementStr.toIntOrNull()
                if (displacement == null || displacement <= 0) {
                    Toast.makeText(requireContext(), "请输入有效的排量", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val selectedType = types[spinnerType.selectedItemPosition]
                val subtypes = ProfileRepository.vehicleTypeMap[selectedType] ?: emptyList()
                val selectedSubtype = if (subtypes.isNotEmpty()) {
                    subtypes[spinnerSubtype.selectedItemPosition]
                } else {
                    VehicleSubType(selectedType, "UNKNOWN")
                }

                val profile = Profile(
                    name = name,
                    type = selectedType,
                    subType = selectedSubtype,
                    displacement = displacement,
                    plate = plate,
                    imageUri = pendingImageUri
                )
                profiles.add(profile)
                ProfileRepository.saveProfiles(requireContext(), profiles)
                val idx = profiles.size - 1
                ProfileRepository.saveSelectedIndex(requireContext(), idx)
                refresh()
                pendingImageUri = null
                dialogImageNameTv = null
            }
            .setNegativeButton("取消") { _, _ ->
                pendingImageUri = null
                dialogImageNameTv = null
            }
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return

        when (requestCode) {
            REQUEST_PICK_IMAGE -> {
                val uri = data?.data ?: return
                pendingImageUri = uri
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) { }
                dialogImageNameTv?.text = uri.lastPathSegment ?: "已选择图片"
            }
            REQUEST_PICK_OVERVIEW_IMAGE -> {
                isPickingOverviewImage = false
                val uri = data?.data ?: return
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) { }
                ProfileRepository.saveOverviewImageUri(requireContext(), uri)
                refresh()
            }
        }
    }
}
