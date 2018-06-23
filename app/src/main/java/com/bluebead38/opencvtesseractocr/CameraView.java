package com.bluebead38.opencvtesseractocr;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import static com.bluebead38.opencvtesseractocr.MainActivity.sTess;


public class CameraView extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private Mat img_input;
    private static final String TAG = "opencv";
    private CameraBridgeViewBase mOpenCvCameraView;
    private String m_strOcrResult = "";

    private Button mBtnOcrStart;
    private Button mBtnFinish;
    private TextView mTextOcrResult;

    private Bitmap bmp_result;

    private OrientationEventListener mOrientEventListener;

    private Rect mRectRoi;

    private SurfaceView mSurfaceRoi;
    private SurfaceView mSurfaceRoiBorder;

    private int mRoiWidth;
    private int mRoiHeight;
    private int mRoiX;
    private int mRoiY;
    private double m_dWscale;
    private double m_dHscale;

    private View m_viewDeco;
    private int m_nUIOption;
    private RelativeLayout.LayoutParams mRelativeParams;
    private ImageView mImageCapture;
    private Mat m_matRoi;
    private boolean mStartFlag = false;

    // 현재 회전 상태 (하단 Home 버튼의 위치)
    private enum mOrientHomeButton {Right, Bottom, Left, Top}

    private mOrientHomeButton mCurrOrientHomeButton = mOrientHomeButton.Right;


    static final int PERMISSION_REQUEST_CODE = 1;
    String[] PERMISSIONS = {"android.permission.CAMERA"};

    /*
    // cpp 관련 부분. 지금은 필요하지 않음
    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("native-lib");
        System.loadLibrary("imported-lib");
    }
    */

    private boolean hasPermissions(String[] permissions) {
        // 퍼미션 확인
        int result = -1;
        for (int i = 0; i < permissions.length; i++) {
            result = ContextCompat.checkSelfPermission(getApplicationContext(), permissions[i]);
        }
        if (result == PackageManager.PERMISSION_GRANTED) {
            return true;

        } else {
            return false;
        }
    }


    private void requestNecessaryPermissions(String[] permissions) {
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE: {
                //퍼미션을 거절했을 때 메시지 출력 후 종료
                if (!hasPermissions(PERMISSIONS)) {
                    Toast.makeText(getApplicationContext(), "CAMERA PERMISSION FAIL", Toast.LENGTH_LONG).show();
                    finish();
                }
                return;
            }
        }
    }


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    // 퍼미션 확인 후 카메라 활성화
                    if (hasPermissions(PERMISSIONS))
                        mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };


    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_view);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (!hasPermissions(PERMISSIONS)) { //퍼미션 허가를 했었는지 여부를 확인
            requestNecessaryPermissions(PERMISSIONS);//퍼미션 허가안되어 있다면 사용자에게 요청
        } else {
            //이미 사용자에게 퍼미션 허가를 받음.
        }

        // 카메라 설정
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.activity_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setCameraIndex(0); // front-camera(1),  back-camera(0)
        mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);


        //뷰 선언
        mBtnOcrStart = (Button) findViewById(R.id.btn_ocrstart);
        mBtnFinish = (Button) findViewById(R.id.btn_finish);

        mTextOcrResult = (TextView) findViewById(R.id.text_ocrresult);

        mSurfaceRoi = (SurfaceView) findViewById(R.id.surface_roi);
        mSurfaceRoiBorder = (SurfaceView) findViewById(R.id.surface_roi_border);

        mImageCapture = (ImageView) findViewById(R.id.image_capture);

        //풀스크린 상태 만들기 (상태바, 네비게이션바 없애고 고정시키기)
        m_viewDeco = getWindow().getDecorView();
        m_nUIOption = getWindow().getDecorView().getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            m_nUIOption |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            m_nUIOption |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            m_nUIOption |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        m_viewDeco.setSystemUiVisibility(m_nUIOption);


        mOrientEventListener = new OrientationEventListener(this,
                SensorManager.SENSOR_DELAY_NORMAL) {

            @Override
            public void onOrientationChanged(int arg0) {

                //방향센서값에 따라 화면 요소들 회전

                // 0˚ (portrait)
                if (arg0 >= 315 || arg0 < 45) {
                    rotateViews(270);
                    mCurrOrientHomeButton = mOrientHomeButton.Bottom;
                    // 90˚
                } else if (arg0 >= 45 && arg0 < 135) {
                    rotateViews(180);
                    mCurrOrientHomeButton = mOrientHomeButton.Left;
                    // 180˚
                } else if (arg0 >= 135 && arg0 < 225) {
                    rotateViews(90);
                    mCurrOrientHomeButton = mOrientHomeButton.Top;
                    // 270˚ (landscape)
                } else {
                    rotateViews(0);
                    mCurrOrientHomeButton = mOrientHomeButton.Right;
                }


                //ROI 선 조정
                mRelativeParams = new RelativeLayout.LayoutParams(mRoiWidth + 5, mRoiHeight + 5);
                mRelativeParams.setMargins(mRoiX, mRoiY, 0, 0);
                mSurfaceRoiBorder.setLayoutParams(mRelativeParams);

                //ROI 영역 조정
                mRelativeParams = new RelativeLayout.LayoutParams(mRoiWidth - 5, mRoiHeight - 5);
                mRelativeParams.setMargins(mRoiX + 5, mRoiY + 5, 0, 0);
                mSurfaceRoi.setLayoutParams(mRelativeParams);

            }
        };

        //방향센서 핸들러 활성화
        mOrientEventListener.enable();

        //방향센서 인식 오류 시, Toast 메시지 출력 후 종료
        if (!mOrientEventListener.canDetectOrientation()) {
            Toast.makeText(this, "Can't Detect Orientation",
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    public void onClickButton(View v) {

        switch (v.getId()) {

            //Start 버튼 클릭 시
            case R.id.btn_ocrstart:
                if (!mStartFlag) {
                    // 인식을 새로 시작하는 경우

                    // 버튼 속성 변경
                    mBtnOcrStart.setEnabled(false);
                    mBtnOcrStart.setText("Working...");
                    mBtnOcrStart.setTextColor(Color.LTGRAY);

                    bmp_result = Bitmap.createBitmap(m_matRoi.cols(), m_matRoi.rows(), Bitmap.Config.ARGB_8888);

                    Utils.matToBitmap(m_matRoi, bmp_result);

                    // 캡쳐한 이미지를 ROI 영역 안에 표시
                    mImageCapture.setVisibility(View.VISIBLE);
                    mImageCapture.setImageBitmap(bmp_result);


                    //Orientation에 따라 Bitmap 회전 (landscape일 때는 미수행)
                    if (mCurrOrientHomeButton != mOrientHomeButton.Right) {
                        switch (mCurrOrientHomeButton) {
                            case Bottom:
                                bmp_result = GetRotatedBitmap(bmp_result, 90);
                                break;
                            case Left:
                                bmp_result = GetRotatedBitmap(bmp_result, 180);
                                break;
                            case Top:
                                bmp_result = GetRotatedBitmap(bmp_result, 270);
                                break;
                        }
                    }

                    new AsyncTess().execute(bmp_result);
                } else {
                    //Retry를 눌렀을 경우

                    // ImageView에서 사용한 캡쳐이미지 제거
                    mImageCapture.setImageBitmap(null);
                    mTextOcrResult.setText(R.string.ocr_result_tip);

                    mBtnOcrStart.setEnabled(true);
                    mBtnOcrStart.setText("Start");
                    mBtnOcrStart.setTextColor(Color.WHITE);

                    mStartFlag = false;
                }

                break;


            // 뒤로 버튼 클릭 시
            case R.id.btn_finish:
                //인식 결과물을 MainActivity에 전달하고 종료
                Intent intent = getIntent();
                intent.putExtra("STRING_OCR_RESULT", m_strOcrResult);
                setResult(RESULT_OK, intent);
                mOpenCvCameraView.disableView();
                finish();
                break;
        }
    }

    public void rotateViews(int degree) {
        mBtnOcrStart.setRotation(degree);
        mBtnFinish.setRotation(degree);
        mTextOcrResult.setRotation(degree);

        switch (degree) {
            // 가로
            case 0:
            case 180:

                //ROI 크기 조정 비율 변경
                m_dWscale = (double) 1 / 2;
                m_dHscale = (double) 1 / 2;


                //결과 TextView 위치 조정
                mRelativeParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                mRelativeParams.setMargins(0, convertDpToPixel(20), 0, 0);
                mRelativeParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
                mTextOcrResult.setLayoutParams(mRelativeParams);

                break;

            // 세로
            case 90:
            case 270:

                m_dWscale = (double) 1 / 4;    //h (반대)
                m_dHscale = (double) 3 / 4;    //w

                mRelativeParams = new RelativeLayout.LayoutParams(convertDpToPixel(300), ViewGroup.LayoutParams.WRAP_CONTENT);
                mRelativeParams.setMargins(convertDpToPixel(15), 0, 0, 0);
                mRelativeParams.addRule(RelativeLayout.CENTER_VERTICAL);
                mTextOcrResult.setLayoutParams(mRelativeParams);


                break;
        }
    }

    //dp 단위로 입력하기 위한 변환 함수 (px 그대로 사용 시 기기마다 화면 크기가 다르기 때문에 다른 위치에 가버림)
    public int convertDpToPixel(float dp) {

        Resources resources = getApplicationContext().getResources();

        DisplayMetrics metrics = resources.getDisplayMetrics();

        float px = dp * (metrics.densityDpi / 160f);

        return (int) px;

    }

    public synchronized static Bitmap GetRotatedBitmap(Bitmap bitmap, int degrees) {
        if (degrees != 0 && bitmap != null) {
            Matrix m = new Matrix();
            m.setRotate(degrees, (float) bitmap.getWidth() / 2, (float) bitmap.getHeight() / 2);
            try {
                Bitmap b2 = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
                if (bitmap != b2) {
                    //bitmap.recycle(); (일반적으로는 하는게 옳으나, ImageView에 쓰이는 Bitmap은 recycle을 하면 오류가 발생함.)
                    bitmap = b2;
                }
            } catch (OutOfMemoryError ex) {
                // We have no memory to rotate. Return the original bitmap.
            }
        }

        return bitmap;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "onResume :: Internal OpenCV library not found.");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "onResume :: OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();

        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        // 프레임 획득
        img_input = inputFrame.rgba();


        // 가로, 세로 사이즈 획득
        mRoiWidth = (int) (img_input.size().width * m_dWscale);
        mRoiHeight = (int) (img_input.size().height * m_dHscale);


        // 사이즈로 중심에 맞는 X , Y 좌표값 계산
        mRoiX = (int) (img_input.size().width - mRoiWidth) / 2;
        mRoiY = (int) (img_input.size().height - mRoiHeight) / 2;

        // ROI 영역 생성
        mRectRoi = new Rect(mRoiX, mRoiY, mRoiWidth, mRoiHeight);


        // ROI 영역 흑백으로 전환
        m_matRoi = img_input.submat(mRectRoi);
        Imgproc.cvtColor(m_matRoi, m_matRoi, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.cvtColor(m_matRoi, m_matRoi, Imgproc.COLOR_GRAY2RGBA);
        m_matRoi.copyTo(img_input.submat(mRectRoi));
        return img_input;
    }


    private class AsyncTess extends AsyncTask<Bitmap, Integer, String> {

        @Override
        protected String doInBackground(Bitmap... mRelativeParams) {
            //Tesseract OCR 수행
            sTess.setImage(bmp_result);

            return sTess.getUTF8Text();
        }

        protected void onPostExecute(String result) {
            //완료 후 버튼 속성 변경 및 결과 출력

            mBtnOcrStart.setEnabled(true);
            mBtnOcrStart.setText("Retry");
            mBtnOcrStart.setTextColor(Color.WHITE);

            mStartFlag = true;

            m_strOcrResult = result;
            mTextOcrResult.setText(m_strOcrResult);

        }
    }
}