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
package ca.stellardrift.permissionsex.velocity;

import ca.stellardrift.permissionsex.minecraft.MinecraftPermissionsEx;
import ca.stellardrift.permissionsex.minecraft.command.MessageFormatter;
import ca.stellardrift.permissionsex.proxycommon.ProxyCommon;
import net.kyori.adventure.text.format.NamedTextColor;

final class VelocityMessageFormatter extends MessageFormatter {

    VelocityMessageFormatter(final MinecraftPermissionsEx<?> manager) {
        super(manager, NamedTextColor.GOLD, NamedTextColor.YELLOW);
    }

    @Override
    protected String transformCommand(final String cmd) {
        return ProxyCommon.PROXY_COMMAND_PREFIX + cmd;
    }

}
