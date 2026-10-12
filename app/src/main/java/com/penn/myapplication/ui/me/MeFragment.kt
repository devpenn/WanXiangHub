package com.penn.myapplication.ui.me

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.penn.myapplication.R

class MeFragment : Fragment() {

    private lateinit var btnSelectImage: LinearLayout
    private lateinit var btnSelectVideo: LinearLayout
    private lateinit var btnClearWallpaper: LinearLayout
    private lateinit var btnSetCustomText: LinearLayout

    // 图片选择器
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                saveWallpaper("image", uri)
                Toast.makeText(requireContext(), "图片壁纸已设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 视频选择器
    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                saveWallpaper("video", uri)
                Toast.makeText(requireContext(), "视频壁纸已设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 权限请求
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(requireContext(), "权限已授予", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "需要存储权限才能选择壁纸", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_me, container, false)

        initViews(view)
        setupListeners()

        return view
    }

    private fun initViews(view: View) {
        btnSelectImage = view.findViewById(R.id.btn_select_image)
        btnSelectVideo = view.findViewById(R.id.btn_select_video)
        btnClearWallpaper = view.findViewById(R.id.btn_clear_wallpaper)
        btnSetCustomText = view.findViewById(R.id.btn_set_custom_text)
    }

    private fun setupListeners() {
        btnSelectImage.setOnClickListener {
            if (checkPermissions()) {
                selectImage()
            } else {
                requestPermissions()
            }
        }

        btnSelectVideo.setOnClickListener {
            if (checkPermissions()) {
                selectVideo()
            } else {
                requestPermissions()
            }
        }

        btnClearWallpaper.setOnClickListener {
            clearWallpaper()
            Toast.makeText(requireContext(), "壁纸已清除", Toast.LENGTH_SHORT).show()
        }

        btnSetCustomText.setOnClickListener {
            showCustomTextDialog()
        }
    }

    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }

    private fun selectImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun selectVideo() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        videoPickerLauncher.launch(intent)
    }

    private fun saveWallpaper(type: String, uri: Uri) {
        try {
            // 获取持久化权限
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // 即使获取持久化权限失败，也继续保存URI
        }

        val prefs = requireContext().getSharedPreferences("HomePrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("wallpaper_type", type)
            putString("wallpaper_uri", uri.toString())
            apply()
        }
    }

    private fun clearWallpaper() {
        val prefs = requireContext().getSharedPreferences("HomePrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("wallpaper_type", "none")
            remove("wallpaper_uri")
            apply()
        }
    }

    private fun showCustomTextDialog() {
        val prefs = requireContext().getSharedPreferences("HomePrefs", Context.MODE_PRIVATE)
        val currentText = prefs.getString("custom_text", "")

        val editText = EditText(requireContext()).apply {
            setText(currentText)
            hint = "输入自定义文字（留空则不显示）"
            setPadding(50, 30, 50, 30)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("设置首页自定义文字")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val text = editText.text.toString().trim()
                prefs.edit().putString("custom_text", text).apply()
                Toast.makeText(requireContext(), "自定义文字已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}