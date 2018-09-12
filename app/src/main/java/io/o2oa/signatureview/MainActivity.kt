package io.o2oa.signatureview

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*

class MainActivity : AppCompatActivity() {

    private var hasWritePermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btn_main_clear.setOnClickListener {
            sign.clear()
        }

        btn_main_pic.setOnClickListener {
            val bitmap = sign.getSignatureBitmap()
            if (bitmap != null) {
                if (!hasWritePermission) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        hasWritePermission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    }
                }
                if (hasWritePermission) {
                    val filePath = bit2File(bitmap)
                    tv_main_msg.text =  "生成图片成功, 文件路径: $filePath"
                }
            }
        }

        //permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1024)
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1024) {
            permissions.forEachIndexed { index, permission->
                if (permission == Manifest.permission.WRITE_EXTERNAL_STORAGE) {
                    if (grantResults[index] == PackageManager.PERMISSION_GRANTED ) {
                        hasWritePermission = true
                    }
                }
            }
        }

    }

    private fun bit2File(bitmap: Bitmap):String {
        val root = Environment.getExternalStorageDirectory()
                .absolutePath
        val filePath = root + File.separator + "temp_signature.jpg"
        bitmapToFile(bitmap, filePath)

        return filePath
    }

    /**
     * 图片写入文件
     *
     * @param bitmap
     * 图片
     * @param format
     * 类型 png jpg webp
     * @param filePath
     * 文件全路径
     * @return 是否写入成功
     */
    fun bitmapToFile(bitmap: Bitmap, filePath: String): Boolean {
        var isSuccess = false

        val file = File(filePath.substring(0,
                filePath.lastIndexOf(File.separator)))
        if (!file.exists()) {
            file.mkdirs()
        }
        var out: OutputStream? = null
        try {
            out = BufferedOutputStream(FileOutputStream(filePath),
                    8 * 1024)
            isSuccess = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } finally {
            if (out!=null) {
                closeIO(out)
            }

        }
        return isSuccess
    }

    /**
     * 关闭流
     *
     * @param closeables
     */
    private fun closeIO(vararg closeables: Closeable) {
        if (closeables.isEmpty()) {
            return
        }
        for (cb in closeables) {
            try {
                if (null == cb) {
                    continue
                }
                cb.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
    }
}
