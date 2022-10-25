package org.japancv.sdk

import android.content.Context
import okio.buffer
import okio.source
import java.io.*

class FileUtils {
    companion object {
        fun getFileContent(context: Context, fileName: String?): String? {
            var pkg: InputStream? = null
            try {
                pkg = context.assets.open(fileName!!)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            var licenseContent = ""
            try {
                licenseContent = pkg!!.source().buffer().readUtf8()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return licenseContent
        }

        fun getAssertFilePath(context: Context, fileName: String): String? {
            copyFileIfNeed(context, fileName)
            return context.filesDir.absolutePath + "/" + fileName
        }

        private fun copyFileIfNeed(context: Context, fileName: String) {
            var `is`: InputStream? = null
            var os: OutputStream? = null
            try {
                val file = File(context.filesDir, fileName)
                if (file.parentFile?.exists() == false) {
                    if (file.parentFile?.mkdirs() == false) {
                        return
                    }
                }
                `is` = context.assets.open(fileName)
                if (file.length() == `is`.available().toLong()) {
                    return
                }
                os = FileOutputStream(file)
                val buffer = ByteArray(1024)
                var length = `is`.read(buffer)
                while (length > 0) {
                    os.write(buffer, 0, length)
                    length = `is`.read(buffer)
                }
                os.flush()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                if (os != null) {
                    try {
                        os.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                if (`is` != null) {
                    try {
                        `is`.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}