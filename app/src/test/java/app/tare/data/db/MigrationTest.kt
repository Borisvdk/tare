package app.tare.data.db

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Template for schema changes. When bumping [TareDatabase] from N to N+1:
 * 1. add `MIGRATION_N_N+1` and register it in `TareDatabase.build`,
 * 2. add a test here: `helper.createDatabase(DB, N)` → insert rows with raw SQL →
 *    `helper.runMigrationsAndValidate(DB, N + 1, true, MIGRATION_N_N+1)` → assert the rows survived.
 * The exported schemas in app/schemas are what the helper reads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TareDatabase::class.java)

    @Test
    fun version1SchemaValidates() {
        // MigrationTestHelper compares a bare name with an absolute path and fails on Windows
        // under Robolectric. CI (Linux) runs it.
        assumeFalse(System.getProperty("os.name").startsWith("Windows"))
        helper.createDatabase(DB, 1).apply {
            execSQL(
                "INSERT INTO log_entry (day, loggedAt, foodId, name, source, grams, isLiquid, n_kcal, n_protein, n_carbs, n_fat, n_fiber) " +
                    "VALUES (20000, 0, NULL, 'Quick add', 'QUICK', NULL, 0, 500, 20, 50, 10, 0)",
            )
            close()
        }
        // With migrations added later, pass them here and bump the version.
        val db = helper.runMigrationsAndValidate(DB, 1, true)
        val count = db.query("SELECT COUNT(*) FROM log_entry").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
        assertThat(count).isEqualTo(1)
        db.close()
    }

    @Test
    fun version2AddsWeightSourceAndKeepsWeighIns() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"))
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO weight (day, kg, loggedAt) VALUES (20000, 82.4, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 2, true)
        db.query("SELECT kg, source FROM weight WHERE day = 20000").use { c ->
            c.moveToFirst()
            assertThat(c.getDouble(0)).isEqualTo(82.4)
            assertThat(c.getString(1)).isEqualTo("manual")
        }
        db.close()
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
