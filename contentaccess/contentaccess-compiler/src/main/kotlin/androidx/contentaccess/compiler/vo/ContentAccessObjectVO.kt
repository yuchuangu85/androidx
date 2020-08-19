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

package androidx.contentaccess.compiler.vo

import javax.lang.model.type.TypeMirror

data class ContentAccessObjectVO (
    // TODO(obenabde): eventually clean up some of these fields if unused anywhere, same for all
    //  other VOs.
    val contentEntity: ContentEntityVO?,
    val interfaceName: String,
    val packageName: String,
    val interfaceType: TypeMirror,
    val queries: List<ContentQueryVO>,
    val updates: List<ContentUpdateVO>,
    val deletes: List<ContentDeleteVO>,
    val inserts: List<ContentInsertVO>
)