package com.example.client

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Credentials
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject

data class RemoteFile(
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val mtime: Long
)

class WebdavClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun ping(ip: String, port: Int): Boolean {
        val url = "http://$ip:$port/api/ping"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful && response.body?.string()?.contains("usb_direct_share") == true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun listFiles(ip: String, port: Int, path: String, auth: String): List<RemoteFile> {
        val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
        val url = "http://$ip:$port/api/list?path=$encodedPath"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", auth)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to list files: ${response.code} ${response.message}")
            val bodyStr = response.body?.string() ?: "[]"
            val jsonArray = JSONArray(bodyStr)
            val list = mutableListOf<RemoteFile>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    RemoteFile(
                        name = obj.getString("name"),
                        size = obj.getLong("size"),
                        isDirectory = obj.getBoolean("isDirectory"),
                        mtime = obj.getLong("mtime")
                    )
                )
            }
            return list
        }
    }

    fun downloadFile(
        ip: String,
        port: Int,
        remotePath: String,
        destLocalFile: File,
        auth: String,
        onProgress: (Long, Long) -> Unit
    ) {
        val encodedPath = java.net.URLEncoder.encode(remotePath, "UTF-8")
        val url = "http://$ip:$port/api/download?path=$encodedPath"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", auth)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed: ${response.code} ${response.message}")
            val body = response.body ?: throw IOException("Empty response body")
            val totalBytes = body.contentLength()
            
            destLocalFile.parentFile?.mkdirs()
            FileOutputStream(destLocalFile).use { fos ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                body.byteStream().use { bis ->
                    while (bis.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            onProgress(totalRead, totalBytes)
                        } else {
                            onProgress(totalRead, -1L)
                        }
                    }
                }
                fos.flush()
            }
        }
    }

    fun uploadFile(
        ip: String,
        port: Int,
        remotePath: String,
        localFile: File,
        auth: String,
        onProgress: (Long, Long) -> Unit
    ) {
        val encodedPath = java.net.URLEncoder.encode(remotePath, "UTF-8")
        val url = "http://$ip:$port/api/upload?path=$encodedPath"
        
        val progressRequestBody = object : RequestBody() {
            override fun contentType(): MediaType? {
                return "application/octet-stream".toMediaTypeOrNull()
            }

            override fun contentLength(): Long {
                return localFile.length()
            }

            override fun writeTo(sink: BufferedSink) {
                val fileLength = contentLength()
                var totalWritten = 0L
                localFile.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        sink.write(buffer, 0, bytesRead)
                        totalWritten += bytesRead
                        onProgress(totalWritten, fileLength)
                    }
                }
            }
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", auth)
            .post(progressRequestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Upload failed: ${response.code} ${response.message}")
            }
        }
    }

    fun createDirectory(ip: String, port: Int, path: String, auth: String) {
        val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
        val url = "http://$ip:$port/api/mkdir?path=$encodedPath"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", auth)
            .post(RequestBody.create("application/json".toMediaTypeOrNull(), "{}"))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to create folder: ${response.code} ${response.message}")
        }
    }

    fun rename(ip: String, port: Int, path: String, newPath: String, auth: String) {
        val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
        val encodedNewPath = java.net.URLEncoder.encode(newPath, "UTF-8")
        val url = "http://$ip:$port/api/rename?path=$encodedPath&newPath=$encodedNewPath"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", auth)
            .post(RequestBody.create("application/json".toMediaTypeOrNull(), "{}"))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to rename: ${response.code} ${response.message}")
        }
    }

    fun delete(ip: String, port: Int, path: String, auth: String) {
        val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
        val url = "http://$ip:$port/api/delete?path=$encodedPath"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", auth)
            .post(RequestBody.create("application/json".toMediaTypeOrNull(), "{}"))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to delete: ${response.code} ${response.message}")
        }
    }
}
