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
import androidx.datastore.CorruptionException
import androidx.datastore.DataMigration
import androidx.datastore.DataStore
import androidx.datastore.DataStoreFactory
import androidx.datastore.handlers.ReplaceFileCorruptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Public factory for creating PreferenceDataStore instances.
 */
object PreferenceDataStoreFactory {
    /**
     * Create an instance of SingleProcessDataStore. The user is responsible for ensuring that
     * there is never more than one instance of SingleProcessDataStore acting on a file at a time.
     *
     * @param produceFile Function which returns the file that the new DataStore will act on.
     * The function must return the same path every time. No two instances of PreferenceDataStore
     * should act on the same file at the same time. The file must have the extension
     * preferences_pb.
     * @param corruptionHandler The corruptionHandler is invoked if DataStore encounters a
     * [CorruptionException] when attempting to read data. CorruptionExceptions are thrown by
     * serializers when data cannot be de-serialized.
     * @param migrations are run before any access to data can occur. Each producer and migration
     * may be run more than once whether or not it already succeeded (potentially because another
     * migration failed or a write to disk failed.)
     * @param scope The scope in which IO operations and transform functions will execute.
     */
    fun create(
        produceFile: () -> File,
        corruptionHandler: ReplaceFileCorruptionHandler<Preferences>? = null,
        migrations: List<DataMigration<Preferences>> = listOf(),
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    ): DataStore<Preferences> {
        val delegate = DataStoreFactory.create(
            produceFile = {
                val file = produceFile()
                check(file.extension == PreferencesSerializer.fileExtension) {
                    "File extension for file: $file does not match required extension for" +
                            " Preferences file: ${PreferencesSerializer.fileExtension}"
                }
                file
            },
            serializer = PreferencesSerializer,
            corruptionHandler = corruptionHandler,
            migrations = migrations,
            scope = scope
        )
        return PreferenceDataStore(delegate)
    }
}

/**
 * Create an instance of SingleProcessDataStore. The user is responsible for ensuring that
 * there is never more than one instance of SingleProcessDataStore acting on a file at a time.
 *
 * @param name The name of the preferences. The preferences will be stored in a file obtained
 * by calling: File(context.filesDir, "datastore/" + name + ".preferences_pb")
 * @param corruptionHandler The corruptionHandler is invoked if DataStore encounters a
 * [CorruptionException] when attempting to read data. CorruptionExceptions are thrown by
 * serializers when data can not be de-serialized.
 * @param migrations are run before any access to data can occur. Each producer and migration
 * may be run more than once whether or not it already succeeded (potentially because another
 * migration failed or a write to disk failed.)
 * @param scope The scope in which IO operations and transform functions will execute.
 */
fun Context.createDataStore(
    name: String,
    corruptionHandler: ReplaceFileCorruptionHandler<Preferences>? = null,
    migrations: List<DataMigration<Preferences>> = listOf(),
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        produceFile = {
            File(this.filesDir, "datastore/$name.preferences_pb")
        },
        corruptionHandler = corruptionHandler,
        migrations = migrations,
        scope = scope
    )

internal class PreferenceDataStore(private val delegate: DataStore<Preferences>) :
    DataStore<Preferences> by delegate {
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences):
            Preferences {
        return delegate.updateData {
            // Make a defensive copy
            transform(it).toPreferences()
        }
    }
}
