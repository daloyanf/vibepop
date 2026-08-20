package com.vibepop.ui

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vibepop.R
import com.vibepop.databinding.ActivityMainBinding
import com.vibepop.service.HeadsetMonitorService
import com.vibepop.util.PermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // 1. 全屏原生相册选择器（直接完整打开系统相册，避免半屏拖拽）
    private val fullGalleryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                val (success, type) = viewModel.importCustomMedia(this, uri)
                if (success) {
                    val typeName = when (type) {
                        "video" -> "视频 (MP4 / 自带声音)"
                        "image" -> "图片 / GIF 动图"
                        else -> "相册媒体"
                    }
                    Toast.makeText(this, "🎉 $typeName 导入成功！", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "导入失败，请检查文件格式", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 2. 备用文件选择器
    private val fallbackPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val (success, type) = viewModel.importCustomMedia(this, uri)
            if (success) {
                val typeName = when (type) {
                    "video" -> "视频 (MP4 / 自带声音)"
                    "image" -> "图片 / GIF 动图"
                    else -> "相册媒体"
                }
                Toast.makeText(this, "🎉 $typeName 导入成功！", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "导入失败，请检查文件格式", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 3. 相册/多媒体权限申请 Launcher
    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.any { it.value }
        if (granted) {
            Toast.makeText(this, "已获得媒体权限，请再次点击导入", Toast.LENGTH_SHORT).show()
            viewModel.refreshPermissions(this)
        } else {
            Toast.makeText(this, "需要媒体读取权限以导入自定义动效", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        HeadsetMonitorService.start(this)
        setupNavigation()
        setupDashboardTab()
        setupThemesTab()
        setupDevicesTab()
        setupSettingsTab()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        HeadsetMonitorService.start(this)
        viewModel.refreshPermissions(this)
    }

    /**
     * 底部导航栏切换逻辑
     */
    private fun setupNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            switchTab(item.itemId)
            true
        }

        // 默认激活控制台 Tab
        switchTab(R.id.nav_dashboard)
    }

    private fun switchTab(itemId: Int) {
        binding.tabDashboardLayout.root.visibility = if (itemId == R.id.nav_dashboard) View.VISIBLE else View.GONE
        binding.tabThemesLayout.root.visibility = if (itemId == R.id.nav_themes) View.VISIBLE else View.GONE
        binding.tabDevicesLayout.root.visibility = if (itemId == R.id.nav_devices) View.VISIBLE else View.GONE
        binding.tabSettingsLayout.root.visibility = if (itemId == R.id.nav_settings) View.VISIBLE else View.GONE
    }

    /**
     * 1. 🏠 控制台面板交互绑定
     */
    private fun setupDashboardTab() {
        val dash = binding.tabDashboardLayout

        // 模拟弹窗一键测试
        dash.btnTestPopup.setOnClickListener {
            if (!PermissionHelper.hasOverlayPermission(this)) {
                Toast.makeText(this, "请先授予悬浮窗权限以展示弹窗", Toast.LENGTH_SHORT).show()
                binding.bottomNav.selectedItemId = R.id.nav_settings
                PermissionHelper.requestOverlayPermission(this)
                return@setOnClickListener
            }
            viewModel.triggerMockPopup(applicationContext)
        }

        // 服务开关
        dash.switchService.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                viewModel.setServiceEnabled(this, isChecked)
            }
        }

        // 权限警告条点击 -> 切换至设置/权限 Tab
        dash.cardPermissionWarning.setOnClickListener {
            binding.bottomNav.selectedItemId = R.id.nav_settings
        }

        // 快捷导航按钮
        dash.btnQuickGoThemes.setOnClickListener {
            binding.bottomNav.selectedItemId = R.id.nav_themes
        }
        dash.btnQuickGoDevices.setOnClickListener {
            binding.bottomNav.selectedItemId = R.id.nav_devices
        }
        dash.btnQuickGoSettings.setOnClickListener {
            binding.bottomNav.selectedItemId = R.id.nav_settings
        }
    }

    /**
     * 2. 🎨 动效工坊面板交互绑定
     */
    private fun setupThemesTab() {
        val themes = binding.tabThemesLayout

        themes.cardThemeClassic.setOnClickListener {
            viewModel.updateAnimationTheme("classic_airpods")
        }
        themes.cardThemeCyberpunk.setOnClickListener {
            viewModel.updateAnimationTheme("cyberpunk_mecha")
        }
        themes.cardThemeMinimalist.setOnClickListener {
            viewModel.updateAnimationTheme("minimalist_pulse")
        }
        themes.cardThemeCustom.setOnClickListener {
            viewModel.updateAnimationTheme("custom_media")
        }

        // 从相册选择
        themes.btnPickFromGallery.setOnClickListener {
            requestMediaAndPick()
        }

        // 视频消退规则
        themes.rgVideoDismissMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rbVideoOnComplete) "on_complete" else "timer"
            viewModel.updateVideoDismissMode(mode)
        }
    }

    private fun requestMediaAndPick() {
        if (!PermissionHelper.hasMediaPermission(this)) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                mediaPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_MEDIA_VIDEO,
                        android.Manifest.permission.READ_MEDIA_IMAGES
                    )
                )
            } else {
                mediaPermissionLauncher.launch(
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                )
            }
            return
        }

        try {
            // 直接打开系统全屏相册（支持图片与视频），彻底避免 Android 13/14 PhotoPicker 的半屏抽屉拖拽问题
            val intent = android.content.Intent(android.content.Intent.ACTION_PICK).apply {
                setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*,video/*")
                putExtra(android.content.Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            fullGalleryPickerLauncher.launch(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    putExtra(android.content.Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                }
                fullGalleryPickerLauncher.launch(fallbackIntent)
            } catch (e2: Exception) {
                fallbackPickerLauncher.launch("*/*")
            }
        }
    }

    /**
     * 3. 🎧 设备管理面板交互绑定
     */
    private fun setupDevicesTab() {
        val devices = binding.tabDevicesLayout

        devices.etDeviceName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                s?.toString()?.let { viewModel.updateDeviceName(it) }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        devices.btnRefreshDevices.setOnClickListener {
            viewModel.refreshBondedDevices(this)
            Toast.makeText(this, "已刷新已配对蓝牙设备列表", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 4. ⚙️ 系统设置与权限面板交互绑定
     */
    private fun setupSettingsTab() {
        val settings = binding.tabSettingsLayout

        settings.btnGrantOverlay.setOnClickListener {
            PermissionHelper.requestOverlayPermission(this)
        }

        settings.btnGrantBluetooth.setOnClickListener {
            PermissionHelper.requestBluetoothPermission(this)
        }

        settings.btnGrantNotification.setOnClickListener {
            PermissionHelper.requestNotificationPermission(this)
        }

        settings.btnGrantMedia.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                mediaPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_MEDIA_VIDEO,
                        android.Manifest.permission.READ_MEDIA_IMAGES
                    )
                )
            } else {
                mediaPermissionLauncher.launch(
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                )
            }
        }

        settings.btnOpenOemSettings.setOnClickListener {
            PermissionHelper.openAppDetailsOrOemSettings(this)
        }

        settings.switchForceSpeakerphone.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                viewModel.updateForceSpeakerphone(isChecked)
            }
        }

        settings.sliderDismissDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val seconds = value.toInt()
                settings.tvDismissDelayLabel.text = "静态图/动画/定时模式消退时长：$seconds 秒"
                viewModel.updateDismissSeconds(seconds)
            }
        }
    }

    /**
     * 统一观察 ViewModel 状态
     */
    private fun observeViewModel() {
        // 权限状态驱动
        viewModel.isOverlayGranted.observe(this) { updatePermissionsUI() }
        viewModel.isBluetoothGranted.observe(this) { updatePermissionsUI() }
        viewModel.isNotificationGranted.observe(this) { updatePermissionsUI() }
        viewModel.isMediaGranted.observe(this) { updatePermissionsUI() }
        viewModel.isOemGranted.observe(this) { updatePermissionsUI() }

        // 服务状态
        viewModel.isServiceRunning.observe(this) { running ->
            val dash = binding.tabDashboardLayout
            if (dash.switchService.isChecked != running) {
                dash.switchService.isChecked = running
            }
            if (running) {
                dash.tvServiceStatusBadge.text = "服务运行中"
                dash.tvServiceStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_connected))
                dash.viewStatusDot.setBackgroundResource(R.drawable.bg_status_dot_green)
                dash.tvServiceSubStatus.text = "耳机连接时将自动唤起拟真动效弹窗"
            } else {
                dash.tvServiceStatusBadge.text = "服务已暂停"
                dash.tvServiceStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected))
                dash.viewStatusDot.setBackgroundResource(R.drawable.bg_status_dot_gray)
                dash.tvServiceSubStatus.text = "点击右侧开关开启耳机连接自动监听"
            }
        }

        // 已配对设备列表展示
        viewModel.bondedDevices.observe(this) { devices ->
            renderBondedDevices(devices)
        }

        // 弹窗参数配置
        viewModel.popupConfig.observe(this) { config ->
            val dash = binding.tabDashboardLayout
            val themes = binding.tabThemesLayout
            val devices = binding.tabDevicesLayout
            val settings = binding.tabSettingsLayout

            // 设备名称
            if (devices.etDeviceName.text?.toString() != config.customDeviceName) {
                devices.etDeviceName.setText(config.customDeviceName)
            }

            // 扬声器外放
            if (settings.switchForceSpeakerphone.isChecked != config.isForceSpeakerphone) {
                settings.switchForceSpeakerphone.isChecked = config.isForceSpeakerphone
            }
            dash.tvQuickSpeakerStatus.text = if (config.isForceSpeakerphone) "手机扬声器" else "耳机音频"

            // 倒计时滑块
            settings.sliderDismissDelay.value = config.autoDismissSeconds.toFloat()
            settings.tvDismissDelayLabel.text = "静态图/动画/定时模式消退时长：${config.autoDismissSeconds} 秒"

            // 视频消退规则
            if (config.videoDismissMode == "on_complete") {
                themes.rbVideoOnComplete.isChecked = true
            } else {
                themes.rbVideoTimer.isChecked = true
            }

            // 主题与动效选中状态更新
            updateThemeCardsUI(config.animationTheme, config.customMediaType, config.customMediaPath)
        }
    }

    private fun updatePermissionsUI() {
        val overlay = viewModel.isOverlayGranted.value ?: false
        val bluetooth = viewModel.isBluetoothGranted.value ?: false
        val notification = viewModel.isNotificationGranted.value ?: false
        val media = viewModel.isMediaGranted.value ?: false
        val oem = viewModel.isOemGranted.value ?: false

        val settings = binding.tabSettingsLayout
        updatePermissionButton(settings.btnGrantOverlay, overlay)
        updatePermissionButton(settings.btnGrantBluetooth, bluetooth)
        updatePermissionButton(settings.btnGrantNotification, notification)
        updatePermissionButton(settings.btnGrantMedia, media)
        updatePermissionButton(settings.btnOpenOemSettings, oem, isOem = true)

        // 控制台权限警告条
        val missingCount = listOf(overlay, bluetooth, notification).count { !it }
        val dash = binding.tabDashboardLayout
        if (missingCount > 0) {
            dash.cardPermissionWarning.visibility = View.VISIBLE
            val missingList = mutableListOf<String>()
            if (!overlay) missingList.add("悬浮窗")
            if (!bluetooth) missingList.add("蓝牙")
            if (!notification) missingList.add("通知")
            dash.tvMissingPermSummary.text = "尚有 ${missingList.joinToString("、")} 权限未授予，点击一键配置"
        } else {
            dash.cardPermissionWarning.visibility = View.GONE
        }
    }

    private fun updatePermissionButton(
        button: com.google.android.material.button.MaterialButton,
        granted: Boolean,
        isOem: Boolean = false
    ) {
        if (granted) {
            button.text = getString(R.string.btn_granted)
            button.isEnabled = isOem // 厂商设置允许再次点击查看
            button.setTextColor(ContextCompat.getColor(this, R.color.status_connected))
            button.strokeColor = ContextCompat.getColorStateList(this, R.color.status_connected)
        } else {
            button.text = if (isOem) "去设置" else getString(R.string.btn_grant)
            button.isEnabled = true
            button.setTextColor(ContextCompat.getColor(this, R.color.primary))
            button.strokeColor = ContextCompat.getColorStateList(this, R.color.primary)
        }
    }

    private fun updateThemeCardsUI(theme: String, mediaType: String, mediaPath: String?) {
        val themes = binding.tabThemesLayout
        val dash = binding.tabDashboardLayout

        val activeStrokeColor = ContextCompat.getColor(this, R.color.primary)
        val defaultStrokeColor = ContextCompat.getColor(this, R.color.card_stroke)
        val activeStrokeWidth = (2 * resources.displayMetrics.density).toInt()
        val defaultStrokeWidth = (1 * resources.displayMetrics.density).toInt()

        // 重置所有卡片边框与对勾
        themes.cardThemeClassic.strokeColor = defaultStrokeColor
        themes.cardThemeClassic.strokeWidth = defaultStrokeWidth
        themes.ivCheckClassic.visibility = View.GONE

        themes.cardThemeCyberpunk.strokeColor = defaultStrokeColor
        themes.cardThemeCyberpunk.strokeWidth = defaultStrokeWidth
        themes.ivCheckCyberpunk.visibility = View.GONE

        themes.cardThemeMinimalist.strokeColor = defaultStrokeColor
        themes.cardThemeMinimalist.strokeWidth = defaultStrokeWidth
        themes.ivCheckMinimalist.visibility = View.GONE

        themes.cardThemeCustom.strokeColor = defaultStrokeColor
        themes.cardThemeCustom.strokeWidth = defaultStrokeWidth
        themes.ivCheckCustom.visibility = View.GONE

        when (theme) {
            "cyberpunk_mecha" -> {
                themes.cardThemeCyberpunk.strokeColor = activeStrokeColor
                themes.cardThemeCyberpunk.strokeWidth = activeStrokeWidth
                themes.ivCheckCyberpunk.visibility = View.VISIBLE
                dash.tvQuickThemeName.text = "赛博机甲"
            }
            "minimalist_pulse" -> {
                themes.cardThemeMinimalist.strokeColor = activeStrokeColor
                themes.cardThemeMinimalist.strokeWidth = activeStrokeWidth
                themes.ivCheckMinimalist.visibility = View.VISIBLE
                dash.tvQuickThemeName.text = "声波脉冲"
            }
            "custom_media" -> {
                themes.cardThemeCustom.strokeColor = activeStrokeColor
                themes.cardThemeCustom.strokeWidth = activeStrokeWidth
                themes.ivCheckCustom.visibility = View.VISIBLE
                val typeDesc = when (mediaType) {
                    "video" -> "视频 (MP4)"
                    "image" -> "图片 / GIF"
                    "lottie" -> "Lottie 矢量"
                    else -> "相册媒体"
                }
                val fileName = mediaPath?.let { java.io.File(it).name } ?: ""
                val hint = if (fileName.isNotEmpty()) "当前已生效：$typeDesc ($fileName)" else "当前已生效：$typeDesc"
                themes.tvCustomMediaDetail.text = hint
                dash.tvQuickThemeName.text = "相册自定义"
            }
            else -> {
                themes.cardThemeClassic.strokeColor = activeStrokeColor
                themes.cardThemeClassic.strokeWidth = activeStrokeWidth
                themes.ivCheckClassic.visibility = View.VISIBLE
                dash.tvQuickThemeName.text = "AirPods Pro"
            }
        }
    }

    private fun renderBondedDevices(devices: List<BondedDeviceItem>) {
        val container = binding.tabDevicesLayout.layoutBondedDevicesContainer
        val dash = binding.tabDashboardLayout
        container.removeAllViews()

        val selectedCount = devices.count { it.isSelected }
        if (selectedCount > 0) {
            dash.tvQuickDeviceFilter.text = "白名单 (${selectedCount}台)"
        } else {
            dash.tvQuickDeviceFilter.text = "所有耳机"
        }

        if (devices.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "未发现已配对的蓝牙耳机，当前默认将响应所有音频设备"
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                textSize = 13f
                setPadding(0, 8, 0, 8)
            }
            container.addView(emptyTv)
            return
        }

        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        val cardStrokeColor = ContextCompat.getColor(this, R.color.card_stroke)
        val activeBgColor = ContextCompat.getColor(this, R.color.card_selected_bg)
        val defaultBgColor = ContextCompat.getColor(this, R.color.bg_card_dark)
        val textPrimaryColor = ContextCompat.getColor(this, R.color.text_primary)
        val textSecondaryColor = ContextCompat.getColor(this, R.color.text_secondary)
        val statusConnectedColor = ContextCompat.getColor(this, R.color.status_connected)

        val density = resources.displayMetrics.density

        for (device in devices) {
            val isSelected = device.isSelected

            val card = com.google.android.material.card.MaterialCardView(this).apply {
                radius = 16f * density
                strokeColor = if (isSelected) primaryColor else cardStrokeColor
                strokeWidth = ((if (isSelected) 2 else 1) * density).toInt()
                setCardBackgroundColor(if (isSelected) activeBgColor else defaultBgColor)
                cardElevation = 2f * density
                isClickable = true
                isFocusable = true
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, (10 * density).toInt())
                }
                layoutParams = lp
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(
                    (16 * density).toInt(),
                    (14 * density).toInt(),
                    (16 * density).toInt(),
                    (14 * density).toInt()
                )
            }

            // 左侧高亮耳机图标
            val iconIv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams((28 * density).toInt(), (28 * density).toInt())
                setImageResource(R.drawable.ic_bluetooth_headset)
                setColorFilter(if (isSelected) primaryColor else textSecondaryColor)
            }
            row.addView(iconIv)

            // 中间设备信息
            val infoLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (14 * density).toInt()
                    marginEnd = (8 * density).toInt()
                }
                layoutParams = lp
            }

            val nameTv = TextView(this).apply {
                text = device.name
                setTextColor(textPrimaryColor)
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val statusSubTv = TextView(this).apply {
                text = if (isSelected) "✅ 已加入白名单（响应弹窗）" else "MAC: ${device.address}"
                setTextColor(if (isSelected) statusConnectedColor else textSecondaryColor)
                textSize = 11f
                setPadding(0, (2 * density).toInt(), 0, 0)
            }

            infoLayout.addView(nameTv)
            infoLayout.addView(statusSubTv)
            row.addView(infoLayout)

            // 右侧高亮开关 SwitchMaterial (显眼直观)
            val switchBtn = com.google.android.material.switchmaterial.SwitchMaterial(this).apply {
                isChecked = isSelected
                isClickable = false
            }
            row.addView(switchBtn)

            // 点击整张卡片任意位置即可切换
            card.setOnClickListener {
                viewModel.toggleTargetDevice(device.address)
            }

            card.addView(row)
            container.addView(card)
        }
    }
}
