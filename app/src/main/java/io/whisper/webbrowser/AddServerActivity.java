package io.whisper.webbrowser;

import io.whisper.exceptions.*;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.util.List;

import cn.bingoogolapple.photopicker.activity.BGAPhotoPickerActivity;
import cn.bingoogolapple.qrcode.core.QRCodeView;
import cn.bingoogolapple.qrcode.zxing.QRCodeDecoder;
import cn.bingoogolapple.qrcode.zxing.ZXingView;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;


public class AddServerActivity extends AppCompatActivity
		implements EasyPermissions.PermissionCallbacks, QRCodeView.Delegate  {

	private static final String TAG = AddServerActivity.class.getSimpleName();

	private static final int REQUEST_PERMISSION_CAMERA 		 = 1;
	private static final int REQUEST_PERMISSION_PHOTO_PICKER = 2;
	private static final int REQUEST_PERMISSION_CHOOSE_PHOTO = 100;

	private QRCodeView mQRCodeView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_add_server);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		toolbar.setTitle("添加服务节点");
		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		mQRCodeView = (ZXingView)findViewById(R.id.zxingview);
		mQRCodeView.setDelegate(this);
		mQRCodeView.hiddenScanRect();
	}

	@Override
	protected void onStart() {
		super.onStart();
		startCamera();
	}

	@Override
	protected void onStop() {
		mQRCodeView.stopSpotAndHiddenRect();
		mQRCodeView.stopCamera();
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		mQRCodeView.onDestroy();
		super.onDestroy();
	}

	@Override
	public boolean onSupportNavigateUp() {
		onBackPressed();
		return super.onSupportNavigateUp();
	}

	private void addPfServer(String serverAddress) {
		try {
			PfdAgent.singleton().pairServer(serverAddress);
			Toast.makeText(this, "授权申请已发送", Toast.LENGTH_SHORT).show();
			finish();
		} catch (WhisperException e) {
			Log.i(TAG, String.format("Friend requst 0x%x", e.getErrorCode()));
			e.printStackTrace();

			if (e.getErrorCode() == 0x8100000C) {
				Toast.makeText(this, "已添加过该设备", Toast.LENGTH_SHORT).show();
				finish();
			} else {
				Toast.makeText(this, "验证设备失败", Toast.LENGTH_SHORT).show();
				mQRCodeView.startSpot();
			}
		}
	}

	@Override
	public void onScanQRCodeSuccess(String qrCode) {
		Log.i(TAG, "Scanned QRCode is: " + qrCode);
		addPfServer(qrCode);
	}

	@Override
	public void onScanQRCodeOpenCameraError() {
		Log.e(TAG, "Opening camera error");
		Toast.makeText(this, "打开相机出错，请检查权限设置", Toast.LENGTH_SHORT).show();
	}

	@AfterPermissionGranted(REQUEST_PERMISSION_CAMERA)
	private void startCamera() {
		String[] perms = {Manifest.permission.CAMERA};
		if (EasyPermissions.hasPermissions(this, perms)) {
			mQRCodeView.startSpotAndShowRect();
		} else {
			EasyPermissions.requestPermissions(this, "扫描二维码需要使用相机权限",
				REQUEST_PERMISSION_CAMERA, perms);
		}
	}

	@AfterPermissionGranted(REQUEST_PERMISSION_PHOTO_PICKER)
	private void choosePhoto() {
		String[] perms = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
		if (EasyPermissions.hasPermissions(this, perms)) {
			Intent intent = BGAPhotoPickerActivity.newIntent(this, null, 1, null, false);
			startActivityForResult(intent, REQUEST_PERMISSION_CHOOSE_PHOTO);
		} else {
			EasyPermissions.requestPermissions(this, "图片选择需要访问设备上的照片",
				REQUEST_PERMISSION_PHOTO_PICKER, perms);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
	}

	@Override
	public void onPermissionsGranted(int requestCode, List<String> perms) {
	}

	@Override
	public void onPermissionsDenied(int requestCode, List<String> perms) {
		if (requestCode == REQUEST_PERMISSION_CAMERA) {
			Toast.makeText(this, "您拒绝了「二维码扫描」所需要的相关权限!", Toast.LENGTH_SHORT).show();
		}
		else if (requestCode == REQUEST_PERMISSION_PHOTO_PICKER) {
			Toast.makeText(this, "您拒绝了「图片选择」所需要的相关权限!", Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode == Activity.RESULT_OK &&
			requestCode == REQUEST_PERMISSION_CHOOSE_PHOTO) {
			String picturePath = BGAPhotoPickerActivity.getSelectedImages(data).get(0);
			new DecodeQRCodeTask().execute(picturePath);
		}
	}

	private class DecodeQRCodeTask extends AsyncTask<String, Void, String> {
		@Override
		protected String doInBackground(String... params) {
			return QRCodeDecoder.syncDecodeQRCode(params[0]);
		}

		@Override
		protected void onPostExecute(String result) {
			if (TextUtils.isEmpty(result)) {
				Toast.makeText(AddServerActivity.this, "未发现二维码", Toast.LENGTH_SHORT).show();
			} else {
				addPfServer(result);
			}
		}
	}
}
