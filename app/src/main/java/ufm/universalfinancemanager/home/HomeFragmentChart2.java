package ufm.universalfinancemanager.home;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.widget.SearchView;
import android.widget.Toast;


import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.time.Month;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.stream.Collectors;

import javax.inject.Inject;

import dagger.android.support.DaggerFragment;
import ufm.universalfinancemanager.R;
import ufm.universalfinancemanager.addeditaccount.AddEditAccountActivity;
import ufm.universalfinancemanager.addeditbudget.AddEditBudgetActivity;
import ufm.universalfinancemanager.addeditcategory.AddEditCategoryActivity;
import ufm.universalfinancemanager.addeditreminder.AddEditReminderActivity;
import ufm.universalfinancemanager.addedittransaction.AddEditTransactionActivity;
import ufm.universalfinancemanager.db.entity.Transaction;
import ufm.universalfinancemanager.support.AccountType;
import ufm.universalfinancemanager.support.Flow;
import ufm.universalfinancemanager.db.entity.Account;
import ufm.universalfinancemanager.support.atomic.User;


/* Aaron: This is the chart for the Networth Bar Graph */
public class HomeFragmentChart2 extends DaggerFragment implements HomeContract.View {

    @Inject
    public HomePresenter mPresenter;

    private CombinedChart mChart;

    @Inject
    public User tUser;

    private int numMonths = 6;
    private View mTransactionsView;
    private SearchView mSearchView;

    private float yMaxValue = 0;
    private List<HomeNetWorthData> arrNWData = new ArrayList<HomeNetWorthData>();
    private List<HomeAccountData> arrAllAccounts = new ArrayList<HomeAccountData>();

    protected String[] mMonths = new String[] { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul" };

    Calendar cal = Calendar.getInstance();
    private int thisMonth = -1;
    private double totalDebts = 0.0;
    private double totalAssets = 0.0;

    float arrData[][] = new float[3][7];

    @Inject
    public HomeFragmentChart2() {}

    @Override
    public void onResume() {
        super.onResume();
        mPresenter.takeView(this);
    }

    @Override
    public void populateList(List<Transaction> items) {

        int month;

        // 1. Initialize 12 months of data
        for (int i = 0; i < 12; i++) {
            arrNWData.add(i, new HomeNetWorthData(i, 0.0f, 0.0f));
        }

        // 2. Get this month
        Date date = new Date();
        cal.setTime(date);
        month = cal.get(Calendar.MONTH);
        thisMonth = month;

        createMonthLegend(month);


        int j = 0;
        // 3. Get Account type and current balances
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getFromAccount() != null) {
                int debtOrAsset = -1;
                Account fromAccount = tUser.getAccount(items.get(i).getFromAccount());

                if (fromAccount.getType() == AccountType.CREDIT_CARD) {
                    arrAllAccounts.add(new HomeAccountData(
                            fromAccount.getName(),
                            AcctType.DEBT,
                            fromAccount.getBalance()));
                }
                else if (fromAccount.getType() != AccountType.CREDIT_CARD) {
                    arrAllAccounts.add(new HomeAccountData(
                            fromAccount.getName(),
                            AcctType.ASSET,
                            fromAccount.getBalance()));
                }

            }
            else if (items.get(i).getToAccount() != null) {

                int debtOrAsset = -1;
                Account toAccount = tUser.getAccount(items.get(i).getToAccount());

                if (toAccount.getType() == AccountType.CREDIT_CARD) {
                    arrAllAccounts.add(new HomeAccountData(
                            toAccount.getName(),
                            AcctType.DEBT,
                            toAccount.getBalance()));
                }
                else if (toAccount.getType() != AccountType.CREDIT_CARD) {
                    arrAllAccounts.add(new HomeAccountData(
                            toAccount.getName(),
                            AcctType.ASSET,
                            toAccount.getBalance()));
                }
            }
        }

        // Sort by Account Name.
        Collections.sort(arrAllAccounts, HomeAccountData.COMPARE_BY_NAME);

        // Remove Dupes with Set
        for (j = arrAllAccounts.size() - 1; j > 0; j--) {
            Log.d("Aaron's Debug", "\nMessage: " +
                    arrAllAccounts.get(j).acctName + " vs. " +
                    arrAllAccounts.get(j-1).acctName);
            if (arrAllAccounts.get(j).acctName.equals(arrAllAccounts.get(j-1).acctName)) {
                arrAllAccounts.remove(j);
            }
        }

        // Get running totals for totalAssests and totalDebt

        for (j = 0; j < arrAllAccounts.size(); j++) {
            if (arrAllAccounts.get(j).acctType == AcctType.DEBT)
                totalDebts = totalDebts + arrAllAccounts.get(j).acctBal;
            else if (arrAllAccounts.get(j).acctType == AcctType.ASSET)
                totalAssets = totalAssets + arrAllAccounts.get(j).acctBal;
        }


        // 2. Add Transaction amounts based on flowtype and account type
        for (int i = 0; i < items.size(); i++) {

            cal.setTime(items.get(i).getDate());
            month = cal.get(Calendar.MONTH);

            // If month = arrNWData.monthInt
            for (int k = 0; k < arrNWData.size(); k++) {
                Account account = null;

                if(items.get(i).getFlow() != Flow.TRANSFER)
                    account = items.get(i).getFlow() == Flow.INCOME ?
                            tUser.getAccount(items.get(i).getToAccount()) :
                            tUser.getAccount(items.get(i).getFromAccount());
                else
                    continue;

                if (items.get(i).getFlow() == Flow.OUTCOME &&
                        account.getType() == AccountType.CREDIT_CARD) {
                    // If transaction is an Outcome & from a credit card, totalDebt increases

                    if (arrNWData.get(k).monthInt == month)
                        arrNWData.get(k).totalDebt += items.get(i).getAmount();

                    Log.d("\nAaron debug: ",
                            "Account Flow: " + items.get(i).getFlow() +
                                    "\nfromAccount type: " + account.getType() +
                                    "\nAmount: " + items.get(i).getAmount());
                } else if (items.get(i).getFlow() == Flow.OUTCOME &&
                        account.getType() != AccountType.CREDIT_CARD) {
                    // If transaction is an Outcome & from an asset account, totalAssets decrease

                    if (arrNWData.get(k).monthInt == month)
                        arrNWData.get(k).totalAsset -= items.get(i).getAmount();

                    Log.d("\nAaron debug: ",
                            "Account Flow: " + items.get(i).getFlow() +
                                    "\nfromAccount type: " + account.getType() +
                                    "\nAmount: " + items.get(i).getAmount());
                } else if (items.get(i).getFlow() == Flow.INCOME &&
                        account.getType() == AccountType.CREDIT_CARD) {
                    // If transaction is an Income & from an credit card account, totalDebt decrease

                    if (arrNWData.get(k).monthInt == month)
                        arrNWData.get(k).totalDebt -= items.get(i).getAmount();

                    Log.d("\nAaron debug: ",
                            "Account Flow: " + items.get(i).getFlow() +
                                    "\ntoAccount type: " + account.getType() +
                                    "\nAmount: " + items.get(i).getAmount());
                } else if (items.get(i).getFlow() == Flow.INCOME &&
                        account.getType() != AccountType.CREDIT_CARD) {
                    // If transaction is an Outcome & from an asset account, totalAssets increase

                    if (arrNWData.get(k).monthInt == month)
                        arrNWData.get(k).totalAsset += items.get(i).getAmount();

                    Log.d("\nAaron debug: ",
                            "Account Flow: " + items.get(i).getFlow() +
                                    "\ntoAccount type: " + account.getType() +
                                    "\nAmount: " + items.get(i).getAmount());
                }
            }



        }

        // Populate Double Array with past 6 months data, including this month





        // 3. Three inputs per month (1: totalCredit, 2: totalDebt: 3: networth
//        getYMaxValue();

//        Log.d("Aaron's Tag", "Message: ");
    }

    public String getMonth(int month) {
        return new DateFormatSymbols().getShortMonths()[month];
    }

    void createMonthLegend(int month) {

        String strMonth = "";
        month = (month + 6)%12;

        for (int i = 0; i < 7; i++) {
            if (month == 12) {
                month = 0;
            }
            strMonth = getMonth(month++);
            mMonths[i] = strMonth;
        }

        Log.d("Aaron Debug: ", "Message: ");

    }
    void getYMaxValue(){
        for (int i = 0; i < arrNWData.size(); i++){
            if (arrNWData.get(i).totalAsset > yMaxValue)
                yMaxValue = arrNWData.get(i).totalAsset;
            if (arrNWData.get(i).totalDebt > yMaxValue)
                yMaxValue = arrNWData.get(i).totalAsset;
        }
        Log.d("Aaron's Tag", "Message: ");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPresenter.dropView();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.home_fragment_chart2, container, false);


        mPresenter.loadTransactions();

        // Get double Array date

//        arrData[][] =

        // Set starting data for net worth (Assets[0][], Liabilities[1][], NetWorth[2][]
        arrData[0][0] = 60f;
        arrData[1][0] = -30f;
        arrData[2][0]= 30f;

//        arrData[0][0] = yMaxValue;
//        arrData[1][0] = -(yMaxValue/2);
//        arrData[2][0]= yMaxValue/2;

        //Generate random data
        Random rand = new Random();

        // Loop through bars 2 through 6 with random-ish data
        for (int i = 1; i < 6; i++) {
            int randomNum1 = rand.nextInt((15 - 3) + 1) + 3;
            arrData[0][i] = arrData[0][i-1]*(1+(float)randomNum1/100);

            int randomNum2 = rand.nextInt((15 - 1) + 1) + 3;
            arrData[1][i] = arrData[1][i-1]*(1+(float)randomNum2/100);

            arrData[2][i] = arrData[0][i] + arrData[1][i];
        }

        mChart = (CombinedChart) root.findViewById(R.id.chart2);
        mChart.getDescription().setText("Category Pie Chart");

        mChart.setDrawGridBackground(true);

        mChart.setDrawBarShadow(true);
        mChart.setHighlightFullBarEnabled(false);

        // draw bars behind lines
        mChart.setDrawOrder(new CombinedChart.DrawOrder[]{
                CombinedChart.DrawOrder.BAR,  CombinedChart.DrawOrder.LINE
        });

        Legend l = mChart.getLegend();
        l.setWordWrapEnabled(true);
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        l.setOrientation(Legend.LegendOrientation.HORIZONTAL);

        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setDrawGridLines(false);
        rightAxis.setAxisMinimum(-100f); // start at -100
        rightAxis.setAxisMaximum(100f); // the axis maximum is 100
//        rightAxis.setAxisMinimum(-yMaxValue*1.1f); // start at -100
//        rightAxis.setAxisMaximum(yMaxValue*1.1f); // the axis maximum is 100

        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setDrawGridLines(false);
        leftAxis.setAxisMinimum(-100f); // start at -100
        leftAxis.setAxisMaximum(100f); // the axis maximum is 100
//        leftAxis.setAxisMinimum(-yMaxValue*1.1f); // start at -100
//        leftAxis.setAxisMaximum(yMaxValue*1.1f); // the axis maximum is 100


        XAxis xAxis = mChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTH_SIDED);
        xAxis.setAxisMinimum(0f);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IAxisValueFormatter() {
            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                return mMonths[(int) value % mMonths.length];
            }
        });

        CombinedData data = new CombinedData();

        data.setData(generateLineData());
        data.setData(generateBarData());

        xAxis.setAxisMaximum(data.getXMax() + 0.25f);
        mChart.setData(data);
        mChart.invalidate();

        return root;
    }

    private ArrayList<Entry> getLineEntriesData(ArrayList<Entry> entries){

        // Get data by month, subtotaled by flowType


        entries.add(new Entry(1, arrData[2][0]));
        entries.add(new Entry(2, arrData[2][1]));
        entries.add(new Entry(3, arrData[2][2]));
        entries.add(new Entry(4, arrData[2][3]));
        entries.add(new Entry(5, arrData[2][4]));
        entries.add(new Entry(6, arrData[2][5]));

        ArrayList<BarEntry> yVals = new ArrayList<>();

//        float barWidth = 9f;
//        float spaceForBar = 10f;

        for (int i = 0; i < 7; i++) {
            float val1 = (float) (Math.random() * 100);
            float val2 = (float) (100);

            yVals.add(new BarEntry(i, new float[]{val1, val2 - val1}));
        }

        return entries;

    }

    private ArrayList<BarEntry> getBarEnteries(ArrayList<BarEntry> entries){


        entries.add(new BarEntry(1,arrData[0][0]));
        entries.add(new BarEntry(1,arrData[1][0]));
        entries.add(new BarEntry(2,arrData[0][1]));
        entries.add(new BarEntry(2,arrData[1][1]));
        entries.add(new BarEntry(3,arrData[0][2]));
        entries.add(new BarEntry(3,arrData[1][2]));
        entries.add(new BarEntry(4,arrData[0][3]));
        entries.add(new BarEntry(4,arrData[1][3]));
        entries.add(new BarEntry(5,arrData[0][4]));
        entries.add(new BarEntry(5,arrData[1][4]));
        entries.add(new BarEntry(6,arrData[0][5]));
        entries.add(new BarEntry(6,arrData[1][5]));

        return  entries;
    }

    private LineData generateLineData() {

        LineData d = new LineData();

        ArrayList<Entry> entries = new ArrayList<Entry>();

        entries = getLineEntriesData(entries);

        LineDataSet set = new LineDataSet(entries, "Net Worth");
        set.setColor(Color.rgb(240, 238, 70));
        set.setColor(Color.DKGRAY);
        // set.setColors(ColorTemplate.COLORFUL_COLORS);
        set.setLineWidth(2.5f);
        set.setCircleColor(Color.rgb(240, 238, 70));
        set.setCircleRadius(5f);
        set.setFillColor(Color.rgb(240, 238, 70));
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setDrawValues(true);
        set.setValueTextSize(10f);
        // set.setValueTextColor(Color.rgb(240, 238, 70));
        set.setValueTextColor(Color.rgb(255, 255, 255));

        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        d.addDataSet(set);

        return d;
    }

    private BarData generateBarData() {

        ArrayList<BarEntry> entries = new ArrayList<BarEntry>();
        entries = getBarEnteries(entries);

        BarDataSet set1 = new BarDataSet(entries, "Assets/Liabilities");
        set1.setColor(Color.rgb(60, 220, 78));
        set1.setColors(ColorTemplate.COLORFUL_COLORS);
        set1.setValueTextColor(Color.rgb(60, 220, 78));
        set1.setValueTextSize(10f);
        set1.setAxisDependency(YAxis.AxisDependency.LEFT);

        ArrayList<Integer> colors  = getColors();
        set1.setColors(colors);

        float barWidth = 0.45f; // x2 dataset


        BarData d = new BarData(set1);
        d.setBarWidth(barWidth);


        return d;
    }

    // Get array of colors for horizontal stacked bar chart, with 2nd value as light grey
    ArrayList<Integer> getColors() {

        ArrayList<Integer> tempColors = new ArrayList<Integer>();

//        for (int c : ColorTemplate.VORDIPLOM_COLORS) {
//            tempColors.add(c);
//            tempColors.add(Color.LTGRAY);
//        }
        tempColors.add(Color.GREEN);
        tempColors.add(Color.RED);

        return tempColors;
    }
}
