import ca.stellardrift.permissionsex.gradle.setupPublication

/*
 * PermissionsEx
 * Copyright (C) zml and PermissionsEx contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

setupPublication()

dependencies {
    api(project(":api"))
    api(project(":core"))
    implementation("com.google.code.gson:gson:2.8.0")
    annotationProcessor("org.immutables:value:2.8.8")
    compileOnlyApi("org.immutables:gson:2.8.8")
    testRuntimeOnly("org.slf4j:slf4j-simple:1.7.30")
}

opinionated {
    useJUnit5()
}