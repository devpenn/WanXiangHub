package com.penn.myapplication.ui.me

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.penn.myapplication.R
import com.penn.myapplication.account.AccountManager
import com.penn.myapplication.ui.home.HomeQuickTools
import com.penn.myapplication.ui.home.WallpaperMemoryCache
import java.io.File
import java.io.FileOutputStream

class MeFragment : Fragment() {

    private lateinit var cardUserProfile: MaterialCardView
    private lateinit var ivUserAvatar: ShapeableImageView
    private lateinit var tvUserNickname: TextView
    private lateinit var tvUserId: TextView
    private lateinit var tvUserSignature: TextView
    private lateinit var tvMemberBadge: TextView

    private lateinit var btnSetQuickTools: LinearLayout
    private lateinit var btnSelectWallpaper: LinearLayout
    private lateinit var btnClearWallpaper: LinearLayout
    private lateinit var btnSetCustomText: LinearLayout
    private lateinit var tvWallpaperStatus: TextView

    private var pendingAvatarUri: Uri? = null
    private var tempEditAvatarView: ShapeableImageView? = null

    // 多合一壁纸选择器
    private val wallpaperPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                saveUnifiedWallpaper(uri)
            }
        }
    }

    // 头像选择器
    private val avatarPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                saveAvatarToInternalStorage(uri)
            }
        }
    }

    // 权限请求
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            selectWallpaper()
        } else {
            Toast.makeText(requireContext(), "需要存储/媒体权限才能选择壁纸", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_me, container, false)

        view.findViewById<TextView>(R.id.tv_page_header_title)?.text = "我的"

        initViews(view)
        setupListeners()
        updateProfileDisplay()
        updateWallpaperStatusText()

        return view
    }

    override fun onResume() {
        super.onResume()
        updateProfileDisplay()
        updateWallpaperStatusText()
    }

    private fun initViews(view: View) {
        cardUserProfile = view.findViewById(R.id.card_user_profile)
        ivUserAvatar = view.findViewById(R.id.iv_user_avatar)
        tvUserNickname = view.findViewById(R.id.tv_user_nickname)
        tvUserId = view.findViewById(R.id.tv_user_id)
        tvUserSignature = view.findViewById(R.id.tv_user_signature)
        tvMemberBadge = view.findViewById(R.id.tv_member_badge)

        btnSetQuickTools = view.findViewById(R.id.btn_set_quick_tools)
        btnSelectWallpaper = view.findViewById(R.id.btn_select_wallpaper)
        btnClearWallpaper = view.findViewById(R.id.btn_clear_wallpaper)
        btnSetCustomText = view.findViewById(R.id.btn_set_custom_text)
        tvWallpaperStatus = view.findViewById(R.id.tv_wallpaper_status)
    }

    private fun setupListeners() {
        cardUserProfile.setOnClickListener {
            showEditProfileDialog()
        }

        btnSetQuickTools.setOnClickListener {
            HomeQuickTools.showCustomDialog(requireContext()) {
                Toast.makeText(requireContext(), "快捷气泡设置已同步", Toast.LENGTH_SHORT).show()
            }
        }

        btnSelectWallpaper.setOnClickListener {
            if (checkPermissions()) {
                selectWallpaper()
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

    /** 更新顶部账号个人资料展示 */
    private fun updateProfileDisplay() {
        if (!isAdded) return
        val profile = AccountManager.getUserProfile(requireContext())

        tvUserNickname.text = profile.nickname
        tvUserId.text = "账号: ${profile.userId}"
        tvUserSignature.text = profile.signature
        tvMemberBadge.text = profile.memberTag

        if (!profile.avatarUri.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(profile.avatarUri)
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    ivUserAvatar.setImageBitmap(bitmap)
                } else {
                    ivUserAvatar.setImageResource(R.drawable.ic_person)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ivUserAvatar.setImageResource(R.drawable.ic_person)
            }
        } else {
            ivUserAvatar.setImageResource(R.drawable.ic_person)
        }
    }

    /** 弹出编辑个人资料对话框 */
    private fun showEditProfileDialog() {
        val context = requireContext()
        val profile = AccountManager.getUserProfile(context)

        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val btnChangeAvatar = dialogView.findViewById<View>(R.id.btn_change_avatar)
        val ivEditAvatar = dialogView.findViewById<ShapeableImageView>(R.id.iv_edit_avatar)
        val etNickname = dialogView.findViewById<TextInputEditText>(R.id.et_edit_nickname)
        val etSignature = dialogView.findViewById<TextInputEditText>(R.id.et_edit_signature)

        tempEditAvatarView = ivEditAvatar
        etNickname.setText(profile.nickname)
        etSignature.setText(profile.signature)

        if (!profile.avatarUri.isNullOrEmpty()) {
            try {
                val file = File(Uri.parse(profile.avatarUri).path ?: "")
                if (file.exists()) {
                    ivEditAvatar.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                }
            } catch (_: Exception) {
            }
        }

        btnChangeAvatar.setOnClickListener {
            selectAvatarImage()
        }

        AlertDialog.Builder(context)
            .setTitle("编辑个人资料")
            .setView(dialogView)
            .setPositiveButton("保存修改") { _, _ ->
                val newNickname = etNickname.text?.toString()?.trim()
                val newSignature = etSignature.text?.toString()?.trim()

                if (newNickname.isNullOrEmpty()) {
                    Toast.makeText(context, "昵称不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                AccountManager.saveUserProfile(
                    context,
                    newNickname,
                    if (newSignature.isNullOrEmpty()) "万物皆有灵，万象尽更新" else newSignature,
                    pendingAvatarUri?.toString()
                )
                Toast.makeText(context, "个人资料已保存", Toast.LENGTH_SHORT).show()
                updateProfileDisplay()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun selectAvatarImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        try {
            avatarPickerLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "无法打开相册", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAvatarToInternalStorage(sourceUri: Uri) {
        try {
            val context = requireContext()
            val destFile = File(context.filesDir, "custom_user_avatar.jpg")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            pendingAvatarUri = Uri.fromFile(destFile)
            tempEditAvatarView?.setImageBitmap(BitmapFactory.decodeFile(destFile.absolutePath))
            Toast.makeText(context, "头像已更新", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "头像保存失败", Toast.LENGTH_SHORT).show()
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

    private fun selectWallpaper() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            wallpaperPickerLauncher.launch(intent)
        } catch (_: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            wallpaperPickerLauncher.launch(fallbackIntent)
        }
    }

    /** 自动智能识别媒体类型 (静态图片 / GIF 动图 / 视频)，安全拷贝至 App 私有存储 */
    private fun saveUnifiedWallpaper(sourceUri: Uri) {
        try {
            val context = requireContext()
            WallpaperMemoryCache.clear()

            val mimeType = context.contentResolver.getType(sourceUri) ?: ""
            val uriString = sourceUri.toString().lowercase()

            val wallpaperType = when {
                mimeType.startsWith("video/") || uriString.endsWith(".mp4") || uriString.endsWith(".webm") || uriString.endsWith(".mkv") -> "video"
                mimeType == "image/gif" || uriString.endsWith(".gif") -> "gif"
                else -> "image"
            }

            val fileName = when (wallpaperType) {
                "video" -> "custom_wallpaper_video.mp4"
                "gif" -> "custom_wallpaper_gif.gif"
                else -> "custom_wallpaper_image.jpg"
            }

            val destFile = File(context.filesDir, fileName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            val internalUriStr = Uri.fromFile(destFile).toString()

            val prefs = context.getSharedPreferences("HomePrefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("wallpaper_type", wallpaperType)
                putString("wallpaper_uri", internalUriStr)
                apply()
            }

            val typeText = when (wallpaperType) {
                "video" -> "动态视频壁纸"
                "gif" -> "GIF 动图壁纸"
                else -> "静态图片壁纸"
            }
            Toast.makeText(context, "已成功设置 $typeText", Toast.LENGTH_SHORT).show()
            updateWallpaperStatusText()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "壁纸保存失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearWallpaper() {
        val context = requireContext()
        WallpaperMemoryCache.clear()

        try {
            File(context.filesDir, "custom_wallpaper_image.jpg").delete()
            File(context.filesDir, "custom_wallpaper_gif.gif").delete()
            File(context.filesDir, "custom_wallpaper_video.mp4").delete()
        } catch (_: Exception) {
        }

        val prefs = context.getSharedPreferences("HomePrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("wallpaper_type", "none")
            remove("wallpaper_uri")
            apply()
        }
        updateWallpaperStatusText()
    }

    private fun updateWallpaperStatusText() {
        if (!isAdded) return
        val prefs = requireContext().getSharedPreferences("HomePrefs", Context.MODE_PRIVATE)
        val type = prefs.getString("wallpaper_type", "none") ?: "none"

        tvWallpaperStatus.text = when (type) {
            "image" -> "静态图片"
            "gif" -> "GIF 动图"
            "video" -> "动态视频"
            else -> "未设置"
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
