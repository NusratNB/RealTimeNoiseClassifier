package com.example.realtimenoiseclassifier

import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import org.tensorflow.lite.DataType
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SoundClassification {

    private val modelName = "lite-model_yamnet_classification_tflite_1.tflite"
    private val inputAudioLength = 15600 // 0.975 sec
    private val clsNum = 521
    var tfLite: Interpreter? = null

    private fun loadModelFile(assetManager: AssetManager, modelPath: String): MappedByteBuffer? {
        val fileDescriptor: AssetFileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset: Long = fileDescriptor.startOffset
        val declaredLength: Long = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun getModel(activity: AssetManager, modelPath: String): Interpreter? {
        return loadModelFile(activity, modelPath)?.let { Interpreter(it) }
    }
    fun initModel(activity: AssetManager){
        tfLite = getModel(activity, modelName)
    }

    fun makeInference(data: FloatArray): FloatArray {

        var outputs: Unit? = null

        val inputByteBuffer: ByteBuffer = ByteBuffer.allocateDirect(4*inputAudioLength )
        inputByteBuffer.order(ByteOrder.nativeOrder())

        for (i in data.indices){
            inputByteBuffer.putFloat(data[i])
        }

        val outputByteBuffer: ByteBuffer = ByteBuffer.allocate(4*clsNum)
        outputByteBuffer.order(ByteOrder.nativeOrder())

        val audioClip = TensorBuffer.createFixedSize(intArrayOf(clsNum), DataType.FLOAT32)
        audioClip.loadBuffer(outputByteBuffer)
        val inputData = TensorBuffer.createFixedSize(intArrayOf(1, inputAudioLength), DataType.FLOAT32)
        inputData.loadBuffer(inputByteBuffer)

        outputs = tfLite?.run(inputData.buffer, audioClip.buffer)
        val outData = audioClip.floatArray
        return outData

    }

}