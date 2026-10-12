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
import com.amap.api.maps.AMap
import com.amap.api.maps.MapView
import org.zarroboogs.maps.R
import org.zarroboogs.maps.data.local.AppDatabase
import org.zarroboogs.maps.data.local.FuelCalculator
import org.zarroboogs.maps.data.model.Profile
import org.zarroboogs.maps.data.model.ProfileRepository
import org.zarroboogs.maps.data.model.VehicleSubType
import org.zarroboogs.maps.ui.fuel.DialogAddFuel
import org.zarroboogs.maps.ui.fuel.FuelRecordBottomSheet
import org.zarroboogs.maps.utils.SettingUtils
import kotlin.concurrent.thread

class VehicleDetailFragment : Fragment() {

    companion object {
        private const val ARG_PROFILE_NAME = "profile_name"
        private const val REQUEST_PICK_IMAGE = 1001

        fun newInstance(profileName: String) = VehicleDetailFragment().apply {
            arguments = Bundle().apply { putString(ARG_PROFILE_NAME, profileName) }
        }
    }

    private var currentProfile: Profile? = null
    private var pendingImageUri: Uri? = null
    private var dialogImageNameTv: TextView? = null
    private var isEditMode = false
    private var editingProfileName: String? = null

    private lateinit var ivVehicleIcon: ImageView
    private lateinit var gridInfo: GridLayout
    private lateinit var fuelSummaryCard: View
    private lateinit var tvAvgFuel: TextView
    private lateinit var tvAvgFuelUnit: TextView
    private lateinit var tvAvgCost: TextView
    private lateinit var tvAvgCostUnit: TextView
    private lateinit var tvTotalSpent: TextView
    private lateinit var tvTotalSpentUnit: TextView
    private lateinit var mapVehicleLocation: MapView
    private lateinit var btnDelete: View
    private lateinit var btnHome: View
    private lateinit var btnEdit: View
    private lateinit var btnAddFuel: View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_vehicle_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ivVehicleIcon = view.findViewById(R.id.iv_vehicle_icon)
        gridInfo = view.findViewById(R.id.grid_profile_info)
        fuelSummaryCard = view.findViewById(R.id.fuel_summary_card)
        tvAvgFuel = view.findViewById(R.id.tv_avg_fuel)
        tvAvgFuelUnit = view.findViewById(R.id.tv_avg_fuel_unit)
        tvAvgCost = view.findViewById(R.id.tv_avg_cost)
        tvAvgCostUnit = view.findViewById(R.id.tv_avg_cost_unit)
        tvTotalSpent = view.findViewById(R.id.tv_total_spent)
        tvTotalSpentUnit = view.findViewById(R.id.tv_total_spent_unit)
        mapVehicleLocation = view.findViewById(R.id.map_vehicle_location)
        btnDelete = view.findViewById(R.id.btn_delete)
        btnHome = view.findViewById(R.id.btn_home)
        btnEdit = view.findViewById(R.id.btn_edit)
        btnAddFuel = view.findViewById(R.id.btn_add_fuel)

        mapVehicleLocation.onCreate(savedInstanceState)
        val aMap = mapVehicleLocation.map
        val dayNightMode = SettingUtils.readDarkMapMode()
        if (dayNightMode == SettingUtils.DAY_NIGHT_MODE_NIGHT ||
            (dayNightMode == SettingUtils.DAY_NIGHT_MODE_AUTO && SettingUtils.isNightTime())) {
            aMap.mapType = AMap.MAP_TYPE_NIGHT
        }
        aMap.uiSettings.isZoomControlsEnabled = false

        // Load target profile from arguments
        val targetName = arguments?.getString(ARG_PROFILE_NAME) ?: ""
        loadAndDisplay(targetName)

        fuelSummaryCard.setOnClickListener {
            currentProfile?.let { p ->
                FuelRecordBottomSheet.newInstance(p.name)
                    .show(childFragmentManager, "FuelRecordBottomSheet")
            }
        }

        btnDelete.setOnClickListener { currentProfile?.let { showDeleteConfirmDialog(it) } }
        btnHome.setOnClickListener { (parentFragment as? HomeFragment)?.showOverview() }
        btnEdit.setOnClickListener { currentProfile?.let { showEditProfileDialog(it) } }
        btnAddFuel.setOnClickListener {
            currentProfile?.let { p ->
                DialogAddFuel.newInstance(p.name) { record ->
                    thread {
                        AppDatabase.getInstance(requireContext()).fuelRecordDao().insert(record)
                        activity?.runOnUiThread { refreshFuelSummary(p) }
                    }
                }.show(childFragmentManager, "DialogAddFuel")
            }
        }
    }

    private fun loadAndDisplay(targetName: String) {
        val profiles = ProfileRepository.loadProfiles(requireContext())
        currentProfile = profiles.find { it.name == targetName }
        if (currentProfile == null) {
            (parentFragment as? HomeFragment)?.showOverview()
            return
        }
        displayProfile(currentProfile!!)
    }

    // ---- Display ----

    private fun displayProfile(profile: Profile) {
        if (profile.imageUri != null) {
            try {
                ivVehicleIcon.setImageURI(profile.imageUri)
            } catch (_: Exception) {
                ivVehicleIcon.setImageResource(R.mipmap.ic_profile_default_image_foreground)
            }
        } else {
            ivVehicleIcon.setImageResource(R.mipmap.ic_profile_default_image_foreground)
        }
        ivVehicleIcon.visibility = View.VISIBLE

        gridInfo.removeAllViews()
        addInfoCard("类型", "${profile.type} - ${profile.subType.label}")
        addInfoCard("排量", "${profile.displacement} cc")
        if (profile.plate.isNotBlank()) {
            addInfoCard("车牌号", profile.plate)
        }

        refreshFuelSummary(profile)
        mapVehicleLocation.visibility = View.VISIBLE
    }

    private fun addInfoCard(title: String, value: String) {
        val inflater = LayoutInflater.from(requireContext())
        val card = inflater.inflate(R.layout.item_profile_info, gridInfo, false)
        card.findViewById<TextView>(R.id.tv_title).text = title
        card.findViewById<TextView>(R.id.tv_value).text = value
        gridInfo.addView(card)
    }

    // ---- Fuel Summary ----

    private fun refreshFuelSummary(profile: Profile) {
        if (!profile.isFuelType()) {
            fuelSummaryCard.visibility = View.GONE
            return
        }
        fuelSummaryCard.visibility = View.VISIBLE

        thread {
            val dao = AppDatabase.getInstance(requireContext()).fuelRecordDao()
            val all = dao.loadByProfile(profile.name)
            val asc = dao.loadByProfileAsc(profile.name)
            activity?.runOnUiThread {
                val fuelPer100km = FuelCalculator.latestFuelPer100km(asc)
                val costPerKm = FuelCalculator.calcAvgCostPerKm(asc)
                val spent = FuelCalculator.totalSpent(all)
                tvAvgFuel.text = FuelCalculator.formatFuelPer100kmValue(fuelPer100km)
                tvAvgCost.text = FuelCalculator.formatCostPerKmValue(costPerKm)
                tvTotalSpent.text = FuelCalculator.formatTotalSpentValue(spent)
            }
        }
    }

    // ---- Delete ----

    private fun showDeleteConfirmDialog(profile: Profile) {
        AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("删除配置")
            .setMessage("确认删除配置「${profile.name}」？")
            .setPositiveButton("删除") { _, _ ->
                val profiles = ProfileRepository.loadProfiles(requireContext())
                profiles.removeAll { it.name == profile.name }
                ProfileRepository.saveProfiles(requireContext(), profiles)
                (parentFragment as? HomeFragment)?.showOverview()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---- Edit Dialog ----

    private fun showEditProfileDialog(profile: Profile) {
        isEditMode = true
        editingProfileName = profile.name
        val types = ProfileRepository.vehicleTypeMap.keys.toList()
        val typeIdx = types.indexOf(profile.type).coerceAtLeast(0)
        val subtypes = ProfileRepository.vehicleTypeMap[profile.type] ?: emptyList()
        val subIdx = subtypes.indexOfFirst { it.label == profile.subType.label }.coerceAtLeast(0)

        showProfileFormDialog(
            title = "编辑配置",
            name = profile.name,
            typeIndex = typeIdx,
            subtypeIndex = subIdx,
            displacement = profile.displacement.toString(),
            plate = profile.plate,
            imageUri = profile.imageUri
        )
    }

    private fun showProfileFormDialog(
        title: String,
        name: String,
        typeIndex: Int,
        subtypeIndex: Int,
        displacement: String,
        plate: String,
        imageUri: Uri?
    ) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_profile, null)

        val spinnerType = dialogView.findViewById<Spinner>(R.id.spinner_type)
        val spinnerSubtype = dialogView.findViewById<Spinner>(R.id.spinner_subtype)
        val etName = dialogView.findViewById<EditText>(R.id.et_profile_name)
        val etDisplacement = dialogView.findViewById<EditText>(R.id.et_displacement)
        val etPlate = dialogView.findViewById<EditText>(R.id.et_plate)
        val btnImport = dialogView.findViewById<Button>(R.id.btn_import_image)
        dialogImageNameTv = dialogView.findViewById(R.id.tv_image_name)

        pendingImageUri = imageUri
        if (imageUri != null) {
            dialogImageNameTv?.text = imageUri.lastPathSegment ?: "已选择图片"
        }

        etName.setText(name)
        etDisplacement.setText(displacement)
        etPlate.setText(plate)

        val types = ProfileRepository.vehicleTypeMap.keys.toList()
        val typeAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item_white, types)
        typeAdapter.setDropDownViewResource(R.layout.spinner_item_white)
        spinnerType.adapter = typeAdapter
        spinnerType.setSelection(typeIndex)

        fun updateSubtypes(pos: Int) {
            val selType = types[pos]
            val subs = ProfileRepository.vehicleTypeMap[selType] ?: emptyList()
            val subAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item_white, subs)
            subAdapter.setDropDownViewResource(R.layout.spinner_item_white)
            spinnerSubtype.adapter = subAdapter
            spinnerSubtype.setSelection(if (pos == typeIndex) subtypeIndex else 0)
        }
        updateSubtypes(typeIndex)

        spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                updateSubtypes(pos)
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
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val newName = etName.text.toString().trim()
                val dispStr = etDisplacement.text.toString().trim()
                val newPlate = etPlate.text.toString().trim()

                if (newName.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入配置名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val profiles = ProfileRepository.loadProfiles(requireContext())
                if (!isEditMode && profiles.any { it.name == newName }) {
                    Toast.makeText(requireContext(), "配置名称已存在", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (isEditMode && newName != editingProfileName && profiles.any { it.name == newName }) {
                    Toast.makeText(requireContext(), "配置名称已存在", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val disp = dispStr.toIntOrNull()
                if (disp == null || disp <= 0) {
                    Toast.makeText(requireContext(), "请输入有效的排量", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val selType = types[spinnerType.selectedItemPosition]
                val subs = ProfileRepository.vehicleTypeMap[selType] ?: emptyList()
                val selSub = if (subs.isNotEmpty()) subs[spinnerSubtype.selectedItemPosition]
                else VehicleSubType(selType, "UNKNOWN")

                val updated = Profile(newName, selType, selSub, disp, newPlate, pendingImageUri)

                if (isEditMode) {
                    val idx = profiles.indexOfFirst { it.name == editingProfileName }
                    if (idx >= 0) profiles[idx] = updated
                } else {
                    profiles.add(updated)
                }
                ProfileRepository.saveProfiles(requireContext(), profiles)
                currentProfile = updated
                if (isEditMode) editingProfileName = updated.name
                displayProfile(updated)
                pendingImageUri = null
                dialogImageNameTv = null
                isEditMode = false
                editingProfileName = null
            }
            .setNegativeButton("取消") { _, _ ->
                pendingImageUri = null
                dialogImageNameTv = null
                isEditMode = false
                editingProfileName = null
            }
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            pendingImageUri = uri
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { }
            dialogImageNameTv?.text = uri.lastPathSegment ?: "已选择图片"
        }
    }

    // ---- MapView Lifecycle ----

    override fun onResume() {
        super.onResume()
        mapVehicleLocation.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapVehicleLocation.onPause()
    }

    override fun onDestroyView() {
        mapVehicleLocation.onDestroy()
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapVehicleLocation.onSaveInstanceState(outState)
    }

    fun onContainerHidden() {
        mapVehicleLocation.visibility = View.GONE
    }

    fun onContainerShown() {
        currentProfile?.let { mapVehicleLocation.visibility = View.VISIBLE }
    }
}
