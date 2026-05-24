package com.mraulio.gbcameramanager.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AppTask<Params, Progress, Result> {
    private static final String TAG = "AppTask";
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private Future<?> future;

    @SafeVarargs
    public final AppTask<Params, Progress, Result> execute(Params... params) {
        onPreExecute();
        future = EXECUTOR.submit(() -> {
            Result result = null;
            Throwable error = null;
            try {
                result = doInBackground(params);
            } catch (Throwable throwable) {
                error = throwable;
                Log.e(TAG, "Background task failed", throwable);
            }
            Result finalResult = result;
            Throwable finalError = error;
            MAIN_HANDLER.post(() -> {
                if (isCancelled()) {
                    onCancelled(finalResult);
                } else if (finalError != null) {
                    onError(finalError);
                } else {
                    onPostExecute(finalResult);
                }
            });
        });
        return this;
    }

    public final boolean cancel(boolean mayInterruptIfRunning) {
        cancelled.set(true);
        return future == null || future.cancel(mayInterruptIfRunning);
    }

    public final boolean isCancelled() {
        return cancelled.get();
    }

    protected void onPreExecute() {
    }

    protected abstract Result doInBackground(Params... params);

    @SafeVarargs
    protected final void publishProgress(Progress... values) {
        MAIN_HANDLER.post(() -> onProgressUpdate(values));
    }

    protected void onProgressUpdate(Progress... values) {
    }

    protected void onPostExecute(Result result) {
    }

    protected void onError(Throwable throwable) {
    }

    protected void onCancelled(Result result) {
        onCancelled();
    }

    protected void onCancelled() {
    }
}
