package me.gly.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaRecorder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.*
import android.widget.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.media.MediaMetadataRetriever

import android.R.string.no




class MainActivity : AppCompatActivity() {

    private var mSurfaceView: SurfaceView? = null
    private var mSurfaceHolder: SurfaceHolder? = null
    private var info: TextView? = null
    private var lastViewer: ImageView? = null
    private var editText: EditText? = null
    private var changeViewBtn: ImageButton? = null
    private var startBtn: ImageButton? = null
    private var recoudFlag: Boolean = false //标记：标记是否已经进入录像模式
    private var clickTime: Long = 0  //计时器：记录手指按下拍照键的时间

    private var mCamera: Camera? = null
    private var mCameraIndex: Int = 0
    private var mMediaRecorder: MediaRecorder? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mSurfaceView = findViewById(R.id.view)
        editText = findViewById(R.id.path)
        changeViewBtn = findViewById(R.id.changeViewBtn)
        startBtn = findViewById(R.id.startBtn)
        info = findViewById(R.id.info)
        lastViewer = findViewById(R.id.lastViewer)

        startBtn?.setOnTouchListener(
        //input: View: The view the touch event has been dispatched to.
        //input: MotionEvent: The MotionEvent object containing full information about the event.
        //returns: boolean: True if the listener has consumed the event, false otherwise.
        object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                //ACTION_MOVE：当触点在屏幕上移动时触发，触点在屏幕上停留也是会触发
                //ACTION_DOWN：当屏幕检测到第一个触点按下之后就会触发到这个事件
                //ACTION_UP：当触点松开时被触发
                if(event.action == MotionEvent.ACTION_DOWN){
                    // 按下拍照键，开始识别是短按还是长按
                    clickTime = Date().time
                    recoudFlag = false
                } else if(event.action == MotionEvent.ACTION_MOVE){
                    // 如果按下时间超过100ms，识别为开始录像
                    if(!recoudFlag && Date().time - clickTime >= 100){
                        recoudFlag = true
                        startRecordVideo()
                    }
                }else if(event.action == MotionEvent.ACTION_UP){
                    if(!recoudFlag || Date().time - clickTime < 100){
                        startTakePicture()
                    }
                    else if(recoudFlag){
                        //录像状态则停止录像
                        endRecordVideo()
                    }
                }
                return true
            }
        })
        changeViewBtn?.setOnClickListener(View.OnClickListener { changeView() })
        startCamera(mCameraIndex)
        showInfo()
    }

    private fun showInfo(){
        val mediaStorageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (!mediaStorageDir!!.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return
            }
        }

        var latestModifiedTime:Long = 0
        var latestModifiedFile:File? = null
        //lastModified()可以获得文件最后修改的时间，找到最后修改时间最大的文件即为最后拍摄
        for(file:File in mediaStorageDir.listFiles()){
            if(file.lastModified() > latestModifiedTime){
                latestModifiedTime = file.lastModified()
                latestModifiedFile = file
            }
        }

        if(latestModifiedFile == null){
            info?.text = "No video or picture find!"
        }
        else if(latestModifiedFile.name.substring(latestModifiedFile.name.length-3,latestModifiedFile.name.length) == "jpg"){
            showPiture(latestModifiedFile)
        }
        else if(latestModifiedFile.name.substring(latestModifiedFile.name.length-3,latestModifiedFile.name.length) == "mp4"){
            showVideo(latestModifiedFile)
        }
    }

    private fun showPiture(picture: File){
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(picture.absolutePath, options)
        options.inSampleSize = calculateInSampleSize(options, 64, 64)
        options.inJustDecodeBounds = false
        val bitmap = BitmapFactory.decodeFile(picture.absolutePath, options)
        lastViewer?.setImageBitmap(bitmap)
        info?.text = "Name: " + picture.name + "\n" + "Size: " + options.outWidth + "×" + options.outHeight
    }

    //https://cloud.tencent.com/developer/article/1719307
    //使用MediaMetadataRetriever方法
    private fun showVideo(video: File){
        val media = MediaMetadataRetriever()
        media.setDataSource(video.absolutePath);
        val bitmap = media.getFrameAtTime();
        lastViewer?.setImageBitmap(bitmap)
        info?.text = "Name: " + video.name + "\n" + "Duration: " + media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) + " Size: " + media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) + "×" + media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        //返回2的整数次幂
        val width = options.outWidth
        val height = options.outHeight
        var inSampleSize = 1
        if(height > reqHeight || width > reqWidth){
            val halfHeight = height / 2;
            val halfWidth = width / 2;
            while (halfHeight / inSampleSize >= reqHeight || halfWidth / inSampleSize >= reqWidth){
                inSampleSize = inSampleSize * 2
            }
        }
        return inSampleSize
    }

    private fun setCameraDisplayOrientation() {
        val rotation = windowManager.defaultDisplay.rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info)
        val result = (info.orientation - degrees + 360) % 360
        mCamera!!.setDisplayOrientation(result)
    }

    private fun startCamera(mCameraIndex: Int) {
        try {
            mCamera = Camera.open(mCameraIndex)
            setCameraDisplayOrientation()
        } catch (e: Exception) {
            // error
            e.printStackTrace()
        }

        mSurfaceHolder = mSurfaceView!!.holder
        mSurfaceHolder?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                try {
                    mCamera!!.setPreviewDisplay(holder)
                    mCamera!!.startPreview()
                } catch (e: IOException) {
                    // error
                    e.printStackTrace()
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, i: Int, i1: Int, i2: Int) {
                try {
                    mCamera!!.stopPreview()
                } catch (e: Exception) {
                    // error
                    e.printStackTrace()
                }
                try {
                    mCamera!!.setPreviewDisplay(holder)
                    mCamera!!.startPreview()
                } catch (e: Exception) {
                    //error
                    e.printStackTrace()
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })
    }

    private fun releaseCamera() {
        if (mCamera != null) {
            mCamera?.setPreviewCallback(null)
            mCamera?.stopPreview()
            mCamera?.release()
            mCamera = null
        }
    }

    private fun releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder!!.reset()
            mMediaRecorder!!.release()
            mMediaRecorder = null
            mCamera!!.lock()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaRecorder()
        releaseCamera()
    }

    private fun changeView(){
        releaseCamera()
        mCameraIndex = (mCameraIndex +1) % Camera.getNumberOfCameras()
        startCamera(mCameraIndex)
        mCamera?.setPreviewDisplay(mSurfaceView?.holder)
        mCamera?.startPreview()
    }

    private fun startTakePicture(){
        mCamera!!.takePicture(null, null, Camera.PictureCallback { bytes, camera ->
            val pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE) ?: return@PictureCallback
            try {
                val fos = FileOutputStream(pictureFile)
                fos.write(bytes)
                fos.close()
            } catch (e: FileNotFoundException) {
                //error
            } catch (e: IOException) {
                //error
            }
            mCamera!!.startPreview()
        })
        showInfo()
    }

    private fun prepareVideoRecorder(): Boolean {
        mMediaRecorder = MediaRecorder()
        mCamera!!.unlock()
        mMediaRecorder!!.setCamera(mCamera)
        mMediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
        mMediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.CAMERA)
        mMediaRecorder!!.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH))
        mMediaRecorder!!.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO).toString())
        mMediaRecorder!!.setPreviewDisplay(mSurfaceHolder!!.surface)
        try {
            mMediaRecorder!!.prepare()
        } catch (e: IllegalStateException) {
            releaseMediaRecorder()
            return false
        } catch (e: IOException) {
            releaseMediaRecorder()
            return false
        }
        return true
    }

    private fun startRecordVideo(){
        if (recoudFlag) {
            if (prepareVideoRecorder()) {
                Toast.makeText(this, "Start Recoding", Toast.LENGTH_LONG).show()
                mMediaRecorder!!.start()
            }else{
                releaseMediaRecorder()
            }
        } else {
             return
        }
    }

    private fun endRecordVideo(){
        if (recoudFlag) {
            mMediaRecorder!!.stop()
            releaseMediaRecorder()
            mCamera!!.lock()
            recoudFlag = false
            Toast.makeText(this, "End Recoding", Toast.LENGTH_LONG).show()
            showInfo()
        } else {
            return
        }
    }

    private fun getOutputMediaFile(type: Int): File? {
        // Android/data/com.bytedance.camera.demo/files/Pictures
        val mediaStorageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (!mediaStorageDir!!.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null
            }
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val mediaFile: File
        mediaFile = if (type == MEDIA_TYPE_IMAGE) {
            File(mediaStorageDir.path + File.separator + "IMG_" + editText?.text.toString() + "_" + timeStamp + ".jpg")
        } else if (type == MEDIA_TYPE_VIDEO) {
            File(mediaStorageDir.path + File.separator + "VID_" + editText?.text.toString() + "_" + timeStamp + ".mp4")
        } else {
            return null
        }
        Log.e("FileName", mediaFile.toString())
        return mediaFile
    }

    companion object {
        private const val MEDIA_TYPE_IMAGE = 1
        private const val MEDIA_TYPE_VIDEO = 2
    }
}
