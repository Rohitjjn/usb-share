package com.example.server

import android.util.Base64
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WebdavServer(
    private val rootDir: File,
    private val port: Int,
    private val username: String,
    private val token: String,
    private val bindIp: String? = null
) {
    private val TAG = "WebdavServer"
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val serverExecutor = java.util.concurrent.Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val serverScope = CoroutineScope(serverExecutor)

    fun start() {
        if (isRunning) return
        isRunning = true
        serverScope.launch {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                
                val bindAddress = if (!bindIp.isNullOrEmpty()) {
                    try {
                        InetSocketAddress(InetAddress.getByName(bindIp), port)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to bind to $bindIp, falling back to 0.0.0.0", e)
                        InetSocketAddress(port)
                    }
                } else {
                    InetSocketAddress(port)
                }
                
                socket.bind(bindAddress)
                serverSocket = socket
                Log.i(TAG, "Server started on ${socket.localSocketAddress}")

                while (isRunning) {
                    val clientSocket = socket.accept()
                    launch(Dispatchers.IO) {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket exception: ${e.message}")
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket: ${e.message}")
        }
        serverSocket = null
        Log.i(TAG, "Server stopped")
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 30000 // 30 sec timeout
            val input = socket.getInputStream()
            
            // Read headers byte-by-byte to preserve EXACT position of body payload start
            val (requestLine, rawHeaders) = readHeaders(input)
            if (requestLine.isEmpty()) {
                sendError(socket, 400, "Bad Request")
                return
            }

            val requestParts = requestLine.split(" ")
            if (requestParts.size < 2) {
                sendError(socket, 400, "Bad Request")
                return
            }

            val rawMethod = requestParts[0].uppercase()
            val fullPathWithQuery = requestParts[1]
            
            val questionIdx = fullPathWithQuery.indexOf('?')
            val requestPath = if (questionIdx != -1) fullPathWithQuery.substring(0, questionIdx) else fullPathWithQuery
            val rawQuery = if (questionIdx != -1) fullPathWithQuery.substring(questionIdx + 1) else null

            // Parse headers into a case-insensitive map
            val headers = mutableMapOf<String, String>()
            for (header in rawHeaders) {
                val colonIdx = header.indexOf(':')
                if (colonIdx != -1) {
                    val key = header.substring(0, colonIdx).trim().lowercase()
                    val value = header.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            val queryParams = parseQueryParams(rawQuery)

            // Handle unauthenticated discovery ping first
            if (rawMethod == "GET" && requestPath == "/api/ping") {
                val responseBody = "{\"status\":\"ok\",\"app\":\"usb_direct_share\"}"
                sendResponse(socket, 200, "OK", "application/json", responseBody.toByteArray(Charsets.UTF_8))
                return
            }

            // Authenticate other endpoints
            if (!authenticate(headers)) {
                sendUnauthorized(socket)
                return
            }

            // Route standard WebDAV and REST APIs
            when {
                // REST API list directory
                rawMethod == "GET" && requestPath == "/api/list" -> {
                    handleApiList(socket, queryParams)
                }
                // REST API download file
                rawMethod == "GET" && requestPath == "/api/download" -> {
                    handleApiDownload(socket, queryParams, headers)
                }
                // REST API upload file
                rawMethod == "POST" && requestPath == "/api/upload" -> {
                    handleApiUpload(socket, queryParams, headers, input)
                }
                // REST API mkdir
                rawMethod == "POST" && requestPath == "/api/mkdir" -> {
                    handleApiMkdir(socket, queryParams)
                }
                // REST API rename
                rawMethod == "POST" && requestPath == "/api/rename" -> {
                    handleApiRename(socket, queryParams)
                }
                // REST API delete
                rawMethod == "POST" && requestPath == "/api/delete" -> {
                    handleApiDelete(socket, queryParams)
                }
                // REST API zip selections
                rawMethod == "POST" && requestPath == "/api/zip" -> {
                    handleApiZip(socket, queryParams)
                }

                // WebDAV OPTIONS
                rawMethod == "OPTIONS" -> {
                    sendOptionsResponse(socket)
                }
                // WebDAV PROPFIND
                rawMethod == "PROPFIND" -> {
                    handlePropfind(socket, requestPath, headers)
                }
                // WebDAV GET
                rawMethod == "GET" -> {
                    handleGet(socket, requestPath, headers)
                }
                // WebDAV PUT (raw upload)
                rawMethod == "PUT" -> {
                    handlePut(socket, requestPath, headers, input)
                }
                // WebDAV MKCOL (create collection)
                rawMethod == "MKCOL" -> {
                    handleMkcol(socket, requestPath)
                }
                // WebDAV DELETE
                rawMethod == "DELETE" -> {
                    handleDelete(socket, requestPath)
                }
                // WebDAV MOVE
                rawMethod == "MOVE" -> {
                    handleMove(socket, requestPath, headers)
                }

                else -> {
                    sendError(socket, 405, "Method Not Allowed")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client request", e)
            try { sendError(socket, 500, "Internal Server Error: ${e.message}") } catch (ex: Exception) {}
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private fun readHeaders(input: InputStream): Pair<String, List<String>> {
        val headerBytes = java.io.ByteArrayOutputStream()
        var state = 0
        while (true) {
            val b = input.read()
            if (b == -1) break
            headerBytes.write(b)
            
            if (state == 0 && b == '\r'.code) state = 1
            else if (state == 1 && b == '\n'.code) state = 2
            else if (state == 2 && b == '\r'.code) state = 3
            else if (state == 3 && b == '\n'.code) {
                break
            } else {
                state = 0
            }
        }
        val headersStr = headerBytes.toString("UTF-8")
        val lines = headersStr.split("\r\n").filter { it.isNotEmpty() }
        if (lines.isEmpty()) return Pair("", emptyList())
        return Pair(lines[0], lines.subList(1, lines.size))
    }

    private fun parseQueryParams(query: String?): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (query.isNullOrEmpty()) return result
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx != -1) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                result[key] = value
            } else {
                val key = URLDecoder.decode(pair, "UTF-8")
                result[key] = ""
            }
        }
        return result
    }

    private fun authenticate(headers: Map<String, String>): Boolean {
        val authHeader = headers["authorization"] ?: return false
        if (!authHeader.startsWith("Basic ", ignoreCase = true)) return false
        return try {
            val base64Credentials = authHeader.substring(6).trim()
            val credentialsBytes = Base64.decode(base64Credentials, Base64.DEFAULT)
            val credentials = String(credentialsBytes, Charsets.UTF_8)
            val values = credentials.split(":", limit = 2)
            if (values.size == 2) {
                val reqUser = values[0]
                val reqToken = values[1]
                reqUser == username && reqToken == token
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun safeResolve(rootDir: File, relativePath: String): File? {
        return try {
            val decoded = URLDecoder.decode(relativePath, "UTF-8")
            val resolved = File(rootDir, decoded).canonicalFile
            if (resolved.absolutePath.startsWith(rootDir.canonicalPath)) {
                resolved
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sendOptionsResponse(socket: Socket) {
        val headers = mapOf(
            "DAV" to "1, 2",
            "Allow" to "OPTIONS, GET, HEAD, PUT, POST, DELETE, PROPFIND, MKCOL, MOVE",
            "Content-Length" to "0"
        )
        sendResponseHeaders(socket, 200, "OK", headers)
    }

    private fun handlePropfind(socket: Socket, path: String, headers: Map<String, String>) {
        val targetFile = safeResolve(rootDir, path)
        if (targetFile == null || !targetFile.exists()) {
            sendError(socket, 404, "Not Found")
            return
        }

        val depth = headers["depth"] ?: "1"
        val filesToList = mutableListOf<File>()
        filesToList.add(targetFile)

        if (depth == "1" && targetFile.isDirectory) {
            val children = targetFile.listFiles()
            if (children != null) {
                filesToList.addAll(children)
            }
        }

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<D:multistatus xmlns:D=\"DAV:\">\n")

        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }

        for (file in filesToList) {
            val relative = file.absolutePath.substring(rootDir.absolutePath.length)
                .replace(File.separatorChar, '/')
            val href = if (relative.isEmpty()) "/" else if (relative.startsWith("/")) relative else "/$relative"
            val encodedHref = href.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

            val displayName = file.name.ifEmpty { "Root" }
            val lastModified = dateFormat.format(Date(file.lastModified()))
            val isDir = file.isDirectory

            sb.append("  <D:response>\n")
            sb.append("    <D:href>$encodedHref</D:href>\n")
            sb.append("    <D:propstat>\n")
            sb.append("      <D:prop>\n")
            sb.append("        <D:displayname>${escapeXml(displayName)}</D:displayname>\n")
            if (isDir) {
                sb.append("        <D:resourcetype><D:collection/></D:resourcetype>\n")
            } else {
                sb.append("        <D:resourcetype/>\n")
                sb.append("        <D:getcontentlength>${file.length()}</D:getcontentlength>\n")
            }
            sb.append("        <D:getlastmodified>$lastModified</D:getlastmodified>\n")
            sb.append("      </D:prop>\n")
            sb.append("      <D:status>HTTP/1.1 200 OK</D:status>\n")
            sb.append("    </D:propstat>\n")
            sb.append("  </D:response>\n")
        }
        sb.append("</D:multistatus>\n")

        val bodyBytes = sb.toString().toByteArray(Charsets.UTF_8)
        sendResponse(socket, 207, "Multi-Status", "application/xml; charset=utf-8", bodyBytes)
    }

    private fun handleGet(socket: Socket, path: String, headers: Map<String, String>) {
        val targetFile = safeResolve(rootDir, path)
        if (targetFile == null || !targetFile.exists()) {
            sendError(socket, 404, "Not Found")
            return
        }

        if (targetFile.isDirectory) {
            sendHtmlFileList(socket, targetFile, path)
            return
        }

        val rangeHeader = headers["range"]
        val fileLength = targetFile.length()
        val contentType = getMimeType(targetFile)

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val rangeStr = rangeHeader.substring(6)
            val parts = rangeStr.split("-")
            var start = 0L
            var end = fileLength - 1

            try {
                if (parts[0].isNotEmpty()) {
                    start = parts[0].toLong()
                }
                if (parts.size > 1 && parts[1].isNotEmpty()) {
                    end = parts[1].toLong()
                }
            } catch (e: Exception) {}

            if (start > end || start >= fileLength) {
                sendResponseHeaders(socket, 416, "Requested Range Not Satisfiable", mapOf(
                    "Content-Range" to "bytes */$fileLength"
                ))
                return
            }

            val contentLength = end - start + 1
            sendResponseHeaders(socket, 206, "Partial Content", mapOf(
                "Content-Type" to contentType,
                "Content-Length" to contentLength.toString(),
                "Content-Range" to "bytes $start-$end/$fileLength",
                "Accept-Ranges" to "bytes"
            ))

            FileInputStream(targetFile).use { input ->
                input.skip(start)
                val buffer = ByteArray(8192)
                var bytesToWrite = contentLength
                val output = socket.getOutputStream()
                while (bytesToWrite > 0) {
                    val toRead = minOf(buffer.size.toLong(), bytesToWrite).toInt()
                    val read = input.read(buffer, 0, toRead)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    bytesToWrite -= read
                }
                output.flush()
            }
        } else {
            sendResponseHeaders(socket, 200, "OK", mapOf(
                "Content-Type" to contentType,
                "Content-Length" to fileLength.toString(),
                "Accept-Ranges" to "bytes"
            ))
            FileInputStream(targetFile).use { input ->
                val buffer = ByteArray(8192)
                val output = socket.getOutputStream()
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
    }

    private fun handlePut(socket: Socket, path: String, headers: Map<String, String>, input: InputStream) {
        val targetFile = safeResolve(rootDir, path)
        if (targetFile == null) {
            sendError(socket, 400, "Bad Request")
            return
        }

        val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L
        targetFile.parentFile?.mkdirs()

        try {
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(8192)
                var totalRead = 0L
                while (totalRead < contentLength) {
                    val toRead = minOf(buffer.size.toLong(), contentLength - totalRead).toInt()
                    val read = input.read(buffer, 0, toRead)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    totalRead += read
                }
            }
            sendResponse(socket, 201, "Created", "text/plain", ByteArray(0))
        } catch (e: Exception) {
            sendError(socket, 500, "Failed to upload file: ${e.message}")
        }
    }

    private fun handleMkcol(socket: Socket, path: String) {
        val targetFile = safeResolve(rootDir, path)
        if (targetFile == null) {
            sendError(socket, 400, "Bad Request")
            return
        }
        if (targetFile.exists()) {
            sendError(socket, 405, "Method Not Allowed (Exists)")
            return
        }
        if (targetFile.mkdirs()) {
            sendResponse(socket, 201, "Created", "text/plain", ByteArray(0))
        } else {
            sendError(socket, 500, "Failed to create directory")
        }
    }

    private fun handleDelete(socket: Socket, path: String) {
        val targetFile = safeResolve(rootDir, path)
        if (targetFile == null || !targetFile.exists()) {
            sendError(socket, 404, "Not Found")
            return
        }
        if (targetFile.deleteRecursively()) {
            sendResponse(socket, 204, "No Content", "text/plain", ByteArray(0))
        } else {
            sendError(socket, 500, "Failed to delete file")
        }
    }

    private fun handleMove(socket: Socket, path: String, headers: Map<String, String>) {
        val sourceFile = safeResolve(rootDir, path)
        if (sourceFile == null || !sourceFile.exists()) {
            sendError(socket, 404, "Not Found")
            return
        }
        val destHeader = headers["destination"]
        if (destHeader == null) {
            sendError(socket, 400, "Missing Destination Header")
            return
        }
        val destPath = extractPathFromUrl(destHeader)
        val destFile = safeResolve(rootDir, destPath)
        if (destFile == null) {
            sendError(socket, 400, "Invalid Destination")
            return
        }
        destFile.parentFile?.mkdirs()
        if (sourceFile.renameTo(destFile)) {
            sendResponse(socket, 201, "Created", "text/plain", ByteArray(0))
        } else {
            sendError(socket, 500, "Failed to move file")
        }
    }

    private fun extractPathFromUrl(urlStr: String): String {
        return try {
            val uri = URI(urlStr)
            uri.path ?: "/"
        } catch (e: Exception) {
            var p = urlStr
            val doubleSlash = p.indexOf("//")
            if (doubleSlash != -1) {
                val nextSlash = p.indexOf('/', doubleSlash + 2)
                if (nextSlash != -1) {
                    p = p.substring(nextSlash)
                }
            }
            p
        }
    }

    // JSON API list directory
    private fun handleApiList(socket: Socket, queryParams: Map<String, String>) {
        val relPath = queryParams["path"] ?: ""
        val targetDir = safeResolve(rootDir, relPath)
        if (targetDir == null || !targetDir.exists()) {
            sendError(socket, 404, "Not Found")
            return
        }
        if (!targetDir.isDirectory) {
            sendError(socket, 400, "Path is not a directory")
            return
        }

        val files = targetDir.listFiles() ?: emptyArray()
        val sb = StringBuilder()
        sb.append("[\n")
        for (i in files.indices) {
            val file = files[i]
            sb.append("  {\n")
            sb.append("    \"name\": \"${escapeJson(file.name)}\",\n")
            sb.append("    \"size\": ${file.length()},\n")
            sb.append("    \"isDirectory\": ${file.isDirectory},\n")
            sb.append("    \"mtime\": ${file.lastModified()}\n")
            sb.append("  }")
            if (i < files.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]")

        sendResponse(socket, 200, "OK", "application/json", sb.toString().toByteArray(Charsets.UTF_8))
    }

    // JSON API download file
    private fun handleApiDownload(socket: Socket, queryParams: Map<String, String>, headers: Map<String, String>) {
        val relPath = queryParams["path"] ?: ""
        handleGet(socket, relPath, headers)
    }

    // JSON API upload file
    private fun handleApiUpload(socket: Socket, queryParams: Map<String, String>, headers: Map<String, String>, input: InputStream) {
        val relPath = queryParams["path"] ?: ""
        if (relPath.isEmpty()) {
            sendError(socket, 400, "Missing path parameter")
            return
        }
        val targetFile = safeResolve(rootDir, relPath)
        if (targetFile == null) {
            sendError(socket, 400, "Invalid Path")
            return
        }
        targetFile.parentFile?.mkdirs()

        val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L
        if (contentLength <= 0L) {
            sendError(socket, 400, "Missing Content-Length")
            return
        }

        try {
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(8192)
                var totalWritten = 0L
                while (totalWritten < contentLength) {
                    val toRead = minOf(buffer.size.toLong(), contentLength - totalWritten).toInt()
                    val read = input.read(buffer, 0, toRead)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    totalWritten += read
                }
            }
            sendResponse(socket, 200, "OK", "application/json", "{\"success\":true}".toByteArray())
        } catch (e: Exception) {
            sendError(socket, 500, "Upload failed: ${e.message}")
        }
    }

    // JSON API mkdir
    private fun handleApiMkdir(socket: Socket, queryParams: Map<String, String>) {
        val relPath = queryParams["path"] ?: ""
        val targetFile = safeResolve(rootDir, relPath)
        if (targetFile == null) {
            sendError(socket, 400, "Invalid Path")
            return
        }
        if (targetFile.exists()) {
            sendError(socket, 400, "Directory already exists")
            return
        }
        if (targetFile.mkdirs()) {
            sendResponse(socket, 200, "OK", "application/json", "{\"success\":true}".toByteArray())
        } else {
            sendError(socket, 500, "Failed to create directory")
        }
    }

    // JSON API rename
    private fun handleApiRename(socket: Socket, queryParams: Map<String, String>) {
        val relPath = queryParams["path"] ?: ""
        val newRelPath = queryParams["newPath"] ?: ""
        val src = safeResolve(rootDir, relPath)
        val dest = safeResolve(rootDir, newRelPath)
        if (src == null || dest == null || !src.exists()) {
            sendError(socket, 400, "Invalid paths or source not found")
            return
        }
        dest.parentFile?.mkdirs()
        if (src.renameTo(dest)) {
            sendResponse(socket, 200, "OK", "application/json", "{\"success\":true}".toByteArray())
        } else {
            sendError(socket, 500, "Failed to rename")
        }
    }

    // JSON API delete
    private fun handleApiDelete(socket: Socket, queryParams: Map<String, String>) {
        val relPath = queryParams["path"] ?: ""
        val target = safeResolve(rootDir, relPath)
        if (target == null || !target.exists()) {
            sendError(socket, 400, "Path not found")
            return
        }
        if (target.deleteRecursively()) {
            sendResponse(socket, 200, "OK", "application/json", "{\"success\":true}".toByteArray())
        } else {
            sendError(socket, 500, "Failed to delete")
        }
    }

    // JSON API zip selections
    private fun handleApiZip(socket: Socket, queryParams: Map<String, String>) {
        val pathsStr = queryParams["paths"] ?: ""
        if (pathsStr.isEmpty()) {
            sendError(socket, 400, "Missing paths parameter")
            return
        }
        val relPaths = pathsStr.split(",")
        val filesToZip = relPaths.mapNotNull { safeResolve(rootDir, it) }.filter { it.exists() }
        if (filesToZip.isEmpty()) {
            sendError(socket, 400, "No files found to zip")
            return
        }

        sendResponseHeaders(socket, 200, "OK", mapOf(
            "Content-Type" to "application/zip",
            "Content-Disposition" to "attachment; filename=\"direct_share.zip\""
        ))

        val output = socket.getOutputStream()
        ZipOutputStream(BufferedOutputStream(output)).use { zos ->
            for (file in filesToZip) {
                zipFileOrFolder(file, file.name, zos)
            }
        }
    }

    private fun zipFileOrFolder(file: File, parentPath: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            if (children.isEmpty()) {
                zos.putNextEntry(ZipEntry("$parentPath/"))
                zos.closeEntry()
            } else {
                for (child in children) {
                    zipFileOrFolder(child, "$parentPath/${child.name}", zos)
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

    private fun sendHtmlFileList(socket: Socket, dir: File, requestPath: String) {
        val files = dir.listFiles() ?: emptyArray()
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><title>USB Direct Share - Browsing ${escapeXml(requestPath)}</title>")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
        sb.append("<style>body { font-family: sans-serif; background: #121212; color: #e0e0e0; padding: 20px; } ")
        sb.append("a { color: #80bfff; text-decoration: none; } a:hover { text-decoration: underline; } ")
        sb.append("table { width: 100%; border-collapse: collapse; margin-top: 20px; } ")
        sb.append("th, td { padding: 10px; text-align: left; border-bottom: 1px solid #333; } ")
        sb.append("th { background: #1f1f1f; }</style></head><body>")
        sb.append("<h1>USB Direct Share Browser</h1>")
        sb.append("<h3>Path: ${escapeXml(requestPath)}</h3>")
        sb.append("<p><a href=\"..\">&uarr; Up One Directory</a></p>")
        sb.append("<table><thead><tr><th>Name</th><th>Size</th><th>Last Modified</th></tr></thead><tbody>")
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (file in files) {
            val suffix = if (file.isDirectory) "/" else ""
            val name = file.name + suffix
            val href = (if (requestPath.endsWith("/")) requestPath else "$requestPath/") + URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
            val size = if (file.isDirectory) "-" else formatBytes(file.length())
            val lastModified = dateFormat.format(Date(file.lastModified()))
            
            sb.append("<tr>")
            sb.append("<td><a href=\"$href\">${escapeXml(name)}</a></td>")
            sb.append("<td>$size</td>")
            sb.append("<td>$lastModified</td>")
            sb.append("</tr>")
        }
        sb.append("</tbody></table></body></html>")

        val bodyBytes = sb.toString().toByteArray(Charsets.UTF_8)
        sendResponse(socket, 200, "OK", "text/html; charset=utf-8", bodyBytes)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1] + ""
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }

    private fun sendResponse(socket: Socket, code: Int, status: String, contentType: String, body: ByteArray) {
        val headers = mapOf(
            "Content-Type" to contentType,
            "Content-Length" to body.size.toString(),
            "Connection" to "close"
        )
        sendResponseHeaders(socket, code, status, headers)
        try {
            socket.getOutputStream().write(body)
            socket.getOutputStream().flush()
        } catch (e: Exception) {}
    }

    private fun sendResponseHeaders(socket: Socket, code: Int, status: String, headers: Map<String, String>) {
        try {
            val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
            writer.write("HTTP/1.1 $code $status\r\n")
            for ((key, value) in headers) {
                writer.write("$key: $value\r\n")
            }
            writer.write("\r\n")
            writer.flush()
        } catch (e: Exception) {}
    }

    private fun sendError(socket: Socket, code: Int, message: String) {
        val body = "<h1>HTTP Error $code: $message</h1>".toByteArray(Charsets.UTF_8)
        sendResponse(socket, code, message, "text/html; charset=utf-8", body)
    }

    private fun sendUnauthorized(socket: Socket) {
        val body = "<h1>401 Unauthorized - USB Direct Share</h1>".toByteArray(Charsets.UTF_8)
        val headers = mapOf(
            "Content-Type" to "text/html",
            "Content-Length" to body.size.toString(),
            "WWW-Authenticate" to "Basic realm=\"USB Direct Share\"",
            "Connection" to "close"
        )
        sendResponseHeaders(socket, 401, "Unauthorized", headers)
        try {
            socket.getOutputStream().write(body)
            socket.getOutputStream().flush()
        } catch (e: Exception) {}
    }

    private fun escapeXml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun getMimeType(file: File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }
    }
}
