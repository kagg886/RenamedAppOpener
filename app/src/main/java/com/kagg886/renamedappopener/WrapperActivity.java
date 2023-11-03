package com.kagg886.renamedappopener;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.kagg886.renamedappopener.databinding.ActivityWrapperBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * @Description
 * @Author kagg886
 * @Date 2023/11/3
 */
public class WrapperActivity extends AppCompatActivity {

    private final Handler toastHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what == -1) {
                finish();
            }
            Toast.makeText(WrapperActivity.this, msg.what, Toast.LENGTH_SHORT).show();
        }
    };
    private ActivityWrapperBinding binding;
    private final Handler allHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            binding.progress.setMax(msg.what);
        }
    };

    private final Handler availableHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            binding.progress.setProgress(binding.progress.getMax() - msg.what);
        }
    };


    @Override
    protected void onCreate(@Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityWrapperBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //检查是否拥有安装apk的权限
        if (!getPackageManager().canRequestPackageInstalls()) {
            ActivityResultLauncher<Intent> request = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult o) {
                    switch (o.getResultCode()) {
                        case Activity.RESULT_OK:
                            installApp();
                            break;
                        case Activity.RESULT_CANCELED:
                            Toast.makeText(WrapperActivity.this, R.string.GRANT_PREMISSION_FAILED_TIPS, Toast.LENGTH_SHORT).show();
                            break;
                    }
                }
            });
            Toast.makeText(this, R.string.PLEASE_GRANT_PERMISSION, Toast.LENGTH_SHORT).show();
            request.launch(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        installApp();
    }

    public void installApp() {
        CompletableFuture.supplyAsync(() -> {
            try (InputStream stream = getContentResolver().openInputStream(getIntent().getData())) {
                //防止输入空文件
                byte[] pre = new byte[3];
                if (stream.read(pre) != 3) {
                    toastHandler.sendEmptyMessage(R.string.FILE_TOO_SMALL);
                    return false;
                }
                //验证zip文件头
                if (pre[0] != 0x50 || pre[1] != 0x4b || pre[2] != 0x03) {
                    toastHandler.sendEmptyMessage(R.string.TARGET_NOT_APK);
                    return false;
                }

                //准备解压
                File out = getExternalCacheDir().toPath().resolve(UUID.randomUUID().toString().replace("-", "") + ".apk").toFile();
                if (!out.createNewFile()) {
                    toastHandler.sendEmptyMessage(R.string.FILE_CREATE_FAILED);
                    return false;
                }

                allHandler.sendEmptyMessage(stream.available());
                //开始解压
                try (FileOutputStream output = new FileOutputStream(out)) {
                    output.write(pre);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = stream.read(buf)) != -1) {
                        availableHandler.sendEmptyMessage(stream.available());
                        output.write(buf, 0, len);
                    }
                }

                //准备安装
                Uri uri = FileProvider.getUriForFile(WrapperActivity.this, getPackageName() + ".fileprovider", out);

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setDataAndType(uri, "application/vnd.android.package-archive");
                startActivity(intent);
            } catch (Exception e) {
                Log.e(WrapperActivity.class.getName(), "parse APK error:", e);
                toastHandler.sendEmptyMessage(R.string.UNKOWN_ERR);
            } finally {
                toastHandler.sendEmptyMessage(-1);
            }
            return true;
        });
    }
}
