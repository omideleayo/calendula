package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.j256.ormlite.misc.TransactionManager;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import java.sql.SQLException;
import java.util.concurrent.Callable;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.DailyScheduleItem;
import es.usc.citius.servando.calendula.persistence.Routine;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.ScheduleItem;

/**
 * Created by joseangel.pineiro on 10/10/14.
 */
public class DailyAgenda {

    public static final String TAG = DailyAgenda.class.getName();

    private static final String PREFERENCES_NAME = "DailyAgendaPreferences";
    private static final String PREF_LAST_DATE = "LastDate";

    private static final int SHOWN_DAYS = 2;

    public void setupForToday(Context ctx, boolean force) {

        final SharedPreferences settings = ctx.getSharedPreferences(PREFERENCES_NAME, 0);
        final DateTime now = DateTime.now();
        Long lastDate = settings.getLong(PREF_LAST_DATE, 0);

        Log.d(TAG, "Setup daily agenda. Last updated: " + new DateTime(lastDate).toString("dd/MM - kk:mm"));
        Interval today = new Interval(now.withTimeAtStartOfDay(), now.withTimeAtStartOfDay().plusDays(1));

        // we need to update daily agenda
        if (!today.contains(lastDate) || force) {
            // Start transaction
            try {
                TransactionManager.callInTransaction(DB.helper().getConnectionSource(), new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        // delete old items
                        DB.dailyScheduleItems().removeAll();
                        // and add new ones
                        createDailySchedule(now);
                        // Save last date to prefs
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putLong(PREF_LAST_DATE, now.getMillis());
                        editor.commit();
                        // End transaction

                        return null;
                    }
                });

            } catch (SQLException e) {
                Log.e(TAG, "Error setting up daily agenda", e);
            }
            // Update alarms
            AlarmScheduler.instance().updateAllAlarms(ctx);

        } else {
            Log.d(TAG, "No need to update daily schedule (" + DailyScheduleItem.findAll().size() + " items found for today)");
        }
    }


    public void createScheduleForDate(LocalDate date) {

        Log.d(TAG, "Adding DailyScheduleItem to daily schedule for date: " + date.toString("dd/MM"));
        int items = 0;
        // create a list with all day doses for schedules bound to routines
        for (Routine r : Routine.findAll())
        {
            for (ScheduleItem s : r.scheduleItems())
            {
                if(s.schedule().enabledForDate(date)){
                    // create a dailyScheduleItem and save it
                    DailyScheduleItem dsi = new DailyScheduleItem(s);
                    dsi.setDate(date);
                    dsi.save();
                    items++;
                }
            }
        }
        // Do the same for hourly schedules
        for (Schedule s : DB.schedules().findHourly())
        {
            // create an schedule item for each repetition today
            for (DateTime time : s.hourlyItemsAt(date.toDateTimeAtStartOfDay()))
            {
                LocalTime timeToday = time.toLocalTime();
                DailyScheduleItem dsi = new DailyScheduleItem(s, timeToday);
                dsi.setDate(date);
                dsi.save();
            }
        }
        Log.d(TAG, items + " items added to daily schedule");
    }

    public void createDailySchedule(DateTime d) {

        for(int i = 0; i < SHOWN_DAYS; i++, d=d.plusDays(1)){
            createScheduleForDate(d.toLocalDate());
        }

        for(DailyScheduleItem s : DB.dailyScheduleItems().findAll()){
            Log.d(TAG, s.toString());
        }
    }

    public void addItem(ScheduleItem item, boolean taken){
        // add to daily schedule
        DailyScheduleItem dsi = new DailyScheduleItem(item);
        dsi.setTakenToday(taken);
        dsi.save();

        for(int i = 1;  i < SHOWN_DAYS; i++){
            dsi = new DailyScheduleItem(item);
            dsi.setDate(LocalDate.now().plusDays(i));
            dsi.setTakenToday(taken);
            dsi.save();
        }
    }

    public void addItem(Schedule s, LocalTime time){
        // add to daily schedule
        DailyScheduleItem dsi = new DailyScheduleItem(s, time);
        dsi.save();

        for(int i = 1;  i < SHOWN_DAYS; i++){
            dsi = new DailyScheduleItem(s, time);
            dsi.setDate(LocalDate.now().plusDays(i));
            dsi.save();
        }
    }

    // SINGLETON

    private static final DailyAgenda instance = new DailyAgenda();

    private DailyAgenda() {
    }

    public static final DailyAgenda instance() {
        return instance;
    }
}
