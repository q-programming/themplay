package pl.qprogramming.themplay.util;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.Getter;
import pl.qprogramming.themplay.R;

@Getter
public abstract class AsyncTaskExecutorService<Params, Progress, Result> {

    private final ExecutorService executor;
    private Handler handler;
    protected final StringBuilder logs;
    protected final Context context;
    Dialog dialog;

    protected AsyncTaskExecutorService(StringBuilder logs, Context context) {
        this.logs = logs;
        this.context = context;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(R.layout.progress);
        dialog = builder.create();
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

    }

    public Handler getHandler() {
        if (handler == null) {
            synchronized (AsyncTaskExecutorService.class) {
                handler = new Handler(Looper.getMainLooper());
            }
        }
        return handler;
    }

    /**
     * Shows dialog with please wait message ( can still be dismissed )
     */
    protected void onPreExecute() {
        dialog.show();
    }

    protected abstract Result doInBackground(Params... params);

    /**
     * Hide dialog after all operation was done
     */
    protected void onPostExecute(Result result) {
        dialog.dismiss();
    }

    protected void onProgressUpdate(Progress value) {
    }

    // used for push progress respond to UI
    public void publishProgress(Progress value) {
        getHandler().post(() -> onProgressUpdate(value));
    }

    public void execute() {
        execute(null);
    }

    public void execute(Params... params) {
        getHandler().post(() -> {
            onPreExecute();
            executor.execute(() -> {
                Result result = doInBackground(params);
                getHandler().post(() -> onPostExecute(result));
            });
        });
    }

    public void shutDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public boolean isCancelled() {
        return executor == null || executor.isTerminated() || executor.isShutdown();
    }
}