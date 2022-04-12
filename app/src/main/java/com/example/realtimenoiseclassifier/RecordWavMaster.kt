package com.example.realtimenoiseclassifier

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.MediaPlayer.OnCompletionListener
import android.text.format.Time
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import java.io.*
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer
import java.nio.ByteOrder


class RecordWavMasterKT(ctx: Context, path: String) {
    val sc = SoundClassification(ctx)
    private var mRecorder: AudioRecord? = null
    var mediaPlayer: MediaPlayer? = null
    var audioTrack: AudioTrack? = null
    var audioName: File? = null
    private var mRecording: File? = null
    private lateinit var mBuffer: ShortArray
    private var audioFilePath: String? = null
    private var mIsRecording = false
    var BUFFER_SIZE_PLAYING = 0
    var isPlayingAudio = false
    var isPlayingMedia = false
    private var playingThread: Thread? = null
    var fileNameMedia: String? = null
    var fileNameAudio: String? = null
    lateinit var sClass: SoundClassification
    private var RECORD_WAV_PATH //= Environment.getExternalStorageDirectory() + File.separator + "AudioRecord";
            : String? = null
    var threshold: Short = 200
    var count = 0

    /* Start AudioRecording */
    fun recordWavStart() {
        mIsRecording = true
        mRecorder!!.startRecording()
        mRecording = getFile("raw")
        startBufferedWrite(mRecording)
        noiseDetect()
    }

    /* Stop AudioRecording */
    fun recordWavStop(): String? {
        try {
            mIsRecording = false
            mRecorder!!.stop()
            audioName = getFile("wav")
            rawToWave(mRecording, audioName)
            Log.e("path_audioFilePath", audioFilePath!!)
            return audioFilePath
        } catch (e: Exception) {
            Log.e("Error saving file : ", e.message!!)
        }
        return null
    }

    private fun searchThreshold(arr: ShortArray, thr: Short): Int {
        val arrLen = arr.size
        var peakIndex: Int = 0
        while (peakIndex < arrLen) {
            if (arr[peakIndex] >= thr || arr[peakIndex] <= -thr) {
                return peakIndex
            }
            peakIndex++
        }
        return -1 //not found
    }

    fun startPlaying(ctx: Context?, id: Int, fileName: File) {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer()
            mediaPlayer!!.setOnCompletionListener { mp ->

                // release resources when end of file is reached
                mp.reset()
                mp.release()
                mediaPlayer = null
                isPlayingMedia = false
            }
            try {
                mediaPlayer!!.setDataSource(fileName.toString())
                mediaPlayer!!.setAudioAttributes(
                    AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                ) // optional step
                mediaPlayer!!.prepare()
                mediaPlayer!!.start()
                Log.d(TAG, "playback started with MediaPlayer")
            } catch (e: IOException) {
                Toast.makeText(ctx, "Couldn't prepare MediaPlayer, IOException", Toast.LENGTH_SHORT)
                    .show()
                Log.e(
                    TAG,
                    "error reading from file while preparing MediaPlayer$e"
                )
            } catch (e: IllegalArgumentException) {
                Toast.makeText(
                    ctx,
                    "Couldn't prepare MediaPlayer, IllegalArgumentException",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e(TAG, "illegal argument given $e")
            }
        }
    }

    //    }
    private fun readAudioDataFromFile(
        ctx: Context,
        filename: File
    ) { // called inside Runnable of playingThread
        var fileInputStream: FileInputStream? = null
        fileInputStream = try {
            FileInputStream(filename)
        } catch (e: IOException) {
            Toast.makeText(ctx, "Couldn't open file input stream, IOException", Toast.LENGTH_SHORT)
                .show()
            Log.e(
                TAG,
                "could not create input stream before using AudioTrack $e"
            )
            e.printStackTrace()
            return
        }
        val data = ByteArray(BUFFER_SIZE_PLAYING / 2)
        var i = 0
        while (isPlayingAudio && i != -1) { // continue until run out of data or user stops playback
            try {
                if (fileInputStream != null) {
                    i = fileInputStream.read(data)
                }
                audioTrack!!.write(data, 0, i)
            } catch (e: IOException) {
                Toast.makeText(
                    ctx,
                    "Couldn't read from file while playing audio, IOException",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e(TAG, "Could not read data $e")
                e.printStackTrace()
                return
            }
        }
        try { // finish file operations
            fileInputStream?.close()
        } catch (e: IOException) {
            Log.e(
                TAG,
                "Could not close file input stream $e"
            )
            e.printStackTrace()
            return
        }

        // clean up resources
        isPlayingAudio = false
        audioTrack!!.stop()
        audioTrack!!.release()
        audioTrack = null
        playingThread = null
    }

    private fun stopPlaying(id: Int) {
        isPlayingAudio = false // will trigger playingThread to exit while loop
        //        }
    }

    /* Release device MIC */
    fun releaseRecord() {
        mRecorder!!.release()
    }

    /* Initializing AudioRecording MIC */
    private fun initRecorder(ctx: Context, path: String) {
        RECORD_WAV_PATH = path
        sClass = SoundClassification(ctx)
        SAMPLE_RATE = validSampleRates
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        BUFFER_SIZE_PLAYING = bufferSize
        mBuffer = ShortArray(bufferSize)
        if (ActivityCompat.checkSelfPermission(
                ctx,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
        }
        mRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, bufferSize
        )
        File(RECORD_WAV_PATH).mkdir()
    }

    private fun noiseDetect(){
        Thread {
            val inputAudioLength = 15600
            var stepSize = 1600

            lateinit var slicedData: Array<ShortArray>
            val currentAudioLength = mBuffer.size
            var localNumFrames = 1

            if(currentAudioLength > inputAudioLength){
                localNumFrames = (currentAudioLength - inputAudioLength) / stepSize

                slicedData = Array(localNumFrames){ShortArray(inputAudioLength)}
                for (i in 0 until (localNumFrames)){
                    slicedData[i] = mBuffer.slice(i*stepSize until inputAudioLength + i*stepSize).toShortArray()
                }
                for (i in 0 until localNumFrames){
                    val predClass = sc.makeInference(slicedData[i])
                    Log.d("Inference result $i", predClass)
                }
            }else if (currentAudioLength == inputAudioLength){

                slicedData = Array(localNumFrames){ShortArray(inputAudioLength)}

                for (i in 0 until (localNumFrames)){
                    slicedData[i] = mBuffer.slice(i*stepSize until inputAudioLength + i*stepSize).toShortArray()
                }
                for (i in 0 until localNumFrames){
                    val predClass = sc.makeInference(slicedData[i])
                    Log.d("Inference result $i", predClass)
                }
            }



        }.start()
    }


    /* Writing RAW file */
    private fun startBufferedWrite(file: File?) {
        Thread {
            var output: DataOutputStream? = null
            try {
                output = DataOutputStream(
                    BufferedOutputStream(
                        FileOutputStream(file)
                    )
                )
                while (mIsRecording) {
                    var sum = 0.0
                    val readSize = mRecorder!!.read(mBuffer, 0, mBuffer.size)


                    val foundPeak = searchThreshold(mBuffer, threshold)

                    // (readsize-init_val) / step = 10
                    for (i in 0 until readSize ) {
                        if (foundPeak > -1) {
                            output.writeShort(mBuffer[i].toInt())
                            sum += (mBuffer[i] * mBuffer[i]).toDouble()
                            count +=1
                        }

                    }

                    if (readSize > 0) {
                        val amplitude = sum / readSize
                    }
                   // if ((readSize-init_val))
                }
            } catch (e: IOException) {
                Log.e("Error writing file : ", (e.message)!!)
            } finally {
                if (output != null) {
                    try {
                        output.flush()
                    } catch (e: IOException) {
                        Log.e("Error writing file : ", (e.message)!!)
                    } finally {
                        try {
                            output.close()
                        } catch (e: IOException) {
                            Log.e("Error writing file : ", (e.message)!!)
                        }
                    }
                }
            }
        }.start()
    }

    /* Converting RAW format To WAV Format*/
    @Throws(IOException::class)
    private fun rawToWave(rawFile: File?, waveFile: File?) {
        val rawData = ByteArray(rawFile!!.length().toInt())
        var input: DataInputStream? = null
        try {
            input = DataInputStream(FileInputStream(rawFile))
            input.read(rawData)
        } finally {
            input?.close()
        }
        var output: DataOutputStream? = null
        try {
            output = DataOutputStream(FileOutputStream(waveFile))
            // WAVE header
            writeString(output, "RIFF") // chunk id
            writeInt(output, 36 + rawData.size) // chunk size
            writeString(output, "WAVE") // format
            writeString(output, "fmt ") // subchunk 1 id
            writeInt(output, 16) // subchunk 1 size
            writeShort(output, 1) // audio format (1 = PCM)
            writeShort(output, 1) // number of channels
            writeInt(output, SAMPLE_RATE) // sample rate
            writeInt(output, SAMPLE_RATE * 2) // byte rate
            writeShort(output, 2) // block align
            writeShort(output, 16) // bits per sample
            writeString(output, "data") // subchunk 2 id
            writeInt(output, rawData.size) // subchunk 2 size
            // Audio data (conversion big endian -> little endian)
            val shorts = ShortArray(rawData.size / 2)
            ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()[shorts]
            val bytes = ByteBuffer.allocate(shorts.size * 2)
            for (s in shorts) {
                bytes.putShort(s)
            }
            output.write(bytes.array())
        } finally {
            if (output != null) {
                output.close()
                rawFile.delete()
            }
        }
    }

    /* Get file name */
    private fun getFile(suffix: String): File {
        val time = Time()
        time.setToNow()
        audioFilePath = time.format("%Y%m%d%H%M%S")
        return File(RECORD_WAV_PATH, time.format("%Y%m%d%H%M%S") + "." + suffix)
    }

    @Throws(IOException::class)
    private fun writeInt(output: DataOutputStream, value: Int) {
        output.write(value shr 0)
        output.write(value shr 8)
        output.write(value shr 16)
        output.write(value shr 24)
    }

    @Throws(IOException::class)
    private fun writeShort(output: DataOutputStream, value: Int) {
        output.write(value shr 0)
        output.write(value shr 8)
    }

    @Throws(IOException::class)
    private fun writeString(output: DataOutputStream, value: String) {
        for (element in value) {
            output.write(element.code)
        }
    }

    fun getFileName(time_suffix: String): String {
        return "$RECORD_WAV_PATH$time_suffix.wav"
    }

    val recordingState: Boolean
        get() = mRecorder!!.recordingState != AudioRecord.RECORDSTATE_STOPPED

    companion object {
        private val samplingRates = intArrayOf(16000, 11025, 11000, 8000, 6000)
        var SAMPLE_RATE = 16000
        const val TAG = "RecordWavMaster"

        /* Get Supported Sample Rate */
        val validSampleRates: Int
            get() {
                for (rate in samplingRates) {
                    val bufferSize = AudioRecord.getMinBufferSize(
                        rate,
                        AudioFormat.CHANNEL_CONFIGURATION_DEFAULT,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    if (bufferSize > 0) {
                        return rate
                    }
                }
                return SAMPLE_RATE
            }
    }

    /* Initializing AudioRecording MIC */
    init {
        initRecorder(ctx, path)
    }
}


