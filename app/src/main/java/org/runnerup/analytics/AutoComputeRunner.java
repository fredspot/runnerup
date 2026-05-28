package org.runnerup.analytics;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/** Runs stale-check and compute for precomputed analytics tables. */
public final class AutoComputeRunner {

  private static final String TAG = "AutoComputeRunner";

  private interface ComputeJob {
    String logName();

    boolean alwaysRun();

    boolean isStale(SQLiteDatabase db);

    int compute(SQLiteDatabase db);
  }

  private AutoComputeRunner() {}

  public static void runAll(SQLiteDatabase db, int[] monthlyComparisonZoneBounds) {
    ComputeJob[] jobs = {
      new ComputeJob() {
        @Override
        public String logName() {
          return "best times";
        }

        @Override
        public boolean alwaysRun() {
          return false;
        }

        @Override
        public boolean isStale(SQLiteDatabase db) {
          return BestTimesCalculator.isDataStale(db);
        }

        @Override
        public int compute(SQLiteDatabase db) {
          return BestTimesCalculator.computeBestTimes(db);
        }
      },
      new ComputeJob() {
        @Override
        public String logName() {
          return "statistics";
        }

        @Override
        public boolean alwaysRun() {
          return false;
        }

        @Override
        public boolean isStale(SQLiteDatabase db) {
          return StatisticsCalculator.isDataStale(db);
        }

        @Override
        public int compute(SQLiteDatabase db) {
          return StatisticsCalculator.computeStatistics(db);
        }
      },
      new ComputeJob() {
        @Override
        public String logName() {
          return "monthly comparison";
        }

        @Override
        public boolean alwaysRun() {
          return false;
        }

        @Override
        public boolean isStale(SQLiteDatabase db) {
          return MonthlyComparisonCalculator.isDataStale(db);
        }

        @Override
        public int compute(SQLiteDatabase db) {
          return MonthlyComparisonCalculator.computeComparison(db, monthlyComparisonZoneBounds);
        }
      },
      new ComputeJob() {
        @Override
        public String logName() {
          return "HR zones";
        }

        @Override
        public boolean alwaysRun() {
          return false;
        }

        @Override
        public boolean isStale(SQLiteDatabase db) {
          return HRZoneStatsCalculator.isDataStale(db);
        }

        @Override
        public int compute(SQLiteDatabase db) {
          return HRZoneStatsCalculator.computeHRZones(db);
        }
      },
      new ComputeJob() {
        @Override
        public String logName() {
          return "yearly cumulative";
        }

        @Override
        public boolean alwaysRun() {
          return false;
        }

        @Override
        public boolean isStale(SQLiteDatabase db) {
          return YearlyCumulativeCalculator.isDataStale(db);
        }

        @Override
        public int compute(SQLiteDatabase db) {
          return YearlyCumulativeCalculator.computeCumulative(db);
        }
      }
    };

    for (ComputeJob job : jobs) {
      if (job.alwaysRun() || job.isStale(db)) {
        if (job.alwaysRun()) {
          Log.i(TAG, "Computing " + job.logName() + "...");
        } else {
          Log.i(TAG, job.logName() + " data is stale, computing...");
        }
        int computed = job.compute(db);
        Log.i(TAG, "Computed " + computed + " " + job.logName() + " records");
      } else {
        Log.i(TAG, job.logName() + " data is fresh, skipping computation");
      }
    }
  }
}
