package com.xah.bsdiffs

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

// 食用方法 先下载新的补丁包到Download，补丁包以 目标版本号.patch 的名称，代表目标版本号可以使用这个补丁包，完成后调用mergePatchApk方法，传入新版本号即可
object BsdiffUpdate {
    // C库
    private val bsdiff = BsdiffJNI()
    // 下载目录
    @JvmStatic
    private fun getDownloadDirectory(): File? {
        // 获取设备内部存储的Download文件夹路径
        val downloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadDirectory.exists()) {
            return downloadDirectory
        }
        return null
    }
    // 清除缓存
    @JvmStatic
    private fun deleteFile(path : String?) : Boolean = path?.let {
        val file = File(it)
        if (file.exists()) {
            file.delete()
        } else {
            true
        }
    } ?: true

    @JvmStatic
    private fun getAPK(context: Context, destinationPath : String) : Boolean {
        val apkFile = File(context.packageCodePath)
        val destinationFile = File(destinationPath)

        try {
            FileInputStream(apkFile).use { sourceStream ->
                FileOutputStream(destinationFile).use { destinationStream ->
                    sourceStream.channel.transferTo(0, sourceStream.channel.size(), destinationStream.channel)
                }
            }
            return true
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
    }
    @JvmStatic
    private fun getPackageName(context: Context) : String = context.packageName
    // 安装APK
    fun installNewApk(context: Context,authority : String = ".provider") {
        val packageName = getPackageName(context)
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "${packageName}_new.apk")
        if (!apkFile.exists()) {
            return
        }
        val uri = FileProvider.getUriForFile(context, packageName + authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
    @JvmStatic
    private fun isExistPatch(patchFileName : String) : String? {
        // 判断内部存储的文件下载文件夹是否存在此文件
        // 获取Download目录
        val downloadDir = getDownloadDirectory()
        if (downloadDir != null) {
            // 拼接文件名
            val patchFile = File(downloadDir,patchFileName)
            return patchFile.absolutePath
        } else {
            return null
        }
    }
    // 用于清理缓存APK
    @JvmStatic
    fun deleteCache(context: Context) : Boolean {
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val packageName = getPackageName(context)
        val newFile = "$downloadDir/${packageName}_new.apk"
        val oldPath ="$downloadDir/${packageName}_old.apk"
        return deleteFile(newFile) && deleteFile(oldPath)
    }
    // 从下载路径选择安装包和补丁包
    fun mergePatchApk(
        context: Context,
        patchFileName : String,
        onLoad : (Boolean) -> Unit,
        onSuccess : () -> Unit = { installNewApk(context) },
    ) : Boolean {
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val packageName = getPackageName(context)
        val newFile = "$downloadDir/${packageName}_new.apk"
        // 自己本体安装包
        val oldPath ="$downloadDir/${packageName}_old.apk"
        if(!getAPK(context,oldPath)) {
            return false
        }
        // 下载好的补丁包 规定以 BsdiffTool生成的文件 为名称 "${oldVersion}_to_${newVersion}.patch"
        val patchFile = isExistPatch(patchFileName) ?: return false
        // 开始加载
        onLoad(true)
        bsdiff.merge(oldPath, patchFile,newFile)
        // 清理缓存:即旧文件和补丁包
        deleteFile(oldPath)
        // 结束加载
        onLoad(false)
        // 插入操作 例如默认安装新的安装包
        onSuccess()
        // 安装好再清理补丁
        deleteFile(patchFile)
        return true
        // 自行检查是否包体完整
    }
}
