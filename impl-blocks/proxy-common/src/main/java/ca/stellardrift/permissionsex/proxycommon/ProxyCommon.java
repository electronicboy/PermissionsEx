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
 */
package ca.stellardrift.permissionsex.proxycommon;

import ca.stellardrift.permissionsex.subject.SubjectRef;
import ca.stellardrift.permissionsex.subject.SubjectType;
import org.spongepowered.configurate.util.UnmodifiableCollections;

import java.util.Map;

public final class ProxyCommon {
    private ProxyCommon() {}

    private static final String SERVER_CONSOLE_NAME = "Server";
    public static final SubjectType<String> SUBJECTS_SYSTEM = SubjectType.stringIdentBuilder("system")
            .fixedEntries(UnmodifiableCollections.immutableMapEntry("Server", () -> null))
            .build();
    public static final SubjectRef<String> IDENT_SERVER_CONSOLE = SubjectRef.subject(SUBJECTS_SYSTEM, SERVER_CONSOLE_NAME);
    public static final String PROXY_COMMAND_PREFIX = "/";
}
