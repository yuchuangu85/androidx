/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.datastore.preferences

import android.content.Context
import androidx.datastore.DataMigration
import androidx.datastore.handlers.ReplaceFileCorruptionHandler
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals

@ObsoleteCoroutinesApi
@kotlinx.coroutines.ExperimentalCoroutinesApi
@FlowPreview
class PreferenceDataStoreFactoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var testFile: File
    private lateinit var dataStoreScope: TestCoroutineScope
    private lateinit var context: Context

    val stringKey = preferencesKey<String>("key")
    val booleanKey = preferencesKey<Boolean>("key")

    @Before
    fun setUp() {
        testFile = tmp.newFile("test_file." + PreferencesSerializer.fileExtension)
        dataStoreScope = TestCoroutineScope()
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testNewInstance() = runBlockingTest {
        val store = PreferenceDataStoreFactory.create(
            produceFile = { testFile },
            scope = dataStoreScope
        )

        val expectedPreferences = preferencesOf(stringKey to "value")

        assertEquals(store.edit { prefs ->
            prefs[stringKey] = "value"
        }, expectedPreferences)
        assertEquals(expectedPreferences, store.data.first())
    }

    @Test
    fun testCorruptionHandlerInstalled() = runBlockingTest {
        testFile.writeBytes(byteArrayOf(0x00, 0x00, 0x00, 0x03)) // Protos can not start with 0x00.

        val valueToReplace = preferencesOf(booleanKey to true)

        val store = PreferenceDataStoreFactory.create(
            produceFile = { testFile },
            corruptionHandler = ReplaceFileCorruptionHandler<Preferences> {
                valueToReplace
            },
            scope = dataStoreScope
        )
        assertEquals(valueToReplace, store.data.first())
    }

    @Test
    fun testMigrationsInstalled() = runBlockingTest {

        val expectedPreferences = preferencesOf(stringKey to "value", booleanKey to true)

        val migrateTo5 = object : DataMigration<Preferences> {
            override suspend fun shouldMigrate(currentData: Preferences) = true

            override suspend fun migrate(currentData: Preferences) =
                currentData.toMutablePreferences().apply { set(stringKey, "value") }.toPreferences()

            override suspend fun cleanUp() {}
        }

        val migratePlus1 = object : DataMigration<Preferences> {
            override suspend fun shouldMigrate(currentData: Preferences) = true

            override suspend fun migrate(currentData: Preferences) =
                currentData.toMutablePreferences().apply { set(booleanKey, true) }.toPreferences()

            override suspend fun cleanUp() {}
        }

        val store = PreferenceDataStoreFactory.create(
            produceFile = { testFile },
            migrations = listOf(migrateTo5, migratePlus1),
            scope = dataStoreScope
        )

        assertEquals(expectedPreferences, store.data.first())
    }

    @Test
    fun testCreateWithContextAndName() = runBlockingTest {
        val prefs = preferencesOf(stringKey to "value")

        var store = context.createDataStore(
            name = "my_settings",
            scope = dataStoreScope
        )
        store.updateData { prefs }

        // Create it again and confirm it's still there
        store = context.createDataStore("my_settings", scope = dataStoreScope)
        assertEquals(prefs, store.data.first())

        // Check that the file name is context.filesDir + name + ".preferences_pb"
        store = PreferenceDataStoreFactory.create(produceFile = {
            File(context.filesDir, "datastore/my_settings.preferences_pb")
        }, scope = dataStoreScope)
        assertEquals(prefs, store.data.first())
    }

    @Test
    fun testCantMutateInternalState() = runBlockingTest {
        val store =
            PreferenceDataStoreFactory.create(produceFile = { testFile }, scope = dataStoreScope)

        var mutableReference: MutablePreferences? = null
        store.edit {
            mutableReference = it
            it[stringKey] = "ABCDEF"
        }

        assertEquals(store.data.first(), preferencesOf(stringKey to "ABCDEF"))
        mutableReference!!.clear()
        assertEquals(store.data.first(), preferencesOf(stringKey to "ABCDEF"))
    }
}