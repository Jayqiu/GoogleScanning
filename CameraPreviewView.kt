package com.threehalf.scanning

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 *@author: JayQiu
 *@create: 2023/2/28
 *@description:
 */
class CameraPreviewView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val TAG = CameraPreviewView::class.java.simpleName
    var mPreviewView: PreviewView
    private var mCamera: Camera? = null
    private var mScanner: BarcodeScanner? = null
    private var mCameraAnalysis: ImageAnalysis? = null
    private var mCameraProvider: ProcessCameraProvider? = null

    // 默认使用后置摄像头
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA//当前相机

    private val mPreview by lazy(LazyThreadSafetyMode.NONE) {
        Preview.Builder().setTargetAspectRatio((AspectRatio.RATIO_4_3)).build()
    }
    private var mOnScannerListener: OnScannerListener? = null

    init {
        inflate(context, R.layout.view_preview_view, this)
        mPreviewView = findViewById(R.id.pv_preview)
        val options = BarcodeScannerOptions.Builder().setBarcodeFormats(
            Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC
        ).build()
        mScanner = BarcodeScanning.getClient(options)
    }

    fun startCamera(lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            mCameraProvider = cameraProviderFuture.get()
            startPreview(lifecycleOwner)
        }, ContextCompat.getMainExecutor(context))
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner) {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        startPreview(lifecycleOwner)
    }

    /**
     * 开启预览
     */
    private fun startPreview(lifecycleOwner: LifecycleOwner) {
        try {
            initCameraAnalysis()
            // 解除相机之前的所有绑定
            mCameraProvider?.unbindAll()
            // 绑定前面用于预览和拍照的UseCase到相机上
            mCamera = mCameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, mPreview, mCameraAnalysis)
            // 设置用于预览的view
            mPreview.setSurfaceProvider(mPreviewView.surfaceProvider)

        } catch (exc: Exception) {
            exc.printStackTrace()
            Toast.makeText(context, "相机启动失败，${exc.message}", Toast.LENGTH_SHORT).show()
            mOnScannerListener?.onFailureListener(exc)
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun initCameraAnalysis() {
        mCameraAnalysis = ImageAnalysis.Builder().setTargetResolution(Size(720, 1280))
            // 仅将最新图像传送到分析仪，并在到达图像时将其丢弃。
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).setImageQueueDepth(1).build()
        mCameraAnalysis?.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                mScanner?.process(image)?.addOnSuccessListener { barcodeList ->
                    val barcode = barcodeList.getOrNull(0)
                    // `rawValue` is the decoded value of the barcode
                    barcode?.rawValue?.let { value ->
                        Log.e(TAG, "==value=====$value")
                        mOnScannerListener?.onSuccessListener(value)
                    }
                }?.addOnFailureListener { e ->
                    mOnScannerListener?.onFailureListener(e)
                }?.addOnCompleteListener {
                    mediaImage.close()
                    imageProxy.close()
                }

            }

        }

    }

    fun addOnScannerListener(onScannerListener: OnScannerListener) {
        this.mOnScannerListener = onScannerListener
    }

    interface OnScannerListener {
        fun onSuccessListener(value: String)
        fun onFailureListener(e: Exception)
    }

}