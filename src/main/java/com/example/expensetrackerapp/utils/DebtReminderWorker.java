package com.example.expensetrackerapp.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.example.expensetrackerapp.R;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class DebtReminderWorker extends ListenableWorker {

    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;

    public DebtReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);

        FirebaseApp.initializeApp(context);
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        SettableFuture<Result> future = SettableFuture.create();
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            future.set(Result.success());
            Log.d("Debt Alert System", "User not log in");
            return future;
        }


        String userId = user.getUid();

        firestore.collection("users").document(userId).collection("debt")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult()) {
                            String dueDateStr = doc.getString("dueDate");
                            String debtorName = doc.getString("debtorName");
                            String amount = doc.getString("debtAmount");
                            if (dueDateStr == null) continue;

                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                                Date dueDate = sdf.parse(dueDateStr);
                                long diffDays = getDaysDiffFromToday(dueDate);

                                if (diffDays == 0 || diffDays == 1 || diffDays == 2) {
                                    String title = "Debt Due in " + diffDays + " day(s)";
                                    String message = "Debtor: " + debtorName + ", Amount: " + amount;
                                    sendNotification("Debt Due in " + diffDays + " day(s)",
                                            "Debtor: " + debtorName +
                                                    ", Amount: " + amount);
                                    Log.d("DebtReminderWorker", "Worker started");
                                    Log.d("DebtReminderWorker", "User ID: " + userId);
                                    Log.d("DebtReminderWorker", "Notification: " + title + " - " + message);
                                }
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                        }
                        future.set(Result.success());
                    } else {
                        future.set(Result.failure());
                    }
                });

        return future;
    }

    private long getDaysDiffFromToday(Date date) {
        Calendar today = Calendar.getInstance();
        Calendar due = Calendar.getInstance();
        due.setTime(date);

        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        due.set(Calendar.HOUR_OF_DAY, 0);
        due.set(Calendar.MINUTE, 0);
        due.set(Calendar.SECOND, 0);
        due.set(Calendar.MILLISECOND, 0);

        long diffMillis = due.getTimeInMillis() - today.getTimeInMillis();
        return TimeUnit.MILLISECONDS.toDays(diffMillis);
    }

    private void sendNotification(String title, String message) {
        Context context = getApplicationContext();
        String channelId = "debt_reminder";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Debt Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManagerCompat.from(context).notify((int) System.currentTimeMillis(), builder.build());
    }
}
