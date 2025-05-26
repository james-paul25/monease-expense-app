package com.example.expensetrackerapp.activity;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetrackerapp.R;
import com.example.expensetrackerapp.adapter.CategoryAdapter;
import com.example.expensetrackerapp.item.CategoryItem;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class FinancialReportActivity extends AppCompatActivity {

    private BarChart barChart;
    private RecyclerView recyclerView;
    private CategoryAdapter adapter;
    private List<CategoryItem> categoryList;
    private TextView incomeOrExpense;
    private boolean isExpenseView = true;
    private FirebaseFirestore firestore;
    private Spinner spinnerMonth;
    private FirebaseAuth mAuth;
    private FirebaseUser user;
    private Button btnExpense, btnIncome, btnBack, btnExportPdf;

    private boolean isSpinnerInitialized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.financial_report);

        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        user = mAuth.getCurrentUser();

        barChart = findViewById(R.id.barChart);
        recyclerView = findViewById(R.id.fin_recyclerViewCategories);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        spinnerMonth = findViewById(R.id.fin_spinnerMonth);
        btnExpense = findViewById(R.id.fin_btnExpense);
        btnIncome = findViewById(R.id.fin_btnIncome);
        btnBack = findViewById(R.id.fin_btnBack);
        incomeOrExpense = findViewById(R.id.expenseOrIncome);
        btnExportPdf = findViewById(R.id.btnExportPdf);

        setupMonthFilter();

        btnExpense.setOnClickListener(v -> {
            isExpenseView = true;
            incomeOrExpense.setText("Expense");
            highlightActiveButton();
            String selectedMonth = spinnerMonth.getSelectedItem().toString();
            fetchDataFromFirestore(List.of("Expense"), selectedMonth);
        });


        btnIncome.setOnClickListener(v -> {
            isExpenseView = false;
            incomeOrExpense.setText("Allowance/Income");
            highlightActiveButton();
            String selectedMonth = spinnerMonth.getSelectedItem().toString();
            fetchDataFromFirestore(List.of("Income", "Allowance"), selectedMonth);
        });

        btnExportPdf.setOnClickListener(v -> exportReportAsPdf());

        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(FinancialReportActivity.this, DashboardActivity.class));
            finish();
        });

        String currentMonth = new SimpleDateFormat("MMMM", Locale.getDefault()).format(Calendar.getInstance().getTime());
        spinnerMonth.setSelection(Calendar.getInstance().get(Calendar.MONTH));
        fetchDataFromFirestore(List.of("Expense"), currentMonth);
        highlightActiveButton();
    }

    private void highlightActiveButton() {
        btnExpense.setBackgroundColor(isExpenseView ?
                ContextCompat.getColor(this, R.color.selected_button) :
                ContextCompat.getColor(this, R.color.unselected_button));
        btnIncome.setBackgroundColor(!isExpenseView ?
                ContextCompat.getColor(this, R.color.selected_button) :
                ContextCompat.getColor(this, R.color.unselected_button));
    }

    private void fetchDataFromFirestore(List<String> types, String selectedMonth) {
        if (user == null) return;

        categoryList = new ArrayList<>();
        String userId = user.getUid();
        HashMap<String, CategoryItem> categoryMap = new HashMap<>();
        double[] maxAmount = {0};

        firestore.collection("users").document(userId)
                .collection("transactions")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String type = document.getString("type");
                            String category = document.getString("category");
                            double amount = document.getDouble("amount") != null ? document.getDouble("amount") : 0;
                            Timestamp timestamp = document.getTimestamp("timestamp");
                            String month = getMonthFromTimestamp(timestamp);

                            if (types.contains(type) && month.equalsIgnoreCase(selectedMonth)) {
                                if (categoryMap.containsKey(category)) {
                                    categoryMap.get(category).addAmount(amount);
                                } else {
                                    categoryMap.put(category, new CategoryItem(category, amount));
                                }
                                if (amount > maxAmount[0]) maxAmount[0] = amount;
                            }
                        }

                        categoryList.addAll(categoryMap.values());
                        categoryList.sort(Comparator.comparing(CategoryItem::getName));

                        adapter = new CategoryAdapter(this, categoryList, isExpenseView, maxAmount[0]);
                        recyclerView.setAdapter(adapter);
                        setupBarChart();
                    } else {
                        Toast.makeText(this, "Error fetching data", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String getMonthFromTimestamp(Object timestamp) {
        try {
            Calendar calendar = Calendar.getInstance();
            if (timestamp instanceof Timestamp) {
                calendar.setTimeInMillis(((Timestamp) timestamp).toDate().getTime());
            } else if (timestamp instanceof Long) {
                calendar.setTimeInMillis((Long) timestamp);
            }
            return new SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.getTime());
        } catch (Exception e) {
            Log.e("FinancialReport", "Invalid timestamp: " + timestamp, e);
        }
        return "";
    }

    private void setupBarChart() {
        barChart.getDescription().setEnabled(false);
        barChart.setPinchZoom(false);
        barChart.setDrawBarShadow(false);
        barChart.setDrawGridBackground(false);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(categoryList.size());
        xAxis.setValueFormatter(new IndexAxisValueFormatter(categoryList.stream()
                .map(CategoryItem::getName)
                .collect(Collectors.toList())));
        xAxis.setLabelRotationAngle(45);
        barChart.getAxisLeft().setDrawGridLines(true);
        barChart.getAxisRight().setEnabled(false);

        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < categoryList.size(); i++) {
            CategoryItem category = categoryList.get(i);
            entries.add(new BarEntry(i, (float) category.getAmount()));
        }

        BarDataSet dataSet = new BarDataSet(entries, "Category Breakdown");
        dataSet.setColors(
                ContextCompat.getColor(this, R.color.pastel_blue),
                ContextCompat.getColor(this, R.color.pastel_lightblue),
                ContextCompat.getColor(this, R.color.pastel_lightpink),
                ContextCompat.getColor(this, R.color.pastel_pink),
                ContextCompat.getColor(this, R.color.pastel_lightyellow),
                ContextCompat.getColor(this, R.color.pastel_yellow)
        );
        dataSet.setValueTextSize(10f);

        ArrayList<IBarDataSet> dataSets = new ArrayList<>();
        dataSets.add(dataSet);

        BarData data = new BarData(dataSets);
        data.setBarWidth(0.9f);

        barChart.setData(data);
        barChart.setFitBars(true);
        barChart.notifyDataSetChanged();
        barChart.animateY(1500);
        barChart.invalidate();
    }

    private void setupMonthFilter() {
        String[] months = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, months);
        spinnerMonth.setAdapter(adapter);

        spinnerMonth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isSpinnerInitialized) {
                    isSpinnerInitialized = true;
                    return;
                }
                String selectedMonth = months[position];
                if (isExpenseView) {
                    fetchDataFromFirestore(List.of("Expense"), selectedMonth);
                } else {
                    fetchDataFromFirestore(List.of("Income", "Allowance"), selectedMonth);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }
    private void exportReportAsPdf() {
        PdfDocument pdfDocument = new PdfDocument();
        Paint paint = new Paint();
        int pageWidth = 595;
        int pageHeight = 842;

        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        paint.setTextSize(16);
        paint.setFakeBoldText(true);
        canvas.drawText("Financial Report - " + (isExpenseView ? "Expense" : "Income"), 40, 50, paint);

        paint.setTextSize(12);
        paint.setFakeBoldText(false);
        int y = 90;

        for (CategoryItem item : categoryList) {
            String line = item.getName() + ": ₱" + String.format(Locale.getDefault(), "%.2f", item.getAmount());
            canvas.drawText(line, 40, y, paint);
            y += 25;
        }

        pdfDocument.finishPage(page);
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MoneaseExpenseTracker");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        String fileName = "transaction report" + System.currentTimeMillis() + ".pdf";
        File file = new File(folder, fileName);

        try {
            pdfDocument.writeTo(new FileOutputStream(file));
            Toast.makeText(this, "PDF exported to: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            shareFile(file);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error exporting PDF", Toast.LENGTH_SHORT).show();
        }

        pdfDocument.close();
    }
    private void shareFile(File file) {
        Uri fileUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", file);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(intent, "Share PDF file"));
    }
}
