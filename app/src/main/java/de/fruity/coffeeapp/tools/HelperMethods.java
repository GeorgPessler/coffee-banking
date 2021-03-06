package de.fruity.coffeeapp.tools;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import de.fruity.coffeeapp.R;
import de.fruity.coffeeapp.database.SqlAccessAPI;
import de.fruity.coffeeapp.ui_elements.CustomToast;

public class HelperMethods {

    private static final int[] color_array = {Color.GREEN, Color.BLUE, Color.RED, Color.YELLOW, Color.GRAY};

    public static String roundTwoDecimals(float d) {
        Float tmp = (float) Math.round(d * 100) / 100;
        return String.format(Locale.getDefault(), "%.2f", tmp);
    }

    public static BigDecimal roundTwoDecimals(float d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd;
    }

	public static int roundAndConvert(float value)
    {
        String rounded = roundTwoDecimals(value);
        float value_f = 0;
        try {
            value_f = NumberFormat.getNumberInstance(Locale.getDefault()).parse(rounded).floatValue();
        } catch (ParseException e) {
            e.printStackTrace();
        }

        value_f = value_f * 100;

        return (int) value_f;
    }

    static public GraphicalView createLineChart(Context context, long person_id) {
        // Creating a XYMultipleSeriesRenderer to customize the whole chart
        XYMultipleSeriesRenderer multiRenderer = new XYMultipleSeriesRenderer();
        XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();

        multiRenderer.setChartTitle("Values over time");
        multiRenderer.setXTitle("Time");
        multiRenderer.setXLabelsPadding(4.0f);
        multiRenderer.setYTitle("Value in euro");
        multiRenderer.setYLabelsPadding(10.0f);
        multiRenderer.setMargins(new int[]{25, 50, 25, 25});
        multiRenderer.setShowLabels(true);
        multiRenderer.setShowLegend(true);
        multiRenderer.setFitLegend(true);

        TimeSeries ts_coffee = getDataset(context.getContentResolver(), "coffee", person_id);
        TimeSeries ts_candy = getDataset(context.getContentResolver(), "candy", person_id);
        TimeSeries ts_beer = getDataset(context.getContentResolver(), "beer", person_id);
        TimeSeries ts_can = getDataset(context.getContentResolver(), "can", person_id);

        // Adding Visits Series to the dataset
        dataset.addSeries(ts_coffee);
        dataset.addSeries(ts_candy);
        dataset.addSeries(ts_beer);
        dataset.addSeries(ts_can);

        double cur_x_max = dataset.getSeriesAt(0).getMaxX();
        double cur_x_min = dataset.getSeriesAt(0).getMinX();
        double cur_y_max = dataset.getSeriesAt(0).getMaxY();
        double cur_y_min = dataset.getSeriesAt(0).getMinY();
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            // Creating XYSeriesRenderer to customize visitsSeries
            XYSeriesRenderer singleRenderer = new XYSeriesRenderer();
            singleRenderer.setColor(color_array[i]);
            singleRenderer.setPointStyle(PointStyle.CIRCLE);
            singleRenderer.setFillPoints(true);
            singleRenderer.setLineWidth(3);
            singleRenderer.setChartValuesTextSize(20);
            singleRenderer.setDisplayChartValues(true);
            singleRenderer.setAnnotationsColor(Color.YELLOW);
            singleRenderer.setAnnotationsTextAlign(Paint.Align.CENTER);
            singleRenderer.setAnnotationsTextSize(20); //current bug annotations with time charts

            multiRenderer.addSeriesRenderer(singleRenderer);

            if (dataset.getSeriesAt(i).getMinX() < cur_x_min)
                cur_x_min = dataset.getSeriesAt(i).getMinX();
            if (dataset.getSeriesAt(i).getMinY() < cur_y_min)
                cur_y_min = dataset.getSeriesAt(i).getMinY();
            if (dataset.getSeriesAt(i).getMaxX() > cur_x_max)
                cur_x_max = dataset.getSeriesAt(i).getMaxX();
            if (dataset.getSeriesAt(i).getMaxY() > cur_y_max)
                cur_y_max = dataset.getSeriesAt(i).getMaxY();
        }

        multiRenderer.setXAxisMin(cur_x_min - ((cur_x_min / 100) * 0.001)); // values are date time double so they are very huge
        multiRenderer.setXAxisMax(cur_x_max + ((cur_x_max / 100) * 0.001)); // -> 1 %% is enough
        if (cur_y_min == 0)
            cur_y_min = -1.5;
        multiRenderer.setYAxisMin(cur_y_min - ((cur_y_min / 100) * 20));
        multiRenderer.setYAxisMax(cur_y_max + ((cur_y_max / 100) * 20));
        // Creating a Time Chart

        return ChartFactory.getTimeChartView(context, dataset, multiRenderer, "hh:mm dd-MMM");
    }

    static private TimeSeries getDataset(ContentResolver cr, String kind, long person_id) {
        TimeSeries ts_candy = new TimeSeries(kind);
        double summ_candy_val = 0.0d;
        Map<Date, BigDecimal> data_candy = SqlAccessAPI.getDateValueTupel(cr, person_id, kind);

        for (Map.Entry<Date, BigDecimal> value : data_candy.entrySet()) {
            summ_candy_val += value.getValue().doubleValue();
            ts_candy.add(value.getKey(), summ_candy_val);
        }

        return ts_candy;
    }



    public static void billanceToast(Context context, int pk, String database_ident) {
        String gettext = "comma_new_" + database_ident + "_billance";

        StringBuilder sb = new StringBuilder();
        String name = SqlAccessAPI.getName(context.getContentResolver(), pk);
        sb.append(context.getText(R.string.hello));
        sb.append(' ');
        sb.append(name);
        sb.append(' ');

        float value = SqlAccessAPI.getValueFromPersonById(context.getContentResolver(), pk, database_ident);
        String packname = context.getPackageName();
        int resourceId= context.getResources().getIdentifier(gettext, "string", packname);
        sb.append(context.getText(resourceId));

        sb.append(' ');
        sb.append(HelperMethods.roundTwoDecimals(value));
        sb.append(" €");

        new CustomToast(context, sb.toString(), 1500);
    }
}