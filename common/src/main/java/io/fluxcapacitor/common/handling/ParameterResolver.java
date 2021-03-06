/*
 * Copyright (c) 2016-2017 Flux Capacitor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxcapacitor.common.handling;

import java.lang.reflect.Parameter;
import java.util.Optional;
import java.util.function.Function;

@FunctionalInterface
public interface ParameterResolver<M> {

    Function<M, Object> resolve(Parameter parameter);

    default Function<M, ? extends Class<?>> resolveClass(Parameter parameter) {
        Function<M, Object> valueResolver = resolve(parameter);
        if (valueResolver == null) {
            return null;
        }
        return Optional.ofNullable(resolve(parameter)).map(f -> f.andThen(Object::getClass)).orElse(null);
    }

}
