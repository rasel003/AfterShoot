package com.aftershoot.declutter.ui.activities

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aftershoot.declutter.db.AfterShootDatabase
import com.aftershoot.declutter.db.Image
import com.aftershoot.declutter.service.ModelRunnerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    enum class DarkModeConfig {
        YES,
        NO,
        FOLLOW_SYSTEM
    }

    private fun shouldEnableDarkMode(darkModeConfig: DarkModeConfig) {
        when (darkModeConfig) {
            DarkModeConfig.YES -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            DarkModeConfig.NO -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            DarkModeConfig.FOLLOW_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    //Execute order 66
    val RQ_CODE_INTRO = 66

    private val imageList = arrayListOf<Image>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                if (ActivityCompat.checkSelfPermission(baseContext, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
                        || ActivityCompat.checkSelfPermission(baseContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                    showSliderAndLogin()
                } else {
                    queryScopedStorage()
                }
            }
        }
    }

    private fun showSliderAndLogin() {
        val introIntent = Intent(this, IntroActivity::class.java)
        startActivityForResult(introIntent, RQ_CODE_INTRO)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RQ_CODE_INTRO && resultCode == Activity.RESULT_OK) {
            queryScopedStorage()
        } else {
            Toast.makeText(this, "Something went wrong, please restart the app!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun queryScopedStorage() {

        val scopedProjection = arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media._ID
        )

        val scopedSortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        val cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                scopedProjection,
                null,
                null,
                scopedSortOrder
        )

        cursor.use {
            it?.let {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn)
                    val size = it.getString(sizeColumn)
                    val date = it.getString(dateColumn)

                    val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                    )
                    imageList.add(Image(contentUri, name, size, date))

                }
            } ?: kotlin.run {
                Log.e("TAG", "Cursor is null!")
            }
        }

        // save all the images into the db
        CoroutineScope(Dispatchers.IO).launch {
            AfterShootDatabase.getDatabase(baseContext)?.getDao()?.insertMultipleImages(imageList)
            startModelRunnerService()
        }
    }

    private fun startModelRunnerService() {
        val intent = Intent(this, ModelRunnerService::class.java)
        Log.e("TAG", "Service status: ${ModelRunnerService.isRunning}")
        if (!ModelRunnerService.isRunning)
            ContextCompat.startForegroundService(this, intent)
        startResultActivity()
    }

    private fun startResultActivity() {
        val intent = Intent(this, ResultActivity::class.java)
        startActivity(intent)
        finish()
    }
}