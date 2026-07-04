package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.client.RemoteFile
import com.example.client.WebdavClient
import com.example.service.WebdavService
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TransferTask(
    val id: String,
    val fileName: String,
    val progress: Float, // 0.0f to 1.0f
    val isUpload: Boolean,
    val status: String // "Queued", "Running", "Completed", "Failed"
)

data class InterfaceInfo(
    val name: String,
    val ip: String,
    val isUsbTether: Boolean
)

class MainViewModel : ViewModel() {
    private val TAG = "MainViewModel"
    private val client = WebdavClient()

    // Server host configurations & state from Service
    val isServerRunning = WebdavService.isServerRunning
    val serverIp = WebdavService.serverIp
    val serverPort = WebdavService.serverPort
    val serverUsername = WebdavService.serverUsername
    val serverToken = WebdavService.serverToken

    // Generated credentials for local server
    private val _localServerPassword = MutableStateFlow("")
    val localServerPassword = _localServerPassword.asStateFlow()

    // Local client network interface states
    private val _networkInterfaces = MutableStateFlow<List<InterfaceInfo>>(emptyList())
    val networkInterfaces = _networkInterfaces.asStateFlow()

    private val _detectedUsbIp = MutableStateFlow("")
    val detectedUsbIp = _detectedUsbIp.asStateFlow()

    // Companion PC details
    val pcIp = MutableStateFlow("")
    val pcPort = MutableStateFlow("8080")
    val pcUsername = MutableStateFlow("admin")
    val pcToken = MutableStateFlow("")
    val isPcConnected = MutableStateFlow(false)

    // Subnet discovery scanner state
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering = _isDiscovering.asStateFlow()

    private val _discoveredIps = MutableStateFlow<List<String>>(emptyList())
    val discoveredIps = _discoveredIps.asStateFlow()

    // Local file browser states ("My Phone" tab)
    private val _currentLocalDirectory = MutableStateFlow<File?>(null)
    val currentLocalDirectory = _currentLocalDirectory.asStateFlow()

    private val _localFiles = MutableStateFlow<List<File>>(emptyList())
    val localFiles = _localFiles.asStateFlow()

    private val _selectedLocalFiles = MutableStateFlow<Set<File>>(emptySet())
    val selectedLocalFiles = _selectedLocalFiles.asStateFlow()

    private val _localGridMode = MutableStateFlow(false)
    val localGridMode = _localGridMode.asStateFlow()

    // Remote file browser states ("PC Files" tab)
    private val _currentRemoteDirectory = MutableStateFlow("/")
    val currentRemoteDirectory = _currentRemoteDirectory.asStateFlow()

    private val _remoteFiles = MutableStateFlow<List<RemoteFile>>(emptyList())
    val remoteFiles = _remoteFiles.asStateFlow()

    private val _selectedRemoteFiles = MutableStateFlow<Set<RemoteFile>>(emptySet())
    val selectedRemoteFiles = _selectedRemoteFiles.asStateFlow()

    private val _remoteGridMode = MutableStateFlow(false)
    val remoteGridMode = _remoteGridMode.asStateFlow()

    private val _remoteError = MutableStateFlow<String?>(null)
    val remoteError = _remoteError.asStateFlow()

    private val _isRemoteLoading = MutableStateFlow(false)
    val isRemoteLoading = _isRemoteLoading.asStateFlow()

    // Active file transfers state
    private val _activeTransfers = MutableStateFlow<List<TransferTask>>(emptyList())
    val activeTransfers = _activeTransfers.asStateFlow()

    init {
        // Generate a random 6-digit numeric password for the server session
        generateRandomPassword()
        refreshInterfaces()
    }

    fun generateRandomPassword() {
        val pin = (100000..999999).random().toString()
        _localServerPassword.value = pin
    }

    // --- Network Discovery ---
    fun refreshInterfaces() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<InterfaceInfo>()
            var primaryUsbIp = ""
            try {
                val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return@launch
                for (iface in interfaces) {
                    if (iface.isLoopback || !iface.isUp) continue
                    val addrs = iface.inetAddresses
                    for (addr in addrs) {
                        if (addr is java.net.Inet4Address) {
                            val ip = addr.hostAddress ?: continue
                            val name = iface.name.lowercase()
                            val isUsb = name.contains("rndis") || name.contains("usb") || name.contains("ncm") || name.contains("eth")
                            list.add(InterfaceInfo(iface.name, ip, isUsb))
                            if (isUsb && primaryUsbIp.isEmpty()) {
                                primaryUsbIp = ip
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get interfaces: ${e.message}")
            }
            // If no usb, but we have others, let's list them
            _networkInterfaces.value = list.sortedByDescending { it.isUsbTether }
            _detectedUsbIp.value = primaryUsbIp
        }
    }

    fun scanSubnetForPC() {
        val baseIp = detectedUsbIp.value.ifEmpty {
            networkInterfaces.value.firstOrNull()?.ip ?: ""
        }
        if (baseIp.isEmpty()) {
            Log.w(TAG, "No base IP detected for subnet scanning")
            return
        }

        val portStr = pcPort.value
        val port = portStr.toIntOrNull() ?: 8080

        _isDiscovering.value = true
        _discoveredIps.value = emptyList()

        viewModelScope.launch(Dispatchers.IO) {
            val results = java.util.Collections.synchronizedList(mutableListOf<String>())
            val lastDot = baseIp.lastIndexOf('.')
            if (lastDot == -1) {
                _isDiscovering.value = false
                return@launch
            }
            val subnet = baseIp.substring(0, lastDot + 1) // e.g. "192.168.42."

            // Scan in parallel using coroutines
            val jobs = (1..254).map { i ->
                launch {
                    val targetIp = "$subnet$i"
                    if (client.ping(targetIp, port)) {
                        results.add(targetIp)
                    }
                }
            }
            jobs.forEach { it.join() }

            withContext(Dispatchers.Main) {
                _discoveredIps.value = results.toList()
                _isDiscovering.value = false
                if (results.isNotEmpty()) {
                    pcIp.value = results.first()
                }
            }
        }
    }

    // --- Server Actions ---
    fun toggleServer(context: Context) {
        val intent = Intent(context, WebdavService::class.java)
        if (isServerRunning.value) {
            intent.action = WebdavService.ACTION_STOP
            context.startService(intent)
        } else {
            intent.action = WebdavService.ACTION_START
            intent.putExtra(WebdavService.EXTRA_PORT, 8080)
            intent.putExtra(WebdavService.EXTRA_USERNAME, "admin")
            intent.putExtra(WebdavService.EXTRA_TOKEN, localServerPassword.value)
            
            // Try to bind to detected USB tether interface IP first
            val ip = detectedUsbIp.value.ifEmpty {
                networkInterfaces.value.firstOrNull { it.isUsbTether }?.ip ?: ""
            }
            intent.putExtra(WebdavService.EXTRA_BIND_IP, ip)
            context.startForegroundService(intent)
        }
    }

    // --- Local Files Browsing ("My Phone") ---
    fun initLocalDirectory(context: Context) {
        if (_currentLocalDirectory.value == null) {
            val root = getLocalRootDir(context)
            _currentLocalDirectory.value = root
            refreshLocalFiles(context)
        }
    }

    fun getLocalRootDir(context: Context): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            Environment.getExternalStorageDirectory()
        } else {
            context.getExternalFilesDir(null) ?: context.filesDir
        }
    }

    fun refreshLocalFiles(context: Context) {
        val currentDir = _currentLocalDirectory.value ?: getLocalRootDir(context)
        _isDiscovering.value = false // reset liveness
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = currentDir.listFiles()?.toList() ?: emptyList()
                // Sort folders first, then alphabetically
                val sorted = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                withContext(Dispatchers.Main) {
                    _localFiles.value = sorted
                    _selectedLocalFiles.value = emptySet()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listing local files", e)
            }
        }
    }

    fun navigateIntoLocalFolder(dir: File, context: Context) {
        if (dir.isDirectory) {
            _currentLocalDirectory.value = dir
            refreshLocalFiles(context)
        }
    }

    fun navigateUpLocal(context: Context) {
        val current = _currentLocalDirectory.value ?: return
        val root = getLocalRootDir(context)
        if (current.absolutePath != root.absolutePath) {
            val parent = current.parentFile
            if (parent != null) {
                _currentLocalDirectory.value = parent
                refreshLocalFiles(context)
            }
        }
    }

    fun toggleLocalGridMode() {
        _localGridMode.value = !_localGridMode.value
    }

    fun toggleLocalFileSelection(file: File) {
        val current = _selectedLocalFiles.value.toMutableSet()
        if (current.contains(file)) {
            current.remove(file)
        } else {
            current.add(file)
        }
        _selectedLocalFiles.value = current
    }

    fun clearLocalSelection() {
        _selectedLocalFiles.value = emptySet()
    }

    fun createLocalFolder(name: String, context: Context): Boolean {
        val parent = _currentLocalDirectory.value ?: getLocalRootDir(context)
        val newFolder = File(parent, name)
        if (newFolder.exists()) return false
        val success = newFolder.mkdirs()
        if (success) {
            refreshLocalFiles(context)
        }
        return success
    }

    fun renameLocalFile(file: File, newName: String, context: Context): Boolean {
        val dest = File(file.parentFile, newName)
        if (dest.exists()) return false
        val success = file.renameTo(dest)
        if (success) {
            refreshLocalFiles(context)
        }
        return success
    }

    fun deleteSelectedLocal(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val selected = _selectedLocalFiles.value
            for (file in selected) {
                file.deleteRecursively()
            }
            withContext(Dispatchers.Main) {
                _selectedLocalFiles.value = emptySet()
                refreshLocalFiles(context)
            }
        }
    }

    fun zipSelectedLocal(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val selected = _selectedLocalFiles.value.toList()
            if (selected.isEmpty()) return@launch
            try {
                val currentDir = _currentLocalDirectory.value ?: getLocalRootDir(context)
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val zipFile = File(currentDir, "DirectShare_$timeStamp.zip")

                ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                    for (file in selected) {
                        zipLocalFileOrFolder(file, file.name, zos)
                    }
                }
                withContext(Dispatchers.Main) {
                    _selectedLocalFiles.value = emptySet()
                    refreshLocalFiles(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to zip", e)
            }
        }
    }

    private fun zipLocalFileOrFolder(file: File, parentPath: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            if (children.isEmpty()) {
                zos.putNextEntry(ZipEntry("$parentPath/"))
                zos.closeEntry()
            } else {
                for (child in children) {
                    zipLocalFileOrFolder(child, "$parentPath/${child.name}", zos)
                }
            }
        } else {
            zos.putNextEntry(ZipEntry(parentPath))
            file.inputStream().use { input ->
                input.copyTo(zos)
            }
            zos.closeEntry()
        }
    }

    // --- Remote PC Files Browsing ("PC Files") ---
    private fun getClientAuthHeader(): String {
        return okhttp3.Credentials.basic(pcUsername.value, pcToken.value)
    }

    fun testPCConnection() {
        val ip = pcIp.value
        val port = pcPort.value.toIntOrNull() ?: 8080
        val auth = getClientAuthHeader()

        _isRemoteLoading.value = true
        _remoteError.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val isLive = client.ping(ip, port)
            withContext(Dispatchers.Main) {
                if (isLive) {
                    isPcConnected.value = true
                    _currentRemoteDirectory.value = "/"
                    refreshRemoteFiles()
                } else {
                    isPcConnected.value = false
                    _remoteError.value = "Failed to ping companion PC. Check IP, Port, and USB Tethering link."
                    _isRemoteLoading.value = false
                }
            }
        }
    }

    fun refreshRemoteFiles() {
        val ip = pcIp.value
        val port = pcPort.value.toIntOrNull() ?: 8080
        val path = _currentRemoteDirectory.value
        val auth = getClientAuthHeader()

        _isRemoteLoading.value = true
        _remoteError.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = client.listFiles(ip, port, path, auth)
                val sorted = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                withContext(Dispatchers.Main) {
                    _remoteFiles.value = sorted
                    _selectedRemoteFiles.value = emptySet()
                    _isRemoteLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching remote files", e)
                withContext(Dispatchers.Main) {
                    _remoteError.value = e.message ?: "Failed to list remote files"
                    _isRemoteLoading.value = false
                }
            }
        }
    }

    fun navigateIntoRemoteFolder(folderName: String) {
        val current = _currentRemoteDirectory.value
        val newPath = if (current.endsWith("/")) "$current$folderName" else "$current/$folderName"
        _currentRemoteDirectory.value = newPath
        refreshRemoteFiles()
    }

    fun navigateUpRemote() {
        val current = _currentRemoteDirectory.value
        if (current == "/") return
        val lastSlash = current.lastIndexOf('/')
        val newPath = if (lastSlash == 0) "/" else current.substring(0, lastSlash)
        _currentRemoteDirectory.value = newPath
        refreshRemoteFiles()
    }

    fun toggleRemoteGridMode() {
        _remoteGridMode.value = !_remoteGridMode.value
    }

    fun toggleRemoteFileSelection(file: RemoteFile) {
        val current = _selectedRemoteFiles.value.toMutableSet()
        if (current.contains(file)) {
            current.remove(file)
        } else {
            current.add(file)
        }
        _selectedRemoteFiles.value = current
    }

    fun clearRemoteSelection() {
        _selectedRemoteFiles.value = emptySet()
    }

    fun createRemoteFolder(name: String) {
        val ip = pcIp.value
        val port = pcPort.value.toIntOrNull() ?: 8080
        val auth = getClientAuthHeader()
        val current = _currentRemoteDirectory.value
        val fullPath = if (current.endsWith("/")) "$current$name" else "$current/$name"

        viewModelScope.launch(Dispatchers.IO) {
            try {
                client.createDirectory(ip, port, fullPath, auth)
                withContext(Dispatchers.Main) {
                    refreshRemoteFiles()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Create remote directory failed", e)
            }
        }
    }

    fun renameRemoteFile(originalName: String, newName: String) {
        val ip = pcIp.value
        val port = pcPort.value.toIntOrNull() ?: 8080
        val auth = getClientAuthHeader()
        val current = _currentRemoteDirectory.value
        val oldPath = if (current.endsWith("/")) "$current$originalName" else "$current/$originalName"
        val newPath = if (current.endsWith("/")) "$current$newName" else "$current/$newName"

        viewModelScope.launch(Dispatchers.IO) {
            try {
                client.rename(ip, port, oldPath, newPath, auth)
                withContext(Dispatchers.Main) {
                    refreshRemoteFiles()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Rename remote failed", e)
            }
        }
    }

    fun deleteSelectedRemote() {
        val ip = pcIp.value
        val port = pcPort.value.toIntOrNull() ?: 8080
        val auth = getClientAuthHeader()
        val current = _currentRemoteDirectory.value
        val selected = _selectedRemoteFiles.value.toList()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                for (file in selected) {
                    val fullPath = if (current.endsWith("/")) "$current${file.name}" else "$current/${file.name}"
                    client.delete(ip, port, fullPath, auth)
                }
                withContext(Dispatchers.Main) {
                    _selectedRemoteFiles.value = emptySet()
                    refreshRemoteFiles()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Delete remote failed", e)
            }
        }
    }

    // --- File Sync / File Transfers ---
    fun downloadSelectedRemote(context: Context) {
        val ip = pcIp.value
        val port = pcPort.value.toIntOrNull() ?: 8080
        val auth = getClientAuthHeader()
        val selected = _selectedRemoteFiles.value.toList()
        val localDestDir = _currentLocalDirectory.value ?: getLocalRootDir(context)

        viewModelScope.launch(Dispatchers.IO) {
            for (file in selected) {
                if (file.isDirectory) continue // simple files download
                val taskId = UUID.randomUUID().toString()
                val remotePath = if (_currentRemoteDirectory.value.endsWith("/")) {
                    "${_currentRemoteDirectory.value}${file.name}"
                } else {
                    "${_currentRemoteDirectory.value}/${file.name}"
                }
                val localFile = File(localDestDir, file.name)

                // Track transfer state
                val task = TransferTask(
                    id = taskId,
                    fileName = file.name,
                    progress = 0f,
                    isUpload = false,
                    status = "Running"
                )
                addTransferTask(task)

                try {
                    client.downloadFile(ip, port, remotePath, localFile, auth) { written, total ->
                        updateTransferProgress(taskId, written, total)
                    }
                    updateTransferStatus(taskId, "Completed")
                } catch (e: Exception) {
                    Log.e(TAG, "Download failed for ${file.name}", e)
                    updateTransferStatus(taskId, "Failed")
                }
            }
            withContext(Dispatchers.Main) {
                _selectedRemoteFiles.value = emptySet()
                refreshLocalFiles(context)
            }
        }
    }

    fun uploadSelectedLocal(context: Context) {
        val ip = pcIp.value
        val port = pcPort.value.toIntOrNull() ?: 8080
        val auth = getClientAuthHeader()
        val selected = _selectedLocalFiles.value.toList()
        val remoteDestDir = _currentRemoteDirectory.value

        viewModelScope.launch(Dispatchers.IO) {
            for (file in selected) {
                if (file.isDirectory) continue // simple files upload
                val taskId = UUID.randomUUID().toString()
                val remotePath = if (remoteDestDir.endsWith("/")) {
                    "$remoteDestDir${file.name}"
                } else {
                    "$remoteDestDir/${file.name}"
                }

                // Track transfer state
                val task = TransferTask(
                    id = taskId,
                    fileName = file.name,
                    progress = 0f,
                    isUpload = true,
                    status = "Running"
                )
                addTransferTask(task)

                try {
                    client.uploadFile(ip, port, remotePath, file, auth) { written, total ->
                        updateTransferProgress(taskId, written, total)
                    }
                    updateTransferStatus(taskId, "Completed")
                } catch (e: Exception) {
                    Log.e(TAG, "Upload failed for ${file.name}", e)
                    updateTransferStatus(taskId, "Failed")
                }
            }
            withContext(Dispatchers.Main) {
                _selectedLocalFiles.value = emptySet()
                refreshRemoteFiles()
            }
        }
    }

    private fun addTransferTask(task: TransferTask) {
        _activeTransfers.value = _activeTransfers.value + task
    }

    private fun updateTransferProgress(id: String, written: Long, total: Long) {
        val fraction = if (total > 0L) written.toFloat() / total.toFloat() else 0.5f
        _activeTransfers.value = _activeTransfers.value.map {
            if (it.id == id) it.copy(progress = fraction) else it
        }
    }

    private fun updateTransferStatus(id: String, status: String) {
        _activeTransfers.value = _activeTransfers.value.map {
            if (it.id == id) it.copy(status = status) else it
        }
    }

    fun removeTransferTask(id: String) {
        _activeTransfers.value = _activeTransfers.value.filterNot { it.id == id }
    }

    fun clearCompletedTransfers() {
        _activeTransfers.value = _activeTransfers.value.filter { it.status == "Running" || it.status == "Queued" }
    }
}
