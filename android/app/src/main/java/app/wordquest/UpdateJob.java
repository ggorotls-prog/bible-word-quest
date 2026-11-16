package app.wordquest;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

// 15분마다 새 버전을 보고, 있으면 「새 버전이 있어요」 알림을 한 번 띄운다(업무 기록 SyncJob의 업데이트 몫과 같음).
public class UpdateJob extends JobService {
    private static final int PERIODIC = 1;

    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        // 폰을 껐다 켜도 남게(setPersisted). 옛 예약(껐다 켜면 사라짐)이 있으면 새로 건다 (2026-11-16)
        android.app.job.JobInfo old = js.getPendingJob(PERIODIC);
        if (old != null && old.isPersisted()) return;
        JobInfo.Builder b = new JobInfo.Builder(PERIODIC, new ComponentName(c, UpdateJob.class))
                .setPeriodic(15 * 60_000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true);
        try { js.schedule(b.build()); } catch (Exception ignored) {}
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            Updater.notifyIfNew(getApplicationContext());
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return true; }
}
