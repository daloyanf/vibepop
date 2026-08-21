package com.vibepop.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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

    private var currentThemeTargetAddress: String? = null
    private var themeTargetList = listOf<ThemeTargetItem>()
    private var isProgrammaticSpinnerChange = false
    private var hasPromptedBluetoothDialog = false

    private val bluetoothStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, android.bluetooth.BluetoothAdapter.ERROR)
                when (state) {
                    android.bluetooth.BluetoothAdapter.STATE_OFF -> {
                        updatePermissionsUI()
                        Toast.makeText(this@MainActivity, getString(R.string.bt_off_toast), Toast.LENGTH_SHORT).show()
                    }
                    android.bluetooth.BluetoothAdapter.STATE_ON -> {
                        updatePermissionsUI()
                        viewModel.refreshBondedDevices(this@MainActivity)
                        Toast.makeText(this@MainActivity, getString(R.string.bt_on_toast), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // 1. 全屏原生相册选择器（直接完整打开系统相册，避免半屏拖拽）
    private val fullGalleryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                handleMediaPicked(uri)
            }
        }
    }

    // 2. 备用文件选择器
    private val fallbackPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            handleMediaPicked(uri)
        }
    }

    private fun handleMediaPicked(uri: android.net.Uri) {
        val targetAddress = currentThemeTargetAddress
        if (!targetAddress.isNullOrBlank()) {
            val (success, _) = viewModel.importDeviceMedia(this, targetAddress, uri)
            if (success) {
                loadThemeTargetUI(targetAddress)
                Toast.makeText(this, getString(R.string.import_success_device), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
            }
        } else {
            val (success, type) = viewModel.importCustomMedia(this, uri)
            if (success) {
                val typeName = when (type) {
                    "video" -> getString(R.string.media_type_video_full)
                    "image" -> getString(R.string.media_type_image_full)
                    else -> getString(R.string.media_type_other)
                }
                loadThemeTargetUI(null)
                Toast.makeText(this, getString(R.string.import_success_global, typeName), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 3. 相册/多媒体权限申请 Launcher
    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.any { it.value }
        if (granted) {
            Toast.makeText(this, getString(R.string.media_perm_granted_toast), Toast.LENGTH_SHORT).show()
            viewModel.refreshPermissions(this)
        } else {
            Toast.makeText(this, getString(R.string.media_perm_needed_toast), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        registerReceiver(bluetoothStateReceiver, IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED))

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
        if (viewModel.consumeTargetPolicyNotice()) {
            showTargetPolicyNotice()
        } else {
            checkAndPromptBluetoothEnabled()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {}
    }

    /**
     * 弹窗响应策略变更为"默认全不响应"后，向升级用户展示一次性说明
     */
    private fun showTargetPolicyNotice() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.target_policy_notice_title))
            .setMessage(getString(R.string.target_policy_notice_message))
            .setPositiveButton(getString(R.string.target_policy_notice_go)) { _, _ ->
                binding.bottomNav.selectedItemId = R.id.nav_devices
                checkAndPromptBluetoothEnabled()
            }
            .setNegativeButton(getString(R.string.target_policy_notice_later)) { _, _ ->
                checkAndPromptBluetoothEnabled()
            }
            .setOnCancelListener {
                checkAndPromptBluetoothEnabled()
            }
            .show()
    }

    private fun checkAndPromptBluetoothEnabled() {
        if (!PermissionHelper.isBluetoothEnabled(this)) {
            if (!hasPromptedBluetoothDialog) {
                hasPromptedBluetoothDialog = true
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.bt_not_enabled_title))
                    .setMessage(getString(R.string.bt_not_enabled_message))
                    .setPositiveButton(getString(R.string.bt_enable_button)) { _, _ ->
                        PermissionHelper.promptEnableBluetooth(this)
                    }
                    .setNegativeButton(getString(R.string.bt_later), null)
                    .show()
            }
        }
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

        if (itemId == R.id.nav_themes) {
            loadThemeTargetUI(currentThemeTargetAddress)
        }
    }

    /**
     * 1. 🏠 控制台面板交互绑定
     */
    private fun setupDashboardTab() {
        val dash = binding.tabDashboardLayout

        // 模拟弹窗一键测试
        dash.btnTestPopup.setOnClickListener {
            if (!PermissionHelper.hasOverlayPermission(this)) {
                Toast.makeText(this, getString(R.string.overlay_needed_toast), Toast.LENGTH_SHORT).show()
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

        // 权限警告条点击 -> 切换至设置/权限 Tab 或引导开启蓝牙
        dash.cardPermissionWarning.setOnClickListener {
            if (!PermissionHelper.isBluetoothEnabled(this)) {
                PermissionHelper.promptEnableBluetooth(this)
            } else {
                binding.bottomNav.selectedItemId = R.id.nav_settings
            }
        }

        // 声音模式切换
        dash.switchForceSpeakerphone.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                viewModel.updateForceSpeakerphone(isChecked)
            }
        }

        // 弹窗时长滑动条
        dash.sliderDismissDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val seconds = value.toInt()
                dash.tvDismissDelayLabel.text = getString(R.string.setting_dismiss_delay, seconds)
                viewModel.updateDismissSeconds(seconds)
            }
        }

        // 快捷导航按钮
        dash.btnQuickGoThemes.setOnClickListener {
            binding.bottomNav.selectedItemId = R.id.nav_themes
        }
        dash.btnQuickGoDevices.setOnClickListener {
            binding.bottomNav.selectedItemId = R.id.nav_devices
        }
    }

    /**
     * 2. 🎨 动效工坊面板交互绑定
     */
    private fun setupThemesTab() {
        val themes = binding.tabThemesLayout

        themes.cardThemeClassic.setOnClickListener {
            applyThemeToCurrentTarget("classic_airpods")
        }
        themes.cardThemeGreatVictory.setOnClickListener {
            applyThemeToCurrentTarget("great_victory")
        }
        themes.cardThemeCustom.setOnClickListener {
            applyThemeToCurrentTarget("custom_media")
        }

        // 修改弹窗显示名称重命名
        themes.btnRenameTargetDevice.setOnClickListener {
            showRenameDialog()
        }
        themes.cardThemeDeviceName.setOnClickListener {
            showRenameDialog()
        }

        // 从相册选择
        themes.btnPickFromGallery.setOnClickListener {
            requestMediaAndPick()
        }

        // 视频消退规则
        themes.rgVideoDismissMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rbVideoOnComplete) "on_complete" else "timer"
            val address = currentThemeTargetAddress
            if (address == null) {
                viewModel.updateVideoDismissMode(mode)
            } else {
                val devConfig = viewModel.getDevicePopupConfig(address)
                viewModel.saveDevicePopupConfig(this, address, devConfig.copy(videoDismissMode = mode))
            }
        }
    }

    private fun showRenameDialog() {
        val address = currentThemeTargetAddress
        val currentName = if (address == null) {
            viewModel.popupConfig.value?.customDeviceName ?: ""
        } else {
            viewModel.getDevicePopupConfig(address).customDeviceName
        }

        val inputEditText = com.google.android.material.textfield.TextInputEditText(this).apply {
            setText(currentName)
            if (currentName.isNotEmpty()) {
                setSelection(currentName.length)
            }
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            textSize = 15f
            isSingleLine = true
        }

        val inputLayout = com.google.android.material.textfield.TextInputLayout(
            this,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            hint = getString(R.string.rename_dialog_hint)
            boxStrokeColor = ContextCompat.getColor(this@MainActivity, R.color.primary)
            setPadding(
                (20 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (20 * resources.displayMetrics.density).toInt(),
                (4 * resources.displayMetrics.density).toInt()
            )
            addView(inputEditText)
        }

        val targetTitle = if (address == null) getString(R.string.rename_global_default) else {
            themeTargetList.find { it.address == address }?.title ?: getString(R.string.rename_target_fallback)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.rename_dialog_title))
            .setMessage(getString(R.string.rename_dialog_message, targetTitle))
            .setView(inputLayout)
            .setPositiveButton(getString(R.string.btn_confirm)) { _, _ ->
                val newName = inputEditText.text?.toString()?.trim() ?: ""
                if (address == null) {
                    viewModel.updateDeviceName(newName)
                } else {
                    val devConfig = viewModel.getDevicePopupConfig(address)
                    viewModel.saveDevicePopupConfig(this, address, devConfig.copy(customDeviceName = newName))
                }
                binding.tabThemesLayout.tvCurrentTargetDeviceName.text = if (newName.isNotBlank()) newName else getString(R.string.name_empty_placeholder)
                Toast.makeText(
                    this,
                    if (newName.isNotBlank()) getString(R.string.rename_updated_toast, newName) else getString(R.string.rename_cleared_toast),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun applyThemeToCurrentTarget(theme: String) {
        val address = currentThemeTargetAddress
        if (address == null) {
            viewModel.updateAnimationTheme(theme)
            loadThemeTargetUI(null)
        } else {
            val devConfig = viewModel.getDevicePopupConfig(address)
            val updated = if (theme != "custom_media") {
                devConfig.copy(animationTheme = theme, customMediaPath = null, customMediaType = "preset")
            } else {
                devConfig.copy(animationTheme = theme)
            }
            viewModel.saveDevicePopupConfig(this, address, updated)
            loadThemeTargetUI(address)
        }
    }

    private fun setupThemesDropdown(devices: List<BondedDeviceItem>) {
        val spinner = binding.tabThemesLayout.spinnerThemeTarget

        val items = mutableListOf<ThemeTargetItem>()
        items.add(ThemeTargetItem(getString(R.string.theme_target_global), null))
        for (device in devices) {
            if (device.isSelected) {
                items.add(ThemeTargetItem(getString(R.string.theme_target_device, device.name), device.address))
            }
        }
        themeTargetList = items

        val adapter = object : android.widget.ArrayAdapter<ThemeTargetItem>(
            this,
            R.layout.item_spinner_target,
            items
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val tv = (convertView ?: layoutInflater.inflate(R.layout.item_spinner_target, parent, false)) as TextView
                val item = getItem(position)
                tv.text = getString(R.string.spinner_arrow, item?.title)
                return tv
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val tv = (convertView ?: layoutInflater.inflate(R.layout.item_spinner_dropdown, parent, false)) as TextView
                val item = getItem(position)
                tv.text = item?.title
                val isCurrent = item?.address == currentThemeTargetAddress
                tv.setTextColor(ContextCompat.getColor(this@MainActivity, if (isCurrent) R.color.primary else R.color.text_primary))
                return tv
            }
        }
        spinner.adapter = adapter

        // 恢复之前选中的位置或默认
        val targetIndex = items.indexOfFirst { it.address == currentThemeTargetAddress }.let {
            if (it >= 0) it else 0
        }
        isProgrammaticSpinnerChange = true
        spinner.setSelection(targetIndex, false)
        isProgrammaticSpinnerChange = false
        currentThemeTargetAddress = items[targetIndex].address

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isProgrammaticSpinnerChange) return
                val item = items.getOrNull(position) ?: return
                currentThemeTargetAddress = item.address
                loadThemeTargetUI(item.address)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun selectTargetInDropdown(address: String?) {
        val spinner = binding.tabThemesLayout.spinnerThemeTarget
        val index = themeTargetList.indexOfFirst { it.address == address }
        if (index >= 0) {
            isProgrammaticSpinnerChange = true
            spinner.setSelection(index, false)
            isProgrammaticSpinnerChange = false
            currentThemeTargetAddress = address
            loadThemeTargetUI(address)
        }
    }

    private fun loadThemeTargetUI(targetAddress: String?) {
        val config = if (targetAddress == null) {
            viewModel.popupConfig.value ?: return
        } else {
            viewModel.getDevicePopupConfig(targetAddress)
        }

        val themes = binding.tabThemesLayout
        val currentName = if (config.customDeviceName.isNotBlank()) config.customDeviceName else getString(R.string.name_empty_placeholder)
        themes.tvCurrentTargetDeviceName.text = currentName

        updateThemeCardsUI(config.animationTheme, config.customMediaType, config.customMediaPath)

        if (config.videoDismissMode == "on_complete") {
            themes.rbVideoOnComplete.isChecked = true
        } else {
            themes.rbVideoTimer.isChecked = true
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
            val intent = Intent(Intent.ACTION_PICK).apply {
                setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*,video/*")
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            fullGalleryPickerLauncher.launch(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                    addCategory(Intent.CATEGORY_OPENABLE)
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
        devices.btnRefreshDevices.setOnClickListener {
            if (!PermissionHelper.isBluetoothEnabled(this)) {
                Toast.makeText(this, getString(R.string.bt_off_prompt_toast), Toast.LENGTH_SHORT).show()
                PermissionHelper.promptEnableBluetooth(this)
            } else {
                viewModel.refreshBondedDevices(this)
                Toast.makeText(this, getString(R.string.bt_refreshed_toast), Toast.LENGTH_SHORT).show()
            }
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
            if (!PermissionHelper.hasBluetoothPermission(this)) {
                PermissionHelper.requestBluetoothPermission(this)
            } else if (!PermissionHelper.isBluetoothEnabled(this)) {
                PermissionHelper.promptEnableBluetooth(this)
            } else {
                Toast.makeText(this, getString(R.string.bt_granted_on_toast), Toast.LENGTH_SHORT).show()
            }
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
                dash.tvServiceStatusBadge.text = getString(R.string.dash_service_running)
                dash.tvServiceStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_connected))
                dash.viewStatusDot.setBackgroundResource(R.drawable.bg_status_dot_green)
                dash.tvServiceSubStatus.text = getString(R.string.dash_service_sub_running)
            } else {
                dash.tvServiceStatusBadge.text = getString(R.string.dash_service_stopped)
                dash.tvServiceStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected))
                dash.viewStatusDot.setBackgroundResource(R.drawable.bg_status_dot_gray)
                dash.tvServiceSubStatus.text = getString(R.string.dash_service_sub_stopped)
            }
        }

        // 已配对设备列表展示与动效工坊下拉框联动
        viewModel.bondedDevices.observe(this) { devices ->
            renderBondedDevices(devices)
            setupThemesDropdown(devices)
        }

        // 弹窗参数配置
        viewModel.popupConfig.observe(this) { config ->
            val dash = binding.tabDashboardLayout

            // 扬声器外放
            if (dash.switchForceSpeakerphone.isChecked != config.isForceSpeakerphone) {
                dash.switchForceSpeakerphone.isChecked = config.isForceSpeakerphone
            }

            // 倒计时滑块
            dash.sliderDismissDelay.value = config.autoDismissSeconds.toFloat()
            dash.tvDismissDelayLabel.text = getString(R.string.setting_dismiss_delay, config.autoDismissSeconds)

            // 仅在当前选择全局默认时同步动效工坊卡片
            if (currentThemeTargetAddress == null) {
                loadThemeTargetUI(null)
            }
        }
    }

    private fun updatePermissionsUI() {
        val overlay = viewModel.isOverlayGranted.value ?: false
        val bluetooth = viewModel.isBluetoothGranted.value ?: false
        val isBtEnabled = PermissionHelper.isBluetoothEnabled(this)
        val notification = viewModel.isNotificationGranted.value ?: false
        val media = viewModel.isMediaGranted.value ?: false
        val oem = viewModel.isOemGranted.value ?: false

        val settings = binding.tabSettingsLayout
        updatePermissionButton(settings.btnGrantOverlay, overlay)
        if (!bluetooth) {
            updatePermissionButton(settings.btnGrantBluetooth, false)
        } else if (!isBtEnabled) {
            settings.btnGrantBluetooth.text = getString(R.string.bt_enable_button)
            settings.btnGrantBluetooth.isEnabled = true
            settings.btnGrantBluetooth.setTextColor(ContextCompat.getColor(this, R.color.primary))
            settings.btnGrantBluetooth.strokeColor = ContextCompat.getColorStateList(this, R.color.primary)
        } else {
            updatePermissionButton(settings.btnGrantBluetooth, true)
        }
        updatePermissionButton(settings.btnGrantNotification, notification)
        updatePermissionButton(settings.btnGrantMedia, media)
        updatePermissionButton(settings.btnOpenOemSettings, oem, isOem = true)

        // 控制台权限与状态警告条
        val missingList = mutableListOf<String>()
        if (!overlay) missingList.add(getString(R.string.perm_missing_overlay))
        if (!bluetooth) missingList.add(getString(R.string.perm_missing_bluetooth))
        if (!notification) missingList.add(getString(R.string.perm_missing_notification))

        val dash = binding.tabDashboardLayout
        if (missingList.isNotEmpty()) {
            dash.cardPermissionWarning.visibility = View.VISIBLE
            dash.tvMissingPermSummary.text = getString(R.string.dash_missing_perm_summary, missingList.joinToString("、"))
        } else if (!isBtEnabled) {
            dash.cardPermissionWarning.visibility = View.VISIBLE
            dash.tvMissingPermSummary.text = getString(R.string.dash_bt_off_warning)
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
            button.text = if (isOem) getString(R.string.btn_go_settings) else getString(R.string.btn_grant)
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

        // 重置三张卡片边框与对勾
        themes.cardThemeClassic.strokeColor = defaultStrokeColor
        themes.cardThemeClassic.strokeWidth = defaultStrokeWidth
        themes.ivCheckClassic.visibility = View.GONE

        themes.cardThemeGreatVictory.strokeColor = defaultStrokeColor
        themes.cardThemeGreatVictory.strokeWidth = defaultStrokeWidth
        themes.ivCheckGreatVictory.visibility = View.GONE

        themes.cardThemeCustom.strokeColor = defaultStrokeColor
        themes.cardThemeCustom.strokeWidth = defaultStrokeWidth
        themes.ivCheckCustom.visibility = View.GONE

        if (theme == "great_victory") {
            themes.cardThemeGreatVictory.strokeColor = activeStrokeColor
            themes.cardThemeGreatVictory.strokeWidth = activeStrokeWidth
            themes.ivCheckGreatVictory.visibility = View.VISIBLE
            dash.tvQuickThemeName.text = getString(R.string.dash_theme_great_victory)
        } else if (theme == "custom_media") {
            themes.cardThemeCustom.strokeColor = activeStrokeColor
            themes.cardThemeCustom.strokeWidth = activeStrokeWidth
            themes.ivCheckCustom.visibility = View.VISIBLE
            val typeDesc = when (mediaType) {
                "video" -> getString(R.string.media_type_video_mp4)
                "image" -> getString(R.string.media_type_image_gif)
                "lottie" -> getString(R.string.media_type_lottie)
                else -> getString(R.string.media_type_other)
            }
            val fileName = mediaPath?.let { java.io.File(it).name } ?: ""
            themes.tvCustomMediaDetail.text = if (fileName.isNotEmpty()) getString(R.string.media_active_hint, typeDesc, fileName) else getString(R.string.media_active_hint_no_file, typeDesc)
            dash.tvQuickThemeName.text = getString(R.string.dash_theme_custom)
        } else {
            themes.cardThemeClassic.strokeColor = activeStrokeColor
            themes.cardThemeClassic.strokeWidth = activeStrokeWidth
            themes.ivCheckClassic.visibility = View.VISIBLE
            dash.tvQuickThemeName.text = getString(R.string.dash_theme_classic)
        }
    }

    private fun renderBondedDevices(devices: List<BondedDeviceItem>) {
        val container = binding.tabDevicesLayout.layoutBondedDevicesContainer
        val dash = binding.tabDashboardLayout
        container.removeAllViews()

        val selectedCount = devices.count { it.isSelected }
        if (selectedCount > 0) {
            dash.tvQuickDeviceFilter.text = getString(R.string.dash_devices_enabled, selectedCount)
        } else {
            dash.tvQuickDeviceFilter.text = getString(R.string.dash_devices_none)
        }

        if (devices.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = getString(R.string.device_empty_hint)
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
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, (12 * density).toInt())
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

            // 1. 左侧齿轮设置图标（点击直接跳转动效工坊定制该耳机效果）
            val gearIv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams((32 * density).toInt(), (32 * density).toInt())
                setImageResource(R.drawable.ic_settings)
                setColorFilter(if (isSelected) primaryColor else textSecondaryColor)
                setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.list_selector_background)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (!device.isSelected) {
                        Toast.makeText(this@MainActivity, getString(R.string.device_need_enable_toast), Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    binding.bottomNav.selectedItemId = R.id.nav_themes
                    selectTargetInDropdown(device.address)
                }
            }
            row.addView(gearIv)

            // 2. 中间耳机信息
            val infoLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (12 * density).toInt()
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
                text = if (isSelected) getString(R.string.device_enabled_status) else getString(R.string.device_disabled_status, device.address)
                setTextColor(if (isSelected) statusConnectedColor else textSecondaryColor)
                textSize = 11f
                setPadding(0, (2 * density).toInt(), 0, 0)
            }

            infoLayout.addView(nameTv)
            infoLayout.addView(statusSubTv)
            row.addView(infoLayout)

            // 3. 右侧状态开关（仅点击此处时才切换开启/关闭状态）
            val switchBtn = com.google.android.material.switchmaterial.SwitchMaterial(this).apply {
                isChecked = isSelected
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    viewModel.toggleTargetDevice(device.address)
                }
            }
            row.addView(switchBtn)

            card.addView(row)
            container.addView(card)
        }
    }
}

data class ThemeTargetItem(
    val title: String,
    val address: String?
)
