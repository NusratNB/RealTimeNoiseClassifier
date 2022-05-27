package com.example.realtimenoiseclassifier

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import org.w3c.dom.Text
import java.io.File

class MainActivity : AppCompatActivity() {

    lateinit var btnRecord: Button
    lateinit var btnPlay: Button
    lateinit var pathToRecords: File
    lateinit var fileName: File
    lateinit var fullAudioPath: File
    lateinit var txtViewOutput: TextView


    private val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()){
                permissions ->
            permissions.entries.forEach{
                val permissionName = it.key
                val isGranted = it.value
                if (isGranted){
                    Toast.makeText(this@MainActivity, "Permission is granted",
                        Toast.LENGTH_SHORT).show()
                }else{
                    if (permissionName== Manifest.permission.READ_EXTERNAL_STORAGE){
                        Toast.makeText(this@MainActivity, "Storage reading denied",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { // get permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO),200);
            requestStoragePermission()
        }
        pathToRecords = File(externalCacheDir?.absoluteFile, "AudioRecord" )
        if (!pathToRecords.exists()){
            pathToRecords.mkdir()
        }

        val audioRecorder = RecordWavMasterKT(this, pathToRecords.toString())
        val audioRecorderJV = RecordWavMaster(this, pathToRecords.toString())
        var recording = true
//        audioRecoder.initRecorder(this, pathToRecords.toString())

        txtViewOutput = findViewById(R.id.txtOutput)
        btnRecord = findViewById(R.id.btnRecord)
        btnRecord.text = "Start"
        btnRecord.setOnClickListener{

            if (recording){
//                audioRecorderJV.recordWavStart()
                audioRecorder.recordWavStart()
//                txtViewOutput.text = audioRecoder.noiseDetect()
                btnRecord.text = "Recording"
                recording = false

            }else{
//                audioRecorderJV.recordWavStop()
                audioRecorder.recordWavStop()
                btnRecord.text = "Start"
                recording = true
//                fileName = audioRecorderJV.audioName!!
                fileName = audioRecorder.audioName!!
                fullAudioPath = File(fileName.toString()) //File(pathToRecords.toString(), fileName.toString())
//                if (prevFileName.exists()){
//                    prevFileName.delete()
//                }
            }

        }
        btnPlay = findViewById(R.id.btnPlay)
        btnPlay.setOnClickListener{
            if (fullAudioPath.exists()){
//                audioRecorderJV.startPlaying(this, 1, fullAudioPath)
                audioRecorder.startPlaying(this, 1, fullAudioPath)
            }else{
                Toast.makeText(this, "Audio doesn't exists", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRationalDialog( title: String, message: String){
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel"){ dialog, _->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun requestStoragePermission(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
        ){
            showRationalDialog("Kids drawing app needs storage access",
                "For loading image, Kids drawing app needs external storage")
        }else{
            requestPermission.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }
}