package org.zarroboogs.maps.ui.hiking

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.zarroboogs.maps.R
import org.zarroboogs.maps.hiking.gpx.CoordSystem
import org.zarroboogs.maps.hiking.gpx.GpxParser
import org.zarroboogs.maps.hiking.gpx.TrackFileType

/**
 * GPX/KML 轨迹导入页 — 3 步流程。
 *
 * Step 1: 选择文件（系统文件选择器，支持 .gpx / .kml）
 * Step 2: 选择坐标系 + 预览信息
 * Step 3: 确认导入 → 返回结果给调用方
 */
class GpxImportActivity : AppCompatActivity() {

    private var selectedUri: Uri? = null
    private var selectedFileName: String = ""
    private var selectedFileType: TrackFileType = TrackFileType.GPX
    private var selectedCoordSystem: CoordSystem = CoordSystem.GCJ02

    private lateinit var txtFileName: TextView
    private lateinit var txtPreview: TextView
    private lateinit var btnConfirm: View
    private lateinit var radioCoord: RadioGroup

    companion object {
        const val TAG = "Hiking"
        private const val REQUEST_FILE_PICK = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gpx_import)

        txtFileName = findViewById(R.id.import_txt_filename)
        txtPreview = findViewById(R.id.import_txt_preview)
        btnConfirm = findViewById(R.id.import_btn_confirm)
        radioCoord = findViewById(R.id.import_radio_coord)

        // Step 1: 点击选择文件
        findViewById<View>(R.id.import_btn_pick_file).setOnClickListener { pickFile() }

        // 坐标系选择
        radioCoord.setOnCheckedChangeListener { _, checkedId ->
            selectedCoordSystem = when (checkedId) {
                R.id.import_radio_wgs84 -> CoordSystem.WGS84
                R.id.import_radio_bd09   -> CoordSystem.BD09
                else                     -> CoordSystem.GCJ02
            }
        }

        // 确认导入
        btnConfirm.setOnClickListener { confirmImport() }
        btnConfirm.isEnabled = false
    }

    /** 打开系统文件选择器，支持 .gpx 和 .kml */
    private fun pickFile() {
        Log.d(TAG, "[IMPORT] pickFile: opening file picker")
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            // 过滤 GPX 和 KML 文件
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/gpx+xml",
                "application/vnd.google-earth.kml+xml",
                "text/xml",
                "application/xml",
                "application/octet-stream"
            ))
        }
        startActivityForResult(intent, REQUEST_FILE_PICK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FILE_PICK && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                selectedUri = uri
                // 获取文件名
                selectedFileName = getFileName(uri)
                selectedFileType = GpxParser().detectType(selectedFileName)
                txtFileName.text = "已选择: $selectedFileName (${selectedFileType.displayName})"

                // 快速预览（解析前几行统计信息）
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val parser = GpxParser()
                    val track = parser.parse(inputStream!!, selectedFileType, CoordSystem.GCJ02)
                    txtPreview.text = "名称: ${track.name}\n" +
                        "航点数: ${track.waypointCount}\n" +
                        "总距离: ${"%.1f".format(track.totalDistanceM / 1000)} km\n" +
                        (if (track.totalElevationGainM > 0) "累计爬升: ${track.totalElevationGainM.toInt()}m\n" else "")
                    txtPreview.visibility = View.VISIBLE
                    btnConfirm.isEnabled = true
                } catch (e: Exception) {
                    txtPreview.text = "文件解析失败: ${e.message}"
                    txtPreview.visibility = View.VISIBLE
                    Toast.makeText(this, "文件解析失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** 确认导入 → 返回结果 */
    private fun confirmImport() {
        val uri = selectedUri ?: return
        val resultIntent = Intent().apply {
            data = uri
            putExtra("file_name", selectedFileName)
            putExtra("file_type", selectedFileType.name)
            putExtra("coord_system", selectedCoordSystem.name)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    /** 从 URI 获取文件名 */
    private fun getFileName(uri: Uri): String {
        var name = "unknown"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
